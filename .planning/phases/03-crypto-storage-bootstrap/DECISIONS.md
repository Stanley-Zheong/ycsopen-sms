# Decisions

## Authority

`03-DECISIONS.md` is the canonical execution decision record. This extension restates its locked outcomes for entry and does not create alternate decision IDs or weaken any constraint.

## DR-P03-001 — YCSE/v1 capacity and representation

Accepted. `ENVELOPE-CONTRACT.md` is the single canonical binary/AAD/allocation contract. The exact maximum overhead is 145 bytes, leaving 110 plaintext bytes in a V1 `VARBINARY(255)` cell; registration-object limits are 5 MiB or 10 MiB. MySQL recovery uses independently authenticated `mysql-encrypted-snapshot-chunk` envelopes capped at 10 MiB each, with a 1 TiB plaintext/104,858-chunk/1,099,526,832,186-byte total-envelope hard bound and bounded streaming in both directions. The complete canonical header, including declared lengths, provider and key reference, is authenticated under distinct data and wrap domains. Any over-capacity row/object/snapshot, ordering or length overflow, noncanonical reference or Connector/J mismatch blocks evidence. No whole-dump allocation, plaintext dump file, truncation, alternate hidden envelope or legacy DDL is allowed.

## DR-P03-002 — Production PKCS#11 boundary

Accepted. Production uses Java 21 SunPKCS11 with operator-provided PKCS#11 module/token and nonextractable KEK/HMAC keys. Local protocol proof uses the pinned SoftHSM 2.7.0 source identity and isolated token lifecycle. `KeyProtectionPort.wrap` is the only caller-visible operation; the production adapter alone performs durable V1200 reservation, then wrap-nonce generation, then provider invocation, returning one immutable wrapped result. The codec cannot reserve or supply a wrap nonce. The counter marks `ROTATION_REQUIRED` at 983,040 operations and rejects new wraps at 1,048,576; failed operations consume their reservation. Deterministic adapters are test-only and cannot satisfy production-provider evidence.

## DR-P03-003 — Current executable surface closure

Accepted. Every current reader/writer identified by source inventory is adopted in this phase or remains a blocking TODO. `DEFERRED_OWNER` is permitted only for a future non-executable surface. Message submission, tenant registration/state/analytics, user hydration, API-key lookup and blacklist lookup are current surfaces.

## DR-P03-004 — Fail-closed inventory

Accepted. Final inventory has no `REVIEW_REQUIRED`. Protected fields/objects, Phase 5 password-hash exclusions, reasoned non-protected fields, future non-executable ownership and blocking rows are explicit. Credential-bearing URLs, unknown candidates, capacity conflicts and ambiguous runtime rows block production acceptance.

## DR-P03-005 — Object authorization seam

Accepted. Object access is bound to object, tenant, subject, purpose, capability state and expiry through a server-side port whose production default denies. Capability and registration-upload secrets use `OpaqueTokenDigestPort` with distinct nonextractable PKCS11 HMAC aliases/domains, ACTIVE-only issue, stored ACTIVE/RETIRING constant-time verification and zero-live-reference retirement; neither may use the mobile blind-index key. Phase 6 later supplies RBAC/reveal policy without replacing storage semantics. No always-allow bean or public/direct S3 link is accepted.

## DR-P03-006 — Tenant registration is a current writer

Accepted. Registration protects legal-representative/contact fields and all five evidence objects through explicit boundaries. No input is dropped and no raw URL is durably stored. Phase 8 retains qualification workflow/UI ownership.

## DR-P03-007 — Multi-version blind-index metadata

Accepted. Canonical blind indexes are 33 binary bytes encoded as 53 lowercase Base32 characters and stored one row per target/field/key version in Phase-owned metadata. ACTIVE and RETIRING versions are queried as a set. Raw-SHA compatibility is owned by an explicit checkpoint-gated reader and is unreachable after COMPLETE. Legacy cells contain only non-queryable row locators after scrub; no cell packs multiple versions. `bulk_sending_items.mobile_encrypted` and `uplink_records.mobile_encrypted` remain required encryption/migration targets but have no V1 hash companion or equality-query contract, so they are explicitly excluded from blind-index metadata and may not disappear from protected inventory/evidence.

## DR-P03-008 — Signed migration admission

Accepted. Writer and encrypted-snapshot manifests use machine-readable schemas, bounded canonical JSON and role-separated detached Ed25519 signatures. They share one `migration_set_id`, exact environment/database/schema/Flyway subject, global sequence and signer version. Their individual canonical digests form one signed pair digest that is compare-and-set atomically with both role digests; cross-pair splice, separate replay, same-sequence change and half-admission fail closed. A pinned set has one ACTIVE and explicit RETIRING anchors; unknown/retired/revoked anchors fail closed and compromise invalidates the affected accepted pair. The snapshot manifest binds the ordered canonical chunk inventory and totals. The checked-in CLI contract owns golden help and exits. Manifest verification cannot close recovery without a bounded-streaming real encrypted MySQL snapshot creation-and-fresh-schema restore drill.

## DR-P03-009 — Staged tenant evidence

Accepted. Registration evidence uses an opaque session, a session-bound repeat-use upload token, purpose-bound multipart uploads, opaque protected-object IDs and atomic single claim. One token supports sequential upload of all five purposes and same-purpose replacement only inside its OPEN tenant-draft/session; an atomic reservation before encryption/store caps admitted attempts at three per purpose and fifteen per session, burns post-reservation failures and returns `REGISTRATION_UPLOAD_LIMIT_REACHED` on concurrent overrun. Expiry, explicit close or successful claim invalidates it. The five purpose/media/size/required rules, configured `PT24H` expiry, orphan reconciliation and stable errors are part of runtime/API-document parity. Legacy URL fields and URL-shaped values are rejected and never fetched.

## Completion and ownership rule

Accepted. Completion has one metric: the declared scoped TODO set is empty after executable evidence, independent reviews and remote delivery attestation. V1200 claims only `ycs.sms.crypto-storage-bootstrap.*`; V1 DDL remains immutable; missing real MySQL, SoftHSM or MinIO proof cannot be replaced by mocks or status prose.
