#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "stringio"
require "tmpdir"

require_relative "run_checks"

ROOT = File.expand_path("../../..", __dir__)
SOURCE_PHASE = ".planning/phases/01-engineering-verification-foundation"
SOURCE_EVIDENCE = "#{SOURCE_PHASE}/EVIDENCE"

def assert(condition, message)
  abort(message) unless condition
end

def prepare_root(root)
  [
    "#{SOURCE_EVIDENCE}/local-chrome-entry.json",
    "#{SOURCE_PHASE}/ENTRY-REVIEW.md"
  ].each do |relative|
    destination = File.join(root, relative)
    FileUtils.mkdir_p(File.dirname(destination))
    FileUtils.cp(File.join(ROOT, relative), destination, preserve: true)
  end
  FileUtils.mkdir_p(File.join(root, "fixture"))
  File.write(File.join(root, "fixture/input.txt"), "synthetic runner input\n")
end

def copy_regular(root, relative)
  source = File.join(ROOT, relative)
  destination = File.join(root, relative)
  FileUtils.mkdir_p(File.dirname(destination))
  FileUtils.cp(source, destination, preserve: true)
end

def read_json(root, relative)
  JSON.parse(File.read(File.join(root, relative)))
end

def write_json(root, relative, value)
  VerificationEvidence.atomic_write_json(File.join(root, relative), value)
end

def write_ordered_json(root, relative, value)
  File.write(File.join(root, relative), "#{JSON.generate(value)}\n")
end

def expected_runtime_facts(root, runtime)
  run = runtime.fetch("run")
  scenario = run.fetch("scenario")
  artifacts = run.fetch("artifacts")
  identity = run.fetch("runtime")
  {
    "path" => Phase01RunChecks::LOCAL_CHROME_RUNTIME,
    "sha256" => Digest::SHA256.file(File.join(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME)).hexdigest,
    "subject_manifest_path" => scenario.dig("subject", "manifestPath"),
    "subject_manifest_digest" => scenario.dig("subject", "manifestDigest"),
    "tested_subject_digest" => scenario.dig("subject", "testedSubjectDigest"),
    "brand" => identity.fetch("brand"),
    "full_version" => identity.fetch("fullVersion"),
    "major" => identity.fetch("major"),
    "executable_path" => identity.fetch("canonicalPath"),
    "viewport" => identity.fetch("viewport"),
    "launch_succeeded" => identity.dig("launch", "succeeded"),
    "scenario_id" => scenario.dig("contract", "scenarioId"),
    "visual_rule_id" => scenario.dig("contract", "visualRuleId"),
    "response_status" => scenario.dig("response", "status"),
    "response_body_sha256" => scenario.dig("response", "bodySha256"),
    "marker_name" => scenario.dig("response", "marker", "name"),
    "marker_value" => scenario.dig("response", "marker", "value"),
    "screenshot_sha256" => artifacts.dig("screenshot", "sha256"),
    "dom_sha256" => artifacts.dig("dom", "sha256"),
    "transcript_sha256" => artifacts.dig("transcript", "sha256"),
    "console_sha256" => artifacts.dig("console", "sha256")
  }
end

def prepare_portable_root(root)
  inputs = Phase01RunChecks.subject_registries.values.flatten.map { |entry| entry.fetch("path") }.uniq
  (inputs + [VerificationEvidence::ENTRY_EVIDENCE_PATH, VerificationEvidence::ENTRY_REVIEW_PATH]).uniq.each do |relative|
    copy_regular(root, relative)
  end
  copy_regular(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME)
  VerificationEvidence.obligation_registry.each do |definition|
    copy_regular(root, File.join(Phase01RunChecks::EVIDENCE_DIR, "#{definition.fetch('obligation_id')}.json"))
  end

  subject = VerificationEvidence.build_subject_manifest(
    root: root,
    registries: Phase01RunChecks.subject_registries,
    manifest_path: Phase01RunChecks::DURABLE_SUBJECT
  )
  binding = {
    "path" => Phase01RunChecks::DURABLE_SUBJECT,
    "subject_manifest_digest" => VerificationEvidence.subject_manifest_digest(subject),
    "tested_subject_digest" => VerificationEvidence.tested_subject_digest(subject.fetch("inputs"))
  }
  runtime = read_json(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME)
  runtime_subject = runtime.fetch("run").fetch("scenario").fetch("subject")
  runtime_subject["manifestPath"] = binding.fetch("path")
  runtime_subject["manifestDigest"] = binding.fetch("subject_manifest_digest")
  runtime_subject["testedSubjectDigest"] = binding.fetch("tested_subject_digest")
  runtime_subject.fetch("health")["subject_manifest_digest"] = binding.fetch("subject_manifest_digest")
  runtime_subject.fetch("health")["tested_subject_digest"] = binding.fetch("tested_subject_digest")
  write_ordered_json(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME, runtime)

  runtime_facts = expected_runtime_facts(root, runtime)
  runtime_snapshot = VerificationEvidence.verified_local_file(
    root,
    Phase01RunChecks::LOCAL_CHROME_RUNTIME,
    [],
    "PORTABLE_TEST_RUNTIME"
  )
  portable_runtime_facts = Phase01RunChecks.portable_runtime_facts(
    runtime,
    runtime_snapshot: runtime_snapshot
  )
  assert(portable_runtime_facts == runtime_facts, "portable runtime facts diverged from formal producer facts")
  entries = VerificationEvidence.obligation_registry.map do |definition|
    obligation_id = definition.fetch("obligation_id")
    relative = File.join(Phase01RunChecks::EVIDENCE_DIR, "#{obligation_id}.json")
    summary = read_json(root, relative)
    summary["subject_manifest_path"] = binding.fetch("path")
    summary["subject_manifest_digest"] = binding.fetch("subject_manifest_digest")
    summary["tested_subject_digest"] = binding.fetch("tested_subject_digest")
    summary["runtime"] = obligation_id == "OBL-NFR-BROWSER" ? runtime_facts : nil
    if obligation_id == "OBL-FOUND-TRACE-003"
      scenario_result = summary.fetch("check_results").find do |result|
        result["check_id"] == "login-scenario-contract" ||
          result["check_id"] == "login-scenario-visual-local-chrome"
      end
      assert(scenario_result, "portable fixture is missing the login scenario result")
      scenario_result["check_id"] = "login-scenario-visual-local-chrome"
      scenario_result["layer"] = "browser"
      scenario_result["argv"] = Phase01RunChecks::SCENARIO_LOCAL_CHROME_ARGV
      scenario_result["result_digest"] = VerificationEvidence.digest_value(
        scenario_result.reject { |key, _value| key == "result_digest" }
      )
    end
    write_json(root, relative, summary)
    {
      "obligation_id" => obligation_id,
      "path" => relative,
      "sha256" => Digest::SHA256.file(File.join(root, relative)).hexdigest,
      "status" => summary.fetch("status"),
      "case_id" => summary.fetch("case_id"),
      "behavior_id" => summary.fetch("behavior_id"),
      "catalog_test" => summary.fetch("catalog_test"),
      "evidence_path" => summary.fetch("evidence_path")
    }
  end
  runtime_path = File.join(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME)
  manifest = {
    "schema_version" => VerificationEvidence::OBLIGATION_MANIFEST_SCHEMA,
    "phase" => VerificationEvidence::PHASE,
    "owner" => VerificationEvidence::OWNER,
    "subject_manifest_path" => binding.fetch("path"),
    "subject_manifest_digest" => binding.fetch("subject_manifest_digest"),
    "tested_subject_digest" => binding.fetch("tested_subject_digest"),
    "entries" => entries,
    "runtime_artifact" => {
      "path" => Phase01RunChecks::LOCAL_CHROME_RUNTIME,
      "sha256" => Digest::SHA256.file(runtime_path).hexdigest,
      "media_type" => "application/json",
      "size" => File.size(runtime_path)
    },
    "ci_locators" => VerificationEvidence::CI_LOCATOR_PATHS.map do |relative|
      { "path" => relative, "sha256" => Digest::SHA256.file(File.join(root, relative)).hexdigest }
    end
  }
  write_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, manifest)
end

def portable_result(root)
  output = StringIO.new
  errors = StringIO.new
  code = Phase01RunChecks.validate_portable_chrome_artifact(
    root: root,
    io: output,
    err: errors,
    validator_root: ROOT
  )
  [code, output.string, errors.string]
end

def with_portable_fixture
  Dir.mktmpdir("phase01-portable-") do |root|
    prepare_portable_root(root)
    yield root
  end
end

def definition(id, ruby_source, timeout: 2, output_contract: "process")
  {
    "id" => id,
    "layer" => "validator",
    "argv" => [RbConfig.ruby, "-e", ruby_source],
    "cwd" => ".",
    "obligation_ids" => ["OBL-FOUND-TRACE-003"],
    "case_ids" => ["CASE-FOUND-TRACE-003"],
    "inputs" => [{ "path" => "fixture/input.txt", "role" => "config" }],
    "timeout_seconds" => timeout,
    "output_contract" => output_contract
  }
end

def run_fixture(root, definitions, name)
  evidence = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/test-runs/#{name}"
  result = Phase01RunChecks.run(root: root, evidence_dir: evidence, definitions: definitions, io: StringIO.new)
  [result, File.join(root, evidence)]
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  pass = definition("pass", "puts 'ok'")
  result, evidence = run_fixture(root, [pass], "pass")
  assert(result.fetch("status") == "PASS", "PASS child did not produce PASS")
  assert(Dir.glob(File.join(evidence, "**/aggregate.json")).one?, "aggregate evidence missing")
end

with_portable_fixture do |root|
  code, output, errors = portable_result(root)
  assert(code.zero?, "current portable artifact rejected: #{errors}")
  assert(output.include?("portable_chrome_artifact=PASS"), "portable PASS marker missing")
  assert(output.include?("live_browser_launched=false"), "portable validation did not attest no browser launch")
end

with_portable_fixture do |root|
  runtime = read_json(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME)
  subject = runtime.fetch("run").fetch("scenario").fetch("subject")
  subject["manifestDigest"] = "a" * 64
  subject["testedSubjectDigest"] = "b" * 64
  subject.fetch("health")["subject_manifest_digest"] = "a" * 64
  subject.fetch("health")["tested_subject_digest"] = "b" * 64
  write_ordered_json(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME, runtime)
  manifest = read_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST)
  manifest["subject_manifest_digest"] = "a" * 64
  manifest["tested_subject_digest"] = "b" * 64
  runtime_path = File.join(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME)
  manifest.fetch("runtime_artifact")["sha256"] = Digest::SHA256.file(runtime_path).hexdigest
  manifest.fetch("runtime_artifact")["size"] = File.size(runtime_path)
  write_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, manifest)
  code, _output, errors = portable_result(root)
  assert(code == 75, "candidate-self-rebound runtime unexpectedly passed")
  assert(errors.include?("PORTABLE_RUNTIME_INVALID"), "candidate-self-rebound returned wrong diagnostic")
end

with_portable_fixture do |root|
  File.open(File.join(root, "scripts/lib/phase-01/run_checks.rb"), "a") { |file| file.write("# code-owned mutation\n") }
  code, _output, errors = portable_result(root)
  assert(code == 75, "old artifact and exact-seven accepted after code-owned mutation")
  assert(errors.include?("SUBJECT_CONTENT_MISMATCH"), "code-owned mutation returned wrong diagnostic")
end

portable_mutations = {
  "runtime-bytes" => lambda do |root|
    File.open(File.join(root, Phase01RunChecks::LOCAL_CHROME_RUNTIME), "a") { |file| file.write(" \n") }
  end,
  "runtime-path" => lambda do |root|
    manifest = read_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST)
    manifest.fetch("runtime_artifact")["path"] = File.join(Phase01RunChecks::EVIDENCE_DIR, "copied-runtime.json")
    write_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, manifest)
  end,
  "runtime-size" => lambda do |root|
    manifest = read_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST)
    manifest.fetch("runtime_artifact")["size"] += 1
    write_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, manifest)
  end,
  "runtime-sha" => lambda do |root|
    manifest = read_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST)
    manifest.fetch("runtime_artifact")["sha256"] = "0" * 64
    write_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, manifest)
  end,
  "summary-bytes" => lambda do |root|
    relative = File.join(Phase01RunChecks::EVIDENCE_DIR, "OBL-FOUND-TRACE-001.json")
    File.open(File.join(root, relative), "a") { |file| file.write(" \n") }
  end,
  "manifest-field" => lambda do |root|
    manifest = read_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST)
    manifest["candidate_expected_subject"] = "forbidden"
    write_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, manifest)
  end,
  "manifest-subject" => lambda do |root|
    manifest = read_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST)
    manifest["subject_manifest_digest"] = "a" * 64
    manifest["tested_subject_digest"] = "b" * 64
    write_json(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, manifest)
  end,
  "subject-missing" => lambda do |root|
    File.delete(File.join(root, Phase01RunChecks::DURABLE_SUBJECT))
  end,
  "manifest-missing" => lambda do |root|
    File.delete(File.join(root, Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST))
  end
}

portable_mutations.each do |name, mutate|
  with_portable_fixture do |root|
    mutate.call(root)
    code, _output, errors = portable_result(root)
    assert(code == 75, "portable mutation unexpectedly passed: #{name}")
    assert(errors.include?("portable_chrome_artifact=BLOCKED"), "portable mutation missing BLOCKED marker: #{name}")
  end
end

portable_oversize_paths = {
  "durable-subject" => [Phase01RunChecks::DURABLE_SUBJECT, Phase01RunChecks::PORTABLE_JSON_MAX_BYTES],
  "runtime" => [Phase01RunChecks::LOCAL_CHROME_RUNTIME, Phase01RunChecks::PORTABLE_RUNTIME_MAX_BYTES],
  "exact-seven-manifest" => [Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST, Phase01RunChecks::PORTABLE_JSON_MAX_BYTES],
  "scenario" => ["web/verification/browser-scenarios.json", Phase01RunChecks::PORTABLE_JSON_MAX_BYTES],
  "runtime-schema" => ["web/verification/local-chrome-runtime.schema.json", Phase01RunChecks::PORTABLE_JSON_MAX_BYTES],
  "obligation-summary" => [
    File.join(Phase01RunChecks::EVIDENCE_DIR, "OBL-FOUND-TRACE-001.json"),
    Phase01RunChecks::PORTABLE_JSON_MAX_BYTES
  ]
}

portable_oversize_paths.each do |name, (relative, limit)|
  with_portable_fixture do |root|
    File.truncate(File.join(root, relative), limit + 1)
    code, _output, errors = portable_result(root)
    assert(code == 75, "oversize portable input unexpectedly passed: #{name}")
    assert(errors.include?("SIZE_LIMIT_EXCEEDED"), "oversize portable input returned wrong diagnostic: #{name} #{errors}")
  end
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  source = <<~'RUBY'
    puts "Authorization: Bearer AUTH_PERSIST_CANARY"
    puts "Cookie: sid=COOKIE_PERSIST_CANARY; mode=fixture"
    puts "password=alpha QUOTED_PERSIST_CANARY omega"
    puts "https://fixture:URL_PERSIST_CANARY@example.invalid/path"
    puts "-----BEGIN PRIVATE KEY-----\nPEM_PERSIST_CANARY\n-----END PRIVATE KEY-----"
    warn "contact=13800138000"
  RUBY
  child_path = File.join(root, "fixture/redaction-child.rb")
  File.write(child_path, source)
  redaction = definition("redaction", "exit 0")
  redaction["argv"] = [RbConfig.ruby, "fixture/redaction-child.rb"]
  result, evidence = run_fixture(root, [redaction], "redaction")
  assert(result.fetch("status") == "PASS", "redaction fixture did not produce PASS")
  persisted = Dir.glob(File.join(evidence, "**/*.{txt,json}")).map { |path| File.binread(path) }.join("\n")
  %w[
    AUTH_PERSIST_CANARY COOKIE_PERSIST_CANARY QUOTED_PERSIST_CANARY
    URL_PERSIST_CANARY PEM_PERSIST_CANARY 13800138000
  ].each do |canary|
    assert(!persisted.include?(canary), "persisted evidence leaked #{canary}")
  end
  assert(persisted.include?("[REDACTED"), "persisted evidence omitted redaction marker")
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  definitions = [definition("first-pass", "puts 'first'"), definition("later-fail", "warn 'failed'; exit 7")]
  result, evidence = run_fixture(root, definitions, "later-fail")
  assert(result.fetch("status") == "FAIL", "later FAIL did not dominate")
  assert(Dir.glob(File.join(evidence, "**/first-pass.json")).one?, "earlier PASS envelope was not retained")
  assert(Dir.glob(File.join(evidence, "**/later-fail.json")).one?, "later FAIL envelope missing")
end

cases = {
  "blocked" => [definition("blocked", "exit 75"), "BLOCKED"],
  "timeout" => [definition("timeout", "sleep 2", timeout: 0.05), "BLOCKED"],
  "interrupted" => [definition("interrupted", "Process.kill('INT', Process.pid)"), "BLOCKED"],
  "malformed" => [definition("malformed", "puts 'not-json'", output_contract: "json-status-v1"), "FAIL"]
}

cases.each do |name, (definition_value, expected_status)|
  Dir.mktmpdir("phase01-runner-") do |root|
    prepare_root(root)
    result, _evidence = run_fixture(root, [definition_value], name)
    assert(result.fetch("status") == expected_status, "#{name} expected #{expected_status}, got #{result.fetch('status')}")
  end
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  missing = definition("missing", "exit 0")
  missing["argv"] = ["phase01-command-that-does-not-exist"]
  result, _evidence = run_fixture(root, [missing], "missing")
  assert(result.fetch("status") == "BLOCKED", "missing executable was not BLOCKED")
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  duplicate = definition("duplicate", "exit 0")
  begin
    run_fixture(root, [duplicate, duplicate], "duplicate")
    abort("duplicate check IDs unexpectedly accepted")
  rescue Phase01RunChecks::ConfigurationError => error
    assert(error.message.include?("CHECK_ID_DUPLICATE"), "duplicate ID returned wrong diagnostic")
  end
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  secret_argv = definition("secret-argv", "puts 'password=ARG_PERSIST_CANARY'")
  evidence = "#{SOURCE_EVIDENCE}/test-runs/secret-argv"
  begin
    Phase01RunChecks.run(root: root, evidence_dir: evidence, definitions: [secret_argv], io: StringIO.new)
    abort("secret-bearing argv unexpectedly accepted")
  rescue Phase01RunChecks::ConfigurationError => error
    assert(error.message.include?("CHECK_ARGV_SECRET_FORBIDDEN"), "secret argv returned wrong diagnostic")
    assert(!File.exist?(File.join(root, evidence)), "secret argv created persistent evidence")
  end
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  relative = "#{SOURCE_EVIDENCE}/browser-source-admission.json"
  destination = File.join(root, relative)
  FileUtils.mkdir_p(File.dirname(destination))
  File.write(destination, "superseded history fixture\n")
  legacy = definition("legacy-membership", "exit 0")
  legacy.fetch("inputs") << { "path" => relative, "role" => "config" }
  begin
    run_fixture(root, [legacy], "legacy-membership")
    abort("legacy browser-source subject membership unexpectedly accepted")
  rescue ArgumentError => error
    assert(error.message.include?("SUBJECT_ILLEGAL_EXCLUSION"), "legacy membership returned wrong diagnostic")
  end
end

Dir.mktmpdir("phase01-runner-") do |root|
  prepare_root(root)
  begin
    Phase01RunChecks.run(root: root, evidence_dir: "../outside", definitions: [definition("pass", "exit 0")], io: StringIO.new)
    abort("outside evidence directory unexpectedly accepted")
  rescue Phase01RunChecks::ConfigurationError => error
    assert(error.message.include?("EVIDENCE_DIR_OUTSIDE_ROOT"), "outside path returned wrong diagnostic")
  end
end

stdout, stderr, status = Open3.capture3(File.join(ROOT, "scripts/verify-phase-01"), "--unknown")
assert(!status.success?, "unknown wrapper flag unexpectedly passed")
assert((stdout + stderr).include?("OPTION_UNKNOWN"), "unknown wrapper flag missing stable diagnostic")

registered_inputs = Phase01RunChecks.subject_registries.values.flatten.map { |entry| entry.fetch("path") }.uniq
evidence_cycle_inputs = [
  Phase01RunChecks::DURABLE_SUBJECT,
  Phase01RunChecks::OBLIGATION_EVIDENCE_MANIFEST,
  Phase01RunChecks::LOCAL_CHROME_RUNTIME,
  *VerificationEvidence.obligation_registry.map do |definition|
    File.join(Phase01RunChecks::EVIDENCE_DIR, "#{definition.fetch('obligation_id')}.json")
  end
]
assert((registered_inputs & evidence_cycle_inputs).empty?, "portable evidence creates a subject digest cycle")
compiled_inputs = Dir.glob(File.join(ROOT, "core/src/main/java/**/*.java")).map { |path| path.delete_prefix("#{ROOT}/") }
compiled_inputs.concat(Dir.glob(File.join(ROOT, "web/src/**/*.{ts,tsx,css}")).map { |path| path.delete_prefix("#{ROOT}/") })
compiled_inputs.concat(%w[web/index.html web/lib/format.ts])
required_planning_inputs = %w[
  .planning/tools/validate-ui-contract.rb
  .planning/PHASE-ARTIFACT-TEMPLATE.md
]
missing_inputs = (compiled_inputs + required_planning_inputs).uniq.sort - registered_inputs
assert(missing_inputs.empty?, "compiled/validator inputs missing from canonical registry: #{missing_inputs.join(',')}")

ci_definitions = Phase01RunChecks.definitions_for(["ci"])
all_definitions = Phase01RunChecks.definitions_for(["all"])
assert(ci_definitions.length == 20, "CI selector count changed: #{ci_definitions.length}")
assert(all_definitions.length == 19, "local-all selector count changed: #{all_definitions.length}")
Phase01RunChecks.scenario_check_contract!(Phase01RunChecks::CHECKS)

portable_scenario = ci_definitions.find { |entry| entry.fetch("id") == "login-scenario-contract" }
local_scenario = all_definitions.find { |entry| entry.fetch("id") == "login-scenario-visual-local-chrome" }
assert(portable_scenario.fetch("argv") == Phase01RunChecks::SCENARIO_CONTRACT_ARGV,
       "CI scenario command is not the flag-free structural validator")
assert(local_scenario.fetch("argv") == Phase01RunChecks::SCENARIO_LOCAL_CHROME_ARGV,
       "local scenario command does not carry the explicit Chrome flag")
assert(ci_definitions.none? { |entry| entry.fetch("argv").include?(Phase01RunChecks::LOCAL_CHROME_FLAG) },
       "CI selector contains a local Chrome flag")
assert(ci_definitions.none? { |entry| entry.fetch("argv").include?(Phase01RunChecks::RUN_PLAYWRIGHT_FLAG) },
       "CI selector contains the server Playwright flag")
assert(ci_definitions.none? { |entry| entry.fetch("argv").include?(Phase01RunChecks::SCENARIO_LOCAL_CHROME_PATH) },
       "CI selector references the local Chrome entry point")

scenario_registry_mutations = {
  "portable-scope" => lambda do |checks|
    checks.find { |entry| entry.fetch("id") == "login-scenario-contract" }["scopes"] = %w[ci all]
  end,
  "local-layer" => lambda do |checks|
    checks.find { |entry| entry.fetch("id") == "login-scenario-visual-local-chrome" }["layer"] = "validator"
  end,
  "server-playwright-flag" => lambda do |checks|
    checks.find { |entry| entry.fetch("id") == "login-scenario-server" }["argv"] << Phase01RunChecks::RUN_PLAYWRIGHT_FLAG
  end,
  "ci-local-flag" => lambda do |checks|
    checks.find { |entry| entry.fetch("id") == "login-scenario-contract" }["argv"] = Phase01RunChecks::SCENARIO_LOCAL_CHROME_ARGV
  end,
  "other-ci-local-file" => lambda do |checks|
    checks.find { |entry| entry.fetch("id") == "copy-static" }["argv"] << Phase01RunChecks::SCENARIO_LOCAL_CHROME_PATH
  end,
  "other-ci-run-playwright" => lambda do |checks|
    checks.find { |entry| entry.fetch("id") == "copy-static" }["argv"] << Phase01RunChecks::RUN_PLAYWRIGHT_FLAG
  end,
  "other-ci-run-playwright-assignment" => lambda do |checks|
    checks.find { |entry| entry.fetch("id") == "copy-static" }["argv"] << "#{Phase01RunChecks::RUN_PLAYWRIGHT_FLAG}=true"
  end
}
scenario_registry_mutations.each do |name, mutate|
  checks = JSON.parse(JSON.generate(Phase01RunChecks::CHECKS))
  mutate.call(checks)
  begin
    Phase01RunChecks.scenario_check_contract!(checks)
    abort("scenario registry mutation unexpectedly passed: #{name}")
  rescue Phase01RunChecks::ConfigurationError
    # Expected: the exact ID/argv/scope/layer contract rejects semantic drift.
  end
end

scenario_source = File.binread(File.join(ROOT, Phase01RunChecks::SCENARIO_VALIDATOR_PATH))
Phase01RunChecks.scenario_validator_source_contract!(scenario_source)
Phase01RunChecks.exact_ci_scenario_source_contract!(Phase01RunChecks::SCENARIO_VALIDATOR_PATH, scenario_source)
source_mutations = {
  "top-level-dynamic-import" => "await import('@playwright/test');\n#{scenario_source}",
  "extra-chrome-call" => "chromeBrowserType.launch({ headless: true });\n#{scenario_source}",
  "local-file-reference" => "import './#{File.basename(Phase01RunChecks::SCENARIO_LOCAL_CHROME_PATH)}';\n#{scenario_source}",
  "unreviewed-static-import" => "import { createRequire } from 'node:module';\n#{scenario_source}",
  "create-require-string-concat" => "const require = createRequire(import.meta.url); require('@play' + 'wright/test');\n#{scenario_source}",
  "spawn-sync" => "spawnSync('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', ['--headless']);\n#{scenario_source}"
}
source_mutations.each do |name, source|
  begin
    Phase01RunChecks.exact_ci_scenario_source_contract!(Phase01RunChecks::SCENARIO_VALIDATOR_PATH, source)
    abort("scenario source mutation unexpectedly passed: #{name}")
  rescue Phase01RunChecks::ConfigurationError
    # Expected: CI portability is bound to source, not merely a registry label.
  end
end
single_byte_mutation = scenario_source.dup
single_byte_mutation.setbyte(single_byte_mutation.bytesize - 1, single_byte_mutation.getbyte(single_byte_mutation.bytesize - 1) ^ 1)
begin
  Phase01RunChecks.exact_ci_scenario_source_contract!(Phase01RunChecks::SCENARIO_VALIDATOR_PATH, single_byte_mutation)
  abort("single-byte scenario source mutation unexpectedly passed")
rescue Phase01RunChecks::ConfigurationError
  # Expected: exact content binding rejects even semantically opaque changes.
end
Phase01RunChecks::CI_SCENARIO_SOURCE_SHA256.each_key do |path|
  next if path == Phase01RunChecks::SCENARIO_VALIDATOR_PATH

  source = File.binread(File.join(ROOT, path))
  Phase01RunChecks.exact_ci_scenario_source_contract!(path, source)
  begin
    Phase01RunChecks.exact_ci_scenario_source_contract!(path, "#{source} ")
    abort("bound CI scenario source drift unexpectedly passed: #{path}")
  rescue Phase01RunChecks::ConfigurationError
    # Expected: every repository-local source in the CI scenario call graph is byte-bound.
  end
end
Phase01RunChecks.registry_contract!(root: ROOT)
Dir.mktmpdir("phase01-scenario-source-") do |root|
  Phase01RunChecks::CI_SCENARIO_SOURCE_SHA256.each_key do |relative|
    destination = File.join(root, relative)
    FileUtils.mkdir_p(File.dirname(destination))
    FileUtils.cp(File.join(ROOT, relative), destination)
  end
  begin
    Phase01RunChecks.registry_contract!(root: root)
    abort("missing local Chrome source unexpectedly passed the registry contract")
  rescue Phase01RunChecks::ConfigurationError => error
    assert(error.message.include?("CHECK_SCENARIO_LOCAL_CHROME_SOURCE_MISSING"),
           "missing local Chrome source returned wrong diagnostic: #{error.message}")
  end
end

matrix = VerificationEvidence.parse_test_matrix(ROOT, "#{SOURCE_PHASE}/TEST-MATRIX.md")
browser_argv = Phase01RunChecks::CHECKS.find { |entry| entry.fetch("id") == "local-chrome-runtime" }.fetch("argv").join(" ")
assert(matrix.fetch("OBL-NFR-BROWSER").fetch("matrix_command") == browser_argv,
       "browser TEST-MATRIX command does not execute the registered runtime producer/validator")

puts "phase01_runner_self_test=PASS cases=40 redaction=persisted-canaries+argv-preflight complete-compiled-inputs portable-independent-binding bounded-json no-digest-cycle scenario-ci-local-split"
