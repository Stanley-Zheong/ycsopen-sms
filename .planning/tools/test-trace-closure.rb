#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "open3"
require "optparse"
require "tmpdir"

VALIDATOR = File.expand_path("validate-trace-closure.rb", __dir__)
OWNER = "engineering-verification-foundation"
PRODUCT_OWNER = "final-release-acceptance"
OWNED = %w[
  OBL-FOUND-TRACE-001
  OBL-FOUND-TRACE-002
  OBL-FOUND-TRACE-003
  OBL-FOUND-TRACE-004
  OBL-FOUND-UI-DRIFT-001
  OBL-FOUND-UI-DRIFT-002
  OBL-NFR-BROWSER
].freeze
PRODUCT_COMPATIBILITY = %w[OBL-NFR-CHINESE OBL-NFR-TIMEZONE].freeze

options = {}
OptionParser.new do |parser|
  parser.banner = "Usage: ruby .planning/tools/test-trace-closure.rb [--case CASE-ID]"
  parser.on("--case ID") { |value| options[:case] = value }
end.parse!

unless [nil, "CASE-FOUND-TRACE-001", "CASE-FOUND-TRACE-002"].include?(options[:case])
  warn "OPTION_CASE_UNKNOWN: #{options[:case]}"
  exit 2
end

def write(path, content)
  FileUtils.mkdir_p(File.dirname(path))
  File.write(path, content)
end

def catalog_line(id, requirement, owner, behavior, test_id, evidence)
  "- #{id} | synthetic source | #{requirement} | #{owner} | #{behavior} | - | #{test_id}:static | #{evidence} | Synthetic obligation #{id}."
end

def create_fixture(root)
  phase_dir = File.join(root, ".planning/phases/01-engineering-verification-foundation")
  requirements = <<~MARKDOWN
    # Requirements

    | Requirement | Required outcome | Owning phase |
    | --- | --- | --- |
    | REQ-NFR-COMPATIBILITY | Compatibility contract. | Phase 1 and Phase 56 |
  MARKDOWN
  catalog = [
    "# PRD Atomic Obligation Catalog",
    "",
    "- PROJECT-PLANNING-TRACE: planning trace authority.",
    "- PROJECT-UI-CONTRACT: UI contract authority.",
    ""
  ]
  OWNED.each_with_index do |id, index|
    requirement = id == "OBL-NFR-BROWSER" ? "REQ-NFR-COMPATIBILITY" : (id.include?("UI-DRIFT") ? "PROJECT-UI-CONTRACT" : "PROJECT-PLANNING-TRACE")
    behavior = if id.include?("UI-DRIFT")
                 "engineering-verification-foundation-05"
               elsif id == "OBL-NFR-BROWSER"
                 "engineering-verification-foundation-03"
               else
                 format("engineering-verification-foundation-%02d", index + 1)
               end
    catalog << catalog_line(id, requirement, OWNER, behavior, "T-#{id.delete_prefix('OBL-')}", "EVIDENCE/#{id}.json")
  end
  PRODUCT_COMPATIBILITY.each_with_index do |id, index|
    catalog << catalog_line(
      id,
      "REQ-NFR-COMPATIBILITY",
      PRODUCT_OWNER,
      format("final-release-acceptance-%02d", index + 1),
      "T-#{id.delete_prefix('OBL-')}",
      "EVIDENCE/#{id}.json"
    )
  end

  spec_rows = OWNED.each_with_index.map do |id, index|
    record = catalog.find { |line| line.start_with?("- #{id} |") }.split(" | ")
    "| #{id} | #{record[2]} | #{record[4]} | engineering-verification-foundation-V#{format('%02d', index + 1)} |"
  end
  spec = <<~MARKDOWN
    # Synthetic phase spec

    ## Requirement and obligation trace

    | Obligation ID | Requirement | Behavior IDs | Verification IDs |
    | --- | --- | --- | --- |
    #{spec_rows.join("\n")}

    <!-- OBL-COMMENT-ONLY must not count as a structured reverse edge. -->
  MARKDOWN

  todo = <<~MARKDOWN
    # TODO

    #{OWNED.map { |id| "- [ ] #{id} — Evidence: not recorded." }.join("\n")}

    <!-- - [ ] OBL-COMMENT-ONLY -->
  MARKDOWN

  matrix_rows = OWNED.each_with_index.map do |id, index|
    record = catalog.find { |line| line.start_with?("- #{id} |") }.split(" | ")
    "| #{id} | #{record[2]} | #{record[4]} | #{record[6]} | not-applicable | not-applicable | not-applicable | CASE-#{index + 1} | Synthetic case | `ruby synthetic.rb` | #{record[7]} |"
  end
  matrix = <<~MARKDOWN
    # Test Matrix

    | Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
    | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
    #{matrix_rows.join("\n")}

    <!-- | OBL-COMMENT-ONLY | fake | fake | fake | fake | fake | fake | fake | fake | fake | fake | -->
  MARKDOWN

  plan = <<~MARKDOWN
    ---
    phase: 01-engineering-verification-foundation
    plan: 99
    obligations:
    #{OWNED.map { |id| "  - #{id}" }.join("\n")}
    ---

    <tasks>
    <task type="auto">
      <name>Synthetic complete task</name>
      <files>synthetic.rb</files>
      <action>Exercise structured trace closure without using OBL-COMMENT-ONLY.</action>
      <verify><automated>ruby synthetic.rb</automated></verify>
      <done>All exact edges pass.</done>
    </task>
    </tasks>
  MARKDOWN

  write(File.join(root, ".planning/REQUIREMENTS.md"), requirements)
  write(File.join(root, ".planning/PRD-OBLIGATIONS.md"), catalog.join("\n") + "\n")
  write(File.join(phase_dir, "01-SPEC.md"), spec)
  write(File.join(phase_dir, "TODO.md"), todo)
  write(File.join(phase_dir, "TEST-MATRIX.md"), matrix)
  write(File.join(phase_dir, "01-99-PLAN.md"), plan)
  FileUtils.mkdir_p(File.join(phase_dir, "EVIDENCE"))
  phase_dir
end

def run_validator(root, expected_success:, expected_token:)
  command = [
    RbConfig.ruby,
    VALIDATOR,
    "--root", root,
    "--phase", "01",
    "--package", OWNER
  ]
  stdout, stderr, status = Open3.capture3(*command, chdir: root)
  output = stdout + stderr
  unless status.success? == expected_success
    abort "TRACE_TEST_STATUS_MISMATCH expected_success=#{expected_success} token=#{expected_token}\n#{output}"
  end
  abort "TRACE_TEST_TOKEN_MISSING token=#{expected_token}\n#{output}" unless output.include?(expected_token)
  output
end

def mutate(root, name)
  copy = File.join(root, "mutations", name)
  FileUtils.mkdir_p(File.dirname(copy))
  FileUtils.cp_r(File.join(root, "baseline"), copy)
  yield copy
  copy
end

Dir.mktmpdir("phase01-trace-closure-") do |workspace|
  baseline = File.join(workspace, "baseline")
  create_fixture(baseline)
  run_validator(baseline, expected_success: true, expected_token: "TRACE_CLOSURE PASS")

  mutations = []
  if [nil, "CASE-FOUND-TRACE-001"].include?(options[:case])
    mutations.concat([
      ["wrong-field-count", "CATALOG_BAD_FIELDS", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub(" | Synthetic obligation OBL-FOUND-TRACE-001.", ""))
      end],
      ["unknown-requirement", "CATALOG_UNKNOWN_REQUIREMENT", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub("PROJECT-PLANNING-TRACE", "PROJECT-UNKNOWN"))
      end],
      ["unknown-owner", "CATALOG_OWNER_OUTSIDE_PHASE", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub("engineering-verification-foundation | engineering-verification-foundation-01", "foreign-owner | foreign-owner-01"))
      end],
      ["missing-behavior", "CATALOG_VALUE_MISSING", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub("engineering-verification-foundation-01", ""))
      end],
      ["missing-test", "CATALOG_VALUE_MISSING", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub("T-FOUND-TRACE-001:static", ""))
      end],
      ["missing-evidence", "CATALOG_VALUE_MISSING", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub("EVIDENCE/OBL-FOUND-TRACE-001.json", ""))
      end]
    ])
  end

  if [nil, "CASE-FOUND-TRACE-002"].include?(options[:case])
    mutations.concat([
      ["duplicate-obligation", "CATALOG_DUPLICATE_OBLIGATION", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        line = File.readlines(path).find { |value| value.start_with?("- OBL-FOUND-TRACE-001 |") }
        File.open(path, "a") { |file| file.write(line) }
      end],
      ["duplicate-test", "CATALOG_DUPLICATE_TEST", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub("T-FOUND-TRACE-002:static", "T-FOUND-TRACE-001:static"))
      end],
      ["duplicate-evidence", "CATALOG_DUPLICATE_EVIDENCE", lambda do |root|
        path = File.join(root, ".planning/PRD-OBLIGATIONS.md")
        File.write(path, File.read(path).sub("EVIDENCE/OBL-FOUND-TRACE-002.json", "EVIDENCE/OBL-FOUND-TRACE-001.json"))
      end],
      ["catalog-to-spec-orphan", "TRACE_CATALOG_TO_SPEC_ORPHAN", lambda do |root|
        path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-SPEC.md")
        File.write(path, File.readlines(path).reject { |line| line.start_with?("| OBL-FOUND-TRACE-001 |") }.join)
      end],
      ["spec-to-catalog-orphan", "TRACE_SPEC_TO_CATALOG_ORPHAN", lambda do |root|
        path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-SPEC.md")
        marker = "| OBL-NFR-BROWSER | REQ-NFR-COMPATIBILITY | engineering-verification-foundation-03 | engineering-verification-foundation-V07 |"
        File.write(path, File.read(path).sub(marker, "#{marker}\n| OBL-FOREIGN | PROJECT-PLANNING-TRACE | foreign-01 | foreign-V01 |"))
      end],
      ["catalog-to-todo-orphan", "TRACE_CATALOG_TO_TODO_ORPHAN", lambda do |root|
        path = File.join(root, ".planning/phases/01-engineering-verification-foundation/TODO.md")
        File.write(path, File.readlines(path).reject { |line| line.start_with?("- [ ] OBL-FOUND-TRACE-002") }.join)
      end],
      ["duplicate-todo-owner", "TRACE_TODO_DUPLICATE_OBLIGATION", lambda do |root|
        path = File.join(root, ".planning/phases/01-engineering-verification-foundation/TODO.md")
        File.open(path, "a") { |file| file.puts "- [ ] OBL-FOUND-TRACE-002 — duplicate" }
      end],
      ["matrix-test-mismatch", "TRACE_MATRIX_TEST_MISMATCH", lambda do |root|
        path = File.join(root, ".planning/phases/01-engineering-verification-foundation/TEST-MATRIX.md")
        File.write(path, File.read(path).sub("T-FOUND-TRACE-002:static", "T-WRONG:static"))
      end],
      ["comment-only-fake-reference", "TRACE_CATALOG_TO_PLAN_ORPHAN", lambda do |root|
        path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-99-PLAN.md")
        body = File.read(path).sub("  - OBL-FOUND-TRACE-001\n", "")
        body << "\n<!-- obligations: [OBL-FOUND-TRACE-001] -->\n"
        File.write(path, body)
      end]
    ])
  end

  mutations.each do |name, token, change|
    fixture = mutate(workspace, name, &change)
    run_validator(fixture, expected_success: false, expected_token: token)
  end

  puts "TRACE_CLOSURE_TEST PASS cases=#{mutations.length} filter=#{options[:case] || 'all'}"
end
