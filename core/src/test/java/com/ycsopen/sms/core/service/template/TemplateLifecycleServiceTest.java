package com.ycsopen.sms.core.service.template;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthTestSupport;
import com.ycsopen.sms.core.web.dto.TemplateApplicationRequest;
import com.ycsopen.sms.core.web.dto.TemplateDecisionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateLifecycleServiceTest {

    @Test
    void tenantSubmitsTemplateWithApprovedSignatureVariablesRulesAndHistory() {
        JdbcTemplate jdbc = jdbc("template-submit");
        seedSignature(jdbc, 9L, 42L, "APPROVED");
        TemplateLifecycleService service = new TemplateLifecycleService(jdbc);

        var template = service.submitApplication(42L, new TemplateApplicationRequest(
                "登录验证码", "您的验证码是 ${code}", "verification", 9L,
                "code:digits(4-8)", "登录二次验证"));

        assertThat(template.tenantId()).isEqualTo(42L);
        assertThat(template.templateName()).isEqualTo("登录验证码");
        assertThat(template.templateType()).isEqualTo("VERIFY");
        assertThat(template.signatureId()).isEqualTo(9L);
        assertThat(template.variableNames()).containsExactly("code");
        assertThat(template.auditStatus()).isEqualTo("PENDING");
        assertThat(template.history()).extracting("eventType").containsExactly("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT variable_names FROM templates WHERE id=?", String.class, template.id()))
                .isEqualTo("code");
    }

    @Test
    void applicationRejectsBadFieldsUnsafeContentAndWrongSignatureOwnership() {
        JdbcTemplate jdbc = jdbc("template-submit-invalid");
        seedSignature(jdbc, 9L, 43L, "APPROVED");
        TemplateLifecycleService service = new TemplateLifecycleService(jdbc);

        assertThatThrownBy(() -> service.submitApplication(42L, new TemplateApplicationRequest(
                "验证码", "您的验证码是 ${code}", "verification", 9L,
                "code:digits(4-8)", "wrong tenant")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_SIGNATURE_INVALID"));

        seedSignature(jdbc, 10L, 42L, "APPROVED");
        assertThatThrownBy(() -> service.submitApplication(42L, new TemplateApplicationRequest(
                "高危模板", "博彩开户链接 ${code}", "marketing", 10L,
                "code:digits(4-8)", "unsafe")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_CONTENT_UNSAFE"));

        assertThatThrownBy(() -> service.submitApplication(42L, new TemplateApplicationRequest(
                "规则错误", "您的验证码是 ${code}", "verification", 10L,
                "other:digits(4-8)", "bad rule")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_PARAM_RULE_INVALID"));
    }

    @Test
    void previewRendersExactDeclaredVariablesAndRejectsMissingExtraOrInjection() {
        JdbcTemplate jdbc = jdbc("template-preview");
        seedSignature(jdbc, 9L, 42L, "APPROVED");
        TemplateLifecycleService service = new TemplateLifecycleService(jdbc);
        long templateId = service.submitApplication(42L, new TemplateApplicationRequest(
                "登录验证码", "您的验证码是 ${code}", "verification", 9L,
                "code:digits(4-8)", "preview")).id();

        assertThat(service.preview(42L, templateId, java.util.Map.of("code", "2468")).renderedContent())
                .isEqualTo("您的验证码是 2468");
        assertThatThrownBy(() -> service.preview(42L, templateId, java.util.Map.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_VARIABLE_MISSING"));
        assertThatThrownBy(() -> service.preview(42L, templateId, java.util.Map.of("code", "2468", "extra", "x")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_VARIABLE_EXTRA"));
        assertThatThrownBy(() -> service.preview(42L, templateId, java.util.Map.of("code", "<script>")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_VARIABLE_RULE_MISMATCH"));
    }

    @Test
    void reviewDecisionAndResubmitPreservePriorVersionHistory() {
        JdbcTemplate jdbc = jdbc("template-review");
        seedSignature(jdbc, 9L, 42L, "APPROVED");
        TemplateLifecycleService service = new TemplateLifecycleService(jdbc);
        long original = service.submitApplication(42L, new TemplateApplicationRequest(
                "登录验证码", "您的验证码是 ${code}", "verification", 9L,
                "code:digits(4-8)", "review")).id();

        var rejected = service.decide(original, new TemplateDecisionRequest("REJECT", "变量说明不足", "operator-7"));
        assertThat(rejected.auditStatus()).isEqualTo("REJECTED");
        assertThat(rejected.history()).extracting("eventType").containsExactly("SUBMITTED", "REJECTED");

        var resubmitted = service.resubmit(42L, original, new TemplateApplicationRequest(
                "登录验证码", "您的验证码是 ${code}", "verification", 9L,
                "code:digits(4-8)", "补充变量说明"));
        assertThat(resubmitted.auditStatus()).isEqualTo("PENDING");
        assertThat(resubmitted.versionNo()).isEqualTo(2);
        assertThat(resubmitted.previousTemplateId()).isEqualTo(original);
        assertThat(service.get(original).auditStatus()).isEqualTo("REJECTED");

        assertThatThrownBy(() -> service.resubmit(42L, original, new TemplateApplicationRequest(
                "登录验证码2", "您的验证码是 ${code}", "verification", 9L,
                "code:digits(4-8)", "重复重新提交")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TEMPLATE_RESUBMIT_SUCCESSOR_EXISTS"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM templates WHERE previous_template_id=?",
                Long.class, original)).isEqualTo(1L);
    }

    private static JdbcTemplate jdbc(String name) {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc(name);
        jdbc.execute("""
                CREATE TABLE signatures (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    sign_content VARCHAR(64) NOT NULL,
                    audit_status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE templates (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    biz_type VARCHAR(32) NOT NULL DEFAULT 'DOMESTIC',
                    template_code VARCHAR(32) NOT NULL,
                    template_name VARCHAR(50) NOT NULL,
                    template_type VARCHAR(32) NOT NULL,
                    content VARCHAR(500) NOT NULL,
                    signature_id BIGINT NOT NULL,
                    param_check_rule VARCHAR(255),
                    description VARCHAR(255),
                    variable_names VARCHAR(255),
                    version_no INT NOT NULL DEFAULT 1,
                    previous_template_id BIGINT,
                    audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    audit_time TIMESTAMP,
                    audit_comment VARCHAR(500),
                    usage_count BIGINT NOT NULL DEFAULT 0,
                    is_system_template BOOLEAN NOT NULL DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(tenant_id, template_code),
                    UNIQUE(previous_template_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE template_review_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    template_id BIGINT NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    opinion VARCHAR(500) NOT NULL,
                    snapshot_content VARCHAR(500) NOT NULL,
                    variable_names VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        return jdbc;
    }

    private static void seedSignature(JdbcTemplate jdbc, long id, long tenantId, String status) {
        jdbc.update("INSERT INTO signatures(id,tenant_id,sign_content,audit_status) VALUES (?,?,?,?)",
                id, tenantId, "安全签名", status);
    }
}
