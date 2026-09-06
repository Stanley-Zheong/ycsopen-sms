#!/usr/bin/env ruby
# frozen_string_literal: true

# Converts one successful real-Chrome Playwright report into the catalog's 83
# exact evidence targets. PASS is never accepted as user input; it is derived
# from the canonical reporter result and current source digests.
require "digest"
require "base64"
require "fileutils"
require "json"
require "open3"

require_relative "phase2-ui-evidence"

root = File.expand_path("../..", __dir__)
phase_dir = File.join(root, ".planning/phases", Phase2UiEvidence::PHASE)
evidence_dir = File.join(phase_dir, "EVIDENCE")
report_relative = ".planning/phases/#{Phase2UiEvidence::PHASE}/EVIDENCE/runs/phase02-latest/playwright-report.json"
report_path = File.join(root, report_relative)
abort("PHASE2_PRODUCER_REPORT_MISSING") unless File.file?(report_path)

rows = Phase2UiEvidence.parse_matrix(root)
catalog = Phase2UiEvidence.parse_catalog(root)
abort("PHASE2_PRODUCER_MATRIX_COUNT_MISMATCH") unless rows.length == 83 && catalog.length == 83
rows.each do |row|
  record = catalog.fetch(row.fetch("obligation_id"))
  %w[requirement_ids behavior_id catalog_test evidence_path].each do |key|
    abort("PHASE2_PRODUCER_CATALOG_MISMATCH: #{row['obligation_id']} #{key}") unless row[key] == record[key]
  end
end

html = File.read(File.join(phase_dir, "design-output/prototype.html"))
spec_source = File.read(File.join(phase_dir, "design-output/prototype.spec.ts"))
abort("PHASE2_PRODUCER_HTML_NOT_REAL") if html.match?(/placeholder|TODO|TBD|FIXME/i) || !html.include?('data-render-source="prototype-html"')
abort("PHASE2_PRODUCER_SYNTHETIC_TEST_SOURCE") if spec_source.match?(/page\.route\s*\(|route\.fulfill|PROTOTYPE_FIXTURE/)

report = JSON.parse(File.read(report_path))
stats = report.fetch("stats")
abort("PHASE2_PRODUCER_REPORT_NOT_GREEN") unless stats["expected"] == 83 && %w[skipped unexpected flaky].all? { |key| stats[key] == 0 }
specs = Phase2UiEvidence.report_specs(report.fetch("suites"))
expected_titles = rows.map { |row| "#{row['playwright_id']} #{row['case_id']} #{row['obligation_id']}" }
abort("PHASE2_PRODUCER_REPORT_SET_MISMATCH") unless specs.length == 83 && specs.map { |item| item.fetch("title") }.sort == expected_titles.sort
specs.each do |item|
  result = item.fetch("tests").first.fetch("results").last
  abort("PHASE2_PRODUCER_TEST_NOT_PASS: #{item['title']}") unless item["ok"] == true && result["status"] == "passed"
end
specs_by_title = specs.to_h { |item| [item.fetch("title"), item] }

expected_source_sha = Phase2UiEvidence::SOURCE_PATHS.to_h do |relative|
  absolute = File.join(root, relative)
  abort("PHASE2_PRODUCER_SOURCE_MISSING: #{relative}") unless File.file?(absolute)
  [relative, Phase2UiEvidence.digest(absolute)]
end
metadata = report.dig("config", "metadata")
abort("PHASE2_PRODUCER_REPORT_METADATA_MISSING") unless metadata.is_a?(Hash)
abort("PHASE2_PRODUCER_REPORT_PHASE_MISMATCH") unless metadata["phase"] == Phase2UiEvidence::PHASE
abort("PHASE2_PRODUCER_REPORT_BROWSER_MISMATCH") unless metadata["browser"] == "Google Chrome"
abort("PHASE2_PRODUCER_REPORT_EXECUTABLE_MISMATCH") unless metadata["executablePath"] == "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
abort("PHASE2_PRODUCER_REPORT_VIEWPORT_MISMATCH") unless metadata["viewport"] == { "width" => 1440, "height" => 900 }
abort("PHASE2_PRODUCER_REPORT_SOURCE_SEAL_MISMATCH") unless metadata["sourceSha256"] == expected_source_sha

chrome_path = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
chrome_version_output, chrome_status = Open3.capture2(chrome_path, "--version")
abort("PHASE2_PRODUCER_CHROME_UNAVAILABLE") unless chrome_status.success?
chrome_version = chrome_version_output[/\d+(?:\.\d+){3}/]
abort("PHASE2_PRODUCER_CHROME_VERSION_UNKNOWN") unless chrome_version

report_sha = Phase2UiEvidence.digest(report_path)
sources = Phase2UiEvidence::SOURCE_PATHS.map do |relative|
  absolute = File.join(root, relative)
  abort("PHASE2_PRODUCER_SOURCE_MISSING: #{relative}") unless File.file?(absolute)
  { "path" => relative, "sha256" => Phase2UiEvidence.digest(absolute) }
end
rows.select { |row| Phase2UiEvidence::VISUAL_OBLIGATIONS.include?(row["obligation_id"]) }.each do |row|
  title = "#{row['playwright_id']} #{row['case_id']} #{row['obligation_id']}"
  result = specs_by_title.fetch(title).fetch("tests").first.fetch("results").last
  target_path = File.join(phase_dir, row.fetch("evidence_path"))
  expected_name = File.basename(target_path)
  attachments = result.fetch("attachments", []).select { |attachment| attachment["name"] == expected_name && attachment["contentType"] == "image/png" }
  abort("PHASE2_PRODUCER_PNG_ATTACHMENT_COUNT_MISMATCH: #{row['obligation_id']}") unless attachments.length == 1
  body = attachments.first["body"]
  abort("PHASE2_PRODUCER_PNG_ATTACHMENT_BODY_MISSING: #{row['obligation_id']}") unless body.is_a?(String) && !body.empty?
  attached_sha = Digest::SHA256.hexdigest(Base64.strict_decode64(body))
  abort("PHASE2_PRODUCER_PNG_ATTACHMENT_CHECKSUM_MISMATCH: #{row['obligation_id']}") unless File.file?(target_path) && attached_sha == Phase2UiEvidence.digest(target_path)
end
run = {
  "report_path" => report_relative,
  "report_sha256" => report_sha,
  "started_at" => stats.fetch("startTime"),
  "duration_ms" => stats.fetch("duration"),
  "browser" => "Google Chrome",
  "browser_version" => chrome_version,
  "executable_path" => chrome_path,
  "viewport" => { "width" => 1440, "height" => 900 },
  "expected" => 83,
  "passed" => 83,
  "failed" => 0,
  "skipped" => 0,
  "flaky" => 0
}

FileUtils.mkdir_p(File.join(evidence_dir, "schema"))
tested_inputs = {
  "schema_version" => "phase02-tested-inputs-v1",
  "phase" => Phase2UiEvidence::PHASE,
  "owner" => Phase2UiEvidence::OWNER,
  "report" => { "path" => report_relative, "sha256" => report_sha },
  "sources" => sources
}
File.write(File.join(evidence_dir, "tested-inputs.json"), JSON.pretty_generate(tested_inputs) + "\n")

rows.each do |row|
  next if Phase2UiEvidence::VISUAL_OBLIGATIONS.include?(row["obligation_id"])

  payload = {
    "schema_version" => Phase2UiEvidence::EVIDENCE_SCHEMA,
    "phase" => Phase2UiEvidence::PHASE,
    "owner" => Phase2UiEvidence::OWNER,
    "obligation_id" => row["obligation_id"],
    "requirement_ids" => row["requirement_ids"],
    "behavior_id" => row["behavior_id"],
    "catalog_test" => row["catalog_test"],
    "case_id" => row["case_id"],
    "playwright_id" => row["playwright_id"],
    "evidence_path" => row["evidence_path"],
    "route" => row["route"],
    "page_id" => row["page_id"],
    "test_id" => row["test_id"],
    "status" => "PASS",
    "exit_code" => 0,
    "assertions" => Phase2UiEvidence.evidence_assertions(
      specs_by_title.fetch("#{row['playwright_id']} #{row['case_id']} #{row['obligation_id']}").fetch("tests").first.fetch("results").last
    ),
    "run" => run,
    "sources" => sources
  }
  File.write(File.join(phase_dir, row.fetch("evidence_path")), JSON.pretty_generate(payload) + "\n")
end

entries = rows.map do |row|
  relative = File.join(".planning/phases", Phase2UiEvidence::PHASE, row.fetch("evidence_path"))
  absolute = File.join(root, relative)
  abort("PHASE2_PRODUCER_TARGET_MISSING: #{row['obligation_id']}") unless File.file?(absolute)
  if Phase2UiEvidence::VISUAL_OBLIGATIONS.include?(row["obligation_id"])
    abort("PHASE2_PRODUCER_PNG_INVALID: #{row['obligation_id']}") unless File.binread(absolute, 8) == "\x89PNG\r\n\x1A\n".b
  end
  {
    "obligation_id" => row["obligation_id"],
    "path" => relative,
    "media_type" => relative.end_with?(".png") ? "image/png" : "application/json",
    "sha256" => Phase2UiEvidence.digest(absolute),
    "size" => File.size(absolute),
    "status" => "PASS",
    "case_id" => row["case_id"],
    "playwright_id" => row["playwright_id"],
    "behavior_id" => row["behavior_id"],
    "catalog_test" => row["catalog_test"],
    "report_path" => report_relative,
    "report_sha256" => report_sha
  }
end

manifest = {
  "schema_version" => Phase2UiEvidence::MANIFEST_SCHEMA,
  "phase" => Phase2UiEvidence::PHASE,
  "owner" => Phase2UiEvidence::OWNER,
  "status" => "PASS",
  "generated_from" => "real checked-in HTML served to installed Google Chrome",
  "run" => run,
  "sources" => sources,
  "entries" => entries
}
File.write(File.join(evidence_dir, "evidence-manifest.json"), JSON.pretty_generate(manifest) + "\n")

source_schema = {
  "type" => "object",
  "required" => %w[path sha256],
  "properties" => {
    "path" => { "type" => "string", "minLength" => 1 },
    "sha256" => { "type" => "string", "pattern" => "^[0-9a-f]{64}$" }
  },
  "additionalProperties" => false
}
run_schema = {
  "type" => "object",
  "required" => %w[report_path report_sha256 started_at duration_ms browser browser_version executable_path viewport expected passed failed skipped flaky],
  "properties" => {
    "report_path" => { "type" => "string", "minLength" => 1 },
    "report_sha256" => { "type" => "string", "pattern" => "^[0-9a-f]{64}$" },
    "started_at" => { "type" => "string", "minLength" => 1 },
    "duration_ms" => { "type" => "number", "minimum" => 0 },
    "browser" => { "const" => "Google Chrome" },
    "browser_version" => { "type" => "string", "pattern" => "^[0-9]+(?:\\.[0-9]+){3}$" },
    "executable_path" => { "const" => chrome_path },
    "viewport" => {
      "type" => "object",
      "required" => %w[width height],
      "properties" => { "width" => { "const" => 1440 }, "height" => { "const" => 900 } },
      "additionalProperties" => false
    },
    "expected" => { "const" => 83 }, "passed" => { "const" => 83 },
    "failed" => { "const" => 0 }, "skipped" => { "const" => 0 }, "flaky" => { "const" => 0 }
  },
  "additionalProperties" => false
}
obligation_schema = {
  "$schema" => "https://json-schema.org/draft/2020-12/schema",
  "title" => "Phase 2 UI obligation evidence",
  "type" => "object",
  "required" => %w[schema_version phase owner obligation_id requirement_ids behavior_id catalog_test case_id playwright_id evidence_path route page_id test_id status exit_code assertions run sources],
  "properties" => {
    "schema_version" => { "const" => Phase2UiEvidence::EVIDENCE_SCHEMA },
    "phase" => { "const" => Phase2UiEvidence::PHASE },
    "owner" => { "const" => Phase2UiEvidence::OWNER },
    "obligation_id" => { "type" => "string", "pattern" => "^OBL-[A-Z0-9-]+$" },
    "requirement_ids" => { "type" => "array", "minItems" => 1, "uniqueItems" => true, "items" => { "type" => "string" } },
    "behavior_id" => { "type" => "string", "minLength" => 1 },
    "catalog_test" => { "type" => "string", "minLength" => 1 },
    "case_id" => { "type" => "string", "pattern" => "^C-OBL-[A-Z0-9-]+$" },
    "playwright_id" => { "type" => "string", "pattern" => "^pw-" },
    "evidence_path" => { "type" => "string", "pattern" => "^EVIDENCE/OBL-.*\\.json$" },
    "route" => { "type" => "string", "pattern" => "^/" },
    "page_id" => { "type" => "string", "minLength" => 1 },
    "test_id" => { "type" => "string", "minLength" => 1 },
    "status" => { "const" => "PASS" }, "exit_code" => { "const" => 0 },
    "assertions" => {
      "type" => "array", "minItems" => 1,
      "items" => {
        "type" => "object", "required" => %w[id status],
        "properties" => { "id" => { "type" => "string", "minLength" => 1 }, "status" => { "const" => "PASS" } },
        "additionalProperties" => false
      }
    },
    "run" => run_schema,
    "sources" => { "type" => "array", "minItems" => Phase2UiEvidence::SOURCE_PATHS.length, "maxItems" => Phase2UiEvidence::SOURCE_PATHS.length, "items" => source_schema }
  },
  "additionalProperties" => false
}
manifest_schema = {
  "$schema" => "https://json-schema.org/draft/2020-12/schema",
  "title" => "Phase 2 UI evidence manifest",
  "type" => "object",
  "required" => %w[schema_version phase owner status generated_from run sources entries],
  "properties" => {
    "schema_version" => { "const" => Phase2UiEvidence::MANIFEST_SCHEMA },
    "phase" => { "const" => Phase2UiEvidence::PHASE },
    "owner" => { "const" => Phase2UiEvidence::OWNER },
    "status" => { "const" => "PASS" },
    "generated_from" => { "type" => "string", "minLength" => 1 },
    "run" => run_schema,
    "sources" => { "type" => "array", "minItems" => Phase2UiEvidence::SOURCE_PATHS.length, "maxItems" => Phase2UiEvidence::SOURCE_PATHS.length, "items" => source_schema },
    "entries" => {
      "type" => "array", "minItems" => 83, "maxItems" => 83,
      "items" => {
        "type" => "object",
        "required" => %w[obligation_id path media_type sha256 size status case_id playwright_id behavior_id catalog_test report_path report_sha256],
        "properties" => {
          "obligation_id" => { "type" => "string", "pattern" => "^OBL-[A-Z0-9-]+$" },
          "path" => { "type" => "string", "minLength" => 1 },
          "media_type" => { "enum" => ["application/json", "image/png"] },
          "sha256" => { "type" => "string", "pattern" => "^[0-9a-f]{64}$" },
          "size" => { "type" => "integer", "minimum" => 1 }, "status" => { "const" => "PASS" },
          "case_id" => { "type" => "string" }, "playwright_id" => { "type" => "string" },
          "behavior_id" => { "type" => "string" }, "catalog_test" => { "type" => "string" },
          "report_path" => { "type" => "string" },
          "report_sha256" => { "type" => "string", "pattern" => "^[0-9a-f]{64}$" }
        },
        "additionalProperties" => false
      }
    }
  },
  "additionalProperties" => false
}
File.write(File.join(evidence_dir, "schema/phase02-ui-obligation-evidence.schema.json"), JSON.pretty_generate(obligation_schema) + "\n")
File.write(File.join(evidence_dir, "schema/phase02-ui-evidence-manifest.schema.json"), JSON.pretty_generate(manifest_schema) + "\n")

puts "phase02_evidence_producer=PASS targets=83 json=80 png=3 browser=Google_Chrome version=#{chrome_version}"
