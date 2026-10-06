#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "optparse"
require "set"
require_relative "planning-validator-support"

options = {}
OptionParser.new do |parser|
  parser.banner = "Usage: ruby .planning/tools/validate-change-ui-contract.rb --change CHANGE-ID"
  parser.on("--change ID") { |value| options[:change] = value }
end.parse!

errors = []
change = options[:change]
unless change&.match?(/\Aissue-[0-9]+-[a-z0-9]+(?:-[a-z0-9]+)*\z/)
  errors << "OPTION_CHANGE_REQUIRED: expected issue-NNN-kebab-case"
end

if errors.empty?
  root = Dir.pwd
  change_dir = File.join(root, ".planning/changes", change)
  ui_elements_path = File.join(change_dir, "UI-ELEMENTS.md")
  test_matrix_path = File.join(change_dir, "TEST-MATRIX.md")
  inventory_path = File.join(change_dir, "EVIDENCE/ui-contract.json")
  errors << "UI_CHANGE_DIRECTORY_MISSING: #{change_dir}" unless File.directory?(change_dir)

  ui_rows = PlanningValidatorSupport.markdown_table(
    ui_elements_path,
    PlanningValidatorSupport::UI_HEADERS,
    errors,
    "change_ui_elements"
  )
  matrix_rows = PlanningValidatorSupport.markdown_table(
    test_matrix_path,
    PlanningValidatorSupport::UI_TEST_MATRIX_HEADERS,
    errors,
    "change_ui_test_matrix"
  )

  routes = ui_rows.flat_map do |row|
    row.fetch(0).scan(%r{/[A-Za-z0-9._~!$&'()*+,;=:@%/-]+})
  end.uniq
  ui_obligations = ui_rows.flat_map { |row| row.fetch(8).scan(/\bOBL-ISSUE-[A-Z0-9-]+\b/) }
  matrix_obligations = matrix_rows.map(&:first)
  matrix_playwright_ids = matrix_rows.map { |row| row.fetch(4) }
  matrix_selectors = matrix_rows.map { |row| row.fetch(6) }.uniq
  case_ids = matrix_rows.map { |row| row.fetch(7) }

  ui_rows.each_with_index do |row, index|
    row.each_with_index do |value, column|
      if PlanningValidatorSupport.placeholder?(value)
        errors << "CHANGE_UI_ELEMENT_PLACEHOLDER: row=#{index + 1} column=#{PlanningValidatorSupport::UI_HEADERS[column]}"
      end
    end
    PlanningValidatorSupport.comma_tokens(row.fetch(7)).each do |selector|
      unless selector.match?(PlanningValidatorSupport::ELEMENT_TEST_ID)
        errors << "CHANGE_UI_TEST_ID_BAD_FORMAT: row=#{index + 1} value=#{selector}"
      end
    end
  end

  matrix_rows.each_with_index do |row, index|
    row.each_with_index do |value, column|
      if PlanningValidatorSupport.placeholder?(value)
        errors << "CHANGE_UI_MATRIX_PLACEHOLDER: row=#{index + 1} column=#{PlanningValidatorSupport::UI_TEST_MATRIX_HEADERS[column]}"
      end
    end
    obligation, _requirements, _behavior, _catalog_test, playwright_id,
      page_cell, selector, case_id, _description, _command, _evidence = row
    errors << "CHANGE_UI_MATRIX_OBLIGATION_BAD: row=#{index + 1} value=#{obligation}" unless obligation.match?(/\AOBL-ISSUE-[A-Z0-9-]+\z/)
    errors << "CHANGE_UI_MATRIX_PLAYWRIGHT_ID_BAD: row=#{index + 1} value=#{playwright_id}" unless playwright_id.match?(/\Apw-[a-z0-9]+(?:-[a-z0-9]+)*\z/)
    errors << "CHANGE_UI_MATRIX_CASE_ID_BAD: row=#{index + 1} value=#{case_id}" unless case_id.match?(/\AC-ISSUE-[A-Z0-9-]+\z/)
    matching_ui = ui_rows.select do |ui_row|
      ui_row.fetch(8).scan(/\bOBL-ISSUE-[A-Z0-9-]+\b/).include?(obligation) &&
        ui_row.fetch(0) == page_cell &&
        PlanningValidatorSupport.comma_tokens(ui_row.fetch(7)).include?(selector) &&
        PlanningValidatorSupport.comma_tokens(ui_row.fetch(11)).include?(playwright_id)
    end
    errors << "CHANGE_UI_MATRIX_LINK_MISSING: obligation=#{obligation}" if matching_ui.empty?
  end

  {
    "obligation" => matrix_obligations,
    "playwright" => matrix_playwright_ids,
    "case" => case_ids
  }.each do |label, values|
    duplicates = PlanningValidatorSupport.duplicates(values)
    errors << "CHANGE_UI_MATRIX_DUPLICATE_#{label.upcase}: #{duplicates.join(',')}" unless duplicates.empty?
  end
  missing_obligations = matrix_obligations - ui_obligations
  stale_obligations = ui_obligations.uniq - matrix_obligations
  errors << "CHANGE_UI_OBLIGATION_LINK_MISSING: #{missing_obligations.join(',')}" unless missing_obligations.empty?
  errors << "CHANGE_UI_OBLIGATION_MATRIX_MISSING: #{stale_obligations.join(',')}" unless stale_obligations.empty?

  inventory = begin
    JSON.parse(PlanningValidatorSupport.read(inventory_path, errors, "change ui contract inventory"))
  rescue JSON::ParserError => error
    errors << "CHANGE_UI_INVENTORY_JSON_BAD: #{error.message}"
    {}
  end
  errors << "CHANGE_UI_MODE_MISMATCH" unless inventory["mode"] == "production"
  errors << "CHANGE_UI_SCOPE_MISMATCH" unless inventory["scope"] == "change-package-addendum"

  manifest = inventory["manifest"].is_a?(Hash) ? inventory["manifest"] : {}
  errors << "CHANGE_UI_INVENTORY_SECTION_MISSING: manifest" if manifest.empty?
  manifest_routes = PlanningValidatorSupport.exact_json_set(manifest, "routes", routes, errors, "change_manifest")
  manifest_test_ids = PlanningValidatorSupport.exact_json_set(manifest, "test_ids", matrix_selectors, errors, "change_manifest")
  manifest_sources = PlanningValidatorSupport.source_paths(root, manifest, errors, "change_manifest")
  PlanningValidatorSupport.validate_source_contains(
    manifest_sources,
    manifest_routes + manifest_test_ids + matrix_obligations + case_ids,
    errors,
    "change_manifest"
  )

  implementation = inventory["implementation"].is_a?(Hash) ? inventory["implementation"] : {}
  errors << "CHANGE_UI_INVENTORY_SECTION_MISSING: implementation" if implementation.empty?
  implementation_routes = PlanningValidatorSupport.exact_json_set(
    implementation, "routes", routes, errors, "change_implementation"
  )
  implementation_test_ids = PlanningValidatorSupport.exact_json_set(
    implementation, "test_ids", matrix_selectors, errors, "change_implementation"
  )
  implementation_sources = PlanningValidatorSupport.source_paths(
    root, implementation, errors, "change_implementation"
  )
  PlanningValidatorSupport.validate_production_implementation_sources(
    root, implementation_sources, implementation_routes, implementation_test_ids, errors
  )

  playwright = inventory["playwright"].is_a?(Hash) ? inventory["playwright"] : {}
  errors << "CHANGE_UI_INVENTORY_SECTION_MISSING: playwright" if playwright.empty?
  playwright_test_ids = PlanningValidatorSupport.exact_json_set(
    playwright, "test_ids", matrix_selectors, errors, "change_playwright"
  )
  errors << "CHANGE_UI_PLAYWRIGHT_EVIDENCE_KIND_MISMATCH" unless playwright["evidence_kind"] == "production"
  playwright_sources = PlanningValidatorSupport.source_paths(root, playwright, errors, "change_playwright")
  PlanningValidatorSupport.validate_playwright_sources(
    root, playwright_sources, playwright_test_ids, errors, production: true
  )

  links = matrix_rows.map do |row|
    route = row.fetch(5)[%r{/[A-Za-z0-9._~!$&'()*+,;=:@%/-]+}]
    {
      obligation_id: row.fetch(0), playwright_id: row.fetch(4), route: route,
      selector: row.fetch(6), case_id: row.fetch(7)
    }
  end
  PlanningValidatorSupport.validate_playwright_matrix_blocks(
    playwright_sources, links, errors, label: "change-production"
  )
  PlanningValidatorSupport.validate_production_execution(
    root, change_dir, inventory["execution"], case_ids.sort, errors
  )

  if errors.empty?
    puts "change_ui_contract=PASS change=#{change} selectors=#{matrix_selectors.length} routes=#{routes.length} cases=#{case_ids.length}"
    exit 0
  end
end

warn "change_ui_contract=BLOCKED errors=#{errors.length}"
errors.each { |error| warn "- #{error}" }
exit 1
