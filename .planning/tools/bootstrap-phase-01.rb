#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "optparse"
require "pathname"
require "set"
require_relative "phase01-chrome-entry-contract"
require_relative "produce-phase-01-chrome-entry"

EXPECTED_PLAN_NAMES = (0..12).map { |index| format("01-%02d-PLAN.md", index) }.freeze
REQUIRED_ARTIFACTS = %w[
  01-SPEC.md
  INTENT.md
  DESIGN.md
  ITERATIONS.md
  DECISIONS.md
  TODO.md
  TEST-MATRIX.md
  ENTRY-REVIEW.md
].freeze

def file_mode(path)
  format("%06o", path.lstat.mode & 0o177777)
end

def parse_json(path, errors, label)
  JSON.parse(path.read)
rescue JSON::ParserError => error
  errors << "#{label}_JSON_INVALID: #{error.message}"
  nil
end

def parse_review(path, errors)
  body = path.read
  expected_header = "| Criterion ID | Verdict | Evidence | Command or inspection rule |"
  errors << "ENTRY_REVIEW_TABLE_HEADER_INVALID" unless body.lines.map(&:strip).include?(expected_header)

  executor = body.match(/^Executor identity:\s*`([^`]+)`\s*$/)&.captures&.first&.strip
  reviewer = body.match(/^Reviewer identity:\s*`([^`]+)`\s*$/)&.captures&.first&.strip
  errors << "ENTRY_REVIEW_EXECUTOR_IDENTITY_MISSING" if executor.nil? || executor.empty?
  errors << "ENTRY_REVIEW_REVIEWER_IDENTITY_MISSING" if reviewer.nil? || reviewer.empty?
  errors << "ENTRY_REVIEW_SELF_APPROVAL" if executor && reviewer && executor == reviewer

  lines = body.lines.map(&:strip)
  header_index = lines.index(expected_header)
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

  errors << "ENTRY_REVIEW_ROWS_MISSING" if rows.empty?
  criterion_ids = rows.map(&:first)
  errors << "ENTRY_REVIEW_EMPTY_CRITERION" if criterion_ids.any?(&:empty?)
  duplicate_ids = criterion_ids.group_by(&:itself).select { |_id, matches| matches.length > 1 }.keys
  errors << "ENTRY_REVIEW_DUPLICATE_CRITERION: #{duplicate_ids.join(',')}" unless duplicate_ids.empty?
  unless criterion_ids.sort == Phase01ChromeEntryContract::REVIEW_CRITERIA.sort
    errors << "ENTRY_REVIEW_CRITERIA_SET_INVALID"
  end
  rows.each do |criterion_id, verdict, evidence, command|
    errors << "ENTRY_REVIEW_VERDICT_INVALID: #{criterion_id}" unless %w[PASS BLOCKER].include?(verdict)
    errors << "ENTRY_REVIEW_EVIDENCE_EMPTY: #{criterion_id}" if evidence.empty?
    errors << "ENTRY_REVIEW_COMMAND_EMPTY: #{criterion_id}" if command.empty?
  end
  errors << "ENTRY_REVIEW_CONTAINS_BLOCKER" if rows.any? { |cells| cells[1] == "BLOCKER" }
  errors << "ENTRY_REVIEW_FINAL_VERDICT_INVALID" unless body.match?(/^## Verdict\s*\n+PASS\s*$/)
end

options = {
  phase_dir: ".planning/phases/01-engineering-verification-foundation",
  catalog: ".planning/PRD-OBLIGATIONS.md"
}
OptionParser.new do |parser|
  parser.on("--phase-dir PATH") { |value| options[:phase_dir] = value }
  parser.on("--catalog PATH") { |value| options[:catalog] = value }
end.parse!

errors = []
phase_dir = Pathname(options[:phase_dir])
catalog = Pathname(options[:catalog])
errors << "CATALOG_MISSING: #{catalog}" unless catalog.file?
errors << "PHASE_DIR_MISSING: #{phase_dir}" unless phase_dir.directory?

records = if catalog.file?
            catalog.readlines(chomp: true).filter_map do |line|
              next unless line.start_with?("- OBL-")

              fields = line.delete_prefix("- ").split(" | ", -1)
              errors << "CATALOG_FIELD_COUNT_INVALID: #{fields.first || line}" unless fields.length == 9
              fields if fields.length == 9
            end
          else
            []
          end
owned_ids = records.select { |fields| fields[3] == "engineering-verification-foundation" }.map(&:first).sort
catalog_ids = records.map(&:first).to_set
errors << "OWNED_OBLIGATION_COUNT_INVALID: expected=7 actual=#{owned_ids.length}" unless owned_ids.length == 7

if phase_dir.directory?
  REQUIRED_ARTIFACTS.each do |name|
    path = phase_dir.join(name)
    errors << "REQUIRED_ARTIFACT_MISSING: #{name}" unless path.file?
  end
  errors << "EVIDENCE_DIR_MISSING" unless phase_dir.join("EVIDENCE").directory?

  evidence_path = phase_dir.join("EVIDENCE/local-chrome-entry.json")
  if !evidence_path.exist?
    errors << "LOCAL_CHROME_ENTRY_MISSING"
  elsif !evidence_path.file? || evidence_path.lstat.symlink?
    errors << "LOCAL_CHROME_ENTRY_UNSAFE"
  else
    errors << "LOCAL_CHROME_ENTRY_MODE_INVALID" unless file_mode(evidence_path) == "100644"
    evidence = parse_json(evidence_path, errors, "LOCAL_CHROME_ENTRY")
    if evidence
      Phase01ChromeEntryContract.validate_entry(evidence).each { |error| errors << "LOCAL_CHROME_ENTRY_#{error}" }
      Phase01ChromeEntryContract.validate_live_file(evidence).each { |error| errors << "LOCAL_CHROME_ENTRY_#{error}" }
      live_version = Phase01LocalChromeEntryProducer.capture_bounded(
        [Phase01ChromeEntryContract::CHROME_PATH, "--version"],
        timeout_seconds: 10,
        output_limit: 4096
      )
      errors << "LOCAL_CHROME_ENTRY_LIVE_VERSION_TIMEOUT" if live_version[:timed_out]
      errors << "LOCAL_CHROME_ENTRY_LIVE_VERSION_FAILED" unless live_version[:success]
      if live_version[:success] && evidence.dig("chrome", "version_output") != live_version[:stdout].strip
        errors << "LOCAL_CHROME_ENTRY_LIVE_VERSION_MISMATCH"
      end
    end
  end

  plan_files = phase_dir.glob("01-*-PLAN.md").sort
  actual_plan_names = plan_files.map { |path| path.basename.to_s }
  errors << "PLAN_COUNT_INVALID: expected=13 actual=#{plan_files.length}" unless plan_files.length == 13
  errors << "PLAN_00_MISSING" unless actual_plan_names.include?("01-00-PLAN.md")
  errors << "PLAN_SET_INVALID: #{actual_plan_names.join(',')}" unless actual_plan_names == EXPECTED_PLAN_NAMES
  remediation_plans = plan_files.select { |path| path.read.match?(/^entry_remediation:\s*true\s*$/) }.map { |path| path.basename.to_s }
  errors << "ENTRY_REMEDIATION_PLAN_SET_INVALID: #{remediation_plans.join(',')}" unless remediation_plans == ["01-00-PLAN.md"]

  plan_files.each do |path|
    tasks = path.read.scan(/<task\b[^>]*>(.*?)<\/task>/m).flatten
    errors << "PLAN_TASK_MISSING: #{path.basename}" if tasks.empty?
    tasks.each_with_index do |task, index|
      %w[files action verify done].each do |field|
        match = task.match(/<#{field}>\s*(.*?)\s*<\/#{field}>/m)
        errors << "PLAN_TASK_FIELD_MISSING: #{path.basename}:#{index + 1}:#{field}" if match.nil? || match[1].strip.empty?
      end
    end
  end

  %w[01-SPEC.md TODO.md TEST-MATRIX.md].each do |name|
    path = phase_dir.join(name)
    next unless path.file?

    body = path.read
    tokens = body.scan(/(?<![A-Z0-9-])OBL-[A-Z0-9-]+(?![A-Z0-9-])/).to_set
    traced_catalog_ids = tokens & catalog_ids
    missing = owned_ids.to_set - traced_catalog_ids
    foreign = traced_catalog_ids - owned_ids.to_set
    unknown = tokens - catalog_ids
    errors << "#{name} MISSING_OWNED_ID: #{missing.to_a.sort.join(',')}" unless missing.empty?
    errors << "#{name} FOREIGN_OBLIGATION_ID: #{foreign.to_a.sort.join(',')}" unless foreign.empty?
    errors << "#{name} UNKNOWN_OBLIGATION_ID: #{unknown.to_a.sort.join(',')}" unless unknown.empty?
  end

  review_path = phase_dir.join("ENTRY-REVIEW.md")
  if review_path.file?
    errors << "ENTRY_REVIEW_UNSAFE" if review_path.lstat.symlink?
    errors << "ENTRY_REVIEW_MODE_INVALID" unless file_mode(review_path) == "100644"
    parse_review(review_path, errors)
  end
end

if errors.empty?
  puts "PHASE_01_BOOTSTRAP PASS"
  puts "owned_obligations=#{owned_ids.length}"
  puts "plans=13"
  puts "chrome_path=#{Phase01ChromeEntryContract::CHROME_PATH}"
  exit 0
end

warn "PHASE_01_BOOTSTRAP BLOCKED"
errors.uniq.each { |error| warn "- #{error}" }
exit 1
