---
phase: 03-crypto-storage-bootstrap
plan: "13"
subsystem: protected-data-migration
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-004]
requirements-completed: []
completion-metric: scoped_todo_empty
task-commit: 246b570
---

# Phase 03 Plan 13: Protected-data migration command

Implemented an explicit, bounded migration command and transactional runner for the seven reviewed targets.

## Result

- Closed CLI contract and byte-golden help for `preflight/start/resume/pause/abort/status`; mutation requires the accepted pair digest and emits sanitized output only.
- Row update, blind-index metadata, outcome and checkpoint share one transaction; restart validates existing protected state without plaintext rollback.
- Numeric-PK targets use stable cursors. `mobile_portability.mobile_hash` uses bounded rescans, exact original-cell digests and random 63-bit binding IDs, so no locator derives from the raw SHA value.
- Scrubbed portability locators contain the random binding ID plus fresh randomness; collisions exhaust a bounded retry and fail closed.

## Verification

- Compile passed.
- `ProtectedDataMigrationRunnerTest` and `ProtectedDataMigrationCommandTest`: 26 tests passed.
- The combined migration/tenant affected suite passed; JSON contracts parse and source scans found no raw-hash prefix locator.

## Boundary

The command services are production-injectable, but the non-help launcher composition and real MySQL migration/recovery proof remain owned by Plan 14. No Phase 03 obligation TODO is closed by this unit result.
