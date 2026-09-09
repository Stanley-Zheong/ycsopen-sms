package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageReceiptErrorOperationsServiceTest {
    private JdbcTemplate jdbc;
    private DispatchTaskRecoveryService recovery;
    private HttpMessageDeliveryService delivery;
    private MessageReceiptErrorOperationsService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase27-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE message_submits(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id VARCHAR(64) NOT NULL,
                  request_digest VARCHAR(64),
                  source_protocol VARCHAR(16),
                  product_type VARCHAR(16),
                  status VARCHAR(16),
                  template_id BIGINT,
                  signature_id BIGINT,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id BIGINT,
                  template_id BIGINT,
                  signature_id BIGINT,
                  message_id VARCHAR(64) NOT NULL,
                  content VARCHAR(600) NOT NULL,
                  send_status VARCHAR(16) NOT NULL,
                  channel_id BIGINT,
                  channel_msg_id VARCHAR(128),
                  operator VARCHAR(16),
                  province VARCHAR(32),
                  city VARCHAR(32),
                  error_code VARCHAR(64),
                  error_message VARCHAR(255),
                  cost DECIMAL(10,4) DEFAULT 0,
                  retry_count INT DEFAULT 0,
                  send_time TIMESTAMP,
                  deliver_time TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  version INT DEFAULT 1
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_send_outbox(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  task_id BIGINT NOT NULL,
                  message_id VARCHAR(64) NOT NULL,
                  channel_id BIGINT NOT NULL,
                  state VARCHAR(16) NOT NULL,
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
                  receipt_digest VARCHAR(64),
                  report_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE billing_records(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  task_ref_id BIGINT NOT NULL,
                  billing_status VARCHAR(16)
                )
                """);
        jdbc.execute("""
                CREATE TABLE provider_status_mappings(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  provider_code VARCHAR(64),
                  platform_category VARCHAR(64),
                  retryable BOOLEAN,
                  severity VARCHAR(16),
                  status VARCHAR(16)
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_operation_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  operation_key VARCHAR(180) NOT NULL UNIQUE,
                  action_id VARCHAR(64) NOT NULL,
                  action_type VARCHAR(32) NOT NULL,
                  target_message_id VARCHAR(64),
                  target_task_id BIGINT,
                  target_receipt_id BIGINT,
                  actor VARCHAR(64) NOT NULL,
                  reason VARCHAR(500) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  result_code VARCHAR(64),
                  result_message VARCHAR(500),
                  snapshot_json VARCHAR(1000),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        recovery = mock(DispatchTaskRecoveryService.class);
        delivery = mock(HttpMessageDeliveryService.class);
        service = new MessageReceiptErrorOperationsService(jdbc, recovery, delivery);
        seed();
    }

    @Test
    void listViewsExposeTraceColumnsAndDoNotRevealProtectedMobileOrFullContent() {
        var filter = new MessageReceiptErrorOperationsService.OperationFilter(42L, null, null, null, null, null, null);

        var submissions = service.submissions(filter);
        var sends = service.sends(filter);
        var receipts = service.receipts(filter);

        assertThat(submissions).extracting(MessageReceiptErrorOperationsService.SubmissionRow::messageId)
                .contains("MSG_FAILED");
        assertThat(sends).extracting(MessageReceiptErrorOperationsService.SendRow::maskedMobile)
                .containsOnly("已保护");
        assertThat(sends.get(0).contentSummary()).doesNotContain("请勿泄露给任何人");
        assertThat(receipts).extracting(MessageReceiptErrorOperationsService.ReceiptRow::rawPayloadSummary)
                .allSatisfy(summary -> assertThat(summary).doesNotContain("13800138000"));
    }

    @Test
    void hmacStatusQueryRejectsCrossTenantMessageWithoutExistenceLeak() {
        MessageStatusQueryService statuses = new MessageStatusQueryService(jdbc);

        assertThatThrownBy(() -> statuses.status(99L, "MSG_FAILED"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("MESSAGE_STATUS_NOT_FOUND");
    }

    @Test
    void resendIsIdempotentAndPreservesOriginalEvidence() {
        when(recovery.retryFailedTask(201L, "operator", "人工确认可重发"))
                .thenReturn(new DispatchTaskRecoveryService.RecoveryResult(201L, 301L, "RETRY_CREATED", 8L));

        var request = new MessageReceiptErrorOperationsService.ActionRequest("ACT-RESEND-1", "人工确认可重发");
        var first = service.resend("MSG_FAILED", request, "operator");
        var second = service.resend("MSG_FAILED", request, "operator");

        assertThat(first.resultCode()).isEqualTo("RETRY_CREATED");
        assertThat(second.status()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_operation_events WHERE action_type='RESEND'",
                Integer.class)).isEqualTo(1);
        verify(recovery, times(1)).retryFailedTask(201L, "operator", "人工确认可重发");
    }

    @Test
    void receiptCorrectionAddsNewReceiptAndUpdatesFinalStateWithoutDeletingOriginal() {
        var result = service.correctReceipt(501L,
                new MessageReceiptErrorOperationsService.ReceiptCorrectionRequest(
                        "ACT-CORRECT-1", "供应商人工确认送达", "DELIVERED", null, "ST20260909"),
                "operator");

        assertThat(result.resultCode()).isEqualTo("RECEIPT_CORRECTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_reports WHERE message_id='MSG_FAILED'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT send_status FROM message_tasks WHERE id=201",
                String.class)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT snapshot_json FROM message_operation_events WHERE action_type='RECEIPT_CORRECT'",
                String.class)).contains("\"receiptId\":501");
    }

    @Test
    void bulkErrorActionReportsPartialOutcomesAndDoesNotDuplicateMarkedRecords() {
        var request = new MessageReceiptErrorOperationsService.BulkErrorActionRequest(
                "ACT-BULK-1", "MARK_PROBLEM", "E42", List.of("MSG_FAILED", "MSG_SENT"), "供应商异常批量标记");

        var result = service.bulkErrors(request, "operator");
        var repeated = service.bulkErrors(request, "operator");

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(repeated.completed()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_operation_events WHERE action_type='MARK_PROBLEM'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void errorGroupsUseNormalizedTaxonomyAndExportRequestsOnlyCreateHandoffEvidence() {
        var errors = service.errorGroups(new MessageReceiptErrorOperationsService.OperationFilter(
                42L, null, null, null, "E42", null, null));
        var export = service.exportRequest(new MessageReceiptErrorOperationsService.OperationFilter(
                42L, null, null, null, null, null, null),
                new MessageReceiptErrorOperationsService.ActionRequest("ACT-EXPORT-1", "运营导出复核"),
                "operator");

        assertThat(errors).singleElement()
                .satisfies(row -> {
                    assertThat(row.normalizedCode()).isEqualTo("E42");
                    assertThat(row.platformCategory()).isEqualTo("FAILURE");
                    assertThat(row.retryable()).isTrue();
                });
        assertThat(export.resultCode()).isEqualTo("EXPORT_REQUESTED");
        assertThat(jdbc.queryForObject("SELECT snapshot_json FROM message_operation_events WHERE action_type='EXPORT_REQUEST'",
                String.class)).contains("secure-async-export");
    }

    private void seed() {
        jdbc.update("""
                INSERT INTO message_submits(id, tenant_id, submit_id, request_digest, source_protocol, product_type,
                  status, template_id, signature_id, created_at)
                VALUES (101, 42, 'SUBMIT-FAILED', 'digest', 'HTTP', 'NOTIFY', 'ACCEPTED', 11, 12, ?),
                       (102, 42, 'SUBMIT-SENT', 'digest2', 'HTTP', 'NOTIFY', 'ACCEPTED', 11, 12, ?)
                """, LocalDateTime.now().minusMinutes(2), LocalDateTime.now().minusMinutes(1));
        jdbc.update("""
                INSERT INTO message_tasks(id, tenant_id, submit_id, template_id, signature_id, message_id, content,
                  send_status, channel_id, channel_msg_id, operator, province, city, error_code, error_message,
                  cost, retry_count, send_time, deliver_time, version)
                VALUES
                  (201, 42, 101, 11, 12, 'MSG_FAILED', '【签名】验证码 2468 请勿泄露给任何人', 'FAILED', 7,
                   'UP-1', 'MOBILE', '广东', '深圳', 'E42', '供应商拒绝', 0.0500, 0, ?, ?, 3),
                  (202, 42, 102, 11, 12, 'MSG_SENT', '【签名】通知内容', 'SENT', 7,
                   'UP-2', 'MOBILE', '广东', '广州', NULL, NULL, 0.0500, 0, ?, NULL, 1)
                """, LocalDateTime.now().minusMinutes(2), LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().minusMinutes(1));
        jdbc.update("""
                INSERT INTO message_send_outbox(id, tenant_id, task_id, message_id, channel_id, state,
                  provider_message_id, error_code, error_message)
                VALUES (401, 42, 201, 'MSG_FAILED', 7, 'FAILED', 'UP-1', 'E42', '供应商拒绝'),
                       (402, 42, 202, 'MSG_SENT', 7, 'SENT', 'UP-2', NULL, NULL)
                """);
        jdbc.update("""
                INSERT INTO delivery_reports(id, message_id, channel_id, upstream_msg_id, report_status,
                  error_code, raw_payload, receipt_digest, report_time)
                VALUES (501, 'MSG_FAILED', 7, 'UP-1', 'FAILED', 'E42', 'raw payload protected', 'R-1', ?)
                """, LocalDateTime.now().minusMinutes(1));
        jdbc.update("""
                INSERT INTO provider_status_mappings(provider_code, platform_category, retryable, severity, status)
                VALUES ('E42', 'FAILURE', TRUE, 'ERROR', 'ACTIVE')
                """);
    }
}
