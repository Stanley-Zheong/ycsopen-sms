#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "optparse"
require "pathname"
require "set"

module Phase01TraceClosure
  EXPECTED_OWNER = "engineering-verification-foundation"
  EXPECTED_OWNED_IDS = Set.new(%w[
    OBL-FOUND-TRACE-001
    OBL-FOUND-TRACE-002
    OBL-FOUND-TRACE-003
    OBL-FOUND-TRACE-004
    OBL-FOUND-UI-DRIFT-001
    OBL-FOUND-UI-DRIFT-002
    OBL-NFR-BROWSER
  ]).freeze
  PRODUCT_COMPATIBILITY = {
    "OBL-NFR-CHINESE" => "final-release-acceptance",
    "OBL-NFR-TIMEZONE" => "final-release-acceptance"
  }.freeze
  OBLIGATION_ID = /\AOBL-[A-Z0-9-]+\z/
  REQUIREMENT_ID = /\A(?:REQ-(?:F|NFR)-[A-Z0-9-]+|PROJECT-[A-Z0-9-]+)\z/
  TEST_REFERENCE = /\AT-[A-Z0-9-]+:(?:static|unit|component|integration|database|api|protocol|security|accessibility|visual|playwright|load|fault|uat)\z/
  EVIDENCE_TARGET = %r{\AEVIDENCE/[A-Za-z0-9._/-]+\.(?:json|png|md|txt|log|xml|html|zip|csv)\z}
  SPEC_HEADERS = ["Obligation ID", "Requirement", "Behavior IDs", "Verification IDs"].freeze
  MATRIX_HEADERS = [
    "Obligation ID", "Requirement IDs", "Behavior ID", "Catalog test/layer",
    "Playwright ID", "Page ID/route", "data-testid", "Case ID", "Case",
    "Command", "Evidence"
  ].freeze

  Record = Struct.new(
    :line, :id, :source, :requirements, :owner, :behavior, :ui_reference,
    :test_reference, :evidence, :obligation, keyword_init: true
  )
  Row = Struct.new(:line, :cells, keyword_init: true)

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

  def read(path, errors, label)
    File.read(path)
  rescue Errno::ENOENT
    errors << "#{label}_MISSING path=#{path}"
    ""
  rescue Errno::EACCES
    errors << "#{label}_UNREADABLE path=#{path}"
    ""
  end

  def duplicate_values(values)
    values.group_by(&:itself).select { |value, rows| !value.to_s.empty? && rows.length > 1 }.keys.sort
  end

  def parse_catalog(path, errors)
    records = []
    read(path, errors, "CATALOG").lines.each_with_index do |line, index|
      next unless line.start_with?("- OBL-")

      fields = line.chomp.split(" | ", -1)
      if fields.length != 9
        errors << "CATALOG_BAD_FIELDS path=#{path} line=#{index + 1} expected=9 actual=#{fields.length}"
        next
      end
      record = Record.new(
        line: index + 1,
        id: fields[0].delete_prefix("- "),
        source: fields[1],
        requirements: fields[2].split(",", -1),
        owner: fields[3],
        behavior: fields[4],
        ui_reference: fields[5],
        test_reference: fields[6],
        evidence: fields[7],
        obligation: fields[8]
      )
      {
        "ID" => record.id,
        "SOURCE" => record.source,
        "REQUIREMENT" => record.requirements.reject(&:empty?).join(","),
        "OWNER" => record.owner,
        "BEHAVIOR" => record.behavior,
        "UI_REFERENCE" => record.ui_reference,
        "TEST" => record.test_reference,
        "EVIDENCE" => record.evidence,
        "OBLIGATION" => record.obligation
      }.each do |field, value|
        errors << "CATALOG_VALUE_MISSING path=#{path} line=#{record.line} id=#{record.id} field=#{field}" if value.empty?
      end
      records << record
    end
    errors << "CATALOG_EMPTY path=#{path}" if records.empty?
    add_catalog_duplicates(records, errors)
    records
  end

  def add_catalog_duplicates(records, errors)
    {
      id: "OBLIGATION",
      test_reference: "TEST",
      evidence: "EVIDENCE"
    }.each do |field, label|
      duplicate_values(records.map { |record| record.public_send(field) }).each do |value|
        lines = records.select { |record| record.public_send(field) == value }.map(&:line)
        errors << "CATALOG_DUPLICATE_#{label} value=#{value} lines=#{lines.join(',')}"
      end
    end
  end

  def known_requirements(requirements_path, catalog_path, errors)
    values = read(requirements_path, errors, "REQUIREMENTS").scan(/^\| (REQ-(?:F|NFR)-[A-Z0-9-]+) \|/).flatten.to_set
    values.merge(read(catalog_path, errors, "CATALOG").scan(/^- (PROJECT-[A-Z0-9-]+): /).flatten)
    errors << "REQUIREMENT_REGISTRY_EMPTY path=#{requirements_path}" if values.empty?
    values
  end

  def validate_catalog_records(records, known_requirements, owner, errors)
    records.each do |record|
      errors << "CATALOG_BAD_OBLIGATION_ID line=#{record.line} value=#{record.id}" unless record.id.match?(OBLIGATION_ID)
      record.requirements.each do |requirement|
        unless requirement.match?(REQUIREMENT_ID) && known_requirements.include?(requirement)
          errors << "CATALOG_UNKNOWN_REQUIREMENT line=#{record.line} id=#{record.id} requirement=#{requirement}"
        end
      end
      if EXPECTED_OWNED_IDS.include?(record.id) && record.owner != owner
        errors << "CATALOG_OWNER_OUTSIDE_PHASE line=#{record.line} id=#{record.id} expected=#{owner} actual=#{record.owner}"
      end
      expected_product_owner = PRODUCT_COMPATIBILITY[record.id]
      if expected_product_owner && record.owner != expected_product_owner
        errors << "CATALOG_PRODUCT_COMPATIBILITY_OWNER_MISMATCH line=#{record.line} id=#{record.id} expected=#{expected_product_owner} actual=#{record.owner}"
      end
      unless record.behavior.empty? || record.behavior.start_with?("#{record.owner}-")
        errors << "CATALOG_BEHAVIOR_OWNER_MISMATCH line=#{record.line} id=#{record.id} owner=#{record.owner} behavior=#{record.behavior}"
      end
      unless record.test_reference.empty? || record.test_reference.match?(TEST_REFERENCE)
        errors << "CATALOG_TEST_INVALID line=#{record.line} id=#{record.id} value=#{record.test_reference}"
      end
      unless record.evidence.empty? || record.evidence.match?(EVIDENCE_TARGET)
        errors << "CATALOG_EVIDENCE_INVALID line=#{record.line} id=#{record.id} value=#{record.evidence}"
      end
    end

    actual_owned = records.select { |record| record.owner == owner }.map(&:id).to_set
    missing = EXPECTED_OWNED_IDS - actual_owned
    foreign = actual_owned - EXPECTED_OWNED_IDS
    errors << "CATALOG_OWNER_SET_MISSING owner=#{owner} ids=#{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "CATALOG_OWNER_SET_FOREIGN owner=#{owner} ids=#{foreign.to_a.sort.join(',')}" unless foreign.empty?
    PRODUCT_COMPATIBILITY.each do |id, product_owner|
      match = records.find { |record| record.id == id }
      errors << "CATALOG_PRODUCT_COMPATIBILITY_MISSING id=#{id} owner=#{product_owner}" if match.nil?
    end
  end

  def table_rows(path, headers, errors, label)
    lines = read(path, errors, label).lines
    header_index = lines.index do |line|
      table_cells(line)&.map(&:downcase) == headers.map(&:downcase)
    end
    if header_index.nil?
      errors << "#{label}_HEADER_INVALID path=#{path}"
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
      rows << Row.new(line: header_index + offset + 2, cells: cells)
    end
    errors << "#{label}_EMPTY path=#{path}" if rows.empty?
    rows
  end

  def table_cells(line)
    stripped = line.strip
    return nil unless stripped.start_with?("|") && stripped.end_with?("|")

    stripped.delete_prefix("|").delete_suffix("|").split("|", -1).map(&:strip)
  end

  def compare_direction(expected, actual, artifact, errors, label)
    missing = expected - actual
    foreign = actual - expected
    errors << "TRACE_CATALOG_TO_#{label}_ORPHAN artifact=#{artifact} ids=#{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "TRACE_#{label}_TO_CATALOG_ORPHAN artifact=#{artifact} ids=#{foreign.to_a.sort.join(',')}" unless foreign.empty?
  end

  def validate_spec(path, owned_records, errors)
    rows = table_rows(path, SPEC_HEADERS, errors, "SPEC_TRACE")
    ids = rows.map { |row| row.cells[0] }
    duplicate_values(ids).each do |id|
      errors << "TRACE_SPEC_DUPLICATE_OBLIGATION artifact=#{path} id=#{id} lines=#{rows.select { |row| row.cells[0] == id }.map(&:line).join(',')}"
    end
    compare_direction(owned_records.keys.to_set, ids.to_set, path, errors, "SPEC")
    rows.each do |row|
      id, requirement_text, behavior_text, verification_text = row.cells
      record = owned_records[id]
      next unless record

      requirements = requirement_text.split(",").map(&:strip).to_set
      expected_requirements = record.requirements.to_set
      errors << "TRACE_SPEC_REQUIREMENT_MISMATCH artifact=#{path} line=#{row.line} id=#{id}" unless requirements == expected_requirements
      behaviors = behavior_text.split(",").map(&:strip)
      errors << "TRACE_SPEC_BEHAVIOR_MISMATCH artifact=#{path} line=#{row.line} id=#{id}" unless behaviors.include?(record.behavior)
      errors << "TRACE_SPEC_VERIFICATION_MISSING artifact=#{path} line=#{row.line} id=#{id}" if verification_text.empty?
    end
  end

  def validate_todo(path, expected_ids, errors)
    rows = read(path, errors, "TODO").lines.each_with_index.filter_map do |line, index|
      match = line.match(/^\s*- \[([ xX])\]\s+(OBL-[A-Z0-9-]+)\b/)
      { line: index + 1, id: match[2] } if match
    end
    ids = rows.map { |row| row[:id] }
    duplicate_values(ids).each do |id|
      errors << "TRACE_TODO_DUPLICATE_OBLIGATION artifact=#{path} id=#{id} lines=#{rows.select { |row| row[:id] == id }.map { |row| row[:line] }.join(',')}"
    end
    compare_direction(expected_ids, ids.to_set, path, errors, "TODO")
  end

  def validate_matrix(path, owned_records, errors)
    rows = table_rows(path, MATRIX_HEADERS, errors, "TEST_MATRIX")
    ids = rows.map { |row| row.cells[0] }
    duplicate_values(ids).each do |id|
      errors << "TRACE_MATRIX_DUPLICATE_OBLIGATION artifact=#{path} id=#{id} lines=#{rows.select { |row| row.cells[0] == id }.map(&:line).join(',')}"
    end
    compare_direction(owned_records.keys.to_set, ids.to_set, path, errors, "MATRIX")
    rows.each do |row|
      id, requirement_text, behavior, test_reference, _playwright, _page, _selector,
        case_id, test_case, command, evidence = row.cells
      record = owned_records[id]
      next unless record

      unless requirement_text.split(",").map(&:strip).to_set == record.requirements.to_set
        errors << "TRACE_MATRIX_REQUIREMENT_MISMATCH artifact=#{path} line=#{row.line} id=#{id}"
      end
      errors << "TRACE_MATRIX_BEHAVIOR_MISMATCH artifact=#{path} line=#{row.line} id=#{id}" unless behavior == record.behavior
      errors << "TRACE_MATRIX_TEST_MISMATCH artifact=#{path} line=#{row.line} id=#{id} expected=#{record.test_reference} actual=#{test_reference}" unless test_reference == record.test_reference
      errors << "TRACE_MATRIX_EVIDENCE_MISMATCH artifact=#{path} line=#{row.line} id=#{id} expected=#{record.evidence} actual=#{evidence}" unless evidence == record.evidence
      {
        "CASE" => case_id,
        "CASE_TEXT" => test_case,
        "COMMAND" => command
      }.each do |field, value|
        errors << "TRACE_MATRIX_VALUE_MISSING artifact=#{path} line=#{row.line} id=#{id} field=#{field}" if value.empty?
      end
    end
  end

  def plan_obligation_ids(path, errors)
    body = read(path, errors, "PLAN")
    frontmatter = body[/\A---\s*\n(.*?)\n---\s*\n/m, 1]
    if frontmatter.nil?
      errors << "TRACE_PLAN_FRONTMATTER_MISSING artifact=#{path}"
      return []
    end
    lines = frontmatter.lines
    index = lines.index { |line| line.match?(/^obligations:\s*/) }
    if index.nil?
      errors << "TRACE_PLAN_OBLIGATIONS_MISSING artifact=#{path}"
      return []
    end
    declaration = lines[index].sub(/^obligations:\s*/, "").strip
    return declaration.scan(/OBL-[A-Z0-9-]+/) unless declaration.empty?

    lines.drop(index + 1).take_while { |line| line.match?(/^\s+/) }.filter_map do |line|
      line[/^\s*-\s*(OBL-[A-Z0-9-]+)\s*$/, 1]
    end
  end

  def validate_plans(paths, expected_ids, errors)
    if paths.empty?
      errors << "TRACE_PLAN_MISSING"
      return
    end
    ids = paths.flat_map { |path| plan_obligation_ids(path, errors) }.to_set
    compare_direction(expected_ids, ids, paths.join(","), errors, "PLAN")
  end

  def evidence_obligation_ids(value, found, errors, path, key_path = [])
    case value
    when Hash
      value.each do |key, child|
        if key == "obligation_id"
          if child.is_a?(String) && child.match?(OBLIGATION_ID)
            found << child
          else
            errors << "TRACE_EVIDENCE_OBLIGATION_ID_INVALID artifact=#{path} key=#{(key_path + [key]).join('.')}"
          end
        elsif key == "obligation_ids"
          if child.is_a?(Array) && child.all? { |item| item.is_a?(String) && item.match?(OBLIGATION_ID) }
            found.merge(child)
          else
            errors << "TRACE_EVIDENCE_OBLIGATION_IDS_INVALID artifact=#{path} key=#{(key_path + [key]).join('.')}"
          end
        end
        evidence_obligation_ids(child, found, errors, path, key_path + [key])
      end
    when Array
      value.each_with_index { |child, index| evidence_obligation_ids(child, found, errors, path, key_path + [index]) }
    end
  end

  def validate_existing_evidence(phase_dir, known_ids, owner_ids, errors)
    evidence_root = File.join(phase_dir, "EVIDENCE")
    return unless File.directory?(evidence_root)

    Dir.glob(File.join(evidence_root, "**/*.json")).sort.each do |path|
      next if File.symlink?(path) || !File.file?(path)
      relative = Pathname(path).relative_path_from(Pathname(evidence_root)).to_s
      # Schemas and mutation fixtures describe evidence fields; they are not executed evidence edges.
      next if relative.start_with?("schema/", "fixtures/")

      value = JSON.parse(File.read(path))
      ids = Set.new
      evidence_obligation_ids(value, ids, errors, path)
      unknown = ids - known_ids
      errors << "TRACE_EVIDENCE_UNKNOWN_OBLIGATION artifact=#{path} ids=#{unknown.to_a.sort.join(',')}" unless unknown.empty?
      next if ids.empty?

      foreign = ids - owner_ids
      errors << "TRACE_EVIDENCE_FOREIGN_OBLIGATION artifact=#{path} ids=#{foreign.to_a.sort.join(',')}" unless foreign.empty?
    rescue JSON::ParserError => e
      errors << "TRACE_EVIDENCE_JSON_INVALID artifact=#{path} error=#{e.class}"
    end
  end

  def validate(root:, phase:, package:, catalog_path:, requirements_path:, phase_dir:)
    errors = []
    unless package == EXPECTED_OWNER
      errors << "OPTION_PACKAGE_UNSUPPORTED expected=#{EXPECTED_OWNER} actual=#{package}"
    end
    unless phase == "01"
      errors << "OPTION_PHASE_UNSUPPORTED expected=01 actual=#{phase}"
    end

    records = parse_catalog(catalog_path, errors)
    registry = known_requirements(requirements_path, catalog_path, errors)
    validate_catalog_records(records, registry, package, errors)
    owned_records = records.select { |record| record.owner == package }.to_h { |record| [record.id, record] }
    validate_spec(File.join(phase_dir, "01-SPEC.md"), owned_records, errors)
    validate_todo(File.join(phase_dir, "TODO.md"), owned_records.keys.to_set, errors)
    validate_matrix(File.join(phase_dir, "TEST-MATRIX.md"), owned_records, errors)
    validate_plans(Dir.glob(File.join(phase_dir, "01-*-PLAN.md")).sort, owned_records.keys.to_set, errors)
    validate_existing_evidence(phase_dir, records.map(&:id).to_set, owned_records.keys.to_set, errors)
    errors
  end
end

options = { root: Dir.pwd }
parser = OptionParser.new do |opts|
  opts.banner = "Usage: ruby .planning/tools/validate-trace-closure.rb --phase 01 --package engineering-verification-foundation [options]"
  opts.on("--root PATH") { |value| options[:root] = value }
  opts.on("--phase NN") { |value| options[:phase] = value }
  opts.on("--package ID") { |value| options[:package] = value }
  opts.on("--catalog PATH") { |value| options[:catalog] = value }
  opts.on("--requirements PATH") { |value| options[:requirements] = value }
  opts.on("--phase-dir PATH") { |value| options[:phase_dir] = value }
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
root = File.expand_path(options[:root])
unless File.directory?(root)
  errors << "OPTION_ROOT_INVALID path=#{root}"
end

if errors.empty?
  catalog = Phase01TraceClosure.contained_path(root, options[:catalog] || ".planning/PRD-OBLIGATIONS.md", errors, "OPTION_CATALOG")
  requirements = Phase01TraceClosure.contained_path(root, options[:requirements] || ".planning/REQUIREMENTS.md", errors, "OPTION_REQUIREMENTS")
  default_phase_dir = ".planning/phases/#{options[:phase]}-#{options[:package]}"
  phase_dir = Phase01TraceClosure.contained_path(root, options[:phase_dir] || default_phase_dir, errors, "OPTION_PHASE_DIR")
  if errors.empty?
    errors.concat(
      Phase01TraceClosure.validate(
        root: root,
        phase: options[:phase],
        package: options[:package],
        catalog_path: catalog,
        requirements_path: requirements,
        phase_dir: phase_dir
      )
    )
  end
end

if errors.empty?
  puts "TRACE_CLOSURE PASS owner=#{options[:package]} obligations=#{Phase01TraceClosure::EXPECTED_OWNED_IDS.length} product_compatibility_owner=final-release-acceptance"
  exit 0
end

warn "TRACE_CLOSURE BLOCKED errors=#{errors.length}"
errors.uniq.each { |error| warn "- #{error}" }
exit 1
