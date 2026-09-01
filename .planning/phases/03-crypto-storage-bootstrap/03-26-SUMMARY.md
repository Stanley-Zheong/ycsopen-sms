---
phase: 03-crypto-storage-bootstrap
plan: "26"
subsystem: protected-message-persistence
tags: [java-21, spring-transaction, ycse, blind-index, routing]

requires:
  - phase: 03-crypto-storage-bootstrap-09
    provides: Sole context-bound message protection prepare/save adapter
  - phase: 03-crypto-storage-bootstrap-25
    provides: Exact current protected reader/writer inventory and source fence
provides:
  - Current message submission adoption of MessageTaskProtectionAdapter
  - Ordered opaque ACTIVE and RETIRING query values at the routing boundary
  - No-write rejection and protection-dependency failure contract
  - Retired direct-key FieldEncryptor surface
affects: [03-10-blind-index-query-owner, 03-29-tenant-registration-writer]

tech-stack:
  added: []
  patterns:
    - Generate immutable business identity before one protected prepare operation
    - Route only prepared opaque query values and persist only through the protected adapter

key-files:
  created:
    - core/src/test/java/com/ycsopen/sms/core/service/message/MessageSubmitServiceTest.java
  modified:
    - core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java
    - core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingContext.java
    - core/src/main/java/com/ycsopen/sms/core/service/routing/ThirdPartyBlacklistClient.java
    - core/src/main/resources/security/protected-data-inventory.json
    - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/CurrentProtectedReaderFenceTest.java
  removed:
    - core/src/main/java/com/ycsopen/sms/core/common/security/FieldEncryptor.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/FieldEncryptorTest.java

key-decisions:
  - "Message submission generates its immutable ID before protection and invokes prepare exactly once before routing."
  - "RoutingContext carries the ordered versioned HMAC set; the temporary Plan 03-10 compatibility accessor derives only an opaque member from that set."
  - "HashUtil remains present but has no caller because Plan 03-10 owns its checkpoint-gated legacy-reader replacement and deletion audit."
  - "No Phase 03 obligation closes here; the authoritative scoped TODO set remains open."

patterns-established:
  - "Protected writer adoption: prepare before policy evaluation, save only after allow, and continue to billing only after adapter save succeeds."
  - "Inventory adoption requires exact source tokens and a production source fence, not a class-name or column-name claim."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 26: Protected Message Writer Adoption Summary

**Message submission now prepares one context-bound YCSE mobile before routing, routes only versioned opaque query values, and persists the envelope plus separate blind-index rows only through the transactional protection adapter.**

## Accomplishments

- Replaced `MessageSubmitService` calls to `HashUtil`, protected-field string setters, and `MessageTaskRepository.save` with one constructor-injected `MessageTaskProtectionAdapter`.
- Moved immutable message-ID creation ahead of protection and routing so the same tenant/message identity binds preparation, routing, persistence, billing, and the response.
- Added ordered `BlindIndexPort.OrderedIndexes` to `RoutingContext`; the existing Plan 03-10-owned scalar compatibility view derives an opaque versioned HMAC member and never receives the phone or raw SHA-256 on the current submission path.
- Proved routing rejection, protection dependency outage, and protected-save failure do not call the persistence adapter save or billing continuation in the invalid path.
- Removed the zero-caller, direct-key `FieldEncryptor` and its legacy unversioned-payload test.
- Transitioned only `message-submit-persistence` from blocking to adopted in the inventory; `tenant-registration-persistence` remains the sole exact current-surface blocker.

## Task Commit

1. **Task 1: Rewire submission and remove legacy direct-key/hash paths** — `89e0cae`

The two tracked deletions in this commit are intentional: the direct-key `FieldEncryptor` production class and its matching legacy test were removed after the production caller scan returned empty.

## Writer Source Scan

| Surface | Evidence | Result |
| --- | --- | --- |
| Submission orchestration | `MessageSubmitService` contains `messageTaskProtectionAdapter.prepare`, ordered `mobileQueryIndexes`, and `messageTaskProtectionAdapter.save` | Adopted |
| Direct message write | No `messageTaskRepository.save`, `setMobileEncrypted`, or `setMobileHash` in the current submission service | Absent |
| Raw mobile digest | No `HashUtil` or `sha256Hex` caller in the message/routing files changed by this plan | Absent |
| Routing payload | `RoutingContext` receives `BlindIndexPort.OrderedIndexes`; the service does not pass phone plaintext or a raw SHA value | Opaque only |
| Third-party routing stub | The existing stub no longer describes future direct decryption and no longer logs the opaque query value | Opaque only |
| Direct-key helper | `FieldEncryptor.java` and `FieldEncryptorTest.java` no longer exist | Removed |
| Legacy hash owner | Repository-wide symbol audit finds no `HashUtil` caller; the utility definition remains for Plan 03-10's required audit and checkpoint-gated legacy reader | Deferred to owner |

The plan's literal broad `sha256Hex` grep also reports the pre-existing private helper in `S3PrivateObjectStoreAdapter`. Those calls digest encrypted object bytes for integrity metadata; they do not accept a mobile, write a legacy mobile index, call `HashUtil`, or belong to this writer surface. The narrower message/routing direct-write fence passes with no match.

## No-Write Failure Contract

- Protection dependency failure occurs during `prepare`, before routing or any persistence call.
- Routing rejection occurs after preparation but before `save`; no task, envelope, blind-index row, or billing reservation is requested.
- Protected-save failure stops before billing. The adapter's existing transactional tests continue to prove task and per-version index inserts roll back together.
- The success path is ordered `prepare -> route -> save -> reserve`, and uses the generated message ID unchanged throughout.

## Automated Checks

- `mvn -f core/pom.xml -Dtest='MessageSubmitServiceTest,RoutingEngineTest,CurrentProtectedReaderFenceTest' test` — PASS.
- `mvn -f core/pom.xml test` — PASS. Explicit real-service integration lanes remain behind their existing opt-in gates and were not treated as Plan 26 evidence.
- `/usr/bin/env ruby .planning/tools/validate-phase-03-protected-inventory.rb --manifest core/src/main/resources/security/protected-data-inventory.json --schema core/src/main/resources/db/migration/V1__init_schema.sql --source-root core/src/main/java` — PASS; exact-six surfaces remain registered and only tenant registration is blocking.
- Repository-wide `HashUtil` caller audit — PASS; only the retained utility definition remains.
- Scoped message/routing direct-write and raw-digest fence — PASS.
- Inventory JSON parsing and `git diff --check` — PASS.

## Decisions Made

- Preserved business ordering around template/signature validation, final-content rendering, routing, persistence, and billing while inserting immutable-ID generation and protected preparation immediately before routing.
- Kept `HashUtil.java` untouched despite zero callers because Plan 03-10 explicitly owns its replacement with `LegacyMobileHashReader`, source audit, and deletion decision.
- Kept every Phase 03 requirement and obligation open. This plan adopts one live writer but does not provide Plan 03-10 real Connector/J/PKCS11 proof, tenant-registration adoption, canonical leak evidence, independent review, or delivery attestation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated the exact-current-surface fence after the planned adoption transition**

- **Found during:** Task 1 complete backend verification.
- **Issue:** `CurrentProtectedReaderFenceTest`, introduced by Plan 03-25, still required message submission to remain in the exact blocker set, so it correctly failed once this plan changed the inventory to the new factual adopted state.
- **Fix:** With orchestrator authorization, changed only the stale message-submit blocker expectation to require tenant registration as the sole blocker and added exact adapter/source assertions. The exact-six surface set and all other safety assertions remain unchanged.
- **Files modified:** `core/src/test/java/com/ycsopen/sms/core/common/security/persistence/CurrentProtectedReaderFenceTest.java`.
- **Verification:** Focused and complete backend suites pass, and the independent inventory validator reports the same sole blocker.
- **Committed in:** `89e0cae`.

**Total deviations:** one blocking test adaptation. **Impact:** The executable fence now represents the planned inventory transition without weakening surface enumeration or protected-reader checks.

## Issues Encountered

- The plan's broad literal `sha256Hex` source fence predates Plan 03-15 and also matches that plan's private ciphertext-checksum helper. The classified result is retained above; the exact message/routing writer fence and repository-wide `HashUtil` caller audit pass. No out-of-scope object-storage code was renamed or modified.

## Known Stubs

- `core/src/main/java/com/ycsopen/sms/core/service/routing/ThirdPartyBlacklistClient.java` remains the pre-existing F-5.3 endpoint/request/response stub. This plan removed unsafe direct-decryption guidance and value logging but does not claim the later third-party integration.
- `core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java` retains the pre-existing F-6.7 real upstream dispatch TODO. Protected persistence now precedes that future owner; real dispatch is not claimed here.
- `RoutingContext.getMobileHash()` is a temporary Plan 03-10 compatibility seam for current routing consumers. On the adopted submission path it derives a versioned opaque query member from `mobileQueryIndexes`; Plan 03-10 owns metadata-first lookup and retirement of the legacy-shaped accessor.

None of these stubs prevents this plan's protected-writer adoption goal, and none is represented as complete.

## Threat Surface Review

- **Information disclosure:** the service no longer places phone plaintext or raw SHA-256 into routing or entity setters, and the third-party stub no longer logs an opaque mobile query value.
- **Denial of service / partial state:** preparation, rejection, and save failures stop before downstream writes; the adapter retains the atomic task/index transaction boundary.
- **Repudiation:** inventory and source tests bind the adopted disposition to exact service, context, entity, repository, and adapter tokens.

No new endpoint, network call, schema object, filesystem path, decryption capability, or secret-bearing configuration was introduced beyond the plan's declared protected-write boundary.

## Verification Boundary

- The default backend suite does not execute explicitly opt-in MySQL, MinIO, SoftHSM, Redis, migration, or timezone lanes. Plan 03-10 owns real Connector/J and production PKCS11 raw-row proof.
- This plan proves current service adoption and deterministic no-write orchestration. It does not close `OBL-CRYPTO-STORAGE-001` or `OBL-CRYPTO-STORAGE-003` without their remaining implementation and evidence.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. The authoritative scoped TODO set is not empty, and `requirements-completed` remains empty.

## Next Plan Readiness

Plan 03-10 can resume its checkpoint-aware legacy reader, ACTIVE/RETIRING metadata lookup, `HashUtil` deletion audit, and real MySQL/PKCS11 proof against the now-adopted message writer. Tenant registration remains a separate blocking writer owned by Plan 03-29.

## Self-Check: PASSED

- Every declared retained implementation/test/inventory file and this summary exists; both intentional `FieldEncryptor` deletions are absent.
- Task commit `89e0cae` exists on `phase/03-crypto-storage-bootstrap` and contains only the planned files plus the authorized stale-fence adaptation.
- Focused tests, the complete backend suite, inventory reconciliation, scoped source fences, JSON parsing, and diff checks pass.
- The inventory preserves exactly six current surfaces and identifies only `tenant-registration-persistence` as blocking.
- `HashUtil.java` remains present with no caller for Plan 03-10; no Phase 03 obligation or requirement is marked complete.
