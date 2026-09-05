#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "minitest/autorun"
require "tmpdir"

require_relative "phase2-ui-evidence"

class Phase2UiEvidenceTest < Minitest::Test
  ROOT = File.expand_path("../..", __dir__)

  def test_current_evidence_is_valid
    result = Phase2UiEvidence.validate!(root: ROOT)
    assert_equal 83, result[:rows].length
  end

  def test_rejects_duplicate_matrix_identity
    with_fixture do |root|
      path = File.join(root, ".planning/phases", Phase2UiEvidence::PHASE, "TEST-MATRIX.md")
      source = File.read(path)
      ids = source.scan(/^\| (OBL-[A-Z0-9-]+) /).flatten
      File.write(path, source.sub(ids[1], ids[0]))
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/DUPLICATE/, error.message)
    end
  end

  def test_rejects_skipped_report_result
    with_fixture do |root|
      manifest_path = manifest_path(root)
      manifest = JSON.parse(File.read(manifest_path))
      report_path = File.join(root, manifest.dig("run", "report_path"))
      report = JSON.parse(File.read(report_path))
      report.fetch("stats")["skipped"] = 1
      File.write(report_path, JSON.pretty_generate(report))
      manifest.fetch("run")["report_sha256"] = Phase2UiEvidence.digest(report_path)
      File.write(manifest_path, JSON.pretty_generate(manifest))
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/SKIPPED_PRESENT/, error.message)
    end
  end

  def test_rejects_synthetic_request_interception
    with_fixture do |root|
      path = File.join(root, Phase2UiEvidence::SOURCE_PATHS[2])
      File.write(path, File.read(path) + "\npage.route(\"**/*\", () => {});\n")
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/SYNTHETIC_ROUTING_PRESENT/, error.message)
    end
  end

  def test_rejects_source_symlink_escape
    with_fixture do |root|
      source_path = File.join(root, Phase2UiEvidence::SOURCE_PATHS[4])
      external = File.join(Dir.tmpdir, "phase2-external-prototype-#{Process.pid}.html")
      File.write(external, File.read(source_path))
      FileUtils.rm_f(source_path)
      File.symlink(external, source_path)
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/SYMLINK_REJECTED|REALPATH_ESCAPE/, error.message)
    ensure
      FileUtils.rm_f(external) if external
    end
  end

  def test_rejects_missing_catalog_target
    with_fixture do |root|
      path = File.join(root, ".planning/phases", Phase2UiEvidence::PHASE, "EVIDENCE/OBL-IA-ADMIN-LOGIN.json")
      FileUtils.rm_f(path)
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/TARGET_MISSING/, error.message)
    end
  end

  def test_rejects_report_bound_to_older_source_revision
    with_fixture do |root|
      path = File.join(root, Phase2UiEvidence::SOURCE_PATHS[4])
      File.write(path, File.read(path).sub("YCS Open SMS Console Prototype", "YCS Open SMS Console Prototype revision"))
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/REPORT_SOURCE_SEAL_MISMATCH/, error.message)
    end
  end

  def test_rejects_visual_target_not_attached_to_report
    with_fixture do |root|
      target = File.join(root, ".planning/phases", Phase2UiEvidence::PHASE, "EVIDENCE/OBL-DESIGN-SYSTEM-001.png")
      File.binwrite(target, File.binread(target) + "drift")
      manifest_path = manifest_path(root)
      manifest = JSON.parse(File.read(manifest_path))
      entry = manifest.fetch("entries").find { |item| item["obligation_id"] == "OBL-DESIGN-SYSTEM-001" }
      entry["sha256"] = Phase2UiEvidence.digest(target)
      entry["size"] = File.size(target)
      File.write(manifest_path, JSON.pretty_generate(manifest))
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/PNG_ATTACHMENT_CHECKSUM_MISMATCH/, error.message)
    end
  end

  def test_rejects_drifted_tested_inputs
    with_fixture do |root|
      path = File.join(root, ".planning/phases", Phase2UiEvidence::PHASE, "EVIDENCE/tested-inputs.json")
      payload = JSON.parse(File.read(path))
      payload.fetch("sources").first["sha256"] = "0" * 64
      File.write(path, JSON.pretty_generate(payload))
      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/TESTED_INPUTS_SOURCE_MISMATCH/, error.message)
    end
  end

  def test_rejects_assertion_claim_not_backed_by_report_step
    with_fixture do |root|
      target = File.join(root, ".planning/phases", Phase2UiEvidence::PHASE, "EVIDENCE/OBL-IA-ADMIN-LOGIN.json")
      payload = JSON.parse(File.read(target))
      payload.fetch("assertions") << { "id" => "fabricated-granular-pass", "status" => "PASS" }
      File.write(target, JSON.pretty_generate(payload))

      manifest_file = manifest_path(root)
      manifest = JSON.parse(File.read(manifest_file))
      entry = manifest.fetch("entries").find { |item| item["obligation_id"] == "OBL-IA-ADMIN-LOGIN" }
      entry["sha256"] = Phase2UiEvidence.digest(target)
      entry["size"] = File.size(target)
      File.write(manifest_file, JSON.pretty_generate(manifest))

      error = assert_raises(RuntimeError) { Phase2UiEvidence.validate!(root: root) }
      assert_match(/JSON_ASSERTIONS_REPORT_MISMATCH/, error.message)
    end
  end

  private

  def manifest_path(root)
    File.join(root, ".planning/phases", Phase2UiEvidence::PHASE, "EVIDENCE/evidence-manifest.json")
  end

  def with_fixture
    Dir.mktmpdir("phase2-evidence-test-") do |root|
      planning = File.join(root, ".planning")
      phases = File.join(planning, "phases")
      FileUtils.mkdir_p(phases)
      FileUtils.cp(File.join(ROOT, ".planning/PRD-OBLIGATIONS.md"), planning)
      FileUtils.cp_r(File.join(ROOT, ".planning/phases", Phase2UiEvidence::PHASE), phases)
      yield root
    end
  end
end
