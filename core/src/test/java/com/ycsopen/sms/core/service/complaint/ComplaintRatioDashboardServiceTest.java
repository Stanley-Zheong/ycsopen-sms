package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintRatioDashboardServiceTest {
    private JdbcTemplate jdbc;
    private ComplaintRatioDashboardService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase45-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false")
                .build();
        jdbc = new JdbcTemplate(database);
        createSchema();
        service = new ComplaintRatioDashboardService(jdbc, new BigDecimal("0.003"));
    }

    @Test
    void ranksBreachedRowsBeforeNormalAndCarriesVersionFreshnessAndQuality() {
        channel(11, "移动主通道", "NORMAL");
        channel(12, "联通备用", "NORMAL");
        ratio("2026-08", "CHANNEL", 12, 10_000, 1, "0.000100", false, "COMPLETE");
        ratio("2026-08", "CHANNEL", 11, 1_000, 3, "0.003000", true, "COMPLETE");

        var rows = service.ranking("channel", YearMonth.of(2026, 8), 10, false);

        assertThat(rows).extracting(ComplaintRatioDashboardService.RatioRow::dimensionId).containsExactly(11L, 12L);
        assertThat(rows.get(0).thresholdValue()).isEqualByComparingTo("0.003");
        assertThat(rows.get(0).thresholdConfigVersion()).isEqualTo("default-v1");
        assertThat(rows.get(0).thresholdResult()).isEqualTo("BREACHED");
        assertThat(rows.get(0).sourceRegistry()).isEqualTo("complaint_ratio_stats:message_tasks:complaints");
        assertThat(rows.get(0).freshnessPolicy()).isEqualTo("T_PLUS_1_DAILY");
        assertThat(rows.get(0).interventionAvailable()).isTrue();
    }

    @Test
    void zeroAndUnknownRowsDoNotAllowIntervention() {
        tenant(7, "示例机构", "SIGNED");
        ratio("2026-08", "TENANT", 7, 0, 1, "0.000000", false, "UNKNOWN");

        assertThat(service.ranking("tenant", YearMonth.of(2026, 8), 10, false).get(0).thresholdResult())
                .isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> service.pause("tenant", 7, YearMonth.of(2026, 8),
                new ComplaintRatioDashboardService.InterventionCommand("证据不足", "review-1"), "operator"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("COMPLAINT_RATIO_INTERVENTION_NOT_ALLOWED");
        assertThat(count("alert_records")).isZero();
        assertThat(count("tenant_risk_episodes")).isZero();
    }

    @Test
    void channelPauseTargetsExactDimensionAndIsIdempotentBySourceKey() {
        channel(11, "移动主通道", "NORMAL");
        ratio("2026-08", "CHANNEL", 11, 1_000, 3, "0.003000", true, "COMPLETE");

        var first = service.pause("channel", 11, YearMonth.of(2026, 8),
                new ComplaintRatioDashboardService.InterventionCommand("投诉率超阈值", "review-1"), "operator");
        var second = service.pause("channel", 11, YearMonth.of(2026, 8),
                new ComplaintRatioDashboardService.InterventionCommand("投诉率超阈值", "review-1"), "operator");

        assertThat(first.evidenceId()).isEqualTo(second.evidenceId());
        assertThat(first.sourceKey()).isEqualTo("complaint-ratio:CHANNEL:11:2026-08:default-v1");
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=11", String.class)).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("SELECT trigger_type FROM channel_pause_events WHERE channel_id=11", String.class))
                .isEqualTo("RATIO");
        assertThat(count("channel_pause_events")).isEqualTo(1);
    }

    @Test
    void pauseRejectsBlankReasonBeforeChangingTargetState() {
        channel(11, "移动主通道", "NORMAL");
        ratio("2026-08", "CHANNEL", 11, 1_000, 3, "0.003000", true, "COMPLETE");

        assertThatThrownBy(() -> service.pause("channel", 11, YearMonth.of(2026, 8),
                new ComplaintRatioDashboardService.InterventionCommand("  ", "review-1"), "operator"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("COMPLAINT_RATIO_INTERVENTION_REASON_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=11", String.class)).isEqualTo("NORMAL");
        assertThat(count("channel_pause_events")).isZero();
    }

    @Test
    void pauseRejectsOverlongReasonBeforeChangingTargetState() {
        channel(11, "移动主通道", "NORMAL");
        ratio("2026-08", "CHANNEL", 11, 1_000, 3, "0.003000", true, "COMPLETE");

        assertThatThrownBy(() -> service.pause("channel", 11, YearMonth.of(2026, 8),
                new ComplaintRatioDashboardService.InterventionCommand("超".repeat(256), "review-1"), "operator"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("COMPLAINT_RATIO_INTERVENTION_REASON_TOO_LONG");
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=11", String.class)).isEqualTo("NORMAL");
        assertThat(count("channel_pause_events")).isZero();
    }

    @Test
    void tenantPauseCreatesAlertAndRiskEpisodeWithExactSnapshot() {
        tenant(7, "示例机构", "SIGNED");
        ratio("2026-08", "TENANT", 7, 1_000, 3, "0.003000", true, "COMPLETE");

        var result = service.pause("tenant", 7, YearMonth.of(2026, 8),
                new ComplaintRatioDashboardService.InterventionCommand("投诉率超阈值", "review-7"), "operator");

        assertThat(result.action()).isEqualTo("AUTO_SUSPEND");
        assertThat(result.alertRecordId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=7", String.class)).isEqualTo("FROZEN");
        assertThat(jdbc.queryForObject("SELECT source_module FROM alert_records WHERE id=?", String.class, result.alertRecordId()))
                .isEqualTo("COMPLAINT_RATIO");
        assertThat(jdbc.queryForObject("SELECT source_snapshot FROM tenant_risk_episodes WHERE id=?", String.class, result.evidenceId()))
                .contains("dimension=TENANT:7").contains("send=1000").contains("complaints=3");
    }

    @Test
    void drilldownReturnsOnlyExactMonthAndDimensionCases() {
        complaint(1, 7, 11, "2026-08-02 10:00:00");
        complaint(2, 7, 12, "2026-08-03 10:00:00");
        complaint(3, 7, 11, "2026-09-01 10:00:00");

        var rows = service.drilldown("channel", 11, YearMonth.of(2026, 8));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).id()).isEqualTo(1);
        assertThat(rows.get(0).channelId()).isEqualTo(11);
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE tenants(
                  id BIGINT PRIMARY KEY,
                  short_name VARCHAR(255),
                  lifecycle_status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE channels(
                  id BIGINT PRIMARY KEY,
                  channel_name VARCHAR(255),
                  status VARCHAR(32) NOT NULL,
                  pause_reason VARCHAR(255),
                  paused_by VARCHAR(64),
                  paused_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE complaint_ratio_stats(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  stat_month CHAR(7) NOT NULL,
                  dimension_type VARCHAR(16) NOT NULL,
                  dimension_id BIGINT NOT NULL,
                  send_count BIGINT NOT NULL,
                  complaint_count BIGINT NOT NULL,
                  ratio DECIMAL(12,6) NOT NULL,
                  over_threshold BOOLEAN NOT NULL,
                  threshold_config_version VARCHAR(32),
                  data_quality VARCHAR(32) NOT NULL,
                  source_registry VARCHAR(128) NOT NULL,
                  calculated_at TIMESTAMP NOT NULL,
                  UNIQUE(stat_month, dimension_type, dimension_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE complaints(
                  id BIGINT PRIMARY KEY,
                  source VARCHAR(32) NOT NULL,
                  tenant_id BIGINT,
                  channel_id BIGINT,
                  message_id VARCHAR(64),
                  summary VARCHAR(500),
                  status VARCHAR(32) NOT NULL,
                  attribution_quality VARCHAR(32) NOT NULL,
                  created_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE channel_pause_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  channel_id BIGINT NOT NULL,
                  event_type VARCHAR(32) NOT NULL,
                  trigger_type VARCHAR(32) NOT NULL,
                  actor VARCHAR(64) NOT NULL,
                  reason VARCHAR(255) NOT NULL,
                  source_event_key VARCHAR(128) NOT NULL UNIQUE,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
                  paused_at TIMESTAMP
                )
                """);
    }

    private void channel(long id, String name, String status) {
        jdbc.update("INSERT INTO channels(id, channel_name, status) VALUES (?,?,?)", id, name, status);
    }

    private void tenant(long id, String name, String lifecycle) {
        jdbc.update("INSERT INTO tenants(id, short_name, lifecycle_status) VALUES (?,?,?)", id, name, lifecycle);
    }

    private void ratio(String month, String type, long id, long send, long complaints, String ratio, boolean over, String quality) {
        jdbc.update("""
                INSERT INTO complaint_ratio_stats(stat_month, dimension_type, dimension_id, send_count,
                    complaint_count, ratio, over_threshold, threshold_config_version, data_quality, source_registry, calculated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, month, type, id, send, complaints, new BigDecimal(ratio), over, "default-v1", quality,
                "complaint_ratio_stats:message_tasks:complaints");
    }

    private void complaint(long id, long tenantId, long channelId, String createdAt) {
        jdbc.update("""
                INSERT INTO complaints(id, source, tenant_id, channel_id, message_id, summary, status, attribution_quality, created_at)
                VALUES (?, 'REGULATOR', ?, ?, ?, '监管投诉', 'PENDING', 'COMPLETE', ?)
                """, id, tenantId, channelId, "MSG-" + id, java.sql.Timestamp.valueOf(createdAt));
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }
}
