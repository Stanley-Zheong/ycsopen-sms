package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.object.ObjectAccessAuthorizationPort;
import com.ycsopen.sms.core.common.security.object.ObjectCapabilityService;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectMetadataRepository;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenDigestPublicationFenceTest {

    @Test
    void capabilityPreparedUnderPreviousVersionCannotPublishAfterActivation() {
        Fixture fixture = fixture();
        VersionedTokenDigest stale = digest(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, 1);
        activateV2(fixture.jdbc(), "OBJECT_CAPABILITY_DIGEST");
        ObjectCapabilityService.StoredCapability capability = new ObjectCapabilityService.StoredCapability(
                "A".repeat(22), "protected-object-1", new byte[32], new byte[32], "download",
                stale, ObjectAccessAuthorizationPort.CapabilityState.ACTIVE,
                Instant.parse("2099-01-01T00:00:00Z"));

        assertThatThrownBy(() -> fixture.metadata().create(capability))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(TokenDigestPublicationFence.SANITIZED_FAILURE);
        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_object_capabilities", Long.class)).isZero();
    }

    @Test
    void registrationSessionPreparedUnderPreviousVersionRollsBackAfterActivation() {
        Fixture fixture = fixture();
        VersionedTokenDigest stale = digest(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD, 1);
        activateV2(fixture.jdbc(), "REGISTRATION_UPLOAD_DIGEST");
        TenantRegistrationObjectSessionService.StoredSession session =
                new TenantRegistrationObjectSessionService.StoredSession(
                        "00000000-0000-4000-8000-000000000001",
                        "00000000-0000-4000-8000-000000000002",
                        TenantRegistrationObjectSessionService.SessionState.OPEN,
                        stale, Instant.parse("2099-01-01T00:00:00Z"), 0);

        assertThatThrownBy(() -> fixture.sessions().create(session))
                .isInstanceOf(TenantRegistrationObjectSessionService.Failure.class);
        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_registration_sessions", Long.class)).isZero();
    }

    @Test
    void fenceRequiresAnActualTransactionAndExactTokenPurpose() {
        Fixture fixture = fixture();
        JdbcTokenDigestPublicationFence fence = new JdbcTokenDigestPublicationFence(fixture.jdbc());

        assertThatThrownBy(() -> fence.lockAndValidate(
                digest(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(TokenDigestPublicationFence.SANITIZED_FAILURE);
    }

    private static Fixture fixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:token-publication-" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
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
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        return new Fixture(jdbc, new ProtectedObjectMetadataRepository(jdbc, manager),
                new TenantRegistrationObjectSessionService.JdbcSessionStore(jdbc, manager));
    }

    private static void activateV2(JdbcTemplate jdbc, String purpose) {
        jdbc.update("UPDATE ycs_crypto_key_references SET key_state='RETIRING' "
                + "WHERE purpose=? AND key_version=1", purpose);
        jdbc.update("UPDATE ycs_crypto_key_references SET key_state='ACTIVE' "
                + "WHERE purpose=? AND key_version=2", purpose);
    }

    private static VersionedTokenDigest digest(OpaqueTokenDigestPort.Purpose purpose, long version) {
        return new VersionedTokenDigest(purpose, version, new byte[32]);
    }

    private record Fixture(
            JdbcTemplate jdbc,
            ProtectedObjectMetadataRepository metadata,
            TenantRegistrationObjectSessionService.JdbcSessionStore sessions) {
    }
}
