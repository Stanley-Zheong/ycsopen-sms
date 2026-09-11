package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.risk.BlacklistRiskControlService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Phase41: focused complaint case intake, state flow, remediation, recovery, and analytics. */
@Service
public class ComplaintCaseService {
    private static final List<String> SOURCES = List.of("REGULATOR", "CARRIER", "OPERATOR", "USER_REPORT");
    private static final List<String> QUALITIES = List.of("COMPLETE", "PARTIAL", "UNKNOWN");
    private static final List<String> DISPOSALS = List.of(
            "BLACKLIST_MOBILE", "SUSPEND_TENANT", "SUSPEND_SIGNATURE_OR_TEMPLATE", "SUSPEND_CHANNEL");

    private final JdbcTemplate jdbc;
    private final BlacklistPort blacklistPort;

    public ComplaintCaseService(JdbcTemplate jdbc, BlacklistRiskControlService blacklistService) {
        this(jdbc, (tenantId, mobile, actor, reason) -> blacklistService.createEntry(
                new BlacklistRiskControlService.BlacklistEntryCreateRequest(
                        tenantId, mobile, "BLACK", "COMPLAINT_LINKED", reason, null), actor).id());
    }

    ComplaintCaseService(JdbcTemplate jdbc, BlacklistPort blacklistPort) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.blacklistPort = Objects.requireNonNull(blacklistPort);
    }

    @Transactional
    public CaseRow create(CreateCommand command, String actor) {
        if (command == null) {
            throw failure("COMPLAINT_REQUEST_REQUIRED", "投诉请求不能为空");
        }
        String source = enumValue(command.source(), SOURCES, "COMPLAINT_SOURCE_INVALID");
        String summary = text(command.summary(), "COMPLAINT_SUMMARY_REQUIRED", 500);
        String quality = command.attributionQuality() == null || command.attributionQuality().isBlank()
                ? inferredQuality(command) : enumValue(command.attributionQuality(), QUALITIES, "COMPLAINT_ATTRIBUTION_INVALID");
        String requirement = optionalText(command.requirement(), 500);
        String contentType = optionalText(command.contentType(), 64);
        String mobile = optionalText(command.complainedMobile(), 32);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO complaints(source, tenant_id, channel_id, signature_id, template_id, message_id,
                                           content_type, complained_mobile, summary, status, attribution_quality,
                                           requirement, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setString(1, source);
            setLong(ps, 2, command.tenantId());
            setLong(ps, 3, command.channelId());
            setLong(ps, 4, command.signatureId());
            setLong(ps, 5, command.templateId());
            ps.setString(6, optionalText(command.messageId(), 64));
            ps.setString(7, contentType);
            ps.setString(8, mobile);
            ps.setString(9, summary);
            ps.setString(10, quality);
            ps.setString(11, requirement);
            ps.setString(12, actor(actor));
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            ps.setTimestamp(13, now);
            ps.setTimestamp(14, now);
            return ps;
        }, key);
        return caseById(Objects.requireNonNull(key.getKey()).longValue());
    }

    @Transactional(readOnly = true)
    public List<CaseRow> cases() {
        return jdbc.query("""
                SELECT * FROM complaints ORDER BY created_at DESC, id DESC LIMIT 200
                """, (rs, i) -> caseRow(rs));
    }

    @Transactional
    public CaseRow accept(long id, StateCommand command) {
        ensureStatus(id, "PENDING");
        jdbc.update("""
                UPDATE complaints SET status='PROCESSING', accepted_by=?, accepted_at=?, opinion=?, updated_at=?
                 WHERE id=?
                """, actor(command.actor()), Timestamp.valueOf(LocalDateTime.now()),
                optionalText(command.opinion(), 500), Timestamp.valueOf(LocalDateTime.now()), id);
        return caseById(id);
    }

    @Transactional
    public CaseRow handle(long id, StateCommand command) {
        ensureStatus(id, "PROCESSING");
        String opinion = text(command.opinion(), "COMPLAINT_OPINION_REQUIRED", 500);
        String remediation = text(command.remediation(), "COMPLAINT_REMEDIATION_REQUIRED", 500);
        String requirement = text(command.requirement(), "COMPLAINT_REQUIREMENT_REQUIRED", 500);
        jdbc.update("""
                UPDATE complaints SET status='PROCESSED', opinion=?, remediation=?, requirement=?,
                       handling_note=?, corrective_action=?, handled_by=?, handled_at=?, updated_at=?
                 WHERE id=?
                """, opinion, remediation, requirement, opinion, remediation, actor(command.actor()),
                Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), id);
        return caseById(id);
    }

    @Transactional
    public CaseRow close(long id, StateCommand command) {
        ensureStatus(id, "PROCESSED");
        String opinion = text(command.opinion(), "COMPLAINT_CLOSE_NOTE_REQUIRED", 500);
        jdbc.update("""
                UPDATE complaints SET status='CLOSED', closed_by=?, closed_at=?, closed_note=?, updated_at=?
                 WHERE id=?
                """, actor(command.actor()), Timestamp.valueOf(LocalDateTime.now()), opinion,
                Timestamp.valueOf(LocalDateTime.now()), id);
        return caseById(id);
    }

    @Transactional
    public RemediationRow remediate(long complaintId, RemediationCommand command) {
        ensureStatus(complaintId, "PROCESSED");
        String type = enumValue(command.disposalType(), DISPOSALS, "COMPLAINT_REMEDIATION_TYPE_INVALID");
        String target = text(command.targetRef(), "COMPLAINT_REMEDIATION_TARGET_REQUIRED", 64);
        String actor = actor(command.actor());
        String reviewId = text(command.authorizedReviewId(), "COMPLAINT_REVIEW_REQUIRED", 128);
        String key = complaintId + ":" + type + ":" + target + ":" + reviewId;
        List<RemediationRow> existing = findRemediationByKey(key);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        try {
            applyTargetAction(complaintId, type, target, actor, optionalText(command.reason(), 500));
            return insertRemediation(complaintId, type, target, actor, reviewId, "APPLIED", null, key, complaintId);
        } catch (RuntimeException ex) {
            if (ex instanceof BusinessException) {
                throw ex;
            }
            String failure = optionalText(command.reason(), 500);
            return insertRemediation(complaintId, type, target, actor, reviewId, "FAILED",
                    failure == null ? blankToDefault(ex.getMessage(), "处置执行失败") : failure, key, complaintId);
        }
    }

    @Transactional
    public RemediationRow recover(long complaintId, RecoveryCommand command) {
        RemediationRow original = remediationById(command.disposalRecordId());
        if (original.complaintId() != complaintId) {
            throw failure("COMPLAINT_RECOVERY_CASE_MISMATCH", "恢复记录必须引用原投诉案件");
        }
        if (!"FAILED".equals(original.status())) {
            throw failure("COMPLAINT_RECOVERY_STATE_INVALID", "只有失败处置可以记录恢复");
        }
        String reviewId = text(command.authorizedReviewId(), "COMPLAINT_RECOVERY_REVIEW_REQUIRED", 128);
        jdbc.update("""
                UPDATE disposal_records SET status='RECOVERED', recovered_by=?, recovered_at=?,
                       resume_condition=?, authorized_review_id=?, original_complaint_id=?
                 WHERE id=?
                """, actor(command.actor()), Timestamp.valueOf(LocalDateTime.now()),
                text(command.resumeCondition(), "COMPLAINT_RECOVERY_CONDITION_REQUIRED", 255),
                reviewId, complaintId, command.disposalRecordId());
        return remediationById(command.disposalRecordId());
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics() {
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM complaints", Integer.class);
        int unknown = jdbc.queryForObject(
                "SELECT COUNT(*) FROM complaints WHERE attribution_quality='UNKNOWN'", Integer.class);
        return new AnalyticsResponse(total, unknown,
                dimensionRows("tenant", "tenant_id"),
                dimensionRows("signature", "signature_id"),
                jdbc.query("""
                        SELECT COALESCE(content_type, 'UNKNOWN') AS dimension, COUNT(*) AS total
                          FROM complaints GROUP BY COALESCE(content_type, 'UNKNOWN') ORDER BY total DESC, dimension ASC
                        """, (rs, i) -> new DimensionRow(rs.getString("dimension"), rs.getInt("total"))));
    }

    private void applyTargetAction(long complaintId, String type, String target, String actor, String reason) {
        CaseRow row = caseById(complaintId);
        switch (type) {
            case "BLACKLIST_MOBILE" -> {
                if (row.tenantId() == null) {
                    throw failure("COMPLAINT_REMEDIATION_TENANT_REQUIRED", "黑名单处置需要机构归因");
                }
                String mobile = target.startsWith("mobile:")
                        ? text(target.substring("mobile:".length()), "COMPLAINT_REMEDIATION_TARGET_REQUIRED", 32)
                        : target;
                requireLinkedTarget(Objects.equals(row.complainedMobile(), mobile));
                blacklistPort.create(row.tenantId(), mobile, actor, "投诉案件:" + complaintId);
            }
            case "SUSPEND_TENANT" -> {
                long tenantId = targetId(target, "tenant");
                requireLinkedTarget(Objects.equals(row.tenantId(), tenantId));
                requireUpdated(jdbc.update(
                        "UPDATE tenants SET lifecycle_status='FROZEN' WHERE id=?",
                        tenantId));
            }
            case "SUSPEND_SIGNATURE_OR_TEMPLATE" -> suspendResource(row, target, actor, reason);
            case "SUSPEND_CHANNEL" -> {
                long channelId = targetId(target, "channel");
                requireLinkedTarget(Objects.equals(row.channelId(), channelId));
                requireUpdated(jdbc.update("""
                        UPDATE channels SET status='PAUSED', pause_reason=?, paused_by=?, paused_at=? WHERE id=?
                        """, blankToDefault(reason, "投诉处置"), actor, Timestamp.valueOf(LocalDateTime.now()),
                        channelId));
            }
            default -> throw failure("COMPLAINT_REMEDIATION_TYPE_INVALID", "处置类型不支持");
        }
    }

    private void suspendResource(CaseRow row, String target, String actor, String reason) {
        if (target.startsWith("signature:")) {
            long signatureId = targetId(target, "signature");
            requireLinkedTarget(Objects.equals(row.signatureId(), signatureId));
            requireUpdated(jdbc.update("UPDATE signatures SET audit_status='REJECTED', audit_comment=? WHERE id=?",
                    "DISABLED_BY_COMPLAINT:" + actor + ":" + blankToDefault(reason, "投诉处置"),
                    signatureId));
            return;
        }
        if (target.startsWith("template:")) {
            long templateId = targetId(target, "template");
            requireLinkedTarget(Objects.equals(row.templateId(), templateId));
            requireUpdated(jdbc.update("UPDATE templates SET audit_status='REJECTED', audit_comment=? WHERE id=?",
                    "DISABLED_BY_COMPLAINT:" + actor + ":" + blankToDefault(reason, "投诉处置"),
                    templateId));
            return;
        }
        throw failure("COMPLAINT_REMEDIATION_TARGET_INVALID", "签名或模板处置目标必须为 signature:{id} 或 template:{id}");
    }

    private RemediationRow insertRemediation(long complaintId, String type, String target, String actor,
                                             String reviewId, String status, String failure, String key,
                                             long originalComplaintId) {
        var holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO disposal_records(complaint_id, disposal_type, target_ref, disposed_by, disposed_at,
                                                 status, authorized_review_id, failure_reason, original_complaint_id,
                                                 idempotency_key)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, new String[]{"id"});
            ps.setLong(1, complaintId);
            ps.setString(2, type);
            ps.setString(3, target);
            ps.setString(4, actor);
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(6, status);
            ps.setString(7, reviewId);
            ps.setString(8, failure);
            ps.setLong(9, originalComplaintId);
            ps.setString(10, key);
            return ps;
        }, holder);
        return remediationById(Objects.requireNonNull(holder.getKey()).longValue());
    }

    private List<RemediationRow> findRemediationByKey(String key) {
        return jdbc.query("""
                SELECT id, complaint_id, disposal_type, target_ref, status, authorized_review_id,
                       failure_reason, original_complaint_id
                  FROM disposal_records WHERE idempotency_key=?
                """, (rs, i) -> remediationRow(rs), key);
    }

    private RemediationRow remediationById(long id) {
        return jdbc.query("""
                SELECT id, complaint_id, disposal_type, target_ref, status, authorized_review_id,
                       failure_reason, original_complaint_id
                  FROM disposal_records WHERE id=?
                """, (rs, i) -> remediationRow(rs), id).stream().findFirst()
                .orElseThrow(() -> failure("COMPLAINT_REMEDIATION_NOT_FOUND", "处置记录不存在"));
    }

    private RemediationRow remediationRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Long original = nullableLong(rs, "original_complaint_id");
        return new RemediationRow(rs.getLong("id"), rs.getLong("complaint_id"), rs.getString("disposal_type"),
                rs.getString("target_ref"), rs.getString("status"), rs.getString("authorized_review_id"),
                rs.getString("failure_reason"), original == null ? null : original);
    }

    private List<DimensionRow> dimensionRows(String prefix, String column) {
        String sql = """
                SELECT %s AS dimension_id, COUNT(*) AS total
                  FROM complaints
                 WHERE %s IS NOT NULL
                 GROUP BY %s
                 ORDER BY total DESC, dimension_id ASC
                """.formatted(column, column, column);
        return jdbc.query(sql, (rs, i) -> new DimensionRow(prefix + ":" + rs.getLong("dimension_id"), rs.getInt("total")));
    }

    private CaseRow caseById(long id) {
        return jdbc.query("SELECT * FROM complaints WHERE id=?", (rs, i) -> caseRow(rs), id).stream().findFirst()
                .orElseThrow(() -> failure("COMPLAINT_NOT_FOUND", "投诉案件不存在"));
    }

    private void ensureStatus(long id, String expected) {
        String actual = caseById(id).status();
        if (!expected.equals(actual)) {
            throw failure("COMPLAINT_STATE_INVALID", "投诉状态不允许当前操作");
        }
    }

    private static String inferredQuality(CreateCommand command) {
        return command.tenantId() != null && command.channelId() != null
                && command.signatureId() != null && command.templateId() != null
                && command.messageId() != null && !command.messageId().isBlank()
                ? "COMPLETE" : "UNKNOWN";
    }

    private static CaseRow caseRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CaseRow(
                rs.getLong("id"), rs.getString("source"), nullableLong(rs, "tenant_id"),
                nullableLong(rs, "channel_id"), nullableLong(rs, "signature_id"),
                nullableLong(rs, "template_id"), rs.getString("message_id"),
                rs.getString("content_type"), rs.getString("complained_mobile"), rs.getString("summary"),
                rs.getString("status"), rs.getString("attribution_quality"), rs.getString("opinion"),
                rs.getString("remediation"), rs.getString("requirement"), nullableDateTime(rs, "accepted_at"),
                nullableDateTime(rs, "handled_at"), nullableDateTime(rs, "closed_at"), rs.getString("closed_note"),
                rs.getString("accepted_by"), rs.getString("handled_by"), rs.getString("closed_by"));
    }

    private static String enumValue(String value, List<String> allowed, String code) {
        String normalized = text(value, code, 64).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw failure(code, "枚举值不支持");
        }
        return normalized;
    }

    private static String text(String value, String code, int max) {
        if (value == null || value.isBlank()) {
            throw failure(code, switch (code) {
                case "COMPLAINT_OPINION_REQUIRED" -> "处理意见不能为空";
                case "COMPLAINT_REMEDIATION_REQUIRED" -> "处置动作不能为空";
                case "COMPLAINT_REQUIREMENT_REQUIRED" -> "整改要求不能为空";
                case "COMPLAINT_CLOSE_NOTE_REQUIRED" -> "关闭说明不能为空";
                default -> "必填字段不能为空";
            });
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw failure(code, "字段长度超过限制");
        }
        return trimmed;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw failure("COMPLAINT_FIELD_TOO_LONG", "字段长度超过限制");
        }
        return trimmed;
    }

    private static String actor(String actor) {
        return text(actor, "COMPLAINT_ACTOR_REQUIRED", 64);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long targetId(String target, String prefix) {
        String expected = prefix + ":";
        if (target == null || !target.startsWith(expected)) {
            throw failure("COMPLAINT_REMEDIATION_TARGET_INVALID", "处置目标格式不正确");
        }
        try {
            return Long.parseLong(target.substring(expected.length()));
        } catch (NumberFormatException ex) {
            throw failure("COMPLAINT_REMEDIATION_TARGET_INVALID", "处置目标编号不正确");
        }
    }

    private static void setLong(PreparedStatement ps, int index, Long value) throws java.sql.SQLException {
        if (value == null) {
            ps.setObject(index, null);
        } else {
            ps.setLong(index, value);
        }
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime nullableDateTime(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    private static void requireUpdated(int rows) {
        if (rows != 1) {
            throw failure("COMPLAINT_REMEDIATION_TARGET_NOT_FOUND", "处置目标不存在");
        }
    }

    private static void requireLinkedTarget(boolean linked) {
        if (!linked) {
            throw failure("COMPLAINT_REMEDIATION_TARGET_UNLINKED", "处置目标不属于投诉归因");
        }
    }

    public interface BlacklistPort {
        long create(Long tenantId, String mobile, String actor, String reason);
    }

    public record CreateCommand(String source, String summary, Long tenantId, Long channelId, Long signatureId,
                                Long templateId, String messageId, String contentType, String complainedMobile,
                                String attributionQuality, String requirement) { }

    public record StateCommand(String status, String opinion, String remediation, String requirement, String actor) { }

    public record RemediationCommand(String disposalType, String targetRef, String actor, String authorizedReviewId,
                                     String reason) { }

    public record RecoveryCommand(long disposalRecordId, String authorizedReviewId, String actor,
                                  String resumeCondition) { }

    public record CaseRow(long id, String source, Long tenantId, Long channelId, Long signatureId, Long templateId,
                          String messageId, String contentType, String complainedMobile, String summary,
                          String status, String attributionQuality, String opinion, String remediation,
                          String requirement, LocalDateTime acceptedAt, LocalDateTime handledAt,
                          LocalDateTime closedAt, String closedNote, String acceptedBy, String handledBy,
                          String closedBy) {
        public CaseRow withStatus(String next) {
            return new CaseRow(id, source, tenantId, channelId, signatureId, templateId, messageId, contentType,
                    complainedMobile, summary, next, attributionQuality, opinion, remediation, requirement,
                    acceptedAt, handledAt, closedAt, closedNote, acceptedBy, handledBy, closedBy);
        }
    }

    public record RemediationRow(long id, long complaintId, String disposalType, String targetRef, String status,
                                 String authorizedReviewId, String failureReason, Long originalComplaintId) { }

    public record DimensionRow(String dimension, int count) { }

    public record AnalyticsResponse(int totalCount, int unknownAttributionCount, List<DimensionRow> byTenant,
                                    List<DimensionRow> bySignature, List<DimensionRow> byContentType) { }
}
