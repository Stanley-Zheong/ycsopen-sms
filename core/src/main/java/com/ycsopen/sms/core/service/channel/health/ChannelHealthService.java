package com.ycsopen.sms.core.service.channel.health;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.web.dto.ChannelHealthMonitorResponse;
import com.ycsopen.sms.core.web.dto.ChannelHealthObservationRequest;
import com.ycsopen.sms.core.web.dto.ChannelPauseRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ChannelHealthService {
    private static final BigDecimal FAILURE_THRESHOLD = new BigDecimal("0.1000");
    private static final BigDecimal TIMEOUT_THRESHOLD = new BigDecimal("0.1000");

    private final JdbcTemplate jdbc;
    private final ChannelRepository channels;
    private final ChannelCandidateEligibilityService eligibility;

    public ChannelHealthService(JdbcTemplate jdbc, ChannelRepository channels,
                                ChannelCandidateEligibilityService eligibility) {
        this.jdbc = jdbc;
        this.channels = channels;
        this.eligibility = eligibility;
    }

    @Transactional(readOnly = true)
    public List<ChannelHealthMonitorResponse> monitor() {
        return channels.findAll().stream()
                .map(this::monitorRow)
                .toList();
    }

    @Transactional
    public ChannelHealthMonitorResponse recordObservation(long channelId, ChannelHealthObservationRequest request) {
        Channel channel = channel(channelId);
        Sample sample = validateSample(request);
        LocalDateTime observedAt = LocalDateTime.now();
        var keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO channel_health_observations(channel_id, connected, timeout_rate, failure_rate,
                                                            average_latency_ms, result_status, reason_code, observed_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            statement.setLong(1, channelId);
            statement.setBoolean(2, sample.connected());
            statement.setBigDecimal(3, sample.timeoutRate());
            statement.setBigDecimal(4, sample.failureRate());
            statement.setLong(5, sample.averageLatencyMs());
            statement.setString(6, sample.resultStatus());
            statement.setString(7, sample.reasonCode());
            statement.setTimestamp(8, Timestamp.valueOf(observedAt));
            return statement;
        }, keyHolder);
        long observationId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        if (channel.getStatus() == Channel.Status.NORMAL && sample.failed() && previousSampleFailed(channelId)) {
            markMaintenanceForHealthFailure(channel, sample.reasonCode(), observationId);
        }
        return monitorRow(channels.findById(channelId).orElseThrow());
    }

    @Transactional
    public ChannelHealthMonitorResponse pause(long channelId, ChannelPauseRequest request) {
        ChannelPauseTrigger trigger = parseTrigger(request.trigger());
        String actor = required(request.actor(), "CHANNEL_PAUSE_ACTOR_REQUIRED", "暂停操作人不能为空");
        String reason = required(request.reason(), "CHANNEL_PAUSE_REASON_REQUIRED", "暂停原因不能为空");
        Channel channel = channel(channelId);
        if (channel.getStatus() == Channel.Status.OFFLINE) {
            throw new BusinessException("CHANNEL_OFFLINE_IMMUTABLE", "已下线通道不能暂停");
        }
        channel.setStatus(Channel.Status.PAUSED);
        channel.setPauseReason(reason);
        channel.setPausedBy(actor);
        channel.setPausedAt(LocalDateTime.now());
        channels.save(channel);
        insertPauseEvent(channelId, "PAUSE", trigger.name(), actor, reason,
                "PAUSE:" + channelId + ":" + trigger + ":" + System.nanoTime());
        return monitorRow(channel);
    }

    @Transactional
    public ChannelHealthMonitorResponse startMaintenance(long channelId, ChannelPauseRequest request) {
        String actor = required(request.actor(), "CHANNEL_MAINTENANCE_ACTOR_REQUIRED", "维护操作人不能为空");
        String reason = required(request.reason(), "CHANNEL_MAINTENANCE_REASON_REQUIRED", "维护原因不能为空");
        Channel channel = channel(channelId);
        if (channel.getStatus() != Channel.Status.NORMAL) {
            throw new BusinessException("CHANNEL_MAINTENANCE_STATE_INVALID", "只有正常通道可以进入维护");
        }
        channel.setStatus(Channel.Status.MAINTENANCE);
        channel.setPauseReason(reason);
        channel.setPausedBy(actor);
        channel.setPausedAt(LocalDateTime.now());
        channels.save(channel);
        insertPauseEvent(channelId, "MAINTENANCE_START", "MANUAL", actor, reason,
                "MAINTENANCE:" + channelId + ":START:" + System.nanoTime());
        return monitorRow(channel);
    }

    @Transactional
    public ChannelHealthMonitorResponse endMaintenance(long channelId, ChannelPauseRequest request) {
        String actor = required(request.actor(), "CHANNEL_MAINTENANCE_ACTOR_REQUIRED", "维护操作人不能为空");
        String reason = required(request.reason(), "CHANNEL_MAINTENANCE_REASON_REQUIRED", "维护结束原因不能为空");
        Channel channel = channel(channelId);
        if (channel.getStatus() != Channel.Status.MAINTENANCE) {
            throw new BusinessException("CHANNEL_MAINTENANCE_STATE_INVALID", "通道不在维护中");
        }
        if (!latestSampleSuccessful(channelId, channel.getPausedAt())) {
            throw new BusinessException("CHANNEL_MAINTENANCE_HEALTH_REQUIRED", "维护结束前必须通过健康验证");
        }
        channel.setStatus(Channel.Status.NORMAL);
        channel.setPauseReason(null);
        channel.setPausedBy(null);
        channel.setPausedAt(null);
        channels.save(channel);
        insertPauseEvent(channelId, "MAINTENANCE_END", "MANUAL", actor, reason, "MAINTENANCE:" + channelId + ":END:" + System.nanoTime());
        return monitorRow(channel);
    }

    @Transactional
    public ChannelHealthMonitorResponse resume(long channelId, String actor) {
        String resolvedActor = required(actor, "CHANNEL_RESUME_ACTOR_REQUIRED", "恢复操作人不能为空");
        Channel channel = channel(channelId);
        if (channel.getStatus() != Channel.Status.PAUSED) {
            throw new BusinessException("CHANNEL_RESUME_STATE_INVALID", "通道不在暂停中");
        }
        channel.setStatus(Channel.Status.NORMAL);
        channel.setPauseReason(null);
        channel.setPausedBy(null);
        channel.setPausedAt(null);
        channels.save(channel);
        insertPauseEvent(channelId, "RESUME", "MANUAL", resolvedActor, "恢复通道",
                "RESUME:" + channelId + ":" + System.nanoTime());
        return monitorRow(channel);
    }

    private void markMaintenanceForHealthFailure(Channel channel, String reasonCode, long observationId) {
        if (channel.getStatus() == Channel.Status.MAINTENANCE) {
            return;
        }
        String key = "HEALTH_FAILURE:" + channel.getId() + ":" + observationId;
        channel.setStatus(Channel.Status.MAINTENANCE);
        channel.setPauseReason(reasonCode);
        channel.setPausedBy("system:health");
        channel.setPausedAt(LocalDateTime.now());
        channels.save(channel);
        insertPauseEvent(channel.getId(), "HEALTH_FAILURE", "HEALTH", "system:health", reasonCode, key);
    }

    private boolean previousSampleFailed(long channelId) {
        List<Boolean> results = jdbc.query("""
                SELECT result_status FROM channel_health_observations
                 WHERE channel_id=? ORDER BY id DESC LIMIT 2
                """, (row, i) -> "FAILED".equals(row.getString("result_status")), channelId);
        return results.size() >= 2 && results.get(0) && results.get(1);
    }

    private boolean latestSampleSuccessful(long channelId, LocalDateTime notBefore) {
        if (notBefore == null) {
            return false;
        }
        return jdbc.query("""
                SELECT connected, result_status FROM channel_health_observations
                 WHERE channel_id=? AND observed_at>? ORDER BY id DESC LIMIT 1
                """, (row, i) -> row.getBoolean("connected") && "SUCCESS".equals(row.getString("result_status")),
                channelId, Timestamp.valueOf(notBefore)).stream().findFirst().orElse(false);
    }

    private void insertPauseEvent(long channelId, String type, String trigger, String actor, String reason, String key) {
        jdbc.update("""
                INSERT INTO channel_pause_events(channel_id, event_type, trigger_type, actor, reason, source_event_key, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, channelId, type, trigger, actor, reason, key, Timestamp.valueOf(LocalDateTime.now()));
    }

    private ChannelHealthMonitorResponse monitorRow(Channel channel) {
        var decision = eligibility.evaluate(channel);
        Latest latest = latest(channel.getId());
        long eventCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM channel_pause_events WHERE channel_id=?", Long.class, channel.getId());
        String state = switch (channel.getStatus()) {
            case PAUSED -> "PAUSED";
            case MAINTENANCE -> "MAINTENANCE";
            case ABNORMAL -> "FAILED";
            case OFFLINE -> "OFFLINE";
            case NORMAL -> latest.failed() ? "DEGRADED" : "HEALTHY";
        };
        return new ChannelHealthMonitorResponse(channel.getId(), channel.getChannelName(),
                channel.getProtocol().name(), channel.getOperator().name(), channel.getStatus().name(), state,
                latest.timeoutRate(), latest.failureRate(), latest.averageLatencyMs(), latest.reasonCode(),
                decision.eligible(), decision.reasonCode(), eventCount, channel.getPauseReason(),
                channel.getPausedBy(), channel.getPausedAt());
    }

    private Latest latest(long channelId) {
        return jdbc.query("""
                SELECT connected, timeout_rate, failure_rate, average_latency_ms, result_status, reason_code
                  FROM channel_health_observations WHERE channel_id=? ORDER BY id DESC LIMIT 1
                """, (row, i) -> new Latest(row.getBoolean("connected"), row.getBigDecimal("timeout_rate"),
                row.getBigDecimal("failure_rate"), row.getLong("average_latency_ms"),
                row.getString("result_status"), row.getString("reason_code")),
                channelId).stream().findFirst()
                .orElse(new Latest(true, BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4), 0L, "SUCCESS", "NO_SAMPLE"));
    }

    private Channel channel(long id) {
        return channels.findById(id)
                .orElseThrow(() -> new BusinessException("CHANNEL_NOT_FOUND", "通道不存在"));
    }

    private Sample validateSample(ChannelHealthObservationRequest request) {
        if (request == null) {
            throw new BusinessException("CHANNEL_HEALTH_SAMPLE_REQUIRED", "健康样本不能为空");
        }
        BigDecimal timeout = rate(request.timeoutRate(), "CHANNEL_TIMEOUT_RATE_INVALID");
        BigDecimal failure = rate(request.failureRate(), "CHANNEL_FAILURE_RATE_INVALID");
        Long latency = request.averageLatencyMs();
        if (latency == null || latency < 0) {
            throw new BusinessException("CHANNEL_LATENCY_INVALID", "平均时延不能为负");
        }
        if (request.connected() == null) {
            throw new BusinessException("CHANNEL_CONNECTED_REQUIRED", "连通状态不能为空");
        }
        boolean connected = request.connected();
        boolean failed = !connected || timeout.compareTo(TIMEOUT_THRESHOLD) >= 0 || failure.compareTo(FAILURE_THRESHOLD) >= 0;
        return new Sample(connected, timeout, failure, latency, failed ? "FAILED" : "SUCCESS",
                blankToDefault(request.reasonCode(), failed ? "HEALTH_CHECK_FAILED" : "OK"));
    }

    private static BigDecimal rate(BigDecimal value, String errorCode) {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(errorCode, "比例必须在0到1之间");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private static ChannelPauseTrigger parseTrigger(String value) {
        try {
            return ChannelPauseTrigger.valueOf(required(value, "CHANNEL_PAUSE_TRIGGER_REQUIRED", "暂停触发来源不能为空").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("CHANNEL_PAUSE_TRIGGER_INVALID", "暂停触发来源不合法");
        }
    }

    private static String required(String value, String code, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(code, message);
        }
        return value.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private record Latest(boolean connected, BigDecimal timeoutRate, BigDecimal failureRate,
                          long averageLatencyMs, String resultStatus, String reasonCode) {
        boolean failed() {
            return !connected || "FAILED".equals(resultStatus);
        }
    }

    private record Sample(boolean connected, BigDecimal timeoutRate, BigDecimal failureRate,
                          long averageLatencyMs, String resultStatus, String reasonCode) {
        boolean failed() {
            return "FAILED".equals(resultStatus);
        }
    }
}
