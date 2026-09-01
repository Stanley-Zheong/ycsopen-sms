#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"
require_relative "phase3-crypto-evidence"
require_relative "../../scripts/lib/phase-03/run_checks"

REPOSITORY_ROOT = File.expand_path("../..", __dir__)
VALIDATOR = File.join(__dir__, "validate-phase-03-crypto-evidence.rb")
PRODUCER = File.join(__dir__, "produce-phase-03-crypto-evidence.rb")
PHASE_DIR = ".planning/phases/03-crypto-storage-bootstrap"
EVIDENCE_DIR = File.join(PHASE_DIR, "EVIDENCE")
RESULT_DIR = "core/target/phase03/results"
INVENTORY_PATH = "core/src/main/resources/security/protected-data-inventory.json"
SUBJECT_PATH = File.join(EVIDENCE_DIR, "tested-inputs.json")
RUN_SUBJECT_PATH = File.join(RESULT_DIR, "tested-inputs.json")
AGGREGATE_PATH = File.join(RESULT_DIR, "aggregate.json")
LEAK_PATH = File.join(RESULT_DIR, "complete-leak-result.json")
INVENTORY_RESULT_PATH = File.join(RESULT_DIR, "protected-inventory-result.json")
DRIFT_INPUT_PATH = File.join(
  EVIDENCE_DIR, "schema/phase03-obligation-evidence.schema.json"
)

def write_json(root, relative, value)
  path = File.join(root, relative)
  FileUtils.mkdir_p(File.dirname(path))
  File.write(path, JSON.pretty_generate(value) + "\n")
end

def write_runner_json(root, relative, value)
  write_json(root, relative, Phase03RunChecks.canonicalize(value))
end

def read_json(root, relative)
  JSON.parse(File.read(File.join(root, relative), encoding: "UTF-8"))
end

def mutate_json(root, relative)
  value = read_json(root, relative)
  yield value
  write_json(root, relative, value)
end

def sha256(root, relative)
  Digest::SHA256.file(File.join(root, relative)).hexdigest
end

def result_ref(root, relative)
  result = read_json(root, relative)
  {
    "check_id" => result.fetch("check_id"),
    "path" => relative,
    "sha256" => sha256(root, relative),
    "result_digest" => result.fetch("result_digest")
  }
end

def seal_result(root, relative)
  result = read_json(root, relative)
  result["result_digest"] = Phase3CryptoEvidence.result_digest(result)
  write_json(root, relative, result)
end

def copy_contract(root)
  [
    ".planning/PRD-OBLIGATIONS.md",
    File.join(PHASE_DIR, "TEST-MATRIX.md"),
    INVENTORY_PATH,
    File.join(EVIDENCE_DIR, "schema/phase03-obligation-evidence.schema.json"),
    File.join(EVIDENCE_DIR, "schema/phase03-evidence-manifest.schema.json")
  ].each do |relative|
    destination = File.join(root, relative)
    FileUtils.mkdir_p(File.dirname(destination))
    FileUtils.cp(File.join(REPOSITORY_ROOT, relative), destination)
  end
end

def child_result(check_id:, layer:, obligation_ids:, case_ids:, adapters:, subject_digest:, facts: nil)
  result = {
    "schema_version" => Phase3CryptoEvidence::CHILD_RESULT_SCHEMA,
    "phase" => Phase3CryptoEvidence::PHASE,
    "check_id" => check_id,
    "layer" => layer,
    "obligation_ids" => obligation_ids.sort,
    "case_ids" => case_ids.sort,
    "adapter_identities" => adapters.sort.map do |adapter|
      { "id" => adapter, "mode" => Phase3CryptoEvidence::ADAPTER_IDENTITIES.fetch(adapter) }
    end,
    "subject_digest" => subject_digest,
    "status" => "PASS",
    "exit_code" => 0,
    "facts" => facts || { "assertion_count" => 3, "contract_digest" => "a" * 64 }
  }
  result.merge("result_digest" => Phase3CryptoEvidence.result_digest(result))
end

def lane_result(definition)
  base = {
    "schema_version" => Phase3CryptoEvidence::ROOT_RESULT_SCHEMA,
    "check_id" => definition.fetch("id"),
    "layer" => definition.fetch("layer"),
    "mode" => definition.fetch("mode"),
    "argv" => definition.fetch("argv"),
    "status" => "PASS", "exit_code" => 0, "error_id" => nil,
    "diagnostic_sha256" => Digest::SHA256.hexdigest("sanitized"),
    "started_at" => "2026-09-01T00:00:00.000000Z",
    "completed_at" => "2026-09-01T00:00:01.000000Z"
  }
  base.merge("result_digest" => Phase03RunChecks.digest(base))
end

def build_runner_results(root)
  subject = Phase03RunChecks.build_subject(root)
  write_runner_json(root, RUN_SUBJECT_PATH, subject)
  subject_digest = Phase3CryptoEvidence.digest_value(subject.fetch("inputs"))

  lanes = Phase03RunChecks::CHECKS.to_h do |definition|
    lane = lane_result(definition)
    write_runner_json(root, File.join(RESULT_DIR, "lanes", "#{definition.fetch('id')}.json"), lane)
    [definition.fetch("id"), lane]
  end
  aggregate = {
    "schema_version" => Phase3CryptoEvidence::ROOT_AGGREGATE_SCHEMA,
    "phase" => Phase3CryptoEvidence::PHASE,
    "status" => "PASS", "tested_subject_digest" => subject_digest,
    "registry_digest" => Phase03RunChecks.digest(Phase03RunChecks::CHECKS),
    "lane_result_digests" => lanes.transform_values { |lane| lane.fetch("result_digest") }
  }
  aggregate["result_digest"] = Phase03RunChecks.digest(aggregate)
  write_runner_json(root, AGGREGATE_PATH, aggregate)

  inventory_digest = Phase3CryptoEvidence.digest_value(read_json(root, INVENTORY_PATH))
  inventory_result = child_result(
    check_id: "phase03-protected-inventory", layer: "static",
    obligation_ids: Phase3CryptoEvidence::OBLIGATIONS.keys,
    case_ids: Phase3CryptoEvidence::OBLIGATIONS.values.map { |row| row.fetch("case_id") },
    adapters: ["phase03-inventory-validator"], subject_digest: subject_digest,
    facts: { "blocking_dispositions" => [], "inventory_digest" => inventory_digest,
             "result" => "ACCEPTED" }
  )
  write_runner_json(root, INVENTORY_RESULT_PATH, inventory_result)

  leak = {
    "schema_version" => Phase3CryptoEvidence::LEAK_RESULT_SCHEMA,
    "phase" => Phase3CryptoEvidence::PHASE,
    "check_id" => "phase03-complete-leak-scan",
    "subject_digest" => subject_digest, "status" => "PASS", "exit_code" => 0,
    "targets" => Phase3CryptoEvidence::LEAK_TARGETS.to_a.sort.map do |id|
      {
        "id" => id, "reader_identity" => Phase3CryptoEvidence::LEAK_READER_IDENTITIES.fetch(id),
        "scanned_items" => 1, "prohibited_matches" => 0,
        "sensitivity_status" => "DETECTED_SEEDED_MUTATION"
      }
    end
  }
  leak["result_digest"] = Phase3CryptoEvidence.result_digest(leak)
  write_runner_json(root, LEAK_PATH, leak)

  Phase03RunChecks::CHILD_BINDINGS.each do |obligation_id, binding|
    definition = Phase3CryptoEvidence::OBLIGATIONS.fetch(obligation_id)
    required = binding.fetch("lanes")
    facts = {
      "aggregate_status" => "PASS", "registry_digest" => aggregate.fetch("registry_digest"),
      "required_lane_ids" => required,
      "lane_result_digests" => required.to_h do |id|
        [id, lanes.fetch(id).fetch("result_digest")]
      end
    }
    result = child_result(
      check_id: binding.fetch("check_id"), layer: binding.fetch("layer"),
      obligation_ids: [obligation_id], case_ids: [definition.fetch("case_id")],
      adapters: binding.fetch("adapters"), subject_digest: subject_digest, facts: facts
    )
    write_runner_json(root, File.join(RESULT_DIR, "#{binding.fetch('check_id')}.json"), result)
  end
end

def run_producer(root)
  Open3.capture3(
    RbConfig.ruby, PRODUCER,
    "--phase-dir", PHASE_DIR, "--result-root", RESULT_DIR,
    chdir: root
  )
end

def assert_producer(name, root, expected_success:, token:)
  expected_outputs = [SUBJECT_PATH, File.join(EVIDENCE_DIR, "evidence-manifest.json")] +
    Phase3CryptoEvidence::OBLIGATIONS.values.map do |definition|
      File.join(PHASE_DIR, definition.fetch("evidence_path"))
    end
  before = expected_outputs.to_h do |relative|
    path = File.join(root, relative)
    [relative, File.exist?(path) && !File.symlink?(path) ? Digest::SHA256.file(path).hexdigest : File.symlink?(path)]
  end
  stdout, stderr, status = run_producer(root)
  output = stdout + stderr
  unless status.success? == expected_success
    abort "#{name}: producer expected success=#{expected_success}, got #{status.exitstatus}:\n#{output}"
  end
  abort "#{name}: producer missing #{token}:\n#{output}" unless output.include?(token)
  unless expected_success
    after = expected_outputs.to_h do |relative|
      path = File.join(root, relative)
      [relative, File.exist?(path) && !File.symlink?(path) ? Digest::SHA256.file(path).hexdigest : File.symlink?(path)]
    end
    abort "#{name}: rejected producer changed durable outputs" unless after == before
  end
end

def build_runner_fixture(root)
  copy_contract(root)
  build_runner_results(root)
end

def build_fixture(root)
  build_runner_fixture(root)
  assert_producer(
    "fixture producer", root, expected_success: true,
    token: "phase03_crypto_evidence_producer=PASS"
  )
end

def refresh_bindings(root)
  subject = read_json(root, SUBJECT_PATH)
  subject_ref = {
    "path" => SUBJECT_PATH,
    "sha256" => sha256(root, SUBJECT_PATH),
    "tested_subject_digest" => Phase3CryptoEvidence.digest_value(subject.fetch("inputs"))
  }
  inventory = read_json(root, INVENTORY_PATH)
  inventory_digest = Phase3CryptoEvidence.digest_value(inventory)
  inventory_result = read_json(root, INVENTORY_RESULT_PATH)
  inventory_result["subject_digest"] = subject_ref["tested_subject_digest"]
  inventory_result["facts"] = {
    "blocking_dispositions" => [], "inventory_digest" => inventory_digest, "result" => "ACCEPTED"
  }
  inventory_result["result_digest"] = Phase3CryptoEvidence.result_digest(inventory_result)
  write_json(root, INVENTORY_RESULT_PATH, inventory_result)
  inventory_ref = {
    "path" => INVENTORY_PATH,
    "sha256" => sha256(root, INVENTORY_PATH),
    "accepted_digest" => inventory_digest,
    "validator_result" => result_ref(root, INVENTORY_RESULT_PATH)
  }

  leak = read_json(root, LEAK_PATH)
  leak["subject_digest"] = subject_ref["tested_subject_digest"]
  leak["result_digest"] = Phase3CryptoEvidence.result_digest(leak)
  write_json(root, LEAK_PATH, leak)
  leak_ref = {
    "path" => LEAK_PATH,
    "sha256" => sha256(root, LEAK_PATH),
    "result_digest" => leak.fetch("result_digest")
  }

  entries = Phase3CryptoEvidence::OBLIGATIONS.map do |obligation_id, definition|
    child_path = File.join(RESULT_DIR, "#{definition.fetch('check_id')}.json")
    child = read_json(root, child_path)
    child["subject_digest"] = subject_ref["tested_subject_digest"]
    child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
    write_json(root, child_path, child)
    evidence = {
      "schema_version" => Phase3CryptoEvidence::OBLIGATION_SCHEMA,
      "phase" => Phase3CryptoEvidence::PHASE,
      "owner" => Phase3CryptoEvidence::OWNER,
      "obligation_id" => obligation_id,
      "requirement_ids" => definition.fetch("requirement_ids"),
      "behavior_id" => definition.fetch("behavior_id"),
      "catalog_test" => definition.fetch("catalog_test"),
      "case_id" => definition.fetch("case_id"),
      "evidence_path" => definition.fetch("evidence_path"),
      "status" => "PASS",
      "exit_code" => 0,
      "subject" => subject_ref,
      "inventory" => inventory_ref,
      "leak_result" => leak_ref,
      "child_results" => [result_ref(root, child_path)]
    }
    evidence_relative = File.join(PHASE_DIR, definition.fetch("evidence_path"))
    write_json(root, evidence_relative, evidence)
    {
      "obligation_id" => obligation_id,
      "path" => evidence_relative,
      "sha256" => sha256(root, evidence_relative),
      "status" => "PASS",
      "evidence_digest" => Phase3CryptoEvidence.digest_value(evidence)
    }
  end
  manifest = {
    "schema_version" => Phase3CryptoEvidence::MANIFEST_SCHEMA,
    "phase" => Phase3CryptoEvidence::PHASE,
    "owner" => Phase3CryptoEvidence::OWNER,
    "status" => "PASS",
    "subject" => subject_ref,
    "inventory" => inventory_ref,
    "leak_result" => leak_ref,
    "entries" => entries
  }
  write_json(root, File.join(EVIDENCE_DIR, "evidence-manifest.json"), manifest)
end

def refresh_child_binding(root, obligation_id)
  definition = Phase3CryptoEvidence::OBLIGATIONS.fetch(obligation_id)
  child_path = File.join(RESULT_DIR, "#{definition.fetch('check_id')}.json")
  evidence_path = File.join(PHASE_DIR, definition.fetch("evidence_path"))
  evidence = read_json(root, evidence_path)
  evidence["child_results"] = [result_ref(root, child_path)]
  write_json(root, evidence_path, evidence)
  manifest_path = File.join(EVIDENCE_DIR, "evidence-manifest.json")
  manifest = read_json(root, manifest_path)
  entry = manifest.fetch("entries").find { |row| row["obligation_id"] == obligation_id }
  entry["sha256"] = sha256(root, evidence_path)
  entry["evidence_digest"] = Phase3CryptoEvidence.digest_value(evidence)
  write_json(root, manifest_path, manifest)
end

def run_validator(root)
  Open3.capture3(
    RbConfig.ruby,
    VALIDATOR,
    "--phase-dir", PHASE_DIR,
    "--require-owner", Phase3CryptoEvidence::OWNER,
    chdir: root
  )
end

def assert_result(name, root, expected_success:, token:)
  stdout, stderr, status = run_validator(root)
  output = stdout + stderr
  unless status.success? == expected_success
    abort "#{name}: expected success=#{expected_success}, got #{status.exitstatus}:\n#{output}"
  end
  abort "#{name}: missing #{token}:\n#{output}" unless output.include?(token)
end

def with_fixture(name)
  Dir.mktmpdir("phase03-crypto-evidence-#{name}-") do |root|
    build_fixture(root)
    yield root
  end
end

def with_runner_fixture(name)
  Dir.mktmpdir("phase03-crypto-producer-#{name}-") do |root|
    build_runner_fixture(root)
    yield root
  end
end

cases = 0

producer_cases = [
  ["aggregate-fail", "ROOT_AGGREGATE_HEADER_INVALID", lambda do |root|
    mutate_json(root, AGGREGATE_PATH) do |aggregate|
      aggregate["status"] = "FAIL"
      aggregate["result_digest"] = Phase3CryptoEvidence.result_digest(aggregate)
    end
  end],
  ["aggregate-registry", "ROOT_AGGREGATE_HEADER_INVALID", lambda do |root|
    mutate_json(root, AGGREGATE_PATH) do |aggregate|
      aggregate["registry_digest"] = "0" * 64
      aggregate["result_digest"] = Phase3CryptoEvidence.result_digest(aggregate)
    end
  end],
  ["aggregate-lane-omitted", "ROOT_AGGREGATE_LANE_SET_INVALID", lambda do |root|
    mutate_json(root, AGGREGATE_PATH) do |aggregate|
      aggregate.fetch("lane_result_digests").delete("durable-artifact-leak-scan")
      aggregate["result_digest"] = Phase3CryptoEvidence.result_digest(aggregate)
    end
  end],
  ["lane-missing", "ROOT_LANE_UNREADABLE", lambda do |root|
    FileUtils.rm(File.join(root, RESULT_DIR, "lanes/fixture-cleanup.json"))
  end],
  ["lane-digest-drift", "ROOT_LANE_RESULT_DIGEST_INVALID", lambda do |root|
    mutate_json(root, File.join(RESULT_DIR, "lanes/default-maven.json")) do |lane|
      lane["diagnostic_sha256"] = "c" * 64
      lane["result_digest"] = Phase3CryptoEvidence.result_digest(lane)
    end
  end],
  ["child-durable-omitted", "CHILD_REQUIRED_LANES_INVALID", lambda do |root|
    path = File.join(RESULT_DIR, "phase03-protected-persistence-integration.json")
    mutate_json(root, path) do |child|
      child.fetch("facts").fetch("required_lane_ids").delete("durable-artifact-leak-scan")
      child.fetch("facts").fetch("lane_result_digests").delete("durable-artifact-leak-scan")
      child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
    end
  end],
  ["child-cleanup-omitted", "CHILD_REQUIRED_LANES_INVALID", lambda do |root|
    path = File.join(RESULT_DIR, "phase03-object-storage-integration.json")
    mutate_json(root, path) do |child|
      child.fetch("facts").fetch("required_lane_ids").delete("fixture-cleanup")
      child.fetch("facts").fetch("lane_result_digests").delete("fixture-cleanup")
      child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
    end
  end],
  ["child-registry-forged", "CHILD_REGISTRY_DIGEST_INVALID", lambda do |root|
    path = File.join(RESULT_DIR, "phase03-pkcs11-fault-integration.json")
    mutate_json(root, path) do |child|
      child.fetch("facts")["registry_digest"] = "0" * 64
      child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
    end
  end],
  ["child-subject-forged", "CHILD_RESULT_CONTRACT_INVALID", lambda do |root|
    path = File.join(RESULT_DIR, "phase03-migration-integration.json")
    mutate_json(root, path) do |child|
      child["subject_digest"] = "0" * 64
      child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
    end
  end],
  ["inventory-subject-forged", "INVENTORY_RESULT_CONTRACT_INVALID", lambda do |root|
    mutate_json(root, INVENTORY_RESULT_PATH) do |result|
      result["subject_digest"] = "0" * 64
      result["result_digest"] = Phase3CryptoEvidence.result_digest(result)
    end
  end],
  ["leak-subject-forged", "LEAK_RESULT_CONTRACT_INVALID", lambda do |root|
    mutate_json(root, LEAK_PATH) do |leak|
      leak["subject_digest"] = "0" * 64
      leak["result_digest"] = Phase3CryptoEvidence.result_digest(leak)
    end
  end],
  ["tested-file-drift", "TESTED_INPUT_SHA256_MISMATCH", lambda do |root|
    File.open(File.join(root, DRIFT_INPUT_PATH), "a") { |file| file.write("\n") }
  end],
  ["tested-input-omitted", "TESTED_INPUT_SET_INVALID", lambda do |root|
    mutate_json(root, RUN_SUBJECT_PATH) { |subject| subject.fetch("inputs").pop }
  end],
  ["tested-role-forged", "TESTED_INPUT_SET_INVALID", lambda do |root|
    mutate_json(root, RUN_SUBJECT_PATH) do |subject|
      input = subject.fetch("inputs").first
      input["role"] = input["role"] == "test" ? "contract" : "test"
    end
  end],
  ["tested-path-forged", "TESTED_INPUT_SET_INVALID", lambda do |root|
    mutate_json(root, RUN_SUBJECT_PATH) do |subject|
      relative = ".planning/PRD-OBLIGATIONS.md"
      path = File.join(root, relative)
      subject.fetch("inputs").first.replace(
        "path" => relative, "mode" => format("%06o", File.stat(path).mode),
        "sha256" => Digest::SHA256.file(path).hexdigest, "role" => "contract"
      )
      subject.fetch("inputs").sort_by! { |input| input.fetch("path") }
    end
  end],
  ["tested-self-reference", "TESTED_INPUT_SELF_REFERENCE", lambda do |root|
    stale = "{}\n"
    write_json(root, SUBJECT_PATH, {})
    mutate_json(root, RUN_SUBJECT_PATH) do |subject|
      path = File.join(root, SUBJECT_PATH)
      subject.fetch("inputs") << {
        "path" => SUBJECT_PATH, "mode" => format("%06o", File.stat(path).mode),
        "sha256" => Digest::SHA256.hexdigest(stale), "role" => "contract"
      }
      subject.fetch("inputs").sort_by! { |input| input.fetch("path") }
    end
  end],
  ["output-symlink", "OUTPUT_FILE_INVALID", lambda do |root|
    outside = File.join(root, "outside-manifest.json")
    File.write(outside, "{}\n")
    File.symlink(outside, File.join(root, EVIDENCE_DIR, "evidence-manifest.json"))
  end]
]

producer_cases.each do |name, token, mutation|
  with_runner_fixture(name) do |root|
    mutation.call(root)
    assert_producer(name, root, expected_success: false, token: token)
    cases += 1
  end
end

2.times do |index|
  with_fixture("positive-#{index}") do |root|
    assert_result("positive fixture #{index}", root, expected_success: true, token: "phase03_crypto_evidence=PASS")
    cases += 1
  end
end

semantic_cases = [
  ["review-required", "INVENTORY_REVIEW_REQUIRED", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("candidates").first["classification"] = "REVIEW_REQUIRED" }
  end],
  ["target-deferred", "INVENTORY_TARGET_DEFERRED", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("targets").first["migration_state"] = "DEFERRED_OWNER" }
  end],
  ["current-deferred", "INVENTORY_CURRENT_DEFERRED", lambda do |root|
    mutate_json(root, INVENTORY_PATH) do |inventory|
      candidate = inventory.fetch("candidates").find { |row| row["classification"] == "DEFERRED_OWNER" }
      candidate["executable"] = true
    end
  end],
  ["capacity-conflict", "INVENTORY_CAPACITY_CONFLICT", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("targets").first["capacity_result"] = "CONFLICT" }
  end],
  ["stale-writer", "INVENTORY_SOURCE_SURFACE_UNRESOLVED", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("source_surfaces").first["disposition"] = "STALE_WRITER" }
  end],
  ["unknown-writer", "INVENTORY_SOURCE_SURFACE_SET_INVALID", lambda do |root|
    mutate_json(root, INVENTORY_PATH) do |inventory|
      inventory.fetch("source_surfaces") << Marshal.load(Marshal.dump(inventory.fetch("source_surfaces").first)).merge("id" => "unknown-writer")
    end
  end],
  ["raw-url-writer", "INVENTORY_RAW_URL_WRITER", lambda do |root|
    mutate_json(root, INVENTORY_PATH) do |inventory|
      inventory.fetch("source_surfaces").first.fetch("sources").first.fetch("tokens") << "setBusinessLicenseUrl"
    end
  end],
  ["missing-no-index-bulk", "INVENTORY_PROTECTED_TARGET_MISSING", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("targets").reject! { |row| row["id"] == "bulk_sending_items.mobile_encrypted" } }
  end],
  ["missing-no-index-uplink", "INVENTORY_PROTECTED_TARGET_MISSING", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("targets").reject! { |row| row["id"] == "uplink_records.mobile_encrypted" } }
  end],
  ["wrong-no-index-bulk", "INVENTORY_NO_INDEX_DISPOSITION_INVALID", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("targets").find { |row| row["id"] == "bulk_sending_items.mobile_encrypted" }["blind_index"] = "NOT_APPLICABLE" }
  end],
  ["wrong-no-index-uplink", "INVENTORY_NO_INDEX_DISPOSITION_INVALID", lambda do |root|
    mutate_json(root, INVENTORY_PATH) { |inventory| inventory.fetch("targets").find { |row| row["id"] == "uplink_records.mobile_encrypted" }["blind_index"] = "REQUIRED_VERSIONED_HMAC" }
  end]
]

semantic_cases.each do |name, token, mutation|
  with_fixture(name) do |root|
    mutation.call(root)
    refresh_bindings(root)
    assert_result(name, root, expected_success: false, token: token)
    cases += 1
  end
end

with_fixture("trace-forgery") do |root|
  relative = File.join(PHASE_DIR, "EVIDENCE/OBL-CRYPTO-STORAGE-001.json")
  mutate_json(root, relative) { |evidence| evidence["behavior_id"] = "crypto-storage-bootstrap-04" }
  manifest_path = File.join(EVIDENCE_DIR, "evidence-manifest.json")
  mutate_json(root, manifest_path) do |manifest|
    entry = manifest.fetch("entries").first
    entry["sha256"] = sha256(root, relative)
    entry["evidence_digest"] = Phase3CryptoEvidence.digest_value(read_json(root, relative))
  end
  assert_result("trace forgery", root, expected_success: false, token: "OBLIGATION_TRACE_MISMATCH")
  cases += 1
end

with_fixture("missing-obligation") do |root|
  mutate_json(root, File.join(EVIDENCE_DIR, "evidence-manifest.json")) { |manifest| manifest.fetch("entries").pop }
  assert_result("missing obligation", root, expected_success: false, token: "MANIFEST_ENTRY_COUNT_MISMATCH")
  cases += 1
end

with_fixture("adapter-spoof") do |root|
  path = File.join(RESULT_DIR, "phase03-protected-persistence-integration.json")
  mutate_json(root, path) { |result| result.fetch("adapter_identities").find { |row| row["id"] == "mysql-8" }["mode"] = "DETERMINISTIC" }
  refresh_bindings(root)
  assert_result("adapter spoof", root, expected_success: false, token: "ADAPTER_MODE_MISMATCH")
  cases += 1
end

with_fixture("adapter-missing") do |root|
  path = File.join(RESULT_DIR, "phase03-object-storage-integration.json")
  mutate_json(root, path) { |result| result.fetch("adapter_identities").reject! { |row| row["id"] == "minio" } }
  refresh_bindings(root)
  assert_result("adapter missing", root, expected_success: false, token: "ADAPTER_SET_MISMATCH")
  cases += 1
end

with_fixture("subject-live-drift") do |root|
  File.open(File.join(root, DRIFT_INPUT_PATH), "a") { |file| file.write("\n") }
  assert_result("subject live drift", root, expected_success: false, token: "TESTED_INPUT_SHA256_MISMATCH")
  cases += 1
end

with_fixture("child-result-digest") do |root|
  path = File.join(RESULT_DIR, "phase03-migration-integration.json")
  mutate_json(root, path) { |result| result["result_digest"] = "0" * 64 }
  assert_result("child result digest", root, expected_success: false, token: "SHA256_MISMATCH")
  cases += 1
end

with_fixture("validator-child-durable-omitted") do |root|
  obligation_id = "OBL-CRYPTO-STORAGE-001"
  path = File.join(RESULT_DIR, "phase03-protected-persistence-integration.json")
  mutate_json(root, path) do |child|
    child.fetch("facts").fetch("required_lane_ids").delete("durable-artifact-leak-scan")
    child.fetch("facts").fetch("lane_result_digests").delete("durable-artifact-leak-scan")
    child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
  end
  refresh_child_binding(root, obligation_id)
  assert_result(
    "validator child durable omitted", root, expected_success: false,
    token: "REQUIRED_LANES_MISMATCH"
  )
  cases += 1
end

with_fixture("validator-child-cleanup-omitted") do |root|
  obligation_id = "OBL-CRYPTO-STORAGE-002"
  path = File.join(RESULT_DIR, "phase03-object-storage-integration.json")
  mutate_json(root, path) do |child|
    child.fetch("facts").fetch("required_lane_ids").delete("fixture-cleanup")
    child.fetch("facts").fetch("lane_result_digests").delete("fixture-cleanup")
    child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
  end
  refresh_child_binding(root, obligation_id)
  assert_result(
    "validator child cleanup omitted", root, expected_success: false,
    token: "REQUIRED_LANES_MISMATCH"
  )
  cases += 1
end

with_fixture("validator-child-lane-digest-forged") do |root|
  obligation_id = "OBL-CRYPTO-STORAGE-003"
  path = File.join(RESULT_DIR, "phase03-pkcs11-fault-integration.json")
  mutate_json(root, path) do |child|
    child.fetch("facts").fetch("lane_result_digests")["default-maven"] = "0" * 64
    child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
  end
  refresh_child_binding(root, obligation_id)
  assert_result(
    "validator child lane digest forged", root, expected_success: false,
    token: "LANE_DIGEST_MISMATCH"
  )
  cases += 1
end

with_fixture("validator-child-registry-forged") do |root|
  obligation_id = "OBL-CRYPTO-STORAGE-004"
  path = File.join(RESULT_DIR, "phase03-migration-integration.json")
  mutate_json(root, path) do |child|
    child.fetch("facts")["registry_digest"] = "0" * 64
    child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
  end
  refresh_child_binding(root, obligation_id)
  assert_result(
    "validator child registry forged", root, expected_success: false,
    token: "REGISTRY_DIGEST_MISMATCH"
  )
  cases += 1
end

with_fixture("validator-child-subject-forged") do |root|
  obligation_id = "OBL-CRYPTO-STORAGE-001"
  path = File.join(RESULT_DIR, "phase03-protected-persistence-integration.json")
  mutate_json(root, path) do |child|
    child["subject_digest"] = "0" * 64
    child["result_digest"] = Phase3CryptoEvidence.result_digest(child)
  end
  refresh_child_binding(root, obligation_id)
  assert_result(
    "validator child subject forged", root, expected_success: false,
    token: "SUBJECT_MISMATCH"
  )
  cases += 1
end

with_fixture("validator-root-lane-omitted") do |root|
  mutate_json(root, AGGREGATE_PATH) do |aggregate|
    aggregate.fetch("lane_result_digests").delete("fixture-cleanup")
    aggregate["result_digest"] = Phase3CryptoEvidence.result_digest(aggregate)
  end
  assert_result(
    "validator root lane omitted", root, expected_success: false,
    token: "ROOT_AGGREGATE_LANE_SET_MISMATCH"
  )
  cases += 1
end

with_fixture("validator-root-lane-mode-spoof") do |root|
  path = File.join(RESULT_DIR, "lanes/fixture-cleanup.json")
  mutate_json(root, path) do |lane|
    lane["mode"] = "DETERMINISTIC"
    lane["result_digest"] = Phase3CryptoEvidence.result_digest(lane)
  end
  assert_result(
    "validator root lane mode spoof", root, expected_success: false,
    token: "ROOT_LANE_MODE_MISMATCH"
  )
  cases += 1
end

with_fixture("inventory-digest") do |root|
  mutate_json(root, File.join(EVIDENCE_DIR, "evidence-manifest.json")) { |manifest| manifest.fetch("inventory")["accepted_digest"] = "0" * 64 }
  assert_result("inventory digest", root, expected_success: false, token: "INVENTORY_ACCEPTED_DIGEST_MISMATCH")
  cases += 1
end

with_fixture("leak-target") do |root|
  mutate_json(root, LEAK_PATH) { |leak| leak.fetch("targets").pop }
  refresh_bindings(root)
  assert_result("leak target", root, expected_success: false, token: "LEAK_TARGET_SET_INVALID")
  cases += 1
end

with_fixture("leak-match") do |root|
  mutate_json(root, LEAK_PATH) { |leak| leak.fetch("targets").first["prohibited_matches"] = 1 }
  refresh_bindings(root)
  assert_result("leak match", root, expected_success: false, token: "LEAK_PROHIBITED_MATCH")
  cases += 1
end

with_fixture("leak-reader-spoof") do |root|
  mutate_json(root, LEAK_PATH) do |leak|
    leak.fetch("targets").find { |target| target["id"] == "evidence" }["reader_identity"] = "phase03-leak-scanner"
  end
  refresh_bindings(root)
  assert_result("leak reader spoof", root, expected_success: false, token: "LEAK_READER_IDENTITY_INVALID")
  cases += 1
end

with_fixture("leak-self-attestation") do |root|
  subject = read_json(root, SUBJECT_PATH)
  leak_snapshot = File.binread(File.join(root, LEAK_PATH))
  subject.fetch("inputs") << {
    "path" => LEAK_PATH,
    "mode" => format("%06o", File.stat(File.join(root, LEAK_PATH)).mode),
    "sha256" => Digest::SHA256.hexdigest(leak_snapshot),
    "role" => "test"
  }
  subject["inputs"].sort_by! { |input| input.fetch("path") }
  write_json(root, SUBJECT_PATH, subject)
  refresh_bindings(root)
  assert_result("leak self attestation", root, expected_success: false, token: "LEAK_SELF_ATTESTATION_REJECTED")
  cases += 1
end

with_fixture("leak-digest") do |root|
  mutate_json(root, LEAK_PATH) { |leak| leak["result_digest"] = "0" * 64 }
  assert_result("leak digest", root, expected_success: false, token: "SHA256_MISMATCH")
  cases += 1
end

prohibited_values = {
  "plaintext-canary" => ["plaintext canary sample", "PROHIBITED_PLAINTEXT_CANARY"],
  "private-key" => ["-----BEGIN PRIVATE KEY-----", "PROHIBITED_PRIVATE_KEY"],
  "pin" => ["PIN=1234", "PROHIBITED_PIN"],
  "raw-token" => ["ocap_v1_example", "PROHIBITED_RAW_TOKEN"],
  "raw-url" => ["https://example.invalid/object", "PROHIBITED_RAW_URL"],
  "ciphertext" => ["ciphertext: opaque-value", "PROHIBITED_CIPHERTEXT_BODY"],
  "provider-text" => ["provider response: rejected", "PROHIBITED_PROVIDER_TEXT"],
  "absolute-path" => ["/tmp/phase03-result", "PROHIBITED_ABSOLUTE_PATH"]
}

prohibited_values.each do |name, (value, token)|
  with_fixture(name) do |root|
    path = File.join(RESULT_DIR, "phase03-pkcs11-fault-integration.json")
    mutate_json(root, path) { |result| result.fetch("facts")["diagnostic"] = value }
    refresh_bindings(root)
    assert_result(name, root, expected_success: false, token: token)
    cases += 1
  end
end

with_fixture("prohibited-key") do |root|
  path = File.join(RESULT_DIR, "phase03-pkcs11-fault-integration.json")
  mutate_json(root, path) { |result| result.fetch("facts")["secret"] = "redacted-value" }
  refresh_bindings(root)
  assert_result("prohibited key", root, expected_success: false, token: "PROHIBITED_CONTENT_KEY")
  cases += 1
end

with_fixture("schema-drift") do |root|
  path = File.join(EVIDENCE_DIR, "schema/phase03-evidence-manifest.schema.json")
  mutate_json(root, path) { |schema| schema.fetch("properties").fetch("entries")["minItems"] = 3 }
  assert_result("schema drift", root, expected_success: false, token: "MANIFEST_SCHEMA_ENTRY_COUNT_MISMATCH")
  cases += 1
end

puts "phase03_crypto_evidence_tests=PASS cases=#{cases} positive=2 producer_targets_created=4"
