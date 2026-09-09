package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageMobile;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageRouting;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Phase 25: focused recovery for paused-channel ready work and explicit failed-message retry. */
@Service
public class DispatchTaskRecoveryService {
    private final JdbcTemplate jdbc;
    private final ChannelRepository channels;
    private final MessageTaskRepository tasks;
    private final MessageTaskProtectionAdapter protection;

    public DispatchTaskRecoveryService(JdbcTemplate jdbc, ChannelRepository channels,
                                       MessageTaskRepository tasks,
                                       MessageTaskProtectionAdapter protection) {
        this.jdbc = jdbc;
        this.channels = channels;
        this.tasks = tasks;
        this.protection = protection;
    }

    @Transactional(readOnly = true)
    public List<MigrationInventoryRow> inventory() {
        return jdbc.query("""
                SELECT t.id AS task_id, t.message_id, t.tenant_id, t.channel_id, t.send_status,
                       c.channel_name, c.status AS channel_status, o.state AS outbox_state,
                       o.error_code AS outbox_error_code
                  FROM message_tasks t
             LEFT JOIN channels c ON c.id=t.channel_id
             LEFT JOIN message_send_outbox o ON o.task_id=t.id
                 WHERE t.send_status IN ('PENDING','FAILED') OR o.state='CLAIMED'
                 ORDER BY t.created_at, t.id
                 LIMIT 200
                """, (rs, row) -> new MigrationInventoryRow(
                rs.getLong("task_id"),
                rs.getString("message_id"),
                rs.getLong("tenant_id"),
                rs.getObject("channel_id", Long.class),
                rs.getString("channel_name"),
                rs.getString("channel_status"),
                rs.getString("send_status"),
                rs.getString("outbox_state"),
                rs.getString("outbox_error_code"),
                classify(rs.getString("send_status"), rs.getString("outbox_state"), rs.getString("channel_status"),
                        rs.getString("outbox_error_code"))));
    }

    @Transactional
    public RecoveryResult migrateReadyTask(long taskId, String actor, String evidence) {
        TaskRecoveryState task = taskState(taskId);
        requireActor(actor);
        requireEvidence(evidence);
        ExistingMigration existing = existingMigration(taskId);
        if (existing != null) {
            return new RecoveryResult(taskId, null, "MIGRATION_EXISTS", existing.channelId());
        }
        if (!"PENDING".equals(task.sendStatus()) || !"READY".equals(task.outboxState())) {
            throw failure("TASK_NOT_MIGRATABLE", "只有未派发的 READY/PENDING 任务可以迁移");
        }
        long fallback = fallbackChannel(task.channelId())
                .orElseGet(() -> noBackup(task, actor, evidence));
        jdbc.update("""
                UPDATE message_tasks SET channel_id=?, error_code=NULL, error_message=NULL
                 WHERE id=? AND send_status='PENDING'
                """, fallback, task.taskId());
        jdbc.update("""
                UPDATE message_send_outbox SET channel_id=?, updated_at=CURRENT_TIMESTAMP
                 WHERE task_id=? AND state='READY'
                """, fallback, task.taskId());
        recordEvent(task.taskId(), null, task.channelId(), fallback, "MIGRATED", actor, evidence);
        return new RecoveryResult(task.taskId(), null, "MIGRATED", fallback);
    }

    @Transactional
    public RecoveryResult retryFailedTask(long taskId, String actor, String evidence) {
        requireActor(actor);
        requireEvidence(evidence);
        TaskRecoveryState state = taskState(taskId);
        if (!"FAILED".equals(state.sendStatus())) {
            throw failure("TASK_NOT_RETRYABLE", "只有失败任务可以重试");
        }
        ExistingRetry existing = existingRetry(taskId);
        if (existing != null) {
            return new RecoveryResult(taskId, existing.newTaskId(), "RETRY_EXISTS", existing.channelId());
        }
        long fallback = fallbackChannel(state.channelId())
                .orElseGet(() -> noBackup(state, actor, evidence));
        MessageTask original = tasks.findById(taskId)
                .orElseThrow(() -> failure("MESSAGE_TASK_NOT_FOUND", "消息任务不存在"));
        String mobile = protection.revealMobileForDispatch(original);
        String messageId = "MSG_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        PreparedMessageRouting routing = protection.prepareForRouting(original.getTenantId(), messageId, mobile);
        PreparedMessageMobile prepared = protection.protectForPersistence(routing, mobile);
        MessageTask retry = new MessageTask();
        retry.setTenantId(original.getTenantId());
        retry.setMessageId(messageId);
        retry.setSubmitId(original.getSubmitId());
        retry.setTemplateId(original.getTemplateId());
        retry.setSignatureId(original.getSignatureId());
        retry.setContent(original.getContent());
        retry.setSendStatus(MessageTask.SendStatus.PENDING);
        retry.setChannelId(fallback);
        retry.setRetryCount((original.getRetryCount() == null ? 0 : original.getRetryCount()) + 1);
        MessageTask saved = protection.save(retry, prepared);
        jdbc.update("""
                INSERT INTO message_send_outbox(tenant_id, task_id, message_id, channel_id, state)
                VALUES (?, ?, ?, ?, 'READY')
                """, saved.getTenantId(), saved.getId(), saved.getMessageId(), fallback);
        recordEvent(taskId, saved.getId(), state.channelId(), fallback, "RETRY_CREATED", actor, evidence);
        return new RecoveryResult(taskId, saved.getId(), "RETRY_CREATED", fallback);
    }

    @Transactional
    public RecoveryTestResult recordRecoveryTest(long channelId, boolean success, String actor, String evidence) {
        requireActor(actor);
        requireEvidence(evidence);
        Channel channel = channels.findById(channelId)
                .orElseThrow(() -> failure("CHANNEL_NOT_FOUND", "通道不存在"));
        jdbc.update("""
                INSERT INTO channel_recovery_tests(channel_id, success, actor, evidence, tested_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, channel.getId(), success, actor.trim(), evidence.trim());
        return new RecoveryTestResult(channelId, success, success ? "RECOVERY_TEST_PASSED" : "RECOVERY_TEST_FAILED");
    }

    @Transactional
    public RecoveryTestResult resumeAfterRecovery(long channelId, String actor, String evidence) {
        requireActor(actor);
        requireEvidence(evidence);
        Channel channel = channels.findById(channelId)
                .orElseThrow(() -> failure("CHANNEL_NOT_FOUND", "通道不存在"));
        if (channel.getStatus() != Channel.Status.PAUSED) {
            throw failure("CHANNEL_NOT_PAUSED", "只有暂停通道可以恢复");
        }
        Boolean passed = jdbc.query("""
                SELECT success FROM channel_recovery_tests
                 WHERE channel_id=? ORDER BY id DESC LIMIT 1
                """, (rs, row) -> rs.getBoolean("success"), channelId).stream().findFirst().orElse(false);
        if (!Boolean.TRUE.equals(passed)) {
            throw failure("RECOVERY_TEST_REQUIRED", "恢复前必须有成功的小流量测试证据");
        }
        channel.setStatus(Channel.Status.NORMAL);
        channel.setPauseReason(null);
        channel.setPausedBy(null);
        channel.setPausedAt(null);
        channels.save(channel);
        jdbc.update("""
                INSERT INTO channel_pause_events(channel_id, event_type, trigger_type, actor, reason, source_event_key, created_at)
                VALUES (?, 'RESUME', 'RECOVERY_TEST', ?, ?, ?, CURRENT_TIMESTAMP)
                """, channelId, actor.trim(), evidence.trim(), "RECOVERY:" + channelId + ":" + System.nanoTime());
        return new RecoveryTestResult(channelId, true, "CHANNEL_RECOVERED");
    }

    private TaskRecoveryState taskState(long taskId) {
        return jdbc.query("""
                SELECT t.id, t.message_id, t.tenant_id, t.channel_id, t.send_status,
                       o.state AS outbox_state, o.error_code AS outbox_error_code
                  FROM message_tasks t
             LEFT JOIN message_send_outbox o ON o.task_id=t.id
                 WHERE t.id=?
                 LIMIT 1
                """, rs -> {
            if (!rs.next()) {
                throw failure("MESSAGE_TASK_NOT_FOUND", "消息任务不存在");
            }
            String outboxState = rs.getString("outbox_state");
            String outboxError = rs.getString("outbox_error_code");
            if ("CLAIMED".equals(outboxState) && outboxError != null && !outboxError.isBlank()) {
                throw failure("UNCERTAIN_OUTCOME_QUARANTINED", "上游结果不确定，不能自动迁移或重试");
            }
            return new TaskRecoveryState(rs.getLong("id"), rs.getString("message_id"),
                    rs.getLong("tenant_id"), rs.getObject("channel_id", Long.class),
                    rs.getString("send_status"), outboxState);
        }, taskId);
    }

    private Optional<Long> fallbackChannel(Long currentChannelId) {
        return channels.findAll().stream()
                .filter(Channel::isRoutable)
                .filter(channel -> !Long.valueOf(channel.getId()).equals(currentChannelId))
                .max(java.util.Comparator.comparingInt(Channel::getPriority))
                .map(Channel::getId);
    }

    private long noBackup(TaskRecoveryState task, String actor, String evidence) {
        recordEvent(task.taskId(), null, task.channelId(), null, "NO_BACKUP", actor, evidence);
        throw failure("NO_BACKUP_CHANNEL", "没有可用备用通道");
    }

    private ExistingRetry existingRetry(long originalTaskId) {
        return jdbc.query("""
                SELECT new_task_id, to_channel_id FROM dispatch_recovery_events
                 WHERE task_id=? AND action='RETRY_CREATED' AND new_task_id IS NOT NULL
                 ORDER BY id DESC LIMIT 1
                """, (rs, row) -> new ExistingRetry(rs.getLong("new_task_id"), rs.getLong("to_channel_id")),
                originalTaskId).stream().findFirst().orElse(null);
    }

    private ExistingMigration existingMigration(long originalTaskId) {
        return jdbc.query("""
                SELECT to_channel_id FROM dispatch_recovery_events
                 WHERE task_id=? AND action='MIGRATED' AND to_channel_id IS NOT NULL
                 ORDER BY id DESC LIMIT 1
                """, (rs, row) -> new ExistingMigration(rs.getLong("to_channel_id")),
                originalTaskId).stream().findFirst().orElse(null);
    }

    private void recordEvent(long taskId, Long newTaskId, Long fromChannelId, Long toChannelId,
                             String action, String actor, String evidence) {
        try {
            jdbc.update("""
                    INSERT INTO dispatch_recovery_events(task_id, new_task_id, from_channel_id, to_channel_id,
                        action, actor, evidence, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, taskId, newTaskId, fromChannelId, toChannelId, action, actor.trim(), evidence.trim(),
                    LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
            // Idempotent repeated recovery request.
        }
    }

    private static String classify(String sendStatus, String outboxState, String channelStatus, String errorCode) {
        if ("CLAIMED".equals(outboxState) && errorCode != null && !errorCode.isBlank()) {
            return "UNCERTAIN";
        }
        if ("PENDING".equals(sendStatus) && "READY".equals(outboxState)
                && ("PAUSED".equals(channelStatus) || "MAINTENANCE".equals(channelStatus) || "ABNORMAL".equals(channelStatus))) {
            return "MIGRATABLE";
        }
        if ("FAILED".equals(sendStatus)) {
            return "RETRYABLE";
        }
        return "OBSERVE";
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.trim().isEmpty()) {
            throw failure("RECOVERY_ACTOR_REQUIRED", "操作人不能为空");
        }
    }

    private static void requireEvidence(String evidence) {
        if (evidence == null || evidence.trim().isEmpty()) {
            throw failure("RECOVERY_EVIDENCE_REQUIRED", "恢复证据不能为空");
        }
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    private record TaskRecoveryState(long taskId, String messageId, long tenantId, Long channelId,
                                     String sendStatus, String outboxState) { }
    private record ExistingRetry(long newTaskId, long channelId) { }
    private record ExistingMigration(long channelId) { }

    public record MigrationInventoryRow(long taskId, String messageId, long tenantId, Long channelId,
                                        String channelName, String channelStatus, String sendStatus,
                                        String outboxState, String outboxErrorCode, String recoveryState) { }
    public record RecoveryResult(long originalTaskId, Long newTaskId, String action, Long channelId) { }
    public record RecoveryTestResult(long channelId, boolean success, String state) { }
}
