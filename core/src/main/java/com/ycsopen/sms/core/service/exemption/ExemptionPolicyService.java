package com.ycsopen.sms.core.service.exemption;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/** Phase 14: auditable, bounded F-3.6 exemption policy. */
@Service
public class ExemptionPolicyService {
    private static final Set<String> TYPES = Set.of("SIGNATURE", "CONTENT", "ACCOUNT");
    private static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");
    private static final Set<String> NON_EXEMPTABLE_CONTROLS = Set.of(
            "ACCOUNT_BALANCE", "BLACKLIST_HARD", "TENANT_TERMINATED"
    );

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ExemptionPolicyService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemDefaultZone());
    }

    ExemptionPolicyService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<ExemptionPolicyResponse> list() {
        return rows("SELECT * FROM exempt_rules ORDER BY created_at DESC, id DESC")
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExemptionPolicyResponse create(ExemptionPolicyCreateRequest request) {
        if (request == null) {
            throw failure("EXEMPTION_REQUEST_REQUIRED", "豁免请求不能为空");
        }
        long tenantId = requiredTenant(request.tenantId());
        String type = enumValue(request.exemptionType(), TYPES, "EXEMPTION_TYPE_INVALID");
        String resourceId = requiredText(request.resourceId(), "EXEMPTION_RESOURCE_REQUIRED", 128);
        String productCode = requiredText(request.productCode(), "EXEMPTION_PRODUCT_REQUIRED", 64);
        String scope = requiredText(request.scopeExpression(), "EXEMPTION_SCOPE_REQUIRED", 255);
        String approval = enumValue(request.approvalStatus(), STATUSES, "EXEMPTION_APPROVAL_INVALID");
        LocalDateTime validFrom = parseTime(request.validFrom(), "EXEMPTION_VALID_FROM_INVALID");
        LocalDateTime validUntil = parseTime(request.validUntil(), "EXEMPTION_VALID_UNTIL_INVALID");
        if (!validUntil.isAfter(validFrom)) {
            throw failure("EXEMPTION_VALIDITY_INVALID", "豁免有效期不合法");
        }
        String reason = requiredText(request.reason(), "EXEMPTION_REASON_REQUIRED", 255);
        String actor = requiredText(request.actor(), "EXEMPTION_ACTOR_REQUIRED", 64);
        for (int attempt = 0; attempt < 3; attempt += 1) {
            try {
                return insertVersion(tenantId, type, resourceId, productCode, scope, approval, validFrom, validUntil, reason, actor);
            } catch (DuplicateKeyException duplicate) {
                if (attempt == 2) {
                    throw failure("EXEMPTION_VERSION_CONFLICT", "豁免版本并发冲突，请重试");
                }
            }
        }
        throw failure("EXEMPTION_VERSION_CONFLICT", "豁免版本并发冲突，请重试");
    }

    private ExemptionPolicyResponse insertVersion(long tenantId, String type, String resourceId, String productCode,
                                                  String scope, String approval, LocalDateTime validFrom,
                                                  LocalDateTime validUntil, String reason, String actor) {
        int version = nextVersion(tenantId, type, resourceId, productCode, scope);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO exempt_rules(tenant_id,exempt_type,resource_id,product_code,scope,approval_status,
                        valid_from,valid_until,approved_by,version_no,reason,created_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, tenantId);
            ps.setString(2, type);
            ps.setString(3, resourceId);
            ps.setString(4, productCode);
            ps.setString(5, scope);
            ps.setString(6, approval);
            ps.setTimestamp(7, Timestamp.valueOf(validFrom));
            ps.setTimestamp(8, Timestamp.valueOf(validUntil));
            ps.setString(9, "APPROVED".equals(approval) ? actor : null);
            ps.setInt(10, version);
            ps.setString(11, reason);
            ps.setString(12, actor);
            return ps;
        }, keyHolder);
        long id = generatedId(keyHolder);
        appendDecision(id, version, "CREATED", tenantId, type, resourceId, productCode, scope, actor, reason, approval);
        return get(id);
    }

    @Transactional
    public ExemptionPolicyResponse revoke(long id, ExemptionPolicyRevokeRequest request) {
        if (request == null) {
            throw failure("EXEMPTION_REQUEST_REQUIRED", "豁免请求不能为空");
        }
        ExemptionRule rule = rule(id);
        if (rule.revokedAt() != null) {
            throw failure("EXEMPTION_ALREADY_REVOKED", "豁免已撤销");
        }
        String actor = requiredText(request == null ? null : request.actor(), "EXEMPTION_ACTOR_REQUIRED", 64);
        String reason = requiredText(request == null ? null : request.reason(), "EXEMPTION_REASON_REQUIRED", 255);
        jdbc.update("UPDATE exempt_rules SET revoked_at=?, revoked_by=?, revoke_reason=? WHERE id=? AND revoked_at IS NULL",
                Timestamp.valueOf(now()), actor, reason, id);
        appendDecision(id, rule.versionNo(), "REVOKED", rule.tenantId(), rule.exemptionType(), rule.resourceId(),
                rule.productCode(), rule.scopeExpression(), actor, reason, "REVOKED");
        return get(id);
    }

    @Transactional
    public ExemptionPolicyPreviewResponse preview(ExemptionPolicyPreviewRequest request) {
        if (request == null) {
            throw failure("EXEMPTION_REQUEST_REQUIRED", "豁免请求不能为空");
        }
        long tenantId = requiredTenant(request.tenantId());
        String type = enumValue(request.exemptionType(), TYPES, "EXEMPTION_TYPE_INVALID");
        String resourceId = requiredText(request.resourceId(), "EXEMPTION_RESOURCE_REQUIRED", 128);
        String productCode = requiredText(request.productCode(), "EXEMPTION_PRODUCT_REQUIRED", 64);
        String scope = requiredText(request.scopeExpression(), "EXEMPTION_SCOPE_REQUIRED", 255);
        String controlCode = requiredText(request.controlCode(), "EXEMPTION_CONTROL_REQUIRED", 64).toUpperCase(Locale.ROOT);
        String actor = requiredText(request.actor(), "EXEMPTION_ACTOR_REQUIRED", 64);
        String reason = requiredText(request.reason(), "EXEMPTION_REASON_REQUIRED", 255);
        if (NON_EXEMPTABLE_CONTROLS.contains(controlCode)) {
            return recordUsage(null, null, tenantId, type, resourceId, productCode, scope, controlCode,
                    actor, reason, "DENIED_NON_EXEMPTABLE", "当前控制不可豁免");
        }
        List<ExemptionRule> candidates = rows("""
                SELECT * FROM exempt_rules
                WHERE tenant_id=? AND exempt_type=?
                  AND (resource_id=? OR resource_id='*')
                  AND (product_code=? OR product_code='*')
                  AND (scope=? OR scope='*')
                ORDER BY
                  CASE WHEN resource_id=? THEN 0 ELSE 1 END,
                  CASE WHEN product_code=? THEN 0 ELSE 1 END,
                  CASE WHEN scope=? THEN 0 ELSE 1 END,
                  version_no DESC,
                  id DESC
                """, tenantId, type, resourceId, productCode, scope, resourceId, productCode, scope);
        if (candidates.isEmpty()) {
            return recordUsage(null, null, tenantId, type, resourceId, productCode, scope, controlCode,
                    actor, reason, "DENIED_OUT_OF_SCOPE", "没有匹配当前范围的豁免");
        }
        ExemptionRule top = candidates.getFirst();
        if (isActive(top)) {
            ExemptionRule rule = top;
            jdbc.update("UPDATE exempt_rules SET usage_count=usage_count+1 WHERE id=?", rule.id());
            return recordUsage(rule.id(), rule.versionNo(), tenantId, type, resourceId, productCode, scope, controlCode,
                    actor, reason, "ACTIVE", "匹配已批准且有效的豁免");
        }
        String result;
        String message;
        if (!"APPROVED".equals(top.approvalStatus())) {
            result = "DENIED_UNAUTHORIZED";
            message = "豁免未批准";
        } else if (top.revokedAt() != null) {
            result = "DENIED_REVOKED";
            message = "豁免已撤销";
        } else {
            result = "DENIED_EXPIRED";
            message = "豁免不在有效期内";
        }
        return recordUsage(top.id(), top.versionNo(), tenantId, type, resourceId, productCode, scope, controlCode,
                actor, reason, result, message);
    }

    @Transactional(readOnly = true)
    public List<ExemptionPolicyUsageResponse> usageHistory() {
        return jdbc.query("""
                SELECT * FROM exempt_rule_usage_history ORDER BY created_at DESC, id DESC
                """, (rs, rowNum) -> new ExemptionPolicyUsageResponse(rs.getLong("id"),
                nullableLong(rs.getObject("exempt_rule_id")), nullableInteger(rs.getObject("version_no")),
                rs.getLong("tenant_id"), rs.getString("subject_type"), rs.getString("subject_id"),
                rs.getString("product_code"), rs.getString("scope_expression"), rs.getString("control_code"),
                rs.getString("actor"), rs.getString("reason"), rs.getString("result"),
                rs.getTimestamp("created_at").toLocalDateTime()));
    }

    private boolean isActive(ExemptionRule rule) {
        LocalDateTime current = now();
        return "APPROVED".equals(rule.approvalStatus())
                && rule.revokedAt() == null
                && !current.isBefore(rule.validFrom())
                && current.isBefore(rule.validUntil());
    }

    private ExemptionPolicyPreviewResponse recordUsage(Long ruleId, Integer versionNo, long tenantId, String type,
                                                       String resourceId, String productCode, String scope,
                                                       String controlCode, String actor, String reason,
                                                       String result, String resultReason) {
        jdbc.update("""
                INSERT INTO exempt_rule_usage_history(exempt_rule_id,version_no,tenant_id,subject_type,subject_id,
                    product_code,scope_expression,control_code,actor,reason,result)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, ruleId, versionNo, tenantId, type, resourceId, productCode, scope, controlCode, actor, reason, result);
        return new ExemptionPolicyPreviewResponse(result, ruleId, versionNo, resultReason, type, resourceId, productCode, scope);
    }

    private void appendDecision(long id, int versionNo, String event, long tenantId, String type, String resourceId,
                                String productCode, String scope, String actor, String reason, String result) {
        jdbc.update("""
                INSERT INTO exempt_rule_history(exempt_rule_id,version_no,event_type,tenant_id,subject_type,subject_id,
                    product_code,scope_expression,actor,reason,result)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """, id, versionNo, event, tenantId, type, resourceId, productCode, scope, actor, reason, result);
    }

    private ExemptionPolicyResponse get(long id) {
        return toResponse(rule(id));
    }

    private ExemptionRule rule(long id) {
        List<ExemptionRule> matches = rows("SELECT * FROM exempt_rules WHERE id=?", id);
        if (matches.isEmpty()) {
            throw failure("EXEMPTION_NOT_FOUND", "豁免不存在");
        }
        return matches.getFirst();
    }

    private List<ExemptionRule> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> new ExemptionRule(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getString("exempt_type"), rs.getString("resource_id"), rs.getString("product_code"),
                rs.getString("scope"), rs.getString("approval_status"),
                rs.getTimestamp("valid_from").toLocalDateTime(), rs.getTimestamp("valid_until").toLocalDateTime(),
                nullableTime(rs.getObject("revoked_at")), rs.getString("revoked_by"), rs.getString("revoke_reason"),
                rs.getInt("version_no"), rs.getLong("usage_count"), rs.getString("reason"),
                rs.getString("created_by"), rs.getTimestamp("created_at").toLocalDateTime()), args);
    }

    private ExemptionPolicyResponse toResponse(ExemptionRule rule) {
        return new ExemptionPolicyResponse(rule.id(), rule.tenantId(), rule.exemptionType(), rule.resourceId(),
                rule.productCode(), rule.scopeExpression(), rule.approvalStatus(), rule.validFrom(), rule.validUntil(),
                rule.revokedAt() != null, rule.versionNo(), rule.usageCount(), rule.reason(), rule.createdBy(),
                rule.revokedBy(), rule.revokeReason(), rule.revokedAt(), rule.createdAt());
    }

    private int nextVersion(long tenantId, String type, String resourceId, String productCode, String scope) {
        jdbc.queryForList("""
                SELECT id FROM exempt_rules
                WHERE tenant_id=? AND exempt_type=? AND resource_id=? AND product_code=? AND scope=?
                FOR UPDATE
                """, tenantId, type, resourceId, productCode, scope);
        Integer version = jdbc.queryForObject("""
                SELECT COALESCE(MAX(version_no),0) + 1 FROM exempt_rules
                WHERE tenant_id=? AND exempt_type=? AND resource_id=? AND product_code=? AND scope=?
                """, Integer.class, tenantId, type, resourceId, productCode, scope);
        return version == null ? 1 : version;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static long requiredTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw failure("EXEMPTION_TENANT_REQUIRED", "豁免租户不能为空");
        }
        return tenantId;
    }

    private static String enumValue(String value, Set<String> allowed, String code) {
        if (value == null || !allowed.contains(value.trim().toUpperCase(Locale.ROOT))) {
            throw failure(code, "枚举值不合法");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requiredText(String value, String code, int max) {
        if (value == null || value.trim().isEmpty()) {
            throw failure(code, "必填字段不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max || trimmed.contains("\n") || trimmed.contains("\r") || trimmed.contains("\t")) {
            throw failure(code, "字段格式不合法");
        }
        return trimmed;
    }

    private static LocalDateTime parseTime(String value, String code) {
        try {
            return LocalDateTime.parse(requiredText(value, code, 32));
        } catch (RuntimeException ex) {
            throw failure(code, "时间格式必须为 ISO LocalDateTime");
        }
    }

    private static long generatedId(KeyHolder keyHolder) {
        Object generated = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("ID");
        if (!(generated instanceof Number)) {
            generated = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("id");
        }
        if (!(generated instanceof Number number)) {
            throw failure("EXEMPTION_CREATE_FAILED", "豁免创建失败");
        }
        return number.longValue();
    }

    private static LocalDateTime nullableTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    private record ExemptionRule(long id, long tenantId, String exemptionType, String resourceId, String productCode,
                                 String scopeExpression, String approvalStatus, LocalDateTime validFrom,
                                 LocalDateTime validUntil, LocalDateTime revokedAt, String revokedBy,
                                 String revokeReason, int versionNo, long usageCount, String reason,
                                 String createdBy, LocalDateTime createdAt) { }
}
