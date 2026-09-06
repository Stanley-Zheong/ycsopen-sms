---
phase: 03-crypto-storage-bootstrap
plan: "21"
subsystem: rotation-recovery-real-proof
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 21: Rotation and recovery real proof

Implemented the missing real MySQL/SoftHSM lifecycle composition without recursively rerunning the already accepted migration, snapshot and object matrices.

## Result

- The production SunPKCS11 adapter proves persisted reservation thresholds, concurrent ceiling enforcement and burned post-reservation provider failure.
- An explicitly externally provisioned `PREPARED` reference is atomically activated; the prior key becomes decrypt-only, new writes use the new key and old ciphertext remains readable.
- Production rewrap authenticates the old envelope, preserves data nonce/ciphertext, changes only wrap metadata, and advances a real-MySQL digest/checkpoint CAS.
- Restarted adapters read the rewrapped value. Live references block retirement; zero references permit retirement without a token-key deletion API.
- ACTIVE/RETIRING blind indexes remain separate rows, and an externally supplied `COMPROMISED` descriptor rejects old unwrap.
- Shared real fixtures now include the mandatory independent snapshot-recovery AES alias/reference required by production configuration.

## Verification

- Production test compilation passed.
- `Phase03RotationRecoveryIntegrationTest` passed 1/1 against real MySQL and SoftHSM.
- Reservation facts covered 983039/983040, restart to 983041, 1048575/1048576, concurrency and provider-failure burn.
- Whitespace audit passed.
- A separate unchanged object-storage rerun was stopped after the fixture could not admit MySQL; it was not required again because that exact lane already passed and the Phase boundary runner owns the single recomposition.

## Boundary

Plan 22 composes the previously accepted migration/snapshot/object lanes with this new lifecycle result. No application-side key provision or compromise mutation API was added because the PRD and Plan 20 architecture do not own one. No Phase 03 obligation TODO is closed by this plan alone.
