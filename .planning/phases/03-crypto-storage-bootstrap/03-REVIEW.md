---
phase: 03-crypto-storage-bootstrap
reviewed: 2026-09-05T23:31:00Z
depth: deep
round: 14
files_reviewed: 16
files_reviewed_list:
  - .github/workflows/ci.yml
  - .planning/debug/phase03-linux-temp-root-ci-failure.md
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-001.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-002.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-003.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-004.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json
  - .planning/phases/03-crypto-storage-bootstrap/ITERATIONS.md
  - .planning/phases/03-crypto-storage-bootstrap/SUMMARY.md
  - .planning/phases/03-crypto-storage-bootstrap/TODO.md
  - .planning/tools/test-delivery-attestation.rb
  - .planning/tools/validate-delivery-attestation.rb
  - .planning/tools/validate-phase-lifecycle.rb
  - core/target/phase03/results/aggregate.json
  - scripts/lib/phase-03/run_checks.rb
status: passed
findings:
  blocker: 0
  high: 0
  warning: 4
  info: 0
  total: 4
---

# Phase 03 Code Review — Round 14

## Result

The explicit-ripgrep corrected candidate is `PASS / no_blockers`. Exact counts are **BLOCKER 0, HIGH 0, WARNING 4, INFO 0**. No new defect was found in the CI toolchain delta, its two destructive workflow mutations, or the regenerated evidence. The four warnings are unchanged nonblocking delivery-quality findings carried from Round 13; none was upgraded by this correction.

## Scope and delta

Round 14 reviewed the complete tracked delta relative to the already reviewed commit `762a36a` and rechecked the unchanged warning/evidence boundaries needed to assess the current subject. The only executable changes are `.github/workflows/ci.yml` and `.planning/tools/test-delivery-attestation.rb`; `core/src/main` and the Round 13 migration-factory portability test are unchanged.

The workflow change is correctly scoped and sufficient for GitHub Actions run `33998572392`'s `Errno::ENOENT`:

- The install step occurs only inside the unconditional `phase-03-portable` job at `.github/workflows/ci.yml:75-79`. Phase 1 is not made dependent on ripgrep.
- It performs an index refresh, installs only `ripgrep` with `--no-install-recommends`, and immediately runs `rg --version`. It does not add a browser, service image, product package, global upgrade, or runtime credential.
- Package resolution/network failure, installation failure, or a missing/non-runnable `rg` makes the required job fail before the contract suite. There is no silent fallback or skipped canary.
- `test-planning-validators.rb` still invokes the actual `rg` process for the alternation canary. The local replay passed with a real ripgrep binary, confirming that explicit provisioning addresses the missing executable rather than weakening the test.

The required job remains job-level unconditional. Its delivery/lifecycle suites, exact-four evidence validator, pre-push gate, source reachability audit and production-JAR exclusion steps remain present. The Phase 1 execute/supersede branches retain complementary conditions over the exact same three legacy files.

## Destructive workflow coverage

`phase03_workflow_errors` now requires both normalized tokens independently:

- Removing `apt-get install --yes --no-install-recommends ripgrep` yields exactly `PHASE03_RIPGREP_INSTALL_MISSING`.
- Removing `rg --version` yields exactly `PHASE03_RIPGREP_PROBE_MISSING`.

The known-good workflow is checked before mutations, and each mutation changes only its target string. Therefore neither case can pass because of a pre-existing unrelated workflow error. The complete suite reports 108/108 PASS, including 104 destructive cases and 11 workflow-contract cases.

## Current-subject evidence binding

- Subject path: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json`
- Inputs: 316.
- Canonical subject-manifest digest: `ef4002d2d3a4518c38a52ba97a3e6f482441e8a54bdea82eb5dd082c1bc81bac`
- Serialized subject file SHA-256: `4a4cc5e45890fe5dccbe0a24b166b5b8f447f0b22f670161aca7eb783129c58c`
- Tested-subject digest: `acdbdba8db25d3936cb9eb99310c1ee2e14f77f430574109d6c239a6906823c0`
- Evidence-manifest SHA-256: `e6ce5f0998ccc4ef3e758e6fd92fa3487555bdea6cbe855103c55ccb3769bb01`
- Root aggregate: 14/14 PASS, result digest `9ddf7fa7de8cb49a6110f10cb309bd1bdbdeb7244074bf38cc240f9cdc5a9e3a`
- Root registry digest: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Exact-four evidence: four ordered PASS entries; every recorded obligation file SHA matches its current bytes and validation reports 4/4 PASS.

The subject records `.github/workflows/ci.yml` as mode `100644` with SHA-256 `8366fd945e38bdfe3ed7de73589b2db3df73e344c381921c57cca2a9e29d14b6`, and `.planning/tools/test-delivery-attestation.rb` as mode `100755` with SHA-256 `41b1bb820e8101441df8183d622b7468a40c22ce5ab2f41568efadd2366f39d2`; both match the reviewed worktree. All root and obligation records bind tested subject `acdbdba8...`.

## Warnings

### WR-01: Workflow structure self-test remains textual rather than semantic

**File:** `.planning/tools/test-delivery-attestation.rb:97-166`

The 11 workflow cases protect the current job name, job conditional, required command strings and both ripgrep tokens, but still inspect normalized text instead of parsed step semantics. A future disabled step, `continue-on-error`, or required command retained only in a comment could evade this self-test. The current workflow has none of those defects and its step structure was directly inspected, so this remains nonblocking.

**Fix:** Parse the workflow YAML into step records, reject disabling/error-suppression fields on required steps, and compare normalized `run` command arrays rather than substrings.

### WR-02: Producer rejects trusted symlinks at the later boundary rather than the initial presence check

**File:** `scripts/lib/phase-03/run_checks.rb:266-293`

The early `File.file?` check follows a symlink, while later subject selection excludes symlinks. A trusted symlink is omitted and then rejected as missing by target-tree reconstruction. Delivery remains fail-closed, but the producer diagnostic is later and less direct than necessary.

**Fix:** Require `File.file? && !File.symlink?` in the producer's initial trusted-input check and add a producer-side trusted-symlink destructive case.

### WR-03: Phase 3 binding revision tables may start at an arbitrary positive number

**Files:** `.planning/tools/validate-delivery-attestation.rb:1023-1039`, `.planning/tools/validate-phase-lifecycle.rb:414-440`

The validators enforce contiguity from the first positive number but do not require that first number to be one. Digest and status validation still fail closed, but the displayed attempt/revision semantics can be ambiguous.

**Fix:** Require a current-subject binding table to start at one, or rename and schema the column as `Binding revision` with an explicit allowed starting value; retain gap and singleton-start destructive cases.

### WR-04: Closure locator records are intentionally pending the Round 14 rebind

**Files:** `.planning/phases/03-crypto-storage-bootstrap/SUMMARY.md:20-35`, `.planning/phases/03-crypto-storage-bootstrap/TODO.md:27-40`, `.planning/phases/03-crypto-storage-bootstrap/ITERATIONS.md:47`

The evidence set now binds the explicit-toolchain subject, while the top-level summary and the prior verification/review records still describe the Round 13 subject. The TODO correctly reopens the subject-bound closure rows, and iteration I-044 explicitly says rebind/review/replay are required. This is a truthful fail-closed workflow state rather than acceptance of stale evidence, but closure is not yet sealed.

**Fix:** After the GSD/Claude/goal replays bind subject `acdbdba8...`, refresh summary/review hashes and close each TODO row only when its cited artifact and pre-push lifecycle validation pass.

## Regression disposition

The Round 1–13 product/security conclusions remain unchanged. No `core/src/main` or Java test file differs from `762a36a`; the FIELD/object/snapshot/migration/MOBILE/token publication and retirement behavior therefore has no implementation delta. The added package is CI-only and cannot enter the application artifact.

## Verification performed

- `ruby .planning/tools/test-delivery-attestation.rb` — PASS, 108 cases, 104 destructive, 55 Phase 03, 11 workflow cases.
- Direct install mutation — rejected with `PHASE03_RIPGREP_INSTALL_MISSING`.
- Direct probe mutation — rejected with `PHASE03_RIPGREP_PROBE_MISSING`.
- `ruby .planning/tools/test-phase-lifecycle.rb` — PASS, Phase 1 21 cases and Phase 3 ten cases.
- `ruby .planning/tools/test-phase-03-crypto-evidence.rb` — PASS, 59 cases, two positive, four producer targets.
- Current exact-four evidence validation — PASS, four obligations.
- `ruby .planning/tools/test-planning-validators.rb` with real `rg` — PASS, including `rg_invocation_scope` and `rg_alternation_canary`.
- Workflow YAML parse and direct job/occurrence inspection — PASS; install/probe appear only in Phase 3.
- Independent subject digests, serialized SHA, two changed-input entries, obligation checksums, 14-lane aggregate and registry checks — PASS.
- `git diff --check 762a36a --` — PASS.

The canonical real-service root was not rerun in this review. Its current-subject 14/14 aggregate and child/evidence bindings were inspected; the short destructive suites requested for Round 14 were run independently.

## Delivery binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | ef4002d2d3a4518c38a52ba97a3e6f482441e8a54bdea82eb5dd082c1bc81bac | acdbdba8db25d3936cb9eb99310c1ee2e14f77f430574109d6c239a6906823c0 | PASS |

## Final verdict

PASS
