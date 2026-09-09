package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageMobile;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageRouting;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DispatchTaskRecoveryServiceTest {
    private JdbcTemplate jdbc;
    private ChannelRepository channels;
    private MessageTaskRepository tasks;
    private MessageTaskProtectionAdapter protection;
    private DispatchTaskRecoveryService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase25-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE channels(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  channel_name VARCHAR(255) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  availability VARCHAR(32),
                  effective_version_id BIGINT
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
                  error_code VARCHAR(64),
                  error_message VARCHAR(255),
                  retry_count INT DEFAULT 0,
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
                  error_code VARCHAR(64),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE dispatch_recovery_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  task_id BIGINT NOT NULL,
                  new_task_id BIGINT,
                  from_channel_id BIGINT,
                  to_channel_id BIGINT,
                  action VARCHAR(32) NOT NULL,
                  actor VARCHAR(64) NOT NULL,
                  evidence VARCHAR(500) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE channel_recovery_tests(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  channel_id BIGINT NOT NULL,
                  success BOOLEAN NOT NULL,
                  actor VARCHAR(64) NOT NULL,
                  evidence VARCHAR(500) NOT NULL,
                  tested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE channel_pause_events(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  channel_id BIGINT NOT NULL,
                  event_type VARCHAR(32) NOT NULL,
                  trigger_type VARCHAR(32) NOT NULL,
                  actor VARCHAR(64) NOT NULL,
                  reason VARCHAR(255) NOT NULL,
                  source_event_key VARCHAR(128),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        channels = mock(ChannelRepository.class);
        tasks = mock(MessageTaskRepository.class);
        protection = mock(MessageTaskProtectionAdapter.class);
        service = new DispatchTaskRecoveryService(jdbc, channels, tasks, protection);
    }

    @Test
    void migratesReadyPendingTaskToFallbackChannelOnce() {
        seedTask(91L, "MSG_1", "PENDING", 100L, "READY", null);
        when(channels.findAll()).thenReturn(List.of(channel(100L, Channel.Status.PAUSED, 10),
                channel(200L, Channel.Status.NORMAL, 90)));

        var result = service.migrateReadyTask(91L, "operator", "health failed");

        assertThat(result.action()).isEqualTo("MIGRATED");
        assertThat(result.channelId()).isEqualTo(200L);
        assertThat(jdbc.queryForObject("SELECT channel_id FROM message_tasks WHERE id=91", Long.class)).isEqualTo(200L);
        assertThat(jdbc.queryForObject("SELECT channel_id FROM message_send_outbox WHERE task_id=91", Long.class)).isEqualTo(200L);
        assertThat(jdbc.queryForObject("SELECT action FROM dispatch_recovery_events WHERE task_id=91", String.class)).isEqualTo("MIGRATED");

        var repeated = service.migrateReadyTask(91L, "operator", "health failed");

        assertThat(repeated.action()).isEqualTo("MIGRATION_EXISTS");
        assertThat(repeated.channelId()).isEqualTo(200L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dispatch_recovery_events WHERE task_id=91", Integer.class)).isEqualTo(1);
    }

    @Test
    void uncertainClaimedOutcomeCannotBeMigratedOrRetried() {
        seedTask(92L, "MSG_2", "PENDING", 100L, "CLAIMED", "PROVIDER_TIMEOUT");

        assertThatThrownBy(() -> service.migrateReadyTask(92L, "operator", "timeout"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("UNCERTAIN_OUTCOME_QUARANTINED");
    }

    @Test
    void noBackupRecordsEvidenceAndLeavesTaskOnOriginalChannel() {
        seedTask(93L, "MSG_3", "PENDING", 100L, "READY", null);
        when(channels.findAll()).thenReturn(List.of(channel(100L, Channel.Status.PAUSED, 10)));

        assertThatThrownBy(() -> service.migrateReadyTask(93L, "operator", "no route"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("NO_BACKUP_CHANNEL");

        assertThat(jdbc.queryForObject("SELECT channel_id FROM message_tasks WHERE id=93", Long.class)).isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT action FROM dispatch_recovery_events WHERE task_id=93", String.class)).isEqualTo("NO_BACKUP");
    }

    @Test
    void pausedChannelRequiresSuccessfulRecoveryTestBeforeResume() {
        Channel paused = channel(300L, Channel.Status.PAUSED, 10);
        when(channels.findById(300L)).thenReturn(Optional.of(paused));

        assertThatThrownBy(() -> service.resumeAfterRecovery(300L, "operator", "probe missing"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("RECOVERY_TEST_REQUIRED");

        service.recordRecoveryTest(300L, true, "operator", "sandbox accepted");
        var result = service.resumeAfterRecovery(300L, "operator", "sandbox accepted");

        assertThat(result.state()).isEqualTo("CHANNEL_RECOVERED");
        assertThat(paused.getStatus()).isEqualTo(Channel.Status.NORMAL);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM channel_pause_events WHERE channel_id=300", Integer.class)).isEqualTo(1);
    }

    @Test
    void retryFailedTaskCreatesNewPendingAttemptWithoutOverwritingOriginal() {
        seedTask(94L, "MSG_4", "FAILED", 100L, "FAILED", null);
        MessageTask original = originalTask(94L, "MSG_4", 100L);
        when(tasks.findById(94L)).thenReturn(Optional.of(original));
        when(channels.findAll()).thenReturn(List.of(channel(100L, Channel.Status.NORMAL, 10),
                channel(201L, Channel.Status.NORMAL, 99)));
        when(protection.revealMobileForDispatch(original)).thenReturn("13800138000");
        PreparedMessageRouting routing = mock(PreparedMessageRouting.class);
        PreparedMessageMobile prepared = mock(PreparedMessageMobile.class);
        when(protection.prepareForRouting(any(), any(), any())).thenReturn(routing);
        when(protection.protectForPersistence(routing, "13800138000")).thenReturn(prepared);
        when(protection.save(any(MessageTask.class), any(PreparedMessageMobile.class))).thenAnswer(invocation -> {
            MessageTask retry = invocation.getArgument(0);
            retry.setId(195L);
            return retry;
        });

        var result = service.retryFailedTask(94L, "operator", "receipt failed");

        assertThat(result.action()).isEqualTo("RETRY_CREATED");
        assertThat(result.newTaskId()).isEqualTo(195L);
        assertThat(jdbc.queryForObject("SELECT send_status FROM message_tasks WHERE id=94", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM message_send_outbox WHERE task_id=195", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT action FROM dispatch_recovery_events WHERE task_id=94", String.class)).isEqualTo("RETRY_CREATED");
    }

    private void seedTask(long taskId, String messageId, String sendStatus, long channelId,
                          String outboxState, String outboxError) {
        jdbc.update("""
                INSERT INTO message_tasks(id, tenant_id, message_id, content, send_status, channel_id)
                VALUES (?, 17, ?, '验证码 123456', ?, ?)
                """, taskId, messageId, sendStatus, channelId);
        jdbc.update("""
                INSERT INTO message_send_outbox(tenant_id, task_id, message_id, channel_id, state, error_code)
                VALUES (17, ?, ?, ?, ?, ?)
                """, taskId, messageId, channelId, outboxState, outboxError);
    }

    private static Channel channel(long id, Channel.Status status, int priority) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setChannelName("channel-" + id);
        channel.setProtocol(Channel.Protocol.HTTP);
        channel.setOperator(Channel.Operator.MOBILE);
        channel.setStatus(status);
        channel.setAvailability("AVAILABLE");
        channel.setEffectiveVersionId(1L);
        channel.setPriority(priority);
        channel.setPrice(new BigDecimal("0.0500"));
        return channel;
    }

    private static MessageTask originalTask(long id, String messageId, long channelId) {
        MessageTask task = new MessageTask();
        task.setId(id);
        task.setTenantId(17L);
        task.setSubmitId(10L);
        task.setTemplateId(20L);
        task.setSignatureId(30L);
        task.setMessageId(messageId);
        task.setContent("验证码 123456");
        task.setChannelId(channelId);
        task.setRetryCount(0);
        task.setSendStatus(MessageTask.SendStatus.FAILED);
        return task;
    }
}
