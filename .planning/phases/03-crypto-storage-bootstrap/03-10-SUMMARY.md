---
phase: 03-crypto-storage-bootstrap
plan: "10"
subsystem: protected-persistence-lookup
tags: [java-21, mysql, connector-j, pkcs11, softhsm, ycse, blind-index]

requires:
  - phase: 03-crypto-storage-bootstrap-09
    provides: Transactional protected message adapter and metadata writer
  - phase: 03-crypto-storage-bootstrap-26
    provides: Live message submission adoption of the protected writer
provides:
  - Checkpoint-aware ACTIVE and RETIRING blind-index lookup owner
  - Opaque pre-COMPLETE legacy lookup capability with one package-scoped digest reader
  - Real Connector/J and SunPKCS11 protected persistence proof
  - Repository-wide HashUtil removal and semantic mobile raw-SHA writer fence
affects: [03-29-tenant-registration-writer, 03-30-phase-seal]

tech-stack:
  added: []
  patterns:
    - Metadata-first equality lookup with exact checkpoint-gated legacy union
    - Opaque in-memory compatibility capability with no public raw accessor
    - Child-process real-service proof with hashes-and-counts-only durable output

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexLookupService.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/LegacyMobileHashReader.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/LegacyMobileLookupToken.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexLookupServiceTest.java
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ProtectedPersistenceIntegrationTest.java
  modified:
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapter.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/PreparedMessageMobile.java
    - core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java
    - core/src/main/java/com/ycsopen/sms/core/service/routing/BlacklistChecker.java
    - core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingContext.java
    - core/src/main/resources/security/protected-data-inventory.json
  removed:
    - core/src/main/java/com/ycsopen/sms/core/common/security/HashUtil.java

key-decisions:
  - "LegacyMobileLookupToken is an opaque, redacted, defensive, non-serializable in-memory capability; only LegacyMobileHashReader may materialize its digest for a pre-COMPLETE legacy query."
  - "Blind-index lookup validates the exact ACTIVE and RETIRING key set, uses one explicitly typed derived union, deduplicates by bound row and fails closed on unknown state, orphan or conflicting binding."
  - "For an 11-byte mobile and the fixed field-kek.v1 reference, the physical YCSE envelope is deterministically 136 bytes; 156 bytes is the complete-envelope capacity bound for a maximum-length key reference, and actual length must not exceed it."
  - "MOBILE_PORTABILITY remains schema-only: the integration proof uses real JDBC metadata and legacy queries and does not invent a Java owner."
  - "No Phase 03 obligation closes here; the authoritative scoped TODO remains nonempty."

patterns-established:
  - "Compatibility reads are metadata-first; raw-SHA compatibility is reachable only through one package-scoped reader while the exact target permits fallback."
  - "Real persistence evidence reads raw binary cells through Connector/J and emits only fixed identities, hashes and counts."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 10: Protected Persistence Boundary Summary

**Protected message persistence now has one metadata-first equality-query owner and a real MySQL/SoftHSM proof that the live writer stores binary YCSE plus versioned HMAC metadata without a phone plaintext or raw-SHA write.**

## Accomplishments

- Added `BlindIndexLookupService` as the sole blacklist equality-query owner. It reads the exact target checkpoint, validates the complete ACTIVE/RETIRING key set, performs one typed derived-union metadata query, rejects missing keys and orphan/conflicting bindings, deduplicates multi-version hits, and preserves tenant-whitelist, system-blacklist, then tenant-blacklist precedence.
- Added an opaque `LegacyMobileLookupToken`. It is created only while the normalized phone remains transient in `MessageTaskProtectionAdapter`, clones all internal state, redacts `toString`, is ignored by Jackson, implements no serialization contract, and exposes no public bytes, string or target-index accessor.
- Restricted raw-SHA compatibility materialization to package-scoped `LegacyMobileHashReader`. The lookup service can call it only before the exact target is `COMPLETE` and only when fallback remains allowed; a completed target never invokes it.
- Removed `HashUtil.java` after the repository-wide Java/Kotlin/Groovy symbol audit found no remaining caller or definition dependency.
- Proved the production Spring transaction proxy, `MessageTaskProtectionAdapter`, Spring Data/Hibernate, Connector/J, real MySQL, production SunPKCS11 adapter and source-verified SoftHSM in one opt-in integration lane.
- Proved real raw rows contain a valid binary YCSE envelope, a random 64-hex non-queryable locator rather than the phone SHA-256, and one canonical 53-character message index for the active write key.
- Proved blacklist ACTIVE/RETIRING union behavior before completion and metadata-only behavior after completion. Proved the schema-only portability target with real legacy and metadata SQL without inventing a Java repository/service owner.
- Proved message, tenant and field AAD swaps fail through the same sanitized boundary; repository metadata failure rolls back the task; a real SoftHSM key-attribute outage fails closed; no failure path leaves a partial task.

## Task Commits

1. **Task 1: Implement metadata-first blind-index lookup compatibility** — `0cb2ccb`
2. **Task 2: Execute raw-row persistence and compatibility proof** — `f27f3d9`

The Task 1 deletion of `HashUtil.java` is intentional. Task 2 changes `MessageTaskProtectionAdapter` only from final to non-final so Spring can create the transaction proxy required by its existing `@Transactional` boundary; constructors, methods, transaction behavior and security semantics are unchanged.

## Physical Boundary Evidence

| Boundary | Evidence | Result |
| --- | --- | --- |
| MySQL identity | Digest-locked MySQL fixture; child reports a 64-hex hash of server version plus database identity | PASS |
| PKCS11 identity | Source-verified SoftHSM 2.7.0 handoff and a 64-hex runtime token identity hash | PASS |
| Stored mobile | Connector/J returns binary bytes beginning with `YCSE`; no generated 11-digit canary subsequence exists | PASS |
| Current envelope length | Fixed `field-kek.v1` reference plus 11-byte mobile encodes to deterministic 136 bytes | PASS |
| Capacity contract | A maximum-length key reference produces the 156-byte complete-envelope bound; current 136-byte value is within that bound | PASS |
| Legacy locator | Exactly 64 lowercase hex characters, generated from random entropy, unequal to the raw phone SHA-256 | PASS |
| Message index | Exactly one active-write metadata row with a canonical 53-character versioned HMAC | PASS |
| Compatibility lookup | Two ACTIVE/RETIRING metadata rows; pre-COMPLETE legacy union and post-COMPLETE metadata-only hits are equivalent | PASS |
| Failure atomicity | AAD swaps, metadata insert failure and real token operation failure return sanitized errors and leave no partial task | PASS |
| Durable output | One line containing only hashed MySQL/SoftHSM/PKCS11 identities, row/index counts and assertion count | PASS |

The integration lane records `rows=1`, `message_indexes=1`, `blacklist_indexes=2`, `portability_indexes=2`, and `assertions=23`. It never prints the phone canary, ciphertext, PIN, credentials, key alias, module path or fixture coordinates.

## Lookup and Legacy Boundary

- `RoutingContext` carries only ordered HMAC query values and the opaque legacy capability. It carries neither phone plaintext nor a raw-SHA string scalar.
- `BlacklistChecker` passes the capability unchanged to `BlindIndexLookupService`; the checker cannot inspect the historical digest.
- `LegacyMobileHashReader` is package-scoped and is the only production class that can obtain a defensive digest copy and format it for the legacy repository projection.
- `COMPLETE` with fallback enabled, an unknown checkpoint state, an incomplete/extra key set, an orphan metadata row, a status mismatch, or conflicting duplicate binding all fail with `BLIND_INDEX_LOOKUP_FAILED`.
- Explicit `CAST(? AS DECIMAL(20, 0))` and `CAST(? AS CHAR(53))` parameters pass both the H2 unit lane and the real MySQL lookup lane.
- The broad plan-era `sha256Hex` literal scan is intentionally replaced by a semantic fence. It rejects every `HashUtil` reference/definition and every mobile raw-SHA production call in current message/routing/write surfaces while retaining the unrelated private S3 ciphertext-checksum helper from Plan 03-15.

## Automated Checks

- `mvn -f core/pom.xml -Dtest='MessageSubmitServiceTest,RoutingEngineTest,CurrentProtectedReaderFenceTest,MessageTaskProtectionAdapterTest,BlindIndexLookupServiceTest' test` — PASS; 21 tests.
- `mvn -f core/pom.xml test` — PASS; 150 tests, with 11 explicitly opt-in integration cases skipped by their profile gates.
- `mvn -f core/pom.xml -Pphase03-integration -Dtest=Phase03ProtectedPersistenceIntegrationTest test` — PASS; one real-service test and no skipped case.
- `/usr/bin/env ruby .planning/tools/validate-phase-03-protected-inventory.rb --manifest core/src/main/resources/security/protected-data-inventory.json --schema core/src/main/resources/db/migration/V1__init_schema.sql --source-root core/src/main/java` — PASS; six exact current surfaces and one still-blocking tenant-registration surface.
- `/usr/bin/env ruby scripts/lib/phase-03/service_checks.rb assert-clean --all` — PASS after the real lane.
- Repository-wide `HashUtil` zero-reference/deletion fence, semantic mobile raw-SHA writer fence, Java compilation and `git diff --check` — PASS.

## Decisions Made

- The current physical 136-byte value and 156-byte maximum are both locked. The maximum is a capacity ceiling, not a fixed row length; changing the production key reference merely to manufacture a 156-byte row would break the admitted PKCS11 descriptor contract.
- Portability evidence remains raw JDBC because the target has no current Java owner. The test proves legacy/metadata parity and checkpoint behavior without adding a fictional repository or service.
- Child Spring properties are passed only to the isolated proof process at command-line property precedence. They do not mutate the parent Maven JVM or later tests.
- The child suppresses only Spring startup stdout. The final durable line remains visible to the parent assertion and contains only admitted hashes and counts.
- Every Phase 03 obligation remains open. This plan supplies implementation and prerequisite evidence but not the canonical obligation evidence, independent review or delivery attestation needed for closure.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added an opaque cross-plan legacy lookup capability**

- **Found during:** Task 1 after Plan 03-26 removed all raw-SHA routing scalars.
- **Issue:** A checkpoint-gated legacy query could no longer be computed without either restoring an unsafe raw string to `RoutingContext` or retaining phone plaintext beyond preparation.
- **Fix:** Added `LegacyMobileLookupToken` and minimally passed it through prepared message, submission and routing objects. Only `LegacyMobileHashReader` can access a defensive digest copy.
- **Committed in:** `0cb2ccb`.

**2. [Rule 3 - Blocking] Typed the derived union for both H2 and MySQL**

- **Found during:** Task 1 focused unit verification.
- **Issue:** H2 could not infer untyped derived-union parameter columns.
- **Fix:** Added explicit DECIMAL and CHAR casts. H2 focused tests and the real MySQL integration both execute the same production lookup successfully.
- **Committed in:** `0cb2ccb`.

**3. [Rule 1 - Bug] Made the existing transactional adapter proxyable**

- **Found during:** Task 2 real Spring context startup.
- **Issue:** The class was final while `save` was `@Transactional`, so Spring could not create its CGLIB proxy and the production-enabled context could not start.
- **Fix:** Removed only the class-level final modifier. The real integration now starts the proxy and exercises the existing transaction boundary.
- **Committed in:** `f27f3d9`.

**4. [Rule 1 - Bug] Corrected fixed-length versus capacity semantics**

- **Found during:** Task 2 raw Connector/J row inspection.
- **Issue:** The plan treated the 156-byte maximum as every phone envelope's fixed length. The encoded length also includes the actual key-reference length.
- **Fix:** Locked the current `field-kek.v1`/11-byte-mobile value to 136 bytes, separately locked the maximum complete-envelope capacity to 156 bytes, and asserted `actual <= maximum` without printing ciphertext.
- **Committed in:** `f27f3d9`.

**5. [Rule 3 - Blocking] Closed the child-context fixture configuration**

- **Found during:** Task 2 real context startup.
- **Issue:** Host datasource variables outranked default child properties, Flyway attempted `${var}` replacement in a schema comment, and Hibernate validation rejected a legacy `CHAR(64)` column represented as an opaque String projection.
- **Fix:** Passed isolated child properties at command-line precedence, disabled Flyway placeholder replacement, and let Flyway own real-schema validation while Hibernate uses `ddl-auto=none`. These settings exist only in the child proof context.
- **Committed in:** `f27f3d9`.

## Known Stubs

- `ThirdPartyBlacklistClient` remains the pre-existing later-integration stub. It receives only the current opaque HMAC value and does not log it; this plan does not claim the external service is implemented.
- `tenant-registration-persistence` remains the single blocking current-surface inventory row and is owned by Plan 03-29.

Neither stub prevents this plan's lookup-owner and physical message-persistence proof, and neither is represented as complete.

## Threat Surface Review

- **Information disclosure:** no canary, raw phone SHA, ciphertext, key alias, PIN, credential or fixture path appears in durable proof output. Raw cell checks compare only lengths, magic bytes and boolean absence.
- **Tampering:** copied bytes fail under message, tenant and field context swaps through one sanitized error class/message.
- **Spoofing:** the fixture binds admitted MySQL image identity, source-verified SoftHSM handoff and runtime PKCS11 identity hashes.
- **Partial state:** metadata/repository failure occurs inside the same transaction and leaves no task; provider failure occurs before persistence.

The native test helper is compiled only beneath the run-owned `core/target/phase03/services` fixture directory, uses fixed arguments, emits no credentials, and is removed by exact fixture cleanup. No production endpoint, schema object, secret-bearing configuration or new external network surface was added.

## Remaining Scoped TODO State

The authoritative Phase 03 TODO file still contains 22 open rows. The rows covering `OBL-CRYPTO-STORAGE-001` and `OBL-CRYPTO-STORAGE-003` remain open because their canonical evidence files, remaining protected surfaces, review gates and delivery attestation are not complete. `requirements-completed` therefore remains empty and the scoped TODO is not empty.

## Self-Check: PASSED

- All five created files, all declared modified files, and this summary exist; `HashUtil.java` is absent.
- Task commits `0cb2ccb` and `f27f3d9` exist on `phase/03-crypto-storage-bootstrap`.
- Focused tests, full backend tests, the final real MySQL/SoftHSM lane, inventory validation, semantic source fences and exact service cleanup pass.
- `STATE.md` still preserves `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; the scoped TODO query remains the sole completion metric.
- All Phase 03 obligation rows and requirement completion markers remain open.
