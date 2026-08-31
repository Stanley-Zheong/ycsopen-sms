#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "json"
require "open3"
require "pathname"
require "rbconfig"
require "set"

module PlanningValidatorSupport
  ELEMENT_TEST_ID = /\A(?:admin|tenant|shared|public)-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+(?:-[a-z0-9]+)*\z/
  OBLIGATION_ID = /\bOBL-[A-Z0-9-]+\b/
  REQUIREMENT_ID = /\b(?:REQ-(?:F|NFR)-[A-Z0-9-]+|PROJECT-[A-Z0-9-]+)\b/
  ENTRY_HEADERS = ["Criterion ID", "Verdict", "Evidence", "Command or inspection rule"].freeze
  UI_HEADERS = [
    "Page ID/route", "Role/permission", "Region", "Element/type",
    "Data/validation/format", "Action and API effect", "States and feedback",
    "data-testid", "Obligation/requirement IDs", "Behavior IDs",
    "Catalog test/layer", "Playwright ID"
  ].freeze
  UI_TEST_MATRIX_HEADERS = [
    "Obligation ID", "Requirement IDs", "Behavior ID", "Catalog test/layer",
    "Playwright ID", "Page ID/route", "data-testid", "Case ID", "Case",
    "Command", "Evidence"
  ].freeze
  SCHEMA_HEADERS = [
    "Schema ID", "PRD data domain", "Schema object/prefix", "Owner package",
    "Migration namespace", "Dependencies", "Compatibility", "Rollback",
    "Cross-owner protocol"
  ].freeze
  SCHEMA_CLAIM_HEADERS = [
    "Claim ID", "Schema object/prefix", "Owner package", "Migration ID",
    "Depends on migration", "Compatibility step", "Rollback", "Cross-owner approval"
  ].freeze

  CatalogRecord = Struct.new(
    :id, :requirements, :owner, :behavior, :ui_reference, :test_reference, :evidence,
    keyword_init: true
  )
  SchemaOwner = Struct.new(
    :id, :domain, :prefix, :owner, :range_start, :range_end, :dependencies,
    :compatibility, :rollback, :protocol, keyword_init: true
  )

  module_function

  def read(path, errors, label = "file")
    File.read(path)
  rescue Errno::ENOENT
    errors << "MISSING_#{label.upcase.tr(' ', '_')}: #{path}"
    ""
  rescue Errno::EACCES
    errors << "UNREADABLE_#{label.upcase.tr(' ', '_')}: #{path}"
    ""
  end

  def markdown_table(path, expected_headers, errors, label)
    body = read(path, errors, label)
    return [] if body.empty?

    lines = body.lines.map(&:strip)
    header_index = lines.index do |line|
      table_cells(line)&.map(&:downcase) == expected_headers.map(&:downcase)
    end
    if header_index.nil?
      errors << "BAD_#{label.upcase}_HEADER: #{path} expected=#{expected_headers.join(' | ')}"
      return []
    end

    rows = []
    lines.drop(header_index + 1).each do |line|
      break unless line.start_with?("|") && line.end_with?("|")

      cells = table_cells(line)
      next if cells.length == expected_headers.length && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) }

      if cells.length != expected_headers.length
        errors << "BAD_#{label.upcase}_COLUMN_COUNT: #{path} expected=#{expected_headers.length} actual=#{cells.length}"
        next
      end
      rows << cells
    end
    errors << "EMPTY_#{label.upcase}_TABLE: #{path}" if rows.empty?
    rows
  end

  def markdown_tables(path, expected_headers, errors, label)
    body = read(path, errors, label)
    return [] if body.empty?

    lines = body.lines.map(&:strip)
    header_indexes = lines.each_index.select do |index|
      table_cells(lines[index])&.map(&:downcase) == expected_headers.map(&:downcase)
    end
    if header_indexes.empty?
      errors << "BAD_#{label.upcase}_HEADER: #{path} expected=#{expected_headers.join(' | ')}"
      return []
    end

    header_indexes.map.with_index do |header_index, table_index|
      rows = []
      lines.drop(header_index + 1).each do |line|
        break unless line.start_with?("|") && line.end_with?("|")

        cells = table_cells(line)
        next if cells.length == expected_headers.length && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) }
        if cells.length != expected_headers.length
          errors << "BAD_#{label.upcase}_COLUMN_COUNT: #{path} table=#{table_index + 1} expected=#{expected_headers.length} actual=#{cells.length}"
          next
        end
        rows << cells
      end
      errors << "EMPTY_#{label.upcase}_TABLE: #{path} table=#{table_index + 1}" if rows.empty?
      rows
    end
  end

  def table_cells(line)
    return nil unless line.start_with?("|") && line.end_with?("|")

    line.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
  end

  def roadmap_packages(path, errors)
    body = read(path, errors, "roadmap")
    phases = {}
    body.split(/(?=^### Phase \d+:)/).each do |block|
      next unless block =~ /^### Phase (\d+):/

      number = Regexp.last_match(1).to_i
      package = block[/^\*\*Package ID\*\*: `([^`]+)`$/, 1]
      if package.nil?
        errors << "ROADMAP_PACKAGE_MISSING: phase=#{number}"
      elsif phases.value?(package)
        errors << "ROADMAP_PACKAGE_DUPLICATE: package=#{package}"
      else
        phases[number] = package
      end
    end
    phases
  end

  def roadmap_dependencies(path, errors)
    body = read(path, errors, "roadmap")
    dependencies = {}
    body.split(/(?=^### Phase \d+:)/).each do |block|
      next unless block =~ /^### Phase (\d+):/

      phase = Regexp.last_match(1).to_i
      line = block[/^\*\*Depends on\*\*: (.+)$/, 1]
      if line.nil?
        errors << "ROADMAP_DEPENDENCY_DECLARATION_MISSING: phase=#{phase}"
        dependencies[phase] = []
        next
      end
      values = []
      unless line == "Nothing."
        line.scan(/\d+(?:-\d+)?/).each do |token|
          if token.include?("-")
            left, right = token.split("-").map(&:to_i)
            values.concat((left..right).to_a)
          else
            values << token.to_i
          end
        end
      end
      duplicate_values = duplicates(values)
      errors << "ROADMAP_DEPENDENCY_DUPLICATE: phase=#{phase} values=#{duplicate_values.join(',')}" unless duplicate_values.empty?
      invalid = values.select { |value| value < 1 || value >= phase }
      errors << "ROADMAP_DEPENDENCY_INVALID: phase=#{phase} values=#{invalid.join(',')}" unless invalid.empty?
      dependencies[phase] = values.uniq
    end
    dependencies
  end

  def validate_dependency_evidence(root, phase, phases, dependency_map, errors)
    dependency_map.fetch(phase, []).each do |dependency_phase|
      dependency_package = phases[dependency_phase]
      if dependency_package.nil?
        errors << "DEPENDENCY_PACKAGE_UNKNOWN: phase=#{phase} dependency=#{dependency_phase}"
        next
      end
      token = format("%02d", dependency_phase)
      directory = File.join(root, ".planning/phases/#{token}-#{dependency_package}")
      errors << "DEPENDENCY_DIRECTORY_MISSING: #{directory}" unless File.directory?(directory)
      summary_path = File.join(directory, "SUMMARY.md")
      verification_path = File.join(directory, "#{token}-VERIFICATION.md")
      todo_path = File.join(directory, "TODO.md")
      verification = read(verification_path, errors, "dependency verification")
      validate_todo(todo_path, errors, require_empty: true)
      read(summary_path, errors, "dependency summary")
      unless verification.match?(/^## Verdict\s*\n+PASS\s*$/)
        errors << "DEPENDENCY_VERIFICATION_NOT_PASS: #{verification_path}"
      end
      validate_dependency_delivery_attestation(root, token, directory, summary_path, errors)
    end
  end

  def validate_dependency_delivery_attestation(root, phase_token, directory, summary_path, errors)
    validator = File.join(root, ".planning/tools/validate-delivery-attestation.rb")
    unless File.file?(validator)
      errors << "DEPENDENCY_DELIVERY_VALIDATOR_MISSING: #{validator}"
      return
    end
    evidence_manifest = File.join(directory, "EVIDENCE/evidence-manifest.json")
    stdout, stderr, status = Open3.capture3(
      RbConfig.ruby,
      validator,
      "--phase", phase_token,
      "--summary", repository_relative(root, summary_path),
      "--evidence-manifest", repository_relative(root, evidence_manifest),
      "--require-pr-check-pass",
      chdir: root
    )
    return if status.success?

    diagnostics = (stdout + stderr).lines.map(&:strip).reject(&:empty?).first(8).join(" | ")
    errors << "DEPENDENCY_DELIVERY_ATTESTATION_INVALID: phase=#{phase_token} diagnostics=#{diagnostics}"
  rescue Errno::ENOENT => error
    errors << "DEPENDENCY_DELIVERY_ATTESTATION_EXECUTION_FAILED: phase=#{phase_token} error=#{error.class}"
  end

  def repository_relative(root, path)
    Pathname(File.expand_path(path)).relative_path_from(Pathname(File.expand_path(root))).to_s
  end

  def catalog_records(path, errors)
    records = []
    read(path, errors, "obligation catalog").lines.each_with_index do |line, index|
      next unless line.start_with?("- OBL-")

      fields = line.chomp.split(" | ", -1)
      if fields.length != 9
        errors << "CATALOG_BAD_FIELDS: line=#{index + 1} expected=9 actual=#{fields.length}"
        next
      end
      records << CatalogRecord.new(
        id: fields[0].delete_prefix("- "),
        requirements: fields[2].split(","),
        owner: fields[3],
        behavior: fields[4],
        ui_reference: fields[5],
        test_reference: fields[6],
        evidence: fields[7]
      )
    end
    errors << "CATALOG_EMPTY: #{path}" if records.empty?
    duplicate_ids = duplicates(records.map(&:id))
    errors << "CATALOG_DUPLICATE_IDS: #{duplicate_ids.join(',')}" unless duplicate_ids.empty?
    records
  end

  def exact_obligation_trace(path, owned_ids, known_ids, errors, label)
    body = read(path, errors, label)
    return if body.empty?

    actual = body.scan(OBLIGATION_ID).to_set
    missing = owned_ids - actual
    foreign = actual - owned_ids
    unknown = actual - known_ids
    errors << "MISSING_OWNED_OBLIGATION: artifact=#{path} ids=#{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "FOREIGN_OBLIGATION_ID: artifact=#{path} ids=#{foreign.to_a.sort.join(',')}" unless foreign.empty?
    errors << "UNKNOWN_OBLIGATION_ID: artifact=#{path} ids=#{unknown.to_a.sort.join(',')}" unless unknown.empty?
  end

  def validate_entry_review(path, errors)
    rows = markdown_table(path, ENTRY_HEADERS, errors, "entry_review")
    ids = rows.map(&:first)
    duplicate_ids = duplicates(ids)
    errors << "ENTRY_REVIEW_DUPLICATE_CRITERION: #{duplicate_ids.join(',')}" unless duplicate_ids.empty?
    rows.each do |criterion_id, verdict, evidence, command|
      errors << "ENTRY_REVIEW_EMPTY_CRITERION_ID" if criterion_id.empty?
      errors << "ENTRY_REVIEW_BAD_VERDICT: criterion=#{criterion_id} verdict=#{verdict}" unless %w[PASS BLOCKER].include?(verdict)
      errors << "ENTRY_REVIEW_EMPTY_EVIDENCE: criterion=#{criterion_id}" if evidence.empty?
      errors << "ENTRY_REVIEW_EMPTY_COMMAND: criterion=#{criterion_id}" if command.empty?
      errors << "ENTRY_REVIEW_BLOCKER: criterion=#{criterion_id}" if verdict == "BLOCKER"
    end

    body = File.file?(path) ? File.read(path) : ""
    errors << "ENTRY_REVIEW_FINAL_VERDICT_MISSING: #{path}" unless body.match?(/^## Verdict\s*\n+PASS\s*$/)
  end

  def validate_plans(plan_paths, errors)
    errors << "PLAN_MISSING: expected at least one plan" if plan_paths.empty?
    plan_paths.each do |path|
      body = read(path, errors, "plan")
      tasks = body.scan(/<task\b[^>]*>(.*?)<\/task>/m).flatten
      errors << "PLAN_TASK_MISSING: #{path}" if tasks.empty?
      tasks.each_with_index do |task, index|
        %w[files action verify done].each do |field|
          match = task.match(/<#{field}>\s*(.*?)\s*<\/#{field}>/m)
          if match.nil? || match[1].strip.empty?
            errors << "PLAN_TASK_FIELD_MISSING: plan=#{path} task=#{index + 1} field=#{field}"
          end
        end
      end
    end
  end

  def validate_todo(path, errors, require_empty:, owned_ids: nil)
    body = read(path, errors, "todo")
    checkbox_rows = body.lines.each_with_index.filter_map do |line, index|
      match = line.match(/^\s*- \[([ xX])\]\s*(.*)$/)
      next if match.nil?

      obligation_ids = match[2].scan(OBLIGATION_ID)
      { line: index + 1, checked: match[1].downcase == "x", obligation_ids: obligation_ids }
    end

    if require_empty
      unchecked = checkbox_rows.reject { |row| row[:checked] }.map { |row| row[:line] }
      errors << "TODO_UNCHECKED: artifact=#{path} lines=#{unchecked.join(',')}" unless unchecked.empty?
      return
    end

    checked = checkbox_rows.select { |row| row[:checked] }.map { |row| row[:line] }
    errors << "TODO_PRECHECKED: artifact=#{path} lines=#{checked.join(',')}" unless checked.empty?
    return if owned_ids.nil?

    checkbox_ids = checkbox_rows.flat_map { |row| row[:obligation_ids] }
    missing = owned_ids - checkbox_ids.to_set
    foreign = checkbox_ids.to_set - owned_ids
    duplicate_ids = duplicates(checkbox_ids)
    errors << "TODO_OWNED_CHECKBOX_MISSING: artifact=#{path} ids=#{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "TODO_FOREIGN_CHECKBOX: artifact=#{path} ids=#{foreign.to_a.sort.join(',')}" unless foreign.empty?
    errors << "TODO_DUPLICATE_OBLIGATION_CHECKBOX: artifact=#{path} ids=#{duplicate_ids.join(',')}" unless duplicate_ids.empty?
  end

  def validate_schema_registry(path, roadmap_owners, errors)
    rows = markdown_table(path, SCHEMA_HEADERS, errors, "schema_registry")
    claims = []
    rows.each do |id, domain, prefix, owner, namespace, dependencies, compatibility, rollback, protocol|
      match = namespace.match(/\AV(\d+)-V(\d+)\z/)
      if match.nil? || match[1].to_i > match[2].to_i
        errors << "SCHEMA_BAD_NAMESPACE: id=#{id} value=#{namespace}"
        range_start = range_end = -1
      else
        range_start = match[1].to_i
        range_end = match[2].to_i
      end
      dependency_ids = dependencies == "-" ? [] : dependencies.split(",")
      errors << "SCHEMA_EMPTY_VALUE: id=#{id}" if [id, domain, prefix, owner, compatibility, rollback, protocol].any?(&:empty?)
      errors << "SCHEMA_UNKNOWN_OWNER: id=#{id} owner=#{owner}" unless roadmap_owners.include?(owner)
      unknown_dependencies = dependency_ids - roadmap_owners
      errors << "SCHEMA_UNKNOWN_DEPENDENCY: id=#{id} dependencies=#{unknown_dependencies.join(',')}" unless unknown_dependencies.empty?
      errors << "SCHEMA_SELF_DEPENDENCY: id=#{id}" if dependency_ids.include?(owner)
      errors << "SCHEMA_BAD_COMPATIBILITY: id=#{id}" unless compatibility == "expand-migrate-contract"
      errors << "SCHEMA_BAD_ROLLBACK: id=#{id}" unless rollback.downcase.include?("rollback")
      errors << "SCHEMA_BAD_CROSS_OWNER_PROTOCOL: id=#{id}" unless protocol.include?("DECISIONS.md")
      claims << SchemaOwner.new(
        id: id, domain: domain, prefix: prefix, owner: owner,
        range_start: range_start, range_end: range_end,
        dependencies: dependency_ids, compatibility: compatibility,
        rollback: rollback, protocol: protocol
      )
    end

    %i[id prefix].each do |field|
      values = claims.map { |claim| claim.public_send(field) }
      duplicate_values = duplicates(values)
      errors << "SCHEMA_DUPLICATE_#{field.to_s.upcase}: #{duplicate_values.join(',')}" unless duplicate_values.empty?
    end
    claims.combination(2).each do |left, right|
      next if left.range_start.negative? || right.range_start.negative?
      next if left.range_end < right.range_start || right.range_end < left.range_start

      errors << "SCHEMA_NAMESPACE_CONFLICT: #{left.id}=V#{left.range_start}-V#{left.range_end} #{right.id}=V#{right.range_start}-V#{right.range_end}"
    end
    uncovered = roadmap_owners - claims.map(&:owner).uniq
    errors << "SCHEMA_OWNER_UNCOVERED: #{uncovered.join(',')}" unless uncovered.empty?
    claims
  end

  def validate_phase_schema_claims(root, phase_dir, phase_package, registry, errors)
    design_path = File.join(phase_dir, "DESIGN.md")
    design = File.file?(design_path) ? File.read(design_path) : ""
    claims_path = File.join(phase_dir, "SCHEMA-CLAIMS.md")
    declared = design.match?(/^Schema migrations:\s*declared\s*$/i)
    none = design.match?(/^Schema migrations:\s*none\s*$/i)
    claims_exist = File.file?(claims_path)
    errors << "SCHEMA_DECLARATION_MISSING: #{design_path}" if claims_exist && !declared
    if (declared && none) || (claims_exist && none)
      errors << "SCHEMA_DECLARATION_CONTRADICTION: #{design_path}"
      return
    end
    return unless declared

    rows = markdown_table(claims_path, SCHEMA_CLAIM_HEADERS, errors, "schema_claims")
    decision_body = File.file?(File.join(phase_dir, "DECISIONS.md")) ? File.read(File.join(phase_dir, "DECISIONS.md")) : ""
    rows.each do |claim_id, object, owner, migration_id, migration_dependencies, step, rollback, approval|
      matches = registry.select { |entry| schema_prefix_match?(entry.prefix, object) }
      if matches.length != 1
        errors << "SCHEMA_CLAIM_OWNER_AMBIGUOUS: claim=#{claim_id} object=#{object} matches=#{matches.map(&:id).join(',')}"
        next
      end
      registry_entry = matches.first
      errors << "SCHEMA_CLAIM_OWNER_MISMATCH: claim=#{claim_id} expected=#{registry_entry.owner} actual=#{owner}" unless owner == registry_entry.owner
      migration_number = migration_id[/\AV(\d+)\z/, 1]&.to_i
      unless migration_number && migration_number.between?(registry_entry.range_start, registry_entry.range_end)
        errors << "SCHEMA_CLAIM_NAMESPACE_VIOLATION: claim=#{claim_id} migration=#{migration_id} namespace=V#{registry_entry.range_start}-V#{registry_entry.range_end}"
      end
      dependencies = migration_dependencies == "-" ? [] : migration_dependencies.split(",")
      bad_dependencies = dependencies.reject { |value| value.match?(/\AV\d+\z/) }
      errors << "SCHEMA_CLAIM_BAD_DEPENDENCY: claim=#{claim_id} values=#{bad_dependencies.join(',')}" unless bad_dependencies.empty?
      errors << "SCHEMA_CLAIM_BAD_STEP: claim=#{claim_id} step=#{step}" unless %w[expand migrate contract].include?(step)
      errors << "SCHEMA_CLAIM_EMPTY_ROLLBACK: claim=#{claim_id}" if rollback.empty?
      if owner != phase_package
        unless approval.match?(/\ADR-[A-Z0-9-]+\z/) && decision_body.include?(approval)
          errors << "SCHEMA_CROSS_OWNER_APPROVAL_MISSING: claim=#{claim_id} owner=#{owner} phase=#{phase_package} approval=#{approval}"
        end
      end
    end

    all_claim_files = Dir.glob(File.join(root, ".planning/phases/*/SCHEMA-CLAIMS.md"))
    all_claim_files << claims_path unless all_claim_files.include?(claims_path)
    migration_locations = Hash.new { |hash, key| hash[key] = [] }
    all_claim_files.select { |path| File.file?(path) }.each do |path|
      local_errors = []
      markdown_table(path, SCHEMA_CLAIM_HEADERS, local_errors, "schema_claims").each do |row|
        migration_locations[row[3]] << path
      end
      errors.concat(local_errors)
    end
    migration_locations.each do |migration_id, locations|
      errors << "SCHEMA_MIGRATION_DUPLICATE: migration=#{migration_id} files=#{locations.join(',')}" if locations.length > 1
    end

    sql_versions = Hash.new { |hash, key| hash[key] = [] }
    Dir.glob(File.join(root, "**/db/migration/V*__*.sql")).reject do |path|
      path.match?(%r{/(?:target|build|node_modules|\.git)/})
    end.each do |path|
      version = File.basename(path)[/\AV(\d+)__/, 1]
      sql_versions[version] << path if version
    end
    sql_versions.each do |version, paths|
      errors << "SCHEMA_SQL_MIGRATION_DUPLICATE: migration=V#{version} files=#{paths.join(',')}" if paths.length > 1
    end
    known_migrations = migration_locations.keys.to_set | sql_versions.keys.map { |version| "V#{version}" }.to_set
    rows.each do |row|
      claim_id = row[0]
      dependencies = row[4] == "-" ? [] : row[4].split(",")
      unknown_dependencies = dependencies.reject { |migration_id| known_migrations.include?(migration_id) }
      unless unknown_dependencies.empty?
        errors << "SCHEMA_CLAIM_DEPENDENCY_UNKNOWN: claim=#{claim_id} migrations=#{unknown_dependencies.join(',')}"
      end
    end
  end

  def schema_prefix_match?(prefix, object)
    base = prefix.delete_suffix("*")
    object.start_with?(base)
  end

  def source_paths(root, section, errors, label)
    sources = section["sources"]
    unless sources.is_a?(Array) && !sources.empty?
      errors << "UI_SOURCE_LIST_MISSING: section=#{label}"
      return []
    end
    sources.filter_map do |entry|
      unless entry.is_a?(Hash) && entry["path"].is_a?(String) && entry["sha256"].is_a?(String)
        errors << "UI_SOURCE_ENTRY_BAD: section=#{label} entry=#{entry.inspect}"
        next
      end
      expanded = File.expand_path(entry["path"], root)
      unless expanded.start_with?(File.expand_path(root) + File::SEPARATOR)
        errors << "UI_SOURCE_OUTSIDE_ROOT: section=#{label} path=#{entry['path']}"
        next
      end
      unless File.file?(expanded)
        errors << "UI_SOURCE_MISSING: section=#{label} path=#{entry['path']}"
        next
      end
      actual = Digest::SHA256.file(expanded).hexdigest
      errors << "UI_SOURCE_CHECKSUM_MISMATCH: section=#{label} path=#{entry['path']}" unless actual == entry["sha256"]
      expanded
    end
  end

  def validate_source_contains(paths, values, errors, label)
    body = paths.filter_map do |path|
      File.binread(path).force_encoding("UTF-8").scrub if File.file?(path)
    end.join("\n")
    missing = values.reject { |value| body.include?(value) }
    errors << "UI_SOURCE_VALUE_MISSING: section=#{label} values=#{missing.join(',')}" unless missing.empty?
  end

  def placeholder?(value)
    value.to_s.strip.empty? || %w[- n/a na tbd todo].include?(value.to_s.strip.downcase)
  end

  def comma_tokens(value)
    value.to_s.split(",").map(&:strip).reject(&:empty?)
  end

  def relative_source_path(root, path)
    Pathname(path).relative_path_from(Pathname(File.expand_path(root))).to_s
  end

  def js_code_position?(source, offset)
    quote = nil
    escaped = false
    line_comment = false
    block_comment = false
    index = 0
    while index < offset
      char = source[index]
      following = source[index + 1]
      if line_comment
        line_comment = false if char == "\n"
      elsif block_comment
        if char == "*" && following == "/"
          block_comment = false
          index += 1
        end
      elsif quote
        if escaped
          escaped = false
        elsif char == "\\"
          escaped = true
        elsif char == quote
          quote = nil
        end
      elsif char == "/" && following == "/"
        line_comment = true
        index += 1
      elsif char == "/" && following == "*"
        block_comment = true
        index += 1
      elsif ["'", '"', "`"].include?(char)
        quote = char
      end
      index += 1
    end
    quote.nil? && !line_comment && !block_comment
  end

  def syntax_match?(source, pattern)
    source.to_enum(:scan, pattern).any? do
      match = Regexp.last_match
      js_code_position?(source, match.begin(0))
    end
  end

  def validate_production_implementation_sources(root, paths, routes, selectors, errors)
    valid_paths = paths.select do |path|
      relative = relative_source_path(root, path)
      valid = relative.match?(%r{\Aweb/src/.+\.(?:tsx|ts|jsx|js)\z})
      errors << "UI_PRODUCTION_SOURCE_BAD_PATH: #{relative}" unless valid
      valid
    end
    sources = valid_paths.to_h { |path| [path, File.read(path)] }
    routes.each do |route|
      escaped = Regexp.escape(route)
      pattern = /(?:<Route\b[^>]*\bpath\s*=\s*(?:\{\s*)?["']#{escaped}["'](?:\s*\})?|(?:\A|[\n,{])\s*path\s*:\s*["']#{escaped}["'])/m
      unless sources.any? { |_path, source| syntax_match?(source, pattern) }
        errors << "UI_PRODUCTION_ROUTE_SYNTAX_MISSING: #{route}"
      end
    end
    selectors.each do |selector|
      escaped = Regexp.escape(selector)
      jsx_pattern = /data-testid\s*=\s*(?:["']#{escaped}["']|\{\s*["']#{escaped}["']\s*\})/
      create_element_pattern = /(?:React\.)?createElement\s*\([^,]+,\s*\{[^}]*["']data-testid["']\s*:\s*["']#{escaped}["']/m
      found = sources.any? do |_path, source|
        syntax_match?(source, jsx_pattern) || syntax_match?(source, create_element_pattern)
      end
      errors << "UI_PRODUCTION_TEST_ID_SYNTAX_MISSING: #{selector}" unless found
    end
  end

  def validate_playwright_sources(root, paths, selectors, errors, production:)
    valid_paths = paths.select do |path|
      relative = relative_source_path(root, path)
      valid_extension = relative.match?(%r{\.(?:spec|test)\.(?:ts|tsx|js|jsx)\z})
      valid_location = !production || relative.match?(%r{\Aweb/.+\.(?:spec|test)\.(?:ts|tsx|js|jsx)\z})
      valid = valid_extension && valid_location
      errors << "UI_PLAYWRIGHT_SOURCE_BAD_PATH: #{relative}" unless valid
      valid
    end
    sources = valid_paths.to_h { |path| [path, File.read(path)] }
    unless sources.any? { |_path, source| syntax_match?(source, /\b(?:test|it)\s*\(/) }
      errors << "UI_PLAYWRIGHT_TEST_SYNTAX_MISSING"
    end
    selectors.each do |selector|
      escaped = Regexp.escape(selector)
      getter = /getByTestId\s*\(\s*["']#{escaped}["']\s*\)/
      locator = /locator\s*\(\s*(?:'\[data-testid="#{escaped}"\]'|"\[data-testid='#{escaped}'\]"|`\[data-testid=["']#{escaped}["']\]`)\s*\)/
      unless sources.any? { |_path, source| syntax_match?(source, getter) || syntax_match?(source, locator) }
        errors << "UI_PLAYWRIGHT_SELECTOR_SYNTAX_MISSING: #{selector}"
      end
    end
  end

  def extract_test_blocks(source)
    blocks = []
    source.to_enum(:scan, /\b(?:test|it)\s*\(/).each do
      match = Regexp.last_match
      next unless js_code_position?(source, match.begin(0))

      opening = source.index("(", match.begin(0))
      depth = 0
      quote = nil
      escaped = false
      line_comment = false
      block_comment = false
      index = opening
      while index < source.length
        char = source[index]
        following = source[index + 1]
        if line_comment
          line_comment = false if char == "\n"
        elsif block_comment
          if char == "*" && following == "/"
            block_comment = false
            index += 1
          end
        elsif quote
          if escaped
            escaped = false
          elsif char == "\\"
            escaped = true
          elsif char == quote
            quote = nil
          end
        elsif char == "/" && following == "/"
          line_comment = true
          index += 1
        elsif char == "/" && following == "*"
          block_comment = true
          index += 1
        elsif ["'", '"', "`"].include?(char)
          quote = char
        elsif char == "("
          depth += 1
        elsif char == ")"
          depth -= 1
          if depth.zero?
            blocks << source[match.begin(0)..index]
            break
          end
        end
        index += 1
      end
    end
    blocks
  end

  def test_block_metadata(block)
    callback_offsets = [block.index("=>"), block.index("async function"), block.index("function")].compact
    callback_offsets.empty? ? block : block[0...callback_offsets.min]
  end

  def exact_metadata_token?(metadata, token)
    escaped = Regexp.escape(token)
    metadata.match?(/(?:\A|[^A-Za-z0-9_-])#{escaped}(?:\z|[^A-Za-z0-9_-])/)
  end

  def playwright_locator_pattern(selector)
    escaped = Regexp.escape(selector)
    getter = /(?:page\.)?getByTestId\s*\(\s*["']#{escaped}["']\s*\)/
    locator = /(?:page\.)?locator\s*\(\s*(?:'\[data-testid="#{escaped}"\]'|"\[data-testid='#{escaped}'\]"|`\[data-testid=["']#{escaped}["']\]`)\s*\)/
    [getter, locator]
  end

  def validate_playwright_matrix_blocks(paths, links, errors, label:)
    blocks = paths.flat_map { |path| extract_test_blocks(File.read(path)) }
    links.each do |link|
      metadata = blocks.to_h { |block| [block, test_block_metadata(block)] }
      playwright_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:playwright_id)) }
      case_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:case_id)) }
      obligation_blocks = blocks.select { |block| exact_metadata_token?(metadata[block], link.fetch(:obligation_id)) }
      errors << "UI_PW_BLOCK_PLAYWRIGHT_ID_MISSING: stage=#{label} id=#{link[:playwright_id]}" if playwright_blocks.empty?
      errors << "UI_PW_BLOCK_CASE_ID_MISSING: stage=#{label} id=#{link[:case_id]}" if case_blocks.empty?
      errors << "UI_PW_BLOCK_OBLIGATION_ID_MISSING: stage=#{label} id=#{link[:obligation_id]}" if obligation_blocks.empty?
      matching = playwright_blocks & case_blocks & obligation_blocks
      if matching.empty?
        errors << "UI_PW_BLOCK_ID_COMBINATION_MISSING: stage=#{label} obligation=#{link[:obligation_id]}"
        next
      end

      route = Regexp.escape(link.fetch(:route))
      with_goto = matching.select do |block|
        syntax_match?(block, /await\s+page\.goto\s*\(\s*["']#{route}["']/)
      end
      if with_goto.empty?
        errors << "UI_PW_BLOCK_GOTO_MISSING: stage=#{label} obligation=#{link[:obligation_id]} route=#{link[:route]}"
        next
      end

      getter, locator = playwright_locator_pattern(link.fetch(:selector))
      actionable = with_goto.any? do |block|
        locator_expression = "(?:#{getter.source}|#{locator.source})"
        action = /await\s+#{locator_expression}\s*\.\s*(?:click|fill|check|uncheck|selectOption|press|hover|focus)\s*\(/
        assertion = /await\s+expect\s*\(\s*#{locator_expression}\s*\)\s*\.\s*(?:toBeVisible|toBeHidden|toHaveText|toContainText|toBeEnabled|toBeDisabled|toHaveValue|toHaveAttribute|toHaveCount|toBeChecked)\s*\(/
        syntax_match?(block, action) || syntax_match?(block, assertion)
      end
      unless actionable
        errors << "UI_PW_BLOCK_ACTION_OR_ASSERTION_MISSING: stage=#{label} obligation=#{link[:obligation_id]} selector=#{link[:selector]}"
      end
    end
  end

  def validate_production_execution(root, phase_dir, execution, expected_cases, errors)
    unless execution.is_a?(Hash)
      errors << "UI_PRODUCTION_EXECUTION_MISSING"
      return
    end
    command = execution["command"].to_s
    commit = execution["commit"].to_s
    config = execution["config"].to_s
    result = execution["result"].to_s
    errors << "UI_PRODUCTION_EXECUTION_COMMAND_MISSING" if placeholder?(command) || !command.match?(/playwright/i)
    errors << "UI_PRODUCTION_EXECUTION_COMMIT_BAD: #{commit}" unless commit.match?(/\A[0-9a-f]{40}\z/i)
    errors << "UI_PRODUCTION_EXECUTION_CONFIG_MISSING" if placeholder?(config)
    errors << "UI_PRODUCTION_EXECUTION_NOT_PASS: #{result}" unless result == "PASS"

    report = execution["report"]
    unless report.is_a?(Hash) && report["path"].is_a?(String) && report["sha256"].is_a?(String)
      errors << "UI_PRODUCTION_EXECUTION_REPORT_BAD"
      return
    end
    report_path = File.expand_path(report["path"], root)
    evidence_root = File.expand_path(File.join(phase_dir, "EVIDENCE")) + File::SEPARATOR
    unless report_path.start_with?(evidence_root)
      errors << "UI_PRODUCTION_EXECUTION_REPORT_OUTSIDE_EVIDENCE: #{report['path']}"
      return
    end
    unless File.file?(report_path)
      errors << "UI_PRODUCTION_EXECUTION_REPORT_MISSING: #{report['path']}"
      return
    end
    actual_checksum = Digest::SHA256.file(report_path).hexdigest
    unless actual_checksum == report["sha256"]
      errors << "UI_PRODUCTION_EXECUTION_REPORT_CHECKSUM_MISMATCH: #{report['path']}"
      return
    end
    report_json = begin
      JSON.parse(File.read(report_path))
    rescue JSON::ParserError
      errors << "UI_PRODUCTION_EXECUTION_REPORT_JSON_BAD: #{report['path']}"
      return
    end
    {
      "command" => command,
      "commit" => commit,
      "config" => config,
      "result" => "PASS"
    }.each do |key, expected|
      errors << "UI_PRODUCTION_EXECUTION_REPORT_MISMATCH: key=#{key}" unless report_json[key] == expected
    end
    actual_cases = report_json["cases"]
    unless actual_cases.is_a?(Array) && actual_cases.all? { |value| value.is_a?(String) }
      errors << "UI_PRODUCTION_EXECUTION_REPORT_CASES_BAD"
      return
    end
    duplicate_cases = duplicates(actual_cases)
    errors << "UI_PRODUCTION_EXECUTION_REPORT_CASE_DUPLICATE: #{duplicate_cases.join(',')}" unless duplicate_cases.empty?
    missing = expected_cases - actual_cases
    stale = actual_cases - expected_cases
    errors << "UI_PRODUCTION_EXECUTION_REPORT_CASE_MISSING: #{missing.join(',')}" unless missing.empty?
    errors << "UI_PRODUCTION_EXECUTION_REPORT_CASE_STALE: #{stale.join(',')}" unless stale.empty?
  end

  def exact_json_set(section, key, expected, errors, label)
    actual = section[key]
    unless actual.is_a?(Array) && actual.all? { |value| value.is_a?(String) }
      errors << "UI_INVENTORY_BAD_ARRAY: section=#{label} key=#{key}"
      return []
    end
    duplicate_values = duplicates(actual)
    errors << "UI_INVENTORY_DUPLICATE: section=#{label} key=#{key} values=#{duplicate_values.join(',')}" unless duplicate_values.empty?
    missing = expected - actual
    stale = actual - expected
    errors << "UI_INVENTORY_MISSING: section=#{label} key=#{key} values=#{missing.join(',')}" unless missing.empty?
    errors << "UI_INVENTORY_STALE: section=#{label} key=#{key} values=#{stale.join(',')}" unless stale.empty?
    actual
  end

  def duplicates(values)
    values.group_by(&:itself).select { |value, matches| !value.to_s.empty? && matches.length > 1 }.keys
  end
end
