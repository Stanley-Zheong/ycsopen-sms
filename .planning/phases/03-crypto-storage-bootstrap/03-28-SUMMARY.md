---
phase: 03-crypto-storage-bootstrap
plan: "28"
subsystem: protected-object-lifecycle
tags: [ycse-v1, aes-gcm, private-object-store, capability, jdbc, spring-mvc]

requires:
  - phase: 03-crypto-storage-bootstrap-16
    provides: Purpose-bound opaque capability verification and deny-by-default authorization seam
  - phase: 03-crypto-storage-bootstrap-24
    provides: Strict bounded YCSE/v1 protect/unprotect codec
provides:
  - Bounded five-purpose plaintext ingestion with encrypt-before-put ordering
  - Safe JDBC metadata and digest-only capability persistence
  - Capability-first private reads with HEAD, exact ciphertext, SHA-256 and complete GCM authentication
  - Deterministic split-write, replacement, delete and orphan reconciliation
  - Application-mediated no-store binary access endpoint with stable sanitized failures
affects: [03-17-registration-object-api, 03-29-tenant-registration-protection, 03-30-real-object-proof]

tech-stack:
  added: []
  patterns:
    - Capability and current authorization before metadata or object-store access
    - Bounded plaintext limit-plus-one ingestion and exact declared-length matching
    - HEAD-before-body plus independent persisted/store/body checksum agreement
    - Operation-ID split-write containment with retryable terminal-object reconciliation

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectMetadataRepository.java
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectService.java
    - core/src/main/java/com/ycsopen/sms/core/web/ProtectedObjectAccessController.java
    - core/src/test/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectServiceTest.java
    - core/src/test/java/com/ycsopen/sms/core/web/ProtectedObjectAccessControllerTest.java
  modified: []

key-decisions:
  - "ProtectedObjectService is the only owner allowed to turn plaintext into a private object or authenticated ciphertext back into plaintext."
  - "The existing ObjectCapabilityService authorizeAndFetch boundary encloses metadata, HEAD, body, checksum and GCM work so every denial precedes storage access."
  - "The JDBC repository exposes opaque store locators only package-locally to the object service; public values and controller responses cannot return them."
  - "The controller activates only when a complete ProtectedObjectService composition exists and otherwise fails closed; current production authorization remains deny-all."

patterns-established:
  - "Write order: media/declared bound -> bounded actual read -> YCSE encryption -> operation reservation -> private put -> metadata commit -> replacement cleanup."
  - "Read order: capability digest -> current authorization -> safe metadata -> HEAD bound -> exact body -> SHA-256 -> strict YCSE parse/GCM tag -> copied plaintext response."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-002, OBL-CRYPTO-STORAGE-003]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 28: Bounded Encrypted Object Lifecycle Summary

**Five-purpose registration objects now encrypt completely before private storage and return bytes only after capability, authorization, bounded storage validation, checksum agreement and full GCM authentication.**

## Accomplishments

- Added a JDBC repository for protected-object metadata, operation states and digest-only capability rows without storing or returning raw capabilities, URLs, buckets, ciphertext or provider diagnostics.
- Added one object lifecycle owner that allocates opaque `pobj_v1_*` and operation IDs, enforces declared and actual purpose limits before cryptography, encrypts before every put, and records safe metadata only after the store accepts the complete envelope.
- Added immediate split-write deletion plus retryable ORPHANED/REPLACED/EXPIRED reconciliation, including safe handling when metadata or store cleanup fails.
- Added capability-first reads that do not query object metadata, issue HEAD, fetch a body or unwrap a key until capability digest and current authorization both pass.
- Added HEAD and persisted-metadata agreement before body allocation, exact bounded ciphertext retrieval, independent SHA-256 comparison and strict YCSE/GCM validation before constructing any response plaintext.
- Added an application-relative capability endpoint that emits only the authenticated byte array with `no-store`, `no-cache` and `nosniff`, and maps all failure classes to stable cause-free responses.

## Task Commit

1. **Task 1: Implement encrypted object lifecycle and access endpoint** — `e279102`

## Lifecycle and Fault Matrix

| Boundary | Enforced behavior | Fault result |
| --- | --- | --- |
| Plaintext input | All five purposes accept their exact 5 MiB or 10 MiB ceiling; absent length reads at most ceiling plus one | Declared over-limit reads zero bytes; actual over-limit, short/long declarations and u32 overflow fail before wrap/store |
| Envelope creation | A 32-byte key reference produces the exact 5,243,025 or 10,485,905 maximum complete envelope | Every envelope is YCSE/v1 ciphertext before `put`; no plaintext or unbounded stream reaches storage |
| Metadata commit | Purpose, opaque locator, SHA-256, size and media must exactly match the just-written envelope | Invalid store results are contained; metadata failure triggers immediate delete or an opaque orphan record tied to the operation |
| Authorized read | Capability digest and current policy both pass before metadata; metadata and HEAD agree before GET | Denial produces no metadata lookup, HEAD, GET or unwrap |
| Body authentication | Exact body length, persisted/store/body SHA-256, strict header/provider/key lengths and complete GCM tag pass | Short/extra/oversized body, checksum mismatch, malformed header and tag tamper return no plaintext |
| Replacement/delete | New metadata commits before old ciphertext cleanup; retryable states remain enumerable | Cleanup failures retain only safe metadata for bounded reconciliation |
| HTTP response | Fully materialized authenticated bytes, admitted media type, no-store/no-cache/nosniff | 403/422/503 responses contain stable category/message only and no request or storage detail |

## Verification

- `mvn -f core/pom.xml -Dtest='ProtectedObjectServiceTest,ProtectedObjectAccessControllerTest' test` — PASS: 20 tests, no failure, error or skip.
- `mvn -f core/pom.xml -Dtest='ProtectedObjectServiceTest,ProtectedObjectAccessControllerTest,ObjectCapabilityServiceTest,S3PrivateObjectStoreAdapterTest,ProtectedFieldCodecTest,EnvelopeCodecTest' test` — PASS: 95 tests, no failure or error; the existing opt-in real MinIO case remained skipped outside its profile.
- `mvn -f core/pom.xml test` — PASS: 189 tests, no failure or error; 11 existing real-service lanes remained behind their explicit opt-in profiles.
- Public URL/presigner scan over the three production files — PASS: no S3 presigner, public ACL, direct URL, redirect or storage-URL operation.
- Bounded-read scan over the three production files — PASS: no `readAllBytes`, `transferTo` or `available` body shortcut.
- Sensitive logging/error scan over the three production files — PASS: no logger, stack trace, console output or dynamic sensitive exception construction.
- Compiled public API leak scan for bucket, storage key, ciphertext, provider, public URL and presigner methods — PASS.
- Stub scan and `git diff --check` — PASS.

## Decisions Made

- Reused `ObjectCapabilityService.authorizeAndFetch` without changing the established capability token or digest contract. Its downstream supplier encloses every storage operation, which makes denial-before-fetch mechanically testable.
- Kept the opaque store locator package-private even inside the public repository metadata type. The controller receives only protected-object identity, media type and already authenticated plaintext bytes.
- Compared the persisted checksum, HEAD/GET checksum and a freshly computed body checksum before GCM processing. This keeps storage integrity and cryptographic authenticity as independent fail-closed gates.
- Used a conditional controller activation instead of creating a partial production service with missing key/store collaborators. When the full composition is absent, no object access route is registered; when present, the existing deny-all authorization remains the default.

## Deviations from Plan

None - the plan was executed inside the five declared files with no schema, dependency, configuration or existing-interface change.

## Issues Encountered

- The first focused run stopped in test compilation because four assertions used AssertJ methods unavailable in this repository version. The assertions were rewritten using compatible byte-search and direct string checks; the next focused run passed all service tests, and the final focused run passed both service and controller suites.

## Known Stubs

None. The conditional endpoint activation is an intentional fail-closed composition rule, not placeholder behavior. Real MySQL/MinIO/PKCS11 composition and staged registration API proof remain assigned to Plans 17, 29 and 30 and are not claimed here.

## Threat Surface Review

- **Information disclosure:** ciphertext is created before store access; public values redact locator/checksum/body fields; errors and endpoint responses contain no capability, binding, bucket, key, URL, ciphertext or provider text.
- **Tampering:** stored metadata, HEAD/GET metadata, independently computed SHA-256, strict YCSE header and full GCM tag all must agree before plaintext exists at the controller boundary.
- **Elevation of privilege:** the existing purpose-bound capability digest and production deny-all authorization execute before repository/store/key access.
- **Repudiation:** each create owns one opaque operation ID, and uncertain split writes become a deterministic delete or retryable safe-metadata reconciliation record.
- **Denial of service:** exact five-purpose limits, declared/actual checks, u32 rejection, bounded limit-plus-one input, HEAD bounds and exact body reads precede length-derived allocation.

The only new network surface is the planned application-mediated GET route. It has no redirect, public/direct object operation, presigner or raw storage response.

## Verification Boundary

- The default backend lane does not execute the opt-in real MySQL, MinIO or SoftHSM fixtures. Plan 30 owns the combined production-adapter proof and exact cleanup evidence; this summary does not substitute deterministic tests for that evidence.
- Plans 17 and 29 still own staged session/upload admission, multipart media-signature validation and atomic tenant claim composition.

## Remaining Scoped TODO State

The authoritative Phase 03 scoped TODO query reports 22 open rows and is not empty. No Phase 03 obligation or requirement checkbox was changed by this plan, and `requirements-completed` remains empty.

## Self-Check: PASSED

- All five planned production/test files and this summary exist.
- Task commit `e279102` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused, affected and default backend tests pass; public URL/presigner, bounded-read, sensitive-error, compiled public API, stub and diff scans pass.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty` unchanged.
- The scoped TODO query reports 22 open rows; no Phase 03 obligation or requirement was checked early.
