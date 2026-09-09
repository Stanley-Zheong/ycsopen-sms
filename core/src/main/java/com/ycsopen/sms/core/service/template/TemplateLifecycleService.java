package com.ycsopen.sms.core.service.template;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

/** Phase 13: F-3.4/F-3.5 template application, review, resubmission, and preview lifecycle. */
@Service
public class TemplateLifecycleService {
    private static final Set<String> TYPES = Set.of("VERIFY", "NOTIFY", "MARKETING", "VERIFICATION", "NOTIFICATION");
    private static final Set<String> DECISIONS = Set.of("APPROVE", "REJECT", "AMENDMENT_REQUIRED");

    private final JdbcTemplate jdbc;

    public TemplateLifecycleService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional
    public TemplateResponse submitApplication(long tenantId, TemplateApplicationRequest request) {
        return createVersion(tenantId, null, 1, request);
    }

    @Transactional
    public TemplateResponse resubmit(long tenantId, long previousTemplateId, TemplateApplicationRequest request) {
        TemplateRow previous = row(previousTemplateId);
        if (previous.tenantId() != tenantId) {
            throw failure("TEMPLATE_FORBIDDEN", "不能修改其他机构模板");
        }
        if (!Set.of("REJECTED", "AMENDMENT_REQUIRED").contains(previous.auditStatus())) {
            throw failure("TEMPLATE_RESUBMIT_STATE_INVALID", "当前模板状态不可重新提交");
        }
        requireNoSuccessor(previousTemplateId);
        return createVersion(tenantId, previousTemplateId, previous.versionNo() + 1, request);
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> listTenant(long tenantId) {
        return rows("SELECT * FROM templates WHERE tenant_id=? ORDER BY created_at DESC, id DESC", tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TemplateReviewQueueResponse reviewQueue(String keyword, String tenantId, String state, String type) {
        StringBuilder sql = new StringBuilder("SELECT * FROM templates WHERE is_system_template=0");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND template_name LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (tenantId != null && !tenantId.isBlank()) {
            try {
                args.add(Long.parseLong(tenantId.trim()));
            } catch (NumberFormatException ex) {
                throw failure("TEMPLATE_TENANT_ID_INVALID", "租户编号不合法");
            }
            sql.append(" AND tenant_id=?");
        }
        if (state != null && !state.isBlank()) {
            sql.append(" AND audit_status=?");
            args.add(normalizeDecisionState(state));
        }
        if (type != null && !type.isBlank()) {
            sql.append(" AND template_type=?");
            args.add(normalizeType(type));
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        return new TemplateReviewQueueResponse(summary(), rows(sql.toString(), args.toArray()).stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public TemplateReviewSummaryResponse summary() {
        return new TemplateReviewSummaryResponse(
                count("SELECT COUNT(*) FROM templates WHERE is_system_template=0"),
                count("SELECT COUNT(*) FROM templates WHERE is_system_template=0 AND audit_status='PENDING'"),
                count("SELECT COUNT(*) FROM templates WHERE is_system_template=0 AND audit_status='APPROVED'"),
                count("SELECT COUNT(*) FROM templates WHERE is_system_template=0 AND audit_status='REJECTED'"),
                count("SELECT COUNT(*) FROM templates WHERE is_system_template=0 AND audit_status='AMENDMENT_REQUIRED'")
        );
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(long templateId) {
        return toResponse(row(templateId));
    }

    @Transactional(readOnly = true)
    public TemplatePreviewResponse preview(long tenantId, long templateId, Map<String, String> variables) {
        TemplateRow template = row(templateId);
        if (template.tenantId() != tenantId) {
            throw failure("TEMPLATE_FORBIDDEN", "不能访问其他机构模板");
        }
        return new TemplatePreviewResponse(TemplateRuleEngine.render(template.content(),
                TemplateRuleEngine.splitVariables(template.variableNames()), template.paramCheckRule(), variables));
    }

    @Transactional
    public TemplateResponse decide(long templateId, TemplateDecisionRequest request) {
        TemplateRow current = row(templateId);
        if (!"PENDING".equals(current.auditStatus())) {
            throw failure("TEMPLATE_REVIEW_STATE_INVALID", "当前模板状态不可审核");
        }
        String decision = enumValue(request == null ? null : request.decision(), DECISIONS, "TEMPLATE_REVIEW_DECISION_INVALID");
        String actor = TemplateRuleEngine.required(request == null ? null : request.actor(), "TEMPLATE_REVIEW_ACTOR_REQUIRED", 64);
        String opinion = safeOpinion(request == null ? null : request.opinion());
        String target = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            default -> "AMENDMENT_REQUIRED";
        };
        int changed = jdbc.update("""
                UPDATE templates SET audit_status=?, audit_comment=?, audit_time=CURRENT_TIMESTAMP
                WHERE id=? AND audit_status='PENDING'
                """, target, opinion, templateId);
        if (changed != 1) {
            throw failure("TEMPLATE_REVIEW_STATE_INVALID", "当前模板状态不可审核");
        }
        appendHistory(templateId, target, actor, opinion, current.content(), current.variableNames());
        return get(templateId);
    }

    private TemplateResponse createVersion(long tenantId, Long previousTemplateId, int versionNo,
                                           TemplateApplicationRequest request) {
        String name = TemplateRuleEngine.required(request == null ? null : request.templateName(), "TEMPLATE_NAME_REQUIRED", 50);
        String content = TemplateRuleEngine.required(request == null ? null : request.content(), "TEMPLATE_CONTENT_REQUIRED", 500);
        List<String> variables = TemplateRuleEngine.variables(content);
        String type = normalizeType(request == null ? null : request.templateType());
        Long signatureId = request == null ? null : request.signatureId();
        if (signatureId == null) {
            throw failure("TEMPLATE_SIGNATURE_REQUIRED", "模板签名不能为空");
        }
        requireApprovedTenantSignature(tenantId, signatureId);
        String rule = TemplateRuleEngine.optional(request.paramCheckRule(), 255);
        TemplateRuleEngine.rules(rule, variables);
        String description = TemplateRuleEngine.optional(request.description(), 255);
        String variableNames = TemplateRuleEngine.joinVariables(variables);
        String code = templateCode(tenantId, name, content, signatureId, versionNo);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO templates(tenant_id,biz_type,template_code,template_name,template_type,content,signature_id,param_check_rule,description,variable_names,version_no,previous_template_id,audit_status,audit_comment,is_system_template)
                    VALUES (?,'DOMESTIC',?,?,?,?,?,?,?,?,?,?,'PENDING','',0)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, code);
            ps.setString(3, name);
            ps.setString(4, type);
            ps.setString(5, content);
            ps.setLong(6, signatureId);
            ps.setString(7, rule);
            ps.setString(8, description);
            ps.setString(9, variableNames);
            ps.setInt(10, versionNo);
            if (previousTemplateId == null) {
                ps.setObject(11, null);
            } else {
                ps.setLong(11, previousTemplateId);
            }
            return ps;
        }, keyHolder);
        Object generated = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("ID");
        if (!(generated instanceof Number)) {
            generated = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("id");
        }
        if (!(generated instanceof Number idNumber)) {
            throw failure("TEMPLATE_CREATE_FAILED", "模板创建失败");
        }
        long id = idNumber.longValue();
        appendHistory(id, "SUBMITTED", "tenant:" + tenantId, description == null ? "提交模板" : description, content, variableNames);
        return get(id);
    }

    private void requireApprovedTenantSignature(long tenantId, long signatureId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT tenant_id,audit_status FROM signatures WHERE id=?", signatureId);
        if (rows.isEmpty() || !Objects.equals(((Number) mapValue(rows.getFirst(), "tenant_id")).longValue(), tenantId)
                || !"APPROVED".equals(String.valueOf(mapValue(rows.getFirst(), "audit_status")))) {
            throw failure("TEMPLATE_SIGNATURE_INVALID", "模板签名必须属于当前机构且已审核通过");
        }
    }

    private void requireNoSuccessor(long previousTemplateId) {
        if (count("SELECT COUNT(*) FROM templates WHERE previous_template_id=?", previousTemplateId) > 0) {
            throw failure("TEMPLATE_RESUBMIT_SUCCESSOR_EXISTS", "当前模板已有重新提交版本");
        }
    }

    private TemplateResponse toResponse(TemplateRow row) {
        return new TemplateResponse(row.id(), row.tenantId(), row.templateCode(), row.templateName(), row.content(),
                row.templateType(), row.signatureId(), row.paramCheckRule(), row.description(),
                TemplateRuleEngine.splitVariables(row.variableNames()), row.versionNo(), row.previousTemplateId(),
                row.auditStatus(), row.auditComment(), row.auditTime(), row.createdAt(), history(row.id()));
    }

    private List<TemplateResponse.History> history(long templateId) {
        return jdbc.query("""
                SELECT event_type, actor, opinion, snapshot_content, variable_names, created_at
                FROM template_review_history WHERE template_id=? ORDER BY created_at, id
                """, (rs, rowNum) -> new TemplateResponse.History(rs.getString("event_type"),
                rs.getString("actor"), rs.getString("opinion"), rs.getString("snapshot_content"),
                TemplateRuleEngine.splitVariables(rs.getString("variable_names")),
                rs.getTimestamp("created_at").toLocalDateTime()), templateId);
    }

    private List<TemplateRow> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> new TemplateRow(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getString("template_code"), rs.getString("template_name"), rs.getString("content"),
                rs.getString("template_type"), rs.getLong("signature_id"), rs.getString("param_check_rule"),
                rs.getString("description"), rs.getString("variable_names"), rs.getInt("version_no"),
                nullableLong(rs.getObject("previous_template_id")), rs.getString("audit_status"),
                rs.getString("audit_comment"),
                rs.getTimestamp("audit_time") == null ? null : rs.getTimestamp("audit_time").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime()), args);
    }

    private TemplateRow row(long templateId) {
        List<TemplateRow> rows = rows("SELECT * FROM templates WHERE id=?", templateId);
        if (rows.isEmpty()) {
            throw failure("TEMPLATE_NOT_FOUND", "模板不存在");
        }
        return rows.getFirst();
    }

    private void appendHistory(long templateId, String event, String actor, String opinion,
                               String snapshotContent, String variableNames) {
        jdbc.update("""
                INSERT INTO template_review_history(template_id,event_type,actor,opinion,snapshot_content,variable_names)
                VALUES (?,?,?,?,?,?)
                """, templateId, event, actor, opinion, snapshotContent, variableNames);
    }

    private static String normalizeType(String value) {
        String type = enumValue(value, TYPES, "TEMPLATE_TYPE_INVALID");
        return switch (type) {
            case "VERIFICATION" -> "VERIFY";
            case "NOTIFICATION" -> "NOTIFY";
            default -> type;
        };
    }

    private static String normalizeDecisionState(String value) {
        return enumValue(value, Set.of("PENDING", "APPROVED", "REJECTED", "AMENDMENT_REQUIRED"), "TEMPLATE_STATUS_INVALID");
    }

    private static String enumValue(String value, Set<String> allowed, String code) {
        if (value == null || !allowed.contains(value.trim().toUpperCase(Locale.ROOT))) {
            throw failure(code, "枚举值不合法");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String safeOpinion(String value) {
        String opinion = TemplateRuleEngine.required(value, "TEMPLATE_REVIEW_OPINION_REQUIRED", 500);
        if (opinion.contains("\n") || opinion.contains("\r") || opinion.contains("\t")) {
            throw failure("TEMPLATE_REVIEW_OPINION_INVALID", "审核意见格式不合法");
        }
        return opinion;
    }

    private long count(String sql, Object... args) {
        Long count = jdbc.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private static Object mapValue(Map<String, Object> row, String key) {
        Object direct = row.get(key);
        if (direct != null) {
            return direct;
        }
        return row.get(key.toUpperCase(Locale.ROOT));
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String templateCode(long tenantId, String name, String content, long signatureId, int versionNo) {
        return "TPL" + tenantId + Integer.toHexString(Objects.hash(name, content, signatureId, versionNo)).toUpperCase(Locale.ROOT);
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    private record TemplateRow(long id, long tenantId, String templateCode, String templateName, String content,
                               String templateType, long signatureId, String paramCheckRule, String description,
                               String variableNames, int versionNo, Long previousTemplateId,
                               String auditStatus, String auditComment, LocalDateTime auditTime,
                               LocalDateTime createdAt) { }
}
