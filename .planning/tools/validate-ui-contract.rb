#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "optparse"
require "set"
require_relative "planning-validator-support"

include PlanningValidatorSupport

options = {}
OptionParser.new do |parser|
  parser.banner = "Usage: ruby .planning/tools/validate-ui-contract.rb --phase NN --package ID --stage design|production"
  parser.on("--phase NN") { |value| options[:phase] = value }
  parser.on("--package ID") { |value| options[:package] = value }
  parser.on("--stage STAGE") { |value| options[:stage] = value }
end.parse!

errors = []
unless options[:phase]&.match?(/\A\d+\z/)
  errors << "OPTION_PHASE_REQUIRED: expected numeric --phase"
end
unless options[:package]&.match?(/\A[a-z0-9]+(?:-[a-z0-9]+)*\z/)
  errors << "OPTION_PACKAGE_REQUIRED: expected kebab-case --package"
end
unless %w[design production].include?(options[:stage])
  errors << "OPTION_STAGE_REQUIRED: expected --stage design|production"
end

if errors.empty?
  root = Dir.pwd
  phase_number = options[:phase].to_i
  phase_token = format("%02d", phase_number)
  package = options[:package]
  stage = options[:stage]
  phase_dir = File.join(root, ".planning/phases/#{phase_token}-#{package}")
  roadmap_path = File.join(root, ".planning/ROADMAP.md")
  catalog_path = File.join(root, ".planning/PRD-OBLIGATIONS.md")

  phases = PlanningValidatorSupport.roadmap_packages(roadmap_path, errors)
  expected_package = phases[phase_number]
  errors << "ROADMAP_PHASE_PACKAGE_MISMATCH: phase=#{phase_number} expected=#{expected_package || '-'} actual=#{package}" unless expected_package == package
  errors << "PHASE_DIRECTORY_MISSING: #{phase_dir}" unless File.directory?(phase_dir)

  ui_elements_path = File.join(phase_dir, "UI-ELEMENTS.md")
  ui_spec_path = File.join(phase_dir, "#{phase_token}-UI-SPEC.md")
  test_matrix_path = File.join(phase_dir, "TEST-MATRIX.md")
  inventory_path = File.join(phase_dir, "EVIDENCE/ui-contract.json")
  [ui_spec_path, test_matrix_path].each do |path|
    if !File.file?(path)
      errors << "UI_ARTIFACT_MISSING: #{path}"
    elsif File.zero?(path)
      errors << "UI_ARTIFACT_EMPTY: #{path}"
    end
  end
  pen_files = File.directory?(phase_dir) ? Dir.glob(File.join(phase_dir, "**/*.pen")) : []
  html_files = File.directory?(phase_dir) ? Dir.glob(File.join(phase_dir, "**/*.html")) : []
  errors << "UI_PENCIL_SOURCE_MISSING: expected at least one .pen under #{phase_dir}" if pen_files.empty?
  errors << "UI_HTML_PROTOTYPE_MISSING: expected at least one .html under #{phase_dir}" if html_files.empty?

  rows = PlanningValidatorSupport.markdown_table(
    ui_elements_path,
    PlanningValidatorSupport::UI_HEADERS,
    errors,
    "ui_elements"
  )
  selectors = []
  page_cells = []
  routes = []
  ui_rows = []
  rows.each_with_index do |row, index|
    page_cell = row[0]
    selector = row[7]
    link_cell = row[8]
    behavior_cell = row[9]
    catalog_test_cell = row[10]
    playwright_cell = row[11]
    playwright_ids = PlanningValidatorSupport.comma_tokens(playwright_cell)
    row.each_with_index do |value, column|
      if PlanningValidatorSupport.placeholder?(value)
        errors << "UI_ELEMENT_PLACEHOLDER: row=#{index + 1} column=#{PlanningValidatorSupport::UI_HEADERS[column]} value=#{value.inspect}"
      end
    end
    page_cells << page_cell
    route = page_cell[/\/(?:[A-Za-z0-9._~!$&'()*+,;=:@%-]+\/?)+/]
    errors << "UI_ROUTE_MISSING: row=#{index + 1} page=#{page_cell}" if route.nil?
    routes << route if route
    unless selector.match?(PlanningValidatorSupport::ELEMENT_TEST_ID)
      errors << "UI_TEST_ID_BAD_FORMAT: row=#{index + 1} value=#{selector}"
    end
    errors << "UI_PLAYWRIGHT_ID_MISSING: row=#{index + 1} selector=#{selector}" if PlanningValidatorSupport.placeholder?(playwright_cell)
    selectors << selector unless selector.empty?
    ui_rows << {
      index: index + 1,
      page: page_cell,
      selector: selector,
      obligation_ids: link_cell.scan(PlanningValidatorSupport::OBLIGATION_ID),
      requirement_ids: link_cell.scan(PlanningValidatorSupport::REQUIREMENT_ID),
      behavior_ids: PlanningValidatorSupport.comma_tokens(behavior_cell),
      catalog_tests: PlanningValidatorSupport.comma_tokens(catalog_test_cell),
      playwright_ids: playwright_ids
    }
    duplicate_obligation_links = PlanningValidatorSupport.duplicates(ui_rows.last[:obligation_ids])
    duplicate_requirement_links = PlanningValidatorSupport.duplicates(ui_rows.last[:requirement_ids])
    duplicate_behavior_links = PlanningValidatorSupport.duplicates(ui_rows.last[:behavior_ids])
    duplicate_test_links = PlanningValidatorSupport.duplicates(ui_rows.last[:catalog_tests])
    duplicate_playwright_links = PlanningValidatorSupport.duplicates(ui_rows.last[:playwright_ids])
    errors << "UI_ROW_DUPLICATE_OBLIGATION_LINK: row=#{index + 1} ids=#{duplicate_obligation_links.join(',')}" unless duplicate_obligation_links.empty?
    errors << "UI_ROW_DUPLICATE_REQUIREMENT_LINK: row=#{index + 1} ids=#{duplicate_requirement_links.join(',')}" unless duplicate_requirement_links.empty?
    errors << "UI_ROW_DUPLICATE_BEHAVIOR_LINK: row=#{index + 1} ids=#{duplicate_behavior_links.join(',')}" unless duplicate_behavior_links.empty?
    errors << "UI_ROW_DUPLICATE_CATALOG_TEST_LINK: row=#{index + 1} ids=#{duplicate_test_links.join(',')}" unless duplicate_test_links.empty?
    errors << "UI_ROW_DUPLICATE_PLAYWRIGHT_LINK: row=#{index + 1} ids=#{duplicate_playwright_links.join(',')}" unless duplicate_playwright_links.empty?
  end
  duplicate_selectors = PlanningValidatorSupport.duplicates(selectors)
  errors << "UI_TEST_ID_DUPLICATE: #{duplicate_selectors.join(',')}" unless duplicate_selectors.empty?
  selectors.uniq!
  routes.uniq!

  records = PlanningValidatorSupport.catalog_records(catalog_path, errors)
  owned_records = records.select { |record| record.owner == package }
  errors << "UI_OWNER_HAS_NO_OBLIGATIONS: package=#{package}" if owned_records.empty?
  direct_records = owned_records.select do |record|
    record.ui_reference.start_with?("element:") || record.ui_reference.start_with?("page:")
  end
  owned_elements = owned_records.filter_map do |record|
    record.ui_reference.delete_prefix("element:") if record.ui_reference.start_with?("element:")
  end.uniq
  owned_pages = owned_records.filter_map do |record|
    record.ui_reference.delete_prefix("page:") if record.ui_reference.start_with?("page:")
  end.uniq
  missing_elements = owned_elements - selectors
  errors << "UI_OWNED_SELECTOR_MISSING: #{missing_elements.join(',')}" unless missing_elements.empty?
  missing_pages = owned_pages.reject { |page_id| page_cells.any? { |cell| cell.include?(page_id) } }
  errors << "UI_OWNED_PAGE_MISSING: #{missing_pages.join(',')}" unless missing_pages.empty?

  direct_ids = direct_records.map(&:id).to_set
  ui_rows.each do |ui_row|
    linked_records = direct_records.select { |record| ui_row[:obligation_ids].include?(record.id) }
    errors << "UI_ROW_OBLIGATION_LINK_MISSING: row=#{ui_row[:index]}" if linked_records.empty?
    foreign_links = ui_row[:obligation_ids].to_set - direct_ids
    errors << "UI_ROW_FOREIGN_OBLIGATION_LINK: row=#{ui_row[:index]} ids=#{foreign_links.to_a.sort.join(',')}" unless foreign_links.empty?
    if ui_row[:obligation_ids].length != ui_row[:playwright_ids].length
      errors << "UI_ROW_PLAYWRIGHT_CARDINALITY_MISMATCH: row=#{ui_row[:index]} obligations=#{ui_row[:obligation_ids].length} playwright_ids=#{ui_row[:playwright_ids].length}"
    end
    next if linked_records.empty?

    expected_requirements = linked_records.flat_map(&:requirements).uniq.sort
    expected_behaviors = linked_records.map(&:behavior).uniq.sort
    expected_tests = linked_records.map(&:test_reference).uniq.sort
    errors << "UI_ROW_REQUIREMENT_LINK_MISMATCH: row=#{ui_row[:index]} expected=#{expected_requirements.join(',')} actual=#{ui_row[:requirement_ids].uniq.sort.join(',')}" unless ui_row[:requirement_ids].uniq.sort == expected_requirements
    errors << "UI_ROW_BEHAVIOR_LINK_MISMATCH: row=#{ui_row[:index]} expected=#{expected_behaviors.join(',')} actual=#{ui_row[:behavior_ids].uniq.sort.join(',')}" unless ui_row[:behavior_ids].uniq.sort == expected_behaviors
    errors << "UI_ROW_CATALOG_TEST_LINK_MISMATCH: row=#{ui_row[:index]} expected=#{expected_tests.join(',')} actual=#{ui_row[:catalog_tests].uniq.sort.join(',')}" unless ui_row[:catalog_tests].uniq.sort == expected_tests
  end
  duplicate_ui_playwright_ids = PlanningValidatorSupport.duplicates(ui_rows.flat_map { |ui_row| ui_row[:playwright_ids] })
  errors << "UI_PLAYWRIGHT_ID_DUPLICATE: #{duplicate_ui_playwright_ids.join(',')}" unless duplicate_ui_playwright_ids.empty?
  direct_records.each do |record|
    reference = record.ui_reference.split(":", 2).last
    candidates = ui_rows.select do |ui_row|
      record.ui_reference.start_with?("element:") ? ui_row[:selector] == reference : ui_row[:page].include?(reference)
    end
    linked = candidates.select { |ui_row| ui_row[:obligation_ids].include?(record.id) }
    errors << "UI_DIRECT_OBLIGATION_LINK_MISSING: obligation=#{record.id} reference=#{record.ui_reference}" if linked.empty?
    errors << "UI_DIRECT_OBLIGATION_LINK_DUPLICATE: obligation=#{record.id} rows=#{linked.map { |ui_row| ui_row[:index] }.join(',')}" if linked.length > 1
  end

  test_matrix = File.file?(test_matrix_path) ? File.read(test_matrix_path) : ""
  matrix_rows = PlanningValidatorSupport.markdown_table(
    test_matrix_path,
    PlanningValidatorSupport::UI_TEST_MATRIX_HEADERS,
    errors,
    "ui_test_matrix"
  )
  matrix_by_obligation = Hash.new { |hash, key| hash[key] = [] }
  case_ids = []
  direct_playwright_ids = []
  direct_matrix_links = []
  matrix_rows.each_with_index do |row, index|
    row.each_with_index do |value, column|
      if PlanningValidatorSupport.placeholder?(value)
        errors << "UI_TEST_MATRIX_PLACEHOLDER: row=#{index + 1} column=#{PlanningValidatorSupport::UI_TEST_MATRIX_HEADERS[column]} value=#{value.inspect}"
      end
    end
    obligation_id, requirement_cell, behavior_id, catalog_test, playwright_id,
      page_cell, selector, case_id, _case_description, _command, _evidence = row
    unless obligation_id.match?(/\AOBL-[A-Z0-9-]+\z/)
      errors << "UI_TEST_MATRIX_BAD_OBLIGATION_ID: row=#{index + 1} value=#{obligation_id}"
      next
    end
    matrix_by_obligation[obligation_id] << { row: index + 1, values: row }
    case_ids << case_id
    record = owned_records.find { |candidate| candidate.id == obligation_id }
    if record.nil?
      errors << "UI_TEST_MATRIX_FOREIGN_OBLIGATION: row=#{index + 1} id=#{obligation_id}"
      next
    end
    actual_requirements = requirement_cell.scan(PlanningValidatorSupport::REQUIREMENT_ID).uniq.sort
    duplicate_requirements = PlanningValidatorSupport.duplicates(requirement_cell.scan(PlanningValidatorSupport::REQUIREMENT_ID))
    errors << "UI_TEST_MATRIX_DUPLICATE_REQUIREMENT: obligation=#{obligation_id} ids=#{duplicate_requirements.join(',')}" unless duplicate_requirements.empty?
    errors << "UI_TEST_MATRIX_REQUIREMENT_MISMATCH: obligation=#{obligation_id} expected=#{record.requirements.sort.join(',')} actual=#{actual_requirements.join(',')}" unless actual_requirements == record.requirements.sort
    errors << "UI_TEST_MATRIX_BEHAVIOR_MISMATCH: obligation=#{obligation_id} expected=#{record.behavior} actual=#{behavior_id}" unless behavior_id == record.behavior
    errors << "UI_TEST_MATRIX_CATALOG_TEST_MISMATCH: obligation=#{obligation_id} expected=#{record.test_reference} actual=#{catalog_test}" unless catalog_test == record.test_reference

    next unless direct_ids.include?(obligation_id)

    linked_ui_rows = ui_rows.select { |ui_row| ui_row[:obligation_ids].include?(obligation_id) }
    if linked_ui_rows.length == 1
      ui_row = linked_ui_rows.first
      obligation_position = ui_row[:obligation_ids].index(obligation_id)
      expected_playwright_id = obligation_position && ui_row[:playwright_ids][obligation_position]
      errors << "UI_TEST_MATRIX_PAGE_MISMATCH: obligation=#{obligation_id} expected=#{ui_row[:page]} actual=#{page_cell}" unless page_cell == ui_row[:page]
      errors << "UI_TEST_MATRIX_SELECTOR_MISMATCH: obligation=#{obligation_id} expected=#{ui_row[:selector]} actual=#{selector}" unless selector == ui_row[:selector]
      errors << "UI_TEST_MATRIX_PLAYWRIGHT_MISMATCH: obligation=#{obligation_id} expected=#{expected_playwright_id || '-'} actual=#{playwright_id}" unless playwright_id == expected_playwright_id
      route = page_cell[/\/(?:[A-Za-z0-9._~!$&'()*+,;=:@%-]+\/?)+/]
      if route
        direct_playwright_ids << playwright_id
        direct_matrix_links << {
          obligation_id: obligation_id,
          case_id: case_id,
          playwright_id: playwright_id,
          route: route,
          selector: selector
        }
      end
    end
  end
  duplicate_cases = PlanningValidatorSupport.duplicates(case_ids)
  errors << "UI_TEST_MATRIX_DUPLICATE_CASE_ID: #{duplicate_cases.join(',')}" unless duplicate_cases.empty?
  duplicate_direct_playwright_ids = PlanningValidatorSupport.duplicates(direct_playwright_ids)
  errors << "UI_TEST_MATRIX_DUPLICATE_PLAYWRIGHT_ID: #{duplicate_direct_playwright_ids.join(',')}" unless duplicate_direct_playwright_ids.empty?
  owned_records.each do |record|
    count = matrix_by_obligation[record.id].length
    errors << "UI_TEST_MATRIX_OWNED_MISSING: #{record.id}" if count.zero?
    errors << "UI_TEST_MATRIX_OWNED_DUPLICATE: obligation=#{record.id} rows=#{matrix_by_obligation[record.id].map { |entry| entry[:row] }.join(',')}" if count > 1
  end

  inventory = begin
    JSON.parse(PlanningValidatorSupport.read(inventory_path, errors, "ui contract inventory"))
  rescue JSON::ParserError => e
    errors << "UI_INVENTORY_JSON_BAD: #{e.message}"
    {}
  end
  expected_mode = phase_number == 2 && package == "console-design-system-prototype-foundation" ? "prototype" : "production"
  mode = inventory["mode"]
  errors << "UI_MODE_MISMATCH: expected=#{expected_mode} actual=#{mode || '-'}" unless mode == expected_mode

  manifest = inventory["manifest"]
  unless manifest.is_a?(Hash)
    errors << "UI_INVENTORY_SECTION_MISSING: manifest"
    manifest = {}
  end
  manifest_routes = PlanningValidatorSupport.exact_json_set(manifest, "routes", routes, errors, "manifest")
  manifest_test_ids = PlanningValidatorSupport.exact_json_set(manifest, "test_ids", selectors, errors, "manifest")
  manifest_sources = PlanningValidatorSupport.source_paths(root, manifest, errors, "manifest")
  PlanningValidatorSupport.validate_source_contains(manifest_sources, manifest_routes + manifest_test_ids, errors, "manifest")

  pencil = inventory["pencil"]
  unless pencil.is_a?(Hash)
    errors << "UI_INVENTORY_SECTION_MISSING: pencil"
    pencil = {}
  end
  pencil_paths = PlanningValidatorSupport.source_paths(root, pencil, errors, "pencil")
  errors << "UI_PENCIL_SOURCE_NOT_TRACED" unless (pencil_paths & pen_files).sort == pen_files.sort

  prototype = inventory["prototype"]
  unless prototype.is_a?(Hash)
    errors << "UI_INVENTORY_SECTION_MISSING: prototype"
    prototype = {}
  end
  prototype_routes = PlanningValidatorSupport.exact_json_set(prototype, "routes", routes, errors, "prototype")
  prototype_test_ids = PlanningValidatorSupport.exact_json_set(prototype, "test_ids", selectors, errors, "prototype")
  prototype_sources = PlanningValidatorSupport.source_paths(root, prototype, errors, "prototype")
  PlanningValidatorSupport.validate_source_contains(
    prototype_sources,
    prototype_routes + prototype_test_ids,
    errors,
    "prototype"
  )
  errors << "UI_HTML_PROTOTYPE_NOT_TRACED" if (prototype_sources & html_files).empty?

  prototype_playwright = inventory["prototype_playwright"]
  unless prototype_playwright.is_a?(Hash)
    errors << "UI_INVENTORY_SECTION_MISSING: prototype_playwright"
    prototype_playwright = {}
  end
  prototype_playwright_test_ids = PlanningValidatorSupport.exact_json_set(
    prototype_playwright, "test_ids", selectors, errors, "prototype_playwright"
  )
  unless prototype_playwright["evidence_kind"] == "prototype"
    errors << "UI_PLAYWRIGHT_EVIDENCE_KIND_MISMATCH: section=prototype_playwright expected=prototype actual=#{prototype_playwright['evidence_kind'] || '-'}"
  end
  prototype_playwright_sources = PlanningValidatorSupport.source_paths(
    root, prototype_playwright, errors, "prototype_playwright"
  )
  prototype_root = File.expand_path(phase_dir) + File::SEPARATOR
  prototype_outside_phase = prototype_playwright_sources.reject { |path| path.start_with?(prototype_root) }
  unless prototype_outside_phase.empty?
    errors << "UI_PROTOTYPE_PLAYWRIGHT_SOURCE_BAD_PATH: #{prototype_outside_phase.map { |path| PlanningValidatorSupport.relative_source_path(root, path) }.join(',')}"
  end
  PlanningValidatorSupport.validate_playwright_sources(
    root,
    prototype_playwright_sources,
    prototype_playwright_test_ids,
    errors,
    production: false
  )
  PlanningValidatorSupport.validate_playwright_matrix_blocks(
    prototype_playwright_sources,
    direct_matrix_links,
    errors,
    label: "design"
  )

  if stage == "production"
    if expected_mode == "prototype"
      errors << "UI_PRODUCTION_STAGE_NOT_APPLICABLE: phase=#{phase_token} package=#{package}"
    end

    implementation = inventory["implementation"]
    unless implementation.is_a?(Hash)
      errors << "UI_INVENTORY_SECTION_MISSING: implementation"
      implementation = {}
    end
    implementation_routes = PlanningValidatorSupport.exact_json_set(
      implementation, "routes", routes, errors, "implementation"
    )
    implementation_test_ids = PlanningValidatorSupport.exact_json_set(
      implementation, "test_ids", selectors, errors, "implementation"
    )
    implementation_sources = PlanningValidatorSupport.source_paths(root, implementation, errors, "implementation")
    PlanningValidatorSupport.validate_production_implementation_sources(
      root,
      implementation_sources,
      implementation_routes,
      implementation_test_ids,
      errors
    )

    playwright = inventory["playwright"]
    unless playwright.is_a?(Hash)
      errors << "UI_INVENTORY_SECTION_MISSING: playwright"
      playwright = {}
    end
    playwright_test_ids = PlanningValidatorSupport.exact_json_set(
      playwright, "test_ids", selectors, errors, "playwright"
    )
    unless playwright["evidence_kind"] == "production"
      errors << "UI_PLAYWRIGHT_EVIDENCE_KIND_MISMATCH: section=playwright expected=production actual=#{playwright['evidence_kind'] || '-'}"
    end
    playwright_sources = PlanningValidatorSupport.source_paths(root, playwright, errors, "playwright")
    PlanningValidatorSupport.validate_playwright_sources(
      root,
      playwright_sources,
      playwright_test_ids,
      errors,
      production: true
    )
    PlanningValidatorSupport.validate_playwright_matrix_blocks(
      playwright_sources,
      direct_matrix_links,
      errors,
      label: "production"
    )
    PlanningValidatorSupport.validate_production_execution(
      root,
      phase_dir,
      inventory["execution"],
      direct_matrix_links.map { |link| link[:case_id] }.sort,
      errors
    )
  end

  if expected_mode == "prototype"
    errors << "UI_PROTOTYPE_LABEL_MISSING: TEST-MATRIX.md" unless test_matrix.match?(/\bprototype\b/i)
  end

  if errors.empty?
    puts "ui_contract=PASS phase=#{phase_token} package=#{package} stage=#{stage} mode=#{expected_mode} selectors=#{selectors.length} routes=#{routes.length} owned_elements=#{owned_elements.length} owned_pages=#{owned_pages.length}"
    exit 0
  end
end

warn "ui_contract=BLOCKED errors=#{errors.length}"
errors.each { |error| warn "- #{error}" }
exit 1
