package com.ycsopen.sms.core.service.statistics;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsAggregationServiceTest {
    private JdbcTemplate jdbc;
    private StatisticsAggregationService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase34-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new StatisticsAggregationService(jdbc);
    }

    @Test
    void rebuildAggregatesResourceChannelAndTenantMetricsFromFinalSources() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime end = start.plusHours(1);
        insertSubmit(100, "S-100", 7, "VERIFY", 55L, 66L, "ACCEPTED", start.plusMinutes(1));
        insertSubmit(101, "S-101", 7, "VERIFY", 55L, 66L, "ACCEPTED", start.plusMinutes(2));
        insertSubmit(102, "S-102", 7, "VERIFY", 55L, 66L, "REJECTED", start.plusMinutes(3));
        insertTask(1, "M-1", 100L, 7, 11L, 55L, 66L, "SENT", "MOBILE", "广东", "深圳",
                new BigDecimal("0.0100"), start.plusMinutes(4), start.plusMinutes(4).plusSeconds(2), 1);
        insertTask(2, "M-2", 101L, 7, 11L, 55L, 66L, "SENT", "MOBILE", "广东", "深圳",
                new BigDecimal("0.0400"), start.plusMinutes(5), start.plusMinutes(5).plusSeconds(1), 1);
        insertReceipt(10, "M-1", 11L, "DELIVERED", start.plusMinutes(6));
        insertReceipt(11, "M-2", 11L, "FAILED", start.plusMinutes(7));
        insertBilling(20, 7, 1, 11L, 25, "CONFIRMED", start.toLocalDate());
        insertBilling(21, 7, 2, 11L, 50, "REVERSED", start.toLocalDate());

        var result = service.rebuild(start, end, "operator");

        assertThat(result.aggregateRows()).isEqualTo(3);
        var channel = only("CHANNEL_DELIVERY", row -> row.channelId().equals(11L));
        assertThat(channel.submitCount()).isEqualTo(2);
        assertThat(channel.acceptedCount()).isEqualTo(2);
        assertThat(channel.rejectedCount()).isZero();
        assertThat(channel.sendCount()).isEqualTo(2);
        assertThat(channel.successCount()).isEqualTo(1);
        assertThat(channel.failureCount()).isEqualTo(1);
        assertThat(channel.feeAmount()).isEqualByComparingTo("0.0650");
        assertThat(channel.avgResponseMs()).isEqualTo(1500);
        assertThat(channel.bucketDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(channel.drilldownKey()).contains("CHANNEL_DELIVERY", "11", "MOBILE", "VERIFY");

        var tenant = only("TENANT_BEHAVIOR", row -> row.tenantId() == 7L);
        assertThat(tenant.submitCount()).isEqualTo(3);
        assertThat(tenant.acceptedCount()).isEqualTo(2);
        assertThat(tenant.rejectedCount()).isEqualTo(1);
        assertThat(tenant.successCount()).isEqualTo(1);
        assertThat(tenant.failureCount()).isEqualTo(1);

        var resource = only("RESOURCE_USAGE", row -> row.signatureId().equals(55L) && row.templateId().equals(66L));
        assertThat(resource.submitCount()).isEqualTo(3);
        assertThat(resource.acceptedCount()).isEqualTo(2);
        assertThat(resource.rejectedCount()).isEqualTo(1);
        assertThat(resource.successCount()).isEqualTo(1);
        assertThat(resource.failureCount()).isEqualTo(1);
        assertThat(resource.correctionIdentity()).hasSize(64);

        assertThat(service.metrics()).extracting(StatisticsAggregationService.MetricRow::metricCode)
                .containsExactly("CHANNEL_DELIVERY", "RESOURCE_USAGE", "TENANT_BEHAVIOR");
    }

    @Test
    void rebuildIsIdempotentAndLateCorrectionReplacesFinalState() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 2, 9, 0);
        LocalDateTime end = start.plusHours(1);
        insertSubmit(200, "S-200", 8, "MARKETING", 77L, 88L, "ACCEPTED", start.plusMinutes(1));
        insertTask(3, "M-3", 200L, 8, 12L, 77L, 88L, "SENT", "UNICOM", "浙江", "杭州",
                new BigDecimal("0.0300"), start.plusMinutes(2), start.plusMinutes(2).plusSeconds(1), 1);
        insertReceipt(30, "M-3", 12L, "DELIVERED", start.plusMinutes(3));
        service.rebuild(start, end, "operator");
        var firstIdentity = only("CHANNEL_DELIVERY", row -> row.channelId().equals(12L)).correctionIdentity();

        jdbc.update("UPDATE message_tasks SET version=2 WHERE id=3");
        insertReceipt(31, "M-3", 12L, "FAILED", start.plusMinutes(4));
        service.rebuild(start, end, "operator");
        service.rebuild(start, end, "operator");

        assertThat(count("SELECT COUNT(*) FROM statistics_aggregates WHERE bucket_start=?", start)).isEqualTo(3);
        var channel = only("CHANNEL_DELIVERY", row -> row.channelId().equals(12L));
        assertThat(channel.successCount()).isZero();
        assertThat(channel.failureCount()).isEqualTo(1);
        assertThat(channel.qualityState()).isEqualTo("CORRECTED");
        assertThat(channel.correctionIdentity()).isNotEqualTo(firstIdentity);
    }

    @Test
    void correctionEventsAreValidatedAndIdempotent() {
        var first = service.recordCorrection(new StatisticsAggregationService.CorrectionCommand(
                "delivery_reports", "31", "channel_delivery", "DELIVERED", "FAILED"));
        var second = service.recordCorrection(new StatisticsAggregationService.CorrectionCommand(
                "delivery_reports", "31", "CHANNEL_DELIVERY", "DELIVERED", "FAILED"));

        assertThat(first.correctionIdentity()).isEqualTo(second.correctionIdentity());
        assertThat(count("SELECT COUNT(*) FROM statistics_correction_events")).isEqualTo(1);
        assertThatThrownBy(() -> service.recordCorrection(new StatisticsAggregationService.CorrectionCommand(
                "delivery_reports", "32", "UNKNOWN", null, "FAILED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("统计指标不支持");
    }

    private StatisticsAggregationService.AggregateRow only(
            String metricCode, Predicate<StatisticsAggregationService.AggregateRow> predicate) {
        var rows = service.aggregates(new StatisticsAggregationService.AggregateFilter(
                metricCode, null, null, null, null, null, null)).stream()
                .filter(predicate)
                .toList();
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void insertSubmit(long id, String submitId, long tenantId, String productType, Long signatureId,
                              Long templateId, String status, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO message_submits(id, submit_id, tenant_id, source_protocol, product_type,
                    signature_id, template_id, status, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, id, submitId, tenantId, "HTTP", productType, signatureId, templateId, status, createdAt);
    }

    private void insertTask(long id, String messageId, Long submitId, long tenantId, Long channelId, Long signatureId,
                            Long templateId, String sendStatus, String carrier, String province, String city,
                            BigDecimal cost, LocalDateTime sendTime, LocalDateTime deliverTime, long version) {
        jdbc.update("""
                INSERT INTO message_tasks(id, message_id, submit_id, tenant_id, template_id, signature_id,
                    mobile_encrypted, mobile_hash, content, send_status, channel_id, operator, province, city,
                    cost, send_time, deliver_time, created_at, updated_at, version)
                VALUES (?,?,?,?,?,?,X'0102',?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, messageId, submitId, tenantId, templateId, signatureId, "hash-" + messageId, "content",
                sendStatus, channelId, carrier, province, city, cost, sendTime, deliverTime, sendTime, deliverTime,
                version);
    }

    private void insertReceipt(long id, String messageId, Long channelId, String status, LocalDateTime reportTime) {
        jdbc.update("""
                INSERT INTO delivery_reports(id, message_id, channel_id, report_status, report_time)
                VALUES (?,?,?,?,?)
                """, id, messageId, channelId, status, reportTime);
    }

    private void insertBilling(long id, long tenantId, long taskRefId, Long channelId, long amount, String status,
                               LocalDate billingDate) {
        jdbc.update("""
                INSERT INTO billing_records(id, tenant_id, task_ref_id, channel_id, unit_price, quantity, amount,
                    billing_status, billing_date, created_at)
                VALUES (?,?,?,?,0.0250,1,?,?,?,CURRENT_TIMESTAMP)
                """, id, tenantId, taskRefId, channelId, amount, status, billingDate);
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE statistics_metric_registry(
                  metric_code VARCHAR(64) PRIMARY KEY,
                  metric_name VARCHAR(128) NOT NULL,
                  source_tables VARCHAR(255) NOT NULL,
                  formula VARCHAR(255) NOT NULL,
                  freshness_rule VARCHAR(128) NOT NULL,
                  permission_scope VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
                  formula_version VARCHAR(32) NOT NULL DEFAULT 'v1',
                  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO statistics_metric_registry(metric_code, metric_name, source_tables, formula,
                    freshness_rule, permission_scope, formula_version)
                VALUES ('CHANNEL_DELIVERY','通道发送成功成本延迟指标','message_tasks,delivery_reports,billing_records,message_send_outbox',
                        'send/success/failure/cost/latency grouped by channel and geography','freshness','PLATFORM','v1'),
                       ('RESOURCE_USAGE','签名模板使用与拒绝指标','message_submits,message_tasks,signatures,templates',
                        'accepted/rejected/final message states grouped by signature/template','freshness','PLATFORM','v1'),
                       ('TENANT_BEHAVIOR','租户发送消费活跃指标','message_submits,message_tasks,billing_records',
                        'accepted/rejected/send/success/failure/consumption grouped by tenant','freshness','TENANT','v1')
                """);
        jdbc.execute("""
                CREATE TABLE statistics_aggregates(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  metric_code VARCHAR(64) NOT NULL,
                  bucket_grain VARCHAR(8) NOT NULL DEFAULT 'HOUR',
                  bucket_start TIMESTAMP NOT NULL,
                  bucket_date DATE NOT NULL,
                  tenant_id BIGINT,
                  channel_id BIGINT,
                  carrier VARCHAR(32),
                  message_type VARCHAR(32),
                  province VARCHAR(64),
                  city VARCHAR(64),
                  signature_id BIGINT,
                  template_id BIGINT,
                  submit_count INT NOT NULL DEFAULT 0,
                  accepted_count INT NOT NULL DEFAULT 0,
                  rejected_count INT NOT NULL DEFAULT 0,
                  send_count INT NOT NULL DEFAULT 0,
                  success_count INT NOT NULL DEFAULT 0,
                  failure_count INT NOT NULL DEFAULT 0,
                  fee_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                  avg_response_ms BIGINT NOT NULL DEFAULT 0,
                  source_version BIGINT NOT NULL DEFAULT 0,
                  correction_identity VARCHAR(128) NOT NULL,
                  drilldown_key VARCHAR(255) NOT NULL,
                  formula_version VARCHAR(32) NOT NULL DEFAULT 'v1',
                  freshness_at TIMESTAMP NOT NULL,
                  quality_state VARCHAR(16) NOT NULL DEFAULT 'FRESH'
                )
                """);
        jdbc.execute("""
                CREATE TABLE statistics_correction_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  source_table VARCHAR(64) NOT NULL,
                  source_id VARCHAR(128) NOT NULL,
                  metric_code VARCHAR(64) NOT NULL,
                  previous_state VARCHAR(64),
                  current_state VARCHAR(64) NOT NULL,
                  correction_identity VARCHAR(128) NOT NULL UNIQUE,
                  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_submits(
                  id BIGINT PRIMARY KEY,
                  submit_id VARCHAR(64) NOT NULL,
                  tenant_id BIGINT NOT NULL,
                  source_protocol VARCHAR(16) NOT NULL,
                  product_type VARCHAR(32) NOT NULL,
                  signature_id BIGINT,
                  template_id BIGINT,
                  status VARCHAR(16) NOT NULL,
                  created_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT PRIMARY KEY,
                  message_id VARCHAR(64) NOT NULL,
                  submit_id BIGINT,
                  tenant_id BIGINT NOT NULL,
                  template_id BIGINT,
                  signature_id BIGINT,
                  mobile_encrypted VARBINARY(255) NOT NULL,
                  mobile_hash CHAR(64) NOT NULL,
                  content VARCHAR(600) NOT NULL,
                  send_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                  channel_id BIGINT,
                  operator VARCHAR(32),
                  province VARCHAR(32),
                  city VARCHAR(32),
                  cost DECIMAL(10,4) NOT NULL DEFAULT 0,
                  send_time TIMESTAMP,
                  deliver_time TIMESTAMP,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL,
                  version INT NOT NULL DEFAULT 1
                )
                """);
        jdbc.execute("""
                CREATE TABLE delivery_reports(
                  id BIGINT PRIMARY KEY,
                  message_id VARCHAR(64) NOT NULL,
                  channel_id BIGINT,
                  report_status VARCHAR(16) NOT NULL,
                  report_time TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE billing_records(
                  id BIGINT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  task_ref_id BIGINT NOT NULL,
                  channel_id BIGINT,
                  unit_price DECIMAL(10,4) NOT NULL,
                  quantity INT NOT NULL DEFAULT 1,
                  amount BIGINT NOT NULL,
                  billing_status VARCHAR(16) NOT NULL,
                  billing_date DATE NOT NULL,
                  created_at TIMESTAMP NOT NULL
                )
                """);
    }
}
