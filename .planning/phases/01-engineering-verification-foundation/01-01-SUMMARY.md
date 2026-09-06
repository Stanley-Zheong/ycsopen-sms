---
phase: 01-engineering-verification-foundation
plan: 01
subsystem: verification
tags: [ruby, evidence, fail-closed, orchestration, chrome-gate-d]
supersession_notice: "Historical execution record only: all Chrome 151/152, downloaded-source, attestation, 12-plan bootstrap, and current/previous-browser claims are superseded by DR-01-016/017; later plans must regression-check this output after Plan 00 independent ENTRY PASS."

requires:
  - phase: phase-01-entry
    provides: independently attested Chrome 151/152 Gate D digest chain and blocking-free entry review
provides:
  - canonical path/mode/SHA-256/role tested-subject protocol
  - strict child-envelope, aggregate, and evidence-manifest validation
  - evidence-preserving repository-root Phase 1 runner
affects: [phase-01-plans-02-through-12, verification-evidence, delivery-attestation]

tech-stack:
  added: []
  patterns: [standard-library Ruby, code-owned argv registry, atomic JSON write, fail-closed status reduction]

key-files:
  created:
    - .planning/tools/verification-evidence.rb
    - .planning/tools/validate-verification-evidence.rb
    - .planning/tools/test-repository-verification.rb
    - scripts/verify-phase-01
    - scripts/lib/phase-01/run_checks.rb
    - scripts/lib/phase-01/test_run_checks.rb
  modified: []

key-decisions:
  - "Canonical subject membership comes only from code-owned per-check registries plus the immutable Gate D exception set."
  - "FAIL dominates BLOCKED, BLOCKED dominates PASS, and every child envelope is persisted before aggregate reduction."
  - "Committed evidence never requires or accepts final commit identity; delivery identity remains an external attestation concern."

patterns-established:
  - "Evidence identity: repository-relative path + full file mode + SHA-256 + one code-owned role."
  - "Execution: fixed argv, bounded child process, redacted output, atomic envelope, read-back validation, checksum-bound aggregate."

requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-only; no schedule, duration, percentage, or completion-date metric
---

# Phase 01 Plan 01: Evidence kernel and root orchestrator Summary

**A standard-library Ruby trust kernel now binds every check to a recomputable tested subject, preserves child diagnostics, and permits aggregate PASS only for an independently validated all-PASS set.**

## Scope result

- **Tasks:** 2/2 plan tasks implemented and verified.
- **Files created:** 10 implementation, test, schema, and fixture files; this SUMMARY is the eleventh plan artifact.
- **Phase status:** Not asserted. Phase 1 obligations and TODOs remain open for later plans.
- **Requirement status:** `REQ-NFR-COMPATIBILITY` is addressed by this plan but is not marked complete.

## Accomplishments

- Implemented canonical `tested-inputs` construction and validation with stable ordering, repository containment, regular-file/mode/content verification, code-owned roles, exact generated-metadata exclusions, and mandatory Gate D admission/probe/attestation/entry-review inputs.
- Implemented strict evidence envelope, aggregate, and evidence-manifest validation, including artifact/envelope checksums, exact check/case/obligation contracts, UTC timestamps, sanitized environment identity, secret/phone rejection, and prohibition of final commit identity.
- Implemented `./scripts/verify-phase-01` and an allowlisted Ruby runner that handles PASS, FAIL, BLOCKED, missing executables, malformed output, timeout, and interruption without erasing earlier child evidence.
- Added one positive synthetic evidence fixture plus 19 isolated destructive mutations and 10 runner behavior cases.
- Kept browser scope restricted to desktop Google Chrome current/previous stable; no Edge, Safari, Firefox, provider, tunnel, secret, VM, or unsupported-browser UI path was added.

## Task commits

1. **Task 1: Canonical tested-subject and versioned evidence protocols** — deferred to single Phase 1 delivery commit.
2. **Task 2: Allowlisted evidence-preserving root orchestrator** — deferred to single Phase 1 delivery commit.

No Git staging or commit was performed. This follows the project-specific single atomic Phase 1 delivery rule, which overrides GSD's default per-task commit convention.

## Files created

- `.planning/tools/verification-evidence.rb` — canonical subject, Gate D chain, envelope, aggregate, evidence-manifest, redaction, checksum, and atomic-write kernel.
- `.planning/tools/validate-verification-evidence.rb` — independent CLI for subject/envelope/aggregate and final evidence-manifest validation.
- `.planning/tools/test-repository-verification.rb` — positive fixture and 19 single-invariant destructive mutations.
- `.planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/tested-inputs.schema.json` — strict tested-input contract.
- `.planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/evidence-envelope.schema.json` — strict child-envelope contract.
- `.planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/aggregate.schema.json` — strict checksum-bound aggregate contract.
- `.planning/phases/01-engineering-verification-foundation/EVIDENCE/fixtures/evidence-mutations.json` — stable mutation-to-error-ID catalog.
- `scripts/verify-phase-01` — sole public Phase 1 repository-root wrapper.
- `scripts/lib/phase-01/run_checks.rb` — code-owned registry, bounded process runner, immediate evidence writer, read-back validator, and reducer.
- `scripts/lib/phase-01/test_run_checks.rb` — runner fail-closed and evidence-retention tests.

## Decisions made

- Gate D validation is performed before subject construction and again during subject validation. Admission/probe replacement, mode/owner drift, accepted-digest mismatch, and probe digest mismatch all fail with stable diagnostics.
- Subject manifests cannot declare exclusions. The exclusion law lives in code; schemas/fixtures and the exact Gate D exception set remain eligible code-owned inputs.
- Aggregate evidence contains ordered check IDs, envelope paths, and envelope SHA-256 values, then parses and compares persisted envelopes before acceptance.
- The validator already supports the downstream `--manifest ... --require-owner engineering-verification-foundation` contract so Plan 10 can seal the complete evidence set without adding executable command data to mutable JSON.

## Deviations from plan

### Auto-fixed issues

**1. [Rule 2 - Missing critical] Bound aggregate rows to persisted envelope checksums**

- **Found during:** Task 2 self-review.
- **Issue:** Ordered evidence paths alone did not detect an envelope changed after in-memory validation.
- **Fix:** Added ordered envelope SHA-256 values, persisted-envelope JSON comparison, and aggregate checksum validation.
- **Files modified:** evidence kernel, aggregate schema, runner.
- **Verification:** runner tests and independent aggregate validation pass.
- **Commit:** deferred to single Phase 1 delivery commit.

**2. [Rule 1 - Bug] Preserved canonical JSON boolean values**

- **Found during:** Task 2 generated-evidence inspection.
- **Issue:** Canonical hash lookup used truthiness and could serialize `false` as `null`.
- **Fix:** Switched to key-presence lookup so false and null remain distinct.
- **Files modified:** evidence kernel.
- **Verification:** generated envelope retains the factual `ci: false` value and validates.
- **Commit:** deferred to single Phase 1 delivery commit.

**3. [Rule 2 - Missing critical] Added final evidence-manifest validation interface**

- **Found during:** downstream contract audit against Plan 10.
- **Issue:** Plan 10 invokes `--manifest` but does not own the Plan 1 validator file.
- **Fix:** Added strict owner/subject/entry/aggregate validation and per-run evidence-manifest generation to prove the interface now.
- **Files modified:** evidence kernel, validator CLI, runner.
- **Verification:** both direct subject/envelope/aggregate validation and `--manifest --require-owner` validation pass.
- **Commit:** deferred to single Phase 1 delivery commit.

**Total deviations:** 3 auto-fixed (1 bug, 2 missing-critical safeguards). No business, UI, schema, or browser-scope expansion.

## Verification evidence

Passed commands:

- `/usr/bin/env ruby .planning/tools/test-repository-verification.rb` — `PASS`, 19 destructive mutations.
- `/usr/bin/env ruby scripts/lib/phase-01/test_run_checks.rb` — `PASS`, 10 runner cases.
- `/usr/bin/env ruby .planning/tools/test-produce-phase-01-chrome-entry.rb` — `PASS`, offline producer safety suite.
- `/usr/bin/env ruby .planning/tools/test-bootstrap-phase-01.rb` — `PASS`, 25 Gate D mutations and exact seven-obligation scope.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — `PASS` for positive and listed fail-closed cases.
- `/usr/bin/env ruby .planning/tools/bootstrap-phase-01.rb --phase-dir .planning/phases/01-engineering-verification-foundation --catalog .planning/PRD-OBLIGATIONS.md` — `PHASE_01_BOOTSTRAP PASS`, seven obligations, 12 plans.
- `./scripts/verify-phase-01 --self-test --evidence-dir .planning/phases/01-engineering-verification-foundation/EVIDENCE` — aggregate `PASS`.
- `validate-verification-evidence.rb` over emitted subject, envelope, aggregate — `PASS`.
- `validate-verification-evidence.rb --manifest ... --require-owner engineering-verification-foundation --check evidence-kernel-self-test` — `PASS`.
- Ruby warning/syntax checks for all Ruby files, `bash -n scripts/verify-phase-01`, JSON parsing for all schemas/fixtures, and `git diff --check` — `PASS`.

`shellcheck` was unavailable; `bash -n` verified the eight-line fixed-argv wrapper. The generated self-test run directory was removed after all four emitted JSON files passed independent validation; it is reproducible runtime output and is not committed evidence.

## Known deferred registrations

- `--all` currently contains the evidence-kernel self-test only. Plan 09 owns registration of the complete repository matrix.
- `--timezone` is recognized as a stable selector but correctly returns a nonzero empty-selection diagnostic until its Plan 08/09 check is registered.
- No obligation or TODO is closed by these synthetic runs.

## Security and privacy check

- No package was installed.
- No credentials, production data, phone numbers, message bodies, private repository content, or developer absolute paths were written to durable fixtures.
- Child commands use argv arrays without shell evaluation; environment serialization is allowlisted and diagnostic output is redacted before hashing.
- Gate D admission, probes, attestation, and `ENTRY-REVIEW.md` were read and validated only; none was rewritten.

## Self-check: PASSED

- All ten plan-owned implementation/test/schema/fixture files exist.
- Required executable files have mode `0755`; JSON data files remain regular repository files.
- Focused tests, destructive fixtures, real bootstrap, independent evidence validation, syntax checks, and whitespace checks pass.
- No files outside Plan 01-01 ownership plus this SUMMARY were created or modified by this executor.
- No Git staging, commit, branch, stash, checkout, reset, or push operation was performed.

## Next plan readiness

The evidence protocol and stable root runner interface are ready for Plan 01-02 trace/lifecycle checks and later code-owned check registration. Phase 1 remains incomplete until its scoped TODO is empty and the final review/delivery gates pass.
