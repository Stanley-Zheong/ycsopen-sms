#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "digest"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"

require_relative "verification-evidence"

ROOT = File.expand_path("../..", __dir__)
PHASE_REL = ".planning/phases/01-engineering-verification-foundation"
EVIDENCE_REL = "#{PHASE_REL}/EVIDENCE"
def assert(condition, message)
  abort(message) unless condition
end

def deep_copy(value)
  JSON.parse(JSON.generate(value))
end

def copy_entry_boundary(root)
  paths = [
    "#{EVIDENCE_REL}/local-chrome-entry.json",
    "#{PHASE_REL}/ENTRY-REVIEW.md"
  ]
  paths.each do |relative|
    destination = File.join(root, relative)
    FileUtils.mkdir_p(File.dirname(destination))
    FileUtils.cp(File.join(ROOT, relative), destination, preserve: true)
  end
end

def fixture_registry
  {
    "fixture-check" => [
      { "path" => "src/app.rb", "role" => "implementation" },
      { "path" => "test/app_test.rb", "role" => "test" },
      { "path" => "config/app.json", "role" => "config" },
      { "path" => "contracts/rule.md", "role" => "contract" },
      { "path" => "validators/check.rb", "role" => "validator" }
    ]
  }
end

def create_fixture(root)
  copy_entry_boundary(root)
  {
    "src/app.rb" => "module FixtureApp; end\n",
    "test/app_test.rb" => "raise 'fixture test' unless true\n",
    "config/app.json" => "{\"enabled\":true}\n",
    "contracts/rule.md" => "# Synthetic contract\n",
    "validators/check.rb" => "exit 0\n"
  }.each do |relative, content|
    path = File.join(root, relative)
    FileUtils.mkdir_p(File.dirname(path))
    File.write(path, content)
  end

  manifest_relative = "#{EVIDENCE_REL}/tested-inputs.json"
  manifest = VerificationEvidence.build_subject_manifest(
    root: root,
    registries: fixture_registry,
    manifest_path: manifest_relative
  )

  artifact_relative = "#{EVIDENCE_REL}/runs/fixture-run/fixture-check.txt"
  artifact_path = File.join(root, artifact_relative)
  FileUtils.mkdir_p(File.dirname(artifact_path))
  File.write(artifact_path, "fixture diagnostic\n")

  envelope = VerificationEvidence.build_envelope(
    run_id: "fixture-run",
    check_id: "fixture-check",
    layer: "validator",
    obligation_ids: ["OBL-FOUND-TRACE-003"],
    case_ids: ["CASE-FOUND-TRACE-003"],
    argv: ["/usr/bin/env", "ruby", "validators/check.rb"],
    cwd: ".",
    started_at: "2026-08-30T00:00:00Z",
    completed_at: "2026-08-30T00:00:01Z",
    environment: { "ruby_engine" => "ruby", "platform" => "synthetic" },
    status: "PASS",
    exit_code: 0,
    errors: [],
    diagnostics: ["fixture diagnostic"],
    artifacts: [VerificationEvidence.artifact_record(root, artifact_relative, "text/plain")],
    subject_manifest_path: manifest_relative,
    subject_manifest_digest: VerificationEvidence.subject_manifest_digest(manifest),
    tested_subject_digest: VerificationEvidence.tested_subject_digest(manifest.fetch("inputs"))
  )
  [manifest_relative, manifest, envelope]
end

def mutate(root, mutation, manifest_relative, manifest, envelope)
  changed_manifest = deep_copy(manifest)
  changed_envelope = deep_copy(envelope)
  entry_evidence = "#{EVIDENCE_REL}/local-chrome-entry.json"
  entry_review = "#{PHASE_REL}/ENTRY-REVIEW.md"

  case mutation
  when "wrong_stale_subject"
    changed_envelope["tested_subject_digest"] = "0" * 64
  when "missing_input"
    changed_manifest["inputs"].delete_at(0)
  when "extra_input"
    changed_manifest["inputs"] << {
      "path" => "rogue.txt", "mode" => "100644", "sha256" => "0" * 64, "role" => "config"
    }
  when "content_mismatch"
    File.write(File.join(root, "src/app.rb"), "changed\n")
  when "mode_mismatch"
    File.chmod(0o600, File.join(root, "src/app.rb"))
  when "executable_mode_mismatch"
    File.chmod(0o755, File.join(root, "validators/check.rb"))
  when "illegal_exclusion"
    changed_manifest["exclusions"] = ["src/app.rb"]
  when "manifest_digest_mismatch"
    changed_envelope["subject_manifest_digest"] = "f" * 64
  when "entry_evidence_version_mismatch"
    path = File.join(root, entry_evidence)
    document = JSON.parse(File.read(path))
    previous_digest = Digest::SHA256.file(path).hexdigest
    document["chrome"]["version_output"] = "Google Chrome 999.1.2.3"
    document["chrome"]["full_version"] = "999.1.2.3"
    document["chrome"]["major"] = 999
    File.write(path, JSON.pretty_generate(document) + "\n")
    current_digest = Digest::SHA256.file(path).hexdigest
    review_path = File.join(root, entry_review)
    File.write(review_path, File.read(review_path).gsub(previous_digest, current_digest))
  when "entry_evidence_mode_drift"
    File.chmod(0o600, File.join(root, entry_evidence))
  when "entry_review_digest_mismatch"
    text = File.read(File.join(root, entry_review))
    actual = Digest::SHA256.file(File.join(root, entry_evidence)).hexdigest
    File.write(File.join(root, entry_review), text.gsub(actual, "0" * 64))
  when "malformed_evidence"
    changed_envelope.delete("check_id")
  when "stale_running"
    changed_envelope["status"] = "RUNNING"
    changed_envelope["completed_at"] = nil
  when "checksum_mismatch"
    changed_envelope.fetch("artifacts").first["sha256"] = "e" * 64
  when "secret_bearing_output"
    changed_envelope["diagnostics"] = ["password=synthetic-secret"]
  when "pass_with_nonzero"
    changed_envelope["exit_code"] = 3
  when "fail_status"
    changed_envelope["status"] = "FAIL"
    changed_envelope["exit_code"] = 1
    changed_envelope["errors"] = ["FIXTURE_FAILURE"]
  when "blocked_status"
    changed_envelope["status"] = "BLOCKED"
    changed_envelope["exit_code"] = nil
    changed_envelope["errors"] = ["FIXTURE_BLOCKED"]
  when "final_commit_identity"
    changed_envelope["commit_sha"] = "b" * 40
  else
    abort("unknown fixture mutation: #{mutation}")
  end

  manifest_path = File.join(root, manifest_relative)
  VerificationEvidence.atomic_write_json(manifest_path, changed_manifest) if changed_manifest != manifest
  [changed_manifest, changed_envelope]
end

mutations = [
  { "id" => "wrong_stale_subject", "error_id" => "EVIDENCE_TESTED_SUBJECT_DIGEST_MISMATCH" },
  { "id" => "missing_input", "error_id" => "SUBJECT_INPUT_MISSING" },
  { "id" => "extra_input", "error_id" => "SUBJECT_INPUT_EXTRA" },
  { "id" => "content_mismatch", "error_id" => "SUBJECT_CONTENT_MISMATCH" },
  { "id" => "mode_mismatch", "error_id" => "SUBJECT_MODE_MISMATCH" },
  { "id" => "executable_mode_mismatch", "error_id" => "SUBJECT_MODE_MISMATCH" },
  { "id" => "illegal_exclusion", "error_id" => "SUBJECT_ILLEGAL_EXCLUSION" },
  { "id" => "manifest_digest_mismatch", "error_id" => "EVIDENCE_SUBJECT_MANIFEST_DIGEST_MISMATCH" },
  { "id" => "entry_evidence_version_mismatch", "error_id" => "ENTRY_EVIDENCE_LIVE_VERSION_MISMATCH" },
  { "id" => "entry_evidence_mode_drift", "error_id" => "ENTRY_EVIDENCE_MODE_UNSAFE" },
  { "id" => "entry_review_digest_mismatch", "error_id" => "ENTRY_REVIEW_EVIDENCE_DIGEST_MISSING" },
  { "id" => "malformed_evidence", "error_id" => "EVIDENCE_MISSING_FIELD" },
  { "id" => "stale_running", "error_id" => "EVIDENCE_STALE_RUNNING" },
  { "id" => "checksum_mismatch", "error_id" => "EVIDENCE_ARTIFACT_CHECKSUM_MISMATCH" },
  { "id" => "secret_bearing_output", "error_id" => "EVIDENCE_SECRET_DETECTED" },
  { "id" => "pass_with_nonzero", "error_id" => "EVIDENCE_PASS_EXIT_MISMATCH" },
  { "id" => "fail_status", "error_id" => "EVIDENCE_STATUS_FAIL" },
  { "id" => "blocked_status", "error_id" => "EVIDENCE_STATUS_BLOCKED" },
  { "id" => "final_commit_identity", "error_id" => "EVIDENCE_FORBIDDEN_FIELD" }
].freeze

Dir.mktmpdir("repository-verification-") do |root|
  manifest_relative, _manifest, envelope = create_fixture(root)
  errors = VerificationEvidence.validate_envelope(
    root: root,
    envelope: envelope,
    registries: fixture_registry,
    subject_manifest_path: manifest_relative
  )
  assert(errors.empty?, "positive evidence fixture failed:\n#{errors.join("\n")}")
end

mutations.each do |fixture|
  Dir.mktmpdir("repository-verification-mutation-") do |root|
    manifest_relative, manifest, envelope = create_fixture(root)
    _changed_manifest, changed_envelope = mutate(root, fixture.fetch("id"), manifest_relative, manifest, envelope)
    errors = VerificationEvidence.validate_envelope(
      root: root,
      envelope: changed_envelope,
      registries: fixture_registry,
      subject_manifest_path: manifest_relative
    )
    if fixture.fetch("id") == "entry_evidence_version_mismatch"
      errors.concat(
        VerificationEvidence.validate_entry_boundary(
          root,
          require_live: true,
          live_file_validator: ->(_document) { [] },
          version_probe: -> { ["Google Chrome 151.0.7922.174\n", true] }
        )
      )
      assert(errors.none? { |error| error.start_with?("ENTRY_REVIEW_EVIDENCE_DIGEST_MISSING") },
             "version mutation also invalidated the entry-review checksum")
    end
    token = fixture.fetch("error_id")
    assert(!errors.empty?, "mutation #{fixture.fetch('id')} unexpectedly passed")
    assert(errors.any? { |error| error.start_with?(token) }, "mutation #{fixture.fetch('id')} missing #{token}:\n#{errors.join("\n")}")
  end
end

Dir.mktmpdir("repository-verification-portable-entry-") do |root|
  manifest_relative, manifest, _envelope = create_fixture(root)
  entry_path = File.join(root, EVIDENCE_REL, "local-chrome-entry.json")
  review_path = File.join(root, PHASE_REL, "ENTRY-REVIEW.md")
  entry = JSON.parse(File.read(entry_path))
  previous_digest = Digest::SHA256.file(entry_path).hexdigest
  entry["chrome"]["version_output"] = "Google Chrome 999.1.2.3"
  entry["chrome"]["full_version"] = "999.1.2.3"
  entry["chrome"]["major"] = 999
  File.write(entry_path, JSON.pretty_generate(entry) + "\n")
  current_digest = Digest::SHA256.file(entry_path).hexdigest
  File.write(review_path, File.read(review_path).gsub(previous_digest, current_digest))
  portable = VerificationEvidence.validate_subject_manifest(root: root, manifest: manifest, registries: fixture_registry)
  assert(portable.empty?, "portable entry validation touched live Chrome: #{portable.join(';')}")
  live = VerificationEvidence.validate_entry_boundary(
    root,
    require_live: true,
    live_file_validator: ->(_document) { [] },
    version_probe: -> { ["Google Chrome 151.0.7922.174\n", true] }
  )
  assert(live.any? { |error| error.start_with?("ENTRY_EVIDENCE_LIVE_VERSION_MISMATCH") }, "local live entry accepted foreign Chrome version")
end

Dir.mktmpdir("repository-verification-missing-live-chrome-") do |root|
  create_fixture(root)
  live = VerificationEvidence.validate_entry_boundary(
    root,
    require_live: true,
    live_file_validator: ->(_document) { [] },
    version_probe: -> { raise Errno::ENOENT, "synthetic missing Chrome" }
  )
  assert(live.any? { |error| error.start_with?("ENTRY_EVIDENCE_LIVE_VERSION_FAILED") },
         "missing live Chrome did not fail closed")
end

assert(VerificationEvidence.reduce_statuses(%w[PASS PASS]) == "PASS", "all PASS did not reduce to PASS")
assert(VerificationEvidence.reduce_statuses(%w[PASS BLOCKED]) == "BLOCKED", "BLOCKED did not dominate PASS")
assert(VerificationEvidence.reduce_statuses(%w[BLOCKED FAIL PASS]) == "FAIL", "FAIL did not dominate BLOCKED")

[
  "#{EVIDENCE_REL}/browser-source-admission.json",
  "#{EVIDENCE_REL}/browser-source-probes/chrome-151.json",
  "#{EVIDENCE_REL}/browser-source-entry-attestation.json",
  "#{EVIDENCE_REL}/local-chrome-entry.json",
  "#{PHASE_REL}/ENTRY-REVIEW.md"
].each do |forbidden_path|
  errors = []
  VerificationEvidence.expected_inputs(
    fixture_registry.merge("forbidden-membership" => [{ "path" => forbidden_path, "role" => "contract" }]),
    errors
  )
  assert(errors.any? { |error| error.start_with?("SUBJECT_ILLEGAL_EXCLUSION") }, "subject accepted excluded entry/history path: #{forbidden_path}")
end

%w[
  .planning/tools/validate-delivery-attestation.rb
  .planning/tools/test-delivery-attestation.rb
].each do |source_path|
  errors = []
  role = source_path.include?("/test-") ? "test" : "validator"
  VerificationEvidence.expected_inputs(
    fixture_registry.merge("delivery-source" => [{ "path" => source_path, "role" => role }]),
    errors
  )
  assert(errors.none? { |error| error.start_with?("SUBJECT_ILLEGAL_EXCLUSION") }, "subject rejected delivery source: #{source_path}")
end

secret_cases = {
  "AUTH_CANARY" => "Authorization: Bearer AUTH_CANARY",
  "FOLDED_AUTH_CANARY" => "Authorization: Bearer\n FOLDED_AUTH_CANARY",
  "BASIC_CANARY" => "Proxy-Authorization: Basic BASIC_CANARY",
  "BARE_BEARER_CANARY" => "Bearer BARE_BEARER_CANARY",
  "COOKIE_CANARY" => "Cookie: session=COOKIE_CANARY; mode=fixture",
  "QUOTED_CANARY" => "password=\"alpha QUOTED_CANARY omega\"",
  "URL_CANARY" => "https://fixture:URL_CANARY@example.invalid/path",
  "PEM_CANARY" => "-----BEGIN PRIVATE KEY-----\nPEM_CANARY\n-----END PRIVATE KEY-----",
  "TRUNCATED_PEM_CANARY" => "-----BEGIN PRIVATE KEY-----\nTRUNCATED_PEM_CANARY",
  "13800138000" => "contact=13800138000"
}
secret_cases.each do |canary, value|
  redacted = VerificationEvidence.redact(value)
  assert(!redacted.include?(canary), "redaction leaked canary #{canary}: #{redacted.inspect}")
  assert(!VerificationEvidence.secret_bearing?(redacted), "redaction retained secret-bearing form for #{canary}")
end

validator = File.join(ROOT, ".planning/tools/validate-verification-evidence.rb")
stdout, stderr, status = Open3.capture3(RbConfig.ruby, validator, "--root", ".", chdir: ROOT)
assert(!status.success?, "empty validator selection unexpectedly passed")
assert((stdout + stderr).include?("EVIDENCE_SELECTION_EMPTY"), "relative --root did not resolve from caller cwd")
assert(!(stdout + stderr).include?("EVIDENCE_REGISTRY_UNAVAILABLE"), "relative --root resolved against script directory")

%i[validate_admission validate_attestation validate_probe].each do |removed_api|
  assert(!Phase01ChromeEntryContract.respond_to?(removed_api), "removed legacy contract API returned: #{removed_api}")
end

path_boundary_cases = 0
missing_nofollow = Module.new
nofollow_errors = []
nofollow_flags = VerificationEvidence.verified_open_flags(
  nofollow_errors,
  "PATH_TEST",
  file_constants: missing_nofollow
)
assert(nofollow_flags.nil? && nofollow_errors == ["PATH_TEST_NOFOLLOW_UNAVAILABLE"],
       "missing O_NOFOLLOW support did not fail closed: #{nofollow_errors.join(';')}")
path_boundary_cases += 1

Dir.mktmpdir("repository-path-boundary-") do |workspace|
  root = File.join(workspace, "root")
  outside = File.join(workspace, "outside")
  FileUtils.mkdir_p([File.join(root, "nested", "leaf"), File.join(outside, "nested", "leaf")])
  safe_relative = "nested/leaf/input.txt"
  outside_file = File.join(outside, "nested", "leaf", "input.txt")
  File.write(File.join(root, safe_relative), "ordinary-inside\n")
  File.write(outside_file, "EXTERNAL_CANARY\n")

  errors = []
  snapshot = VerificationEvidence.verified_local_file(root, safe_relative, errors, "PATH_TEST")
  assert(errors.empty? && snapshot&.bytes == "ordinary-inside\n", "ordinary in-root file failed: #{errors.join(';')}")
  path_boundary_cases += 1

  exact_size = File.size(File.join(root, safe_relative))
  boundary_read_started = false
  errors = []
  snapshot = VerificationEvidence.verified_local_file(
    root, safe_relative, errors, "PATH_TEST", max_bytes: exact_size,
    before_read: lambda do |_io, opened_stat|
      boundary_read_started = true
      assert(opened_stat.size == exact_size, "boundary test did not use descriptor fstat size")
    end
  )
  assert(errors.empty? && snapshot&.bytes == "ordinary-inside\n" && boundary_read_started,
         "exact size boundary was rejected: #{errors.join(';')}")
  path_boundary_cases += 1

  oversize_read_started = false
  errors = []
  snapshot = VerificationEvidence.verified_local_file(
    root, safe_relative, errors, "PATH_TEST", max_bytes: exact_size - 1,
    before_read: lambda do |_io, _opened_stat|
      oversize_read_started = true
    end
  )
  assert(snapshot.nil? && !oversize_read_started,
         "oversize file reached the read boundary")
  assert(errors == ["PATH_TEST_SIZE_LIMIT_EXCEEDED: #{safe_relative} size=#{exact_size} max=#{exact_size - 1}"],
         "oversize file returned unstable diagnostic: #{errors.join(';')}")
  path_boundary_cases += 1

  errors = []
  snapshot = VerificationEvidence.verified_local_file(root, safe_relative, errors, "PATH_TEST", max_bytes: 0)
  assert(snapshot.nil? && errors == ["PATH_TEST_MAX_BYTES_INVALID: 0"],
         "invalid size limit was accepted: #{errors.join(';')}")
  path_boundary_cases += 1

  final_link = File.join(root, "final-link.txt")
  File.symlink(outside_file, final_link)
  errors = []
  snapshot = VerificationEvidence.verified_local_file(root, "final-link.txt", errors, "PATH_TEST")
  assert(snapshot.nil? && errors.any? { |error| error.start_with?("PATH_TEST_SYMLINK_COMPONENT") }, "final symlink was accepted")
  path_boundary_cases += 1

  nested_link = File.join(root, "nested-link")
  File.symlink(File.join(outside, "nested"), nested_link)
  errors = []
  snapshot = VerificationEvidence.verified_local_file(root, "nested-link/leaf/input.txt", errors, "PATH_TEST")
  assert(snapshot.nil? && errors.any? { |error| error.start_with?("PATH_TEST_SYMLINK_COMPONENT") }, "nested symlink was accepted")
  path_boundary_cases += 1

  errors = []
  snapshot = VerificationEvidence.verified_local_file(root, "../outside/nested/leaf/input.txt", errors, "PATH_TEST")
  assert(snapshot.nil? && errors.any? { |error| error.start_with?("PATH_TEST_PATH_INVALID") }, "outside escape was accepted")
  path_boundary_cases += 1

  root_link = File.join(workspace, "root-link")
  File.symlink(root, root_link)
  errors = []
  snapshot = VerificationEvidence.verified_local_file(root_link, safe_relative, errors, "PATH_TEST")
  assert(snapshot.nil? && errors.any? { |error| error.start_with?("PATH_TEST_ROOT_SYMLINK") }, "symlink root was accepted")
  path_boundary_cases += 1

  hardlink = File.join(root, "hardlink.txt")
  File.link(File.join(root, safe_relative), hardlink)
  errors = []
  snapshot = VerificationEvidence.verified_local_file(root, "hardlink.txt", errors, "PATH_TEST")
  assert(snapshot.nil? && errors.any? { |error| error.start_with?("PATH_TEST_LINK_COUNT_INVALID") }, "hard link was accepted")
  path_boundary_cases += 1

  race_relative = "race-final.txt"
  race_path = File.join(root, race_relative)
  File.write(race_path, "inside-before-race\n")
  errors = []
  snapshot = VerificationEvidence.verified_local_file(
    root, race_relative, errors, "PATH_TEST",
    before_open: lambda do |_candidate|
      File.rename(race_path, "#{race_path}.original")
      File.symlink(outside_file, race_path)
    end
  )
  assert(snapshot.nil? && errors.any? { |error| error.start_with?("PATH_TEST_SYMLINK_COMPONENT") }, "final-component TOCTOU was accepted: #{errors.join(';')}")
  assert(!snapshot&.bytes&.include?("EXTERNAL_CANARY"), "final-component TOCTOU returned the canary")
  path_boundary_cases += 1

  race_dir = File.join(root, "race-dir")
  FileUtils.mkdir_p(File.join(race_dir, "nested", "leaf"))
  File.write(File.join(race_dir, "nested", "leaf", "input.txt"), "inside-before-dir-race\n")
  errors = []
  snapshot = VerificationEvidence.verified_local_file(
    root, "race-dir/nested/leaf/input.txt", errors, "PATH_TEST",
    before_open: lambda do |_candidate|
      File.rename(race_dir, "#{race_dir}.original")
      File.symlink(outside, race_dir)
    end
  )
  assert(snapshot.nil? && errors.any? { |error| error.start_with?("PATH_TEST_DESCRIPTOR_OUTSIDE_ROOT") || error.start_with?("PATH_TEST_COMPONENT_CHANGED") || error.start_with?("PATH_TEST_IDENTITY_CHANGED") }, "intermediate-component TOCTOU was accepted: #{errors.join(';')}")
  assert(!snapshot&.bytes&.include?("EXTERNAL_CANARY"), "intermediate-component TOCTOU returned the canary")
  path_boundary_cases += 1
end

puts "repository_verification_self_test=PASS mutations=#{mutations.length} redaction=#{secret_cases.length} path_boundary=#{path_boundary_cases} root=caller-cwd"
