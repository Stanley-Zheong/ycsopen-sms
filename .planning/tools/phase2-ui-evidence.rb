#!/usr/bin/env ruby
# frozen_string_literal: true

# Shared fail-closed validation for Phase 2 UI evidence. The case runner and the
# phase-level validator use the same implementation so their decisions cannot
# drift.
require "digest"
require "base64"
require "json"
require "pathname"

module Phase2UiEvidence
  PHASE = "02-console-design-system-prototype-foundation"
  OWNER = "console-design-system-prototype-foundation"
  PHASE_NAME = "02"
  EVIDENCE_SCHEMA = "phase02-ui-obligation-evidence-v1"
  MANIFEST_SCHEMA = "phase02-ui-evidence-manifest-v1"
  VISUAL_OBLIGATIONS = %w[
    OBL-DESIGN-SYSTEM-001
    OBL-DESIGN-SYSTEM-002
    OBL-DESIGN-SYSTEM-004
  ].freeze
  SOURCE_PATHS = [
    ".planning/phases/#{PHASE}/TEST-MATRIX.md",
    ".planning/phases/#{PHASE}/UI-ELEMENTS.md",
    ".planning/phases/#{PHASE}/design-output/prototype.spec.ts",
    ".planning/phases/#{PHASE}/design-output/playwright.config.ts",
    ".planning/phases/#{PHASE}/design-output/prototype.html",
    ".planning/phases/#{PHASE}/design-output/console-design.pen",
    ".planning/phases/#{PHASE}/design-output/tokens.css",
    ".planning/phases/#{PHASE}/design-output/ycsan-style-snapshot.json",
    ".planning/phases/#{PHASE}/EVIDENCE/ycsan-reference-1440x900.png"
  ].freeze

  module_function

  def parse_catalog(root)
    records = {}
    File.foreach(File.join(root, ".planning/PRD-OBLIGATIONS.md")) do |line|
      next unless line.start_with?("- OBL-")

      cells = line.delete_prefix("- ").split("|").map(&:strip)
      next unless cells[3] == OWNER

      records[cells[0]] = {
        "obligation_id" => cells[0],
        "requirement_ids" => cells[2].scan(/(?:REQ-[A-Z0-9-]+|PROJECT-[A-Z0-9-]+)/),
        "behavior_id" => cells[4],
        "ui_reference" => cells[5],
        "catalog_test" => cells[6],
        "evidence_path" => cells[7]
      }
    end
    records
  end

  def parse_matrix(root)
    path = File.join(root, ".planning/phases/#{PHASE}/TEST-MATRIX.md")
    rows = []
    File.foreach(path) do |line|
      next unless line.start_with?("| OBL-")

      cells = line.split("|")[1..-2].map(&:strip)
      route = cells[5][/\/(?:[A-Za-z0-9._~!$&'()*+,;=:@%-]+\/?)+/]
      page_id = cells[5].start_with?("/") ? route.delete_prefix("/").tr("/", "-") : cells[5].split.first
      rows << {
        "obligation_id" => cells[0],
        "requirement_ids" => cells[1].scan(/(?:REQ-[A-Z0-9-]+|PROJECT-[A-Z0-9-]+)/),
        "behavior_id" => cells[2],
        "catalog_test" => cells[3],
        "playwright_id" => cells[4],
        "page_id" => page_id,
        "route" => route,
        "test_id" => cells[6],
        "case_id" => cells[7],
        "evidence_path" => cells[10]
      }
    end
    rows
  end

  def report_specs(suites, output = [])
    suites.each do |suite|
      output.concat(suite.fetch("specs", []))
      report_specs(suite.fetch("suites", []), output)
    end
    output
  end

  def report_steps(steps, output = [])
    steps.each do |step|
      output << step
      report_steps(step.fetch("steps", []), output)
    end
    output
  end

  # Evidence assertions come only from completed Playwright test.step records.
  # This prevents the producer from claiming a granular PASS when the matching
  # browser assertion was removed from the executable test.
  def evidence_assertions(result)
    steps = report_steps(result.fetch("steps", [])).select { |step| step.fetch("title", "").start_with?("evidence:") }
    ids = steps.map { |step| step.fetch("title").delete_prefix("evidence:") }
    raise "PHASE2_REPORT_EVIDENCE_STEP_MISSING" if ids.empty?
    unique!(ids, "PHASE2_REPORT_EVIDENCE_STEP")
    failed = steps.select { |step| step.key?("error") && step["error"] }
    raise "PHASE2_REPORT_EVIDENCE_STEP_FAILED: #{failed.map { |step| step['title'] }.join(',')}" unless failed.empty?

    ids.map { |id| { "id" => id, "status" => "PASS" } }
  end

  def digest(path)
    Digest::SHA256.file(path).hexdigest
  end

  def real_file!(root, relative, label)
    candidate = File.expand_path(relative, root)
    raise "#{label}_OUTSIDE_ROOT: #{relative}" unless candidate.start_with?("#{root}/")
    raise "#{label}_MISSING: #{relative}" unless File.file?(candidate)

    real = File.realpath(candidate)
    raise "#{label}_REALPATH_ESCAPE: #{relative}" unless real.start_with?("#{File.realpath(root)}/")

    current = Pathname.new(candidate)
    until current.to_s == root
      raise "#{label}_SYMLINK_REJECTED: #{relative}" if File.symlink?(current)
      current = current.parent
    end
    real
  end

  def unique!(values, label)
    duplicates = values.group_by(&:itself).select { |_key, items| items.length > 1 }.keys
    raise "#{label}_DUPLICATE: #{duplicates.join(',')}" unless duplicates.empty?
  end

  def validate!(root:)
    root = File.expand_path(root)
    phase_dir = File.join(root, ".planning/phases/#{PHASE}")
    evidence_dir = File.join(phase_dir, "EVIDENCE")
    catalog = parse_catalog(root)
    rows = parse_matrix(root)
    raise "PHASE2_CATALOG_COUNT_MISMATCH: #{catalog.length}" unless catalog.length == 83
    raise "PHASE2_MATRIX_COUNT_MISMATCH: #{rows.length}" unless rows.length == 83

    %w[obligation_id playwright_id test_id case_id].each do |key|
      unique!(rows.map { |row| row.fetch(key) }, "PHASE2_MATRIX_#{key.upcase}")
    end
    rows.each do |row|
      record = catalog.fetch(row.fetch("obligation_id"))
      %w[requirement_ids behavior_id catalog_test evidence_path].each do |key|
        raise "PHASE2_MATRIX_CATALOG_MISMATCH: #{row['obligation_id']} #{key}" unless row[key] == record[key]
      end
      raise "PHASE2_MATRIX_ROUTE_MISSING: #{row['obligation_id']}" unless row["route"]
    end

    html_path = real_file!(root, SOURCE_PATHS[4], "PHASE2_SOURCE")
    spec_path = real_file!(root, SOURCE_PATHS[2], "PHASE2_SOURCE")
    html = File.read(html_path)
    spec = File.read(spec_path)
    raise "PHASE2_HTML_SYNTHETIC_MARKER_PRESENT" if html.match?(/placeholder|TODO|TBD|FIXME/i)
    raise "PHASE2_HTML_REAL_SOURCE_MARKER_MISSING" unless html.include?('data-render-source="prototype-html"')
    raise "PHASE2_SPEC_SYNTHETIC_ROUTING_PRESENT" if spec.match?(/page\.route\s*\(|route\.fulfill|PROTOTYPE_FIXTURE/)
    raise "PHASE2_SPEC_CHECKED_IN_HTML_MISSING" unless spec.match?(/readFile\([^\n)]*prototypePath[^\n)]*\)/)

    manifest_path = File.join(evidence_dir, "evidence-manifest.json")
    manifest = JSON.parse(File.read(real_file!(root, manifest_path.delete_prefix("#{root}/"), "PHASE2_MANIFEST")))
    raise "PHASE2_MANIFEST_SCHEMA_MISMATCH" unless manifest["schema_version"] == MANIFEST_SCHEMA
    raise "PHASE2_MANIFEST_STATUS_NOT_PASS" unless manifest["status"] == "PASS"
    raise "PHASE2_MANIFEST_PHASE_MISMATCH" unless manifest["phase"] == PHASE
    raise "PHASE2_MANIFEST_OWNER_MISMATCH" unless manifest["owner"] == OWNER

    report_info = manifest.fetch("run")
    report_relative = report_info.fetch("report_path")
    report_path = real_file!(root, report_relative, "PHASE2_REPORT")
    raise "PHASE2_REPORT_OUTSIDE_EVIDENCE" unless report_path.start_with?("#{File.realpath(evidence_dir)}/")
    report_sha = digest(report_path)
    raise "PHASE2_REPORT_CHECKSUM_MISMATCH" unless report_sha == report_info.fetch("report_sha256")
    report = JSON.parse(File.read(report_path))
    stats = report.fetch("stats")
    raise "PHASE2_REPORT_EXPECTED_MISMATCH" unless stats["expected"] == 83
    %w[skipped unexpected flaky].each do |key|
      raise "PHASE2_REPORT_#{key.upcase}_PRESENT" unless stats[key] == 0
    end
    specs = report_specs(report.fetch("suites"))
    raise "PHASE2_REPORT_SPEC_COUNT_MISMATCH: #{specs.length}" unless specs.length == 83
    unique!(specs.map { |item| item.fetch("title") }, "PHASE2_REPORT_TITLE")
    expected_titles = rows.map { |row| "#{row['playwright_id']} #{row['case_id']} #{row['obligation_id']}" }.sort
    raise "PHASE2_REPORT_TITLE_SET_MISMATCH" unless specs.map { |item| item.fetch("title") }.sort == expected_titles
    specs.each do |item|
      result = item.fetch("tests").first.fetch("results").last
      raise "PHASE2_REPORT_TEST_NOT_PASS: #{item['title']}" unless item["ok"] == true && result["status"] == "passed"
      evidence_assertions(result)
    end
    specs_by_title = specs.to_h { |item| [item.fetch("title"), item] }

    expected_source_sha = SOURCE_PATHS.to_h do |relative|
      [relative, digest(real_file!(root, relative, "PHASE2_SOURCE"))]
    end
    metadata = report.dig("config", "metadata")
    raise "PHASE2_REPORT_METADATA_MISSING" unless metadata.is_a?(Hash)
    raise "PHASE2_REPORT_PHASE_MISMATCH" unless metadata["phase"] == PHASE
    raise "PHASE2_REPORT_BROWSER_MISMATCH" unless metadata["browser"] == "Google Chrome"
    raise "PHASE2_REPORT_EXECUTABLE_MISMATCH" unless metadata["executablePath"] == "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
    raise "PHASE2_REPORT_VIEWPORT_MISMATCH" unless metadata["viewport"] == { "width" => 1440, "height" => 900 }
    raise "PHASE2_REPORT_SOURCE_SEAL_MISMATCH" unless metadata["sourceSha256"] == expected_source_sha

    source_entries = manifest.fetch("sources")
    unique!(source_entries.map { |item| item.fetch("path") }, "PHASE2_SOURCE_PATH")
    raise "PHASE2_SOURCE_SET_MISMATCH" unless source_entries.map { |item| item.fetch("path") }.sort == SOURCE_PATHS.sort
    source_entries.each do |item|
      source_path = real_file!(root, item.fetch("path"), "PHASE2_SOURCE")
      raise "PHASE2_SOURCE_CHECKSUM_MISMATCH: #{item['path']}" unless digest(source_path) == item.fetch("sha256")
    end

    tested_inputs_path = File.join(evidence_dir, "tested-inputs.json")
    tested_inputs = JSON.parse(File.read(real_file!(root, tested_inputs_path.delete_prefix("#{root}/"), "PHASE2_TESTED_INPUTS")))
    raise "PHASE2_TESTED_INPUTS_SCHEMA_MISMATCH" unless tested_inputs["schema_version"] == "phase02-tested-inputs-v1"
    raise "PHASE2_TESTED_INPUTS_PHASE_MISMATCH" unless tested_inputs["phase"] == PHASE && tested_inputs["owner"] == OWNER
    raise "PHASE2_TESTED_INPUTS_REPORT_MISMATCH" unless tested_inputs["report"] == { "path" => report_relative, "sha256" => report_sha }
    raise "PHASE2_TESTED_INPUTS_SOURCE_MISMATCH" unless tested_inputs["sources"] == source_entries
    obligation_schema_path = File.join(evidence_dir, "schema/phase02-ui-obligation-evidence.schema.json")
    manifest_schema_path = File.join(evidence_dir, "schema/phase02-ui-evidence-manifest.schema.json")
    obligation_schema = JSON.parse(File.read(real_file!(root, obligation_schema_path.delete_prefix("#{root}/"), "PHASE2_OBLIGATION_SCHEMA")))
    manifest_schema = JSON.parse(File.read(real_file!(root, manifest_schema_path.delete_prefix("#{root}/"), "PHASE2_MANIFEST_SCHEMA")))
    raise "PHASE2_OBLIGATION_SCHEMA_VERSION_MISMATCH" unless obligation_schema.dig("properties", "schema_version", "const") == EVIDENCE_SCHEMA
    raise "PHASE2_OBLIGATION_SCHEMA_SOURCE_COUNT_MISMATCH" unless obligation_schema.dig("properties", "sources", "minItems") == SOURCE_PATHS.length && obligation_schema.dig("properties", "sources", "maxItems") == SOURCE_PATHS.length
    raise "PHASE2_MANIFEST_SCHEMA_VERSION_MISMATCH" unless manifest_schema.dig("properties", "schema_version", "const") == MANIFEST_SCHEMA
    raise "PHASE2_MANIFEST_SCHEMA_SOURCE_COUNT_MISMATCH" unless manifest_schema.dig("properties", "sources", "minItems") == SOURCE_PATHS.length && manifest_schema.dig("properties", "sources", "maxItems") == SOURCE_PATHS.length
    raise "PHASE2_MANIFEST_SCHEMA_ENTRY_COUNT_MISMATCH" unless manifest_schema.dig("properties", "entries", "minItems") == 83 && manifest_schema.dig("properties", "entries", "maxItems") == 83

    entries = manifest.fetch("entries")
    raise "PHASE2_ENTRY_COUNT_MISMATCH: #{entries.length}" unless entries.length == 83
    unique!(entries.map { |item| item.fetch("obligation_id") }, "PHASE2_ENTRY_OBLIGATION")
    unique!(entries.map { |item| item.fetch("path") }, "PHASE2_ENTRY_PATH")
    raise "PHASE2_ENTRY_SET_MISMATCH" unless entries.map { |item| item.fetch("obligation_id") }.sort == catalog.keys.sort

    by_obligation = entries.to_h { |item| [item.fetch("obligation_id"), item] }
    rows.each do |row|
      entry = by_obligation.fetch(row.fetch("obligation_id"))
      target = File.join(".planning/phases", PHASE, row.fetch("evidence_path"))
      raise "PHASE2_ENTRY_TARGET_MISMATCH: #{row['obligation_id']}" unless entry["path"] == target
      target_path = real_file!(root, target, "PHASE2_TARGET")
      raise "PHASE2_ENTRY_CHECKSUM_MISMATCH: #{row['obligation_id']}" unless digest(target_path) == entry["sha256"]
      raise "PHASE2_ENTRY_SIZE_MISMATCH: #{row['obligation_id']}" unless File.size(target_path) == entry["size"]
      raise "PHASE2_ENTRY_STATUS_NOT_PASS: #{row['obligation_id']}" unless entry["status"] == "PASS"
      raise "PHASE2_ENTRY_REPORT_MISMATCH: #{row['obligation_id']}" unless entry["report_sha256"] == report_sha

      if VISUAL_OBLIGATIONS.include?(row["obligation_id"])
        raise "PHASE2_PNG_MEDIA_TYPE_MISMATCH: #{row['obligation_id']}" unless entry["media_type"] == "image/png"
        raise "PHASE2_PNG_MAGIC_MISMATCH: #{row['obligation_id']}" unless File.binread(target_path, 8) == "\x89PNG\r\n\x1A\n".b
        title = "#{row['playwright_id']} #{row['case_id']} #{row['obligation_id']}"
        result = specs_by_title.fetch(title).fetch("tests").first.fetch("results").last
        expected_name = File.basename(target_path)
        attachments = result.fetch("attachments", []).select { |attachment| attachment["name"] == expected_name && attachment["contentType"] == "image/png" }
        raise "PHASE2_PNG_ATTACHMENT_COUNT_MISMATCH: #{row['obligation_id']}" unless attachments.length == 1
        body = attachments.first["body"]
        raise "PHASE2_PNG_ATTACHMENT_BODY_MISSING: #{row['obligation_id']}" unless body.is_a?(String) && !body.empty?
        attached_sha = Digest::SHA256.hexdigest(Base64.strict_decode64(body))
        raise "PHASE2_PNG_ATTACHMENT_CHECKSUM_MISMATCH: #{row['obligation_id']}" unless attached_sha == digest(target_path)
      else
        payload = JSON.parse(File.read(target_path))
        expected_keys = %w[assertions behavior_id case_id catalog_test evidence_path exit_code obligation_id owner page_id phase playwright_id requirement_ids route run schema_version sources status test_id]
        raise "PHASE2_JSON_KEYS_MISMATCH: #{row['obligation_id']}" unless payload.keys.sort == expected_keys.sort
        raise "PHASE2_JSON_SCHEMA_MISMATCH: #{row['obligation_id']}" unless payload["schema_version"] == EVIDENCE_SCHEMA
        raise "PHASE2_JSON_STATUS_NOT_PASS: #{row['obligation_id']}" unless payload["status"] == "PASS" && payload["exit_code"] == 0
        %w[obligation_id behavior_id case_id catalog_test evidence_path page_id playwright_id route test_id].each do |key|
          raise "PHASE2_JSON_MATRIX_MISMATCH: #{row['obligation_id']} #{key}" unless payload[key] == row[key]
        end
        raise "PHASE2_JSON_REQUIREMENTS_MISMATCH: #{row['obligation_id']}" unless payload["requirement_ids"] == row["requirement_ids"]
        raise "PHASE2_JSON_REPORT_MISMATCH: #{row['obligation_id']}" unless payload.dig("run", "report_sha256") == report_sha
        title = "#{row['playwright_id']} #{row['case_id']} #{row['obligation_id']}"
        result = specs_by_title.fetch(title).fetch("tests").first.fetch("results").last
        report_assertions = evidence_assertions(result)
        raise "PHASE2_JSON_ASSERTIONS_REPORT_MISMATCH: #{row['obligation_id']}" unless payload["assertions"] == report_assertions
        raise "PHASE2_JSON_SOURCE_SET_MISMATCH: #{row['obligation_id']}" unless payload["sources"] == source_entries
      end
    end

    { rows: rows, catalog: catalog, manifest: manifest, report: report, specs: specs }
  rescue JSON::ParserError, KeyError => e
    raise "PHASE2_EVIDENCE_MALFORMED: #{e.message}"
  end
end
