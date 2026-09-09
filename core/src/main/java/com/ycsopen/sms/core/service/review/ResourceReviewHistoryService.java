package com.ycsopen.sms.core.service.review;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.ResourceReviewHistoryItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Phase 15: read-only normalized review-history projection. */
@Service
public class ResourceReviewHistoryService {
    private static final Set<String> RESOURCE_TYPES = Set.of("SIGNATURE", "TEMPLATE", "EXEMPTION");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final int MAX_PAGE = 10_000;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private final JdbcTemplate jdbc;

    public ResourceReviewHistoryService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional(readOnly = true)
    public List<ResourceReviewHistoryItem> search(String resourceType, String tenantId, String decisionState,
                                                  String actor, String riskLevel, String keyword,
                                                  String createdFrom, String createdTo) {
        return search(resourceType, tenantId, decisionState, actor, riskLevel, keyword, createdFrom, createdTo, null, null);
    }

    @Transactional(readOnly = true)
    public List<ResourceReviewHistoryItem> search(String resourceType, String tenantId, String decisionState,
                                                  String actor, String riskLevel, String keyword,
                                                  String createdFrom, String createdTo, String page, String pageSize) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM (
                    SELECT h.id AS history_id, 'SIGNATURE' AS resource_type, s.id AS resource_id,
                           s.sign_code AS resource_code, 'v1' AS resource_version, s.tenant_id,
                           h.event_type AS decision_state, h.actor, h.opinion AS reason,
                           h.risk_level, s.evidence_url AS evidence_ref, s.sign_content AS submitted_snapshot,
                           CONCAT('/admin/signatures/review?keyword=', s.sign_code) AS lifecycle_link,
                           h.created_at
                    FROM signature_review_history h
                    JOIN signatures s ON s.id = h.signature_id
                    UNION ALL
                    SELECT h.id AS history_id, 'TEMPLATE' AS resource_type, t.id AS resource_id,
                           t.template_code AS resource_code, CONCAT('v', t.version_no) AS resource_version, t.tenant_id,
                           h.event_type AS decision_state, h.actor, h.opinion AS reason,
                           NULL AS risk_level, CONCAT('signature:', t.signature_id) AS evidence_ref,
                           h.snapshot_content AS submitted_snapshot,
                           CONCAT('/admin/templates/review?keyword=', t.template_code) AS lifecycle_link,
                           h.created_at
                    FROM template_review_history h
                    JOIN templates t ON t.id = h.template_id
                    UNION ALL
                    SELECT h.id AS history_id, 'EXEMPTION' AS resource_type, h.exempt_rule_id AS resource_id,
                           h.subject_id AS resource_code, CONCAT('v', h.version_no) AS resource_version, h.tenant_id,
                           h.result AS decision_state, h.actor, h.reason,
                           NULL AS risk_level, CONCAT(h.product_code, ' ', h.scope_expression) AS evidence_ref,
                           CONCAT(h.subject_type, ':', h.subject_id) AS submitted_snapshot,
                           '/admin/exemption/policy' AS lifecycle_link,
                           h.created_at
                    FROM exempt_rule_history h
                ) review_history
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        appendFilters(sql, args, resourceType, tenantId, decisionState, actor, riskLevel, keyword, createdFrom, createdTo);
        sql.append(" ORDER BY created_at DESC, resource_type, history_id DESC");
        int safePage = boundedPage(page);
        int safePageSize = boundedPageSize(pageSize);
        sql.append(" LIMIT ? OFFSET ?");
        args.add(safePageSize);
        args.add(safePage * safePageSize);
        return jdbc.query(sql.toString(), this::mapRow, args.toArray());
    }

    @Transactional(readOnly = true)
    public ResourceReviewHistoryItem detail(String decisionId) {
        if (decisionId == null || decisionId.isBlank() || !decisionId.contains(":")) {
            throw failure("REVIEW_HISTORY_DECISION_ID_INVALID", "审核历史编号不合法");
        }
        String[] parts = decisionId.trim().split(":", 2);
        String type = enumValue(parts[0], RESOURCE_TYPES, "REVIEW_HISTORY_TYPE_INVALID");
        long historyId;
        try {
            historyId = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            throw failure("REVIEW_HISTORY_DECISION_ID_INVALID", "审核历史编号不合法");
        }
        List<ResourceReviewHistoryItem> rows = jdbc.query(detailSql(type), this::mapRow, historyId);
        if (rows.isEmpty()) {
            throw failure("REVIEW_HISTORY_NOT_FOUND", "审核历史不存在");
        }
        return rows.getFirst();
    }

    private ResourceReviewHistoryItem mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ResourceReviewHistoryItem(
                rs.getString("resource_type") + ":" + rs.getLong("history_id"),
                rs.getString("resource_type"),
                rs.getLong("resource_id"),
                rs.getString("resource_code"),
                rs.getString("resource_version"),
                rs.getLong("tenant_id"),
                rs.getString("decision_state"),
                rs.getString("actor"),
                rs.getString("reason"),
                rs.getString("risk_level"),
                rs.getString("evidence_ref"),
                rs.getString("submitted_snapshot"),
                rs.getString("lifecycle_link"),
                rs.getTimestamp("created_at").toLocalDateTime()
        );
    }

    private void appendFilters(StringBuilder sql, List<Object> args, String resourceType, String tenantId,
                               String decisionState, String actor, String riskLevel, String keyword,
                               String createdFrom, String createdTo) {
        if (resourceType != null && !resourceType.isBlank()) {
            sql.append(" AND resource_type=?");
            args.add(enumValue(resourceType, RESOURCE_TYPES, "REVIEW_HISTORY_TYPE_INVALID"));
        }
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND tenant_id=?");
            args.add(longValue(tenantId, "REVIEW_HISTORY_TENANT_ID_INVALID"));
        }
        if (decisionState != null && !decisionState.isBlank()) {
            sql.append(" AND decision_state=?");
            args.add(decisionState.trim().toUpperCase(Locale.ROOT));
        }
        if (actor != null && !actor.isBlank()) {
            sql.append(" AND actor LIKE ?");
            args.add("%" + actor.trim() + "%");
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            sql.append(" AND risk_level=?");
            args.add(enumValue(riskLevel, RISK_LEVELS, "REVIEW_HISTORY_RISK_INVALID"));
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (resource_code LIKE ? OR submitted_snapshot LIKE ? OR reason LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value);
            args.add(value);
            args.add(value);
        }
        if (createdFrom != null && !createdFrom.isBlank()) {
            sql.append(" AND created_at>=?");
            args.add(timestampValue(createdFrom, "REVIEW_HISTORY_CREATED_FROM_INVALID"));
        }
        if (createdTo != null && !createdTo.isBlank()) {
            sql.append(" AND created_at<=?");
            args.add(timestampValue(createdTo, "REVIEW_HISTORY_CREATED_TO_INVALID"));
        }
    }

    private static int boundedPage(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw failure("REVIEW_HISTORY_PAGE_INVALID", "分页参数不合法");
        }
        if (parsed < 0 || parsed > MAX_PAGE) {
            throw failure("REVIEW_HISTORY_PAGE_INVALID", "分页参数不合法");
        }
        return parsed;
    }

    private static int boundedPageSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAGE_SIZE;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw failure("REVIEW_HISTORY_PAGE_SIZE_INVALID", "分页参数不合法");
        }
        if (parsed < 1 || parsed > MAX_PAGE_SIZE) {
            throw failure("REVIEW_HISTORY_PAGE_SIZE_INVALID", "分页参数不合法");
        }
        return parsed;
    }

    private static String detailSql(String resourceType) {
        return switch (resourceType) {
            case "SIGNATURE" -> """
                    SELECT h.id AS history_id, 'SIGNATURE' AS resource_type, s.id AS resource_id,
                           s.sign_code AS resource_code, 'v1' AS resource_version, s.tenant_id,
                           h.event_type AS decision_state, h.actor, h.opinion AS reason,
                           h.risk_level, s.evidence_url AS evidence_ref, s.sign_content AS submitted_snapshot,
                           CONCAT('/admin/signatures/review?keyword=', s.sign_code) AS lifecycle_link,
                           h.created_at
                    FROM signature_review_history h
                    JOIN signatures s ON s.id = h.signature_id
                    WHERE h.id=?
                    """;
            case "TEMPLATE" -> """
                    SELECT h.id AS history_id, 'TEMPLATE' AS resource_type, t.id AS resource_id,
                           t.template_code AS resource_code, CONCAT('v', t.version_no) AS resource_version, t.tenant_id,
                           h.event_type AS decision_state, h.actor, h.opinion AS reason,
                           NULL AS risk_level, CONCAT('signature:', t.signature_id) AS evidence_ref,
                           h.snapshot_content AS submitted_snapshot,
                           CONCAT('/admin/templates/review?keyword=', t.template_code) AS lifecycle_link,
                           h.created_at
                    FROM template_review_history h
                    JOIN templates t ON t.id = h.template_id
                    WHERE h.id=?
                    """;
            case "EXEMPTION" -> """
                    SELECT h.id AS history_id, 'EXEMPTION' AS resource_type, h.exempt_rule_id AS resource_id,
                           h.subject_id AS resource_code, CONCAT('v', h.version_no) AS resource_version, h.tenant_id,
                           h.result AS decision_state, h.actor, h.reason,
                           NULL AS risk_level, CONCAT(h.product_code, ' ', h.scope_expression) AS evidence_ref,
                           CONCAT(h.subject_type, ':', h.subject_id) AS submitted_snapshot,
                           '/admin/exemption/policy' AS lifecycle_link,
                           h.created_at
                    FROM exempt_rule_history h
                    WHERE h.id=?
                    """;
            default -> throw failure("REVIEW_HISTORY_TYPE_INVALID", "筛选条件不合法");
        };
    }

    private static Timestamp timestampValue(String raw, String errorCode) {
        try {
            return Timestamp.valueOf(LocalDateTime.parse(raw.trim()));
        } catch (DateTimeParseException ex) {
            throw failure(errorCode, "筛选条件不合法");
        }
    }

    private static long longValue(String raw, String errorCode) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            throw failure(errorCode, "筛选条件不合法");
        }
    }

    private static String enumValue(String raw, Set<String> allowed, String errorCode) {
        if (raw == null) {
            throw failure(errorCode, "筛选条件不合法");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw failure(errorCode, "筛选条件不合法");
        }
        return normalized;
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }
}
