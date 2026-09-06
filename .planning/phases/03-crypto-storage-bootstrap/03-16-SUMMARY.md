---
phase: 03-crypto-storage-bootstrap
plan: "16"
subsystem: protected-object-capability
tags: [opaque-capability, deny-by-default, hmac, key-rotation, java-21]

requires:
  - phase: 03-crypto-storage-bootstrap-05
    provides: Purpose-separated versioned opaque-token digest port
  - phase: 03-crypto-storage-bootstrap-07
    provides: Production ACTIVE/RETIRING PKCS11 token-digest verification
  - phase: 03-crypto-storage-bootstrap-15
    provides: Ciphertext-only private object-store boundary
provides:
  - One-time application-relative `ocap_v1_` capability delivery
  - Digest-only capability storage contract with tenant, subject, object and purpose binding
  - Deny-by-default production object authorization seam
  - Authorization and capability validation before every downstream object fetch
affects: [03-18-safe-logging, 03-20-key-lifecycle, 03-28-protected-object-service, 06-rbac-reveal]

tech-stack:
  added: []
  patterns:
    - CSPRNG lookup and secret with canonical unpadded Base64url token grammar
    - ACTIVE-only issue and stored ACTIVE/RETIRING constant-time verification
    - Conditional deny-all production authorization before downstream work

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectAccessAuthorizationPort.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/DenyAllObjectAccessAuthorization.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectCapabilityService.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectCapabilityToken.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/object/ObjectCapabilityServiceTest.java
  modified:
    - core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageConfiguration.java

key-decisions:
  - "Capability tokens use a 16-byte nonsecret lookup plus a 32-byte CSPRNG secret; the complete token is retained only by a one-claim application-relative path value."
  - "Stored capability values contain binding digests, object/purpose, versioned keyed digest, state and expiry, with no token or secret field."
  - "Production supplies a conditional deny-all authorization bean until Phase 6 installs current RBAC and reveal policy."

patterns-established:
  - "Validation order: parse domain and grammar, load safe metadata, validate state/expiry/bindings/keyed digest, authorize, then fetch."
  - "Access faults collapse to one sanitized denial without token, binding-oracle or provider detail."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 16: Opaque Capability Authorization Summary

**Purpose-bound opaque object capabilities now verify versioned keyed digests and current authorization before any private object fetch, with production denying by default.**

## Accomplishments

- Added a narrow authorization request containing protected object, tenant, subject, purpose, capability state and expiry without exposing the capability token.
- Added a production-safe `DenyAllObjectAccessAuthorization` and conditional configuration bean; no allow-all production implementation exists.
- Issued canonical `ocap_v1_` credentials from a nonsecret 16-byte lookup and a 32-byte CSPRNG secret, returned only through a one-claim application-relative path.
- Persisted only lookup, object/purpose, SHA-256 binding digests, `VersionedTokenDigest`, capability state and expiry; stored values have no token or secret field and redact protected values in string rendering.
- Used only `OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY` for ACTIVE issue and stored-version ACTIVE/RETIRING verification; no blind-index call exists in the capability service.
- Enforced exact token grammar including canonical unpadded Base64url, injected-clock expiry and stable sanitized failures.
- Proved every denial path prevents the downstream supplier that represents private object-store access.

## Task Commit

1. **Task 1: Implement deny-by-default capability authorization** — `d05e6d2`

## Authorization Matrix

| Capability check | Authorization port | Downstream fetch | Result |
| --- | --- | --- | --- |
| Valid ACTIVE binding, live ACTIVE digest version, explicit allow fixture | Invoked with complete safe context | Invoked once | Accepted |
| Valid capability, production deny-all or explicit deny fixture | Invoked | Not invoked | Sanitized denial |
| Expired by clock or stored EXPIRED state | Not invoked | Not invoked | Sanitized denial |
| Stored REVOKED state, including consumed capability transition | Not invoked | Not invoked | Sanitized denial |
| Wrong object, tenant, subject or purpose | Not invoked | Not invoked | Sanitized denial |
| Tampered secret or noncanonical equivalent Base64url spelling | Not invoked | Not invoked | Sanitized denial |
| `regup_v1_` cross-domain or malformed token | Not invoked; metadata is not queried | Not invoked | Sanitized denial |
| Stored ACTIVE or RETIRING key version | Invoked after constant-time digest verification | Invoked only when explicitly allowed | Accepted |
| Stored RETIRED, REVOKED or unknown key version | Not invoked | Not invoked | Sanitized denial |
| Digest provider or authorization implementation failure | Fails closed | Not invoked | Sanitized denial |

## Capability Storage Contract

Compiled `StoredCapability` fields are limited to:

- nonsecret lookup ID;
- protected object ID and canonical purpose;
- 32-byte tenant and subject binding digests;
- purpose-checked `VersionedTokenDigest` containing key version and a defensive 32-byte digest;
- `ACTIVE`, `REVOKED` or `EXPIRED` state;
- expiry instant.

There is no complete-token, secret, URL, bucket, object-store key, ciphertext or blind-index field. The complete path value overwrites its internal character buffer after its single claim and always renders as redacted.

## Capability Fault Results

- Expiry is checked with the injected `Clock`; equality with the expiry instant is denied.
- Revoked, expired and consumed-as-revoked metadata is rejected before digest authorization or fetch.
- Tenant, subject, object and purpose changes fail against stored binding facts before fetch.
- Secret tamper and alternative noncanonical Base64url encodings fail without accepting an equivalent decoded secret.
- Registration-upload prefixes fail before capability metadata lookup, preserving digest-domain separation.
- A capability issued under version 1 remains valid after version 1 becomes RETIRING and version 2 becomes ACTIVE; RETIRED, REVOKED and removed version 1 states fail closed.
- Digest-provider and authorization exceptions expose only `CAPABILITY_DENIED`; provider canaries and complete tokens do not appear in the error.

## Verification

- `mvn -f core/pom.xml -Dtest=ObjectCapabilityServiceTest test` — PASS: 10 tests with no failure, error or skip.
- `mvn -f core/pom.xml test` — PASS: 160 tests with no failure or error; 11 existing service tests remain behind their opt-in profiles.
- Capability source scan for `BlindIndexPort` — PASS: no reference in `ObjectCapabilityService` or `ObjectCapabilityToken`.
- Production authorization scan for `return true`, `AlwaysAllow` and always-allow spellings — PASS: none in the object package or crypto configuration.
- Capability source scan for logger calls — PASS: no logger or log call in token issuance/verification values.
- `javap` stored-capability field audit — PASS: only lookup, object, binding digests, purpose, versioned digest, state and expiry are present.
- `git diff --check` — PASS before the task commit.

## Phase 6 Boundary

Phase 3 supplies only the server-side capability validation and authorization seam. It makes no UI permission, RBAC, masked/full reveal, privileged audit or approval-workflow claim. The conditional deny-all bean keeps production closed until Phase 6 supplies an explicit current policy implementation through the same port.

## Deviations from Plan

None — implementation and tests remain inside the six declared files and follow DR-P03-005 without adding an endpoint, schema change, allow-all policy or alternate token digest.

## Issues Encountered

- The first noncanonical Base64url test fixture used the wrong tail-bit assumption. The fixture was corrected to mutate only unused bits of a 32-byte secret encoding, then the complete focused and backend suites passed.

## Known Stubs

None. The `CapabilityStore` is the planned persistence seam and has a complete immutable stored value contract; Plan 28 owns its MySQL repository and protected-object lifecycle composition.

## Threat Surface Review

- **Spoofing/tampering:** the secret digest is keyed, purpose-separated and bound to tenant, subject, object and purpose; the parser rejects malformed and noncanonical token forms.
- **Elevation of privilege:** production defaults to deny, authorization sees the full safe context, and no downstream fetch occurs before both capability and policy acceptance.
- **Information disclosure:** complete tokens are neither stored nor rendered; stored digests and bindings defensively copy bytes; access faults omit token and provider detail.
- **Key rotation:** issue is ACTIVE-only while old live capabilities address their exact stored ACTIVE/RETIRING version; unknown and terminal versions fail closed.

No endpoint, schema, filesystem access, public object link, RBAC implementation or audit claim was introduced by this plan.

## Remaining Scoped TODO State

All Phase 03 obligation TODO rows remain open. This plan supplies capability issuance and access gating only; protected-object persistence/lifecycle, tenant registration composition, leak evidence, canonical obligation evidence, independent review and delivery attestation remain owned by later plans.

## Self-Check: PASSED

- All six planned implementation/test files and this summary exist.
- Task commit `d05e6d2` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused tests, complete backend tests, authorization/token source scans, compiled stored-field audit and diff checks pass against the committed implementation.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty` with its frontmatter unchanged.
- Every Phase 03 obligation TODO remains open and `requirements-completed` remains empty.
