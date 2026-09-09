# Design

Schema migrations: declared

## Four vertical slices

1. Registration: protected upload session, provider-backed phone code, complete validated fields, disabled pending administrator, safe status projection.
2. Review: list/detail, internal one-time evidence capability, OCR inspection result, mandatory human confirmation, three attributable decisions, first-approval access/trial creation.
3. Maintenance: platform business-metadata edit and tenant recertification; certification-bound change immediately returns to `PENDING` and blocks new work.
4. Operating status: reuse `tenant_accounts.status` (`NORMAL`, `DISABLED`, `ARREARS_FROZEN`) plus immutable safe events; the shared eligibility policy guards HTTP submission before template/routing/billing work.

## Validation and protected data

`TenantQualificationValidator` owns the 18-character unified-social-credit-code checksum, Chinese resident identity checksum, lengths, date, conditional trademark proof, and normalizations. Database uniqueness remains the race-safe final credit-code boundary. Phase 03 remains the only field/object encryption implementation. The legal representative image ceiling is corrected from 5 MiB to the catalog-required 10 MiB in the existing closed envelope/upload targets, with the prior Phase 03 security tests updated rather than copied.

Contact verification stores a bounded challenge ID, encrypted phone envelope, bcrypt code hash, expiry, attempt counter, verified/consumed timestamps, and request-IP rate data. The code is delivered only through `PlatformMessageBootstrapService`; no response, log, event, or evidence contains it. Verification is one-time, expires, burns bounded failed attempts, and is atomically consumed by registration/recertification for the same phone.

## OCR assistance and human review

Phase 08 adds a narrow HTTP inspection adapter, not an OCR engine. The service uses an internal one-time protected-object capability to decrypt the business-license object, sends the bounded bytes to the configured inspection provider, and persists only extracted company name, credit code, confidence/status, and provider request ID. A local provider sandbox exercises the real adapter contract. Approval requires `COMPLETED` inspection plus an explicit human-confirmed flag; provider output never auto-approves. Provider absence/failure is visible and blocks approval without losing the application.

## Evidence access

The browser never receives storage locators, protected object IDs, or capability tokens. An authorized review-content endpoint derives tenant, object, purpose, and reviewer subject server-side, issues a one-time capability, immediately consumes it through `ProtectedObjectService`, and returns validated bytes with no-store/nosniff headers. `QualificationObjectAuthorization` rechecks exact reviewer permission at read time and permits only the fixed system OCR subject for OCR access. Phase 06 captures the authenticated HTTP operation.

## State and concurrency

Certification transitions are `UNVERIFIED|REJECTED|SUPPLEMENT_REQUIRED|VERIFIED → PENDING` on submit/recertify, then `PENDING → VERIFIED|REJECTED|SUPPLEMENT_REQUIRED` by review. Review and operating actions lock the current row or use an expected revision so two stale decisions cannot both win. First approval changes `SUBMITTED → TRIAL`, enables the one pending tenant administrator, and creates exactly one account. Recertification never rewrites an old decision and does not change an existing commercial lifecycle; non-`VERIFIED` certification still blocks new work.

`TenantEligibilityPolicy` allows new work only when certification is `VERIFIED`, lifecycle is `TRIAL` or `SIGNED`, and tenant-account status is `NORMAL`. Historical qualification/event reads do not call this policy. Signature/template application endpoints do not exist yet; Phases 12–13 must call the same policy when they create them, and Phase 08 does not pre-build those modules.

## Persistence and audit

V1700 expands tenant fields/status and creates only two new stores: bounded contact challenges and immutable `tenant_qualification_events`. Events hold action, before/after certification/lifecycle/account status, changed field names, safe reason, actor, and timestamp—never protected values, code hashes, tokens, object IDs, or OCR bytes. Database triggers reject event UPDATE/DELETE. V1701 adds exact menu/read/review/update/status/evidence permissions. Structural request audit remains Phase 06-owned.

## Failure semantics

Validation and expired/invalid verification return stable 400/409/410/422 codes without consuming protected claims incorrectly. Stale review/status returns 409 and retains dialog input. Provider or protected-object failure returns 503 and leaves the application pending. Authorization returns 403 without tenant fields/evidence. Duplicate credit code returns 409 from both precheck and database-race mapping. All responses use safe DTOs.
