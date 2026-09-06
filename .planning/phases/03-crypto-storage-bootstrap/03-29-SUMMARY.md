---
phase: 03-crypto-storage-bootstrap
plan: "29"
subsystem: tenant-registration-protection
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-002]
requirements-completed: []
completion-metric: scoped_todo_empty
task-commit: 16bbd78
---

# Phase 03 Plan 29: Tenant registration protection

The live registration path now stores three tenant-bound YCSE envelopes and purpose-bound protected-object IDs instead of dropping protected values or accepting raw URLs.

## Result

- Request contract accepts one registration session, three required and two optional `pobj_v1_*` IDs, and the upload token only in its header; unknown and legacy URL input fail closed.
- Tenant ID allocation, three exact-AAD envelopes, session credential claim, five staged-object transitions and final tenant save run in the same Spring transaction.
- Entity accessors and response DTO exclude protected bytes, object IDs, token and storage details.
- Protected-data inventory is now `READY` with zero blocking current reader/writer surfaces; its destructive test was updated to synthesize a real false-ready condition.

## Verification

- Registration, object API, entity mapping, manifest and reader-fence affected tests: 28 tests passed.
- Protected inventory: 17 destructive cases passed; acceptance reports `READY` and `blocking_surfaces=0`.
- JSON parsing, diff checks and direct URL/source scans passed.

## Boundary

Real MySQL/MinIO/PKCS11 composition remains owned by Plan 30. The inventory readiness result removes the current-code blocker but does not close Phase 03 obligation or delivery TODOs.
