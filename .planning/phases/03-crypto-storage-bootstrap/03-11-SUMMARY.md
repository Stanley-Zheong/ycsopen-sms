---
phase: 03-crypto-storage-bootstrap
plan: "11"
subsystem: database
tags: [mysql, flyway, cryptographic-metadata, optimistic-locking, integration-testing]

requires:
  - phase: 03-crypto-storage-bootstrap/03-01
    provides: immutable V1 checksum, protected-data inventory, and V1200-V1299 owner allocation
provides:
  - V1200 Phase-owned key, blind-index, object/session and migration metadata
  - Database-enforced stored-shape constraints plus optimistic compare-and-set mutation contracts
  - Real MySQL proof of immutable V1 followed by validated V1200
affects: [03-06, 03-07, 03-09, 03-10, 03-12, 03-13, 03-14, 03-16, 03-20, 03-27, 03-28]

tech-stack:
  added: []
  patterns:
    - expand-only owner-scoped Flyway migration
    - purpose-and-version composite foreign keys
    - bounded optimistic compare-and-set reservation

key-files:
  created:
    - core/src/main/resources/db/migration/V1200__create_crypto_storage_metadata.sql
    - core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationIntegrationTest.java
  modified:
    - .planning/phases/03-crypto-storage-bootstrap/SCHEMA-CLAIMS.md
    - .planning/phases/03-crypto-storage-bootstrap/DESIGN.md

key-decisions:
  - "V1200 uses CHECK, UNIQUE and composite foreign-key constraints with optimistic/current-state/current-count CAS writes; it does not require trigger-creation privileges on binlog-enabled MySQL."
  - "The seven migration targets are materialized as reviewed metadata, including two protected no-index fields and one schema-only migratable digest."

patterns-established:
  - "Exact-purpose digest references: capability and registration digests foreign-key both purpose and version."
  - "Inseparable manifest admission: writer and snapshot digests occupy one non-null singleton row guarded by global sequence and optimistic version."

requirements-addressed:
  - REQ-NFR-DATA-PROTECTION
  - OBL-CRYPTO-STORAGE-002
  - OBL-CRYPTO-STORAGE-003
  - OBL-CRYPTO-STORAGE-004
requirements-completed: []
completion_metric: scoped_todo_empty
completed: 2026-09-01
---

# Phase 03 Plan 11: Crypto Storage Expand Schema Summary

**V1200 adds twelve Phase-owned metadata tables for bounded key reservations, per-version blind indexes, inseparable manifest admission, migration checkpoints, and staged protected-object sessions, proven against fresh MySQL 8.4 without changing V1.**

## Accomplishments

- Created only `ycs_crypto_*` objects in the registered V1200-V1299 namespace; V1 and every legacy table remain unchanged.
- Enforced key purpose/state, 1,048,576 wrap ceiling, 53-character ASCII-bin blind indexes, exact target/version uniqueness and composite key-reference foreign keys.
- Recorded all five digest-index targets, the schema-only risk-log target, and both mandatory protected no-index mobile targets.
- Added one global non-null writer/snapshot admission tuple with unsigned sequence, signer identity, subject and role/pair digests, plus pair-bound run/checkpoint/lease/event metadata.
- Added OPEN/CLAIMED/CLOSED/EXPIRED registration sessions, exact-purpose upload credential digests, per-purpose/session attempt ceilings, purpose-current replacement, single-claim and reconciliation metadata.
- Proved fresh V1→V1200 migration, Flyway validation, metadata shape, cross-purpose/version rejection, concurrent wrap and manifest CAS, concurrent upload ceilings, terminal-session rejection and legacy metadata-lock independence on real MySQL.

## Task Commit

1. **Task 1: Add V1200 expand schema and real-MySQL proof** — `c9d29ec` (`feat`)

## Files Created/Modified

- `core/src/main/resources/db/migration/V1200__create_crypto_storage_metadata.sql` — expand-only P03 metadata, constraints, indexes, foreign keys and target declarations.
- `core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationIntegrationTest.java` — fixed-digest MySQL 8.4/Flyway and concurrency proof.
- `.planning/phases/03-crypto-storage-bootstrap/SCHEMA-CLAIMS.md` — exact twelve-table physical ownership declaration and next-version statement.
- `.planning/phases/03-crypto-storage-bootstrap/DESIGN.md` — deployable CAS/constraint model and no-SUPER boundary.

## Schema and Flyway Evidence

- Registered owner/range: `crypto-storage-bootstrap`, `V1200-V1299`.
- Applied history on a fresh schema: versions `1`, then `1200`; Flyway reported successful validation of both migrations.
- Immutable V1 SHA-256: `fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9`.
- V1200 SHA-256: `38505fcf86ac9b81920c3bbaca919608c2b01582931a39b08fded83e480e6464`.
- Next owner-scoped version: `V1201`, selector result `PASS`.
- The real test verified exactly twelve `ycs_crypto_*` tables, zero P03 triggers, the declared blind-index keys/collation, seven exact target rows and zero prohibited metadata column names.

## Verification

- `python3 skills/flyway-migration/scripts/next_flyway_version.py --owner crypto-storage-bootstrap --check V1201` — PASS.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — PASS.
- `mvn -f core/pom.xml -Pphase03-integration -Dtest='Phase01MySqlIntegrationTest,Phase03MigrationIntegrationTest#appliesV1200WithoutChangingV1' test` — PASS. The Phase03 test executed the real MySQL lane and independently asserted V1; the two Phase01 tests remained skipped because only the Phase03 opt-in property was enabled.
- `mvn -f core/pom.xml test` — PASS with no failure or error. The eight real-service tests are intentionally opt-in and skipped in the ordinary suite.
- `git diff --check` — PASS.

## Decisions Made

- Normal Flyway application users must be able to apply V1200 on binlog-enabled MySQL. Stored shapes are enforced by CHECK/UNIQUE/FK constraints; monotonic reservations and state transitions use optimistic/current-state/current-count compare-and-set predicates in one transaction, without triggers or stored routines.
- The global manifest admission is one complete singleton tuple. Exact re-verification is a no-op predicate match; only a higher sequence and current optimistic version can win an update.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Made the Phase03 test explicitly preserve the literal V1 template marker**

- **Found during:** Task 1 real-MySQL verification.
- **Issue:** The Phase03 Spring test context attempted Flyway placeholder replacement for V1's literal `${var}` comment before any migration ran.
- **Fix:** Set `spring.flyway.placeholder-replacement=false` through the test's dynamic properties, preserving V1 unchanged.
- **Files modified:** `core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationIntegrationTest.java`.
- **Verification:** Fresh real MySQL applied V1 and V1200 and Flyway validation passed.
- **Committed in:** `c9d29ec`.

**2. [Rule 3 - Blocking] Removed the trigger privilege dependency**

- **Found during:** Task 1 real-MySQL verification.
- **Issue:** Binlog-enabled MySQL rejected `CREATE TRIGGER` for the normal migration user with error 1419 because it lacked `SUPER`; retaining triggers would make V1200 undeployable.
- **Fix:** Kept stored-shape enforcement in CHECK/UNIQUE/FK constraints and made all mutation proofs use bounded optimistic/current-state/current-count CAS statements. V1200 creates no trigger or stored routine.
- **Files modified:** `core/src/main/resources/db/migration/V1200__create_crypto_storage_metadata.sql`, `core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationIntegrationTest.java`, `.planning/phases/03-crypto-storage-bootstrap/SCHEMA-CLAIMS.md`, `.planning/phases/03-crypto-storage-bootstrap/DESIGN.md`.
- **Verification:** Fresh real MySQL applied V1200 as the normal application user; concurrent ceiling/replay tests and the full plan command passed.
- **Committed in:** `c9d29ec`.

**Total deviations:** 2 auto-fixed blocking issues. Both were required for a deployable and truthful real-MySQL verification boundary; no legacy DDL or cross-owner object was added.

## Issues Encountered

- A real-MySQL assertion initially compared Connector/J's unsigned numeric representation to a Java `Long` inside a raw map. The test now requests typed values and passed unchanged database behavior.
- Flyway warns that its current release has formally tested MySQL through 8.1 while the repository-pinned runtime is MySQL 8.4.11. Migration and validation both passed on the pinned runtime; the warning is retained as an evidence boundary, not relabeled as certification.

## Known Stubs

None. This plan supplies the expand schema and its physical proof. Production consumers remain owned by later plans and all three obligation TODO rows named by this plan remain open.

## Completion Metric

The Phase 03 scoped TODO set is not empty. `OBL-CRYPTO-STORAGE-002`, `OBL-CRYPTO-STORAGE-003`, and `OBL-CRYPTO-STORAGE-004` remain unchecked; this plan does not claim obligation or phase completion.

## Next Plan Readiness

V1200 consumers may now execute in their declared later waves. No consumer may bypass the exact purpose/version foreign keys, optimistic CAS predicates, migration target registry or session terminal-state checks.

## Self-Check: PASSED

- All four declared task files exist.
- Task commit `c9d29ec` exists in git history.
- V1 SHA-256 remains the locked value and V1200 is the only new migration.
- Plan verification and the required backend regression both passed.

---
*Phase: 03-crypto-storage-bootstrap*
*Plan: 03-11*
