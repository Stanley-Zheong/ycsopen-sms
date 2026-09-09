package com.ycsopen.sms.core.service.bulk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Phase 29: bulk API, import preview, scheduled task state, and task operations. */
@Service
public class BulkScheduledTaskService {
    private static final int MAX_ITEMS = 500;
    private static final long MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final MessageSubmitService messageSubmitService;
    private final ObjectMapper json;

    public BulkScheduledTaskService(JdbcTemplate jdbc, MessageSubmitService messageSubmitService, ObjectMapper json) {
        this.jdbc = jdbc;
        this.messageSubmitService = messageSubmitService;
        this.json = json;
    }

    public PreviewResult preview(long tenantId, BulkCommand command) {
        BulkCommand checked = command.checked();
        List<ValidatedRow> validatedRows = validateRows(checked);
        List<PreviewRow> rows = previewRows(validatedRows);
        long valid = validatedRows.stream().filter(row -> "VALID".equals(row.validationStatus())).count();
        return new PreviewResult(tenantId, checked.batchKey(), checked.taskName(), checked.sourceFileName(),
                rows.size(), (int) valid, rows.size() - (int) valid, rows);
    }

    public BulkTaskView create(long tenantId, BulkCommand command, String clientIp, String actor) {
        BulkCommand checked = command.checked();
        List<ValidatedRow> rows = validateRows(checked);
        long bulkId;
        try {
            bulkId = insertBulk(tenantId, checked, rows, actor);
        } catch (DuplicateKeyException duplicate) {
            return taskByBatchKey(tenantId, checked.batchKey());
        }
        if (checked.scheduleAt() != null) {
            return task(bulkId);
        }
        try {
            for (ValidatedRow row : rows) {
                if (!"VALID".equals(row.validationStatus())) {
                    continue;
                }
                String submitId = checked.batchKey() + "-" + row.rowNo();
                var response = messageSubmitService.submit(tenantId, null, new SmsSendRequest(
                        submitId,
                        row.phoneNumber(),
                        checked.templateId(),
                        checked.signId(),
                        row.variables(),
                        null), clientIp);
                insertValidItem(tenantId, bulkId, row, response.messageId(), submitId);
            }
            return reconcile(bulkId);
        } catch (RuntimeException failure) {
            markFailed(bulkId, failure);
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public List<BulkTaskView> tenantTasks(long tenantId) {
        return tasks("WHERE tenant_id=?", tenantId);
    }

    @Transactional(readOnly = true)
    public List<BulkTaskView> adminTasks(Long tenantId, String state) {
        if (tenantId != null && state != null && !state.isBlank()) {
            return tasks("WHERE tenant_id=? AND task_status=?", tenantId, state.trim().toUpperCase(Locale.ROOT));
        }
        if (tenantId != null) {
            return tasks("WHERE tenant_id=?", tenantId);
        }
        if (state != null && !state.isBlank()) {
            return tasks("WHERE task_status=?", state.trim().toUpperCase(Locale.ROOT));
        }
        return tasks("", new Object[0]);
    }

    @Transactional(readOnly = true)
    public BulkTaskDetail detail(long bulkId) {
        BulkTaskView task = task(bulkId);
        List<BulkItemView> items = jdbc.query("""
                SELECT id, item_tracking_id, row_no, message_id, send_status, validation_status,
                       validation_reason, cost, updated_at
                  FROM bulk_sending_items
                 WHERE bulk_id=?
                 ORDER BY row_no, id
                """, (rs, row) -> new BulkItemView(
                rs.getLong("id"), rs.getString("item_tracking_id"), rs.getInt("row_no"),
                rs.getString("message_id"), rs.getString("send_status"), rs.getString("validation_status"),
                rs.getString("validation_reason"), rs.getBigDecimal("cost"), rs.getTimestamp("updated_at").toLocalDateTime()), bulkId);
        return new BulkTaskDetail(task, items);
    }

    @Transactional
    public BulkTaskView controlForTenant(long tenantId, long bulkId, String action, String actor, String reason) {
        BulkTaskView task = task(bulkId);
        if (task.tenantId() != tenantId) {
            throw new BusinessException("BULK_TASK_NOT_FOUND", "批任务不存在");
        }
        return control(bulkId, action, actor, reason);
    }

    @Transactional
    public BulkTaskView control(long bulkId, String action, String actor, String reason) {
        String checkedAction = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String checkedReason = reason == null ? "" : reason.trim();
        if (checkedReason.isBlank()) {
            throw new BusinessException("BULK_ACTION_REASON_REQUIRED", "操作原因不能为空");
        }
        BulkTaskView before = task(bulkId);
        switch (checkedAction) {
            case "PAUSE" -> requireState(before.state(), "PENDING", "RUNNING");
            case "RESUME" -> requireState(before.state(), "PAUSED");
            case "CANCEL" -> requireState(before.state(), "PENDING", "RUNNING", "PAUSED");
            case "RESTART" -> requireState(before.state(), "FAILED");
            case "FAIL" -> requireState(before.state(), "RUNNING");
            default -> throw new BusinessException("BULK_ACTION_INVALID", "不支持的批任务动作");
        }
        if ("PAUSE".equals(checkedAction)) {
            jdbc.update("UPDATE bulk_sendings SET task_status='PAUSED', control_reason=? WHERE id=?",
                    checkedReason, bulkId);
        } else if ("RESUME".equals(checkedAction)) {
            jdbc.update("UPDATE bulk_sendings SET task_status='RUNNING', control_reason=? WHERE id=?",
                    checkedReason, bulkId);
        } else if ("CANCEL".equals(checkedAction)) {
            jdbc.update("UPDATE bulk_sending_items SET send_status='CANCELLED' WHERE bulk_id=? AND send_status='PENDING'", bulkId);
            jdbc.update("UPDATE bulk_sendings SET task_status='CANCELLED', control_reason=?, end_time=CURRENT_TIMESTAMP WHERE id=?",
                    checkedReason, bulkId);
        } else if ("RESTART".equals(checkedAction)) {
            jdbc.update("UPDATE bulk_sending_items SET send_status='PENDING' WHERE bulk_id=? AND send_status IN ('FAILED','CANCELLED')", bulkId);
            jdbc.update("UPDATE bulk_sendings SET task_status='RUNNING', failure_reason=NULL, control_reason=?, start_time=COALESCE(start_time, CURRENT_TIMESTAMP), end_time=NULL WHERE id=?",
                    checkedReason, bulkId);
        } else {
            jdbc.update("UPDATE bulk_sendings SET task_status='FAILED', failure_reason=?, control_reason=?, end_time=CURRENT_TIMESTAMP WHERE id=?",
                    checkedReason, checkedReason, bulkId);
        }
        return reconcile(bulkId);
    }

    @Transactional
    public BulkTaskView reconcile(long bulkId) {
        Counts counts = jdbc.query("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN send_status IN ('SENT','DELIVERED') THEN 1 ELSE 0 END) AS completed,
                       SUM(CASE WHEN send_status='PENDING' THEN 1 ELSE 0 END) AS running,
                       SUM(CASE WHEN send_status='FAILED' THEN 1 ELSE 0 END) AS failed,
                       SUM(CASE WHEN send_status='CANCELLED' THEN 1 ELSE 0 END) AS cancelled,
                       COALESCE(SUM(cost), 0) AS cost
                  FROM bulk_sending_items
                 WHERE bulk_id=?
                """, rs -> {
            if (!rs.next()) {
                return new Counts(0, 0, 0, 0, 0, java.math.BigDecimal.ZERO);
            }
            return new Counts(rs.getInt("total"), rs.getInt("completed"), rs.getInt("running"),
                    rs.getInt("failed"), rs.getInt("cancelled"), rs.getBigDecimal("cost"));
        }, bulkId);
        BulkTaskView before = task(bulkId);
        String state = before.state();
        if (!List.of("PAUSED", "FAILED", "CANCELLED").contains(state)
                && counts.total() > 0 && counts.running() == 0) {
            state = counts.failed() > 0 ? "FAILED" : "COMPLETED";
        }
        jdbc.update("""
                UPDATE bulk_sendings
                   SET task_status=?, success_count=?, fail_count=invalid_count+?, running_count=?,
                       completed_count=?, cancelled_count=?, total_cost=?, end_time=CASE WHEN ? IN ('COMPLETED','FAILED','CANCELLED') THEN CURRENT_TIMESTAMP ELSE end_time END
                 WHERE id=?
                """, state, counts.completed(), counts.failed(), counts.running(),
                counts.completed(), counts.cancelled(), counts.cost(), state, bulkId);
        return task(bulkId);
    }

    private long insertBulk(long tenantId, BulkCommand command, List<ValidatedRow> rows, String actor) {
        int valid = (int) rows.stream().filter(row -> "VALID".equals(row.validationStatus())).count();
        int invalid = rows.size() - valid;
        String state = valid == 0 ? "FAILED" : command.scheduleAt() == null ? "RUNNING" : "PENDING";
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bulk_sendings(tenant_id, batch_key, task_name, message_type, priority,
                        template_id, signature_id, total_count, valid_count, invalid_count, fail_count,
                        task_status, schedule_time, start_time, end_time, created_by, source_file_name,
                        failure_reason, import_snapshot_json)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            statement.setLong(1, tenantId);
            statement.setString(2, command.batchKey());
            statement.setString(3, command.taskName());
            statement.setString(4, command.messageType());
            statement.setString(5, command.priority());
            statement.setLong(6, Long.parseLong(command.templateId()));
            statement.setLong(7, Long.parseLong(command.signId()));
            statement.setInt(8, rows.size());
            statement.setInt(9, valid);
            statement.setInt(10, invalid);
            statement.setInt(11, invalid);
            statement.setString(12, state);
            statement.setObject(13, command.scheduleAt());
            statement.setObject(14, "RUNNING".equals(state) ? LocalDateTime.now() : null);
            statement.setObject(15, "FAILED".equals(state) ? LocalDateTime.now() : null);
            statement.setString(16, actor);
            statement.setString(17, command.sourceFileName());
            statement.setString(18, "FAILED".equals(state) ? "无有效导入行" : null);
            statement.setString(19, json(previewRows(rows)));
            return statement;
        }, key);
        return key.getKey().longValue();
    }

    private void insertValidItem(long tenantId, long bulkId, ValidatedRow row, String messageId, String externalSubmitId) {
        Integer inserted = jdbc.update("""
                INSERT INTO bulk_sending_items(bulk_id, tenant_id, item_tracking_id, row_no, mobile_encrypted,
                    mobile_hash, message_id, message_task_id, submit_id, template_params, send_status,
                    validation_status, validation_reason, cost)
                SELECT ?, ?, ?, ?, mt.mobile_encrypted, mt.mobile_hash, mt.message_id, mt.id, mt.submit_id, ?,
                       mt.send_status, 'VALID', NULL, mt.cost
                  FROM message_tasks mt
                  JOIN message_submits ms ON ms.id=mt.submit_id
                 WHERE mt.tenant_id=? AND mt.message_id=? AND ms.submit_id=?
                """, bulkId, tenantId, itemTrackingId(bulkId, row.rowNo()), row.rowNo(),
                json(row.variables()), tenantId, messageId, externalSubmitId);
        if (inserted == null || inserted != 1) {
            throw new BusinessException("BULK_ITEM_LINK_FAILED", "批量子项未能关联单发任务");
        }
    }

    private void markFailed(long bulkId, RuntimeException failure) {
        String reason = failure instanceof BusinessException ? failure.getMessage() : "批量提交失败";
        jdbc.update("""
                UPDATE bulk_sendings
                   SET task_status='FAILED', failure_reason=?, end_time=CURRENT_TIMESTAMP
                 WHERE id=? AND task_status NOT IN ('COMPLETED','CANCELLED')
                """, reason, bulkId);
    }

    private BulkTaskView taskByBatchKey(long tenantId, String batchKey) {
        return jdbc.query("SELECT * FROM bulk_sendings WHERE tenant_id=? AND batch_key=?",
                rs -> rs.next() ? view(rs) : null, tenantId, batchKey);
    }

    private BulkTaskView task(long bulkId) {
        BulkTaskView view = jdbc.query("SELECT * FROM bulk_sendings WHERE id=?",
                rs -> rs.next() ? view(rs) : null, bulkId);
        if (view == null) {
            throw new BusinessException("BULK_TASK_NOT_FOUND", "批任务不存在");
        }
        return view;
    }

    private List<BulkTaskView> tasks(String where, Object... args) {
        return jdbc.query("""
                SELECT * FROM bulk_sendings %s ORDER BY created_at DESC, id DESC LIMIT 200
                """.formatted(where), (rs, row) -> view(rs), args);
    }

    private BulkTaskView view(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new BulkTaskView(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("batch_key"),
                rs.getString("task_name"), rs.getString("message_type"), rs.getString("priority"),
                rs.getInt("total_count"), rs.getInt("valid_count"), rs.getInt("invalid_count"),
                rs.getInt("running_count"), rs.getInt("completed_count"), rs.getInt("fail_count"),
                rs.getInt("cancelled_count"), rs.getBigDecimal("total_cost"), rs.getString("task_status"),
                rs.getTimestamp("schedule_time") == null ? null : rs.getTimestamp("schedule_time").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("failure_reason"),
                rs.getString("control_reason"));
    }

    private List<ValidatedRow> validateRows(BulkCommand command) {
        if (command.items().size() > MAX_ITEMS) {
            throw new BusinessException("BULK_LIMIT_EXCEEDED", "批量条数超过限制");
        }
        if (command.sizeBytes() == null || command.sizeBytes() <= 0) {
            throw new BusinessException("BULK_FILE_SIZE_REQUIRED", "上传文件大小不能为空");
        }
        if (command.sizeBytes() > MAX_UPLOAD_BYTES) {
            throw new BusinessException("BULK_FILE_TOO_LARGE", "上传文件超过限制");
        }
        String lower = command.sourceFileName().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".csv") || lower.endsWith(".xlsx"))) {
            throw new BusinessException("BULK_FILE_TYPE_INVALID", "仅支持 CSV 或 Excel 文件");
        }
        if (command.malwareVerdict() == null || command.malwareVerdict().isBlank()) {
            throw new BusinessException("BULK_FILE_SCAN_REQUIRED", "上传文件缺少安全扫描结果");
        }
        if (!"CLEAN".equalsIgnoreCase(command.malwareVerdict())) {
            throw new BusinessException("BULK_FILE_MALWARE_REJECTED", "上传文件未通过安全扫描");
        }
        Set<String> seen = new HashSet<>();
        List<ValidatedRow> rows = new ArrayList<>();
        int rowNo = 1;
        for (BulkItemCommand item : command.items()) {
            String phone = item.phoneNumber() == null ? "" : item.phoneNumber().trim();
            String status = "VALID";
            String reason = null;
            if (!phone.matches("1[3-9][0-9]{9}")) {
                status = "INVALID";
                reason = "手机号格式不合法";
            } else if (!seen.add(phone)) {
                status = "INVALID";
                reason = "重复手机号";
            }
            rows.add(new ValidatedRow(rowNo++, phone, mask(phone), item.variables() == null ? Map.of() : item.variables(), status, reason));
        }
        return rows;
    }

    private static List<PreviewRow> previewRows(List<ValidatedRow> rows) {
        return rows.stream()
                .map(row -> new PreviewRow(row.rowNo(), row.maskedPhone(), row.variables(),
                        row.validationStatus(), row.validationReason()))
                .toList();
    }

    private static void requireState(String actual, String... allowed) {
        for (String state : allowed) {
            if (state.equals(actual)) {
                return;
            }
        }
        throw new BusinessException("BULK_ACTION_STATE_INVALID", "当前状态不允许执行该操作");
    }

    private String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("BULK_JSON_UNAVAILABLE", ex);
        }
    }

    private static String itemTrackingId(long bulkId, int rowNo) {
        return "BIT-" + bulkId + "-" + rowNo;
    }

    private static String mask(String phone) {
        return phone.matches("1[3-9][0-9]{9}") ? phone.substring(0, 3) + "****" + phone.substring(7) : "INVALID";
    }

    public record BulkCommand(String batchKey, String taskName, String messageType, String priority,
                              String templateId, String signId, LocalDateTime scheduleAt,
                              String sourceFileName, Long sizeBytes, String malwareVerdict,
                              List<BulkItemCommand> items) {
        BulkCommand checked() {
            String checkedBatch = text(batchKey, "BULK_BATCH_KEY_REQUIRED");
            if (!checkedBatch.matches("[A-Za-z0-9_.:-]{1,64}")) {
                throw new BusinessException("BULK_BATCH_KEY_INVALID", "批量流水号不合法");
            }
            String checkedType = value(messageType, "NOTIFY").toUpperCase(Locale.ROOT);
            String checkedPriority = value(priority, "NORMAL").toUpperCase(Locale.ROOT);
            if (!List.of("VERIFY", "NOTIFY", "MARKETING").contains(checkedType)
                    || !List.of("LOW", "NORMAL", "HIGH").contains(checkedPriority)
                    || !number(templateId) || !number(signId)) {
                throw new BusinessException("BULK_COMMAND_INVALID", "批量任务参数不合法");
            }
            List<BulkItemCommand> checkedItems = items == null ? List.of() : items;
            if (checkedItems.isEmpty()) {
                throw new BusinessException("BULK_ITEMS_REQUIRED", "批量任务至少需要一条明细");
            }
            return new BulkCommand(checkedBatch, text(taskName, "BULK_TASK_NAME_REQUIRED"), checkedType,
                    checkedPriority, templateId.trim(), signId.trim(), scheduleAt,
                    text(sourceFileName, "BULK_SOURCE_FILE_REQUIRED"), sizeBytes, malwareVerdict, checkedItems);
        }
    }

    private static String text(String value, String code) {
        if (value == null || value.trim().isBlank()) {
            throw new BusinessException(code, "字段不能为空");
        }
        return value.trim();
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static boolean number(String value) {
        return value != null && value.matches("[1-9][0-9]{0,17}");
    }

    private record Counts(int total, int completed, int running, int failed, int cancelled, java.math.BigDecimal cost) { }

    public record BulkItemCommand(String phoneNumber, Map<String, String> variables) { }
    private record ValidatedRow(int rowNo, String phoneNumber, String maskedPhone, Map<String, String> variables,
                                String validationStatus, String validationReason) { }
    public record PreviewRow(int rowNo, String maskedPhone, Map<String, String> variables,
                             String validationStatus, String validationReason) { }
    public record PreviewResult(long tenantId, String batchKey, String taskName, String sourceFileName,
                                int total, int valid, int invalid, List<PreviewRow> rows) { }
    public record BulkTaskView(long bulkId, long tenantId, String batchKey, String taskName, String messageType,
                               String priority, int totalCount, int validCount, int invalidCount, int runningCount,
                               int completedCount, int failCount, int cancelledCount, java.math.BigDecimal totalCost,
                               String state, LocalDateTime scheduleAt, LocalDateTime createdAt, String failureReason,
                               String controlReason) { }
    public record BulkItemView(long itemId, String itemTrackingId, int rowNo, String messageId, String sendStatus,
                               String validationStatus, String validationReason, java.math.BigDecimal cost,
                               LocalDateTime updatedAt) { }
    public record BulkTaskDetail(BulkTaskView task, List<BulkItemView> items) { }
}
