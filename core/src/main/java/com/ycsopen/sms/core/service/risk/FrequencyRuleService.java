package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.FrequencyRule;
import com.ycsopen.sms.core.repository.FrequencyRuleRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Phase 18: frequency rule management and observable hit analytics. */
@Service
public class FrequencyRuleService {
    private static final Set<String> TYPES = Set.of("MOBILE", "TENANT_LEVEL", "IP", "CONTENT_SIMILARITY");
    private static final Set<String> ACTIONS = Set.of("BLOCK", "DELAY", "ALERT");
    private static final Set<String> SCOPES = Set.of("GLOBAL", "TENANT", "API_KEY");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private final FrequencyRuleRepository repository;
    private final JdbcTemplate jdbc;

    public FrequencyRuleService(FrequencyRuleRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<RuleRow> rules(String name, String type, String action, String status) {
        String normalizedType = optionalEnum(type, TYPES, "FREQUENCY_TYPE_INVALID");
        String normalizedAction = optionalEnum(action, ACTIONS, "FREQUENCY_ACTION_INVALID");
        String normalizedStatus = optionalEnum(status, STATUSES, "FREQUENCY_STATUS_INVALID");
        return repository.findAll().stream()
                .filter(row -> name == null || name.isBlank() || row.getRuleName().contains(name.trim()))
                .filter(row -> normalizedType == null || normalizedType.equals(row.getLimitType().name()))
                .filter(row -> normalizedAction == null || normalizedAction.equals(row.getAction().name()))
                .filter(row -> normalizedStatus == null || normalizedStatus.equals(row.getStatus().name()))
                .sorted((left, right) -> Long.compare(id(right), id(left)))
                .map(this::row)
                .toList();
    }

    @Transactional
    public RuleRow save(RuleRequest request) {
        if (request == null) {
            throw failure("FREQUENCY_RULE_REQUIRED", "频控规则不能为空");
        }
        String ruleName = text(request.ruleName(), "FREQUENCY_RULE_NAME_REQUIRED", 100);
        FrequencyRule.LimitType type = FrequencyRule.LimitType.valueOf(enumValue(request.limitType(), TYPES,
                "FREQUENCY_TYPE_INVALID"));
        FrequencyRule.Action action = FrequencyRule.Action.valueOf(enumValue(request.action(), ACTIONS,
                "FREQUENCY_ACTION_INVALID"));
        FrequencyRule.Scope scope = FrequencyRule.Scope.valueOf(enumValue(request.scope(), SCOPES,
                "FREQUENCY_SCOPE_INVALID"));
        FrequencyRule.Status status = FrequencyRule.Status.valueOf(enumValue(request.status(), STATUSES,
                "FREQUENCY_STATUS_INVALID"));
        int limitCount = positive(request.limitCount(), "FREQUENCY_LIMIT_COUNT_INVALID");
        int windowSeconds = supportedWindow(request.limitWindowSeconds());
        Long scopeRefId = scopeRef(scope, request.scopeRefId());
        FrequencyRule entity = request.id() == null
                ? new FrequencyRule()
                : repository.findById(request.id()).orElseThrow(() ->
                        failure("FREQUENCY_RULE_NOT_FOUND", "频控规则不存在"));
        if (request.id() == null && repository.existsByRuleNameAndLimitTypeAndScopeAndScopeRefIdAndStatus(
                ruleName, type, scope, scopeRefId, FrequencyRule.Status.ACTIVE)) {
            throw failure("FREQUENCY_RULE_DUPLICATE", "同作用域下已有生效频控规则");
        }
        entity.setRuleName(ruleName);
        entity.setLimitType(type);
        entity.setLimitCount(limitCount);
        entity.setLimitWindowSeconds(windowSeconds);
        entity.setAction(action);
        entity.setScope(scope);
        entity.setScopeRefId(scopeRefId);
        entity.setStatus(status);
        if (entity.getHitCount() == null) entity.setHitCount(0L);
        return row(repository.save(entity));
    }

    @Transactional
    public RuleRow disable(long id) {
        FrequencyRule entity = repository.findById(id).orElseThrow(() ->
                failure("FREQUENCY_RULE_NOT_FOUND", "频控规则不存在"));
        entity.setStatus(FrequencyRule.Status.DISABLED);
        return row(repository.save(entity));
    }

    @Transactional
    public RuleRow enable(long id) {
        FrequencyRule entity = repository.findById(id).orElseThrow(() ->
                failure("FREQUENCY_RULE_NOT_FOUND", "频控规则不存在"));
        entity.setStatus(FrequencyRule.Status.ACTIVE);
        return row(repository.save(entity));
    }

    @Transactional
    public ImportResponse importRules(ImportRequest request) {
        if (request == null || request.ruleNames() == null || request.ruleNames().isEmpty()) {
            throw failure("FREQUENCY_IMPORT_REQUIRED", "导入频控规则不能为空");
        }
        if (request.ruleNames().size() > 200) {
            throw failure("FREQUENCY_IMPORT_TOO_LARGE", "单次最多导入200条频控规则");
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (String candidate : request.ruleNames()) {
            try {
                save(new RuleRequest(null, candidate, request.limitType(), request.limitCount(),
                        request.limitWindowSeconds(), request.action(), request.scope(), request.scopeRefId(),
                        "ACTIVE"));
                success++;
            } catch (RuntimeException failure) {
                errors.add((candidate == null ? "" : candidate) + ":" + failure.getMessage());
            }
        }
        return new ImportResponse(success, errors.size(), errors);
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics() {
        long total = repository.count();
        long active = repository.findAllByStatus(FrequencyRule.Status.ACTIVE).size();
        long hits = count("SELECT COUNT(*) FROM frequency_rule_hits WHERE created_at>=CURRENT_DATE");
        long blocked = count("SELECT COUNT(*) FROM frequency_rule_hits WHERE blocked=TRUE AND created_at>=CURRENT_DATE");
        long delayed = count("SELECT COUNT(*) FROM frequency_rule_hits WHERE action='DELAY' AND created_at>=CURRENT_DATE");
        long alerts = count("SELECT COUNT(*) FROM frequency_rule_hits WHERE action='ALERT' AND created_at>=CURRENT_DATE");
        double blockRate = hits == 0 ? 0 : (double) blocked / hits;
        double coverageRate = total == 0 ? 0 : (double) active / total;
        return new AnalyticsResponse(total, active, hits, blocked, delayed, alerts, blockRate, coverageRate);
    }

    @Transactional
    public ExportRequestResponse exportRequest(String name, String type, String action, String status, String actor) {
        int matchedRows = rules(name, type, action, status).size();
        String requestId = "FREQUENCY_EXPORT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO frequency_rule_export_requests
                (request_id, filter_name, filter_type, filter_action, filter_status, matched_rows, actor, status, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, requestId, blankToNull(name), blankToNull(type), blankToNull(action), blankToNull(status),
                matchedRows, actor, "REQUESTED", LocalDateTime.now());
        return new ExportRequestResponse(requestId, matchedRows, "REQUESTED");
    }

    private RuleRow row(FrequencyRule entity) {
        return new RuleRow(entity.getId(), entity.getRuleName(), entity.getLimitType().name(), entity.getLimitCount(),
                entity.getLimitWindowSeconds(), entity.getAction().name(),
                (entity.getScope() == null ? FrequencyRule.Scope.GLOBAL : entity.getScope()).name(),
                entity.getScopeRefId(), entity.getStatus().name(), entity.getHitCount() == null ? 0 : entity.getHitCount(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private long count(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0 : value;
        } catch (RuntimeException failure) {
            return 0L;
        }
    }

    private Long scopeRef(FrequencyRule.Scope scope, Long value) {
        if (scope == FrequencyRule.Scope.GLOBAL) {
            return null;
        }
        if (value == null || value <= 0) {
            throw failure("FREQUENCY_SCOPE_REF_REQUIRED", "租户或 API Key 作用域必须提供有效引用ID");
        }
        return value;
    }

    private int supportedWindow(Integer value) {
        int window = positive(value, "FREQUENCY_WINDOW_INVALID");
        if (window != 1 && window != 60 && window != 3600 && window != 86400) {
            throw failure("FREQUENCY_WINDOW_INVALID", "频控窗口只支持秒、分钟、小时、天");
        }
        return window;
    }

    private int positive(Integer value, String code) {
        if (value == null || value < 1) {
            throw failure(code, "数值必须大于0");
        }
        return value;
    }

    private String optionalEnum(String value, Set<String> allowed, String code) {
        if (value == null || value.isBlank()) return null;
        return enumValue(value, allowed, code);
    }

    private String enumValue(String value, Set<String> allowed, String code) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw failure(code, "枚举值非法：" + value);
        }
        return normalized;
    }

    private String text(String value, String code, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw failure(code, "必填文本不能为空");
        }
        if (trimmed.length() > maxLength) {
            throw failure(code, "文本长度超过限制");
        }
        return trimmed;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long id(FrequencyRule rule) {
        return rule.getId() == null ? 0 : rule.getId();
    }

    private BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public record RuleRow(Long id, String ruleName, String limitType, int limitCount, int limitWindowSeconds,
                          String action, String scope, Long scopeRefId, String status, long hitCount,
                          LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record RuleRequest(Long id, String ruleName, String limitType, Integer limitCount,
                              Integer limitWindowSeconds, String action, String scope, Long scopeRefId,
                              String status) { }

    public record ImportRequest(List<String> ruleNames, String limitType, Integer limitCount,
                                Integer limitWindowSeconds, String action, String scope, Long scopeRefId) { }

    public record ImportResponse(int success, int failed, List<String> errors) { }

    public record AnalyticsResponse(long total, long active, long hits, long blocked,
                                    long delayed, long alerts, double blockRate,
                                    double coverageRate) { }

    public record ExportRequestResponse(String requestId, int matchedRows, String status) { }
}
