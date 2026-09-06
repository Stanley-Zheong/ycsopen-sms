---
phase: 03-crypto-storage-bootstrap
plan: "27"
subsystem: signed-migration-pair-admission
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-004]
requirements-completed: []
completion-metric: scoped_todo_empty
task-commit: e4489a2
---

# Phase 03 Plan 27: Signed migration-pair admission

Writer and encrypted-snapshot manifests now cross one bounded, canonical, role-separated Ed25519 verification boundary and one atomic persisted pair decision. Neither role exposes an independently accepted result.

## Implemented contract

- Added closed `ycs-writer-fence/v1` and `ycs-encrypted-snapshot/v1` schemas with bounded strings, writer/chunk arrays, per-chunk sizes, total snapshot sizes and no unknown fields.
- Added canonical regular-file reads with symlink, path, size, duplicate-key and noncanonical JSON rejection.
- Added one pair digest plus role byte `0x01` writer and `0x02` snapshot signatures under the same configured signer version.
- Added exactly-one-ACTIVE trust configuration. RETIRING signers may only reverify their exact accepted tuple within their sequence ceiling; RETIRED, REVOKED and unknown signers fail closed.
- Added in-memory and JDBC atomic stores. Identical tuples are idempotent; a higher ACTIVE tuple advances by CAS; same-sequence changes, stale competitors and half-pair attempts are rejected.
- Added ordered chunk, totals, signature, subject, replay, trust-rollout, compromise-recovery and concurrent one-winner tests.

## Verification

- `mvn -f core/pom.xml -DskipTests compile` — PASS.
- `mvn -f core/pom.xml -Dtest='MigrationPreflightTest,SignedMigrationManifestVerifierTest' test` — PASS, 13 tests.
- Affected migration suite (`ProtectedDataManifestTest`, `LegacyValueClassifierTest`, `MigrationPreflightTest`, `SignedMigrationManifestVerifierTest`) — PASS, 22 tests.
- Both JSON schemas parse successfully with `jq`; focused source scans contain no stub, unbounded production read, logging or dynamic sensitive-error path.
- JDBC race verification leaves exactly one complete database tuple and no writer-only or snapshot-only result.

## Iteration findings

- The resumed checkpoint compiled after two previously recorded type/exception fixes.
- Two over-limit chunk fixtures correctly fail at the closed canonical-schema gate, while missing, duplicate, reordered, post-final and total-mismatch fixtures fail at the inventory gate. Tests now lock those distinct failure classes.
- H2 MySQL mode does not provide MySQL `HEX`/`UNHEX`; the JDBC test registers exact test-only aliases rather than changing production MySQL SQL.

## Boundary

Migration execution, encrypted snapshot creation/restore and canonical obligation evidence remain owned by later Phase 03 plans. All Phase 03 obligation rows and `requirements-completed` remain open; the authoritative scoped TODO is not empty.

## Self-check

- Task commit `e4489a2` contains exactly the six declared implementation/test/schema files.
- Focused and affected migration tests pass.
- No Phase 03 TODO, obligation or requirement was closed.
- `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty` remain unchanged.
