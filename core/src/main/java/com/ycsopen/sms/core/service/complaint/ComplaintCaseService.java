package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.logging.SafeLogValue;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger.Category;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger.Event;
import com.ycsopen.sms.core.service.risk.BlacklistRiskControlService;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private static final List<String> CONTENT_TYPES = List.of("VERIFY", "NOTIFY", "MARKETING");
    private static final List<String> DISPOSALS = List.of(
            "BLACKLIST_MOBILE", "SUSPEND_TENANT", "SUSPEND_SIGNATURE_OR_TEMPLATE", "SUSPEND_CHANNEL");
    private static final String REMEDIATION_FAILURE_REASON = "处置执行失败，请根据安全审计日志排查";

    private final JdbcTemplate jdbc;
    private final BlacklistPort blacklistPort;
    private final SecurityEventLogger security;
    private final TransactionTemplate commandTransaction;
    private final TransactionTemplate resourceTransaction;

    @Autowired
    public ComplaintCaseService(JdbcTemplate jdbc, BlacklistRiskControlService blacklistService,
                                PlatformTransactionManager transactionManager, SecurityEventLogger security) {
        this(jdbc, (tenantId, mobile, actor, reason) -> blacklistService.createEntry(
                new BlacklistRiskControlService.BlacklistEntryCreateRequest(
                        tenantId, mobile, "BLACK", "COMPLAINT_LINKED", reason, null), actor).id(),
                transactionManager, security);
    }

    ComplaintCaseService(JdbcTemplate jdbc, BlacklistPort blacklistPort) {
        this(jdbc, blacklistPort, new DataSourceTransactionManager(
                Objects.requireNonNull(jdbc.getDataSource())), new SecurityEventLogger());
    }

    ComplaintCaseService(JdbcTemplate jdbc, BlacklistPort blacklistPort,
                         PlatformTransactionManager transactionManager) {
        this(jdbc, blacklistPort, transactionManager, new SecurityEventLogger());
    }

    ComplaintCaseService(JdbcTemplate jdbc, BlacklistPort blacklistPort,
                         PlatformTransactionManager transactionManager, SecurityEventLogger security) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.blacklistPort = Objects.requireNonNull(blacklistPort);
        this.security = Objects.requireNonNull(security);
        this.commandTransaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.resourceTransaction = new TransactionTemplate(transactionManager);
        this.resourceTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public CaseRow create(CreateCommand command, String actor) {
        if (command == null) {
            throw failure("COMPLAINT_REQUEST_REQUIRED", "投诉请求不能为空");
        }
        String source = enumValue(command.source(), SOURCES, "COMPLAINT_SOURCE_INVALID");
        String summary = text(command.summary(), "COMPLAINT_SUMMARY_REQUIRED", 500);
        validateReferences(command);
        String quality = inferredQuality(command);
        String requirement = optionalText(command.requirement(), 500);
        String contentType = command.contentType() == null || command.contentType().isBlank()
                ? null : enumValue(command.contentType(), CONTENT_TYPES, "COMPLAINT_CONTENT_TYPE_INVALID");
        String mobile = optionalText(command.complainedMobile(), 32);
        String createdBy = actor(actor);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
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
            ps.setString(12, createdBy);
            ps.setTimestamp(13, now);
            ps.setTimestamp(14, now);
            return ps;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        appendEvent(id, "REGISTERED", createdBy, now, null, "PENDING",
                "来源：" + source + "；摘要：" + summary, null, "SUCCESS", null, null,
                null, "COMPLAINT", id);
        return caseById(id);
    }

    @Transactional(readOnly = true)
    public List<CaseRow> cases() {
        return jdbc.query("""
                SELECT * FROM complaints ORDER BY created_at DESC, id DESC LIMIT 200
                """, (rs, i) -> caseRow(rs));
    }

    @Transactional(readOnly = true)
    public List<RemediationRow> remediations() {
        return jdbc.query("""
                WITH visible_complaints AS (
                    SELECT id
                      FROM complaints
                     ORDER BY created_at DESC, id DESC
                     LIMIT 200
                ), ranked_remediations AS (
                    SELECT d.*,
                           ROW_NUMBER() OVER (
                               PARTITION BY d.complaint_id
                               ORDER BY d.disposed_at DESC, d.id DESC
                           ) AS latest_rank,
                           ROW_NUMBER() OVER (
                               PARTITION BY d.complaint_id, d.status
                               ORDER BY d.disposed_at DESC, d.id DESC
                           ) AS status_rank
                      FROM disposal_records d
                      JOIN visible_complaints c ON c.id = d.complaint_id
                )
                SELECT id, complaint_id, disposal_type, target_ref, status, authorized_review_id,
                       failure_reason, original_complaint_id
                  FROM ranked_remediations
                 WHERE latest_rank = 1 OR (status = 'FAILED' AND status_rank = 1)
                 ORDER BY disposed_at DESC, id DESC
                """, (rs, i) -> remediationRow(rs));
    }

    @Transactional(readOnly = true)
    public CaseDetail caseDetail(long id) {
        CaseRow complaint = caseById(id);
        List<CaseEventRow> timeline = jdbc.query("""
                SELECT id, complaint_id, event_type, actor, occurred_at, from_status, to_status,
                       evidence_text, target_ref, result, review_id, failure_reason, related_disposal_id
                  FROM complaint_case_events
                 WHERE complaint_id=?
                 ORDER BY occurred_at ASC, id ASC
                """, (rs, row) -> eventRow(rs), id);
        List<RemediationRow> caseRemediations = jdbc.query("""
                SELECT id, complaint_id, disposal_type, target_ref, status, authorized_review_id,
                       failure_reason, original_complaint_id
                  FROM disposal_records
                 WHERE complaint_id=?
                 ORDER BY disposed_at DESC, id DESC
                """, (rs, row) -> remediationRow(rs), id);
        return new CaseDetail(complaint, timeline, caseRemediations);
    }

    @Transactional(readOnly = true)
    public ReferenceOptions referenceOptions() {
        return new ReferenceOptions(
                jdbc.query("""
                        SELECT id, COALESCE(NULLIF(short_name, ''), NULLIF(full_name, ''), CONCAT('tenant:', id)) AS label
                          FROM tenants ORDER BY id LIMIT 500
                        """, (rs, row) -> new ReferenceOption(rs.getLong("id"), rs.getString("label"), null)),
                jdbc.query("""
                        SELECT id, COALESCE(NULLIF(channel_name, ''), CONCAT('channel:', id)) AS label
                          FROM channels ORDER BY id LIMIT 500
                        """, (rs, row) -> new ReferenceOption(rs.getLong("id"), rs.getString("label"), null)),
                jdbc.query("""
                        SELECT id, COALESCE(NULLIF(sign_content, ''), CONCAT('signature:', id)) AS label, tenant_id
                          FROM signatures ORDER BY id LIMIT 500
                        """, (rs, row) -> new ReferenceOption(
                        rs.getLong("id"), rs.getString("label"), nullableLong(rs, "tenant_id"))),
                jdbc.query("""
                        SELECT id, COALESCE(NULLIF(template_name, ''), CONCAT('template:', id)) AS label, tenant_id
                          FROM templates ORDER BY id LIMIT 500
                        """, (rs, row) -> new ReferenceOption(
                        rs.getLong("id"), rs.getString("label"), nullableLong(rs, "tenant_id"))));
    }

    @Transactional
    public CaseRow accept(long id, StateCommand command) {
        if (command == null) {
            throw failure("COMPLAINT_REQUEST_REQUIRED", "投诉请求不能为空");
        }
        String opinion = text(command.opinion(), "COMPLAINT_ACCEPT_OPINION_REQUIRED", 500);
        String acceptedBy = actor(command.actor());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int rows = jdbc.update("""
                UPDATE complaints SET status='PROCESSING', accepted_by=?, accepted_at=?, opinion=?, updated_at=?
                 WHERE id=? AND status='PENDING'
                """, acceptedBy, now, opinion, now, id);
        requireTransition(rows, id);
        appendEvent(id, "ACCEPTED", acceptedBy, now, "PENDING", "PROCESSING", opinion,
                null, "SUCCESS", null, null, null, "COMPLAINT", id);
        return caseById(id);
    }

    @Transactional
    public CaseRow handle(long id, StateCommand command) {
        if (command == null) {
            throw failure("COMPLAINT_REQUEST_REQUIRED", "投诉请求不能为空");
        }
        String opinion = text(command.opinion(), "COMPLAINT_OPINION_REQUIRED", 500);
        String remediation = text(command.remediation(), "COMPLAINT_REMEDIATION_REQUIRED", 500);
        String requirement = text(command.requirement(), "COMPLAINT_REQUIREMENT_REQUIRED", 500);
        String handledBy = actor(command.actor());
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        int rows = jdbc.update("""
                UPDATE complaints SET status='PROCESSED', opinion=?, remediation=?, requirement=?,
                       handling_note=?, corrective_action=?, handled_by=?, handled_at=?, updated_at=?
                 WHERE id=? AND status='PROCESSING'
                """, opinion, remediation, requirement, opinion, remediation, handledBy, now, now, id);
        requireTransition(rows, id);
        appendEvent(id, "HANDLED", handledBy, now, "PROCESSING", "PROCESSED",
                "处理意见：" + opinion + "；处置动作：" + remediation + "；整改要求：" + requirement,
                null, "SUCCESS", null, null, null, "COMPLAINT", id);
        return caseById(id);
    }

    public CaseRow close(long id, StateCommand command) {
        if (command == null) {
            throw failure("COMPLAINT_REQUEST_REQUIRED", "投诉请求不能为空");
        }
        String opinion = text(command.opinion(), "COMPLAINT_CLOSE_NOTE_REQUIRED", 500);
        String closedBy = actor(command.actor());
        return Objects.requireNonNull(commandTransaction.execute(status -> {
            requireStatus(lockedCase(id), "PROCESSED");
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            int rows = jdbc.update("""
                    UPDATE complaints SET status='CLOSED', closed_by=?, closed_at=?, closed_note=?, updated_at=?
                     WHERE id=? AND status='PROCESSED'
                    """, closedBy, now, opinion, now, id);
            requireTransition(rows, id);
            appendEvent(id, "CLOSED", closedBy, now, "PROCESSED", "CLOSED", opinion,
                    null, "SUCCESS", null, null, null, "COMPLAINT", id);
            return caseById(id);
        }));
    }

    public RemediationRow remediate(long complaintId, RemediationCommand command) {
        if (command == null) {
            throw failure("COMPLAINT_REQUEST_REQUIRED", "投诉请求不能为空");
        }
        String type = enumValue(command.disposalType(), DISPOSALS, "COMPLAINT_REMEDIATION_TYPE_INVALID");
        String target = text(command.targetRef(), "COMPLAINT_REMEDIATION_TARGET_REQUIRED", 64);
        String actor = actor(command.actor());
        String reviewId = text(command.authorizedReviewId(), "COMPLAINT_REVIEW_REQUIRED", 128);
        String reason = text(command.reason(), "COMPLAINT_REMEDIATION_REASON_REQUIRED", 255);
        String key = complaintId + ":" + type + ":" + target + ":" + reviewId;
        return Objects.requireNonNull(commandTransaction.execute(commandStatus -> {
            CaseRow complaint = lockedCase(complaintId);
            requireStatus(complaint, "PROCESSED");
            List<RemediationRow> existing = findRemediationByKey(key);
            if (!existing.isEmpty()) {
                return existing.get(0);
            }
            try {
                return Objects.requireNonNull(resourceTransaction.execute(resourceStatus -> {
                    applyTargetAction(complaint, type, target, actor, reason);
                    Timestamp occurredAt = Timestamp.valueOf(LocalDateTime.now());
                    RemediationRow applied = insertRemediation(
                            complaintId, type, target, actor, reviewId, "APPLIED", null, key,
                            complaintId, occurredAt);
                    appendEvent(complaintId, "REMEDIATION_APPLIED", actor, occurredAt,
                            "PROCESSED", "PROCESSED", reason, target, "APPLIED", reviewId, null,
                            applied.id(), "DISPOSAL_RECORD", applied.id());
                    return applied;
                }));
            } catch (RuntimeException ex) {
                if (ex instanceof BusinessException) {
                    throw ex;
                }
                logRemediationFailure(complaintId, type, ex);
                String failure = REMEDIATION_FAILURE_REASON;
                Timestamp occurredAt = Timestamp.valueOf(LocalDateTime.now());
                RemediationRow failed = insertRemediation(
                        complaintId, type, target, actor, reviewId, "FAILED", failure, key, complaintId, occurredAt);
                appendEvent(complaintId, "REMEDIATION_FAILED", actor, occurredAt,
                        "PROCESSED", "PROCESSED", reason, target, "FAILED", reviewId, failure,
                        failed.id(), "DISPOSAL_RECORD", failed.id());
                return failed;
            }
        }));
    }

    public RemediationRow recover(long complaintId, RecoveryCommand command) {
        if (command == null) {
            throw failure("COMPLAINT_REQUEST_REQUIRED", "投诉请求不能为空");
        }
        String reviewId = text(command.authorizedReviewId(), "COMPLAINT_RECOVERY_REVIEW_REQUIRED", 128);
        String resumeCondition = text(command.resumeCondition(), "COMPLAINT_RECOVERY_CONDITION_REQUIRED", 255);
        String recoveredBy = actor(command.actor());
        return Objects.requireNonNull(commandTransaction.execute(status -> {
            CaseRow complaint = lockedCase(complaintId);
            if (!List.of("PROCESSED", "CLOSED").contains(complaint.status())) {
                throw failure("COMPLAINT_STATE_STALE", "投诉状态已变化，请刷新后重试");
            }
            List<RemediationRow> failedRows = jdbc.query("""
                    SELECT id, complaint_id, disposal_type, target_ref, status, authorized_review_id,
                           failure_reason, original_complaint_id
                      FROM disposal_records
                     WHERE complaint_id=? AND status='FAILED'
                     ORDER BY disposed_at DESC, id DESC
                     LIMIT 1
                    """, (rs, row) -> remediationRow(rs), complaintId);
            if (failedRows.isEmpty()) {
                throw failure("COMPLAINT_RECOVERY_STATE_INVALID", "没有可恢复的失败处置");
            }
            RemediationRow original = failedRows.get(0);
            if (original.id() != command.disposalRecordId()) {
                throw failure("COMPLAINT_STATE_STALE", "失败处置已变化，请刷新后重试");
            }
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            int rows = jdbc.update("""
                    UPDATE disposal_records SET status='RECOVERED', recovered_by=?, recovered_at=?,
                           resume_condition=?, authorized_review_id=?, original_complaint_id=?
                     WHERE id=? AND complaint_id=? AND status='FAILED'
                    """, recoveredBy, now, resumeCondition, reviewId, complaintId,
                    command.disposalRecordId(), complaintId);
            if (rows != 1) {
                throw failure("COMPLAINT_STATE_STALE", "失败处置已变化，请刷新后重试");
            }
            appendEvent(complaintId, "RECOVERED", recoveredBy, now, complaint.status(), complaint.status(),
                    resumeCondition, original.targetRef(), "RECOVERED", reviewId, null, original.id(),
                    "DISPOSAL_RECORD", original.id());
            return remediationById(command.disposalRecordId());
        }));
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse analytics() {
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM complaints", Integer.class);
        int unknown = jdbc.queryForObject(
                "SELECT COUNT(*) FROM complaints WHERE attribution_quality='UNKNOWN'", Integer.class);
        return new AnalyticsResponse(total, unknown,
                jdbc.query("""
                        SELECT CAST(created_at AS DATE) AS complaint_day, COUNT(*) AS total
                          FROM complaints
                         GROUP BY CAST(created_at AS DATE)
                         ORDER BY complaint_day ASC
                        """, (rs, i) -> new TrendRow(
                        rs.getDate("complaint_day").toLocalDate().toString(), rs.getInt("total"))),
                dimensionRows("tenant", "tenant_id"),
                dimensionRows("signature", "signature_id"),
                jdbc.query("""
                        SELECT COALESCE(content_type, 'UNKNOWN') AS dimension, COUNT(*) AS total
                          FROM complaints GROUP BY COALESCE(content_type, 'UNKNOWN') ORDER BY total DESC, dimension ASC
                        """, (rs, i) -> new DimensionRow(rs.getString("dimension"), rs.getInt("total"))));
    }

    private void applyTargetAction(CaseRow row, String type, String target, String actor, String reason) {
        switch (type) {
            case "BLACKLIST_MOBILE" -> {
                if (row.tenantId() == null) {
                    throw failure("COMPLAINT_REMEDIATION_TENANT_REQUIRED", "黑名单处置需要机构归因");
                }
                String mobile = target.startsWith("mobile:")
                        ? text(target.substring("mobile:".length()), "COMPLAINT_REMEDIATION_TARGET_REQUIRED", 32)
                        : target;
                requireLinkedTarget(Objects.equals(row.complainedMobile(), mobile));
                blacklistPort.create(row.tenantId(), mobile, actor, "投诉案件:" + row.id());
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
                                             long originalComplaintId, Timestamp occurredAt) {
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
            ps.setTimestamp(5, occurredAt);
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
        String failureReason = rs.getString("failure_reason") == null ? null : REMEDIATION_FAILURE_REASON;
        return new RemediationRow(rs.getLong("id"), rs.getLong("complaint_id"), rs.getString("disposal_type"),
                rs.getString("target_ref"), rs.getString("status"), rs.getString("authorized_review_id"),
                failureReason, original == null ? null : original);
    }

    private CaseEventRow eventRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CaseEventRow(
                rs.getLong("id"), rs.getLong("complaint_id"), rs.getString("event_type"),
                rs.getString("actor"), rs.getTimestamp("occurred_at").toLocalDateTime(),
                rs.getString("from_status"), rs.getString("to_status"), rs.getString("evidence_text"),
                rs.getString("target_ref"), rs.getString("result"), rs.getString("review_id"),
                rs.getString("failure_reason"), nullableLong(rs, "related_disposal_id"));
    }

    private void appendEvent(long complaintId, String eventType, String actor, Timestamp occurredAt,
                             String fromStatus, String toStatus, String evidenceText, String targetRef,
                             String result, String reviewId, String failureReason, Long relatedDisposalId,
                             String sourceRecordType, long sourceRecordId) {
        jdbc.update("""
                INSERT INTO complaint_case_events(
                    complaint_id, event_type, actor, occurred_at, from_status, to_status,
                    evidence_text, target_ref, result, review_id, failure_reason, related_disposal_id,
                    source_record_type, source_record_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, complaintId, eventType, actor, occurredAt, fromStatus, toStatus,
                evidenceText, targetRef, result, reviewId, failureReason, relatedDisposalId,
                sourceRecordType, sourceRecordId);
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

    private void validateReferences(CreateCommand command) {
        if (command.tenantId() != null) {
            requireReferenceExists("SELECT COUNT(*) FROM tenants WHERE id=?", command.tenantId());
        }
        if (command.channelId() != null) {
            requireReferenceExists("SELECT COUNT(*) FROM channels WHERE id=?", command.channelId());
        }
        Long signatureTenant = command.signatureId() == null
                ? null : referenceTenant("SELECT tenant_id FROM signatures WHERE id=?", command.signatureId());
        Long templateTenant = command.templateId() == null
                ? null : referenceTenant("SELECT tenant_id FROM templates WHERE id=?", command.templateId());
        if (command.tenantId() != null) {
            if ((command.signatureId() != null && !Objects.equals(command.tenantId(), signatureTenant))
                    || (command.templateId() != null && !Objects.equals(command.tenantId(), templateTenant))) {
                throw failure("COMPLAINT_REFERENCE_TENANT_MISMATCH", "签名或模板与投诉机构不匹配");
            }
            return;
        }
        if (command.signatureId() != null && command.templateId() != null
                && signatureTenant != null && templateTenant != null
                && !Objects.equals(signatureTenant, templateTenant)) {
            throw failure("COMPLAINT_REFERENCE_TENANT_MISMATCH", "签名与模板不属于同一机构");
        }
    }

    private void requireReferenceExists(String sql, long id) {
        if (jdbc.queryForObject(sql, Integer.class, id) == 0) {
            throw failure("COMPLAINT_REFERENCE_NOT_FOUND", "投诉关联资源不存在");
        }
    }

    private Long referenceTenant(String sql, long id) {
        List<Long> owners = jdbc.query(sql, (rs, row) -> nullableLong(rs, "tenant_id"), id);
        if (owners.isEmpty()) {
            throw failure("COMPLAINT_REFERENCE_NOT_FOUND", "投诉关联资源不存在");
        }
        return owners.get(0);
    }

    private void logRemediationFailure(long complaintId, String type, RuntimeException exception) {
        security.warn(Event.PROVIDER_REJECTION, Category.PROVIDER,
                SafeLogValue.purpose("complaint:" + complaintId),
                SafeLogValue.purpose("type:" + type),
                SafeLogValue.purpose("exception:" + safeExceptionType(exception)),
                SafeLogValue.correlation(MDC.get("traceId")));
    }

    private static String safeExceptionType(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName()
                .replaceAll("[^A-Za-z0-9._:-]", "_");
        if (simpleName.isBlank()) {
            return "RuntimeException";
        }
        return simpleName.substring(0, Math.min(simpleName.length(), 50));
    }

    private CaseRow caseById(long id) {
        return jdbc.query("SELECT * FROM complaints WHERE id=?", (rs, i) -> caseRow(rs), id).stream().findFirst()
                .orElseThrow(() -> failure("COMPLAINT_NOT_FOUND", "投诉案件不存在"));
    }

    private CaseRow lockedCase(long id) {
        return jdbc.query("SELECT * FROM complaints WHERE id=? FOR UPDATE", (rs, row) -> caseRow(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> failure("COMPLAINT_NOT_FOUND", "投诉案件不存在"));
    }

    private void requireStatus(CaseRow row, String expected) {
        if (!expected.equals(row.status())) {
            throw failure("COMPLAINT_STATE_STALE", "投诉状态已变化，请刷新后重试");
        }
    }

    private void requireTransition(int rows, long id) {
        if (rows == 1) {
            return;
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM complaints WHERE id=?", Integer.class, id) == 0) {
            throw failure("COMPLAINT_NOT_FOUND", "投诉案件不存在");
        }
        throw failure("COMPLAINT_STATE_STALE", "投诉状态已变化，请刷新后重试");
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
                case "COMPLAINT_ACCEPT_OPINION_REQUIRED" -> "受理意见不能为空";
                case "COMPLAINT_REMEDIATION_REQUIRED" -> "处置动作不能为空";
                case "COMPLAINT_REMEDIATION_REASON_REQUIRED" -> "处置原因不能为空";
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

    public record CaseEventRow(long id, long complaintId, String eventType, String actor,
                               LocalDateTime occurredAt, String fromStatus, String toStatus,
                               String evidenceText, String targetRef, String result, String reviewId,
                               String failureReason, Long relatedDisposalId) { }

    public record CaseDetail(CaseRow complaint, List<CaseEventRow> timeline,
                             List<RemediationRow> remediations) { }

    public record ReferenceOption(long id, String label, Long tenantId) { }

    public record ReferenceOptions(List<ReferenceOption> tenants, List<ReferenceOption> channels,
                                   List<ReferenceOption> signatures, List<ReferenceOption> templates) { }

    public record DimensionRow(String dimension, int count) { }

    public record TrendRow(String day, int count) { }

    public record AnalyticsResponse(int totalCount, int unknownAttributionCount, List<TrendRow> trend,
                                    List<DimensionRow> byTenant,
                                    List<DimensionRow> bySignature, List<DimensionRow> byContentType) { }
}
