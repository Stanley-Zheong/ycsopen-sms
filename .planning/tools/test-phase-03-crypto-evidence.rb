#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"
require_relative "phase3-crypto-evidence"

REPOSITORY_ROOT = File.expand_path("../..", __dir__)
VALIDATOR = File.join(__dir__, "validate-phase-03-crypto-evidence.rb")
PHASE_DIR = ".planning/phases/03-crypto-storage-bootstrap"
EVIDENCE_DIR = File.join(PHASE_DIR, "EVIDENCE")
RESULT_DIR = "core/target/phase03/results"
INVENTORY_PATH = "core/src/main/resources/security/protected-data-inventory.json"
SUBJECT_PATH = File.join(EVIDENCE_DIR, "tested-inputs.json")
LEAK_PATH = File.join(RESULT_DIR, "complete-leak-result.json")
INVENTORY_RESULT_PATH = File.join(RESULT_DIR, "protected-inventory-result.json")
SOURCE_PATH = "fixtures/phase03-contract-input.txt"

def write_json(root, relative, value)
  path = File.join(root, relative)
  FileUtils.mkdir_p(File.dirname(path))
  File.write(path, JSON.pretty_generate(value) + "\n")
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

def make_ready_inventory(root)
  inventory = read_json(root, INVENTORY_PATH)
  inventory["obligation_readiness"] = {
    "status" => "READY",
    "blocking_surface_ids" => [],
    "reason" => "Every current protected boundary is represented by accepted executable evidence."
  }
  inventory.fetch("source_surfaces").each do |surface|
    surface["disposition"] = "PROTECTED_BOUNDARY_ADOPTED"
    surface["obligation_blocking"] = false
    surface["sources"].each do |source|
      source["tokens"] = source.fetch("tokens").map do |token|
        token.match?(/(?:set|String\s+)(?:BusinessLicense|LegalRepIdFront|LegalRepIdBack|ShortlinkDomainProof|TrademarkProof|Evidence|File)Url/) ? "ProtectedBoundaryWrite" : token
      end
    end
  end
  inventory.fetch("targets").each do |target|
    target.fetch("writers").each do |source|
      source["tokens"] = source.fetch("tokens").map do |token|
        token.match?(/(?:set|String\s+)(?:BusinessLicense|LegalRepIdFront|LegalRepIdBack|ShortlinkDomainProof|TrademarkProof|Evidence|File)Url/) ? "ProtectedBoundaryWrite" : token
      end
    end
  end
  write_json(root, INVENTORY_PATH, inventory)
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

def build_fixture(root)
  copy_contract(root)
  make_ready_inventory(root)
  source_file = File.join(root, SOURCE_PATH)
  FileUtils.mkdir_p(File.dirname(source_file))
  File.write(source_file, "phase03 evidence contract input\n")
  File.chmod(0o644, source_file)

  input = {
    "path" => SOURCE_PATH,
    "mode" => format("%06o", File.stat(source_file).mode),
    "sha256" => Digest::SHA256.file(source_file).hexdigest,
    "role" => "contract"
  }
  subject = {
    "schema_version" => Phase3CryptoEvidence::TESTED_INPUTS_SCHEMA,
    "phase" => Phase3CryptoEvidence::PHASE,
    "owner" => Phase3CryptoEvidence::OWNER,
    "inputs" => [input]
  }
  write_json(root, SUBJECT_PATH, subject)
  subject_digest = Phase3CryptoEvidence.digest_value(subject.fetch("inputs"))

  inventory_digest = Phase3CryptoEvidence.digest_value(read_json(root, INVENTORY_PATH))
  inventory_result = child_result(
    check_id: "phase03-protected-inventory",
    layer: "static",
    obligation_ids: Phase3CryptoEvidence::OBLIGATIONS.keys,
    case_ids: Phase3CryptoEvidence::OBLIGATIONS.values.map { |definition| definition.fetch("case_id") },
    adapters: ["phase03-inventory-validator"],
    subject_digest: subject_digest,
    facts: { "blocking_dispositions" => [], "inventory_digest" => inventory_digest, "result" => "ACCEPTED" }
  )
  write_json(root, INVENTORY_RESULT_PATH, inventory_result)

  leak = {
    "schema_version" => Phase3CryptoEvidence::LEAK_RESULT_SCHEMA,
    "phase" => Phase3CryptoEvidence::PHASE,
    "check_id" => "phase03-complete-leak-scan",
    "subject_digest" => subject_digest,
    "status" => "PASS",
    "exit_code" => 0,
    "targets" => Phase3CryptoEvidence::LEAK_TARGETS.to_a.sort.map do |id|
      {
        "id" => id,
        "reader_identity" => "phase03-leak-scanner",
        "scanned_items" => 1,
        "prohibited_matches" => 0,
        "sensitivity_status" => "DETECTED_SEEDED_MUTATION"
      }
    end
  }
  leak["result_digest"] = Phase3CryptoEvidence.result_digest(leak)
  write_json(root, LEAK_PATH, leak)

  Phase3CryptoEvidence::OBLIGATIONS.each do |obligation_id, definition|
    relative = File.join(RESULT_DIR, "#{definition.fetch('check_id')}.json")
    write_json(root, relative, child_result(
      check_id: definition.fetch("check_id"),
      layer: definition.fetch("layer"),
      obligation_ids: [obligation_id],
      case_ids: [definition.fetch("case_id")],
      adapters: definition.fetch("adapters"),
      subject_digest: subject_digest
    ))
  end
  refresh_bindings(root)
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

cases = 0

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
  File.write(File.join(root, SOURCE_PATH), "changed contract input\n")
  assert_result("subject live drift", root, expected_success: false, token: "TESTED_INPUT_SHA256_MISMATCH")
  cases += 1
end

with_fixture("child-result-digest") do |root|
  path = File.join(RESULT_DIR, "phase03-migration-integration.json")
  mutate_json(root, path) { |result| result["result_digest"] = "0" * 64 }
  assert_result("child result digest", root, expected_success: false, token: "SHA256_MISMATCH")
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

puts "phase03_crypto_evidence_tests=PASS cases=#{cases} positive=2 pass_targets_created=0"
