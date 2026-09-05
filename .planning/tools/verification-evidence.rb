#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "pathname"
require "securerandom"
require "time"

require_relative "phase01-chrome-entry-contract"

module VerificationEvidence
  PHASE = "01-engineering-verification-foundation"
  SUBJECT_SCHEMA = "phase01-tested-inputs-v1"
  ENVELOPE_SCHEMA = "phase01-verification-evidence-v1"
  AGGREGATE_SCHEMA = "phase01-verification-aggregate-v1"
  EVIDENCE_MANIFEST_SCHEMA = "phase01-evidence-manifest-v1"
  OBLIGATION_SUMMARY_SCHEMA = "phase01-obligation-summary-v1"
  OBLIGATION_MANIFEST_SCHEMA = "phase01-obligation-evidence-manifest-v1"
  ROLES = %w[implementation test config contract validator].freeze
  TERMINAL_STATUSES = %w[PASS FAIL BLOCKED].freeze
  SHA256 = /\A[0-9a-f]{64}\z/
  STABLE_ID = /\A[A-Z][A-Z0-9_:-]*\z/
  RUN_ID = /\A[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}\z/
  CHECK_ID = /\A[a-z0-9][a-z0-9._-]{0,127}\z/
  ENVIRONMENT_KEYS = %w[
    architecture ci java_version locale node_version os platform ruby_engine
    ruby_version timezone
  ].freeze
  FORBIDDEN_EVIDENCE_KEYS = %w[
    commit commit_id commit_sha final_commit git_commit git_sha remote_commit
    remote_sha
  ].freeze
  SECRET_PATTERN = /(?:password|passwd|secret|token|authorization|proxy-authorization|cookie|set-cookie|api[_-]?key|private[_-]?key)\s*[:=]/i
  AUTH_SCHEME_PATTERN = /\b(?:Bearer|Basic)\s+[A-Za-z0-9._~+\/=:-]+/i
  URL_CREDENTIAL_PATTERN = %r{\b[a-z][a-z0-9+.-]*://[^\s/@:]+(?::[^\s/@]*)?@}i
  PRIVATE_KEY_PATTERN = /-----BEGIN [^-\r\n]*PRIVATE KEY-----.*?-----END [^-\r\n]*PRIVATE KEY-----/mi
  PRIVATE_KEY_BEGIN_PATTERN = /-----BEGIN [^-\r\n]*PRIVATE KEY-----/i
  PHONE_PATTERN = /(?<!\d)1[3-9]\d{9}(?!\d)/

  PHASE_DIR = ".planning/phases/#{PHASE}"
  EVIDENCE_DIR = "#{PHASE_DIR}/EVIDENCE"
  ENTRY_EVIDENCE_PATH = "#{EVIDENCE_DIR}/local-chrome-entry.json"
  ENTRY_REVIEW_PATH = "#{PHASE_DIR}/ENTRY-REVIEW.md"
  LEGACY_BROWSER_SOURCE_PREFIX = "#{EVIDENCE_DIR}/browser-source"

  SUBJECT_FIELDS = %w[schema_version phase inputs].freeze
  INPUT_FIELDS = %w[path mode sha256 role].freeze
  ENVELOPE_FIELDS = %w[
    schema_version run_id phase obligation_ids case_ids check_id layer argv cwd
    subject_manifest_path subject_manifest_digest tested_subject_digest
    started_at completed_at environment status exit_code errors diagnostics artifacts
  ].freeze
  ARTIFACT_FIELDS = %w[path sha256 media_type size].freeze
  AGGREGATE_FIELDS = %w[
    schema_version run_id phase check_ids evidence_paths subject_manifest_path
    subject_manifest_digest tested_subject_digest evidence_sha256s status errors
  ].freeze
  EVIDENCE_MANIFEST_FIELDS = %w[
    schema_version phase owner subject_manifest_path subject_manifest_digest
    tested_subject_digest entries aggregate
  ].freeze
  EVIDENCE_MANIFEST_ENTRY_FIELDS = %w[
    check_id path sha256 status obligation_ids case_ids
  ].freeze
  EVIDENCE_MANIFEST_AGGREGATE_FIELDS = %w[path sha256 status].freeze
  OBLIGATION_SUMMARY_FIELDS = %w[
    schema_version phase owner obligation_id requirement_ids behavior_id catalog_test
    case_id matrix_command evidence_path subject_manifest_path subject_manifest_digest
    tested_subject_digest status exit_code check_results supporting_results
    supporting_contracts runtime product_acceptance_claims
  ].freeze
  OBLIGATION_RESULT_FIELDS = %w[
    check_id layer argv cwd case_ids status exit_code diagnostics artifact_checksums result_digest
  ].freeze
  OBLIGATION_MANIFEST_FIELDS = %w[
    schema_version phase owner subject_manifest_path subject_manifest_digest
    tested_subject_digest entries runtime_artifact ci_locators
  ].freeze
  OBLIGATION_MANIFEST_ENTRY_FIELDS = %w[
    obligation_id path sha256 status case_id behavior_id catalog_test evidence_path
  ].freeze
  DURABLE_SUBJECT_PATH = "#{EVIDENCE_DIR}/tested-inputs.json"
  DURABLE_MANIFEST_PATH = "#{EVIDENCE_DIR}/evidence-manifest.json"
  LOCAL_RUNTIME_PATH = "#{EVIDENCE_DIR}/local-chrome-runtime.json"
  OWNER = "engineering-verification-foundation"
  CI_LOCATOR_PATHS = [".github/workflows/ci.yml", "scripts/verify-phase-01"].freeze
  SUPPORTING_CONTRACTS = [
    "simplified-Chinese copy/export contract support only",
    "UTC+8/IANA timezone contract support only"
  ].freeze
  CATALOG_QUERY_ARGV = [
    "/usr/bin/env", "ruby", ".planning/tools/validate-prd-obligations.rb",
    "--owner", OWNER, "--assert-unique", "--assert-traced"
  ].freeze
  OBLIGATION_REGISTRY = [
    { "obligation_id" => "OBL-FOUND-TRACE-001", "check_ids" => ["trace-closure-001"], "supporting" => true },
    { "obligation_id" => "OBL-FOUND-TRACE-002", "check_ids" => ["trace-closure-002"], "supporting" => true },
    { "obligation_id" => "OBL-FOUND-TRACE-003", "check_ids" => %w[
      evidence-kernel-self-test core-unit web-install web-lint web-unit web-build
      login-scenario-server login-scenario-visual-local-chrome copy-local-browser mysql-real redis-real
      timezone-contract service-java-integration local-chrome-contract-fixtures
    ], "supporting" => false },
    { "obligation_id" => "OBL-FOUND-TRACE-004", "check_ids" => ["phase-lifecycle-delivery"], "supporting" => false },
    { "obligation_id" => "OBL-FOUND-UI-DRIFT-001", "check_ids" => ["ui-drift"], "supporting" => false },
    { "obligation_id" => "OBL-FOUND-UI-DRIFT-002", "check_ids" => ["ui-drift"], "supporting" => false },
    { "obligation_id" => "OBL-NFR-BROWSER", "check_ids" => ["local-chrome-runtime"], "supporting" => false }
  ].freeze

  VerifiedLocalFile = Struct.new(:path, :bytes, :stat, keyword_init: true)

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

  def subject_manifest_digest(manifest)
    digest_value(manifest)
  end

  def tested_subject_digest(inputs)
    digest_value(inputs)
  end

  def atomic_write_json(path, value)
    directory = File.dirname(path)
    FileUtils.mkdir_p(directory) unless Dir.exist?(directory)
    temporary = File.join(directory, ".#{File.basename(path)}.#{Process.pid}.#{SecureRandom.hex(8)}.tmp")
    payload = "#{canonical_json(value)}\n"
    descriptor = File.open(temporary, File::WRONLY | File::CREAT | File::EXCL, 0o600)
    descriptor.write(payload)
    descriptor.flush
    descriptor.fsync
    descriptor.close
    File.chmod(0o644, temporary)
    File.rename(temporary, path)
    value
  ensure
    descriptor&.close unless descriptor&.closed?
    File.delete(temporary) if defined?(temporary) && temporary && File.exist?(temporary)
  end

  def canonical_relative_path?(value)
    return false unless value.is_a?(String) && !value.empty? && !value.include?("\0") && !value.include?("\\")

    path = Pathname(value)
    !path.absolute? && path.cleanpath.to_s == value && path.each_filename.none? { |part| part == ".." }
  end

  def path_identity(stat)
    [stat.dev, stat.ino, stat.mode]
  end

  def stable_file_state(stat)
    [stat.dev, stat.ino, stat.mode, stat.nlink, stat.size, stat.mtime.to_r, stat.ctime.to_r]
  end

  def descriptor_realpath(io)
    if RUBY_PLATFORM.include?("darwin")
      buffer = "\0" * 1024
      io.fcntl(50, buffer) # F_GETPATH on Darwin.
      path = buffer.unpack1("Z*")
      return nil if path.empty?

      File.realpath(path)
    elsif File.directory?("/proc/self/fd")
      File.realpath("/proc/self/fd/#{io.fileno}")
    end
  rescue SystemCallError, IOError
    nil
  end

  def strictly_contained_path?(root_real, candidate_real)
    candidate_real.start_with?("#{root_real}#{File::SEPARATOR}")
  end

  def verified_open_flags(errors, error_prefix, file_constants: File)
    unless file_constants.const_defined?(:NOFOLLOW)
      errors << "#{error_prefix}_NOFOLLOW_UNAVAILABLE"
      return nil
    end

    File::RDONLY | file_constants.const_get(:NOFOLLOW)
  end

  # Resolve and read a repository-local regular file without trusting a pathname
  # after validation. Every relative component is lstat'ed, links are rejected,
  # and the bytes are read from an O_NOFOLLOW descriptor whose identity is checked
  # before and after the read. Test-only callbacks bracket descriptor opening and
  # the bounded read so destructive tests can prove rejection happens pre-read.
  def verified_local_file(root, relative, errors, error_prefix, before_open: nil, before_read: nil, max_bytes: nil)
    unless max_bytes.nil? || (max_bytes.is_a?(Integer) && max_bytes.positive?)
      errors << "#{error_prefix}_MAX_BYTES_INVALID: #{max_bytes.inspect}"
      return nil
    end
    unless canonical_relative_path?(relative)
      errors << "#{error_prefix}_PATH_INVALID: #{relative.inspect}"
      return nil
    end

    root_lexical = File.expand_path(root)
    root_stat = File.lstat(root_lexical)
    if root_stat.symlink?
      errors << "#{error_prefix}_ROOT_SYMLINK"
      return nil
    end
    unless root_stat.directory?
      errors << "#{error_prefix}_ROOT_NOT_DIRECTORY"
      return nil
    end
    root_path = File.realpath(root_lexical)
    resolved_root_stat = File.lstat(root_path)
    unless [root_stat.dev, root_stat.ino] == [resolved_root_stat.dev, resolved_root_stat.ino]
      errors << "#{error_prefix}_ROOT_IDENTITY_CHANGED"
      return nil
    end
    candidate = File.expand_path(relative, root_path)
    unless strictly_contained_path?(root_path, candidate)
      errors << "#{error_prefix}_PATH_OUTSIDE_ROOT: #{relative}"
      return nil
    end

    component_stats = [[root_path, path_identity(resolved_root_stat)]]
    current = root_path
    components = Pathname(relative).each_filename.to_a
    components.each_with_index do |component, index|
      current = File.join(current, component)
      stat = File.lstat(current)
      if stat.symlink?
        errors << "#{error_prefix}_SYMLINK_COMPONENT: #{relative} component=#{component}"
        return nil
      end
      final = index == components.length - 1
      unless final ? stat.file? : stat.directory?
        errors << "#{error_prefix}_NOT_REGULAR: #{relative}"
        return nil
      end
      component_stats << [current, path_identity(stat)]
    end
    final_stat = File.lstat(candidate)
    if final_stat.nlink != 1
      errors << "#{error_prefix}_LINK_COUNT_INVALID: #{relative} nlink=#{final_stat.nlink}"
      return nil
    end

    candidate_real = File.realpath(candidate)
    unless strictly_contained_path?(root_path, candidate_real) && candidate_real == candidate
      errors << "#{error_prefix}_REALPATH_OUTSIDE_ROOT: #{relative}"
      return nil
    end

    before_open&.call(candidate)
    flags = verified_open_flags(errors, error_prefix)
    return nil unless flags

    File.open(candidate, flags) do |io|
      io.binmode
      opened_stat = io.stat
      unless opened_stat.file? && opened_stat.nlink == 1 &&
          [opened_stat.dev, opened_stat.ino] == [final_stat.dev, final_stat.ino]
        errors << "#{error_prefix}_IDENTITY_CHANGED: #{relative}"
        return nil
      end

      descriptor_path = descriptor_realpath(io)
      unless descriptor_path
        errors << "#{error_prefix}_DESCRIPTOR_PATH_UNAVAILABLE: #{relative}"
        return nil
      end
      if !strictly_contained_path?(root_path, descriptor_path) || descriptor_path != candidate
        errors << "#{error_prefix}_DESCRIPTOR_OUTSIDE_ROOT: #{relative}"
        return nil
      end

      component_stats.each do |path, identity|
        current_stat = File.lstat(path)
        if current_stat.symlink? || path_identity(current_stat) != identity
          errors << "#{error_prefix}_COMPONENT_CHANGED: #{relative}"
          return nil
        end
      end
      post_real = File.realpath(candidate)
      unless strictly_contained_path?(root_path, post_real) && post_real == candidate
        errors << "#{error_prefix}_REALPATH_OUTSIDE_ROOT: #{relative}"
        return nil
      end

      pre_read_stat = io.stat
      if max_bytes && pre_read_stat.size > max_bytes
        errors << "#{error_prefix}_SIZE_LIMIT_EXCEEDED: #{relative} size=#{pre_read_stat.size} max=#{max_bytes}"
        return nil
      end
      unless stable_file_state(pre_read_stat) == stable_file_state(opened_stat)
        errors << "#{error_prefix}_CONTENT_CHANGED_BEFORE_READ: #{relative}"
        return nil
      end
      before_read&.call(io, pre_read_stat)
      bytes = max_bytes ? io.read(max_bytes + 1) : io.read
      if max_bytes && bytes.bytesize > max_bytes
        errors << "#{error_prefix}_SIZE_LIMIT_EXCEEDED: #{relative} size=#{bytes.bytesize} max=#{max_bytes}"
        return nil
      end
      after_stat = io.stat
      unless stable_file_state(after_stat) == stable_file_state(pre_read_stat)
        errors << "#{error_prefix}_CONTENT_CHANGED_DURING_READ: #{relative}"
        return nil
      end
      VerifiedLocalFile.new(path: candidate, bytes: bytes, stat: after_stat)
    end
  rescue Errno::ELOOP
    errors << "#{error_prefix}_SYMLINK_COMPONENT: #{relative}"
    nil
  rescue Errno::ENOENT, Errno::ENOTDIR
    errors << "#{error_prefix}_MISSING: #{relative}"
    nil
  rescue Errno::EACCES
    errors << "#{error_prefix}_UNREADABLE: #{relative}"
    nil
  end

  def file_mode(file_or_stat)
    stat = file_or_stat.respond_to?(:stat) ? file_or_stat.stat : file_or_stat
    format("%06o", stat.mode)
  end

  def snapshot_sha256(snapshot)
    Digest::SHA256.hexdigest(snapshot.bytes)
  end

  def expected_inputs(registries, errors = [])
    unless registries.is_a?(Hash) && !registries.empty?
      errors << "SUBJECT_REGISTRY_EMPTY"
      return []
    end

    entries = []
    registries.keys.sort.each do |check_id|
      unless check_id.is_a?(String) && check_id.match?(CHECK_ID)
        errors << "SUBJECT_REGISTRY_CHECK_ID_INVALID: #{check_id.inspect}"
        next
      end
      values = registries[check_id]
      unless values.is_a?(Array) && !values.empty?
        errors << "SUBJECT_REGISTRY_INPUTS_EMPTY: #{check_id}"
        next
      end
      values.each do |entry|
        unless entry.is_a?(Hash) && entry.keys.map(&:to_s).sort == %w[path role]
          errors << "SUBJECT_REGISTRY_ENTRY_INVALID: #{check_id}"
          next
        end
        path = entry["path"] || entry[:path]
        role = entry["role"] || entry[:role]
        errors << "SUBJECT_PATH_INVALID: #{path.inspect}" unless canonical_relative_path?(path)
        errors << "SUBJECT_ROLE_INVALID: path=#{path} role=#{role.inspect}" unless ROLES.include?(role)
        errors << "SUBJECT_ILLEGAL_EXCLUSION: #{path}" if code_owned_excluded_path?(path)
        entries << { "path" => path, "role" => role } if canonical_relative_path?(path) && ROLES.include?(role)
      end
    end
    by_path = {}
    entries.each do |entry|
      prior = by_path[entry["path"]]
      if prior && prior["role"] != entry["role"]
        errors << "SUBJECT_ROLE_CONFLICT: path=#{entry['path']} roles=#{prior['role']},#{entry['role']}"
      end
      by_path[entry["path"]] ||= entry
    end
    by_path.values.sort_by { |entry| entry["path"] }
  end

  def code_owned_excluded_path?(path)
    return false unless path.is_a?(String)

    basename = File.basename(path)
    return true if path == ENTRY_EVIDENCE_PATH || path == ENTRY_REVIEW_PATH
    return true if path.start_with?(LEGACY_BROWSER_SOURCE_PREFIX)
    return true if basename == "tested-inputs.json" || basename == "evidence-manifest.json"
    return true if basename == "TODO.md" || basename.end_with?("SUMMARY.md")
    return true if basename.match?(/(?:CLAUDE-REVIEW|ENTRY-REVIEW|\d+-REVIEW|\d+-VERIFICATION)\.md\z/)
    return true if path.include?("/EVIDENCE/runs/") || basename.start_with?("OBL-")
    return true if path.include?("/EVIDENCE/delivery-attestation") || path.include?("/EVIDENCE/delivery-tag")

    false
  end

  def build_subject_manifest(root:, registries:, manifest_path:, require_live_entry: false)
    errors = validate_entry_boundary(root, require_live: require_live_entry)
    expected = expected_inputs(registries, errors)
    entries = expected.filter_map do |entry|
      snapshot = verified_local_file(root, entry["path"], errors, "SUBJECT_INPUT")
      next unless snapshot

      {
        "path" => entry["path"],
        "mode" => file_mode(snapshot),
        "sha256" => snapshot_sha256(snapshot),
        "role" => entry["role"]
      }
    end
    raise ArgumentError, errors.join("\n") unless errors.empty?

    manifest = { "schema_version" => SUBJECT_SCHEMA, "phase" => PHASE, "inputs" => entries }
    destination = File.expand_path(manifest_path, root)
    atomic_write_json(destination, manifest)
  end

  def validate_subject_manifest(root:, manifest:, registries:, require_live_entry: false, max_bytes: nil)
    errors = []
    exact_hash(manifest, SUBJECT_FIELDS, errors, "SUBJECT")
    return errors unless manifest.is_a?(Hash)

    errors << "SUBJECT_SCHEMA_UNSUPPORTED: #{manifest['schema_version'].inspect}" unless manifest["schema_version"] == SUBJECT_SCHEMA
    errors << "SUBJECT_PHASE_MISMATCH: #{manifest['phase'].inspect}" unless manifest["phase"] == PHASE
    errors << "SUBJECT_ILLEGAL_EXCLUSION: manifest controls exclusions" if manifest.key?("exclusions")

    inputs = manifest["inputs"]
    unless inputs.is_a?(Array)
      errors << "SUBJECT_INPUTS_TYPE_INVALID"
      return errors
    end

    expected = expected_inputs(registries, errors)
    actual_paths = []
    seen = {}
    inputs.each_with_index do |entry, index|
      label = "SUBJECT_INPUT index=#{index}"
      exact_hash(entry, INPUT_FIELDS, errors, label)
      next unless entry.is_a?(Hash)

      path = entry["path"]
      actual_paths << path if path.is_a?(String)
      errors << "SUBJECT_INPUT_DUPLICATE: #{path}" if seen.key?(path)
      seen[path] = true
      errors << "SUBJECT_ROLE_INVALID: path=#{path} role=#{entry['role'].inspect}" unless ROLES.include?(entry["role"])
      errors << "SUBJECT_MODE_INVALID: path=#{path}" unless entry["mode"].is_a?(String) && entry["mode"].match?(/\A10[0-7]{4}\z/)
      errors << "SUBJECT_SHA256_INVALID: path=#{path}" unless entry["sha256"].is_a?(String) && entry["sha256"].match?(SHA256)
      errors << "SUBJECT_ILLEGAL_EXCLUSION: #{path}" if code_owned_excluded_path?(path)

      snapshot = verified_local_file(root, path, errors, "SUBJECT_INPUT", max_bytes: max_bytes)
      next unless snapshot
      errors << "SUBJECT_MODE_MISMATCH: path=#{path} expected=#{entry['mode']} actual=#{file_mode(snapshot)}" unless entry["mode"] == file_mode(snapshot)
      digest = snapshot_sha256(snapshot)
      errors << "SUBJECT_CONTENT_MISMATCH: path=#{path} expected=#{entry['sha256']} actual=#{digest}" unless entry["sha256"] == digest
    end

    errors << "SUBJECT_INPUT_ORDER_INVALID" unless actual_paths == actual_paths.sort
    expected_by_path = expected.to_h { |entry| [entry["path"], entry] }
    actual_by_path = inputs.filter_map { |entry| [entry["path"], entry] if entry.is_a?(Hash) && entry["path"].is_a?(String) }.to_h
    (expected_by_path.keys - actual_by_path.keys).sort.each { |path| errors << "SUBJECT_INPUT_MISSING: #{path}" }
    (actual_by_path.keys - expected_by_path.keys).sort.each { |path| errors << "SUBJECT_INPUT_EXTRA: #{path}" }
    (expected_by_path.keys & actual_by_path.keys).sort.each do |path|
      errors << "SUBJECT_ROLE_MISMATCH: path=#{path}" unless expected_by_path[path]["role"] == actual_by_path[path]["role"]
    end
    errors.concat(validate_entry_boundary(root, require_live: require_live_entry, max_bytes: max_bytes))
    errors.uniq
  end

  def build_envelope(run_id:, check_id:, layer:, obligation_ids:, case_ids:, argv:, cwd:, started_at:, completed_at:,
    environment:, status:, exit_code:, errors:, diagnostics:, artifacts:, subject_manifest_path:,
    subject_manifest_digest:, tested_subject_digest:)
    {
      "schema_version" => ENVELOPE_SCHEMA,
      "run_id" => run_id,
      "phase" => PHASE,
      "obligation_ids" => obligation_ids,
      "case_ids" => case_ids,
      "check_id" => check_id,
      "layer" => layer,
      "argv" => argv,
      "cwd" => cwd,
      "subject_manifest_path" => subject_manifest_path,
      "subject_manifest_digest" => subject_manifest_digest,
      "tested_subject_digest" => tested_subject_digest,
      "started_at" => started_at,
      "completed_at" => completed_at,
      "environment" => environment,
      "status" => status,
      "exit_code" => exit_code,
      "errors" => errors,
      "diagnostics" => diagnostics,
      "artifacts" => artifacts
    }
  end

  def artifact_record(root, relative_path, media_type)
    errors = []
    snapshot = verified_local_file(root, relative_path, errors, "ARTIFACT")
    raise ArgumentError, errors.join("\n") unless snapshot

    {
      "path" => relative_path,
      "sha256" => snapshot_sha256(snapshot),
      "media_type" => media_type,
      "size" => snapshot.bytes.bytesize
    }
  end

  def validate_envelope(root:, envelope:, registries:, subject_manifest_path: nil, check_contracts: nil)
    errors = []
    exact_hash(envelope, ENVELOPE_FIELDS, errors, "EVIDENCE")
    return errors unless envelope.is_a?(Hash)

    errors << "EVIDENCE_SCHEMA_UNSUPPORTED: #{envelope['schema_version'].inspect}" unless envelope["schema_version"] == ENVELOPE_SCHEMA
    errors << "EVIDENCE_PHASE_MISMATCH: #{envelope['phase'].inspect}" unless envelope["phase"] == PHASE
    errors << "EVIDENCE_RUN_ID_INVALID" unless envelope["run_id"].is_a?(String) && envelope["run_id"].match?(RUN_ID)
    errors << "EVIDENCE_CHECK_ID_INVALID" unless envelope["check_id"].is_a?(String) && envelope["check_id"].match?(CHECK_ID)
    errors << "EVIDENCE_CHECK_NOT_REGISTERED: #{envelope['check_id']}" unless registries.key?(envelope["check_id"])
    errors << "EVIDENCE_LAYER_INVALID" unless envelope["layer"].is_a?(String) && envelope["layer"].match?(CHECK_ID)
    validate_string_ids(envelope["obligation_ids"], errors, "EVIDENCE_OBLIGATION_IDS")
    validate_string_ids(envelope["case_ids"], errors, "EVIDENCE_CASE_IDS")
    validate_argv(envelope["argv"], errors)
    errors << "EVIDENCE_CWD_INVALID" unless canonical_relative_path?(envelope["cwd"]) || envelope["cwd"] == "."
    validate_timestamp_pair(envelope, errors)
    validate_environment(envelope["environment"], errors)
    reject_forbidden_keys(envelope, errors)
    validate_check_contract(envelope, check_contracts[envelope["check_id"]], errors) if check_contracts

    status = envelope["status"]
    if status == "RUNNING"
      errors << "EVIDENCE_STALE_RUNNING"
    elsif !TERMINAL_STATUSES.include?(status)
      errors << "EVIDENCE_STATUS_INVALID: #{status.inspect}"
    end
    if status == "PASS" && envelope["exit_code"] != 0
      errors << "EVIDENCE_PASS_EXIT_MISMATCH: #{envelope['exit_code'].inspect}"
    elsif status == "FAIL"
      errors << "EVIDENCE_STATUS_FAIL"
      errors << "EVIDENCE_FAIL_EXIT_MISMATCH" unless envelope["exit_code"].is_a?(Integer) && envelope["exit_code"] != 0
    elsif status == "BLOCKED"
      errors << "EVIDENCE_STATUS_BLOCKED"
      errors << "EVIDENCE_BLOCKED_EXIT_MISMATCH" unless envelope["exit_code"].nil? || (envelope["exit_code"].is_a?(Integer) && envelope["exit_code"] != 0)
    end

    validate_diagnostics(envelope["errors"], envelope["diagnostics"], errors)
    validate_artifacts(root, envelope["artifacts"], errors)

    manifest_relative = subject_manifest_path || envelope["subject_manifest_path"]
    if envelope["subject_manifest_path"] != manifest_relative
      errors << "EVIDENCE_SUBJECT_MANIFEST_PATH_MISMATCH"
    end
    manifest_snapshot = verified_local_file(root, manifest_relative, errors, "SUBJECT_MANIFEST")
    if manifest_snapshot
      begin
        manifest = JSON.parse(manifest_snapshot.bytes)
        errors.concat(validate_subject_manifest(root: root, manifest: manifest, registries: registries))
        expected_manifest_digest = subject_manifest_digest(manifest)
        expected_subject_digest = tested_subject_digest(manifest.fetch("inputs", []))
        unless envelope["subject_manifest_digest"] == expected_manifest_digest
          errors << "EVIDENCE_SUBJECT_MANIFEST_DIGEST_MISMATCH: expected=#{expected_manifest_digest} actual=#{envelope['subject_manifest_digest']}"
        end
        unless envelope["tested_subject_digest"] == expected_subject_digest
          errors << "EVIDENCE_TESTED_SUBJECT_DIGEST_MISMATCH: expected=#{expected_subject_digest} actual=#{envelope['tested_subject_digest']}"
        end
      rescue JSON::ParserError
        errors << "SUBJECT_MANIFEST_JSON_INVALID"
      end
    end
    errors.uniq
  end

  def build_aggregate(run_id:, envelopes:, evidence_paths:, evidence_sha256s:, subject_manifest_path:, subject_manifest_digest:, tested_subject_digest:)
    statuses = envelopes.map { |envelope| envelope.fetch("status") }
    status = reduce_statuses(statuses)
    {
      "schema_version" => AGGREGATE_SCHEMA,
      "run_id" => run_id,
      "phase" => PHASE,
      "check_ids" => envelopes.map { |envelope| envelope.fetch("check_id") },
      "evidence_paths" => evidence_paths,
      "evidence_sha256s" => evidence_sha256s,
      "subject_manifest_path" => subject_manifest_path,
      "subject_manifest_digest" => subject_manifest_digest,
      "tested_subject_digest" => tested_subject_digest,
      "status" => status,
      "errors" => envelopes.flat_map { |envelope| envelope.fetch("errors") }.uniq.sort
    }
  end

  def validate_aggregate(root:, aggregate:, envelopes:)
    errors = []
    exact_hash(aggregate, AGGREGATE_FIELDS, errors, "AGGREGATE")
    return errors unless aggregate.is_a?(Hash)

    errors << "AGGREGATE_SCHEMA_UNSUPPORTED" unless aggregate["schema_version"] == AGGREGATE_SCHEMA
    errors << "AGGREGATE_PHASE_MISMATCH" unless aggregate["phase"] == PHASE
    check_ids = envelopes.map { |envelope| envelope["check_id"] }
    errors << "AGGREGATE_CHECK_SET_MISMATCH" unless aggregate["check_ids"] == check_ids
    errors << "AGGREGATE_CHECK_DUPLICATE" unless check_ids.uniq.length == check_ids.length
    errors << "AGGREGATE_EVIDENCE_PATH_COUNT_MISMATCH" unless aggregate["evidence_paths"].is_a?(Array) && aggregate["evidence_paths"].length == check_ids.length
    errors << "AGGREGATE_EVIDENCE_DIGEST_COUNT_MISMATCH" unless aggregate["evidence_sha256s"].is_a?(Array) && aggregate["evidence_sha256s"].length == check_ids.length
    %w[subject_manifest_path subject_manifest_digest tested_subject_digest].each do |field|
      values = envelopes.map { |envelope| envelope[field] }.uniq
      errors << "AGGREGATE_#{field.upcase}_MISMATCH" unless values == [aggregate[field]]
    end
    reduced = reduce_statuses(envelopes.map { |envelope| envelope["status"] })
    errors << "AGGREGATE_STATUS_MISMATCH: expected=#{reduced} actual=#{aggregate['status']}" unless aggregate["status"] == reduced
    aggregate.fetch("evidence_paths", []).each_with_index do |path, index|
      snapshot = verified_local_file(root, path, errors, "AGGREGATE_EVIDENCE")
      next unless snapshot
      expected_digest = aggregate.fetch("evidence_sha256s", [])[index]
      actual_digest = snapshot_sha256(snapshot)
      errors << "AGGREGATE_EVIDENCE_CHECKSUM_MISMATCH: index=#{index}" unless expected_digest == actual_digest
      begin
        persisted = JSON.parse(snapshot.bytes)
        errors << "AGGREGATE_EVIDENCE_CONTENT_MISMATCH: index=#{index}" unless persisted == envelopes[index]
      rescue JSON::ParserError
        errors << "AGGREGATE_EVIDENCE_JSON_INVALID: index=#{index}"
      end
    end
    errors
  end

  def reduce_statuses(statuses)
    return "FAIL" if statuses.include?("FAIL")
    return "BLOCKED" if statuses.include?("BLOCKED") || statuses.any? { |status| status != "PASS" }

    "PASS"
  end

  def validate_evidence_manifest(root:, manifest:, registries:, check_contracts:, required_owner:)
    errors = []
    exact_hash(manifest, EVIDENCE_MANIFEST_FIELDS, errors, "EVIDENCE_MANIFEST")
    return errors unless manifest.is_a?(Hash)

    errors << "EVIDENCE_MANIFEST_SCHEMA_UNSUPPORTED" unless manifest["schema_version"] == EVIDENCE_MANIFEST_SCHEMA
    errors << "EVIDENCE_MANIFEST_PHASE_MISMATCH" unless manifest["phase"] == PHASE
    errors << "EVIDENCE_MANIFEST_OWNER_MISMATCH: expected=#{required_owner} actual=#{manifest['owner']}" unless manifest["owner"] == required_owner

    subject_path = manifest["subject_manifest_path"]
    subject_snapshot = verified_local_file(root, subject_path, errors, "SUBJECT_MANIFEST")
    if subject_snapshot
      begin
        subject = JSON.parse(subject_snapshot.bytes)
        errors.concat(validate_subject_manifest(root: root, manifest: subject, registries: registries))
        errors << "EVIDENCE_MANIFEST_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless manifest["subject_manifest_digest"] == subject_manifest_digest(subject)
        errors << "EVIDENCE_MANIFEST_TESTED_SUBJECT_DIGEST_MISMATCH" unless manifest["tested_subject_digest"] == tested_subject_digest(subject.fetch("inputs", []))
      rescue JSON::ParserError
        errors << "SUBJECT_MANIFEST_JSON_INVALID"
      end
    end

    entries = manifest["entries"]
    unless entries.is_a?(Array) && !entries.empty?
      errors << "EVIDENCE_MANIFEST_ENTRIES_EMPTY"
      return errors
    end

    envelopes = []
    entry_ids = []
    entries.each_with_index do |entry, index|
      exact_hash(entry, EVIDENCE_MANIFEST_ENTRY_FIELDS, errors, "EVIDENCE_MANIFEST_ENTRY")
      next unless entry.is_a?(Hash)
      entry_ids << entry["check_id"]
      snapshot = verified_local_file(root, entry["path"], errors, "EVIDENCE_MANIFEST_ENTRY")
      next unless snapshot
      digest = snapshot_sha256(snapshot)
      errors << "EVIDENCE_MANIFEST_ENTRY_CHECKSUM_MISMATCH: index=#{index}" unless entry["sha256"] == digest
      begin
        envelope = JSON.parse(snapshot.bytes)
        envelopes << envelope
        errors << "EVIDENCE_MANIFEST_ENTRY_CHECK_ID_MISMATCH: index=#{index}" unless entry["check_id"] == envelope["check_id"]
        errors << "EVIDENCE_MANIFEST_ENTRY_STATUS_MISMATCH: index=#{index}" unless entry["status"] == envelope["status"]
        errors << "EVIDENCE_MANIFEST_ENTRY_OBLIGATIONS_MISMATCH: index=#{index}" unless entry["obligation_ids"] == envelope["obligation_ids"]
        errors << "EVIDENCE_MANIFEST_ENTRY_CASES_MISMATCH: index=#{index}" unless entry["case_ids"] == envelope["case_ids"]
        errors.concat(
          validate_envelope(
            root: root,
            envelope: envelope,
            registries: registries,
            subject_manifest_path: subject_path,
            check_contracts: check_contracts
          )
        )
      rescue JSON::ParserError
        errors << "EVIDENCE_MANIFEST_ENTRY_JSON_INVALID: index=#{index}"
      end
    end
    errors << "EVIDENCE_MANIFEST_ENTRY_DUPLICATE" unless entry_ids.uniq.length == entry_ids.length
    errors << "EVIDENCE_MANIFEST_ENTRY_ORDER_INVALID" unless entry_ids == registries.keys
    missing = (registries.keys - entry_ids).sort
    extra = (entry_ids - registries.keys).sort
    missing.each { |check_id| errors << "EVIDENCE_MANIFEST_CHECK_MISSING: #{check_id}" }
    extra.each { |check_id| errors << "EVIDENCE_MANIFEST_CHECK_EXTRA: #{check_id}" }

    aggregate_record = manifest["aggregate"]
    exact_hash(aggregate_record, EVIDENCE_MANIFEST_AGGREGATE_FIELDS, errors, "EVIDENCE_MANIFEST_AGGREGATE")
    if aggregate_record.is_a?(Hash)
      aggregate_snapshot = verified_local_file(root, aggregate_record["path"], errors, "EVIDENCE_MANIFEST_AGGREGATE")
      if aggregate_snapshot
        digest = snapshot_sha256(aggregate_snapshot)
        errors << "EVIDENCE_MANIFEST_AGGREGATE_CHECKSUM_MISMATCH" unless aggregate_record["sha256"] == digest
        begin
          aggregate = JSON.parse(aggregate_snapshot.bytes)
          errors << "EVIDENCE_MANIFEST_AGGREGATE_STATUS_MISMATCH" unless aggregate_record["status"] == aggregate["status"]
          errors.concat(validate_aggregate(root: root, aggregate: aggregate, envelopes: envelopes))
        rescue JSON::ParserError
          errors << "EVIDENCE_MANIFEST_AGGREGATE_JSON_INVALID"
        end
      end
    end
    errors.uniq
  end

  def build_evidence_manifest(root:, owner:, envelopes:, evidence_paths:, aggregate_path:, subject_manifest_path:,
    subject_manifest_digest:, tested_subject_digest:)
    entries = envelopes.each_with_index.map do |envelope, index|
      path = evidence_paths.fetch(index)
      snapshot = verified_local_file(root, path, [], "EVIDENCE_MANIFEST_ENTRY")
      raise ArgumentError, "EVIDENCE_MANIFEST_ENTRY_UNREADABLE: #{path}" unless snapshot
      {
        "check_id" => envelope.fetch("check_id"),
        "path" => path,
        "sha256" => snapshot_sha256(snapshot),
        "status" => envelope.fetch("status"),
        "obligation_ids" => envelope.fetch("obligation_ids"),
        "case_ids" => envelope.fetch("case_ids")
      }
    end
    aggregate_snapshot = verified_local_file(root, aggregate_path, [], "EVIDENCE_MANIFEST_AGGREGATE")
    raise ArgumentError, "EVIDENCE_MANIFEST_AGGREGATE_UNREADABLE: #{aggregate_path}" unless aggregate_snapshot
    aggregate = JSON.parse(aggregate_snapshot.bytes)
    {
      "schema_version" => EVIDENCE_MANIFEST_SCHEMA,
      "phase" => PHASE,
      "owner" => owner,
      "subject_manifest_path" => subject_manifest_path,
      "subject_manifest_digest" => subject_manifest_digest,
      "tested_subject_digest" => tested_subject_digest,
      "entries" => entries,
      "aggregate" => {
        "path" => aggregate_path,
        "sha256" => snapshot_sha256(aggregate_snapshot),
        "status" => aggregate.fetch("status")
      }
    }
  end

  def validate_entry_boundary(root, require_live: false, max_bytes: nil,
                              live_file_validator: nil, version_probe: nil)
    errors = []
    evidence_snapshot = verified_local_file(root, ENTRY_EVIDENCE_PATH, errors, "ENTRY_EVIDENCE", max_bytes: max_bytes)
    review_snapshot = verified_local_file(root, ENTRY_REVIEW_PATH, errors, "ENTRY_REVIEW", max_bytes: max_bytes)
    return errors unless evidence_snapshot && review_snapshot

    { ENTRY_EVIDENCE_PATH => evidence_snapshot, ENTRY_REVIEW_PATH => review_snapshot }.each do |relative, snapshot|
      stat = snapshot.stat
      errors << "ENTRY_BOUNDARY_OWNER_MISMATCH: path=#{relative}" unless stat.uid == Process.uid
      errors << "#{relative == ENTRY_EVIDENCE_PATH ? 'ENTRY_EVIDENCE' : 'ENTRY_REVIEW'}_MODE_UNSAFE: mode=#{file_mode(snapshot)}" unless file_mode(snapshot) == "100644"
    end

    evidence = JSON.parse(evidence_snapshot.bytes)
    Phase01ChromeEntryContract.validate_entry(evidence).each { |error| errors << "ENTRY_EVIDENCE_SCHEMA_INVALID: #{error}" }
    if require_live
      validate_live_file = live_file_validator || lambda do |document|
        Phase01ChromeEntryContract.validate_live_file(document)
      end
      probe_version = version_probe || lambda do
        stdout, _stderr, status = Open3.capture3(Phase01ChromeEntryContract::CHROME_PATH, "--version")
        [stdout, status.success?]
      end
      validate_live_file.call(evidence).each { |error| errors << "ENTRY_EVIDENCE_#{error}" }
      stdout, succeeded = probe_version.call
      if !succeeded
        errors << "ENTRY_EVIDENCE_LIVE_VERSION_FAILED"
      elsif evidence.dig("chrome", "version_output") != stdout.strip
        errors << "ENTRY_EVIDENCE_LIVE_VERSION_MISMATCH"
      end
    end

    review = review_snapshot.bytes
    executor = review.match(/^Executor identity:\s*`([^`]+)`\s*$/)&.captures&.first&.strip
    reviewer = review.match(/^Reviewer identity:\s*`([^`]+)`\s*$/)&.captures&.first&.strip
    errors << "ENTRY_REVIEW_EXECUTOR_IDENTITY_MISSING" if executor.nil? || executor.empty?
    errors << "ENTRY_REVIEW_REVIEWER_IDENTITY_MISSING" if reviewer.nil? || reviewer.empty?
    errors << "ENTRY_REVIEW_SELF_APPROVAL" if executor && reviewer && executor == reviewer

    header = "| Criterion ID | Verdict | Evidence | Command or inspection rule |"
    lines = review.lines.map(&:strip)
    header_index = lines.index(header)
    errors << "ENTRY_REVIEW_TABLE_HEADER_INVALID" unless header_index
    rows = []
    if header_index
      lines.drop(header_index + 1).each do |line|
        break unless line.start_with?("|") && line.end_with?("|")
        cells = line.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
        next if cells.length == 4 && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) }
        if cells.length != 4
          errors << "ENTRY_REVIEW_ROW_COLUMN_COUNT_INVALID"
          next
        end
        rows << cells
      end
    end
    criterion_ids = rows.map(&:first)
    errors << "ENTRY_REVIEW_CRITERIA_SET_INVALID" unless criterion_ids.sort == Phase01ChromeEntryContract::REVIEW_CRITERIA.sort
    errors << "ENTRY_REVIEW_DUPLICATE_CRITERION" unless criterion_ids.uniq.length == criterion_ids.length
    rows.each do |criterion_id, verdict, durable_evidence, command|
      errors << "ENTRY_REVIEW_VERDICT_INVALID: #{criterion_id}" unless %w[PASS BLOCKER].include?(verdict)
      errors << "ENTRY_REVIEW_EVIDENCE_EMPTY: #{criterion_id}" if durable_evidence.empty?
      errors << "ENTRY_REVIEW_COMMAND_EMPTY: #{criterion_id}" if command.empty?
    end
    expected_verdict = rows.any? { |cells| cells[1] == "BLOCKER" } ? "BLOCKED" : "PASS"
    errors << "ENTRY_REVIEW_FINAL_VERDICT_INVALID" unless review.match?(/^## Verdict\s*\n+#{expected_verdict}\s*$/)
    evidence_digest = snapshot_sha256(evidence_snapshot)
    errors << "ENTRY_REVIEW_EVIDENCE_DIGEST_MISSING" unless review.include?(evidence_digest)
    errors
  rescue JSON::ParserError => error
    errors << "ENTRY_EVIDENCE_JSON_INVALID: #{error.message}"
    errors
  rescue SystemCallError => error
    errors << "ENTRY_EVIDENCE_LIVE_VERSION_FAILED: #{error.class}"
    errors
  end

  def obligation_registry
    OBLIGATION_REGISTRY.map { |entry| Marshal.load(Marshal.dump(entry)) }
  end

  def strip_markdown_code(value)
    text = value.to_s.strip
    text.start_with?("`") && text.end_with?("`") ? text[1...-1] : text
  end

  def parse_test_matrix(root, relative_path, max_bytes: nil)
    errors = []
    snapshot = verified_local_file(root, relative_path, errors, "TEST_MATRIX", max_bytes: max_bytes)
    unless snapshot
      diagnostic = errors.empty? ? "TEST_MATRIX_MISSING: #{relative_path}" : errors.join(";")
      raise ArgumentError, diagnostic
    end

    rows = {}
    snapshot.bytes.lines(chomp: true).each do |line|
      next unless line.start_with?("| OBL-")

      cells = line.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
      raise ArgumentError, "TEST_MATRIX_COLUMN_COUNT_INVALID" unless cells.length == 11
      obligation_id = cells[0]
      raise ArgumentError, "TEST_MATRIX_OBLIGATION_DUPLICATE: #{obligation_id}" if rows.key?(obligation_id)
      rows[obligation_id] = {
        "requirement_ids" => cells[1].split(",").map(&:strip).reject(&:empty?).sort,
        "behavior_id" => cells[2],
        "catalog_test" => cells[3],
        "case_id" => cells[7],
        "matrix_command" => strip_markdown_code(cells[9]),
        "evidence_path" => cells[10]
      }
    end
    expected = OBLIGATION_REGISTRY.map { |entry| entry.fetch("obligation_id") }
    raise ArgumentError, "TEST_MATRIX_OBLIGATION_SET_INVALID" unless rows.keys == expected
    rows
  end

  def parse_obligation_catalog(root, relative_path, max_bytes: nil)
    errors = []
    snapshot = verified_local_file(root, relative_path, errors, "OBLIGATION_CATALOG", max_bytes: max_bytes)
    unless snapshot
      diagnostic = errors.empty? ? "OBLIGATION_CATALOG_MISSING: #{relative_path}" : errors.join(";")
      raise ArgumentError, diagnostic
    end

    wanted = OBLIGATION_REGISTRY.map { |entry| entry.fetch("obligation_id") }
    rows = {}
    snapshot.bytes.lines(chomp: true).each do |line|
      next unless line.start_with?("- OBL-")

      cells = line.delete_prefix("- ").split("|").map(&:strip)
      next unless wanted.include?(cells[0])
      raise ArgumentError, "OBLIGATION_CATALOG_ROW_INVALID: #{cells[0]}" unless cells.length >= 8
      raise ArgumentError, "OBLIGATION_CATALOG_DUPLICATE: #{cells[0]}" if rows.key?(cells[0])
      rows[cells[0]] = {
        "requirement_ids" => cells[2].split(",").map(&:strip).reject(&:empty?).sort,
        "owner" => cells[3],
        "behavior_id" => cells[4],
        "catalog_test" => cells[6],
        "evidence_path" => cells[7]
      }
    end
    raise ArgumentError, "OBLIGATION_CATALOG_SET_INVALID" unless rows.keys == wanted
    rows
  end

  def obligation_result_fact(envelope)
    base = {
      "check_id" => envelope.fetch("check_id"),
      "layer" => envelope.fetch("layer"),
      "argv" => envelope.fetch("argv"),
      "cwd" => envelope.fetch("cwd"),
      "case_ids" => envelope.fetch("case_ids"),
      "status" => envelope.fetch("status"),
      "exit_code" => envelope.fetch("exit_code"),
      "diagnostics" => envelope.fetch("diagnostics").first(10).map { |value| redact(value.to_s)[0, 4096] },
      "artifact_checksums" => envelope.fetch("artifacts").map do |artifact|
        artifact.slice("sha256", "media_type", "size")
      end
    }
    base.merge("result_digest" => digest_value(base))
  end

  def supporting_result_fact(root, case_id)
    stdout, stderr, status = Open3.capture3(*CATALOG_QUERY_ARGV, chdir: root)
    exit_code = status.exitstatus
    raise ArgumentError, "OBLIGATION_OWNER_QUERY_FAILED: exit=#{exit_code}" unless status.success?
    diagnostic = redact([stdout, stderr].reject(&:empty?).join("\n"))[0, 4096]
    raise ArgumentError, "OBLIGATION_OWNER_QUERY_COUNT_INVALID" unless diagnostic.include?("selected=7")

    base = {
      "check_id" => "catalog-owner-query",
      "layer" => "catalog",
      "argv" => CATALOG_QUERY_ARGV,
      "cwd" => ".",
      "case_ids" => [case_id],
      "status" => "PASS",
      "exit_code" => 0,
      "diagnostics" => [diagnostic],
      "artifact_checksums" => []
    }
    base.merge("result_digest" => digest_value(base))
  end

  def local_runtime_facts(root, relative_path, subject_manifest_path:, subject_manifest_digest:, tested_subject_digest:)
    runtime_snapshot = verified_local_file(root, relative_path, [], "LOCAL_CHROME_RUNTIME")
    raise ArgumentError, "LOCAL_CHROME_RUNTIME_MISSING" unless runtime_snapshot
    argv = [
      "/usr/bin/env", "node", "web/scripts/validate-local-chrome-evidence.mjs",
      "--runtime", relative_path,
      "--scenario", "web/verification/browser-scenarios.json",
      "--subject-manifest", subject_manifest_path,
      "--subject-manifest-digest", subject_manifest_digest,
      "--tested-subject-digest", tested_subject_digest
    ]
    stdout, stderr, status = Open3.capture3(*argv, chdir: root)
    raise ArgumentError, "LOCAL_CHROME_RUNTIME_INVALID: #{redact(stderr)[0, 1000]}" unless status.success?
    raise ArgumentError, "LOCAL_CHROME_RUNTIME_PASS_MARKER_MISSING" unless stdout.include?("local_chrome_runtime=PASS")

    runtime = JSON.parse(runtime_snapshot.bytes)
    run = runtime.fetch("run")
    scenario = run.fetch("scenario")
    artifacts = run.fetch("artifacts")
    identity = run.fetch("runtime")
    {
      "path" => relative_path,
      "sha256" => snapshot_sha256(runtime_snapshot),
      "subject_manifest_path" => scenario.dig("subject", "manifestPath"),
      "subject_manifest_digest" => scenario.dig("subject", "manifestDigest"),
      "tested_subject_digest" => scenario.dig("subject", "testedSubjectDigest"),
      "brand" => identity.fetch("brand"),
      "full_version" => identity.fetch("fullVersion"),
      "major" => identity.fetch("major"),
      "executable_path" => identity.fetch("canonicalPath"),
      "viewport" => identity.fetch("viewport"),
      "launch_succeeded" => identity.dig("launch", "succeeded"),
      "scenario_id" => scenario.dig("contract", "scenarioId"),
      "visual_rule_id" => scenario.dig("contract", "visualRuleId"),
      "response_status" => scenario.dig("response", "status"),
      "response_body_sha256" => scenario.dig("response", "bodySha256"),
      "marker_name" => scenario.dig("response", "marker", "name"),
      "marker_value" => scenario.dig("response", "marker", "value"),
      "screenshot_sha256" => artifacts.dig("screenshot", "sha256"),
      "dom_sha256" => artifacts.dig("dom", "sha256"),
      "transcript_sha256" => artifacts.dig("transcript", "sha256"),
      "console_sha256" => artifacts.dig("console", "sha256")
    }
  rescue JSON::ParserError
    raise ArgumentError, "LOCAL_CHROME_RUNTIME_JSON_INVALID"
  end

  def load_valid_local_check_manifest(root, relative_path)
    manifest_snapshot = verified_local_file(root, relative_path, [], "CHECK_MANIFEST")
    raise ArgumentError, "CHECK_MANIFEST_MISSING: #{relative_path}" unless manifest_snapshot
    manifest = JSON.parse(manifest_snapshot.bytes)
    raise ArgumentError, "CHECK_MANIFEST_SCHEMA_INVALID" unless manifest["schema_version"] == EVIDENCE_MANIFEST_SCHEMA

    definitions = Phase01RunChecks.definitions_for(["all"])
    expected_ids = definitions.map { |definition| definition.fetch("id") }
    actual_ids = manifest.fetch("entries", []).map { |entry| entry["check_id"] }
    raise ArgumentError, "CHECK_MANIFEST_NOT_LOCAL_ALL" unless actual_ids == expected_ids
    registries = Phase01RunChecks.subject_registries(definitions)
    contracts = Phase01RunChecks.check_contracts(definitions)
    validation = validate_evidence_manifest(
      root: root, manifest: manifest, registries: registries, check_contracts: contracts, required_owner: OWNER
    )
    raise ArgumentError, "CHECK_MANIFEST_INVALID: #{validation.join(';')}" unless validation.empty?

    envelopes = manifest.fetch("entries").to_h do |entry|
      envelope_snapshot = verified_local_file(root, entry.fetch("path"), [], "CHECK_ENVELOPE")
      raise ArgumentError, "CHECK_ENVELOPE_MISSING: #{entry.fetch('check_id')}" unless envelope_snapshot
      [entry.fetch("check_id"), JSON.parse(envelope_snapshot.bytes)]
    end
    unless envelopes.values.all? { |envelope| envelope["status"] == "PASS" && envelope["exit_code"] == 0 }
      raise ArgumentError, "CHECK_MANIFEST_NON_PASS"
    end
    [manifest, envelopes]
  rescue JSON::ParserError
    raise ArgumentError, "CHECK_MANIFEST_JSON_INVALID"
  end

  def build_obligation_evidence(root:, check_manifest:, output_dir:, matrix:, catalog:, runtime:)
    raise ArgumentError, "OBLIGATION_OUTPUT_DIR_INVALID" unless canonical_relative_path?(output_dir)
    check_record, envelopes = load_valid_local_check_manifest(root, check_manifest)
    matrix_rows = parse_test_matrix(root, matrix)
    catalog_rows = parse_obligation_catalog(root, catalog)

    source_snapshot = verified_local_file(root, check_record.fetch("subject_manifest_path"), [], "CHECK_SUBJECT")
    raise ArgumentError, "CHECK_SUBJECT_MISSING" unless source_snapshot
    source = JSON.parse(source_snapshot.bytes)
    source_errors = validate_subject_manifest(root: root, manifest: source, registries: Phase01RunChecks.subject_registries)
    raise ArgumentError, "CHECK_SUBJECT_INVALID: #{source_errors.join(';')}" unless source_errors.empty?

    root_real = File.realpath(root)
    output_root = File.expand_path(output_dir, root_real)
    raise ArgumentError, "OBLIGATION_OUTPUT_DIR_OUTSIDE_ROOT" unless output_root.start_with?("#{root_real}#{File::SEPARATOR}")
    FileUtils.mkdir_p(output_root)
    subject_path = File.join(output_dir, "tested-inputs.json")
    atomic_write_json(File.join(root, subject_path), source)
    subject_manifest_digest_value = subject_manifest_digest(source)
    tested_subject_digest_value = tested_subject_digest(source.fetch("inputs"))
    runtime_facts = local_runtime_facts(
      root,
      runtime,
      subject_manifest_path: subject_path,
      subject_manifest_digest: subject_manifest_digest_value,
      tested_subject_digest: tested_subject_digest_value
    )

    entries = OBLIGATION_REGISTRY.map do |definition|
      obligation_id = definition.fetch("obligation_id")
      matrix_row = matrix_rows.fetch(obligation_id)
      catalog_row = catalog_rows.fetch(obligation_id)
      unless catalog_row.slice("requirement_ids", "behavior_id", "catalog_test", "evidence_path") ==
          matrix_row.slice("requirement_ids", "behavior_id", "catalog_test", "evidence_path")
        raise ArgumentError, "OBLIGATION_RELATION_MISMATCH: #{obligation_id}"
      end
      raise ArgumentError, "OBLIGATION_OWNER_MISMATCH: #{obligation_id}" unless catalog_row["owner"] == OWNER
      check_results = definition.fetch("check_ids").map do |check_id|
        envelope = envelopes.fetch(check_id) { raise ArgumentError, "OBLIGATION_CHECK_MISSING: #{check_id}" }
        raise ArgumentError, "OBLIGATION_CHECK_CASE_MISMATCH: #{check_id}" unless envelope.fetch("case_ids").include?(matrix_row.fetch("case_id"))
        obligation_result_fact(envelope)
      end
      supporting_results = definition.fetch("supporting") ? [supporting_result_fact(root, matrix_row.fetch("case_id"))] : []
      summary = {
        "schema_version" => OBLIGATION_SUMMARY_SCHEMA,
        "phase" => PHASE,
        "owner" => OWNER,
        "obligation_id" => obligation_id,
        "requirement_ids" => matrix_row.fetch("requirement_ids"),
        "behavior_id" => matrix_row.fetch("behavior_id"),
        "catalog_test" => matrix_row.fetch("catalog_test"),
        "case_id" => matrix_row.fetch("case_id"),
        "matrix_command" => matrix_row.fetch("matrix_command"),
        "evidence_path" => matrix_row.fetch("evidence_path"),
        "subject_manifest_path" => subject_path,
        "subject_manifest_digest" => subject_manifest_digest_value,
        "tested_subject_digest" => tested_subject_digest_value,
        "status" => "PASS",
        "exit_code" => 0,
        "check_results" => check_results,
        "supporting_results" => supporting_results,
        "supporting_contracts" => obligation_id == "OBL-FOUND-TRACE-003" ? SUPPORTING_CONTRACTS : [],
        "runtime" => obligation_id == "OBL-NFR-BROWSER" ? runtime_facts : nil,
        "product_acceptance_claims" => []
      }
      relative = File.join(output_dir, "#{obligation_id}.json")
      atomic_write_json(File.join(root, relative), summary)
      summary_snapshot = verified_local_file(root, relative, [], "OBLIGATION_SUMMARY")
      raise ArgumentError, "OBLIGATION_SUMMARY_UNREADABLE: #{relative}" unless summary_snapshot
      {
        "obligation_id" => obligation_id,
        "path" => relative,
        "sha256" => snapshot_sha256(summary_snapshot),
        "status" => "PASS",
        "case_id" => matrix_row.fetch("case_id"),
        "behavior_id" => matrix_row.fetch("behavior_id"),
        "catalog_test" => matrix_row.fetch("catalog_test"),
        "evidence_path" => matrix_row.fetch("evidence_path")
      }
    end

    runtime_snapshot = verified_local_file(root, runtime, [], "LOCAL_CHROME_RUNTIME")
    raise ArgumentError, "LOCAL_CHROME_RUNTIME_MISSING" unless runtime_snapshot
    manifest = {
      "schema_version" => OBLIGATION_MANIFEST_SCHEMA,
      "phase" => PHASE,
      "owner" => OWNER,
      "subject_manifest_path" => subject_path,
      "subject_manifest_digest" => subject_manifest_digest_value,
      "tested_subject_digest" => tested_subject_digest_value,
      "entries" => entries,
      "runtime_artifact" => {
        "path" => runtime,
        "sha256" => snapshot_sha256(runtime_snapshot),
        "media_type" => "application/json",
        "size" => runtime_snapshot.bytes.bytesize
      },
      "ci_locators" => CI_LOCATOR_PATHS.map do |relative|
        snapshot = verified_local_file(root, relative, [], "CI_LOCATOR")
        raise ArgumentError, "CI_LOCATOR_MISSING: #{relative}" unless snapshot
        { "path" => relative, "sha256" => snapshot_sha256(snapshot) }
      end
    }
    atomic_write_json(File.join(root, output_dir, "evidence-manifest.json"), manifest)
    validation = validate_obligation_manifest(
      root: root, manifest: manifest, registries: Phase01RunChecks.subject_registries,
      check_contracts: Phase01RunChecks.check_contracts, required_owner: OWNER,
      output_dir: output_dir
    )
    raise ArgumentError, "OBLIGATION_MANIFEST_INVALID: #{validation.join(';')}" unless validation.empty?
    manifest
  end

  def validate_obligation_result(result, contract, expected_case, errors, label)
    exact_hash(result, OBLIGATION_RESULT_FIELDS, errors, label)
    return unless result.is_a?(Hash)
    digest_payload = result.reject { |key, _value| key == "result_digest" }
    errors << "#{label}_DIGEST_MISMATCH" unless result["result_digest"] == digest_value(digest_payload)
    errors << "#{label}_STATUS_INVALID" unless result["status"] == "PASS" && result["exit_code"] == 0
    errors << "#{label}_CASE_MISMATCH" unless result["case_ids"].is_a?(Array) && result["case_ids"].include?(expected_case)
    Array(result["diagnostics"]).each do |diagnostic|
      errors << "#{label}_DIAGNOSTIC_INVALID" unless diagnostic.is_a?(String) && diagnostic.length <= 4096
      errors << "#{label}_SECRET_DETECTED" if diagnostic.is_a?(String) && secret_bearing?(diagnostic)
      errors << "#{label}_TRANSIENT_PATH_FORBIDDEN" if diagnostic.is_a?(String) && diagnostic.include?("/EVIDENCE/runs/")
    end
    if contract
      %w[layer argv cwd].each do |field|
        errors << "#{label}_CONTRACT_MISMATCH: #{field}" unless result[field] == contract[field]
      end
      errors << "#{label}_CASE_CONTRACT_MISMATCH" unless result["case_ids"] == contract["case_ids"].sort
    end
  end

  def validate_obligation_summary(root:, summary:, definition:, subject:, matrix_rows:, catalog_rows:, runtime_facts:, check_contracts:, subject_path:)
    errors = []
    exact_hash(summary, OBLIGATION_SUMMARY_FIELDS, errors, "OBLIGATION_SUMMARY")
    return errors unless summary.is_a?(Hash)
    obligation_id = definition.fetch("obligation_id")
    matrix_row = matrix_rows.fetch(obligation_id)
    catalog_row = catalog_rows.fetch(obligation_id)
    errors << "OBLIGATION_SUMMARY_SCHEMA_UNSUPPORTED" unless summary["schema_version"] == OBLIGATION_SUMMARY_SCHEMA
    errors << "OBLIGATION_SUMMARY_PHASE_MISMATCH" unless summary["phase"] == PHASE
    errors << "OBLIGATION_SUMMARY_OWNER_MISMATCH" unless summary["owner"] == OWNER
    errors << "OBLIGATION_SUMMARY_ID_MISMATCH" unless summary["obligation_id"] == obligation_id
    %w[requirement_ids behavior_id catalog_test evidence_path].each do |field|
      errors << "OBLIGATION_SUMMARY_MATRIX_MISMATCH: #{field}" unless summary[field] == matrix_row[field]
      errors << "OBLIGATION_SUMMARY_CATALOG_MISMATCH: #{field}" unless summary[field] == catalog_row[field]
    end
    %w[case_id matrix_command].each do |field|
      errors << "OBLIGATION_SUMMARY_MATRIX_MISMATCH: #{field}" unless summary[field] == matrix_row[field]
    end
    errors << "OBLIGATION_SUMMARY_STATUS_INVALID" unless summary["status"] == "PASS" && summary["exit_code"] == 0
    errors << "OBLIGATION_SUMMARY_SUBJECT_PATH_MISMATCH" unless summary["subject_manifest_path"] == subject_path
    errors << "OBLIGATION_SUMMARY_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless summary["subject_manifest_digest"] == subject_manifest_digest(subject)
    errors << "OBLIGATION_SUMMARY_TESTED_SUBJECT_DIGEST_MISMATCH" unless summary["tested_subject_digest"] == tested_subject_digest(subject.fetch("inputs", []))
    errors << "OBLIGATION_PRODUCT_ACCEPTANCE_SPOOF" unless summary["product_acceptance_claims"] == []
    expected_support = obligation_id == "OBL-FOUND-TRACE-003" ? SUPPORTING_CONTRACTS : []
    errors << "OBLIGATION_SUPPORTING_CONTRACTS_INVALID" unless summary["supporting_contracts"] == expected_support

    results = summary["check_results"]
    unless results.is_a?(Array) && results.map { |result| result["check_id"] } == definition.fetch("check_ids")
      errors << "OBLIGATION_CHECK_SET_INVALID"
    else
      results.each do |result|
        validate_obligation_result(result, check_contracts[result["check_id"]], matrix_row.fetch("case_id"), errors, "OBLIGATION_CHECK_RESULT")
      end
    end
    supporting = summary["supporting_results"]
    if definition.fetch("supporting")
      unless supporting.is_a?(Array) && supporting.length == 1 && supporting.dig(0, "check_id") == "catalog-owner-query"
        errors << "OBLIGATION_OWNER_QUERY_MISSING"
      else
        expected = { "layer" => "catalog", "argv" => CATALOG_QUERY_ARGV, "cwd" => ".", "case_ids" => [matrix_row.fetch("case_id")] }
        validate_obligation_result(supporting.first, expected, matrix_row.fetch("case_id"), errors, "OBLIGATION_SUPPORT_RESULT")
        errors << "OBLIGATION_OWNER_QUERY_COUNT_INVALID" unless Array(supporting.first["diagnostics"]).join.include?("selected=7")
      end
    else
      errors << "OBLIGATION_SUPPORT_RESULT_EXTRA" unless supporting == []
    end
    if obligation_id == "OBL-NFR-BROWSER"
      errors << "OBLIGATION_RUNTIME_MISMATCH" unless summary["runtime"] == runtime_facts
    else
      errors << "OBLIGATION_RUNTIME_FOREIGN" unless summary["runtime"].nil?
    end
    errors.uniq
  end

  def validate_obligation_manifest(root:, manifest:, registries:, check_contracts:, required_owner:, output_dir: EVIDENCE_DIR)
    errors = []
    exact_hash(manifest, OBLIGATION_MANIFEST_FIELDS, errors, "OBLIGATION_MANIFEST")
    return errors unless manifest.is_a?(Hash)
    errors << "OBLIGATION_MANIFEST_SCHEMA_UNSUPPORTED" unless manifest["schema_version"] == OBLIGATION_MANIFEST_SCHEMA
    errors << "OBLIGATION_MANIFEST_PHASE_MISMATCH" unless manifest["phase"] == PHASE
    errors << "OBLIGATION_MANIFEST_OWNER_MISMATCH" unless manifest["owner"] == required_owner && required_owner == OWNER
    expected_subject_path = File.join(output_dir, "tested-inputs.json")
    errors << "OBLIGATION_MANIFEST_SUBJECT_PATH_MISMATCH" unless manifest["subject_manifest_path"] == expected_subject_path
    subject_snapshot = verified_local_file(root, expected_subject_path, errors, "OBLIGATION_SUBJECT")
    subject = nil
    if subject_snapshot
      begin
        subject = JSON.parse(subject_snapshot.bytes)
        errors.concat(validate_subject_manifest(root: root, manifest: subject, registries: registries))
        errors << "OBLIGATION_MANIFEST_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless manifest["subject_manifest_digest"] == subject_manifest_digest(subject)
        errors << "OBLIGATION_MANIFEST_TESTED_SUBJECT_DIGEST_MISMATCH" unless manifest["tested_subject_digest"] == tested_subject_digest(subject.fetch("inputs", []))
      rescue JSON::ParserError
        errors << "OBLIGATION_SUBJECT_JSON_INVALID"
      end
    end
    matrix_rows = parse_test_matrix(root, "#{PHASE_DIR}/TEST-MATRIX.md")
    catalog_rows = parse_obligation_catalog(root, ".planning/PRD-OBLIGATIONS.md")
    runtime_facts = if subject
      local_runtime_facts(
        root,
        LOCAL_RUNTIME_PATH,
        subject_manifest_path: expected_subject_path,
        subject_manifest_digest: subject_manifest_digest(subject),
        tested_subject_digest: tested_subject_digest(subject.fetch("inputs", []))
      )
    end
    runtime_snapshot = verified_local_file(root, LOCAL_RUNTIME_PATH, errors, "OBLIGATION_RUNTIME")
    expected_runtime = runtime_snapshot && {
      "path" => LOCAL_RUNTIME_PATH, "sha256" => snapshot_sha256(runtime_snapshot),
      "media_type" => "application/json", "size" => runtime_snapshot.bytes.bytesize
    }
    errors << "OBLIGATION_MANIFEST_RUNTIME_MISMATCH" unless manifest["runtime_artifact"] == expected_runtime
    expected_ci = CI_LOCATOR_PATHS.map do |relative|
      snapshot = verified_local_file(root, relative, errors, "OBLIGATION_CI_LOCATOR")
      { "path" => relative, "sha256" => snapshot && snapshot_sha256(snapshot) }
    end
    errors << "OBLIGATION_MANIFEST_CI_LOCATORS_MISMATCH" unless manifest["ci_locators"] == expected_ci

    entries = manifest["entries"]
    expected_ids = OBLIGATION_REGISTRY.map { |definition| definition.fetch("obligation_id") }
    unless entries.is_a?(Array)
      errors << "OBLIGATION_MANIFEST_ENTRIES_INVALID"
      return errors
    end
    actual_ids = entries.filter_map { |entry| entry["obligation_id"] if entry.is_a?(Hash) }
    errors << "OBLIGATION_MANIFEST_ENTRY_SET_INVALID" unless actual_ids == expected_ids
    errors << "OBLIGATION_MANIFEST_ENTRY_DUPLICATE" unless actual_ids.uniq.length == actual_ids.length
    entries.each_with_index do |entry, index|
      exact_hash(entry, OBLIGATION_MANIFEST_ENTRY_FIELDS, errors, "OBLIGATION_MANIFEST_ENTRY")
      next unless entry.is_a?(Hash) && index < OBLIGATION_REGISTRY.length
      definition = OBLIGATION_REGISTRY[index]
      obligation_id = definition.fetch("obligation_id")
      expected_path = File.join(output_dir, "#{obligation_id}.json")
      errors << "OBLIGATION_MANIFEST_ENTRY_PATH_MISMATCH" unless entry["path"] == expected_path
      summary_snapshot = verified_local_file(root, entry["path"], errors, "OBLIGATION_SUMMARY")
      next unless summary_snapshot
      errors << "OBLIGATION_MANIFEST_ENTRY_CHECKSUM_MISMATCH" unless entry["sha256"] == snapshot_sha256(summary_snapshot)
      begin
        summary = JSON.parse(summary_snapshot.bytes)
        errors.concat(validate_obligation_summary(
          root: root, summary: summary, definition: definition, subject: subject || {},
          matrix_rows: matrix_rows, catalog_rows: catalog_rows, runtime_facts: runtime_facts,
          check_contracts: check_contracts, subject_path: expected_subject_path
        ))
        %w[obligation_id status case_id behavior_id catalog_test evidence_path].each do |field|
          errors << "OBLIGATION_MANIFEST_ENTRY_SUMMARY_MISMATCH: #{field}" unless entry[field] == summary[field]
        end
      rescue JSON::ParserError
        errors << "OBLIGATION_SUMMARY_JSON_INVALID"
      end
    end
    errors.uniq
  rescue ArgumentError => error
    errors << error.message
    errors.uniq
  end

  def exact_hash(value, expected_fields, errors, prefix)
    unless value.is_a?(Hash)
      errors << "#{prefix}_TYPE_INVALID"
      return
    end
    actual = value.keys.map(&:to_s)
    (expected_fields - actual).sort.each { |field| errors << "#{prefix}_MISSING_FIELD: #{field}" }
    (actual - expected_fields).sort.each do |field|
      token = prefix == "EVIDENCE" ? "EVIDENCE_FORBIDDEN_FIELD" : "#{prefix}_UNKNOWN_FIELD"
      errors << "#{token}: #{field}"
    end
  end

  def validate_string_ids(values, errors, prefix)
    unless values.is_a?(Array) && !values.empty?
      errors << "#{prefix}_EMPTY"
      return
    end
    errors << "#{prefix}_DUPLICATE" unless values.uniq.length == values.length
    errors << "#{prefix}_ORDER_INVALID" unless values == values.sort
    values.each { |value| errors << "#{prefix}_INVALID: #{value.inspect}" unless value.is_a?(String) && !value.empty? }
  end

  def validate_check_contract(envelope, contract, errors)
    unless contract.is_a?(Hash)
      errors << "EVIDENCE_CHECK_CONTRACT_MISSING: #{envelope['check_id']}"
      return
    end
    {
      "layer" => contract["layer"],
      "argv" => contract["argv"],
      "cwd" => contract["cwd"],
      "obligation_ids" => contract["obligation_ids"]&.sort,
      "case_ids" => contract["case_ids"]&.sort
    }.each do |field, expected|
      errors << "EVIDENCE_CHECK_CONTRACT_MISMATCH: check=#{envelope['check_id']} field=#{field}" unless envelope[field] == expected
    end
  end

  def validate_argv(argv, errors)
    unless argv.is_a?(Array) && !argv.empty?
      errors << "EVIDENCE_ARGV_EMPTY"
      return
    end
    argv.each do |argument|
      errors << "EVIDENCE_ARGV_INVALID" unless argument.is_a?(String) && !argument.empty? && !argument.include?("\0")
      errors << "EVIDENCE_SECRET_DETECTED: argv" if argument.is_a?(String) && secret_bearing?(argument)
    end
  end

  def validate_timestamp_pair(envelope, errors)
    started = parse_utc_timestamp(envelope["started_at"], errors, "EVIDENCE_STARTED_AT")
    completed = parse_utc_timestamp(envelope["completed_at"], errors, "EVIDENCE_COMPLETED_AT")
    errors << "EVIDENCE_TIMESTAMP_ORDER_INVALID" if started && completed && completed < started
  end

  def parse_utc_timestamp(value, errors, prefix)
    unless value.is_a?(String) && value.end_with?("Z")
      errors << "#{prefix}_INVALID"
      return nil
    end
    Time.iso8601(value)
  rescue ArgumentError
    errors << "#{prefix}_INVALID"
    nil
  end

  def validate_environment(environment, errors)
    unless environment.is_a?(Hash)
      errors << "EVIDENCE_ENVIRONMENT_TYPE_INVALID"
      return
    end
    (environment.keys.map(&:to_s) - ENVIRONMENT_KEYS).sort.each { |key| errors << "EVIDENCE_ENVIRONMENT_FIELD_FORBIDDEN: #{key}" }
    environment.each_value { |value| errors << "EVIDENCE_SECRET_DETECTED: environment" if value.is_a?(String) && secret_bearing?(value) }
  end

  def validate_diagnostics(error_ids, diagnostics, errors)
    unless error_ids.is_a?(Array) && error_ids.all? { |value| value.is_a?(String) && value.match?(STABLE_ID) }
      errors << "EVIDENCE_ERRORS_INVALID"
    end
    unless diagnostics.is_a?(Array) && diagnostics.all? { |value| value.is_a?(String) }
      errors << "EVIDENCE_DIAGNOSTICS_INVALID"
      return
    end
    diagnostics.each { |value| errors << "EVIDENCE_SECRET_DETECTED: diagnostics" if secret_bearing?(value) }
  end

  def secret_bearing?(value)
    text = value.to_s
    text.match?(SECRET_PATTERN) || text.match?(AUTH_SCHEME_PATTERN) || text.match?(PHONE_PATTERN) || text.match?(URL_CREDENTIAL_PATTERN) || text.match?(PRIVATE_KEY_BEGIN_PATTERN)
  end

  def redact(value)
    original = value.to_s
    redacted = original.gsub(PRIVATE_KEY_PATTERN, "[REDACTED_PRIVATE_KEY]")
    redacted = redacted.gsub(/-----BEGIN [^-\r\n]*PRIVATE KEY-----.*\z/mi, "[REDACTED_PRIVATE_KEY]")
    redact_continuation = false
    redacted = redacted.lines.map do |line|
      if line.match?(SECRET_PATTERN) || line.match?(AUTH_SCHEME_PATTERN)
        redact_continuation = true
        newline = line.end_with?("\n") ? "\n" : ""
        "[REDACTED_SECRET_LINE]#{newline}"
      elsif redact_continuation && line.match?(/\A[ \t]+/)
        newline = line.end_with?("\n") ? "\n" : ""
        "[REDACTED_SECRET_CONTINUATION]#{newline}"
      else
        redact_continuation = false
        line
      end
    end.join
    redacted = redacted.gsub(URL_CREDENTIAL_PATTERN) do |match|
      scheme = match.split("://", 2).first
      "#{scheme}://[REDACTED]@"
    end
    redacted = redacted.gsub(PHONE_PATTERN, "[REDACTED_PHONE]")
    return redacted unless secret_bearing?(redacted)

    "[REDACTED_OUTPUT sha256=#{Digest::SHA256.hexdigest(original)} bytes=#{original.bytesize}]"
  end

  def validate_artifacts(root, artifacts, errors)
    unless artifacts.is_a?(Array)
      errors << "EVIDENCE_ARTIFACTS_TYPE_INVALID"
      return
    end
    artifacts.each_with_index do |artifact, index|
      exact_hash(artifact, ARTIFACT_FIELDS, errors, "EVIDENCE_ARTIFACT")
      next unless artifact.is_a?(Hash)
      snapshot = verified_local_file(root, artifact["path"], errors, "EVIDENCE_ARTIFACT")
      next unless snapshot
      digest = snapshot_sha256(snapshot)
      errors << "EVIDENCE_ARTIFACT_CHECKSUM_MISMATCH: index=#{index}" unless artifact["sha256"] == digest
      errors << "EVIDENCE_ARTIFACT_SIZE_MISMATCH: index=#{index}" unless artifact["size"] == snapshot.bytes.bytesize
      errors << "EVIDENCE_ARTIFACT_MEDIA_TYPE_INVALID: index=#{index}" unless artifact["media_type"].is_a?(String) && artifact["media_type"].include?("/")
    end
  end

  def reject_forbidden_keys(value, errors, path = [])
    case value
    when Hash
      value.each do |key, nested|
        current = path + [key.to_s]
        errors << "EVIDENCE_FORBIDDEN_FIELD: #{current.join('.')}" if FORBIDDEN_EVIDENCE_KEYS.include?(key.to_s.downcase)
        reject_forbidden_keys(nested, errors, current)
      end
    when Array
      value.each_with_index { |nested, index| reject_forbidden_keys(nested, errors, path + [index.to_s]) }
    end
  end
end
