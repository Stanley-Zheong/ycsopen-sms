package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.SensitiveWord;
import com.ycsopen.sms.core.repository.SensitiveWordRepository;
import com.ycsopen.sms.core.service.routing.ContentReviewChecker;
import com.ycsopen.sms.core.service.routing.RoutingContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Phase 17: runtime final-content safety management and executable scan preview. */
@Service
public class ContentSafetyService {
    private static final Set<String> CATEGORIES = Set.of("ILLEGAL", "FINANCIAL", "MARKETING", "POLITICAL", "ADULT", "OTHER");
    private static final Set<String> LEVELS = Set.of("HIGH", "MEDIUM", "LOW");
    private static final Set<String> ACTIONS = Set.of("BLOCK", "REPLACE", "ALERT");
    private static final Set<String> SCOPES = Set.of("GLOBAL", "TENANT", "PRODUCT");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private final SensitiveWordRepository repository;
    private final ContentReviewChecker checker;
    private final JdbcTemplate jdbc;

    public ContentSafetyService(SensitiveWordRepository repository, ContentReviewChecker checker, JdbcTemplate jdbc) {
        this.repository = repository;
        this.checker = checker;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PolicyRow> policies(String word, String category, String level, String action, String status) {
        String normalizedCategory = optionalEnum(category, CATEGORIES, "CONTENT_CATEGORY_INVALID");
        String normalizedLevel = optionalEnum(level, LEVELS, "CONTENT_LEVEL_INVALID");
        String normalizedAction = optionalEnum(action, ACTIONS, "CONTENT_ACTION_INVALID");
        String normalizedStatus = optionalEnum(status, STATUSES, "CONTENT_STATUS_INVALID");
        return repository.findAll().stream()
                .filter(row -> word == null || word.isBlank() || row.getWord().contains(word.trim()))
                .filter(row -> normalizedCategory == null || normalizedCategory.equals(row.getCategory().name()))
                .filter(row -> normalizedLevel == null || normalizedLevel.equals(row.getLevel().name()))
                .filter(row -> normalizedAction == null || normalizedAction.equals(row.getAction().name()))
                .filter(row -> normalizedStatus == null || normalizedStatus.equals(row.getStatus().name()))
                .sorted((left, right) -> Long.compare(id(right), id(left)))
                .map(this::row)
                .toList();
    }

    @Transactional
    public PolicyRow save(PolicyRequest request) {
        if (request == null) {
            throw failure("CONTENT_POLICY_REQUIRED", "内容审核策略不能为空");
        }
        String word = text(request.word(), "CONTENT_WORD_REQUIRED", 128);
        SensitiveWord.Category category = SensitiveWord.Category.valueOf(enumValue(request.category(), CATEGORIES, "CONTENT_CATEGORY_INVALID"));
        SensitiveWord.Level level = SensitiveWord.Level.valueOf(enumValue(request.level(), LEVELS, "CONTENT_LEVEL_INVALID"));
        SensitiveWord.Action action = SensitiveWord.Action.valueOf(enumValue(request.action(), ACTIONS, "CONTENT_ACTION_INVALID"));
        SensitiveWord.Scope scope = SensitiveWord.Scope.valueOf(enumValue(request.scope(), SCOPES, "CONTENT_SCOPE_INVALID"));
        SensitiveWord.Status status = SensitiveWord.Status.valueOf(enumValue(request.status(), STATUSES, "CONTENT_STATUS_INVALID"));
        Long scopeRefId = scopeRef(scope, request.scopeRefId());
        String replacement = request.replacement() == null ? null : request.replacement().trim();
        if (action == SensitiveWord.Action.REPLACE && (replacement == null || replacement.isBlank())) {
            replacement = "***";
        }
        SensitiveWord entity = request.id() == null
                ? new SensitiveWord()
                : repository.findById(request.id()).orElseThrow(() ->
                        failure("CONTENT_POLICY_NOT_FOUND", "内容审核策略不存在"));
        if (request.id() == null && repository.existsByWordAndScopeAndScopeRefIdAndStatus(
                word, scope, scopeRefId, SensitiveWord.Status.ACTIVE)) {
            throw failure("CONTENT_POLICY_DUPLICATE", "同作用域下已有生效词库策略");
        }
        entity.setWord(word);
        entity.setCategory(category);
        entity.setLevel(level);
        entity.setAction(action);
        entity.setReplacement(replacement);
        entity.setScope(scope);
        entity.setScopeRefId(scopeRefId);
        entity.setStatus(status);
        if (entity.getHitCount() == null) entity.setHitCount(0L);
        return row(repository.save(entity));
    }

    @Transactional
    public PolicyRow disable(long id) {
        SensitiveWord entity = repository.findById(id).orElseThrow(() ->
                failure("CONTENT_POLICY_NOT_FOUND", "内容审核策略不存在"));
        entity.setStatus(SensitiveWord.Status.DISABLED);
        return row(repository.save(entity));
    }

    @Transactional
    public ImportResponse importPolicies(ImportRequest request) {
        if (request == null || request.words() == null || request.words().isEmpty()) {
            throw failure("CONTENT_IMPORT_REQUIRED", "导入词库不能为空");
        }
        if (request.words().size() > 500) {
            throw failure("CONTENT_IMPORT_TOO_LARGE", "单次最多导入500条词库");
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (String candidate : request.words()) {
            try {
                save(new PolicyRequest(null, candidate, request.category(), request.level(), request.replacement(),
                        request.action(), request.scope(), request.scopeRefId(), "ACTIVE"));
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
        long active = repository.findAllByStatus(SensitiveWord.Status.ACTIVE).size();
        Long hits = count("SELECT COUNT(*) FROM content_safety_hits WHERE created_at>=CURRENT_DATE");
        Long intercepts = count("SELECT COUNT(*) FROM content_safety_hits WHERE blocked=TRUE AND created_at>=CURRENT_DATE");
        Long replacements = count("SELECT COUNT(*) FROM content_safety_hits WHERE action='REPLACE' AND created_at>=CURRENT_DATE");
        Long alerts = count("SELECT COUNT(*) FROM content_safety_hits WHERE action='ALERT' AND created_at>=CURRENT_DATE");
        double interceptRate = hits == 0 ? 0 : (double) intercepts / hits;
        double coverageRate = total == 0 ? 0 : (double) active / total;
        return new AnalyticsResponse(total, active, hits, intercepts, replacements, alerts, interceptRate, coverageRate);
    }

    @Transactional(readOnly = true)
    public ScanResponse scan(ScanRequest request) {
        if (request == null) {
            throw failure("CONTENT_SCAN_REQUIRED", "试扫请求不能为空");
        }
        String content = text(request.content(), "CONTENT_SCAN_CONTENT_REQUIRED", 500);
        var result = checker.preview(RoutingContext.builder()
                .tenantId(request.tenantId())
                .templateId(request.templateId())
                .content(content)
                .build());
        return new ScanResponse(result.blocked(), result.reason(), result.finalContent());
    }

    @Transactional
    public ExportRequestResponse exportRequest(String word, String category, String action, String status, String actor) {
        int matchedRows = policies(word, category, null, action, status).size();
        String requestId = "CONTENT_EXPORT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO content_safety_export_requests
                (request_id, filter_word, filter_category, filter_action, filter_status, matched_rows, actor, status, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, requestId, blankToNull(word), blankToNull(category), blankToNull(action), blankToNull(status),
                matchedRows, actor, "REQUESTED", LocalDateTime.now());
        return new ExportRequestResponse(requestId, matchedRows, "REQUESTED");
    }

    private PolicyRow row(SensitiveWord entity) {
        return new PolicyRow(entity.getId(), entity.getWord(), entity.getCategory().name(), entity.getLevel().name(),
                entity.getReplacement(), entity.getAction().name(), entity.getScope().name(), entity.getScopeRefId(),
                entity.getStatus().name(), entity.getHitCount() == null ? 0 : entity.getHitCount(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private Long count(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0 : value;
        } catch (RuntimeException failure) {
            return 0L;
        }
    }

    private Long scopeRef(SensitiveWord.Scope scope, Long value) {
        if (scope == SensitiveWord.Scope.GLOBAL) {
            return null;
        }
        if (value == null || value <= 0) {
            throw failure("CONTENT_SCOPE_REF_REQUIRED", "机构或产品作用域必须提供有效引用ID");
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

    private long id(SensitiveWord word) {
        return word.getId() == null ? 0 : word.getId();
    }

    private BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public record PolicyRow(Long id, String word, String category, String level, String replacement, String action,
                            String scope, Long scopeRefId, String status, long hitCount,
                            LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record PolicyRequest(Long id, String word, String category, String level, String replacement,
                                String action, String scope, Long scopeRefId, String status) { }

    public record ImportRequest(List<String> words, String category, String level, String replacement,
                                String action, String scope, Long scopeRefId) { }

    public record ImportResponse(int success, int failed, List<String> errors) { }

    public record AnalyticsResponse(long total, long active, long hits, long intercepts,
                                    long replacements, long alerts, double interceptRate,
                                    double coverageRate) { }

    public record ScanRequest(Long tenantId, Long templateId, String content) { }

    public record ScanResponse(boolean blocked, String reason, String finalContent) { }

    public record ExportRequestResponse(String requestId, int matchedRows, String status) { }
}
