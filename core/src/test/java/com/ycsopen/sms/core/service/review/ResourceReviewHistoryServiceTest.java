package com.ycsopen.sms.core.service.review;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceReviewHistoryServiceTest {

    @Test
    void normalizesSignatureTemplateAndExemptionReviewDecisions() {
        JdbcTemplate jdbc = jdbc("review-history-normalize");
        seed(jdbc);
        ResourceReviewHistoryService service = new ResourceReviewHistoryService(jdbc);

        var rows = service.search(null, null, null, null, null, null, null, null);

        assertThat(rows).extracting("resourceType").contains("SIGNATURE", "TEMPLATE", "EXEMPTION");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.decisionId()).isEqualTo("SIGNATURE:1");
            assertThat(row.tenantId()).isEqualTo(42L);
            assertThat(row.decisionState()).isEqualTo("APPROVED");
            assertThat(row.actor()).isEqualTo("operator-7");
            assertThat(row.reason()).isEqualTo("材料完整");
            assertThat(row.riskLevel()).isEqualTo("HIGH");
            assertThat(row.evidenceRef()).isEqualTo("pobj-proof-1");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.decisionId()).isEqualTo("TEMPLATE:1");
            assertThat(row.resourceVersion()).isEqualTo("v2");
            assertThat(row.submittedSnapshot()).contains("${code}");
        });
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.decisionId()).isEqualTo("EXEMPTION:1");
            assertThat(row.submittedSnapshot()).isEqualTo("SIGNATURE:sig-1201");
            assertThat(row.evidenceRef()).isEqualTo("DOMESTIC_SMS LOGIN");
        });
    }

    @Test
    void filtersAndFetchesImmutableDetailByDecisionIdentity() {
        JdbcTemplate jdbc = jdbc("review-history-filter");
        seed(jdbc);
        ResourceReviewHistoryService service = new ResourceReviewHistoryService(jdbc);

        var signatureRows = service.search("signature", "42", "APPROVED", "operator", "HIGH", "YCSIG", null, null);
        assertThat(signatureRows).hasSize(1);
        assertThat(signatureRows.getFirst().decisionId()).isEqualTo("SIGNATURE:1");
        assertThat(service.search(null, null, null, null, null, null, null, null, "0", "1")).hasSize(1);
        assertThat(service.search(null, null, null, null, null, null, null, null, "1", "1")).hasSize(1);

        var detail = service.detail("TEMPLATE:1");
        assertThat(detail.resourceType()).isEqualTo("TEMPLATE");
        assertThat(detail.lifecycleLink()).isEqualTo("/admin/templates/review?keyword=TPL-001");

        assertThatThrownBy(() -> service.search("CHANNEL", null, null, null, null, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("REVIEW_HISTORY_TYPE_INVALID"));
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, "bad-date", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("REVIEW_HISTORY_CREATED_FROM_INVALID"));
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, null, null, "-1", "50"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("REVIEW_HISTORY_PAGE_INVALID"));
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, null, null, "10001", "200"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("REVIEW_HISTORY_PAGE_INVALID"));
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null, null, null, "0", "500"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("REVIEW_HISTORY_PAGE_SIZE_INVALID"));
        assertThatThrownBy(() -> service.detail("BAD"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("REVIEW_HISTORY_DECISION_ID_INVALID"));
    }

    private static JdbcTemplate jdbc(String name) {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc(name);
        jdbc.execute("""
                CREATE TABLE signatures (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    sign_code VARCHAR(32) NOT NULL,
                    sign_content VARCHAR(64) NOT NULL,
                    risk_level VARCHAR(32) NOT NULL,
                    evidence_url VARCHAR(255)
                )
                """);
        jdbc.execute("""
                CREATE TABLE signature_review_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    signature_id BIGINT NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    opinion VARCHAR(500) NOT NULL,
                    risk_level VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE templates (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    template_code VARCHAR(32) NOT NULL,
                    version_no INT NOT NULL,
                    signature_id BIGINT NOT NULL
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
        return jdbc;
    }

    private static void seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO signatures(id,tenant_id,sign_code,sign_content,risk_level,evidence_url) VALUES (1,42,'YCSIG','优创硕安','HIGH','pobj-proof-1')");
        jdbc.update("INSERT INTO signature_review_history(signature_id,event_type,actor,opinion,risk_level,created_at) VALUES (1,'APPROVED','operator-7','材料完整','HIGH','2026-09-09 01:00:00')");
        jdbc.update("INSERT INTO templates(id,tenant_id,template_code,version_no,signature_id) VALUES (2,42,'TPL-001',2,1)");
        jdbc.update("INSERT INTO template_review_history(template_id,event_type,actor,opinion,snapshot_content,variable_names,created_at) VALUES (2,'REJECTED','operator-8','变量说明不足','验证码 ${code}','code','2026-09-09 02:00:00')");
        jdbc.update("INSERT INTO exempt_rule_history(exempt_rule_id,version_no,event_type,tenant_id,subject_type,subject_id,product_code,scope_expression,actor,reason,result,created_at) VALUES (3,1,'CREATED',42,'SIGNATURE','sig-1201','DOMESTIC_SMS','LOGIN','operator-7','临时豁免','APPROVED','2026-09-09 03:00:00')");
    }
}
