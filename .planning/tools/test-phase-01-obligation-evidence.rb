#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "pathname"
require "tmpdir"

require_relative "verification-evidence"
require File.expand_path("../../scripts/lib/phase-01/run_checks", __dir__)

SOURCE_ROOT = File.expand_path("../..", __dir__)

def deep_copy(value)
  JSON.parse(JSON.generate(value))
end

def assert_error(label, errors, token)
  abort("#{label}: expected #{token}, got #{errors.join(';')}") unless errors.any? { |error| error.include?(token) }
end

Dir.mktmpdir("plan10-obligation-") do |root|
  definitions = Phase01RunChecks.definitions_for(["all"])
  registries = Phase01RunChecks.subject_registries(definitions)
  contracts = Phase01RunChecks.check_contracts(definitions)
  required_paths = registries.values.flatten.map { |entry| entry.fetch("path") }.uniq
  required_paths.concat([
    VerificationEvidence::ENTRY_EVIDENCE_PATH,
    VerificationEvidence::ENTRY_REVIEW_PATH,
    VerificationEvidence::LOCAL_RUNTIME_PATH,
    "#{VerificationEvidence::PHASE_DIR}/TEST-MATRIX.md",
    ".planning/PRD-OBLIGATIONS.md"
  ])
  required_paths.uniq.each do |relative|
    source = File.join(SOURCE_ROOT, relative)
    destination = File.join(root, relative)
    FileUtils.mkdir_p(File.dirname(destination))
    FileUtils.cp(source, destination, preserve: true)
  end
  FileUtils.mkdir_p(File.join(root, "web"))
  FileUtils.ln_s(File.join(SOURCE_ROOT, "web/node_modules"), File.join(root, "web/node_modules"))

  temporary_relative = File.join(VerificationEvidence::EVIDENCE_DIR, "test-check-run")
  subject_relative = File.join(temporary_relative, "tested-inputs.json")
  subject = VerificationEvidence.build_subject_manifest(root: root, registries: registries, manifest_path: subject_relative)
  manifest_digest = VerificationEvidence.subject_manifest_digest(subject)
  subject_digest = VerificationEvidence.tested_subject_digest(subject.fetch("inputs"))
  runtime_path = File.join(root, VerificationEvidence::LOCAL_RUNTIME_PATH)
  runtime = JSON.parse(File.read(runtime_path))
  runtime.fetch("run").fetch("scenario").fetch("subject").merge!(
    "manifestPath" => VerificationEvidence::DURABLE_SUBJECT_PATH,
    "manifestDigest" => manifest_digest,
    "testedSubjectDigest" => subject_digest
  )
  runtime.fetch("run").fetch("scenario").fetch("subject").fetch("health").merge!(
    "subject_manifest_digest" => manifest_digest,
    "tested_subject_digest" => subject_digest
  )
  File.write(runtime_path, JSON.pretty_generate(runtime) + "\n")
  envelopes = definitions.map do |definition|
    VerificationEvidence.build_envelope(
      run_id: "plan10-test-run", check_id: definition.fetch("id"), layer: definition.fetch("layer"),
      obligation_ids: definition.fetch("obligation_ids").sort, case_ids: definition.fetch("case_ids").sort,
      argv: definition.fetch("argv"), cwd: definition.fetch("cwd"),
      started_at: "2026-08-31T00:00:00.000000Z", completed_at: "2026-08-31T00:00:00.000001Z",
      environment: { "ci" => false }, status: "PASS", exit_code: 0, errors: [],
      diagnostics: ["fixture PASS"], artifacts: [], subject_manifest_path: subject_relative,
      subject_manifest_digest: manifest_digest, tested_subject_digest: subject_digest
    )
  end
  evidence_paths = []
  evidence_sha256s = []
  envelopes.each do |envelope|
    relative = File.join(temporary_relative, "#{envelope.fetch('check_id')}.json")
    VerificationEvidence.atomic_write_json(File.join(root, relative), envelope)
    evidence_paths << relative
    evidence_sha256s << Digest::SHA256.file(File.join(root, relative)).hexdigest
  end
  aggregate = VerificationEvidence.build_aggregate(
    run_id: "plan10-test-run", envelopes: envelopes, evidence_paths: evidence_paths,
    evidence_sha256s: evidence_sha256s, subject_manifest_path: subject_relative,
    subject_manifest_digest: manifest_digest, tested_subject_digest: subject_digest
  )
  aggregate_relative = File.join(temporary_relative, "aggregate.json")
  VerificationEvidence.atomic_write_json(File.join(root, aggregate_relative), aggregate)
  check_manifest = VerificationEvidence.build_evidence_manifest(
    root: root, owner: VerificationEvidence::OWNER, envelopes: envelopes,
    evidence_paths: evidence_paths, aggregate_path: aggregate_relative,
    subject_manifest_path: subject_relative, subject_manifest_digest: manifest_digest,
    tested_subject_digest: subject_digest
  )
  check_manifest_relative = File.join(temporary_relative, "check-manifest.json")
  VerificationEvidence.atomic_write_json(File.join(root, check_manifest_relative), check_manifest)
  legacy_errors = VerificationEvidence.validate_evidence_manifest(
    root: root, manifest: check_manifest, registries: registries,
    check_contracts: contracts, required_owner: VerificationEvidence::OWNER
  )
  abort("legacy v1 regression: #{legacy_errors.join(';')}") unless legacy_errors.empty?

  output_relative = VerificationEvidence::EVIDENCE_DIR
  formal = VerificationEvidence.build_obligation_evidence(
    root: root, check_manifest: check_manifest_relative, output_dir: output_relative,
    matrix: "#{VerificationEvidence::PHASE_DIR}/TEST-MATRIX.md", catalog: ".planning/PRD-OBLIGATIONS.md",
    runtime: VerificationEvidence::LOCAL_RUNTIME_PATH
  )
  formal_errors = VerificationEvidence.validate_obligation_manifest(
    root: root, manifest: formal, registries: Phase01RunChecks.subject_registries,
    check_contracts: Phase01RunChecks.check_contracts, required_owner: VerificationEvidence::OWNER,
    output_dir: output_relative
  )
  abort("positive obligation manifest: #{formal_errors.join(';')}") unless formal_errors.empty?
  abort("expected exactly seven summaries") unless formal.fetch("entries").length == 7
  browser_entry = formal.fetch("entries").find { |entry| entry.fetch("obligation_id") == "OBL-NFR-BROWSER" }
  browser_summary = JSON.parse(File.read(File.join(root, browser_entry.fetch("path"))))
  runtime_facts = browser_summary.fetch("runtime")
  abort("runtime subject path missing") unless runtime_facts.fetch("subject_manifest_path") == VerificationEvidence::DURABLE_SUBJECT_PATH
  abort("runtime subject manifest digest missing") unless runtime_facts.fetch("subject_manifest_digest") == manifest_digest
  abort("runtime tested subject digest missing") unless runtime_facts.fetch("tested_subject_digest") == subject_digest

  manifest_mutations = {
    "missing" => ["OBLIGATION_MANIFEST_ENTRY_SET_INVALID", ->(copy) { copy["entries"].pop }],
    "duplicate" => ["OBLIGATION_MANIFEST_ENTRY_SET_INVALID", ->(copy) { copy["entries"][1] = deep_copy(copy["entries"][0]) }],
    "foreign" => ["OBLIGATION_MANIFEST_ENTRY_SET_INVALID", ->(copy) { copy["entries"][0]["obligation_id"] = "OBL-FOREIGN" }],
    "order" => ["OBLIGATION_MANIFEST_ENTRY_SET_INVALID", ->(copy) { copy["entries"].reverse! }],
    "owner" => ["OBLIGATION_MANIFEST_OWNER_MISMATCH", ->(copy) { copy["owner"] = "foreign" }],
    "subject" => ["OBLIGATION_MANIFEST_SUBJECT_MANIFEST_DIGEST_MISMATCH", ->(copy) { copy["subject_manifest_digest"] = "0" * 64 }],
    "runtime" => ["OBLIGATION_MANIFEST_RUNTIME_MISMATCH", ->(copy) { copy["runtime_artifact"]["sha256"] = "0" * 64 }],
    "ci" => ["OBLIGATION_MANIFEST_CI_LOCATORS_MISMATCH", ->(copy) { copy["ci_locators"][0]["sha256"] = "0" * 64 }]
  }
  manifest_mutations.each do |label, (token, mutation)|
    copy = deep_copy(formal)
    mutation.call(copy)
    errors = VerificationEvidence.validate_obligation_manifest(
      root: root, manifest: copy, registries: Phase01RunChecks.subject_registries,
      check_contracts: Phase01RunChecks.check_contracts, required_owner: VerificationEvidence::OWNER,
      output_dir: output_relative
    )
    assert_error(label, errors, token)
  end

  original_runtime = JSON.parse(File.read(runtime_path))
  mutated_runtime = deep_copy(original_runtime)
  mutated_subject = mutated_runtime.fetch("run").fetch("scenario").fetch("subject")
  mutated_subject["manifestDigest"] = "9" * 64
  mutated_subject.fetch("health")["subject_manifest_digest"] = "9" * 64
  File.write(runtime_path, JSON.pretty_generate(mutated_runtime) + "\n")
  runtime_bound_manifest = deep_copy(formal)
  runtime_bound_manifest.fetch("runtime_artifact")["sha256"] = Digest::SHA256.file(runtime_path).hexdigest
  runtime_bound_manifest.fetch("runtime_artifact")["size"] = File.size(runtime_path)
  runtime_errors = VerificationEvidence.validate_obligation_manifest(
    root: root, manifest: runtime_bound_manifest, registries: Phase01RunChecks.subject_registries,
    check_contracts: Phase01RunChecks.check_contracts, required_owner: VerificationEvidence::OWNER,
    output_dir: output_relative
  )
  assert_error("runtime-subject-binding", runtime_errors, "RUNTIME_SUBJECT_MANIFEST_DIGEST_MISMATCH")
  File.write(runtime_path, JSON.pretty_generate(original_runtime) + "\n")

  summary_mutations = {
    "fail-status" => ["OBLIGATION_SUMMARY_STATUS_INVALID", "OBL-FOUND-TRACE-003", ->(summary) { summary["status"] = "FAIL"; summary["exit_code"] = 1 }],
    "product-spoof" => ["OBLIGATION_PRODUCT_ACCEPTANCE_SPOOF", "OBL-FOUND-TRACE-003", ->(summary) { summary["product_acceptance_claims"] = ["Phase 56 complete"] }],
    "argv" => ["OBLIGATION_CHECK_RESULT_CONTRACT_MISMATCH", "OBL-FOUND-TRACE-003", lambda do |summary|
      result = summary["check_results"].first
      result["argv"] = ["false"]
      result["result_digest"] = VerificationEvidence.digest_value(result.reject { |key, _value| key == "result_digest" })
    end],
    "result-digest" => ["OBLIGATION_CHECK_RESULT_DIGEST_MISMATCH", "OBL-FOUND-TRACE-003", ->(summary) { summary["check_results"][0]["result_digest"] = "0" * 64 }],
    "check-missing" => ["OBLIGATION_CHECK_SET_INVALID", "OBL-FOUND-TRACE-003", ->(summary) { summary["check_results"].pop }],
    "owner-query" => ["OBLIGATION_OWNER_QUERY_MISSING", "OBL-FOUND-TRACE-001", ->(summary) { summary["supporting_results"] = [] }],
    "behavior" => ["OBLIGATION_SUMMARY_MATRIX_MISMATCH", "OBL-FOUND-TRACE-001", ->(summary) { summary["behavior_id"] = "foreign" }],
    "runtime-fact" => ["OBLIGATION_RUNTIME_MISMATCH", "OBL-NFR-BROWSER", ->(summary) { summary["runtime"]["brand"] = "Chromium" }],
    "foreign-runtime" => ["OBLIGATION_RUNTIME_FOREIGN", "OBL-FOUND-TRACE-001", ->(summary) { summary["runtime"] = {} }],
    "unknown-field" => ["OBLIGATION_SUMMARY_UNKNOWN_FIELD", "OBL-FOUND-TRACE-001", ->(summary) { summary["unexpected"] = true }]
  }
  summary_mutations.each do |label, (token, obligation_id, mutation)|
    entry = formal.fetch("entries").find { |item| item.fetch("obligation_id") == obligation_id }
    path = File.join(root, entry.fetch("path"))
    original = JSON.parse(File.read(path))
    mutated = deep_copy(original)
    mutation.call(mutated)
    VerificationEvidence.atomic_write_json(path, mutated)
    copy = deep_copy(formal)
    copy_entry = copy.fetch("entries").find { |item| item.fetch("obligation_id") == obligation_id }
    copy_entry["sha256"] = Digest::SHA256.file(path).hexdigest
    copy_entry["status"] = mutated["status"] if mutated.key?("status")
    copy_entry["behavior_id"] = mutated["behavior_id"] if mutated.key?("behavior_id")
    errors = VerificationEvidence.validate_obligation_manifest(
      root: root, manifest: copy, registries: Phase01RunChecks.subject_registries,
      check_contracts: Phase01RunChecks.check_contracts, required_owner: VerificationEvidence::OWNER,
      output_dir: output_relative
    )
    assert_error(label, errors, token)
    VerificationEvidence.atomic_write_json(path, original)
  end


  bound_source_relative = ".planning/tools/validate-delivery-attestation.rb"
  bound_source_path = File.join(root, bound_source_relative)
  bound_source_content = File.binread(bound_source_path)
  File.open(bound_source_path, "ab") { |file| file.write("\n# subject-content-mutation\n") }
  content_errors = VerificationEvidence.validate_subject_manifest(
    root: root,
    manifest: JSON.parse(File.read(File.join(root, VerificationEvidence::DURABLE_SUBJECT_PATH))),
    registries: Phase01RunChecks.subject_registries
  )
  assert_error("bound-delivery-content", content_errors, "SUBJECT_CONTENT_MISMATCH")
  File.binwrite(bound_source_path, bound_source_content)

  bound_source_mode = File.lstat(bound_source_path).mode & 0o7777
  mutated_mode = (bound_source_mode & 0o111).zero? ? (bound_source_mode | 0o111) : (bound_source_mode & ~0o111)
  File.chmod(mutated_mode, bound_source_path)
  mode_errors = VerificationEvidence.validate_subject_manifest(
    root: root,
    manifest: JSON.parse(File.read(File.join(root, VerificationEvidence::DURABLE_SUBJECT_PATH))),
    registries: Phase01RunChecks.subject_registries
  )
  assert_error("bound-delivery-executable-mode", mode_errors, "SUBJECT_MODE_MISMATCH")
  File.chmod(bound_source_mode, bound_source_path)

  puts "phase_01_obligation_evidence=PASS mutations=#{manifest_mutations.length + summary_mutations.length + 3} summaries=7 legacy_v1=PASS runtime_binding=PASS bound_source=content+executable-mode"
end
