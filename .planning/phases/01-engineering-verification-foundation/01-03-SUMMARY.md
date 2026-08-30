---
phase: 01-engineering-verification-foundation
plan: 03
subsystem: verification
tags: [ruby, git, annotated-tag, delivery-attestation, fail-closed]
supersession_notice: "Historical execution record only: all Chrome 151/152, current/previous-browser, browser-source, and 12-plan bootstrap claims are superseded by DR-01-016/017; later plans must regression-check delivery consumers after Plan 00 independent ENTRY PASS."

requires:
  - phase: 01-engineering-verification-foundation
    plan: 01
    provides: canonical tested-subject and evidence-manifest protocol
  - phase: 01-engineering-verification-foundation
    plan: 02
    provides: lifecycle stages and dependency gate delegation
provides:
  - configured-remote branch plus annotated-tag attestation validation
  - target-tree subject, evidence, review, and PR/check recomputation
  - local bare-remote positive and destructive delivery fixtures
  - dependency entry consumption of live delivery attestations
affects: [phase-01-plan-12, later-phase-entry, atomic-delivery, delivery-evidence]

tech-stack:
  added: []
  patterns: [standard-library Ruby, fixed argv, isolated bare object store, Ripper restricted-literal registry parsing]

key-files:
  created:
    - .planning/tools/validate-delivery-attestation.rb
    - .planning/tools/test-delivery-attestation.rb
    - .planning/phases/01-engineering-verification-foundation/01-03-SUMMARY.md
  modified:
    - .planning/tools/planning-validator-support.rb
    - .planning/tools/validate-phase-entry.rb
    - .planning/EXECUTION-STANDARD.md
    - .planning/ROADMAP.md
    - .planning/STATE.md

key-decisions:
  - "Local commit, branch, tracking ref, status, or tag labels never prove remote delivery; live configured-remote resolution is mandatory."
  - "Target-tree run-check registries are parsed with a restricted Ripper literal evaluator and are never executed."
  - "Committed SUMMARY records pre-commit locators; final commit/tree identity exists in the external annotated tag."

patterns-established:
  - "Delivery identity: configured remote + full branch ref + deterministic annotated tag + independently resolved PR/check."
  - "Target recomputation: fetch exact refs into a temporary bare store, derive registry-owned inputs, then verify path/mode/content/role and all digest bindings."

requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-only; no schedule, duration, percentage, or completion-date metric
---

# Phase 01 Plan 03: Annotated remote delivery attestation Summary

**A fail-closed delivery protocol now proves a configured remote branch, annotated tag target, target-tree tested subject, evidence/review digests, and external PR check without trusting local Git labels or executing fetched code.**

## Scope result

- **Tasks:** 2/2 plan tasks implemented and verified.
- **Protocol status:** Implemented and fixture-verified; no production Phase 1 push, PR result, annotated tag, or live delivery PASS is claimed.
- **Requirement status:** `REQ-NFR-COMPATIBILITY` is addressed but remains incomplete with all Phase 1 owned-obligation TODOs still open.
- **Browser scope:** Unchanged: desktop Google Chrome 152/151 only; no Edge, Safari, Firefox, provider, tunnel, secret, VM, or unsupported-browser UI path was introduced.

## Accomplishments

- Implemented strict summary locator parsing for configured remote name/URL, full branch ref, deterministic delivery-tag ref, PR locator, and required check name without accepting a final self SHA.
- Implemented exact `git ls-remote` branch/tag/peeled-tag resolution, annotated-object type/name/tagger validation, branch/tag target equality, isolated fixed-ref fetch, commit/tree recomputation, and strict ordered tag payload validation.
- Rebuilt the canonical input union from the peeled target's `scripts/lib/phase-01/run_checks.rb` with a restricted Ripper literal evaluator supporting constants, frozen literals, arrays, and hashes while rejecting dynamic code. The validator never `require`s, `eval`s, or executes target-tree Ruby.
- Recomputed every target-tree subject entry's repository-relative path, file mode, byte SHA-256, and code-owned role; rejected missing/extra/duplicate/out-of-order/illegal inputs and required the immutable five-file Gate D chain.
- Revalidated target-tree evidence-manifest entries, aggregate, GSD goal/code review, and Claude review against the same subject path and two subject digests, then bound the raw evidence-manifest digest to the annotated tag.
- Added production GitHub PR/check lookup through fixed `gh api` argv and an explicitly gated local-fixture state format. PR head, check name/locator/status/conclusion, check actor, tag payload, and target commit must agree.
- Replaced dependency `SUMMARY.md` Remote-SHA regex trust with execution of the live delivery validator and made phase-entry output disclose the live-annotated-attestation dependency mode.
- Synchronized the execution standard, roadmap, and state with the one-commit plus external annotated-tag protocol while preserving TODO-only completion and the Chrome-only Phase 1 scope.

## Task commits

1. **Task 1: Implement live annotated delivery-attestation validation** — deferred to the single final Phase 1 delivery commit.
2. **Task 2: Synchronize the governing delivery and effective-TODO contract** — deferred to the single final Phase 1 delivery commit.

No Git staging, commit, branch creation, stash, checkout, reset, push, tag creation, or real-remote write was performed. Fixture repositories use temporary local bare stores and Git plumbing/fast-import only.

## Files created or modified

- `.planning/tools/validate-delivery-attestation.rb` — configured-remote, annotated-tag, target-tree, evidence/review, and PR/check validator.
- `.planning/tools/test-delivery-attestation.rb` — one positive plus 25 isolated destructive local-bare-remote cases.
- `.planning/tools/planning-validator-support.rb` — dependency delivery validation now invokes the live validator instead of matching narrative SHA labels.
- `.planning/tools/validate-phase-entry.rb` — PASS output identifies dependency count and live annotated-attestation mode.
- `.planning/EXECUTION-STANDARD.md` — canonical one-commit, external-tag, effective-TODO contract.
- `.planning/ROADMAP.md` — authoritative live dependency/exit semantics and Plan 01-03 implementation status.
- `.planning/STATE.md` — evidence-based Plan 01-03 status, next executable Plan 01-05, and no false delivery completion.

## Decisions made

- The only deterministic Phase 1 tag ref is `refs/tags/ycsopen-sms/phase-01/delivery`; missing, lightweight, moved, renamed, malformed, or payload-reordered tags fail closed.
- Remote configuration is read from the repository and must exactly equal the summary locator. Production accepts allowlisted GitHub URL forms only and rejects credential-bearing URLs; local paths require the explicit fixture gate.
- Target-tree registry extraction is data parsing, not code execution. Unsupported/dynamic registry expressions block attestation instead of falling back to manifest self-assertion.
- `subject_manifest_digest` hashes canonical manifest JSON; `tested_subject_digest` hashes canonical input rows; `evidence_manifest_digest` hashes the committed evidence-manifest bytes. All three must match the target records and tag payload.
- A required PR check is independently resolved. Local fixture JSON cannot be used for a GitHub remote, and GitHub API state cannot be replaced by tag labels.

## Deviations from plan

### Auto-fixed issues

**1. [Rule 1 - Bug] Removed a nil process option from the fixture command helper**

- **Found during:** Task 1 GREEN execution.
- **Issue:** The test helper passed `chdir: nil` to Ruby 4 `Open3.capture3`, causing a `TypeError` before the first fixture ran.
- **Fix:** The helper now adds `chdir` only when a concrete path is present.
- **Verification:** All 26 delivery cases execute and return their expected status/diagnostic.
- **Commit:** deferred to the single final Phase 1 delivery commit.

**2. [Rule 2 - Missing critical] Derived the target input set without executing fetched Ruby**

- **Found during:** Task 1 trust-boundary self-review.
- **Issue:** Verifying only paths self-declared by `tested-inputs.json` could not detect a registry input omitted from the manifest; loading target Ruby would create an arbitrary-code-execution boundary before trust was established.
- **Fix:** Added restricted Ripper parsing of literal registry constants and exact registry-versus-manifest path/role reconciliation before digest acceptance.
- **Verification:** The positive fixture uses a constant reference matching the real registry shape; missing, extra, role, content, mode, and illegal-exclusion variants fail independently.
- **Commit:** deferred to the single final Phase 1 delivery commit.

**Total deviations:** 2 auto-fixed (one fixture bug, one missing-critical trust safeguard). Neither changes application behavior, database schema, UI, browser scope, or delivery authority.

## Verification evidence

Passed commands:

- `/usr/bin/env ruby .planning/tools/test-delivery-attestation.rb` — `PASS`, 26 cases: one positive and 25 destructive.
- `/usr/bin/env ruby .planning/tools/test-phase-lifecycle.rb` — `PASS`, 19 lifecycle cases.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — `PASS`, existing entry/UI/schema/dependency regressions.
- `/usr/bin/env ruby .planning/tools/test-repository-verification.rb` — `PASS`, 19 evidence mutations.
- `/usr/bin/env ruby .planning/tools/test-trace-closure.rb --case CASE-FOUND-TRACE-001` — `PASS`, six trace mutations.
- `/usr/bin/env ruby .planning/tools/test-trace-closure.rb --case CASE-FOUND-TRACE-002` — `PASS`, nine reverse-edge mutations.
- `/usr/bin/env ruby .planning/tools/test-bootstrap-phase-01.rb` — `PASS`, exact seven-obligation scope and 25 Gate D mutations.
- `/usr/bin/env ruby .planning/tools/bootstrap-phase-01.rb --phase-dir .planning/phases/01-engineering-verification-foundation --catalog .planning/PRD-OBLIGATIONS.md` — `PHASE_01_BOOTSTRAP PASS`, seven obligations and 12 plans.
- `/usr/bin/env ruby .planning/tools/validate-trace-closure.rb --phase 01 --package engineering-verification-foundation` — real Phase 1 trace `PASS`.
- `/usr/bin/env ruby .planning/tools/validate-phase-lifecycle.rb --phase 01 --package engineering-verification-foundation --stage entry` — real Phase 1 lifecycle entry `PASS`.
- Ruby syntax/warning checks for all four changed Ruby implementation/support files, the governing contradiction scan, and `git diff --check` — `PASS`.

The generic later-phase entry validator was regression-tested in fixtures. Phase 1's real execution authorization remains its bootstrap plus independent `ENTRY-REVIEW.md`; no fixture or local repository state is represented as final remote delivery evidence.

## Security and privacy check

- Standard-library Ruby and existing `git`/`gh` executables only; no package was installed.
- Every child process uses argv arrays. There is no shell interpolation, `eval`, dynamic `require`, credential echo, or target-code execution.
- Remote name, URL class, branch ref, tag ref, repository-relative paths, tag payload fields/order, JSON fields, modes, hashes, PR/check locators, and identities are validated before use.
- Fetched objects live in a fresh temporary bare store and are read with `cat-file`/`ls-tree`; no checkout is created.
- Local fixture mode is accepted only for an actual local configured URL plus an explicit flag and cannot be combined with a GitHub remote.
- Durable fixtures contain no credentials, phone numbers, production data, message bodies, tenant data, private repository content, or developer-specific absolute paths.

## Threat flags

| Flag | File | Description |
| --- | --- | --- |
| threat_flag: remote-ref-validation | `.planning/tools/validate-delivery-attestation.rb` | Reads configured remote refs/objects and rejects spoofed, moved, lightweight, or mismatched targets. |
| threat_flag: external-check-validation | `.planning/tools/validate-delivery-attestation.rb` | Reads GitHub PR/check state through fixed argv and binds external actor/check identity to the tag payload. |
| threat_flag: target-tree-parsing | `.planning/tools/validate-delivery-attestation.rb` | Parses fetched registry literals without executing target code and fails closed on dynamic expressions. |

## Known stubs

None. The absent production annotated tag and live PR/check PASS are explicit Plan 01-12 delivery work, not placeholder behavior in this validator.

## Self-check: PASSED

- Both new executable Ruby files exist with mode `0755`; all plan-owned modified files and this SUMMARY exist.
- The 26-case delivery suite covers positive remote visibility plus missing/lightweight/moved/malformed tag, remote/branch/commit/tree drift, subject set/role/content/mode/digest drift, evidence/review drift, and PR/check/actor drift.
- Lifecycle, planning, evidence, trace, bootstrap, real Phase 1 trace/lifecycle entry, syntax, contradiction, and whitespace checks pass.
- No files outside Plan 01-03 ownership plus this SUMMARY were created or modified by this executor.
- No Git staging, commit, branch, stash, checkout, reset, push, production tag, or real remote write occurred.

## Next plan readiness

Plan 01-05 may proceed from the already completed 01-04 dependency. Plan 01-09 can now depend on this delivery protocol once Plans 01-06 and 01-07 complete. Phase 1 remains incomplete until every scoped TODO is empty and Plan 01-12 performs the one authorized commit/PR/tag/live-attestation sequence.
