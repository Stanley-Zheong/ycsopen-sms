#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"

REPOSITORY_ROOT = File.expand_path("../..", __dir__)
VALIDATOR = File.join(__dir__, "validate-phase-03-protected-inventory.rb")
MANIFEST = "core/src/main/resources/security/protected-data-inventory.json"
SQL_SCHEMA = "core/src/main/resources/db/migration/V1__init_schema.sql"
SOURCE_ROOT = "core/src/main/java"
CONTRACT_FILES = [
  MANIFEST,
  SQL_SCHEMA,
  SOURCE_ROOT,
  ".planning/phases/03-crypto-storage-bootstrap/ENVELOPE-CONTRACT.md",
  ".planning/phases/03-crypto-storage-bootstrap/EVIDENCE/schema/protected-data-inventory.schema.json"
].freeze

def copy_fixture(root)
  CONTRACT_FILES.each do |relative|
    source = File.join(REPOSITORY_ROOT, relative)
    destination = File.join(root, relative)
    FileUtils.mkdir_p(File.dirname(destination))
    if File.directory?(source)
      FileUtils.cp_r(source, destination)
    else
      FileUtils.cp(source, destination)
    end
  end
end

def run_validator(root)
  Open3.capture3(
    RbConfig.ruby,
    VALIDATOR,
    "--manifest", MANIFEST,
    "--schema", SQL_SCHEMA,
    "--source-root", SOURCE_ROOT,
    "--acceptance",
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

def mutate_manifest(root)
  path = File.join(root, MANIFEST)
  manifest = JSON.parse(File.read(path, encoding: "UTF-8"))
  yield manifest
  File.write(path, JSON.pretty_generate(manifest) + "\n")
end

def with_fixture(name)
  Dir.mktmpdir("phase03-inventory-#{name}-") do |root|
    copy_fixture(root)
    yield root
  end
end

cases = 0

with_fixture("baseline") do |root|
  assert_result("baseline", root, expected_success: true, token: "protected_inventory=PASS")
  assert_result("baseline readiness", root, expected_success: true, token: "obligation_readiness=READY")
  cases += 1
end

%w[bulk_sending_items.mobile_encrypted uplink_records.mobile_encrypted].each do |target_id|
  with_fixture("missing-protected") do |root|
    mutate_manifest(root) { |manifest| manifest["targets"].reject! { |target| target["id"] == target_id } }
    assert_result("missing #{target_id}", root, expected_success: false, token: "PROTECTED_TARGET_MISSING")
    cases += 1
  end
end

with_fixture("review-required") do |root|
  mutate_manifest(root) { |manifest| manifest["candidates"].first["classification"] = "REVIEW_REQUIRED" }
  assert_result("review required", root, expected_success: false, token: "CANDIDATE_REVIEW_REQUIRED")
  cases += 1
end

with_fixture("current-deferred") do |root|
  mutate_manifest(root) do |manifest|
    manifest["targets"].find { |target| target["id"] == "message_tasks.mobile_encrypted" }["migration_state"] = "DEFERRED_OWNER"
  end
  assert_result("current target deferred", root, expected_success: false, token: "TARGET_DEFERRED_FORBIDDEN")
  cases += 1
end

with_fixture("candidate-current-deferred") do |root|
  mutate_manifest(root) do |manifest|
    candidate = manifest["candidates"].find { |row| row["id"] == "users.password_hash" }
    candidate["classification"] = "DEFERRED_OWNER"
    candidate["future_owner"] = "console-identity-platform-rbac"
  end
  assert_result("current candidate deferred", root, expected_success: false, token: "CANDIDATE_DEFERRED_INVALID")
  cases += 1
end

with_fixture("capacity") do |root|
  mutate_manifest(root) do |manifest|
    target = manifest["targets"].find { |row| row["id"] == "users.phone_encrypted" }
    target["maximum_complete_envelope_bytes"] = 157
  end
  assert_result("computed capacity drift", root, expected_success: false, token: "TARGET_ENVELOPE_CAPACITY_INVALID")
  cases += 1
end

with_fixture("runtime-capacity") do |root|
  path = File.join(root, SQL_SCHEMA)
  sql = File.read(path, encoding: "UTF-8")
  changed = sql.sub("mobile_encrypted   VARBINARY(255) NOT NULL COMMENT '🔒'", "mobile_encrypted   VARBINARY(155) NOT NULL COMMENT '🔒'")
  abort "runtime capacity fixture did not mutate" if changed == sql
  File.write(path, changed)
  assert_result("runtime capacity conflict", root, expected_success: false, token: "TARGET_DATABASE_CAPACITY_CONFLICT")
  cases += 1
end

with_fixture("digest") do |root|
  mutate_manifest(root) do |manifest|
    manifest["digest_targets"].reject! { |row| row["id"] == "third_party_risk_check_logs.mobile_hash" }
  end
  assert_result("missing schema-only digest", root, expected_success: false, token: "DIGEST_TARGET_MISSING")
  cases += 1
end

with_fixture("no-index") do |root|
  mutate_manifest(root) do |manifest|
    target = manifest["targets"].find { |row| row["id"] == "bulk_sending_items.mobile_encrypted" }
    target["blind_index"] = "NOT_APPLICABLE"
  end
  assert_result("no-index disposition drift", root, expected_success: false, token: "TARGET_BLIND_INDEX_INVALID")
  cases += 1
end

with_fixture("false-ready") do |root|
  mutate_manifest(root) do |manifest|
    blocker = manifest["source_surfaces"].first
    blocker["obligation_blocking"] = true
    manifest["obligation_readiness"]["status"] = "READY"
    manifest["obligation_readiness"]["blocking_surface_ids"] = [blocker["id"]]
  end
  assert_result("false obligation ready", root, expected_success: false, token: "OBLIGATION_FALSE_READY")
  cases += 1
end

with_fixture("unknown-writer") do |root|
  path = File.join(root, SOURCE_ROOT, "com/ycsopen/sms/core/UnknownProtectedWriter.java")
  File.write(path, "class UnknownProtectedWriter { void write(Tenant tenant, String value) { tenant.setEvidenceUrl(value); } }\n")
  assert_result("unknown writer", root, expected_success: false, token: "SOURCE_WRITER_UNKNOWN")
  cases += 1
end

with_fixture("unbounded-read") do |root|
  path = File.join(root, SOURCE_ROOT, "com/ycsopen/sms/core/UnboundedProtectedReader.java")
  File.write(path, "class UnboundedProtectedReader { byte[] read(java.nio.file.Path path) throws Exception { return java.nio.file.Files.readAllBytes(path); } }\n")
  assert_result("unbounded allocation", root, expected_success: false, token: "SOURCE_UNBOUNDED_LENGTH_ALLOCATION")
  cases += 1
end

with_fixture("source-drift") do |root|
  path = File.join(root, SOURCE_ROOT, "com/ycsopen/sms/core/service/message/MessageSubmitService.java")
  content = File.read(path, encoding: "UTF-8")
  changed = content.sub("messageTaskProtectionAdapter.save", "messageTaskProtectionAdapter.unreviewedSave")
  abort "source drift fixture did not mutate" if changed == content
  File.write(path, changed)
  assert_result("source token drift", root, expected_success: false, token: "TOKEN_MISSING")
  cases += 1
end

with_fixture("unknown-inline") do |root|
  path = File.join(root, SQL_SCHEMA)
  sql = File.read(path, encoding: "UTF-8")
  changed = sql.sub("phone_encrypted       VARBINARY(255)", "phone_encrypted       VARBINARY(255)\n    api_token_encrypted   VARBINARY(255),")
  abort "unknown inline fixture did not mutate" if changed == sql
  File.write(path, changed)
  assert_result("unknown inline target", root, expected_success: false, token: "DISCOVERED_INLINE_TARGET_UNKNOWN")
  cases += 1
end

with_fixture("unknown-candidate") do |root|
  path = File.join(root, SQL_SCHEMA)
  sql = File.read(path, encoding: "UTF-8")
  changed = sql.sub("avatar_url            VARCHAR(200),", "avatar_url            VARCHAR(200),\n    secret_callback_url   VARCHAR(255),")
  abort "unknown candidate fixture did not mutate" if changed == sql
  File.write(path, changed)
  assert_result("unknown candidate", root, expected_success: false, token: "DISCOVERED_CANDIDATE_UNKNOWN")
  cases += 1
end

with_fixture("duplicate") do |root|
  mutate_manifest(root) { |manifest| manifest["targets"] << Marshal.load(Marshal.dump(manifest["targets"].first)) }
  assert_result("duplicate target", root, expected_success: false, token: "TARGET_DUPLICATE")
  cases += 1
end

puts "phase03_protected_inventory_tests=PASS cases=#{cases}"
