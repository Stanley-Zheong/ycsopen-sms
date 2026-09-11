package com.ycsopen.sms.core.service.alert;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertEngineServiceTest {
    private JdbcTemplate jdbc;
    private AlertEngineService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase35-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new AlertEngineService(jdbc);
    }

    @Test
    void ruleConfigurationCreatesOneEpisodeAndDeliveryAttemptForSustainedBreach() {
        var rule = service.saveRule(new AlertEngineService.RuleCommand(
                "通道失败率告警", "FAILURE_RATE", "FAILURE_RATE", "statistics_aggregates",
                new BigDecimal("0.1000"), ">=", 5, "CRITICAL", "[\"SMS\",\"EMAIL\",\"DINGTALK\",\"WECOM\"]",
                "[\"operations\",\"finance\",\"tenant:7\"]", "PLATFORM", "ACTIVE"), "operator");

        var event = new AlertEngineService.SourceEvent("FAILURE_RATE", new BigDecimal("0.1800"), 6,
                "CHANNEL", "channel:11:2026-01-01T10", "通道失败率过高",
                "通道 11 失败率超过阈值", "影响 1250 项", false);
        var first = service.evaluate(event, "operator");
        var second = service.evaluate(event, "operator");

        assertThat(rule.notifyChannels()).contains("SMS", "EMAIL", "DINGTALK", "WECOM");
        assertThat(first.alerts()).hasSize(1);
        assertThat(second.alerts()).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM alert_records")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM alert_delivery_attempts WHERE alert_record_id=?",
                first.alerts().get(0).id())).isEqualTo(4);
        var alert = service.history(new AlertEngineService.HistoryFilter("ACTIVE", "CRITICAL")).get(0);
        assertThat(alert.title()).isEqualTo("通道失败率过高");
        assertThat(alert.severity()).isEqualTo("CRITICAL");
        assertThat(alert.sourceModule()).isEqualTo("CHANNEL");
        assertThat(alert.impactScope()).isEqualTo("影响 1250 项");
        assertThat(alert.deliveryState()).isEqualTo("DELIVERED");
        assertThat(service.dashboard()).isEqualTo(new AlertEngineService.Dashboard(1, 1, 1, 0));
    }

    @Test
    void adapterFailureRecordsAttemptWithoutLosingAlertAndRecoveryResolvesEpisode() {
        service.saveRule(new AlertEngineService.RuleCommand(
                "队列积压告警", "QUEUE_BACKLOG", "QUEUE_BACKLOG", "queue_depth",
                new BigDecimal("1000"), ">=", 3, "HIGH", "[\"EMAIL\"]",
                "[\"operations\"]", "PLATFORM", "ACTIVE"), "operator");

        var breach = new AlertEngineService.SourceEvent("QUEUE_BACKLOG", new BigDecimal("2000"), 4,
                "QUEUE", "queue:main", "主队列积压", "主队列积压超过阈值", "影响主队列", true);
        var result = service.evaluate(breach, "operator");
        assertThat(result.alerts()).hasSize(1);
        assertThat(result.alerts().get(0).deliveryState()).isEqualTo("FAILED");
        assertThat(service.deliveries(result.alerts().get(0).id())).singleElement()
                .satisfies(row -> {
                    assertThat(row.status()).isEqualTo("FAILED");
                    assertThat(row.retryCount()).isEqualTo(1);
                    assertThat(row.failureReason()).contains("adapter");
                });

        var recovered = new AlertEngineService.SourceEvent("QUEUE_BACKLOG", new BigDecimal("50"), 4,
                "QUEUE", "queue:main", "主队列恢复", "主队列已恢复", "影响主队列", false);
        service.evaluate(recovered, "operator");

        var alert = service.history(new AlertEngineService.HistoryFilter("RESOLVED", "HIGH")).get(0);
        assertThat(alert.status()).isEqualTo("RESOLVED");
        assertThat(alert.resolutionNote()).isEqualTo("SOURCE_RECOVERED");
        assertThat(service.dashboard().resolvedCount()).isEqualTo(1);
    }

    @Test
    void acknowledgeResolveAndMuteFollowStateContract() {
        service.saveRule(new AlertEngineService.RuleCommand(
                "余额不足告警", "BALANCE", "BALANCE", "prepaid_accounts",
                new BigDecimal("100"), "<=", 1, "MEDIUM", "[\"EMAIL\"]",
                "[\"finance\"]", "TENANT", "ACTIVE"), "operator");
        long alertId = service.evaluate(new AlertEngineService.SourceEvent(
                "BALANCE", new BigDecimal("20"), 1, "BILLING", "tenant:7:balance",
                "余额不足", "租户 7 余额低于阈值", "租户 7", false), "operator").alerts().get(0).id();

        var mute = service.mute(alertId, new AlertEngineService.MuteCommand(30, "夜间维护"), "operator");
        var muted = service.history(new AlertEngineService.HistoryFilter("ACTIVE", null)).get(0);
        assertThat(mute.scope()).isEqualTo("GLOBAL");
        assertThat(muted.status()).isEqualTo("ACTIVE");
        assertThat(muted.deliveryState()).isEqualTo("MUTED");

        var acknowledged = service.acknowledge(alertId, "operator");
        assertThat(acknowledged.status()).isEqualTo("ACKNOWLEDGED");
        assertThat(acknowledged.acknowledgedBy()).isEqualTo("operator");
        var resolved = service.resolve(alertId, new AlertEngineService.ResolveCommand("确认恢复"), "operator");
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.resolvedBy()).isEqualTo("operator");
        assertThatThrownBy(() -> service.acknowledge(alertId, "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有活跃告警可以确认");
        assertThatThrownBy(() -> service.mute(alertId, new AlertEngineService.MuteCommand(30, "已解决后误操作"), "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有活跃或已确认告警可以静音");
    }

    @Test
    void globalMuteSuppressesNewNotificationAttemptsOnly() {
        service.saveRule(new AlertEngineService.RuleCommand(
                "投诉率告警", "COMPLAINT_RATIO", "COMPLAINT_RATIO", "complaint_ratio",
                new BigDecimal("0.0500"), ">=", 1, "HIGH", "[\"EMAIL\"]",
                "[\"operations\"]", "TENANT", "ACTIVE"), "operator");
        jdbc.update("""
                INSERT INTO alert_mutes(scope, reason, muted_by, muted_until)
                VALUES ('GLOBAL', '维护', 'operator', DATEADD('MINUTE', 30, CURRENT_TIMESTAMP))
                """);

        var result = service.evaluate(new AlertEngineService.SourceEvent(
                "COMPLAINT_RATIO", new BigDecimal("0.0800"), 1, "COMPLAINT",
                "tenant:7:complaint", "投诉率超阈值", "投诉率超阈值", "租户 7", false), "operator");

        assertThat(result.alerts()).hasSize(1);
        assertThat(result.alerts().get(0).status()).isEqualTo("ACTIVE");
        assertThat(result.alerts().get(0).deliveryState()).isEqualTo("MUTED");
        assertThat(count("SELECT COUNT(*) FROM alert_delivery_attempts")).isZero();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE alert_rules(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  rule_name VARCHAR(128) NOT NULL,
                  rule_type VARCHAR(32) NOT NULL,
                  metric_name VARCHAR(64) NOT NULL,
                  metric_source VARCHAR(64) NOT NULL DEFAULT 'statistics_aggregates',
                  threshold_value DECIMAL(12,4) NOT NULL,
                  comparison_op VARCHAR(8) NOT NULL DEFAULT '>=',
                  duration_minutes INT NOT NULL DEFAULT 5,
                  severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
                  notify_channels VARCHAR(1000),
                  notification_targets VARCHAR(1000),
                  source_scope VARCHAR(64) NOT NULL DEFAULT 'PLATFORM',
                  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
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
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  triggered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  acknowledged_at TIMESTAMP,
                  resolved_at TIMESTAMP,
                  severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
                  source_module VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
                  source_key VARCHAR(128) NOT NULL DEFAULT 'UNKNOWN',
                  impact_scope VARCHAR(255),
                  acknowledged_by VARCHAR(64),
                  resolved_by VARCHAR(64),
                  resolution_note VARCHAR(255),
                  delivery_state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  muted_until TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE alert_delivery_attempts(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  alert_record_id BIGINT NOT NULL,
                  channel VARCHAR(32) NOT NULL,
                  target_snapshot VARCHAR(500) NOT NULL,
                  provider_result VARCHAR(64) NOT NULL,
                  retry_count INT NOT NULL DEFAULT 0,
                  status VARCHAR(16) NOT NULL DEFAULT 'DELIVERED',
                  failure_reason VARCHAR(255),
                  attempted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE alert_mutes(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  scope VARCHAR(64) NOT NULL DEFAULT 'GLOBAL',
                  reason VARCHAR(255) NOT NULL,
                  muted_by VARCHAR(64) NOT NULL,
                  muted_until TIMESTAMP NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
