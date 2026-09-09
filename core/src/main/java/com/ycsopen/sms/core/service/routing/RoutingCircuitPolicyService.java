package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Phase 21: versioned routing, circuit, and retry policy contract. */
@Service
public class RoutingCircuitPolicyService {
    private static final Set<String> CONDITIONS = Set.of("CARRIER", "PREFIX", "TENANT", "CONTENT_KEYWORD", "TIME");
    private static final Set<String> TARGETS = Set.of("CHANNEL", "POOL", "WEIGHT", "DEFAULT");
    private static final int OPEN_THRESHOLD = 3;

    private final JdbcTemplate jdbc;

    public RoutingCircuitPolicyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public PolicyImportResponse importPolicy(PolicyImportRequest request, String actor) {
        if (request == null || request.rules() == null || request.rules().isEmpty()) {
            throw failure("ROUTING_POLICY_REQUIRED", "路由策略不能为空");
        }
        String versionNo = text(request.versionNo(), "ROUTING_VERSION_REQUIRED", 32);
        String source = text(request.sourceName(), "ROUTING_SOURCE_REQUIRED", 64);
        List<PolicyRule> valid = request.rules().stream()
                .map(row -> new PolicyRule(row.priority(),
                        enumValue(row.conditionType(), CONDITIONS, "ROUTING_CONDITION_INVALID"),
                        text(row.conditionValue(), "ROUTING_CONDITION_VALUE_REQUIRED", 128),
                        enumValue(row.targetType(), TARGETS, "ROUTING_TARGET_INVALID"),
                        text(row.targetRef(), "ROUTING_TARGET_REF_REQUIRED", 128),
                        Math.max(0, row.weight())))
                .sorted(Comparator.comparingInt(PolicyRule::priority))
                .toList();
        jdbc.update("UPDATE routing_policy_versions SET status='SUPERSEDED' WHERE status='ACTIVE'");
        jdbc.update("UPDATE routing_policy_rules SET status='SUPERSEDED' WHERE status='ACTIVE'");
        jdbc.update("""
                INSERT INTO routing_policy_versions(version_no,status,source_name,effective_at,actor)
                VALUES (?,'ACTIVE',?,?,?)
                """, versionNo, source, request.effectiveAt() == null ? LocalDateTime.now() : request.effectiveAt(), actor(actor));
        Long versionId = jdbc.queryForObject("SELECT id FROM routing_policy_versions WHERE version_no=?", Long.class, versionNo);
        if (versionId == null) throw failure("ROUTING_VERSION_CREATE_FAILED", "路由版本创建失败");
        for (PolicyRule row : valid) {
            jdbc.update("""
                    INSERT INTO routing_policy_rules(version_id,priority,condition_type,condition_value,target_type,target_ref,weight,status)
                    VALUES (?,?,?,?,?,?,?,'ACTIVE')
                    """, versionId, row.priority(), row.conditionType(), row.conditionValue(), row.targetType(), row.targetRef(), row.weight());
        }
        return new PolicyImportResponse(versionNo, valid.size(), "ACTIVE");
    }

    @Transactional(readOnly = true)
    public List<PolicyVersion> versions() {
        return jdbc.query("""
                SELECT id,version_no,status,source_name,effective_at,actor,created_at
                FROM routing_policy_versions ORDER BY effective_at DESC,id DESC LIMIT 20
                """, (rs, row) -> new PolicyVersion(rs.getLong("id"), rs.getString("version_no"), rs.getString("status"),
                rs.getString("source_name"), rs.getTimestamp("effective_at").toLocalDateTime(),
                rs.getString("actor"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Transactional(readOnly = true)
    public List<PolicyRuleView> rules() {
        return jdbc.query("""
                SELECT r.id,v.version_no,r.priority,r.condition_type,r.condition_value,r.target_type,r.target_ref,r.weight,r.status
                FROM routing_policy_rules r JOIN routing_policy_versions v ON v.id=r.version_id
                WHERE r.status='ACTIVE' AND v.status='ACTIVE'
                ORDER BY r.priority ASC,r.id ASC LIMIT 200
                """, (rs, row) -> new PolicyRuleView(rs.getLong("id"), rs.getString("version_no"),
                rs.getInt("priority"), rs.getString("condition_type"), rs.getString("condition_value"),
                rs.getString("target_type"), rs.getString("target_ref"), rs.getInt("weight"), rs.getString("status")));
    }

    @Transactional
    public SimulationResult simulate(SimulationRequest request) {
        SimulationInput input = new SimulationInput(request == null ? null : request.tenantId(),
                safeUpper(request == null ? null : request.carrier()),
                trim(request == null ? null : request.prefix()),
                trim(request == null ? null : request.content()),
                safeUpper(request == null ? null : request.normalizedCategory()));
        List<PolicyRuleView> candidates = rules().stream().filter(rule -> matches(rule, input)).toList();
        Selection selection = candidates.stream()
                .map(this::selectIfEligible)
                .filter(candidate -> candidate.eligible())
                .findFirst()
                .orElse(new Selection(null, "DEFAULT", "DEFAULT_CHANNEL", true, "no-match default target"));
        RetryPolicy retry = retryPolicy(input.normalizedCategory().isBlank() ? "FAILURE" : input.normalizedCategory());
        String versionNo = candidates.isEmpty() ? null : candidates.getFirst().versionNo();
        Long matchedRuleId = selection.rule() == null ? null : selection.rule().id();
        jdbc.update("""
                INSERT INTO routing_decision_history(version_no,tenant_id,carrier,prefix,content_keyword,target_type,target_ref,
                    matched_rule_id,explanation,circuit_status,retry_policy)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, versionNo, input.tenantId(), input.carrier(), input.prefix(), keyword(input.content()),
                selection.targetType(), selection.targetRef(), matchedRuleId, selection.explanation(),
                circuitStatus(selection.targetRef()), retry.normalizedCategory() + ":" + retry.maxAttempts());
        return new SimulationResult(versionNo, matchedRuleId, selection.targetType(), selection.targetRef(),
                selection.explanation(), circuitStatus(selection.targetRef()), retry);
    }

    @Transactional
    public CircuitState recordCircuit(String channelCode, boolean success, int latencyMs) {
        String code = text(channelCode, "ROUTING_CHANNEL_REQUIRED", 64);
        CircuitState current = circuitStates().stream()
                .filter(row -> row.channelCode().equals(code))
                .findFirst()
                .orElse(new CircuitState(0L, code, "CLOSED", 0, 0, 0, "init"));
        int failures = success ? 0 : current.failureCount() + 1;
        int successes = success ? current.successCount() + 1 : current.successCount();
        String status = failures >= OPEN_THRESHOLD ? "OPEN" : success && "OPEN".equals(current.status()) ? "HALF_OPEN" : current.status();
        if ("HALF_OPEN".equals(status) && successes >= 1) status = "CLOSED";
        String history = (current.history() + " -> " + (success ? "success" : "failure") + ":" + status);
        jdbc.update("""
                MERGE INTO routing_circuit_states(channel_code,status,failure_count,success_count,latency_ms,opened_at,history,updated_at)
                KEY(channel_code) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, code, status, failures, successes, Math.max(0, latencyMs),
                "OPEN".equals(status) ? LocalDateTime.now() : null, text(history, "ROUTING_CIRCUIT_HISTORY_INVALID", 512));
        return circuitStates().stream().filter(row -> row.channelCode().equals(code)).findFirst()
                .orElseThrow(() -> failure("ROUTING_CIRCUIT_SAVE_FAILED", "熔断状态保存失败"));
    }

    @Transactional(readOnly = true)
    public List<CircuitState> circuitStates() {
        return jdbc.query("""
                SELECT id,channel_code,status,failure_count,success_count,latency_ms,history
                FROM routing_circuit_states ORDER BY updated_at DESC,id DESC LIMIT 100
                """, (rs, row) -> new CircuitState(rs.getLong("id"), rs.getString("channel_code"),
                rs.getString("status"), rs.getInt("failure_count"), rs.getInt("success_count"),
                rs.getInt("latency_ms"), rs.getString("history")));
    }

    @Transactional
    public RetryPolicy saveRetryPolicy(RetryPolicy request) {
        String category = enumValue(request.normalizedCategory(), Set.of("SUCCESS", "FAILURE", "PENDING", "UNKNOWN_REVIEW_REQUIRED"),
                "ROUTING_RETRY_CATEGORY_INVALID");
        jdbc.update("""
                MERGE INTO routing_retry_rules(normalized_category,retryable,delay_seconds,max_attempts,status,updated_at)
                KEY(normalized_category) VALUES (?,?,?,?, 'ACTIVE', CURRENT_TIMESTAMP)
                """, category, request.retryable(), Math.max(0, request.delaySeconds()), Math.max(0, request.maxAttempts()));
        return retryPolicy(category);
    }

    @Transactional(readOnly = true)
    public RetryPolicy retryPolicy(String normalizedCategory) {
        String category = safeUpper(normalizedCategory);
        return jdbc.query("""
                SELECT normalized_category,retryable,delay_seconds,max_attempts,status
                FROM routing_retry_rules WHERE normalized_category=? AND status='ACTIVE'
                """, (rs, row) -> new RetryPolicy(rs.getString("normalized_category"), rs.getBoolean("retryable"),
                rs.getInt("delay_seconds"), rs.getInt("max_attempts"), rs.getString("status")), category)
                .stream().findFirst().orElse(new RetryPolicy(category.isBlank() ? "FAILURE" : category,
                        !"SUCCESS".equals(category), 30, "SUCCESS".equals(category) ? 0 : 3, "DEFAULT"));
    }

    private Selection selectIfEligible(PolicyRuleView rule) {
        String status = circuitStatus(rule.targetRef());
        if ("OPEN".equals(status)) {
            return new Selection(rule, rule.targetType(), rule.targetRef(), false, "rule " + rule.id() + " skipped: circuit open");
        }
        return new Selection(rule, rule.targetType(), rule.targetRef(), true,
                "rule " + rule.id() + " matched version " + rule.versionNo() + " target " + rule.targetType() + "/" + rule.targetRef());
    }

    private boolean matches(PolicyRuleView rule, SimulationInput input) {
        return switch (rule.conditionType()) {
            case "CARRIER" -> rule.conditionValue().equals(input.carrier());
            case "PREFIX" -> input.prefix().startsWith(rule.conditionValue());
            case "TENANT" -> input.tenantId() != null && rule.conditionValue().equals(String.valueOf(input.tenantId()));
            case "CONTENT_KEYWORD" -> !input.content().isBlank() && input.content().contains(rule.conditionValue());
            case "TIME" -> true;
            default -> false;
        };
    }

    private String circuitStatus(String targetRef) {
        return circuitStates().stream()
                .filter(row -> row.channelCode().equals(targetRef))
                .map(CircuitState::status)
                .findFirst()
                .orElse("CLOSED");
    }

    private String keyword(String content) {
        return content.length() > 64 ? content.substring(0, 64) : content;
    }

    private String text(String value, String code, int max) {
        String trimmed = trim(value);
        if (trimmed.isEmpty() || trimmed.length() > max) throw failure(code, "文本不能为空且不能超过长度限制");
        return trimmed;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String safeUpper(String value) {
        return trim(value).toUpperCase(Locale.ROOT);
    }

    private String enumValue(String value, Set<String> allowed, String code) {
        String normalized = safeUpper(value);
        if (!allowed.contains(normalized)) throw failure(code, "枚举值非法：" + value);
        return normalized;
    }

    private String actor(String value) {
        return text(value, "ACTOR_REQUIRED", 64);
    }

    private BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public record PolicyRule(int priority, String conditionType, String conditionValue, String targetType,
                             String targetRef, int weight) { }
    public record PolicyImportRequest(String versionNo, String sourceName, LocalDateTime effectiveAt,
                                      List<PolicyRule> rules) { }
    public record PolicyImportResponse(String versionNo, int imported, String status) { }
    public record PolicyVersion(Long id, String versionNo, String status, String sourceName,
                                LocalDateTime effectiveAt, String actor, LocalDateTime createdAt) { }
    public record PolicyRuleView(Long id, String versionNo, int priority, String conditionType, String conditionValue,
                                 String targetType, String targetRef, int weight, String status) { }
    public record SimulationRequest(Long tenantId, String carrier, String prefix, String content,
                                    String normalizedCategory) { }
    private record SimulationInput(Long tenantId, String carrier, String prefix, String content,
                                   String normalizedCategory) { }
    private record Selection(PolicyRuleView rule, String targetType, String targetRef, boolean eligible,
                             String explanation) { }
    public record SimulationResult(String versionNo, Long matchedRuleId, String targetType, String targetRef,
                                   String explanation, String circuitStatus, RetryPolicy retryPolicy) { }
    public record CircuitState(Long id, String channelCode, String status, int failureCount, int successCount,
                               int latencyMs, String history) { }
    public record RetryPolicy(String normalizedCategory, boolean retryable, int delaySeconds, int maxAttempts,
                              String status) { }
}

