---
phase: 03-crypto-storage-bootstrap
plan: "08"
subsystem: protected-entity-persistence
tags: [java-21, jpa, hibernate, varbinary, jackson, lombok]

requires:
  - phase: 03-crypto-storage-bootstrap-01
    provides: Reviewed protected-data inventory and V1 capacity contract
  - phase: 03-crypto-storage-bootstrap-05
    provides: Opaque binary and redacted-value conventions for protected data
provides:
  - Hidden byte-array mappings for all six current non-message protected VARBINARY fields
  - Hidden opaque-object-ID mappings over the three current Tenant reference columns
  - Executable hydration, serialization, public-surface and unrelated-writeback regression fence
affects: [03-09-message-persistence, 03-10-real-mysql-persistence, 03-25-reader-fence, 03-29-tenant-registration]

tech-stack:
  added: []
  patterns:
    - Field-access JPA byte arrays with no generated raw getter or setter
    - Explicit JSON and string-rendering exclusion for protected entity state
    - ORM writeback regression using non-UTF-8 and NUL-bearing opaque bytes

key-files:
  created:
    - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/ProtectedEntityMappingTest.java
  modified:
    - core/src/main/java/com/ycsopen/sms/core/domain/entity/User.java
    - core/src/main/java/com/ycsopen/sms/core/domain/entity/Tenant.java
    - core/src/main/java/com/ycsopen/sms/core/domain/entity/BlacklistEntry.java
    - core/src/main/java/com/ycsopen/sms/core/domain/entity/TenantApiKey.java

key-decisions:
  - "Protected VARBINARY state is field-access-only byte[] data: Lombok raw accessors are disabled and no converter or hydration decryptor exists."
  - "Tenant object references remain mapped to immutable V1 column names but are represented internally as hidden *ObjectId fields, never returned URL properties."
  - "The three legacy Tenant URL-named methods remain deprecated write-only compile seams until Plan 29 installs the purpose-bound claim writer; they expose no read path and do not move fence ownership into this plan."

patterns-established:
  - "Opaque entity mapping: JsonIgnore plus Lombok getter/setter suppression and ToString exclusion on every protected field."
  - "Writeback proof: hydrate arbitrary bytes, change only an unrelated public field, flush, then compare the exact stored byte sequence."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 08: Protected Entity Mapping Summary

**Six current non-message `VARBINARY(255)` fields now hydrate as hidden opaque bytes, while Tenant evidence references are hidden object IDs and exact bytes survive unrelated ORM updates.**

## Accomplishments

- Converted `users.phone_encrypted`, three Tenant identity/contact cells, `blacklist_entries.mobile_encrypted`, and `tenant_api_keys.app_secret_encrypted` from `String` to `byte[]` without a global converter or hydration decryptor.
- Disabled generated raw getters and setters and added explicit `JsonIgnore` and string-rendering exclusion on every protected byte field.
- Recast the three currently mapped Tenant evidence-reference fields as hidden `businessLicenseObjectId`, `shortlinkDomainProofObjectId`, and `trademarkProofObjectId` values while preserving their immutable V1 column bindings.
- Preserved current source compatibility through deprecated write-only URL-named seams; no URL/object-ID getter exists, and Plan 29 remains the sole owner of request rejection, purpose binding, and atomic object claim.
- Added a JPA regression that uses NUL, `0xff`, and `0x80` bytes to prove binary hydration and exact writeback after only unrelated entity state changes.
- Added reflection and Jackson checks proving protected bytes and opaque object IDs do not enter public raw accessors, JSON, or current string rendering.

## Task Commit

1. **Task 1: Convert current protected entity fields to opaque hidden binary mappings** — `f23783c`

## Exact Mapping Evidence

| Entity | V1 column | Java representation | Public surface |
| --- | --- | --- | --- |
| `User` | `phone_encrypted` | hidden `byte[] phoneEncrypted` | no raw getter/setter; JSON/string excluded |
| `Tenant` | `legal_rep_id_no_encrypted` | hidden `byte[] legalRepIdNoEncrypted` | no raw getter/setter; JSON/string excluded |
| `Tenant` | `contact_id_no_encrypted` | hidden `byte[] contactIdNoEncrypted` | no raw getter/setter; JSON/string excluded |
| `Tenant` | `contact_phone_encrypted` | hidden `byte[] contactPhoneEncrypted` | no raw getter/setter; JSON/string excluded |
| `BlacklistEntry` | `mobile_encrypted` | hidden `byte[] mobileEncrypted` | no raw getter/setter; JSON/string excluded |
| `TenantApiKey` | `app_secret_encrypted` | hidden `byte[] appSecretEncrypted` | no raw getter/setter; JSON/string excluded |

The focused persistence test inserts byte sequences that are not valid implicit UTF-8 text, hydrates all four entities, verifies the in-memory fields byte-for-byte, changes only login count, trial usage, blacklist reason, or rate limit, flushes through Hibernate, and compares every stored cell byte-for-byte.

## Automated Checks

- `mvn -f core/pom.xml -Dtest=ProtectedEntityMappingTest test` — PASS; all focused mapping, public-surface, serialization and writeback checks pass.
- `mvn -f core/pom.xml test` — PASS; no failure or error. The existing real-service lanes remain opt-in and skipped by the default profile.
- Source scan for protected `String` declarations and generated raw getter names in the four entity files — PASS; no match.
- `git diff --check` — PASS.

## Decisions Made

- Field access is retained through the existing JPA `@Id` placement, so Hibernate binds raw bytes directly without a converter, listener, automatic decryption, or character encoding step.
- No public byte-array method was added. This removes both mutable-array exposure and accidental Jackson discovery rather than relying only on defensive copies at a public accessor.
- Password hashing behavior is untouched: `User.passwordHash` remains the Phase 5-owned `String` field with its existing access behavior.
- Existing V1 object-reference column names are not DDL contracts for URLs. The Java fields now describe opaque IDs and are hidden from responses; Plan 29 still owns eliminating the temporary legacy request writer and enforcing `pobj_v1_*` claim semantics.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- The first compile confirmed that the current `TenantService` still invokes the three historical URL-named setters. The entity retains those setters as deprecated write-only compatibility seams so this mapping plan neither breaks the current build nor preempts Plan 29's writer-fence ownership.

## Known Stubs

None. The deprecated Tenant methods are an explicit compatibility boundary with a named downstream owner, not completion evidence for registration protection or object claim.

## Threat Surface Review

- **Information disclosure:** protected byte fields and object IDs have no public getter and are explicitly excluded from Jackson and Lombok string rendering.
- **Tampering:** the persistence test proves unrelated updates rebind the exact byte arrays without text conversion, truncation, or replacement.
- **Elevation of privilege:** entity hydration performs no decryption and exposes no implicit plaintext accessor.

No endpoint, authentication path, schema object, network connection, file access path, or new trust boundary was introduced.

## Verification Boundary

This plan proves deterministic Hibernate/JPA mapping and writeback with the repository's H2 test profile. It does not claim real Connector/J/MySQL message-persistence evidence; Plan 10 owns that real-service boundary after Plans 07, 09, 11, and 25.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. `OBL-CRYPTO-STORAGE-001` still requires the protected writers/readers, real MySQL evidence, migration/leak evidence, canonical exact-four production, independent review, and the authoritative scoped TODO query.

## Self-Check: PASSED

- All five declared implementation/test files and this summary exist.
- Task commit `f23783c` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused verification, complete backend verification, forbidden-source scan, stub scan and diff check pass against the committed task.
- No secret, production data, local runtime state or generated build output is tracked.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`.
- All Phase 03 obligation TODO rows remain open and `requirements-completed` remains empty.
