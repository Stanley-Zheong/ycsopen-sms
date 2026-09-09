package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.domain.entity.BillingRecord;
import com.ycsopen.sms.core.repository.BillingRecordRepository;
import com.ycsopen.sms.core.service.billing.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpMessageDeliveryServiceTest {
    private JdbcTemplate jdbc;
    private RecordingProvider provider;
    private BillingRecordRepository billingRecords;
    private BillingService billingService;
    private HttpMessageDeliveryService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase24-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  message_id VARCHAR(64) NOT NULL,
                  content VARCHAR(600) NOT NULL,
                  send_status VARCHAR(16) NOT NULL,
                  channel_id BIGINT,
                  channel_msg_id VARCHAR(128),
                  error_code VARCHAR(64),
                  error_message VARCHAR(255),
                  send_time TIMESTAMP,
                  deliver_time TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_send_outbox(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL UNIQUE,
                  message_id VARCHAR(64) NOT NULL UNIQUE,
                  channel_id BIGINT NOT NULL,
                  state VARCHAR(16) NOT NULL,
                  claim_token VARCHAR(64) UNIQUE,
                  claimed_at TIMESTAMP,
                  attempt_count INT NOT NULL DEFAULT 0,
                  provider_message_id VARCHAR(128),
                  error_code VARCHAR(64),
                  error_message VARCHAR(255),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE delivery_reports(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  message_id VARCHAR(64) NOT NULL,
                  channel_id BIGINT,
                  upstream_msg_id VARCHAR(128),
                  report_status VARCHAR(16) NOT NULL,
                  error_code VARCHAR(64),
                  raw_payload TEXT,
                  receipt_digest CHAR(64) UNIQUE,
                  report_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        provider = new RecordingProvider();
        billingRecords = mock(BillingRecordRepository.class);
        billingService = mock(BillingService.class);
        service = new HttpMessageDeliveryService(jdbc, provider,
                (taskId, tenantId, messageId) -> "13800138000", billingRecords, billingService);
    }

    @Test
    void dispatchClaimsOneReadyOutboxAndSendsExactlyOnce() {
        seedAcceptedTask("MSG_1", "PENDING");
        provider.next = SmsUpstreamProviderClient.ProviderSubmitResult.accepted("UP-1");

        Optional<HttpMessageDeliveryService.DispatchResult> first = service.dispatchNext();
        Optional<HttpMessageDeliveryService.DispatchResult> second = service.dispatchNext();

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        assertThat(provider.requests).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT send_status FROM message_tasks WHERE id=1", String.class)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT channel_msg_id FROM message_tasks WHERE id=1", String.class)).isEqualTo("UP-1");
        assertThat(jdbc.queryForObject("SELECT state FROM message_send_outbox WHERE task_id=1", String.class)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM message_send_outbox WHERE task_id=1", Integer.class)).isEqualTo(1);
    }

    @Test
    void providerRejectionFailsTaskAndReversesReservedBillingOnce() {
        seedAcceptedTask("MSG_2", "PENDING");
        BillingRecord billing = billing(77L);
        when(billingRecords.findFirstByTaskRefId(1L)).thenReturn(Optional.of(billing));
        provider.next = SmsUpstreamProviderClient.ProviderSubmitResult.rejected("BLACKLIST", "blocked");

        service.dispatchNext();

        assertThat(jdbc.queryForObject("SELECT send_status FROM message_tasks WHERE id=1", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT state FROM message_send_outbox WHERE task_id=1", String.class)).isEqualTo("FAILED");
        verify(billingService, times(1)).reverse(77L);
    }

    @Test
    void unknownProviderOutcomeIsQuarantinedAndNotRetriedAutomatically() {
        seedAcceptedTask("MSG_3", "PENDING");
        provider.next = SmsUpstreamProviderClient.ProviderSubmitResult.unknown("PROVIDER_TIMEOUT", "unknown");

        service.dispatchNext();
        Optional<HttpMessageDeliveryService.DispatchResult> second = service.dispatchNext();

        assertThat(second).isEmpty();
        assertThat(provider.requests).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT send_status FROM message_tasks WHERE id=1", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT state FROM message_send_outbox WHERE task_id=1", String.class)).isEqualTo("CLAIMED");
        assertThat(jdbc.queryForObject("SELECT error_code FROM message_send_outbox WHERE task_id=1", String.class)).isEqualTo("PROVIDER_TIMEOUT");
    }

    @Test
    void duplicateDeliveredReceiptConfirmsBillingOnceAndStoresOneReport() {
        seedAcceptedTask("MSG_4", "SENT");
        jdbc.update("UPDATE message_tasks SET channel_msg_id='UP-4' WHERE id=1");
        BillingRecord billing = billing(78L);
        when(billingRecords.findFirstByTaskRefId(1L)).thenReturn(Optional.of(billing));

        var first = service.applyReceipt(new HttpMessageDeliveryService.ReceiptCommand(
                "MSG_4", "UP-4", 42L, "DELIVERED", null, null, "{\"status\":\"DELIVERED\"}", null));
        var second = service.applyReceipt(new HttpMessageDeliveryService.ReceiptCommand(
                "MSG_4", "UP-4", 42L, "DELIVERED", null, null, "{\"status\":\"DELIVERED\"}", null));

        assertThat(first.applied()).isTrue();
        assertThat(second.applied()).isFalse();
        assertThat(jdbc.queryForObject("SELECT send_status FROM message_tasks WHERE id=1", String.class)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_reports", Integer.class)).isEqualTo(1);
        verify(billingService, times(1)).confirm(78L);
    }

    @Test
    void failedReceiptReversesBillingOnceAndWinsOverLaterDuplicates() {
        seedAcceptedTask("MSG_5", "SENT");
        jdbc.update("UPDATE message_tasks SET channel_msg_id='UP-5' WHERE id=1");
        BillingRecord billing = billing(79L);
        when(billingRecords.findFirstByTaskRefId(1L)).thenReturn(Optional.of(billing));

        service.applyReceipt(new HttpMessageDeliveryService.ReceiptCommand(
                "MSG_5", "UP-5", 42L, "FAILED", "DELIVRD_FAILED", "failed", "{\"status\":\"FAILED\"}", null));
        service.applyReceipt(new HttpMessageDeliveryService.ReceiptCommand(
                "MSG_5", "UP-5", 42L, "FAILED", "DELIVRD_FAILED", "failed", "{\"status\":\"FAILED\"}", null));

        assertThat(jdbc.queryForObject("SELECT send_status FROM message_tasks WHERE id=1", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_reports", Integer.class)).isEqualTo(1);
        verify(billingService, times(1)).reverse(79L);
    }

    private void seedAcceptedTask(String messageId, String status) {
        jdbc.update("""
                INSERT INTO message_tasks(id, tenant_id, message_id, content, send_status, channel_id)
                VALUES (1, 17, ?, '验证码 123456', ?, 42)
                """, messageId, status);
        jdbc.update("""
                INSERT INTO message_send_outbox(tenant_id, task_id, message_id, channel_id, state)
                VALUES (17, 1, ?, 42, 'READY')
                """, messageId);
    }

    private static BillingRecord billing(long id) {
        BillingRecord billing = new BillingRecord();
        billing.setId(id);
        return billing;
    }

    private static final class RecordingProvider implements SmsUpstreamProviderClient {
        private final List<ProviderSubmitRequest> requests = new ArrayList<>();
        private ProviderSubmitResult next = ProviderSubmitResult.accepted("UP-DEFAULT");

        @Override
        public ProviderSubmitResult submit(ProviderSubmitRequest request) {
            requests.add(request);
            return next;
        }
    }
}
