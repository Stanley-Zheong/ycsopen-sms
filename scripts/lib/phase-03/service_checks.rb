#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "pathname"
require "securerandom"
require "tempfile"
require_relative "../phase-01/service_checks"

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
    SERVICE_ROOT = File.join(OWNED_ROOT, "services")
    PROVISIONER = File.join(ROOT, "scripts/provision-phase-03-softhsm")
    SOFTHSM_MANIFEST = File.join(__dir__, "softhsm-source.json")
    MYSQL_IMAGE = Phase01::ServiceChecks::MYSQL_IMAGE
    MINIO_IMAGE = "minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"
    MINIO_IMAGE_ID = "sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"
    MINIO_VERSION = "RELEASE.2025-09-07T16-13-09Z"
    OWNER_LABEL = "com.ycsopen.phase03.owner=crypto-storage-bootstrap"
    RUN_LABEL = "com.ycsopen.phase03.run"
    RUN_ID_PATTERN = /\A(?:mysql|minio|softhsm)-[0-9a-f]{12}\z/
    MINIO_NAME_PATTERN = /\Aphase03-minio-(?:minio-)?[0-9a-f]{12}\z/
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

    def validate_run_id!(run_id, service)
      unless RUN_ID_PATTERN.match?(run_id) && run_id.start_with?("#{service}-")
        raise CheckError.new("SERVICE_RUN_ID_INVALID", "run ID does not match the owned service grammar")
      end
      true
    end

    def validate_minio_identity!(identity)
      unless identity.is_a?(Hash) && identity["repo_digests"].is_a?(Array) &&
             identity["repo_digests"].include?(MINIO_IMAGE) && identity["image_id"] == MINIO_IMAGE_ID
        raise CheckError.new("MINIO_IMAGE_IDENTITY_MISMATCH", "local MinIO image is not the locked digest")
      end
      unless %w[linux/amd64 linux/arm64].include?(identity["platform"])
        raise CheckError.new("MINIO_IMAGE_PLATFORM_MISMATCH", "MinIO image platform is unsupported")
      end
      unless identity["version"] == MINIO_VERSION
        raise CheckError.new("MINIO_VERSION_MISMATCH", "MinIO release label is not the locked release")
      end
      true
    end

    def inspect_minio_image!
      stdout, = Phase01::ServiceChecks.command([
        Phase01::ServiceChecks.docker_binary, "image", "inspect", MINIO_IMAGE, "--format",
        "{{json .RepoDigests}}|{{.Id}}|{{.Os}}/{{.Architecture}}|{{index .Config.Labels \"version\"}}"
      ])
      repo_json, image_id, platform, version = stdout.strip.split("|", 4)
      identity = {
        "repo_digests" => JSON.parse(repo_json), "image_id" => image_id,
        "platform" => platform, "version" => version
      }
      validate_minio_identity!(identity)
      identity
    rescue JSON::ParserError
      raise CheckError.new("MINIO_IMAGE_IDENTITY_MALFORMED", "MinIO image identity was malformed")
    end

    def start_service!(service, run_id:, credentials: {})
      service = service.to_sym
      validate_run_id!(run_id, service)
      case service
      when :mysql then start_mysql!(run_id, credentials)
      when :minio then start_minio!(run_id, credentials)
      when :softhsm then start_softhsm!(run_id)
      else raise CheckError.new("SERVICE_ARGUMENT_INVALID", "unsupported Phase 3 service")
      end
    rescue StandardError
      cleanup_service!(service, run_id: run_id) if %i[mysql minio softhsm].include?(service)
      raise
    end

    def start_mysql!(run_id, credentials)
      delegated = "p03#{run_id}"
      identity = Phase01::ServiceChecks.start_service!(:mysql, run_id: delegated, credentials: credentials)
      identity.merge(
        "schema_version" => "phase03-service-v1", "service" => "mysql", "run_id" => run_id,
        "image_reference" => MYSQL_IMAGE, "delegated_run_id" => delegated
      )
    end

    def start_minio!(run_id, credentials)
      identity = inspect_minio_image!
      access_key = credentials.fetch(:access_key)
      secret_key = credentials.fetch(:secret_key)
      unless access_key.match?(/\Aphase03[0-9a-f]{12}\z/) && secret_key.length >= 32
        raise CheckError.new("MINIO_TEST_CREDENTIAL_INVALID", "ephemeral MinIO credentials do not satisfy the test contract")
      end
      container = "phase03-minio-#{run_id}"
      env_file = Tempfile.new(["phase03-minio-", ".env"])
      begin
        env_file.chmod(0o600)
        env_file.write("MINIO_ROOT_USER=#{access_key}\nMINIO_ROOT_PASSWORD=#{secret_key}\n")
        env_file.flush
        Phase01::ServiceChecks.command([
          Phase01::ServiceChecks.docker_binary, "run", "--detach", "--rm", "--name", container,
          "--label", OWNER_LABEL, "--label", "#{RUN_LABEL}=#{run_id}", "--env-file", env_file.path,
          "--publish", "127.0.0.1::9000", "--tmpfs", "/data:rw,nosuid,nodev,noexec,size=256m",
          MINIO_IMAGE, "server", "/data", "--address", ":9000", "--console-address", ":9001"
        ])
      ensure
        env_file.close!
      end
      verify_minio_container!(container, run_id)
      wait_for_minio!(container)
      functional_minio_probe!(container)
      port = Phase01::ServiceChecks.published_port!(container, 9000)
      identity.merge(
        "schema_version" => "phase03-service-v1", "status" => "READY", "service" => "minio",
        "run_id" => run_id, "container_name" => container, "host" => "127.0.0.1", "port" => port,
        "image_reference" => MINIO_IMAGE, "image_digest" => MINIO_IMAGE_ID.delete_prefix("sha256:")
      )
    end

    def verify_minio_container!(container, run_id)
      mounts, = Phase01::ServiceChecks.command([
        Phase01::ServiceChecks.docker_binary, "container", "inspect", container, "--format", "{{json .Mounts}}"
      ])
      persistent = JSON.parse(mounts).select { |mount| %w[volume bind].include?(mount["Type"]) }
      raise CheckError.new("SERVICE_PERSISTENT_MOUNT_FORBIDDEN", "MinIO has a persistent mount") unless persistent.empty?

      identity, = Phase01::ServiceChecks.command([
        Phase01::ServiceChecks.docker_binary, "container", "inspect", container, "--format",
        "{{json .Config.Image}}|{{index .Config.Labels \"#{RUN_LABEL}\"}}|{{.Image}}"
      ])
      reference_json, observed_run, image_id = identity.strip.split("|", 3)
      unless JSON.parse(reference_json) == MINIO_IMAGE && observed_run == run_id && image_id == MINIO_IMAGE_ID
        raise CheckError.new("MINIO_CONTAINER_IDENTITY_MISMATCH", "running MinIO container identity differs from the locked image")
      end
      true
    rescue JSON::ParserError
      raise CheckError.new("MINIO_CONTAINER_IDENTITY_MALFORMED", "running MinIO identity was malformed")
    end

    def wait_for_minio!(container)
      60.times do
        stdout, _stderr, status = Phase01::ServiceChecks.command([
          Phase01::ServiceChecks.docker_binary, "exec", container, "curl", "--fail", "--silent",
          "http://127.0.0.1:9000/minio/health/ready"
        ], allow_failure: true, timeout: 5)
        return true if status.zero? && stdout.empty?
        sleep 1
      end
      raise CheckError.new("MINIO_UNAVAILABLE", "MinIO authenticated fixture did not become ready")
    end

    def functional_minio_probe!(container)
      bucket = "phase03-probe-#{SecureRandom.hex(6)}"
      script = <<~'SH'.strip
        set -eu
        export MC_CONFIG_DIR=/tmp/phase03-mc
        mc alias set phase03 http://127.0.0.1:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
        mc mb "phase03/$PHASE03_BUCKET" >/dev/null
        printf 'phase03-synthetic-object' | mc pipe "phase03/$PHASE03_BUCKET/probe" >/dev/null
        test "$(mc cat "phase03/$PHASE03_BUCKET/probe")" = "phase03-synthetic-object"
        mc rm "phase03/$PHASE03_BUCKET/probe" >/dev/null
        mc rb "phase03/$PHASE03_BUCKET" >/dev/null
      SH
      _stdout, _stderr, status = Phase01::ServiceChecks.command([
        Phase01::ServiceChecks.docker_binary, "exec", "--env", "PHASE03_BUCKET=#{bucket}", container,
        "/bin/sh", "-c", script
      ], allow_failure: true, timeout: 30)
      raise CheckError.new("MINIO_FUNCTIONAL_PROBE_FAILED", "authenticated MinIO object lifecycle failed") unless status.zero?

      true
    end

    def start_softhsm!(run_id)
      destination = File.join(SERVICE_ROOT, run_id)
      stdout, = Phase01::ServiceChecks.command([
        PROVISIONER, "--manifest", SOFTHSM_MANIFEST, "--destination", destination,
        "--provision", "--initialize-token", "--preflight"
      ], timeout: 600)
      result = JSON.parse(stdout)
      unless result["status"] == "PASS" && result.dig("runtime", "mechanism_count") == 4 &&
             result.dig("runtime", "nonextractable_key_count") == 2
        raise CheckError.new("SOFTHSM_PREFLIGHT_INCOMPLETE", "provisioner did not return the real preflight contract")
      end
      handoff = read_softhsm_handoff!(destination)
      {
        "schema_version" => "phase03-service-v1", "status" => "READY", "service" => "softhsm",
        "run_id" => run_id, "version" => handoff.fetch("version"),
        "source_sha256" => handoff.fetch("source_sha256"),
        "library_sha256" => sha256_file(handoff.fetch("library")),
        "cli_sha256" => sha256_file(handoff.fetch("cli")),
        "mechanism_count" => 4, "nonextractable_key_count" => 2
      }
    rescue JSON::ParserError, KeyError
      raise CheckError.new("SOFTHSM_HANDOFF_INVALID", "SoftHSM handoff did not match the closed schema")
    end

    def read_softhsm_handoff!(destination)
      handoff_path = File.join(destination, "runtime/handoff.json")
      unless File.file?(handoff_path) && !File.symlink?(handoff_path) && (File.stat(handoff_path).mode & 0o077).zero?
        raise CheckError.new("SOFTHSM_HANDOFF_PERMISSION_INVALID", "SoftHSM handoff is missing or not private")
      end
      handoff = JSON.parse(File.binread(handoff_path))
      expected_keys = %w[cli config header library pin_source schema_version slot source_sha256 token_dir version]
      unless handoff.is_a?(Hash) && handoff.keys.sort == expected_keys &&
             handoff["schema_version"] == "ycs-softhsm-handoff/v1" &&
             handoff["version"] == SOFTHSM_VERSION && handoff["source_sha256"] == SOFTHSM_ARCHIVE_SHA256
        raise CheckError.new("SOFTHSM_HANDOFF_INVALID", "SoftHSM handoff identity differs from the locked source")
      end
      handoff
    rescue JSON::ParserError
      raise CheckError.new("SOFTHSM_HANDOFF_INVALID", "SoftHSM handoff is malformed")
    end

    def cleanup_service!(service, run_id:)
      service = service.to_sym
      validate_run_id!(run_id, service)
      case service
      when :mysql
        Phase01::ServiceChecks.cleanup_service!(:mysql, run_id: "p03#{run_id}")
        Phase01::ServiceChecks.assert_cleaned!(:mysql, run_id: "p03#{run_id}")
      when :minio
        container = "phase03-minio-#{run_id}"
        raise CheckError.new("SERVICE_CLEANUP_TARGET_INVALID", "MinIO cleanup target is invalid") unless MINIO_NAME_PATTERN.match?(container)
        Phase01::ServiceChecks.command([Phase01::ServiceChecks.docker_binary, "rm", "--force", "--volumes", container],
                                       allow_failure: true, timeout: 30)
        _stdout, _stderr, status = Phase01::ServiceChecks.command([
          Phase01::ServiceChecks.docker_binary, "container", "inspect", container
        ], allow_failure: true)
        raise CheckError.new("SERVICE_CLEANUP_FAILED", "MinIO container remains") if status.zero?
      when :softhsm
        destination = File.join(SERVICE_ROOT, run_id)
        Phase01::ServiceChecks.command([
          PROVISIONER, "--manifest", SOFTHSM_MANIFEST, "--destination", destination, "--cleanup"
        ])
        validate_cleanup!(destination)
      else
        raise CheckError.new("SERVICE_ARGUMENT_INVALID", "unsupported Phase 3 service")
      end
      true
    end

    def assert_clean!(service = nil)
      services = service ? [service.to_sym] : %i[mysql minio softhsm]
      if services.include?(:mysql)
        containers, = Phase01::ServiceChecks.command([
          Phase01::ServiceChecks.docker_binary, "container", "ls", "--all", "--format", "{{.Names}}"
        ])
        networks, = Phase01::ServiceChecks.command([
          Phase01::ServiceChecks.docker_binary, "network", "ls", "--format", "{{.Name}}"
        ])
        if containers.lines.any? { |line| line.start_with?("phase01-mysql-p03mysql-") } ||
           networks.lines.any? { |line| line.start_with?("phase01-net-p03mysql-") }
          raise CheckError.new("SERVICE_CLEANUP_FAILED", "Phase 3 MySQL resource remains")
        end
      end
      if services.include?(:minio)
        containers, = Phase01::ServiceChecks.command([
          Phase01::ServiceChecks.docker_binary, "container", "ls", "--all", "--filter", "label=#{OWNER_LABEL}",
          "--format", "{{.Names}}"
        ])
        raise CheckError.new("SERVICE_CLEANUP_FAILED", "Phase 3 MinIO resource remains") unless containers.strip.empty?
      end
      if services.include?(:softhsm)
        residue = Dir[File.join(SERVICE_ROOT, "softhsm-*")].select { |path| File.exist?(path) || File.symlink?(path) }
        raise CheckError.new("SOFTHSM_CLEANUP_FAILED", "Phase 3 SoftHSM resource remains") unless residue.empty?
      end
      true
    end

    def cli_pairs(argv)
      command_name = argv.shift
      options = {}
      until argv.empty?
        flag = argv.shift
        if flag == "--all"
          options[:all] = true
          next
        end
        value = argv.shift
        raise CheckError.new("SERVICE_ARGUMENT_INVALID", "arguments must be fixed flag/value pairs") unless flag&.start_with?("--") && value
        options[flag.delete_prefix("--").tr("-", "_").to_sym] = value
      end
      [command_name, options]
    end

    def run_cli(argv, out: $stdout, err: $stderr)
      command_name, options = cli_pairs(argv.dup)
      case command_name
      when "start"
        service = options.fetch(:service).to_sym
        run_id = options.fetch(:run_id)
        credentials = case service
                      when :mysql
                        { user: ENV.fetch("PHASE03_MYSQL_USER"), password: ENV.fetch("PHASE03_MYSQL_PASSWORD"),
                          root_password: ENV.fetch("PHASE03_MYSQL_ROOT_PASSWORD") }
                      when :minio
                        { access_key: ENV.fetch("PHASE03_MINIO_ACCESS_KEY"), secret_key: ENV.fetch("PHASE03_MINIO_SECRET_KEY") }
                      else {}
                      end
        out.puts(JSON.generate(start_service!(service, run_id: run_id, credentials: credentials)))
      when "stop"
        service = options.fetch(:service).to_sym
        run_id = options.fetch(:run_id)
        cleanup_service!(service, run_id: run_id)
        out.puts(JSON.generate("status" => "CLEANED", "service" => service.to_s, "run_id" => run_id))
      when "assert-clean"
        service = options[:all] ? nil : options.fetch(:service)
        assert_clean!(service)
        out.puts(JSON.generate("status" => "PASS", "cleanup" => service || "all"))
      else
        raise CheckError.new("SERVICE_ARGUMENT_INVALID", "expected start, stop, or assert-clean")
      end
      0
    rescue KeyError, ArgumentError => e
      err.puts(JSON.generate("status" => "FAIL", "error_id" => "SERVICE_ARGUMENT_INVALID", "diagnostic" => sanitize(e.message)))
      64
    rescue CheckError, Phase01::ServiceChecks::CheckError => e
      error_id = e.respond_to?(:error_id) ? e.error_id : "SERVICE_FAILURE"
      status = error_id.match?(/UNAVAILABLE|TIMEOUT|RUNNER_PLATFORM|MISSING/) ? "BLOCKED" : "FAIL"
      err.puts(JSON.generate("status" => status, "error_id" => error_id, "diagnostic" => sanitize(e.message)))
      2
    end
  end
end

if $PROGRAM_NAME == __FILE__
  exit Phase03::ServiceChecks.run_cli(ARGV)
end
