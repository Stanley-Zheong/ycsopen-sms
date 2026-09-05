#!/usr/bin/env ruby
# frozen_string_literal: true

require "digest"
require "fileutils"
require "json"
require "open3"
require "tmpdir"

require_relative "verification-evidence"
require_relative "validate-phase-lifecycle"

VALIDATOR = File.expand_path("validate-phase-lifecycle.rb", __dir__)
TRACE_VALIDATOR = File.expand_path("validate-trace-closure.rb", __dir__)
EVIDENCE_VALIDATOR = File.expand_path("validate-verification-evidence.rb", __dir__)
EVIDENCE_KERNEL = File.expand_path("verification-evidence.rb", __dir__)
CHROME_ENTRY_CONTRACT = File.expand_path("phase01-chrome-entry-contract.rb", __dir__)
SOURCE_PHASE_DIR = File.expand_path("../phases/01-engineering-verification-foundation", __dir__)
OWNER = "engineering-verification-foundation"
OWNED = %w[
  OBL-FOUND-TRACE-001
  OBL-FOUND-TRACE-002
  OBL-FOUND-TRACE-003
  OBL-FOUND-TRACE-004
  OBL-FOUND-UI-DRIFT-001
  OBL-FOUND-UI-DRIFT-002
  OBL-NFR-BROWSER
].freeze

exact_entry = {
  "obligation_id" => "OBL-FOUND-TRACE-004",
  "path" => ".planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-TRACE-004.json",
  "status" => "PASS"
}
exact_path = exact_entry.fetch("path")
unless Phase01Lifecycle.obligation_entry_matches?(
  Phase01Lifecycle::OBLIGATION_EVIDENCE_SCHEMA, exact_entry, "OBL-FOUND-TRACE-004", exact_path
)
  abort "EXACT_SEVEN_ENTRY_KNOWN_GOOD_REJECTED"
end
%w[obligation_id path status].each do |field|
  mutation = JSON.parse(JSON.generate(exact_entry))
  mutation[field] = field == "status" ? "FAIL" : "foreign"
  if Phase01Lifecycle.obligation_entry_matches?(
    Phase01Lifecycle::OBLIGATION_EVIDENCE_SCHEMA, mutation, "OBL-FOUND-TRACE-004", exact_path
  )
    abort "EXACT_SEVEN_ENTRY_MUTATION_ACCEPTED field=#{field}"
  end
end
legacy_entry = exact_entry.reject { |key, _value| key == "obligation_id" }.merge("obligation_ids" => ["OBL-FOUND-TRACE-004"])
unless Phase01Lifecycle.obligation_entry_matches?(
  Phase01Lifecycle::LEGACY_EVIDENCE_SCHEMA, legacy_entry, "OBL-FOUND-TRACE-004", exact_path
)
  abort "LEGACY_ENTRY_EXPLICIT_DISPATCH_REJECTED"
end
if Phase01Lifecycle.obligation_entry_matches?("phase01-foreign-manifest-v1", exact_entry, "OBL-FOUND-TRACE-004", exact_path)
  abort "UNSUPPORTED_EVIDENCE_SCHEMA_ACCEPTED"
end
phase03_entry = {
  "obligation_id" => "OBL-CRYPTO-STORAGE-001",
  "path" => ".planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-001.json",
  "status" => "PASS"
}
unless Phase01Lifecycle.obligation_entry_matches?(
  Phase01Lifecycle::PHASE03_EVIDENCE_SCHEMA,
  phase03_entry,
  "OBL-CRYPTO-STORAGE-001",
  phase03_entry.fetch("path")
)
  abort "PHASE03_ENTRY_EXPLICIT_DISPATCH_REJECTED"
end

def write(path, content, mode: nil)
  FileUtils.mkdir_p(File.dirname(path))
  File.write(path, content)
  File.chmod(mode, path) if mode
end

def copy(path, destination)
  FileUtils.mkdir_p(File.dirname(destination))
  FileUtils.cp(path, destination, preserve: true)
end

def catalog_line(id, requirement, owner, behavior, test_id, evidence)
  "- #{id} | synthetic source | #{requirement} | #{owner} | #{behavior} | - | #{test_id}:static | #{evidence} | Synthetic obligation #{id}."
end

def create_trace_artifacts(root, phase_dir)
  write(File.join(root, ".planning/REQUIREMENTS.md"), <<~MARKDOWN)
    # Requirements

    | Requirement | Required outcome | Owning phase |
    | --- | --- | --- |
    | REQ-NFR-COMPATIBILITY | Compatibility contract. | Phase 1 and Phase 56 |
  MARKDOWN

  catalog = [
    "# PRD Atomic Obligation Catalog",
    "",
    "- PROJECT-PLANNING-TRACE: planning trace authority.",
    "- PROJECT-UI-CONTRACT: UI contract authority.",
    ""
  ]
  OWNED.each_with_index do |id, index|
    requirement = id == "OBL-NFR-BROWSER" ? "REQ-NFR-COMPATIBILITY" : (id.include?("UI-DRIFT") ? "PROJECT-UI-CONTRACT" : "PROJECT-PLANNING-TRACE")
    behavior = id.include?("UI-DRIFT") ? "engineering-verification-foundation-05" : (id == "OBL-NFR-BROWSER" ? "engineering-verification-foundation-03" : format("engineering-verification-foundation-%02d", index + 1))
    catalog << catalog_line(id, requirement, OWNER, behavior, "T-#{id.delete_prefix('OBL-')}", "EVIDENCE/#{id}.json")
  end
  catalog << catalog_line("OBL-NFR-CHINESE", "REQ-NFR-COMPATIBILITY", "final-release-acceptance", "final-release-acceptance-01", "T-NFR-CHINESE", "EVIDENCE/OBL-NFR-CHINESE.json")
  catalog << catalog_line("OBL-NFR-TIMEZONE", "REQ-NFR-COMPATIBILITY", "final-release-acceptance", "final-release-acceptance-02", "T-NFR-TIMEZONE", "EVIDENCE/OBL-NFR-TIMEZONE.json")
  write(File.join(root, ".planning/PRD-OBLIGATIONS.md"), catalog.join("\n") + "\n")

  spec_rows = OWNED.each_with_index.map do |id, index|
    fields = catalog.find { |line| line.start_with?("- #{id} |") }.split(" | ")
    "| #{id} | #{fields[2]} | #{fields[4]} | engineering-verification-foundation-V#{format('%02d', index + 1)} |"
  end
  write(File.join(phase_dir, "01-SPEC.md"), <<~MARKDOWN)
    # Synthetic spec

    ## Requirement and obligation trace

    | Obligation ID | Requirement | Behavior IDs | Verification IDs |
    | --- | --- | --- | --- |
    #{spec_rows.join("\n")}
  MARKDOWN

  matrix_rows = OWNED.each_with_index.map do |id, index|
    fields = catalog.find { |line| line.start_with?("- #{id} |") }.split(" | ")
    "| #{id} | #{fields[2]} | #{fields[4]} | #{fields[6]} | not-applicable | not-applicable | not-applicable | CASE-#{index + 1} | Synthetic case | `ruby synthetic.rb` | #{fields[7]} |"
  end
  write(File.join(phase_dir, "TEST-MATRIX.md"), <<~MARKDOWN)
    # Test Matrix

    | Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
    | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
    #{matrix_rows.join("\n")}
  MARKDOWN

  write(File.join(phase_dir, "01-99-PLAN.md"), <<~MARKDOWN)
    ---
    phase: 01-engineering-verification-foundation
    plan: 99
    obligations: [#{OWNED.join(', ')}]
    ---

    <tasks>
    <task type="auto">
      <name>Synthetic lifecycle task</name>
      <files>synthetic.rb</files>
      <action>Run the synthetic lifecycle contract.</action>
      <verify><automated>ruby synthetic.rb</automated></verify>
      <done>The synthetic lifecycle contract passes.</done>
    </task>
    </tasks>
  MARKDOWN
  write(File.join(root, "synthetic.rb"), "puts 'PASS'\n")
end

def create_entry_review(path)
  evidence = File.join(SOURCE_PHASE_DIR, "EVIDENCE/local-chrome-entry.json")
  digest = Digest::SHA256.file(evidence).hexdigest
  rows = Phase01ChromeEntryContract::REVIEW_CRITERIA.map do |criterion_id|
    "| #{criterion_id} | PASS | fixture local entry #{digest} | independent fixture inspection |"
  end
  write(path, <<~MARKDOWN)
    # Entry Review

    Executor identity: `lifecycle-fixture-executor`
    Reviewer identity: `lifecycle-fixture-independent-reviewer`

    | Criterion ID | Verdict | Evidence | Command or inspection rule |
    | --- | --- | --- | --- |
    #{rows.join("\n")}

    ## Verdict

    PASS
  MARKDOWN
end

def create_review(path, manifest_path, manifest_digest, subject_digest, *, attempt: 1, blocker: 0, high: 0, escalated: "no", result: "PASS")
  write(path, <<~MARKDOWN)
    # Review

    | Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
    | --- | --- | --- | --- | --- | --- | --- | --- |
    | #{attempt} | #{blocker} | #{high} | #{escalated} | #{manifest_path} | #{manifest_digest} | #{subject_digest} | #{result} |

    ## Final verdict

    #{result}
  MARKDOWN
end

def create_evidence(root, phase_dir)
  copy(EVIDENCE_KERNEL, File.join(root, ".planning/tools/verification-evidence.rb"))
  copy(EVIDENCE_VALIDATOR, File.join(root, ".planning/tools/validate-verification-evidence.rb"))
  copy(CHROME_ENTRY_CONTRACT, File.join(root, ".planning/tools/phase01-chrome-entry-contract.rb"))
  copy(
    File.join(SOURCE_PHASE_DIR, "EVIDENCE/local-chrome-entry.json"),
    File.join(phase_dir, "EVIDENCE/local-chrome-entry.json")
  )

  input_path = ".planning/tools/lifecycle-fixture-input.rb"
  write(File.join(root, input_path), "# frozen_string_literal: true\nLIFECYCLE_FIXTURE = true\n")
  checks = OWNED.each_with_index.map do |id, index|
    {
      "id" => "lifecycle-fixture-#{index + 1}",
      "layer" => "validator",
      "argv" => ["/usr/bin/env", "ruby", input_path],
      "cwd" => ".",
      "obligation_ids" => [id],
      "case_ids" => ["CASE-LIFECYCLE-#{index + 1}"],
      "inputs" => [{ "path" => input_path, "role" => "test" }]
    }
  end
  module_source = <<~RUBY
    # frozen_string_literal: true
    module Phase01RunChecks
      CHECKS = #{checks.inspect}.freeze
      module_function
      def subject_registries
        CHECKS.to_h { |definition| [definition.fetch("id"), definition.fetch("inputs")] }
      end
      def check_contracts
        CHECKS.to_h { |definition| [definition.fetch("id"), definition] }
      end
    end
  RUBY
  write(File.join(root, "scripts/lib/phase-01/run_checks.rb"), module_source)

  registries = checks.to_h { |definition| [definition.fetch("id"), definition.fetch("inputs")] }
  contracts = checks.to_h { |definition| [definition.fetch("id"), definition] }
  subject_relative = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json"
  subject = VerificationEvidence.build_subject_manifest(root: root, registries: registries, manifest_path: subject_relative)
  subject_manifest_digest = VerificationEvidence.subject_manifest_digest(subject)
  tested_subject_digest = VerificationEvidence.tested_subject_digest(subject.fetch("inputs"))
  run_id = "phase01-20260830T000000-abcdef123456"
  envelopes = []
  evidence_paths = []
  checks.each do |definition|
    envelope = VerificationEvidence.build_envelope(
      run_id: run_id,
      check_id: definition.fetch("id"),
      layer: definition.fetch("layer"),
      obligation_ids: definition.fetch("obligation_ids"),
      case_ids: definition.fetch("case_ids"),
      argv: definition.fetch("argv"),
      cwd: definition.fetch("cwd"),
      started_at: "2026-08-30T00:00:00Z",
      completed_at: "2026-08-30T00:00:01Z",
      environment: {},
      status: "PASS",
      exit_code: 0,
      errors: [],
      diagnostics: [],
      artifacts: [],
      subject_manifest_path: subject_relative,
      subject_manifest_digest: subject_manifest_digest,
      tested_subject_digest: tested_subject_digest
    )
    validation = VerificationEvidence.validate_envelope(
      root: root,
      envelope: envelope,
      registries: registries,
      subject_manifest_path: subject_relative,
      check_contracts: contracts
    )
    abort "FIXTURE_ENVELOPE_INVALID #{validation.join('; ')}" unless validation.empty?
    evidence_relative = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/#{definition.fetch('obligation_ids').first}.json"
    VerificationEvidence.atomic_write_json(File.join(root, evidence_relative), envelope)
    envelopes << envelope
    evidence_paths << evidence_relative
  end
  evidence_sha256s = evidence_paths.map { |path| Digest::SHA256.file(File.join(root, path)).hexdigest }
  aggregate = VerificationEvidence.build_aggregate(
    run_id: run_id,
    envelopes: envelopes,
    evidence_paths: evidence_paths,
    evidence_sha256s: evidence_sha256s,
    subject_manifest_path: subject_relative,
    subject_manifest_digest: subject_manifest_digest,
    tested_subject_digest: tested_subject_digest
  )
  aggregate_relative = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/aggregate.json"
  VerificationEvidence.atomic_write_json(File.join(root, aggregate_relative), aggregate)
  manifest = VerificationEvidence.build_evidence_manifest(
    root: root,
    owner: OWNER,
    envelopes: envelopes,
    evidence_paths: evidence_paths,
    aggregate_path: aggregate_relative,
    subject_manifest_path: subject_relative,
    subject_manifest_digest: subject_manifest_digest,
    tested_subject_digest: tested_subject_digest
  )
  manifest_relative = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json"
  VerificationEvidence.atomic_write_json(File.join(root, manifest_relative), manifest)
  [manifest_relative, subject_relative, subject_manifest_digest, tested_subject_digest]
end

def create_fixture(root, exit_ready: false)
  phase_dir = File.join(root, ".planning/phases/01-engineering-verification-foundation")
  FileUtils.mkdir_p(File.join(phase_dir, "EVIDENCE"))
  create_trace_artifacts(root, phase_dir)
  %w[01-CONTEXT.md INTENT.md DESIGN.md ITERATIONS.md DECISIONS.md CLAUDE-REVIEW.md].each do |name|
    write(File.join(phase_dir, name), name == "DESIGN.md" ? "# Design\n\nSchema migrations: none\n" : "# #{name}\n\nSynthetic content.\n")
  end
  create_entry_review(File.join(phase_dir, "ENTRY-REVIEW.md"))
  copy(TRACE_VALIDATOR, File.join(root, ".planning/tools/validate-trace-closure.rb"))
  manifest_path, subject_path, manifest_digest, subject_digest = create_evidence(root, phase_dir)

  todo_rows = OWNED.map do |id|
    checked = exit_ready ? "x" : " "
    "- [#{checked}] #{id} — Evidence: EVIDENCE/#{id}.json"
  end
  todo_rows.concat([
    "- [#{exit_ready ? 'x' : ' '}] GSD goal verification has no unresolved blocking finding. Evidence: `01-VERIFICATION.md`.",
    "- [#{exit_ready ? 'x' : ' '}] GSD code review has no unresolved blocking finding. Evidence: `01-REVIEW.md`.",
    "- [#{exit_ready ? 'x' : ' '}] Claude final review has no BLOCKER or HIGH finding. Evidence: `CLAUDE-REVIEW.md`.",
    "- [#{exit_ready ? 'x' : ' '}] Every TEST-MATRIX command passes and each evidence target exists. Evidence: `EVIDENCE/evidence-manifest.json`.",
    "- [#{exit_ready ? 'x' : ' '}] Scoped TODO query returns no unchecked item. Evidence: lifecycle validator.",
    "- [ ] One atomic Phase 1 commit is visible on the configured GitHub remote and recorded in SUMMARY.md. Evidence: reserved external delivery attestation."
  ])
  write(File.join(phase_dir, "TODO.md"), "# TODO\n\n#{todo_rows.join("\n")}\n")
  create_review(File.join(phase_dir, "01-VERIFICATION.md"), subject_path, manifest_digest, subject_digest)
  create_review(File.join(phase_dir, "01-REVIEW.md"), subject_path, manifest_digest, subject_digest)
  create_review(File.join(phase_dir, "CLAUDE-REVIEW.md"), subject_path, manifest_digest, subject_digest)

  [phase_dir, manifest_path, subject_path, manifest_digest, subject_digest]
end

def validator_command(root, stage, extra = [])
  [
    RbConfig.ruby,
    VALIDATOR,
    "--root", root,
    "--phase", "01",
    "--package", OWNER,
    "--stage", stage,
    *extra
  ]
end

def run_case(root, stage, expected_success:, expected_token:, extra: [])
  stdout, stderr, status = Open3.capture3(*validator_command(root, stage, extra), chdir: root)
  output = stdout + stderr
  unless status.success? == expected_success
    abort "LIFECYCLE_TEST_STATUS_MISMATCH stage=#{stage} expected_success=#{expected_success} token=#{expected_token}\n#{output}"
  end
  abort "LIFECYCLE_TEST_TOKEN_MISSING stage=#{stage} token=#{expected_token}\n#{output}" unless output.include?(expected_token)
end

def mutation(workspace, baseline, name)
  path = File.join(workspace, "mutations", name)
  FileUtils.mkdir_p(File.dirname(path))
  FileUtils.cp_r(baseline, path)
  yield path
  path
end

Dir.mktmpdir("phase01-lifecycle-") do |workspace|
  entry_baseline = File.join(workspace, "entry-baseline")
  create_fixture(entry_baseline, exit_ready: false)
  run_case(entry_baseline, "entry", expected_success: true, expected_token: "PHASE_LIFECYCLE PASS stage=entry")

  exit_baseline = File.join(workspace, "exit-baseline")
  _phase_dir, manifest_path, subject_path, manifest_digest, subject_digest = create_fixture(exit_baseline, exit_ready: true)
  exit_args = ["--evidence-manifest", manifest_path, "--require-gsd-clear", "--require-claude-clear", "--allow-reserved-delivery"]
  run_case(exit_baseline, "pre-push-exit", expected_success: true, expected_token: "PHASE_LIFECYCLE PASS stage=pre-push-exit", extra: exit_args)

  cases = [
    ["missing-artifact", entry_baseline, "entry", "LIFECYCLE_ARTIFACT_MISSING", [], lambda do |root|
      FileUtils.rm_f(File.join(root, ".planning/phases/01-engineering-verification-foundation/INTENT.md"))
    end],
    ["missing-task-action", entry_baseline, "entry", "LIFECYCLE_PLAN_TASK_FIELD_MISSING", [], lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-99-PLAN.md")
      File.write(path, File.read(path).sub(/<action>.*?<\/action>/m, "<action></action>"))
    end],
    ["missing-task-files", entry_baseline, "entry", "LIFECYCLE_PLAN_TASK_FIELD_MISSING", [], lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-99-PLAN.md")
      File.write(path, File.read(path).sub(/<files>.*?<\/files>/m, "<files></files>"))
    end],
    ["missing-task-verify", entry_baseline, "entry", "LIFECYCLE_PLAN_TASK_FIELD_MISSING", [], lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-99-PLAN.md")
      File.write(path, File.read(path).sub(/<verify>.*?<\/verify>/m, "<verify></verify>"))
    end],
    ["missing-task-done", entry_baseline, "entry", "LIFECYCLE_PLAN_TASK_FIELD_MISSING", [], lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-99-PLAN.md")
      File.write(path, File.read(path).sub(/<done>.*?<\/done>/m, "<done></done>"))
    end],
    ["non-runnable-task-verify", entry_baseline, "entry", "LIFECYCLE_PLAN_VERIFY_NOT_RUNNABLE", [], lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/01-99-PLAN.md")
      File.write(path, File.read(path).sub("ruby synthetic.rb", "./missing-phase-command"))
    end],
    ["malformed-entry-review", entry_baseline, "entry", "LIFECYCLE_ENTRY_REVIEW_HEADER_INVALID", [], lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/ENTRY-REVIEW.md")
      File.write(path, File.read(path).sub("Command or inspection rule", "Command"))
    end],
    ["prechecked-entry-todo", entry_baseline, "entry", "LIFECYCLE_ENTRY_TODO_PRECHECKED", [], lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/TODO.md")
      File.write(path, File.read(path).sub("- [ ] OBL-FOUND-TRACE-001", "- [x] OBL-FOUND-TRACE-001"))
    end],
    ["unchecked-exit-todo", exit_baseline, "pre-push-exit", "LIFECYCLE_EXIT_TODO_UNCHECKED", exit_args, lambda do |root|
      path = File.join(root, ".planning/phases/01-engineering-verification-foundation/TODO.md")
      File.write(path, File.read(path).sub("- [x] OBL-FOUND-TRACE-001", "- [ ] OBL-FOUND-TRACE-001"))
    end],
    ["absent-evidence", exit_baseline, "pre-push-exit", "LIFECYCLE_TODO_EVIDENCE_MISSING", exit_args, lambda do |root|
      FileUtils.rm_f(File.join(root, ".planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-TRACE-001.json"))
    end],
    ["stale-tested-subject", exit_baseline, "pre-push-exit", "LIFECYCLE_EVIDENCE_INVALID", exit_args, lambda do |root|
      path = File.join(root, ".planning/tools/lifecycle-fixture-input.rb")
      File.open(path, "a") { |file| file.puts "# drift" }
    end],
    ["legacy-browser-source-membership", exit_baseline, "pre-push-exit", "SUBJECT_ILLEGAL_EXCLUSION", exit_args, lambda do |root|
      relative = ".planning/phases/01-engineering-verification-foundation/EVIDENCE/browser-source-admission.json"
      content = "superseded history fixture\n"
      write(File.join(root, relative), content)
      path = File.join(root, subject_path)
      subject = JSON.parse(File.read(path))
      subject.fetch("inputs") << {
        "path" => relative,
        "mode" => "100644",
        "sha256" => Digest::SHA256.hexdigest(content),
        "role" => "config"
      }
      subject.fetch("inputs").sort_by! { |entry| entry.fetch("path") }
      File.write(path, JSON.generate(subject) + "\n")
    end],
    ["gsd-blocker", exit_baseline, "pre-push-exit", "LIFECYCLE_REVIEW_BLOCKING_FINDINGS", exit_args, lambda do |root|
      create_review(File.join(root, ".planning/phases/01-engineering-verification-foundation/01-VERIFICATION.md"), subject_path, manifest_digest, subject_digest, blocker: 1, result: "BLOCKED")
    end],
    ["claude-high", exit_baseline, "pre-push-exit", "LIFECYCLE_REVIEW_BLOCKING_FINDINGS", exit_args, lambda do |root|
      create_review(File.join(root, ".planning/phases/01-engineering-verification-foundation/CLAUDE-REVIEW.md"), subject_path, manifest_digest, subject_digest, high: 1, result: "BLOCKED")
    end],
    ["review-escalated", exit_baseline, "pre-push-exit", "LIFECYCLE_REVIEW_ESCALATED", exit_args, lambda do |root|
      create_review(File.join(root, ".planning/phases/01-engineering-verification-foundation/01-REVIEW.md"), subject_path, manifest_digest, subject_digest, blocker: 1, escalated: "yes", result: "BLOCKED")
    end],
    ["review-stalled", exit_baseline, "pre-push-exit", "LIFECYCLE_REVIEW_STALLED_NOT_ESCALATED", exit_args, lambda do |root|
      write(File.join(root, ".planning/phases/01-engineering-verification-foundation/01-REVIEW.md"), <<~MARKDOWN)
        # Review

        | Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
        | --- | --- | --- | --- | --- | --- | --- | --- |
        | 1 | 1 | 0 | no | #{subject_path} | #{manifest_digest} | #{subject_digest} | BLOCKED |
        | 2 | 1 | 0 | no | #{subject_path} | #{manifest_digest} | #{subject_digest} | BLOCKED |

        ## Final verdict

        BLOCKED
      MARKDOWN
    end],
    ["phase01-noninitial-binding", exit_baseline, "pre-push-exit", "LIFECYCLE_REVIEW_ATTEMPT_SEQUENCE_INVALID", exit_args, lambda do |root|
      create_review(
        File.join(root, ".planning/phases/01-engineering-verification-foundation/01-REVIEW.md"),
        subject_path, manifest_digest, subject_digest, attempt: 2
      )
    end]
  ]
  cases.each do |name, baseline, stage, token, extra, change|
    fixture = mutation(workspace, baseline, name, &change)
    run_case(fixture, stage, expected_success: false, expected_token: token, extra: extra)
  end

  local_only = File.join(workspace, "local-only")
  FileUtils.mkdir_p(local_only)
  _stdout, stderr, status = Open3.capture3("git", "init", "--bare", File.join(local_only, "remote.git"))
  abort "LOCAL_BARE_GIT_INIT_FAILED #{stderr}" unless status.success?
  create_fixture(local_only, exit_ready: true)
  run_case(
    local_only,
    "post-push-delivery",
    expected_success: false,
    expected_token: "LIFECYCLE_DELIVERY_VALIDATOR_MISSING",
    extra: ["--evidence-manifest", ".planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json"]
  )

  fake_delivery = File.join(local_only, ".planning/tools/validate-delivery-attestation.rb")
  write(fake_delivery, <<~RUBY, mode: 0o755)
    #!/usr/bin/env ruby
    puts "delivery_attestation=PASS"
  RUBY
  run_case(
    local_only,
    "post-push-delivery",
    expected_success: true,
    expected_token: "PHASE_LIFECYCLE PASS stage=post-push-delivery",
    extra: ["--evidence-manifest", ".planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json"]
  )

  puts "PHASE_LIFECYCLE_TEST PASS cases=#{cases.length + 4} negative=#{cases.length + 1} positive=3 exact_seven_dispatch=PASS mutations=4 legacy_dispatch=PASS"
end

PHASE03_OWNER = "crypto-storage-bootstrap"
PHASE03_OWNED = %w[
  OBL-CRYPTO-STORAGE-001
  OBL-CRYPTO-STORAGE-002
  OBL-CRYPTO-STORAGE-003
  OBL-CRYPTO-STORAGE-004
].freeze

def phase03_lifecycle_fixture(root)
  relative_phase_dir = ".planning/phases/03-crypto-storage-bootstrap"
  phase_dir = File.join(root, relative_phase_dir)
  FileUtils.mkdir_p(File.join(phase_dir, "EVIDENCE"))
  write(File.join(root, "synthetic.rb"), "puts 'PASS'\n")
  catalog = PHASE03_OWNED.each_with_index.map do |id, index|
    catalog_line(
      id, "REQ-NFR-DATA-PROTECTION", PHASE03_OWNER,
      "crypto-storage-bootstrap-0#{index + 1}", "T-CRYPTO-STORAGE-00#{index + 1}",
      "EVIDENCE/#{id}.json"
    )
  end
  write(File.join(root, ".planning/PRD-OBLIGATIONS.md"), "# Catalog\n\n#{catalog.join("\n")}\n")
  write(File.join(phase_dir, "03-SPEC.md"), "# Phase 03 spec\n")
  write(File.join(phase_dir, "03-CONTEXT.md"), "# Phase 03 context\n")
  write(File.join(phase_dir, "INTENT.md"), "# Intent\n")
  write(File.join(phase_dir, "DESIGN.md"), "# Design\n\nSchema migrations: declared\n")
  write(File.join(phase_dir, "SCHEMA-CLAIMS.md"), "# Schema claims\n\nV1200 is expand-only.\n")
  write(File.join(phase_dir, "ITERATIONS.md"), "# Iterations\n")
  write(File.join(phase_dir, "DECISIONS.md"), "# Decisions\n")
  write(File.join(phase_dir, "TEST-MATRIX.md"), "# Test matrix\n")
  write(File.join(phase_dir, "03-01-PLAN.md"), <<~MARKDOWN)
    # Plan

    <tasks>
    <task type="auto">
      <files>synthetic.rb</files>
      <action>Run the Phase 03 fixture.</action>
      <verify><automated>ruby synthetic.rb</automated></verify>
      <done>The Phase 03 fixture passes.</done>
    </task>
    </tasks>
  MARKDOWN
  write(File.join(phase_dir, "ENTRY-REVIEW.md"), <<~MARKDOWN)
    # Entry review

    | Criterion ID | Verdict | Evidence | Command or inspection rule |
    | --- | --- | --- | --- |
    | PH03-ENTRY-001 | PASS | fixture | ruby synthetic.rb |

    ## Verdict

    PASS
  MARKDOWN

  subject_path = "#{relative_phase_dir}/EVIDENCE/tested-inputs.json"
  input_content = File.read(File.join(root, "synthetic.rb"))
  inputs = [{
    "path" => "synthetic.rb", "mode" => "100644",
    "sha256" => Digest::SHA256.hexdigest(input_content), "role" => "test"
  }]
  subject = {
    "schema_version" => "phase03-tested-inputs-v1",
    "phase" => "03-crypto-storage-bootstrap",
    "owner" => PHASE03_OWNER,
    "inputs" => inputs
  }
  write(File.join(root, subject_path), JSON.pretty_generate(subject) + "\n")
  subject_manifest_digest = Digest::SHA256.hexdigest(JSON.generate(Phase01Lifecycle.canonical(subject)))
  tested_subject_digest = Digest::SHA256.hexdigest(JSON.generate(Phase01Lifecycle.canonical(inputs)))
  subject_reference = {
    "path" => subject_path,
    "sha256" => Digest::SHA256.file(File.join(root, subject_path)).hexdigest,
    "tested_subject_digest" => tested_subject_digest
  }
  inventory_reference = {
    "path" => "core/src/main/resources/security/protected-data-inventory.json",
    "sha256" => "1" * 64,
    "accepted_digest" => "2" * 64,
    "validator_result" => {
      "check_id" => "phase03-protected-inventory",
      "path" => "core/target/phase03/results/protected-inventory-result.json",
      "sha256" => "3" * 64,
      "result_digest" => "4" * 64
    }
  }
  leak_reference = {
    "path" => "core/target/phase03/results/complete-leak-result.json",
    "sha256" => "5" * 64,
    "result_digest" => "6" * 64
  }
  entries = PHASE03_OWNED.map do |id|
    path = "#{relative_phase_dir}/EVIDENCE/#{id}.json"
    write(File.join(root, path), JSON.generate("obligation_id" => id, "status" => "PASS") + "\n")
    {
      "obligation_id" => id,
      "path" => path,
      "sha256" => Digest::SHA256.file(File.join(root, path)).hexdigest,
      "status" => "PASS",
      "evidence_digest" => "7" * 64
    }
  end
  manifest = {
    "schema_version" => "phase03-evidence-manifest-v1",
    "phase" => "03-crypto-storage-bootstrap",
    "owner" => PHASE03_OWNER,
    "status" => "PASS",
    "subject" => subject_reference,
    "inventory" => inventory_reference,
    "leak_result" => leak_reference,
    "entries" => entries
  }
  manifest_path = "#{relative_phase_dir}/EVIDENCE/evidence-manifest.json"
  write(File.join(root, manifest_path), JSON.pretty_generate(manifest) + "\n")

  todo = PHASE03_OWNED.map do |id|
    "- [x] #{id} is closed by the exact-four manifest. Evidence: `EVIDENCE/#{id}.json`."
  end
  todo.concat([
    "- [x] GSD goal verification has no unresolved blocking finding.",
    "- [x] GSD code review has no unresolved blocking or high finding.",
    "- [x] Claude convergence review has no unresolved blocking or high finding.",
    "- [x] Scoped TODO query is empty after the reserved external-delivery item closes.",
    "- [ ] Annotated delivery tag, required remote check and live delivery attestation pass."
  ])
  write(File.join(phase_dir, "TODO.md"), "# TODO\n\n#{todo.join("\n")}\n")
  create_review(File.join(phase_dir, "03-VERIFICATION.md"), subject_path, subject_manifest_digest, tested_subject_digest, attempt: 2)
  create_review(File.join(phase_dir, "03-REVIEW.md"), subject_path, subject_manifest_digest, tested_subject_digest, attempt: 2)
  create_review(File.join(phase_dir, "CLAUDE-REVIEW.md"), subject_path, subject_manifest_digest, tested_subject_digest, attempt: 2)

  write(File.join(root, ".planning/tools/validate-phase-03-crypto-evidence.rb"), <<~RUBY, mode: 0o755)
    #!/usr/bin/env ruby
    require "digest"
    require "json"
    phase_index = ARGV.index("--phase-dir")
    owner_index = ARGV.index("--require-owner")
    phase_dir = phase_index && ARGV[phase_index + 1]
    owner = owner_index && ARGV[owner_index + 1]
    manifest_path = File.join(phase_dir.to_s, "EVIDENCE/evidence-manifest.json")
    errors = []
    manifest = JSON.parse(File.read(manifest_path))
    errors << "SCHEMA" unless manifest["schema_version"] == "phase03-evidence-manifest-v1"
    errors << "OWNER" unless manifest["owner"] == owner
    errors << "STATUS" unless manifest["status"] == "PASS"
    subject = manifest["subject"]
    errors << "SUBJECT" unless subject.is_a?(Hash) && subject["path"] == File.join(phase_dir, "EVIDENCE/tested-inputs.json")
    entries = manifest["entries"]
    errors << "ENTRY_SET" unless entries.is_a?(Array) && entries.map { |row| row["obligation_id"] } == #{PHASE03_OWNED.inspect}
    Array(entries).each do |entry|
      errors << "ENTRY_STATUS" unless entry["status"] == "PASS"
      errors << "ENTRY_SHA" unless File.file?(entry["path"]) && Digest::SHA256.file(entry["path"]).hexdigest == entry["sha256"]
    end
    if errors.empty?
      puts "phase03_crypto_evidence=PASS"
      exit 0
    end
    warn "phase03_crypto_evidence=BLOCKED \#{errors.join(',')}"
    exit 1
  RUBY
  [relative_phase_dir, manifest_path, subject_path, subject_manifest_digest, tested_subject_digest]
end

def phase03_lifecycle_command(root, extra = [])
  [
    RbConfig.ruby, VALIDATOR,
    "--root", root,
    "--phase", "03",
    "--package", PHASE03_OWNER,
    "--stage", "pre-push-exit",
    *extra
  ]
end

def run_phase03_lifecycle_case(root, expected_success:, expected_token:, extra:)
  stdout, stderr, status = Open3.capture3(*phase03_lifecycle_command(root, extra), chdir: root)
  output = stdout + stderr
  unless status.success? == expected_success
    abort "PHASE03_LIFECYCLE_STATUS_MISMATCH expected=#{expected_success} token=#{expected_token}\n#{output}"
  end
  abort "PHASE03_LIFECYCLE_TOKEN_MISSING token=#{expected_token}\n#{output}" unless output.include?(expected_token)
end

Dir.mktmpdir("phase03-lifecycle-") do |workspace|
  baseline = File.join(workspace, "baseline")
  _phase_dir, manifest_path, subject_path, manifest_digest, subject_digest = phase03_lifecycle_fixture(baseline)
  common = [
    "--evidence-manifest", manifest_path,
    "--require-gsd-clear", "--require-claude-clear", "--allow-reserved-delivery"
  ]
  run_phase03_lifecycle_case(
    baseline, expected_success: true,
    expected_token: "PHASE_LIFECYCLE PASS stage=pre-push-exit phase=03 package=crypto-storage-bootstrap",
    extra: common
  )
  cases = [
    ["dynamic-artifact", "LIFECYCLE_ARTIFACT_MISSING", common, lambda do |root|
      FileUtils.rm_f(File.join(root, ".planning/phases/03-crypto-storage-bootstrap/03-SPEC.md"))
    end],
    ["dynamic-plan", "LIFECYCLE_PLAN_MISSING", common, lambda do |root|
      FileUtils.rm_f(File.join(root, ".planning/phases/03-crypto-storage-bootstrap/03-01-PLAN.md"))
    end],
    ["declared-schema-claims", "LIFECYCLE_SCHEMA_CLAIMS_REQUIRED", common, lambda do |root|
      FileUtils.rm_f(File.join(root, ".planning/phases/03-crypto-storage-bootstrap/SCHEMA-CLAIMS.md"))
    end],
    ["phase03-evidence-dispatch", "LIFECYCLE_EVIDENCE_INVALID", common, lambda do |root|
      path = File.join(root, manifest_path)
      manifest = JSON.parse(File.read(path))
      manifest["status"] = "BLOCKED"
      File.write(path, JSON.pretty_generate(manifest) + "\n")
    end],
    ["owned-todo-evidence-path", "LIFECYCLE_TODO_EVIDENCE_REFERENCE_MISSING", common, lambda do |root|
      path = File.join(root, ".planning/phases/03-crypto-storage-bootstrap/TODO.md")
      File.write(path, File.read(path).sub(
        " Evidence: `EVIDENCE/OBL-CRYPTO-STORAGE-001.json`.",
        ""
      ))
    end],
    ["dynamic-gsd-review", "LIFECYCLE_REVIEW_BLOCKING_FINDINGS", common, lambda do |root|
      create_review(
        File.join(root, ".planning/phases/03-crypto-storage-bootstrap/03-REVIEW.md"),
        subject_path, manifest_digest, subject_digest, high: 1, result: "BLOCKED"
      )
    end],
    ["noncontiguous-binding-revision", "LIFECYCLE_REVIEW_ATTEMPT_SEQUENCE_INVALID", common, lambda do |root|
      write(File.join(root, ".planning/phases/03-crypto-storage-bootstrap/03-REVIEW.md"), <<~MARKDOWN)
        # Review

        | Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
        | --- | --- | --- | --- | --- | --- | --- | --- |
        | 2 | 1 | 0 | no | #{subject_path} | #{manifest_digest} | #{subject_digest} | BLOCKED |
        | 4 | 0 | 0 | no | #{subject_path} | #{manifest_digest} | #{subject_digest} | PASS |

        ## Final verdict

        PASS
      MARKDOWN
    end],
    ["claude-convergence-row", "LIFECYCLE_REVIEW_BLOCKING_FINDINGS", ["--evidence-manifest", manifest_path, "--require-gsd-clear", "--allow-reserved-delivery"], lambda do |root|
      create_review(
        File.join(root, ".planning/phases/03-crypto-storage-bootstrap/CLAUDE-REVIEW.md"),
        subject_path, manifest_digest, subject_digest, high: 1, result: "BLOCKED"
      )
    end],
    ["reserved-delivery-row", "LIFECYCLE_RESERVED_DELIVERY_PREMATURELY_CHECKED", common, lambda do |root|
      path = File.join(root, ".planning/phases/03-crypto-storage-bootstrap/TODO.md")
      File.write(path, File.read(path).sub(
        "- [ ] Annotated delivery tag, required remote check and live delivery attestation pass.",
        "- [x] Annotated delivery tag, required remote check and live delivery attestation pass."
      ))
    end]
  ]
  cases.each do |name, token, extra, change|
    fixture = mutation(workspace, baseline, "phase03-#{name}", &change)
    run_phase03_lifecycle_case(fixture, expected_success: false, expected_token: token, extra: extra)
  end
  puts "PHASE03_LIFECYCLE_TEST PASS cases=#{cases.length + 1} negative=#{cases.length} positive=1 schema=actual nested_subject=PASS"
end
