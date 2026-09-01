#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "minitest/autorun"
require "tmpdir"
require_relative "service_checks"

class Phase03ServiceChecksTest < Minitest::Test
  ServiceChecks = Phase03::ServiceChecks

  def manifest
    JSON.parse(File.read(File.join(__dir__, "softhsm-source.json")))
  end

  def valid_report
    {
      "token_initialized" => true,
      "mechanisms" => ServiceChecks::REQUIRED_MECHANISMS,
      "keys" => %w[AES HMAC].map { |kind| { "kind" => kind }.merge(ServiceChecks::REQUIRED_KEY_ATTRIBUTES) }
    }
  end

  def assert_check(error_id)
    error = assert_raises(ServiceChecks::CheckError) { yield }
    assert_equal error_id, error.error_id
  end

  def test_locked_manifest_is_accepted
    assert ServiceChecks.validate_manifest!(manifest)
  end

  def test_capability_patch_is_exact_and_fail_closed_for_context_or_hash_drift
    Dir.mktmpdir("phase03-capability-patch-") do |directory|
      target_dir = File.join(directory, "src/lib")
      FileUtils.mkdir_p(target_dir)
      fixtures = [
        ["SoftHSM.cpp", ServiceChecks::SOFTHSM_CAPABILITY_ORIGINAL,
         ServiceChecks::SOFTHSM_CAPABILITY_REPLACEMENT],
        ["main.cpp", ServiceChecks::SOFTHSM_FUNCTION_LIST_ORIGINAL,
         ServiceChecks::SOFTHSM_FUNCTION_LIST_REPLACEMENT]
      ]
      targets = fixtures.map do |name, original, replacement|
        source = "prefix\n#{original}\nsuffix\n"
        patched = source.sub(original, replacement)
        File.binwrite(File.join(target_dir, name), source)
        {
          path: "src/lib/#{name}", source_sha256: Digest::SHA256.hexdigest(source),
          result_sha256: Digest::SHA256.hexdigest(patched),
          original: original, replacement: replacement
        }
      end

      result = ServiceChecks.apply_softhsm_capability_patch!(
        directory, targets: targets
      )

      assert_equal ServiceChecks::SOFTHSM_CAPABILITY_PATCH_SHA256, result
      targets.each do |specification|
        assert_equal specification.fetch(:result_sha256),
                     Digest::SHA256.file(File.join(directory, specification.fetch(:path))).hexdigest
      end

      missing = "prefix without the locked C_GetInfo context\n"
      File.binwrite(File.join(target_dir, "SoftHSM.cpp"), missing)
      missing_targets = targets.map(&:dup)
      missing_targets.first[:source_sha256] = Digest::SHA256.hexdigest(missing)
      missing_targets.first[:result_sha256] = Digest::SHA256.hexdigest(missing)
      assert_check("SOFTHSM_CAPABILITY_PATCH_CONTEXT_MISMATCH") do
        ServiceChecks.apply_softhsm_capability_patch!(directory, targets: missing_targets)
      end

      assert_check("SOFTHSM_CAPABILITY_PATCH_DIGEST_MISMATCH") do
        ServiceChecks.apply_softhsm_capability_patch!(
          directory, targets: targets, expected_patch_sha256: "0" * 64
        )
      end

      fixtures.each do |name, original, _replacement|
        File.binwrite(File.join(target_dir, name), "prefix\n#{original}\nsuffix\n")
      end
      File.binwrite(File.join(target_dir, "main.cpp"), "hash drift\n")
      assert_check("SOFTHSM_CAPABILITY_SOURCE_DRIFT") do
        ServiceChecks.apply_softhsm_capability_patch!(directory, targets: targets)
      end
    end
  end

  def test_wrong_source_identities_fail_closed
    {
      "archive_sha256" => ["0" * 64, "SOFTHSM_SOURCE_DIGEST_MISMATCH"],
      "version" => ["2.6.1", "SOFTHSM_VERSION_MISMATCH"],
      "tag" => ["main", "SOFTHSM_SOURCE_IDENTITY_MISMATCH"],
      "commit" => ["deadbee", "SOFTHSM_SOURCE_IDENTITY_MISMATCH"],
      "archive_url" => ["https://example.invalid/latest", "SOFTHSM_SOURCE_IDENTITY_MISMATCH"]
    }.each do |field, (value, error_id)|
      assert_check(error_id) { ServiceChecks.validate_manifest!(manifest.merge(field => value)) }
    end
  end

  def test_unknown_and_missing_manifest_fields_fail_closed
    assert_check("SOFTHSM_MANIFEST_SCHEMA_MISMATCH") do
      ServiceChecks.validate_manifest!(manifest.merge("fallback" => "package-manager"))
    end
    assert_check("SOFTHSM_MANIFEST_SCHEMA_MISMATCH") do
      ServiceChecks.validate_manifest!(manifest.reject { |key, _| key == "commit" })
    end
  end

  def test_destination_must_be_owned_and_non_symlinked
    assert_check("SOFTHSM_DESTINATION_OUTSIDE_OWNER") { ServiceChecks.owned_destination!(Dir.tmpdir) }
    owned = File.join(ServiceChecks::OWNED_ROOT, "service-check-test")
    assert_equal File.expand_path(owned), ServiceChecks.owned_destination!(owned)
  end

  def test_runtime_version_is_exact
    assert ServiceChecks.validate_runtime_version!("SoftHSM 2.7.0\n")
    assert_check("SOFTHSM_VERSION_MISMATCH") { ServiceChecks.validate_runtime_version!("SoftHSM 2.7.1") }
  end

  def test_missing_mechanism_fails_closed
    report = valid_report.merge("mechanisms" => ServiceChecks::REQUIRED_MECHANISMS - ["CKM_AES_GCM"])
    assert_check("SOFTHSM_MECHANISM_MISSING") { ServiceChecks.validate_preflight_report!(report) }
  end

  def test_wrong_key_attribute_fails_closed
    keys = valid_report.fetch("keys").map(&:dup)
    keys.first["CKA_EXTRACTABLE"] = true
    assert_check("SOFTHSM_KEY_ATTRIBUTE_MISMATCH") do
      ServiceChecks.validate_preflight_report!(valid_report.merge("keys" => keys))
    end
  end

  def test_missing_token_and_incomplete_key_set_fail_closed
    assert_check("SOFTHSM_TOKEN_NOT_INITIALIZED") do
      ServiceChecks.validate_preflight_report!(valid_report.merge("token_initialized" => false))
    end
    assert_check("SOFTHSM_KEY_SET_INVALID") do
      ServiceChecks.validate_preflight_report!(valid_report.merge("keys" => valid_report.fetch("keys").take(1)))
    end
  end

  def test_cleanup_assertion_detects_residue
    Dir.mktmpdir("phase03-cleanup-test-") do |directory|
      assert_check("SOFTHSM_CLEANUP_FAILED") { ServiceChecks.validate_cleanup!(directory) }
    end
    missing = File.join(Dir.tmpdir, "phase03-missing-#{Process.pid}")
    assert ServiceChecks.validate_cleanup!(missing)
  end

  def test_diagnostics_redact_secrets_and_paths
    output = ServiceChecks.sanitize("user_pin=hunter2 path=/tmp/private/token secret=value")
    refute_includes output, "hunter2"
    refute_includes output, "/tmp/private/token"
    refute_includes output, "value"
  end

  def test_preflight_report_accepts_exact_contract
    assert ServiceChecks.validate_preflight_report!(valid_report)
  end

  def test_service_run_id_grammar_is_closed_and_service_scoped
    assert ServiceChecks.validate_run_id!("mysql-012345abcdef", :mysql)
    assert_check("SERVICE_RUN_ID_INVALID") do
      ServiceChecks.validate_run_id!("minio-012345abcdef", :mysql)
    end
    assert_check("SERVICE_RUN_ID_INVALID") do
      ServiceChecks.validate_run_id!("softhsm-latest", :softhsm)
    end
  end

  def test_minio_image_identity_requires_exact_digest_platform_and_release
    identity = {
      "repo_digests" => [ServiceChecks::MINIO_IMAGE],
      "image_id" => ServiceChecks::MINIO_IMAGE_ID,
      "platform" => "linux/arm64",
      "version" => ServiceChecks::MINIO_VERSION
    }
    assert ServiceChecks.validate_minio_identity!(identity)

    assert_check("MINIO_IMAGE_IDENTITY_MISMATCH") do
      ServiceChecks.validate_minio_identity!(identity.merge("repo_digests" => ["minio/minio@sha256:#{'0' * 64}"]))
    end
    assert_check("MINIO_IMAGE_IDENTITY_MISMATCH") do
      ServiceChecks.validate_minio_identity!(identity.merge("image_id" => "sha256:#{'0' * 64}"))
    end
    assert_check("MINIO_IMAGE_PLATFORM_MISMATCH") do
      ServiceChecks.validate_minio_identity!(identity.merge("platform" => "linux/riscv64"))
    end
    assert_check("MINIO_VERSION_MISMATCH") do
      ServiceChecks.validate_minio_identity!(identity.merge("version" => "latest"))
    end
  end
end
