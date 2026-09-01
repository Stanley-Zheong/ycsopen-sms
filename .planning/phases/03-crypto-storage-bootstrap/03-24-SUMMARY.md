---
phase: 03-crypto-storage-bootstrap
plan: "24"
subsystem: protected-field-codec
tags: [ycse-v1, aes-gcm, envelope-encryption, opaque-key-port, java-21]

requires:
  - phase: 03-crypto-storage-bootstrap-04
    provides: Strict YCSE/v1 binary, authenticated-header, AAD and capacity contract
  - phase: 03-crypto-storage-bootstrap-05
    provides: Single opaque KeyProtectionPort wrap operation and immutable WrappedDataKey
provides:
  - Per-value YCSE/v1 protection with a fresh 32-byte DEK and 12-byte data nonce
  - One opaque adapter-owned wrap operation under the exact authenticated header and semantic context
  - Strict unprotect with one sanitized failure category and no legacy or plaintext fallback
  - Pre-cryptography database, protected-object and snapshot-chunk capacity enforcement
affects: [03-09-protected-persistence, 03-13-migration-runner, 03-14-snapshot-orchestration, 03-28-integration]

tech-stack:
  added: []
  patterns:
    - Constructor-injected strict envelope codec with one opaque key-port wrap
    - Exact YCSE data/wrap domain separation with full canonical header and context authentication
    - Transient DEK and cryptographic working-buffer clearing in finally blocks

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldCodec.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldCodecTest.java
  modified:
    - core/src/test/java/com/ycsopen/sms/core/common/security/FieldEncryptorTest.java

key-decisions:
  - "Inject the canonical active KEK reference as public envelope metadata so the complete header is authenticated before the single wrap call; reject any different adapter-returned reference."
  - "Collapse parser, context, JCE, key-provider, wrong-key and tamper failures into ProtectionFailure.PROTECTED_DATA_INVALID with no cause or fallback."

patterns-established:
  - "Protect ordering: bounds -> canonical header/data AAD -> fresh DEK/data nonce -> data encryption -> exactly one adapter wrap -> strict envelope encode."
  - "Unprotect ordering: strict decode -> exact header/data AAD -> opaque unwrap -> data authentication -> transient-buffer clearing."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 24: Strict Protected-Field Codec Summary

**Per-value YCSE/v1 protection now binds the complete canonical header and six-field semantic context to fresh data encryption and one adapter-owned DEK wrap, with bounded inputs and one fail-closed error category.**

## Accomplishments

- Added `ProtectedFieldCodec` as the persistence-facing owner of fresh 32-byte DEKs, fresh 12-byte data nonces, AES-256-GCM data protection and strict YCSE/v1 serialization.
- Constructed data AAD from the full 19-byte header plus provider/key reference and canonical semantic context, while the opaque key port independently applies the distinct wrap domain to the same header and context.
- Kept wrap admission, wrap nonce generation and provider use entirely behind `KeyProtectionPort.wrap`; each protect attempt has one call, no retry path and no `WrapOperationAdmissionPort` dependency.
- Required the immutable adapter result to carry the configured canonical key reference, 12-byte wrap nonce and 48-byte wrapped DEK; mismatched output fails closed before serialization.
- Enforced the selected plaintext and complete-envelope limits before random generation, JCE encryption or wrap admission, including the exact 110-byte database-field boundary and over-limit object/snapshot inputs.
- Cleared transient DEK, nonce, AAD, ciphertext and wrapped-key working arrays in `finally` blocks where Java permits, without clearing caller-owned plaintext or returned plaintext.
- Extended the legacy regression suite to prove the direct-key, unversioned `FieldEncryptor` payload is never admitted as YCSE/v1.

## Task Commit

1. **Task 1: Implement per-value envelope protection** — `c835496`

## Contract Results

| Contract | Executable result |
| --- | --- |
| Envelope identity | Strict `YCSE` magic, version `1`, provider `pkcs11` and canonical injected/returned key-reference agreement |
| Data protection | Fresh 32-byte DEK and fresh 12-byte data nonce per value using AES-256-GCM with a 128-bit tag |
| Wrap boundary | Exactly one `KeyProtectionPort.wrap(dek, authenticatedHeader, semanticContext)` call; no codec admission, wrap nonce or retry API |
| Adapter result ownership | Serialized wrap nonce and wrapped DEK are exactly the immutable values returned by the adapter |
| Ordering | Recording adapter observes `admission -> nonce -> provider`; over-capacity input reaches none of those steps |
| Exact-context round trip | Unicode and binary values unprotect only with the original target and six-field semantic context |
| Capacity | A database value of 110 bytes with a 32-byte key reference produces exactly 255 envelope bytes; 111-byte database, over-5-MiB identity object and over-10-MiB snapshot chunk inputs fail before wrap |
| Failure surface | Wrong context/key/target, header/wrap nonce/wrapped DEK/data nonce/ciphertext mutation and adapter outage all return `PROTECTED_DATA_INVALID` with message `protected data is invalid`, no cause and no plaintext fallback |
| Transient key handling | The recording adapter's DEK reference contains only zero bytes after protect returns |

## Verification

- `mvn -f core/pom.xml -Dtest='ProtectedFieldCodecTest,FieldEncryptorTest' test` — PASS; 14 tests with zero failures, errors or skips.
- `mvn -f core/pom.xml test` — PASS; 132 tests with zero failures or errors. Nine existing real-service tests remained skipped behind their configured opt-in gates.
- `rg -n 'keyProtectionPort\.wrap\(' core/src/main/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldCodec.java` — PASS; exactly one source call site.
- `! rg -n 'WrapOperationAdmissionPort|wrapNonce.*nextBytes|FIELD_ENCRYPTION_KEY|keyBytes|getEncoded\(' core/src/main/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldCodec.java` — PASS.
- `git diff --check` — PASS.

## Decisions Made

- The active KEK reference is injected as nonsecret public envelope metadata because the existing opaque port requires the complete authenticated header before it can perform its single wrap operation. The adapter remains the sole owner of actual key selection/material, durable admission, wrap nonce and provider operation, and a returned-reference mismatch is rejected.
- Protect validates capacity before creating random material or consuming adapter admission, while unprotect always strict-decodes before key access.
- Every recoverable protected-data failure is deliberately remapped to the existing single `ProtectionFailure` category instead of leaking parser, provider, key, context or tag detail.

## Deviations from Plan

None — implementation and tests remain inside the three declared task files.

## Issues Encountered

None.

## Known Stubs

None. The nullable local byte-array initializers in `ProtectedFieldCodec` are `finally`-cleanup guards for transient cryptographic buffers, not rendered/data-source stubs. The recording key port exists only as a focused test double; it is not production wiring and is not PKCS11 evidence.

## Verification Boundary

- The complete backend suite leaves nine existing real-service tests skipped behind explicit opt-in gates. This plan proves the deterministic codec/key-port boundary only and does not claim real MySQL, MinIO, SoftHSM, persistence adoption, migration, leak-scan or delivery evidence.

## Threat Surface Review

- **Information disclosure:** no production key bytes, KEK handle, caller-selected wrap nonce, plaintext fallback or exception cause is exposed; transient DEK material is cleared after each operation.
- **Tampering:** the complete header/provider/key reference and canonical semantic context are authenticated under distinct data and wrap domains; mutation tests cover header, both nonces, wrapped DEK and ciphertext/tag.
- **Denial of service:** selected-purpose plaintext and complete-envelope bounds are checked before random generation, encryption or durable wrap admission.

No endpoint, authentication route, filesystem access or schema change was introduced.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This plan supplies codec prerequisite behavior only; `requirements-completed` remains empty until canonical persistence, provider, migration, leak, independent-review and delivery evidence satisfy the scoped TODO query.

## Self-Check: PASSED

- Both created files, the modified legacy regression test and this summary exist.
- Task commit `c835496` exists on `phase/03-crypto-storage-bootstrap` with no tracked deletion.
- Focused and complete backend verification pass against the committed implementation.
- No placeholder, secret, production data, generated output or runtime state is present in the committed task files.
- `.planning/STATE.md` still retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; no additional status metric was added.
- All Phase 03 obligation TODO rows remain open and `requirements-completed` remains empty.
