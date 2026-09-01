#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "pathname"
require "set"
require "time"

module Phase3CryptoEvidence
  PHASE = "03-crypto-storage-bootstrap"
  OWNER = "crypto-storage-bootstrap"
  OBLIGATION_SCHEMA = "phase03-obligation-evidence-v1"
  MANIFEST_SCHEMA = "phase03-evidence-manifest-v1"
  TESTED_INPUTS_SCHEMA = "phase03-tested-inputs-v1"
  CHILD_RESULT_SCHEMA = "phase03-child-result-v1"
  LEAK_RESULT_SCHEMA = "phase03-leak-result-v1"
  SHA256 = /\A[0-9a-f]{64}\z/
  FILE_LIMIT = 4 * 1024 * 1024
  ROOT_RESULT_DIR = "core/target/phase03/results"
  ROOT_RESULT_SCHEMA = "phase03-root-lane-result-v1"
  ROOT_AGGREGATE_SCHEMA = "phase03-root-aggregate-v1"

  OBLIGATIONS = {
    "OBL-CRYPTO-STORAGE-001" => {
      "requirement_ids" => ["REQ-NFR-DATA-PROTECTION"],
      "behavior_id" => "crypto-storage-bootstrap-01",
      "catalog_test" => "T-CRYPTO-STORAGE-001:database",
      "case_id" => "CASE-CRYPTO-STORAGE-001",
      "evidence_path" => "EVIDENCE/OBL-CRYPTO-STORAGE-001.json",
      "check_id" => "phase03-protected-persistence-integration",
      "layer" => "database",
      "adapters" => %w[java21-sunpkcs11 mysql-8]
    },
    "OBL-CRYPTO-STORAGE-002" => {
      "requirement_ids" => ["REQ-NFR-DATA-PROTECTION"],
      "behavior_id" => "crypto-storage-bootstrap-02",
      "catalog_test" => "T-CRYPTO-STORAGE-002:security",
      "case_id" => "CASE-CRYPTO-STORAGE-002",
      "evidence_path" => "EVIDENCE/OBL-CRYPTO-STORAGE-002.json",
      "check_id" => "phase03-object-storage-integration",
      "layer" => "security",
      "adapters" => %w[java21-sunpkcs11 minio mysql-8]
    },
    "OBL-CRYPTO-STORAGE-003" => {
      "requirement_ids" => ["REQ-NFR-DATA-PROTECTION"],
      "behavior_id" => "crypto-storage-bootstrap-03",
      "catalog_test" => "T-CRYPTO-STORAGE-003:fault",
      "case_id" => "CASE-CRYPTO-STORAGE-003",
      "evidence_path" => "EVIDENCE/OBL-CRYPTO-STORAGE-003.json",
      "check_id" => "phase03-pkcs11-fault-integration",
      "layer" => "fault",
      "adapters" => %w[java21-sunpkcs11 softhsm-2.7.0]
    },
    "OBL-CRYPTO-STORAGE-004" => {
      "requirement_ids" => ["REQ-NFR-DATA-PROTECTION"],
      "behavior_id" => "crypto-storage-bootstrap-04",
      "catalog_test" => "T-CRYPTO-STORAGE-004:database",
      "case_id" => "CASE-CRYPTO-STORAGE-004",
      "evidence_path" => "EVIDENCE/OBL-CRYPTO-STORAGE-004.json",
      "check_id" => "phase03-migration-integration",
      "layer" => "database",
      "adapters" => %w[java21-sunpkcs11 mysql-8 softhsm-2.7.0]
    }
  }.freeze

  ADAPTER_IDENTITIES = {
    "mysql-8" => "REAL",
    "minio" => "REAL",
    "java21-sunpkcs11" => "REAL",
    "softhsm-2.7.0" => "REAL",
    "phase03-inventory-validator" => "DETERMINISTIC",
    "phase03-leak-scanner" => "DETERMINISTIC",
    "phase03-artifact-scanner" => "DETERMINISTIC",
    "phase03-contract-validator" => "DETERMINISTIC"
  }.freeze
  REQUIRED_NO_INDEX_TARGETS = Set[
    "bulk_sending_items.mobile_encrypted",
    "uplink_records.mobile_encrypted"
  ].freeze
  REQUIRED_SOURCE_SURFACES = Set[
    "message-submit-persistence",
    "tenant-registration-persistence",
    "auth-user-hydration-save",
    "hmac-api-key-hydration",
    "blacklist-lookup-hydration",
    "tenant-lifecycle-analytics-hydration-save"
  ].freeze
  LEAK_TARGETS = Set.new(%w[database-cells evidence logs object-bytes reports]).freeze
  LEAK_READER_IDENTITIES = {
    "database-cells" => "phase03-leak-scanner",
    "evidence" => "phase03-artifact-scanner",
    "logs" => "phase03-leak-scanner",
    "object-bytes" => "phase03-leak-scanner",
    "reports" => "phase03-artifact-scanner"
  }.freeze

  MANIFEST_FIELDS = Set.new(%w[schema_version phase owner status subject inventory leak_result entries]).freeze
  SUBJECT_REF_FIELDS = Set.new(%w[path sha256 tested_subject_digest]).freeze
  INVENTORY_REF_FIELDS = Set.new(%w[path sha256 accepted_digest validator_result]).freeze
  LEAK_REF_FIELDS = Set.new(%w[path sha256 result_digest]).freeze
  RESULT_REF_FIELDS = Set.new(%w[check_id path sha256 result_digest]).freeze
  ENTRY_FIELDS = Set.new(%w[obligation_id path sha256 status evidence_digest]).freeze
  EVIDENCE_FIELDS = Set.new(%w[
    schema_version phase owner obligation_id requirement_ids behavior_id catalog_test
    case_id evidence_path status exit_code subject inventory leak_result child_results
  ]).freeze
  CHILD_FIELDS = Set.new(%w[
    schema_version phase check_id layer obligation_ids case_ids adapter_identities
    subject_digest status exit_code facts result_digest
  ]).freeze
  ADAPTER_FIELDS = Set.new(%w[id mode]).freeze
  TESTED_INPUT_FIELDS = Set.new(%w[schema_version phase owner inputs]).freeze
  INPUT_FIELDS = Set.new(%w[path mode sha256 role]).freeze
  LEAK_FIELDS = Set.new(%w[schema_version phase check_id subject_digest status exit_code targets result_digest]).freeze
  LEAK_TARGET_FIELDS = Set.new(%w[id reader_identity scanned_items prohibited_matches sensitivity_status]).freeze
  ROOT_AGGREGATE_FIELDS = Set.new(%w[
    schema_version phase status tested_subject_digest registry_digest
    lane_result_digests result_digest
  ]).freeze
  ROOT_LANE_FIELDS = Set.new(%w[
    schema_version check_id layer mode argv status exit_code error_id diagnostic_sha256
    started_at completed_at result_digest
  ]).freeze
  CHILD_LANE_FACT_FIELDS = Set.new(%w[
    aggregate_status registry_digest required_lane_ids lane_result_digests
  ]).freeze

  PROHIBITED_KEYS = Set.new(%w[
    plaintext plaintext_canary canary key key_bytes private_key secret pin raw_token
    token url raw_url ciphertext ciphertext_body wrapped_dek provider_text
    provider_response raw_payload body object_content matched_content
  ]).freeze
  PROHIBITED_VALUE_PATTERNS = {
    "PLAINTEXT_CANARY" => /(?:plaintext[_ -]?canary|canary[_-][a-z0-9])/i,
    "PRIVATE_KEY" => /-----BEGIN [^-\r\n]*PRIVATE KEY-----/i,
    "RAW_TOKEN" => /(?:ocap_v1_|regup_v1_|\bBearer\s+[A-Za-z0-9._~+\/=:-]+)/i,
    "RAW_URL" => %r{(?:https?|s3|file)://}i,
    "PIN" => /\bPIN\s*[:=]\s*\S+/i,
    "CIPHERTEXT_BODY" => /(?:ciphertext|wrapped[_ -]?dek)\s*[:=]/i,
    "PROVIDER_TEXT" => /provider[_ -]?(?:text|response)\s*[:=]/i,
    "PHONE" => /(?<!\d)1[3-9]\d{9}(?!\d)/
  }.freeze

  module_function

  def canonicalize(value)
    case value
    when Hash
      value.keys.map(&:to_s).sort.to_h do |key|
        nested = value.key?(key) ? value[key] : value[key.to_sym]
        [key, canonicalize(nested)]
      end
    when Array
      value.map { |item| canonicalize(item) }
    else
      value
    end
  end

  def canonical_json(value)
    JSON.generate(canonicalize(value))
  end

  def digest_value(value)
    Digest::SHA256.hexdigest(canonical_json(value))
  end

  def result_digest(result)
    digest_value(result.reject { |key, _value| key.to_s == "result_digest" })
  end

  class Validator
    attr_reader :errors

    def initialize(root:, phase_dir:, require_owner: OWNER)
      @root = Pathname(root).realpath
      @phase_dir = canonical_relative(phase_dir)
      @required_owner = require_owner
      @errors = []
      @documents_for_scan = []
      @root_result_cache = nil
    rescue Errno::ENOENT
      @errors = ["REPOSITORY_ROOT_MISSING"]
    end

    def validate
      return self unless @errors.empty?

      validate_contract_schemas
      catalog = parse_catalog
      matrix = parse_matrix
      validate_trace_sources(catalog, matrix)

      manifest_relative = File.join(@phase_dir, "EVIDENCE/evidence-manifest.json")
      manifest = load_json(manifest_relative, "MANIFEST")
      return self unless manifest

      @documents_for_scan << ["MANIFEST", manifest]
      exact_fields(manifest, MANIFEST_FIELDS, "MANIFEST")
      validate_common_header(manifest, MANIFEST_SCHEMA, "MANIFEST")
      error("MANIFEST_STATUS_INVALID") unless manifest["status"] == "PASS"

      subject = validate_subject_reference(manifest["subject"], "MANIFEST_SUBJECT")
      inventory = validate_inventory_reference(manifest["inventory"], subject, "MANIFEST_INVENTORY")
      leak = validate_leak_reference(manifest["leak_result"], subject, "MANIFEST_LEAK")
      validate_entries(manifest["entries"], subject, inventory, leak, catalog, matrix)
      @documents_for_scan.each { |label, value| scan_prohibited(value, label) }
      @errors.uniq!
      self
    rescue StandardError => exception
      @errors << "VALIDATOR_INTERNAL_ERROR: #{exception.class}: #{exception.message}"
      @errors.uniq!
      self
    end

    # Shared producer preflight: applies the same semantic inventory gate without materializing
    # evidence first. The producer inspects errors and writes nothing unless this remains empty.
    def validate_inventory_document(inventory)
      validate_inventory(inventory)
      @errors.uniq!
      self
    end

    # Shared producer preflight for sanitized child/aggregate/lane inputs.
    def validate_sanitized_documents(documents)
      Array(documents).each_with_index do |document, index|
        scan_prohibited(document, "PRODUCER_INPUT_#{index}")
      end
      @errors.uniq!
      self
    end

    private

    def validate_contract_schemas
      obligation = load_json(File.join(@phase_dir, "EVIDENCE/schema/phase03-obligation-evidence.schema.json"), "OBLIGATION_SCHEMA")
      manifest = load_json(File.join(@phase_dir, "EVIDENCE/schema/phase03-evidence-manifest.schema.json"), "MANIFEST_SCHEMA")
      if obligation
        error("OBLIGATION_SCHEMA_VERSION_MISMATCH") unless obligation.dig("properties", "schema_version", "const") == OBLIGATION_SCHEMA
        error("OBLIGATION_SCHEMA_CLOSED_OBJECT_REQUIRED") unless obligation["additionalProperties"] == false
      end
      if manifest
        error("MANIFEST_SCHEMA_VERSION_MISMATCH") unless manifest.dig("properties", "schema_version", "const") == MANIFEST_SCHEMA
        error("MANIFEST_SCHEMA_ENTRY_COUNT_MISMATCH") unless manifest.dig("properties", "entries", "minItems") == 4 && manifest.dig("properties", "entries", "maxItems") == 4
        error("MANIFEST_SCHEMA_CLOSED_OBJECT_REQUIRED") unless manifest["additionalProperties"] == false
      end
    end

    def parse_catalog
      rows = {}
      path = ".planning/PRD-OBLIGATIONS.md"
      snapshot = read_file(path, "CATALOG")
      return rows unless snapshot

      snapshot.lines(chomp: true).each do |line|
        next unless line.start_with?("- OBL-")

        cells = line.delete_prefix("- ").split("|").map(&:strip)
        next unless cells[3] == OWNER
        error("CATALOG_ROW_INVALID") and next unless cells.length == 9
        rows[cells[0]] = {
          "requirement_ids" => cells[2].split(",").sort,
          "behavior_id" => cells[4],
          "catalog_test" => cells[6],
          "evidence_path" => cells[7]
        }
      end
      error("CATALOG_EXACT_FOUR_MISMATCH") unless rows.keys == OBLIGATIONS.keys
      rows
    end

    def parse_matrix
      rows = {}
      snapshot = read_file(File.join(@phase_dir, "TEST-MATRIX.md"), "TEST_MATRIX")
      return rows unless snapshot

      snapshot.lines(chomp: true).each do |line|
        next unless line.start_with?("| OBL-")

        cells = line.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
        next unless OBLIGATIONS.key?(cells[0])
        error("TEST_MATRIX_ROW_INVALID: #{cells[0]}") and next unless cells.length == 11
        rows[cells[0]] = {
          "requirement_ids" => cells[1].split(",").sort,
          "behavior_id" => cells[2],
          "catalog_test" => cells[3],
          "case_id" => cells[7],
          "evidence_path" => cells[10].delete_prefix("`").delete_suffix("`")
        }
      end
      error("TEST_MATRIX_EXACT_FOUR_MISMATCH") unless rows.keys == OBLIGATIONS.keys
      rows
    end

    def validate_trace_sources(catalog, matrix)
      OBLIGATIONS.each do |obligation_id, expected|
        %w[requirement_ids behavior_id catalog_test evidence_path].each do |field|
          error("CATALOG_TRACE_MISMATCH: #{obligation_id} #{field}") unless catalog.dig(obligation_id, field) == expected[field]
          error("MATRIX_TRACE_MISMATCH: #{obligation_id} #{field}") unless matrix.dig(obligation_id, field) == expected[field]
        end
        error("MATRIX_TRACE_MISMATCH: #{obligation_id} case_id") unless matrix.dig(obligation_id, "case_id") == expected["case_id"]
      end
    end

    def validate_subject_reference(reference, label)
      exact_fields(reference, SUBJECT_REF_FIELDS, label)
      return nil unless reference.is_a?(Hash)
      error("#{label}_PATH_MISMATCH") unless reference["path"] ==
        File.join(@phase_dir, "EVIDENCE/tested-inputs.json")

      document, snapshot = load_bound_json(reference, label)
      return nil unless document
      exact_fields(document, TESTED_INPUT_FIELDS, "TESTED_INPUTS")
      error("TESTED_INPUTS_SCHEMA_MISMATCH") unless document["schema_version"] == TESTED_INPUTS_SCHEMA
      error("TESTED_INPUTS_PHASE_MISMATCH") unless document["phase"] == PHASE
      error("TESTED_INPUTS_OWNER_MISMATCH") unless document["owner"] == OWNER
      inputs = document["inputs"]
      unless inputs.is_a?(Array) && !inputs.empty?
        error("TESTED_INPUTS_EMPTY")
        return nil
      end
      paths = inputs.filter_map { |entry| entry["path"] if entry.is_a?(Hash) }
      error("TESTED_INPUTS_ORDER_INVALID") unless paths == paths.sort
      error("TESTED_INPUTS_DUPLICATE") unless paths.uniq.length == paths.length
      inputs.each_with_index do |entry, index|
        exact_fields(entry, INPUT_FIELDS, "TESTED_INPUT")
        next unless entry.is_a?(Hash)
        path = entry["path"]
        input_snapshot = read_file(path, "TESTED_INPUT index=#{index}")
        next unless input_snapshot
        stat = safe_path(path, "TESTED_INPUT index=#{index}")&.stat
        actual_mode = stat && format("%06o", stat.mode)
        error("TESTED_INPUT_MODE_MISMATCH: #{path}") unless entry["mode"] == actual_mode
        error("TESTED_INPUT_SHA256_MISMATCH: #{path}") unless entry["sha256"] == Digest::SHA256.hexdigest(input_snapshot)
        error("TESTED_INPUT_ROLE_INVALID: #{path}") unless %w[implementation test config contract validator].include?(entry["role"])
      end
      expected_subject = Phase3CryptoEvidence.digest_value(inputs)
      error("TESTED_SUBJECT_DIGEST_MISMATCH") unless reference["tested_subject_digest"] == expected_subject
      @documents_for_scan << [label, reference]
      @documents_for_scan << ["TESTED_INPUTS", document]
      { reference: reference, document: document, digest: expected_subject, snapshot: snapshot }
    end

    def validate_inventory_reference(reference, subject, label)
      exact_fields(reference, INVENTORY_REF_FIELDS, label)
      return nil unless reference.is_a?(Hash)
      error("#{label}_PATH_MISMATCH") unless reference["path"] ==
        "core/src/main/resources/security/protected-data-inventory.json"

      inventory, _snapshot = load_bound_json(reference, label)
      return nil unless inventory
      accepted_digest = Phase3CryptoEvidence.digest_value(inventory)
      error("INVENTORY_ACCEPTED_DIGEST_MISMATCH") unless reference["accepted_digest"] == accepted_digest
      validate_inventory(inventory)
      validator_result = validate_result_reference(
        reference["validator_result"], subject,
        expected_check: "phase03-protected-inventory",
        expected_obligations: OBLIGATIONS.keys,
        expected_cases: OBLIGATIONS.values.map { |definition| definition.fetch("case_id") },
        expected_layer: "static",
        required_adapters: ["phase03-inventory-validator"],
        expected_obligation: nil,
        label: "INVENTORY_VALIDATOR_RESULT"
      )
      if validator_result
        facts = validator_result["facts"]
        error("INVENTORY_VALIDATOR_FACTS_INVALID") unless facts == {
          "blocking_dispositions" => [], "inventory_digest" => accepted_digest, "result" => "ACCEPTED"
        }
      end
      @documents_for_scan << [label, reference]
      { reference: reference, document: inventory, digest: accepted_digest, snapshot: _snapshot }
    end

    def validate_inventory(inventory)
      error("INVENTORY_VERSION_INVALID") unless inventory["manifest_version"] == "ycs-protected-data-inventory/v1"
      error("INVENTORY_REVIEW_REQUIRED") if recursive_value?(inventory, "REVIEW_REQUIRED")
      readiness = inventory["obligation_readiness"]
      unless readiness.is_a?(Hash) && readiness["status"] == "READY" && readiness["blocking_surface_ids"] == []
        error("INVENTORY_NOT_ACCEPTED")
      end

      targets = Array(inventory["targets"])
      target_ids = targets.filter_map { |row| row["id"] if row.is_a?(Hash) }.to_set
      REQUIRED_NO_INDEX_TARGETS.each do |target_id|
        error("INVENTORY_PROTECTED_TARGET_MISSING: #{target_id}") unless target_ids.include?(target_id)
        row = targets.find { |candidate| candidate["id"] == target_id }
        error("INVENTORY_NO_INDEX_DISPOSITION_INVALID: #{target_id}") unless row && row["blind_index"] == "EXCLUDED_NO_EQUALITY_CONTRACT"
      end
      targets.each do |row|
        next unless row.is_a?(Hash)
        id = row["id"]
        error("INVENTORY_CAPACITY_CONFLICT: #{id}") unless row["capacity_result"] == "FITS"
        error("INVENTORY_TARGET_DEFERRED: #{id}") if row["migration_state"] == "DEFERRED_OWNER"
        Array(row["writers"]).each do |source|
          error("INVENTORY_RAW_URL_WRITER: #{id}") if raw_url_writer_source?(source)
          validate_source_reference(source, "INVENTORY_WRITER: #{id}")
        end
        Array(row["readers"]).each { |source| validate_source_reference(source, "INVENTORY_READER: #{id}") }
      end
      Array(inventory["digest_targets"]).each do |row|
        next unless row.is_a?(Hash)
        error("INVENTORY_DIGEST_DEFERRED: #{row['id']}") if row["migration_state"] == "DEFERRED_OWNER"
      end
      Array(inventory["candidates"]).each do |row|
        next unless row.is_a?(Hash) && row["classification"] == "DEFERRED_OWNER"
        if row["executable"] || row["migratable"] || row["future_owner"].to_s.empty?
          error("INVENTORY_CURRENT_DEFERRED: #{row['id']}")
        end
      end

      surfaces = Array(inventory["source_surfaces"])
      ids = surfaces.filter_map { |row| row["id"] if row.is_a?(Hash) }.to_set
      error("INVENTORY_SOURCE_SURFACE_SET_INVALID") unless ids == REQUIRED_SOURCE_SURFACES
      surfaces.each do |surface|
        next unless surface.is_a?(Hash)
        id = surface["id"]
        if surface["obligation_blocking"] != false || surface["disposition"] != "PROTECTED_BOUNDARY_ADOPTED"
          error("INVENTORY_SOURCE_SURFACE_UNRESOLVED: #{id}")
        end
        Array(surface["sources"]).each do |source|
          error("INVENTORY_RAW_URL_WRITER: #{id}") if raw_url_writer_source?(source)
          validate_source_reference(source, "INVENTORY_SOURCE_SURFACE: #{id}")
        end
      end
    end

    def validate_source_reference(source, label)
      unless source.is_a?(Hash) && source.keys.map(&:to_s).to_set == Set.new(%w[path tokens])
        error("#{label}_INVALID")
        return
      end
      error("#{label}_PATH_INVALID") unless canonical_relative?(source["path"])
      tokens = source["tokens"]
      error("#{label}_TOKENS_INVALID") unless tokens.is_a?(Array) && !tokens.empty? && tokens.all? { |token| token.is_a?(String) && !token.empty? }
    end

    def raw_url_writer_source?(source)
      Array(source.is_a?(Hash) ? source["tokens"] : []).any? do |token|
        token.match?(/(?:set|String\s+)(?:BusinessLicense|LegalRepIdFront|LegalRepIdBack|ShortlinkDomainProof|TrademarkProof|Evidence|File)Url/)
      end
    end

    def validate_leak_reference(reference, subject, label)
      exact_fields(reference, LEAK_REF_FIELDS, label)
      return nil unless reference.is_a?(Hash)
      error("#{label}_PATH_MISMATCH") unless reference["path"] ==
        File.join(ROOT_RESULT_DIR, "complete-leak-result.json")

      leak, snapshot = load_bound_json(reference, label)
      return nil unless leak
      exact_fields(leak, LEAK_FIELDS, "LEAK_RESULT")
      error("LEAK_SCHEMA_MISMATCH") unless leak["schema_version"] == LEAK_RESULT_SCHEMA
      error("LEAK_PHASE_MISMATCH") unless leak["phase"] == PHASE
      error("LEAK_CHECK_ID_MISMATCH") unless leak["check_id"] == "phase03-complete-leak-scan"
      error("LEAK_SUBJECT_MISMATCH") unless subject && leak["subject_digest"] == subject[:digest]
      error("LEAK_STATUS_INVALID") unless leak["status"] == "PASS" && leak["exit_code"] == 0
      error("LEAK_RESULT_DIGEST_MISMATCH") unless leak["result_digest"] == Phase3CryptoEvidence.result_digest(leak)
      error("LEAK_REFERENCE_DIGEST_MISMATCH") unless reference["result_digest"] == leak["result_digest"]
      targets = leak["targets"]
      unless targets.is_a?(Array)
        error("LEAK_TARGETS_INVALID")
        return nil
      end
      ids = targets.filter_map { |target| target["id"] if target.is_a?(Hash) }
      error("LEAK_TARGET_SET_INVALID") unless ids == LEAK_TARGETS.to_a.sort
      targets.each do |target|
        exact_fields(target, LEAK_TARGET_FIELDS, "LEAK_TARGET")
        next unless target.is_a?(Hash)
        expected_reader = LEAK_READER_IDENTITIES[target["id"]]
        error("LEAK_READER_IDENTITY_INVALID: #{target['id']}") unless expected_reader && target["reader_identity"] == expected_reader
        error("LEAK_SCANNED_COUNT_INVALID: #{target['id']}") unless target["scanned_items"].is_a?(Integer) && target["scanned_items"].positive?
        error("LEAK_PROHIBITED_MATCH: #{target['id']}") unless target["prohibited_matches"] == 0
        error("LEAK_SENSITIVITY_INVALID: #{target['id']}") unless target["sensitivity_status"] == "DETECTED_SEEDED_MUTATION"
      end
      subject_inputs = Array(subject&.dig(:document, "inputs"))
      if subject_inputs.any? { |input| input.is_a?(Hash) && input["path"] == reference["path"] }
        error("LEAK_SELF_ATTESTATION_REJECTED")
      end
      @documents_for_scan << [label, reference]
      @documents_for_scan << ["LEAK_RESULT", leak]
      { reference: reference, document: leak, digest: leak["result_digest"], snapshot: snapshot }
    end

    def validate_entries(entries, subject, inventory, leak, catalog, matrix)
      unless entries.is_a?(Array)
        error("MANIFEST_ENTRIES_INVALID")
        return
      end
      error("MANIFEST_ENTRY_COUNT_MISMATCH") unless entries.length == 4
      ids = entries.filter_map { |entry| entry["obligation_id"] if entry.is_a?(Hash) }
      error("MANIFEST_ENTRY_SET_MISMATCH") unless ids == OBLIGATIONS.keys
      error("MANIFEST_ENTRY_DUPLICATE") unless ids.uniq.length == ids.length
      entries.each do |entry|
        exact_fields(entry, ENTRY_FIELDS, "MANIFEST_ENTRY")
        next unless entry.is_a?(Hash)
        obligation_id = entry["obligation_id"]
        expected = OBLIGATIONS[obligation_id]
        next error("MANIFEST_ENTRY_OBLIGATION_UNKNOWN: #{obligation_id}") unless expected
        expected_relative = File.join(@phase_dir, expected["evidence_path"])
        error("MANIFEST_ENTRY_PATH_MISMATCH: #{obligation_id}") unless entry["path"] == expected_relative
        evidence, = load_bound_json(entry, "MANIFEST_ENTRY #{obligation_id}")
        next unless evidence
        error("MANIFEST_ENTRY_STATUS_INVALID: #{obligation_id}") unless entry["status"] == "PASS"
        error("MANIFEST_ENTRY_EVIDENCE_DIGEST_MISMATCH: #{obligation_id}") unless entry["evidence_digest"] == Phase3CryptoEvidence.digest_value(evidence)
        validate_evidence(evidence, obligation_id, expected, subject, inventory, leak, catalog, matrix)
        @documents_for_scan << ["OBLIGATION_EVIDENCE #{obligation_id}", evidence]
      end
    end

    def validate_evidence(evidence, obligation_id, expected, subject, inventory, leak, catalog, matrix)
      exact_fields(evidence, EVIDENCE_FIELDS, "OBLIGATION_EVIDENCE")
      validate_common_header(evidence, OBLIGATION_SCHEMA, "OBLIGATION_EVIDENCE")
      error("OBLIGATION_ID_MISMATCH: #{obligation_id}") unless evidence["obligation_id"] == obligation_id
      %w[requirement_ids behavior_id catalog_test case_id evidence_path].each do |field|
        error("OBLIGATION_TRACE_MISMATCH: #{obligation_id} #{field}") unless evidence[field] == expected[field]
      end
      %w[requirement_ids behavior_id catalog_test evidence_path].each do |field|
        error("OBLIGATION_CATALOG_MISMATCH: #{obligation_id} #{field}") unless evidence[field] == catalog.dig(obligation_id, field)
        error("OBLIGATION_MATRIX_MISMATCH: #{obligation_id} #{field}") unless evidence[field] == matrix.dig(obligation_id, field)
      end
      error("OBLIGATION_MATRIX_MISMATCH: #{obligation_id} case_id") unless evidence["case_id"] == matrix.dig(obligation_id, "case_id")
      error("OBLIGATION_STATUS_INVALID: #{obligation_id}") unless evidence["status"] == "PASS" && evidence["exit_code"] == 0
      error("OBLIGATION_SUBJECT_BINDING_MISMATCH: #{obligation_id}") unless subject && evidence["subject"] == subject[:reference]
      error("OBLIGATION_INVENTORY_BINDING_MISMATCH: #{obligation_id}") unless inventory && evidence["inventory"] == inventory[:reference]
      error("OBLIGATION_LEAK_BINDING_MISMATCH: #{obligation_id}") unless leak && evidence["leak_result"] == leak[:reference]

      results = evidence["child_results"]
      unless results.is_a?(Array) && !results.empty?
        error("OBLIGATION_CHILD_RESULTS_EMPTY: #{obligation_id}")
        return
      end
      check_ids = results.filter_map { |result| result["check_id"] if result.is_a?(Hash) }
      error("OBLIGATION_CHILD_RESULT_SET_INVALID: #{obligation_id}") unless check_ids == [expected["check_id"]]
      validate_result_reference(
        results.first, subject,
        expected_check: expected["check_id"],
        expected_obligations: [obligation_id],
        expected_cases: [expected["case_id"]],
        expected_layer: expected["layer"],
        required_adapters: expected["adapters"],
        expected_obligation: obligation_id,
        label: "OBLIGATION_CHILD_RESULT #{obligation_id}"
      )
    end

    def validate_result_reference(reference, subject, expected_check:, expected_obligations:, expected_cases:, expected_layer:, required_adapters:, expected_obligation:, label:)
      exact_fields(reference, RESULT_REF_FIELDS, label)
      return nil unless reference.is_a?(Hash)
      expected_path = File.join(
        ROOT_RESULT_DIR,
        expected_obligation ? "#{expected_check}.json" : "protected-inventory-result.json"
      )
      error("#{label}_PATH_MISMATCH") unless reference["path"] == expected_path
      result, = load_bound_json(reference, label)
      return nil unless result
      exact_fields(result, CHILD_FIELDS, label)
      error("#{label}_SCHEMA_MISMATCH") unless result["schema_version"] == CHILD_RESULT_SCHEMA
      error("#{label}_PHASE_MISMATCH") unless result["phase"] == PHASE
      error("#{label}_CHECK_ID_MISMATCH") unless result["check_id"] == expected_check && reference["check_id"] == expected_check
      error("#{label}_LAYER_MISMATCH") unless result["layer"] == expected_layer
      error("#{label}_OBLIGATION_SET_MISMATCH") unless result["obligation_ids"] == expected_obligations.sort
      error("#{label}_CASE_SET_MISMATCH") unless result["case_ids"] == expected_cases.sort
      error("#{label}_SUBJECT_MISMATCH") unless subject && result["subject_digest"] == subject[:digest]
      error("#{label}_STATUS_INVALID") unless result["status"] == "PASS" && result["exit_code"] == 0
      actual_digest = Phase3CryptoEvidence.result_digest(result)
      error("#{label}_RESULT_DIGEST_MISMATCH") unless result["result_digest"] == actual_digest
      error("#{label}_REFERENCE_DIGEST_MISMATCH") unless reference["result_digest"] == actual_digest
      validate_adapters(result["adapter_identities"], required_adapters, label)
      error("#{label}_FACTS_INVALID") unless result["facts"].is_a?(Hash) && !result["facts"].empty?
      validate_child_lane_facts(result, subject, expected_obligation, label) if expected_obligation
      @documents_for_scan << [label, reference]
      @documents_for_scan << ["#{label}_DOCUMENT", result]
      result
    end

    def validate_child_lane_facts(result, subject, obligation_id, label)
      facts = result["facts"]
      exact_fields(facts, CHILD_LANE_FACT_FIELDS, "#{label}_FACTS")
      return unless facts.is_a?(Hash)

      contract = root_contract
      binding = contract.const_get(:CHILD_BINDINGS).fetch(obligation_id)
      required = binding.fetch("lanes")
      digests = facts["lane_result_digests"]
      error("#{label}_AGGREGATE_STATUS_INVALID") unless facts["aggregate_status"] == "PASS"
      error("#{label}_REQUIRED_LANES_MISMATCH") unless facts["required_lane_ids"] == required
      error("#{label}_DURABLE_LANE_MISSING") unless required.include?("durable-artifact-leak-scan")
      error("#{label}_CLEANUP_LANE_MISSING") unless required.include?("fixture-cleanup")
      unless digests.is_a?(Hash) && digests.keys.sort == required.sort &&
             digests.values.all? { |digest| digest.is_a?(String) && digest.match?(SHA256) }
        error("#{label}_LANE_DIGEST_SET_MISMATCH")
      end

      root = validate_root_results(subject, contract)
      return unless root
      error("#{label}_REGISTRY_DIGEST_MISMATCH") unless facts["registry_digest"] == root[:registry_digest]
      error("#{label}_LANE_DIGEST_MISMATCH") unless digests == root[:lane_digests].slice(*required)
    end

    def validate_root_results(subject, contract)
      return @root_result_cache if @root_result_cache

      aggregate = load_root_json(File.join(ROOT_RESULT_DIR, "aggregate.json"), "ROOT_AGGREGATE")
      return nil unless aggregate
      exact_fields(aggregate, ROOT_AGGREGATE_FIELDS, "ROOT_AGGREGATE")
      checks = contract.const_get(:CHECKS)
      expected_lane_ids = checks.map { |definition| definition.fetch("id") }
      expected_registry = contract.digest(checks)
      lane_digests = aggregate["lane_result_digests"]
      error("ROOT_AGGREGATE_SCHEMA_MISMATCH") unless aggregate["schema_version"] == ROOT_AGGREGATE_SCHEMA
      error("ROOT_AGGREGATE_PHASE_MISMATCH") unless aggregate["phase"] == PHASE
      error("ROOT_AGGREGATE_STATUS_INVALID") unless aggregate["status"] == "PASS"
      error("ROOT_AGGREGATE_SUBJECT_MISMATCH") unless subject && aggregate["tested_subject_digest"] == subject[:digest]
      error("ROOT_AGGREGATE_REGISTRY_MISMATCH") unless aggregate["registry_digest"] == expected_registry
      error("ROOT_AGGREGATE_RESULT_DIGEST_MISMATCH") unless aggregate["result_digest"] == contract.digest(aggregate.reject { |key, _| key == "result_digest" })
      unless lane_digests.is_a?(Hash) && lane_digests.keys.sort == expected_lane_ids.sort &&
             lane_digests.values.all? { |digest| digest.is_a?(String) && digest.match?(SHA256) }
        error("ROOT_AGGREGATE_LANE_SET_MISMATCH")
        return nil
      end

      checks.each do |definition|
        id = definition.fetch("id")
        lane = load_root_json(File.join(ROOT_RESULT_DIR, "lanes", "#{id}.json"), "ROOT_LANE #{id}")
        next unless lane
        exact_fields(lane, ROOT_LANE_FIELDS, "ROOT_LANE #{id}")
        error("ROOT_LANE_SCHEMA_MISMATCH: #{id}") unless lane["schema_version"] == ROOT_RESULT_SCHEMA
        error("ROOT_LANE_CHECK_ID_MISMATCH: #{id}") unless lane["check_id"] == id
        error("ROOT_LANE_LAYER_MISMATCH: #{id}") unless lane["layer"] == definition.fetch("layer")
        error("ROOT_LANE_MODE_MISMATCH: #{id}") unless lane["mode"] == definition.fetch("mode")
        error("ROOT_LANE_ARGV_MISMATCH: #{id}") unless lane["argv"] == definition.fetch("argv")
        error("ROOT_LANE_STATUS_INVALID: #{id}") unless lane.values_at("status", "exit_code", "error_id") == ["PASS", 0, nil]
        error("ROOT_LANE_DIAGNOSTIC_INVALID: #{id}") unless lane["diagnostic_sha256"].is_a?(String) && lane["diagnostic_sha256"].match?(SHA256)
        %w[started_at completed_at].each do |field|
          begin
            Time.iso8601(lane.fetch(field))
          rescue ArgumentError, KeyError, TypeError
            error("ROOT_LANE_TIMESTAMP_INVALID: #{id} #{field}")
          end
        end
        actual = contract.digest(lane.reject { |key, _| key == "result_digest" })
        error("ROOT_LANE_RESULT_DIGEST_MISMATCH: #{id}") unless lane["result_digest"] == actual
        error("ROOT_LANE_AGGREGATE_DIGEST_MISMATCH: #{id}") unless lane_digests[id] == actual
      end
      @documents_for_scan << ["ROOT_AGGREGATE", aggregate]
      @root_result_cache = { registry_digest: expected_registry, lane_digests: lane_digests }
    end

    def load_root_json(relative, label)
      snapshot = read_file(relative, label)
      return nil unless snapshot
      JSON.parse(snapshot)
    rescue JSON::ParserError
      error("#{label}_JSON_INVALID")
      nil
    end

    def root_contract
      require_relative "../../scripts/lib/phase-03/run_checks"
      Phase03RunChecks.registry_contract!
      Phase03RunChecks
    rescue Phase03RunChecks::ConfigurationError
      error("ROOT_REGISTRY_INVALID")
      Phase03RunChecks
    end

    def validate_adapters(adapters, required, label)
      unless adapters.is_a?(Array)
        error("#{label}_ADAPTERS_INVALID")
        return
      end
      ids = adapters.filter_map { |adapter| adapter["id"] if adapter.is_a?(Hash) }
      error("#{label}_ADAPTER_SET_MISMATCH") unless ids.sort == required.sort && ids.uniq.length == ids.length
      adapters.each do |adapter|
        exact_fields(adapter, ADAPTER_FIELDS, "#{label}_ADAPTER")
        next unless adapter.is_a?(Hash)
        expected_mode = ADAPTER_IDENTITIES[adapter["id"]]
        error("#{label}_ADAPTER_IDENTITY_UNKNOWN: #{adapter['id']}") unless expected_mode
        error("#{label}_ADAPTER_MODE_MISMATCH: #{adapter['id']}") unless adapter["mode"] == expected_mode
      end
    end

    def validate_common_header(document, schema, label)
      return unless document.is_a?(Hash)
      error("#{label}_SCHEMA_MISMATCH") unless document["schema_version"] == schema
      error("#{label}_PHASE_MISMATCH") unless document["phase"] == PHASE
      error("#{label}_OWNER_MISMATCH") unless document["owner"] == OWNER && @required_owner == OWNER
    end

    def load_bound_json(reference, label)
      return [nil, nil] unless reference.is_a?(Hash)
      path = reference["path"]
      snapshot = read_file(path, label)
      return [nil, nil] unless snapshot
      error("#{label}_SHA256_INVALID") unless reference["sha256"].is_a?(String) && reference["sha256"].match?(SHA256)
      error("#{label}_SHA256_MISMATCH") unless reference["sha256"] == Digest::SHA256.hexdigest(snapshot)
      [parse_json(snapshot, label), snapshot]
    end

    def load_json(relative, label)
      snapshot = read_file(relative, label)
      snapshot && parse_json(snapshot, label)
    end

    def parse_json(snapshot, label)
      JSON.parse(snapshot)
    rescue JSON::ParserError, EncodingError => exception
      error("#{label}_JSON_INVALID: #{exception.message}")
      nil
    end

    def read_file(relative, label)
      path = safe_path(relative, label)
      return nil unless path
      stat = path.lstat
      unless stat.file? && stat.nlink == 1 && stat.size <= FILE_LIMIT
        error("#{label}_FILE_INVALID: #{relative}")
        return nil
      end
      path.binread
    rescue Errno::ENOENT, Errno::ENOTDIR
      error("#{label}_MISSING: #{relative}")
      nil
    end

    def safe_path(relative, label)
      unless canonical_relative?(relative)
        error("#{label}_PATH_INVALID: #{relative.inspect}")
        return nil
      end
      candidate = @root.join(relative).cleanpath
      unless candidate.to_s.start_with?("#{@root}#{File::SEPARATOR}")
        error("#{label}_PATH_OUTSIDE_ROOT: #{relative}")
        return nil
      end
      current = candidate
      until current == @root
        if current.exist? && current.lstat.symlink?
          error("#{label}_SYMLINK_REJECTED: #{relative}")
          return nil
        end
        current = current.parent
      end
      candidate
    end

    def canonical_relative(value)
      raise ArgumentError, "PHASE_DIR_INVALID" unless canonical_relative?(value)
      Pathname(value).cleanpath.to_s
    end

    def canonical_relative?(value)
      return false unless value.is_a?(String) && !value.empty? && !value.include?("\0") && !value.include?("\\")
      path = Pathname(value)
      !path.absolute? && path.cleanpath.to_s == value && path.each_filename.none? { |part| part == ".." }
    end

    def exact_fields(value, expected, label)
      unless value.is_a?(Hash)
        error("#{label}_TYPE_INVALID")
        return
      end
      actual = value.keys.map(&:to_s).to_set
      missing = expected - actual
      unknown = actual - expected
      error("#{label}_FIELDS_MISSING: #{missing.to_a.sort.join(',')}") unless missing.empty?
      error("#{label}_FIELDS_UNKNOWN: #{unknown.to_a.sort.join(',')}") unless unknown.empty?
    end

    def recursive_value?(value, wanted)
      case value
      when Hash then value.any? { |key, nested| key == wanted || recursive_value?(nested, wanted) }
      when Array then value.any? { |nested| recursive_value?(nested, wanted) }
      else value == wanted
      end
    end

    def scan_prohibited(value, label, path = [])
      case value
      when Hash
        value.each do |key, nested|
          key_text = key.to_s.downcase
          error("PROHIBITED_CONTENT_KEY: #{label} #{(path + [key.to_s]).join('.')}") if PROHIBITED_KEYS.include?(key_text)
          scan_prohibited(nested, label, path + [key.to_s])
        end
      when Array
        value.each_with_index { |nested, index| scan_prohibited(nested, label, path + [index.to_s]) }
      when String
        if value.start_with?("/") || value.match?(/\A[A-Za-z]:[\\\/]/)
          error("PROHIBITED_ABSOLUTE_PATH: #{label} #{path.join('.')}")
        end
        return if path.any? { |segment| segment.to_s.match?(/(?:sha256|digests?)\z/) }
        PROHIBITED_VALUE_PATTERNS.each do |token, pattern|
          error("PROHIBITED_#{token}: #{label} #{path.join('.')}") if value.match?(pattern)
        end
      end
    end

    def error(message)
      @errors << message
      false
    end
  end
end
