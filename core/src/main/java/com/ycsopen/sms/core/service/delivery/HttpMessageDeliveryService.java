package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.repository.BillingRecordRepository;
import com.ycsopen.sms.core.service.billing.BillingService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dispatches Phase 23 accepted outbox rows and applies final provider receipts exactly once. */
@Service
public class HttpMessageDeliveryService {
    private final JdbcTemplate jdbc;
    private final SmsUpstreamProviderClient provider;
    private final MessageDispatchRecipientResolver recipients;
    private final BillingRecordRepository billingRecords;
    private final BillingService billingService;

    public HttpMessageDeliveryService(JdbcTemplate jdbc,
                                      SmsUpstreamProviderClient provider,
                                      MessageDispatchRecipientResolver recipients,
                                      BillingRecordRepository billingRecords,
                                      BillingService billingService) {
        this.jdbc = jdbc;
        this.provider = provider;
        this.recipients = recipients;
        this.billingRecords = billingRecords;
        this.billingService = billingService;
    }

    @Transactional
    public Optional<DispatchResult> dispatchNext() {
        List<Long> candidates = jdbc.queryForList("""
                SELECT id FROM message_send_outbox
                WHERE state='READY'
                ORDER BY created_at, id
                LIMIT 1
                """, Long.class);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        long outboxId = candidates.get(0);
        String token = UUID.randomUUID().toString();
        int claimed = jdbc.update("""
                UPDATE message_send_outbox
                   SET state='CLAIMED', claim_token=?, claimed_at=CURRENT_TIMESTAMP,
                       attempt_count=attempt_count+1, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND state='READY'
                """, token, outboxId);
        if (claimed != 1) {
            return Optional.empty();
        }

        DispatchRow row = dispatchRow(outboxId, token);
        String recipient = recipients.requireRecipient(row.taskId(), row.tenantId(), row.messageId());
        SmsUpstreamProviderClient.ProviderSubmitResult providerResult = provider.submit(
                new SmsUpstreamProviderClient.ProviderSubmitRequest(row.tenantId(), row.taskId(),
                        row.messageId(), row.channelId(), recipient, row.content(), row.idempotencyKey()));

        if (providerResult.status() == SmsUpstreamProviderClient.ProviderSubmitResult.Status.ACCEPTED) {
            markSent(row, providerResult.providerMessageId());
        } else if (providerResult.status() == SmsUpstreamProviderClient.ProviderSubmitResult.Status.REJECTED) {
            markFailed(row, providerResult.errorCode(), providerResult.errorMessage());
        } else {
            markUnknown(row, providerResult.errorCode(), providerResult.errorMessage());
        }
        return Optional.of(new DispatchResult(row.messageId(), providerResult.status().name(),
                providerResult.providerMessageId(), providerResult.errorCode()));
    }

    @Transactional
    public ReceiptResult applyReceipt(ReceiptCommand command) {
        ReceiptCommand checked = command.checked();
        TaskState task = taskForReceipt(checked);
        String digest = receiptDigest(checked);
        boolean inserted = insertReceipt(checked, digest);
        if (!inserted || "DELIVERED".equals(task.sendStatus()) || "FAILED".equals(task.sendStatus())) {
            return new ReceiptResult(task.messageId(), task.sendStatus(), false);
        }
        if (checked.delivered()) {
            jdbc.update("""
                    UPDATE message_tasks
                       SET send_status='DELIVERED', deliver_time=CURRENT_TIMESTAMP,
                           error_code=NULL, error_message=NULL
                     WHERE id=? AND send_status='SENT'
                    """, task.taskId());
            confirmBilling(task.taskId());
            return new ReceiptResult(task.messageId(), "DELIVERED", true);
        }
        jdbc.update("""
                UPDATE message_tasks
                   SET send_status='FAILED', deliver_time=CURRENT_TIMESTAMP,
                       error_code=?, error_message=?
                 WHERE id=? AND send_status='SENT'
                """, checked.errorCode(), checked.errorMessage(), task.taskId());
        reverseBilling(task.taskId());
        return new ReceiptResult(task.messageId(), "FAILED", true);
    }

    private DispatchRow dispatchRow(long outboxId, String token) {
        return jdbc.query("""
                SELECT o.id, o.tenant_id, o.task_id, o.message_id, o.channel_id,
                       t.content, o.claim_token
                  FROM message_send_outbox o
                  JOIN message_tasks t ON t.id=o.task_id
                 WHERE o.id=? AND o.claim_token=? AND o.state='CLAIMED'
                """, rs -> {
            if (!rs.next()) {
                throw new BusinessException("OUTBOX_CLAIM_LOST", "派发任务声明失败");
            }
            return new DispatchRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("task_id"),
                    rs.getString("message_id"), rs.getLong("channel_id"), rs.getString("content"),
                    rs.getString("claim_token"));
        }, outboxId, token);
    }

    private void markSent(DispatchRow row, String providerMessageId) {
        jdbc.update("""
                UPDATE message_tasks
                   SET send_status='SENT', channel_msg_id=?, send_time=CURRENT_TIMESTAMP,
                       error_code=NULL, error_message=NULL
                 WHERE id=? AND send_status='PENDING'
                """, providerMessageId, row.taskId());
        jdbc.update("""
                UPDATE message_send_outbox
                   SET state='SENT', provider_message_id=?, error_code=NULL, error_message=NULL,
                       updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND state='CLAIMED'
                """, providerMessageId, row.id());
    }

    private void markFailed(DispatchRow row, String errorCode, String errorMessage) {
        jdbc.update("""
                UPDATE message_tasks
                   SET send_status='FAILED', error_code=?, error_message=?
                 WHERE id=? AND send_status='PENDING'
                """, errorCode, errorMessage, row.taskId());
        reverseBilling(row.taskId());
        jdbc.update("""
                UPDATE message_send_outbox
                   SET state='FAILED', error_code=?, error_message=?, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND state='CLAIMED'
                """, errorCode, errorMessage, row.id());
    }

    private void markUnknown(DispatchRow row, String errorCode, String errorMessage) {
        jdbc.update("""
                UPDATE message_send_outbox
                   SET error_code=?, error_message=?, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND state='CLAIMED'
                """, errorCode, errorMessage, row.id());
    }

    private TaskState taskForReceipt(ReceiptCommand command) {
        List<TaskState> states = jdbc.query("""
                SELECT id, tenant_id, message_id, send_status
                  FROM message_tasks
                 WHERE message_id=? OR channel_msg_id=?
                 ORDER BY id
                 LIMIT 1
                """, (rs, row) -> new TaskState(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getString("message_id"), rs.getString("send_status")),
                command.messageId(), command.providerMessageId());
        if (states.isEmpty()) {
            throw new BusinessException("MESSAGE_TASK_NOT_FOUND", "消息任务不存在");
        }
        return states.get(0);
    }

    private boolean insertReceipt(ReceiptCommand command, String digest) {
        try {
            jdbc.update("""
                    INSERT INTO delivery_reports(message_id, channel_id, upstream_msg_id, report_status,
                        error_code, raw_payload, receipt_digest, report_time)
                    VALUES (?,?,?,?,?,?,?,?)
                    """, command.messageId(), command.channelId(), command.providerMessageId(),
                    command.delivered() ? "DELIVERED" : "FAILED", command.errorCode(),
                    command.safePayload(), digest, command.reportTime());
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private void confirmBilling(long taskId) {
        billingRecords.findFirstByTaskRefId(taskId).ifPresent(record -> billingService.confirm(record.getId()));
    }

    private void reverseBilling(long taskId) {
        billingRecords.findFirstByTaskRefId(taskId).ifPresent(record -> billingService.reverse(record.getId()));
    }

    private static String receiptDigest(ReceiptCommand command) {
        return sha256(command.messageId() + "\n" + command.providerMessageId() + "\n"
                + command.providerStatus() + "\n" + (command.errorCode() == null ? "" : command.errorCode()));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record DispatchRow(long id, long tenantId, long taskId, String messageId, long channelId,
                               String content, String idempotencyKey) { }
    private record TaskState(long taskId, long tenantId, String messageId, String sendStatus) { }

    public record DispatchResult(String messageId, String providerStatus, String providerMessageId, String errorCode) { }

    public record ReceiptResult(String messageId, String finalStatus, boolean applied) { }

    public record ReceiptCommand(String messageId, String providerMessageId, Long channelId, String providerStatus,
                                 String errorCode, String errorMessage, String safePayload,
                                 LocalDateTime reportTime) {
        ReceiptCommand checked() {
            if (blank(messageId) || blank(providerMessageId) || blank(providerStatus)) {
                throw new BusinessException("INVALID_RECEIPT", "回执字段不完整");
            }
            String normalized = providerStatus.trim().toUpperCase();
            if (!List.of("DELIVERED", "SUCCESS", "FAILED", "REJECTED").contains(normalized)) {
                throw new BusinessException("INVALID_RECEIPT_STATUS", "回执状态不支持");
            }
            if (!delivered(normalized) && blank(errorCode)) {
                throw new BusinessException("RECEIPT_ERROR_CODE_REQUIRED", "失败回执必须包含错误码");
            }
            return new ReceiptCommand(messageId.trim(), providerMessageId.trim(), channelId,
                    normalized, text(errorCode), text(errorMessage), safe(safePayload),
                    reportTime == null ? LocalDateTime.now() : reportTime);
        }

        boolean delivered() {
            return delivered(providerStatus);
        }

        private static boolean delivered(String status) {
            return "DELIVERED".equals(status) || "SUCCESS".equals(status);
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }

        private static String text(String value) {
            return blank(value) ? null : value.trim();
        }

        private static String safe(String value) {
            String trimmed = text(value);
            if (trimmed == null) {
                return "{}";
            }
            return trimmed.length() <= 1000 ? trimmed : trimmed.substring(0, 1000);
        }
    }
}
