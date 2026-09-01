#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "open3"
require "stringio"
require "tmpdir"

require_relative "run_checks"

CASES = { count: 0 }

def assert(condition, message)
  raise message unless condition
  CASES[:count] += 1
end

def rejects(message)
  yield
  raise "mutation unexpectedly passed: #{message}"
rescue Phase03RunChecks::ConfigurationError
  CASES[:count] += 1
end

Phase03RunChecks.registry_contract!
ids = Phase03RunChecks::CHECKS.map { |row| row.fetch("id") }
assert(ids.length == 13, "fixed registry count drifted")
assert(ids.uniq.length == ids.length, "registry IDs are not unique")
assert(ids.include?("default-maven"), "default Maven lane missing")
assert(ids.include?("real-service-integration"), "real-service lane missing")
assert(ids.last == "fixture-cleanup", "cleanup must remain the final lane")
assert(Phase03RunChecks::REAL_TESTS.length == 7, "real test union drifted")
assert(Phase03RunChecks::REAL_TESTS.include?("Phase03RotationRecoveryIntegrationTest"),
       "rotation/recovery lane missing")

duplicate = Marshal.load(Marshal.dump(Phase03RunChecks::CHECKS))
duplicate << duplicate.first
rejects("duplicate ID") { Phase03RunChecks.registry_contract!(duplicate) }

missing = Marshal.load(Marshal.dump(Phase03RunChecks::CHECKS))
missing.reject! { |row| row.fetch("id") == "fixture-cleanup" }
rejects("missing cleanup") { Phase03RunChecks.registry_contract!(missing) }

identity_spoof = Marshal.load(Marshal.dump(Phase03RunChecks::CHECKS))
identity_spoof.find { |row| row.fetch("id") == "real-service-integration" }["mode"] = "DETERMINISTIC"
rejects("real identity spoof") { Phase03RunChecks.registry_contract!(identity_spoof) }

shell = Marshal.load(Marshal.dump(Phase03RunChecks::CHECKS))
shell.first["argv"] = ["/bin/sh", "-c", "true"]
rejects("shell command") { Phase03RunChecks.registry_contract!(shell) }

real_drift = Marshal.load(Marshal.dump(Phase03RunChecks::CHECKS))
real_drift.find { |row| row.fetch("id") == "real-service-integration" }
          .fetch("argv").map! { |part| part.gsub(/,?Phase03RotationRecoveryIntegrationTest/, "") }
rejects("real test omission") { Phase03RunChecks.registry_contract!(real_drift) }

assert(Phase03RunChecks.aggregate_status(%w[PASS PASS]) == "PASS", "PASS aggregation failed")
assert(Phase03RunChecks.aggregate_status(%w[PASS BLOCKED]) == "BLOCKED", "BLOCKED dominance failed")
assert(Phase03RunChecks.aggregate_status(%w[BLOCKED FAIL]) == "FAIL", "FAIL dominance failed")
rejects("empty status") { Phase03RunChecks.aggregate_status([]) }
rejects("unknown status") { Phase03RunChecks.aggregate_status(["FORGED_PASS"]) }

subject = Phase03RunChecks.build_subject(Phase03RunChecks::ROOT)
paths = subject.fetch("inputs").map { |row| row.fetch("path") }
assert(paths == paths.sort && paths.uniq.length == paths.length, "subject inputs not canonical")
assert(paths.include?("scripts/lib/phase-03/run_checks.rb"), "runner absent from subject")
assert(paths.include?("core/pom.xml"), "Maven config absent from subject")
assert(paths.none? { |path| path.start_with?("core/target/") }, "generated output entered subject")
assert(paths.none? { |path| path.end_with?("SUMMARY.md", "TODO.md", "ITERATIONS.md") },
       "mutable closure record entered subject")
assert(paths.any? { |path| path.include?("/EVIDENCE/schema/") }, "evidence schemas absent")
assert(paths.include?(".planning/phases/03-crypto-storage-bootstrap/EVIDENCE/README.md"),
       "evidence contract README absent")
producer_outputs = %w[tested-inputs.json evidence-manifest.json OBL-CRYPTO-STORAGE-001.json
                      OBL-CRYPTO-STORAGE-002.json OBL-CRYPTO-STORAGE-003.json
                      OBL-CRYPTO-STORAGE-004.json]
assert(paths.none? { |path| producer_outputs.include?(File.basename(path)) },
       "producer output entered tested subject")
assert(subject.fetch("inputs").all? { |row| row.fetch("sha256").match?(Phase03RunChecks::SHA256) },
       "subject digest invalid")

Dir.mktmpdir("phase03-runner-root-") do |root|
  FileUtils.mkdir_p(File.join(root, "core/target/phase03"))
  accepted = Phase03RunChecks.contained_result_root(root, "core/target/phase03/results")
  assert(accepted.end_with?("core/target/phase03/results"), "contained result rejected")
  rejects("absolute output") { Phase03RunChecks.contained_result_root(root, "/tmp/results") }
  rejects("traversal output") do
    Phase03RunChecks.contained_result_root(root, "core/target/phase03/../outside")
  end
  File.symlink(File.join(root, "core"), File.join(root, "core/target/phase03/link"))
  rejects("symlink output") do
    Phase03RunChecks.contained_result_root(root, "core/target/phase03/link/results")
  end
end

definition = {
  "id" => "fixture-pass", "layer" => "unit", "mode" => "DETERMINISTIC",
  "argv" => ["/usr/bin/env", "ruby", "-e", "STDOUT.write(ENV.fetch('PHASE03_TEST_CANARY'))"],
  "timeout_seconds" => 30, "obligation_ids" => Phase03RunChecks::OBLIGATIONS
}
lane = Phase03RunChecks.execute(
  Phase03RunChecks::ROOT, definition, "PHASE03_TEST_CANARY" => "credential=must-not-persist"
)
assert(lane.fetch("status") == "PASS", "passing child failed")
assert(!JSON.generate(lane).include?("must-not-persist"), "raw child output persisted")
assert(lane.fetch("result_digest") == Phase03RunChecks.digest(
  lane.reject { |key, _value| key == "result_digest" }
), "lane digest invalid")

failed = Marshal.load(Marshal.dump(definition))
failed["id"] = "fixture-fail"
failed["argv"] = ["/usr/bin/env", "ruby", "-e", "exit 9"]
assert(Phase03RunChecks.execute(Phase03RunChecks::ROOT, failed).fetch("status") == "FAIL",
       "nonzero child did not fail")

blocked = Marshal.load(Marshal.dump(definition))
blocked["id"] = "fixture-blocked"
blocked["argv"] = ["phase03-command-that-does-not-exist"]
assert(Phase03RunChecks.execute(Phase03RunChecks::ROOT, blocked).fetch("status") == "BLOCKED",
       "missing executable did not block")

out = StringIO.new
err = StringIO.new
assert(Phase03RunChecks.source_audit(out: out, err: err).zero?, "source audit failed: #{err.string}")
assert(out.string.include?("phase03_source_audit=PASS"), "source audit marker missing")

assert(Phase03RunChecks.cli([], io: StringIO.new, err: StringIO.new) == 2,
       "missing options did not block")
assert(Phase03RunChecks.cli(["--unknown"], io: StringIO.new, err: StringIO.new) == 2,
       "unknown option did not block")

Dir.mktmpdir("phase03-leak-binding-") do |root|
  subject_digest = "a" * 64
  java = {
    "schema_version" => Phase3CryptoEvidence::LEAK_RESULT_SCHEMA,
    "phase" => Phase3CryptoEvidence::PHASE,
    "check_id" => "phase03-complete-leak-scan", "subject_digest" => subject_digest,
    "status" => "PASS", "exit_code" => 0,
    "targets" => Phase3CryptoEvidence::LEAK_TARGETS.to_a.sort.map do |id|
      { "id" => id, "reader_identity" => Phase3CryptoEvidence::LEAK_READER_IDENTITIES.fetch(id),
        "scanned_items" => 1, "prohibited_matches" => 0,
        "sensitivity_status" => "DETECTED_SEEDED_MUTATION" }
    end
  }
  java["result_digest"] = Phase3CryptoEvidence.result_digest(java)
  artifact = {
    "schema_version" => "phase03-artifact-leak-scan-v1",
    "phase" => Phase3CryptoEvidence::PHASE, "check_id" => "phase03-artifact-leak-scan",
    "status" => "PASS", "exit_code" => 0, "input_digest" => "b" * 64,
    "targets" => %w[evidence reports].map do |id|
      { "id" => id, "reader_identity" => "phase03-artifact-scanner", "scanned_items" => 1,
        "prohibited_matches" => 0, "sensitivity_status" => "DETECTED_SEEDED_MUTATION" }
    end
  }
  artifact["result_digest"] = Phase3CryptoEvidence.result_digest(artifact)
  java_path = File.join(root, Phase03RunChecks::JAVA_LEAK_RESULT)
  artifact_path = File.join(root, Phase03RunChecks::ARTIFACT_LEAK_RESULT)
  FileUtils.mkdir_p(File.dirname(java_path))
  File.write(java_path, JSON.generate(java))
  File.write(artifact_path, JSON.generate(artifact))
  output = File.join(root, "core/target/phase03/results/complete-leak-result.json")
  Phase03RunChecks.persist_complete_leak_result(root, subject_digest, output)
  assert(JSON.parse(File.read(output)).fetch("result_digest") == java.fetch("result_digest"),
         "complete leak result was rewritten")

  forged = Marshal.load(Marshal.dump(artifact))
  forged.fetch("targets").first["prohibited_matches"] = 1
  forged["result_digest"] = Phase3CryptoEvidence.result_digest(forged)
  File.write(artifact_path, JSON.generate(forged))
  rejects("artifact leak forgery") do
    Phase03RunChecks.persist_complete_leak_result(root, subject_digest, output)
  end

  File.write(artifact_path, JSON.generate(artifact))
  rejects("leak subject drift") do
    Phase03RunChecks.persist_complete_leak_result(root, "c" * 64, output)
  end
end

Phase03RunChecks::CHILD_BINDINGS.each do |obligation_id, binding|
  assert(Phase03RunChecks::OBLIGATIONS.include?(obligation_id), "unknown child obligation")
  assert((binding.fetch("lanes") - ids).empty?, "child references unknown lane")
  assert(binding.fetch("lanes").include?("real-service-integration"),
         "child lacks real-service binding")
  assert(binding.fetch("lanes").include?("fixture-cleanup"), "child lacks cleanup binding")
end

stdout, stderr, status = Open3.capture3(
  File.join(Phase03RunChecks::ROOT, "scripts/verify-phase-03"), "--unknown",
  chdir: Phase03RunChecks::ROOT
)
assert(status.exitstatus == 2, "wrapper unknown option exit drifted")
assert((stdout + stderr).include?("phase03_root_verification=BLOCKED"),
       "wrapper stable diagnostic missing")

puts "phase03_run_checks_tests=PASS cases=#{CASES.fetch(:count)} checks=#{ids.length}"
