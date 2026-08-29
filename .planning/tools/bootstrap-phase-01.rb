#!/usr/bin/env ruby
# frozen_string_literal: true

require "optparse"
require "pathname"
require "set"

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

errors << "missing catalog: #{catalog}" unless catalog.file?
errors << "missing phase directory: #{phase_dir}" unless phase_dir.directory?

records = if catalog.file?
            catalog.readlines(chomp: true).filter_map do |line|
              next unless line.start_with?("- OBL-")

              fields = line.delete_prefix("- ").split(" | ", -1)
              errors << "catalog record must have 9 fields: #{fields.first || line}" unless fields.length == 9
              fields if fields.length == 9
            end
          else
            []
          end

owned_ids = records.select { |fields| fields[3] == "engineering-verification-foundation" }.map(&:first).sort
catalog_ids = records.map(&:first).to_set
errors << "Phase 1 has no owned obligations in #{catalog}" if owned_ids.empty?

required_files = %w[
  01-SPEC.md
  INTENT.md
  DESIGN.md
  ITERATIONS.md
  DECISIONS.md
  TODO.md
  TEST-MATRIX.md
  ENTRY-REVIEW.md
]

required_files.each do |name|
  path = phase_dir.join(name)
  errors << "missing required artifact: #{path}" unless path.file?
end
errors << "missing required evidence directory: #{phase_dir.join('EVIDENCE')}" unless phase_dir.join("EVIDENCE").directory?

plan_files = phase_dir.glob("01-*-PLAN.md").sort
errors << "expected at least one 01-*-PLAN.md in #{phase_dir}" if plan_files.empty?

trace_files = %w[01-SPEC.md TODO.md TEST-MATRIX.md]
trace_files.each do |name|
  path = phase_dir.join(name)
  next unless path.file?

  body = path.read
  traced_tokens = body.scan(/(?<![A-Z0-9-])OBL-[A-Z0-9-]+(?![A-Z0-9-])/).to_set
  traced_catalog_ids = traced_tokens & catalog_ids
  missing_owned_ids = owned_ids.to_set - traced_catalog_ids
  foreign_obligation_ids = traced_catalog_ids - owned_ids.to_set
  unknown_obligation_ids = traced_tokens - catalog_ids

  unless missing_owned_ids.empty?
    errors << "#{path} MISSING_OWNED_ID: #{missing_owned_ids.to_a.sort.join(', ')}"
  end
  unless foreign_obligation_ids.empty?
    errors << "#{path} FOREIGN_OBLIGATION_ID: #{foreign_obligation_ids.to_a.sort.join(', ')}"
  end
  unless unknown_obligation_ids.empty?
    errors << "#{path} UNKNOWN_OBLIGATION_ID: #{unknown_obligation_ids.to_a.sort.join(', ')}"
  end

  owned_ids.each do |obligation_id|
    count = body.scan(/(?<![A-Z0-9-])#{Regexp.escape(obligation_id)}(?![A-Z0-9-])/).length
    errors << "#{path} must trace #{obligation_id} at least once" if count.zero?
  end
end

plan_files.each do |path|
  body = path.read
  tasks = body.scan(/<task\b[^>]*>(.*?)<\/task>/m).flatten
  errors << "#{path} must contain at least one <task>" if tasks.empty?

  tasks.each_with_index do |task, index|
    %w[files action verify done].each do |field|
      match = task.match(/<#{field}>\s*(.*?)\s*<\/#{field}>/m)
      errors << "#{path} task #{index + 1} missing nonempty <#{field}>" if match.nil? || match[1].strip.empty?
    end
  end
end

entry_review = phase_dir.join("ENTRY-REVIEW.md")
if entry_review.file?
  body = entry_review.read
  errors << "#{entry_review} must contain a ## Verdict section" unless body.match?(/^## Verdict\s*$/)
  errors << "#{entry_review} verdict must be PASS" unless body.match?(/^## Verdict\s*\n+PASS\s*$/)

  review_lines = body.lines.map(&:strip)
  expected_header = ["criterion id", "verdict", "evidence", "command or inspection rule"]
  header_index = review_lines.index do |line|
    next false unless line.start_with?("|") && line.end_with?("|")

    cells = line.delete_prefix("|").delete_suffix("|").split("|").map(&:strip)
    cells.map(&:downcase) == expected_header
  end

  if header_index.nil?
    errors << "#{entry_review} must contain the exact criterion table header: Criterion ID | Verdict | Evidence | Command or inspection rule"
  else
    criterion_rows = []
    review_lines.drop(header_index + 1).each do |line|
      break unless line.start_with?("|") && line.end_with?("|")

      cells = line.delete_prefix("|").delete_suffix("|").split("|").map(&:strip)
      next if cells.length == 4 && cells.all? { |cell| cell.match?(/\A:?-{3,}:?\z/) }

      criterion_rows << cells
    end
    malformed_rows = criterion_rows.reject { |cells| cells.length == 4 }
    errors << "#{entry_review} has criterion rows with the wrong column count" unless malformed_rows.empty?
    criterion_rows.select! { |cells| cells.length == 4 }
    errors << "#{entry_review} must contain at least one criterion result row" if criterion_rows.empty?

    criterion_ids = criterion_rows.map(&:first)
    duplicate_ids = criterion_ids.group_by(&:itself).select { |_id, matches| matches.length > 1 }.keys
    errors << "#{entry_review} has duplicate criterion IDs: #{duplicate_ids.join(', ')}" unless duplicate_ids.empty?

    criterion_rows.each_with_index do |cells, index|
      criterion_id, verdict, evidence, command = cells
      errors << "#{entry_review} criterion row #{index + 1} has an empty criterion ID" if criterion_id.empty?
      errors << "#{entry_review} criterion #{criterion_id} verdict must be PASS or BLOCKER" unless %w[PASS BLOCKER].include?(verdict)
      errors << "#{entry_review} criterion #{criterion_id} has empty evidence" if evidence.empty?
      errors << "#{entry_review} criterion #{criterion_id} has empty command or inspection rule" if command.empty?
    end

    blocker_rows = criterion_rows.select { |cells| cells[1] == "BLOCKER" }
    errors << "#{entry_review} contains BLOCKER criterion verdicts" unless blocker_rows.empty?
  end
end

if errors.empty?
  puts "PHASE_01_BOOTSTRAP PASS"
  puts "owned_obligations=#{owned_ids.length}"
  puts "plans=#{plan_files.length}"
  puts "phase_dir=#{phase_dir}"
  exit 0
end

warn "PHASE_01_BOOTSTRAP BLOCKED"
errors.each { |error| warn "- #{error}" }
exit 1
