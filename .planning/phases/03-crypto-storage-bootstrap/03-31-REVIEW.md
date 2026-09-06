---
phase: 03-crypto-storage-bootstrap
plan: 31
round: 4
reviewed: 2026-09-06T03:09:14Z
depth: deep
files_reviewed: 49
files_reviewed_list:
  - AGENTS.md
  - .github/workflows/ci.yml
  - .gitignore
  - .planning/phases/03-crypto-storage-bootstrap/03-31-PLAN.md
  - .planning/phases/03-crypto-storage-bootstrap/03-31-SOLUTION.md
  - .planning/phases/03-crypto-storage-bootstrap/TODO.md
  - README.md
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/KeyReferenceRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/JdbcFieldReferencePublicationFence.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/JdbcMobileBlindIndexPublicationFence.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/BlindIndexRotationService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/EnvelopeCodec.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/CipherEnvelope.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/MigrationStateRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/Pkcs11MigrationBlindIndexPort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationRunner.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectMetadataRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexLookupService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexMetadataRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/LegacyMobileLookupToken.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskRowBinding.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/PreparedMessageMobile.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/PreparedMessageRouting.java
  - core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java
  - core/src/main/java/com/ycsopen/sms/core/service/routing/FrequencyChecker.java
  - core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingContext.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/FrequencyRule.java
  - core/src/main/java/com/ycsopen/sms/core/repository/FrequencyRuleRepository.java
  - core/src/main/resources/security/protected-data-inventory.json
  - core/src/main/resources/db/migration/V1__init_schema.sql
  - core/src/main/resources/db/migration/V1200__create_crypto_storage_metadata.sql
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/lifecycle/SnapshotEnvelopeInventoryTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/pkcs11/SunPkcs11KeyAdapterTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationRunnerTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectMetadataRepositoryJdbcTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexLookupServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/CurrentProtectedReaderFenceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapterTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/message/MessageSubmitServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/service/routing/FrequencyCheckerTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ObjectStorageIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ProtectedPersistenceIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ServiceHarness.java
  - scripts/provision-phase-03-softhsm
  - scripts/lib/phase-03/softhsm-source.json
  - scripts/lib/phase-03/service_checks.rb
  - docs/使用手册.md
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
verdict: pass
---

# Phase 03 Plan 31: Aggregate Code Review Report — Round 4

**Reviewed:** 2026-09-06T03:09:14Z
**Depth:** deep
**Files Reviewed:** 49, plus the staged removal/query of all previously tracked `core/target/**` files
**Status:** clean
**Final scoped count:** BLOCKER 0 / HIGH 0
**Verdict:** PASS

## Summary

Round 4 independently traced the CR-06, CR-07, and CR-08 corrections through the JDBC state
repository, migration runner, envelope codec, current writer, key-version fences, destructive H2
coverage, and real-MySQL regression. The Round 3 blocker is closed: both admitted message shapes now
implement the locked predicates, and terminal states cannot accept legacy or structurally invalid
current rows.

The earlier CR-01 through CR-05 corrections remain present. The four Claude Attempt observations
specified for independent adjudication were also checked against the schema and production call
chains; none is a valid scoped correctness finding. No new BLOCKER, HIGH, WARNING, or INFO defect was
found in the Plan 31 remediation surface.

## Review History and Disposition

| Finding | Round found | Round 4 disposition | Evidence |
|---|---:|---|---|
| CR-01 — applicable CI gates removed | 1 | **CLOSED; no regression** | Phase 01 portable/supersession and Phase 03 portable-contract jobs remain beside Backend, real integration, and Web; every PR job uses default checkout and verifies `HEAD == GITHUB_SHA` (`.github/workflows/ci.yml:17-207`). |
| CR-02 — expired `OPEN` session could publish | 1 | **CLOSED; no regression** | Publication still locks the shared session row, uses database-clock expiry, contains expired `OPEN` as `EXPIRED`, and rejects before `STAGED`. |
| CR-03 — real close/claim publication race proof missing | 1 | **CLOSED; no regression** | The real InnoDB suite still covers `CLOSED` and `CLAIMED` in publish-first and terminal-first orders. |
| CR-04 — 11-digit migration input inherited online validation | 1 | **CLOSED; no regression** | Migration still derives indexes from the already verified historical digest (`ProtectedDataMigrationRunner.java:355-380`); the online `1[3-9]` writer contract is unchanged. |
| CR-05 — unknown state could bypass migration completion | 2 | **CLOSED** | The exhaustive locator partition remains in `messageStateShapesValid`; all non-current locators take the legacy validator, while `remainingLegacyRows` admits only bound current rows (`MigrationStateRepository.java:974-982,1047-1061`). |
| CR-06 — legacy plaintext/hash integrity omitted from state advancement | 3 | **CLOSED** | The validator requires exactly 11 ASCII digits, parses only canonical lowercase SHA-256, and compares the decoded hash to `SHA-256(plaintext)` with `MessageDigest.isEqual` (`MigrationStateRepository.java:1064-1098`). |
| CR-07 — magic-only current envelope accepted | 3 | **CLOSED** | Current rows pass the sole `EnvelopeCodec` parser as `DATABASE_FIELD` and require ciphertext length `11 + DATA_TAG_BYTES` = 27 before binding metadata is examined (`MigrationStateRepository.java:879-970`). |
| CR-08 — whole-target message scan took an unbounded row lock | 4 / Claude | **CLOSED** | `MESSAGE_STATE_SCAN_SQL` has no `FOR UPDATE` (`MigrationStateRepository.java:312-318`). Bounded migration batches retain `LIMIT ? FOR UPDATE` (`584-606`), publication re-locks one row and uses a complete old-value CAS (`744-770`), and target/lease/checkpoint locks remain (`460-523`). |

## CR-06/07 State and Metadata Checks

- `BACKFILLED` and `VERIFIED` call `integrityAndBindingComplete`; the message-specific branch
  exhaustively accepts either a valid legacy shape or a valid current shape
  (`ProtectedDataMigrationRunner.java:161-166`; `MigrationStateRepository.java:1005-1061`).
- Legacy metadata must contain exactly the ACTIVE/RETIRING key set in ascending version order, with
  matching version/status, `MOBILE_BLIND_INDEX` purpose, canonical index encoding, the SHA-256 of
  the legacy `mobile_hash` cell in `original_row_digest`, and null `row_binding_digest`
  (`MigrationStateRepository.java:1099-1127`).
- Current metadata has the same exact key-version/status/purpose and canonical-value requirements,
  but binds the whole current row `(tenant, row id, message id, locator, envelope)` in
  `original_row_digest`; legacy `row_binding_digest` remains forbidden (`879-970`). This matches the
  production writer, which assigns the YCSE bytes to `mobile_encrypted`, the `p3c1_` locator to
  `mobile_hash`, and persists the whole-row digest (`MessageTask.java:82-90`;
  `BlindIndexMetadataRepository.java:37-80`).
- `SCRUBBED` and `COMPLETE` call `remainingLegacyRows`; for `MESSAGE_TASK` it counts every non-current
  locator and every current locator whose structural or metadata binding fails. Consequently no
  legacy plaintext/hash shape is terminally admissible (`ProtectedDataMigrationRunner.java:172-183`;
  `MigrationStateRepository.java:973-983`).
- The destructive real regression installs recomputed bindings around magic-only and wrong-length
  YCSE shapes, unknown locators, non-YCSE values, missing bindings, invalid legacy plaintext, and
  plaintext/hash mismatch; rejected advances preserve the prior state, then valid rows traverse
  `BACKFILLED`, `VERIFIED`, `SCRUBBED`, and `COMPLETE`
  (`Phase03MigrationIntegrationTest.java:1102-1214,1225-1410`).

## CR-08 Concurrency Boundary

The corrected state scan is a transaction-consistent, non-locking read of `message_tasks`. It does
not remove the locks that protect mutations:

- `claimLease` locks the single migration-target row and admits no competing live target lease;
  `checkpoint` locks the run/target checkpoint (`MigrationStateRepository.java:460-523`).
- `readMessageBatch` is bounded by the caller-validated maximum and uses `ORDER BY id LIMIT ? FOR
  UPDATE` (`527-606`).
- `publishProtectedMessage` revalidates the field and blind-index purpose fences, locks exactly the
  selected message row, compares tenant/message/hash/envelope, and updates only when all original
  columns still match (`714-803`).
- Existing index metadata is accepted only as an exact legacy set; current metadata is inserted in
  the same transaction. Any failed row comparison, metadata mismatch, or checkpoint/state write
  rolls the transaction back.

The state reader's bounded, row-specific metadata lock for a legacy row is not the removed
unbounded `message_tasks` lock and does not widen the live-message lock footprint. It preserves the
same exact-metadata serialization reused by per-row publication.

## Claude Attempt Finding Adjudication

| Observation | Decision | Proof |
|---|---|---|
| `message_id` and `mobile_encrypted` columns are swapped | **NOT A FINDING** | Both scan queries select `mobile_hash` as column 4, `message_id` as 5, and `mobile_encrypted` as 6; `MessageTaskSource(String messageId, byte[] mobileEncrypted)` consumes columns 5/6 in that order (`MigrationStateRepository.java:312-318,584-606,1130-1144`). V1 declares the same column meanings (`V1__init_schema.sql:463-472`), and the current writer assigns envelope to `mobile_encrypted` and locator to `mobile_hash` (`MessageTask.java:45-53,82-90`). |
| Wiping validator arrays aliases and corrupts `LegacyRow` | **NOT A FINDING** | `LegacyRow` clones `storedValue` and `originalCellDigest` in its canonical constructor and returns fresh clones; `MessageTaskSource` does the same for `mobileEncrypted` (`MigrationStateRepository.java:171-250`). Validator cleanup therefore clears only accessor copies. |
| Linux real CI lacks the dependencies needed to build SoftHSM | **NOT A FINDING** | CI installs `build-essential`, `cmake`, and `libssl-dev` (`.github/workflows/ci.yml:136-145`). The pinned manifest disables DB, migration, p11-kit, GOST, FIPS, static, and tests, and selects OpenSSL. The harness invokes the provisioner with `--provision --initialize-token --preflight`; the provisioner builds/installs with CMake and compiles the native PKCS#11 probe (`service_checks.rb:409-429`; `provision-phase-03-softhsm:269-291,317-348`). |
| One active MOBILE rule should block only a tenant-specific subset | **NOT A PLAN-31 FINDING** | The current `FrequencyRule` entity has no tenant ownership and the repository deliberately returns every ACTIVE rule (`FrequencyRule.java:12-38`; `FrequencyRuleRepository.java:8-10`). The locked correction explicitly says **any** ACTIVE MOBILE rule fails closed globally before Redis and field wrapping; Phase 18 owns the future stable identity and complete scoping subsystem (`03-31-SOLUTION.md:59-63`). The implementation and zero-Redis test match that boundary (`FrequencyChecker.java:29-51`; `FrequencyCheckerTest.java:29-40`). |

## Narrative Findings (AI reviewer)

All reviewed files meet the scoped correctness, security, concurrency, and maintainability standard.
No issues found.

## Verification Evidence

- `mvn -f core/pom.xml -Dtest=ProtectedDataMigrationRunnerTest test` — 11 tests, 0 failures,
  0 errors, 0 skips (fresh Round 4 run).
- The supplied real migration execution reports 2 tests, 0 failures, 0 errors, 0 skips.
- Static extraction of `MESSAGE_STATE_SCAN_SQL` confirms `message_scan_nonlocking=PASS`.
- `git ls-files 'core/target/**'` returns zero paths.
- `git diff --check` passes before report update.

Remote synthetic-merge CI remains an external delivery gate in `TODO.md`; its pending execution is
not a source-code review defect and does not alter the Round 4 BLOCKER/HIGH verdict.

## CR-11 focused follow-up

The third synthetic-merge replay exposed `REPORTS_INPUTS_EMPTY` only after the tracked generated
files had been removed. A focused independent review checked the solution before and after both
external-review findings. The final implementation:

- consumes only the same run's closed-format, sanitized PKCS#11 real-proof output;
- centralizes that format check in the producer test instead of copying its numeric contract;
- relies on the already successful `startAll()` service-owned directory creation, then requires the
  generated root's real path to equal the repository path before any write;
- leaves the Ruby scanner's empty-input, symlink, type, count and size rejection unchanged; and
- removes the temporary report in `finally`.

The final focused verdict is PASS with `BLOCKER 0 / HIGH 0`. The clean local real-profile replay
executes the leak suite with zero skips; remote acceptance remains the physical TODO boundary.

---

_Reviewed: 2026-09-06T03:09:14Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: deep_
