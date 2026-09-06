---
phase: 03-crypto-storage-bootstrap
plan: "25"
subsystem: protected-reader-persistence
tags: [java-21, spring-data-jpa, projections, protected-data-inventory, fail-closed]

requires:
  - phase: 03-crypto-storage-bootstrap-08
    provides: Hidden opaque byte-array mappings and exact-byte unrelated-write preservation
provides:
  - Secret-excluding Tenant API-key authentication projection
  - Ciphertext-excluding blacklist legacy-compatibility projection
  - ID-only tenant analytics projection
  - Executable exact-current-surface reader/writer and inventory drift fence
affects: [03-10-blind-index-query-owner, 03-26-message-writer-adoption, 03-29-tenant-registration-writer]

tech-stack:
  added: []
  patterns:
    - Explicit JPQL closed projections select only the fields a current reader needs
    - Protected reader/writer inventory records adopted boundaries separately from dedicated writer blockers

key-files:
  created:
    - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/CurrentProtectedReaderFenceTest.java
  modified:
    - core/src/main/java/com/ycsopen/sms/core/repository/TenantApiKeyRepository.java
    - core/src/main/java/com/ycsopen/sms/core/web/interceptor/HmacAuthInterceptor.java
    - core/src/main/java/com/ycsopen/sms/core/repository/BlacklistEntryRepository.java
    - core/src/main/java/com/ycsopen/sms/core/service/routing/BlacklistChecker.java
    - core/src/main/java/com/ycsopen/sms/core/repository/TenantRepository.java
    - core/src/main/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioService.java
    - core/src/test/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioServiceTest.java
    - core/src/main/resources/security/protected-data-inventory.json

key-decisions:
  - "API-key authentication selects only id, tenantId and status; app_secret_encrypted is not hydrated by the current incomplete signature path."
  - "Blacklist lookups expose only id, status, type, tenant and the legacy compatibility index; Plan 10 remains the sole ACTIVE/RETIRING metadata and checkpoint-gated legacy-union owner."
  - "Tenant lifecycle writes continue through Plan 08 opaque entity preservation, while complaint analytics enumerates only tenant IDs."
  - "Only message submission and tenant registration remain current-surface blockers; no Phase 03 obligation closes in this plan."

patterns-established:
  - "Projection fence: every protected-data-excluding query names its exact selected fields and is exercised against generated SQL."
  - "Surface drift fence: repository call-site discovery must remain a subset of manifest-declared source locations."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 25: Current Protected Reader and Writer Fence Summary

**Current API-key, blacklist and tenant-analytics reads now use exact field projections, while entity-based auth and tenant lifecycle writes retain Plan 08's hidden opaque-byte preservation and dedicated writer gaps remain explicit blockers.**

## Accomplishments

- Replaced Tenant API-key full-entity authentication lookup with an explicit `id`, `tenantId`, and `status` projection that does not select `app_secret_encrypted`.
- Replaced blacklist full-entity lookup with explicit system/tenant legacy-compatibility projections over `id`, `status`, `listType`, `tenantId`, and `mobileHash`; `mobile_encrypted` is not selected.
- Replaced complaint analytics tenant enumeration with an ID-only projection so protected Tenant bytes and object references are not hydrated.
- Preserved existing AuthService and TenantService entity behavior under the previously proven hidden `byte[]` mapping and exact-byte unrelated-write boundary.
- Reconciled the protected-data inventory with the actual opaque mappings, current projection call sites, protected message adapter, and object-ID field names.
- Added a fail-closed fence that verifies the exact six current surfaces, the exact two remaining blockers, source-token existence, repository call-site coverage, projection method shape, explicit JPQL selection, generated SQL behavior, and safe service consumption.

## Task Commit

1. **Task 1: Fence every enumerated current reader and writer** — `de8d67b`

## Exact Safe Reader/Writer Table

| Current surface | Boundary adopted by this plan/current dependency | Protected state behavior | Inventory status |
| --- | --- | --- | --- |
| Auth user hydration/save | Plan 08 hidden opaque `byte[]` entity mapping | Full entity remains allowed because protected phone bytes have no public getter/serialization path and unrelated writes preserve exact bytes | Adopted, non-blocking |
| Tenant lifecycle hydration/save | Plan 08 hidden opaque `byte[]` plus hidden object-ID mapping | Full entity remains allowed for lifecycle changes; protected values are not read and are written back unchanged | Adopted, non-blocking |
| Tenant complaint analytics | `TenantRepository.IdProjection` | Selects only `tenants.id` | Adopted, non-blocking |
| HMAC API-key lookup | `TenantApiKeyRepository.AuthenticationProjection` | Selects only key row ID, tenant ID, and status; secret bytes are excluded | Adopted, non-blocking |
| Blacklist lookup | `BlacklistEntryRepository.LookupProjection` | Selects ID, status, list type, tenant ID, and legacy index; ciphertext is excluded | Adopted compatibility boundary, non-blocking |
| Message submission writer | Existing protected adapter exists, but the live caller still invokes fail-closed plaintext/raw-digest seams | No completion claim; Plan 26 must rewire the live caller | Blocking |
| Tenant registration writer | Current request still drops protected identity/contact values and retains legacy proof URL inputs | No completion claim; Plan 29 must install protected field and object-claim persistence | Blocking |

The blacklist projection is deliberately not the final cryptographic query owner. Plan 10 remains responsible for ACTIVE and RETIRING blind-index metadata lookup plus checkpoint-gated legacy union behavior.

## Automated Checks

- `mvn -f core/pom.xml -Dtest='CurrentProtectedReaderFenceTest,ComplaintRatioServiceTest' test` — PASS; all focused projection, source-fence, current behavior, and mechanically adapted analytics tests pass.
- `/usr/bin/env ruby .planning/tools/validate-phase-03-protected-inventory.rb --manifest core/src/main/resources/security/protected-data-inventory.json --schema core/src/main/resources/db/migration/V1__init_schema.sql --source-root core/src/main/java` — PASS; exact inventory reconciles with six current surfaces and reports only the two dedicated writer blockers.
- `mvn -f core/pom.xml test` — PASS; no failure or error. Existing real-service lanes remain behind their explicit opt-in gates.
- Generated Hibernate SQL in the focused test selects only the declared API-key, blacklist, and tenant-ID projection columns.
- `git diff --check` — PASS.

## Decisions Made

- Used explicit JPQL interface projections instead of derived full-entity repository methods so the selected column set is visible in source and executable SQL.
- Kept the blacklist field named `legacyIndex` at the projection boundary to prevent this temporary raw-digest compatibility input from being mistaken for the Plan 10 versioned blind-index query contract.
- Kept AuthService and TenantService on entity paths because Plan 08 already proves exact opaque-byte preservation for their unrelated writes; only the analytics reader required an ID-only projection.
- Left every Phase 03 obligation TODO open. This plan adopts current non-message reader boundaries but does not provide real MySQL evidence, writer closure, migration/leak evidence, independent review, or delivery attestation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Adapted the existing complaint analytics unit test to the ID-only repository contract**

- **Found during:** Task 1 full backend verification.
- **Issue:** `ComplaintRatioServiceTest` stubbed `tenantRepository.findAll()` and therefore no longer exercised the service after production moved to `findAllIds()`; Mockito reported three test errors.
- **Fix:** With executor authorization, mechanically replaced only the old Tenant entity helper/stubs with `TenantRepository.IdProjection` and `findAllIds()` while preserving every ratio, threshold, channel, and saved-result assertion.
- **Files modified:** `core/src/test/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioServiceTest.java`.
- **Verification:** Focused complaint/fence tests and the complete backend suite pass.
- **Committed in:** `de8d67b`.

**Total deviations:** 1 auto-fixed blocking test adaptation. **Impact:** Test wiring now matches the safe production query without changing complaint analytics behavior or expanding implementation scope.

## Issues Encountered

None remain after the authorized test adaptation.

## Known Stubs

- `core/src/main/java/com/ycsopen/sms/core/web/interceptor/HmacAuthInterceptor.java` retains the pre-existing body-cache and complete HMAC signature verification TODO. This plan narrows the current lookup so it cannot hydrate the secret before that explicit owner exists; it does not claim complete HMAC authentication.
- Message submission and tenant registration remain explicit inventory blockers with dedicated downstream plans. They are not deferred or represented as completed behavior.

## Threat Surface Review

- **Information disclosure:** explicit JPQL projections omit protected secret/ciphertext fields, and analytics no longer hydrates the protected Tenant entity.
- **Repudiation:** the source fence binds every current surface to exact source paths/tokens and rejects undeclared repository access-path drift.
- **Tampering:** the manifest's blocking set is executable and exact; an adopted disposition cannot silently remove the message or registration blocker.

No endpoint, schema object, network connection, filesystem access path, decryption capability, or new secret-bearing API was introduced. The authentication and data-access changes are the planned threat-model surfaces.

## Verification Boundary

- Projection column behavior is proven with Spring Data JPA, Hibernate, and H2-generated SQL. Real Connector/J/MySQL protected persistence evidence remains owned by Plan 10.
- The default suite does not execute explicitly opt-in MySQL, MinIO, SoftHSM, Redis, migration, or timezone lanes; this summary does not treat those skipped lanes as Plan 25 evidence.
- This plan proves current non-message reader fencing only. It does not close message/registration writers or `OBL-CRYPTO-STORAGE-001`.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. The authoritative scoped TODO set is not empty, and `requirements-completed` remains empty.

## Self-Check: PASSED

- Every declared implementation file plus the authorized existing-test adaptation and this summary exists.
- Task commit `de8d67b` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused verification, inventory reconciliation, complete backend verification, source/stub/threat scans, JSON parsing, and diff checks pass.
- The inventory records exactly six current surfaces and exactly two blocking surface IDs: `message-submit-persistence` and `tenant-registration-persistence`.
- No secret, production data, generated build output, local runtime state, or agent credential is tracked.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty` unchanged.
- No Phase 03 obligation TODO is closed and `requirements-completed` remains empty.
