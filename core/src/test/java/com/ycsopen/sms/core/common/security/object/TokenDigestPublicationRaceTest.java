package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.lifecycle.EnvelopeReferenceInventory;
import com.ycsopen.sms.core.common.security.key.lifecycle.JdbcTokenDigestPublicationFence;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyLifecycleService;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.key.lifecycle.TokenDigestPublicationFence;
import java.time.Instant;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import com.ycsopen.sms.core.verification.Phase03ServiceHarness;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenDigestPublicationRaceTest {

    @Test
    @EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
    void realMySqlSerializesBothTokenPublishersWithLifecycleActivation() throws Exception {
        try (Phase03ServiceHarness.ServiceSession mysql = Phase03ServiceHarness.startMySql()) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/phase01"
                            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                            + "&allowPublicKeyRetrieval=true&useSSL=false",
                    mysql.username(), mysql.password());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .placeholderReplacement(false).load().migrate();
            Fixture fixture = fixture(dataSource);
            seedTokenKeys(fixture.jdbc());
            String protectedObjectId = seedProtectedObject(fixture.jdbc());

            BlockingFence objectFence = new BlockingFence(fixture.jdbc());
            ProtectedObjectMetadataRepository repository = new ProtectedObjectMetadataRepository(
                    fixture.jdbc(), fixture.manager(), objectFence);
            ObjectCapabilityService.StoredCapability capability =
                    new ObjectCapabilityService.StoredCapability(
                            "B".repeat(22), protectedObjectId, new byte[32], new byte[32],
                            "download", digest(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY),
                            ObjectAccessAuthorizationPort.CapabilityState.ACTIVE,
                            Instant.parse("2099-01-01T00:00:00Z"));
            KeyLifecycleService objectLifecycle = lifecycle(fixture,
                    KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST,
                    "MYSQL_OBJECT_CAPABILITY_REFERENCES", "ycs_crypto_object_capabilities",
                    "digest_key_version", "capability_lookup_id");
            provePublisherFirst(objectFence,
                    () -> assertThat(repository.create(capability)).isTrue(),
                    () -> objectLifecycle.activate(
                            KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 2));
            assertThatThrownBy(() -> objectLifecycle.retire(
                    KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);

            BlockingFence registrationFence = new BlockingFence(fixture.jdbc());
            TenantRegistrationObjectSessionService.JdbcSessionStore sessions =
                    new TenantRegistrationObjectSessionService.JdbcSessionStore(
                            fixture.jdbc(), fixture.manager(), registrationFence);
            TenantRegistrationObjectSessionService.StoredSession session =
                    new TenantRegistrationObjectSessionService.StoredSession(
                            "00000000-0000-4000-8000-000000000011",
                            "00000000-0000-4000-8000-000000000012",
                            TenantRegistrationObjectSessionService.SessionState.OPEN,
                            digest(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD),
                            Instant.parse("2099-01-01T00:00:00Z"), 0);
            KeyLifecycleService registrationLifecycle = lifecycle(fixture,
                    KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST,
                    "MYSQL_REGISTRATION_SESSION_REFERENCES", "ycs_crypto_registration_sessions",
                    "upload_digest_key_version", "registration_session_id");
            provePublisherFirst(registrationFence,
                    () -> assertThat(sessions.create(session)).isTrue(),
                    () -> registrationLifecycle.activate(
                            KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 2));
            assertThatThrownBy(() -> registrationLifecycle.retire(
                    KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);

            fixture.jdbc().update("DELETE FROM ycs_crypto_object_capabilities "
                    + "WHERE capability_lookup_id = ?", capability.lookupId());
            resetPurpose(fixture.jdbc(), KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST);
            ObjectCapabilityService.StoredCapability staleCapability =
                    new ObjectCapabilityService.StoredCapability(
                            "C".repeat(22), protectedObjectId, new byte[32], new byte[32],
                            "download", digest(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY),
                            ObjectAccessAuthorizationPort.CapabilityState.ACTIVE,
                            Instant.parse("2099-01-01T00:00:00Z"));
            proveLifecycleFirst(dataSource,
                    KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST,
                    () -> new ProtectedObjectMetadataRepository(
                            fixture.jdbc(), fixture.manager()).create(staleCapability));
            assertThat(fixture.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM ycs_crypto_object_capabilities "
                            + "WHERE capability_lookup_id = ?", Long.class,
                    staleCapability.lookupId())).isZero();

            fixture.jdbc().update("DELETE FROM ycs_crypto_registration_sessions "
                    + "WHERE registration_session_id = ?", session.registrationSessionId());
            resetPurpose(fixture.jdbc(), KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST);
            TenantRegistrationObjectSessionService.StoredSession staleSession =
                    new TenantRegistrationObjectSessionService.StoredSession(
                            "00000000-0000-4000-8000-000000000031",
                            "00000000-0000-4000-8000-000000000032",
                            TenantRegistrationObjectSessionService.SessionState.OPEN,
                            digest(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD),
                            Instant.parse("2099-01-01T00:00:00Z"), 0);
            proveLifecycleFirst(dataSource,
                    KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST,
                    () -> new TenantRegistrationObjectSessionService.JdbcSessionStore(
                            fixture.jdbc(), fixture.manager()).create(staleSession));
            assertThat(fixture.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM ycs_crypto_registration_sessions "
                            + "WHERE registration_session_id = ?", Long.class,
                    staleSession.registrationSessionId())).isZero();
        }
    }

    @Test
    void capabilityPublicationCommitsBeforeActivationAndThenBlocksRetirement() throws Exception {
        Fixture fixture = fixture();
        BlockingFence fence = new BlockingFence(fixture.jdbc());
        ProtectedObjectMetadataRepository repository = new ProtectedObjectMetadataRepository(
                fixture.jdbc(), fixture.manager(), fence);
        ObjectCapabilityService.StoredCapability capability = new ObjectCapabilityService.StoredCapability(
                "A".repeat(22), "protected-object-race", new byte[32], new byte[32], "download",
                digest(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY),
                ObjectAccessAuthorizationPort.CapabilityState.ACTIVE,
                Instant.parse("2099-01-01T00:00:00Z"));
        KeyLifecycleService lifecycle = lifecycle(fixture,
                KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST,
                "OBJECT_CAPABILITY_REFERENCES", "ycs_crypto_object_capabilities",
                "digest_key_version", "capability_lookup_id");

        provePublisherFirst(fence, () -> assertThat(repository.create(capability)).isTrue(),
                () -> lifecycle.activate(KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 2));
        assertThatThrownBy(() -> lifecycle.retire(
                KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);
    }

    @Test
    void registrationPublicationCommitsBeforeActivationAndThenBlocksRetirement() throws Exception {
        Fixture fixture = fixture();
        BlockingFence fence = new BlockingFence(fixture.jdbc());
        TenantRegistrationObjectSessionService.JdbcSessionStore sessions =
                new TenantRegistrationObjectSessionService.JdbcSessionStore(
                        fixture.jdbc(), fixture.manager(), fence);
        TenantRegistrationObjectSessionService.StoredSession session =
                new TenantRegistrationObjectSessionService.StoredSession(
                        "00000000-0000-4000-8000-000000000001",
                        "00000000-0000-4000-8000-000000000002",
                        TenantRegistrationObjectSessionService.SessionState.OPEN,
                        digest(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD),
                        Instant.parse("2099-01-01T00:00:00Z"), 0);
        KeyLifecycleService lifecycle = lifecycle(fixture,
                KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST,
                "REGISTRATION_SESSION_REFERENCES", "ycs_crypto_registration_sessions",
                "upload_digest_key_version", "registration_session_id");

        provePublisherFirst(fence, () -> assertThat(sessions.create(session)).isTrue(),
                () -> lifecycle.activate(KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 2));
        assertThatThrownBy(() -> lifecycle.retire(
                KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);
    }

    private static void provePublisherFirst(
            BlockingFence fence, Runnable publish, Runnable activate) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> publisher = executor.submit(publish);
            assertThat(fence.locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> activation = executor.submit(activate);
            assertThatThrownBy(() -> activation.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            fence.release.countDown();
            publisher.get(5, TimeUnit.SECONDS);
            activation.get(5, TimeUnit.SECONDS);
        }
    }

    private static void proveLifecycleFirst(
            DataSource dataSource,
            KeyReferenceRepository.Purpose purpose,
            Runnable publish) throws Exception {
        CommitBlockingDataSource gated = new CommitBlockingDataSource(dataSource);
        Fixture lifecycleFixture = fixture(gated);
        KeyLifecycleService lifecycle = lifecycle(lifecycleFixture, purpose,
                "LIFECYCLE_FIRST_" + purpose.name(),
                purpose == KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST
                        ? "ycs_crypto_object_capabilities" : "ycs_crypto_registration_sessions",
                purpose == KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST
                        ? "digest_key_version" : "upload_digest_key_version",
                purpose == KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST
                        ? "capability_lookup_id" : "registration_session_id");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> activation = executor.submit(() -> lifecycle.activate(purpose, 2));
            assertThat(gated.beforeCommit.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> publishing = executor.submit(() -> {
                try {
                    publish.run();
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            assertThatThrownBy(() -> publishing.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            gated.release.countDown();
            activation.get(5, TimeUnit.SECONDS);
            assertThat(publishing.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    private static KeyLifecycleService lifecycle(
            Fixture fixture,
            KeyReferenceRepository.Purpose purpose,
            String sourceId,
            String table,
            String versionColumn,
            String identityColumn) {
        EnvelopeReferenceInventory.Source source = new EnvelopeReferenceInventory.Source() {
            @Override
            public String sourceId() {
                return sourceId;
            }

            @Override
            public List<EnvelopeReferenceInventory.Reference> liveReferences() {
                return fixture.jdbc().query("SELECT " + versionColumn + ", " + identityColumn
                                + " FROM " + table,
                        (resultSet, row) -> new EnvelopeReferenceInventory.Reference(
                                sourceId, purpose == KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST
                                ? EnvelopeReferenceInventory.Kind.OBJECT_CAPABILITY
                                : EnvelopeReferenceInventory.Kind.REGISTRATION_UPLOAD_SESSION,
                                purpose, resultSet.getLong(1), new byte[32]));
            }
        };
        return new KeyLifecycleService(fixture.keys(),
                new EnvelopeReferenceInventory(Set.of(sourceId), List.of(source)));
    }

    private static VersionedTokenDigest digest(OpaqueTokenDigestPort.Purpose purpose) {
        return new VersionedTokenDigest(purpose, 1, new byte[32]);
    }

    private static Fixture fixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:token-race-" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        Fixture fixture = fixture(dataSource);
        JdbcTemplate jdbc = fixture.jdbc();
        jdbc.execute("CREATE TABLE ycs_crypto_key_references ("
                + "purpose VARCHAR(48) NOT NULL, key_version BIGINT NOT NULL, "
                + "provider_id VARCHAR(32) NOT NULL, provider_key_reference VARCHAR(128) NOT NULL, "
                + "key_state VARCHAR(24) NOT NULL, wrap_operation_count BIGINT NOT NULL DEFAULT 0, "
                + "rotation_required BOOLEAN NOT NULL DEFAULT FALSE, optimistic_version BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (purpose,key_version))");
        for (String purpose : new String[]{"OBJECT_CAPABILITY_DIGEST", "REGISTRATION_UPLOAD_DIGEST"}) {
            jdbc.update("INSERT INTO ycs_crypto_key_references "
                            + "(purpose,key_version,provider_id,provider_key_reference,key_state) VALUES "
                            + "(?,1,'pkcs11',?,'ACTIVE'),(?,2,'pkcs11',?,'PREPARED')",
                    purpose, purpose.toLowerCase() + ".v1",
                    purpose, purpose.toLowerCase() + ".v2");
        }
        jdbc.execute("CREATE TABLE ycs_crypto_object_capabilities ("
                + "capability_lookup_id VARCHAR(64) PRIMARY KEY, protected_object_id VARCHAR(128) NOT NULL, "
                + "tenant_binding_digest BINARY(32) NOT NULL, subject_binding_digest BINARY(32) NOT NULL, "
                + "capability_purpose VARCHAR(40) NOT NULL, digest_key_purpose VARCHAR(48) NOT NULL, "
                + "digest_key_version BIGINT NOT NULL, capability_credential_digest BINARY(32) NOT NULL, "
                + "capability_state VARCHAR(16) NOT NULL, expires_at TIMESTAMP NOT NULL, "
                + "optimistic_version BIGINT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE ycs_crypto_registration_sessions ("
                + "registration_session_id VARCHAR(36) PRIMARY KEY, tenant_draft_id VARCHAR(36) NOT NULL, "
                + "session_state VARCHAR(16) NOT NULL, upload_digest_purpose VARCHAR(48) NOT NULL, "
                + "upload_digest_key_version BIGINT NOT NULL, upload_credential_digest BINARY(32) NOT NULL, "
                + "admitted_attempt_count INT NOT NULL DEFAULT 0, expires_at TIMESTAMP NOT NULL, "
                + "optimistic_version BIGINT NOT NULL DEFAULT 0)");
        return fixture;
    }

    private static Fixture fixture(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        return new Fixture(jdbc, manager, new KeyReferenceRepository.Jdbc(
                jdbc, new TransactionTemplate(manager)));
    }

    private static void seedTokenKeys(JdbcTemplate jdbc) {
        for (String purpose : new String[]{"OBJECT_CAPABILITY_DIGEST", "REGISTRATION_UPLOAD_DIGEST"}) {
            jdbc.update("INSERT INTO ycs_crypto_key_references "
                            + "(purpose,key_version,provider_id,provider_key_reference,key_state) VALUES "
                            + "(?,1,'pkcs11',?,'ACTIVE'),(?,2,'pkcs11',?,'PREPARED')",
                    purpose, purpose.toLowerCase() + ".v1",
                    purpose, purpose.toLowerCase() + ".v2");
        }
    }

    private static void resetPurpose(
            JdbcTemplate jdbc, KeyReferenceRepository.Purpose purpose) {
        jdbc.update("UPDATE ycs_crypto_key_references SET key_state = CASE key_version "
                        + "WHEN 1 THEN 'ACTIVE' ELSE 'PREPARED' END, optimistic_version = 0 "
                        + "WHERE purpose = ?",
                purpose.name());
    }

    private static String seedProtectedObject(JdbcTemplate jdbc) {
        String sessionId = "00000000-0000-4000-8000-000000000021";
        String tenantDraftId = "00000000-0000-4000-8000-000000000022";
        String protectedObjectId = "pobj_v1_" + "a".repeat(32);
        jdbc.update("""
                INSERT INTO ycs_crypto_registration_sessions
                    (registration_session_id, tenant_draft_id, session_state,
                     upload_digest_purpose, upload_digest_key_version,
                     upload_credential_digest, expires_at)
                VALUES (?, ?, 'OPEN', 'REGISTRATION_UPLOAD_DIGEST', 2, ?, ?)
                """, sessionId, tenantDraftId, new byte[32],
                java.sql.Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")));
        jdbc.update("""
                INSERT INTO ycs_crypto_protected_objects
                    (protected_object_id, registration_session_id, tenant_draft_id,
                     object_purpose, object_state, opaque_store_locator, envelope_digest,
                     envelope_size, media_type, expires_at)
                VALUES (?, ?, ?, 'BUSINESS_LICENSE', 'STAGED', ?, ?, 1,
                        'application/octet-stream', ?)
                """, protectedObjectId, sessionId, tenantDraftId, "object-locator-token-race",
                new byte[32], java.sql.Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")));
        return protectedObjectId;
    }

    private static final class BlockingFence implements TokenDigestPublicationFence {
        private final JdbcTokenDigestPublicationFence delegate;
        private final CountDownLatch locked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean first = new AtomicBoolean(true);

        private BlockingFence(JdbcTemplate jdbc) {
            delegate = new JdbcTokenDigestPublicationFence(jdbc);
        }

        @Override
        public void lockAndValidate(VersionedTokenDigest digest) {
            delegate.lockAndValidate(digest);
            if (first.compareAndSet(true, false)) {
                locked.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("publication coordination failed");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
        }
    }

    private static final class CommitBlockingDataSource extends DelegatingDataSource {
        private final CountDownLatch beforeCommit = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean firstCommit = new AtomicBoolean(true);

        private CommitBlockingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return gate(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return gate(super.getConnection(username, password));
        }

        private Connection gate(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("commit".equals(method.getName())
                                && firstCommit.compareAndSet(true, false)) {
                            beforeCommit.countDown();
                            await(release);
                        }
                        try {
                            return method.invoke(delegate, arguments);
                        } catch (InvocationTargetException failure) {
                            throw failure.getTargetException();
                        }
                    });
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("lifecycle commit coordination failed");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }

    private record Fixture(
            JdbcTemplate jdbc,
            DataSourceTransactionManager manager,
            KeyReferenceRepository keys) {
    }
}
