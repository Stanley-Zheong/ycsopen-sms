package com.ycsopen.sms.core.service.uplink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.cmpp.CmppClientSession;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryClient;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UplinkNormalizationServiceTest {
    private JdbcTemplate jdbc;
    private RecordingClient client;
    private WebhookDeliveryTransportService transport;
    private UplinkNormalizationService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase32-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createWebhookTables();
        createUplinkTables();
        client = new RecordingClient();
        transport = new WebhookDeliveryTransportService(jdbc, client, new ObjectMapper());
        service = new UplinkNormalizationService(jdbc, transport);
    }

    @Test
    void normalizesHttpAndCmppUplinksWithTenantCorrelationAndIdempotentPush() {
        transport.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                null, "https://example.com/uplink", null, 5, 1));
        var first = service.normalizeHttpUplink(7, "HTTP-API", "HTTP-UP-1", "MSG-HTTP-UP-1",
                "13800138000", "回复帮助", "帮助", "CMCC", "北京", "北京", "10690000",
                1L, 2L, "STANDARD", true, LocalDateTime.now());
        var duplicate = service.normalizeHttpUplink(7, "HTTP-API", "HTTP-UP-1", "MSG-HTTP-UP-1",
                "13800138000", "回复帮助", "帮助", "CMCC", "北京", "北京", "10690000",
                1L, 2L, "STANDARD", true, LocalDateTime.now());
        var cmpp = service.normalize(new UplinkNormalizationService.NormalizeCommand(
                7, "cmpp", "CMPP-GW-A", "CMPP-UP-1", "MSG-CMPP-1", "13900001111",
                "短信回复 OK", "OK", "CMCC", "浙江", "杭州", "10690000", 2L, 3L,
                "STANDARD", true, LocalDateTime.now()));

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(first.sourceProtocol()).isEqualTo("HTTP");
        assertThat(cmpp.sourceProtocol()).isEqualTo("CMPP");
        assertThat(cmpp.signatureId()).isEqualTo(3L);
        assertThat(cmpp.productCode()).isEqualTo("STANDARD");
        assertThat(cmpp.phoneMasked()).isEqualTo("139****1111");
        assertThat(cmpp.pushEventId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uplink_records", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_delivery_events WHERE event_type='UPLINK'",
                Integer.class)).isEqualTo(2);

        service.normalize(command(7, "HTTP", "HTTP-API-B", "HTTP-UP-1", false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uplink_records WHERE source_event_id='HTTP-UP-1'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void tenantSearchNeverLeaksOtherTenantsAndAdminCanFilterByProtectedPhone() {
        service.normalize(command(7, "HTTP", "HTTP-API", "HTTP-UP-1", false));
        service.normalize(new UplinkNormalizationService.NormalizeCommand(
                8, "HTTP", "HTTP-API", "HTTP-UP-2", "MSG-8", "13900002222",
                "跨租户数据", "跨租户", "CUCC", "上海", "上海", "10690000", null, null,
                "STANDARD", false, LocalDateTime.now()));

        var tenantRows = service.tenantSearch(7, new UplinkNormalizationService.SearchFilter(
                null, null, "回复", null, null, null, null));
        var adminRows = service.adminSearch(new UplinkNormalizationService.SearchFilter(
                null, "13900002222", null, null, null, null, null));

        assertThat(tenantRows).extracting(UplinkNormalizationService.UplinkRecord::tenantId).containsOnly(7L);
        assertThat(adminRows).hasSize(1);
        assertThat(adminRows.get(0).tenantId()).isEqualTo(8L);
        assertThat(adminRows.get(0).phoneMasked()).isEqualTo("139****2222");
    }

    @Test
    void cmppNormalizedEventCanEnterUplinkNormalizationBoundary() {
        transport.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                null, "https://example.com/uplink", null, 5, 1));
        var event = new CmppClientSession.NormalizedEvent("UPLINK", null, "13900001111",
                "回复 OK", "UPLINK", false, false, false);

        var normalized = service.normalizeCmppUplink(7, "CMPP-GW-A", "SEQ-7001", "MSG-1",
                "CMCC", "浙江", "杭州", 2L, 3L, "STANDARD", event);

        assertThat(normalized.sourceProtocol()).isEqualTo("CMPP");
        assertThat(normalized.sourceConnector()).isEqualTo("CMPP-GW-A");
        assertThat(normalized.phoneMasked()).isEqualTo("139****1111");
        assertThat(normalized.pushEventId()).isNotNull();
    }

    @Test
    void autoReplyConfigRequiresAuditReasonAndLoopGuard() {
        assertThatThrownBy(() -> service.saveAutoReplyConfig(7,
                new UplinkNormalizationService.AutoReplyCommand(true, "OK", null, "收到", 0, "配置"),
                "tenant-admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("防循环");
        assertThatThrownBy(() -> service.saveAutoReplyConfig(7,
                new UplinkNormalizationService.AutoReplyCommand(true, "OK", null, "收到", 30, " "),
                "tenant-admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");

        var saved = service.saveAutoReplyConfig(7,
                new UplinkNormalizationService.AutoReplyCommand(true, "OK", "TPL-1", "收到", 30, "租户申请启用"),
                "tenant-admin");

        assertThat(saved.enabled()).isTrue();
        assertThat(saved.loopGuardMinutes()).isEqualTo(30);
        assertThat(saved.updatedBy()).isEqualTo("tenant-admin");

        var first = service.planAutoReply(7, "13800138000", "回复 OK", 101L, LocalDateTime.now());
        var second = service.planAutoReply(7, "13800138000", "回复 OK", 102L, LocalDateTime.now());

        assertThat(first.decision()).isEqualTo("SHOULD_REPLY");
        assertThat(second.decision()).isEqualTo("SUPPRESSED_LOOP_GUARD");
    }

    @Test
    void pushMonitorAndDestinationActionsReuseWebhookDeliveryTransport() {
        transport.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                null, "https://example.com/uplink", null, 5, 1));
        client.next = new WebhookDeliveryClient.DeliveryResponse(500, "HTTP_500", "downstream down");
        var normalized = service.normalize(command(7, "HTTP", "HTTP-API", "HTTP-UP-1", true));
        for (int i = 0; i < 5; i++) {
            jdbc.update("UPDATE webhook_delivery_events SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",
                    normalized.pushEventId());
            transport.deliverNext();
        }

        var failures = service.pushMonitor(new UplinkNormalizationService.PushMonitorFilter(7L, "PUSH_FAILED", "uplink"));
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).attemptRows()).isEqualTo(5);

        var paused = service.pausePushEvent(normalized.pushEventId(), "operator", "目的地持续失败");
        var resumed = service.resumePushEvent(normalized.pushEventId(), "operator", "目的地恢复");

        assertThat(paused.state()).isEqualTo("PAUSED");
        assertThat(resumed.state()).isEqualTo("RETRY");
        assertThat(service.detail(normalized.id(), null).pushState()).isEqualTo("RETRY");
    }

    @Test
    void pushValidationFailureDoesNotRollbackNormalizedUplink() {
        jdbc.update("""
                INSERT INTO tenant_callback_configs(tenant_id, uplink_callback_url, callback_signing_secret)
                VALUES (?,?,?)
                """, 7L, "http://example.com/uplink", "secret");

        var normalized = service.normalize(command(7, "HTTP", "HTTP-API", "HTTP-UP-1", true));

        assertThat(normalized.id()).isPositive();
        assertThat(service.detail(normalized.id(), null).pushState()).isEqualTo("PUSH_FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM uplink_records", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_delivery_events", Integer.class)).isZero();
    }

    private UplinkNormalizationService.NormalizeCommand command(long tenantId, String protocol, String connector,
                                                                String sourceEventId, boolean pushRequested) {
        return new UplinkNormalizationService.NormalizeCommand(
                tenantId, protocol, connector, sourceEventId, "MSG-" + sourceEventId, "13800138000",
                "回复帮助", "帮助", "CMCC", "北京", "北京", "10690000", 1L, 2L,
                "STANDARD", pushRequested, LocalDateTime.now());
    }

    private void createWebhookTables() {
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

    private void createUplinkTables() {
        jdbc.execute("""
                CREATE TABLE uplink_records (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  source_protocol VARCHAR(16) NOT NULL,
                  source_connector VARCHAR(64) NOT NULL,
                  source_event_id VARCHAR(128) NOT NULL,
                  message_id VARCHAR(128),
                  phone_masked VARCHAR(32) NOT NULL,
                  phone_hash VARCHAR(128) NOT NULL,
                  content VARCHAR(500) NOT NULL,
                  content_keyword VARCHAR(64),
                  state VARCHAR(32) NOT NULL DEFAULT 'NORMALIZED',
                  carrier VARCHAR(32),
                  province VARCHAR(64),
                  city VARCHAR(64),
                  destination VARCHAR(255),
                  channel_id BIGINT,
                  signature_id BIGINT,
                  product_code VARCHAR(64),
                  push_state VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED',
                  push_event_id BIGINT,
                  receive_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(tenant_id, source_protocol, source_connector, source_event_id),
                  UNIQUE(push_event_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE tenant_uplink_auto_reply_configs (
                  tenant_id BIGINT PRIMARY KEY,
                  enabled BOOLEAN NOT NULL DEFAULT FALSE,
                  keyword VARCHAR(64),
                  template_id VARCHAR(64),
                  response_content VARCHAR(255),
                  loop_guard_minutes INT NOT NULL DEFAULT 30,
                  audit_reason VARCHAR(255),
                  updated_by VARCHAR(64) NOT NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE tenant_uplink_auto_reply_attempts (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  phone_hash VARCHAR(128) NOT NULL,
                  uplink_record_id BIGINT,
                  keyword VARCHAR(64) NOT NULL,
                  response_content VARCHAR(255),
                  template_id VARCHAR(64),
                  decision VARCHAR(32) NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private static final class RecordingClient implements WebhookDeliveryClient {
        private final ArrayList<String> destinations = new ArrayList<>();
        private DeliveryResponse next = new DeliveryResponse(204, "HTTP_204", null);

        @Override
        public DeliveryResponse post(String destinationUrl, String payloadJson, Map<String, String> headers) {
            destinations.add(destinationUrl);
            return next;
        }
    }
}
