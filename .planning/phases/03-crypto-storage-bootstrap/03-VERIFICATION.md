---
phase: 03-crypto-storage-bootstrap
verified: 2026-09-06T00:13:38Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
subject_manifest_path: .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json
subject_manifest_digest: 8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2
subject_manifest_file_sha256: 04c123056eb6ecf97340dbebf4c52719b00c620b36d5eab63350a35b6cd86759
tested_subject_digest: fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a
evidence_manifest_sha256: d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906
plan_deviations:
  - id: DR-P03-012
    status: accepted
    scope: execution-shape-only
    reason: Fixed root real-service and production-reachability lanes replace the absent monolithic Phase03FullFaultIntegrationTest without waiving behavior.
---

# Phase 03: Crypto Storage Bootstrap — PR-Head and Committed-Results Closure Verification

**Phase Goal:** Protected database/object fields and logs contain no prohibited plaintext in executable samples; rotation and rollback preserve data without persisted master keys; existing plaintext migrates through a verified, resumable and auditable path.
**Verified:** 2026-09-06T00:13:38Z
**Status:** passed
**Mode:** Fresh corrected-subject goal-backward verification. SUMMARY files were not accepted as implementation evidence.

## Verification Basis

The ROADMAP success criteria, `REQ-NFR-DATA-PROTECTION`, Phase 03 SPEC behavior contracts and the authoritative owner query reduce to four atomic product must-haves: OBL-CRYPTO-STORAGE-001 through 004. Verification worked backward from those outcomes into production artifacts, transaction/key wiring, focused and real-service tests, and the current evidence chain.

The inspected 316-input candidate is bound by canonical subject-manifest digest `8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2` and tested-subject digest `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a`. The evidence manifest file SHA-256 is `d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906`.

## Goal Achievement

### Observable Truths

| # | Must-have | Status | Code, wiring and executable evidence |
| --- | --- | --- | --- |
| 1 | **OBL-CRYPTO-STORAGE-001:** every current executable/migratable protected database value crosses the context-bound envelope/index boundary, and accepted database/log samples contain no prohibited plaintext. | ✓ VERIFIED | The accepted 17-field inventory drives current readers/writers, migration and leak checks. Message, blacklist and tenant-registration writers use protected codecs plus FIELD/MOBILE publication fences. Real protected-persistence child result `a1804394...`, inventory result `5ddab2ee...` and complete leak result `320287b4...` are bound into current PASS evidence `d8150937...`. |
| 2 | **OBL-CRYPTO-STORAGE-002:** protected objects remain private ciphertext and are returned only after application authorization/capability checks; staged registration objects retain purpose/session/claim/reconciliation semantics without raw URLs. | ✓ VERIFIED | The conditional production object graph composes the private S3 adapter, protected codec, JDBC metadata, deny-by-default authorization, capability service and registration sessions. Object creation reserves the exact FIELD version; deletion releases it only from valid terminal predecessor states. Real object child result `f9e5c820...` plus inventory/leak results are bound into current PASS evidence `f4046d27...`. |
| 3 | **OBL-CRYPTO-STORAGE-003:** keys remain opaque and purpose-separated; activation, rewrap, restart, rollback and publication-versus-retirement races preserve readability while exact live references block retirement. | ✓ VERIFIED | Production SunPKCS11 composition resolves database-owned versions. FIELD, MOBILE, object-capability, registration-upload and SNAPSHOT purposes share ordered purpose-lock protocols but retain distinct aliases/domains/inventories. Retirement performs its final inventory inside the guarded purpose transaction; retained snapshots and object reservations remain reference sources. Real PKCS11/fault child result `d11c992d...` is bound into PASS evidence `806dd787...`; no raw root-key material appears in evidence or leak results. |
| 4 | **OBL-CRYPTO-STORAGE-004:** signed preflight and legacy migration are fail-closed, idempotent, resumable and auditable, with bounded authenticated encrypted snapshot restore into a fresh schema and no plaintext dump file. | ✓ VERIFIED | The shipped ServiceLoader factory authenticates canonical Ed25519-signed production configuration from an independently pinned public key, verifies the configured snapshot root, then composes JDBC/HSM/migration and snapshot create/restore/delete operations. Restore authenticates the retained manifest and every chunk before MySQL, reauthenticates afterward, re-dumps the target and compares length/SHA-256 before completion. Fixed MySQL clients enforce fixed argv/environment and root-owned trusted executable paths with owner/mode/ACL/inode/size/signed-digest checks. Real migration child result `aa505308...` is bound into PASS evidence `5508e090...`. |

**Score:** 4/4 must-haves verified

### ROADMAP Success Criteria

| Roadmap contract | Status | Closure |
| --- | --- | --- |
| Protected database/object fields and logs contain no prohibited plaintext in executable samples. | ✓ VERIFIED | OBL-001/002, accepted inventory and complete multi-surface leak result |
| Key rotation and rollback preserve data and never persist master keys. | ✓ VERIFIED | OBL-003, real MySQL/SoftHSM rotation/recovery and exact reference-gated retirement |
| Existing plaintext migrates through a verified, resumable, auditable path. | ✓ VERIFIED | OBL-004, signed admission, checkpoints and real encrypted fresh-schema restore |

## Artifact and Production-Wiring Verification

| Artifact/link | Required behavior | Status | Evidence |
| --- | --- | --- | --- |
| Protected inventory + V1200/V1201 | Closed target set and Phase-owned version/reference/reservation schema without V1 mutation | ✓ VERIFIED | Accepted inventory digest `9d31954a...`; owner/trace query selects exactly four obligations; inventory/evidence validators reject unresolved targets. |
| `CryptoStorageConfiguration` → lifecycle/fences | One fail-closed production crypto graph using database-owned key state | ✓ WIRED | Exactly one `FieldReferencePublicationFence` bean; lifecycle consumes FIELD and snapshot sources; production reachability lane PASS `98b29a89...`. |
| Current persistence/object writers → purpose locks | No stale FIELD/MOBILE/token reference can become durable beside activation/retirement | ✓ WIRED | Publication locks the complete purpose set before insert; lifecycle takes the same lock before final inventory/transition; deterministic race tests and real-service lanes cover both sides. |
| Retained snapshot store → SNAPSHOT retirement | Retained/incomplete/corrupt/unavailable snapshot state cannot be treated as zero references | ✓ WIRED | `EnvelopeReferenceInventory` enumerates exact manifests/chunks and fails closed; snapshot service publishes manifest only after complete encrypted chunks. |
| `ProtectedDataMigrationLauncher` → production factory | Shipped command reaches signed config, JDBC/HSM, fixed process authority and real snapshot operations | ✓ WIRED | ServiceLoader requires one provider; common production compose path owns resources and closes partial acquisition; test bridge/Docker adapter are absent from the production JAR. |
| Fixed 14-lane root → exact-four producer | Only current same-subject successful real/deterministic/leak/cleanup results can emit obligation PASS | ✓ WIRED | Aggregate PASS, all 14 lane digests present, exact-four validator PASS, destructive evidence suite 59 cases PASS. |

## Round 11 Delivery Trust-Chain Closure

Round 11 identified a delivery-subject trust gap rather than a product-crypto defect. The corrected implementation was verified at four levels:

1. **Closed trusted set:** `scripts/lib/phase-03/run_checks.rb`, the target-tree validator and the destructive fixture each declare the same duplicate-free 15-file trusted-input set. Every trusted entry exists in the 316-entry current subject with its actual Git-compatible mode, SHA-256 and code-owned role.
2. **Immutable reconstruction:** delivery validation fetches the target commit into an isolated bare object store, reads files with `git cat-file`, enumerates with `git ls-tree`, parses the target producer's literal trusted set and compares the complete path/role/content/mode set. It does not substitute mutable working-tree inputs for tag-target evidence.
3. **Destructive proof:** `test-delivery-attestation.rb` passed 109 cases, including 105 destructive cases and 12 workflow cases. The 15 trusted paths each have missing/content/mode mutations (45/45 rejected). Lifecycle tests passed Phase 03 10/10; root-registry tests passed 68 assertions across 14 checks.
4. **Required check:** `.github/workflows/ci.yml` defines the unconditional `Phase 03 portable registry` job. It checks out the pull-request head SHA (falling back to `github.sha` outside pull requests), explicitly installs and probes `ripgrep`, then runs backend/default checks, Flyway ownership, destructive validators, current exact-four/lifecycle validation, source reachability and a packaged-JAR rejection of test-only bridges.

The current producer still follows a trusted symlink during its early `File.file?` presence check before later excluding symlinks. This is nonblocking for delivery integrity because target-tree reconstruction rejects Git mode `120000` and reports the trusted blob missing; the current canonical subject contains regular files with the expected modes and hashes.

## Linux-Portability Baseline Verification

The Linux-portable baseline immediately preceding the current CI-toolchain delta changed only `ProductionMigrationCommandServicesFactoryTest`:

- JUnit `@TempDir`, which resolves below Linux `/tmp`, was replaced by `Files.createTempDirectory(Path.of(System.getProperty("user.home")).toRealPath(), ...)` plus deterministic recursive cleanup.
- The test still canonicalizes the created directory before writing signed configuration fixtures. It changes only where test fixtures live; it does not provide an alternate production factory path or weaken signature, ownership, permission, no-symlink or stable-read checks.
- No `core/src/main` file changed in this portability correction. Production `assertNoSymlinkComponents` still requires every ancestor to be a directory owned by the process owner or root and rejects group/other-writable ancestors. File metadata remains owner-read, non-group/other-writable, regular, non-symlink and stable across reads.
- The portable test hash remains included in the current 316-input subject, which passed the canonical 14/14 root on Linux, including default Maven, production reachability and the real MySQL/MinIO/SoftHSM lane.

Therefore the portability change fixes an invalid Linux test-fixture assumption while preserving the production writable-ancestor rejection policy.

## PR-Head and Committed-Results Delta Verification

Relative to the Round 14 subject, production and Java test behavior is unchanged: there is no `core/src/main` or `core/src/test` worktree diff. The 316-input subject changes only the Phase 03 workflow and its delivery-contract test:

- `.github/workflows/ci.yml` supplies `actions/checkout@v4` with the pull-request head SHA, falling back to `github.sha` for non-PR events. The required check therefore validates the proposed head rather than GitHub's synthetic pull-request merge ref.
- `.planning/tools/test-delivery-attestation.rb` requires both checkout expressions, and its new destructive mutation proves that replacing the head expression with `github.sha` is rejected.
- Sanitized `core/target/phase03/results` are committed validator inputs for clean-checkout replay but are intentionally excluded by the subject builder; their presence changes neither the 316-input subject nor the product implementation.

The current subject passed the 14/14 root, exact-four 4/4 validation, 59 evidence fixtures, delivery 109/105 with 12 workflow cases, and Phase 1/Phase 3 lifecycle suites 21/10. The closure therefore preserves all four product obligations while binding CI execution to the PR head and making clean-checkout result inputs available.

## Evidence Integrity

| Evidence | Current binding | Status |
| --- | --- | --- |
| Subject manifest | canonical digest `8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2`; serialized file SHA `04c123056eb6ecf97340dbebf4c52719b00c620b36d5eab63350a35b6cd86759` | ✓ VERIFIED |
| Tested subject | `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a` | ✓ VERIFIED |
| Evidence manifest | file SHA `d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906`; exact four ordered PASS entries | ✓ VERIFIED |
| Root aggregate | result `6fa4a45c604071b3f5e8118c7334071c281275c02c54dbc729a66e8a6a8fd1b1`; registry `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`; 14/14 lanes PASS | ✓ VERIFIED |
| OBL-001 | evidence digest `d8150937efce8af258eb5eeb6486fb742539fe6add9a23511ee4da5a52b3e5fe`; file SHA `86e32e172e201aac329ae791fc58ad3c463b2e44dab322fee749659c1fc8c660` | ✓ PASS |
| OBL-002 | evidence digest `f4046d271b81db2cba930aafd4aa3543cd4669ce703546433a9059790d144ee9`; file SHA `8221ee663db643fa7b267b8b71b4f3ea9915013c1afc63723100977fd4a66bda` | ✓ PASS |
| OBL-003 | evidence digest `806dd787a932cb92bb34c45b58c27153e51aac76f40175a81921c1659fd72307`; file SHA `5f91c6d6c799cf12ce058e10880410e02015a4f40863b9fc32336d28e318bf8f` | ✓ PASS |
| OBL-004 | evidence digest `5508e09046b333b26684c05e36a436785ca3fdaf5953932ddfdafc26239c4b89`; file SHA `c3bfb0bdda1fdc9c5e6169df2c7e97aa8fe0eb3d81fbd981166f36c584570c2e` | ✓ PASS |
| Inventory/leak | accepted inventory `9d31954a...`; inventory result `5ddab2ee...`; complete leak result `320287b4...` | ✓ PASS |

## Behavioral Spot-Checks

| Check | Fresh result | Status |
| --- | --- | --- |
| Delivery trust-chain destructive suite | `DELIVERY_ATTESTATION_TEST PASS cases=109 destructive=105 workflow=12` | ✓ PASS |
| Phase lifecycle destructive suite | Phase 1 21 cases PASS; Phase 3 10 cases, 9 negative/1 positive PASS | ✓ PASS |
| Fixed root-registry self-test | `phase03_run_checks_tests=PASS cases=68 checks=14` | ✓ PASS |
| Exact-four producer/validator destructive suite | 59 cases, two positive, four producer targets PASS | ✓ PASS |
| Current exact-four validation | four obligations / four PASS targets | ✓ PASS |
| Owner/trace validation | 522 records, 108/108 requirements, selected=4, no duplicate/unknown/orphan error | ✓ PASS |
| Fixture cleanup and diff check | cleanup `all` PASS; `git diff --check` exit 0 | ✓ PASS |
| Production JAR exclusion | no test bridge, Docker snapshot process, test class or Testcontainers match | ✓ PASS |

The canonical real-service root was not rerun during this verifier pass. Its same-subject 14/14 result envelopes were inspected and revalidated, as requested.

## Requirements Coverage

| Requirement | Status | Evidence |
| --- | --- | --- |
| `REQ-NFR-DATA-PROTECTION` | ✓ SATISFIED | All three ROADMAP criteria and four atomic storage/key obligations verified |
| `OBL-CRYPTO-STORAGE-001` | ✓ SATISFIED | Protected persistence, accepted inventory and leak-free real result |
| `OBL-CRYPTO-STORAGE-002` | ✓ SATISFIED | Production object graph, private ciphertext and authorized lifecycle result |
| `OBL-CRYPTO-STORAGE-003` | ✓ SATISFIED | Opaque purpose-separated PKCS11 keys, rotation/races and reference-gated retirement |
| `OBL-CRYPTO-STORAGE-004` | ✓ SATISFIED | Signed admission, resumable migration and authenticated encrypted recovery |

No additional requirement mapped to Phase 03 is orphaned.

## Independent Review Disposition

- GSD code review Round 15: PASS, BLOCKER 0, HIGH 0, WARNING 3, INFO 0. The three inherited warnings remain nonblocking delivery-quality boundaries; review of the current PR-head and committed-results closure subject found no new defect.
- Claude Attempt 12: PASS, BLOCKER 0, HIGH 0, WARNING 3, INFO 3 (session `ac52aa94-c613-4702-83a2-ed207bb75a41`). It independently confirmed the PR-head trust binding, destructive checkout mutation and complete sanitized 21-result clean-checkout closure without a product, delivery or security blocker/high finding.
- Both reviews bind the current canonical subject-manifest digest `8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2`, tested-subject digest `fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a`, and evidence manifest SHA-256 `d997cc30660ec0da233500fd8b6dd5edf6b2c3fbe55804c4aa3d2ddd9ca03906`.
- Independent code/evidence inspection confirms the subject delta is limited to `.github/workflows/ci.yml` and `.planning/tools/test-delivery-attestation.rb`; production code and the Linux test fixture are unchanged.
- The previously corrected product invariants remain directly present in current code: the FIELD fence has one production Spring bean, and `markDeleted` releases a FIELD reservation only from `OBJECT_STORED`, `RECONCILE_DELETE` or `COMPLETED`.

## Human Verification Required

None. Phase 03 has no UI/visual acceptance, and the database, object-store, PKCS11, migration/recovery, leak and delivery-trust boundaries have executable evidence.

## Nonblocking Limits

- SoftHSM establishes Java 21 SunPKCS11 protocol behavior, not certification of a specific physical HSM deployment.
- Production snapshot commands require signed, root-owned MySQL clients below the fixed trusted roots. The real lane substitutes only a test-source Docker process boundary where host clients cannot satisfy production authority; it is absent from the production JAR.
- Java path execution retains a platform TOCTOU residual after the last identity check; repeated owner/mode/ACL/inode/size/digest checks and the signed digest are the available authority boundary.
- Retry closures must remain free of external non-JDBC side effects. Current callers retry only classified transient lock failures after JDBC rollback.
- Remaining TODO/summary/tag/remote-check updates are delivery-workflow records owned by the orchestrator. This report verifies the Phase 03 product goal and current evidence binding; it does not self-certify those later records as finalized.

## Gaps Summary

No unresolved product truth, missing/stub artifact, broken production link, evidence-trust blocker or human-only acceptance item remains. The PR-head checkout delta changes no production or Linux fixture behavior and is protected by a destructive workflow mutation; committed sanitized results remain outside the product subject. All four atomic must-haves are achieved by actual production-reachable code and the current subject evidence chain.

## Delivery Parser Binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | 8d5db434594e9710ddaee7b9174fdd4e7303abdebb37432b69a159f918058dc2 | fa490969381b4caf835af3cabe733a3962b4fe6857a97df322d65a94c3605d4a | PASS |

## Final verdict

PASS
