---
phase: 03-crypto-storage-bootstrap
plan: "05"
subsystem: opaque-key-boundaries
tags: [key-ports, blind-index, hmac, base32, opaque-token-digest, java-21]

requires:
  - phase: 03-crypto-storage-bootstrap-04
    provides: Strict YCSE/v1 authenticated header and semantic context contract
provides:
  - One caller-visible DEK wrap operation with adapter-owned admission, key selection and nonce
  - Immutable wrapped-DEK, mobile blind-index and opaque-token digest values
  - Ordered ACTIVE/RETIRING mobile index sets encoded as 53 lowercase Base32 characters
  - Purpose-separated object-capability and registration-upload token digest boundary
  - Test-only deterministic adapter labeled deterministic-test-adapter
affects: [03-06-pkcs11-adapter, 03-09-protected-persistence, 03-16-object-capability, 03-17-registration-upload, 03-20-key-lifecycle, 03-24-protected-field-codec]

tech-stack:
  added: []
  patterns:
    - Adapter-owned reserve then nonce then provider sequence behind one wrap call
    - Immutable defensive binary values with redacted string representations
    - Purpose, alias, domain, binding and stored-version separation for token digests

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/KeyProtectionPort.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/BlindIndexPort.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/WrappedDataKey.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/VersionedBlindIndex.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/KeyHealth.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/WrapOperationAdmissionPort.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/OpaqueTokenDigestPort.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/key/VersionedTokenDigest.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/key/DeterministicTestKeyAdapter.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/key/VersionedBlindIndexTest.java
  modified: []

key-decisions:
  - "KeyProtectionPort.wrap accepts only a DEK, authenticated header and semantic context; the adapter alone selects the key, reserves one durable operation and creates the wrap nonce."
  - "Mobile blind-index results are immutable ascending unique-version sets; each member is one version byte plus 32-byte HMAC encoded as exactly 53 lowercase Base32 characters."
  - "Opaque token digests use compile-time-limited capability/upload purposes, separate HMAC families and domains, ACTIVE-only issue, and stored ACTIVE/RETIRING constant-time verification."

patterns-established:
  - "Single wrap boundary: no caller-visible admission, caller nonce, release, decrement, reset or retry handle exists."
  - "Fail-closed version policy: unknown, retired, revoked, wrong-purpose, wrong-binding and malformed token inputs do not verify."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 05: Opaque Versioned Key Boundaries Summary

**Opaque DEK wrapping, versioned mobile HMAC indexes, and purpose-separated capability/upload token digests now have immutable fail-closed Java contracts without exposing master-key material or wrap controls.**

## Accomplishments

- Defined `KeyProtectionPort.wrap(dek, authenticatedHeader, semanticContext)` as the only caller-visible wrap operation. Neither key reference, wrap nonce nor admission is a caller parameter.
- Kept `WrapOperationAdmissionPort` package-scoped with one monotonic `reserve` operation and no release, decrement, reset or reuse API.
- Added immutable defensive `WrappedDataKey`, `VersionedBlindIndex`, `VersionedTokenDigest` and sanitized `KeyHealth` values; protected byte values are copied at every public boundary and redacted from `toString()`.
- Bound mobile HMAC inputs to `mobile-sha256-v1`, target type, field, `mobile-routing` purpose, scope and the in-memory historical SHA-256 digest of one canonical 11-digit mobile.
- Encoded one key-version byte plus a 32-byte HMAC as exactly 53 lowercase RFC 4648 Base32 characters without padding, with ordered unique version sets for write/query metadata rows.
- Restricted opaque-token digestion to `OBJECT_CAPABILITY` and `REGISTRATION_UPLOAD`, with separate deterministic test keys, purpose bytes and canonical tenant/subject/resource-or-session binding.
- Implemented ACTIVE-only issue, exact stored ACTIVE/RETIRING version verification, constant-time digest comparison and fail-closed unknown/RETIRED/REVOKED/cross-purpose/cross-binding handling.

## Task Commit

1. **Task 1: Define opaque key ports and versioned blind index** — `4e8e5aa`

## Wrap Boundary Proof

| Invariant | Executable result |
| --- | --- |
| Caller-visible API | `wrap(byte[], byte[], ProtectionContext)` only |
| Caller key reference | Absent; adapter returns its canonical reference in `WrappedDataKey` |
| Caller wrap nonce | Absent; deterministic adapter derives it only after `reserve` |
| Admission visibility | Outer interface is package-scoped; only `reserve` exists |
| Retry/release path | No release, decrement, reset, reuse or admission handle API exists |
| Header/key agreement | Wrong authenticated-header key reference fails before reservation |
| Exact context | Wrong resource context fails authenticated unwrap with a sanitized test failure |

The deterministic vector reserves operation `1`, creates its 12-byte nonce after that reservation, returns a 48-byte wrapped DEK, and unwraps the original 32-byte DEK only with the exact header and semantic context.

## Versioned Blind-Index Vectors

For mobile `13800138000`, target `MESSAGE_TASK`, field `mobile`, purpose `mobile-routing`, scope `tenant:17`, and deterministic ACTIVE version `2`, the canonical result is:

`alesvuhbuvlf3pvcduj5ghm4kicrnfqkxbocojpoeq453uxdjsjc6`

- Length is exactly 53 characters and alphabet is exactly `[a-z2-7]`.
- Write and query results contain versions `1, 2` in canonical ascending order with no duplicate version.
- Changing target, field or tenant scope changes the HMAC result.
- A noncanonical `+86` mobile form is rejected rather than normalized ambiguously.

## Opaque Token Digest Vectors

Using the synthetic 32-byte secret `01..20` only inside the test process:

| Purpose | ACTIVE version | Deterministic digest |
| --- | ---: | --- |
| Object capability | 2 | `3e12d6d32c48339739caad700103f744aa5cb1effeaabf2e9f3c8f9ffb5ce81b` |
| Registration upload | 2 | `a28ad654a0c6c839ba2fc141e72c4a593348adda74bfcc461307c0a35187b97a` |

Both ACTIVE version `2` and stored RETIRING version `1` verify in their own purpose and binding. Unknown version `99`, RETIRED version `3`, REVOKED version `4`, wrong secret length, wrong resource/session binding, and capability/upload cross-use all fail closed. A recording comparator proves valid-state verification delegates the final 32-byte decision to `MessageDigest.isEqual`.

## Verification

- `mvn -f core/pom.xml -Dtest=VersionedBlindIndexTest test` — PASS; 10 tests with zero failures, errors or skips.
- `mvn -f core/pom.xml test` — PASS; 112 tests with zero failures or errors. Nine existing real-service tests remained skipped behind their configured opt-in environment gates.
- `! rg -n 'getKey|keyBytes|sha256Hex' core/src/main/java/com/ycsopen/sms/core/common/security/key` — PASS.
- `! rg -n 'DeterministicTestKeyAdapter|deterministic-test-adapter' core/src/main/java` — PASS.
- `javap -classpath core/target/classes -p com.ycsopen.sms.core.common.security.key.KeyProtectionPort com.ycsopen.sms.core.common.security.key.WrapOperationAdmissionPort` — PASS; one public wrap method and a non-public admission type with only `reserve`.
- `git diff --check` — PASS.

## Decisions Made

- Kept wrap key selection inside the adapter. The authenticated header must name the same key reference returned by the adapter; mismatch is rejected before consuming admission.
- Treated `ROTATION_REQUIRED` as an actionable warning that still permits admitted writes until the separate hard ceiling; `UNAVAILABLE` is the only sanitized health state that denies new writes.
- Used strict canonical 11-digit mobile input rather than silently accepting country prefixes or formatting characters.
- Kept token digest verification inside the port so callers never compare stored digests themselves or choose a different version/key family.

## Deviations from Plan

None — all implementation and tests remain inside the ten declared files.

## Issues Encountered

None.

## Known Stubs

None. `DeterministicTestKeyAdapter` is deliberately package-private under test source with evidence label `deterministic-test-adapter`; it is not production wiring and does not satisfy PKCS11 or physical-HSM evidence. Production SunPKCS11 behavior remains owned by Plans 06 and 07.

## Threat Surface Review

- **Information disclosure:** production ports expose no KEK, blind-index HMAC key, raw token string, provider key object or key accessor; immutable values redact their protected bytes.
- **Tampering:** wrap header/key and exact semantic context are authenticated; blind indexes and token digests bind distinct canonical domain metadata.
- **Spoofing:** the deterministic adapter is test-source-only, package-private, and explicitly labeled as non-HSM evidence.
- **Denial of service:** malformed lengths, ambiguous mobile input, invalid versions and empty/duplicate version sets fail before producing metadata.
- **Repudiation:** fixed known vectors and stable failure behavior distinguish the deterministic boundary without overclaiming production-provider evidence.

No endpoint, authentication route, filesystem access or schema change was introduced by this plan.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This plan defines prerequisite key/index contracts only; it does not claim production PKCS11, persistence, capability, migration, leak-proof, independent-review or delivery evidence and completes no obligation.

## Self-Check: PASSED

- All ten declared source/test artifacts and this summary exist.
- Task commit `4e8e5aa` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused and complete backend verification pass against the committed implementation.
- No placeholder, secret, production data, generated output or runtime state is present in the committed files.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty`; no progress, percentage, duration or estimate field is added.
- All Phase 03 obligation TODO rows remain open and `requirements-completed` remains empty.
