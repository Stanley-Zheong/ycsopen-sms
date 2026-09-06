#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "rbconfig"
require "tmpdir"

VALIDATOR = File.expand_path("validate-delivery-attestation.rb", __dir__)
PHASE = "01"
PACKAGE = "engineering-verification-foundation"
PHASE_DIR = ".planning/phases/01-#{PACKAGE}"
SUBJECT_PATH = "#{PHASE_DIR}/EVIDENCE/tested-inputs.json"
EVIDENCE_PATH = "#{PHASE_DIR}/EVIDENCE/evidence-manifest.json"
BRANCH_REF = "refs/heads/phase/01-engineering-verification"
TAG_REF = "refs/tags/ycsopen-sms/phase-01/delivery"
CHECK_NAME = "phase-01-verification"
PR_LOCATOR = "local://pull/13"
CHECK_LOCATOR = "local://checks/phase-01-verification"
TAGGER_NAME = "Phase Delivery Bot"
TAGGER_EMAIL = "phase-delivery@example.invalid"

HISTORICAL_BROWSER_SOURCE_FILES = [
  "#{PHASE_DIR}/EVIDENCE/browser-source-admission.json",
  "#{PHASE_DIR}/EVIDENCE/browser-source-probes/chrome-151.json",
  "#{PHASE_DIR}/EVIDENCE/browser-source-probes/chrome-152.json",
  "#{PHASE_DIR}/EVIDENCE/browser-source-entry-attestation.json"
].freeze
EXCLUDED_ENTRY_FILES = [
  "#{PHASE_DIR}/EVIDENCE/local-chrome-entry.json",
  "#{PHASE_DIR}/ENTRY-REVIEW.md"
].freeze

def canonical(value)
  case value
  when Hash
    value.keys.sort.to_h { |key| [key, canonical(value.fetch(key))] }
  when Array
    value.map { |entry| canonical(entry) }
  else
    value
  end
end

def canonical_json(value)
  JSON.generate(canonical(value))
end

def json_bytes(value)
  "#{canonical_json(value)}\n"
end

def sha256(value)
  Digest::SHA256.hexdigest(value)
end

def run!(*argv, chdir: nil, stdin_data: "")
  spawn_options = { stdin_data: stdin_data }
  spawn_options[:chdir] = chdir if chdir
  stdout, stderr, status = Open3.capture3(*argv, **spawn_options)
  abort "COMMAND_FAILED argv=#{argv.inspect}\n#{stderr}#{stdout}" unless status.success?
  stdout
end

def write(path, content, mode: nil)
  FileUtils.mkdir_p(File.dirname(path))
  File.write(path, content)
  File.chmod(mode, path) if mode
end

def input_record(path, content, role: "contract", mode: "100644")
  { "path" => path, "mode" => mode, "sha256" => sha256(content), "role" => role }
end

def base_target_files(mutation)
  registry_entries = [
    { "path" => ".planning/tools/verification-evidence.rb", "role" => "validator" },
    { "path" => "scripts/lib/phase-01/run_checks.rb", "role" => "implementation" },
    { "path" => ".planning/tools/validate-delivery-attestation.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-delivery-attestation.rb", "role" => "test" },
    { "path" => ".planning/tools/validate-phase-entry.rb", "role" => "validator" },
    { "path" => ".planning/tools/test-planning-validators.rb", "role" => "test" },
    { "path" => ".github/workflows/ci.yml", "role" => "config" },
    { "path" => "scripts/verify-phase-01", "role" => "implementation" }
  ]
  files = {
    ".planning/tools/verification-evidence.rb" => "# fixture evidence kernel\n",
    ".planning/tools/validate-delivery-attestation.rb" => "# fixture delivery validator\n",
    ".planning/tools/test-delivery-attestation.rb" => "# fixture delivery test\n",
    ".planning/tools/validate-phase-entry.rb" => "# fixture entry validator\n",
    ".planning/tools/test-planning-validators.rb" => "# fixture planning validator tests\n",
    ".github/workflows/ci.yml" => "name: fixture-ci\n",
    "scripts/verify-phase-01" => "#!/bin/sh\nexit 0\n",
    "scripts/lib/phase-01/run_checks.rb" => <<~RUBY
      # frozen_string_literal: true
      module Phase01RunChecks
        FIXTURE_INPUTS = #{registry_entries.inspect}.freeze
        CHECKS = [
          {
            "id" => "fixture-check",
            "inputs" => FIXTURE_INPUTS
          }
        ].freeze
      end
    RUBY
  }
  (HISTORICAL_BROWSER_SOURCE_FILES + EXCLUDED_ENTRY_FILES).each do |path|
    files[path] = "superseded or separately validated fixture #{File.basename(path)}\n"
  end

  inputs = files.filter_map do |path, content|
    next unless registry_entries.any? { |entry| entry["path"] == path }

    role = registry_entries.find { |entry| entry.fetch("path") == path }.fetch("role")
    input_record(path, content, role: role)
  end
  if mutation == :missing_required_input
    inputs.reject! { |entry| entry["path"] == "scripts/lib/phase-01/run_checks.rb" }
  elsif mutation == :legacy_browser_source_membership
    path = HISTORICAL_BROWSER_SOURCE_FILES.first
    inputs << input_record(path, files.fetch(path), role: "config")
  elsif mutation == :local_entry_membership
    path = EXCLUDED_ENTRY_FILES.first
    inputs << input_record(path, files.fetch(path), role: "contract")
  elsif mutation == :entry_review_membership
    path = EXCLUDED_ENTRY_FILES.last
    inputs << input_record(path, files.fetch(path), role: "contract")
  elsif mutation == :extra_missing_input
    inputs << { "path" => "missing/extra-input.rb", "mode" => "100644", "sha256" => "0" * 64, "role" => "test" }
  elsif mutation == :illegal_exclusion
    files["#{PHASE_DIR}/TODO.md"] = "# generated metadata\n"
    inputs << input_record("#{PHASE_DIR}/TODO.md", files.fetch("#{PHASE_DIR}/TODO.md"), role: "contract")
  elsif mutation == :changed_content
    inputs.first["sha256"] = "f" * 64
  elsif mutation == :changed_mode
    inputs.first["mode"] = "100755"
  end
  inputs.sort_by! { |entry| entry.fetch("path") }

  subject = {
    "schema_version" => "phase01-tested-inputs-v1",
    "phase" => "01-engineering-verification-foundation",
    "inputs" => inputs
  }
  subject_bytes = json_bytes(subject)
  subject_manifest_digest = sha256(canonical_json(subject))
  tested_subject_digest = sha256(canonical_json(inputs))
  files[SUBJECT_PATH] = subject_bytes

  envelope_path = "#{PHASE_DIR}/EVIDENCE/trace-004.json"
  envelope = {
    "schema_version" => "phase01-delivery-evidence-binding-v1",
    "phase" => "01-engineering-verification-foundation",
    "subject_manifest_path" => SUBJECT_PATH,
    "subject_manifest_digest" => subject_manifest_digest,
    "tested_subject_digest" => tested_subject_digest,
    "status" => "PASS"
  }
  files[envelope_path] = json_bytes(envelope)
  aggregate_path = "#{PHASE_DIR}/EVIDENCE/aggregate.json"
  aggregate = envelope.merge("schema_version" => "phase01-delivery-aggregate-binding-v1")
  files[aggregate_path] = json_bytes(aggregate)
  evidence = {
    "schema_version" => "phase01-evidence-manifest-v1",
    "phase" => "01-engineering-verification-foundation",
    "owner" => PACKAGE,
    "subject_manifest_path" => SUBJECT_PATH,
    "subject_manifest_digest" => subject_manifest_digest,
    "tested_subject_digest" => tested_subject_digest,
    "entries" => [
      {
        "check_id" => "delivery-fixture",
        "path" => envelope_path,
        "sha256" => sha256(files.fetch(envelope_path)),
        "status" => "PASS",
        "obligation_ids" => ["OBL-FOUND-TRACE-004"],
        "case_ids" => ["CASE-FOUND-TRACE-004"]
      }
    ],
    "aggregate" => {
      "path" => aggregate_path,
      "sha256" => sha256(files.fetch(aggregate_path)),
      "status" => "PASS"
    }
  }
  evidence["subject_manifest_digest"] = "e" * 64 if mutation == :evidence_subject_manifest_digest
  evidence["tested_subject_digest"] = "e" * 64 if mutation == :evidence_tested_subject_digest

  if mutation.to_s.start_with?("exact")
    obligation_ids = %w[
      OBL-FOUND-TRACE-001 OBL-FOUND-TRACE-002 OBL-FOUND-TRACE-003 OBL-FOUND-TRACE-004
      OBL-FOUND-UI-DRIFT-001 OBL-FOUND-UI-DRIFT-002 OBL-NFR-BROWSER
    ]
    runtime_path = "#{PHASE_DIR}/EVIDENCE/local-chrome-runtime.json"
    files[runtime_path] = "{\"fixture\":true}\n"
    exact_entries = obligation_ids.map do |obligation_id|
      path = "#{PHASE_DIR}/EVIDENCE/#{obligation_id}.json"
      summary = {
        "schema_version" => "phase01-obligation-summary-v1",
        "phase" => "01-engineering-verification-foundation",
        "owner" => PACKAGE,
        "obligation_id" => obligation_id,
        "subject_manifest_path" => SUBJECT_PATH,
        "subject_manifest_digest" => subject_manifest_digest,
        "tested_subject_digest" => tested_subject_digest,
        "status" => "PASS"
      }
      if obligation_id == "OBL-NFR-BROWSER"
        summary["runtime"] = {
          "path" => runtime_path,
          "sha256" => sha256(files.fetch(runtime_path)),
          "subject_manifest_path" => SUBJECT_PATH,
          "subject_manifest_digest" => subject_manifest_digest,
          "tested_subject_digest" => tested_subject_digest
        }
      end
      files[path] = json_bytes(summary)
      {
        "obligation_id" => obligation_id,
        "path" => path,
        "sha256" => sha256(files.fetch(path)),
        "status" => "PASS",
        "case_id" => "CASE-#{obligation_id}",
        "behavior_id" => "engineering-verification-foundation-fixture",
        "catalog_test" => "T-#{obligation_id}:static",
        "evidence_path" => "EVIDENCE/#{obligation_id}.json"
      }
    end
    evidence = {
      "schema_version" => "phase01-obligation-evidence-manifest-v1",
      "phase" => "01-engineering-verification-foundation",
      "owner" => PACKAGE,
      "subject_manifest_path" => SUBJECT_PATH,
      "subject_manifest_digest" => subject_manifest_digest,
      "tested_subject_digest" => tested_subject_digest,
      "entries" => exact_entries,
      "runtime_artifact" => {
        "path" => runtime_path,
        "sha256" => sha256(files.fetch(runtime_path)),
        "media_type" => "application/json",
        "size" => files.fetch(runtime_path).bytesize
      },
      "ci_locators" => [".github/workflows/ci.yml", "scripts/verify-phase-01"].map do |path|
        { "path" => path, "sha256" => sha256(files.fetch(path)) }
      end
    }
    evidence["entries"].pop if mutation == :exact_missing_entry
    evidence["entries"][1] = JSON.parse(JSON.generate(evidence["entries"][0])) if mutation == :exact_duplicate_entry
    evidence["entries"][0]["obligation_id"] = "OBL-FOREIGN" if mutation == :exact_foreign_obligation
    evidence["entries"][0]["sha256"] = "f" * 64 if mutation == :exact_summary_checksum
    evidence["runtime_artifact"]["sha256"] = "f" * 64 if mutation == :exact_runtime_checksum
    evidence["ci_locators"][0]["sha256"] = "f" * 64 if mutation == :exact_ci_checksum
    if mutation == :exact_runtime_subject_binding
      browser_entry = evidence.fetch("entries").find { |entry| entry.fetch("obligation_id") == "OBL-NFR-BROWSER" }
      browser_summary = JSON.parse(files.fetch(browser_entry.fetch("path")))
      browser_summary.fetch("runtime")["tested_subject_digest"] = "9" * 64
      files[browser_entry.fetch("path")] = json_bytes(browser_summary)
      browser_entry["sha256"] = sha256(files.fetch(browser_entry.fetch("path")))
    end
  end
  files[EVIDENCE_PATH] = json_bytes(evidence)

  review = <<~MARKDOWN
    # Review

    | Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
    | --- | --- | --- | --- | --- | --- | --- | --- |
    | 1 | 0 | 0 | no | #{SUBJECT_PATH} | #{subject_manifest_digest} | #{tested_subject_digest} | PASS |

    ## Final verdict

    PASS
  MARKDOWN
  files["#{PHASE_DIR}/01-VERIFICATION.md"] = review
  files["#{PHASE_DIR}/01-REVIEW.md"] = review
  files["#{PHASE_DIR}/CLAUDE-REVIEW.md"] = review
  files["#{PHASE_DIR}/01-VERIFICATION.md"] = review.sub(tested_subject_digest, "d" * 64) if mutation == :review_subject_digest

  [files, subject_manifest_digest, tested_subject_digest]
end

def fast_import_commit(remote, files)
  stream = +"commit #{BRANCH_REF}\n"
  stream << "author Fixture <fixture@example.invalid> 1788048000 +0000\n"
  stream << "committer Fixture <fixture@example.invalid> 1788048000 +0000\n"
  stream << "data 15\nfixture commit\n"
  files.sort.each do |path, content|
    stream << "M 100644 inline #{path}\n"
    stream << "data #{content.bytesize}\n#{content}"
  end
  stream << "\ndone\n"
  run!("git", "--git-dir", remote, "fast-import", "--quiet", stdin_data: stream)
  run!("git", "--git-dir", remote, "rev-parse", BRANCH_REF).strip
end

def write_annotated_tag(remote, commit, tree, subject_manifest_digest, tested_subject_digest, evidence_digest, mutation)
  payload = {
    "phase" => PHASE,
    "package" => PACKAGE,
    "branch" => BRANCH_REF,
    "commit" => commit,
    "tree" => tree,
    "subject_manifest_path" => SUBJECT_PATH,
    "subject_manifest_digest" => subject_manifest_digest,
    "tested_subject_digest" => tested_subject_digest,
    "evidence_manifest_path" => EVIDENCE_PATH,
    "evidence_manifest_digest" => evidence_digest,
    "pr_locator" => PR_LOCATOR,
    "check_name" => CHECK_NAME,
    "check_locator" => CHECK_LOCATOR,
    "attestor_name" => TAGGER_NAME,
    "attestor_email" => TAGGER_EMAIL,
    "external_actor" => "fixture:phase-reviewer",
    "status" => "PASS"
  }
  payload["commit"] = "a" * 40 if mutation == :tag_commit
  payload["tree"] = "b" * 40 if mutation == :tag_tree
  payload["subject_manifest_digest"] = "c" * 64 if mutation == :tag_subject_manifest_digest
  payload["tested_subject_digest"] = "c" * 64 if mutation == :tag_tested_subject_digest
  payload["evidence_manifest_digest"] = "c" * 64 if mutation == :tag_evidence_digest
  payload["pr_locator"] = "local://pull/99" if mutation == :tag_pr_locator
  payload["check_name"] = "wrong-check" if mutation == :tag_check_name
  payload["external_actor"] = "fixture:wrong-reviewer" if mutation == :tag_external_actor
  payload_rows = payload.to_a
  payload_rows = payload_rows.reverse if mutation == :tag_field_order
  payload_rows = payload_rows.reject { |key, _value| key == "status" } if mutation == :tag_missing_field
  message = "ycsopen-sms-delivery-attestation-v1\n" + payload_rows.map { |key, value| "#{key}=#{value}" }.join("\n") + "\n"
  tag_object = <<~TAG
    object #{commit}
    type commit
    tag #{TAG_REF.delete_prefix('refs/tags/')}
    tagger #{TAGGER_NAME} <#{TAGGER_EMAIL}> 1788048000 +0000

    #{message}
  TAG
  tag_sha = run!("git", "--git-dir", remote, "mktag", stdin_data: tag_object).strip
  run!("git", "--git-dir", remote, "update-ref", TAG_REF, tag_sha)
end

def build_fixture(workspace, mutation)
  control = File.join(workspace, "control")
  remote = File.join(workspace, "remote.git")
  FileUtils.mkdir_p(control)
  run!("git", "init", "--quiet", control)
  run!("git", "init", "--quiet", "--bare", remote)
  run!("git", "-C", control, "config", "remote.origin.url", remote)

  files, subject_manifest_digest, tested_subject_digest = base_target_files(mutation)
  commit = fast_import_commit(remote, files)
  tree = run!("git", "--git-dir", remote, "rev-parse", "#{commit}^{tree}").strip
  evidence_digest = sha256(files.fetch(EVIDENCE_PATH))
  write_annotated_tag(remote, commit, tree, subject_manifest_digest, tested_subject_digest, evidence_digest, mutation)

  case mutation
  when :missing_tag
    run!("git", "--git-dir", remote, "update-ref", "-d", TAG_REF)
  when :lightweight_tag
    run!("git", "--git-dir", remote, "update-ref", TAG_REF, commit)
  when :moved_tag
    moved = run!(
      "git", "--git-dir", remote,
      "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid",
      "commit-tree", tree, "-p", commit, stdin_data: "moved\n"
    ).strip
    moved_tree = run!("git", "--git-dir", remote, "rev-parse", "#{moved}^{tree}").strip
    write_annotated_tag(remote, moved, moved_tree, subject_manifest_digest, tested_subject_digest, evidence_digest, :none)
  end

  summary_remote = mutation == :remote_url ? "#{remote}.wrong" : remote
  summary_branch = mutation == :branch_ref ? "refs/heads/phase/missing" : BRANCH_REF
  summary = <<~MARKDOWN
    # Phase delivery locator

    Delivery remote name: `origin`
    Delivery remote URL: `#{summary_remote}`
    Delivery branch ref: `#{summary_branch}`
    Delivery tag ref: `#{TAG_REF}`
    Delivery PR locator: `#{PR_LOCATOR}`
    Delivery required check: `#{CHECK_NAME}`
  MARKDOWN
  write(File.join(control, "summary.md"), summary)
  write(File.join(control, EVIDENCE_PATH), files.fetch(EVIDENCE_PATH))
  pr_state = {
    "schema_version" => "phase01-local-pr-check-v1",
    "pr_locator" => PR_LOCATOR,
    "head_sha" => commit,
    "state" => "OPEN",
    "check_name" => CHECK_NAME,
    "check_locator" => CHECK_LOCATOR,
    "check_status" => "COMPLETED",
    "check_conclusion" => mutation == :check_not_pass ? "FAILURE" : "SUCCESS",
    "external_actor" => mutation == :external_actor ? "fixture:other-reviewer" : "fixture:phase-reviewer"
  }
  write(File.join(control, "pr-state.json"), json_bytes(pr_state))
  case mutation
  when :local_summary_symlink
    outside = File.join(workspace, "outside-summary.txt")
    write(outside, "EXTERNAL_DELIVERY_CANARY\n")
    File.rename(File.join(control, "summary.md"), File.join(control, "summary.original.md"))
    File.symlink(outside, File.join(control, "summary.md"))
  when :local_intermediate_symlink
    planning = File.join(control, ".planning")
    File.rename(planning, "#{planning}.original")
    outside_planning = File.join(workspace, "outside-planning")
    write(File.join(outside_planning, PHASE_DIR.delete_prefix(".planning/"), "EVIDENCE", "evidence-manifest.json"), "EXTERNAL_DELIVERY_CANARY\n")
    File.symlink(outside_planning, planning)
  when :local_summary_hardlink
    File.link(File.join(control, "summary.md"), File.join(control, "summary-hardlink.md"))
  when :local_root_symlink
    link = File.join(workspace, "control-link")
    File.symlink(control, link)
    return link
  end
  control
end

def run_case(workspace, name, mutation, expected_success, token)
  root = File.join(workspace, name)
  control = build_fixture(root, mutation)
  argv = [
    RbConfig.ruby, VALIDATOR,
    "--root", control,
    "--phase", PHASE,
    "--summary", mutation == :local_outside_escape ? "../outside-summary.txt" : "summary.md",
    "--evidence-manifest", EVIDENCE_PATH,
    "--require-pr-check-pass",
    "--allow-local-fixture",
    "--fixture-pr-state", "pr-state.json"
  ]
  stdout, stderr, status = Open3.capture3(*argv, chdir: control)
  output = stdout + stderr
  abort "STATUS_MISMATCH case=#{name} expected=#{expected_success}\n#{output}" unless status.success? == expected_success
  abort "TOKEN_MISSING case=#{name} token=#{token}\n#{output}" unless output.include?(token)
  abort "EXTERNAL_CANARY_LEAK case=#{name}" if output.include?("EXTERNAL_DELIVERY_CANARY")
end

abort "DELIVERY_VALIDATOR_MISSING: #{VALIDATOR}" unless File.file?(VALIDATOR)

cases = [
  ["valid-legacy", :none, true, "DELIVERY_ATTESTATION PASS"],
  ["valid-exact-seven", :exact, true, "DELIVERY_ATTESTATION PASS"],
  ["exact-missing-entry", :exact_missing_entry, false, "OBLIGATION_EVIDENCE_ENTRY_SET_INVALID"],
  ["exact-duplicate-entry", :exact_duplicate_entry, false, "OBLIGATION_EVIDENCE_ENTRY_SET_INVALID"],
  ["exact-foreign-obligation", :exact_foreign_obligation, false, "OBLIGATION_EVIDENCE_ENTRY_SET_INVALID"],
  ["exact-summary-checksum", :exact_summary_checksum, false, "CHECKSUM_MISMATCH"],
  ["exact-runtime-checksum", :exact_runtime_checksum, false, "OBLIGATION_RUNTIME_ARTIFACT_CHECKSUM_MISMATCH"],
  ["exact-ci-checksum", :exact_ci_checksum, false, "OBLIGATION_CI_LOCATOR index=0_CHECKSUM_MISMATCH"],
  ["exact-runtime-subject-binding", :exact_runtime_subject_binding, false, "OBLIGATION_BROWSER_RUNTIME_TESTED_SUBJECT_DIGEST_MISMATCH"],
  ["missing-tag", :missing_tag, false, "DELIVERY_TAG_MISSING"],
  ["lightweight-tag", :lightweight_tag, false, "DELIVERY_TAG_NOT_ANNOTATED"],
  ["moved-tag", :moved_tag, false, "DELIVERY_TAG_TARGET_MISMATCH"],
  ["remote-url", :remote_url, false, "REMOTE_URL_MISMATCH"],
  ["branch-ref", :branch_ref, false, "REMOTE_BRANCH_REF_MISSING"],
  ["tag-commit", :tag_commit, false, "TAG_COMMIT_MISMATCH"],
  ["tag-tree", :tag_tree, false, "TAG_TREE_MISMATCH"],
  ["tag-field-order", :tag_field_order, false, "DELIVERY_TAG_FIELD_ORDER_INVALID"],
  ["tag-missing-field", :tag_missing_field, false, "DELIVERY_TAG_PAYLOAD_FIELDS_INVALID"],
  ["missing-required-input", :missing_required_input, false, "SUBJECT_REQUIRED_INPUT_MISSING"],
  ["legacy-browser-source-membership", :legacy_browser_source_membership, false, "SUBJECT_ILLEGAL_EXCLUSION"],
  ["local-entry-membership", :local_entry_membership, false, "SUBJECT_ILLEGAL_EXCLUSION"],
  ["entry-review-membership", :entry_review_membership, false, "SUBJECT_ILLEGAL_EXCLUSION"],
  ["extra-missing-input", :extra_missing_input, false, "SUBJECT_INPUT_TARGET_MISSING"],
  ["changed-content", :changed_content, false, "SUBJECT_INPUT_CONTENT_MISMATCH"],
  ["changed-mode", :changed_mode, false, "SUBJECT_INPUT_MODE_MISMATCH"],
  ["illegal-exclusion", :illegal_exclusion, false, "SUBJECT_ILLEGAL_EXCLUSION"],
  ["tag-subject-manifest", :tag_subject_manifest_digest, false, "TAG_SUBJECT_MANIFEST_DIGEST_MISMATCH"],
  ["tag-tested-subject", :tag_tested_subject_digest, false, "TAG_TESTED_SUBJECT_DIGEST_MISMATCH"],
  ["evidence-subject-manifest", :evidence_subject_manifest_digest, false, "EVIDENCE_SUBJECT_MANIFEST_DIGEST_MISMATCH"],
  ["evidence-tested-subject", :evidence_tested_subject_digest, false, "EVIDENCE_TESTED_SUBJECT_DIGEST_MISMATCH"],
  ["tag-evidence", :tag_evidence_digest, false, "TAG_EVIDENCE_MANIFEST_DIGEST_MISMATCH"],
  ["tag-pr-locator", :tag_pr_locator, false, "TAG_PR_LOCATOR_MISMATCH"],
  ["tag-check-name", :tag_check_name, false, "TAG_CHECK_NAME_MISMATCH"],
  ["check-not-pass", :check_not_pass, false, "PR_CHECK_NOT_PASS"],
  ["tag-external-actor", :tag_external_actor, false, "TAG_EXTERNAL_ACTOR_MISMATCH"],
  ["external-actor", :external_actor, false, "TAG_EXTERNAL_ACTOR_MISMATCH"],
  ["review-subject", :review_subject_digest, false, "REVIEW_TESTED_SUBJECT_DIGEST_MISMATCH"],
  ["local-summary-symlink", :local_summary_symlink, false, "SUMMARY_SYMLINK_COMPONENT"],
  ["local-intermediate-symlink", :local_intermediate_symlink, false, "LOCAL_EVIDENCE_MANIFEST_SYMLINK_COMPONENT"],
  ["local-summary-hardlink", :local_summary_hardlink, false, "SUMMARY_LINK_COUNT_INVALID"],
  ["local-root-symlink", :local_root_symlink, false, "SUMMARY_ROOT_SYMLINK"],
  ["local-outside-escape", :local_outside_escape, false, "SUMMARY_PATH_INVALID"]
].freeze

Dir.mktmpdir("phase01-delivery-") do |workspace|
  cases.each { |name, mutation, success, token| run_case(workspace, name, mutation, success, token) }
end

puts "DELIVERY_ATTESTATION_TEST PASS cases=#{cases.length} destructive=#{cases.count { |_name, _mutation, success, _token| !success }}"
