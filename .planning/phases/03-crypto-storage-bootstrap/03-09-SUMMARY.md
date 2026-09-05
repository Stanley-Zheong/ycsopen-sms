---
phase: 03-crypto-storage-bootstrap
plan: "09"
subsystem: protected-message-persistence
tags: [java-21, spring-transaction, jpa, jdbc, ycse-v1, blind-index]

requires:
  - phase: 03-crypto-storage-bootstrap-05
    provides: Opaque version-aware BlindIndexPort and redacted VersionedBlindIndex values
  - phase: 03-crypto-storage-bootstrap-08
    provides: Hidden binary JPA mapping pattern for protected VARBINARY fields
  - phase: 03-crypto-storage-bootstrap-11
    provides: V1200 key-reference and per-version blind-index metadata schema
  - phase: 03-crypto-storage-bootstrap-24
    provides: Strict context-bound YCSE/v1 ProtectedFieldCodec
provides:
  - Sole context-bound prepare/save owner for protected message mobile persistence
  - Binary MessageTask YCSE mapping with unforgeable adapter-owned assignment
  - Same-transaction task and ACTIVE/RETIRING blind-index metadata persistence
  - Executable identity, dependency, capacity and rollback failure contract
affects: [03-10-real-mysql-persistence, 03-12-migration-admission, 03-26-message-writer-adoption]

tech-stack:
  added: []
  patterns:
    - Prepare key-dependent values before routing or database mutation
    - Persist the legacy row and every write-compatible key version in one transaction
    - Bind polymorphic metadata through an in-transaction row-existence check and original-row digest

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapter.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/PreparedMessageMobile.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexMetadataRepository.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapterTest.java
  modified:
    - core/src/main/java/com/ycsopen/sms/core/domain/entity/MessageTask.java
    - core/src/main/java/com/ycsopen/sms/core/repository/MessageTaskRepository.java

key-decisions:
  - "PreparedMessageMobile is the unforgeable, defensive and redacted handoff between prepare, opaque routing and the adapter-owned save operation."
  - "The required legacy mobile_hash cell receives a random 32-byte lowercase-hex locator; mobile HMAC values remain separate V1200 rows selected against ACTIVE/RETIRING key metadata."
  - "The existing MessageSubmitService String setters compile only as fail-closed seams until Plan 03-26 rewires the caller; they perform no character conversion or persistence."

patterns-established:
  - "Message AAD: database-field / crypto-storage-bootstrap / message_tasks / mobile_encrypted / tenant:<id> / message_id=<id>."
  - "Blind-index context: MESSAGE_TASK / mobile / mobile-routing / tenant:<id>."
  - "Atomic write: prepare outside DB mutation, then entity flush, row-binding check and one metadata insert per write version inside one transaction."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 09: Protected Message Persistence Owner Summary

**Message mobile persistence now has one tenant/message-bound YCSE preparation and transactional save owner that stores binary envelopes, non-queryable legacy locators, and separate per-version HMAC metadata without plaintext or raw-mobile-SHA conversion.**

## Accomplishments

- Added `MessageTaskProtectionAdapter.prepare` with strict positive tenant identity, generated message-ID grammar and normalized 11-digit mobile validation.
- Bound protection to the exact database-field context `message_tasks.mobile_encrypted`, tenant scope and immutable `message_id=<id>` resource identity.
- Obtained both ordered `mobile-routing` write and query HMAC sets before any database mutation and required every write value to remain query-compatible.
- Represented `MessageTask.mobile_encrypted` as hidden `byte[]`, excluded both protected columns from Jackson/Lombok access, and required an adapter-only assignment permit.
- Stored one random 64-character locator in required legacy `mobile_hash`; no new write stores a mobile SHA-256 or packs multiple values into one legacy cell.
- Added V1200-aware JDBC metadata insertion that derives each persisted `index_status` from the exact `MOBILE_BLIND_INDEX` key-version row and admits only `ACTIVE` or `RETIRING`.
- Kept entity flush, same-transaction existence proof, original-row binding digest and every versioned metadata insert inside one rollback boundary.
- Added focused H2/JPA/JDBC tests that inspect raw binary cells and prove no partial task or index survives identity, capacity, dependency or metadata failure.

## Task Commit

1. **Task 1: Implement message protection adapter and binary representation** — `8ed333e`

## Persistence Contract Evidence

| Contract | Executable result |
| --- | --- |
| Plaintext input | Exactly 11 validated ASCII digits are copied into a bounded byte array and cleared after protection; no ciphertext/String conversion exists |
| YCSE context | `database-field`, `crypto-storage-bootstrap`, `message_tasks`, `mobile_encrypted`, `tenant:17`, `message_id=MSG_...` |
| Envelope representation | Strict YCSE/v1 bytes in `MessageTask.mobileEncrypted`; the 32-byte test key reference exercises the maximum 156-byte mobile envelope |
| Legacy cell | Random 32-byte entropy encoded as exactly 64 lowercase hexadecimal characters; asserted unequal to the historical raw mobile SHA-256 |
| Blind-index context | `MESSAGE_TASK`, `mobile`, `mobile-routing`, tenant scope |
| Version consistency | Prepared write indexes must be contained in the ordered query set; persisted rows select the same key version and current `ACTIVE`/`RETIRING` status from V1200 metadata |
| Polymorphic binding | Insert occurs only after exactly one `message_tasks` row matches ID, tenant, message ID and locator in the same transaction |
| Atomic failure | Missing version state after an earlier index insert rolls back that insert and the flushed task together; sanitized failure exposes no binary/index/provider detail |
| Ownership | Only the adapter holds the assignment permit and calls `saveAndFlush`; arbitrary callers cannot construct a prepared value or assign raw protected bytes |

## Automated Checks

- `mvn -f core/pom.xml -Dtest=MessageTaskProtectionAdapterTest test` — PASS; 4 focused tests, no failure, error or skip.
- `mvn -f core/pom.xml test` — PASS; 144 tests, no failure or error. Ten existing real-service tests remained behind their explicit opt-in gates.
- Plan-file source scan for UTF-8 ciphertext conversion, `new String`, `HashUtil` and `sha256Hex` — PASS; no prohibited path in the protected persistence implementation.
- Ownership scan — PASS; the adapter is the only production caller of protected assignment, `saveAndFlush` and metadata insertion in the declared plan files.
- `git diff --check` — PASS.

## Decisions Made

- Preparation is intentionally separate from save so Plan 03-26 can prepare before routing, expose only the ordered opaque query set, discard on rejection, and save only through this adapter.
- The entity accepts protected state only with a nested permit whose constructor is private to the adapter. The public compatibility setters required by the not-yet-rewired service throw before conversion or persistence.
- Metadata status is not inferred from index ordering. Each row is inserted with `INSERT ... SELECT` against the exact V1200 purpose/version and its current `ACTIVE` or `RETIRING` state.
- The 32-byte `original_row_digest` hashes a domain-separated non-plaintext binding over tenant, generated row ID, immutable message ID, locator and envelope. It is not a raw mobile digest.

## Deviations from Plan

None — implementation and tests remain inside the six declared task files.

## Issues Encountered

- `MessageSubmitService` still contains the historical plaintext/raw-hash calls owned by Plan 03-26. This plan converts those entity methods into fail-closed compile seams rather than modifying a file outside its declared ownership or describing caller adoption as finished.

## Known Stubs

None. The fail-closed legacy setters are a named downstream compatibility boundary, not a working plaintext fallback or completion evidence.

## Threat Surface Review

- **Tampering:** tenant, logical owner/table/field and immutable message identity are authenticated in YCSE AAD; a prepared value cannot be saved against a different tenant or message ID.
- **Denial of service / partial state:** key, index, entity or metadata failure produces no committed task/index pair, and a metadata failure after one successful insert rolls the entire unit back.
- **Information disclosure:** protected entity bytes have no raw getter/setter or JSON surface, prepared/index renderings are redacted, and persistence exceptions have one stable message with no cause.

No endpoint, authentication path, filesystem access, network connection or schema object was added. The planned trust-boundary change is the V1200 JDBC metadata writer, whose exact transaction and rollback behavior is covered.

## Verification Boundary

- Focused persistence behavior is proven with H2, Hibernate and Spring transactions. Plan 03-10 still owns real Connector/J/MySQL plus production SunPKCS11/SoftHSM raw-row and context-swap evidence.
- Plan 03-26 still owns rewiring `MessageSubmitService`, routing only opaque query values, removing the direct raw-hash/new-write source path and transitioning the inventory writer disposition.
- The default suite does not execute the ten explicitly opt-in real-service tests; their absence is not represented as production evidence.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This plan provides the protected message persistence prerequisite only; `requirements-completed` stays empty until the canonical evidence, migration/leak, independent-review and delivery gates make the authoritative scoped TODO query empty.

## Self-Check: PASSED

- All six declared implementation/test files and this summary exist.
- Task commit `8ed333e` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused and full backend verification, ownership/prohibited-source scan, stub scan and diff check pass against the committed task.
- No secret, production data, generated output, local runtime state or agent credential is tracked.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; no time, duration, progress-bar or percentage metric was added.
- All Phase 03 obligation TODO rows remain open and `requirements-completed` remains empty.
