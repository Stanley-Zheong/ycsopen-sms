package com.ycsopen.sms.core.common.security.object;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Focused SQL-state regressions for protected-object publication and deletion finalization. */
class ProtectedObjectMetadataRepositoryJdbcTest {

    @ParameterizedTest
    @ValueSource(strings = {"CLOSED", "CLAIMED"})
    void terminalSessionCannotPublishAnObjectStoredWhileItWasOpen(String terminalState) {
        Fixture fixture = fixture();
        CreateFixture create = fixture.insertCreate("terminal", terminalState);

        assertThatThrownBy(() -> fixture.repository().completeCreate(
                create.operation(), create.stored()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("protected object metadata operation failed");

        assertThat(fixture.objectState(create.operation().protectedObjectId()))
                .isEqualTo("ORPHANED");
        assertThat(fixture.operationState(create.operation().protectedObjectId()))
                .isEqualTo("OBJECT_STORED");
    }

    @Test
    void openSessionPublishesTheStoredObject() {
        Fixture fixture = fixture();
        CreateFixture create = fixture.insertCreate("open", "OPEN");

        fixture.repository().completeCreate(create.operation(), create.stored());

        assertThat(fixture.objectState(create.operation().protectedObjectId()))
                .isEqualTo("STAGED");
        assertThat(fixture.operationState(create.operation().protectedObjectId()))
                .isEqualTo("COMPLETED");
    }

    @Test
    void expiredOpenSessionIsContainedBeforePublication() {
        Fixture fixture = fixture();
        CreateFixture create = fixture.insertCreate(
                "expired", "OPEN", Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> fixture.repository().completeCreate(
                create.operation(), create.stored()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("protected object metadata operation failed");

        assertThat(fixture.sessionState(create.operation().registrationSessionId()))
                .isEqualTo("EXPIRED");
        assertThat(fixture.objectState(create.operation().protectedObjectId()))
                .isEqualTo("ORPHANED");
        assertThat(fixture.operationState(create.operation().protectedObjectId()))
                .isEqualTo("OBJECT_STORED");
    }

    @Test
    void closeAfterPublicationExpiresTheStagedObject() {
        Fixture fixture = fixture();
        CreateFixture create = fixture.insertCreate("publish-first", "OPEN");
        fixture.repository().completeCreate(create.operation(), create.stored());

        fixture.sessions().transition(create.operation().registrationSessionId(),
                TenantRegistrationObjectSessionService.SessionState.CLOSED, Instant.now(),
                ignored -> true);

        assertThat(fixture.sessionState(create.operation().registrationSessionId()))
                .isEqualTo("CLOSED");
        assertThat(fixture.objectState(create.operation().protectedObjectId()))
                .isEqualTo("EXPIRED");
    }

    @Test
    void concurrentCloseAndPublicationNeverLeaveStagedMetadataUnderClosedSession()
            throws Exception {
        Fixture fixture = fixture();
        CreateFixture create = fixture.insertCreate("race", "OPEN");
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> publication = executor.submit(() -> {
                start.await();
                try {
                    fixture.repository().completeCreate(create.operation(), create.stored());
                    return true;
                } catch (IllegalStateException terminalWon) {
                    return false;
                }
            });
            Future<?> close = executor.submit(() -> {
                start.await();
                fixture.sessions().transition(create.operation().registrationSessionId(),
                        TenantRegistrationObjectSessionService.SessionState.CLOSED,
                        Instant.now(), ignored -> true);
                return null;
            });

            start.countDown();
            boolean published = publication.get();
            close.get();

            assertThat(fixture.sessionState(create.operation().registrationSessionId()))
                    .isEqualTo("CLOSED");
            assertThat(fixture.objectState(create.operation().protectedObjectId()))
                    .isEqualTo(published ? "EXPIRED" : "ORPHANED");
        }
    }

    @Test
    void deletionDoesNotCompleteFailedOperationOrReleaseItsFieldReservation() {
        Fixture fixture = fixture();
        String objectId = objectId('F');
        fixture.insertObject(objectId);
        fixture.insertOperation(objectId, "FAILED");

        fixture.repository().markDeleted(objectId);

        assertThat(fixture.objectState(objectId)).isEqualTo("DELETED");
        assertThat(fixture.operationState(objectId)).isEqualTo("FAILED");
        assertThat(fixture.fieldPurpose(objectId)).isEqualTo("FIELD_ENCRYPTION_KEK");
        assertThat(fixture.fieldVersion(objectId)).isEqualTo(7L);
    }

    @Test
    void deletionReleasesFieldReservationOnlyFromEligibleOperationStates() {
        Fixture fixture = fixture();
        List<String> eligibleStates = List.of(
                "OBJECT_STORED", "RECONCILE_DELETE", "COMPLETED");

        for (int index = 0; index < eligibleStates.size(); index++) {
            String objectId = objectId((char) ('A' + index));
            fixture.insertObject(objectId);
            fixture.insertOperation(objectId, eligibleStates.get(index));

            fixture.repository().markDeleted(objectId);

            assertThat(fixture.operationState(objectId)).isEqualTo("COMPLETED");
            assertThat(fixture.fieldPurpose(objectId)).isNull();
            assertThat(fixture.fieldVersion(objectId)).isNull();
        }
    }

    private static Fixture fixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE ycs_crypto_registration_sessions (
                    registration_session_id VARCHAR(36) PRIMARY KEY,
                    tenant_draft_id VARCHAR(36) NOT NULL,
                    session_state VARCHAR(16) NOT NULL,
                    upload_digest_purpose VARCHAR(48) NOT NULL,
                    upload_digest_key_version BIGINT NOT NULL,
                    upload_credential_digest BINARY(32) NOT NULL,
                    admitted_attempt_count INT NOT NULL DEFAULT 0,
                    expires_at TIMESTAMP NOT NULL,
                    optimistic_version BIGINT NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE ycs_crypto_protected_objects (
                    protected_object_id VARCHAR(80) PRIMARY KEY,
                    registration_session_id VARCHAR(36),
                    tenant_draft_id VARCHAR(36),
                    object_purpose VARCHAR(40),
                    object_state VARCHAR(24) NOT NULL,
                    opaque_store_locator VARCHAR(160),
                    envelope_digest BINARY(32),
                    envelope_size BIGINT,
                    media_type VARCHAR(64),
                    replaces_object_id VARCHAR(80),
                    expires_at TIMESTAMP,
                    optimistic_version BIGINT NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE ycs_crypto_object_operations (
                    operation_id VARCHAR(36) PRIMARY KEY,
                    registration_session_id VARCHAR(36),
                    object_purpose VARCHAR(40),
                    protected_object_id VARCHAR(80),
                    operation_state VARCHAR(24) NOT NULL,
                    attempt_number INT,
                    affected_count BIGINT NOT NULL DEFAULT 0,
                    field_key_purpose VARCHAR(48),
                    field_key_version BIGINT,
                    optimistic_version BIGINT NOT NULL DEFAULT 0
                )
                """);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        return new Fixture(jdbc, new ProtectedObjectMetadataRepository(jdbc, transactions),
                new TenantRegistrationObjectSessionService.JdbcSessionStore(jdbc, transactions));
    }

    private static String objectId(char fill) {
        return "pobj_v1_" + String.valueOf(fill).repeat(32);
    }

    private record Fixture(JdbcTemplate jdbc,
                           ProtectedObjectMetadataRepository repository,
                           TenantRegistrationObjectSessionService.JdbcSessionStore sessions) {

        CreateFixture insertCreate(String seed, String sessionState) {
            return insertCreate(seed, sessionState, Instant.now().plusSeconds(3_600));
        }

        CreateFixture insertCreate(String seed, String sessionState, Instant expiresAt) {
            String sessionId = UUID.nameUUIDFromBytes((seed + "-session").getBytes()).toString();
            String draftId = UUID.nameUUIDFromBytes((seed + "-draft").getBytes()).toString();
            String operationId = UUID.nameUUIDFromBytes((seed + "-operation").getBytes()).toString();
            char objectFill = Character.toUpperCase(seed.charAt(0));
            String protectedObjectId = objectId(objectFill);
            byte[] digest = new byte[32];
            digest[0] = 1;
            jdbc.update("""
                    INSERT INTO ycs_crypto_registration_sessions
                        (registration_session_id, tenant_draft_id, session_state,
                         upload_digest_purpose, upload_digest_key_version,
                         upload_credential_digest, expires_at)
                    VALUES (?, ?, ?, 'REGISTRATION_UPLOAD_DIGEST', 1, ?, ?)
                    """, sessionId, draftId, sessionState, digest, expiresAt);
            String storageKey = "obj_v1_" + "a".repeat(64);
            String sha256 = "b".repeat(64);
            jdbc.update("""
                    INSERT INTO ycs_crypto_protected_objects
                        (protected_object_id, registration_session_id, tenant_draft_id,
                         object_purpose, object_state, opaque_store_locator,
                         envelope_digest, envelope_size, media_type, expires_at)
                    VALUES (?, ?, ?, 'BUSINESS_LICENSE', 'ORPHANED', ?, ?, 128,
                            'application/pdf', ?)
                    """, protectedObjectId, sessionId, draftId, storageKey,
                    HexFormat.of().parseHex(sha256), expiresAt);
            jdbc.update("""
                    INSERT INTO ycs_crypto_object_operations
                        (operation_id, registration_session_id, object_purpose,
                         protected_object_id, operation_state, attempt_number,
                         field_key_purpose, field_key_version)
                    VALUES (?, ?, 'BUSINESS_LICENSE', ?, 'OBJECT_STORED', 1,
                            'FIELD_ENCRYPTION_KEK', 7)
                    """, operationId, sessionId, protectedObjectId);
            var operation = new ProtectedObjectMetadataRepository.CreateOperation(
                    operationId, protectedObjectId, sessionId, draftId,
                    PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, 1, expiresAt, null);
            var stored = new StoredObjectMetadata(storageKey,
                    PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, 128, sha256,
                    "application/pdf");
            return new CreateFixture(operation, stored);
        }

        void insertObject(String objectId) {
            jdbc.update("""
                    INSERT INTO ycs_crypto_protected_objects
                        (protected_object_id, object_state)
                    VALUES (?, 'DELETING')
                    """, objectId);
        }

        void insertOperation(String objectId, String state) {
            jdbc.update("""
                    INSERT INTO ycs_crypto_object_operations
                        (operation_id, protected_object_id, operation_state,
                         field_key_purpose, field_key_version)
                    VALUES (?, ?, ?, 'FIELD_ENCRYPTION_KEK', 7)
                    """, UUID.randomUUID().toString(), objectId, state);
        }

        String objectState(String objectId) {
            return jdbc.queryForObject("""
                    SELECT object_state FROM ycs_crypto_protected_objects
                    WHERE protected_object_id = ?
                    """, String.class, objectId);
        }

        String operationState(String objectId) {
            return jdbc.queryForObject("""
                    SELECT operation_state FROM ycs_crypto_object_operations
                    WHERE protected_object_id = ?
                    """, String.class, objectId);
        }

        String fieldPurpose(String objectId) {
            return jdbc.queryForObject("""
                    SELECT field_key_purpose FROM ycs_crypto_object_operations
                    WHERE protected_object_id = ?
                    """, String.class, objectId);
        }

        Long fieldVersion(String objectId) {
            return jdbc.queryForObject("""
                    SELECT field_key_version FROM ycs_crypto_object_operations
                    WHERE protected_object_id = ?
                    """, Long.class, objectId);
        }

        String sessionState(String sessionId) {
            return jdbc.queryForObject("""
                    SELECT session_state FROM ycs_crypto_registration_sessions
                    WHERE registration_session_id = ?
                    """, String.class, sessionId);
        }
    }

    private record CreateFixture(
            ProtectedObjectMetadataRepository.CreateOperation operation,
            StoredObjectMetadata stored) {
    }
}
