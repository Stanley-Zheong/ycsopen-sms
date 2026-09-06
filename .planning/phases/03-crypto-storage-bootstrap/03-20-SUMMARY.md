---
phase: 03-crypto-storage-bootstrap
plan: "20"
subsystem: key-lifecycle-and-rewrap
requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
task-commits: [c3f3236, 7dbdf79]
---

# Phase 03 Plan 20: Key lifecycle and rewrap

Implemented explicit key state, atomic activation, reference-gated retirement, independent blind-index rotation rows and restartable DEK rewrap.

## Result

- One ACTIVE/ROTATION_REQUIRED slot per purpose; 983040 requests rotation and 1048576 blocks further wraps without decrement or reuse.
- Retirement requires an exact zero-live-reference inventory with deterministic digest; no application key-delete API exists.
- Blind-index rotation stores ACTIVE and RETIRING versions as separate rows and verifies exact parity without changing a legacy cell.
- Rewrap authenticates the old full header, wraps under the repository-selected new ACTIVE reference, preserves data nonce/ciphertext, fully unprotects the result, then advances envelope CAS and checkpoint atomically.
- Runtime activation requires adapter reconstruction/restart with new ACTIVE and old DECRYPT_ONLY descriptors. A stale adapter is proven to fail before wrap/commit; a rebuilt adapter completes rewrap.

## Verification

- Production compile passed.
- `KeyLifecycleServiceTest` and `EnvelopeRewrapServiceTest`: 11 tests passed.
- Codec/context/field-codec regression after the AAD correction passed.

## Boundary

Real PKCS11 rotation, stale token-issuer race and recovery composition remain owned by Plan 21. No Phase 03 obligation TODO is closed by unit evidence alone.
