---
phase: 03-crypto-storage-bootstrap
verified: 2026-09-05T23:39:17Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
subject_manifest_path: .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json
subject_manifest_digest: ef4002d2d3a4518c38a52ba97a3e6f482441e8a54bdea82eb5dd082c1bc81bac
subject_manifest_file_sha256: 4a4cc5e45890fe5dccbe0a24b166b5b8f447f0b22f670161aca7eb783129c58c
tested_subject_digest: acdbdba8db25d3936cb9eb99310c1ee2e14f77f430574109d6c239a6906823c0
evidence_manifest_sha256: e6ce5f0998ccc4ef3e758e6fd92fa3487555bdea6cbe855103c55ccb3769bb01
plan_deviations:
  - id: DR-P03-012
    status: accepted
    scope: execution-shape-only
    reason: Fixed root real-service and production-reachability lanes replace the absent monolithic Phase03FullFaultIntegrationTest without waiving behavior.
---

# Phase 03: Crypto Storage Bootstrap — Explicit CI-Toolchain Subject Verification

**Phase Goal:** Protected database/object fields and logs contain no prohibited plaintext in executable samples; rotation and rollback preserve data without persisted master keys; existing plaintext migrates through a verified, resumable and auditable path.
**Verified:** 2026-09-05T23:39:17Z
**Status:** passed
**Mode:** Fresh corrected-subject goal-backward verification. SUMMARY files were not accepted as implementation evidence.

## Verification Basis

The ROADMAP success criteria, `REQ-NFR-DATA-PROTECTION`, Phase 03 SPEC behavior contracts and the authoritative owner query reduce to four atomic product must-haves: OBL-CRYPTO-STORAGE-001 through 004. Verification worked backward from those outcomes into production artifacts, transaction/key wiring, focused and real-service tests, and the current evidence chain.

The inspected 316-input candidate is bound by canonical subject-manifest digest `ef4002d2d3a4518c38a52ba97a3e6f482441e8a54bdea82eb5dd082c1bc81bac` and tested-subject digest `acdbdba8db25d3936cb9eb99310c1ee2e14f77f430574109d6c239a6906823c0`. The evidence manifest file SHA-256 is `e6ce5f0998ccc4ef3e758e6fd92fa3487555bdea6cbe855103c55ccb3769bb01`.

## Goal Achievement

### Observable Truths

| # | Must-have | Status | Code, wiring and executable evidence |
| --- | --- | --- | --- |
| 1 | **OBL-CRYPTO-STORAGE-001:** every current executable/migratable protected database value crosses the context-bound envelope/index boundary, and accepted database/log samples contain no prohibited plaintext. | ✓ VERIFIED | The accepted 17-field inventory drives current readers/writers, migration and leak checks. Message, blacklist and tenant-registration writers use protected codecs plus FIELD/MOBILE publication fences. Real protected-persistence child result `7f8542a4...`, inventory result `342dc3cd...` and complete leak result `c526ae8e...` are bound into current PASS evidence `79a104d7...`. |
| 2 | **OBL-CRYPTO-STORAGE-002:** protected objects remain private ciphertext and are returned only after application authorization/capability checks; staged registration objects retain purpose/session/claim/reconciliation semantics without raw URLs. | ✓ VERIFIED | The conditional production object graph composes the private S3 adapter, protected codec, JDBC metadata, deny-by-default authorization, capability service and registration sessions. Object creation reserves the exact FIELD version; deletion releases it only from valid terminal predecessor states. Real object child result `f1b22c7f...` plus inventory/leak results are bound into current PASS evidence `ffcadc3c...`. |
| 3 | **OBL-CRYPTO-STORAGE-003:** keys remain opaque and purpose-separated; activation, rewrap, restart, rollback and publication-versus-retirement races preserve readability while exact live references block retirement. | ✓ VERIFIED | Production SunPKCS11 composition resolves database-owned versions. FIELD, MOBILE, object-capability, registration-upload and SNAPSHOT purposes share ordered purpose-lock protocols but retain distinct aliases/domains/inventories. Retirement performs its final inventory inside the guarded purpose transaction; retained snapshots and object reservations remain reference sources. Real PKCS11/fault child result `8959a3d8...` is bound into PASS evidence `11e27b18...`; no raw root-key material appears in evidence or leak results. |
| 4 | **OBL-CRYPTO-STORAGE-004:** signed preflight and legacy migration are fail-closed, idempotent, resumable and auditable, with bounded authenticated encrypted snapshot restore into a fresh schema and no plaintext dump file. | ✓ VERIFIED | The shipped ServiceLoader factory authenticates canonical Ed25519-signed production configuration from an independently pinned public key, verifies the configured snapshot root, then composes JDBC/HSM/migration and snapshot create/restore/delete operations. Restore authenticates the retained manifest and every chunk before MySQL, reauthenticates afterward, re-dumps the target and compares length/SHA-256 before completion. Fixed MySQL clients enforce fixed argv/environment and root-owned trusted executable paths with owner/mode/ACL/inode/size/signed-digest checks. Real migration child result `2e58f8c5...` is bound into PASS evidence `676b010a...`. |

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
| `CryptoStorageConfiguration` → lifecycle/fences | One fail-closed production crypto graph using database-owned key state | ✓ WIRED | Exactly one `FieldReferencePublicationFence` bean; lifecycle consumes FIELD and snapshot sources; production reachability lane PASS `f645a508...`. |
| Current persistence/object writers → purpose locks | No stale FIELD/MOBILE/token reference can become durable beside activation/retirement | ✓ WIRED | Publication locks the complete purpose set before insert; lifecycle takes the same lock before final inventory/transition; deterministic race tests and real-service lanes cover both sides. |
| Retained snapshot store → SNAPSHOT retirement | Retained/incomplete/corrupt/unavailable snapshot state cannot be treated as zero references | ✓ WIRED | `EnvelopeReferenceInventory` enumerates exact manifests/chunks and fails closed; snapshot service publishes manifest only after complete encrypted chunks. |
| `ProtectedDataMigrationLauncher` → production factory | Shipped command reaches signed config, JDBC/HSM, fixed process authority and real snapshot operations | ✓ WIRED | ServiceLoader requires one provider; common production compose path owns resources and closes partial acquisition; test bridge/Docker adapter are absent from the production JAR. |
| Fixed 14-lane root → exact-four producer | Only current same-subject successful real/deterministic/leak/cleanup results can emit obligation PASS | ✓ WIRED | Aggregate PASS, all 14 lane digests present, exact-four validator PASS, destructive evidence suite 59 cases PASS. |

## Round 11 Delivery Trust-Chain Closure

Round 11 identified a delivery-subject trust gap rather than a product-crypto defect. The corrected implementation was verified at four levels:

1. **Closed trusted set:** `scripts/lib/phase-03/run_checks.rb`, the target-tree validator and the destructive fixture each declare the same duplicate-free 15-file trusted-input set. Every trusted entry exists in the 316-entry current subject with its actual Git-compatible mode, SHA-256 and code-owned role.
2. **Immutable reconstruction:** delivery validation fetches the target commit into an isolated bare object store, reads files with `git cat-file`, enumerates with `git ls-tree`, parses the target producer's literal trusted set and compares the complete path/role/content/mode set. It does not substitute mutable working-tree inputs for tag-target evidence.
3. **Destructive proof:** `test-delivery-attestation.rb` passed 108 cases, including 104 destructive cases and 11 workflow cases. The 15 trusted paths each have missing/content/mode mutations (45/45 rejected). Lifecycle tests passed Phase 03 10/10; root-registry tests passed 68 assertions across 14 checks.
4. **Required check:** `.github/workflows/ci.yml` defines the unconditional `Phase 03 portable registry` job. It explicitly installs and probes `ripgrep`, then runs backend/default checks, Flyway ownership, destructive validators, current exact-four/lifecycle validation, source reachability and a packaged-JAR rejection of test-only bridges.

The current producer still follows a trusted symlink during its early `File.file?` presence check before later excluding symlinks. This is nonblocking for delivery integrity because target-tree reconstruction rejects Git mode `120000` and reports the trusted blob missing; the current canonical subject contains regular files with the expected modes and hashes.

## Linux-Portability Baseline Verification

The Linux-portable baseline immediately preceding the current CI-toolchain delta changed only `ProductionMigrationCommandServicesFactoryTest`:

- JUnit `@TempDir`, which resolves below Linux `/tmp`, was replaced by `Files.createTempDirectory(Path.of(System.getProperty("user.home")).toRealPath(), ...)` plus deterministic recursive cleanup.
- The test still canonicalizes the created directory before writing signed configuration fixtures. It changes only where test fixtures live; it does not provide an alternate production factory path or weaken signature, ownership, permission, no-symlink or stable-read checks.
- No `core/src/main` file changed in this portability correction. Production `assertNoSymlinkComponents` still requires every ancestor to be a directory owned by the process owner or root and rejects group/other-writable ancestors. File metadata remains owner-read, non-group/other-writable, regular, non-symlink and stable across reads.
- The portable test hash remains included in the current 316-input subject, which passed the canonical 14/14 root on Linux, including default Maven, production reachability and the real MySQL/MinIO/SoftHSM lane.

Therefore the portability change fixes an invalid Linux test-fixture assumption while preserving the production writable-ancestor rejection policy.

## Explicit CI-Toolchain Delta Verification

Relative to the immediately preceding Linux-portable subject, the current product and test behavior is unchanged: there is no `core/src/main` or `core/src/test` worktree diff. The only subject changes are the CI workflow and its delivery-contract test:

- `.github/workflows/ci.yml` installs `ripgrep` with `--no-install-recommends` and executes `rg --version` before the Phase 03 verification commands that depend on `rg`.
- `.planning/tools/test-delivery-attestation.rb` requires both the install command and the executable probe, and its two new destructive mutations prove that omission of either token is rejected.
- These changes make an existing validator dependency explicit; they do not alter production crypto, storage, migration, path-authority or Linux fixture behavior.

The current subject passed the 14/14 root, exact-four 4/4 validation, 59 evidence fixtures, delivery 108/104 with 11 workflow cases, and Phase 1/Phase 3 lifecycle suites 21/10. The toolchain correction therefore preserves all four product obligations while closing the CI precondition.

## Evidence Integrity

| Evidence | Current binding | Status |
| --- | --- | --- |
| Subject manifest | canonical digest `ef4002d2d3a4518c38a52ba97a3e6f482441e8a54bdea82eb5dd082c1bc81bac`; serialized file SHA `4a4cc5e45890fe5dccbe0a24b166b5b8f447f0b22f670161aca7eb783129c58c` | ✓ VERIFIED |
| Tested subject | `acdbdba8db25d3936cb9eb99310c1ee2e14f77f430574109d6c239a6906823c0` | ✓ VERIFIED |
| Evidence manifest | file SHA `e6ce5f0998ccc4ef3e758e6fd92fa3487555bdea6cbe855103c55ccb3769bb01`; exact four ordered PASS entries | ✓ VERIFIED |
| Root aggregate | result `9ddf7fa7de8cb49a6110f10cb309bd1bdbdeb7244074bf38cc240f9cdc5a9e3a`; registry `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`; 14/14 lanes PASS | ✓ VERIFIED |
| OBL-001 | evidence digest `79a104d79e269614ce6bda175b03c0dd7c90bd5f718ed2728598cc9232396432`; file SHA `10d7dff640cd7ebe8a155dd15d0136d60e8d39e21b13954cc3b5af8e6c962a9a` | ✓ PASS |
| OBL-002 | evidence digest `ffcadc3c3ea7aae14782cecaaddeec7bf3742eeb4e27c1a5d371e9cf3d54ccd6`; file SHA `efda99114ff1437bc9cf40636aa5744dca2cf646eefb011a3b4adfb8f58e1982` | ✓ PASS |
| OBL-003 | evidence digest `11e27b18ebb036ef257135cd7b731969241071a3c38c72c793d0af5064661f1c`; file SHA `73920555bcf357d06f740bb96d4036a08761e14eaa1b7b173b6b38bd93e23d7b` | ✓ PASS |
| OBL-004 | evidence digest `676b010aa9fb8d29c30948478b5c4c63cb1ab5114a5736c581f14785de2f289c`; file SHA `8bc52c1be68b104ab2dfcab9d59749ef89754dd3d797a29596357b5c54623a2f` | ✓ PASS |
| Inventory/leak | accepted inventory `9d31954a...`; inventory result `342dc3cd...`; complete leak result `c526ae8e...` | ✓ PASS |

## Behavioral Spot-Checks

| Check | Fresh result | Status |
| --- | --- | --- |
| Delivery trust-chain destructive suite | `DELIVERY_ATTESTATION_TEST PASS cases=108 destructive=104 workflow=11` | ✓ PASS |
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

- GSD code review Round 14: PASS, BLOCKER 0, HIGH 0, WARNING 4. The four inherited warnings are nonblocking delivery-quality boundaries; review of the current explicit CI-toolchain subject found no new defect.
- Claude Attempt 11: PASS, BLOCKER 0, HIGH 0, WARNING 2, INFO 3 (session `2c99f091-0f05-413b-aa99-1b7d3fdbbdc4`). It independently confirmed the current subject preserves the four product obligations and makes the CI `ripgrep` prerequisite explicit without changing production or Linux-fixture behavior.
- Independent code/evidence inspection confirms the subject delta is limited to `.github/workflows/ci.yml` and `.planning/tools/test-delivery-attestation.rb`; production code and the Linux test fixture are unchanged.
- The previously corrected product invariants remain directly present in current code: the FIELD fence has one production Spring bean, and `markDeleted` releases a FIELD reservation only from `OBJECT_STORED`, `RECONCILE_DELETE` or `COMPLETED`.

## Human Verification Required

None. Phase 03 has no UI/visual acceptance, and the database, object-store, PKCS11, migration/recovery, leak and delivery-trust boundaries have executable evidence.

## Nonblocking Limits

- SoftHSM establishes Java 21 SunPKCS11 protocol behavior, not certification of a specific physical HSM deployment.
- Production snapshot commands require signed, root-owned MySQL clients below the fixed trusted roots. The real lane substitutes only a test-source Docker process boundary where host clients cannot satisfy production authority; it is absent from the production JAR.
- Java path execution retains a platform TOCTOU residual after the last identity check; repeated owner/mode/ACL/inode/size/digest checks and the signed digest are the available authority boundary.
- Retry closures must remain free of external non-JDBC side effects. Current callers retry only classified transient lock failures after JDBC rollback.
- Round 14, Claude Attempt 11, and remaining TODO/summary/tag/remote-check updates are delivery-workflow records owned by the orchestrator. This report verifies the Phase 03 product goal and current evidence binding; it does not self-certify those later records as finalized.

## Gaps Summary

No unresolved product truth, missing/stub artifact, broken production link, evidence-trust blocker or human-only acceptance item remains. The explicit CI-toolchain delta changes no production or Linux fixture behavior and is protected by two destructive workflow mutations. All four atomic must-haves are achieved by actual production-reachable code and the current subject evidence chain.

## Delivery Parser Binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | ef4002d2d3a4518c38a52ba97a3e6f482441e8a54bdea82eb5dd082c1bc81bac | acdbdba8db25d3936cb9eb99310c1ee2e14f77f430574109d6c239a6906823c0 | PASS |

## Final verdict

PASS
