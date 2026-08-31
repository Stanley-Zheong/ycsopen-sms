# Decisions

## Authority

`03-DECISIONS.md` is the canonical execution decision record. This extension restates its locked outcomes for entry and does not create alternate decision IDs or weaken any constraint.

## DR-P03-001 — YCSE/v1 capacity and representation

Accepted. The exact maximum overhead is 145 bytes, leaving 110 plaintext bytes in a V1 `VARBINARY(255)` cell. Production provider ID is `pkcs11`; key references are canonical ASCII with at most 32 bytes. Any over-capacity row, noncanonical reference or Connector/J mismatch blocks evidence. No truncation, alternate hidden envelope or legacy DDL is allowed.

## DR-P03-002 — Production PKCS#11 boundary

Accepted. Production uses Java 21 SunPKCS11 with operator-provided PKCS#11 module/token and nonextractable KEK/HMAC keys. Local protocol proof uses the pinned SoftHSM 2.7.0 source identity and isolated token lifecycle. Deterministic adapters are test-only and cannot satisfy production-provider evidence.

## DR-P03-003 — Current executable surface closure

Accepted. Every current reader/writer identified by source inventory is adopted in this phase or remains a blocking TODO. `DEFERRED_OWNER` is permitted only for a future non-executable surface. Message submission, tenant registration/state/analytics, user hydration, API-key lookup and blacklist lookup are current surfaces.

## DR-P03-004 — Fail-closed inventory

Accepted. Final inventory has no `REVIEW_REQUIRED`. Protected fields/objects, Phase 5 password-hash exclusions, reasoned non-protected fields, future non-executable ownership and blocking rows are explicit. Credential-bearing URLs, unknown candidates, capacity conflicts and ambiguous runtime rows block production acceptance.

## DR-P03-005 — Object authorization seam

Accepted. Object access is bound to object, tenant, subject, purpose, capability state and expiry through a server-side port whose production default denies. Phase 6 later supplies RBAC/reveal policy without replacing storage semantics. No always-allow bean or public/direct S3 link is accepted.

## DR-P03-006 — Tenant registration is a current writer

Accepted. Registration protects legal-representative/contact fields and all five evidence objects through explicit boundaries. No input is dropped and no raw URL is durably stored. Phase 8 retains qualification workflow/UI ownership.

## DR-P03-007 — Multi-version blind-index metadata

Accepted. Canonical blind indexes are 33 binary bytes encoded as 53 lowercase Base32 characters and stored one row per target/field/key version in Phase-owned metadata. ACTIVE and RETIRING versions are queried as a set. Raw-SHA compatibility is owned by an explicit checkpoint-gated reader and is unreachable after COMPLETE. Legacy cells contain only non-queryable row locators after scrub; no cell packs multiple versions.

## DR-P03-008 — Signed migration admission

Accepted. Writer and encrypted-snapshot manifests use bounded canonical JSON, detached Ed25519 signatures, configured X.509 public-key fingerprint, exact environment/schema/Flyway/database binding and strictly increasing replay-resistant sequences. The fixed preflight command returns stable exit categories and every rejection precedes lease, event, checkpoint and business-row mutation.

## DR-P03-009 — Staged tenant evidence

Accepted. Registration evidence uses an opaque session, purpose-bound multipart uploads, opaque protected-object IDs and atomic single claim. The five purpose/media/size/required rules, configured `PT24H` expiry, orphan reconciliation and stable errors are part of runtime/API-document parity. Legacy URL fields and URL-shaped values are rejected and never fetched.

## Completion and ownership rule

Accepted. Completion has one metric: the declared scoped TODO set is empty after executable evidence, independent reviews and remote delivery attestation. V1200 claims only `ycs.sms.crypto-storage-bootstrap.*`; V1 DDL remains immutable; missing real MySQL, SoftHSM or MinIO proof cannot be replaced by mocks or status prose.
