package com.ycsopen.sms.core.service.unsubscribe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryClient;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsubscribeComplianceServiceTest {
    private JdbcTemplate jdbc;
    private RecordingBlacklistWriter blacklistWriter;
    private WebhookDeliveryTransportService transport;
    private UnsubscribeComplianceService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase33-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        var client = new RecordingClient();
        transport = new WebhookDeliveryTransportService(jdbc, client, new ObjectMapper());
        blacklistWriter = new RecordingBlacklistWriter();
        service = new UnsubscribeComplianceService(jdbc, blacklistWriter, transport);
    }

    @Test
    void keywordDefaultsAndNormalizationMatchWithoutSubstringBypass() {
        assertThat(service.matchIntent(7, "td")).isEqualTo(new UnsubscribeComplianceService.MatchResult(true, "TD"));
        assertThat(service.matchIntent(7, "ＴＤ")).isEqualTo(new UnsubscribeComplianceService.MatchResult(true, "TD"));
        assertThat(service.matchIntent(7, "ATD").matched()).isFalse();
        assertThat(service.matchIntent(7, "TDD").matched()).isFalse();
        assertThat(service.matchIntent(7, "退订成功").matched()).isFalse();

        var tenantKeyword = service.saveKeyword(7L,
                new UnsubscribeComplianceService.KeywordCommand("STOP7", "TENANT", null, "ACTIVE"),
                "tenant-admin");

        assertThat(tenantKeyword.scope()).isEqualTo("TENANT");
        assertThat(service.matchIntent(7, "stop7").matched()).isTrue();
        assertThat(service.matchIntent(8, "stop7").matched()).isFalse();
        assertThatThrownBy(() -> service.saveKeyword(7L,
                new UnsubscribeComplianceService.KeywordCommand("STOP", "GLOBAL", null, "ACTIVE"),
                "tenant-admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户不能维护全局关键词");
    }

    @Test
    void matchedUplinkCreatesOneTenantBlacklistAndLinkedEvidenceIdempotently() {
        transport.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                null, null, "https://example.com/unsubscribe", 5, 1));

        var first = service.handleMatchedUplink(new UnsubscribeComplianceService.HandleUplinkCommand(
                7, 9001, "13800138000", "TD", "MSG-1", 88L, "STANDARD"), "uplink");
        var second = service.handleMatchedUplink(new UnsubscribeComplianceService.HandleUplinkCommand(
                7, 9001, "13800138000", "TD", "MSG-1", 88L, "STANDARD"), "uplink");

        assertThat(first.matched()).isTrue();
        assertThat(first.state()).isEqualTo("TENANT_BLACKLISTED");
        assertThat(second.state()).isEqualTo("IDEMPOTENT");
        assertThat(blacklistWriter.calls).hasSize(1);
        assertThat(blacklistWriter.calls.get(0).tenantId()).isEqualTo(7L);
        assertThat(blacklistWriter.calls.get(0).mobile()).isEqualTo("13800138000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unsubscribe_records WHERE uplink_record_id=9001",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT masked_mobile FROM unsubscribe_records WHERE uplink_record_id=9001",
                String.class)).isEqualTo("138****8000");
        assertThat(jdbc.queryForObject("SELECT notification_state FROM unsubscribe_records WHERE uplink_record_id=9001",
                String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT event_type FROM webhook_delivery_events", String.class))
                .isEqualTo("UNSUBSCRIBE");

        byte[] protectedPhone = jdbc.queryForObject("SELECT mobile_encrypted FROM unsubscribe_records WHERE uplink_record_id=9001",
                byte[].class);
        assertThat(new String(protectedPhone, StandardCharsets.UTF_8)).doesNotContain("13800138000");
    }

    @Test
    void unmatchedUplinkDoesNotWriteBlacklistOrEvidence() {
        var result = service.handleMatchedUplink(new UnsubscribeComplianceService.HandleUplinkCommand(
                7, 9002, "13800138000", "回复帮助", "MSG-2", null, null), "uplink");

        assertThat(result.matched()).isFalse();
        assertThat(blacklistWriter.calls).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM unsubscribe_records", Integer.class)).isZero();
    }

    @Test
    void searchExportStatisticsAndAlertsUseEvidenceAndFinalSentDenominator() {
        insertEvidence(7, 88L, "STANDARD", "TD", "138****8000");
        insertEvidence(7, 88L, "STANDARD", "QUIT", "139****1111");
        jdbc.update("""
                INSERT INTO message_tasks(id, tenant_id, signature_id, send_status, created_at)
                VALUES (1,7,88,'SENT',CURRENT_TIMESTAMP),
                       (2,7,88,'DELIVERED',CURRENT_TIMESTAMP),
                       (3,7,88,'FAILED',CURRENT_TIMESTAMP),
                       (4,8,88,'SENT',CURRENT_TIMESTAMP)
                """);

        var tenantRows = service.tenantSearch(7, new UnsubscribeComplianceService.SearchFilter(
                null, null, "TD", "TENANT_BLACKLISTED", 88L, "STANDARD", null, null, null));
        var export = service.requestTenantExport(7, new UnsubscribeComplianceService.SearchFilter(
                null, null, null, null, null, null, null, null, null), "tenant-admin");
        var stats = service.statistics(new UnsubscribeComplianceService.StatisticsFilter(
                7L, 88L, "STANDARD", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)));
        var alerts = service.evaluateAlerts(new UnsubscribeComplianceService.StatisticsFilter(
                7L, 88L, "STANDARD", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)), 0.5);

        assertThat(tenantRows).hasSize(1);
        assertThat(export.status()).isEqualTo("PENDING");
        assertThat(export.recordCount()).isEqualTo(2);
        assertThat(stats).hasSize(1);
        assertThat(stats.get(0).unsubscribeCount()).isEqualTo(2);
        assertThat(stats.get(0).finalSentCount()).isEqualTo(2);
        assertThat(stats.get(0).rate()).isEqualTo(1.0);
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).sourceEvent()).isEqualTo("UNSUBSCRIBE_RATE_ABNORMAL");
        assertThat(alerts.get(0).formula()).isEqualTo("unsubscribe_count/final_sent_count");
    }

    private void insertEvidence(long tenantId, Long signatureId, String productCode, String keyword, String masked) {
        jdbc.update("""
                INSERT INTO unsubscribe_records(mobile_encrypted, mobile_hash, trigger_keyword, tenant_id, signature_id,
                    result, confirmed_reply_sent, notified_tenant, uplink_record_id, masked_mobile, method,
                    product_code, handling_state, notification_state, confirmation_state)
                VALUES (X'010203', ?, ?, ?, ?, 'TENANT_BLACKLISTED', 0, 0, NULL, ?, 'UPLINK',
                    ?, 'TENANT_BLACKLISTED', 'NOT_CONFIGURED', 'DISABLED')
                """, "hash-" + masked, keyword, tenantId, signatureId, masked, productCode);
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE unsubscribe_keywords(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  keyword VARCHAR(32) NOT NULL,
                  keyword_normalized VARCHAR(64) NOT NULL,
                  scope VARCHAR(16) NOT NULL DEFAULT 'GLOBAL',
                  tenant_id BIGINT,
                  scope_key VARCHAR(80) NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                  created_by VARCHAR(64),
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(scope_key, keyword_normalized)
                )
                """);
        jdbc.update("""
                INSERT INTO unsubscribe_keywords(keyword, keyword_normalized, scope, scope_key, status, created_by)
                VALUES ('TD','TD','GLOBAL','GLOBAL','ACTIVE','system'),
                       ('退订','退订','GLOBAL','GLOBAL','ACTIVE','system'),
                       ('QUIT','QUIT','GLOBAL','GLOBAL','ACTIVE','system'),
                       ('UNSUBSCRIBE','UNSUBSCRIBE','GLOBAL','GLOBAL','ACTIVE','system')
                """);
        jdbc.execute("""
                CREATE TABLE unsubscribe_records(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  mobile_encrypted VARBINARY(255) NOT NULL,
                  mobile_hash CHAR(64) NOT NULL,
                  unsubscribed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  trigger_keyword VARCHAR(32),
                  tenant_id BIGINT NOT NULL,
                  signature_id BIGINT,
                  result VARCHAR(32) NOT NULL DEFAULT 'TENANT_BLACKLISTED',
                  confirmed_reply_sent BOOLEAN NOT NULL DEFAULT FALSE,
                  notified_tenant BOOLEAN NOT NULL DEFAULT FALSE,
                  uplink_record_id BIGINT,
                  masked_mobile VARCHAR(32),
                  method VARCHAR(24) NOT NULL DEFAULT 'UPLINK',
                  product_code VARCHAR(64),
                  handling_state VARCHAR(32) NOT NULL DEFAULT 'TENANT_BLACKLISTED',
                  notification_state VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIGURED',
                  notification_event_id BIGINT,
                  confirmation_state VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
                  reply_event_id BIGINT,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(uplink_record_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  signature_id BIGINT,
                  send_status VARCHAR(24) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE export_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  export_type VARCHAR(64) NOT NULL,
                  created_by VARCHAR(64),
                  file_format VARCHAR(16) NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
                  progress_pct INT NOT NULL DEFAULT 0,
                  record_count BIGINT NOT NULL DEFAULT 0,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE unsubscribe_alert_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  signature_id BIGINT,
                  product_code VARCHAR(64),
                  period_start TIMESTAMP NOT NULL,
                  period_end TIMESTAMP NOT NULL,
                  unsubscribe_count INT NOT NULL,
                  final_sent_count INT NOT NULL,
                  rate DECIMAL(12,6) NOT NULL,
                  threshold_rate DECIMAL(12,6) NOT NULL,
                  formula VARCHAR(128) NOT NULL,
                  freshness_at TIMESTAMP NOT NULL,
                  source_event VARCHAR(64) NOT NULL DEFAULT 'UNSUBSCRIBE_RATE_ABNORMAL',
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        createWebhookSchema();
    }

    private void createWebhookSchema() {
        jdbc.execute("""
                CREATE TABLE tenant_callback_configs(
                  tenant_id BIGINT PRIMARY KEY,
                  delivery_callback_url VARCHAR(255),
                  uplink_callback_url VARCHAR(255),
                  unsubscribe_callback_url VARCHAR(255),
                  retry_max_count INT NOT NULL DEFAULT 5,
                  retry_backoff_seconds INT NOT NULL DEFAULT 1,
                  config_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                  latest_failure_reason VARCHAR(255),
                  paused_at TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  version INT NOT NULL DEFAULT 1,
                  callback_signing_secret VARCHAR(128)
                )
                """);
        jdbc.execute("""
                CREATE TABLE webhook_delivery_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  event_type VARCHAR(32) NOT NULL,
                  source_id VARCHAR(128) NOT NULL,
                  logical_id VARCHAR(160) NOT NULL,
                  destination_url VARCHAR(512) NOT NULL,
                  envelope_version VARCHAR(16) NOT NULL DEFAULT 'v1',
                  payload_json TEXT NOT NULL,
                  signature VARCHAR(128) NOT NULL,
                  state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                  attempt_count INT NOT NULL DEFAULT 0,
                  max_attempts INT NOT NULL DEFAULT 5,
                  next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  paused_at TIMESTAMP,
                  terminal_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(tenant_id, event_type, logical_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE webhook_delivery_attempts(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  event_id BIGINT NOT NULL,
                  attempt_no INT NOT NULL,
                  destination_url VARCHAR(512) NOT NULL,
                  http_status INT,
                  result_code VARCHAR(64) NOT NULL,
                  result_message VARCHAR(255),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(event_id, attempt_no)
                )
                """);
    }

    private static final class RecordingBlacklistWriter implements UnsubscribeComplianceService.TenantBlacklistWriter {
        private final List<Call> calls = new ArrayList<>();

        @Override
        public long create(long tenantId, String mobile, String reason) {
            calls.add(new Call(tenantId, mobile, reason));
            return 1000 + calls.size();
        }
    }

    private static final class RecordingClient implements WebhookDeliveryClient {
        @Override
        public DeliveryResponse post(String destinationUrl, String payloadJson, Map<String, String> headers) {
            return new DeliveryResponse(204, "HTTP_204", null);
        }
    }

    private record Call(long tenantId, String mobile, String reason) { }
}
