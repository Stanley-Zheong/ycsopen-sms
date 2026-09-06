# Phase 03 PR #15 review remediation solution

## Status

This solution is locked before further implementation. PR #15 remains incomplete until every row in `TODO.md` is physically checked with executable evidence.

## Design constraints

- Keep one remediation plan and one correction commit; do not create one plan, summary, evidence JSON, or commit per finding.
- Preserve existing cryptographic and transaction boundaries where they are correct.
- Prefer behavior tests and standard CI artifacts over source-tree hash sealing.
- Phase 03 must not implement Phase 18's complete frequency-control subsystem.
- After implementation, run one independent aggregate GSD review and one Claude review. Only a scoped correctness defect or a new BLOCKER/HIGH reopens implementation; unrelated observations go to the owning future phase.

## Locked solutions

### 1. Legacy message mobile migration is one atomic row transition

`message_tasks.mobile_hash` remains the single migration target/state machine. Do not introduce a second `MESSAGE_TASK_MOBILE` checkpoint.

For each legacy message row, the JDBC boundary locks and returns `id`, `tenant_id`, `message_id`, `mobile_encrypted`, and `mobile_hash` as one typed row. The runner must:

1. require an 11-digit ASCII mobile in `mobile_encrypted`;
2. require its SHA-256 to equal the lowercase legacy `mobile_hash`;
3. protect the mobile with the same `tenant_id + message_id` AAD used by the current writer;
4. generate ACTIVE/RETIRING message blind indexes;
5. atomically publish the YCSE value, a random non-queryable locator, current row-binding digests, indexes, outcome, and checkpoint.

The same operation must converge a half-migrated row that already has legacy indexes but still contains plaintext. COMPLETE requires both zero legacy hashes and zero non-YCSE `message_tasks.mobile_encrypted` values. A mismatch or concurrent change leaves the complete row unchanged.

### 2. Blacklist queries carry two scopes

The transient lookup capability carries two separate index sets:

- `global` for system blacklists;
- `tenant:<currentTenant>` for the current tenant's white/black lists.

The lookup service validates both sets against the same ACTIVE/RETIRING version set, queries their union, and then applies the existing precedence: current-tenant whitelist, system blacklist, current-tenant blacklist. Indexes from another tenant are never queried. Scope is not removed from the HMAC input.

### 3. Snapshot rotation uses the existing wrapping-key lifecycle

`KeyReference` validates `rotationRequired` with `purpose.usesEncryptionLifecycle()`, which already includes FIELD and SNAPSHOT. No snapshot service or database lifecycle redesign is allowed. Tests cover threshold reconstruction, next-chunk use after the threshold, restart, and the unchanged hard ceiling.

### 4. Routing preparation and persistence protection are separate

Before routing, `MessageTaskProtectionAdapter` may compute only opaque routing/query material and the legacy lookup capability. It must not create a field envelope, call KEK wrap, reserve wrap quota, or retain plaintext in the returned routing object.

After routing accepts the request, a second method consumes the original request mobile plus the opaque routing result and creates exactly one persistence envelope and locator. `save` remains the atomic task/envelope/index transaction. Blacklist, content, and frequency rejection tests must prove the FIELD wrap count is unchanged.

### 5. Object publication shares the registration-session row lock

`completeCreate` first locks the same `ycs_crypto_registration_sessions` row used by close/claim. It requires one matching tenant binding and `OPEN` state before replacing or publishing any object.

- Publish first: close subsequently changes STAGED to EXPIRED.
- Close/claim first: publication fails before mutation; existing split-write containment deletes the provider object or leaves ORPHANED/RECONCILE_DELETE for retry.

The provider network call stays outside the database transaction.

### 6. Mobile frequency rules fail closed until Phase 18

Phase 03 removes `getOpaqueMobileQueryValue()` as a Redis frequency identity. It does not add another HSM key, alias-mapping subsystem, or partial Lua migration protocol.

If an ACTIVE `MOBILE` frequency rule is encountered before Phase 18 supplies a cross-rotation stable identity, submission returns the explicit sanitized result `FREQUENCY_MOBILE_IDENTITY_NOT_READY` before Redis mutation and before field protection. TENANT_LEVEL and IP rules keep their current behavior. Phase 18 owns the stable identifier, atomic multi-window counting, rotation overlap, TTL, and concurrency tests.

### 7. Pull requests test the synthetic merge

Required PR jobs use the default `actions/checkout@v4` behavior and assert `git rev-parse HEAD == $GITHUB_SHA`. A head/tag attestation may not replace the merge test. Phase 4 and Phase 5 use stacked PR bases until PR #15 is merged, so each PR shows only its own phase delta.

### 8. Generated test output is not source

Restore the normal `core/target/` ignore and remove every tracked `core/target/**` file. CI runs the Phase 03 real Maven profile in the fresh merge checkout, verifies that the named real integration classes are not skipped, and uploads Surefire reports as GitHub Actions artifacts.

The prior source-wide tested-input seal, committed result JSON, and lifecycle self-attestation are not Phase 03 completion gates after this correction. Standard test exit status, JUnit reports, repository queries, and the PR required check are the evidence.

### 9. Frontend verification is an independent CI job

The merge checkout runs exactly the repository contract:

```text
npm --prefix web ci
npm --prefix web test
npm --prefix web run build
```

The job has no Phase 1 file-existence condition.

### 10. Deployment documentation describes two truthful modes

Remove every `FIELD_ENCRYPTION_KEY` instruction because no production code consumes it.

- Disabled default: the service can start for limited development, but protected sending and registration-object operations fail closed.
- Enabled: document the actual PKCS#11 module allowlist, slot, token identity, PIN environment reference, five required purpose-separated key labels/references, database key-reference bootstrap, encrypted snapshot root, and object-store requirements.

If a supported production bootstrap command does not exist, the manual states that limitation instead of claiming the protected flow is ready.

## Verification map

| Finding | Required executable proof |
| --- | --- |
| 1 | Unit atomicity/mismatch/half-migration tests plus real MySQL COMPLETE/no-plaintext/decrypt test |
| 2 | Context-sensitive unit lookup tests plus real SoftHSM global/tenant precedence test |
| 3 | Repository threshold/restart/ceiling tests and snapshot continuation test |
| 4 | Three routing-rejection unit cases and real wrap-count unchanged/accepted-once test |
| 5 | OPEN/CLOSED/CLAIMED tests plus two real MySQL interleavings |
| 6 | ACTIVE MOBILE rule returns the explicit fail-closed result with no Redis or wrap mutation |
| 7 | PR job verifies the merge SHA |
| 8 | `git ls-files 'core/target/**'` is empty; real integration reports show zero skipped named tests |
| 9 | Independent Web CI job passes all three required commands |
| 10 | Documentation query has zero obsolete key references; enabled/disabled configuration tests match `application.yml` |

## Independent review corrections

The first aggregate GSD review found four release blockers. Their resolution is fixed here before implementation continues:

1. **Preserve applicable CI gates.** Keep the synthetic-merge checkout and the three standard Backend, Phase 03 real-integration, and Web jobs. Restore the existing Phase 01 portable/supersession job and a Phase 03 portable-contract job containing Flyway ownership tests, destructive planning/protected-inventory and service-check fixtures, production source reachability, and the production-JAR test-bridge exclusion. Do not restore committed-result sealing, delivery self-attestation, or lifecycle self-attestation.
2. **Expiry participates in the session lock.** `completeCreate` must evaluate `expires_at` with the database clock while holding the registration-session row lock. An `OPEN` row whose expiry is not in the future is atomically contained as `EXPIRED` and publication is rejected before any object metadata replacement or `STAGED` transition; the object remains on the existing deletion-reconciliation path.
3. **Prove the object race on production locking semantics.** `Phase03ObjectStorageIntegrationTest` must run deterministic independent-connection races on real MySQL/InnoDB for both `CLOSED` and `CLAIMED`, with both publish-first and terminal-first winners. Publish-first must converge to the terminal object state; terminal-first must reject publication and leave no new `STAGED` row.
4. **Separate migration input compatibility from online validation.** The locked legacy contract remains exactly 11 ASCII digits. Migration-only index derivation consumes the already verified SHA-256 digest under the same tenant/message context and key-version rules; it must not call the online writer's `1[3-9]` plaintext validator. A non-`1[3-9]` legacy value must migrate and allow COMPLETE without widening the current writer contract.
5. **Close the message-state shape, not only the legacy counters.** During BACKFILLED/VERIFIED, every message row must be either an admitted legacy shape or a current shape. The legacy shape requires exactly 11 ASCII digits in `mobile_encrypted`, a lowercase 64-character `mobile_hash` equal to `SHA-256(mobile_encrypted)`, and the exact ACTIVE/RETIRING versioned-index set bound to that hash. The current shape requires a strict `p3c1_[A-Za-z0-9_-]{43}` locator, an `EnvelopeCodec`-decodable `DATABASE_FIELD` YCSE envelope whose ciphertext is exactly 11 plaintext bytes plus the fixed AEAD tag, and a valid current row binding. Structural validation does not decrypt or call the HSM. During SCRUBBED/COMPLETE, only the current shape is legal. Any unknown/noncanonical locator, magic-only or wrong-length YCSE value, missing binding, invalid legacy plaintext, or plaintext/hash mismatch blocks state advancement.
6. **State validation must not lock the live message table.** Whole-target BACKFILLED/VERIFIED/SCRUBBED/COMPLETE scans use transaction-consistent non-locking reads and may not issue an unbounded `SELECT ... FOR UPDATE` over `message_tasks`. Per-row migration keeps its existing row lock and compare-and-set publication. State advancement remains serialized by the migration lease and writer fence; compatible online writers publish the current row and indexes atomically.
7. **A clean CI runner explicitly prepares the locked service images.** The direct Phase 03 real-integration job must pull the `linux/amd64` MySQL and MinIO images by their existing immutable platform digests before Maven starts. The current fixture keeps authority for identity, platform, release-label and container checks; missing or mismatched images still fail closed. Do not restore the old root evidence runner, pull Redis, or make test startup perform an implicit network mutation.
8. **MinIO identity follows OCI digest and image-store semantics.** Keep the existing digest-pinned MinIO repository reference and record the config digest for each admitted platform. RepoDigest, platform and release label must always match exactly. Docker's classic store reports `.Id` as the selected platform config digest, while the containerd store may report the manifest-list digest; admit only those two exact locked representations. The result fields stay unambiguous: `image_digest` is the manifest-list digest, `config_digest` is always the admitted platform's config digest, and `image_id` is the actual validated Docker-store representation. The running-container check must equal that pre-start `image_id`, with a destructive contract test proving mismatch rejection. Do not replace the immutable reference with a mutable tag or accept an arbitrary image ID.

These corrections reopen only the affected TODOs and the final review/delivery rows. Aggregate review repeats until no scoped BLOCKER/HIGH remains; Claude receives the final bounded remediation patch rather than the unrelated Phase 1-3 cumulative branch diff. The CI image-preparation and OCI-identity corrections are delivery-environment prerequisites and are accepted only by a fresh synthetic-merge real-integration run with all seven named suites executed and zero skips.

## Completion and delivery

The obsolete delivery tag remains historical and is not moved. Phase 03 closes on one correction commit pushed to PR #15, a successful synthetic-merge backend/real-service/Web CI run, one aggregate independent review, one Claude review, and a physically empty TODO query.
