package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.service.tenant.QualificationEvidenceService;
import com.ycsopen.sms.core.service.tenant.QualificationObjectAuthorization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real application composition over synthetic key, metadata, and private-storage adapters. */
class QualificationEvidenceCompositionTest {
    private static final byte[] PLAINTEXT =
            "synthetic-business-license".getBytes(StandardCharsets.UTF_8);
    private JdbcTemplate jdbc;
    private ProtectedObjectServiceTest.EvidenceStack stack;
    private QualificationEvidenceService evidence;
    private String objectId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:evidence-" + java.util.UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        createAuthorizationSchema();
        QualificationObjectAuthorization authorization = new QualificationObjectAuthorization(jdbc);
        Clock clock = Clock.systemUTC();
        stack = ProtectedObjectServiceTest.evidenceStack(authorization, clock);
        objectId = stack.createClaimed(PLAINTEXT);
        jdbc.update("INSERT INTO tenants(id,business_license_url) VALUES (42,?)", objectId);
        jdbc.update("INSERT INTO ycs_crypto_protected_objects VALUES (?,?,'CLAIMED')", objectId,
                "22222222-2222-4222-8222-222222222222");
        grantReviewer();
        evidence = new QualificationEvidenceService(jdbc, stack.capabilities, stack.objects);
    }

    @Test
    void readsClaimedBytesThroughTheRealCapabilityAuthorizationAndProtectedObjectStack() {
        QualificationEvidenceService.EvidenceContent content = evidence.readForReviewer(42,
                QualificationEvidenceService.EvidenceKind.BUSINESS_LICENSE, "101");
        assertThat(content.bytes()).containsExactly(PLAINTEXT);
        assertThat(stack.capabilitiesIn(ObjectAccessAuthorizationPort.CapabilityState.REVOKED))
                .isEqualTo(1);
        assertThat(content.toString()).doesNotContain(objectId, "ocap_v1_",
                "synthetic-business-license");
    }

    @Test
    void permissionRevocationDeniesBeforeCapabilityConsumption() {
        jdbc.update("UPDATE permissions SET status='DISABLED'");
        assertThatThrownBy(() -> evidence.readForReviewer(42,
                QualificationEvidenceService.EvidenceKind.BUSINESS_LICENSE, "101"))
                .isExactlyInstanceOf(QualificationEvidenceService.Failure.class)
                .hasMessage("QUALIFICATION_EVIDENCE_DENIED");
        assertThat(stack.capabilitiesIn(ObjectAccessAuthorizationPort.CapabilityState.ACTIVE))
                .isEqualTo(1);
        assertThat(stack.capabilitiesIn(ObjectAccessAuthorizationPort.CapabilityState.REVOKED))
                .isZero();
    }

    @Test
    void wrongBindingAndRevokedOwnershipAreDeniedBeforeConsumption() {
        String wrongBindingToken = stack.issue(objectId, "101");
        assertDenied(() -> stack.objects.read(new ProtectedObjectService.ReadRequest(objectId,
                wrongBindingToken, "tenant:other-draft", "101", "qualification-review",
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        assertThat(stack.capabilitiesIn(ObjectAccessAuthorizationPort.CapabilityState.ACTIVE))
                .isEqualTo(1);

        String noLongerOwnedToken = stack.issue(objectId, "101");
        jdbc.update("UPDATE tenants SET business_license_url=NULL WHERE id=42");
        assertDenied(() -> stack.objects.read(new ProtectedObjectService.ReadRequest(objectId,
                noLongerOwnedToken, "tenant:22222222-2222-4222-8222-222222222222", "101", "qualification-review",
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        assertThat(stack.capabilitiesIn(ObjectAccessAuthorizationPort.CapabilityState.ACTIVE))
                .isEqualTo(2);
    }

    @Test
    void oneTimeCapabilityCannotFetchTheProtectedBytesTwice() {
        String token = stack.issue(objectId, "101");
        ProtectedObjectService.ReadRequest request = new ProtectedObjectService.ReadRequest(objectId,
                token, "tenant:22222222-2222-4222-8222-222222222222", "101", "qualification-review",
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE);
        assertThat(stack.objects.read(request).bytes()).containsExactly(PLAINTEXT);
        assertDenied(() -> stack.objects.read(request));
        assertThat(stack.capabilitiesIn(ObjectAccessAuthorizationPort.CapabilityState.REVOKED))
                .isEqualTo(1);
    }

    private void createAuthorizationSchema() {
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, user_type VARCHAR(30), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY, status VARCHAR(20))");
        jdbc.execute("CREATE TABLE permissions(id BIGINT PRIMARY KEY, permission_code VARCHAR(100), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE user_roles(user_id BIGINT, role_id BIGINT, expires_at TIMESTAMP NULL)");
        jdbc.execute("CREATE TABLE role_permissions(role_id BIGINT, permission_id BIGINT)");
        jdbc.execute("CREATE TABLE tenants(id BIGINT PRIMARY KEY, business_license_url VARCHAR(255), legal_rep_id_front_url VARCHAR(255), legal_rep_id_back_url VARCHAR(255), shortlink_domain_proof_url VARCHAR(255), trademark_proof_url VARCHAR(255))");
        jdbc.execute("CREATE TABLE ycs_crypto_protected_objects(protected_object_id VARCHAR(80), tenant_draft_id VARCHAR(80), object_state VARCHAR(20))");
    }

    private void grantReviewer() {
        jdbc.update("INSERT INTO users VALUES (101,'OPERATOR','ACTIVE')");
        jdbc.update("INSERT INTO roles VALUES (1,'ACTIVE')");
        jdbc.update("INSERT INTO permissions VALUES (1,'tenant:evidence:read','ACTIVE')");
        jdbc.update("INSERT INTO user_roles VALUES (101,1,NULL)");
        jdbc.update("INSERT INTO role_permissions VALUES (1,1)");
    }

    private static void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isExactlyInstanceOf(ProtectedObjectService.Failure.class)
                .satisfies(failure -> assertThat(((ProtectedObjectService.Failure) failure).category())
                        .isEqualTo(ProtectedObjectService.Failure.Category
                                .PROTECTED_OBJECT_ACCESS_DENIED));
    }
}
