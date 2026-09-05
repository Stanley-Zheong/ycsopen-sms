#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "digest"
require "pathname"
require "set"

module Phase3ProtectedInventory
  MANIFEST_VERSION = "ycs-protected-data-inventory/v1"
  ENVELOPE_OVERHEAD = 145
  DATABASE_PLAINTEXT_CEILING = 110
  OPAQUE_OBJECT_ID_CEILING = 64

  INLINE_TARGETS = {
    "users.phone_encrypted" => 11,
    "tenants.legal_rep_id_no_encrypted" => 18,
    "tenants.contact_id_no_encrypted" => 18,
    "tenants.contact_phone_encrypted" => 11,
    "signatures.applicant_phone_encrypted" => 11,
    "signatures.applicant_id_no_encrypted" => 18,
    "channels.account_encrypted" => 110,
    "channels.password_encrypted" => 110,
    "mobile_portability.mobile_encrypted" => 11,
    "blacklist_entries.mobile_encrypted" => 11,
    "tenant_api_keys.app_secret_encrypted" => 110,
    "tenant_protocol_credentials.account_encrypted" => 110,
    "tenant_protocol_credentials.password_encrypted" => 110,
    "message_tasks.mobile_encrypted" => 11,
    "bulk_sending_items.mobile_encrypted" => 11,
    "uplink_records.mobile_encrypted" => 11,
    "unsubscribe_records.mobile_encrypted" => 11
  }.freeze

  OBJECT_TARGETS = {
    "tenants.business_license_url" => 10_485_760,
    "tenants.legal_rep_id_front_url" => 5_242_880,
    "tenants.legal_rep_id_back_url" => 5_242_880,
    "tenants.shortlink_domain_proof_url" => 10_485_760,
    "tenants.trademark_proof_url" => 10_485_760,
    "signatures.evidence_url" => 10_485_760,
    "export_tasks.file_url" => 10_485_760
  }.freeze

  DIGEST_TARGETS = Set[
    "mobile_portability.mobile_hash",
    "blacklist_entries.mobile_hash",
    "third_party_risk_check_logs.mobile_hash",
    "message_tasks.mobile_hash",
    "unsubscribe_records.mobile_hash"
  ].freeze

  REQUIRED_NO_INDEX_TARGETS = Set[
    "bulk_sending_items.mobile_encrypted",
    "uplink_records.mobile_encrypted"
  ].freeze

  MIGRATION_TARGETS = {
    "BLACKLIST_ENTRY" => true,
    "MESSAGE_TASK" => true,
    "MOBILE_PORTABILITY" => true,
    "THIRD_PARTY_RISK_CHECK_LOG" => true,
    "UNSUBSCRIBE_RECORD" => true,
    "BULK_SENDING_ITEM_MOBILE" => false,
    "UPLINK_RECORD_MOBILE" => false
  }.freeze
  MIGRATION_EVIDENCE_KEYS = Set[
    "schema_version", "accepted_pair_digest", "v1_sha256", "target_count",
    "complete_target_count", "blocking_target_count", "indexed_target_set_digest", "targets"
  ].freeze
  MIGRATION_TARGET_KEYS = Set[
    "target_type", "target_state", "legacy_fallback_allowed",
    "checkpoint_count", "event_count", "blind_index_count"
  ].freeze

  CANDIDATE_COLUMNS = Set[
    "users.password_hash",
    "users.avatar_url",
    "tenants.unified_social_credit_code",
    "tenants.legal_rep_name",
    "tenants.contact_name",
    "tenants.registered_address",
    "tenants.business_address",
    "tenants.contract_attachment_url",
    "tenant_callback_configs.delivery_callback_url",
    "tenant_callback_configs.uplink_callback_url",
    "tenant_callback_configs.unsubscribe_callback_url",
    "delivery_reports.raw_payload",
    "uplink_records.push_url",
    "short_links.target_url",
    "operation_logs.request_url"
  ].freeze

  REQUIRED_SURFACES = Set[
    "message-submit-persistence",
    "tenant-registration-persistence",
    "auth-user-hydration-save",
    "hmac-api-key-hydration",
    "blacklist-lookup-hydration",
    "tenant-lifecycle-analytics-hydration-save"
  ].freeze

  TOP_LEVEL_KEYS = Set[
    "manifest_version", "manifest_schema", "envelope_contract", "targets",
    "digest_targets", "candidates", "source_surfaces", "obligation_readiness"
  ].freeze
  TARGET_KEYS = Set[
    "id", "table", "column", "kind", "classification", "storage_representation",
    "source_bound_bytes", "maximum_complete_envelope_bytes", "storage_capacity_bytes",
    "capacity_result", "migration_state", "blind_index", "tenant_column",
    "identity_column", "readers", "writers", "reason"
  ].freeze
  DIGEST_KEYS = Set["id", "table", "column", "classification", "migration_state", "reason"].freeze
  CANDIDATE_KEYS = Set[
    "id", "table", "column", "classification", "executable", "migratable",
    "future_owner", "sources", "reason"
  ].freeze
  SURFACE_KEYS = Set[
    "id", "role", "disposition", "obligation_blocking", "sources", "reason"
  ].freeze
  SOURCE_KEYS = Set["path", "tokens"].freeze

  Column = Struct.new(:table, :name, :type, :capacity, :source, keyword_init: true) do
    def id
      "#{table}.#{name}"
    end
  end

  class Validator
    attr_reader :errors, :blocking_surfaces

    def initialize(root:, manifest_path:, sql_schema_path:, source_root:, acceptance:)
      @root = Pathname(root).realpath
      @manifest_path = Pathname(manifest_path).expand_path(@root)
      @sql_schema_path = Pathname(sql_schema_path).expand_path(@root)
      @source_root = Pathname(source_root).expand_path(@root)
      @acceptance = acceptance
      @errors = []
      @blocking_surfaces = []
    end

    def validate
      manifest = load_json(@manifest_path, "MANIFEST")
      return self unless manifest

      validate_exact_keys(manifest, TOP_LEVEL_KEYS, "MANIFEST")
      validate_contract_files(manifest)
      columns = parse_sql_schema
      validate_targets(manifest.fetch("targets", []), columns)
      validate_digest_targets(manifest.fetch("digest_targets", []), columns)
      validate_candidates(manifest.fetch("candidates", []), columns)
      validate_source_surfaces(manifest.fetch("source_surfaces", []))
      validate_source_discovery(manifest)
      validate_obligation_readiness(manifest)
      self
    rescue KeyError => error
      @errors << "MANIFEST_REQUIRED_FIELD_MISSING: #{error.message}"
      self
    end

    private

    def load_json(path, label)
      JSON.parse(path.read(encoding: "UTF-8"))
    rescue Errno::ENOENT
      @errors << "#{label}_MISSING: #{path}"
      nil
    rescue JSON::ParserError, EncodingError => error
      @errors << "#{label}_JSON_INVALID: #{error.message}"
      nil
    end

    def validate_contract_files(manifest)
      @errors << "MANIFEST_VERSION_INVALID" unless manifest["manifest_version"] == MANIFEST_VERSION

      schema_path = safe_repository_path(manifest["manifest_schema"], "MANIFEST_SCHEMA")
      schema = load_json(schema_path, "MANIFEST_SCHEMA") if schema_path
      if schema
        @errors << "MANIFEST_SCHEMA_ID_INVALID" unless schema["$id"] == MANIFEST_VERSION
        required_properties = %w[targets digest_targets candidates source_surfaces obligation_readiness]
        missing = required_properties - schema.fetch("properties", {}).keys
        @errors << "MANIFEST_SCHEMA_PROPERTIES_MISSING: #{missing.join(',')}" unless missing.empty?
      end

      contract = manifest["envelope_contract"]
      unless contract.is_a?(Hash)
        @errors << "ENVELOPE_CONTRACT_INVALID"
        return
      end
      validate_exact_keys(
        contract,
        Set["path", "version", "maximum_overhead_bytes", "database_plaintext_ceiling_bytes", "opaque_object_id_ceiling_bytes"],
        "ENVELOPE_CONTRACT"
      )
      contract_path = safe_repository_path(contract["path"], "ENVELOPE_CONTRACT")
      @errors << "ENVELOPE_CONTRACT_MISSING: #{contract_path}" if contract_path && !contract_path.file?
      @errors << "ENVELOPE_VERSION_INVALID" unless contract["version"] == "YCSE/v1"
      @errors << "ENVELOPE_OVERHEAD_INVALID" unless contract["maximum_overhead_bytes"] == ENVELOPE_OVERHEAD
      unless contract["database_plaintext_ceiling_bytes"] == DATABASE_PLAINTEXT_CEILING
        @errors << "DATABASE_PLAINTEXT_CEILING_INVALID"
      end
      unless contract["opaque_object_id_ceiling_bytes"] == OPAQUE_OBJECT_ID_CEILING
        @errors << "OPAQUE_OBJECT_ID_CEILING_INVALID"
      end
    end

    def parse_sql_schema
      sql = @sql_schema_path.read(encoding: "UTF-8")
      columns = {}
      sql.scan(/CREATE TABLE\s+`?(\w+)`?\s*\((.*?)\)\s*ENGINE=/mi) do |table, body|
        body.each_line do |line|
          match = line.match(/^\s*`?(\w+)`?\s+([A-Z]+)(?:\((\d+)\))?/i)
          next unless match

          name = match[1]
          type = match[2].upcase
          capacity = match[3]&.to_i
          column = Column.new(table: table, name: name, type: type, capacity: capacity, source: line.strip)
          columns[column.id] = column
        end
      end
      @errors << "SQL_SCHEMA_EMPTY" if columns.empty?
      columns
    rescue Errno::ENOENT
      @errors << "SQL_SCHEMA_MISSING: #{@sql_schema_path}"
      {}
    rescue EncodingError => error
      @errors << "SQL_SCHEMA_ENCODING_INVALID: #{error.message}"
      {}
    end

    def validate_targets(targets, columns)
      unless targets.is_a?(Array)
        @errors << "TARGETS_INVALID"
        return
      end
      validate_unique_records(targets, "TARGET")
      actual_ids = targets.filter_map { |target| target["id"] }.to_set
      expected_ids = INLINE_TARGETS.keys.to_set | OBJECT_TARGETS.keys.to_set
      report_set_difference("PROTECTED_TARGET", expected_ids, actual_ids)
      discovered_inline = columns.values.select { |column| column.type == "VARBINARY" }.map(&:id).to_set
      report_set_difference("DISCOVERED_INLINE_TARGET", INLINE_TARGETS.keys.to_set, discovered_inline)

      targets.each do |target|
        validate_exact_keys(target, TARGET_KEYS, "TARGET")
        id = target["id"]
        next unless id

        @errors << "TARGET_ID_MISMATCH: #{id}" unless id == "#{target['table']}.#{target['column']}"
        column = columns[id]
        unless column
          @errors << "TARGET_SCHEMA_COLUMN_MISSING: #{id}"
          next
        end
        @errors << "TARGET_CLASSIFICATION_INVALID: #{id}" unless target["classification"] == "PROTECTED"
        @errors << "TARGET_CAPACITY_RESULT_INVALID: #{id}" unless target["capacity_result"] == "FITS"
        @errors << "TARGET_REVIEW_REQUIRED: #{id}" if target.values.include?("REVIEW_REQUIRED")
        if target["migration_state"] == "DEFERRED_OWNER"
          @errors << "TARGET_DEFERRED_FORBIDDEN: #{id}"
        end
        validate_source_refs(target["readers"], "TARGET_READER: #{id}")
        validate_source_refs(target["writers"], "TARGET_WRITER: #{id}")

        if INLINE_TARGETS.key?(id)
          expected_bound = INLINE_TARGETS.fetch(id)
          @errors << "TARGET_KIND_INVALID: #{id}" unless target["kind"] == "DATABASE_FIELD"
          unless target["storage_representation"] == "INLINE_ENVELOPE"
            @errors << "TARGET_REPRESENTATION_INVALID: #{id}"
          end
          @errors << "TARGET_SCHEMA_TYPE_INVALID: #{id}" unless column.type == "VARBINARY"
          @errors << "TARGET_SOURCE_BOUND_INVALID: #{id}" unless target["source_bound_bytes"] == expected_bound
          expected_envelope = expected_bound + ENVELOPE_OVERHEAD
          unless target["maximum_complete_envelope_bytes"] == expected_envelope
            @errors << "TARGET_ENVELOPE_CAPACITY_INVALID: #{id}"
          end
          unless target["storage_capacity_bytes"] == column.capacity && expected_envelope <= column.capacity.to_i
            @errors << "TARGET_DATABASE_CAPACITY_CONFLICT: #{id}"
          end
          expected_blind_index = if REQUIRED_NO_INDEX_TARGETS.include?(id)
                                   "EXCLUDED_NO_EQUALITY_CONTRACT"
                                 elsif DIGEST_TARGETS.include?(id.sub(/mobile_encrypted\z/, "mobile_hash"))
                                   "REQUIRED_VERSIONED_HMAC"
                                 else
                                   "NOT_APPLICABLE"
                                 end
          unless target["blind_index"] == expected_blind_index
            @errors << "TARGET_BLIND_INDEX_INVALID: #{id}"
          end
        elsif OBJECT_TARGETS.key?(id)
          expected_bound = OBJECT_TARGETS.fetch(id)
          @errors << "TARGET_KIND_INVALID: #{id}" unless target["kind"] == "PROTECTED_OBJECT_REFERENCE"
          unless target["storage_representation"] == "OPAQUE_PROTECTED_OBJECT_ID"
            @errors << "TARGET_REPRESENTATION_INVALID: #{id}"
          end
          @errors << "TARGET_SCHEMA_TYPE_INVALID: #{id}" unless column.type == "VARCHAR"
          @errors << "TARGET_SOURCE_BOUND_INVALID: #{id}" unless target["source_bound_bytes"] == expected_bound
          unless target["maximum_complete_envelope_bytes"] == expected_bound + ENVELOPE_OVERHEAD
            @errors << "TARGET_ENVELOPE_CAPACITY_INVALID: #{id}"
          end
          unless target["storage_capacity_bytes"] == column.capacity && OPAQUE_OBJECT_ID_CEILING <= column.capacity.to_i
            @errors << "TARGET_OBJECT_ID_CAPACITY_CONFLICT: #{id}"
          end
          @errors << "TARGET_BLIND_INDEX_INVALID: #{id}" unless target["blind_index"] == "NOT_APPLICABLE"
        end
      end
    end

    def validate_digest_targets(records, columns)
      unless records.is_a?(Array)
        @errors << "DIGEST_TARGETS_INVALID"
        return
      end
      validate_unique_records(records, "DIGEST_TARGET")
      actual_ids = records.filter_map { |record| record["id"] }.to_set
      report_set_difference("DIGEST_TARGET", DIGEST_TARGETS, actual_ids)
      discovered_digests = columns.values.select do |column|
        column.name == "mobile_hash" && column.type == "CHAR" && column.capacity == 64
      end.map(&:id).to_set
      report_set_difference("DISCOVERED_DIGEST_TARGET", DIGEST_TARGETS, discovered_digests)
      records.each do |record|
        validate_exact_keys(record, DIGEST_KEYS, "DIGEST_TARGET")
        id = record["id"]
        column = columns[id]
        @errors << "DIGEST_SCHEMA_COLUMN_MISSING: #{id}" unless column
        if column && !(column.type == "CHAR" && column.capacity == 64)
          @errors << "DIGEST_SCHEMA_SHAPE_INVALID: #{id}"
        end
        unless record["classification"] == "LEGACY_SHA256_MIGRATION_TARGET"
          @errors << "DIGEST_CLASSIFICATION_INVALID: #{id}"
        end
        if record["migration_state"] == "DEFERRED_OWNER" || record.values.include?("REVIEW_REQUIRED")
          @errors << "DIGEST_DISPOSITION_INVALID: #{id}"
        end
      end
    end

    def validate_candidates(records, columns)
      unless records.is_a?(Array)
        @errors << "CANDIDATES_INVALID"
        return
      end
      validate_unique_records(records, "CANDIDATE")
      actual_ids = records.filter_map { |record| record["id"] }.to_set
      discovered_ids = columns.values.select { |column| candidate_column?(column) }.map(&:id).to_set - OBJECT_TARGETS.keys.to_set
      report_set_difference("CANDIDATE", CANDIDATE_COLUMNS, actual_ids)
      report_set_difference("DISCOVERED_CANDIDATE", CANDIDATE_COLUMNS, discovered_ids)
      records.each do |record|
        validate_exact_keys(record, CANDIDATE_KEYS, "CANDIDATE")
        id = record["id"]
        @errors << "CANDIDATE_SCHEMA_COLUMN_MISSING: #{id}" unless columns.key?(id)
        classification = record["classification"]
        if classification == "REVIEW_REQUIRED" || classification.nil?
          @errors << "CANDIDATE_REVIEW_REQUIRED: #{id}"
        end
        unless %w[EXCLUDED_PHASE_5_HASH NOT_PROTECTED_WITH_REASON DEFERRED_OWNER].include?(classification)
          @errors << "CANDIDATE_CLASSIFICATION_INVALID: #{id}"
        end
        if classification == "DEFERRED_OWNER"
          if record["executable"] || record["migratable"] || record["future_owner"].to_s.empty?
            @errors << "CANDIDATE_DEFERRED_INVALID: #{id}"
          end
        elsif !record["future_owner"].nil?
          @errors << "CANDIDATE_FUTURE_OWNER_UNEXPECTED: #{id}"
        end
        validate_source_refs(record["sources"], "CANDIDATE_SOURCE: #{id}")
      end
    end

    def validate_source_surfaces(surfaces)
      unless surfaces.is_a?(Array)
        @errors << "SOURCE_SURFACES_INVALID"
        return
      end
      validate_unique_records(surfaces, "SOURCE_SURFACE")
      actual_ids = surfaces.filter_map { |surface| surface["id"] }.to_set
      report_set_difference("SOURCE_SURFACE", REQUIRED_SURFACES, actual_ids)
      surfaces.each do |surface|
        validate_exact_keys(surface, SURFACE_KEYS, "SOURCE_SURFACE")
        id = surface["id"]
        @errors << "SOURCE_SURFACE_DISPOSITION_INVALID: #{id}" if surface["disposition"].to_s.empty?
        @errors << "SOURCE_SURFACE_REVIEW_REQUIRED: #{id}" if surface.values.include?("REVIEW_REQUIRED")
        validate_source_refs(surface["sources"], "SOURCE_SURFACE: #{id}")
        @blocking_surfaces << id if surface["obligation_blocking"] == true
      end
    end

    def validate_source_discovery(manifest)
      return unless @source_root.directory?

      target_sources = manifest.fetch("targets", []).each_with_object(Hash.new { |hash, key| hash[key] = Set.new }) do |target, paths|
        Array(target["readers"]).concat(Array(target["writers"])).each do |source|
          paths[target["id"]] << source["path"]
        end
      end
      declared_writer_tokens = manifest.fetch("targets", []).flat_map do |target|
        Array(target["writers"]).flat_map { |source| Array(source["tokens"]) }
      end.to_set

      java_files.each do |path|
        relative = path.relative_path_from(@root).to_s
        content = path.read(encoding: "UTF-8")
        content.scan(/@Column\s*\(\s*name\s*=\s*"([a-z0-9_]+)"/).flatten.each do |column|
          matching_ids = (INLINE_TARGETS.keys + OBJECT_TARGETS.keys).select { |id| id.end_with?(".#{column}") }
          next if matching_ids.empty?
          next if matching_ids.any? { |id| target_sources[id].include?(relative) }

          @errors << "SOURCE_MAPPING_UNDECLARED: #{relative}:#{column}"
        end
        content.scan(/\.((?:set)[A-Za-z0-9]*(?:Encrypted|BusinessLicenseUrl|LegalRepIdFrontUrl|LegalRepIdBackUrl|ShortlinkDomainProofUrl|TrademarkProofUrl|EvidenceUrl|FileUrl))\s*\(/).flatten.each do |writer|
          @errors << "SOURCE_WRITER_UNKNOWN: #{relative}:#{writer}" unless declared_writer_tokens.include?(writer)
        end
        if content.match?(/(?:Files\s*\.\s*)?readAllBytes\s*\(/)
          @errors << "SOURCE_UNBOUNDED_LENGTH_ALLOCATION: #{relative}"
        end
      rescue EncodingError => error
        @errors << "SOURCE_ENCODING_INVALID: #{relative}:#{error.message}"
      end
    end

    def validate_obligation_readiness(manifest)
      readiness = manifest["obligation_readiness"]
      unless readiness.is_a?(Hash)
        @errors << "OBLIGATION_READINESS_INVALID"
        return
      end
      validate_exact_keys(readiness, Set["status", "blocking_surface_ids", "reason"], "OBLIGATION_READINESS")
      status = readiness["status"]
      ids = Array(readiness["blocking_surface_ids"])
      @errors << "OBLIGATION_READINESS_BLOCKER_SET_INVALID" unless ids.to_set == @blocking_surfaces.to_set
      unless %w[READY BLOCKED_BY_CURRENT_IMPLEMENTATION].include?(status)
        @errors << "OBLIGATION_READINESS_STATUS_INVALID"
      end
      if status == "READY" && !@blocking_surfaces.empty?
        @errors << "OBLIGATION_FALSE_READY"
      end
      if status == "BLOCKED_BY_CURRENT_IMPLEMENTATION" && @blocking_surfaces.empty?
        @errors << "OBLIGATION_BLOCKED_WITHOUT_FACT"
      end
      if @acceptance && status == "READY"
        raw_writer = declared_raw_url_writer?
        @errors << "OBLIGATION_READY_WITH_RAW_URL_WRITER" if raw_writer
      end
    end

    def declared_raw_url_writer?
      java_files.any? do |path|
        path.read(encoding: "UTF-8").match?(
          /\.set(?:BusinessLicense|LegalRepIdFront|LegalRepIdBack|ShortlinkDomainProof|TrademarkProof|Evidence|File)Url\s*\(/
        )
      end
    end

    def candidate_column?(column)
      column.name == "password_hash" ||
        column.name.end_with?("_url") ||
        column.name == "raw_payload" ||
        %w[
          unified_social_credit_code legal_rep_name contact_name
          registered_address business_address
        ].include?(column.name)
    end

    def validate_source_refs(refs, label)
      unless refs.is_a?(Array)
        @errors << "#{label}_LIST_INVALID"
        return
      end
      refs.each do |source|
        validate_exact_keys(source, SOURCE_KEYS, label)
        path = safe_repository_path(source["path"], label)
        next unless path
        unless path.file?
          @errors << "#{label}_MISSING: #{source['path']}"
          next
        end
        content = path.read(encoding: "UTF-8")
        tokens = source["tokens"]
        if !tokens.is_a?(Array) || tokens.empty? || tokens.any? { |token| !token.is_a?(String) || token.empty? }
          @errors << "#{label}_TOKENS_INVALID: #{source['path']}"
          next
        end
        tokens.each do |token|
          @errors << "#{label}_TOKEN_MISSING: #{source['path']}:#{token}" unless content.include?(token)
        end
      rescue EncodingError => error
        @errors << "#{label}_ENCODING_INVALID: #{source['path']}:#{error.message}"
      end
    end

    def safe_repository_path(relative, label)
      unless relative.is_a?(String) && !relative.empty?
        @errors << "#{label}_PATH_INVALID"
        return nil
      end
      candidate = @root.join(relative).cleanpath
      unless candidate.to_s.start_with?(@root.to_s + File::SEPARATOR)
        @errors << "#{label}_PATH_OUTSIDE_REPOSITORY: #{relative}"
        return nil
      end
      candidate
    end

    def java_files
      @java_files ||= Dir.glob(@source_root.join("**/*.java")).sort.map { |path| Pathname(path) }
    end

    def validate_unique_records(records, label)
      ids = records.filter_map { |record| record.is_a?(Hash) ? record["id"] : nil }
      duplicates = ids.tally.select { |_id, count| count > 1 }.keys
      @errors << "#{label}_DUPLICATE: #{duplicates.join(',')}" unless duplicates.empty?
      @errors << "#{label}_ROW_INVALID" if records.any? { |record| !record.is_a?(Hash) }
    end

    def validate_exact_keys(record, expected, label)
      unless record.is_a?(Hash)
        @errors << "#{label}_OBJECT_INVALID"
        return
      end
      actual = record.keys.to_set
      missing = expected - actual
      unknown = actual - expected
      @errors << "#{label}_FIELDS_MISSING: #{missing.to_a.sort.join(',')}" unless missing.empty?
      @errors << "#{label}_FIELDS_UNKNOWN: #{unknown.to_a.sort.join(',')}" unless unknown.empty?
    end

    def report_set_difference(label, expected, actual)
      missing = expected - actual
      unknown = actual - expected
      @errors << "#{label}_MISSING: #{missing.to_a.sort.join(',')}" unless missing.empty?
      @errors << "#{label}_UNKNOWN: #{unknown.to_a.sort.join(',')}" unless unknown.empty?
    end
  end

  # Strict counts/digests-only validator for the real MySQL migration result.
  class MigrationEvidenceValidator
    attr_reader :errors

    def initialize(root:, evidence_path:, v1_path:)
      @root = Pathname(root).realpath
      @evidence_path = Pathname(evidence_path).expand_path(@root)
      @v1_path = Pathname(v1_path).expand_path(@root)
      @errors = []
    end

    def validate
      unless repository_file?(@evidence_path) && @evidence_path.size <= 65_536
        @errors << "MYSQL_EVIDENCE_PATH_INVALID"
        return self
      end
      evidence = JSON.parse(@evidence_path.read(encoding: "UTF-8"))
      exact_keys(evidence, MIGRATION_EVIDENCE_KEYS, "MYSQL_EVIDENCE")
      @errors << "MYSQL_EVIDENCE_SCHEMA_INVALID" unless evidence["schema_version"] == "phase03-migration-inventory/v1"
      digest(evidence["accepted_pair_digest"], "MYSQL_EVIDENCE_PAIR_DIGEST")
      digest(evidence["v1_sha256"], "MYSQL_EVIDENCE_V1_DIGEST")
      digest(evidence["indexed_target_set_digest"], "MYSQL_EVIDENCE_INDEX_SET_DIGEST")
      expected_v1 = Digest::SHA256.file(@v1_path).hexdigest if repository_file?(@v1_path)
      @errors << "MYSQL_EVIDENCE_V1_DRIFT" unless evidence["v1_sha256"] == expected_v1
      expected_index_digest = Digest::SHA256.hexdigest(
        DIGEST_TARGETS.to_a.sort.join("\n")
      )
      unless evidence["indexed_target_set_digest"] == expected_index_digest
        @errors << "MYSQL_EVIDENCE_INDEX_SET_DRIFT"
      end
      targets = evidence["targets"]
      unless targets.is_a?(Array)
        @errors << "MYSQL_EVIDENCE_TARGETS_INVALID"
        return self
      end
      target_types = targets.filter_map { |target| target.is_a?(Hash) ? target["target_type"] : nil }
      unless target_types.to_set == MIGRATION_TARGETS.keys.to_set && target_types.length == MIGRATION_TARGETS.length
        @errors << "MYSQL_EVIDENCE_TARGET_SET_INVALID"
      end
      targets.each { |target| validate_target(target) }
      integer(evidence["target_count"], MIGRATION_TARGETS.length, "MYSQL_EVIDENCE_TARGET_COUNT")
      integer(evidence["complete_target_count"], MIGRATION_TARGETS.length, "MYSQL_EVIDENCE_COMPLETE_COUNT")
      integer(evidence["blocking_target_count"], 0, "MYSQL_EVIDENCE_BLOCKING_COUNT")
      self
    rescue Errno::ENOENT, JSON::ParserError, EncodingError, TypeError => error
      @errors << "MYSQL_EVIDENCE_INVALID: #{error.class}"
      self
    end

    private

    def validate_target(target)
      unless target.is_a?(Hash)
        @errors << "MYSQL_EVIDENCE_TARGET_INVALID"
        return
      end
      exact_keys(target, MIGRATION_TARGET_KEYS, "MYSQL_EVIDENCE_TARGET")
      target_type = target["target_type"]
      indexed = MIGRATION_TARGETS[target_type]
      return if indexed.nil?

      @errors << "MYSQL_EVIDENCE_TARGET_INCOMPLETE: #{target_type}" unless target["target_state"] == "COMPLETE"
      if target["legacy_fallback_allowed"] != false
        @errors << "MYSQL_EVIDENCE_LEGACY_FALLBACK: #{target_type}"
      end
      positive_integer(target["checkpoint_count"], "MYSQL_EVIDENCE_CHECKPOINT_COUNT: #{target_type}")
      positive_integer(target["event_count"], "MYSQL_EVIDENCE_EVENT_COUNT: #{target_type}")
      blind_count = target["blind_index_count"]
      if !blind_count.is_a?(Integer) || blind_count.negative? || indexed && blind_count.zero? || !indexed && !blind_count.zero?
        @errors << "MYSQL_EVIDENCE_BLIND_INDEX_COUNT: #{target_type}"
      end
    end

    def repository_file?(path)
      path.to_s.start_with?(@root.to_s + File::SEPARATOR) && path.file? && !path.symlink?
    end

    def exact_keys(record, expected, label)
      unless record.is_a?(Hash) && record.keys.to_set == expected
        @errors << "#{label}_FIELDS_INVALID"
      end
    end

    def digest(value, label)
      @errors << "#{label}_INVALID" unless value.is_a?(String) && value.match?(/\A[0-9a-f]{64}\z/)
    end

    def integer(value, expected, label)
      @errors << "#{label}_INVALID" unless value.is_a?(Integer) && value == expected
    end

    def positive_integer(value, label)
      @errors << "#{label}_INVALID" unless value.is_a?(Integer) && value.positive?
    end
  end
end
