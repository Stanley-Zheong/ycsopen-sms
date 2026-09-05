---
phase: 03-crypto-storage-bootstrap
plan: "06"
subsystem: production-pkcs11-key-adapter
tags: [java-21, sunpkcs11, aes-gcm, hmac-sha256, mysql, key-rotation]

requires:
  - phase: 03-crypto-storage-bootstrap-03
    provides: Source-verified SoftHSM fixture and Java 21 SunPKCS11 prerequisite lane
  - phase: 03-crypto-storage-bootstrap-05
    provides: Opaque key, blind-index and token-digest ports
  - phase: 03-crypto-storage-bootstrap-11
    provides: V1200 key-reference and monotonic wrap-count metadata
provides:
  - Allowlisted canonical-module and exact-slot SunPKCS11 provider/session factory
  - Opaque AES-256 KEK and purpose-separated HmacSHA256 token-handle adapter
  - Independent durable wrap reservation with rotation threshold and hard ceiling
  - Stable redacted provider failure categories with correlation and descriptor hash
affects: [03-07-pkcs11-integration, 03-09-protected-persistence, 03-16-object-capability, 03-17-registration-upload, 03-20-key-lifecycle]

tech-stack:
  added: []
  patterns:
    - SunPKCS11-only production session with no deterministic fallback
    - Adapter-owned reserve then random nonce then provider invocation
    - Purpose, alias, version, state and HMAC-domain separation

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11CryptoStorageProperties.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11ProviderFactory.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/SunPkcs11KeyAdapter.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11KeyDescriptor.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11FailureMapper.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/KekWrapUsageRepository.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11ProviderFactoryTest.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/key/pkcs11/SunPkcs11KeyAdapterTest.java
  modified: []

key-decisions:
  - "The production adapter is the only owner of durable reservation, random wrap nonce generation and provider invocation, in that order."
  - "A successful reservation is never released, decremented or reused, including when nonce generation or provider work fails."
  - "Provider diagnostics expose only a stable category, random correlation and SHA-256 descriptor identity; provider text, module path, token identity, alias and PIN are omitted."
  - "Focused unit doubles are labeled unit-mapping-only-not-pkcs11-evidence; real SoftHSM execution remains the Plan 07 boundary."

patterns-established:
  - "Wrap admission: one REQUIRES_NEW atomic V1200 update reserves only below 1,048,576 and switches the key row to ROTATION_REQUIRED at 983,040."
  - "Opaque token handling: issue selects exactly one ACTIVE key; verification accepts only the stored ACTIVE or RETIRING version and compares with MessageDigest.isEqual."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 06: Production SunPKCS11 Key Adapter Summary

**Java 21 SunPKCS11 now owns opaque AES-GCM KEK wrapping and three separated HMAC families, with durable pre-nonce admission, bounded key use and redacted fail-closed errors.**

## Accomplishments

- Added a production provider factory that accepts only a canonical allowlisted native module, exact slot identity, expected provider identity and a complete alias inventory.
- Loaded AES-256 and HmacSHA256 token handles through the SunPKCS11 session boundary and rejected missing, duplicate, wrong-algorithm, wrong-size, non-token, non-sensitive or extractable key metadata.
- Implemented `KeyProtectionPort`, `BlindIndexPort` and `OpaqueTokenDigestPort` in one production adapter without exposing key bytes or calling `getEncoded()`.
- Made the adapter the sole production owner of `reserve -> random 96-bit nonce -> provider` ordering.
- Added a `REQUIRES_NEW` V1200 update that increments before wrap, marks `ROTATION_REQUIRED` at count `983040`, admits count `1048576`, rejects the following reservation and never provides release/decrement/reset behavior.
- Preserved Plan 05 mobile HMAC and opaque-token canonical domains, ACTIVE/RETIRING verification policy, purpose/alias separation and constant-time digest comparison.
- Mapped provider, token, mechanism, alias, attribute, ceiling and operation failures to stable sanitized categories with correlation and hashed descriptor only.

## Task Commit

1. **Task 1: Build production SunPKCS11 opaque-key operations** — `94ed134`

## Reservation and Provider Boundary Proof

| Invariant | Executable result |
| --- | --- |
| Ordering | Event probe records exactly `reserve`, `nonce`, `provider` for one wrap |
| Failure consumption | Simulated provider failure leaves the reserved count incremented and exposes only `PKCS11_OPERATION_FAILED` metadata |
| Rotation threshold | Reservation from `983039` returns `983040` and health becomes `ROTATION_REQUIRED` |
| Rotation continuation | Reservation from `983040` returns `983041` without reusing a prior count |
| Hard ceiling | Reservation from `1048575` returns `1048576`; the following call rejects before nonce/provider invocation |
| Concurrency | Forty-eight competing callers starting sixteen slots below the ceiling produce exactly sixteen successes and never exceed `1048576` |
| Independent durability | Repository test proves `PROPAGATION_REQUIRES_NEW`, atomic conditional update, count read and commit order |

## Key and Domain Results

- Provider tests reject a non-allowlisted module, unexpected provider identity, missing alias, duplicate cross-purpose alias, wrong key size and extractable key metadata.
- Mobile index tests preserve the exact ACTIVE v2 vector `alesvuhbuvlf3pvcduj5ghm4kicrnfqkxbocojpoeq453uxdjsjc6` and a separate RETIRING v1 vector.
- Object-capability ACTIVE v2 preserves digest `3e12d6d32c48339739caad700103f744aa5cb1effeaabf2e9f3c8f9ffb5ce81b`.
- Registration-upload ACTIVE v2 preserves digest `a28ad654a0c6c839ba2fc141e72c4a593348adda74bfcc461307c0a35187b97a`.
- Unknown, RETIRED, COMPROMISED, cross-purpose and cross-binding token verification fails closed; ACTIVE and stored RETIRING versions verify through `MessageDigest.isEqual`.

## Automated Checks

- `mvn -f core/pom.xml -Dtest='Pkcs11ProviderFactoryTest,SunPkcs11KeyAdapterTest' test` — PASS; 9 focused tests, no failures or errors.
- `mvn -f core/pom.xml test` — PASS; 121 tests, no failures or errors. Nine existing opt-in integration tests remain skipped by the default profile.
- `! rg -n 'getEncoded\(|FIELD_ENCRYPTION_KEY|CHANGE_ME_BASE64|decrementWrap|releaseWrap|resetWrap' core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11` — PASS.
- `git diff --check` — PASS.

## Decisions Made

- Module path, slot, expected token identity and complete key-alias inventory form the closed provider locator; any mismatch prevents session construction.
- Existing token keys are never exported for inspection. The factory admits only an opaque SunPKCS11 handle with the required metadata, and the adapter verifies usable AES-GCM/HMAC mechanisms before accepting the session.
- Every HMAC family has its own descriptor purpose and alias set. Capability and registration-upload issue/verify paths cannot select each other's handles even when presented with identical token-secret bytes.
- Unit crypto operations are package-private test seams and carry the explicit `unit-mapping-only-not-pkcs11-evidence` label. They prove mapping, ordering and fault behavior only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical secret lifetime] Clear the acquired PIN without cloning it**

- **Found during:** Task 1 security review.
- **Issue:** Cloning the supplier-returned PIN would leave an additional uncleared character array.
- **Fix:** The provider factory owns the acquired array and clears that exact array in its `finally` block.
- **Files modified:** `Pkcs11CryptoStorageProperties.java`, `Pkcs11ProviderFactory.java`.
- **Verification:** focused provider tests and complete backend suite.
- **Committed in:** `94ed134`.

**2. [Rule 1 - Error-boundary bug] Sanitize nonce-generation failures after durable reservation**

- **Found during:** Task 1 source review.
- **Issue:** A runtime failure from nonce generation occurred after reservation but outside the sanitized operation boundary.
- **Fix:** Kept nonce generation after reservation while moving it inside the redacted failure boundary; the reservation remains consumed.
- **Files modified:** `SunPkcs11KeyAdapter.java`.
- **Verification:** ordering/failure focused test and source inspection.
- **Committed in:** `94ed134`.

**3. [Rule 2 - Metadata bound] Reject unrepresentable mobile key versions**

- **Found during:** Task 1 source review.
- **Issue:** V1200 permits a wide numeric key version, while the canonical mobile index encodes exactly one version byte.
- **Fix:** `Pkcs11KeyDescriptor` rejects mobile-index versions outside `1..255` before provider use.
- **Files modified:** `Pkcs11KeyDescriptor.java`.
- **Verification:** compile, focused tests and complete backend suite.
- **Committed in:** `94ed134`.

**Total deviations:** Three auto-fixed correctness/security issues; all are inside the declared files and preserve the locked architecture.

## Issues Encountered

- Context7 was not installed in the execution environment. Java 21 behavior was checked against the installed JDK 21 API/classes and `jdk.crypto.cryptoki` source before using the version-specific SunPKCS11 boundary.
- The first focused run exposed only two test-fixture expectation errors: macOS canonical `/var` path spelling and the RETIRING mobile v1 expected vector. Both fixtures were corrected; production behavior did not change.

## Known Stubs

None. The package-private crypto and token-loader doubles exist only in the two focused test files, are labeled `unit-mapping-only-not-pkcs11-evidence`, and are not production wiring.

## Threat Surface Review

- **Spoofing:** canonical allowlisted module, exact slot/provider identity and required alias inventory fail closed.
- **Information disclosure:** production code never calls `getEncoded()` and error objects omit provider text, module path, token identity, alias, PIN, key bytes, AAD and ciphertext.
- **Elevation of privilege:** purpose, version, state, alias, algorithm, size, token/sensitive/extractable metadata and mechanism checks precede accepted operation.
- **Denial of service:** no fallback exists; provider failures set sanitized unavailable health, and atomic reservation prevents nonce-use ceiling overruns under concurrency.
- **Tampering:** AES-GCM wrap authenticates the exact `YCSE-WRAP-AAD\0` header/context bytes; purpose-separated HMAC domains prevent cross-use.

The files add native-module loading and database mutation at existing trust boundaries already registered in the plan threat model. No endpoint, authentication route, schema change or new network boundary was introduced.

## Real SoftHSM Boundary

No real SoftHSM execution is claimed by this plan. The focused tests prove API mapping, sequencing, vectors and failure handling with explicitly labeled unit doubles. Plan 07 owns the actual source-verified SoftHSM token plus real MySQL reservation/concurrency/restart evidence; any unavailable or mismatched runtime must fail closed there.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. In particular, `OBL-CRYPTO-STORAGE-003` remains open until Plan 07 real SoftHSM/MySQL execution, later rotation/rewrap/recovery work, canonical evidence production, independent review and the scoped TODO query all pass.

## Self-Check: PASSED

- All eight declared implementation/test files and this summary exist.
- Task commit `94ed134` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused verification, complete backend verification, forbidden-source scan and diff check pass against the committed task.
- No secret, production data, local runtime state or generated build output is tracked.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`.
- All Phase 03 obligation TODO rows remain open and `requirements-completed` remains empty.
