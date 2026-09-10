package com.ycsopen.sms.core.service.alert;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AlertEngineService {
    private static final List<String> SUPPORTED_METRICS = List.of(
            "CHANNEL_HEALTH", "FAILURE_RATE", "BALANCE", "QUEUE_BACKLOG", "COMPLAINT_RATIO", "UNSUBSCRIBE_RATE");

    private final JdbcTemplate jdbc;

    public AlertEngineService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total_count,
                       SUM(CASE WHEN status='ACTIVE' THEN 1 ELSE 0 END) AS active_count,
                       SUM(CASE WHEN severity IN ('HIGH','CRITICAL') AND status<>'RESOLVED' THEN 1 ELSE 0 END) AS severe_count,
                       SUM(CASE WHEN status='RESOLVED' THEN 1 ELSE 0 END) AS resolved_count
                  FROM alert_records
                """, (rs, row) -> new Dashboard(rs.getLong("total_count"), rs.getLong("active_count"),
                rs.getLong("severe_count"), rs.getLong("resolved_count")));
    }

    @Transactional(readOnly = true)
    public List<RuleRow> rules() {
        return jdbc.query("""
                SELECT id, rule_name, rule_type, metric_name, metric_source, threshold_value, comparison_op,
                       duration_minutes, severity, notify_channels, notification_targets, source_scope, status, updated_at
                  FROM alert_rules
                 ORDER BY id DESC
                """, (rs, row) -> rule(rs));
    }

    @Transactional
    public RuleRow saveRule(RuleCommand command, String actor) {
        RuleCommand checked = command.checked();
        jdbc.update("""
                INSERT INTO alert_rules(rule_name, rule_type, metric_name, metric_source, threshold_value,
                    comparison_op, duration_minutes, severity, notify_channels, notification_targets,
                    source_scope, status, created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, checked.ruleName(), checked.ruleType(), checked.metricName(), checked.metricSource(),
                checked.thresholdValue(), checked.comparisonOp(), checked.durationMinutes(), checked.severity(),
                checked.notifyChannels(), checked.notificationTargets(), checked.sourceScope(), checked.status(), actor(actor));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM alert_rules WHERE created_by=?", Long.class, actor(actor));
        return requireRule(id == null ? 0 : id);
    }

    @Transactional
    public EvaluationResult evaluate(SourceEvent event, String actor) {
        SourceEvent checked = event.checked();
        List<RuleRow> candidates = jdbc.query("""
                SELECT id, rule_name, rule_type, metric_name, metric_source, threshold_value, comparison_op,
                       duration_minutes, severity, notify_channels, notification_targets, source_scope, status, updated_at
                  FROM alert_rules
                 WHERE status='ACTIVE' AND metric_name=?
                """, (rs, row) -> rule(rs), checked.metricName());
        List<AlertRow> changed = new ArrayList<>();
        for (RuleRow rule : candidates) {
            boolean thresholdMatched = compare(checked.metricValue(), rule.comparisonOp(), rule.thresholdValue());
            boolean sustained = checked.sustainedMinutes() >= rule.durationMinutes();
            if (thresholdMatched && sustained) {
                changed.add(triggerOrUpdate(rule, checked, actor(actor)));
            } else {
                resolveByRecovery(rule, checked, actor(actor)).forEach(changed::add);
            }
        }
        return new EvaluationResult(changed);
    }

    @Transactional(readOnly = true)
    public List<AlertRow> history(HistoryFilter filter) {
        HistoryFilter checked = filter == null ? new HistoryFilter(null, null) : filter;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM alert_records WHERE 1=1
                """);
        if (text(checked.status()) != null) {
            sql.append(" AND status=?");
            params.add(checked.status().trim().toUpperCase(Locale.ROOT));
        }
        if (text(checked.severity()) != null) {
            sql.append(" AND severity=?");
            params.add(checked.severity().trim().toUpperCase(Locale.ROOT));
        }
        sql.append(" ORDER BY triggered_at DESC, id DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, row) -> alert(rs), params.toArray());
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttemptRow> deliveries(Long alertId) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, alert_record_id, channel, target_snapshot, provider_result, retry_count, status,
                       failure_reason, attempted_at
                  FROM alert_delivery_attempts WHERE 1=1
                """);
        if (alertId != null) {
            sql.append(" AND alert_record_id=?");
            params.add(alertId);
        }
        sql.append(" ORDER BY attempted_at DESC, id DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, row) -> delivery(rs), params.toArray());
    }

    @Transactional
    public AlertRow acknowledge(long alertId, String actor) {
        int updated = jdbc.update("""
                UPDATE alert_records
                   SET status='ACKNOWLEDGED', acknowledged_by=?, acknowledged_at=CURRENT_TIMESTAMP,
                       updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status='ACTIVE'
                """, actor(actor), alertId);
        if (updated != 1) {
            throw new BusinessException("ALERT_ACK_STATE_INVALID", "只有活跃告警可以确认");
        }
        return requireAlert(alertId);
    }

    @Transactional
    public AlertRow resolve(long alertId, ResolveCommand command, String actor) {
        ResolveCommand checked = command.checked();
        int updated = jdbc.update("""
                UPDATE alert_records
                   SET status='RESOLVED', resolved_by=?, resolved_at=CURRENT_TIMESTAMP,
                       resolution_note=?, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status='ACKNOWLEDGED'
                """, actor(actor), checked.reason(), alertId);
        if (updated != 1) {
            throw new BusinessException("ALERT_RESOLVE_STATE_INVALID", "只有已确认告警可以解决");
        }
        return requireAlert(alertId);
    }

    @Transactional
    public MuteRow mute(long alertId, MuteCommand command, String actor) {
        MuteCommand checked = command.checked();
        LocalDateTime until = LocalDateTime.now().plusMinutes(checked.minutes());
        int updated = jdbc.update("""
                UPDATE alert_records
                   SET muted_until=?, delivery_state='MUTED', updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status IN ('ACTIVE','ACKNOWLEDGED')
                """, until, alertId);
        if (updated != 1) {
            throw new BusinessException("ALERT_MUTE_STATE_INVALID", "只有活跃或已确认告警可以静音");
        }
        jdbc.update("""
                INSERT INTO alert_mutes(scope, reason, muted_by, muted_until)
                VALUES ('GLOBAL', ?, ?, ?)
                """, checked.reason(), actor(actor), until);
        Long muteId = jdbc.queryForObject("SELECT MAX(id) FROM alert_mutes WHERE muted_by=?", Long.class, actor(actor));
        return new MuteRow(muteId == null ? 0 : muteId, "GLOBAL", checked.reason(), actor(actor), until);
    }

    private AlertRow triggerOrUpdate(RuleRow rule, SourceEvent event, String actor) {
        List<AlertRow> existing = jdbc.query("""
                SELECT * FROM alert_records
                 WHERE rule_id=? AND source_key=? AND status IN ('ACTIVE','ACKNOWLEDGED')
                 ORDER BY id DESC LIMIT 1
                """, (rs, row) -> alert(rs), rule.id(), event.sourceKey());
        if (!existing.isEmpty()) {
            AlertRow row = existing.get(0);
            jdbc.update("""
                    UPDATE alert_records
                       SET metric_value=?, content=?, impact_scope=?, updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, event.metricValue(), event.description(), event.impactScope(), row.id());
            return requireAlert(row.id());
        }
        boolean muted = isMuted();
        String deliveryState = muted ? "MUTED" : "PENDING";
        jdbc.update("""
                INSERT INTO alert_records(rule_id, title, content, metric_value, status, severity, source_module,
                    source_key, impact_scope, delivery_state)
                VALUES (?,?,?,?, 'ACTIVE', ?, ?, ?, ?, ?)
                """, rule.id(), event.title(), event.description(), event.metricValue(), rule.severity(),
                event.sourceModule(), event.sourceKey(), event.impactScope(), deliveryState);
        Long alertId = jdbc.queryForObject("SELECT MAX(id) FROM alert_records WHERE rule_id=? AND source_key=?",
                Long.class, rule.id(), event.sourceKey());
        long id = alertId == null ? 0 : alertId;
        if (muted) {
            return requireAlert(id);
        }
        List<String> channels = parseTokens(rule.notifyChannels());
        if (channels.isEmpty()) {
            channels = List.of("EMAIL");
        }
        String targetSnapshot = text(rule.notificationTargets()) == null ? "[\"operations\"]" : rule.notificationTargets();
        boolean failed = event.forceAdapterFailure();
        for (String channel : channels) {
            jdbc.update("""
                    INSERT INTO alert_delivery_attempts(alert_record_id, channel, target_snapshot, provider_result,
                        retry_count, status, failure_reason)
                    VALUES (?,?,?,?,?,?,?)
                    """, id, channel, targetSnapshot, failed ? "PROVIDER_ERROR" : "ACCEPTED",
                    failed ? 1 : 0, failed ? "FAILED" : "DELIVERED", failed ? "adapter unavailable" : null);
        }
        jdbc.update("""
                UPDATE alert_records
                   SET delivery_state=?, updated_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, failed ? "FAILED" : "DELIVERED", id);
        return requireAlert(id);
    }

    private List<AlertRow> resolveByRecovery(RuleRow rule, SourceEvent event, String actor) {
        List<AlertRow> existing = jdbc.query("""
                SELECT * FROM alert_records
                 WHERE rule_id=? AND source_key=? AND status IN ('ACTIVE','ACKNOWLEDGED')
                """, (rs, row) -> alert(rs), rule.id(), event.sourceKey());
        for (AlertRow row : existing) {
            jdbc.update("""
                    UPDATE alert_records
                       SET status='RESOLVED', resolved_by=?, resolved_at=CURRENT_TIMESTAMP,
                           resolution_note='SOURCE_RECOVERED', updated_at=CURRENT_TIMESTAMP
                     WHERE id=? AND status IN ('ACTIVE','ACKNOWLEDGED')
                    """, actor, row.id());
        }
        return existing.stream().map(row -> requireAlert(row.id())).toList();
    }

    private boolean isMuted() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM alert_mutes
                 WHERE scope='GLOBAL' AND muted_until>CURRENT_TIMESTAMP
                """, Integer.class);
        return count != null && count > 0;
    }

    private RuleRow requireRule(long id) {
        return jdbc.queryForObject("""
                SELECT id, rule_name, rule_type, metric_name, metric_source, threshold_value, comparison_op,
                       duration_minutes, severity, notify_channels, notification_targets, source_scope, status, updated_at
                  FROM alert_rules
                 WHERE id=?
                """, (rs, row) -> rule(rs), id);
    }

    private AlertRow requireAlert(long id) {
        return jdbc.queryForObject("SELECT * FROM alert_records WHERE id=?", (rs, row) -> alert(rs), id);
    }

    private static boolean compare(BigDecimal value, String op, BigDecimal threshold) {
        int comparison = value.compareTo(threshold);
        return switch (op) {
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case "=" -> comparison == 0;
            default -> throw new BusinessException("ALERT_COMPARISON_INVALID", "告警比较符不支持");
        };
    }

    private static List<String> parseTokens(String json) {
        String value = text(json);
        if (value == null) {
            return List.of();
        }
        String normalized = value.replace("[", "").replace("]", "").replace("\"", "");
        List<String> tokens = new ArrayList<>();
        for (String item : normalized.split(",")) {
            String token = text(item);
            if (token != null) {
                tokens.add(token.toUpperCase(Locale.ROOT));
            }
        }
        return tokens;
    }

    private static RuleRow rule(ResultSet rs) throws SQLException {
        return new RuleRow(rs.getLong("id"), rs.getString("rule_name"), rs.getString("rule_type"),
                rs.getString("metric_name"), rs.getString("metric_source"), rs.getBigDecimal("threshold_value"),
                rs.getString("comparison_op"), rs.getInt("duration_minutes"), rs.getString("severity"),
                rs.getString("notify_channels"), rs.getString("notification_targets"),
                rs.getString("source_scope"), rs.getString("status"), timestamp(rs.getTimestamp("updated_at")));
    }

    private static AlertRow alert(ResultSet rs) throws SQLException {
        return new AlertRow(rs.getLong("id"), rs.getLong("rule_id"), rs.getString("title"), rs.getString("content"),
                rs.getBigDecimal("metric_value"), rs.getString("status"), rs.getString("severity"),
                rs.getString("source_module"), rs.getString("source_key"), rs.getString("impact_scope"),
                timestamp(rs.getTimestamp("triggered_at")), timestamp(rs.getTimestamp("acknowledged_at")),
                rs.getString("acknowledged_by"), timestamp(rs.getTimestamp("resolved_at")),
                rs.getString("resolved_by"), rs.getString("resolution_note"), rs.getString("delivery_state"),
                timestamp(rs.getTimestamp("muted_until")));
    }

    private static DeliveryAttemptRow delivery(ResultSet rs) throws SQLException {
        return new DeliveryAttemptRow(rs.getLong("id"), rs.getLong("alert_record_id"), rs.getString("channel"),
                rs.getString("target_snapshot"), rs.getString("provider_result"), rs.getInt("retry_count"),
                rs.getString("status"), rs.getString("failure_reason"), timestamp(rs.getTimestamp("attempted_at")));
    }

    private static LocalDateTime timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String actor(String actor) {
        return text(actor) == null ? "system" : actor.trim();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Dashboard(long totalCount, long activeCount, long severeCount, long resolvedCount) { }

    public record RuleRow(long id, String ruleName, String ruleType, String metricName, String metricSource,
                          BigDecimal thresholdValue, String comparisonOp, int durationMinutes, String severity,
                          String notifyChannels, String notificationTargets, String sourceScope, String status,
                          LocalDateTime updatedAt) { }

    public record RuleCommand(String ruleName, String ruleType, String metricName, String metricSource,
                              BigDecimal thresholdValue, String comparisonOp, int durationMinutes, String severity,
                              String notifyChannels, String notificationTargets, String sourceScope, String status) {
        RuleCommand checked() {
            if (text(ruleName) == null || text(ruleType) == null || text(metricName) == null || thresholdValue == null) {
                throw new BusinessException("ALERT_RULE_INVALID", "告警规则字段不完整");
            }
            String metric = metricName.trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_METRICS.contains(metric)) {
                throw new BusinessException("ALERT_RULE_METRIC_UNSUPPORTED", "告警指标不支持");
            }
            String op = text(comparisonOp) == null ? ">=" : comparisonOp.trim();
            if (!List.of(">", ">=", "<", "<=", "=").contains(op)) {
                throw new BusinessException("ALERT_RULE_COMPARISON_UNSUPPORTED", "告警比较符不支持");
            }
            int duration = durationMinutes <= 0 ? 1 : durationMinutes;
            return new RuleCommand(ruleName.trim(), ruleType.trim().toUpperCase(Locale.ROOT), metric,
                    text(metricSource) == null ? "statistics_aggregates" : metricSource.trim(),
                    thresholdValue, op, duration,
                    text(severity) == null ? "MEDIUM" : severity.trim().toUpperCase(Locale.ROOT),
                    text(notifyChannels) == null ? "[\"EMAIL\"]" : notifyChannels.trim(),
                    text(notificationTargets) == null ? "[\"operations\"]" : notificationTargets.trim(),
                    text(sourceScope) == null ? "PLATFORM" : sourceScope.trim().toUpperCase(Locale.ROOT),
                    text(status) == null ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record SourceEvent(String metricName, BigDecimal metricValue, int sustainedMinutes, String sourceModule,
                              String sourceKey, String title, String description, String impactScope,
                              boolean forceAdapterFailure) {
        SourceEvent checked() {
            if (text(metricName) == null || metricValue == null || text(sourceKey) == null || text(title) == null) {
                throw new BusinessException("ALERT_SOURCE_EVENT_INVALID", "告警源事件字段不完整");
            }
            return new SourceEvent(metricName.trim().toUpperCase(Locale.ROOT), metricValue, sustainedMinutes,
                    text(sourceModule) == null ? "UNKNOWN" : sourceModule.trim(),
                    sourceKey.trim(), title.trim(), text(description), text(impactScope), forceAdapterFailure);
        }
    }

    public record EvaluationResult(List<AlertRow> alerts) { }

    public record AlertRow(long id, long ruleId, String title, String content, BigDecimal metricValue, String status,
                           String severity, String sourceModule, String sourceKey, String impactScope,
                           LocalDateTime triggeredAt, LocalDateTime acknowledgedAt, String acknowledgedBy,
                           LocalDateTime resolvedAt, String resolvedBy, String resolutionNote,
                           String deliveryState, LocalDateTime mutedUntil) { }

    public record DeliveryAttemptRow(long id, long alertRecordId, String channel, String targetSnapshot,
                                     String providerResult, int retryCount, String status, String failureReason,
                                     LocalDateTime attemptedAt) { }

    public record HistoryFilter(String status, String severity) { }

    public record ResolveCommand(String reason) {
        ResolveCommand checked() {
            if (text(reason) == null) {
                throw new BusinessException("ALERT_RESOLVE_REASON_REQUIRED", "解决原因不能为空");
            }
            return new ResolveCommand(reason.trim());
        }
    }

    public record MuteCommand(int minutes, String reason) {
        MuteCommand checked() {
            if (minutes <= 0 || text(reason) == null) {
                throw new BusinessException("ALERT_MUTE_INVALID", "静音时长和原因不能为空");
            }
            return new MuteCommand(minutes, reason.trim());
        }
    }

    public record MuteRow(long id, String scope, String reason, String mutedBy, LocalDateTime mutedUntil) { }
}
