package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Phase 27: query and action boundary for message, receipt, and normalized error operations. */
@Service
public class MessageReceiptErrorOperationsService {
    private static final int MAX_LIST_ROWS = 200;
    private static final int MAX_BULK_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final DispatchTaskRecoveryService recovery;
    private final HttpMessageDeliveryService delivery;

    public MessageReceiptErrorOperationsService(JdbcTemplate jdbc,
                                                DispatchTaskRecoveryService recovery,
                                                HttpMessageDeliveryService delivery) {
        this.jdbc = jdbc;
        this.recovery = recovery;
        this.delivery = delivery;
    }

    @Transactional(readOnly = true)
    public List<SubmissionRow> submissions(OperationFilter filter) {
        OperationFilter checked = filter.checked();
        return jdbc.query("""
                SELECT ms.id, ms.tenant_id, ms.submit_id, ms.source_protocol, ms.product_type, ms.status,
                       ms.template_id, ms.signature_id, ms.created_at,
                       mt.message_id, mt.send_status, mt.error_code, mt.error_message
                  FROM message_submits ms
             LEFT JOIN message_tasks mt ON mt.submit_id=ms.id
                 WHERE (? IS NULL OR ms.tenant_id=?)
                   AND (? IS NULL OR mt.message_id=?)
                   AND (? IS NULL OR ms.status=? OR mt.send_status=?)
                   AND (? IS NULL OR ms.created_at>=?)
                   AND (? IS NULL OR ms.created_at<=?)
                 ORDER BY ms.created_at DESC, ms.id DESC
                 LIMIT ?
                """, (rs, row) -> new SubmissionRow(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getString("submit_id"), rs.getString("message_id"), rs.getString("source_protocol"),
                rs.getString("product_type"), rs.getString("status"), rs.getString("send_status"),
                rs.getObject("template_id", Long.class), rs.getObject("signature_id", Long.class),
                rs.getString("error_code"), rs.getString("error_message"),
                timestamp(rs.getTimestamp("created_at"))),
                checked.tenantId(), checked.tenantId(), checked.messageId(), checked.messageId(),
                checked.status(), checked.status(), checked.status(), checked.startAt(), checked.startAt(),
                checked.endAt(), checked.endAt(), MAX_LIST_ROWS);
    }

    @Transactional(readOnly = true)
    public List<SendRow> sends(OperationFilter filter) {
        OperationFilter checked = filter.checked();
        return jdbc.query("""
                SELECT t.id, t.message_id, t.tenant_id, t.submit_id, t.template_id, t.signature_id,
                       t.content, t.send_status, t.channel_id, t.channel_msg_id, t.operator, t.province, t.city,
                       t.error_code, t.error_message, t.cost, t.retry_count, t.send_time, t.deliver_time,
                       t.created_at, t.version,
                       o.state AS outbox_state, o.provider_message_id AS outbox_provider_message_id
                  FROM message_tasks t
             LEFT JOIN message_send_outbox o ON o.task_id=t.id
                 WHERE (? IS NULL OR t.tenant_id=?)
                   AND (? IS NULL OR t.message_id=?)
                   AND (? IS NULL OR t.send_status=?)
                   AND (? IS NULL OR t.channel_id=?)
                   AND (? IS NULL OR t.created_at>=?)
                   AND (? IS NULL OR t.created_at<=?)
                 ORDER BY t.created_at DESC, t.id DESC
                 LIMIT ?
                """, (rs, row) -> new SendRow(rs.getLong("id"), rs.getString("message_id"),
                rs.getLong("tenant_id"), rs.getObject("submit_id", Long.class), "已保护",
                redactContent(rs.getString("content")), rs.getString("send_status"),
                rs.getObject("channel_id", Long.class), firstText(rs.getString("channel_msg_id"),
                rs.getString("outbox_provider_message_id")), rs.getString("operator"), rs.getString("province"),
                rs.getString("city"), rs.getString("error_code"), rs.getString("error_message"),
                rs.getBigDecimal("cost"), rs.getInt("retry_count"), rs.getString("outbox_state"),
                timestamp(rs.getTimestamp("send_time")), timestamp(rs.getTimestamp("deliver_time")),
                timestamp(rs.getTimestamp("created_at")), rs.getInt("version")),
                checked.tenantId(), checked.tenantId(), checked.messageId(), checked.messageId(),
                checked.status(), checked.status(), checked.channelId(), checked.channelId(),
                checked.startAt(), checked.startAt(), checked.endAt(), checked.endAt(), MAX_LIST_ROWS);
    }

    @Transactional(readOnly = true)
    public List<ReceiptRow> receipts(OperationFilter filter) {
        OperationFilter checked = filter.checked();
        return jdbc.query("""
                SELECT r.id, r.message_id, t.tenant_id, r.channel_id, r.upstream_msg_id, r.report_status,
                       r.error_code, r.raw_payload, r.report_time, r.receipt_digest,
                       t.send_status, t.channel_msg_id, t.operator, t.province, t.city
                  FROM delivery_reports r
             LEFT JOIN message_tasks t ON t.message_id=r.message_id
                 WHERE (? IS NULL OR t.tenant_id=?)
                   AND (? IS NULL OR r.message_id=?)
                   AND (? IS NULL OR r.report_status=?)
                   AND (? IS NULL OR r.channel_id=?)
                   AND (? IS NULL OR r.report_time>=?)
                   AND (? IS NULL OR r.report_time<=?)
                 ORDER BY r.report_time DESC, r.id DESC
                 LIMIT ?
                """, (rs, row) -> new ReceiptRow(rs.getLong("id"), rs.getString("message_id"),
                rs.getObject("tenant_id", Long.class), "已保护", rs.getObject("channel_id", Long.class),
                rs.getString("upstream_msg_id"), rs.getString("report_status"), rs.getString("send_status"),
                rs.getString("error_code"), summarizePayload(rs.getString("raw_payload")),
                rs.getString("receipt_digest"), rs.getString("operator"), rs.getString("province"),
                rs.getString("city"), timestamp(rs.getTimestamp("report_time"))),
                checked.tenantId(), checked.tenantId(), checked.messageId(), checked.messageId(),
                checked.status(), checked.status(), checked.channelId(), checked.channelId(),
                checked.startAt(), checked.startAt(), checked.endAt(), checked.endAt(), MAX_LIST_ROWS);
    }

    @Transactional(readOnly = true)
    public List<ErrorGroupRow> errorGroups(OperationFilter filter) {
        OperationFilter checked = filter.checked();
        return jdbc.query("""
                SELECT COALESCE(t.error_code, 'UNKNOWN') AS normalized_code,
                       COALESCE(psm.platform_category, 'UNKNOWN_REVIEW_REQUIRED') AS platform_category,
                       COALESCE(psm.severity, 'WARN') AS severity,
                       COALESCE(psm.retryable, FALSE) AS retryable,
                       COUNT(*) AS total_count,
                       COUNT(DISTINCT t.tenant_id) AS tenant_count,
                       COUNT(DISTINCT t.channel_id) AS channel_count,
                       MIN(t.created_at) AS first_seen_at,
                       MAX(t.updated_at) AS last_seen_at
                  FROM message_tasks t
             LEFT JOIN provider_status_mappings psm
                    ON psm.status='ACTIVE'
                   AND psm.provider_code=t.error_code
                 WHERE t.send_status='FAILED'
                   AND (? IS NULL OR t.tenant_id=?)
                   AND (? IS NULL OR t.error_code=?)
                   AND (? IS NULL OR t.channel_id=?)
                   AND (? IS NULL OR t.created_at>=?)
                   AND (? IS NULL OR t.created_at<=?)
                 GROUP BY COALESCE(t.error_code, 'UNKNOWN'), COALESCE(psm.platform_category, 'UNKNOWN_REVIEW_REQUIRED'),
                          COALESCE(psm.severity, 'WARN'), COALESCE(psm.retryable, FALSE)
                 ORDER BY total_count DESC, last_seen_at DESC
                 LIMIT ?
                """, (rs, row) -> new ErrorGroupRow(rs.getString("normalized_code"),
                rs.getString("platform_category"), rs.getString("severity"), rs.getBoolean("retryable"),
                rs.getInt("total_count"), rs.getInt("tenant_count"), rs.getInt("channel_count"),
                timestamp(rs.getTimestamp("first_seen_at")), timestamp(rs.getTimestamp("last_seen_at"))),
                checked.tenantId(), checked.tenantId(), checked.errorCode(), checked.errorCode(),
                checked.channelId(), checked.channelId(), checked.startAt(), checked.startAt(),
                checked.endAt(), checked.endAt(), MAX_LIST_ROWS);
    }

    @Transactional
    public ActionResult resend(String messageId, ActionRequest request, String actor) {
        ActionRequest checked = request.checked();
        TaskRef task = taskByMessage(messageId);
        if (!"FAILED".equals(task.sendStatus())) {
            throw failure("MESSAGE_NOT_RETRYABLE", "只有失败消息可以重发");
        }
        String key = operationKey(checked.actionId(), "RESEND", task.messageId());
        ActionResult duplicate = existing(key);
        if (duplicate != null) return duplicate;
        recordOperation(key, checked.actionId(), "RESEND", task.messageId(), task.taskId(), null,
                actor(actor), checked.reason(), "REQUESTED", null, null, task.snapshot());
        try {
            DispatchTaskRecoveryService.RecoveryResult retry =
                    recovery.retryFailedTask(task.taskId(), actor(actor), checked.reason());
            complete(key, retry.action(), retry.newTaskId() == null ? "重发请求已记录" : "重发任务已创建:" + retry.newTaskId());
            return new ActionResult(checked.actionId(), "RESEND", task.messageId(), "COMPLETED",
                    retry.action(), retry.newTaskId() == null ? null : String.valueOf(retry.newTaskId()));
        } catch (RuntimeException ex) {
            fail(key, code(ex), ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public ActionResult appeal(String messageId, ActionRequest request, String actor) {
        ActionRequest checked = request.checked();
        TaskRef task = taskByMessage(messageId);
        String key = operationKey(checked.actionId(), "APPEAL", task.messageId());
        ActionResult duplicate = existing(key);
        if (duplicate != null) return duplicate;
        recordOperation(key, checked.actionId(), "APPEAL", task.messageId(), task.taskId(), null,
                actor(actor), checked.reason(), "COMPLETED", "APPEAL_RECORDED", "申诉证据已登记", task.snapshot());
        return new ActionResult(checked.actionId(), "APPEAL", task.messageId(), "COMPLETED",
                "APPEAL_RECORDED", null);
    }

    @Transactional
    public ActionResult correctReceipt(long receiptId, ReceiptCorrectionRequest request, String actor) {
        ReceiptCorrectionRequest checked = request.checked();
        ReceiptRef receipt = receiptById(receiptId);
        String key = operationKey(checked.actionId(), "RECEIPT_CORRECT", String.valueOf(receiptId));
        ActionResult duplicate = existing(key);
        if (duplicate != null) return duplicate;
        recordOperation(key, checked.actionId(), "RECEIPT_CORRECT", receipt.messageId(), receipt.taskId(),
                receipt.receiptId(), actor(actor), checked.reason(), "REQUESTED", null, null, receipt.snapshot());
        String digest = sha256(key + ":" + checked.status() + ":" + checked.errorCode());
        jdbc.update("""
                INSERT INTO delivery_reports(message_id, channel_id, upstream_msg_id, report_status,
                    error_code, raw_payload, receipt_digest, report_time)
                VALUES (?,?,?,?,?,?,?,?)
                """, receipt.messageId(), receipt.channelId(), receipt.upstreamMsgId(), checked.status(),
                checked.errorCode(), "CORRECTION originalReceipt=" + receiptId + "; reason=" + checked.reason()
                        + "; taxonomyVersion=" + checked.taxonomyVersion(), digest, LocalDateTime.now());
        applyFinalState(receipt.taskId(), checked.status(), checked.errorCode(), checked.reason());
        complete(key, "RECEIPT_CORRECTED", "回执纠正已应用，原始回执保留");
        return new ActionResult(checked.actionId(), "RECEIPT_CORRECT", receipt.messageId(), "COMPLETED",
                "RECEIPT_CORRECTED", digest);
    }

    @Transactional
    public ActionResult replayReceipt(long receiptId, ActionRequest request, String actor) {
        ActionRequest checked = request.checked();
        ReceiptRef receipt = receiptById(receiptId);
        String key = operationKey(checked.actionId(), "RECEIPT_REPLAY", String.valueOf(receiptId));
        ActionResult duplicate = existing(key);
        if (duplicate != null) return duplicate;
        recordOperation(key, checked.actionId(), "RECEIPT_REPLAY", receipt.messageId(), receipt.taskId(),
                receipt.receiptId(), actor(actor), checked.reason(), "REQUESTED", null, null, receipt.snapshot());
        HttpMessageDeliveryService.ReceiptResult result = delivery.applyReceipt(new HttpMessageDeliveryService.ReceiptCommand(
                receipt.messageId(), firstText(receipt.upstreamMsgId(), receipt.messageId()), receipt.channelId(),
                receipt.reportStatus(), receipt.errorCode(), checked.reason(), receipt.rawPayload(), LocalDateTime.now()));
        complete(key, "RECEIPT_REPLAYED", result.finalStatus());
        return new ActionResult(checked.actionId(), "RECEIPT_REPLAY", receipt.messageId(), "COMPLETED",
                "RECEIPT_REPLAYED", result.finalStatus());
    }

    @Transactional
    public BulkActionResult bulkErrors(BulkErrorActionRequest request, String actor) {
        BulkErrorActionRequest checked = request.checked();
        List<ActionResult> results = new ArrayList<>();
        for (String messageId : checked.messageIds()) {
            try {
                TaskRef task = taskByMessage(messageId);
                if (!"FAILED".equals(task.sendStatus()) || !checked.errorCode().equals(task.errorCode())) {
                    results.add(new ActionResult(checked.actionId(), checked.action(), messageId,
                            "FAILED", "MESSAGE_NOT_IN_ERROR_GROUP", null));
                    continue;
                }
                if ("BULK_RETRY".equals(checked.action())) {
                    results.add(resend(messageId, new ActionRequest(checked.actionId() + "-" + messageId,
                            checked.reason()), actor));
                } else {
                    String key = operationKey(checked.actionId(), "MARK_PROBLEM", messageId);
                    ActionResult duplicate = existing(key);
                    if (duplicate != null) {
                        results.add(duplicate);
                        continue;
                    }
                    recordOperation(key, checked.actionId(), "MARK_PROBLEM", messageId, task.taskId(), null,
                            actor(actor), checked.reason(), "COMPLETED", "PROBLEM_MARKED", "问题已标记", task.snapshot());
                    results.add(new ActionResult(checked.actionId(), "MARK_PROBLEM", messageId,
                            "COMPLETED", "PROBLEM_MARKED", null));
                }
            } catch (RuntimeException ex) {
                results.add(new ActionResult(checked.actionId(), checked.action(), messageId,
                        "FAILED", code(ex), ex.getMessage()));
            }
        }
        long completed = results.stream().filter(result -> "COMPLETED".equals(result.status())).count();
        return new BulkActionResult(checked.actionId(), checked.action(), results.size(), (int) completed,
                results.size() - (int) completed, results);
    }

    @Transactional
    public ActionResult exportRequest(OperationFilter filter, ActionRequest request, String actor) {
        OperationFilter checkedFilter = filter.checked();
        ActionRequest checked = request.checked();
        String target = checkedFilter.messageId() == null ? "snapshot" : checkedFilter.messageId();
        String key = operationKey(checked.actionId(), "EXPORT_REQUEST", target);
        ActionResult duplicate = existing(key);
        if (duplicate != null) return duplicate;
        int matchedRows = sends(checkedFilter).size() + receipts(checkedFilter).size() + submissions(checkedFilter).size();
        recordOperation(key, checked.actionId(), "EXPORT_REQUEST", checkedFilter.messageId(), null, null,
                actor(actor), checked.reason(), "COMPLETED", "EXPORT_REQUESTED",
                "导出请求已登记，匹配行数:" + matchedRows,
                "{\"matchedRows\":" + matchedRows + ",\"fileGenerationOwner\":\"secure-async-export\"}");
        return new ActionResult(checked.actionId(), "EXPORT_REQUEST", target, "COMPLETED",
                "EXPORT_REQUESTED", String.valueOf(matchedRows));
    }

    private void applyFinalState(long taskId, String status, String errorCode, String reason) {
        if ("DELIVERED".equals(status)) {
            jdbc.update("""
                    UPDATE message_tasks
                       SET send_status='DELIVERED', deliver_time=CURRENT_TIMESTAMP,
                           error_code=NULL, error_message=NULL
                     WHERE id=?
                    """, taskId);
        } else {
            jdbc.update("""
                    UPDATE message_tasks
                       SET send_status='FAILED', deliver_time=CURRENT_TIMESTAMP,
                           error_code=?, error_message=?
                     WHERE id=?
                    """, errorCode, reason, taskId);
        }
    }

    private TaskRef taskByMessage(String messageId) {
        String normalized = requireText(messageId, "MESSAGE_ID_REQUIRED", 64);
        return jdbc.query("""
                SELECT id, tenant_id, message_id, send_status, channel_id, error_code, error_message, version
                  FROM message_tasks
                 WHERE message_id=?
                 LIMIT 1
                """, rs -> {
            if (!rs.next()) throw failure("MESSAGE_TASK_NOT_FOUND", "消息任务不存在");
            return new TaskRef(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("message_id"),
                    rs.getString("send_status"), rs.getObject("channel_id", Long.class),
                    rs.getString("error_code"), rs.getString("error_message"), rs.getInt("version"));
        }, normalized);
    }

    private ReceiptRef receiptById(long receiptId) {
        if (receiptId <= 0) throw failure("RECEIPT_ID_REQUIRED", "回执ID不合法");
        return jdbc.query("""
                SELECT r.id, r.message_id, r.channel_id, r.upstream_msg_id, r.report_status, r.error_code,
                       r.raw_payload, r.report_time, t.id AS task_id, t.tenant_id, t.send_status
                  FROM delivery_reports r
                  JOIN message_tasks t ON t.message_id=r.message_id
                 WHERE r.id=?
                 LIMIT 1
                """, rs -> {
            if (!rs.next()) throw failure("RECEIPT_NOT_FOUND", "回执不存在");
            return new ReceiptRef(rs.getLong("id"), rs.getString("message_id"), rs.getObject("channel_id", Long.class),
                    rs.getString("upstream_msg_id"), rs.getString("report_status"), rs.getString("error_code"),
                    rs.getString("raw_payload"), timestamp(rs.getTimestamp("report_time")), rs.getLong("task_id"),
                    rs.getLong("tenant_id"), rs.getString("send_status"));
        }, receiptId);
    }

    private ActionResult existing(String operationKey) {
        return jdbc.query("""
                SELECT action_id, action_type, COALESCE(target_message_id, '') AS target_message_id,
                       status, result_code, result_message
                  FROM message_operation_events
                 WHERE operation_key=?
                 LIMIT 1
                """, (rs, row) -> new ActionResult(rs.getString("action_id"), rs.getString("action_type"),
                rs.getString("target_message_id"), "DUPLICATE".equals(rs.getString("status"))
                ? "DUPLICATE" : rs.getString("status"), rs.getString("result_code"),
                rs.getString("result_message")), operationKey).stream().findFirst().orElse(null);
    }

    private void recordOperation(String operationKey, String actionId, String actionType, String messageId, Long taskId,
                                 Long receiptId, String actor, String reason, String status, String resultCode,
                                 String resultMessage, String snapshotJson) {
        try {
            jdbc.update("""
                    INSERT INTO message_operation_events(operation_key, action_id, action_type, target_message_id,
                        target_task_id, target_receipt_id, actor, reason, status, result_code, result_message, snapshot_json)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, operationKey, actionId, actionType, messageId, taskId, receiptId, actor, reason,
                    status, resultCode, resultMessage, snapshotJson);
        } catch (DuplicateKeyException ignored) {
            // Caller returns the existing operation after this method.
        }
    }

    private void complete(String operationKey, String code, String message) {
        jdbc.update("""
                UPDATE message_operation_events
                   SET status='COMPLETED', result_code=?, result_message=?
                 WHERE operation_key=?
                """, code, message, operationKey);
    }

    private void fail(String operationKey, String code, String message) {
        jdbc.update("""
                UPDATE message_operation_events
                   SET status='FAILED', result_code=?, result_message=?
                 WHERE operation_key=?
                """, code, message, operationKey);
    }

    private static String operationKey(String actionId, String actionType, String target) {
        return requireText(actionId, "ACTION_ID_REQUIRED", 64) + ":" + actionType + ":"
                + requireText(target, "ACTION_TARGET_REQUIRED", 64);
    }

    private static String actor(String value) {
        return requireText(value, "ACTOR_REQUIRED", 64);
    }

    private static String requireText(String value, String code, int max) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank() || text.length() > max) throw failure(code, "文本不能为空且不能超过长度限制");
        return text;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        if (text.length() > max) throw failure("TEXT_TOO_LONG", "文本超过长度限制");
        return text;
    }

    private static LocalDateTime timestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String firstText(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private static String redactContent(String content) {
        if (content == null || content.isBlank()) return "";
        return content.length() <= 16 ? content : content.substring(0, 16) + "...";
    }

    private static String summarizePayload(String payload) {
        if (payload == null || payload.isBlank()) return "";
        String clean = payload.replaceAll("\\s+", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120) + "...";
    }

    private static String code(Throwable failure) {
        return failure instanceof BusinessException business ? business.getErrorCode() : failure.getClass().getSimpleName();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public record OperationFilter(Long tenantId, String messageId, String status, Long channelId, String errorCode,
                                  LocalDateTime startAt, LocalDateTime endAt) {
        OperationFilter checked() {
            return new OperationFilter(tenantId, optionalText(messageId, 64), optionalText(status, 32),
                    channelId, optionalText(errorCode, 64), startAt, endAt);
        }
    }

    public record SubmissionRow(long submissionId, long tenantId, String submitId, String messageId,
                                String sourceProtocol, String productType, String submissionStatus,
                                String sendStatus, Long templateId, Long signatureId, String errorCode,
                                String errorMessage, LocalDateTime createdAt) { }

    public record SendRow(long taskId, String messageId, long tenantId, Long submissionId, String maskedMobile,
                          String contentSummary, String sendStatus, Long channelId, String providerMessageId,
                          String carrier, String province, String city, String errorCode, String errorMessage,
                          java.math.BigDecimal cost, int retryCount, String outboxState, LocalDateTime sentAt,
                          LocalDateTime deliveredAt, LocalDateTime createdAt, int version) { }

    public record ReceiptRow(long receiptId, String messageId, Long tenantId, String maskedMobile, Long channelId,
                             String providerMessageId, String receiptStatus, String sendStatus, String errorCode,
                             String rawPayloadSummary, String receiptDigest, String carrier, String province,
                             String city, LocalDateTime reportTime) { }

    public record ErrorGroupRow(String normalizedCode, String platformCategory, String severity, boolean retryable,
                                int totalCount, int tenantCount, int channelCount, LocalDateTime firstSeenAt,
                                LocalDateTime lastSeenAt) { }

    public record ActionRequest(String actionId, String reason) {
        ActionRequest checked() {
            return new ActionRequest(requireText(actionId, "ACTION_ID_REQUIRED", 64),
                    requireText(reason, "ACTION_REASON_REQUIRED", 500));
        }
    }

    public record ReceiptCorrectionRequest(String actionId, String reason, String status, String errorCode,
                                           String taxonomyVersion) {
        ReceiptCorrectionRequest checked() {
            String checkedStatus = requireText(status, "RECEIPT_STATUS_REQUIRED", 16).toUpperCase(Locale.ROOT);
            if (!List.of("DELIVERED", "FAILED").contains(checkedStatus)) {
                throw failure("RECEIPT_STATUS_INVALID", "回执状态不支持");
            }
            String checkedError = "FAILED".equals(checkedStatus)
                    ? requireText(errorCode, "RECEIPT_ERROR_CODE_REQUIRED", 64)
                    : optionalText(errorCode, 64);
            return new ReceiptCorrectionRequest(requireText(actionId, "ACTION_ID_REQUIRED", 64),
                    requireText(reason, "ACTION_REASON_REQUIRED", 500), checkedStatus, checkedError,
                    requireText(taxonomyVersion, "TAXONOMY_VERSION_REQUIRED", 64));
        }
    }

    public record BulkErrorActionRequest(String actionId, String action, String errorCode, List<String> messageIds,
                                         String reason) {
        BulkErrorActionRequest checked() {
            String checkedAction = requireText(action, "BULK_ACTION_REQUIRED", 32).toUpperCase(Locale.ROOT);
            if (!List.of("BULK_RETRY", "MARK_PROBLEM").contains(checkedAction)) {
                throw failure("BULK_ACTION_INVALID", "批量动作不支持");
            }
            if (messageIds == null || messageIds.isEmpty() || messageIds.size() > MAX_BULK_SIZE) {
                throw failure("BULK_SELECTION_INVALID", "批量选择不能为空且不能超过限制");
            }
            return new BulkErrorActionRequest(requireText(actionId, "ACTION_ID_REQUIRED", 64), checkedAction,
                    requireText(errorCode, "ERROR_CODE_REQUIRED", 64),
                    messageIds.stream().map(id -> requireText(id, "MESSAGE_ID_REQUIRED", 64)).distinct().toList(),
                    requireText(reason, "ACTION_REASON_REQUIRED", 500));
        }
    }

    public record ActionResult(String actionId, String action, String target, String status,
                               String resultCode, String resultMessage) { }

    public record BulkActionResult(String actionId, String action, int total, int completed, int failed,
                                   List<ActionResult> results) { }

    private record TaskRef(long taskId, long tenantId, String messageId, String sendStatus, Long channelId,
                           String errorCode, String errorMessage, int version) {
        String snapshot() {
            return "{\"messageId\":\"" + messageId + "\",\"tenantId\":" + tenantId
                    + ",\"sendStatus\":\"" + sendStatus + "\",\"errorCode\":\""
                    + (errorCode == null ? "" : errorCode) + "\",\"version\":" + version + "}";
        }
    }

    private record ReceiptRef(long receiptId, String messageId, Long channelId, String upstreamMsgId,
                              String reportStatus, String errorCode, String rawPayload, LocalDateTime reportTime,
                              long taskId, long tenantId, String sendStatus) {
        String snapshot() {
            return "{\"receiptId\":" + receiptId + ",\"messageId\":\"" + messageId
                    + "\",\"tenantId\":" + tenantId + ",\"reportStatus\":\"" + reportStatus
                    + "\",\"sendStatus\":\"" + sendStatus + "\"}";
        }
    }
}
