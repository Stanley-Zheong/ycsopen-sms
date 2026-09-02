#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "open3"
require "optparse"
require "pathname"
require "rbconfig"
require "ripper"
require "tmpdir"
require "uri"

require_relative "verification-evidence"

module Phase01DeliveryAttestation
  PHASE = "01"
  PACKAGE = "engineering-verification-foundation"
  PHASE_NAME = "01-engineering-verification-foundation"
  TAG_SCHEMA = "ycsopen-sms-delivery-attestation-v1"
  LOCAL_PR_SCHEMA = "phase01-local-pr-check-v1"
  SHA1 = /\A[0-9a-f]{40}\z/
  SHA256 = /\A[0-9a-f]{64}\z/
  REMOTE_NAME = /\A[A-Za-z0-9][A-Za-z0-9._-]*\z/
  ROLES = %w[implementation test config contract validator].freeze
  SUBJECT_FIELDS = %w[schema_version phase inputs].freeze
  INPUT_FIELDS = %w[path mode sha256 role].freeze
  EVIDENCE_FIELDS = %w[
    schema_version phase owner subject_manifest_path subject_manifest_digest
    tested_subject_digest entries aggregate
  ].freeze
  EVIDENCE_ENTRY_FIELDS = %w[check_id path sha256 status obligation_ids case_ids].freeze
  EVIDENCE_AGGREGATE_FIELDS = %w[path sha256 status].freeze
  OBLIGATION_EVIDENCE_SCHEMA = "phase01-obligation-evidence-manifest-v1"
  OBLIGATION_EVIDENCE_FIELDS = %w[
    schema_version phase owner subject_manifest_path subject_manifest_digest
    tested_subject_digest entries runtime_artifact ci_locators
  ].freeze
  OBLIGATION_EVIDENCE_ENTRY_FIELDS = %w[
    obligation_id path sha256 status case_id behavior_id catalog_test evidence_path
  ].freeze
  RUNTIME_ARTIFACT_FIELDS = %w[path sha256 media_type size].freeze
  CI_LOCATOR_FIELDS = %w[path sha256].freeze
  EXPECTED_OBLIGATION_IDS = %w[
    OBL-FOUND-TRACE-001 OBL-FOUND-TRACE-002 OBL-FOUND-TRACE-003 OBL-FOUND-TRACE-004
    OBL-FOUND-UI-DRIFT-001 OBL-FOUND-UI-DRIFT-002 OBL-NFR-BROWSER
  ].freeze
  EXPECTED_CI_LOCATORS = [".github/workflows/ci.yml", "scripts/verify-phase-01"].freeze
  TAG_FIELDS = %w[
    phase package branch commit tree subject_manifest_path subject_manifest_digest
    tested_subject_digest evidence_manifest_path evidence_manifest_digest
    pr_locator check_name check_locator attestor_name attestor_email external_actor status
  ].freeze
  SUMMARY_FIELDS = {
    remote_name: "Delivery remote name",
    remote_url: "Delivery remote URL",
    branch_ref: "Delivery branch ref",
    tag_ref: "Delivery tag ref",
    pr_locator: "Delivery PR locator",
    check_name: "Delivery required check"
  }.freeze
  REVIEW_HEADERS = [
    "Attempt", "BLOCKER", "HIGH", "Escalated", "Subject manifest path",
    "Subject manifest digest", "Tested subject digest", "Result"
  ].freeze
  UNSUPPORTED_LITERAL = Object.new.freeze
  Context = Struct.new(
    :phase, :package, :phase_name, :tag_ref, :subject_schema, :evidence_schema,
    :registry_path,
    keyword_init: true
  )

  module_function

  def context_for(options, errors)
    phase = options.fetch(:phase)
    summary = options.fetch(:summary)
    match = summary.match(%r{\A\.planning/phases/(\d{2})-([a-z0-9]+(?:-[a-z0-9]+)*)/SUMMARY\.md\z})
    if match
      errors << "SUMMARY_PHASE_PATH_MISMATCH" unless match[1] == phase
      package = match[2]
    elsif phase == PHASE
      # The compact path is allowed only by the isolated Phase 1 fixture suite.
      package = PACKAGE
    else
      errors << "SUMMARY_CANONICAL_PATH_REQUIRED"
      return nil
    end
    phase_name = "#{phase}-#{package}"
    Context.new(
      phase: phase,
      package: package,
      phase_name: phase_name,
      tag_ref: "refs/tags/ycsopen-sms/phase-#{phase}/delivery",
      subject_schema: "phase#{phase}-tested-inputs-v1",
      evidence_schema: "phase#{phase}-evidence-manifest-v1",
      registry_path: "scripts/lib/phase-#{phase}/run_checks.rb"
    )
  end

  def canonical(value)
    case value
    when Hash
      value.keys.map(&:to_s).sort.to_h do |key|
        nested = value.key?(key) ? value[key] : value[key.to_sym]
        [key, canonical(nested)]
      end
    when Array
      value.map { |entry| canonical(entry) }
    else
      value
    end
  end

  def canonical_json(value)
    JSON.generate(canonical(value))
  end

  def digest_value(value)
    Digest::SHA256.hexdigest(canonical_json(value))
  end

  def canonical_relative_path?(value)
    return false unless value.is_a?(String) && !value.empty? && !value.include?("\0") && !value.include?("\\")

    path = Pathname(value)
    !path.absolute? && path.cleanpath.to_s == value && path.each_filename.none? { |part| part == ".." }
  end

  def safe_ref?(value, prefix)
    return false unless value.is_a?(String) && value.start_with?(prefix)
    return false if value.include?("..") || value.include?("@{") || value.include?("//") || value.end_with?("/") || value.end_with?(".lock")

    value.match?(/\Arefs\/(?:heads|tags)\/[A-Za-z0-9][A-Za-z0-9._\/-]*\z/)
  end

  def exact_hash(value, fields, errors, label)
    unless value.is_a?(Hash)
      errors << "#{label}_TYPE_INVALID"
      return false
    end
    actual = value.keys.map(&:to_s).sort
    expected = fields.sort
    errors << "#{label}_FIELDS_INVALID expected=#{expected.join(',')} actual=#{actual.join(',')}" unless actual == expected
    actual == expected
  end

  def read_local(root, relative, errors, label)
    VerificationEvidence.verified_local_file(root, relative, errors, label)&.bytes
  end

  def parse_json(bytes, errors, label)
    JSON.parse(bytes)
  rescue JSON::ParserError
    errors << "#{label}_JSON_INVALID"
    nil
  end

  def git(root, *argv)
    Open3.capture3("git", "-C", root, *argv)
  rescue Errno::ENOENT => error
    ["", error.message, nil]
  end

  def bare_git(git_dir, *argv, stdin_data: "")
    Open3.capture3("git", "--git-dir", git_dir, *argv, stdin_data: stdin_data)
  rescue Errno::ENOENT => error
    ["", error.message, nil]
  end

  def command_value(stdout, status, errors, label)
    unless status&.success?
      errors << "#{label}_COMMAND_FAILED"
      return nil
    end
    value = stdout.strip
    if value.empty?
      errors << "#{label}_EMPTY"
      return nil
    end
    value
  end

  def parse_summary(bytes, errors)
    values = {}
    text = bytes.to_s
    SUMMARY_FIELDS.each do |key, label|
      matches = text.scan(/^#{Regexp.escape(label)}:\s*`([^`\r\n]+)`\s*$/)
      if matches.length != 1
        errors << "SUMMARY_#{key.to_s.upcase}_INVALID matches=#{matches.length}"
      else
        values[key] = matches.first.first
      end
    end
    values
  end

  def local_remote?(url)
    return true if url.start_with?("file://")
    return true if Pathname(url).absolute?

    false
  rescue ArgumentError
    false
  end

  def github_repository(url)
    case url
    when %r{\Ahttps://github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?\z}
      [Regexp.last_match(1), Regexp.last_match(2)]
    when %r{\Agit@github\.com:([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?\z}
      [Regexp.last_match(1), Regexp.last_match(2)]
    when %r{\Assh://git@github\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\.git)?\z}
      [Regexp.last_match(1), Regexp.last_match(2)]
    end
  end

  def remote_url_contains_credentials?(url)
    uri = URI.parse(url)
    uri.userinfo && !uri.userinfo.empty?
  rescue URI::InvalidURIError
    false
  end

  def parse_remote_refs(output, branch_ref, tag_ref, errors)
    values = Hash.new { |hash, key| hash[key] = [] }
    output.lines.each do |line|
      match = line.match(/\A([0-9a-f]{40})\t([^\s]+)\s*\z/)
      unless match
        errors << "REMOTE_REF_RESPONSE_INVALID"
        next
      end
      values[match[2]] << match[1]
    end
    branch_values = values.fetch(branch_ref, [])
    tag_values = values.fetch(tag_ref, [])
    peeled_values = values.fetch("#{tag_ref}^{}", [])
    errors << "REMOTE_BRANCH_REF_MISSING" if branch_values.empty?
    errors << "REMOTE_BRANCH_REF_AMBIGUOUS" if branch_values.length > 1
    errors << "DELIVERY_TAG_MISSING" if tag_values.empty?
    errors << "DELIVERY_TAG_REF_AMBIGUOUS" if tag_values.length > 1
    if tag_values.length == 1 && peeled_values.empty?
      errors << "DELIVERY_TAG_NOT_ANNOTATED"
    elsif peeled_values.length > 1
      errors << "DELIVERY_TAG_PEEL_AMBIGUOUS"
    end
    allowed = [branch_ref, tag_ref, "#{tag_ref}^{}"]
    (values.keys - allowed).each { |ref| errors << "REMOTE_REF_UNEXPECTED ref=#{ref}" }
    [branch_values.first, tag_values.first, peeled_values.first]
  end

  def fetch_remote_objects(root, remote_name, branch_ref, tag_ref, errors)
    result = nil
    Dir.mktmpdir("phase01-delivery-object-store-") do |directory|
      _stdout, _stderr, init_status = Open3.capture3("git", "init", "--quiet", "--bare", directory)
      unless init_status.success?
        errors << "OBJECT_STORE_INIT_FAILED"
        next
      end
      _stdout, _stderr, status = bare_git(
        directory,
        "fetch", "--quiet", "--no-tags", "--no-write-fetch-head", remote_name,
        "+#{branch_ref}:refs/verify/branch",
        "+#{tag_ref}:refs/verify/tag"
      )
      unless status&.success?
        # A remote configured in the caller repository is not inherited by a new bare store.
        remote_stdout, _remote_stderr, remote_status = git(root, "config", "--get", "remote.#{remote_name}.url")
        remote_url = command_value(remote_stdout, remote_status, errors, "REMOTE_URL")
        if remote_url
          _stdout, _stderr, status = bare_git(
            directory,
            "fetch", "--quiet", "--no-tags", "--no-write-fetch-head", remote_url,
            "+#{branch_ref}:refs/verify/branch",
            "+#{tag_ref}:refs/verify/tag"
          )
        end
      end
      errors << "REMOTE_OBJECT_FETCH_FAILED" unless status&.success?
      next unless status&.success?

      result = yield directory
    end
    result
  end

  def parse_tag_object(bytes, expected_tag_name, errors)
    header, message = bytes.split("\n\n", 2)
    if message.nil?
      errors << "DELIVERY_TAG_OBJECT_MALFORMED"
      return [nil, nil]
    end
    headers = {}
    header.lines.each do |line|
      key, value = line.chomp.split(" ", 2)
      if headers.key?(key)
        errors << "DELIVERY_TAG_HEADER_DUPLICATE key=#{key}"
      else
        headers[key] = value
      end
    end
    errors << "DELIVERY_TAG_OBJECT_TYPE_INVALID" unless headers["type"] == "commit"
    errors << "DELIVERY_TAG_NAME_MISMATCH" unless headers["tag"] == expected_tag_name
    tagger = headers["tagger"].to_s.match(/\A(.+) <([^<>\s]+)> \d+ [+-]\d{4}\z/)
    errors << "DELIVERY_TAG_TAGGER_INVALID" unless tagger

    lines = message.lines.map(&:chomp)
    errors << "DELIVERY_TAG_SCHEMA_INVALID" unless lines.shift == TAG_SCHEMA
    payload = {}
    payload_order = []
    lines.reject(&:empty?).each do |line|
      key, value = line.split("=", 2)
      if key.to_s.empty? || value.to_s.empty? || payload.key?(key)
        errors << "DELIVERY_TAG_PAYLOAD_INVALID"
      else
        payload_order << key
        payload[key] = value
      end
    end
    exact_hash(payload, TAG_FIELDS, errors, "DELIVERY_TAG_PAYLOAD")
    errors << "DELIVERY_TAG_FIELD_ORDER_INVALID" unless payload_order == TAG_FIELDS
    [{ object: headers["object"], tagger_name: tagger&.[](1), tagger_email: tagger&.[](2) }, payload]
  end

  def target_blob(git_dir, commit, path, errors, label)
    unless canonical_relative_path?(path)
      errors << "#{label}_PATH_INVALID"
      return nil
    end
    stdout, _stderr, status = bare_git(git_dir, "cat-file", "blob", "#{commit}:#{path}")
    unless status&.success?
      errors << "#{label}_TARGET_MISSING path=#{path}"
      return nil
    end
    stdout
  end

  def target_mode(git_dir, commit, path, errors, label)
    stdout, _stderr, status = bare_git(git_dir, "ls-tree", "-z", commit, "--", path)
    unless status&.success?
      errors << "#{label}_TREE_LOOKUP_FAILED path=#{path}"
      return nil
    end
    rows = stdout.split("\0").reject(&:empty?)
    if rows.length != 1
      errors << "#{label}_TARGET_MISSING path=#{path}"
      return nil
    end
    match = rows.first.match(/\A(100[0-7]{3}) blob [0-9a-f]{40}\t(.+)\z/)
    unless match && match[2] == path
      errors << "#{label}_TREE_ENTRY_INVALID path=#{path}"
      return nil
    end
    match[1]
  end

  def illegal_subject_path?(path, context)
    basename = File.basename(path)
    evidence_prefix = ".planning/phases/#{context.phase_name}/EVIDENCE/"
    return true if path == "#{evidence_prefix}local-chrome-entry.json"
    return true if path.start_with?("#{evidence_prefix}browser-source")
    return true if basename == "tested-inputs.json" || basename == "evidence-manifest.json"
    return true if basename == "TODO.md" || basename.end_with?("SUMMARY.md")
    return true if basename.match?(/(?:CLAUDE-REVIEW|ENTRY-REVIEW|\d+-REVIEW|\d+-VERIFICATION)\.md\z/)
    return true if path.include?("/EVIDENCE/runs/") || basename.start_with?("OBL-")
    return true if path.include?("/EVIDENCE/delivery-attestation") || path.include?("/EVIDENCE/delivery-tag")

    false
  end

  def walk_ast(node, &block)
    return unless node.is_a?(Array)

    yield node
    node.each { |child| walk_ast(child, &block) if child.is_a?(Array) }
  end

  def literal_string(node)
    return UNSUPPORTED_LITERAL unless node.is_a?(Array) && node[0] == :string_literal

    content = node[1]
    return "" if content == [:string_content]
    return UNSUPPORTED_LITERAL unless content.is_a?(Array) && content[0] == :string_content

    parts = content.drop(1)
    return UNSUPPORTED_LITERAL unless parts.all? { |part| part.is_a?(Array) && part[0] == :@tstring_content }
    parts.map { |part| part[1] }.join
  end

  def literal_value(node, constants)
    return UNSUPPORTED_LITERAL unless node.is_a?(Array)

    case node[0]
    when :string_literal
      literal_string(node)
    when :@tstring_content
      node[1]
    when :@int
      Integer(node[1], exception: false) || UNSUPPORTED_LITERAL
    when :array
      values = Array(node[1]).map { |entry| literal_value(entry, constants) }
      values.include?(UNSUPPORTED_LITERAL) ? UNSUPPORTED_LITERAL : values
    when :hash
      list = node[1]
      return {} if list.nil?
      return UNSUPPORTED_LITERAL unless list.is_a?(Array) && list[0] == :assoclist_from_args

      result = {}
      Array(list[1]).each do |association|
        return UNSUPPORTED_LITERAL unless association.is_a?(Array) && association[0] == :assoc_new
        key = literal_value(association[1], constants)
        value = literal_value(association[2], constants)
        return UNSUPPORTED_LITERAL if key.equal?(UNSUPPORTED_LITERAL) || value.equal?(UNSUPPORTED_LITERAL) || !key.is_a?(String) || result.key?(key)
        result[key] = value
      end
      result
    when :var_ref
      token = node[1]
      return constants[token[1]] if token.is_a?(Array) && token[0] == :@const && constants.key?(token[1])
      return true if token == [:@kw, "true", token[2]]
      return false if token == [:@kw, "false", token[2]]
      return nil if token == [:@kw, "nil", token[2]]
      UNSUPPORTED_LITERAL
    when :call
      receiver, _period, method = node[1], node[2], node[3]
      if method.is_a?(Array) && method[0] == :@ident && method[1] == "freeze"
        literal_value(receiver, constants)
      else
        UNSUPPORTED_LITERAL
      end
    else
      UNSUPPORTED_LITERAL
    end
  end

  def code_owned_subject_entries(git_dir, commit, context, errors)
    registry_path = context.registry_path
    source = target_blob(git_dir, commit, registry_path, errors, "SUBJECT_REGISTRY")
    return [] unless source
    ast = Ripper.sexp(source)
    unless ast
      errors << "SUBJECT_REGISTRY_RUBY_INVALID"
      return []
    end

    assignments = []
    walk_ast(ast) do |node|
      next unless node[0] == :assign && node.dig(1, 0) == :var_field && node.dig(1, 1, 0) == :@const
      assignments << [node.dig(1, 1, 1), node[2]]
    end
    duplicate_constants = assignments.map(&:first).group_by(&:itself).select { |_name, matches| matches.length > 1 }.keys
    errors << "SUBJECT_REGISTRY_CONSTANT_DUPLICATE names=#{duplicate_constants.sort.join(',')}" unless duplicate_constants.empty?
    constants = {}
    pending = assignments.dup
    loop do
      before = pending.length
      pending = pending.reject do |name, expression|
        value = literal_value(expression, constants)
        next false if value.equal?(UNSUPPORTED_LITERAL)
        constants[name] = value
        true
      end
      break if pending.empty? || pending.length == before
    end
    checks = constants["CHECKS"]
    unless checks.is_a?(Array) && !checks.empty? && checks.all? { |entry| entry.is_a?(Hash) }
      errors << "SUBJECT_REGISTRY_CHECKS_NOT_LITERAL"
      return []
    end
    entries = checks.flat_map do |check|
      values = check["inputs"]
      unless values.is_a?(Array) && !values.empty?
        errors << "SUBJECT_REGISTRY_INPUTS_EMPTY check=#{check['id']}"
        next []
      end
      values.filter_map do |entry|
        unless entry.is_a?(Hash) && entry.keys.sort == %w[path role]
          errors << "SUBJECT_REGISTRY_ENTRY_INVALID check=#{check['id']}"
          next
        end
        { "path" => entry["path"], "role" => entry["role"] }
      end
    end
    by_path = {}
    entries.each do |entry|
      path = entry["path"]
      prior = by_path[path]
      if prior && prior["role"] != entry["role"]
        errors << "SUBJECT_REGISTRY_ROLE_CONFLICT path=#{path}"
      end
      by_path[path] ||= entry
    end
    by_path.values.sort_by { |entry| entry["path"] }
  end

  def validate_subject(git_dir, commit, subject_path, context, errors)
    bytes = target_blob(git_dir, commit, subject_path, errors, "SUBJECT_MANIFEST")
    return [nil, nil, nil] unless bytes

    subject = parse_json(bytes, errors, "SUBJECT_MANIFEST")
    return [nil, nil, nil] unless subject

    exact_hash(subject, SUBJECT_FIELDS, errors, "SUBJECT")
    errors << "SUBJECT_SCHEMA_UNSUPPORTED" unless subject["schema_version"] == context.subject_schema
    errors << "SUBJECT_PHASE_MISMATCH" unless subject["phase"] == context.phase_name
    inputs = subject["inputs"]
    unless inputs.is_a?(Array)
      errors << "SUBJECT_INPUTS_INVALID"
      return [subject, nil, nil]
    end
    paths = []
    inputs.each_with_index do |entry, index|
      exact_hash(entry, INPUT_FIELDS, errors, "SUBJECT_INPUT index=#{index}")
      next unless entry.is_a?(Hash)
      path = entry["path"]
      paths << path if path.is_a?(String)
      errors << "SUBJECT_INPUT_PATH_INVALID index=#{index}" unless canonical_relative_path?(path)
      errors << "SUBJECT_INPUT_ROLE_INVALID path=#{path}" unless ROLES.include?(entry["role"])
      errors << "SUBJECT_INPUT_MODE_INVALID path=#{path}" unless entry["mode"].is_a?(String) && entry["mode"].match?(/\A100[0-7]{3}\z/)
      errors << "SUBJECT_INPUT_SHA256_INVALID path=#{path}" unless entry["sha256"].is_a?(String) && entry["sha256"].match?(SHA256)
      errors << "SUBJECT_ILLEGAL_EXCLUSION path=#{path}" if path.is_a?(String) && illegal_subject_path?(path, context)
      next unless canonical_relative_path?(path)

      content = target_blob(git_dir, commit, path, errors, "SUBJECT_INPUT")
      mode = target_mode(git_dir, commit, path, errors, "SUBJECT_INPUT")
      if content && entry["sha256"] != Digest::SHA256.hexdigest(content)
        errors << "SUBJECT_INPUT_CONTENT_MISMATCH path=#{path}"
      end
      errors << "SUBJECT_INPUT_MODE_MISMATCH path=#{path}" if mode && entry["mode"] != mode
    end
    errors << "SUBJECT_INPUT_ORDER_INVALID" unless paths == paths.sort
    duplicate = paths.group_by(&:itself).select { |_path, matches| matches.length > 1 }.keys
    errors << "SUBJECT_INPUT_DUPLICATE paths=#{duplicate.sort.join(',')}" unless duplicate.empty?
    required_inputs = [context.registry_path]
    missing = required_inputs - paths
    errors << "SUBJECT_REQUIRED_INPUT_MISSING paths=#{missing.sort.join(',')}" unless missing.empty?
    expected = code_owned_subject_entries(git_dir, commit, context, errors)
    actual_pairs = inputs.filter_map do |entry|
      { "path" => entry["path"], "role" => entry["role"] } if entry.is_a?(Hash)
    end.sort_by { |entry| entry["path"].to_s }
    expected_paths = expected.map { |entry| entry["path"] }
    actual_paths = actual_pairs.map { |entry| entry["path"] }
    registry_missing = expected_paths - actual_paths
    registry_extra = actual_paths - expected_paths
    errors << "SUBJECT_INPUT_MISSING paths=#{registry_missing.sort.join(',')}" unless registry_missing.empty?
    errors << "SUBJECT_INPUT_EXTRA paths=#{registry_extra.sort.join(',')}" unless registry_extra.empty?
    expected_by_path = expected.to_h { |entry| [entry["path"], entry["role"]] }
    actual_pairs.each do |entry|
      next unless expected_by_path.key?(entry["path"])
      errors << "SUBJECT_INPUT_ROLE_MISMATCH path=#{entry['path']}" unless entry["role"] == expected_by_path[entry["path"]]
    end
    [subject, digest_value(subject), digest_value(inputs)]
  end

  def validate_binding_record(record, subject_path, subject_manifest_digest, tested_subject_digest, errors, label)
    unless record.is_a?(Hash)
      errors << "#{label}_TYPE_INVALID"
      return
    end
    errors << "#{label}_STATUS_NOT_PASS" unless record["status"] == "PASS"
    errors << "#{label}_SUBJECT_PATH_MISMATCH" unless record["subject_manifest_path"] == subject_path
    errors << "#{label}_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless record["subject_manifest_digest"] == subject_manifest_digest
    errors << "#{label}_TESTED_SUBJECT_DIGEST_MISMATCH" unless record["tested_subject_digest"] == tested_subject_digest
  end

  def validate_evidence(git_dir, commit, evidence_path, local_bytes, subject_path, subject_manifest_digest, tested_subject_digest, context, errors)
    bytes = target_blob(git_dir, commit, evidence_path, errors, "EVIDENCE_MANIFEST")
    return nil unless bytes

    errors << "LOCAL_EVIDENCE_MANIFEST_MISMATCH" unless local_bytes == bytes
    manifest = parse_json(bytes, errors, "EVIDENCE_MANIFEST")
    return nil unless manifest

    schema = manifest["schema_version"]
    fields = schema == OBLIGATION_EVIDENCE_SCHEMA ? OBLIGATION_EVIDENCE_FIELDS : EVIDENCE_FIELDS
    exact_hash(manifest, fields, errors, "EVIDENCE_MANIFEST")
    errors << "EVIDENCE_SCHEMA_UNSUPPORTED" unless [context.evidence_schema, OBLIGATION_EVIDENCE_SCHEMA].include?(schema)
    errors << "EVIDENCE_PHASE_MISMATCH" unless manifest["phase"] == context.phase_name
    errors << "EVIDENCE_OWNER_MISMATCH" unless manifest["owner"] == context.package
    errors << "EVIDENCE_SUBJECT_PATH_MISMATCH" unless manifest["subject_manifest_path"] == subject_path
    errors << "EVIDENCE_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless manifest["subject_manifest_digest"] == subject_manifest_digest
    errors << "EVIDENCE_TESTED_SUBJECT_DIGEST_MISMATCH" unless manifest["tested_subject_digest"] == tested_subject_digest

    if schema == OBLIGATION_EVIDENCE_SCHEMA
      validate_obligation_evidence_entries(
        git_dir, commit, manifest, subject_path, subject_manifest_digest, tested_subject_digest, errors
      )
    elsif schema == context.evidence_schema
      validate_legacy_evidence_entries(
        git_dir, commit, manifest, subject_path, subject_manifest_digest, tested_subject_digest, errors
      )
    end
    Digest::SHA256.hexdigest(bytes)
  end

  def validate_legacy_evidence_entries(git_dir, commit, manifest, subject_path, subject_manifest_digest, tested_subject_digest, errors)
    entries = manifest["entries"]
    if !entries.is_a?(Array) || entries.empty?
      errors << "EVIDENCE_ENTRIES_EMPTY"
    else
      check_ids = []
      entries.each_with_index do |entry, index|
        exact_hash(entry, EVIDENCE_ENTRY_FIELDS, errors, "EVIDENCE_ENTRY index=#{index}")
        next unless entry.is_a?(Hash)
        check_ids << entry["check_id"]
        errors << "EVIDENCE_ENTRY_STATUS_NOT_PASS index=#{index}" unless entry["status"] == "PASS"
        validate_evidence_record(
          git_dir, commit, entry, subject_path, subject_manifest_digest, tested_subject_digest, errors,
          "EVIDENCE_ENTRY index=#{index}"
        )
      end
      errors << "EVIDENCE_ENTRY_DUPLICATE" unless check_ids.compact.uniq.length == check_ids.compact.length
    end

    aggregate = manifest["aggregate"]
    exact_hash(aggregate, EVIDENCE_AGGREGATE_FIELDS, errors, "EVIDENCE_AGGREGATE")
    return unless aggregate.is_a?(Hash)

    errors << "EVIDENCE_AGGREGATE_STATUS_NOT_PASS" unless aggregate["status"] == "PASS"
    validate_evidence_record(
      git_dir, commit, aggregate, subject_path, subject_manifest_digest, tested_subject_digest, errors,
      "EVIDENCE_AGGREGATE"
    )
  end

  def validate_obligation_evidence_entries(git_dir, commit, manifest, subject_path, subject_manifest_digest, tested_subject_digest, errors)
    entries = manifest["entries"]
    unless entries.is_a?(Array)
      errors << "OBLIGATION_EVIDENCE_ENTRIES_INVALID"
      return
    end
    ids = entries.filter_map { |entry| entry["obligation_id"] if entry.is_a?(Hash) }
    errors << "OBLIGATION_EVIDENCE_ENTRY_SET_INVALID" unless ids == EXPECTED_OBLIGATION_IDS
    errors << "OBLIGATION_EVIDENCE_ENTRY_DUPLICATE" unless ids.uniq.length == ids.length
    browser_runtime = nil
    entries.each_with_index do |entry, index|
      exact_hash(entry, OBLIGATION_EVIDENCE_ENTRY_FIELDS, errors, "OBLIGATION_EVIDENCE_ENTRY index=#{index}")
      next unless entry.is_a?(Hash)

      obligation_id = EXPECTED_OBLIGATION_IDS[index]
      expected_path = ".planning/phases/#{PHASE_NAME}/EVIDENCE/#{obligation_id}.json"
      errors << "OBLIGATION_EVIDENCE_ENTRY_ID_MISMATCH index=#{index}" unless entry["obligation_id"] == obligation_id
      errors << "OBLIGATION_EVIDENCE_ENTRY_PATH_MISMATCH index=#{index}" unless entry["path"] == expected_path
      errors << "OBLIGATION_EVIDENCE_ENTRY_STATUS_NOT_PASS index=#{index}" unless entry["status"] == "PASS"
      record = validate_evidence_record(
        git_dir, commit, entry, subject_path, subject_manifest_digest, tested_subject_digest, errors,
        "OBLIGATION_EVIDENCE_ENTRY index=#{index}"
      )
      next unless record

      errors << "OBLIGATION_EVIDENCE_SUMMARY_ID_MISMATCH index=#{index}" unless record["obligation_id"] == obligation_id
      errors << "OBLIGATION_EVIDENCE_SUMMARY_STATUS_NOT_PASS index=#{index}" unless record["status"] == "PASS"
      if obligation_id == "OBL-NFR-BROWSER"
        browser_runtime = record["runtime"]
        unless browser_runtime.is_a?(Hash)
          errors << "OBLIGATION_BROWSER_RUNTIME_MISSING"
          next
        end
        errors << "OBLIGATION_BROWSER_RUNTIME_SUBJECT_PATH_MISMATCH" unless browser_runtime["subject_manifest_path"] == subject_path
        errors << "OBLIGATION_BROWSER_RUNTIME_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless browser_runtime["subject_manifest_digest"] == subject_manifest_digest
        errors << "OBLIGATION_BROWSER_RUNTIME_TESTED_SUBJECT_DIGEST_MISMATCH" unless browser_runtime["tested_subject_digest"] == tested_subject_digest
      end
    end

    validate_target_artifact(git_dir, commit, manifest["runtime_artifact"], RUNTIME_ARTIFACT_FIELDS, errors, "OBLIGATION_RUNTIME_ARTIFACT")
    if browser_runtime.is_a?(Hash) && manifest["runtime_artifact"].is_a?(Hash)
      errors << "OBLIGATION_BROWSER_RUNTIME_ARTIFACT_MISMATCH" unless browser_runtime["path"] == manifest["runtime_artifact"]["path"] && browser_runtime["sha256"] == manifest["runtime_artifact"]["sha256"]
    end
    locators = manifest["ci_locators"]
    unless locators.is_a?(Array) && !locators.empty?
      errors << "OBLIGATION_CI_LOCATORS_INVALID"
      return
    end
    locator_paths = []
    locators.each_with_index do |locator, index|
      exact_hash(locator, CI_LOCATOR_FIELDS, errors, "OBLIGATION_CI_LOCATOR index=#{index}")
      locator_paths << locator["path"] if locator.is_a?(Hash)
      validate_target_artifact(git_dir, commit, locator, CI_LOCATOR_FIELDS, errors, "OBLIGATION_CI_LOCATOR index=#{index}")
    end
    errors << "OBLIGATION_CI_LOCATOR_DUPLICATE" unless locator_paths.compact.uniq.length == locator_paths.compact.length
    errors << "OBLIGATION_CI_LOCATOR_SET_INVALID" unless locator_paths == EXPECTED_CI_LOCATORS
  end

  def validate_evidence_record(git_dir, commit, entry, subject_path, subject_manifest_digest, tested_subject_digest, errors, label)
    bytes = target_blob(git_dir, commit, entry["path"], errors, label)
    return nil unless bytes

    errors << "#{label}_CHECKSUM_MISMATCH" unless entry["sha256"] == Digest::SHA256.hexdigest(bytes)
    record = parse_json(bytes, errors, label)
    validate_binding_record(record, subject_path, subject_manifest_digest, tested_subject_digest, errors, label) if record
    record
  end

  def validate_target_artifact(git_dir, commit, record, fields, errors, label)
    exact_hash(record, fields, errors, label)
    return unless record.is_a?(Hash)

    bytes = target_blob(git_dir, commit, record["path"], errors, label)
    return unless bytes

    errors << "#{label}_CHECKSUM_MISMATCH" unless record["sha256"] == Digest::SHA256.hexdigest(bytes)
    errors << "#{label}_SIZE_MISMATCH" if record.key?("size") && record["size"] != bytes.bytesize
  end

  def table_cells(line)
    stripped = line.strip
    return nil unless stripped.start_with?("|") && stripped.end_with?("|")

    stripped.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
  end

  def validate_review(git_dir, commit, path, subject_path, subject_manifest_digest, tested_subject_digest, errors)
    bytes = target_blob(git_dir, commit, path, errors, "REVIEW")
    return unless bytes
    lines = bytes.lines
    header = lines.index { |line| table_cells(line)&.map(&:downcase) == REVIEW_HEADERS.map(&:downcase) }
    if header.nil?
      errors << "REVIEW_HEADER_INVALID path=#{path}"
      return
    end
    rows = lines.drop(header + 1).filter_map do |line|
      cells = table_cells(line)
      next if cells.nil? || (cells.length == REVIEW_HEADERS.length && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) })
      break if !line.lstrip.start_with?("|")
      cells
    end
    if rows.empty? || rows.last.length != REVIEW_HEADERS.length
      errors << "REVIEW_ROWS_INVALID path=#{path}"
      return
    end
    attempt, blocker, high, escalated, record_subject_path, record_subject_digest, record_tested_digest, result = rows.last
    errors << "REVIEW_ATTEMPT_INVALID path=#{path}" unless Integer(attempt, exception: false)&.between?(1, 3)
    errors << "REVIEW_BLOCKING_FINDINGS path=#{path}" unless blocker == "0" && high == "0"
    errors << "REVIEW_ESCALATED path=#{path}" unless escalated == "no"
    errors << "REVIEW_RESULT_NOT_PASS path=#{path}" unless result == "PASS"
    errors << "REVIEW_SUBJECT_PATH_MISMATCH path=#{path}" unless record_subject_path == subject_path
    errors << "REVIEW_SUBJECT_MANIFEST_DIGEST_MISMATCH path=#{path}" unless record_subject_digest == subject_manifest_digest
    errors << "REVIEW_TESTED_SUBJECT_DIGEST_MISMATCH path=#{path}" unless record_tested_digest == tested_subject_digest
    errors << "REVIEW_FINAL_VERDICT_NOT_PASS path=#{path}" unless bytes.match?(/^## Final verdict\s*\n+PASS\s*$/)
  end

  def parse_local_pr_state(root, relative, errors)
    bytes = read_local(root, relative, errors, "FIXTURE_PR_STATE")
    return nil unless bytes
    state = parse_json(bytes, errors, "FIXTURE_PR_STATE")
    expected = %w[
      schema_version pr_locator head_sha state check_name check_locator check_status
      check_conclusion external_actor
    ]
    exact_hash(state, expected, errors, "FIXTURE_PR_STATE") if state
    errors << "FIXTURE_PR_STATE_SCHEMA_INVALID" if state && state["schema_version"] != LOCAL_PR_SCHEMA
    state
  end

  def gh_json(*argv, errors:, label:)
    stdout, _stderr, status = Open3.capture3("gh", "api", "--method", "GET", *argv)
    unless status.success?
      errors << "#{label}_API_FAILED"
      return nil
    end
    parse_json(stdout, errors, label)
  rescue Errno::ENOENT
    errors << "#{label}_CLI_MISSING"
    nil
  end

  def github_external_state(remote_url, pr_locator, check_name, commit, errors)
    repository = github_repository(remote_url)
    unless repository
      errors << "REMOTE_URL_NOT_ALLOWED"
      return nil
    end
    owner, repo = repository
    match = pr_locator.match(%r{\Ahttps://github\.com/#{Regexp.escape(owner)}/#{Regexp.escape(repo)}/pull/(\d+)\z})
    unless match
      errors << "PR_LOCATOR_INVALID"
      return nil
    end
    pull = gh_json("repos/#{owner}/#{repo}/pulls/#{match[1]}", errors: errors, label: "PR")
    checks = gh_json("repos/#{owner}/#{repo}/commits/#{commit}/check-runs?per_page=100", errors: errors, label: "PR_CHECK")
    return nil unless pull && checks

    matches = Array(checks["check_runs"]).select { |check| check.is_a?(Hash) && check["name"] == check_name }
    if matches.length != 1
      errors << "PR_CHECK_MATCH_COUNT_INVALID count=#{matches.length}"
      return nil
    end
    check = matches.first
    {
      "pr_locator" => pr_locator,
      "head_sha" => pull.dig("head", "sha"),
      "state" => pull["merged"] ? "MERGED" : pull["state"].to_s.upcase,
      "check_name" => check["name"],
      "check_locator" => check["details_url"],
      "check_status" => check["status"].to_s.upcase,
      "check_conclusion" => check["conclusion"].to_s.upcase,
      "external_actor" => "github-app:#{check.dig('app', 'slug')}"
    }
  end

  def validate_external_state(state, summary, payload, commit, errors)
    return unless state.is_a?(Hash)

    errors << "PR_LOCATOR_MISMATCH" unless state["pr_locator"] == summary[:pr_locator]
    errors << "PR_HEAD_MISMATCH" unless state["head_sha"] == commit
    errors << "PR_STATE_INVALID" unless %w[OPEN MERGED].include?(state["state"])
    errors << "PR_CHECK_NAME_MISMATCH" unless state["check_name"] == summary[:check_name]
    errors << "PR_CHECK_NOT_PASS" unless state["check_status"] == "COMPLETED" && state["check_conclusion"] == "SUCCESS"
    errors << "TAG_PR_LOCATOR_MISMATCH" unless payload["pr_locator"] == state["pr_locator"]
    errors << "TAG_CHECK_NAME_MISMATCH" unless payload["check_name"] == state["check_name"]
    errors << "TAG_CHECK_LOCATOR_MISMATCH" unless payload["check_locator"] == state["check_locator"]
    errors << "TAG_EXTERNAL_ACTOR_MISMATCH" unless payload["external_actor"] == state["external_actor"]
  end

  def validate(options)
    errors = []
    root = File.expand_path(options.fetch(:root))
    context = context_for(options, errors)
    return errors unless context
    summary_bytes = read_local(root, options.fetch(:summary), errors, "SUMMARY")
    local_evidence = read_local(root, options.fetch(:evidence_manifest), errors, "LOCAL_EVIDENCE_MANIFEST")
    summary = parse_summary(summary_bytes, errors)
    return errors unless errors.empty?

    remote_name = summary[:remote_name]
    errors << "REMOTE_NAME_INVALID" unless remote_name.match?(REMOTE_NAME)
    errors << "REMOTE_BRANCH_REF_INVALID" unless safe_ref?(summary[:branch_ref], "refs/heads/")
    errors << "DELIVERY_TAG_REF_INVALID" unless summary[:tag_ref] == context.tag_ref
    errors << "REMOTE_URL_CREDENTIALS_FORBIDDEN" if remote_url_contains_credentials?(summary[:remote_url])
    return errors unless errors.empty?

    configured_stdout, _configured_stderr, configured_status = git(root, "config", "--get", "remote.#{remote_name}.url")
    configured_url = command_value(configured_stdout, configured_status, errors, "CONFIGURED_REMOTE_URL")
    errors << "REMOTE_URL_MISMATCH" if configured_url && configured_url != summary[:remote_url]
    is_local = configured_url && local_remote?(configured_url)
    errors << "LOCAL_FIXTURE_NOT_ALLOWED" if is_local && !options[:allow_local_fixture]
    errors << "LOCAL_FIXTURE_OPTIONS_FORBIDDEN" if !is_local && (options[:allow_local_fixture] || options[:fixture_pr_state])
    errors << "REMOTE_URL_NOT_ALLOWED" if configured_url && !is_local && github_repository(configured_url).nil?
    return errors unless errors.empty?

    refs_stdout, _refs_stderr, refs_status = git(
      root,
      "ls-remote", remote_name,
      summary[:branch_ref], summary[:tag_ref], "#{summary[:tag_ref]}^{}"
    )
    unless refs_status&.success?
      errors << "REMOTE_REF_QUERY_FAILED"
      return errors
    end
    branch_sha, _tag_sha, peeled_sha = parse_remote_refs(refs_stdout, summary[:branch_ref], summary[:tag_ref], errors)
    return errors if branch_sha.nil? || peeled_sha.nil?
    errors << "DELIVERY_TAG_TARGET_MISMATCH" unless branch_sha == peeled_sha
    return errors unless errors.empty?

    fetch_remote_objects(root, remote_name, summary[:branch_ref], summary[:tag_ref], errors) do |git_dir|
      type_stdout, _type_stderr, type_status = bare_git(git_dir, "cat-file", "-t", "refs/verify/tag")
      errors << "DELIVERY_TAG_NOT_ANNOTATED" unless type_status&.success? && type_stdout.strip == "tag"
      next unless errors.empty?

      branch_target_stdout, _branch_stderr, branch_status = bare_git(git_dir, "rev-parse", "refs/verify/branch^{commit}")
      tag_target_stdout, _tag_stderr, tag_status = bare_git(git_dir, "rev-parse", "refs/verify/tag^{}")
      commit = command_value(tag_target_stdout, tag_status, errors, "TAG_TARGET")
      branch_target = command_value(branch_target_stdout, branch_status, errors, "BRANCH_TARGET")
      errors << "DELIVERY_TAG_TARGET_MISMATCH" if commit && branch_target && commit != branch_target
      next unless errors.empty?

      tree_stdout, _tree_stderr, tree_status = bare_git(git_dir, "rev-parse", "#{commit}^{tree}")
      tree = command_value(tree_stdout, tree_status, errors, "TAG_TARGET_TREE")
      tag_stdout, _tag_stderr, tag_object_status = bare_git(git_dir, "cat-file", "tag", "refs/verify/tag")
      unless tag_object_status&.success?
        errors << "DELIVERY_TAG_OBJECT_READ_FAILED"
        next
      end
      tag_header, payload = parse_tag_object(tag_stdout, context.tag_ref.delete_prefix("refs/tags/"), errors)
      next unless tag_header && payload

      errors << "TAG_OBJECT_TARGET_MISMATCH" unless tag_header[:object] == commit
      errors << "TAG_PHASE_MISMATCH" unless payload["phase"] == context.phase
      errors << "TAG_PACKAGE_MISMATCH" unless payload["package"] == context.package
      errors << "TAG_BRANCH_MISMATCH" unless payload["branch"] == summary[:branch_ref]
      errors << "TAG_COMMIT_MISMATCH" unless payload["commit"] == commit
      errors << "TAG_TREE_MISMATCH" unless payload["tree"] == tree
      errors << "TAG_STATUS_NOT_PASS" unless payload["status"] == "PASS"
      errors << "TAG_ATTESTOR_NAME_MISMATCH" unless payload["attestor_name"] == tag_header[:tagger_name]
      errors << "TAG_ATTESTOR_EMAIL_MISMATCH" unless payload["attestor_email"] == tag_header[:tagger_email]
      expected_subject_path = ".planning/phases/#{context.phase_name}/EVIDENCE/tested-inputs.json"
      expected_evidence_path = ".planning/phases/#{context.phase_name}/EVIDENCE/evidence-manifest.json"
      errors << "TAG_SUBJECT_PATH_INVALID" unless canonical_relative_path?(payload["subject_manifest_path"])
      errors << "TAG_SUBJECT_PATH_MISMATCH" unless payload["subject_manifest_path"] == expected_subject_path
      errors << "TAG_EVIDENCE_PATH_INVALID" unless canonical_relative_path?(payload["evidence_manifest_path"])
      errors << "TAG_EVIDENCE_PATH_MISMATCH" unless payload["evidence_manifest_path"] == expected_evidence_path && payload["evidence_manifest_path"] == options[:evidence_manifest]
      next unless errors.empty?

      _subject, subject_manifest_digest, tested_subject_digest = validate_subject(
        git_dir, commit, payload["subject_manifest_path"], context, errors
      )
      errors << "TAG_SUBJECT_MANIFEST_DIGEST_MISMATCH" unless payload["subject_manifest_digest"] == subject_manifest_digest
      errors << "TAG_TESTED_SUBJECT_DIGEST_MISMATCH" unless payload["tested_subject_digest"] == tested_subject_digest
      evidence_digest = validate_evidence(
        git_dir,
        commit,
        payload["evidence_manifest_path"],
        local_evidence,
        payload["subject_manifest_path"],
        subject_manifest_digest,
        tested_subject_digest,
        context,
        errors
      )
      errors << "TAG_EVIDENCE_MANIFEST_DIGEST_MISMATCH" unless payload["evidence_manifest_digest"] == evidence_digest

      %W[
        .planning/phases/#{context.phase_name}/#{context.phase}-VERIFICATION.md
        .planning/phases/#{context.phase_name}/#{context.phase}-REVIEW.md
        .planning/phases/#{context.phase_name}/CLAUDE-REVIEW.md
      ].each do |path|
        validate_review(git_dir, commit, path, payload["subject_manifest_path"], subject_manifest_digest, tested_subject_digest, errors)
      end

      state = if is_local
        if options[:fixture_pr_state].nil?
          errors << "FIXTURE_PR_STATE_REQUIRED"
          nil
        else
          parse_local_pr_state(root, options[:fixture_pr_state], errors)
        end
      else
        github_external_state(configured_url, summary[:pr_locator], summary[:check_name], commit, errors)
      end
      validate_external_state(state, summary, payload, commit, errors)
    end
    errors.uniq
  end
end

options = {
  root: Dir.pwd,
  require_pr_check_pass: false,
  allow_local_fixture: false
}
parser = OptionParser.new do |opts|
  opts.banner = "Usage: ruby .planning/tools/validate-delivery-attestation.rb --phase 01 --summary PATH --evidence-manifest PATH --require-pr-check-pass"
  opts.on("--root PATH") { |value| options[:root] = value }
  opts.on("--phase NN") { |value| options[:phase] = value }
  opts.on("--summary PATH") { |value| options[:summary] = value }
  opts.on("--evidence-manifest PATH") { |value| options[:evidence_manifest] = value }
  opts.on("--require-pr-check-pass") { options[:require_pr_check_pass] = true }
  opts.on("--allow-local-fixture") { options[:allow_local_fixture] = true }
  opts.on("--fixture-pr-state PATH") { |value| options[:fixture_pr_state] = value }
end

begin
  parser.parse!
rescue OptionParser::ParseError => error
  warn "OPTION_ERROR: #{error.message}"
  exit 2
end

errors = []
errors << "OPTION_PHASE_INVALID" unless options[:phase]&.match?(/\A\d{2}\z/) && options[:phase] != "00"
errors << "OPTION_SUMMARY_REQUIRED" if options[:summary].to_s.empty?
errors << "OPTION_EVIDENCE_MANIFEST_REQUIRED" if options[:evidence_manifest].to_s.empty?
errors << "OPTION_REQUIRE_PR_CHECK_PASS_REQUIRED" unless options[:require_pr_check_pass]
errors << "OPTION_ROOT_INVALID" unless File.directory?(File.expand_path(options[:root]))
if errors.empty?
  errors.concat(Phase01DeliveryAttestation.validate(options))
end

if errors.empty?
  puts "DELIVERY_ATTESTATION PASS phase=#{options[:phase]} tag=refs/tags/ycsopen-sms/phase-#{options[:phase]}/delivery"
  exit 0
end

warn "DELIVERY_ATTESTATION BLOCKED errors=#{errors.uniq.length}"
errors.uniq.each { |error| warn "- #{error}" }
exit 1
