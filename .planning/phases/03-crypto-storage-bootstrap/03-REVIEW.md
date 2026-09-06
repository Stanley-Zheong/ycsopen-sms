---
phase: 03-crypto-storage-bootstrap
reviewed: 2026-09-06T00:17:41Z
depth: deep
round: 15
files_reviewed: 39
files_reviewed_list:
  - .gitignore
  - .github/workflows/ci.yml
  - .planning/debug/phase03-linux-temp-root-ci-failure.md
  - .planning/phases/03-crypto-storage-bootstrap/03-22-SUMMARY.md
  - .planning/phases/03-crypto-storage-bootstrap/03-23-SUMMARY.md
  - .planning/phases/03-crypto-storage-bootstrap/03-30-SUMMARY.md
  - .planning/phases/03-crypto-storage-bootstrap/03-VERIFICATION.md
  - .planning/phases/03-crypto-storage-bootstrap/CLAUDE-REVIEW.md
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
  - core/target/phase03/results/aggregate.json
  - core/target/phase03/results/complete-leak-result.json
  - core/target/phase03/results/lanes/artifact-scanner-fixtures.json
  - core/target/phase03/results/lanes/crypto-evidence-fixtures.json
  - core/target/phase03/results/lanes/default-maven.json
  - core/target/phase03/results/lanes/durable-artifact-leak-scan.json
  - core/target/phase03/results/lanes/fixture-cleanup.json
  - core/target/phase03/results/lanes/flyway-owner-fixtures.json
  - core/target/phase03/results/lanes/flyway-owner-selection.json
  - core/target/phase03/results/lanes/planning-validator-fixtures.json
  - core/target/phase03/results/lanes/production-reachability.json
  - core/target/phase03/results/lanes/protected-inventory-acceptance.json
  - core/target/phase03/results/lanes/protected-inventory-fixtures.json
  - core/target/phase03/results/lanes/real-service-integration.json
  - core/target/phase03/results/lanes/service-contract-fixtures.json
  - core/target/phase03/results/lanes/source-contract-audit.json
  - core/target/phase03/results/phase03-migration-integration.json
  - core/target/phase03/results/phase03-object-storage-integration.json
  - core/target/phase03/results/phase03-pkcs11-fault-integration.json
  - core/target/phase03/results/phase03-protected-persistence-integration.json
  - core/target/phase03/results/protected-inventory-result.json
status: passed
findings:
  blocker: 0
  high: 0
  warning: 3
  info: 0
  total: 3
---

# Phase 03 Code Review — Round 15

## Result

The PR-head/committed-results closure candidate is `PASS / no_blockers`. Exact counts are **BLOCKER 0, HIGH 0, WARNING 3, INFO 0**. No BLOCKER/HIGH was found in the checkout binding, destructive mutation, sanitized result closure, Maven preservation behavior, ignore boundary, regenerated evidence, or finalized local closure records. WR-04 is closed; the three remaining warnings are unchanged nonblocking quality boundaries.

## Checkout binding

The Phase 3 checkout now uses:

```yaml
ref: ${{ github.event_name == 'pull_request' && github.event.pull_request.head.sha || github.sha }}
```

This correctly checks the pull-request head commit rather than GitHub's synthetic merge commit, while all non-PR events retain the event SHA. That makes the required Phase 3 result describe the same feature-head commit that the future delivery tag will target. GitHub's official event documentation explicitly recommends `github.event.pull_request.head.sha` when the workflow must test only the PR head, and `actions/checkout` accepts a commit SHA as `ref`.

The change is scoped only to `phase-03-portable`; the Phase 1 checkout, execute branch, and superseded branch are unchanged. The Phase 3 job remains job-level unconditional, read-only at the workflow permission boundary, and still executes the explicit ripgrep toolchain, Maven suite, destructive contracts, evidence/lifecycle validation, source audit and production-JAR exclusion.

## Checkout mutation

`test-delivery-attestation.rb` adds a dedicated `head-checkout` mutation that replaces `github.event.pull_request.head.sha` with `github.sha`. The known-good workflow is validated first; the mutation then produces exactly `PHASE03_HEAD_CHECKOUT_MISSING`. The complete suite reports 109/109 PASS, including 105 destructive cases and 12 workflow-contract cases. The mutation therefore fails closed when PR-head binding is removed.

## Committed sanitized result closure

The staged delivery set contains exactly 21 JSON files below `core/target/phase03/results`, all mode `100644`, excluding the duplicate runner-local `core/target/phase03/results/tested-inputs.json`:

- one root aggregate;
- all 14 registry lane results;
- one complete leak result;
- one protected-inventory result;
- four obligation child results.

This is the exact transitive closure consumed by `Phase3CryptoEvidence::Validator`: every child validates against its required lane digests, the aggregate validates the complete 14-lane set and registry digest, and inventory/leak references load their concrete result files. Omitting any one of these files makes clean-checkout evidence validation fail. The durable subject is the separately checksum-bound `.planning/.../EVIDENCE/tested-inputs.json`, so committing the duplicate runner copy is unnecessary.

All 21 documents parse as JSON and match their closed schema key sets. A recursive prohibited-key/value scan found no plaintext, phone number, credential, PIN, token, URL, private key, ciphertext body, wrapped DEK, provider response or raw payload. Lane records contain fixed argv plus hashes/timestamps/status only; diagnostic content is represented solely by SHA-256. The canonical evidence validator's own prohibited scan and digest checks also pass.

The subject builder explicitly rejects every path beginning with `core/target/`. Independent reconstruction confirms zero `core/target` entries in the 316-input subject, so the committed results cannot recursively alter the subject they attest. The new `.gitignore` rules first ignore all lane output and then explicitly expose the fixed 14 registry lane filenames. Together with the seven top-level result exceptions, exactly the intended 21 evidence files are trackable; the duplicate `results/tested-inputs.json`, unexpected JSON/non-JSON lane files, packaged JAR, classes and all other build outputs remain ignored. The current index contains exactly those 21 files and no duplicate subject.

## Maven preservation

The 21-file set and its combined byte digest were measured before and after both required Maven invocations:

- `mvn -f core/pom.xml test` — PASS, 365 tests, zero failures/errors, 17 explicitly gated skips; result set remained 21 files with identical combined digest.
- `mvn -f core/pom.xml -DskipTests package` — PASS; result set again remained 21 files with identical combined digest.

Neither command invokes Maven `clean`, removes the committed evidence directory, nor rewrites any result JSON. Thus the workflow's Maven steps do not destroy evidence before validation.

## Current-subject evidence binding

- Inputs: 316 unique entries.
- Canonical subject-manifest digest: `8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2`
- Serialized subject file SHA-256: `04c123056eb6ecf97340dbebf4c52719b00c620b36d5eab63350a35b6cd86759`
- Tested-subject digest: `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a`
- Evidence-manifest SHA-256: `d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906`
- Root aggregate: 14/14 PASS, result digest `6fa4a45c604071b3f5e8118c7334071c281275c02c54dbc729a66e8a6a8fd1b1`
- Root registry digest: `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`
- Exact-four evidence: four ordered PASS entries, each obligation checksum matching current bytes; validator 4/4 PASS.

All aggregate, lane, child, inventory, leak and obligation records bind tested subject `fa490969...`. Goal verification, this Round 15 review, Claude Attempt 12 and the phase summaries bind that same subject/evidence set. The local pre-push lifecycle gate passes with only the live required-check/annotated-tag item reserved.

## Warnings

### WR-01: Workflow structure self-test remains textual rather than semantic

**File:** `.planning/tools/test-delivery-attestation.rb:97-169`

The 12 workflow cases protect the current checkout tokens and required commands but inspect normalized text instead of parsed step semantics. A future disabled step, error suppression, or token retained only in a comment could evade the self-test. The current workflow has none of those defects and its checkout expression/step structure was directly inspected, so this remains nonblocking.

**Fix:** Parse workflow YAML into job/step records, reject disabling or error-suppression fields on required steps, and validate the checkout `with.ref` expression structurally.

### WR-02: Producer rejects trusted symlinks at the later boundary rather than the initial presence check

**File:** `scripts/lib/phase-03/run_checks.rb:266-293`

The early `File.file?` check follows a symlink, while later subject selection excludes symlinks. Target-tree reconstruction consequently rejects the omitted trusted input as missing, so delivery remains fail-closed, but the producer diagnostic is later than necessary.

**Fix:** Require `File.file? && !File.symlink?` in the initial trusted-input check and add a producer-side trusted-symlink mutation.

### WR-03: Phase 3 binding revision tables may start at an arbitrary positive number

**Files:** `.planning/tools/validate-delivery-attestation.rb:1023-1039`, `.planning/tools/validate-phase-lifecycle.rb:414-440`

The validators enforce contiguity from the first positive number but do not require the first number to be one. Digest/status validation remains fail-closed, but attempt/revision semantics can be ambiguous.

**Fix:** Require current-subject binding tables to start at one, or schema the field explicitly as `Binding revision`; retain singleton-start and gap destructive cases.

## Regression disposition

No `core/src/main` or Java test file changed relative to `fb5f30b`. The Round 1–14 product/security conclusions therefore remain unchanged: publication fences, snapshot recovery, configuration authenticity, object reservation state transitions, MOBILE/token races and production composition have no implementation delta. The committed files are sanitized evidence records, not executable application resources, and are outside the packaged JAR.

## Verification performed

- `ruby .planning/tools/test-delivery-attestation.rb` — PASS, 109 cases, 105 destructive, 55 Phase 03, 12 workflow cases.
- Direct head-checkout mutation — rejected with `PHASE03_HEAD_CHECKOUT_MISSING`.
- `ruby .planning/tools/test-phase-lifecycle.rb` — PASS, Phase 1 21 cases and Phase 3 ten cases.
- `ruby .planning/tools/test-phase-03-crypto-evidence.rb` — PASS, 59 cases, two positive, four producer targets.
- Current exact-four/root evidence validation — PASS, four obligations and complete 14-lane root.
- Maven test/package preservation comparison — PASS; 21/21 identical before and after each command.
- Recursive 21-file prohibited-key/value scan — PASS, zero findings.
- Subject recursion check — PASS, zero `core/target` subject entries.
- Ignore-boundary check — PASS; exactly 21 intended result files are visible, while unexpected JSON/non-JSON lane files, duplicate subject and other build outputs are ignored.
- Local closure check — PASS; goal verification, Claude Attempt 12, summaries and TODO bind the current subject, and pre-push lifecycle validation accepts only the reserved external check/tag row.
- `git diff --check` and staged diff check — PASS.

The long real-service root was not rerun in this review. Its current-subject 14/14 aggregate, lane and child records were fully parsed and revalidated; requested short destructive suites and Maven preservation checks were run independently.

## Delivery binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | 8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2 | fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a | PASS |

## Final verdict

PASS
