#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "open3"
require "tmpdir"

planning_root = File.expand_path("..", __dir__)
catalog = File.join(planning_root, "PRD-OBLIGATIONS.md")
bootstrap = File.join(__dir__, "bootstrap-phase-01.rb")

records = File.readlines(catalog, chomp: true).filter_map do |line|
  next unless line.start_with?("- OBL-")

  fields = line.delete_prefix("- ").split(" | ", -1)
  abort "invalid catalog fixture row: #{line}" unless fields.length == 9

  fields
end
owned_ids = records.select { |fields| fields[3] == "engineering-verification-foundation" }.map(&:first).sort
foreign_id = records.find { |fields| fields[3] != "engineering-verification-foundation" }&.first
abort "missing owned or foreign fixture IDs" if owned_ids.empty? || foreign_id.nil?

Dir.mktmpdir("phase-01-bootstrap-test-") do |tmpdir|
  phase_dir = File.join(tmpdir, "01-engineering-verification-foundation")
  evidence_dir = File.join(phase_dir, "EVIDENCE")
  FileUtils.mkdir_p(evidence_dir)

  trace_body = owned_ids.join("\n") + "\n"
  File.write(File.join(phase_dir, "01-SPEC.md"), trace_body)
  File.write(File.join(phase_dir, "TODO.md"), trace_body)
  File.write(File.join(phase_dir, "TEST-MATRIX.md"), trace_body)
  %w[INTENT.md DESIGN.md ITERATIONS.md DECISIONS.md].each do |name|
    File.write(File.join(phase_dir, name), "# #{name.delete_suffix('.md')}\n")
  end
  File.write(
    File.join(phase_dir, "ENTRY-REVIEW.md"),
    <<~MARKDOWN
      # Entry Review

      | Criterion ID | Verdict | Evidence | Command or inspection rule |
      | --- | --- | --- | --- |
      | ENTRY-01 | PASS | EVIDENCE/bootstrap.json | ruby bootstrap-phase-01.rb |

      ## Verdict

      PASS
    MARKDOWN
  )
  File.write(
    File.join(phase_dir, "01-01-PLAN.md"),
    <<~PLAN
      <task type="auto">
        <files>.planning/tools/validate-phase-entry.rb</files>
        <action>Implement the Phase 1 validator contract.</action>
        <verify>Run the focused validator tests.</verify>
        <done>The validator returns deterministic evidence.</done>
      </task>
    PLAN
  )

  positive_output, positive_error, positive_status = Open3.capture3(
    RbConfig.ruby,
    bootstrap,
    "--phase-dir",
    phase_dir,
    "--catalog",
    catalog
  )
  unless positive_status.success? && positive_output.include?("PHASE_01_BOOTSTRAP PASS")
    abort "positive fixture failed:\n#{positive_output}#{positive_error}"
  end

  %w[01-SPEC.md TODO.md TEST-MATRIX.md].each do |name|
    File.open(File.join(phase_dir, name), "a") { |file| file.puts(foreign_id) }
  end
  _negative_output, negative_error, negative_status = Open3.capture3(
    RbConfig.ruby,
    bootstrap,
    "--phase-dir",
    phase_dir,
    "--catalog",
    catalog
  )
  abort "foreign fixture unexpectedly passed" if negative_status.success?
  abort "foreign fixture did not identify #{foreign_id}" unless negative_error.include?(foreign_id)
  %w[01-SPEC.md TODO.md TEST-MATRIX.md].each do |name|
    abort "foreign fixture did not reject #{name}" unless negative_error.include?(name)
  end
  abort "foreign fixture did not use FOREIGN_OBLIGATION_ID" unless negative_error.include?("FOREIGN_OBLIGATION_ID")

  puts "bootstrap_self_test=PASS owned_obligations=#{owned_ids.length} foreign_rejection=#{foreign_id} trace_files=3"
end
