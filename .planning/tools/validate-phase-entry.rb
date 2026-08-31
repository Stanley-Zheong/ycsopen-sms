#!/usr/bin/env ruby
# frozen_string_literal: true

require "open3"
require "optparse"
require "rbconfig"
require "set"
require_relative "planning-validator-support"

options = { ui: false }
OptionParser.new do |parser|
  parser.banner = "Usage: ruby .planning/tools/validate-phase-entry.rb --phase NN --package ID --obligations PATH --entry-review PATH [--entry-evidence PATH] [--ui]"
  parser.on("--phase NN") { |value| options[:phase] = value }
  parser.on("--package ID") { |value| options[:package] = value }
  parser.on("--obligations PATH") { |value| options[:obligations] = value }
  parser.on("--entry-review PATH") { |value| options[:entry_review] = value }
  parser.on("--entry-evidence PATH") { |value| options[:entry_evidence] = value }
  parser.on("--ui") { options[:ui] = true }
end.parse!

errors = []
errors << "OPTION_PHASE_REQUIRED: expected numeric --phase" unless options[:phase]&.match?(/\A\d+\z/)
errors << "OPTION_PACKAGE_REQUIRED: expected kebab-case --package" unless options[:package]&.match?(/\A[a-z0-9]+(?:-[a-z0-9]+)*\z/)
errors << "OPTION_OBLIGATIONS_REQUIRED" if options[:obligations].to_s.empty?
errors << "OPTION_ENTRY_REVIEW_REQUIRED" if options[:entry_review].to_s.empty?

if errors.empty?
  root = Dir.pwd
  phase_number = options[:phase].to_i
  phase_token = format("%02d", phase_number)
  package = options[:package]
  phase_dir = File.join(root, ".planning/phases/#{phase_token}-#{package}")
  roadmap_path = File.join(root, ".planning/ROADMAP.md")
  schema_registry_path = File.join(root, ".planning/SCHEMA-OWNERSHIP.md")
  obligation_path = File.expand_path(options[:obligations], root)
  entry_review_path = File.expand_path(options[:entry_review], root)
  expected_entry_review = File.join(phase_dir, "ENTRY-REVIEW.md")
  expected_entry_evidence = File.join(phase_dir, "ENTRY-EVIDENCE.md")

  phases = PlanningValidatorSupport.roadmap_packages(roadmap_path, errors)
  dependency_map = PlanningValidatorSupport.roadmap_dependencies(roadmap_path, errors)
  expected_package = phases[phase_number]
  errors << "ROADMAP_PHASE_PACKAGE_MISMATCH: phase=#{phase_number} expected=#{expected_package || '-'} actual=#{package}" unless expected_package == package
  errors << "ENTRY_REVIEW_PATH_MISMATCH: expected=#{expected_entry_review} actual=#{entry_review_path}" unless entry_review_path == expected_entry_review
  entry_evidence_path = options[:entry_evidence].to_s.empty? ? nil : File.expand_path(options[:entry_evidence], root)
  if entry_evidence_path && entry_evidence_path != expected_entry_evidence
    errors << "ENTRY_EVIDENCE_PATH_MISMATCH: expected=#{expected_entry_evidence} actual=#{entry_evidence_path}"
  end
  errors << "PHASE_DIRECTORY_MISSING: #{phase_dir}" unless File.directory?(phase_dir)
  PlanningValidatorSupport.validate_dependency_evidence(
    root,
    phase_number,
    phases,
    dependency_map,
    errors
  )

  required_artifacts = [
    "#{phase_token}-SPEC.md",
    "#{phase_token}-CONTEXT.md",
    "INTENT.md",
    "DESIGN.md",
    "ITERATIONS.md",
    "DECISIONS.md",
    "TODO.md",
    "TEST-MATRIX.md",
    "ENTRY-REVIEW.md",
    "CLAUDE-REVIEW.md"
  ]
  required_artifacts.each do |name|
    path = File.join(phase_dir, name)
    if !File.file?(path)
      errors << "PHASE_ARTIFACT_MISSING: #{path}"
    elsif File.zero?(path)
      errors << "PHASE_ARTIFACT_EMPTY: #{path}"
    end
  end
  evidence_dir = File.join(phase_dir, "EVIDENCE")
  errors << "EVIDENCE_DIRECTORY_MISSING: #{evidence_dir}" unless File.directory?(evidence_dir)

  records = PlanningValidatorSupport.catalog_records(obligation_path, errors)
  owned_ids = records.select { |record| record.owner == package }.map(&:id).to_set
  known_ids = records.map(&:id).to_set
  errors << "OWNER_OBLIGATION_SET_EMPTY: package=#{package}" if owned_ids.empty?
  {
    "#{phase_token}-SPEC.md" => "spec",
    "TODO.md" => "todo",
    "TEST-MATRIX.md" => "test_matrix"
  }.each do |name, label|
    PlanningValidatorSupport.exact_obligation_trace(
      File.join(phase_dir, name), owned_ids, known_ids, errors, label
    )
  end

  plan_paths = File.directory?(phase_dir) ? Dir.glob(File.join(phase_dir, "#{phase_token}-*-PLAN.md")).sort : []
  PlanningValidatorSupport.validate_plans(plan_paths, errors)
  PlanningValidatorSupport.validate_entry_review(entry_review_path, errors)
  PlanningValidatorSupport.validate_entry_evidence(entry_evidence_path, entry_review_path, errors) if entry_evidence_path
  PlanningValidatorSupport.validate_todo(
    File.join(phase_dir, "TODO.md"),
    errors,
    require_empty: false,
    owned_ids: owned_ids
  )

  schema_registry = PlanningValidatorSupport.validate_schema_registry(
    schema_registry_path,
    phases.values,
    errors
  )
  PlanningValidatorSupport.validate_phase_schema_claims(
    root,
    phase_dir,
    package,
    schema_registry,
    errors
  ) if File.directory?(phase_dir)

  if options[:ui]
    ui_validator = File.join(__dir__, "validate-ui-contract.rb")
    stdout, stderr, status = Open3.capture3(
      RbConfig.ruby,
      ui_validator,
      "--phase",
      phase_token,
      "--package",
      package,
      "--stage",
      "design",
      chdir: root
    )
    unless status.success?
      errors << "UI_CONTRACT_BLOCKED"
      (stderr + stdout).lines.map(&:strip).reject(&:empty?).each do |line|
        errors << "UI: #{line}"
      end
    end
  end

  if errors.empty?
    puts "phase_entry=PASS phase=#{phase_token} package=#{package} obligations=#{owned_ids.length} plans=#{plan_paths.length} dependencies=#{dependency_map.fetch(phase_number, []).length} dependency_delivery=live-annotated-attestation ui_stage=#{options[:ui] ? 'design' : 'not-applicable'} schema_claims=#{File.file?(File.join(phase_dir, 'SCHEMA-CLAIMS.md')) ? 'declared' : 'none'}"
    exit 0
  end
end

warn "phase_entry=BLOCKED errors=#{errors.length}"
errors.each { |error| warn "- #{error}" }
exit 1
