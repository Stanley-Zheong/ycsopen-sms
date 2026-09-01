---
phase: 03-crypto-storage-bootstrap
plan: "30"
subsystem: real-object-registration-proof
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
task-commit: be59a2a
---

# Phase 03 Plan 30: Real protected-object registration proof

The production object/session/tenant adapters now pass one composed MySQL, MinIO and SunPKCS11/SoftHSM integration lane.

## Verified boundaries

- Five object purposes, replacement/reconciliation, exact and over-limit sizes, three-attempt purpose and fifteen-attempt session ceilings, and post-reservation storage failure.
- Claim, close, expiry, cross-session/draft/purpose denial, transaction rollback to OPEN/STAGED and committed CLAIMED state.
- Separate capability/upload token domains, ACTIVE/RETIRING verification, cross-domain denial and live-reference retirement blocking.
- Anonymous MinIO denial, raw-object YCSE/canary checks, checksum/tag/orphan faults, three protected tenant cells, opaque object IDs and absence of raw URLs/plaintext/tokens in MySQL.
- Run-owned service cleanup.

## Verification

- `Phase03ObjectStorageIntegrationTest`: PASS with 82 internal real-boundary assertions and metadata-only MySQL/MinIO/PKCS11 identity hashes.
- Independent `service_checks.rb assert-clean --service minio`: PASS.
- Runtime corrections were limited to unsigned generated-key conversion, unique PASS-line extraction and self-referential replacement cleanup order.

## Boundary

The composed proof is a Phase 03 evidence input, not an obligation seal. Migration recovery, rotation/fault composition, leak scan, evidence production and stage reviews remain open.
