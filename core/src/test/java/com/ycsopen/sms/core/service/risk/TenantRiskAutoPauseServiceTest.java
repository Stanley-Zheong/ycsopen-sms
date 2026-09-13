package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRiskAutoPauseServiceTest {
    private JdbcTemplate jdbc;
    private TenantRiskAutoPauseService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase42-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false")
                .build();
        jdbc = new JdbcTemplate(database);
        createSchema();
        service = new TenantRiskAutoPauseService(jdbc);
    }

    @Test
    void zeroDenominatorCreatesUnknownEpisodeWithoutMisleadingSafeRateOrPause() {
        tenant(7, "SIGNED");
        service.saveRule(new TenantRiskAutoPauseService.RuleCommand(
                "投诉率预警", 7L, "COMPLAINT_RATE", new BigDecimal("0.0100"), 30,
                "AUTO_SUSPEND", "[\"tenant:7\",\"ops\"]", "ACTIVE"), "operator");

        TenantRiskAutoPauseService.EvaluationResult result = service.evaluate(new TenantRiskAutoPauseService.EvaluationCommand(
                7L, "COMPLAINT_RATE", 0L, 0L, 30, "complaints-empty-window", "complaints/sms_send_stats"), "operator");

        assertThat(result.dataQuality()).isEqualTo("UNKNOWN");
        assertThat(result.rate()).isNull();
        assertThat(result.paused()).isFalse();
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=7", String.class)).isEqualTo("SIGNED");
        assertThat(jdbc.queryForObject("SELECT rate FROM tenant_risk_episodes WHERE source_key=?", BigDecimal.class, "complaints-empty-window")).isNull();
        assertThat(jdbc.queryForObject("SELECT paused_by FROM tenant_risk_episodes WHERE source_key=?", String.class, "complaints-empty-window")).isNull();
        assertThat(jdbc.queryForObject("SELECT paused_at FROM tenant_risk_episodes WHERE source_key=?", Object.class, "complaints-empty-window")).isNull();
    }

    @Test
    void sustainedBreachCreatesOneEpisodeAndAutoPauseRejectsNewSends() {
        tenant(8, "SIGNED");
        service.saveRule(new TenantRiskAutoPauseService.RuleCommand(
                "失败率封停", 8L, "FAILURE_RATE", new BigDecimal("0.2000"), 15,
                "AUTO_SUSPEND", "[\"tenant:8\",\"ops\"]", "ACTIVE"), "operator");

        TenantRiskAutoPauseService.EvaluationCommand command = new TenantRiskAutoPauseService.EvaluationCommand(
                8L, "FAILURE_RATE", 25L, 100L, 15, "failure-window-8", "statistics_aggregates");
        TenantRiskAutoPauseService.EvaluationResult first = service.evaluate(command, "operator");
        TenantRiskAutoPauseService.EvaluationResult second = service.evaluate(command, "operator");

        assertThat(first.dataQuality()).isEqualTo("COMPLETE");
        assertThat(first.paused()).isTrue();
        assertThat(second.episodeId()).isEqualTo(first.episodeId());
        assertThat(count("tenant_risk_episodes")).isEqualTo(1);
        assertThat(count("alert_records")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=8", String.class)).isEqualTo("FROZEN");
        assertThatThrownBy(() -> service.assertSubmissionAllowed(8))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("TENANT_RISK_AUTO_PAUSED");
    }

    @Test
    void recoveryRequiresAuthorizedReviewAndRestoresOriginalLifecycleWithEvidence() {
        tenant(9, "TRIAL");
        service.saveRule(new TenantRiskAutoPauseService.RuleCommand(
                "退订率封停", 9L, "UNSUBSCRIBE_RATE", new BigDecimal("0.0500"), 20,
                "AUTO_SUSPEND", "[\"tenant:9\"]", "ACTIVE"), "operator");
        TenantRiskAutoPauseService.EvaluationResult result = service.evaluate(new TenantRiskAutoPauseService.EvaluationCommand(
                9L, "UNSUBSCRIBE_RATE", 7L, 100L, 20, "unsubscribe-window-9", "unsubscribe_records"), "operator");

        assertThatThrownBy(() -> service.recover(result.episodeId(), new TenantRiskAutoPauseService.RecoveryCommand("", "整改完成"), "operator"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("TENANT_RISK_RECOVERY_REVIEW_REQUIRED");

        TenantRiskAutoPauseService.EpisodeRow recovered = service.recover(
                result.episodeId(), new TenantRiskAutoPauseService.RecoveryCommand("review-42-1", "投诉与退订来源复核通过"), "operator");

        assertThat(recovered.status()).isEqualTo("RESOLVED");
        assertThat(recovered.recoveryReviewId()).isEqualTo("review-42-1");
        assertThat(recovered.sourceSnapshot()).contains("numerator=7").contains("denominator=100");
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=9", String.class)).isEqualTo("TRIAL");
        service.assertSubmissionAllowed(9);
    }

    @Test
    void notifyOnlyBreachKeepsSubmissionOpenButRetainsEpisodeAndAlertEvidence() {
        tenant(10, "SIGNED");
        service.saveRule(new TenantRiskAutoPauseService.RuleCommand(
                "投诉率通知", 10L, "COMPLAINT_RATE", new BigDecimal("0.0100"), 10,
                "NOTIFY", "[\"ops\"]", "ACTIVE"), "operator");

        TenantRiskAutoPauseService.EvaluationResult result = service.evaluate(new TenantRiskAutoPauseService.EvaluationCommand(
                10L, "COMPLAINT_RATE", 2L, 100L, 10, "complaint-window-10", "complaints"), "operator");

        assertThat(result.paused()).isFalse();
        assertThat(service.episodes(10L)).hasSize(1);
        assertThat(count("alert_records")).isEqualTo(1);
        service.assertSubmissionAllowed(10);
    }

    private void tenant(long id, String lifecycle) {
        jdbc.update("INSERT INTO tenants(id,lifecycle_status) VALUES (?,?)", id, lifecycle);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE tenants(
                  id BIGINT PRIMARY KEY,
                  lifecycle_status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE tenant_alert_rules(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  rule_name VARCHAR(100) NOT NULL,
                  tenant_id BIGINT,
                  metric VARCHAR(32) NOT NULL,
                  threshold_value DECIMAL(12,4) NOT NULL,
                  duration_minutes INT NOT NULL,
                  action VARCHAR(32) NOT NULL,
                  notify_targets VARCHAR(1000),
                  status VARCHAR(32) NOT NULL,
                  created_by VARCHAR(64),
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE alert_records(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  rule_id BIGINT NOT NULL,
                  title VARCHAR(255) NOT NULL,
                  content VARCHAR(1000),
                  metric_value DECIMAL(12,4),
                  status VARCHAR(32) NOT NULL,
                  triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  severity VARCHAR(16) NOT NULL,
                  source_module VARCHAR(64) NOT NULL,
                  source_key VARCHAR(128) NOT NULL,
                  impact_scope VARCHAR(255),
                  delivery_state VARCHAR(32) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE tenant_risk_episodes(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  rule_id BIGINT NOT NULL,
                  alert_record_id BIGINT,
                  metric VARCHAR(32) NOT NULL,
                  source_key VARCHAR(128) NOT NULL UNIQUE,
                  source_registry VARCHAR(128) NOT NULL,
                  numerator BIGINT NOT NULL,
                  denominator BIGINT NOT NULL,
                  rate DECIMAL(12,6),
                  threshold_value DECIMAL(12,6) NOT NULL,
                  window_minutes INT NOT NULL,
                  data_quality VARCHAR(32) NOT NULL,
                  action VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  before_lifecycle_status VARCHAR(32),
                  source_snapshot VARCHAR(1000) NOT NULL,
                  paused_by VARCHAR(64),
                  paused_at TIMESTAMP,
                  recovery_review_id VARCHAR(128),
                  recovery_note VARCHAR(255),
                  recovered_by VARCHAR(64),
                  recovered_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
