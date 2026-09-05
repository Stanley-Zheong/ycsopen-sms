---
phase: 03-crypto-storage-bootstrap
reviewed: 2026-09-06T06:35:03+08:00
depth: deep
round: 12
files_reviewed: 66
status: passed
findings:
  blocker: 0
  high: 0
  warning: 4
  info: 0
  total: 4
---

# Phase 03 Code Review — Round 12

## Result

The corrected Phase 03 candidate is `passed / no_blockers`. Exact counts are BLOCKER 0, HIGH 0, WARNING 4, INFO 0. Round 11's delivery-subject BLOCKER and required-check HIGH are closed. The remaining warnings do not invalidate the product implementation, tested subject, exact-four evidence, or required-check commands, but should be cleaned up before the final delivery records are sealed.

## Scope

The review covered the 66 changed implementation, test, configuration, workflow, documentation and validator files in the final candidate. It rechecked the full Phase 03 product diff and performed a deeper incremental review of the Round 11 corrections in:

- `.github/workflows/ci.yml`
- `.planning/tools/test-delivery-attestation.rb`
- `.planning/tools/test-phase-03-crypto-evidence.rb`
- `.planning/tools/test-phase-lifecycle.rb`
- `.planning/tools/validate-delivery-attestation.rb`
- `.planning/tools/validate-phase-lifecycle.rb`
- `scripts/lib/phase-03/run_checks.rb`
- `scripts/lib/phase-03/test_run_checks.rb`

## Round 11 closure

- The producer and target-tree validator declare the same exact, duplicate-free 15-file trusted-input set. Every current trusted entry exists in the 316-entry tested subject with its actual mode and SHA-256, and rebuilding the subject from the current tree produces byte-equivalent JSON.
- Delivery validation reconstructs the complete Phase 03 path/role set from the fetched target commit, checks the target producer's literal trusted set against the validator set, and validates every subject content digest and Git mode.
- All 15 trusted inputs have independent target-tree missing, content-drift and mode-drift cases: 45/45 rejected. The full delivery suite passed 106 cases, including 102 destructive cases, 55 Phase 03 cases and nine workflow-contract cases.
- The actual `Phase 03 portable registry` job is unconditional and executes both delivery/lifecycle destructive suites, current exact-four evidence validation and the complete pre-push lifecycle command. The display name exactly matches the delivery summary.
- Phase 1 remains compatible: 21 lifecycle cases passed, the legacy delivery cases passed, and the current workflow's execute/supersede conditions use the exact same three legacy paths with complementary `!= ''` and `== ''` branches.
- All four owned TODO rows now cite their exact `EVIDENCE/OBL-CRYPTO-STORAGE-00N.json` path; omission is rejected by a dedicated lifecycle mutation.
- Round 10's product fixes remain present: the FIELD publication fence has one production bean, and protected-object FIELD reservation release remains limited to `OBJECT_STORED`, `RECONCILE_DELETE` or `COMPLETED`.

## Warnings

### WR-01: Workflow structure test is textual rather than semantic

**File:** `.planning/tools/test-delivery-attestation.rb:97-160`

The nine workflow cases correctly protect the current job name, job-level conditional, required command strings and Phase 1 condition sets. The parser does not parse step semantics, however, so a future step-level `if: false`, `continue-on-error: true`, or a command retained only in a comment can evade this self-test. The current workflow has none of these defects and its commands were independently inspected, so this is not a current HIGH finding.

**Fix:** Parse the workflow YAML into step records and require the named destructive/evidence steps to have no disabling condition or error suppression; validate exact normalized `run` command arrays instead of substring presence.

### WR-02: Trusted symlinks are rejected only at the later delivery boundary

**File:** `scripts/lib/phase-03/run_checks.rb:266-293`

`missing_trusted` uses `File.file?`, which follows a symlink, while the later subject selection excludes symlinks. A trusted path replaced by a symlink can therefore be omitted from a locally generated subject instead of failing immediately. The remote target-tree validator still rejects it because a Git symlink is not an accepted regular-blob mode and the trusted path is then missing, so the delivery chain remains fail-closed.

**Fix:** Make the producer's initial trusted-input check require `File.file? && !File.symlink?`, and add one producer-side trusted-symlink destructive case.

### WR-03: Phase 3 binding revisions may start at an arbitrary positive number

**Files:** `.planning/tools/validate-delivery-attestation.rb:1023-1039`, `.planning/tools/validate-phase-lifecycle.rb:414-440`

Both validators require contiguous Phase 3 rows but derive the expected sequence from the first supplied positive number. This preserves digest/status fail-close behavior and rejects gaps, but it allows a singleton table to begin at any positive number and makes `Attempt` ambiguous between historical review attempt and current-subject binding revision.

**Fix:** Require current-subject binding tables to start at 1, or rename and schema the field as `Binding revision` with an explicit allowed starting value. Add singleton-start and gap mutations to both validator suites.

### WR-04: Top-level closure hashes still describe the pre-replay reports

**Files:** `.planning/phases/03-crypto-storage-bootstrap/SUMMARY.md:30-32`, `.planning/phases/03-crypto-storage-bootstrap/03-22-SUMMARY.md:38-44`

The subject, subject-file, tested-subject, root and evidence digests are current. The three review-file hashes and some final-review prose still describe reports created before the corrected-subject replay. `TODO.md` now accurately leaves those review and scoped-query rows open, and the real pre-push validator correctly blocks until all three current-subject bindings exist, so this is a truthful pending workflow state rather than a fail-open.

**Fix:** After the GSD goal and Claude replays bind subject `78ab379f...`, refresh the review SHA-256 values and final-review prose, then close the corresponding TODO rows only after pre-push lifecycle validation succeeds.

## Verification performed

- `ruby .planning/tools/test-delivery-attestation.rb` — PASS, 106 cases / 102 destructive / 55 Phase 03 / nine workflow cases.
- `ruby .planning/tools/test-phase-lifecycle.rb` — PASS, Phase 1 21 cases and Phase 3 ten cases.
- `ruby scripts/lib/phase-03/test_run_checks.rb` — PASS, 68 assertions / 14 checks.
- `ruby .planning/tools/test-phase-03-crypto-evidence.rb` — PASS, 59 cases / two positive / four producer targets.
- Current exact-four evidence validation — PASS, four obligations.
- Independent producer rebuild, trusted-set equality and all 15 content/mode checks — PASS.
- Parsed actual CI job name, unconditional execution and required command presence — PASS.
- `CryptoStorageConfigurationWiringTest,ProtectedObjectMetadataRepositoryJdbcTest` — three tests passed, no failures/errors/skips.
- `mvn -f core/pom.xml -DskipTests package` — PASS.
- `git diff --check` — PASS.
- Canonical root/evidence supplied to this review — root 14/14 PASS and evidence 4/4 PASS; long real-service lanes were not repeated.

## Delivery binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | e04d225b74fa9c2a18ef9316987278bd370c70fa99b8cb0feeeb305a8e3e2eb1 | 78ab379f0d1e55c34740a6e13962dd558579558f45441ed0d757d5b4a1eb1c7f | PASS |

## Final verdict

PASS
