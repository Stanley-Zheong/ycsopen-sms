# Phase 03 Decision Record

**Status:** locked for execution
**Completion metric:** authoritative Phase-3 TODO set is empty
**Scope:** crypto-storage-bootstrap only

## DR-P03-001 — YCSE/v1 capacity and in-place representation

- **Decision:** YCSE/v1 uses provider ID `pkcs11` (6 bytes), a canonical key reference of at most 32 ASCII bytes, 19 header bytes, 12-byte wrap nonce, 48-byte wrapped DEK, 12-byte data nonce, and 16-byte data tag. Maximum overhead is 145 bytes. A V1 `VARBINARY(255)` cell therefore accepts at most 110 plaintext bytes.
- **Evidence:** `03-RESEARCH.md` “Open Questions (RESOLVED)” capacity table; V1 declares every reversible protected DB cell as `VARBINARY(255)`; PRD declares phone as 11 digits and identity number as 18 characters.
- **Alternatives rejected:** shrinking authenticated metadata, truncating values, context-free side tables, editing V1, or silently assigning a current target to a future owner.
- **Consequences:** phone and identity targets fit. Credential writers enforce a 110-byte UTF-8 ceiling. Any existing larger value, noncanonical key reference, or real-MySQL binding mismatch is a blocking TODO and prevents OBL-001/004 evidence.
- **Scope:** all V1 reversible protected DB targets; no cross-owner DDL.

## DR-P03-002 — Production adapter and SoftHSM evidence boundary

- **Decision:** the production key port is Java 21 SunPKCS11 over PKCS#11. SoftHSM 2.7.0 is protocol-conformance evidence, not physical-HSM certification.
- **Evidence:** official immutable GitHub release tag `2.7.0`, commit `13e6e86`; admitted codeload archive SHA-256 `be14a5820ec457eac5154462ffae51ba5d8a643f6760514d4b4b83a77be91573`; Oracle Java 21 SunPKCS11 documentation cited in research.
- **Alternatives rejected:** application-held KEK bytes, deterministic production fallback, an unselected vendor KMS SDK, moving package-manager/source fallback, or claiming SoftHSM as certified hardware.
- **Consequences:** provisioning must verify the exact archive before extraction/build, install into a run-owned destination, identify the exact library and CLI, initialize a run-owned token, test mechanisms/key attributes, and clean every run-owned resource. Deployment supplies its vendor module/token/HA/IAM binding through the same fail-closed port.
- **Scope:** key protection and blind-index operations only.

## DR-P03-003 — Current executable surface closure

- **Decision:** every current executable reader/writer listed in the research surface table is adopted now or remains a blocking TODO. `DEFERRED_OWNER` applies only to future, non-executable surfaces.
- **Evidence:** source search over JPA mappings, repository calls, service saves, protected setters, request DTOs, and direct URLs found message submission, tenant registration/state/analytics, auth user hydration, API-key lookup, and blacklist lookup surfaces.
- **Alternatives rejected:** assuming an unused getter is safe, treating an `_encrypted` column name as evidence, allowing ORM string coercion, or using a writer fence as a substitute for repairing repository code.
- **Consequences:** the inventory/source fence runs before evidence generation. OBL-001 production rejects every current or migratable `DEFERRED_OWNER`, unknown writer/reader, unresolved row, capacity conflict, or stale deployment writer.
- **Scope:** current repository plus deployment writer-version preflight; no claim about uninspected production data.

## DR-P03-004 — Fail-closed inventory classification

- **Decision:** final evidence permits no `REVIEW_REQUIRED`. Explicit PRD-protected data is `PROTECTED`; password hashes are `EXCLUDED_PHASE_5_HASH`; named non-encrypted business fields require `NOT_PROTECTED_WITH_REASON`; credential-bearing URLs are blocking; future non-executable raw/business surfaces may use owner-specific `DEFERRED_OWNER`.
- **Evidence:** PRD 6.2.1 protected classes, V1 comments, current source surface enumeration, and obligation trace.
- **Alternatives rejected:** ignore regexes, implicit exemptions, broadening Phase 3 into password/RBAC work, or allowing ambiguity to coexist with PASS evidence.
- **Consequences:** unknown candidates and ambiguous runtime rows fail the inventory and evidence producers. The exact-four producer refuses OBL-001 when any current/migratable target is deferred or unresolved.
- **Scope:** Phase-3 inventory and evidence acceptance.

## DR-P03-005 — Pre-Phase-6 object authorization seam

- **Decision:** protected object access crosses a narrow server-side authorization port bound to object, tenant, subject, purpose, capability state, and expiry; production defaults to deny.
- **Evidence:** Phase boundary assigns storage/temporary access to Phase 3 and RBAC/reveal/audit to Phase 6.
- **Alternatives rejected:** raw S3 URLs, always-allow production beans, UI/RBAC completion claims, or fetching bytes before authorization.
- **Consequences:** tests inject explicit allow/deny policies and prove denial before fetch. Phase 6 later binds current RBAC/reveal policy without replacing storage semantics.
- **Scope:** storage access seam only.

## DR-P03-006 — Tenant registration is a current protected writer

- **Decision:** the existing registration endpoint must persist all declared identity/contact inputs through the protected field boundary and all five submitted proof objects through protected object IDs. Raw URL inputs are not accepted as durable references.
- **Evidence:** `TenantRegistrationRequest` currently accepts legal representative ID, both ID images, contact ID/phone, business license, short-link proof, and trademark proof; `TenantService` currently drops five protected values and writes three raw URL fields.
- **Alternatives rejected:** deferring the live endpoint to Phase 8, keeping raw URLs, or claiming object protection from an unused storage port.
- **Consequences:** registration service/controller/entity/response tests are part of OBL-001/002 closure; Phase 8 remains owner of qualification workflow/UI rather than the cryptographic storage boundary.
- **Scope:** storage representation of the current endpoint only.

## DR-P03-007 — Blind indexes use Phase-owned multi-version metadata

- **Decision:** all new keyed mobile indexes are canonical 33-byte version-plus-HMAC values encoded as 53 lowercase Base32 characters and stored in Phase-owned `ycs_crypto_blind_indexes`; no legacy `CHAR(64)` cell stores two versions or a new raw mobile SHA-256. The table binds `target_type`, `legacy_row_id`, `field_id`, `key_version`, `index_value`, and `status` with one row per logical target/key version. Its unique key is `(target_type, legacy_row_id, field_id, key_version)` and its lookup key is `(target_type, field_id, status, key_version, index_value)`. The polymorphic legacy binding cannot have a cross-table SQL foreign key, so the reviewed target manifest, same-transaction existence check, original-row digest, and orphan reconciliation are the mandatory binding proof; `key_version` is foreign-keyed to the Phase-owned key-reference registry.
- **Compatibility:** HMAC input schema `mobile-sha256-v1` first normalizes an 11-digit mobile, computes the historical 32-byte SHA-256 in memory, and HMACs that value with target/field/purpose context. This lets migration recompute indexes even for an audit row that retained only the historical digest. Readers compute every ACTIVE and RETIRING key version, query the metadata table with an `IN`/union, and may union a raw-SHA legacy query only while that exact target checkpoint is not `COMPLETE`. A completed target rejects legacy fallback.
- **Migration:** the exact current legacy-index targets are `mobile_portability.mobile_hash`, `blacklist_entries.mobile_hash`, `third_party_risk_check_logs.mobile_hash`, `message_tasks.mobile_hash`, and `unsubscribe_records.mobile_hash`; protected mobile columns without a `mobile_hash` are field-encryption targets but not invented equality indexes. Each target advances `DISCOVERED -> BACKFILLED -> VERIFIED -> CUTOVER -> SCRUBBED -> COMPLETE`. Backfill inserts metadata idempotently; cutover requires a compatible deployed-writer fence; scrub replaces legacy `CHAR(64)` raw digests with non-queryable row-binding locators and atomically updates any locator-based metadata binding. Blacklist and portability targets must prove query hits before backfill, during dual-read compatibility, and after HMAC-only cutover on real MySQL. Concurrent legacy writes, checkpoint drift, duplicate key-version rows, or missing bindings block advancement and leave the earlier reader mode intact.
- **Legacy implementation:** the global `HashUtil` API is not a production query owner. Before every current target is complete, the only raw-SHA computation is an explicitly named `LegacyMobileHashReader` guarded by the target checkpoint; after completion it is unreachable and removable. New writes persist a non-queryable 64-character row locator in required legacy columns and persist each current write-compatible ACTIVE/RETIRING HMAC version as a separate metadata row. Mixed-version writer compatibility ends only when the signed writer fence and per-version backfill prove older readers are absent; no logical value is concatenated into one cell.
- **Evidence:** migration and rotation tests prove one metadata row per active/retiring version, no single-cell dual write, fallback disabled after completion, rollback to the last verified checkpoint before cutover, and forward recovery from an encrypted snapshot after scrub.
- **Scope:** Phase-owned metadata and compatibility logic only; V1 DDL remains immutable.

## DR-P03-008 — Migration preflight consumes signed, replay-resistant manifests

- **Decision:** production `WriterFencePort` and `EncryptedSnapshotVerifier` implementations read bounded canonical JSON manifests and detached Ed25519 signatures from explicit canonical CLI paths. The X.509 public key is a non-secret deployment input whose SHA-256 fingerprint must equal `ycsopen.crypto-storage.migration.manifest-public-key-sha256`; symlinks, non-regular files, duplicate/unknown JSON fields, noncanonical encoding, unbounded files, fingerprint drift, and invalid signatures fail closed.
- **Writer manifest:** schema `ycs-writer-fence/v1` binds environment, schema, Flyway-set digest, issued marker, expiry marker, strictly increasing sequence, and a nonempty unique writer set of artifact ID, version, source digest, and migration-compatibility declaration. Empty, unknown, stale, future, duplicate, replayed, environment-mismatched, schema-mismatched, or forged sets are rejected. The last accepted sequence and digest are stored in Phase-owned migration state before any target can mutate.
- **Snapshot manifest:** schema `ycs-encrypted-snapshot/v1` binds environment, database-instance fingerprint, schema, Flyway-set digest, private snapshot URI, SHA-256 content digest, encryption key reference, completed marker, and strictly increasing sequence. It must be signed by the same trust root, reference an admitted nonextractable recovery key, identify the exact pre-migration database/Flyway subject, be fresh and unreplayed, and verify the encrypted snapshot digest before mutation.
- **Command:** `phase03-migration preflight --writer-manifest PATH --writer-signature PATH --snapshot-manifest PATH --snapshot-signature PATH --environment ID --database-instance-fingerprint HEX --schema NAME --flyway-set-digest HEX` is the only production admission command. The public-key path and expected fingerprint come from `ycsopen.crypto-storage.migration.manifest-public-key-path` and `ycsopen.crypto-storage.migration.manifest-public-key-sha256` and cannot be overridden by manifest content. Exit codes are fixed: `0` accepted, `20` invalid invocation/path, `21` canonical/schema failure, `22` signature/fingerprint failure, `23` environment/database/Flyway mismatch, `24` empty/unknown/stale/replay writer set, `25` invalid/unavailable encrypted snapshot, and `26` key/provider preflight failure. Any nonzero result precedes leases, checkpoints, events, or legacy updates and must leave every business-table update count at zero.
- **Scope:** deployment/migration admission only; no secret signing key enters the repository or application.

## DR-P03-009 — Tenant evidence uses staged protected-object IDs

- **Decision:** tenant registration never accepts, fetches, or persists external evidence URLs. The fixed flow is: create an opaque registration-object session, upload each object through a multipart endpoint, then submit registration JSON containing only the session ID and five purpose-bound `pobj_v1_*` IDs. Staged objects are tenant-draft/purpose bound, single-claim, non-reusable, and atomically claimed with tenant persistence.
- **API:** `POST /api/v1/console/tenants/registration-object-sessions` returns a session ID, one-time upload token, and expiry marker. `POST /api/v1/console/tenants/registration-object-sessions/{sessionId}/objects/{purpose}` accepts exactly one multipart part named `file` plus `X-Registration-Upload-Token`; it returns the opaque protected-object ID and purpose. `POST /api/v1/console/tenants/register` accepts the session ID and object ID fields `businessLicenseObjectId`, `legalRepIdFrontObjectId`, `legalRepIdBackObjectId`, `shortlinkDomainProofObjectId`, and `trademarkProofObjectId`; the token remains in the header and is never stored or echoed.
- **Object rules:** business license is required and accepts PDF/JPEG/PNG up to 10 MiB; legal-representative front/back are required and accept JPEG/PNG up to 5 MiB each; short-link-domain proof and trademark proof are optional and accept PDF/JPEG/PNG up to 10 MiB each. Server-side signature sniffing must match the declared media type. A session has at most one current STAGED object per purpose, uses configured `PT24H` expiry, and transitions objects through `STAGED -> CLAIMED` or `STAGED -> EXPIRED/ORPHANED -> DELETED`. A failed tenant transaction leaves objects staged for deterministic reconciliation; object-store success plus metadata failure is reconciled by operation ID.
- **Compatibility and errors:** legacy `*Url` JSON fields and any `http(s)` value are rejected with HTTP 422 and code `LEGACY_OBJECT_URL_NOT_ACCEPTED`; missing required IDs use `REGISTRATION_OBJECT_REQUIRED`; wrong session/purpose/tenant-draft, reused/expired ID, media mismatch, oversize, and partial claim have stable distinct 4xx codes. Unknown JSON fields fail closed. No silent legacy acceptance or remote URL fetch exists.
- **Documentation:** `core/docs/API.md` and `docs/使用手册.md` must match the runtime DTO, endpoints, media/size rules, lifecycle, error codes, and old-client rejection behavior.
- **Scope:** protected upload/storage/claim semantics for the existing endpoint only; qualification workflow, UI, RBAC, and reveal policy remain later phases.
