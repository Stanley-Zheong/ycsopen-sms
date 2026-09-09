package com.ycsopen.sms.core.service.tool;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Phase 20: one effective provider status taxonomy for connector, receipt, retry, billing and analytics consumers. */
@Service
public class ProviderStatusTaxonomyService implements ProviderStatusTaxonomyPort {
    private static final Set<String> PROTOCOLS = Set.of("HTTP", "CMPP", "SGIP", "SMGP");
    private static final Set<String> CATEGORIES = Set.of("SUCCESS", "FAILURE", "PENDING", "UNKNOWN_REVIEW_REQUIRED");
    private static final Set<String> SEVERITIES = Set.of("INFO", "WARN", "ERROR", "CRITICAL");

    private final JdbcTemplate jdbc;

    public ProviderStatusTaxonomyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ImportResponse importMappings(ImportRequest request, String actor) {
        if (request == null || request.rows() == null || request.rows().isEmpty()) {
            throw failure("STATUS_IMPORT_REQUIRED", "状态码映射不能为空");
        }
        String versionNo = request.versionNo() == null || request.versionNo().isBlank()
                ? "ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT)
                : text(request.versionNo(), "STATUS_VERSION_INVALID", 32);
        String source = text(request.sourceName(), "STATUS_SOURCE_REQUIRED", 64);
        List<ValidatedMapping> validRows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (MappingRow row : request.rows()) {
            try {
                validRows.add(new ValidatedMapping(
                        text(row.providerName(), "STATUS_PROVIDER_REQUIRED", 64),
                        enumValue(row.protocol(), PROTOCOLS, "STATUS_PROTOCOL_INVALID"),
                        text(row.providerCode(), "STATUS_CODE_REQUIRED", 64),
                        enumValue(row.platformCategory(), CATEGORIES, "STATUS_CATEGORY_INVALID"),
                        row.finalState(), row.billable(), row.retryable(),
                        enumValue(row.severity(), SEVERITIES, "STATUS_SEVERITY_INVALID"),
                        text(row.advice(), "STATUS_ADVICE_REQUIRED", 255)));
            } catch (RuntimeException failure) {
                errors.add((row == null ? "" : row.providerCode()) + ":" + failure.getMessage());
            }
        }
        if (validRows.isEmpty()) {
            throw failure("STATUS_IMPORT_NO_VALID_ROWS", "状态码导入没有有效映射，保留当前生效版本");
        }
        jdbc.update("UPDATE provider_status_versions SET status='SUPERSEDED' WHERE status='ACTIVE'");
        jdbc.update("UPDATE provider_status_mappings SET status='SUPERSEDED' WHERE status='ACTIVE'");
        jdbc.update("""
                INSERT INTO provider_status_versions(version_no, status, source_name, effective_at, conflict_count, actor)
                VALUES (?, 'ACTIVE', ?, ?, 0, ?)
                """, versionNo, source, request.effectiveAt() == null ? LocalDateTime.now() : request.effectiveAt(), actor(actor));
        Long versionId = jdbc.queryForObject("SELECT id FROM provider_status_versions WHERE version_no=?", Long.class, versionNo);
        if (versionId == null) throw failure("STATUS_VERSION_CREATE_FAILED", "状态码版本创建失败");
        for (ValidatedMapping row : validRows) {
            jdbc.update("""
                    INSERT INTO provider_status_mappings(version_id, provider_name, protocol, provider_code,
                        platform_category, final_state, billable, retryable, severity, advice, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?, 'ACTIVE')
                    """, versionId, row.providerName(), row.protocol(), row.providerCode(),
                    row.platformCategory(), row.finalState(), row.billable(), row.retryable(),
                    row.severity(), row.advice());
        }
        jdbc.update("UPDATE provider_status_versions SET conflict_count=? WHERE id=?", errors.size(), versionId);
        return new ImportResponse(versionNo, validRows.size(), errors.size(), errors);
    }

    @Override
    @Transactional
    public NormalizedStatus normalize(String providerName, String protocol, String providerCode) {
        String normalizedProvider = text(providerName, "STATUS_PROVIDER_REQUIRED", 64);
        String normalizedProtocol = enumValue(protocol, PROTOCOLS, "STATUS_PROTOCOL_INVALID");
        String normalizedCode = text(providerCode, "STATUS_CODE_REQUIRED", 64);
        List<NormalizedStatus> rows = jdbc.query("""
                SELECT v.version_no, m.platform_category, m.final_state, m.billable, m.retryable, m.severity, m.advice
                FROM provider_status_mappings m
                JOIN provider_status_versions v ON v.id=m.version_id
                WHERE m.status='ACTIVE' AND v.status='ACTIVE'
                  AND m.provider_name=? AND m.protocol=? AND m.provider_code=?
                ORDER BY v.effective_at DESC, m.id DESC
                LIMIT 1
                """, (rs, row) -> new NormalizedStatus(normalizedProvider, normalizedProtocol, normalizedCode,
                rs.getString("version_no"), rs.getString("platform_category"), rs.getBoolean("final_state"),
                rs.getBoolean("billable"), rs.getBoolean("retryable"), rs.getString("severity"),
                rs.getString("advice"), "MAPPED"), normalizedProvider, normalizedProtocol, normalizedCode);
        NormalizedStatus result = rows.isEmpty()
                ? new NormalizedStatus(normalizedProvider, normalizedProtocol, normalizedCode, null,
                "UNKNOWN_REVIEW_REQUIRED", false, false, false, "WARN",
                "未知状态码，进入人工映射队列，不确认最终态、不计费、不自动重试", "UNKNOWN_SAFE_FALLBACK")
                : rows.get(0);
        jdbc.update("""
                INSERT INTO provider_status_normalization_events(provider_name, protocol, provider_code, version_no,
                    platform_category, final_state, billable, retryable, severity, advice, source)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, result.providerName(), result.protocol(), result.providerCode(), result.versionNo(),
                result.platformCategory(), result.finalState(), result.billable(), result.retryable(),
                result.severity(), result.advice(), result.source());
        return result;
    }

    @Transactional(readOnly = true)
    public List<VersionRow> versions() {
        return jdbc.query("""
                SELECT id, version_no, status, source_name, effective_at, conflict_count, actor, created_at
                FROM provider_status_versions
                ORDER BY effective_at DESC, id DESC
                LIMIT 20
                """, (rs, row) -> new VersionRow(rs.getLong("id"), rs.getString("version_no"),
                rs.getString("status"), rs.getString("source_name"), rs.getTimestamp("effective_at").toLocalDateTime(),
                rs.getInt("conflict_count"), rs.getString("actor"), rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Transactional(readOnly = true)
    public List<MappingView> mappings() {
        return jdbc.query("""
                SELECT provider_name, protocol, provider_code, platform_category, final_state, billable,
                       retryable, severity, advice
                FROM provider_status_mappings
                WHERE status='ACTIVE'
                ORDER BY provider_name, protocol, provider_code
                LIMIT 200
                """, (rs, row) -> new MappingView(rs.getString("provider_name"), rs.getString("protocol"),
                rs.getString("provider_code"), rs.getString("platform_category"), rs.getBoolean("final_state"),
                rs.getBoolean("billable"), rs.getBoolean("retryable"), rs.getString("severity"),
                rs.getString("advice")));
    }

    @Transactional
    public ExportResponse exportRequest(String providerName, String protocol, String actor) {
        int matched = mappings().stream()
                .filter(row -> providerName == null || providerName.isBlank() || row.providerName().equals(providerName.trim()))
                .filter(row -> protocol == null || protocol.isBlank() || row.protocol().equals(protocol.trim().toUpperCase(Locale.ROOT)))
                .toList().size();
        String requestId = "STATUS_EXPORT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO provider_status_export_requests(request_id, provider_name, protocol, matched_rows, actor, status)
                VALUES (?,?,?,?,?, 'REQUESTED')
                """, requestId, blankToNull(providerName), blankToNull(protocol), matched, actor(actor));
        return new ExportResponse(requestId, matched, "REQUESTED");
    }

    private String enumValue(String value, Set<String> allowed, String code) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw failure(code, "枚举值非法：" + value);
        return normalized;
    }

    private String text(String value, String code, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) throw failure(code, "文本不能为空且不能超过长度限制");
        return trimmed;
    }

    private String actor(String value) {
        return text(value, "ACTOR_REQUIRED", 64);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public record MappingRow(String providerName, String protocol, String providerCode, String platformCategory,
                             boolean finalState, boolean billable, boolean retryable, String severity, String advice) { }
    private record ValidatedMapping(String providerName, String protocol, String providerCode, String platformCategory,
                                    boolean finalState, boolean billable, boolean retryable, String severity,
                                    String advice) { }
    public record ImportRequest(String versionNo, String sourceName, LocalDateTime effectiveAt, List<MappingRow> rows) { }
    public record ImportResponse(String versionNo, int success, int failed, List<String> errors) { }
    public record VersionRow(Long id, String versionNo, String status, String sourceName, LocalDateTime effectiveAt,
                             int conflictCount, String actor, LocalDateTime createdAt) { }
    public record MappingView(String providerName, String protocol, String providerCode, String platformCategory,
                              boolean finalState, boolean billable, boolean retryable, String severity, String advice) { }
    public record ExportResponse(String requestId, int matchedRows, String status) { }
}
