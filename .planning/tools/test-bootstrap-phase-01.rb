#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"
require_relative "phase01-chrome-entry-contract"

def assert(condition, message)
  abort(message) unless condition
end

def run_bootstrap(bootstrap, phase_dir, catalog)
  Open3.capture3(RbConfig.ruby, bootstrap, "--phase-dir", phase_dir, "--catalog", catalog)
end

def write_review(path, verdict: "PASS", rows: nil, executor: "phase1-plan00-executor", reviewer: "independent-entry-reviewer")
  rows ||= Phase01ChromeEntryContract::REVIEW_CRITERIA.map do |criterion_id|
    [criterion_id, "PASS", "fixture evidence for #{criterion_id}", "independent fixture inspection for #{criterion_id}"]
  end
  table = rows.map { |cells| "| #{cells.join(' | ')} |" }.join("\n")
  File.write(path, <<~MARKDOWN)
    # Phase 1 Entry Review

    Executor identity: `#{executor}`
    Reviewer identity: `#{reviewer}`

    | Criterion ID | Verdict | Evidence | Command or inspection rule |
    | --- | --- | --- | --- |
    #{table}

    ## Verdict

    #{verdict}
  MARKDOWN
  File.chmod(0o644, path)
end

def write_phase_fixture(phase_dir, owned_ids, evidence)
  FileUtils.mkdir_p(File.join(phase_dir, "EVIDENCE"))
  trace_body = owned_ids.join("\n") + "\n"
  %w[01-SPEC.md TODO.md TEST-MATRIX.md].each { |name| File.write(File.join(phase_dir, name), trace_body) }
  %w[INTENT.md DESIGN.md ITERATIONS.md DECISIONS.md].each { |name| File.write(File.join(phase_dir, name), "# #{name.delete_suffix('.md')}\n") }
  13.times do |index|
    plan = format("01-%02d-PLAN.md", index)
    entry_flag = index.zero? ? "entry_remediation: true\n" : ""
    File.write(
      File.join(phase_dir, plan),
      "---\n#{entry_flag}---\n<task type=\"auto\"><files>x</files><action>x</action><verify>x</verify><done>x</done></task>\n"
    )
  end
  evidence_path = File.join(phase_dir, "EVIDENCE", "local-chrome-entry.json")
  File.write(evidence_path, JSON.pretty_generate(evidence) + "\n")
  File.chmod(0o644, evidence_path)
  write_review(File.join(phase_dir, "ENTRY-REVIEW.md"))
end

planning_root = File.expand_path("..", __dir__)
catalog = File.join(planning_root, "PRD-OBLIGATIONS.md")
bootstrap = File.join(__dir__, "bootstrap-phase-01.rb")
producer = File.join(__dir__, "produce-phase-01-chrome-entry.rb")
records = File.readlines(catalog, chomp: true).filter_map do |line|
  next unless line.start_with?("- OBL-")
  fields = line.delete_prefix("- ").split(" | ", -1)
  abort "invalid catalog row: #{line}" unless fields.length == 9
  fields
end
owned_ids = records.select { |fields| fields[3] == "engineering-verification-foundation" }.map(&:first).sort
foreign_id = records.find { |fields| fields[3] != "engineering-verification-foundation" }&.first
assert(owned_ids.length == 7, "expected seven Phase 1 obligations, got #{owned_ids.length}")
assert(foreign_id, "missing foreign obligation fixture")

Dir.mktmpdir("phase01-real-entry-fixture-") do |tmpdir|
  evidence_path = File.join(tmpdir, "entry.json")
  stdout, stderr, status = Open3.capture3(RbConfig.ruby, producer, "--output", evidence_path)
  assert(status.success?, "could not create baseline local Chrome evidence:\n#{stdout}#{stderr}")
  baseline_evidence = JSON.parse(File.read(evidence_path))

  Dir.mktmpdir("phase01-bootstrap-test-") do |fixture_root|
    phase_dir = File.join(fixture_root, Phase01ChromeEntryContract::PHASE)
    write_phase_fixture(phase_dir, owned_ids, baseline_evidence)
    output, error, result = run_bootstrap(bootstrap, phase_dir, catalog)
    assert(result.success? && output.include?("PHASE_01_BOOTSTRAP PASS") && output.include?("plans=13"), "positive bootstrap fixture failed:\n#{output}#{error}")

    mutations = 0
    reset = -> { FileUtils.rm_rf(phase_dir); write_phase_fixture(phase_dir, owned_ids, baseline_evidence) }
    mutate = lambda do |label, diagnostic, &block|
      reset.call
      block.call
      _out, err, status = run_bootstrap(bootstrap, phase_dir, catalog)
      assert(!status.success?, "#{label} unexpectedly passed")
      assert(err.include?(diagnostic), "#{label} lacked #{diagnostic}:\n#{err}")
      mutations += 1
    end

    evidence_file = -> { File.join(phase_dir, "EVIDENCE", "local-chrome-entry.json") }
    review_file = -> { File.join(phase_dir, "ENTRY-REVIEW.md") }

    mutate.call("missing entry evidence", "LOCAL_CHROME_ENTRY_MISSING") { FileUtils.rm_f(evidence_file.call) }
    mutate.call("entry evidence symlink", "LOCAL_CHROME_ENTRY_UNSAFE") do
      target = File.join(fixture_root, "entry-target.json")
      FileUtils.rm_f(target)
      FileUtils.mv(evidence_file.call, target)
      File.symlink(target, evidence_file.call)
    end
    mutate.call("entry evidence mode", "LOCAL_CHROME_ENTRY_MODE_INVALID") { File.chmod(0o600, evidence_file.call) }
    mutate.call("legacy entry field", "FORBIDDEN_FIELD") do
      doc = JSON.parse(File.read(evidence_file.call)); doc["admission"] = { "sha256" => "a" * 64 }; File.write(evidence_file.call, JSON.pretty_generate(doc) + "\n")
    end
    mutate.call("wrong Chrome brand", "VERSION_BRAND_INVALID") do
      doc = JSON.parse(File.read(evidence_file.call)); doc["chrome"]["version_output"] = "Chromium #{doc['chrome']['full_version']}"; File.write(evidence_file.call, JSON.pretty_generate(doc) + "\n")
    end
    mutate.call("stale installed version", "LIVE_VERSION_MISMATCH") do
      doc = JSON.parse(File.read(evidence_file.call)); doc["chrome"]["version_output"] = "Google Chrome 999.1.2.3"; doc["chrome"]["full_version"] = "999.1.2.3"; doc["chrome"]["major"] = 999; File.write(evidence_file.call, JSON.pretty_generate(doc) + "\n")
    end
    mutate.call("missing plan 00", "PLAN_00_MISSING") { FileUtils.rm_f(File.join(phase_dir, "01-00-PLAN.md")) }
    mutate.call("wrong plan count", "PLAN_COUNT_INVALID") { File.write(File.join(phase_dir, "01-13-PLAN.md"), "<task><files>x</files><action>x</action><verify>x</verify><done>x</done></task>\n") }
    mutate.call("non-sole entry remediation", "ENTRY_REMEDIATION_PLAN_SET_INVALID") do
      path = File.join(phase_dir, "01-01-PLAN.md"); File.write(path, File.read(path).sub("---\n", "---\nentry_remediation: true\n"))
    end
    mutate.call("duplicate criterion", "ENTRY_REVIEW_DUPLICATE_CRITERION") do
      rows = Phase01ChromeEntryContract::REVIEW_CRITERIA.map { |id| [id, "PASS", "evidence", "command"] }; rows << rows.first.dup; write_review(review_file.call, rows: rows)
    end
    mutate.call("empty criterion", "ENTRY_REVIEW_EMPTY_CRITERION") do
      rows = Phase01ChromeEntryContract::REVIEW_CRITERIA.map { |id| [id, "PASS", "evidence", "command"] }; rows[0][0] = ""; write_review(review_file.call, rows: rows)
    end
    mutate.call("missing criterion", "ENTRY_REVIEW_CRITERIA_SET_INVALID") do
      rows = Phase01ChromeEntryContract::REVIEW_CRITERIA.drop(1).map { |id| [id, "PASS", "evidence", "command"] }; write_review(review_file.call, rows: rows)
    end
    mutate.call("BLOCKER row", "ENTRY_REVIEW_CONTAINS_BLOCKER") do
      rows = Phase01ChromeEntryContract::REVIEW_CRITERIA.map { |id| [id, id == Phase01ChromeEntryContract::REVIEW_CRITERIA.first ? "BLOCKER" : "PASS", "evidence", "command"] }; write_review(review_file.call, rows: rows)
    end
    mutate.call("final verdict", "ENTRY_REVIEW_FINAL_VERDICT_INVALID") { write_review(review_file.call, verdict: "BLOCKED") }
    mutate.call("executor self approval", "ENTRY_REVIEW_SELF_APPROVAL") { write_review(review_file.call, executor: "same-agent", reviewer: "same-agent") }
    mutate.call("malformed table", "ENTRY_REVIEW_TABLE_HEADER_INVALID") do
      File.write(review_file.call, File.read(review_file.call).sub("| Criterion ID | Verdict | Evidence | Command or inspection rule |", "| Criterion ID | Verdict | Evidence |"))
    end
    mutate.call("foreign obligation", "FOREIGN_OBLIGATION_ID") do
      %w[01-SPEC.md TODO.md TEST-MATRIX.md].each { |name| File.open(File.join(phase_dir, name), "a") { |file| file.puts(foreign_id) } }
    end

    puts "bootstrap_self_test=PASS owned_obligations=#{owned_ids.length} plans=13 mutations=#{mutations}"
  end
end
