package com.ycsopen.sms.core.service.bulk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkScheduledTaskServiceTest {
    private JdbcTemplate jdbc;
    private MessageSubmitService submitter;
    private BulkScheduledTaskService service;
    private final AtomicLong ids = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase29-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE message_submits(
                  id BIGINT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  submit_id VARCHAR(64) NOT NULL,
                  request_digest CHAR(64),
                  source_protocol VARCHAR(16),
                  product_type VARCHAR(32),
                  status VARCHAR(32),
                  UNIQUE(tenant_id, submit_id)
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT PRIMARY KEY,
                  message_id VARCHAR(64) NOT NULL UNIQUE,
                  submit_id BIGINT,
                  tenant_id BIGINT NOT NULL,
                  template_id BIGINT,
                  signature_id BIGINT,
                  mobile_encrypted VARBINARY(255) NOT NULL,
                  mobile_hash CHAR(64) NOT NULL,
                  content VARCHAR(600) NOT NULL,
                  send_status VARCHAR(24) NOT NULL,
                  channel_id BIGINT,
                  cost DECIMAL(10,4) NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("""
                CREATE TABLE bulk_sendings(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  task_name VARCHAR(128) NOT NULL,
                  message_type VARCHAR(32) NOT NULL,
                  priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
                  template_id BIGINT,
                  signature_id BIGINT,
                  total_count INT NOT NULL DEFAULT 0,
                  success_count INT NOT NULL DEFAULT 0,
                  fail_count INT NOT NULL DEFAULT 0,
                  task_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                  schedule_time TIMESTAMP,
                  start_time TIMESTAMP,
                  end_time TIMESTAMP,
                  total_cost DECIMAL(12,4) NOT NULL DEFAULT 0,
                  created_by VARCHAR(64),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  batch_key VARCHAR(64),
                  source_file_name VARCHAR(255),
                  valid_count INT NOT NULL DEFAULT 0,
                  invalid_count INT NOT NULL DEFAULT 0,
                  running_count INT NOT NULL DEFAULT 0,
                  completed_count INT NOT NULL DEFAULT 0,
                  cancelled_count INT NOT NULL DEFAULT 0,
                  failure_reason VARCHAR(500),
                  control_reason VARCHAR(500),
                  import_snapshot_json VARCHAR(4000),
                  UNIQUE(tenant_id, batch_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE bulk_sending_items(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  bulk_id BIGINT NOT NULL,
                  mobile_encrypted VARBINARY(255) NOT NULL,
                  template_params VARCHAR(1000),
                  send_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                  send_time TIMESTAMP,
                  error_message VARCHAR(255),
                  tenant_id BIGINT,
                  item_tracking_id VARCHAR(80) UNIQUE,
                  row_no INT,
                  mobile_hash CHAR(64),
                  message_id VARCHAR(64),
                  message_task_id BIGINT,
                  submit_id BIGINT,
                  validation_status VARCHAR(16) NOT NULL DEFAULT 'VALID',
                  validation_reason VARCHAR(255),
                  cost DECIMAL(10,4) NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(bulk_id, row_no)
                )
                """);
        submitter = mock(MessageSubmitService.class);
        when(submitter.submit(anyLong(), nullable(Long.class), any(SmsSendRequest.class), nullable(String.class)))
                .thenAnswer(this::acceptedSubmission);
        service = new BulkScheduledTaskService(jdbc, submitter, new ObjectMapper());
    }

    @Test
    void previewsImportValidationAndPartialFailures() {
        var preview = service.preview(42, command("BULK-1"));

        assertThat(preview.total()).isEqualTo(3);
        assertThat(preview.valid()).isEqualTo(1);
        assertThat(preview.invalid()).isEqualTo(2);
        assertThat(preview.rows()).extracting(BulkScheduledTaskService.PreviewRow::validationReason)
                .contains(null, "重复手机号", "手机号格式不合法");
    }

    @Test
    void createBulkReusesSingleMessageAcceptanceAndIsBatchIdempotent() {
        var first = service.create(42, command("BULK-2"), "127.0.0.1", "operator");
        var second = service.create(42, command("BULK-2"), "127.0.0.1", "operator");

        assertThat(second.bulkId()).isEqualTo(first.bulkId());
        assertThat(first.totalCount()).isEqualTo(3);
        assertThat(first.validCount()).isEqualTo(1);
        assertThat(first.invalidCount()).isEqualTo(2);
        assertThat(first.runningCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bulk_sending_items", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT message_task_id FROM bulk_sending_items", Long.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT mobile_hash FROM bulk_sending_items", String.class)).hasSize(64);
    }

    @Test
    void scheduledBulkCreationDoesNotSubmitBeforeScheduleWorkerRuns() {
        var scheduled = service.create(42, scheduledCommand("BULK-SCHEDULED"), "127.0.0.1", "operator");

        assertThat(scheduled.state()).isEqualTo("PENDING");
        assertThat(scheduled.scheduleAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 9, 0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bulk_sending_items", Integer.class)).isZero();
        verify(submitter, never()).submit(anyLong(), nullable(Long.class), any(SmsSendRequest.class), nullable(String.class));
    }

    @Test
    void rejectsUnsafeOrIncompleteImportMetadata() {
        assertThatThrownBy(() -> service.preview(42, commandWithMetadata("BULK-NOSIZE", "contacts.csv", null, "CLEAN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("大小");
        assertThatThrownBy(() -> service.preview(42, commandWithMetadata("BULK-BIG", "contacts.csv", 11 * 1024 * 1024L, "CLEAN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过限制");
        assertThatThrownBy(() -> service.preview(42, commandWithMetadata("BULK-TYPE", "contacts.txt", 128L, "CLEAN")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CSV 或 Excel");
        assertThatThrownBy(() -> service.preview(42, commandWithMetadata("BULK-MALWARE", "contacts.csv", 128L, "INFECTED")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全扫描");
    }

    @Test
    void allInvalidImportCompletesAsFailedWithoutSubmitting() {
        var failed = service.create(42, invalidOnlyCommand("BULK-INVALID"), "127.0.0.1", "operator");

        assertThat(failed.state()).isEqualTo("FAILED");
        assertThat(failed.validCount()).isZero();
        assertThat(failed.invalidCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bulk_sending_items", Integer.class)).isZero();
        verify(submitter, never()).submit(anyLong(), nullable(Long.class), any(SmsSendRequest.class), nullable(String.class));
    }

    @Test
    void midBatchSubmitFailureKeepsPreviousTrackingAndMarksTaskFailed() {
        AtomicInteger calls = new AtomicInteger();
        when(submitter.submit(anyLong(), nullable(Long.class), any(SmsSendRequest.class), nullable(String.class)))
                .thenAnswer(call -> {
                    if (calls.incrementAndGet() == 2) {
                        throw new BusinessException("BULK_ITEM_ACCEPTANCE_FAILED", "第二行提交失败");
                    }
                    return acceptedSubmission(call);
                });

        assertThatThrownBy(() -> service.create(42, twoValidRowsCommand("BULK-PARTIAL-FAIL"), "127.0.0.1", "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第二行提交失败");

        long bulkId = jdbc.queryForObject("SELECT id FROM bulk_sendings WHERE batch_key='BULK-PARTIAL-FAIL'", Long.class);
        assertThat(jdbc.queryForObject("SELECT task_status FROM bulk_sendings WHERE id=?", String.class, bulkId)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bulk_sending_items WHERE bulk_id=?", Integer.class, bulkId)).isEqualTo(1);
    }

    @Test
    void controlsStateBoundariesAndReconcilesTerminalItems() {
        var created = service.create(42, command("BULK-3"), "127.0.0.1", "operator");

        assertThat(service.control(created.bulkId(), "PAUSE", "operator", "下游维护").state()).isEqualTo("PAUSED");
        assertThat(service.control(created.bulkId(), "RESUME", "operator", "恢复").state()).isEqualTo("RUNNING");
        jdbc.update("UPDATE bulk_sending_items SET send_status='DELIVERED' WHERE bulk_id=?", created.bulkId());
        assertThat(service.reconcile(created.bulkId()).state()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> service.control(created.bulkId(), "PAUSE", "operator", "已完成不能暂停"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态");
    }

    @Test
    void cancelFailAndRestartControlsHaveExecutableStateEvidence() {
        var cancelled = service.create(42, twoValidRowsCommand("BULK-CANCEL"), "127.0.0.1", "operator");
        assertThat(service.control(cancelled.bulkId(), "CANCEL", "operator", "客户取消").state()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM bulk_sending_items WHERE bulk_id=? AND send_status='CANCELLED'",
                Integer.class, cancelled.bulkId())).isEqualTo(2);

        var failed = service.create(42, twoValidRowsCommand("BULK-RESTART"), "127.0.0.1", "operator");
        assertThat(service.control(failed.bulkId(), "FAIL", "operator", "通道异常").state()).isEqualTo("FAILED");
        jdbc.update("UPDATE bulk_sending_items SET send_status='FAILED' WHERE bulk_id=? AND row_no=1", failed.bulkId());
        jdbc.update("UPDATE bulk_sending_items SET send_status='CANCELLED' WHERE bulk_id=? AND row_no=2", failed.bulkId());
        assertThat(service.control(failed.bulkId(), "RESTART", "operator", "重新处理非终态子项").state()).isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM bulk_sending_items WHERE bulk_id=? AND send_status='PENDING'",
                Integer.class, failed.bulkId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bulk_sending_items WHERE bulk_id=?", Integer.class, failed.bulkId())).isEqualTo(2);
    }

    @Test
    void tenantControlCannotOperateAnotherTenantsTask() {
        var created = service.create(42, command("BULK-4"), "127.0.0.1", "operator");

        assertThatThrownBy(() -> service.controlForTenant(43, created.bulkId(), "PAUSE", "operator", "越权"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    private static BulkScheduledTaskService.BulkCommand command(String batchKey) {
        return new BulkScheduledTaskService.BulkCommand(batchKey, "营销批次", "NOTIFY", "HIGH",
                "11", "12", null, "contacts.csv", 128L, "CLEAN", List.of(
                new BulkScheduledTaskService.BulkItemCommand("13800138000", Map.of("code", "1234")),
                new BulkScheduledTaskService.BulkItemCommand("13800138000", Map.of("code", "5678")),
                new BulkScheduledTaskService.BulkItemCommand("bad-phone", Map.of("code", "0000"))));
    }

    private static BulkScheduledTaskService.BulkCommand scheduledCommand(String batchKey) {
        return new BulkScheduledTaskService.BulkCommand(batchKey, "计划批次", "NOTIFY", "HIGH",
                "11", "12", LocalDateTime.of(2026, 9, 10, 9, 0), "contacts.csv", 128L, "CLEAN", List.of(
                new BulkScheduledTaskService.BulkItemCommand("13800138000", Map.of("code", "1234"))));
    }

    private static BulkScheduledTaskService.BulkCommand commandWithMetadata(
            String batchKey, String sourceFileName, Long sizeBytes, String malwareVerdict) {
        return new BulkScheduledTaskService.BulkCommand(batchKey, "营销批次", "NOTIFY", "HIGH",
                "11", "12", null, sourceFileName, sizeBytes, malwareVerdict, List.of(
                new BulkScheduledTaskService.BulkItemCommand("13800138000", Map.of("code", "1234"))));
    }

    private static BulkScheduledTaskService.BulkCommand invalidOnlyCommand(String batchKey) {
        return new BulkScheduledTaskService.BulkCommand(batchKey, "无效批次", "NOTIFY", "HIGH",
                "11", "12", null, "contacts.csv", 128L, "CLEAN", List.of(
                new BulkScheduledTaskService.BulkItemCommand("bad-phone", Map.of("code", "0000")),
                new BulkScheduledTaskService.BulkItemCommand("not-mobile", Map.of("code", "0001"))));
    }

    private static BulkScheduledTaskService.BulkCommand twoValidRowsCommand(String batchKey) {
        return new BulkScheduledTaskService.BulkCommand(batchKey, "两条有效", "NOTIFY", "HIGH",
                "11", "12", null, "contacts.csv", 128L, "CLEAN", List.of(
                new BulkScheduledTaskService.BulkItemCommand("13800138000", Map.of("code", "1234")),
                new BulkScheduledTaskService.BulkItemCommand("13900139000", Map.of("code", "5678"))));
    }

    private SmsSendResponse acceptedSubmission(org.mockito.invocation.InvocationOnMock call) {
        long tenantId = call.getArgument(0);
        SmsSendRequest request = call.getArgument(2);
        long submitId = ids.incrementAndGet();
        long taskId = ids.incrementAndGet();
        String messageId = "MSG_" + taskId + "_BULKTEST";
        jdbc.update("""
                INSERT INTO message_submits(id, tenant_id, submit_id, request_digest, source_protocol, product_type, status)
                VALUES (?,?,?,?, 'HTTP', 'NOTIFY', 'ACCEPTED')
                """, submitId, tenantId, request.submitId(), "0".repeat(64));
        jdbc.update("""
                INSERT INTO message_tasks(id, message_id, submit_id, tenant_id, template_id, signature_id,
                    mobile_encrypted, mobile_hash, content, send_status, channel_id, cost)
                VALUES (?,?,?,?,?,?,?,?, 'content', 'PENDING', 9, ?)
                """, taskId, messageId, submitId, tenantId, Long.parseLong(request.templateId()),
                Long.parseLong(request.signId()), new byte[]{1, 2, 3}, "a".repeat(64),
                new BigDecimal("0.0500"));
        return new SmsSendResponse(messageId, "PENDING");
    }
}
