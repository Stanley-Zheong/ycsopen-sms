#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "cgi"
require "digest"
require "open3"
require "optparse"
require "pathname"
require "rbconfig"
require "set"
require "shellwords"

module Phase01Lifecycle
  STAGES = %w[entry pre-push-exit post-push-delivery effective-todo-empty].freeze
  ENTRY_REVIEW_HEADERS = ["Criterion ID", "Verdict", "Evidence", "Command or inspection rule"].freeze
  REVIEW_HEADERS = [
    "Attempt", "BLOCKER", "HIGH", "Escalated", "Subject manifest path",
    "Subject manifest digest", "Tested subject digest", "Result"
  ].freeze
  REQUIRED_ARTIFACTS = %w[
    INTENT.md
    DESIGN.md
    ITERATIONS.md
    DECISIONS.md
    TODO.md
    TEST-MATRIX.md
    ENTRY-REVIEW.md
    CLAUDE-REVIEW.md
  ].freeze
  OBLIGATION_ID = /\bOBL-[A-Z0-9-]+\b/
  SHA256 = /\A[0-9a-f]{64}\z/
  LEGACY_EVIDENCE_SCHEMA = "phase01-evidence-manifest-v1"
  OBLIGATION_EVIDENCE_SCHEMA = "phase01-obligation-evidence-manifest-v1"
  PHASE03_EVIDENCE_SCHEMA = "phase03-evidence-manifest-v1"

  TodoRow = Struct.new(:line, :checked, :text, :obligation_ids, :reserved_delivery, keyword_init: true)
  TableRow = Struct.new(:line, :cells, keyword_init: true)

  module_function

  def contained_path(root, value, errors, label)
    expanded_root = File.expand_path(root)
    expanded = File.expand_path(value, expanded_root)
    unless expanded == expanded_root || expanded.start_with?(expanded_root + File::SEPARATOR)
      errors << "#{label}_OUTSIDE_ROOT path=#{value}"
      return nil
    end
    expanded
  end

  def repository_relative(root, path)
    Pathname(File.expand_path(path)).relative_path_from(Pathname(File.expand_path(root))).to_s
  end

  def read(path, errors, label)
    File.read(path)
  rescue Errno::ENOENT
    errors << "#{label}_MISSING path=#{path}"
    ""
  rescue Errno::EACCES
    errors << "#{label}_UNREADABLE path=#{path}"
    ""
  end

  def table_cells(line)
    stripped = line.strip
    return nil unless stripped.start_with?("|") && stripped.end_with?("|")

    stripped.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
  end

  def table_rows(path, headers, errors, label)
    lines = read(path, errors, label).lines
    header_index = lines.index { |line| table_cells(line)&.map(&:downcase) == headers.map(&:downcase) }
    if header_index.nil?
      errors << "#{label}_HEADER_INVALID path=#{path} expected=#{headers.join(' | ')}"
      return []
    end

    rows = []
    lines.drop(header_index + 1).each_with_index do |line, offset|
      break unless line.lstrip.start_with?("|") && line.rstrip.end_with?("|")

      cells = table_cells(line)
      next if cells.length == headers.length && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) }
      if cells.length != headers.length
        errors << "#{label}_COLUMN_COUNT path=#{path} line=#{header_index + offset + 2} expected=#{headers.length} actual=#{cells.length}"
        next
      end
      rows << TableRow.new(line: header_index + offset + 2, cells: cells)
    end
    errors << "#{label}_EMPTY path=#{path}" if rows.empty?
    rows
  end

  def parse_catalog(path, owner, errors)
    owned = {}
    read(path, errors, "LIFECYCLE_CATALOG").lines.each_with_index do |line, index|
      next unless line.start_with?("- OBL-")

      fields = line.chomp.split(" | ", -1)
      if fields.length != 9
        errors << "LIFECYCLE_CATALOG_BAD_FIELDS path=#{path} line=#{index + 1} expected=9 actual=#{fields.length}"
        next
      end
      id = fields[0].delete_prefix("- ")
      next unless fields[3] == owner

      owned[id] = { line: index + 1, evidence: fields[7] }
    end
    errors << "LIFECYCLE_OWNER_SET_EMPTY owner=#{owner}" if owned.empty?
    owned
  end

  def required_artifacts(phase)
    ["#{phase}-SPEC.md", "#{phase}-CONTEXT.md", *REQUIRED_ARTIFACTS]
  end

  def validate_artifacts(phase, phase_dir, errors)
    required_artifacts(phase).each do |name|
      path = File.join(phase_dir, name)
      if !File.file?(path)
        errors << "LIFECYCLE_ARTIFACT_MISSING path=#{path}"
      elsif File.zero?(path)
        errors << "LIFECYCLE_ARTIFACT_EMPTY path=#{path}"
      end
    end
    evidence_dir = File.join(phase_dir, "EVIDENCE")
    errors << "LIFECYCLE_EVIDENCE_DIRECTORY_MISSING path=#{evidence_dir}" unless File.directory?(evidence_dir)
    design = File.join(phase_dir, "DESIGN.md")
    claims = File.join(phase_dir, "SCHEMA-CLAIMS.md")
    if File.file?(design)
      declaration = File.read(design)[/^Schema migrations:\s*(none|declared)\s*$/i, 1]&.downcase
      errors << "LIFECYCLE_SCHEMA_DECLARATION_INVALID path=#{design} expected=none-or-declared" unless declaration
      if declaration == "none" && File.exist?(claims)
        errors << "LIFECYCLE_SCHEMA_CLAIMS_FORBIDDEN path=#{claims}"
      elsif declaration == "declared" && (!File.file?(claims) || File.zero?(claims))
        errors << "LIFECYCLE_SCHEMA_CLAIMS_REQUIRED path=#{claims}"
      end
    end
  end

  def validate_verify_command(root, path, task_index, verify_body, errors)
    command = verify_body[/<automated>\s*(.*?)\s*<\/automated>/m, 1] || verify_body.gsub(/<[^>]+>/, " ")
    command = CGI.unescapeHTML(command).strip
    placeholder_command = command.match?(/\A(?:TODO|TBD|placeholder|not recorded)\z/i) ||
      command.match?(/\b(?:TBD|placeholder|not recorded)\b/i)
    if command.empty? || placeholder_command
      errors << "LIFECYCLE_PLAN_VERIFY_NOT_RUNNABLE path=#{path} task=#{task_index} command=#{command.inspect}"
      return
    end
    command.split(/\s*(?:&&|\|\|)\s*/).each do |segment|
      begin
        argv = Shellwords.split(segment)
      rescue ArgumentError
        errors << "LIFECYCLE_PLAN_VERIFY_NOT_RUNNABLE path=#{path} task=#{task_index} command=#{command.inspect}"
        next
      end
      executable = argv.first
      next unless executable&.start_with?("/", "./")

      resolved = executable.start_with?("/") ? executable : File.expand_path(executable, root)
      unless File.file?(resolved) && File.executable?(resolved)
        errors << "LIFECYCLE_PLAN_VERIFY_NOT_RUNNABLE path=#{path} task=#{task_index} executable=#{executable}"
      end
    end
  end

  def validate_plans(root, phase, phase_dir, errors)
    paths = Dir.glob(File.join(phase_dir, "#{phase}-*-PLAN.md")).sort
    errors << "LIFECYCLE_PLAN_MISSING phase_dir=#{phase_dir}" if paths.empty?
    paths.each do |path|
      body = read(path, errors, "LIFECYCLE_PLAN")
      tasks = body.to_enum(:scan, /<task\b[^>]*>(.*?)<\/task>/m).map { Regexp.last_match(1) }
      errors << "LIFECYCLE_PLAN_TASK_MISSING path=#{path}" if tasks.empty?
      tasks.each_with_index do |task, index|
        %w[files action verify done].each do |field|
          match = task.match(/<#{field}>\s*(.*?)\s*<\/#{field}>/m)
          if match.nil? || match[1].strip.empty?
            errors << "LIFECYCLE_PLAN_TASK_FIELD_MISSING path=#{path} task=#{index + 1} field=#{field}"
          end
        end
        verify = task[/<verify>\s*(.*?)\s*<\/verify>/m, 1]
        validate_verify_command(root, path, index + 1, verify, errors) if verify && !verify.strip.empty?
      end
    end
  end

  def validate_entry_review(path, errors)
    rows = table_rows(path, ENTRY_REVIEW_HEADERS, errors, "LIFECYCLE_ENTRY_REVIEW")
    ids = rows.map { |row| row.cells[0] }
    ids.group_by(&:itself).each do |id, matches|
      errors << "LIFECYCLE_ENTRY_REVIEW_DUPLICATE_CRITERION id=#{id}" if !id.empty? && matches.length > 1
    end
    rows.each do |row|
      criterion, verdict, evidence, command = row.cells
      errors << "LIFECYCLE_ENTRY_REVIEW_CRITERION_MISSING line=#{row.line}" if criterion.empty?
      errors << "LIFECYCLE_ENTRY_REVIEW_VERDICT_INVALID line=#{row.line} criterion=#{criterion} verdict=#{verdict}" unless %w[PASS BLOCKER].include?(verdict)
      errors << "LIFECYCLE_ENTRY_REVIEW_BLOCKER line=#{row.line} criterion=#{criterion}" if verdict == "BLOCKER"
      errors << "LIFECYCLE_ENTRY_REVIEW_EVIDENCE_MISSING line=#{row.line} criterion=#{criterion}" if evidence.empty?
      errors << "LIFECYCLE_ENTRY_REVIEW_COMMAND_MISSING line=#{row.line} criterion=#{criterion}" if command.empty?
    end
    body = File.file?(path) ? File.read(path) : ""
    errors << "LIFECYCLE_ENTRY_REVIEW_FINAL_VERDICT_NOT_PASS path=#{path}" unless body.match?(/^## Verdict\s*\n+PASS\s*$/)
  end

  def reserved_delivery_text?(text, phase)
    numbered_phase = Integer(phase, exception: false)&.to_s
    return true if numbered_phase && text.match?(/One atomic Phase #{Regexp.escape(numbered_phase)} commit is visible on the configured GitHub remote/i)

    text.match?(/Annotated delivery tag, required remote check and live delivery attestation pass/i)
  end

  def parse_todo(path, phase, errors)
    body = read(path, errors, "LIFECYCLE_TODO")
    rows = body.lines.each_with_index.filter_map do |line, index|
      match = line.match(/^\s*- \[([ xX])\]\s*(.*?)\s*$/)
      next unless match

      text = match[2]
      TodoRow.new(
        line: index + 1,
        checked: match[1].downcase == "x",
        text: text,
        obligation_ids: text.scan(OBLIGATION_ID).uniq,
        reserved_delivery: reserved_delivery_text?(text, phase)
      )
    end
    errors << "LIFECYCLE_TODO_EMPTY path=#{path}" if rows.empty?
    rows
  end

  def validate_entry_todo(path, rows, owned_ids, errors)
    owned_rows = rows.select { |row| !(row.obligation_ids & owned_ids.to_a).empty? }
    checked = owned_rows.select(&:checked)
    unless checked.empty?
      errors << "LIFECYCLE_ENTRY_TODO_PRECHECKED path=#{path} lines=#{checked.map(&:line).join(',')} ids=#{checked.flat_map(&:obligation_ids).uniq.sort.join(',')}"
    end
    actual_ids = owned_rows.flat_map(&:obligation_ids)
    duplicate = actual_ids.group_by(&:itself).select { |_id, values| values.length > 1 }.keys
    missing = owned_ids - actual_ids.to_set
    errors << "LIFECYCLE_TODO_OWNED_MISSING path=#{path} ids=#{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "LIFECYCLE_TODO_OWNED_DUPLICATE path=#{path} ids=#{duplicate.sort.join(',')}" unless duplicate.empty?
  end

  def evidence_reference(row)
    row.text[/Evidence:\s*`?(EVIDENCE\/[A-Za-z0-9._\/-]+\.json)`?/, 1]
  end

  def obligation_entry_matches?(schema, entry, obligation_id, path)
    return false unless entry.is_a?(Hash) && entry["path"] == path && entry["status"] == "PASS"

    case schema
    when OBLIGATION_EVIDENCE_SCHEMA, PHASE03_EVIDENCE_SCHEMA
      entry["obligation_id"] == obligation_id
    when LEGACY_EVIDENCE_SCHEMA
      Array(entry["obligation_ids"]).include?(obligation_id)
    else
      false
    end
  end

  def run_child(argv, root)
    Open3.capture3(*argv, chdir: root)
  rescue Errno::ENOENT => e
    ["", e.message, nil]
  end

  def load_and_validate_evidence(root, phase, phase_dir, owner, manifest_path, errors)
    phase03 = phase == "03"
    validator = File.join(
      root,
      phase03 ? ".planning/tools/validate-phase-03-crypto-evidence.rb" : ".planning/tools/validate-verification-evidence.rb"
    )
    unless File.file?(validator)
      errors << "LIFECYCLE_EVIDENCE_VALIDATOR_MISSING path=#{validator}"
      return nil
    end
    unless File.file?(manifest_path)
      errors << "LIFECYCLE_EVIDENCE_MANIFEST_MISSING path=#{manifest_path}"
      return nil
    end
    relative_manifest = repository_relative(root, manifest_path)
    argv = if phase03
      [
        RbConfig.ruby, validator,
        "--phase-dir", repository_relative(root, phase_dir),
        "--require-owner", owner
      ]
    else
      [RbConfig.ruby, validator, "--root", root, "--manifest", relative_manifest, "--require-owner", owner]
    end
    stdout, stderr, status = run_child(argv, root)
    unless status&.success?
      diagnostics = (stdout + stderr).lines.map(&:strip).reject(&:empty?).first(8).join(" | ")
      errors << "LIFECYCLE_EVIDENCE_INVALID manifest=#{relative_manifest} diagnostics=#{diagnostics}"
      return nil
    end
    JSON.parse(File.read(manifest_path))
  rescue JSON::ParserError
    errors << "LIFECYCLE_EVIDENCE_MANIFEST_JSON_INVALID path=#{manifest_path}"
    nil
  end

  def validate_checked_obligation_evidence(root, phase_dir, rows, owned_records, manifest, errors)
    entries = manifest&.fetch("entries", nil)
    if manifest && !entries.is_a?(Array)
      errors << "LIFECYCLE_EVIDENCE_ENTRIES_INVALID"
    end
    owned_rows = rows.select { |row| row.checked && !(row.obligation_ids & owned_records.keys).empty? }
    owned_rows.each do |row|
      row.obligation_ids.each do |id|
        next unless owned_records.key?(id)

        expected = owned_records.fetch(id).fetch(:evidence)
        cited = evidence_reference(row)
        if cited.nil?
          errors << "LIFECYCLE_TODO_EVIDENCE_REFERENCE_MISSING line=#{row.line} id=#{id}"
          next
        end
        unless cited == expected
          errors << "LIFECYCLE_TODO_EVIDENCE_TARGET_MISMATCH line=#{row.line} id=#{id} expected=#{expected} actual=#{cited}"
        end
        absolute = File.join(phase_dir, cited)
        unless File.file?(absolute)
          errors << "LIFECYCLE_TODO_EVIDENCE_MISSING line=#{row.line} id=#{id} path=#{absolute}"
          next
        end
        next unless entries.is_a?(Array)

        root_relative = repository_relative(root, absolute)
        matches = entries.select { |entry| obligation_entry_matches?(manifest["schema_version"], entry, id, root_relative) }
        if matches.length != 1
          errors << "LIFECYCLE_TODO_EVIDENCE_NOT_IN_VALID_MANIFEST line=#{row.line} id=#{id} path=#{root_relative} matches=#{matches.length}"
        end
      end
    end
    if manifest && ![OBLIGATION_EVIDENCE_SCHEMA, LEGACY_EVIDENCE_SCHEMA, PHASE03_EVIDENCE_SCHEMA].include?(manifest["schema_version"])
      errors << "LIFECYCLE_EVIDENCE_SCHEMA_UNSUPPORTED schema=#{manifest['schema_version'].inspect}"
    end
  end

  def evidence_binding(root, manifest, errors)
    return nil unless manifest.is_a?(Hash)

    if manifest["schema_version"] == PHASE03_EVIDENCE_SCHEMA
      subject_reference = manifest["subject"]
      unless subject_reference.is_a?(Hash)
        errors << "LIFECYCLE_EVIDENCE_SUBJECT_REFERENCE_INVALID"
        return nil
      end
      subject_path = subject_reference["path"]
      unless subject_path.is_a?(String)
        errors << "LIFECYCLE_EVIDENCE_SUBJECT_PATH_INVALID"
        return nil
      end
      subject_absolute = contained_path(root, subject_path, errors, "LIFECYCLE_EVIDENCE_SUBJECT")
      return nil unless subject_absolute && File.file?(subject_absolute)

      subject = JSON.parse(File.read(subject_absolute))
      inputs = subject["inputs"]
      unless inputs.is_a?(Array)
        errors << "LIFECYCLE_EVIDENCE_SUBJECT_INPUTS_INVALID"
        return nil
      end
      {
        "subject_manifest_path" => subject_path,
        "subject_manifest_digest" => Digest::SHA256.hexdigest(JSON.generate(canonical(subject))),
        "tested_subject_digest" => Digest::SHA256.hexdigest(JSON.generate(canonical(inputs)))
      }
    else
      manifest.slice("subject_manifest_path", "subject_manifest_digest", "tested_subject_digest")
    end
  rescue JSON::ParserError
    errors << "LIFECYCLE_EVIDENCE_SUBJECT_JSON_INVALID path=#{subject_path}"
    nil
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

  def validate_exit_todo(path, rows, allow_reserved_delivery, errors)
    reserved = rows.select(&:reserved_delivery)
    errors << "LIFECYCLE_RESERVED_DELIVERY_TODO_MISSING path=#{path}" unless reserved.length == 1
    reserved.each do |row|
      # The atomic commit cannot prove its own remote visibility; Plan 03 closes this one item externally.
      if allow_reserved_delivery && row.checked
        errors << "LIFECYCLE_RESERVED_DELIVERY_PREMATURELY_CHECKED path=#{path} line=#{row.line}"
      end
    end
    unchecked = rows.reject(&:checked)
    unchecked = unchecked.reject(&:reserved_delivery) if allow_reserved_delivery
    unless unchecked.empty?
      errors << "LIFECYCLE_EXIT_TODO_UNCHECKED path=#{path} lines=#{unchecked.map(&:line).join(',')}"
    end
  end

  def validate_review(path, manifest, errors, label, allow_latest_binding: false)
    rows = table_rows(path, REVIEW_HEADERS, errors, "LIFECYCLE_REVIEW")
    return if rows.empty?

    attempts = []
    rows.each do |row|
      attempt_text, blocker_text, high_text, escalated, subject_path, subject_digest, tested_digest, result = row.cells
      attempt = Integer(attempt_text, exception: false)
      blocker = Integer(blocker_text, exception: false)
      high = Integer(high_text, exception: false)
      valid_attempt = allow_latest_binding ? attempt&.positive? : attempt&.between?(1, 3)
      errors << "LIFECYCLE_REVIEW_ATTEMPT_INVALID review=#{label} line=#{row.line} value=#{attempt_text}" unless valid_attempt
      errors << "LIFECYCLE_REVIEW_BLOCKER_COUNT_INVALID review=#{label} line=#{row.line} value=#{blocker_text}" unless blocker&.>= 0
      errors << "LIFECYCLE_REVIEW_HIGH_COUNT_INVALID review=#{label} line=#{row.line} value=#{high_text}" unless high&.>= 0
      errors << "LIFECYCLE_REVIEW_ESCALATED_VALUE_INVALID review=#{label} line=#{row.line} value=#{escalated}" unless %w[yes no].include?(escalated)
      errors << "LIFECYCLE_REVIEW_RESULT_INVALID review=#{label} line=#{row.line} value=#{result}" unless %w[PASS BLOCKED].include?(result)
      if manifest
        errors << "LIFECYCLE_REVIEW_SUBJECT_PATH_MISMATCH review=#{label} line=#{row.line}" unless subject_path == manifest["subject_manifest_path"]
        errors << "LIFECYCLE_REVIEW_SUBJECT_MANIFEST_DIGEST_MISMATCH review=#{label} line=#{row.line}" unless subject_digest == manifest["subject_manifest_digest"] && subject_digest.match?(SHA256)
        errors << "LIFECYCLE_REVIEW_TESTED_SUBJECT_DIGEST_MISMATCH review=#{label} line=#{row.line}" unless tested_digest == manifest["tested_subject_digest"] && tested_digest.match?(SHA256)
      end
      attempts << { attempt: attempt, blocker: blocker, high: high, escalated: escalated, result: result, line: row.line }
    end
    valid_attempts = attempts.select { |attempt| attempt[:attempt] && attempt[:blocker] && attempt[:high] }
    actual_sequence = valid_attempts.map { |attempt| attempt[:attempt] }
    expected_sequence = if allow_latest_binding && !actual_sequence.empty?
      (actual_sequence.first...(actual_sequence.first + actual_sequence.length)).to_a
    else
      (1..valid_attempts.length).to_a
    end
    errors << "LIFECYCLE_REVIEW_ATTEMPT_SEQUENCE_INVALID review=#{label} expected=#{expected_sequence.join(',')} actual=#{actual_sequence.join(',')}" unless actual_sequence == expected_sequence
    errors << "LIFECYCLE_REVIEW_ATTEMPT_LIMIT_EXCEEDED review=#{label} count=#{rows.length}" if rows.length > 3
    valid_attempts.each_cons(2) do |previous, current|
      previous_count = previous[:blocker] + previous[:high]
      current_count = current[:blocker] + current[:high]
      if previous_count.positive? && current_count >= previous_count && current_count.positive? && current[:escalated] != "yes"
        errors << "LIFECYCLE_REVIEW_STALLED_NOT_ESCALATED review=#{label} line=#{current[:line]} previous=#{previous_count} current=#{current_count}"
      end
    end
    final = valid_attempts.last
    if final
      blocking = final[:blocker] + final[:high]
      errors << "LIFECYCLE_REVIEW_BLOCKING_FINDINGS review=#{label} line=#{final[:line]} blocker=#{final[:blocker]} high=#{final[:high]}" if blocking.positive?
      errors << "LIFECYCLE_REVIEW_ESCALATED review=#{label} line=#{final[:line]}" if final[:escalated] == "yes"
      errors << "LIFECYCLE_REVIEW_FINAL_RESULT_NOT_PASS review=#{label} line=#{final[:line]} result=#{final[:result]}" unless final[:result] == "PASS" && blocking.zero? && final[:escalated] == "no"
    end
  end

  def invoke_trace_validator(root, phase, package, phase_dir, catalog_path, errors)
    validator = File.join(root, ".planning/tools/validate-trace-closure.rb")
    unless File.file?(validator)
      errors << "LIFECYCLE_TRACE_VALIDATOR_MISSING path=#{validator}"
      return
    end
    stdout, stderr, status = run_child(
      [
        RbConfig.ruby, validator,
        "--root", root,
        "--phase", phase,
        "--package", package,
        "--catalog", repository_relative(root, catalog_path),
        "--phase-dir", repository_relative(root, phase_dir)
      ],
      root
    )
    unless status&.success?
      diagnostics = (stdout + stderr).lines.map(&:strip).reject(&:empty?).first(8).join(" | ")
      errors << "LIFECYCLE_TRACE_INVALID diagnostics=#{diagnostics}"
    end
  end

  def invoke_delivery_validator(root, phase, phase_dir, evidence_manifest, errors)
    validator = File.join(root, ".planning/tools/validate-delivery-attestation.rb")
    unless File.file?(validator)
      errors << "LIFECYCLE_DELIVERY_VALIDATOR_MISSING path=#{validator}"
      return
    end
    summary = File.join(phase_dir, "SUMMARY.md")
    stdout, stderr, status = run_child(
      [
        RbConfig.ruby, validator,
        "--phase", phase,
        "--summary", repository_relative(root, summary),
        "--evidence-manifest", repository_relative(root, evidence_manifest),
        "--require-pr-check-pass"
      ],
      root
    )
    return if status&.success?

    diagnostics = (stdout + stderr).lines.map(&:strip).reject(&:empty?).first(8).join(" | ")
    errors << "LIFECYCLE_DELIVERY_ATTESTATION_INVALID diagnostics=#{diagnostics}"
  end

  def validate(options)
    errors = []
    root = options.fetch(:root)
    phase = options.fetch(:phase)
    package = options.fetch(:package)
    phase_dir = options.fetch(:phase_dir)
    catalog = options.fetch(:catalog)
    stage = options.fetch(:stage)
    todo_path = File.join(phase_dir, "TODO.md")

    validate_artifacts(phase, phase_dir, errors)
    validate_plans(root, phase, phase_dir, errors)
    owned_records = parse_catalog(catalog, package, errors)
    todo_rows = parse_todo(todo_path, phase, errors)
    # The legacy trace validator is a Phase 1 contract. Phase 3 trace closure is
    # checked by its exact-four evidence validator at exit.
    invoke_trace_validator(root, phase, package, phase_dir, catalog, errors) if phase == "01"

    if stage == "entry"
      validate_entry_review(File.join(phase_dir, "ENTRY-REVIEW.md"), errors)
      validate_entry_todo(todo_path, todo_rows, owned_records.keys.to_set, errors)
      return errors
    end

    evidence_manifest = options.fetch(:evidence_manifest)
    manifest = load_and_validate_evidence(root, phase, phase_dir, package, evidence_manifest, errors)
    binding = evidence_binding(root, manifest, errors)
    validate_exit_todo(todo_path, todo_rows, true, errors)
    validate_checked_obligation_evidence(root, phase_dir, todo_rows, owned_records, manifest, errors)

    review_checks = {
      "GSD goal verification" => File.join(phase_dir, "#{phase}-VERIFICATION.md"),
      "GSD code review" => File.join(phase_dir, "#{phase}-REVIEW.md"),
      "Claude" => File.join(phase_dir, "CLAUDE-REVIEW.md")
    }
    if options[:require_gsd_clear] || todo_rows.any? { |row| row.checked && row.text.include?("GSD goal verification") }
      validate_review(
        review_checks.fetch("GSD goal verification"), binding, errors, "gsd-goal",
        allow_latest_binding: phase != "01"
      )
    end
    if options[:require_gsd_clear] || todo_rows.any? { |row| row.checked && row.text.include?("GSD code review") }
      validate_review(
        review_checks.fetch("GSD code review"), binding, errors, "gsd-code",
        allow_latest_binding: phase != "01"
      )
    end
    if options[:require_claude_clear] || todo_rows.any? { |row| row.checked && row.text.match?(/Claude .*review/i) }
      validate_review(
        review_checks.fetch("Claude"), binding, errors, "claude",
        allow_latest_binding: phase != "01"
      )
    end

    unless options[:allow_reserved_delivery] || %w[post-push-delivery effective-todo-empty].include?(stage)
      reserved = todo_rows.select(&:reserved_delivery)
      errors << "LIFECYCLE_RESERVED_DELIVERY_UNCHECKED lines=#{reserved.reject(&:checked).map(&:line).join(',')}" if reserved.any? { |row| !row.checked }
    end

    if %w[post-push-delivery effective-todo-empty].include?(stage)
      invoke_delivery_validator(root, phase, phase_dir, evidence_manifest, errors)
    end
    errors
  end
end

if $PROGRAM_NAME == __FILE__
options = { root: Dir.pwd, stage: nil, allow_reserved_delivery: false, require_gsd_clear: false, require_claude_clear: false }
parser = OptionParser.new do |opts|
  opts.banner = "Usage: ruby .planning/tools/validate-phase-lifecycle.rb --phase 01 --package ID --stage STAGE [options]"
  opts.on("--root PATH") { |value| options[:root] = value }
  opts.on("--phase NN") { |value| options[:phase] = value }
  opts.on("--package ID") { |value| options[:package] = value }
  opts.on("--stage STAGE") { |value| options[:stage] = value }
  opts.on("--phase-dir PATH") { |value| options[:phase_dir] = value }
  opts.on("--catalog PATH") { |value| options[:catalog] = value }
  opts.on("--evidence-manifest PATH") { |value| options[:evidence_manifest] = value }
  opts.on("--require-gsd-clear") { options[:require_gsd_clear] = true }
  opts.on("--require-claude-clear") { options[:require_claude_clear] = true }
  opts.on("--allow-reserved-delivery") { options[:allow_reserved_delivery] = true }
end

begin
  parser.parse!
rescue OptionParser::ParseError => e
  warn "OPTION_ERROR: #{e.message}"
  exit 2
end

errors = []
errors << "OPTION_PHASE_REQUIRED" unless options[:phase]&.match?(/\A\d{2}\z/)
errors << "OPTION_PACKAGE_REQUIRED" unless options[:package]&.match?(/\A[a-z0-9]+(?:-[a-z0-9]+)*\z/)
errors << "OPTION_STAGE_INVALID value=#{options[:stage]} allowed=#{Phase01Lifecycle::STAGES.join(',')}" unless Phase01Lifecycle::STAGES.include?(options[:stage])
root = File.expand_path(options[:root])
errors << "OPTION_ROOT_INVALID path=#{root}" unless File.directory?(root)

if errors.empty?
  default_phase_dir = ".planning/phases/#{options[:phase]}-#{options[:package]}"
  phase_dir = Phase01Lifecycle.contained_path(root, options[:phase_dir] || default_phase_dir, errors, "OPTION_PHASE_DIR")
  catalog = Phase01Lifecycle.contained_path(root, options[:catalog] || ".planning/PRD-OBLIGATIONS.md", errors, "OPTION_CATALOG")
  default_manifest = File.join(default_phase_dir, "EVIDENCE/evidence-manifest.json")
  evidence_manifest = Phase01Lifecycle.contained_path(root, options[:evidence_manifest] || default_manifest, errors, "OPTION_EVIDENCE_MANIFEST")
  if errors.empty?
    errors.concat(
      Phase01Lifecycle.validate(
        root: root,
        phase: options[:phase],
        package: options[:package],
        stage: options[:stage],
        phase_dir: phase_dir,
        catalog: catalog,
        evidence_manifest: evidence_manifest,
        require_gsd_clear: options[:require_gsd_clear],
        require_claude_clear: options[:require_claude_clear],
        allow_reserved_delivery: options[:allow_reserved_delivery]
      )
    )
  end
end

if errors.empty?
  puts "PHASE_LIFECYCLE PASS stage=#{options[:stage]} phase=#{options[:phase]} package=#{options[:package]}"
  exit 0
end

warn "PHASE_LIFECYCLE BLOCKED stage=#{options[:stage] || '-'} errors=#{errors.uniq.length}"
errors.uniq.each { |error| warn "- #{error}" }
exit 1
end
