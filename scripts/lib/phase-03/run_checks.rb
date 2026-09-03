#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "pathname"
require "securerandom"
require "time"
require "timeout"

require_relative "../../../.planning/tools/phase3-crypto-evidence"

# Fixed Phase-3 verification registry. Expensive real-service work is intentionally executed only
# here, once at the top-level phase boundary. Focused plan checks remain useful while editing, but
# are not recursively replayed by later integration tests.
module Phase03RunChecks
  class ConfigurationError < StandardError; end

  ROOT = File.expand_path("../../..", __dir__)
  DEFAULT_RESULT_ROOT = "core/target/phase03/results"
  PHASE_DIR = ".planning/phases/03-crypto-storage-bootstrap"
  INVENTORY = "core/src/main/resources/security/protected-data-inventory.json"
  JAVA_LEAK_RESULT = "core/target/phase03/leak-integration-report.json"
  ARTIFACT_LEAK_RESULT = "core/target/phase03/artifact-leak-result.json"
  MIGRATION_INVENTORY = "core/target/phase03/migration-inventory.json"
  RESULT_SCHEMA = "phase03-root-lane-result-v1"
  AGGREGATE_SCHEMA = "phase03-root-aggregate-v1"
  SHA256 = /\A[0-9a-f]{64}\z/

  OBLIGATIONS = Phase3CryptoEvidence::OBLIGATIONS.keys.freeze
  CASES = Phase3CryptoEvidence::OBLIGATIONS.values.map { |row| row.fetch("case_id") }.freeze

  REAL_TESTS = %w[
    Phase03FixturePreflightTest
    Phase03ProtectedPersistenceIntegrationTest
    Phase03Pkcs11IntegrationTest
    Phase03MigrationIntegrationTest
    Phase03ObjectStorageIntegrationTest
    Phase03LeakScanIntegrationTest
    Phase03RotationRecoveryIntegrationTest
  ].freeze

  # These tests prove that the corrected security components are reachable from shipped
  # production composition, not merely constructible inside broad integration fixtures.
  PRODUCTION_REACHABILITY_TESTS = %w[
    ObjectStorageConfigurationTest
    ProductionMigrationCommandServicesFactoryTest
    VersionedKeyDescriptorRegistryTest
    BlacklistEntryProtectionAdapterTest
  ].freeze

  CHECKS = [
    {
      "id" => "default-maven", "layer" => "unit", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "mvn", "-f", "core/pom.xml", "test"],
      "timeout_seconds" => 1_800, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "flyway-owner-fixtures", "layer" => "validator", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "python3", "skills/flyway-migration/tests/test_next_flyway_version.py"],
      "timeout_seconds" => 180, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "flyway-owner-selection", "layer" => "validator", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "python3", "skills/flyway-migration/scripts/next_flyway_version.py",
                 "--owner", "crypto-storage-bootstrap", "--check", "V1201"],
      "timeout_seconds" => 120, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "planning-validator-fixtures", "layer" => "validator", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/test-planning-validators.rb"],
      "timeout_seconds" => 600, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "crypto-evidence-fixtures", "layer" => "validator", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/test-phase-03-crypto-evidence.rb"],
      "timeout_seconds" => 600, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "protected-inventory-fixtures", "layer" => "validator", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/test-phase-03-protected-inventory.rb"],
      "timeout_seconds" => 300, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "service-contract-fixtures", "layer" => "validator", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-03/test_service_checks.rb"],
      "timeout_seconds" => 600, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "artifact-scanner-fixtures", "layer" => "validator", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/test-scan-phase-03-artifacts.rb"],
      "timeout_seconds" => 300, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "source-contract-audit", "layer" => "static", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-03/run_checks.rb", "--internal-source-audit"],
      "timeout_seconds" => 120, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "production-reachability", "layer" => "composition", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "mvn", "-f", "core/pom.xml",
                 "-Dtest=#{PRODUCTION_REACHABILITY_TESTS.join(',')}", "test"],
      "timeout_seconds" => 900, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "real-service-integration", "layer" => "integration", "mode" => "REAL",
      "argv" => ["/usr/bin/env", "mvn", "-f", "core/pom.xml", "-Pphase03-integration",
                 "-Dtest=#{REAL_TESTS.join(',')}", "test"],
      "timeout_seconds" => 7_200, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "protected-inventory-acceptance", "layer" => "database", "mode" => "REAL",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/validate-phase-03-protected-inventory.rb",
                 "--manifest", INVENTORY,
                 "--schema", "core/src/main/resources/db/migration/V1__init_schema.sql",
                 "--source-root", "core/src/main/java", "--mysql-evidence", MIGRATION_INVENTORY,
                 "--acceptance"],
      "timeout_seconds" => 300, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "durable-artifact-leak-scan", "layer" => "security", "mode" => "DETERMINISTIC",
      "argv" => ["/usr/bin/env", "ruby", ".planning/tools/scan-phase-03-artifacts.rb",
                 "--phase-dir", PHASE_DIR, "--generated-root", "core/target/phase03",
                 "--output", ARTIFACT_LEAK_RESULT],
      "timeout_seconds" => 300, "obligation_ids" => OBLIGATIONS
    },
    {
      "id" => "fixture-cleanup", "layer" => "integration", "mode" => "REAL",
      "argv" => ["/usr/bin/env", "ruby", "scripts/lib/phase-03/service_checks.rb",
                 "assert-clean", "--all"],
      "timeout_seconds" => 300, "obligation_ids" => OBLIGATIONS
    }
  ].freeze

  CHILD_BINDINGS = {
    "OBL-CRYPTO-STORAGE-001" => {
      "check_id" => "phase03-protected-persistence-integration", "layer" => "database",
      "adapters" => %w[java21-sunpkcs11 mysql-8],
      "lanes" => %w[default-maven source-contract-audit production-reachability real-service-integration
                    protected-inventory-acceptance durable-artifact-leak-scan fixture-cleanup]
    },
    "OBL-CRYPTO-STORAGE-002" => {
      "check_id" => "phase03-object-storage-integration", "layer" => "security",
      "adapters" => %w[java21-sunpkcs11 minio mysql-8],
      "lanes" => %w[default-maven source-contract-audit production-reachability real-service-integration
                    durable-artifact-leak-scan fixture-cleanup]
    },
    "OBL-CRYPTO-STORAGE-003" => {
      "check_id" => "phase03-pkcs11-fault-integration", "layer" => "fault",
      "adapters" => %w[java21-sunpkcs11 softhsm-2.7.0],
      "lanes" => %w[default-maven service-contract-fixtures source-contract-audit production-reachability
                    real-service-integration durable-artifact-leak-scan fixture-cleanup]
    },
    "OBL-CRYPTO-STORAGE-004" => {
      "check_id" => "phase03-migration-integration", "layer" => "database",
      "adapters" => %w[java21-sunpkcs11 mysql-8 softhsm-2.7.0],
      "lanes" => %w[default-maven flyway-owner-fixtures flyway-owner-selection
                    planning-validator-fixtures protected-inventory-fixtures source-contract-audit
                    production-reachability
                    real-service-integration protected-inventory-acceptance
                    durable-artifact-leak-scan fixture-cleanup]
    }
  }.freeze

  module_function

  def canonicalize(value)
    case value
    when Hash
      value.keys.map(&:to_s).sort.to_h { |key| [key, canonicalize(value[key] || value[key.to_sym])] }
    when Array then value.map { |item| canonicalize(item) }
    else value
    end
  end

  def canonical_json(value)
    JSON.generate(canonicalize(value))
  end

  def digest(value)
    Digest::SHA256.hexdigest(canonical_json(value))
  end

  def atomic_json(path, value)
    FileUtils.mkdir_p(File.dirname(path))
    temporary = "#{path}.#{Process.pid}.#{SecureRandom.hex(6)}.tmp"
    File.open(temporary, File::WRONLY | File::CREAT | File::EXCL, 0o600) do |file|
      file.write(JSON.pretty_generate(canonicalize(value)) + "\n")
      file.flush
      file.fsync
    end
    File.chmod(0o644, temporary)
    File.rename(temporary, path)
  ensure
    File.delete(temporary) if defined?(temporary) && temporary && File.exist?(temporary)
  end

  def registry_contract!(checks = CHECKS)
    raise ConfigurationError, "CHECK_REGISTRY_EMPTY" unless checks.is_a?(Array) && !checks.empty?
    ids = checks.map { |row| row.fetch("id") }
    raise ConfigurationError, "CHECK_ID_DUPLICATE" unless ids.uniq.length == ids.length
    checks.each do |row|
      expected = %w[id layer mode argv timeout_seconds obligation_ids]
      raise ConfigurationError, "CHECK_FIELDS_INVALID: #{row['id']}" unless row.keys.sort == expected.sort
      raise ConfigurationError, "CHECK_ID_INVALID" unless row.fetch("id").match?(/\A[a-z0-9][a-z0-9-]*\z/)
      raise ConfigurationError, "CHECK_MODE_INVALID: #{row['id']}" unless %w[DETERMINISTIC REAL].include?(row.fetch("mode"))
      argv = row.fetch("argv")
      raise ConfigurationError, "CHECK_ARGV_INVALID: #{row['id']}" unless argv.is_a?(Array) && argv.all? { |part| part.is_a?(String) && !part.empty? }
      raise ConfigurationError, "CHECK_TIMEOUT_INVALID: #{row['id']}" unless row.fetch("timeout_seconds").is_a?(Integer) && row.fetch("timeout_seconds").positive?
      raise ConfigurationError, "CHECK_OBLIGATIONS_INVALID: #{row['id']}" unless (row.fetch("obligation_ids") - OBLIGATIONS).empty?
    end
    required = %w[default-maven production-reachability real-service-integration protected-inventory-acceptance
                  durable-artifact-leak-scan fixture-cleanup source-contract-audit]
    missing = required - ids
    raise ConfigurationError, "CHECK_REQUIRED_LANES_MISSING: #{missing.join(',')}" unless missing.empty?
    real = checks.find { |row| row.fetch("id") == "real-service-integration" }
    command = real.fetch("argv").join(" ")
    missing_tests = REAL_TESTS.reject { |name| command.include?(name) }
    raise ConfigurationError, "CHECK_REAL_TESTS_MISSING: #{missing_tests.join(',')}" unless missing_tests.empty?
    reachability = checks.find { |row| row.fetch("id") == "production-reachability" }
    reachability_command = reachability.fetch("argv").join(" ")
    missing_reachability_tests = PRODUCTION_REACHABILITY_TESTS.reject do |name|
      reachability_command.include?(name)
    end
    unless missing_reachability_tests.empty?
      raise ConfigurationError,
            "CHECK_PRODUCTION_REACHABILITY_TESTS_MISSING: #{missing_reachability_tests.join(',')}"
    end
    child_lanes = CHILD_BINDINGS.values.flat_map { |row| row.fetch("lanes") }.uniq
    missing_child_lanes = child_lanes - ids
    raise ConfigurationError, "CHECK_CHILD_LANES_MISSING: #{missing_child_lanes.join(',')}" unless missing_child_lanes.empty?
    expected_real = %w[fixture-cleanup protected-inventory-acceptance real-service-integration]
    actual_real = checks.select { |row| row.fetch("mode") == "REAL" }.map { |row| row.fetch("id") }.sort
    raise ConfigurationError, "CHECK_REAL_IDENTITY_SET_INVALID" unless actual_real == expected_real.sort
    raise ConfigurationError, "CHECK_SHELL_COMMAND_FORBIDDEN" if checks.any? do |row|
      row.fetch("argv").first.match?(%r{(?:^|/)(?:sh|bash|zsh)\z})
    end
    raise ConfigurationError, "CHECK_OBLIGATION_SET_INVALID" unless checks.all? do |row|
      row.fetch("obligation_ids").sort == OBLIGATIONS.sort
    end
    true
  end

  def subject_paths(root)
    patterns = [
      "core/pom.xml", "core/src/main/**/*", "core/src/test/**/*", "core/docs/**/*",
      "docs/**/*", "scripts/lib/phase-03/**/*", "scripts/verify-phase-03",
      ".planning/tools/*phase-03*", ".planning/tools/phase3-crypto-evidence.rb",
      "#{PHASE_DIR}/**/*", "skills/flyway-migration/**/*"
    ]
    paths = patterns.flat_map { |pattern| Dir.glob(File.join(root, pattern), File::FNM_DOTMATCH) }
    paths.select { |path| File.file?(path) && !File.symlink?(path) }
         .map { |path| Pathname(path).relative_path_from(Pathname(root)).to_s }
         .reject do |path|
           basename = File.basename(path)
           producer_output = path.start_with?("#{PHASE_DIR}/EVIDENCE/") &&
             (basename == "tested-inputs.json" || basename == "evidence-manifest.json" ||
              basename.match?(/\AOBL-CRYPTO-STORAGE-00[1-4]\.json\z/))
           mutable_phase_record = path.start_with?("#{PHASE_DIR}/") &&
             (basename.match?(/(?:SUMMARY|VERIFICATION|REVIEW)\.md\z/) ||
              %w[TODO.md ITERATIONS.md DECISIONS.md TEST-MATRIX.md].include?(basename) ||
              producer_output)
           path.start_with?("core/target/") || path.include?("/.DS_Store") || mutable_phase_record
         end
         .uniq.sort
  end

  def build_subject(root)
    inputs = subject_paths(root).map do |relative|
      path = File.join(root, relative)
      stat = File.stat(path)
      role = if relative.match?(%r{(?:^|/)(?:test|tests)(?:/|_)}) || relative.end_with?("Test.java")
               "test"
             elsif relative.end_with?(".md", ".json")
               "contract"
             elsif relative.end_with?(".yml", ".yaml", ".xml")
               "config"
             elsif relative.include?("validate-") || relative.include?("scanner")
               "validator"
             else
               "implementation"
             end
      {
        "path" => relative, "mode" => format("%06o", stat.mode),
        "sha256" => Digest::SHA256.file(path).hexdigest, "role" => role
      }
    end
    raise ConfigurationError, "TESTED_INPUTS_EMPTY" if inputs.empty?
    {
      "schema_version" => Phase3CryptoEvidence::TESTED_INPUTS_SCHEMA,
      "phase" => Phase3CryptoEvidence::PHASE,
      "owner" => Phase3CryptoEvidence::OWNER,
      "inputs" => inputs
    }
  end

  def contained_result_root(root, relative)
    path = Pathname(relative)
    raise ConfigurationError, "RESULT_ROOT_INVALID" if path.absolute? || path.cleanpath.to_s != relative || path.each_filename.include?("..")
    expected = Pathname(root).join("core/target/phase03").cleanpath
    candidate = Pathname(root).join(relative).cleanpath
    raise ConfigurationError, "RESULT_ROOT_OUTSIDE_PHASE" unless candidate.to_s.start_with?(expected.to_s + File::SEPARATOR)
    current = candidate
    until current == Pathname(root)
      raise ConfigurationError, "RESULT_ROOT_SYMLINK" if current.exist? && current.lstat.symlink?
      current = current.parent
    end
    candidate.to_s
  end

  def execute(root, definition, environment = {})
    started = Time.now.utc
    stdout = ""
    stderr = ""
    status = "BLOCKED"
    exit_code = nil
    error_id = nil
    begin
      Open3.popen3(environment, *definition.fetch("argv"), chdir: root, pgroup: true) do |stdin, out, err, wait|
        stdin.close
        out_reader = Thread.new { bounded_capture(out) }
        err_reader = Thread.new { bounded_capture(err) }
        begin
          Timeout.timeout(definition.fetch("timeout_seconds")) { wait.join }
          process = wait.value
          stdout = out_reader.value.to_s.byteslice(0, 1_048_576).to_s
          stderr = err_reader.value.to_s.byteslice(0, 1_048_576).to_s
          if process.signaled?
            error_id = "CHILD_INTERRUPTED"
          elsif process.exitstatus.zero?
            status = "PASS"
            exit_code = 0
          elsif process.exitstatus == 75
            error_id = "CHILD_BLOCKED"
            exit_code = 75
          else
            status = "FAIL"
            error_id = "CHILD_NONZERO"
            exit_code = process.exitstatus
          end
        rescue Timeout::Error
          error_id = "CHILD_TIMEOUT"
          Process.kill("TERM", -wait.pid) rescue nil
          wait.join(2)
          Process.kill("KILL", -wait.pid) rescue nil unless wait.join(0)
          wait.join
          stdout = out_reader.value.to_s.byteslice(0, 1_048_576).to_s
          stderr = err_reader.value.to_s.byteslice(0, 1_048_576).to_s
        ensure
          out_reader.kill if out_reader&.alive?
          err_reader.kill if err_reader&.alive?
        end
      end
    rescue Errno::ENOENT, Errno::EACCES
      error_id = "CHILD_EXECUTABLE_UNAVAILABLE"
    end
    combined = (stdout + "\0" + stderr).encode("UTF-8", invalid: :replace, undef: :replace, replace: "?")
    base = {
      "schema_version" => RESULT_SCHEMA, "check_id" => definition.fetch("id"),
      "layer" => definition.fetch("layer"), "mode" => definition.fetch("mode"),
      "argv" => definition.fetch("argv"), "status" => status, "exit_code" => exit_code,
      "error_id" => error_id, "diagnostic_sha256" => Digest::SHA256.hexdigest(combined),
      "started_at" => started.iso8601(6), "completed_at" => Time.now.utc.iso8601(6)
    }
    base.merge("result_digest" => digest(base))
  end

  def bounded_capture(stream, maximum = 1_048_576)
    retained = +""
    while (chunk = stream.read(16_384))
      remaining = maximum - retained.bytesize
      retained << chunk.byteslice(0, remaining) if remaining.positive?
    end
    retained
  end

  def source_audit(root: ROOT, out: $stdout, err: $stderr)
    rules = {
      "legacy-key-config" => ["core/src/main/resources/application.yml", /FIELD_ENCRYPTION_KEY|key-base64|CHANGE_ME_BASE64/],
      "legacy-secret-extraction" => ["core/src/main/java/com/ycsopen/sms/core/common/security/key", /getEncoded\(|keyBytes|decrementWrap|releaseWrap|resetWrap/],
      "public-object-access" => ["core/src/main/java/com/ycsopen/sms/core/common/security/object", /Presigner|\bpresign\w*\s*\(|PUBLIC_READ|public-read|\bgetUrl\s*\(/],
      "legacy-registration-url" => ["core/src/main/java/com/ycsopen/sms/core", /businessLicenseUrl|legalRepIdFrontUrl|legalRepIdBackUrl|shortlinkDomainProofUrl|trademarkProofUrl/]
    }
    failures = []
    rules.each do |id, (relative, pattern)|
      path = File.join(root, relative)
      files = File.directory?(path) ? Dir.glob(File.join(path, "**/*")).select { |entry| File.file?(entry) } : [path]
      failures << id if files.any? { |entry| File.binread(entry).force_encoding("UTF-8").scrub.match?(pattern) }
    end
    required_docs = ["registration-object-sessions", "businessLicenseObjectId",
                     "LEGACY_OBJECT_URL_NOT_ACCEPTED", "PT24H"]
    doc_text = ["core/docs/API.md", "docs/使用手册.md"].map { |path| File.read(File.join(root, path), encoding: "UTF-8") }.join("\n")
    failures << "registration-doc-contract" unless required_docs.all? { |token| doc_text.include?(token) }
    if failures.empty?
      out.puts "phase03_source_audit=PASS rules=#{rules.length + 1}"
      return 0
    end
    failures.each { |id| err.puts "phase03_source_audit=FAIL rule=#{id}" }
    1
  rescue Errno::ENOENT, Errno::EACCES
    err.puts "phase03_source_audit=BLOCKED rule=source-unavailable"
    75
  end

  def persist_complete_leak_result(root, subject_digest, output_path)
    java = JSON.parse(File.read(File.join(root, JAVA_LEAK_RESULT), encoding: "UTF-8"))
    artifact = JSON.parse(File.read(File.join(root, ARTIFACT_LEAK_RESULT), encoding: "UTF-8"))
    raise ConfigurationError, "LEAK_JAVA_SUBJECT_MISMATCH" unless java["subject_digest"] == subject_digest
    raise ConfigurationError, "LEAK_JAVA_HEADER_INVALID" unless java.values_at(
      "schema_version", "phase", "check_id"
    ) == [Phase3CryptoEvidence::LEAK_RESULT_SCHEMA, Phase3CryptoEvidence::PHASE,
          "phase03-complete-leak-scan"]
    raise ConfigurationError, "LEAK_JAVA_STATUS_INVALID" unless java.values_at("status", "exit_code") == ["PASS", 0]
    raise ConfigurationError, "LEAK_ARTIFACT_HEADER_INVALID" unless artifact.values_at(
      "schema_version", "phase", "check_id"
    ) == ["phase03-artifact-leak-scan-v1", Phase3CryptoEvidence::PHASE,
          "phase03-artifact-leak-scan"]
    raise ConfigurationError, "LEAK_ARTIFACT_STATUS_INVALID" unless artifact.values_at("status", "exit_code") == ["PASS", 0] &&
      artifact["result_digest"] == Phase3CryptoEvidence.result_digest(artifact)
    expected_ids = Phase3CryptoEvidence::LEAK_TARGETS.to_a.sort
    raise ConfigurationError, "LEAK_JAVA_TARGETS_INVALID" unless java.fetch("targets").map { |row| row.fetch("id") } == expected_ids
    raise ConfigurationError, "LEAK_JAVA_DIGEST_INVALID" unless java["result_digest"] == Phase3CryptoEvidence.result_digest(java)
    java.fetch("targets").each do |row|
      expected_reader = Phase3CryptoEvidence::LEAK_READER_IDENTITIES.fetch(row.fetch("id"))
      raise ConfigurationError, "LEAK_JAVA_TARGET_INVALID" unless row.fetch("reader_identity") == expected_reader &&
        row.fetch("scanned_items").is_a?(Integer) && row.fetch("scanned_items").positive? &&
        row.fetch("prohibited_matches").zero? &&
        row.fetch("sensitivity_status") == "DETECTED_SEEDED_MUTATION"
    end
    raise ConfigurationError, "LEAK_ARTIFACT_TARGET_SET_INVALID" unless artifact.fetch("targets").map { |row| row.fetch("id") } == %w[evidence reports]
    artifact.fetch("targets").each do |row|
      raise ConfigurationError, "LEAK_ARTIFACT_TARGET_INVALID" unless row.fetch("prohibited_matches").zero? &&
        row.fetch("reader_identity") == "phase03-artifact-scanner" &&
        row.fetch("scanned_items").is_a?(Integer) && row.fetch("scanned_items").positive? &&
        row.fetch("sensitivity_status") == "DETECTED_SEEDED_MUTATION"
    end
    # The production Java command already combined its three runtime surfaces with an independently
    # generated Ruby two-surface report. This later root scan is a final post-run durability guard;
    # it is required as a separate lane and must not rewrite or double-count the command result.
    atomic_json(output_path, java)
    java
  rescue JSON::ParserError, KeyError, Errno::ENOENT => error
    raise ConfigurationError, "LEAK_RESULT_INVALID: #{error.class}"
  end

  def child_result(obligation_id, binding, subject_digest, lanes, registry_digest)
    selected = binding.fetch("lanes").map { |id| lanes.fetch(id) }
    base = {
      "schema_version" => Phase3CryptoEvidence::CHILD_RESULT_SCHEMA,
      "phase" => Phase3CryptoEvidence::PHASE,
      "check_id" => binding.fetch("check_id"), "layer" => binding.fetch("layer"),
      "obligation_ids" => [obligation_id],
      "case_ids" => [Phase3CryptoEvidence::OBLIGATIONS.fetch(obligation_id).fetch("case_id")],
      "adapter_identities" => binding.fetch("adapters").sort.map do |id|
        { "id" => id, "mode" => Phase3CryptoEvidence::ADAPTER_IDENTITIES.fetch(id) }
      end,
      "subject_digest" => subject_digest, "status" => "PASS", "exit_code" => 0,
      "facts" => {
        "aggregate_status" => "PASS", "registry_digest" => registry_digest,
        "required_lane_ids" => binding.fetch("lanes"),
        "lane_result_digests" => selected.to_h { |lane| [lane.fetch("check_id"), lane.fetch("result_digest")] }
      }
    }
    base.merge("result_digest" => Phase3CryptoEvidence.result_digest(base))
  end

  def aggregate_status(statuses)
    values = Array(statuses)
    raise ConfigurationError, "AGGREGATE_STATUS_INVALID" unless !values.empty? &&
      values.all? { |status| %w[PASS FAIL BLOCKED].include?(status) }
    return "FAIL" if values.include?("FAIL")
    return "BLOCKED" if values.include?("BLOCKED")

    "PASS"
  end

  def run(root: ROOT, result_root: DEFAULT_RESULT_ROOT, io: $stdout)
    registry_contract!
    root = File.realpath(root)
    result_path = contained_result_root(root, result_root)
    FileUtils.mkdir_p(result_path)
    subject = build_subject(root)
    tested_subject_digest = Phase3CryptoEvidence.digest_value(subject.fetch("inputs"))
    atomic_json(File.join(result_path, "tested-inputs.json"), subject)
    FileUtils.rm_f([File.join(root, JAVA_LEAK_RESULT), File.join(root, ARTIFACT_LEAK_RESULT)])

    environment = { "PHASE03_TESTED_SUBJECT_DIGEST" => tested_subject_digest }
    lanes = {}
    CHECKS.each do |definition|
      lane = execute(root, definition, environment)
      lanes[definition.fetch("id")] = lane
      atomic_json(File.join(result_path, "lanes", "#{definition.fetch('id')}.json"), lane)
      io.puts format("%-34s %s", definition.fetch("id"), lane.fetch("status"))
    end
    statuses = lanes.values.map { |lane| lane.fetch("status") }
    aggregate_status = aggregate_status(statuses)
    registry_digest = digest(CHECKS)
    aggregate = {
      "schema_version" => AGGREGATE_SCHEMA, "phase" => Phase3CryptoEvidence::PHASE,
      "status" => aggregate_status, "tested_subject_digest" => tested_subject_digest,
      "registry_digest" => registry_digest,
      "lane_result_digests" => lanes.transform_values { |lane| lane.fetch("result_digest") }
    }
    aggregate["result_digest"] = digest(aggregate)
    atomic_json(File.join(result_path, "aggregate.json"), aggregate)
    return aggregate unless aggregate_status == "PASS"

    persist_complete_leak_result(root, tested_subject_digest,
                                 File.join(result_path, "complete-leak-result.json"))
    CHILD_BINDINGS.each do |obligation_id, binding|
      child = child_result(obligation_id, binding, tested_subject_digest, lanes, registry_digest)
      atomic_json(File.join(result_path, "#{binding.fetch('check_id')}.json"), child)
    end
    inventory_digest = Phase3CryptoEvidence.digest_value(JSON.parse(File.read(File.join(root, INVENTORY))))
    inventory_base = {
      "schema_version" => Phase3CryptoEvidence::CHILD_RESULT_SCHEMA,
      "phase" => Phase3CryptoEvidence::PHASE, "check_id" => "phase03-protected-inventory",
      "layer" => "static", "obligation_ids" => OBLIGATIONS.sort, "case_ids" => CASES.sort,
      "adapter_identities" => [{ "id" => "phase03-inventory-validator", "mode" => "DETERMINISTIC" }],
      "subject_digest" => tested_subject_digest, "status" => "PASS", "exit_code" => 0,
      "facts" => { "blocking_dispositions" => [], "inventory_digest" => inventory_digest,
                   "result" => "ACCEPTED" }
    }
    inventory_base["result_digest"] = Phase3CryptoEvidence.result_digest(inventory_base)
    atomic_json(File.join(result_path, "protected-inventory-result.json"), inventory_base)
    aggregate
  end

  def cli(argv, root: ROOT, io: $stdout, err: $stderr)
    return source_audit(root: root, out: io, err: err) if argv == ["--internal-source-audit"]
    result_root = nil
    selector = false
    index = 0
    while index < argv.length
      case argv[index]
      when "--all" then selector = true
      when "--result-root"
        index += 1
        raise ConfigurationError, "OPTION_RESULT_ROOT_REQUIRED" if index >= argv.length || result_root
        result_root = argv[index]
      else raise ConfigurationError, "OPTION_UNKNOWN"
      end
      index += 1
    end
    raise ConfigurationError, "OPTION_ALL_REQUIRED" unless selector
    raise ConfigurationError, "OPTION_RESULT_ROOT_REQUIRED" unless result_root
    result = run(root: root, result_root: result_root, io: io)
    io.puts "phase03_root_verification=#{result.fetch('status')}"
    result.fetch("status") == "PASS" ? 0 : 1
  rescue ConfigurationError => error
    err.puts "phase03_root_verification=BLOCKED error=#{error.message.gsub(/[^A-Za-z0-9_:,.-]/, '_')}"
    2
  end
end

if $PROGRAM_NAME == __FILE__
  exit Phase03RunChecks.cli(ARGV)
end
