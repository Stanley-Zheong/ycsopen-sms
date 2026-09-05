---
phase: 03-crypto-storage-bootstrap
plan: "17"
subsystem: registration-object-api
tags: [multipart, opaque-token, jdbc, protected-object, atomic-admission, spring-mvc]

requires:
  - phase: 03-crypto-storage-bootstrap-05
    provides: Purpose-separated versioned opaque-token digest port
  - phase: 03-crypto-storage-bootstrap-11
    provides: Registration-session, attempt, protected-object and operation schema
  - phase: 03-crypto-storage-bootstrap-28
    provides: Bounded encrypted protected-object lifecycle and reconciliation
provides:
  - Fixed private create/upload/close registration-object routes
  - One session-bound repeat-use credential for all five upload purposes
  - Atomic three-per-purpose and fifteen-per-session admitted-attempt reservation
  - OPEN-only replacement with burned post-reservation failures
  - Runtime-parity API and operator documentation
affects: [03-20-key-lifecycle, 03-29-tenant-registration-protection, 03-30-real-object-proof]

tech-stack:
  added: []
  patterns:
    - Validate multipart shape, media, declared size and magic bytes before atomic reservation
    - Serialize both purpose and session admission ceilings under the locked session row
    - Persist only REGISTRATION_UPLOAD versioned digest and server-side binding
    - Return only opaque session/object identity, wire purpose and expiry marker

key-files:
  created:
    - core/src/main/java/com/ycsopen/sms/core/common/security/object/TenantRegistrationObjectSessionService.java
    - core/src/main/java/com/ycsopen/sms/core/web/controller/TenantRegistrationObjectController.java
    - core/src/test/java/com/ycsopen/sms/core/web/TenantRegistrationObjectApiTest.java
  modified:
    - core/docs/API.md
    - docs/使用手册.md

key-decisions:
  - "The token lookup component is the canonical Base64url form of the random session UUID bytes, so the route session and credential lookup are inseparably bound without storing another identifier."
  - "The session row lock serializes the per-purpose and whole-session counters in one transaction; every admitted downstream outcome consumes its slot."
  - "Media type, declared length and PDF/JPEG/PNG signature validation precede admission, while actual-length/envelope/store failures after reservation burn the slot."
  - "Terminal expiry updates commit before the stable boundary error is returned, instead of being rolled back with an exception."

patterns-established:
  - "Upload order: exact multipart -> purpose/media/size/magic -> token lookup/binding/digest -> atomic reservation -> protected object create -> opaque response."
  - "Session states: OPEN -> CLAIMED | CLOSED | EXPIRED; no terminal state permits token reuse."

requirements-addressed: [REQ-NFR-DATA-PROTECTION, OBL-CRYPTO-STORAGE-001, OBL-CRYPTO-STORAGE-002]
requirements-completed: []
completion-metric: scoped_todo_empty
---

# Phase 03 Plan 17: Staged Registration Evidence Upload Summary

**A single purpose-separated session credential now drives five private multipart uploads, OPEN replacement and atomically bounded admission without exposing storage references.**

## Accomplishments

- Added the fixed session create, purpose-bound multipart upload and explicit close routes with `no-store` responses and stable cause-free errors.
- Added `regup_v1_` issue and verification using only `OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD`, a tenant-draft/session binding, ACTIVE-only issue and stored ACTIVE/RETIRING verification.
- Added exact-one-file multipart enforcement plus purpose-specific media, plaintext, complete-envelope and signature rules before reservation.
- Added one transactionally locked reservation boundary that cannot exceed three attempts per purpose or fifteen per session and never releases a reserved slot after downstream failure.
- Added OPEN-only same-purpose replacement, terminal claim/close/expiry invalidation, cross-session/cross-draft rejection and capability-domain rejection.
- Updated both API documents with the same routes, states, purposes, limits, lifecycle, error names, privacy boundary and legacy rejection contract used by runtime tests.

## Task Commit

1. **Task 1: Implement the fixed staged evidence upload API and documentation** — `f258adf`

## Route and Response Matrix

| Method | Route | Admitted request | Returned data |
| --- | --- | --- | --- |
| POST | `/api/v1/console/tenants/registration-object-sessions` | No request body | `registrationObjectSessionId`, one `regup_v1_` credential, `expiresAt` |
| POST | `/api/v1/console/tenants/registration-object-sessions/{sessionId}/objects/{purpose}` | One `file` multipart part and `X-Registration-Upload-Token` | `pobj_v1_*`, wire purpose, `expiresAt` |
| DELETE | `/api/v1/console/tenants/registration-object-sessions/{sessionId}` | Matching `X-Registration-Upload-Token` | `CLOSED` state |

No request DTO or controller parameter admits URL, bucket, key, public or presigned storage references. The response records contain no store locator, provider identity, ciphertext or token except the intentional one-time credential returned by session creation.

## State and Binding Matrix

| Session condition | Upload credential result | Object effect |
| --- | --- | --- |
| `OPEN`, matching tenant-draft/session, ACTIVE/RETIRING stored version | Accepted after preflight and reservation | Creates current `STAGED` object |
| `OPEN`, same purpose already has current `STAGED` object | Accepted until the purpose limit | New object replaces the current object by operation-backed reconciliation |
| `CLAIMED` | Rejected | No reservation or object mutation |
| `CLOSED` | Rejected | Remaining `STAGED` objects become reconciliation candidates |
| At or after expiry | Atomically transitions to `EXPIRED`, then rejects | Remaining `STAGED` objects become reconciliation candidates |
| Cross session or cross tenant-draft | `REGISTRATION_UPLOAD_TOKEN_INVALID` | No reservation or object mutation |
| Capability-domain, unknown, RETIRED or REVOKED digest version | `REGISTRATION_UPLOAD_TOKEN_INVALID` | No reservation or object mutation |

## Exact Purpose Limits

| Wire purpose | Media | Maximum plaintext | Maximum complete envelope | Attempts |
| --- | --- | ---: | ---: | ---: |
| `business-license` | PDF/JPEG/PNG | 10,485,760 | 10,485,905 | 3 |
| `legal-rep-id-front` | JPEG/PNG | 5,242,880 | 5,243,025 | 3 |
| `legal-rep-id-back` | JPEG/PNG | 5,242,880 | 5,243,025 | 3 |
| `shortlink-domain-proof` | PDF/JPEG/PNG | 10,485,760 | 10,485,905 | 3 |
| `trademark-proof` | PDF/JPEG/PNG | 10,485,760 | 10,485,905 | 3 |

The session ceiling is 15 admitted attempts and the configured TTL is `PT24H`. Purpose and envelope constants are asserted against the existing protected-object envelope targets at class initialization and in documentation-parity tests.

## Stable Error Matrix

| HTTP | Code | Boundary |
| ---: | --- | --- |
| 422 | `REGISTRATION_UPLOAD_INPUT_INVALID` | Malformed session, purpose or multipart shape |
| 403 | `REGISTRATION_UPLOAD_TOKEN_INVALID` | Malformed, cross-bound or non-verifying credential |
| 409 | `REGISTRATION_UPLOAD_SESSION_NOT_OPEN` | Claimed or closed session |
| 410 | `REGISTRATION_UPLOAD_SESSION_EXPIRED` | Expired session |
| 415 | `REGISTRATION_UPLOAD_MEDIA_TYPE_NOT_ACCEPTED` | Media not admitted for the purpose |
| 413 | `REGISTRATION_UPLOAD_SIZE_LIMIT_EXCEEDED` | Declared plaintext outside the purpose bound |
| 422 | `REGISTRATION_UPLOAD_SIGNATURE_MISMATCH` | Declared media disagrees with PDF/JPEG/PNG magic |
| 429 | `REGISTRATION_UPLOAD_LIMIT_REACHED` | Purpose or session reservation ceiling reached |
| 503 | `REGISTRATION_UPLOAD_UNAVAILABLE` | Protected-object, persistence or provider boundary unavailable |
| 422 | `LEGACY_OBJECT_URL_NOT_ACCEPTED` | Legacy `*Url` field or HTTP(S)-shaped registration value |

## Verification

- `mvn -f core/pom.xml -Dtest=TenantRegistrationObjectApiTest test` — PASS: 10 tests with no failure, error or skip.
- `mvn -f core/pom.xml test` — PASS: 199 tests with no failure or error; 11 existing real-service lanes remained behind their explicit opt-in profiles.
- Documentation/runtime parity scan — PASS for route, object-field, `PT24H`, capacity, admission and stable-error literals in both documents.
- Endpoint input/response scan — PASS: no URL, bucket, key, public or presigned reference parameter or response record.
- Token/storage leak scan — PASS: no logger, console output, stack trace, storage locator response or provider diagnostic in the new production files.
- Digest-domain scan — PASS: production session/controller code contains `REGISTRATION_UPLOAD` usage and no `BlindIndexPort` or `OBJECT_CAPABILITY` invocation.
- `git diff --check` and created-file checks — PASS.

## Decisions Made

- Derived the token lookup string from the random UUID bytes used for the session route. This makes a token from another session fail before digest verification while retaining only the schema-defined session ID and versioned digest.
- Kept tenant-draft identity server generated and private. It participates in the digest and object bindings but is not exposed as another client-controlled cross-binding surface.
- Used a session-row lock as the serialization point for both counters. A separate purpose counter row is created and locked inside that same transaction, so concurrent callers cannot exceed either declared limit.
- Returned transaction outcomes for expiry and admission denials. This allows the `EXPIRED` state and object reconciliation state changes to commit before the service emits a stable failure.
- Kept controller activation conditional on a complete session-service composition. The real MySQL/MinIO/PKCS11 composition remains owned by Plan 30 and no partial fallback route is registered.

## Deviations from Plan

None - the plan was executed inside the five declared files without schema, dependency, configuration or unrelated source changes.

## Issues Encountered

- The first focused invocation stopped at test compilation because the test adapter used a nonexistent `KeyHealth.Status.HEALTHY`; it was corrected to the repository's `READY` state.
- The next focused invocation identified the intentionally unwritten documentation contract, MockMvc multipart-part enumeration behavior and the repository's numeric Instant serialization. Documentation was added, exact-part detection was made compatible with `MultipartHttpServletRequest`, and tests asserted the expiry marker without imposing a different shared serializer.
- Review of the JDBC expiry path found that throwing inside `TransactionTemplate` would roll back the terminal state. The callback now returns a typed outcome so expiry commits before the stable error leaves the service.

## Known Stubs

None. The conditional endpoint activation is a fail-closed composition boundary. Plan 29 owns atomic tenant persistence/object claim adoption, and Plan 30 owns real MySQL/MinIO/PKCS11 composition evidence; neither is claimed by this plan.

## Threat Surface Review

- **Information disclosure:** token-bearing values redact their string rendering; only session creation intentionally returns the complete credential once; other responses and errors contain no token, storage locator or provider detail.
- **Tampering:** lookup, session, tenant-draft and digest purpose/version must all agree before reservation; cross-domain and cross-binding credentials fail closed.
- **Repudiation:** explicit `OPEN`, `CLAIMED`, `CLOSED` and `EXPIRED` states plus the existing operation-ID object lifecycle make terminal and replacement outcomes deterministic.
- **Denial of service:** exact multipart/media/size/magic checks run before an atomic bounded reservation; reservation ceilings hold under concurrency and downstream failures burn slots.
- **Elevation of privilege:** the upload path calls only the distinct `REGISTRATION_UPLOAD` digest domain and cannot accept an object capability token.

The three new HTTP routes are the planned threat surface from P03-17-T1 through P03-17-T5; no unplanned network, file, schema or authentication surface was introduced.

## Verification Boundary

- The default backend lane does not execute the opt-in real MySQL, MinIO or SoftHSM fixtures. Plan 30 owns combined production-adapter evidence and exact cleanup; deterministic service/MVC tests are not represented as that proof.
- The existing tenant registration JSON adoption remains assigned to Plan 29. This plan fixes and documents its object-field and legacy-rejection contract without checking either owned obligation.

## Remaining Scoped TODO State

The authoritative Phase 03 scoped TODO query reports 22 open rows and is not empty. No Phase 03 obligation or requirement checkbox changed, and `requirements-completed` remains empty.

## Self-Check: PASSED

- All five planned production/test/documentation files and this summary exist.
- Task commit `f258adf` exists on `phase/03-crypto-storage-bootstrap` and contains no tracked deletion.
- Focused and default backend tests plus documentation parity, endpoint reference, token/storage leak, digest-domain, stub and diff scans pass.
- `.planning/STATE.md` retains `milestone_name: YCSOpen SMS v1.0` and `completion_metric: scoped_todo_empty` unchanged.
- The scoped TODO query reports 22 open rows and zero checked rows; no Phase 03 obligation or requirement was checked early.
