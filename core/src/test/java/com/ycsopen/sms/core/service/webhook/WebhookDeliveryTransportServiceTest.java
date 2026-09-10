package com.ycsopen.sms.core.service.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookDeliveryTransportServiceTest {
    private JdbcTemplate jdbc;
    private RecordingClient client;
    private WebhookDeliveryTransportService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase28-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE tenant_callback_configs(
                  tenant_id BIGINT PRIMARY KEY,
                  delivery_callback_url VARCHAR(255),
                  uplink_callback_url VARCHAR(255),
                  unsubscribe_callback_url VARCHAR(255),
                  retry_max_count INT NOT NULL DEFAULT 5,
                  retry_backoff_seconds INT NOT NULL DEFAULT 30,
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
        client = new RecordingClient();
        service = new WebhookDeliveryTransportService(jdbc, client, new ObjectMapper());
    }

    @Test
    void savesThreeDistinctDestinationsAndRejectsSsrfTargets() {
        var config = service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://example.com/status",
                "https://example.com/uplink",
                "https://example.com/unsubscribe",
                5,
                15));

        assertThat(config.statusCallbackUrl()).endsWith("/status");
        assertThat(config.uplinkCallbackUrl()).endsWith("/uplink");
        assertThat(config.unsubscribeCallbackUrl()).endsWith("/unsubscribe");
        assertThat(config.retryMaxCount()).isEqualTo(5);
        assertThat(config.version()).isEqualTo(1);

        assertThatThrownBy(() -> service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://127.0.0.1/status", null, null, 5, 15)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内网");
        assertThatThrownBy(() -> service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://169.254.169.254/latest/meta-data", null, null, 5, 15)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内网");
        assertThatThrownBy(() -> service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "http://example.com/status", null, null, 5, 15)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void enqueuesSignedStatusEventAndDeliversItIdempotently() {
        service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://example.com/status", null, null, 5, 10));
        client.next = new WebhookDeliveryClient.DeliveryResponse(204, "HTTP_204", null);

        var first = service.enqueueStatusEvent(7, "MSG_1", "DELIVERED", null);
        var second = service.enqueueStatusEvent(7, "MSG_1", "DELIVERED", null);
        var delivered = service.deliverNext();

        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(delivered).isPresent();
        assertThat(delivered.get().state()).isEqualTo("DELIVERED");
        assertThat(client.requests).hasSize(1);
        assertThat(client.requests.get(0).headers()).containsKeys(
                "X-YCS-Webhook-Version",
                "X-YCS-Webhook-Idempotency-Key",
                "X-YCS-Webhook-Signature");
        assertThat(client.requests.get(0).headers().get("X-YCS-Webhook-Signature")).startsWith("sha256=");
        assertThat(jdbc.queryForObject("SELECT callback_signing_secret FROM tenant_callback_configs WHERE tenant_id=?",
                String.class, 7L)).hasSizeGreaterThan(32);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_delivery_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM webhook_delivery_attempts", Integer.class)).isEqualTo(1);
    }

    @Test
    void statusPayloadIsSerializedAsValidJsonForControlCharacters() throws Exception {
        service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://example.com/status", null, null, 5, 10));
        service.enqueueStatusEvent(7, "MSG_\n1", "DELIVERED", null);

        String payload = jdbc.queryForObject("SELECT payload_json FROM webhook_delivery_events",
                String.class);

        assertThat(new ObjectMapper().readTree(payload).path("messageId").asText()).isEqualTo("MSG_\n1");
    }

    @Test
    void uplinkEventUsesConfiguredTenantDestinationAndValidatesRequiredFields() {
        service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                null, "https://example.com/uplink", null, 5, 10));

        var result = service.enqueueUplinkEvent(7, "UP-1", null, "138****8000", "回复帮助");

        assertThat(result.destinationUrl()).isEqualTo("https://example.com/uplink");
        assertThat(result.logicalId()).isEqualTo("UPLINK:UP-1");
        assertThatThrownBy(() -> service.enqueueUplinkEvent(7, " ", "MSG", "138****8000", "回复帮助"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字段不完整");
        assertThatThrownBy(() -> service.enqueueUplinkEvent(8, "UP-2", "MSG", "138****8000", "回复帮助"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    void failuresRetryToConfiguredTerminalAttemptAndExposePushFailedState() {
        service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://example.com/status", null, null, 5, 1));
        client.next = new WebhookDeliveryClient.DeliveryResponse(500, "HTTP_500", "downstream failed");
        long eventId = service.enqueueStatusEvent(7, "MSG_FAIL", "FAILED", null).eventId();

        for (int i = 0; i < 5; i++) {
            jdbc.update("UPDATE webhook_delivery_events SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=?", eventId);
            service.deliverNext();
        }

        assertThat(jdbc.queryForObject("SELECT state FROM webhook_delivery_events WHERE id=?", String.class, eventId))
                .isEqualTo("PUSH_FAILED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM webhook_delivery_events WHERE id=?", Integer.class, eventId))
                .isEqualTo(5);
        assertThat(service.failures(null, null)).hasSize(1);
        assertThat(service.config(7).latestFailureReason()).contains("HTTP_500");
    }

    @Test
    void manualReplayUsesStoredTenantDestinationAndDoesNotAcceptRedirect() {
        service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://example.com/status", null, null, 1, 1));
        client.next = new WebhookDeliveryClient.DeliveryResponse(500, "HTTP_500", "failed");
        long eventId = service.enqueueStatusEvent(7, "MSG_REPLAY", "FAILED", null).eventId();
        service.deliverNext();
        assertThat(service.failures(7L, "PUSH_FAILED")).hasSize(1);

        client.next = new WebhookDeliveryClient.DeliveryResponse(200, "HTTP_200", "ok");
        var replayed = service.replay(eventId, "operator", "人工复核后重放");

        assertThat(replayed.state()).isEqualTo("DELIVERED");
        assertThat(client.requests).extracting(RecordedRequest::destinationUrl)
                .containsOnly("https://example.com/status");
    }

    @Test
    void pauseAndResumeKeepExplicitStateAndReasonBoundary() {
        service.saveConfig(7, new WebhookDeliveryTransportService.CallbackConfigCommand(
                "https://example.com/status", null, null, 5, 10));
        long eventId = service.enqueueStatusEvent(7, "MSG_PAUSE", "FAILED", null).eventId();

        assertThatThrownBy(() -> service.pause(eventId, "operator", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原因");
        assertThat(service.pause(eventId, "operator", "下游维护").state()).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("SELECT state FROM webhook_delivery_events WHERE id=?", String.class, eventId))
                .isEqualTo("PAUSED");
        assertThat(service.resume(eventId, "operator", "下游恢复").state()).isEqualTo("RETRY");
    }

    private static final class RecordingClient implements WebhookDeliveryClient {
        private final List<RecordedRequest> requests = new ArrayList<>();
        private DeliveryResponse next = new DeliveryResponse(204, "HTTP_204", null);

        @Override
        public DeliveryResponse post(String destinationUrl, String payloadJson, Map<String, String> headers) {
            requests.add(new RecordedRequest(destinationUrl, payloadJson, headers));
            return next;
        }
    }

    private record RecordedRequest(String destinationUrl, String payloadJson, Map<String, String> headers) { }
}
