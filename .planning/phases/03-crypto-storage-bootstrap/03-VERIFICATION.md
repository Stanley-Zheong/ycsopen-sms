---
phase: 03-crypto-storage-bootstrap
verified: 2026-09-05T22:44:47Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
subject_manifest_path: .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json
subject_manifest_digest: e04d225b74fa9c2a18ef9316987278bd370c70fa99b8cb0feeeb305a8e3e2eb1
subject_manifest_file_sha256: 12c9531010209cca14a8507ff9b7e3df21ef805811c9aaf3c07bfd21af83d843
tested_subject_digest: 78ab379f0d1e55c34740a6e13962dd558579558f45441ed0d757d5b4a1eb1c7f
evidence_manifest_sha256: 9cad7d1610be6d473ebcc63647da2954427bcaf79b5feb45ddabaa16d80b23db
plan_deviations:
  - id: DR-P03-012
    status: accepted
    scope: execution-shape-only
    reason: Fixed root real-service and production-reachability lanes replace the absent monolithic Phase03FullFaultIntegrationTest without waiving behavior.
---

# Phase 03: Crypto Storage Bootstrap — Corrected-Subject Verification

**Phase Goal:** Protected database/object fields and logs contain no prohibited plaintext in executable samples; rotation and rollback preserve data without persisted master keys; existing plaintext migrates through a verified, resumable and auditable path.
**Verified:** 2026-09-05T22:44:47Z
**Status:** passed
**Mode:** Fresh corrected-subject goal-backward verification. SUMMARY files were not accepted as implementation evidence.

## Verification Basis

The ROADMAP success criteria, `REQ-NFR-DATA-PROTECTION`, Phase 03 SPEC behavior contracts and the authoritative owner query reduce to four atomic product must-haves: OBL-CRYPTO-STORAGE-001 through 004. Verification worked backward from those outcomes into production artifacts, transaction/key wiring, focused and real-service tests, and the current evidence chain.

The inspected candidate is bound by canonical subject-manifest digest `e04d225b74fa9c2a18ef9316987278bd370c70fa99b8cb0feeeb305a8e3e2eb1` and tested-subject digest `78ab379f0d1e55c34740a6e13962dd558579558f45441ed0d757d5b4a1eb1c7f`. The evidence manifest file SHA-256 is `9cad7d1610be6d473ebcc63647da2954427bcaf79b5feb45ddabaa16d80b23db`.

## Goal Achievement

### Observable Truths

| # | Must-have | Status | Code, wiring and executable evidence |
| --- | --- | --- | --- |
| 1 | **OBL-CRYPTO-STORAGE-001:** every current executable/migratable protected database value crosses the context-bound envelope/index boundary, and accepted database/log samples contain no prohibited plaintext. | ✓ VERIFIED | The accepted 17-field inventory drives current readers/writers, migration and leak checks. Message, blacklist and tenant-registration writers use protected codecs plus FIELD/MOBILE publication fences. Real protected-persistence child result `b6eaf21a...`, inventory result `bdf390a2...` and complete leak result `ca8d6467...` are bound into current PASS evidence `6e07acea...`. |
| 2 | **OBL-CRYPTO-STORAGE-002:** protected objects remain private ciphertext and are returned only after application authorization/capability checks; staged registration objects retain purpose/session/claim/reconciliation semantics without raw URLs. | ✓ VERIFIED | The conditional production object graph composes the private S3 adapter, protected codec, JDBC metadata, deny-by-default authorization, capability service and registration sessions. Object creation reserves the exact FIELD version; deletion releases it only from valid terminal predecessor states. Real object child result `064dabc5...` plus inventory/leak results are bound into current PASS evidence `3bd7fc37...`. |
| 3 | **OBL-CRYPTO-STORAGE-003:** keys remain opaque and purpose-separated; activation, rewrap, restart, rollback and publication-versus-retirement races preserve readability while exact live references block retirement. | ✓ VERIFIED | Production SunPKCS11 composition resolves database-owned versions. FIELD, MOBILE, object-capability, registration-upload and SNAPSHOT purposes share ordered purpose-lock protocols but retain distinct aliases/domains/inventories. Retirement performs its final inventory inside the guarded purpose transaction; retained snapshots and object reservations remain reference sources. Real PKCS11/fault child result `28428730...` is bound into PASS evidence `a8499dca...`; no raw root-key material appears in evidence or leak results. |
| 4 | **OBL-CRYPTO-STORAGE-004:** signed preflight and legacy migration are fail-closed, idempotent, resumable and auditable, with bounded authenticated encrypted snapshot restore into a fresh schema and no plaintext dump file. | ✓ VERIFIED | The shipped ServiceLoader factory authenticates canonical Ed25519-signed production configuration from an independently pinned public key, verifies the configured snapshot root, then composes JDBC/HSM/migration and snapshot create/restore/delete operations. Restore authenticates the retained manifest and every chunk before MySQL, reauthenticates afterward, re-dumps the target and compares length/SHA-256 before completion. Fixed MySQL clients enforce fixed argv/environment and root-owned trusted executable paths with owner/mode/ACL/inode/size/signed-digest checks. Real migration child result `d0957e09...` is bound into PASS evidence `3397bf5e...`. |

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

## Evidence Integrity

| Evidence | Current binding | Status |
| --- | --- | --- |
| Subject manifest | canonical digest `e04d225b74fa9c2a18ef9316987278bd370c70fa99b8cb0feeeb305a8e3e2eb1`; serialized file SHA `12c9531010209cca14a8507ff9b7e3df21ef805811c9aaf3c07bfd21af83d843` | ✓ VERIFIED |
| Tested subject | `78ab379f0d1e55c34740a6e13962dd558579558f45441ed0d757d5b4a1eb1c7f` | ✓ VERIFIED |
| Evidence manifest | file SHA `9cad7d1610be6d473ebcc63647da2954427bcaf79b5feb45ddabaa16d80b23db`; exact four ordered PASS entries | ✓ VERIFIED |
| Root aggregate | result `2cdc39315467c747b7502b53db4c92597ee048db68a460a7e522038df6a18bef`; registry `4b1f32f9e6a2693a5f442cb0f2617f83992423b4a799b2fa319f3f452546edb7`; 14/14 lanes PASS | ✓ VERIFIED |
| OBL-001 | evidence digest `6e07aceafe5c0c2ca017d23123f2e9b6954157ff18680ab14018cc13b035e4ab`; file SHA `47bd84a2cc02a53450cedaac9f51f718d3cac9a5fe3cb2ed59d3320df280275b` | ✓ PASS |
| OBL-002 | evidence digest `3bd7fc379f560fc7c9c9398d9553c3e13839e6af1a32e79d1ade53faa4155e76`; file SHA `4bb3b0fbb424abbbb7f95282acccc897f4877092e8a36d43eaf1778446da33c3` | ✓ PASS |
| OBL-003 | evidence digest `a8499dca3a008e9738a36efdfe43f0da74fe22d4d11faa7c13633a32154a9213`; file SHA `32d4381743d6cb50272cf5ffaaf254a7ac8fd4407fa66b3dcf730a2cfd0bb66c` | ✓ PASS |
| OBL-004 | evidence digest `3397bf5e828e8723fba8bf15fe2695faa3609e1741a3ddbf22dd1f49195ac511`; file SHA `7d555f4c1c77a768e83a899faee6e3f7430e01b25596d68664b3dfeceaec128d` | ✓ PASS |
| Inventory/leak | accepted inventory `9d31954a...`; inventory result `bdf390a2...`; complete leak result `ca8d6467...` | ✓ PASS |

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

- GSD code review Round 12: PASS, BLOCKER 0, HIGH 0. Its four warnings concern workflow-test semantics, early local symlink rejection, binding-number naming/start, and pending final record refresh; none breaks the current product or target-tree evidence chain.
- Claude Attempt 9: PASS, BLOCKER 0, HIGH 0. Its four warnings and one info item are the same nonblocking delivery/test strictness boundaries and the then-pending goal-verification replay.
- Round 10 product fixes remain present: the FIELD fence has one production Spring bean, and `markDeleted` releases a FIELD reservation only from `OBJECT_STORED`, `RECONCILE_DELETE` or `COMPLETED`; focused regression tests remain in the current subject.

## Human Verification Required

None. Phase 03 has no UI/visual acceptance, and the database, object-store, PKCS11, migration/recovery, leak and delivery-trust boundaries have executable evidence.

## Nonblocking Limits

- SoftHSM establishes Java 21 SunPKCS11 protocol behavior, not certification of a specific physical HSM deployment.
- Production snapshot commands require signed, root-owned MySQL clients below the fixed trusted roots. The macOS real lane substitutes only a test-source Docker process boundary, which is absent from the production JAR.
- Java path execution retains a platform TOCTOU residual after the last identity check; repeated owner/mode/ACL/inode/size/digest checks and the signed digest are the available authority boundary.
- Retry closures must remain free of external non-JDBC side effects. Current callers retry only classified transient lock failures after JDBC rollback.
- Remaining TODO/summary/tag/remote-check updates are delivery-workflow records owned by the orchestrator. This report verifies the Phase 03 goal; it does not self-certify that those later records have already been sealed.

## Gaps Summary

No unresolved product truth, missing/stub artifact, broken production link, evidence-trust blocker, BLOCKER/HIGH review finding or human-only acceptance item remains. All four atomic must-haves are achieved by actual production-reachable code and the corrected-subject evidence chain.

## Delivery Parser Binding

| Attempt | BLOCKER | HIGH | Escalated | Subject manifest path | Subject manifest digest | Tested subject digest | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 0 | no | .planning/phases/03-crypto-storage-bootstrap/EVIDENCE/tested-inputs.json | e04d225b74fa9c2a18ef9316987278bd370c70fa99b8cb0feeeb305a8e3e2eb1 | 78ab379f0d1e55c34740a6e13962dd558579558f45441ed0d757d5b4a1eb1c7f | PASS |

## Final verdict

PASS
