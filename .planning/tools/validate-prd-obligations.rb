#!/usr/bin/env ruby
# frozen_string_literal: true

require "optparse"
require "set"

PLANNING_ROOT = File.expand_path("..", __dir__)
CATALOG_PATH = File.join(PLANNING_ROOT, "PRD-OBLIGATIONS.md")
REQUIREMENTS_PATH = File.join(PLANNING_ROOT, "REQUIREMENTS.md")
ROADMAP_PATH = File.join(PLANNING_ROOT, "ROADMAP.md")
EXPECTED_RECORD_COUNT = 522
EXPECTED_REQUIREMENT_COUNT = 108
ALLOWED_TEST_LAYERS = Set.new(%w[
  static unit component integration database api protocol security accessibility
  visual playwright load fault uat
]).freeze
ELEMENT_TEST_ID = /\A(?:admin|tenant|shared|public)-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+-[a-z0-9]+(?:-[a-z0-9]+)*\z/

Options = Struct.new(:owner, :requirement, :id_prefix, :assert_unique, :assert_traced, keyword_init: true)
Record = Struct.new(
  :line_number,
  :id,
  :source,
  :requirements,
  :owner,
  :behavior,
  :ui_reference,
  :test_reference,
  :evidence,
  :obligation,
  keyword_init: true
)

def parse_options
  options = Options.new(assert_unique: false, assert_traced: false)
  parser = OptionParser.new do |opts|
    opts.banner = "Usage: ruby .planning/tools/validate-prd-obligations.rb [options]"
    opts.on("--owner ID", "Select records owned by package ID") { |value| options.owner = value }
    opts.on("--requirement ID", "Select records linked to requirement or PROJECT ID") do |value|
      options.requirement = value
    end
    opts.on("--id-prefix PREFIX", "Select obligation IDs beginning with PREFIX") do |value|
      options.id_prefix = value
    end
    opts.on("--assert-unique", "Explicitly request global ID uniqueness assertions") do
      options.assert_unique = true
    end
    opts.on("--assert-traced", "Explicitly request bidirectional trace assertions") do
      options.assert_traced = true
    end
  end
  parser.parse!
  options
rescue OptionParser::ParseError => e
  warn "OPTION_ERROR: #{e.message}"
  exit 2
end

def read_lines(path, errors)
  File.readlines(path, chomp: true)
rescue Errno::ENOENT => e
  errors << "MISSING_FILE: #{e.message}"
  []
end

def known_requirements(lines)
  lines.filter_map { |line| line[/^\| (REQ-(?:F|NFR)-[A-Z0-9-]+) \|/, 1] }.to_set
end

def project_registry(lines)
  lines.filter_map { |line| line[/^- (PROJECT-[A-Z0-9-]+): /, 1] }.to_set
end

def known_owners(lines)
  lines.filter_map { |line| line[/^\*\*Package ID\*\*: `([^`]+)`$/, 1] }.to_set
end

def ui_owners(lines)
  lines.join("\n").split(/^### Phase \d+:/).drop(1).filter_map do |section|
    next unless section.match?(/^\*\*UI hint\*\*: yes$/)

    section[/^\*\*Package ID\*\*: `([^`]+)`$/, 1]
  end.to_set
end

def parse_records(lines, errors)
  records = []

  lines.each_with_index do |line, index|
    next unless line.start_with?("- OBL-")

    fields = line.split(" | ", -1)
    if fields.length != 9
      errors << "BAD_FIELDS line=#{index + 1} expected=9 actual=#{fields.length}"
      next
    end

    requirement_ids = fields[2].split(",", -1)
    records << Record.new(
      line_number: index + 1,
      id: fields[0].delete_prefix("- "),
      source: fields[1],
      requirements: requirement_ids,
      owner: fields[3],
      behavior: fields[4],
      ui_reference: fields[5],
      test_reference: fields[6],
      evidence: fields[7],
      obligation: fields[8]
    )
  end

  records
end

def add_duplicate_errors(records, field, label, errors)
  records.group_by { |record| record.public_send(field) }.each do |value, matches|
    next if value.empty? || matches.length == 1

    errors << "DUPLICATE_#{label} value=#{value} lines=#{matches.map(&:line_number).join(',')}"
  end
end

def validate_record(record, valid_requirement_ids, known_owners, known_ui_owners, errors)
  required_values = {
    "ID" => record.id,
    "SOURCE" => record.source,
    "REQUIREMENTS" => record.requirements.reject(&:empty?).join(","),
    "OWNER" => record.owner,
    "BEHAVIOR" => record.behavior,
    "UI_REFERENCE" => record.ui_reference,
    "TEST" => record.test_reference,
    "EVIDENCE" => record.evidence,
    "OBLIGATION" => record.obligation
  }
  required_values.each do |label, value|
    errors << "MISSING_VALUE line=#{record.line_number} field=#{label}" if value.nil? || value.empty?
  end

  unless record.id.match?(/\AOBL-[A-Z0-9-]+\z/)
    errors << "BAD_OBLIGATION_ID line=#{record.line_number} value=#{record.id}"
  end

  if record.requirements.any? { |id| id.empty? || id != id.strip }
    errors << "BAD_REQUIREMENT_LIST line=#{record.line_number} value=#{record.requirements.join(',')}"
  end
  duplicate_requirements = record.requirements.group_by(&:itself).select { |_id, matches| matches.length > 1 }.keys
  unless duplicate_requirements.empty?
    errors << "DUPLICATE_RECORD_REQUIREMENT line=#{record.line_number} ids=#{duplicate_requirements.join(',')}"
  end
  record.requirements.each do |requirement_id|
    next if valid_requirement_ids.include?(requirement_id)

    errors << "UNKNOWN_REQUIREMENT line=#{record.line_number} id=#{requirement_id}"
  end
  source_requirements = record.source.scan(/F-(\d+)\.(\d+)/).map do |group, item|
    "REQ-F-#{group}-#{item}"
  end
  missing_source_requirements = source_requirements - record.requirements
  unless missing_source_requirements.empty?
    errors << "MISSING_SOURCE_REQUIREMENT line=#{record.line_number} ids=#{missing_source_requirements.join(',')}"
  end

  unless known_owners.include?(record.owner)
    errors << "UNKNOWN_OWNER line=#{record.line_number} owner=#{record.owner}"
  end
  if record.ui_reference != "-" && !known_ui_owners.include?(record.owner)
    errors << "UI_REFERENCE_NON_UI_OWNER line=#{record.line_number} owner=#{record.owner} ui=#{record.ui_reference}"
  end
  unless record.behavior.start_with?("#{record.owner}-")
    errors << "BAD_BEHAVIOR_OWNER_PREFIX line=#{record.line_number} owner=#{record.owner} behavior=#{record.behavior}"
  end

  case record.ui_reference
  when "-"
    nil
  when /\Apage:[a-z0-9]+(?:-[a-z0-9]+)+\z/
    nil
  when /\Aelement:(.+)\z/
    test_id = Regexp.last_match(1)
    unless test_id.match?(ELEMENT_TEST_ID)
      errors << "BAD_ELEMENT_TEST_ID line=#{record.line_number} value=#{test_id}"
    end
  else
    errors << "BAD_UI_REFERENCE line=#{record.line_number} value=#{record.ui_reference}"
  end

  test_id, layer, extra = record.test_reference.split(":", 3)
  if test_id.nil? || !test_id.match?(/\AT-[A-Z0-9-]+\z/) || layer.nil? || extra
    errors << "BAD_TEST_REFERENCE line=#{record.line_number} value=#{record.test_reference}"
  elsif !ALLOWED_TEST_LAYERS.include?(layer)
    errors << "BAD_TEST_LAYER line=#{record.line_number} layer=#{layer}"
  end

  unless record.evidence.match?(%r{\AEVIDENCE/[A-Za-z0-9._/-]+\.(?:json|png|md|txt|log|xml|html|zip|csv)\z})
    errors << "BAD_EVIDENCE_TARGET line=#{record.line_number} value=#{record.evidence}"
  end
end

def select_records(records, options)
  records.select do |record|
    (!options.owner || record.owner == options.owner) &&
      (!options.requirement || record.requirements.include?(options.requirement)) &&
      (!options.id_prefix || record.id.start_with?(options.id_prefix))
  end
end

options = parse_options
errors = []
catalog_lines = read_lines(CATALOG_PATH, errors)
requirements = known_requirements(read_lines(REQUIREMENTS_PATH, errors))
projects = project_registry(catalog_lines)
roadmap_lines = read_lines(ROADMAP_PATH, errors)
owners = known_owners(roadmap_lines)
ui_owner_packages = ui_owners(roadmap_lines)
records = parse_records(catalog_lines, errors)
valid_requirement_ids = requirements | projects

errors << "BAD_RECORD_COUNT expected=#{EXPECTED_RECORD_COUNT} actual=#{records.length}" unless records.length == EXPECTED_RECORD_COUNT
unless requirements.length == EXPECTED_REQUIREMENT_COUNT
  errors << "BAD_REQUIREMENT_COUNT expected=#{EXPECTED_REQUIREMENT_COUNT} actual=#{requirements.length}"
end
errors << "EMPTY_PROJECT_REGISTRY" if projects.empty?
errors << "EMPTY_OWNER_REGISTRY" if owners.empty?
errors << "EMPTY_UI_OWNER_REGISTRY" if ui_owner_packages.empty?

records.each { |record| validate_record(record, valid_requirement_ids, owners, ui_owner_packages, errors) }
add_duplicate_errors(records, :id, "OBLIGATION_ID", errors)
add_duplicate_errors(records, :test_reference, "TEST", errors)
add_duplicate_errors(records, :evidence, "EVIDENCE", errors)
records.group_by { |record| record.test_reference.split(":", 2).first }.each do |test_id, matches|
  next if test_id.empty? || matches.length == 1

  errors << "DUPLICATE_TEST_ID value=#{test_id} lines=#{matches.map(&:line_number).join(',')}"
end

traced_requirements = records.flat_map(&:requirements).grep(/\AREQ-/).to_set
missing_requirements = requirements - traced_requirements
unless missing_requirements.empty?
  errors << "UNTRACED_REQUIREMENTS count=#{missing_requirements.length} ids=#{missing_requirements.to_a.sort.join(',')}"
end

covered_owners = records.map(&:owner).to_set & owners
missing_owners = owners - covered_owners
unless missing_owners.empty?
  errors << "UNCOVERED_OWNERS count=#{missing_owners.length} ids=#{missing_owners.to_a.sort.join(',')}"
end

selected = select_records(records, options)
if (options.owner || options.requirement || options.id_prefix) && selected.empty?
  errors << "EMPTY_SELECTION owner=#{options.owner || '-'} requirement=#{options.requirement || '-'} id_prefix=#{options.id_prefix || '-'}"
end

if errors.any?
  errors.each { |error| warn error }
  warn "validation=FAILED errors=#{errors.length}"
  exit 1
end

puts [
  "validation=PASS",
  "count=#{records.length}",
  "fields=9",
  "requirements=#{traced_requirements.length}/#{requirements.length}",
  "unknown_requirements=0",
  "duplicate_record_requirement_links=0",
  "owners=#{covered_owners.length}/#{owners.length}",
  "unknown_owners=0",
  "duplicate_obligation_ids=0",
  "duplicate_test_ids=0",
  "duplicate_evidence_targets=0",
  "element_refs=#{records.count { |record| record.ui_reference.start_with?('element:') }}",
  "invalid_element_refs=0",
  "ui_owners=#{ui_owner_packages.length}",
  "non_ui_owner_refs=0",
  "selected=#{selected.length}",
  "projects=#{projects.length}"
].join(" ")
