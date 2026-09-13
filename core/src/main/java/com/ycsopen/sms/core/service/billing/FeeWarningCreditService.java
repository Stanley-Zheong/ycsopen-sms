package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Phase 40: fee warnings and credit enforcement. Monetary values are stored in mil. */
@Service
public class FeeWarningCreditService {
    private static final List<String> METRIC_TYPES = List.of(
            "PREPAID_AMOUNT", "PREPAID_ESTIMATED_DAYS", "POSTPAID_CREDIT_RATIO");
    private static final List<String> ACTIONS = List.of("WARN_ONLY", "MANUAL_APPROVAL", "BLOCK");
    private static final long DEFAULT_MESSAGE_AMOUNT_MIL = 50L;

    private final JdbcTemplate jdbc;

    public FeeWarningCreditService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional
    public RuleRow saveRule(RuleCommand command, String actor) {
        RuleCommand checked = command.checked();
        jdbc.update("""
                INSERT INTO fee_warning_rules(rule_name,tenant_id,metric_type,threshold_value,action,
                    notify_channels,notification_targets,status,created_by)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, checked.ruleName(), checked.tenantId(), checked.metricType(), checked.thresholdValue(),
                checked.action(), checked.notifyChannels(), checked.notificationTargets(), checked.status(), actor(actor));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM fee_warning_rules WHERE created_by=?", Long.class, actor(actor));
        return requireRule(id == null ? 0 : id);
    }

    @Transactional(readOnly = true)
    public List<RuleRow> rules(Long tenantId) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id,rule_name,tenant_id,metric_type,threshold_value,action,notify_channels,
                       notification_targets,status,updated_at
                  FROM fee_warning_rules WHERE 1=1
                """);
        if (tenantId != null) {
            sql.append(" AND (tenant_id IS NULL OR tenant_id=?)");
            params.add(tenantId);
        }
        sql.append(" ORDER BY id DESC");
        return jdbc.query(sql.toString(), (rs, row) -> rule(rs), params.toArray());
    }

    @Transactional
    public List<EpisodeRow> evaluateTenant(long tenantId, long estimatedAmountMil, String actor) {
        long estimate = estimatedAmountMil <= 0 ? DEFAULT_MESSAGE_AMOUNT_MIL : estimatedAmountMil;
        ContractSnapshot contract = activeContract(tenantId);
        if ("POSTPAID".equals(contract.billingMode())) {
            return evaluatePostpaid(tenantId, contract, estimate, actor);
        }
        return evaluatePrepaid(tenantId, estimate, actor);
    }

    @Transactional
    public void enforceSubmission(long tenantId, long estimatedAmountMil, String actor) {
        evaluateTenant(tenantId, estimatedAmountMil, actor);
        assertSubmissionAllowed(tenantId);
    }

    @Transactional(readOnly = true)
    public void assertSubmissionAllowed(long tenantId) {
        List<EpisodeRow> episodes = episodes(tenantId).stream()
                .filter(row -> List.of("ACTIVE", "BLOCKED").contains(row.status()))
                .filter(row -> !"APPROVED".equals(row.approvalState()))
                .filter(row -> List.of("BLOCK", "MANUAL_APPROVAL").contains(row.action()))
                .toList();
        for (EpisodeRow episode : episodes) {
            if ("BLOCK".equals(episode.action()) || "BLOCKED".equals(episode.status())) {
                throw new BusinessException("FEE_WARNING_CREDIT_BLOCKED", "费用或授信策略已阻断提交");
            }
            if ("MANUAL_APPROVAL".equals(episode.action())) {
                throw new BusinessException("FEE_WARNING_MANUAL_APPROVAL_REQUIRED", "费用或授信预警需要人工批准后继续");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<EpisodeRow> episodes(Long tenantId) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id,tenant_id,rule_id,alert_record_id,metric_type,source_key,source_amount_mil,
                       credit_limit_mil,used_amount_mil,threshold_value,ratio,action,status,approval_state,
                       delivery_state,source_snapshot,actor,resolution_note,created_at,updated_at
                  FROM fee_warning_episodes WHERE 1=1
                """);
        if (tenantId != null) {
            sql.append(" AND tenant_id=?");
            params.add(tenantId);
        }
        sql.append(" ORDER BY created_at DESC,id DESC LIMIT 500");
        return jdbc.query(sql.toString(), (rs, row) -> episode(rs), params.toArray());
    }

    @Transactional
    public EpisodeRow approveEpisode(long episodeId, String reason, String actor) {
        String note = text(reason, "APPROVAL_REASON_REQUIRED", 255);
        int updated = jdbc.update("""
                UPDATE fee_warning_episodes
                   SET approval_state='APPROVED', status='APPROVED', resolution_note=?, actor=?,
                       updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND action='MANUAL_APPROVAL' AND approval_state='PENDING'
                """, note, actor(actor), episodeId);
        if (updated != 1) {
            throw new BusinessException("FEE_WARNING_APPROVAL_STATE_INVALID", "只有待批准费用预警可以批准");
        }
        return requireEpisode(episodeId);
    }

    private List<EpisodeRow> evaluatePrepaid(long tenantId, long estimatedAmountMil, String actor) {
        PrepaidSnapshot prepaid = prepaid(tenantId);
        long available = Math.max(0, prepaid.balanceMil() - prepaid.frozenMil());
        List<EpisodeRow> changed = new ArrayList<>();
        for (RuleRow rule : activeRules(tenantId, List.of("PREPAID_AMOUNT", "PREPAID_ESTIMATED_DAYS"))) {
            if ("PREPAID_AMOUNT".equals(rule.metricType()) && BigDecimal.valueOf(available).compareTo(rule.thresholdValue()) <= 0) {
                changed.add(trigger(rule, tenantId, available, null, null, null, actor,
                        "availableMil=%d,balanceMil=%d,frozenMil=%d".formatted(available, prepaid.balanceMil(), prepaid.frozenMil())));
            }
            if ("PREPAID_ESTIMATED_DAYS".equals(rule.metricType()) && estimatedAmountMil > 0) {
                BigDecimal days = BigDecimal.valueOf(available).divide(BigDecimal.valueOf(estimatedAmountMil), 4, RoundingMode.DOWN);
                if (days.compareTo(rule.thresholdValue()) <= 0) {
                    changed.add(trigger(rule, tenantId, available, null, null, days, actor,
                            "availableMil=%d,estimatedAmountMil=%d,estimatedDays=%s".formatted(available, estimatedAmountMil, days)));
                }
            }
        }
        return changed;
    }

    private List<EpisodeRow> evaluatePostpaid(long tenantId, ContractSnapshot contract, long estimatedAmountMil, String actor) {
        long used = postpaidUsed(tenantId);
        long projected = used + estimatedAmountMil;
        long creditLimit = contract.creditLimitMil() == null ? 0 : contract.creditLimitMil();
        BigDecimal ratio = creditLimit <= 0
                ? BigDecimal.ONE
                : BigDecimal.valueOf(projected).divide(BigDecimal.valueOf(creditLimit), 4, RoundingMode.HALF_UP);
        List<EpisodeRow> changed = new ArrayList<>();
        for (RuleRow rule : activeRules(tenantId, List.of("POSTPAID_CREDIT_RATIO"))) {
            if (ratio.compareTo(rule.thresholdValue()) >= 0) {
                changed.add(trigger(rule, tenantId, projected, creditLimit, used, ratio, actor,
                        "usedMil=%d,estimatedAmountMil=%d,creditLimitMil=%d,ratio=%s".formatted(used, estimatedAmountMil, creditLimit, ratio)));
            }
        }
        return changed;
    }

    private EpisodeRow trigger(RuleRow rule, long tenantId, long sourceAmountMil, Long creditLimitMil, Long usedAmountMil,
                               BigDecimal ratio, String actor, String sourceSnapshot) {
        String sourceKey = "fee-warning:%d:%d:%s".formatted(tenantId, rule.id(), rule.metricType());
        EpisodeRow existing = findEpisode(sourceKey);
        if (existing != null) {
            return existing;
        }
        long alertId = createAlert(rule, tenantId, sourceAmountMil, ratio, sourceSnapshot);
        String status = "BLOCK".equals(rule.action()) ? "BLOCKED" : "ACTIVE";
        String approvalState = "MANUAL_APPROVAL".equals(rule.action()) ? "PENDING" : "NOT_REQUIRED";
        try {
            jdbc.update("""
                    INSERT INTO fee_warning_episodes(tenant_id,rule_id,alert_record_id,metric_type,source_key,
                        source_amount_mil,credit_limit_mil,used_amount_mil,threshold_value,ratio,action,status,
                        approval_state,delivery_state,source_snapshot,actor)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, tenantId, rule.id(), alertId, rule.metricType(), sourceKey, sourceAmountMil, creditLimitMil,
                    usedAmountMil, rule.thresholdValue(), ratio, rule.action(), status, approvalState, "DELIVERED",
                    sourceSnapshot, actor(actor));
        } catch (DataIntegrityViolationException duplicate) {
            return findEpisode(sourceKey);
        }
        return requireEpisodeBySourceKey(sourceKey);
    }

    private long createAlert(RuleRow rule, long tenantId, long sourceAmountMil, BigDecimal ratio, String sourceSnapshot) {
        long alertRuleId = alertRuleId();
        String title = "费用预警：" + rule.ruleName();
        String sourceKey = "fee-warning:%d:%d:%s".formatted(tenantId, rule.id(), rule.metricType());
        BigDecimal metric = ratio == null ? BigDecimal.valueOf(sourceAmountMil) : ratio;
        jdbc.update("""
                INSERT INTO alert_records(rule_id,title,content,metric_value,status,severity,source_module,
                    source_key,impact_scope,delivery_state)
                VALUES (?,?,?,?, 'ACTIVE', 'HIGH', 'BILLING', ?, ?, 'DELIVERED')
                """, alertRuleId, title, sourceSnapshot, metric, sourceKey, "tenant:" + tenantId);
        Long alertId = jdbc.queryForObject("SELECT MAX(id) FROM alert_records WHERE source_key=?", Long.class, sourceKey);
        long id = alertId == null ? 0 : alertId;
        for (String channel : tokens(rule.notifyChannels())) {
            jdbc.update("""
                    INSERT INTO alert_delivery_attempts(alert_record_id,channel,target_snapshot,provider_result,
                        retry_count,status,failure_reason)
                    VALUES (?,?,?,?,0,'DELIVERED',NULL)
                    """, id, channel, rule.notificationTargets(), "ACCEPTED");
        }
        return id;
    }

    private long alertRuleId() {
        List<Long> ids = jdbc.query("""
                SELECT id FROM alert_rules WHERE rule_type='FEE_WARNING' ORDER BY id LIMIT 1
                """, (rs, row) -> rs.getLong("id"));
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbc.update("""
                INSERT INTO alert_rules(rule_name,rule_type,metric_name,metric_source,threshold_value,comparison_op,
                    duration_minutes,severity,notify_channels,notification_targets,source_scope,status,created_by)
                VALUES ('费用预警传输','FEE_WARNING','BALANCE','fee_warning_episodes',0,'>=',1,'HIGH',
                    '["EMAIL"]','["finance"]','PLATFORM','ACTIVE','system')
                """);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM alert_rules WHERE rule_type='FEE_WARNING'", Long.class);
        return id == null ? 0 : id;
    }

    private List<RuleRow> activeRules(long tenantId, List<String> metricTypes) {
        return jdbc.query("""
                SELECT id,rule_name,tenant_id,metric_type,threshold_value,action,notify_channels,
                       notification_targets,status,updated_at
                  FROM fee_warning_rules
                 WHERE status='ACTIVE' AND (tenant_id IS NULL OR tenant_id=?)
                """, (rs, row) -> rule(rs), tenantId).stream()
                .filter(rule -> metricTypes.contains(rule.metricType()))
                .toList();
    }

    private PrepaidSnapshot prepaid(long tenantId) {
        return jdbc.query("""
                SELECT balance_mil,frozen_mil FROM prepaid_accounts WHERE tenant_id=?
                """, (rs, row) -> new PrepaidSnapshot(rs.getLong("balance_mil"), rs.getLong("frozen_mil")), tenantId)
                .stream().findFirst().orElse(new PrepaidSnapshot(0, 0));
    }

    private ContractSnapshot activeContract(long tenantId) {
        return jdbc.query("""
                SELECT billing_mode,credit_limit_mil FROM tenant_contracts
                 WHERE tenant_id=? AND contract_status='ACTIVE'
                """, (rs, row) -> new ContractSnapshot(rs.getString("billing_mode"), nullableLong(rs, "credit_limit_mil")), tenantId)
                .stream().findFirst().orElse(new ContractSnapshot("PREPAID", null));
    }

    private long postpaidUsed(long tenantId) {
        Long used = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_mil),0)
                  FROM postpaid_usage_ledger
                 WHERE tenant_id=? AND state='RECORDED' AND CURRENT_DATE BETWEEN period_start AND period_end
                """, Long.class, tenantId);
        return used == null ? 0 : used;
    }

    private RuleRow requireRule(long id) {
        return jdbc.queryForObject("""
                SELECT id,rule_name,tenant_id,metric_type,threshold_value,action,notify_channels,
                       notification_targets,status,updated_at
                  FROM fee_warning_rules WHERE id=?
                """, (rs, row) -> rule(rs), id);
    }

    private EpisodeRow requireEpisode(long id) {
        return jdbc.queryForObject("""
                SELECT id,tenant_id,rule_id,alert_record_id,metric_type,source_key,source_amount_mil,
                       credit_limit_mil,used_amount_mil,threshold_value,ratio,action,status,approval_state,
                       delivery_state,source_snapshot,actor,resolution_note,created_at,updated_at
                  FROM fee_warning_episodes WHERE id=?
                """, (rs, row) -> episode(rs), id);
    }

    private EpisodeRow requireEpisodeBySourceKey(String sourceKey) {
        return Objects.requireNonNull(findEpisode(sourceKey));
    }

    private EpisodeRow findEpisode(String sourceKey) {
        return jdbc.query("""
                SELECT id,tenant_id,rule_id,alert_record_id,metric_type,source_key,source_amount_mil,
                       credit_limit_mil,used_amount_mil,threshold_value,ratio,action,status,approval_state,
                       delivery_state,source_snapshot,actor,resolution_note,created_at,updated_at
                  FROM fee_warning_episodes WHERE source_key=?
                """, (rs, row) -> episode(rs), sourceKey).stream().findFirst().orElse(null);
    }

    private static RuleRow rule(ResultSet rs) throws SQLException {
        return new RuleRow(rs.getLong("id"), rs.getString("rule_name"), nullableLong(rs, "tenant_id"),
                rs.getString("metric_type"), rs.getBigDecimal("threshold_value"), rs.getString("action"),
                rs.getString("notify_channels"), rs.getString("notification_targets"), rs.getString("status"),
                timestamp(rs.getTimestamp("updated_at")));
    }

    private static EpisodeRow episode(ResultSet rs) throws SQLException {
        return new EpisodeRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("rule_id"),
                nullableLong(rs, "alert_record_id"), rs.getString("metric_type"), rs.getString("source_key"),
                rs.getLong("source_amount_mil"), nullableLong(rs, "credit_limit_mil"),
                nullableLong(rs, "used_amount_mil"), rs.getBigDecimal("threshold_value"),
                rs.getBigDecimal("ratio"), rs.getString("action"), rs.getString("status"),
                rs.getString("approval_state"), rs.getString("delivery_state"), rs.getString("source_snapshot"),
                rs.getString("actor"), rs.getString("resolution_note"), timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("updated_at")));
    }

    private static List<String> tokens(String json) {
        String value = json == null || json.isBlank() ? "[\"EMAIL\"]" : json;
        String normalized = value.replace("[", "").replace("]", "").replace("\"", "");
        List<String> result = new ArrayList<>();
        for (String part : normalized.split(",")) {
            String token = part.trim();
            if (!token.isEmpty()) {
                result.add(token.toUpperCase(Locale.ROOT));
            }
        }
        return result.isEmpty() ? List.of("EMAIL") : result;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String actor(String value) {
        return text(value, "ACTOR_REQUIRED", 64);
    }

    private static String text(String value, String code, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw new BusinessException(code, "文本不能为空且不能超过长度限制");
        }
        return trimmed;
    }

    public record RuleCommand(String ruleName, Long tenantId, String metricType, BigDecimal thresholdValue,
                              String action, String notifyChannels, String notificationTargets, String status) {
        RuleCommand checked() {
            String metric = text(metricType, "FEE_WARNING_METRIC_REQUIRED", 32).toUpperCase(Locale.ROOT);
            if (!METRIC_TYPES.contains(metric)) {
                throw new BusinessException("FEE_WARNING_METRIC_UNSUPPORTED", "费用预警指标不支持");
            }
            String nextAction = text(action, "FEE_WARNING_ACTION_REQUIRED", 32).toUpperCase(Locale.ROOT);
            if (!ACTIONS.contains(nextAction)) {
                throw new BusinessException("FEE_WARNING_ACTION_UNSUPPORTED", "费用预警动作不支持");
            }
            if (thresholdValue == null || thresholdValue.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("FEE_WARNING_THRESHOLD_INVALID", "费用预警阈值必须大于等于零");
            }
            return new RuleCommand(text(ruleName, "FEE_WARNING_RULE_NAME_REQUIRED", 128), tenantId, metric,
                    thresholdValue, nextAction, text(notifyChannels, "FEE_WARNING_CHANNELS_REQUIRED", 500),
                    text(notificationTargets, "FEE_WARNING_TARGETS_REQUIRED", 500),
                    status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record RuleRow(long id, String ruleName, Long tenantId, String metricType, BigDecimal thresholdValue,
                          String action, String notifyChannels, String notificationTargets, String status,
                          LocalDateTime updatedAt) { }

    public record EpisodeRow(long id, long tenantId, long ruleId, Long alertRecordId, String metricType,
                             String sourceKey, long sourceAmountMil, Long creditLimitMil, Long usedAmountMil,
                             BigDecimal thresholdValue, BigDecimal ratio, String action, String status,
                             String approvalState, String deliveryState, String sourceSnapshot, String actor,
                             String resolutionNote, LocalDateTime createdAt, LocalDateTime updatedAt) { }

    private record PrepaidSnapshot(long balanceMil, long frozenMil) { }

    private record ContractSnapshot(String billingMode, Long creditLimitMil) { }
}
