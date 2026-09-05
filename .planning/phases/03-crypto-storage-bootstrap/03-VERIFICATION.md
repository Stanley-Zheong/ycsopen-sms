---
phase: 03-crypto-storage-bootstrap
verified: 2026-09-05T23:13:22Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
subject_manifest_path: .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json
subject_manifest_digest: 52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683
subject_manifest_file_sha256: 4ab907f8f6897533e3967a657ebaf0b57e3d68f141a54a56489793fea7cb1c68
tested_subject_digest: 10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe
evidence_manifest_sha256: a9ac4a5b5b1df2a931d39d0a4c356e786418151c094182e8b2682c4e2478ee61
plan_deviations:
  - id: DR-P03-012
    status: accepted
    scope: execution-shape-only
    reason: Fixed root real-service and production-reachability lanes replace the absent monolithic Phase03FullFaultIntegrationTest without waiving behavior.
---

# Phase 03: Crypto Storage Bootstrap — Linux-Portable Corrected-Subject Verification

**Phase Goal:** Protected database/object fields and logs contain no prohibited plaintext in executable samples; rotation and rollback preserve data without persisted master keys; existing plaintext migrates through a verified, resumable and auditable path.
**Verified:** 2026-09-05T23:13:22Z
**Status:** passed
**Mode:** Fresh corrected-subject goal-backward verification. SUMMARY files were not accepted as implementation evidence.

## Verification Basis

The ROADMAP success criteria, `REQ-NFR-DATA-PROTECTION`, Phase 03 SPEC behavior contracts and the authoritative owner query reduce to four atomic product must-haves: OBL-CRYPTO-STORAGE-001 through 004. Verification worked backward from those outcomes into production artifacts, transaction/key wiring, focused and real-service tests, and the current evidence chain.

The inspected 316-input candidate is bound by canonical subject-manifest digest `52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683` and tested-subject digest `10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe`. The evidence manifest file SHA-256 is `a9ac4a5b5b1df2a931d39d0a4c356e786418151c094182e8b2682c4e2478ee61`.

## Goal Achievement

### Observable Truths

| # | Must-have | Status | Code, wiring and executable evidence |
| --- | --- | --- | --- |
| 1 | **OBL-CRYPTO-STORAGE-001:** every current executable/migratable protected database value crosses the context-bound envelope/index boundary, and accepted database/log samples contain no prohibited plaintext. | ✓ VERIFIED | The accepted 17-field inventory drives current readers/writers, migration and leak checks. Message, blacklist and tenant-registration writers use protected codecs plus FIELD/MOBILE publication fences. Real protected-persistence child result `b6eaf21a...`, inventory result `f1163584...` and complete leak result `5b4d2e1f...` are bound into current PASS evidence `7cb13cfb...`. |
| 2 | **OBL-CRYPTO-STORAGE-002:** protected objects remain private ciphertext and are returned only after application authorization/capability checks; staged registration objects retain purpose/session/claim/reconciliation semantics without raw URLs. | ✓ VERIFIED | The conditional production object graph composes the private S3 adapter, protected codec, JDBC metadata, deny-by-default authorization, capability service and registration sessions. Object creation reserves the exact FIELD version; deletion releases it only from valid terminal predecessor states. Real object child result `064dabc5...` plus inventory/leak results are bound into current PASS evidence `7d650030...`. |
| 3 | **OBL-CRYPTO-STORAGE-003:** keys remain opaque and purpose-separated; activation, rewrap, restart, rollback and publication-versus-retirement races preserve readability while exact live references block retirement. | ✓ VERIFIED | Production SunPKCS11 composition resolves database-owned versions. FIELD, MOBILE, object-capability, registration-upload and SNAPSHOT purposes share ordered purpose-lock protocols but retain distinct aliases/domains/inventories. Retirement performs its final inventory inside the guarded purpose transaction; retained snapshots and object reservations remain reference sources. Real PKCS11/fault child result `28428730...` is bound into PASS evidence `0feb7892...`; no raw root-key material appears in evidence or leak results. |
| 4 | **OBL-CRYPTO-STORAGE-004:** signed preflight and legacy migration are fail-closed, idempotent, resumable and auditable, with bounded authenticated encrypted snapshot restore into a fresh schema and no plaintext dump file. | ✓ VERIFIED | The shipped ServiceLoader factory authenticates canonical Ed25519-signed production configuration from an independently pinned public key, verifies the configured snapshot root, then composes JDBC/HSM/migration and snapshot create/restore/delete operations. Restore authenticates the retained manifest and every chunk before MySQL, reauthenticates afterward, re-dumps the target and compares length/SHA-256 before completion. Fixed MySQL clients enforce fixed argv/environment and root-owned trusted executable paths with owner/mode/ACL/inode/size/signed-digest checks. Real migration child result `d0957e09...` is bound into PASS evidence `70bbe6f3...`. |

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
| `CryptoStorageConfiguration` → lifecycle/fences | One fail-closed production crypto graph using database-owned key state | ✓ WIRED | Exactly one `FieldReferencePublicationFence` bean; lifecycle consumes FIELD and snapshot sources; production reachability lane PASS `59571ca1...`. |
| Current persistence/object writers → purpose locks | No stale FIELD/MOBILE/token reference can become durable beside activation/retirement | ✓ WIRED | Publication locks the complete purpose set before insert; lifecycle takes the same lock before final inventory/transition; deterministic race tests and real-service lanes cover both sides. |
| Retained snapshot store → SNAPSHOT retirement | Retained/incomplete/corrupt/unavailable snapshot state cannot be treated as zero references | ✓ WIRED | `EnvelopeReferenceInventory` enumerates exact manifests/chunks and fails closed; snapshot service publishes manifest only after complete encrypted chunks. |
| `ProtectedDataMigrationLauncher` → production factory | Shipped command reaches signed config, JDBC/HSM, fixed process authority and real snapshot operations | ✓ WIRED | ServiceLoader requires one provider; common production compose path owns resources and closes partial acquisition; test bridge/Docker adapter are absent from the production JAR. |
| Fixed 14-lane root → exact-four producer | Only current same-subject successful real/deterministic/leak/cleanup results can emit obligation PASS | ✓ WIRED | Aggregate PASS, all 14 lane digests present, exact-four validator PASS, destructive evidence suite 59 cases PASS. |

## Round 11 Delivery Trust-Chain Closure

Round 11 identified a delivery-subject trust gap rather than a product-crypto defect. The corrected implementation was verified at four levels:

1. **Closed trusted set:** `scripts/lib/phase-03/run_checks.rb`, the target-tree validator and the destructive fixture each declare the same duplicate-free 15-file trusted-input set. Every trusted entry exists in the 316-entry current subject with its actual Git-compatible mode, SHA-256 and code-owned role.
2. **Immutable reconstruction:** delivery validation fetches the target commit into an isolated bare object store, reads files with `git cat-file`, enumerates with `git ls-tree`, parses the target producer's literal trusted set and compares the complete path/role/content/mode set. It does not substitute mutable working-tree inputs for tag-target evidence.
3. **Destructive proof:** `test-delivery-attestation.rb` passed 106 cases, including 102 destructive cases, 55 Phase 03 cases and nine workflow cases. The 15 trusted paths each have missing/content/mode mutations (45/45 rejected). Lifecycle tests passed Phase 03 10/10; root-registry tests passed 68 assertions across 14 checks.
4. **Required check:** `.github/workflows/ci.yml` defines the unconditional `Phase 03 portable registry` job. It runs backend/default checks, Flyway ownership, destructive validators, current exact-four/lifecycle validation, source reachability and a packaged-JAR rejection of test-only bridges.

The current producer still follows a trusted symlink during its early `File.file?` presence check before later excluding symlinks. This is nonblocking for delivery integrity because target-tree reconstruction rejects Git mode `120000` and reports the trusted blob missing; the current canonical subject contains regular files with the expected modes and hashes.

## Linux-Portability Delta Verification

The current product subject differs from the prior reviewed subject only in `ProductionMigrationCommandServicesFactoryTest`:

- JUnit `@TempDir`, which resolves below Linux `/tmp`, was replaced by `Files.createTempDirectory(Path.of(System.getProperty("user.home")).toRealPath(), ...)` plus deterministic recursive cleanup.
- The test still canonicalizes the created directory before writing signed configuration fixtures. It changes only where test fixtures live; it does not provide an alternate production factory path or weaken signature, ownership, permission, no-symlink or stable-read checks.
- No `core/src/main` file changed in this portability correction. Production `assertNoSymlinkComponents` still requires every ancestor to be a directory owned by the process owner or root and rejects group/other-writable ancestors. File metadata remains owner-read, non-group/other-writable, regular, non-symlink and stable across reads.
- The new test hash is included in the 316-input subject, and the corrected subject passed the canonical 14/14 root on Linux, including default Maven, production reachability and the real MySQL/MinIO/SoftHSM lane.

Therefore the portability change fixes an invalid Linux test-fixture assumption while preserving the production writable-ancestor rejection policy.

## Evidence Integrity

| Evidence | Current binding | Status |
| --- | --- | --- |
| Subject manifest | canonical digest `52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683`; serialized file SHA `4ab907f8f6897533e3967a657ebaf0b57e3d68f141a54a56489793fea7cb1c68` | ✓ VERIFIED |
| Tested subject | `10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe` | ✓ VERIFIED |
| Evidence manifest | file SHA `a9ac4a5b5b1df2a931d39d0a4c356e786418151c094182e8b2682c4e2478ee61`; exact four ordered PASS entries | ✓ VERIFIED |
| Root aggregate | result `84d6b662a53902b3efbff3ec761dc7b3c4cf71d13a8386b8b3a81ad107db9be1`; registry `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`; 14/14 lanes PASS | ✓ VERIFIED |
| OBL-001 | evidence digest `7cb13cfbd499b5ce7f48dfb73d29a15aef407c3154cdfd001d180a4b838593c5`; file SHA `9d3ebdd110e8018b1a989e6388a00f56e6444ef920a4f5b59939a5ae9fe27f94` | ✓ PASS |
| OBL-002 | evidence digest `7d650030c05c66d9b275008cf2050472bfc5ff4373f0e958ce69a51640b9bf6d`; file SHA `5233adcf0347bc8fca3ac3be5acee17bdb77531a915fb856bb460d75eb6ed98e` | ✓ PASS |
| OBL-003 | evidence digest `0feb7892fbea31353da8c0f243effc98858aa386641cdb4c4bc0bcdaea046aaa`; file SHA `6499bb83df4ae980fbb477c3eecd0abc05b8178ac380b9588c73e627235fface` | ✓ PASS |
| OBL-004 | evidence digest `70bbe6f3b78026c7e329710f9fffe77bb90d073b309251ef3eb68f158f5c6969`; file SHA `22ba43d6eabb68b9f4439d597b0761dfba178a15b6aea62d4e88c3c6da7fe387` | ✓ PASS |
| Inventory/leak | accepted inventory `9d31954a...`; inventory result `f1163584...`; complete leak result `5b4d2e1f...` | ✓ PASS |

## Behavioral Spot-Checks

| Check | Fresh result | Status |
| --- | --- | --- |
| Delivery trust-chain destructive suite | `DELIVERY_ATTESTATION_TEST PASS cases=106 destructive=102 phase03=55 workflow=9` | ✓ PASS |
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

- GSD code review Round 13: PASS, BLOCKER 0, HIGH 0, WARNING 4, INFO 0. The four inherited warnings are nonblocking delivery-quality boundaries carried forward from Round 12; review of the current Linux-portable subject found no new defect.
- Claude Attempt 10: PASS, BLOCKER 0, HIGH 0, WARNING 2, INFO 3 (session `804e630e-6a4b-4d51-b44c-48296e2d4741`). It independently confirmed that the current-subject test-only portability correction does not weaken production path-authority enforcement; its warnings concern explicit POSIX `0700` documentation and abnormal-termination cleanup residue, not product correctness or security.
- Both completed reviews bind canonical subject-manifest digest `52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683`, tested subject digest `10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe`, and evidence manifest SHA `a9ac4a5b5b1df2a931d39d0a4c356e786418151c094182e8b2682c4e2478ee61`; no review refresh or replay caveat remains.
- Round 10 product fixes remain present: the FIELD fence has one production Spring bean, and `markDeleted` releases a FIELD reservation only from `OBJECT_STORED`, `RECONCILE_DELETE` or `COMPLETED`; focused regression tests remain in the current subject.

## Human Verification Required

None. Phase 03 has no UI/visual acceptance, and the database, object-store, PKCS11, migration/recovery, leak and delivery-trust boundaries have executable evidence.

## Nonblocking Limits

- SoftHSM establishes Java 21 SunPKCS11 protocol behavior, not certification of a specific physical HSM deployment.
- Production snapshot commands require signed, root-owned MySQL clients below the fixed trusted roots. The real lane substitutes only a test-source Docker process boundary where host clients cannot satisfy production authority; it is absent from the production JAR.
- Java path execution retains a platform TOCTOU residual after the last identity check; repeated owner/mode/ACL/inode/size/digest checks and the signed digest are the available authority boundary.
- Retry closures must remain free of external non-JDBC side effects. Current callers retry only classified transient lock failures after JDBC rollback.
- Remaining TODO/summary/tag/remote-check updates are delivery-workflow records owned by the orchestrator. This report verifies the Phase 03 goal; it does not self-certify that those later records have already been sealed.

## Gaps Summary

No unresolved product truth, missing/stub artifact, broken production link, evidence-trust blocker or human-only acceptance item remains. The Linux portability delta is test-only and preserves the production writable-ancestor rejection policy. All four atomic must-haves are achieved by actual production-reachable code and the current corrected-subject evidence chain.

## Delivery Parser Binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | 52e1847cb46d035aa493f3afccd3386bef352112a29856d6b5bdf43f270ac683 | 10cf0ddfe6d34edbd7bce33b13b66b3a7e09af88129cdc8706d8f3ef0165f3fe | PASS |

## Final verdict

PASS
