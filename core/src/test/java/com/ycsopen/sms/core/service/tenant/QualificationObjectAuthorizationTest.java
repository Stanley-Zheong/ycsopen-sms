package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.security.object.ObjectAccessAuthorizationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationObjectAuthorizationTest {
    private static final Instant NOW = Instant.parse("2026-09-07T08:00:00Z");
    private static final String OBJECT = "pobj_v1_abcdefghijklmnopqrstuvwxyzABCDEF";
    private JdbcTemplate jdbc;
    private QualificationObjectAuthorization authorization;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:qualification-auth;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, user_type VARCHAR(30), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY, status VARCHAR(20))");
        jdbc.execute("CREATE TABLE permissions(id BIGINT PRIMARY KEY, permission_code VARCHAR(100), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE user_roles(user_id BIGINT, role_id BIGINT, expires_at TIMESTAMP NULL)");
        jdbc.execute("CREATE TABLE role_permissions(role_id BIGINT, permission_id BIGINT)");
        jdbc.execute("CREATE TABLE tenants(id BIGINT PRIMARY KEY, business_license_url VARCHAR(255), legal_rep_id_front_url VARCHAR(255), legal_rep_id_back_url VARCHAR(255), shortlink_domain_proof_url VARCHAR(255), trademark_proof_url VARCHAR(255))");
        jdbc.execute("CREATE TABLE ycs_crypto_protected_objects(protected_object_id VARCHAR(80), tenant_draft_id VARCHAR(80), object_state VARCHAR(20))");
        jdbc.update("INSERT INTO tenants(id,business_license_url) VALUES (42,?)", OBJECT);
        jdbc.update("INSERT INTO ycs_crypto_protected_objects VALUES (?,?,'CLAIMED')", OBJECT, "draft-42");
        jdbc.update("INSERT INTO users VALUES (101,'OPERATOR','ACTIVE')");
        jdbc.update("INSERT INTO roles VALUES (1,'ACTIVE')");
        jdbc.update("INSERT INTO permissions VALUES (1,'tenant:evidence:read','ACTIVE')");
        jdbc.update("INSERT INTO user_roles VALUES (101,1,NULL)");
        jdbc.update("INSERT INTO role_permissions VALUES (1,1)");
        authorization = new QualificationObjectAuthorization(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void permitsOnlyLiveReviewerPermissionForAnOwnedClaimedEvidenceObject() {
        assertThat(authorization.authorize(request("101", "qualification-review", OBJECT))).isTrue();
        jdbc.update("UPDATE permissions SET status='DISABLED'");
        assertThat(authorization.authorize(request("101", "qualification-review", OBJECT))).isFalse();
        assertThat(authorization.authorize(request("101", "qualification-review", "pobj_v1_00000000000000000000000000000000"))).isFalse();
    }

    @Test
    void systemInspectionSubjectIsPurposeExactAndCannotAuthorizeReviewerReads() {
        assertThat(authorization.authorize(request(QualificationEvidenceService.OCR_SUBJECT,
                "qualification-ocr", OBJECT))).isTrue();
        assertThat(authorization.authorize(request(QualificationEvidenceService.OCR_SUBJECT,
                "qualification-review", OBJECT))).isFalse();
        assertThat(authorization.authorize(request("101", "qualification-ocr", OBJECT))).isFalse();
    }

    private ObjectAccessAuthorizationPort.Request request(String subject, String purpose, String object) {
        return new ObjectAccessAuthorizationPort.Request(object, "tenant:draft-42", subject, purpose,
                ObjectAccessAuthorizationPort.CapabilityState.ACTIVE, NOW.plusSeconds(60));
    }
}
