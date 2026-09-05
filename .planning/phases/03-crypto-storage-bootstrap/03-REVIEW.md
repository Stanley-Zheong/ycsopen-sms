---
phase: 03-crypto-storage-bootstrap
reviewed: 2026-09-05T23:16:32Z
depth: deep
round: 13
files_reviewed: 12
files_reviewed_list:
  - .planning/phases/03-crypto-storage-bootstrap/03-VERIFICATION.md
  - .planning/phases/03-crypto-storage-bootstrap/CLAUDE-REVIEW.md
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-001.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-002.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-003.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/OBL-CRYPTO-STORAGE-004.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/evidence-manifest.json
  - .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json
  - .planning/phases/03-crypto-storage-bootstrap/ITERATIONS.md
  - .planning/phases/03-crypto-storage-bootstrap/TODO.md
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProductionMigrationCommandServicesFactory.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProductionMigrationCommandServicesFactoryTest.java
status: passed
findings:
  blocker: 0
  high: 0
  warning: 4
  info: 0
  total: 4
---

# Phase 03 Code Review — Round 13

## Result

The Linux-portable corrected candidate is `PASS / no_blockers`. Exact counts are **BLOCKER 0, HIGH 0, WARNING 4, INFO 0**. No new defect was found in the portability delta or its regenerated evidence. The four warnings are unchanged nonblocking delivery-quality findings inherited from Round 12; none was upgraded by this correction.

## Scope and delta

Round 13 reviewed the complete tracked delta relative to the already reviewed commit `9e6240a`, plus the unchanged production path-authority implementation needed to verify that the test correction does not weaken shipped behavior. The sole Java subject change is `ProductionMigrationCommandServicesFactoryTest`: JUnit's Linux `/tmp`-rooted `@TempDir` was replaced by a unique temporary directory below canonical `user.home`, followed by `@AfterEach` recursive cleanup.

- `Files.createTempDirectory(parent, prefix)` provides a distinct path for every per-method test instance, so concurrent test methods do not share a directory or collide.
- `Path.of(System.getProperty("user.home")).toRealPath()` fails test setup when the property is absent, the path is unavailable, or canonicalization fails; it cannot silently select an untrusted fallback.
- Spring's `FileSystemUtils.deleteRecursively(Path)` is null-tolerant if setup fails before assignment. Normal success and exception paths clean the directory. A hard process termination may leave a uniquely named test artifact, but cannot create a false PASS, weaken production validation, or contaminate another test invocation.
- The created path is still passed through the real signed-configuration production composition. No alternate factory, relaxed verifier, production source change, or test-only endpoint entered `core/src/main`.
- Production `assertNoSymlinkComponents` remains intact at `ProductionMigrationCommandServicesFactory.java:633-653`: every ancestor must be a directory owned by the process owner or root and must not be group/world writable. A host with a nonconforming home ancestor fails closed.

The focused factory suite passed all 11 tests with the real production checks. The current subject records the changed test as mode `100644` with SHA-256 `c9dee1111327608ead20cf6318a082d5be2e058e4f046d1546dca38f986664ed`, equal to the reviewed worktree bytes.

## Current-subject evidence binding

- Subject path: `.planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json`
- Inputs: 316 unique entries validated against current bytes and modes.
- Canonical subject-manifest digest: `52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683`
- Serialized subject file SHA-256: `4ab907f8f6897533e3967a657ebaf0b57e3d68f141a54a56489793fea7cb1c68`
- Tested-subject digest: `10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe`
- Evidence-manifest SHA-256: `a9ac4a5b5b1df2a931d39d0a4c356e786418151c094182e8b2682c4e2478ee61`
- Root aggregate: 14/14 PASS, result digest `84d6b662a53902b3efbff3ec761dc7b3c4cf71d13a8386b8b3a81ad107db9be1`
- Root registry digest: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Exact-four evidence: four ordered PASS entries; every recorded obligation file SHA matches its current bytes and the validator reports 4/4 PASS.

All root and obligation records name the same tested-subject digest. The evidence validator independently reconstructed and validated the 316-input subject, so the portability test change is included rather than covered by stale results.

## Warnings

### WR-01: Workflow structure self-test remains textual rather than semantic

**File:** `.planning/tools/test-delivery-attestation.rb:97-160`

The nine workflow cases protect the current job name, job conditional and required command strings, but do not parse step semantics. A future disabled step, `continue-on-error`, or command retained only in a comment could evade that self-test. The current workflow has no such defect and the required commands were inspected in Round 12, so this remains nonblocking.

**Fix:** Parse the workflow YAML into step records, reject disabling/error-suppression fields on required steps, and compare normalized `run` command arrays rather than substrings.

### WR-02: Producer rejects trusted symlinks at the later boundary rather than the initial presence check

**File:** `scripts/lib/phase-03/run_checks.rb:266-293`

The early `File.file?` check follows a symlink, while later subject selection excludes symlinks. A trusted symlink is consequently omitted and then rejected as missing by target-tree reconstruction. Delivery remains fail-closed, but the producer's diagnostic is later and less direct than necessary.

**Fix:** Require `File.file? && !File.symlink?` in the producer's initial trusted-input check and add a producer-side trusted-symlink destructive case.

### WR-03: Phase 3 binding revision tables may start at an arbitrary positive number

**Files:** `.planning/tools/validate-delivery-attestation.rb:1023-1039`, `.planning/tools/validate-phase-lifecycle.rb:414-440`

The validators enforce contiguity from the first positive number but do not require that first number to be one. Digest and status validation still fail closed, but the displayed attempt/revision semantics can be ambiguous.

**Fix:** Require a current-subject binding table to start at one, or rename and schema the column as `Binding revision` with an explicit allowed starting value; retain gap and singleton-start destructive cases.

### WR-04: Closure locator records are intentionally pending the Round 13 rebind

**Files:** `.planning/phases/03-crypto-storage-bootstrap/SUMMARY.md:20-35`, `.planning/phases/03-crypto-storage-bootstrap/TODO.md:27-40`, `.planning/phases/03-crypto-storage-bootstrap/ITERATIONS.md:46`

The regenerated verification and Claude report bind the current Linux-portable subject, while the top-level summary still contains the previous subject/root/review hashes and the TODO correctly leaves the local closure rows open. Iteration I-042 also says canonical replay is pending although that replay now exists. This is a truthful fail-closed workflow state, not evidence acceptance of stale output, but the phase closure documents are not yet sealed.

**Fix:** After this Round 13 report is accepted, refresh the summary and iteration record with the four current digests and new review hashes; close each TODO row only when its cited artifact and pre-push lifecycle validation pass.

## Regression disposition

The Round 1–12 product/security conclusions remain unchanged. The FIELD publication fence still has one production bean; protected-object reservation completion retains its operation-state predecessor guard; snapshot, migration, MOBILE and token publication/retirement paths are unchanged; and the current evidence subject contains the previously reviewed product fixes. No production file changed relative to `9e6240a`.

## Verification performed

- `mvn -f core/pom.xml -Dtest=ProductionMigrationCommandServicesFactoryTest test` — PASS, 11 tests, zero failures/errors/skips.
- `ruby .planning/tools/test-delivery-attestation.rb` — PASS, 106 cases, 102 destructive, 55 Phase 03, nine workflow cases.
- `ruby .planning/tools/test-phase-lifecycle.rb` — PASS, Phase 1 21 cases and Phase 3 ten cases.
- `ruby .planning/tools/test-phase-03-crypto-evidence.rb` — PASS, 59 cases, two positive, four producer targets.
- Current exact-four evidence validation — PASS, four obligations.
- Independent canonical digest, serialized SHA, 316-input count, changed-test entry, obligation checksum, 14-lane aggregate and registry checks — PASS.
- `git diff --check 9e6240a --` — PASS.

The canonical real-service root was not rerun in this review. Its current-subject 14/14 aggregate and child bindings were inspected, and the short validators/tests requested for Round 13 were run independently.

## Delivery binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | 52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683 | 10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe | PASS |

## Final verdict

PASS
