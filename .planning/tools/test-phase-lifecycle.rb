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

def create_review(path, manifest_path, manifest_digest, subject_digest, *, blocker: 0, high: 0, escalated: "no", result: "PASS")
  write(path, <<~MARKDOWN)
    # Review

    | Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
    | --- | --- | --- | --- | --- | --- | --- | --- |
    | 1 | #{blocker} | #{high} | #{escalated} | #{manifest_path} | #{manifest_digest} | #{subject_digest} | #{result} |

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
