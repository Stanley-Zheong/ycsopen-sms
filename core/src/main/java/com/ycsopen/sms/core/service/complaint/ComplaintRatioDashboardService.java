package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/** Phase45 complaint-ratio dashboard read model and explicit intervention boundary. */
@Service
public class ComplaintRatioDashboardService {
    private static final String SOURCE_REGISTRY = "complaint_ratio_stats:message_tasks:complaints";
    private static final String THRESHOLD_VERSION = "default-0.003-v1";

    private final JdbcTemplate jdbc;
    private final BigDecimal threshold;

    public ComplaintRatioDashboardService(
            JdbcTemplate jdbc,
            @Value("${ycsopen.routing.complaint-ratio-threshold:0.003}") BigDecimal threshold) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.threshold = threshold == null ? new BigDecimal("0.003") : threshold;
    }

    @Transactional(readOnly = true)
    public List<RatioRow> ranking(String dimension, YearMonth month, Integer topN, boolean includeAll) {
        String type = dimensionType(dimension);
        int limit = normalizeLimit(topN);
        String sql = """
                SELECT stats.stat_month, stats.dimension_type, stats.dimension_id,
                       COALESCE(channel.channel_name, tenant.short_name, CONCAT(stats.dimension_type, ':', stats.dimension_id)) AS dimension_name,
                       stats.send_count, stats.complaint_count, stats.ratio, stats.over_threshold,
                       COALESCE(stats.threshold_config_version, ?) AS threshold_config_version,
                       COALESCE(stats.data_quality, 'UNKNOWN') AS data_quality,
                       COALESCE(stats.source_registry, ?) AS source_registry,
                       stats.calculated_at
                  FROM complaint_ratio_stats stats
             LEFT JOIN channels channel
                    ON stats.dimension_type='CHANNEL' AND stats.dimension_id=channel.id
             LEFT JOIN tenants tenant
                    ON stats.dimension_type='TENANT' AND stats.dimension_id=tenant.id
                 WHERE stats.stat_month=? AND stats.dimension_type=?
                 ORDER BY stats.over_threshold DESC, stats.ratio DESC, stats.dimension_id ASC
                """ + (includeAll ? "" : " LIMIT ?");
        Object[] args = includeAll
                ? new Object[]{THRESHOLD_VERSION, SOURCE_REGISTRY, month.toString(), type}
                : new Object[]{THRESHOLD_VERSION, SOURCE_REGISTRY, month.toString(), type, limit};
        return jdbc.query(sql, (rs, rowNum) -> ratioRow(rs, rowNum + 1, month), args);
    }

    @Transactional(readOnly = true)
    public List<ComplaintCaseRow> drilldown(String dimension, long dimensionId, YearMonth month) {
        String column = switch (dimensionType(dimension)) {
            case "CHANNEL" -> "channel_id";
            case "TENANT" -> "tenant_id";
            default -> throw failure("COMPLAINT_RATIO_DIMENSION_INVALID", "投诉占比维度无效");
        };
        LocalDateTime start = month.atDay(1).atStartOfDay();
        LocalDateTime end = month.plusMonths(1).atDay(1).atStartOfDay();
        return jdbc.query("""
                SELECT id, source, tenant_id, channel_id, message_id, summary, status,
                       COALESCE(attribution_quality, 'UNKNOWN') AS attribution_quality, created_at
                  FROM complaints
                 WHERE %s=? AND created_at>=? AND created_at<?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 100
                """.formatted(column), (rs, row) -> new ComplaintCaseRow(
                rs.getLong("id"),
                rs.getString("source"),
                nullableLong(rs, "tenant_id"),
                nullableLong(rs, "channel_id"),
                rs.getString("message_id"),
                rs.getString("summary"),
                rs.getString("status"),
                rs.getString("attribution_quality"),
                timestamp(rs, "created_at")
        ), dimensionId, Timestamp.valueOf(start), Timestamp.valueOf(end));
    }

    @Transactional
    public InterventionResult pause(String dimension, long dimensionId, YearMonth month, InterventionCommand command, String actor) {
        String type = dimensionType(dimension);
        RatioRow row = requireActionable(type, dimensionId, month);
        String reason = requiredReason(command);
        String sourceKey = sourceKey(type, dimensionId, month, row.thresholdConfigVersion());
        if ("CHANNEL".equals(type)) {
            return pauseChannel(dimensionId, reason, sourceKey, actor(actor));
        }
        return pauseTenant(dimensionId, row, reason, sourceKey, actor(actor));
    }

    private InterventionResult pauseChannel(long channelId, String reason, String sourceKey, String actor) {
        lockChannel(channelId);
        List<InterventionResult> existing = jdbc.query("""
                SELECT id, channel_id
                  FROM channel_pause_events
                 WHERE source_event_key=?
                """, (rs, row) -> new InterventionResult("CHANNEL", channelId, "PAUSED",
                rs.getLong("id"), null, sourceKey, "PAUSE"), sourceKey);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        requireUpdated(jdbc.update("""
                UPDATE channels
                   SET status='PAUSED', pause_reason=?, paused_by=?, paused_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status<>'OFFLINE'
                """, reason, actor, channelId));
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO channel_pause_events(channel_id, event_type, trigger_type, actor, reason, source_event_key, created_at)
                    VALUES (?, 'PAUSE', 'RATIO', ?, ?, ?, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setLong(1, channelId);
            ps.setString(2, actor);
            ps.setString(3, reason);
            ps.setString(4, sourceKey);
            return ps;
        }, key);
        return new InterventionResult("CHANNEL", channelId, "PAUSED",
                Objects.requireNonNull(key.getKey()).longValue(), null, sourceKey, "PAUSE");
    }

    private InterventionResult pauseTenant(long tenantId, RatioRow row, String reason, String sourceKey, String actor) {
        String before = lockTenant(tenantId);
        List<InterventionResult> existing = jdbc.query("""
                SELECT id, alert_record_id
                  FROM tenant_risk_episodes
                 WHERE source_key=?
                """, (rs, index) -> new InterventionResult("TENANT", tenantId, "PAUSED",
                rs.getLong("id"), nullableLong(rs, "alert_record_id"), sourceKey, "AUTO_SUSPEND"), sourceKey);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        long alertId = createAlert(tenantId, row, sourceKey, reason);
        requireUpdated(jdbc.update("UPDATE tenants SET lifecycle_status='FROZEN' WHERE id=?", tenantId));
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO tenant_risk_episodes(tenant_id, rule_id, alert_record_id, metric, source_key,
                        source_registry, numerator, denominator, rate, threshold_value, window_minutes, data_quality,
                        action, status, before_lifecycle_status, source_snapshot, paused_by, paused_at)
                    VALUES (?, 0, ?, 'COMPLAINT_RATE', ?, ?, ?, ?, ?, ?, 43200, 'COMPLETE',
                            'AUTO_SUSPEND', 'PAUSED', ?, ?, ?, CURRENT_TIMESTAMP)
                    """, new String[]{"id"});
            ps.setLong(1, tenantId);
            ps.setLong(2, alertId);
            ps.setString(3, sourceKey);
            ps.setString(4, SOURCE_REGISTRY);
            ps.setLong(5, row.complaintCount());
            ps.setLong(6, row.sendCount());
            ps.setBigDecimal(7, row.ratio());
            ps.setBigDecimal(8, row.thresholdValue());
            ps.setString(9, before);
            ps.setString(10, snapshot(row, reason));
            ps.setString(11, actor);
            return ps;
        }, key);
        return new InterventionResult("TENANT", tenantId, "PAUSED",
                Objects.requireNonNull(key.getKey()).longValue(), alertId, sourceKey, "AUTO_SUSPEND");
    }

    private long createAlert(long tenantId, RatioRow row, String sourceKey, String reason) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO alert_records(rule_id, title, content, metric_value, status, severity, source_module,
                        source_key, impact_scope, delivery_state)
                    VALUES (0, ?, ?, ?, 'ACTIVE', 'HIGH', 'COMPLAINT_RATIO', ?, ?, 'DELIVERED')
                    """, new String[]{"id"});
            ps.setString(1, "投诉占比超阈值：" + row.dimensionName());
            ps.setString(2, snapshot(row, reason));
            ps.setBigDecimal(3, row.ratio());
            ps.setString(4, sourceKey);
            ps.setString(5, "tenant:" + tenantId);
            return ps;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private RatioRow requireActionable(String type, long dimensionId, YearMonth month) {
        List<RatioRow> rows = jdbc.query("""
                SELECT stats.stat_month, stats.dimension_type, stats.dimension_id,
                       COALESCE(channel.channel_name, tenant.short_name, CONCAT(stats.dimension_type, ':', stats.dimension_id)) AS dimension_name,
                       stats.send_count, stats.complaint_count, stats.ratio, stats.over_threshold,
                       COALESCE(stats.threshold_config_version, ?) AS threshold_config_version,
                       COALESCE(stats.data_quality, 'UNKNOWN') AS data_quality,
                       COALESCE(stats.source_registry, ?) AS source_registry,
                       stats.calculated_at
                  FROM complaint_ratio_stats stats
             LEFT JOIN channels channel
                    ON stats.dimension_type='CHANNEL' AND stats.dimension_id=channel.id
             LEFT JOIN tenants tenant
                    ON stats.dimension_type='TENANT' AND stats.dimension_id=tenant.id
                 WHERE stats.stat_month=? AND stats.dimension_type=? AND stats.dimension_id=?
                """, (rs, row) -> ratioRow(rs, 1, month), THRESHOLD_VERSION, SOURCE_REGISTRY, month.toString(), type, dimensionId);
        if (rows.isEmpty()) {
            throw failure("COMPLAINT_RATIO_ROW_NOT_FOUND", "投诉占比记录不存在");
        }
        RatioRow row = rows.get(0);
        if (!"COMPLETE".equals(row.dataQuality()) || !"BREACHED".equals(row.thresholdResult())) {
            throw failure("COMPLAINT_RATIO_INTERVENTION_NOT_ALLOWED", "只有完整且超阈值的投诉占比记录可以干预");
        }
        return row;
    }

    private RatioRow ratioRow(ResultSet rs, int rank, YearMonth month) throws SQLException {
        long send = rs.getLong("send_count");
        long complaints = rs.getLong("complaint_count");
        BigDecimal ratio = rs.getBigDecimal("ratio");
        boolean over = rs.getBoolean("over_threshold");
        LocalDateTime calculatedAt = timestamp(rs, "calculated_at");
        String quality = text(rs.getString("data_quality"), dataQuality(send, complaints, calculatedAt));
        String sourceRegistry = text(rs.getString("source_registry"), SOURCE_REGISTRY);
        return new RatioRow(
                rs.getString("stat_month"),
                rs.getString("dimension_type"),
                rs.getLong("dimension_id"),
                rs.getString("dimension_name"),
                send,
                complaints,
                ratio == null ? BigDecimal.ZERO : ratio,
                threshold,
                rs.getString("threshold_config_version"),
                over,
                quality,
                thresholdResult(over, quality),
                sourceRegistry,
                month.equals(YearMonth.now()) ? "CURRENT_MONTH_HOURLY" : "T_PLUS_1_DAILY",
                calculatedAt,
                rank,
                "COMPLETE".equals(quality) && over,
                sourceKey(rs.getString("dimension_type"), rs.getLong("dimension_id"), month, rs.getString("threshold_config_version"))
        );
    }

    private static String dataQuality(long send, long complaints, LocalDateTime calculatedAt) {
        if (calculatedAt == null || (send == 0 && complaints > 0)) {
            return "UNKNOWN";
        }
        if (send == 0) {
            return "ZERO_DENOMINATOR";
        }
        return "COMPLETE";
    }

    private static String thresholdResult(boolean over, String quality) {
        if (!"COMPLETE".equals(quality)) {
            return "UNKNOWN";
        }
        return over ? "BREACHED" : "NORMAL";
    }

    private static String dimensionType(String dimension) {
        if (dimension == null) {
            throw failure("COMPLAINT_RATIO_DIMENSION_REQUIRED", "投诉占比维度不能为空");
        }
        return switch (dimension.trim().toUpperCase()) {
            case "CHANNEL" -> "CHANNEL";
            case "TENANT" -> "TENANT";
            default -> throw failure("COMPLAINT_RATIO_DIMENSION_INVALID", "投诉占比维度无效");
        };
    }

    private static int normalizeLimit(Integer value) {
        if (value == null) {
            return 10;
        }
        if (value < 1 || value > 100) {
            throw failure("COMPLAINT_RATIO_LIMIT_INVALID", "Top N 必须在 1 到 100 之间");
        }
        return value;
    }

    private static String sourceKey(String type, long dimensionId, YearMonth month, String thresholdVersion) {
        return "complaint-ratio:%s:%d:%s:%s".formatted(type, dimensionId, month, text(thresholdVersion, THRESHOLD_VERSION));
    }

    private static String snapshot(RatioRow row, String reason) {
        return "statMonth=%s,dimension=%s:%d,send=%d,complaints=%d,ratio=%s,threshold=%s,quality=%s,reason=%s"
                .formatted(row.statMonth(), row.dimensionType(), row.dimensionId(), row.sendCount(), row.complaintCount(),
                        row.ratio().toPlainString(), row.thresholdValue().toPlainString(), row.dataQuality(), reason);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String actor(String actor) {
        return text(actor, "console");
    }

    private void lockChannel(long channelId) {
        List<String> statuses = jdbc.query("""
                SELECT status FROM channels WHERE id=? FOR UPDATE
                """, (rs, row) -> rs.getString(1), channelId);
        if (statuses.isEmpty()) {
            throw failure("COMPLAINT_RATIO_TARGET_NOT_FOUND", "投诉占比干预目标不存在或不可暂停");
        }
    }

    private String lockTenant(long tenantId) {
        return jdbc.query("""
                SELECT lifecycle_status FROM tenants WHERE id=? FOR UPDATE
                """, (rs, index) -> rs.getString(1), tenantId).stream().findFirst()
                .orElseThrow(() -> failure("TENANT_NOT_FOUND", "机构不存在"));
    }

    private static String requiredReason(InterventionCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            throw failure("COMPLAINT_RATIO_INTERVENTION_REASON_REQUIRED", "投诉占比干预原因不能为空");
        }
        String reason = command.reason().trim();
        if (reason.length() > 255) {
            throw failure("COMPLAINT_RATIO_INTERVENTION_REASON_TOO_LONG", "投诉占比干预原因不能超过 255 个字符");
        }
        return reason;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static void requireUpdated(int rows) {
        if (rows <= 0) {
            throw failure("COMPLAINT_RATIO_TARGET_NOT_FOUND", "投诉占比干预目标不存在或不可暂停");
        }
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public record RatioRow(
            String statMonth,
            String dimensionType,
            long dimensionId,
            String dimensionName,
            long sendCount,
            long complaintCount,
            BigDecimal ratio,
            BigDecimal thresholdValue,
            String thresholdConfigVersion,
            boolean overThreshold,
            String dataQuality,
            String thresholdResult,
            String sourceRegistry,
            String freshnessPolicy,
            LocalDateTime calculatedAt,
            int rank,
            boolean interventionAvailable,
            String alertSourceKey) {
    }

    public record ComplaintCaseRow(
            long id,
            String source,
            Long tenantId,
            Long channelId,
            String messageId,
            String summary,
            String status,
            String attributionQuality,
            LocalDateTime createdAt) {
    }

    public record InterventionCommand(String reason, String reviewId) {
    }

    public record InterventionResult(
            String dimensionType,
            long dimensionId,
            String status,
            long evidenceId,
            Long alertRecordId,
            String sourceKey,
            String action) {
    }
}
