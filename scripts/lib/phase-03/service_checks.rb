#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "pathname"

module Phase03
  module ServiceChecks
    class CheckError < StandardError
      attr_reader :error_id

      def initialize(error_id, message)
        @error_id = error_id
        super(message)
      end
    end

    SOFTHSM_SCHEMA = "ycs-softhsm-source/v1"
    SOFTHSM_VERSION = "2.7.0"
    SOFTHSM_TAG = "2.7.0"
    SOFTHSM_COMMIT = "13e6e86"
    SOFTHSM_URL = "https://codeload.github.com/softhsm/SoftHSMv2/tar.gz/refs/tags/2.7.0"
    SOFTHSM_ARCHIVE_SHA256 = "be14a5820ec457eac5154462ffae51ba5d8a643f6760514d4b4b83a77be91573"
    SOFTHSM_ARCHIVE_ROOT = "SoftHSMv2-2.7.0"
    REQUIRED_MECHANISMS = %w[
      CKM_AES_KEY_GEN CKM_AES_GCM CKM_GENERIC_SECRET_KEY_GEN CKM_SHA256_HMAC
    ].freeze
    REQUIRED_KEY_ATTRIBUTES = {
      "CKA_TOKEN" => true,
      "CKA_PRIVATE" => true,
      "CKA_SENSITIVE" => true,
      "CKA_EXTRACTABLE" => false
    }.freeze
    BUILD_ARGUMENTS = %w[
      -DCMAKE_BUILD_TYPE=Release
      -DBUILD_TESTS=OFF
      -DENABLE_P11_KIT=OFF
      -DENABLE_GOST=OFF
      -DENABLE_FIPS=OFF
      -DENABLE_STATIC=OFF
      -DWITH_OBJECTSTORE_BACKEND_DB=OFF
      -DWITH_MIGRATE=OFF
      -DWITH_CRYPTO_BACKEND=openssl
    ].freeze
    ROOT = File.expand_path("../../..", __dir__)
    OWNED_ROOT = File.join(ROOT, "core/target/phase03")
    REDACTION_PATTERNS = [
      /(?i)(?:so[-_ ]?pin|user[-_ ]?pin|password|secret)\s*[=:]\s*\S+/,
      /(?i)(?:token|key)[-_ ]?(?:value|bytes)\s*[=:]\s*\S+/
    ].freeze

    module_function

    def load_manifest!(path)
      raise CheckError.new("SOFTHSM_MANIFEST_PATH_INVALID", "manifest must be a regular non-symlink file") unless
        File.file?(path) && !File.symlink?(path)

      manifest = JSON.parse(File.binread(path))
      validate_manifest!(manifest)
      manifest
    rescue JSON::ParserError
      raise CheckError.new("SOFTHSM_MANIFEST_MALFORMED", "source manifest is not valid JSON")
    end

    def validate_manifest!(manifest)
      expected = {
        "schema_version" => SOFTHSM_SCHEMA,
        "product" => "SoftHSMv2",
        "version" => SOFTHSM_VERSION,
        "tag" => SOFTHSM_TAG,
        "commit" => SOFTHSM_COMMIT,
        "archive_url" => SOFTHSM_URL,
        "archive_sha256" => SOFTHSM_ARCHIVE_SHA256,
        "archive_root" => SOFTHSM_ARCHIVE_ROOT,
        "build_system" => "cmake",
        "build_arguments" => BUILD_ARGUMENTS,
        "required_mechanisms" => REQUIRED_MECHANISMS,
        "required_key_attributes" => REQUIRED_KEY_ATTRIBUTES
      }
      unless manifest.is_a?(Hash) && manifest.keys.sort == expected.keys.sort
        raise CheckError.new("SOFTHSM_MANIFEST_SCHEMA_MISMATCH", "source manifest has missing or unknown fields")
      end
      expected.each do |field, value|
        next if manifest[field] == value

        error_id = case field
                   when "archive_sha256" then "SOFTHSM_SOURCE_DIGEST_MISMATCH"
                   when "version" then "SOFTHSM_VERSION_MISMATCH"
                   when "commit", "tag", "archive_url" then "SOFTHSM_SOURCE_IDENTITY_MISMATCH"
                   when "required_mechanisms" then "SOFTHSM_MECHANISM_CONTRACT_MISMATCH"
                   when "required_key_attributes" then "SOFTHSM_ATTRIBUTE_CONTRACT_MISMATCH"
                   else "SOFTHSM_MANIFEST_SCHEMA_MISMATCH"
                   end
        raise CheckError.new(error_id, "source manifest field #{field} differs from the locked contract")
      end
      true
    end

    def owned_destination!(destination)
      absolute = File.expand_path(destination, ROOT)
      parent = File.dirname(absolute)
      existing = parent
      existing = File.dirname(existing) until File.exist?(existing) || existing == File.dirname(existing)
      real_existing = File.realpath(existing)
      owned_root = File.expand_path(OWNED_ROOT)
      unless real_existing == owned_root || real_existing.start_with?(owned_root + File::SEPARATOR) ||
             (owned_root.start_with?(real_existing + File::SEPARATOR) && absolute.start_with?(owned_root + File::SEPARATOR))
        raise CheckError.new("SOFTHSM_DESTINATION_OUTSIDE_OWNER", "destination is outside core/target/phase03")
      end
      cursor = absolute
      until cursor == real_existing
        if File.symlink?(cursor)
          raise CheckError.new("SOFTHSM_DESTINATION_SYMLINK", "destination contains a symbolic link")
        end
        cursor = File.dirname(cursor)
      end
      unless absolute.start_with?(owned_root + File::SEPARATOR)
        raise CheckError.new("SOFTHSM_DESTINATION_OUTSIDE_OWNER", "destination must be below core/target/phase03")
      end
      absolute
    rescue Errno::ENOENT, Errno::EACCES
      raise CheckError.new("SOFTHSM_DESTINATION_INVALID", "destination could not be canonicalized")
    end

    def verify_archive_digest!(path, expected = SOFTHSM_ARCHIVE_SHA256)
      observed = Digest::SHA256.file(path).hexdigest
      unless observed == expected
        raise CheckError.new("SOFTHSM_SOURCE_DIGEST_MISMATCH", "downloaded archive does not match the locked SHA-256")
      end
      observed
    end

    def validate_runtime_version!(output)
      unless output.to_s.strip.match?(/\A(?:SoftHSM(?:v2)?\s+)?2\.7\.0\z/i)
        raise CheckError.new("SOFTHSM_VERSION_MISMATCH", "runtime version is not exactly 2.7.0")
      end
      true
    end

    def validate_preflight_report!(report)
      unless report.is_a?(Hash) && report["token_initialized"] == true
        raise CheckError.new("SOFTHSM_TOKEN_NOT_INITIALIZED", "run-owned token initialization was not proven")
      end
      mechanisms = Array(report["mechanisms"])
      missing = REQUIRED_MECHANISMS - mechanisms
      unless missing.empty?
        raise CheckError.new("SOFTHSM_MECHANISM_MISSING", "required mechanism count is incomplete")
      end
      keys = report["keys"]
      unless keys.is_a?(Array) && keys.length == 2 && keys.map { |key| key["kind"] }.sort == %w[AES HMAC]
        raise CheckError.new("SOFTHSM_KEY_SET_INVALID", "exactly one AES and one HMAC key are required")
      end
      keys.each do |key|
        REQUIRED_KEY_ATTRIBUTES.each do |attribute, expected|
          unless key[attribute] == expected
            raise CheckError.new("SOFTHSM_KEY_ATTRIBUTE_MISMATCH", "#{key['kind']} key attribute #{attribute} is invalid")
          end
        end
      end
      true
    end

    def validate_cleanup!(destination)
      return true unless File.exist?(destination) || File.symlink?(destination)

      raise CheckError.new("SOFTHSM_CLEANUP_FAILED", "run-owned SoftHSM destination remains")
    end

    def sanitize(value)
      sanitized = value.to_s
      REDACTION_PATTERNS.each { |pattern| sanitized = sanitized.gsub(pattern, "[REDACTED]") }
      sanitized.gsub(%r{(?:/[^\s]+)+}, "[PATH_REDACTED]")
    end

    def sha256_file(path)
      Digest::SHA256.file(path).hexdigest
    end
  end
end

if $PROGRAM_NAME == __FILE__
  warn(JSON.generate("status" => "FAIL", "error_id" => "SERVICE_ARGUMENT_INVALID",
                     "diagnostic" => "service command implementation is installed by Task 2"))
  exit 64
end
