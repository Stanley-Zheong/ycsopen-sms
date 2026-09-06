---
phase: 03-crypto-storage-bootstrap
plan: "14"
subsystem: migration-and-encrypted-snapshot-recovery
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-004]
requirements-completed: []
completion-metric: scoped_todo_empty
task-commits: [53475e2, 1c11164]
---

# Phase 03 Plan 14: Migration and encrypted snapshot recovery

## Delivered

- Added the production encrypted MySQL snapshot service: fixed 10 MiB plaintext chunks, ordered canonical YCSE envelopes, atomic ciphertext-only storage, complete-inventory authentication and bounded direct restore into a fresh schema.
- Added an independent `SNAPSHOT_RECOVERY` PKCS11 key purpose, reference, reservation count and cross-purpose rejection.
- Added the production `advance` migration command and a PKCS11 historical-digest bridge. Legacy lowercase SHA-256 cells become the same domain-bound HMAC indexes used by online mobile lookup without recovering the mobile number.
- Executed all seven reviewed targets: five indexed targets reached HMAC-only `COMPLETE`; bulk/uplink reached `COMPLETE` without index rows. Signed-preflight rejection, idempotence, rotation, concurrency rollback/retry, classification faults and V1 immutability were exercised.
- Added strict counts/digests-only `--mysql-evidence` validation with destructive omission, no-index and secret-field fixtures.

## Verification

- `Phase03MigrationIntegrationTest`: 2/2 PASS against real MySQL and SoftHSM, including encrypted snapshot restore.
- `EncryptedMySqlSnapshotServiceTest`: 8/8 PASS for chunk, inventory, context, key, total/count and partial-restore faults.
- Focused command/PKCS11/snapshot unit set: PASS.
- Protected inventory validator with real MySQL evidence: PASS; seven targets complete, zero blocking targets, V1 digest unchanged.
- MySQL cleanup: PASS.

## Boundary

This plan supplies migration/recovery evidence. Phase-level leak, rotation/composed-fault, evidence production and independent closure gates remain open.
