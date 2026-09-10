package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Phase42 tenant-level risk warning and auto-pause service. */
@Service
public class TenantRiskAutoPauseService {
    private static final List<String> METRICS = List.of("COMPLAINT_RATE", "FAILURE_RATE", "UNSUBSCRIBE_RATE");
    private static final List<String> ACTIONS = List.of("NOTIFY", "AUTO_SUSPEND");

    private final JdbcTemplate jdbc;

    public TenantRiskAutoPauseService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional
    public RuleRow saveRule(RuleCommand command, String actor) {
        RuleCommand input = requireRule(command);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO tenant_alert_rules(rule_name, tenant_id, metric, threshold_value, duration_minutes,
                        action, notify_targets, status, created_by)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, new String[]{"id"});
            ps.setString(1, input.ruleName().trim());
            ps.setLong(2, input.tenantId());
            ps.setString(3, input.metric());
            ps.setBigDecimal(4, input.thresholdValue());
            ps.setInt(5, input.durationMinutes());
            ps.setString(6, input.action());
            ps.setString(7, text(input.notifyTargets(), "[]"));
            ps.setString(8, input.status());
            ps.setString(9, actor(actor));
            return ps;
        }, key);
        return ruleById(Objects.requireNonNull(key.getKey()).longValue());
    }

    @Transactional(readOnly = true)
    public List<RuleRow> rules(Long tenantId) {
        if (tenantId == null) {
            return jdbc.query("""
                    SELECT id, rule_name, tenant_id, metric, threshold_value, duration_minutes, action,
                           notify_targets, status, updated_at
                      FROM tenant_alert_rules
                     ORDER BY id DESC
                    """, (rs, row) -> rule(rs));
        }
        return jdbc.query("""
                SELECT id, rule_name, tenant_id, metric, threshold_value, duration_minutes, action,
                       notify_targets, status, updated_at
                  FROM tenant_alert_rules
                 WHERE tenant_id IS NULL OR tenant_id=?
                 ORDER BY id DESC
                """, (rs, row) -> rule(rs), tenantId);
    }

    @Transactional
    public EvaluationResult evaluate(EvaluationCommand command, String actor) {
        EvaluationCommand input = requireEvaluation(command);
        List<EpisodeRow> existing = jdbc.query("""
                SELECT * FROM tenant_risk_episodes WHERE source_key=?
                """, (rs, row) -> episode(rs), input.sourceKey());
        if (!existing.isEmpty()) {
            EpisodeRow row = existing.get(0);
            return new EvaluationResult(row.id(), row.dataQuality(), row.rate(), "PAUSED".equals(row.status()));
        }

        RuleRow rule = activeRule(input.tenantId(), input.metric());
        if (input.denominator() <= 0) {
            EpisodeRow unknown = createEpisode(rule, input, null, "UNKNOWN", "UNKNOWN", null, null);
            return new EvaluationResult(unknown.id(), "UNKNOWN", null, false);
        }

        BigDecimal rate = BigDecimal.valueOf(input.numerator())
                .divide(BigDecimal.valueOf(input.denominator()), 6, RoundingMode.HALF_UP);
        boolean sustained = input.windowMinutes() >= rule.durationMinutes();
        boolean breached = sustained && rate.compareTo(rule.thresholdValue()) >= 0;
        if (!breached) {
            return new EvaluationResult(null, "COMPLETE", rate, false);
        }

        long alertId = createAlert(rule, input.tenantId(), rate, sourceSnapshot(input, rate));
        String before = lifecycle(input.tenantId());
        boolean pause = "AUTO_SUSPEND".equals(rule.action());
        if (pause) {
            jdbc.update("UPDATE tenants SET lifecycle_status='FROZEN' WHERE id=?", input.tenantId());
        }
        EpisodeRow episode = createEpisode(rule, input, rate, "COMPLETE", pause ? "PAUSED" : "ACTIVE",
                alertId, pause ? actor : null, before);
        return new EvaluationResult(episode.id(), "COMPLETE", rate, pause);
    }

    @Transactional(readOnly = true)
    public List<EpisodeRow> episodes(Long tenantId) {
        if (tenantId == null) {
            return jdbc.query("SELECT * FROM tenant_risk_episodes ORDER BY id DESC", (rs, row) -> episode(rs));
        }
        return jdbc.query("SELECT * FROM tenant_risk_episodes WHERE tenant_id=? ORDER BY id DESC",
                (rs, row) -> episode(rs), tenantId);
    }

    @Transactional
    public EpisodeRow recover(long episodeId, RecoveryCommand command, String actor) {
        if (command == null || command.reviewId() == null || command.reviewId().isBlank()) {
            throw denied("TENANT_RISK_RECOVERY_REVIEW_REQUIRED", "机构风险恢复需要授权复核编号");
        }
        EpisodeRow before = episodeById(episodeId);
        jdbc.update("""
                UPDATE tenant_risk_episodes
                   SET status='RESOLVED', recovery_review_id=?, recovery_note=?, recovered_by=?, recovered_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, command.reviewId().trim(), text(command.note(), "恢复复核通过"), actor(actor), episodeId);
        if ("AUTO_SUSPEND".equals(before.action()) && "PAUSED".equals(before.status())) {
            jdbc.update("UPDATE tenants SET lifecycle_status=? WHERE id=?",
                    text(before.beforeLifecycleStatus(), "SIGNED"), before.tenantId());
        }
        return episodeById(episodeId);
    }

    @Transactional(readOnly = true)
    public void assertSubmissionAllowed(long tenantId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenant_risk_episodes
                 WHERE tenant_id=? AND action='AUTO_SUSPEND' AND status='PAUSED'
                """, Integer.class, tenantId);
        if (count != null && count > 0) {
            throw denied("TENANT_RISK_AUTO_PAUSED", "机构风险预警已自动暂停，不可新增提交");
        }
    }

    private EpisodeRow createEpisode(RuleRow rule, EvaluationCommand input, BigDecimal rate, String quality,
                                     String status, Long alertId, String pausedBy) {
        return createEpisode(rule, input, rate, quality, status, alertId, pausedBy, lifecycle(input.tenantId()));
    }

    private EpisodeRow createEpisode(RuleRow rule, EvaluationCommand input, BigDecimal rate, String quality,
                                     String status, Long alertId, String pausedBy, String beforeLifecycle) {
        String snapshot = sourceSnapshot(input, rate);
        try {
            jdbc.update("""
                    INSERT INTO tenant_risk_episodes(tenant_id, rule_id, alert_record_id, metric, source_key,
                        source_registry, numerator, denominator, rate, threshold_value, window_minutes, data_quality,
                        action, status, before_lifecycle_status, source_snapshot, paused_by, paused_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, input.tenantId(), rule.id(), alertId, input.metric(), input.sourceKey(), input.sourceRegistry(),
                    input.numerator(), input.denominator(), rate, rule.thresholdValue(), input.windowMinutes(), quality,
                    rule.action(), status, beforeLifecycle, snapshot, pausedBy, pausedBy == null ? null : LocalDateTime.now());
        } catch (DataIntegrityViolationException duplicate) {
            return requireEpisodeBySourceKey(input.sourceKey());
        }
        return requireEpisodeBySourceKey(input.sourceKey());
    }

    private long createAlert(RuleRow rule, long tenantId, BigDecimal rate, String snapshot) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO alert_records(rule_id, title, content, metric_value, status, severity, source_module,
                        source_key, impact_scope, delivery_state)
                    VALUES (?, ?, ?, ?, 'ACTIVE', 'HIGH', 'TENANT_RISK', ?, ?, 'DELIVERED')
                    """, new String[]{"id"});
            ps.setLong(1, rule.id());
            ps.setString(2, "机构风险预警：" + rule.ruleName());
            ps.setString(3, snapshot);
            ps.setBigDecimal(4, rate);
            ps.setString(5, "tenant-risk:%d:%d:%s".formatted(tenantId, rule.id(), rule.metric()));
            ps.setString(6, "tenant:" + tenantId);
            return ps;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private RuleRow activeRule(long tenantId, String metric) {
        List<RuleRow> rules = jdbc.query("""
                SELECT id, rule_name, tenant_id, metric, threshold_value, duration_minutes, action,
                       notify_targets, status, updated_at
                  FROM tenant_alert_rules
                 WHERE metric=? AND status='ACTIVE' AND (tenant_id IS NULL OR tenant_id=?)
                 ORDER BY tenant_id DESC, id DESC
                 LIMIT 1
                """, (rs, row) -> rule(rs), metric, tenantId);
        if (rules.isEmpty()) {
            throw denied("TENANT_RISK_RULE_NOT_FOUND", "未配置有效机构风险规则");
        }
        return rules.get(0);
    }

    private RuleRow ruleById(long id) {
        return jdbc.queryForObject("""
                SELECT id, rule_name, tenant_id, metric, threshold_value, duration_minutes, action,
                       notify_targets, status, updated_at
                  FROM tenant_alert_rules
                 WHERE id=?
                """, (rs, row) -> rule(rs), id);
    }

    private EpisodeRow episodeById(long id) {
        return jdbc.queryForObject("SELECT * FROM tenant_risk_episodes WHERE id=?", (rs, row) -> episode(rs), id);
    }

    private EpisodeRow requireEpisodeBySourceKey(String sourceKey) {
        return jdbc.queryForObject("SELECT * FROM tenant_risk_episodes WHERE source_key=?",
                (rs, row) -> episode(rs), sourceKey);
    }

    private String lifecycle(long tenantId) {
        List<String> rows = jdbc.query("SELECT lifecycle_status FROM tenants WHERE id=?", (rs, row) -> rs.getString(1), tenantId);
        if (rows.isEmpty()) {
            throw denied("TENANT_NOT_FOUND", "机构不存在");
        }
        return rows.get(0);
    }

    private static String sourceSnapshot(EvaluationCommand command, BigDecimal rate) {
        String rateText = rate == null ? "UNKNOWN" : rate.toPlainString();
        return "sourceRegistry=%s,metric=%s,numerator=%d,denominator=%d,windowMinutes=%d,rate=%s"
                .formatted(command.sourceRegistry(), command.metric(), command.numerator(), command.denominator(),
                        command.windowMinutes(), rateText);
    }

    private static RuleCommand requireRule(RuleCommand command) {
        if (command == null || command.ruleName() == null || command.ruleName().isBlank()) {
            throw denied("TENANT_RISK_RULE_NAME_REQUIRED", "规则名称不能为空");
        }
        if (command.tenantId() == null) {
            throw denied("TENANT_RISK_TENANT_REQUIRED", "机构风险规则必须明确机构");
        }
        requireMetric(command.metric());
        if (command.thresholdValue() == null || command.thresholdValue().compareTo(BigDecimal.ZERO) < 0) {
            throw denied("TENANT_RISK_THRESHOLD_INVALID", "阈值必须大于等于 0");
        }
        if (command.durationMinutes() <= 0) {
            throw denied("TENANT_RISK_DURATION_INVALID", "持续窗口必须大于 0");
        }
        if (!ACTIONS.contains(command.action())) {
            throw denied("TENANT_RISK_ACTION_INVALID", "机构风险动作无效");
        }
        String status = command.status() == null || command.status().isBlank() ? "ACTIVE" : command.status();
        if (!List.of("ACTIVE", "DISABLED").contains(status)) {
            throw denied("TENANT_RISK_STATUS_INVALID", "机构风险规则状态无效");
        }
        return new RuleCommand(command.ruleName(), command.tenantId(), command.metric(), command.thresholdValue(),
                command.durationMinutes(), command.action(), command.notifyTargets(), status);
    }

    private static EvaluationCommand requireEvaluation(EvaluationCommand command) {
        if (command == null) {
            throw denied("TENANT_RISK_EVALUATION_REQUIRED", "机构风险评估参数不能为空");
        }
        requireMetric(command.metric());
        if (command.tenantId() == null) {
            throw denied("TENANT_RISK_TENANT_REQUIRED", "机构风险评估必须明确机构");
        }
        if (command.numerator() == null || command.numerator() < 0 || command.denominator() == null || command.denominator() < 0) {
            throw denied("TENANT_RISK_SOURCE_INVALID", "来源分子分母必须为非负数");
        }
        if (command.windowMinutes() <= 0) {
            throw denied("TENANT_RISK_WINDOW_INVALID", "来源窗口必须大于 0");
        }
        if (command.sourceKey() == null || command.sourceKey().isBlank()) {
            throw denied("TENANT_RISK_SOURCE_KEY_REQUIRED", "来源快照必须提供唯一键");
        }
        String registry = text(command.sourceRegistry(), "statistics_aggregates");
        return new EvaluationCommand(command.tenantId(), command.metric(), command.numerator(), command.denominator(),
                command.windowMinutes(), command.sourceKey().trim(), registry);
    }

    private static void requireMetric(String metric) {
        if (!METRICS.contains(metric)) {
            throw denied("TENANT_RISK_METRIC_INVALID", "机构风险指标无效");
        }
    }

    private static RuleRow rule(ResultSet rs) throws SQLException {
        return new RuleRow(rs.getLong("id"), rs.getString("rule_name"), nullableLong(rs, "tenant_id"),
                rs.getString("metric"), rs.getBigDecimal("threshold_value"), rs.getInt("duration_minutes"),
                rs.getString("action"), rs.getString("notify_targets"), rs.getString("status"),
                rs.getString("updated_at"));
    }

    private static EpisodeRow episode(ResultSet rs) throws SQLException {
        return new EpisodeRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("rule_id"),
                nullableLong(rs, "alert_record_id"), rs.getString("metric"), rs.getString("source_key"),
                rs.getString("source_registry"), rs.getLong("numerator"), rs.getLong("denominator"),
                rs.getBigDecimal("rate"), rs.getBigDecimal("threshold_value"), rs.getInt("window_minutes"),
                rs.getString("data_quality"), rs.getString("action"), rs.getString("status"),
                rs.getString("before_lifecycle_status"), rs.getString("source_snapshot"),
                rs.getString("recovery_review_id"), rs.getString("recovered_by"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String actor(String actor) {
        return actor == null || actor.isBlank() ? "console" : actor.trim();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static BusinessException denied(String code, String message) {
        return new BusinessException(code, message);
    }

    public record RuleCommand(String ruleName, Long tenantId, String metric, BigDecimal thresholdValue,
                              int durationMinutes, String action, String notifyTargets, String status) {}

    public record RuleRow(long id, String ruleName, Long tenantId, String metric, BigDecimal thresholdValue,
                          int durationMinutes, String action, String notifyTargets, String status, String updatedAt) {}

    public record EvaluationCommand(Long tenantId, String metric, Long numerator, Long denominator,
                                    int windowMinutes, String sourceKey, String sourceRegistry) {}

    public record EvaluationResult(Long episodeId, String dataQuality, BigDecimal rate, boolean paused) {}

    public record RecoveryCommand(String reviewId, String note) {}

    public record EpisodeRow(long id, long tenantId, long ruleId, Long alertRecordId, String metric,
                             String sourceKey, String sourceRegistry, long numerator, long denominator,
                             BigDecimal rate, BigDecimal thresholdValue, int windowMinutes, String dataQuality,
                             String action, String status, String beforeLifecycleStatus, String sourceSnapshot,
                             String recoveryReviewId, String recoveredBy) {
        public EpisodeRow withStatus(String status) {
            return new EpisodeRow(id, tenantId, ruleId, alertRecordId, metric, sourceKey, sourceRegistry,
                    numerator, denominator, rate, thresholdValue, windowMinutes, dataQuality, action, status,
                    beforeLifecycleStatus, sourceSnapshot, recoveryReviewId, recoveredBy);
        }
    }
}
