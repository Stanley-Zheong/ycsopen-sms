#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "optparse"
require "pathname"
require "securerandom"
require "set"
require "time"
require_relative "phase3-crypto-evidence"
require_relative "../../scripts/lib/phase-03/run_checks"

# Materializes Phase-3 obligation evidence only from the fixed root runner's accepted result set.
module Phase03CryptoEvidenceProducer
  class Rejected < StandardError; end

  PHASE_DIR = ".planning/phases/03-crypto-storage-bootstrap"
  INVENTORY_PATH = "core/src/main/resources/security/protected-data-inventory.json"
  RESULT_DIR = Phase3CryptoEvidence::ROOT_RESULT_DIR
  SHA256 = Phase3CryptoEvidence::SHA256
  FILE_LIMIT = Phase3CryptoEvidence::FILE_LIMIT
  SUBJECT_FIELDS = Set.new(%w[schema_version phase owner inputs]).freeze
  INPUT_FIELDS = Set.new(%w[path mode sha256 role]).freeze
  GENERATED_EVIDENCE_PATHS = Set.new(
    [File.join(PHASE_DIR, "EVIDENCE/tested-inputs.json"),
     File.join(PHASE_DIR, "EVIDENCE/evidence-manifest.json")] +
    Phase3CryptoEvidence::OBLIGATIONS.values.map do |definition|
      File.join(PHASE_DIR, definition.fetch("evidence_path"))
    end
  ).freeze

  class Producer
    attr_reader :written_paths

    def initialize(root:, phase_dir:, result_root:)
      @root = Pathname(root).realpath
      reject!("PHASE_DIR_INVALID") unless phase_dir == PHASE_DIR
      reject!("RESULT_ROOT_INVALID") unless result_root == RESULT_DIR
      @phase_dir = phase_dir
      @result_root = result_root
      @written_paths = []
    rescue Errno::ENOENT
      raise Rejected, "REPOSITORY_ROOT_MISSING"
    end

    def produce
      Phase03RunChecks.registry_contract!
      validate_trace_contract!
      validate_schemas!
      subject, subject_bytes = read_json(File.join(@result_root, "tested-inputs.json"), "TESTED_INPUTS")
      subject_digest = validate_subject!(subject)
      inventory, inventory_bytes = read_json(INVENTORY_PATH, "INVENTORY")
      validate_inventory!(inventory)
      inventory_digest = Phase3CryptoEvidence.digest_value(inventory)

      aggregate, = read_json(File.join(@result_root, "aggregate.json"), "ROOT_AGGREGATE")
      lane_results = validate_aggregate_and_lanes!(aggregate, subject_digest)
      children = validate_children!(subject_digest, aggregate, lane_results)
      inventory_result = validate_inventory_result!(subject_digest, inventory_digest)
      leak, leak_bytes = validate_leak!(subject_digest)

      # Lane argv is a fixed executable contract and may contain `/usr/bin/env`; only its digest
      # is persisted into evidence. Scan the documents that can flow into durable evidence.
      sanitized = [aggregate, inventory_result, leak] + children.values
      probe = Phase3CryptoEvidence::Validator.new(
        root: @root.to_s, phase_dir: @phase_dir,
        require_owner: Phase3CryptoEvidence::OWNER
      ).validate_sanitized_documents(sanitized)
      reject!("PROHIBITED_INPUT_CONTENT") unless probe.errors.empty?

      evidence_dir = File.join(@phase_dir, "EVIDENCE")
      subject_path = File.join(evidence_dir, "tested-inputs.json")
      subject_ref = {
        "path" => subject_path,
        "sha256" => Digest::SHA256.hexdigest(subject_bytes),
        "tested_subject_digest" => subject_digest
      }
      inventory_result_path = File.join(@result_root, "protected-inventory-result.json")
      inventory_ref = {
        "path" => INVENTORY_PATH,
        "sha256" => Digest::SHA256.hexdigest(inventory_bytes),
        "accepted_digest" => inventory_digest,
        "validator_result" => result_reference(inventory_result_path, inventory_result)
      }
      leak_path = File.join(@result_root, "complete-leak-result.json")
      leak_ref = {
        "path" => leak_path,
        "sha256" => Digest::SHA256.hexdigest(leak_bytes),
        "result_digest" => leak.fetch("result_digest")
      }

      entries = Phase3CryptoEvidence::OBLIGATIONS.map do |obligation_id, definition|
        child_path = File.join(@result_root, "#{definition.fetch('check_id')}.json")
        evidence = obligation_evidence(
          obligation_id, definition, subject_ref, inventory_ref, leak_ref,
          result_reference(child_path, children.fetch(obligation_id))
        )
        relative = File.join(@phase_dir, definition.fetch("evidence_path"))
        [relative, evidence, {
          "obligation_id" => obligation_id,
          "path" => relative,
          "sha256" => Digest::SHA256.hexdigest(encoded_json(evidence)),
          "status" => "PASS",
          "evidence_digest" => Phase3CryptoEvidence.digest_value(evidence)
        }]
      end
      manifest = {
        "schema_version" => Phase3CryptoEvidence::MANIFEST_SCHEMA,
        "phase" => Phase3CryptoEvidence::PHASE,
        "owner" => Phase3CryptoEvidence::OWNER,
        "status" => "PASS",
        "subject" => subject_ref,
        "inventory" => inventory_ref,
        "leak_result" => leak_ref,
        "entries" => entries.map(&:last)
      }

      manifest_path = File.join(evidence_dir, "evidence-manifest.json")
      output_paths = [subject_path, manifest_path] + entries.map(&:first)
      output_paths.each { |relative| safe_output_path(relative) }
      atomic_bytes(subject_path, subject_bytes)
      entries.each { |relative, evidence, _entry| atomic_bytes(relative, encoded_json(evidence)) }
      atomic_bytes(manifest_path, encoded_json(manifest))
      self
    rescue Phase03RunChecks::ConfigurationError => error
      raise Rejected, sanitized(error.message, "ROOT_REGISTRY_INVALID")
    end

    private

    def validate_trace_contract!
      catalog = {}
      read_bytes(".planning/PRD-OBLIGATIONS.md", "CATALOG").lines(chomp: true).each do |line|
        next unless line.start_with?("- OBL-")
        cells = line.delete_prefix("- ").split("|").map(&:strip)
        next unless cells[3] == Phase3CryptoEvidence::OWNER
        reject!("CATALOG_ROW_INVALID") unless cells.length == 9
        catalog[cells[0]] = {
          "requirement_ids" => cells[2].split(",").sort,
          "behavior_id" => cells[4], "catalog_test" => cells[6],
          "evidence_path" => cells[7]
        }
      end
      matrix = {}
      read_bytes(File.join(@phase_dir, "TEST-MATRIX.md"), "TEST_MATRIX").lines(chomp: true).each do |line|
        next unless line.start_with?("| OBL-")
        cells = line.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
        next unless Phase3CryptoEvidence::OBLIGATIONS.key?(cells[0])
        reject!("TEST_MATRIX_ROW_INVALID") unless cells.length == 11
        matrix[cells[0]] = {
          "requirement_ids" => cells[1].split(",").sort,
          "behavior_id" => cells[2], "catalog_test" => cells[3],
          "case_id" => cells[7],
          "evidence_path" => cells[10].delete_prefix("`").delete_suffix("`")
        }
      end
      reject!("CATALOG_EXACT_FOUR_MISMATCH") unless catalog.keys == Phase3CryptoEvidence::OBLIGATIONS.keys
      reject!("TEST_MATRIX_EXACT_FOUR_MISMATCH") unless matrix.keys == Phase3CryptoEvidence::OBLIGATIONS.keys
      Phase3CryptoEvidence::OBLIGATIONS.each do |id, expected|
        %w[requirement_ids behavior_id catalog_test evidence_path].each do |field|
          reject!("CATALOG_TRACE_MISMATCH") unless catalog.dig(id, field) == expected[field]
          reject!("MATRIX_TRACE_MISMATCH") unless matrix.dig(id, field) == expected[field]
        end
        reject!("MATRIX_TRACE_MISMATCH") unless matrix.dig(id, "case_id") == expected["case_id"]
      end
    end

    def validate_schemas!
      obligation, = read_json(
        File.join(@phase_dir, "EVIDENCE/schema/phase03-obligation-evidence.schema.json"),
        "OBLIGATION_SCHEMA"
      )
      manifest, = read_json(
        File.join(@phase_dir, "EVIDENCE/schema/phase03-evidence-manifest.schema.json"),
        "MANIFEST_SCHEMA"
      )
      reject!("OBLIGATION_SCHEMA_INVALID") unless obligation["additionalProperties"] == false &&
        obligation.dig("properties", "schema_version", "const") == Phase3CryptoEvidence::OBLIGATION_SCHEMA
      reject!("MANIFEST_SCHEMA_INVALID") unless manifest["additionalProperties"] == false &&
        manifest.dig("properties", "schema_version", "const") == Phase3CryptoEvidence::MANIFEST_SCHEMA &&
        manifest.dig("properties", "entries", "minItems") == 4 &&
        manifest.dig("properties", "entries", "maxItems") == 4
    end

    def validate_subject!(subject)
      exact!(subject, SUBJECT_FIELDS, "TESTED_INPUTS")
      reject!("TESTED_INPUTS_HEADER_INVALID") unless subject.values_at(
        "schema_version", "phase", "owner"
      ) == [Phase3CryptoEvidence::TESTED_INPUTS_SCHEMA,
            Phase3CryptoEvidence::PHASE, Phase3CryptoEvidence::OWNER]
      inputs = subject["inputs"]
      reject!("TESTED_INPUTS_EMPTY") unless inputs.is_a?(Array) && !inputs.empty?
      paths = inputs.map do |input|
        exact!(input, INPUT_FIELDS, "TESTED_INPUT")
        path = input.fetch("path")
        reject!("TESTED_INPUT_SELF_REFERENCE") if path.start_with?("#{RESULT_DIR}/") ||
          GENERATED_EVIDENCE_PATHS.include?(path)
        bytes = read_bytes(path, "TESTED_INPUT")
        stat = safe_path(path, "TESTED_INPUT").stat
        reject!("TESTED_INPUT_MODE_MISMATCH") unless input["mode"] == format("%06o", stat.mode)
        reject!("TESTED_INPUT_SHA256_MISMATCH") unless input["sha256"] == Digest::SHA256.hexdigest(bytes)
        reject!("TESTED_INPUT_ROLE_INVALID") unless %w[implementation test config contract validator].include?(input["role"])
        path
      end
      reject!("TESTED_INPUTS_ORDER_INVALID") unless paths == paths.sort
      reject!("TESTED_INPUTS_DUPLICATE") unless paths.uniq.length == paths.length
      expected_subject = Phase03RunChecks.build_subject(@root.to_s)
      reject!("TESTED_INPUT_SET_INVALID") unless subject == expected_subject
      Phase3CryptoEvidence.digest_value(inputs)
    end

    def validate_inventory!(inventory)
      probe = Phase3CryptoEvidence::Validator.new(
        root: @root.to_s, phase_dir: @phase_dir,
        require_owner: Phase3CryptoEvidence::OWNER
      ).validate_inventory_document(inventory)
      reject!("INVENTORY_NOT_ACCEPTED") unless probe.errors.empty?
    end

    def validate_aggregate_and_lanes!(aggregate, subject_digest)
      exact!(aggregate, Phase3CryptoEvidence::ROOT_AGGREGATE_FIELDS, "ROOT_AGGREGATE")
      checks = Phase03RunChecks::CHECKS
      expected_ids = checks.map { |definition| definition.fetch("id") }
      expected_registry = Phase03RunChecks.digest(checks)
      reject!("ROOT_AGGREGATE_HEADER_INVALID") unless aggregate.values_at(
        "schema_version", "phase", "status", "tested_subject_digest", "registry_digest"
      ) == [Phase3CryptoEvidence::ROOT_AGGREGATE_SCHEMA, Phase3CryptoEvidence::PHASE,
            "PASS", subject_digest, expected_registry]
      reject!("ROOT_AGGREGATE_RESULT_DIGEST_INVALID") unless aggregate["result_digest"] ==
        Phase03RunChecks.digest(aggregate.reject { |key, _| key == "result_digest" })
      digests = aggregate["lane_result_digests"]
      reject!("ROOT_AGGREGATE_LANE_SET_INVALID") unless digests.is_a?(Hash) &&
        digests.keys.sort == expected_ids.sort &&
        digests.values.all? { |value| value.is_a?(String) && value.match?(SHA256) }

      checks.to_h do |definition|
        id = definition.fetch("id")
        lane, = read_json(File.join(@result_root, "lanes", "#{id}.json"), "ROOT_LANE")
        exact!(lane, Phase3CryptoEvidence::ROOT_LANE_FIELDS, "ROOT_LANE")
        reject!("ROOT_LANE_CONTRACT_INVALID") unless lane.values_at(
          "schema_version", "check_id", "layer", "mode", "argv", "status",
          "exit_code", "error_id"
        ) == [Phase3CryptoEvidence::ROOT_RESULT_SCHEMA, id, definition.fetch("layer"),
              definition.fetch("mode"), definition.fetch("argv"), "PASS", 0, nil]
        reject!("ROOT_LANE_DIAGNOSTIC_INVALID") unless lane["diagnostic_sha256"].is_a?(String) &&
          lane["diagnostic_sha256"].match?(SHA256)
        %w[started_at completed_at].each do |field|
          Time.iso8601(lane.fetch(field))
        rescue ArgumentError, KeyError, TypeError
          reject!("ROOT_LANE_TIMESTAMP_INVALID")
        end
        actual = Phase03RunChecks.digest(lane.reject { |key, _| key == "result_digest" })
        reject!("ROOT_LANE_RESULT_DIGEST_INVALID") unless lane["result_digest"] == actual && digests[id] == actual
        [id, lane]
      end
    end

    def validate_children!(subject_digest, aggregate, lanes)
      Phase03RunChecks::CHILD_BINDINGS.to_h do |obligation_id, binding|
        definition = Phase3CryptoEvidence::OBLIGATIONS.fetch(obligation_id)
        child, = read_json(
          File.join(@result_root, "#{binding.fetch('check_id')}.json"), "CHILD_RESULT"
        )
        exact!(child, Phase3CryptoEvidence::CHILD_FIELDS, "CHILD_RESULT")
        expected_adapters = binding.fetch("adapters").sort.map do |id|
          { "id" => id, "mode" => Phase3CryptoEvidence::ADAPTER_IDENTITIES.fetch(id) }
        end
        reject!("CHILD_RESULT_CONTRACT_INVALID") unless child.values_at(
          "schema_version", "phase", "check_id", "layer", "obligation_ids", "case_ids",
          "adapter_identities", "subject_digest", "status", "exit_code"
        ) == [Phase3CryptoEvidence::CHILD_RESULT_SCHEMA, Phase3CryptoEvidence::PHASE,
              binding.fetch("check_id"), binding.fetch("layer"), [obligation_id],
              [definition.fetch("case_id")], expected_adapters, subject_digest, "PASS", 0]
        reject!("CHILD_RESULT_DIGEST_INVALID") unless child["result_digest"] ==
          Phase3CryptoEvidence.result_digest(child)
        facts = child["facts"]
        exact!(facts, Phase3CryptoEvidence::CHILD_LANE_FACT_FIELDS, "CHILD_FACTS")
        required = binding.fetch("lanes")
        reject!("CHILD_REQUIRED_LANES_INVALID") unless facts["required_lane_ids"] == required &&
          required.include?("durable-artifact-leak-scan") && required.include?("fixture-cleanup")
        expected_digests = required.to_h { |id| [id, lanes.fetch(id).fetch("result_digest")] }
        reject!("CHILD_LANE_DIGESTS_INVALID") unless facts["lane_result_digests"] == expected_digests
        reject!("CHILD_REGISTRY_DIGEST_INVALID") unless facts.values_at(
          "aggregate_status", "registry_digest"
        ) == ["PASS", aggregate.fetch("registry_digest")]
        [obligation_id, child]
      end
    end

    def validate_inventory_result!(subject_digest, inventory_digest)
      result, = read_json(File.join(@result_root, "protected-inventory-result.json"), "INVENTORY_RESULT")
      exact!(result, Phase3CryptoEvidence::CHILD_FIELDS, "INVENTORY_RESULT")
      expected_adapters = [{ "id" => "phase03-inventory-validator", "mode" => "DETERMINISTIC" }]
      reject!("INVENTORY_RESULT_CONTRACT_INVALID") unless result.values_at(
        "schema_version", "phase", "check_id", "layer", "obligation_ids", "case_ids",
        "adapter_identities", "subject_digest", "status", "exit_code", "facts"
      ) == [Phase3CryptoEvidence::CHILD_RESULT_SCHEMA, Phase3CryptoEvidence::PHASE,
            "phase03-protected-inventory", "static", Phase3CryptoEvidence::OBLIGATIONS.keys.sort,
            Phase3CryptoEvidence::OBLIGATIONS.values.map { |row| row.fetch("case_id") }.sort,
            expected_adapters, subject_digest, "PASS", 0,
            { "blocking_dispositions" => [], "inventory_digest" => inventory_digest,
              "result" => "ACCEPTED" }]
      reject!("INVENTORY_RESULT_DIGEST_INVALID") unless result["result_digest"] ==
        Phase3CryptoEvidence.result_digest(result)
      result
    end

    def validate_leak!(subject_digest)
      path = File.join(@result_root, "complete-leak-result.json")
      leak, bytes = read_json(path, "LEAK_RESULT")
      exact!(leak, Phase3CryptoEvidence::LEAK_FIELDS, "LEAK_RESULT")
      reject!("LEAK_RESULT_CONTRACT_INVALID") unless leak.values_at(
        "schema_version", "phase", "check_id", "subject_digest", "status", "exit_code"
      ) == [Phase3CryptoEvidence::LEAK_RESULT_SCHEMA, Phase3CryptoEvidence::PHASE,
            "phase03-complete-leak-scan", subject_digest, "PASS", 0]
      reject!("LEAK_RESULT_DIGEST_INVALID") unless leak["result_digest"] ==
        Phase3CryptoEvidence.result_digest(leak)
      targets = leak["targets"]
      reject!("LEAK_TARGET_SET_INVALID") unless targets.is_a?(Array) &&
        targets.map { |row| row["id"] } == Phase3CryptoEvidence::LEAK_TARGETS.to_a.sort
      targets.each do |target|
        exact!(target, Phase3CryptoEvidence::LEAK_TARGET_FIELDS, "LEAK_TARGET")
        reject!("LEAK_TARGET_INVALID") unless target["reader_identity"] ==
          Phase3CryptoEvidence::LEAK_READER_IDENTITIES[target["id"]] &&
          target["scanned_items"].is_a?(Integer) && target["scanned_items"].positive? &&
          target["prohibited_matches"] == 0 &&
          target["sensitivity_status"] == "DETECTED_SEEDED_MUTATION"
      end
      [leak, bytes]
    end

    def obligation_evidence(obligation_id, definition, subject, inventory, leak, child)
      {
        "schema_version" => Phase3CryptoEvidence::OBLIGATION_SCHEMA,
        "phase" => Phase3CryptoEvidence::PHASE, "owner" => Phase3CryptoEvidence::OWNER,
        "obligation_id" => obligation_id,
        "requirement_ids" => definition.fetch("requirement_ids"),
        "behavior_id" => definition.fetch("behavior_id"),
        "catalog_test" => definition.fetch("catalog_test"),
        "case_id" => definition.fetch("case_id"),
        "evidence_path" => definition.fetch("evidence_path"),
        "status" => "PASS", "exit_code" => 0,
        "subject" => subject, "inventory" => inventory, "leak_result" => leak,
        "child_results" => [child]
      }
    end

    def result_reference(path, result)
      bytes = read_bytes(path, "RESULT_REFERENCE")
      {
        "check_id" => result.fetch("check_id"), "path" => path,
        "sha256" => Digest::SHA256.hexdigest(bytes),
        "result_digest" => result.fetch("result_digest")
      }
    end

    def exact!(value, fields, label)
      reject!("#{label}_FIELDS_INVALID") unless value.is_a?(Hash) && value.keys.to_set == fields
    end

    def read_json(relative, label)
      bytes = read_bytes(relative, label)
      [JSON.parse(bytes), bytes]
    rescue JSON::ParserError, EncodingError
      reject!("#{label}_JSON_INVALID")
    end

    def read_bytes(relative, label)
      path = safe_path(relative, label)
      before = path.lstat
      reject!("#{label}_FILE_INVALID") unless before.file? && before.nlink == 1 &&
        before.size.positive? && before.size <= FILE_LIMIT
      bytes = File.binread(path, FILE_LIMIT + 1)
      after = path.lstat
      reject!("#{label}_FILE_CHANGED") unless bytes.bytesize == before.size &&
        after.dev == before.dev && after.ino == before.ino && after.size == before.size &&
        after.mtime == before.mtime
      bytes
    rescue Errno::ENOENT, Errno::ENOTDIR, Errno::EACCES
      reject!("#{label}_UNREADABLE")
    end

    def safe_path(relative, label)
      reject!("#{label}_PATH_INVALID") unless canonical_relative?(relative)
      candidate = @root.join(relative).cleanpath
      reject!("#{label}_PATH_ESCAPE") unless candidate.to_s.start_with?("#{@root}#{File::SEPARATOR}")
      current = candidate
      until current == @root
        reject!("#{label}_SYMLINK_REJECTED") if current.exist? && current.lstat.symlink?
        current = current.parent
      end
      candidate
    end

    def canonical_relative?(value)
      return false unless value.is_a?(String) && !value.empty? &&
        !value.include?("\0") && !value.include?("\\")
      path = Pathname(value)
      !path.absolute? && path.cleanpath.to_s == value &&
        path.each_filename.none? { |part| part == ".." }
    end

    def atomic_bytes(relative, bytes)
      path = safe_output_path(relative)
      temporary = path.dirname.join(".#{path.basename}.#{Process.pid}.#{SecureRandom.hex(8)}.tmp")
      File.open(temporary, File::WRONLY | File::CREAT | File::EXCL, 0o600) do |file|
        file.write(bytes)
        file.flush
        file.fsync
      end
      File.chmod(0o644, temporary)
      File.rename(temporary, path)
      @written_paths << relative
    ensure
      File.delete(temporary) if defined?(temporary) && temporary && File.exist?(temporary)
    end

    def safe_output_path(relative)
      reject!("OUTPUT_PATH_INVALID") unless canonical_relative?(relative)
      candidate = @root.join(relative).cleanpath
      reject!("OUTPUT_PATH_ESCAPE") unless candidate.to_s.start_with?("#{@root}#{File::SEPARATOR}")
      parent = candidate.dirname
      reject!("OUTPUT_PARENT_INVALID") unless parent.exist? && parent.lstat.directory? &&
        !parent.lstat.symlink?
      current = parent
      until current == @root
        reject!("OUTPUT_SYMLINK_REJECTED") if current.lstat.symlink?
        current = current.parent
      end
      if candidate.exist?
        stat = candidate.lstat
        reject!("OUTPUT_FILE_INVALID") unless stat.file? && !stat.symlink? && stat.nlink == 1
      end
      candidate
    end

    def encoded_json(value)
      JSON.pretty_generate(Phase3CryptoEvidence.canonicalize(value)) + "\n"
    end

    def sanitized(value, fallback)
      token = value.to_s
      token.match?(/\A[A-Z0-9_:,.-]+\z/) ? token : fallback
    end

    def reject!(code)
      raise Rejected, sanitized(code, "EVIDENCE_INPUT_REJECTED")
    end
  end
end

if $PROGRAM_NAME == __FILE__
  options = {}
  parser = OptionParser.new do |cli|
    cli.on("--phase-dir PATH") { |value| options[:phase_dir] = value }
    cli.on("--result-root PATH") { |value| options[:result_root] = value }
  end
  begin
    parser.parse!(ARGV)
    unless ARGV.empty? && %i[phase_dir result_root].all? { |key| options[key] }
      raise Phase03CryptoEvidenceProducer::Rejected, "ARGUMENTS_INVALID"
    end
    producer = Phase03CryptoEvidenceProducer::Producer.new(root: Dir.pwd, **options).produce
    puts "phase03_crypto_evidence_producer=PASS obligations=4 files=#{producer.written_paths.length}"
    exit 0
  rescue OptionParser::ParseError, Phase03CryptoEvidenceProducer::Rejected => error
    token = error.message.to_s.match?(/\A[A-Z0-9_:,.-]+\z/) ? error.message : "EVIDENCE_INPUT_REJECTED"
    warn "phase03_crypto_evidence_producer=BLOCKED error=#{token}"
    exit 1
  rescue StandardError => error
    token = error.class.name.gsub(/[^A-Za-z0-9]/, "_").upcase
    warn "phase03_crypto_evidence_producer=BLOCKED error=INTERNAL_#{token}"
    exit 1
  end
end
