---
phase: 03-crypto-storage-bootstrap
reviewed: 2026-09-01T13:06:13Z
status: stale_snapshot
depth: deep
note: |
  This file is a historical snapshot from the pre-repaired subject used before production-reachability and object-lifecycle fixes.
  It is retained for traceability, but it must not be treated as the current-pass clearance signal.
files_reviewed: 148
files_reviewed_list:
  - core/src/main/java/com/ycsopen/sms/core/common/security/FieldEncryptor.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/HashUtil.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageConfiguration.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageStartupVerifier.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/CipherEnvelope.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/EnvelopeCodec.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/ProtectionContext.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/envelope/ProtectionFailure.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/BlindIndexPort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/KeyHealth.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/KeyProtectionPort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/OpaqueTokenDigestPort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/VersionedBlindIndex.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/VersionedTokenDigest.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/WrapOperationAdmissionPort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/WrappedDataKey.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/BlindIndexRotationService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/EnvelopeReferenceInventory.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/EnvelopeRewrapService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/KeyLifecycleService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/KeyReferenceRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/lifecycle/KeyState.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/KekWrapUsageRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11CryptoStorageProperties.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11FailureMapper.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11KeyDescriptor.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11ProviderFactory.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/SunPkcs11KeyAdapter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/logging/LeakScanReport.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/logging/Phase03LeakScanCommand.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/logging/SafeLogValue.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/logging/SecurityEventLogger.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/logging/SecurityRedactionConverter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/logging/SensitiveDataLeakScanner.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/EncryptedSnapshotVerifier.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/LegacyValueClassifier.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/MigrationPreflight.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/MigrationPreflightProperties.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/MigrationStateRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/Pkcs11MigrationBlindIndexPort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataManifest.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationCommand.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationLauncher.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationRunner.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataTarget.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/SignedMigrationManifestVerifier.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/WriterFencePort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/snapshot/EncryptedMySqlSnapshotService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/snapshot/MySqlSnapshotProcess.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/snapshot/SnapshotChunkStore.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/migration/snapshot/SnapshotManifest.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/DenyAllObjectAccessAuthorization.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectAccessAuthorizationPort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectCapabilityService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectCapabilityToken.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectStoreProperties.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/PrivateObjectStorePort.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectMetadataRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/S3PrivateObjectStoreAdapter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/StoredObjectMetadata.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/object/TenantRegistrationObjectSessionService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexLookupService.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexMetadataRepository.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/LegacyMobileHashReader.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/LegacyMobileLookupToken.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapter.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/PreparedMessageMobile.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldCodec.java
  - core/src/main/java/com/ycsopen/sms/core/common/security/persistence/TenantRegistrationProtectionAdapter.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/BlacklistEntry.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/MessageTask.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/Tenant.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/TenantApiKey.java
  - core/src/main/java/com/ycsopen/sms/core/domain/entity/User.java
  - core/src/main/java/com/ycsopen/sms/core/repository/BlacklistEntryRepository.java
  - core/src/main/java/com/ycsopen/sms/core/repository/MessageTaskRepository.java
  - core/src/main/java/com/ycsopen/sms/core/repository/TenantApiKeyRepository.java
  - core/src/main/java/com/ycsopen/sms/core/repository/TenantRepository.java
  - core/src/main/java/com/ycsopen/sms/core/service/complaint/ComplaintRatioService.java
  - core/src/main/java/com/ycsopen/sms/core/service/message/MessageSubmitService.java
  - core/src/main/java/com/ycsopen/sms/core/service/routing/BlacklistChecker.java
  - core/src/main/java/com/ycsopen/sms/core/service/routing/ChannelSelector.java
  - core/src/main/java/com/ycsopen/sms/core/service/routing/FrequencyChecker.java
  - core/src/main/java/com/ycsopen/sms/core/service/routing/RoutingContext.java
  - core/src/main/java/com/ycsopen/sms/core/service/routing/ThirdPartyBlacklistClient.java
  - core/src/main/java/com/ycsopen/sms/core/service/tenant/TenantService.java
  - core/src/main/java/com/ycsopen/sms/core/web/ProtectedObjectAccessController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/TenantController.java
  - core/src/main/java/com/ycsopen/sms/core/web/controller/TenantRegistrationObjectController.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TenantRegistrationRequest.java
  - core/src/main/java/com/ycsopen/sms/core/web/dto/TenantRegistrationResponse.java
  - core/src/main/java/com/ycsopen/sms/core/web/interceptor/HmacAuthInterceptor.java
  - core/src/main/resources/application.yml
  - core/src/main/resources/db/migration/V1200__create_crypto_storage_metadata.sql
  - core/src/main/resources/logback-spring.xml
  - core/src/main/resources/security/migration/phase03-migration-cli-contract.json
  - core/src/main/resources/security/migration/ycs-encrypted-snapshot-v1.schema.json
  - core/src/main/resources/security/migration/ycs-writer-fence-v1.schema.json
  - core/src/main/resources/security/protected-data-inventory.json
  - core/src/test/java/com/ycsopen/sms/core/common/security/FieldEncryptorTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/config/CryptoStorageStartupVerifierTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/envelope/EnvelopeCodecTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/envelope/ProtectionContextTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/DeterministicTestKeyAdapter.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/VersionedBlindIndexTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/lifecycle/EnvelopeRewrapServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/lifecycle/KeyLifecycleServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/pkcs11/Pkcs11ProviderFactoryTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/key/pkcs11/SunPkcs11KeyAdapterTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/logging/SafeLogValueTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/logging/SecurityRedactionConverterTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/logging/SensitiveDataLeakScannerTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/LegacyValueClassifierTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/MigrationPreflightTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataManifestTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationCommandTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationRunnerTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/SignedMigrationManifestVerifierTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/migration/snapshot/EncryptedMySqlSnapshotServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/object/ObjectCapabilityServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/object/S3PrivateObjectStoreAdapterTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexLookupServiceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/CurrentProtectedReaderFenceTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapterTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/ProtectedEntityMappingTest.java
  - core/src/test/java/com/ycsopen/sms/core/common/security/persistence/ProtectedFieldCodecTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase01MySqlIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03EncryptedSnapshotHarness.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03FixturePreflightTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03LeakScanIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationCommandFixture.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03MigrationIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ObjectStorageIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03Pkcs11IntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ProtectedPersistenceIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03RotationRecoveryIntegrationTest.java
  - core/src/test/java/com/ycsopen/sms/core/verification/Phase03ServiceHarness.java
  - core/src/test/java/com/ycsopen/sms/core/web/ProtectedObjectAccessControllerTest.java
  - core/src/test/java/com/ycsopen/sms/core/web/TenantRegistrationObjectApiTest.java
  - scripts/lib/phase-03/run_checks.rb
  - scripts/lib/phase-03/service_checks.rb
  - scripts/lib/phase-03/softhsm-source.json
  - scripts/lib/phase-03/test_run_checks.rb
  - scripts/lib/phase-03/test_service_checks.rb
  - scripts/provision-phase-03-softhsm
  - scripts/verify-phase-03
findings:
  critical: 9
  warning: 1
  info: 0
  total: 10
status: issues_found
---

# Phase 03: Code Review Report

**Reviewed:** 2026-09-01T13:06:13Z  
**Depth:** deep  
**Files Reviewed:** 148  
**Status:** issues_found  
**Blocking/high count:** **9 Critical (blocking), 1 Warning**

## Summary

The submitted Phase 03 implementation is not releasable. The low-level crypto, migration, and object-storage components are substantial, but several acceptance paths exist only in manually composed tests and are absent from the production application. Cross-module tracing also found a current-writer/migration incompatibility, a delete/claim data-loss race, a capability replay defect, an S3 ACL authorization gap, and a split-write state machine that can lose its only reconciliation locator.

The already-passed complete real-service suite was not rerun. This review used the recorded tested subject `a9115b1e8a04f683a52604982552d220fac11006d4782be0d76b48e97097c875` and focused static/cross-file checks so that it did not duplicate the accepted root verification run.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: The documented migration command has no production service provider and always exits before doing work

**File:** `core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationLauncher.java:26-39`  
**Classification:** BLOCKER  
**Issue:** Every non-help invocation loads `CommandServicesFactory` with `ServiceLoader` and requires exactly one provider. There is no implementation of that interface and no `META-INF/services` registration in main or test resources. The integration tests call the injected four-argument `run(..., services)` overload, bypassing the public entry path. Consequently the fixed `phase03-migration preflight/start/resume/recovery` surface deterministically returns `KEY_OR_PROVIDER` (exit 26) in a production artifact.  
**Fix:** Implement a production `CommandServicesFactory` that composes the JDBC state repository, PKCS#11 ports, manifest verifier, runner, snapshot/recovery ports, and register it under `META-INF/services/com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationLauncher$CommandServicesFactory`. Add an integration test that packages the artifact and invokes the same three-argument launcher/main entry used by operators, without injecting services.

### CR-02: Private-object upload and access are not production-composed, so both HTTP surfaces disappear

**Files:** `core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageConfiguration.java:36-89`; `core/src/main/java/com/ycsopen/sms/core/web/ProtectedObjectAccessController.java:19-21`; `core/src/main/java/com/ycsopen/sms/core/web/controller/TenantRegistrationObjectController.java:28-30`; `core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectStoreProperties.java:11-19`  
**Classification:** BLOCKER  
**Issue:** Production configuration creates only the four crypto ports and the deny-all authorization port. `ProtectedObjectService`, `ObjectCapabilityService`, `TenantRegistrationObjectSessionService`, and `S3PrivateObjectStoreAdapter` are plain classes with no main-code factory. The properties record is not enabled by either `@EnableConfigurationProperties` or configuration-properties scanning. Both controllers are conditional on the missing services, so Spring silently omits the upload/session and capability-read routes. Tests manually construct these components and therefore cannot detect the missing application path.  
**Fix:** Add an explicit conditional production configuration that enables and validates `ObjectStoreProperties`, owns a lifecycle-managed S3 client/adapter, and wires the capability, protected-object, and registration-session services. Add an application-context test that asserts the complete bean graph and routes when enabled and a closed/absent state when disabled; also exercise one request through the real MVC route and production bean graph.

### CR-03: The production runtime cannot represent or activate a second key version, while live writers remain pinned to v1

**Files:** `core/src/main/java/com/ycsopen/sms/core/common/security/config/CryptoStorageStartupVerifier.java:153-174`; `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapter.java:31-62`; `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/TenantRegistrationProtectionAdapter.java:44-67`; `core/src/main/java/com/ycsopen/sms/core/common/security/key/pkcs11/SunPkcs11KeyAdapter.java:76-107`  
**Classification:** BLOCKER  
**Issue:** Startup creates exactly five hard-coded version-1 ACTIVE descriptors. Both production field codecs hard-code `field-kek.v1`, and `SunPkcs11KeyAdapter` snapshots active keys and reference maps once in its constructor. `KeyLifecycleService` has no production owner or command. The rotation test succeeds only by manually creating multiple descriptors and rebuilding adapters. Thus the production application cannot prepare/activate v2, and changing lifecycle database state cannot make current writes use the new active key; a restart from the current settings reconstructs only v1. This contradicts the accepted activate/restart/retire lifecycle.  
**Fix:** Bind a versioned descriptor registry from validated configuration or a single lifecycle-owned source, wire `KeyLifecycleService`, and make new-write key selection read the currently ACTIVE descriptor while unwrap retains permitted historical references. Add a production-context test covering prepare v2, activate, new write on v2, old read, restart, and safe retirement without manually replacing the runtime adapter.

### CR-04: Current message writes are classified as legacy digests and make migration reject a valid Phase 03 database

**Files:** `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/MessageTaskProtectionAdapter.java:122-127`; `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/PreparedMessageMobile.java:22-24,97-118`; `core/src/main/java/com/ycsopen/sms/core/common/security/migration/MigrationStateRepository.java:409-428,462-487`; `core/src/main/java/com/ycsopen/sms/core/common/security/migration/ProtectedDataMigrationRunner.java:245-259`  
**Classification:** BLOCKER  
**Issue:** The Phase 03 writer stores a random 32-byte value as 64 lowercase hex in the legacy `mobile_hash` column—the same shape the migration classifies as an approved historical SHA-256 digest. Its blind-index rows are correctly bound to a composite `PreparedMessageMobile.originalRowDigest`, but migration decides a row is still legacy when no metadata exists with `original_row_digest = SHA2(mobile_hash)`. A new Phase 03 row therefore enters the legacy path, hashes the random locator as if it were a phone digest, and then `INSERT IGNORE` plus exact parity conflicts with the already-correct indexes and composite digest. One legitimate current write is enough to stop migration progress.  
**Fix:** Give current locators a versioned encoding that cannot match the legacy digest grammar (and widen the column if necessary), or explicitly recognize YCSE/current rows and validate their existing metadata rather than re-indexing the locator. Add a real-MySQL test that performs a production current write before a migration batch and proves the row is skipped/validated while true legacy rows still migrate.

### CR-05: The “private bucket” check accepts grants to foreign AWS canonical users

**File:** `core/src/main/java/com/ycsopen/sms/core/common/security/object/S3PrivateObjectStoreAdapter.java:223-254`  
**Classification:** BLOCKER  
**Issue:** `ensurePrivateBucket` treats every `CANONICAL_USER` grantee as the owner. It neither reads the ACL response owner ID nor compares the grantee canonical ID or permission with that owner. A READ or FULL_CONTROL grant to any other AWS account therefore passes as private. Existing tests cover a public GROUP grant, not a foreign canonical-user grant.  
**Fix:** Compare every grant against `GetBucketAclResponse.owner().id()`, permit only the exact owner with the required owner permission, and reject all other canonical IDs and permissions. Where supported, verify public-access-block and the intended deny policy too. Add foreign-canonical-user READ and FULL_CONTROL rejection tests against the adapter.

### CR-06: Delete races with registration claim and can remove ciphertext after the row becomes CLAIMED

**Files:** `core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectService.java:145-166`; `core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectMetadataRepository.java:388-395`; `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/TenantRegistrationProtectionAdapter.java:347-398`  
**Classification:** BLOCKER  
**Issue:** Delete performs an unlocked metadata read, deletes the S3 object, and only then conditionally changes the database state. The claim path locks and transitions the same row from STAGED to CLAIMED. A valid interleaving is: delete reads STAGED; claim commits CLAIMED; delete removes the object; `markDeleted` and `markOrphaned` both reject CLAIMED. The database now records a claimed registration object whose ciphertext has been irreversibly deleted.  
**Fix:** Acquire the row lock and atomically reserve deletion with a DELETING/reconciliation state before touching S3. Claim must only transition from STAGED and therefore lose the CAS once deletion is reserved. Persist provider failure in that state and reconcile idempotently. Add a concurrent real-MySQL test that coordinates claim and delete at the boundary and proves CLAIMED can never reference a deleted object.

### CR-07: Capabilities are reusable even though the locked acceptance contract requires reuse rejection

**File:** `core/src/main/java/com/ycsopen/sms/core/common/security/object/ObjectCapabilityService.java:96-116,283-288`  
**Classification:** BLOCKER  
**Issue:** `authorizeAndFetch` validates an ACTIVE record and invokes the downstream fetch but never consumes or transitions the capability. The persistence seam exposes only create and find. The test named for consumed capabilities manually changes fake state outside the service, so it does not test single-use behavior. The exact same token can be replayed repeatedly until expiry, contrary to OBL-CRYPTO-STORAGE-002’s explicit “expired/reused capability” rejection.  
**Fix:** Add a transactional/atomic `consumeActive` operation using a digest-safe lookup and a state/version/expiry CAS. Consume before the protected fetch (with a documented retry contract), and reject any second use. Add repository-backed concurrent tests proving that exactly one of two simultaneous uses succeeds.

### CR-08: A dual-provider failure loses the only object locator, making split-write cleanup non-replayable

**Files:** `core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectService.java:244-257`; `core/src/main/java/com/ycsopen/sms/core/common/security/object/ProtectedObjectMetadataRepository.java:284-293,328-338`; `core/src/main/resources/db/migration/V1200__create_crypto_storage_metadata.sql:342-365`  
**Classification:** BLOCKER  
**Issue:** After S3 put succeeds and metadata completion fails, the service first attempts immediate S3 delete and then attempts `recordOrphan`. If S3 and the database are both unavailable, both exceptions are swallowed. The previously persisted RESERVED operation contains neither object ID nor opaque storage locator, and the implemented path never advances to the schema’s OBJECT_STORED state. Nothing durable can later identify or delete the ciphertext, so the promised replayable reconciliation becomes a permanent orphan.  
**Fix:** Persist an idempotent OBJECT_STORED transition containing the protected object ID, opaque locator, checksum, purpose, and expiry immediately after the successful put and before final metadata commit, or use an external durable operation journal. Recovery must scan that state and deterministically complete or delete. Add a fault-injection test where completion, immediate delete, and orphan-recording all fail, then providers recover and reconciliation removes the exact object.

### CR-09: Blind-index matches are not bound to the current blacklist row semantics, enabling stale whitelist/blacklist decisions

**Files:** `core/src/main/java/com/ycsopen/sms/core/common/security/persistence/BlindIndexLookupService.java:148-179`; `core/src/main/java/com/ycsopen/sms/core/domain/entity/BlacklistEntry.java:17-52`; `core/src/main/java/com/ycsopen/sms/core/repository/BlacklistEntryRepository.java:10`  
**Classification:** BLOCKER  
**Issue:** Lookup joins blind-index metadata to the current `blacklist_entries` row by numeric ID and verifies only that the stored digest is 32 bytes. It never proves that the index/digest still describes the current row. The JPA entity remains freely mutable through setters and the repository exposes generic `save`/delete behavior. Reusing or editing a row’s mobile/tenant/list type can therefore leave an old mobile’s index attached to new current semantics—for example, an old number can inherit a tenant WHITE entry and bypass blacklist routing, while the new number is not matched.  
**Fix:** Replace generic mutable writes with a protected blacklist persistence adapter that updates the row and all ACTIVE/RETIRING indexes atomically. Persist and verify an immutable row binding/version that covers the protected mobile identity and policy-relevant fields, or prevent identity/policy mutation and create a new row. Add tests for mobile, tenant, list-type, status, delete, and ID-reuse/stale-metadata cases; mismatches must fail closed rather than return a routing decision.

## Warnings

### WR-01: The production object-store factory creates an S3 client with no ownership/close contract

**File:** `core/src/main/java/com/ycsopen/sms/core/common/security/object/S3PrivateObjectStoreAdapter.java:55-99`  
**Classification:** WARNING  
**Issue:** `create` builds an AWS SDK client and HTTP client, but the adapter is not `AutoCloseable` and exposes no close method. Once CR-02 wires this factory into the application, context shutdown/reload cannot deterministically release provider resources.  
**Fix:** Prefer separately managed `S3Client` and HTTP-client beans with destroy methods, or make the adapter explicitly own and close the client it creates while never closing injected clients. Add a lifecycle test that closes the application context and verifies the owned client is closed exactly once.

---

_Reviewed: 2026-09-01T13:06:13Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: deep_
