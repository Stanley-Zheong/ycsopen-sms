package com.ycsopen.sms.core.service.exemption;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthTestSupport;
import com.ycsopen.sms.core.web.dto.ExemptionPolicyCreateRequest;
import com.ycsopen.sms.core.web.dto.ExemptionPolicyPreviewRequest;
import com.ycsopen.sms.core.web.dto.ExemptionPolicyRevokeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExemptionPolicyServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-09T00:00:00Z"), ZoneId.of("UTC"));

    @Test
    void createsBoundedApprovedExemptionAndDecisionHistory() {
        JdbcTemplate jdbc = jdbc("exemption-create");
        ExemptionPolicyService service = service(jdbc);

        var created = service.create(request("SIGNATURE", "sig-1201", "APPROVED", "2026-09-01T00:00:00", "2026-10-01T00:00:00"));

        assertThat(created.tenantId()).isEqualTo(42L);
        assertThat(created.exemptionType()).isEqualTo("SIGNATURE");
        assertThat(created.resourceId()).isEqualTo("sig-1201");
        assertThat(created.productCode()).isEqualTo("DOMESTIC_SMS");
        assertThat(created.scopeExpression()).isEqualTo("LOGIN");
        assertThat(created.approvalStatus()).isEqualTo("APPROVED");
        assertThat(created.versionNo()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exempt_rule_history WHERE exempt_rule_id=? AND result='APPROVED'",
                Long.class, created.id())).isEqualTo(1L);
    }

    @Test
    void previewAppliesDeterministicPrecedenceAndCannotBypassNonExemptableControls() {
        JdbcTemplate jdbc = jdbc("exemption-preview");
        ExemptionPolicyService service = service(jdbc);
        var approvedV1 = service.create(request("CONTENT", "tpl-1", "APPROVED", "2026-09-01T00:00:00", "2026-09-20T00:00:00"));
        var approvedV2 = service.create(request("CONTENT", "tpl-1", "APPROVED", "2026-09-01T00:00:00", "2026-09-20T00:00:00"));

        var active = service.preview(preview("CONTENT", "tpl-1", "CONTENT_REVIEW"));
        assertThat(active.result()).isEqualTo("ACTIVE");
        assertThat(active.exemptionRuleId()).isEqualTo(approvedV2.id());
        assertThat(active.versionNo()).isEqualTo(2);

        var nonExemptable = service.preview(preview("CONTENT", "tpl-1", "ACCOUNT_BALANCE"));
        assertThat(nonExemptable.result()).isEqualTo("DENIED_NON_EXEMPTABLE");
        assertThat(nonExemptable.exemptionRuleId()).isNull();

        var outOfScope = service.preview(new ExemptionPolicyPreviewRequest(42L, "CONTENT", "tpl-other",
                "DOMESTIC_SMS", "LOGIN", "CONTENT_REVIEW", "范围验证", "operator-7"));
        assertThat(outOfScope.result()).isEqualTo("DENIED_OUT_OF_SCOPE");

        var pending = service.create(request("ACCOUNT", "acct-1", "PENDING", "2026-09-01T00:00:00", "2026-09-20T00:00:00"));
        assertThat(service.preview(preview("ACCOUNT", "acct-1", "FREQUENCY_LIMIT")).result()).isEqualTo("DENIED_UNAUTHORIZED");

        service.revoke(approvedV2.id(), new ExemptionPolicyRevokeRequest("撤销测试", "operator-7"));
        assertThat(service.preview(preview("CONTENT", "tpl-1", "CONTENT_REVIEW")).result()).isEqualTo("DENIED_REVOKED");
        service.revoke(approvedV1.id(), new ExemptionPolicyRevokeRequest("撤销测试", "operator-7"));

        service.create(request("SIGNATURE", "sig-old", "APPROVED", "2026-08-01T00:00:00", "2026-08-02T00:00:00"));
        assertThat(service.preview(preview("SIGNATURE", "sig-old", "SIGNATURE_REVIEW")).result()).isEqualTo("DENIED_EXPIRED");
        assertThat(pending.approvalStatus()).isEqualTo("PENDING");
    }

    @Test
    void everyDecisionAndUseStoresVersionSubjectActorReasonAndResult() {
        JdbcTemplate jdbc = jdbc("exemption-audit");
        ExemptionPolicyService service = service(jdbc);
        var created = service.create(request("SIGNATURE", "sig-1201", "APPROVED", "2026-09-01T00:00:00", "2026-10-01T00:00:00"));

        service.preview(preview("SIGNATURE", "sig-1201", "SIGNATURE_REVIEW"));
        service.revoke(created.id(), new ExemptionPolicyRevokeRequest("结束临时豁免", "operator-7"));

        var usage = service.usageHistory();
        assertThat(usage).hasSize(1);
        assertThat(usage.getFirst().exemptionRuleId()).isEqualTo(created.id());
        assertThat(usage.getFirst().versionNo()).isEqualTo(1);
        assertThat(usage.getFirst().subjectType()).isEqualTo("SIGNATURE");
        assertThat(usage.getFirst().subjectId()).isEqualTo("sig-1201");
        assertThat(usage.getFirst().actor()).isEqualTo("operator-7");
        assertThat(usage.getFirst().reason()).isEqualTo("范围验证");
        assertThat(usage.getFirst().result()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM exempt_rule_history WHERE exempt_rule_id=?",
                Long.class, created.id())).isEqualTo(2L);
    }

    @Test
    void specificUnauthorizedRuleWinsOverLaterApprovedWildcard() {
        JdbcTemplate jdbc = jdbc("exemption-wildcard");
        ExemptionPolicyService service = service(jdbc);
        service.create(request("CONTENT", "tpl-1", "PENDING", "2026-09-01T00:00:00", "2026-10-01T00:00:00"));
        service.create(new ExemptionPolicyCreateRequest(42L, "CONTENT", "*", "DOMESTIC_SMS", "LOGIN",
                "APPROVED", "2026-09-01T00:00:00", "2026-10-01T00:00:00", "通用豁免", "operator-7"));

        var specific = service.preview(preview("CONTENT", "tpl-1", "CONTENT_REVIEW"));
        var fallback = service.preview(preview("CONTENT", "tpl-other", "CONTENT_REVIEW"));

        assertThat(specific.result()).isEqualTo("DENIED_UNAUTHORIZED");
        assertThat(fallback.result()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsInvalidBoundaryFields() {
        ExemptionPolicyService service = service(jdbc("exemption-invalid"));

        assertThatThrownBy(() -> service.create(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("EXEMPTION_REQUEST_REQUIRED"));
        assertThatThrownBy(() -> service.preview(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("EXEMPTION_REQUEST_REQUIRED"));
        assertThatThrownBy(() -> service.revoke(404L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("EXEMPTION_REQUEST_REQUIRED"));
        assertThatThrownBy(() -> service.create(request("SIGNATURE", "sig-1201", "APPROVED", "2026-10-01T00:00:00", "2026-09-01T00:00:00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("EXEMPTION_VALIDITY_INVALID"));
        assertThatThrownBy(() -> service.create(request("CHANNEL", "sig-1201", "APPROVED", "2026-09-01T00:00:00", "2026-10-01T00:00:00")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("EXEMPTION_TYPE_INVALID"));
    }

    private static ExemptionPolicyService service(JdbcTemplate jdbc) {
        return new ExemptionPolicyService(jdbc, CLOCK);
    }

    private static ExemptionPolicyCreateRequest request(String type, String resourceId, String approval,
                                                        String validFrom, String validUntil) {
        return new ExemptionPolicyCreateRequest(42L, type, resourceId, "DOMESTIC_SMS", "LOGIN", approval,
                validFrom, validUntil, "临时业务豁免", "operator-7");
    }

    private static ExemptionPolicyPreviewRequest preview(String type, String resourceId, String control) {
        return new ExemptionPolicyPreviewRequest(42L, type, resourceId, "DOMESTIC_SMS", "LOGIN",
                control, "范围验证", "operator-7");
    }

    private static JdbcTemplate jdbc(String name) {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc(name);
        jdbc.execute("""
                CREATE TABLE exempt_rules (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    exempt_type VARCHAR(32) NOT NULL,
                    resource_id VARCHAR(128) NOT NULL DEFAULT '*',
                    product_code VARCHAR(64) NOT NULL DEFAULT 'SMS',
                    scope VARCHAR(255) NOT NULL,
                    approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    valid_from TIMESTAMP NOT NULL,
                    valid_until TIMESTAMP NOT NULL,
                    approved_by VARCHAR(64),
                    revoked_at TIMESTAMP,
                    revoked_by VARCHAR(64),
                    revoke_reason VARCHAR(255),
                    version_no INT NOT NULL DEFAULT 1,
                    reason VARCHAR(255) NOT NULL DEFAULT '',
                    created_by VARCHAR(64) NOT NULL DEFAULT 'system',
                    usage_count BIGINT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(tenant_id, exempt_type, resource_id, product_code, scope, version_no)
                )
                """);
        jdbc.execute("""
                CREATE TABLE exempt_rule_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    exempt_rule_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    subject_type VARCHAR(32) NOT NULL,
                    subject_id VARCHAR(128) NOT NULL,
                    product_code VARCHAR(64) NOT NULL,
                    scope_expression VARCHAR(255) NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    result VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE exempt_rule_usage_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    exempt_rule_id BIGINT,
                    version_no INT,
                    tenant_id BIGINT NOT NULL,
                    subject_type VARCHAR(32) NOT NULL,
                    subject_id VARCHAR(128) NOT NULL,
                    product_code VARCHAR(64) NOT NULL,
                    scope_expression VARCHAR(255) NOT NULL,
                    control_code VARCHAR(64) NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    result VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        return jdbc;
    }
}
