package com.ycsopen.sms.core.service.signature;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.service.channel.health.ChannelCandidateEligibilityService;
import com.ycsopen.sms.core.service.tenant.TenantEligibilityPolicy;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

/** Phase 12: F-3.1/F-3.2/F-3.3 签名申请、审核与通道报备生命周期。 */
@Service
public class SignatureLifecycleService {
    private static final Set<String> SIGN_TYPES = Set.of("ENTERPRISE", "APP", "TRADEMARK", "INSTITUTION", "GOVERNMENT");
    private static final Set<String> USAGE_TYPES = Set.of("SELF", "OTHER");
    private static final Set<String> REVIEW_DECISIONS = Set.of("APPROVE", "REJECT", "SUPPLEMENT_REQUIRED");
    private static final Set<String> FILING_RESULTS = Set.of("REGISTERED", "FAILED");

    private final JdbcTemplate jdbc;
    private final TenantEligibilityPolicy tenantEligibility;
    private final ChannelRepository channels;
    private final ChannelCandidateEligibilityService channelEligibility;

    public SignatureLifecycleService(JdbcTemplate jdbc, TenantEligibilityPolicy tenantEligibility,
                                     ChannelRepository channels,
                                     ChannelCandidateEligibilityService channelEligibility) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.tenantEligibility = Objects.requireNonNull(tenantEligibility);
        this.channels = Objects.requireNonNull(channels);
        this.channelEligibility = Objects.requireNonNull(channelEligibility);
    }

    @Transactional
    public SignatureResponse submitApplication(long tenantId, SignatureApplicationRequest request) {
        String signContent = cleanRequired(request == null ? null : request.signContent(), "SIGNATURE_CONTENT_REQUIRED", 64);
        String signType = enumValue(request.signType(), SIGN_TYPES, "SIGNATURE_TYPE_INVALID");
        String usageType = enumValue(request.usageType() == null ? "SELF" : request.usageType(), USAGE_TYPES, "SIGNATURE_USAGE_INVALID");
        String evidenceRef = cleanOptional(request.evidenceRef(), 255);
        String applicantName = cleanOptional(request.applicantName(), 50);
        if (evidenceRequired(signType, usageType) && (evidenceRef == null || evidenceRef.isBlank())) {
            throw failure("SIGNATURE_EVIDENCE_REQUIRED", "签名证明材料不能为空");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM signatures WHERE tenant_id=? AND sign_content=?",
                Integer.class, tenantId, signContent) > 0) {
            throw failure("SIGNATURE_DUPLICATED", "同一机构签名内容已存在");
        }

        tenantEligibility.requireNewWorkAllowed(tenantId);
        String risk = risk(signType, usageType);
        String signCode = signCode(tenantId, signContent, signType, usageType);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO signatures(tenant_id,biz_type,sign_code,sign_content,sign_type,usage_type,risk_level,evidence_url,applicant_name,audit_status,audit_comment)
                        VALUES (?,'DOMESTIC',?,?,?,?,?,?,?,'PENDING','')
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, tenantId);
                ps.setString(2, signCode);
                ps.setString(3, signContent);
                ps.setString(4, signType);
                ps.setString(5, usageType);
                ps.setString(6, risk);
                ps.setString(7, evidenceRef);
                ps.setString(8, applicantName);
                return ps;
            }, keyHolder);
        } catch (DuplicateKeyException ex) {
            throw failure("SIGNATURE_DUPLICATED", "同一机构签名编码已存在");
        }
        Object generatedId = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("ID");
        if (!(generatedId instanceof Number)) {
            generatedId = keyHolder.getKeys() == null ? null : keyHolder.getKeys().get("id");
        }
        if (!(generatedId instanceof Number idNumber)) {
            throw failure("SIGNATURE_CREATE_FAILED", "签名申请创建失败");
        }
        long id = idNumber.longValue();
        appendHistory(id, "SUBMITTED", "tenant:" + tenantId, "提交申请", risk);
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<SignatureResponse> listTenant(long tenantId) {
        return rows("SELECT * FROM signatures WHERE tenant_id=? ORDER BY created_at DESC, id DESC", tenantId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SignatureReviewQueueResponse reviewQueue(String keyword, String tenantId, String signType,
                                                    String riskLevel, String auditStatus) {
        StringBuilder sql = new StringBuilder("SELECT * FROM signatures WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND sign_content LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND tenant_id=?");
            try {
                args.add(Long.parseLong(tenantId.trim()));
            } catch (NumberFormatException ex) {
                throw failure("SIGNATURE_TENANT_ID_INVALID", "租户编号不合法");
            }
        }
        if (signType != null && !signType.isBlank()) {
            sql.append(" AND sign_type=?");
            args.add(enumValue(signType, SIGN_TYPES, "SIGNATURE_TYPE_INVALID"));
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            sql.append(" AND risk_level=?");
            args.add(enumValue(riskLevel, Set.of("LOW", "MEDIUM", "HIGH"), "SIGNATURE_RISK_INVALID"));
        }
        if (auditStatus != null && !auditStatus.isBlank()) {
            sql.append(" AND audit_status=?");
            args.add(normalizeStatus(auditStatus));
        }
        sql.append(" ORDER BY created_at DESC, id DESC");
        List<SignatureResponse> items = rows(sql.toString(), args.toArray()).stream().map(this::toResponse).toList();
        return new SignatureReviewQueueResponse(summary(), items);
    }

    @Transactional(readOnly = true)
    public SignatureReviewSummaryResponse summary() {
        return new SignatureReviewSummaryResponse(
                count("SELECT COUNT(*) FROM signatures"),
                count("SELECT COUNT(*) FROM signatures WHERE audit_status='PENDING'"),
                count("SELECT COUNT(*) FROM signatures WHERE audit_status='APPROVED'"),
                count("SELECT COUNT(*) FROM signatures WHERE audit_status='REJECTED'"),
                count("SELECT COUNT(*) FROM signatures WHERE audit_status='SUPPLEMENT_REQUIRED'"),
                count("SELECT COUNT(*) FROM signatures WHERE risk_level='HIGH'")
        );
    }

    @Transactional(readOnly = true)
    public SignatureResponse get(long signatureId) {
        return toResponse(row(signatureId));
    }

    @Transactional
    public SignatureResponse decide(long signatureId, SignatureDecisionRequest request) {
        SignatureRow row = row(signatureId);
        String decision = enumValue(request == null ? null : request.decision(), REVIEW_DECISIONS, "SIGNATURE_REVIEW_DECISION_INVALID");
        String actor = cleanRequired(request == null ? null : request.actor(), "SIGNATURE_REVIEW_ACTOR_REQUIRED", 64);
        String opinion = safeOpinion(request == null ? null : request.opinion());
        if (!Set.of("PENDING", "SUPPLEMENT_REQUIRED").contains(row.auditStatus())) {
            throw failure("SIGNATURE_REVIEW_STATE_INVALID", "当前签名状态不可审核");
        }
        String targetStatus = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "REJECT" -> "REJECTED";
            default -> "SUPPLEMENT_REQUIRED";
        };
        int changed = jdbc.update("""
                UPDATE signatures SET audit_status=?, audit_comment=?, audit_time=CURRENT_TIMESTAMP
                WHERE id=? AND audit_status IN ('PENDING','SUPPLEMENT_REQUIRED')
                """, targetStatus, opinion, signatureId);
        if (changed != 1) {
            throw failure("SIGNATURE_REVIEW_STATE_INVALID", "当前签名状态不可审核");
        }
        appendHistory(signatureId, targetStatus, actor, opinion, row.riskLevel());
        return get(signatureId);
    }

    @Transactional(readOnly = true)
    public List<SignatureFilingResponse> filingMatrix(long signatureId) {
        SignatureRow signature = row(signatureId);
        Map<Long, FilingRow> existing = existingFilings(signature.id());
        return channels.findAll().stream()
                .sorted(Comparator.comparing(Channel::getId))
                .map(channel -> filingResponse(signature.id(), channel, existing.get(channel.getId())))
                .toList();
    }

    @Transactional
    public SignatureFilingResponse requestFiling(long signatureId, long channelId, String actor) {
        SignatureRow signature = row(signatureId);
        if (!"APPROVED".equals(signature.auditStatus())) {
            throw failure("SIGNATURE_NOT_APPROVED", "签名未审核通过，不能通道报备");
        }
        Channel channel = channel(channelId);
        String cleanActor = cleanRequired(actor, "SIGNATURE_FILING_ACTOR_REQUIRED", 64);
        FilingRow existing = existingFiling(signatureId, channelId).orElse(null);
        if (existing != null && Set.of("REGISTERING", "REGISTERED").contains(existing.status())) {
            return filingResponse(signatureId, channel, existing);
        }
        int nextAttempt = existing == null ? 1 : existing.attemptCount() + 1;
        String requestId = "filing-" + signatureId + "-" + channelId + "-" + nextAttempt;
        if (existing == null) {
            try {
                jdbc.update("""
                        INSERT INTO signature_channel_registrations(signature_id,channel_id,reg_status,provider_request_id,result_message,attempt_count,requested_by,last_attempt_at)
                        VALUES (?,?,'REGISTERING',?,'',?,?,CURRENT_TIMESTAMP)
                        """, signatureId, channelId, requestId, nextAttempt, cleanActor);
            } catch (DuplicateKeyException ex) {
                return filingResponse(signatureId, channel, existingFiling(signatureId, channelId).orElseThrow());
            }
        } else {
            int changed = jdbc.update("""
                    UPDATE signature_channel_registrations
                    SET reg_status='REGISTERING', provider_request_id=?, result_message='', attempt_count=?, requested_by=?, last_attempt_at=CURRENT_TIMESTAMP
                    WHERE signature_id=? AND channel_id=? AND reg_status IN ('NONE','FAILED')
                    """, requestId, nextAttempt, cleanActor, signatureId, channelId);
            if (changed != 1) {
                return filingResponse(signatureId, channel, existingFiling(signatureId, channelId).orElseThrow());
            }
        }
        return filingResponse(signatureId, channel, existingFiling(signatureId, channelId).orElseThrow());
    }

    @Transactional
    public SignatureFilingResponse recordFilingResult(long signatureId, long channelId, SignatureFilingResultRequest request) {
        SignatureRow signature = row(signatureId);
        if (!"APPROVED".equals(signature.auditStatus())) {
            throw failure("SIGNATURE_NOT_APPROVED", "签名未审核通过，不能记录通道报备结果");
        }
        Channel channel = channel(channelId);
        String status = enumValue(request == null ? null : request.status(), FILING_RESULTS, "SIGNATURE_FILING_RESULT_INVALID");
        String message = cleanRequired(request == null ? null : request.resultMessage(), "SIGNATURE_FILING_RESULT_REQUIRED", 255);
        cleanRequired(request == null ? null : request.actor(), "SIGNATURE_FILING_ACTOR_REQUIRED", 64);
        FilingRow existing = existingFiling(signatureId, channelId)
                .orElseThrow(() -> failure("SIGNATURE_FILING_REQUEST_REQUIRED", "需先提交通道报备请求"));
        if (!"REGISTERING".equals(existing.status())) {
            throw failure("SIGNATURE_FILING_STATE_INVALID", "当前报备状态不可记录结果");
        }
        int changed = jdbc.update("""
                UPDATE signature_channel_registrations
                SET reg_status=?, result_message=?, last_attempt_at=CURRENT_TIMESTAMP
                WHERE signature_id=? AND channel_id=? AND reg_status='REGISTERING'
                """, status, message, signatureId, channelId);
        if (changed != 1) {
            throw failure("SIGNATURE_FILING_STATE_INVALID", "当前报备状态不可记录结果");
        }
        return filingResponse(signatureId, channel, existingFiling(signatureId, channelId).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<SignatureFilingResponse> usableChannels(long signatureId) {
        SignatureRow signature = row(signatureId);
        if (!"APPROVED".equals(signature.auditStatus())) {
            return List.of();
        }
        return jdbc.query("""
                SELECT signature_id, channel_id, reg_status, provider_request_id, result_message, attempt_count
                FROM signature_channel_registrations
                WHERE signature_id=? AND reg_status='REGISTERED'
                ORDER BY channel_id
                """, (rs, rowNum) -> new FilingRow(rs.getLong("signature_id"), rs.getLong("channel_id"),
                rs.getString("reg_status"), rs.getString("provider_request_id"),
                rs.getString("result_message"), rs.getInt("attempt_count")), signatureId)
                .stream()
                .map(filing -> channels.findById(filing.channelId()).map(channel -> filingResponse(signatureId, channel, filing)).orElse(null))
                .filter(Objects::nonNull)
                .filter(SignatureFilingResponse::channelEligible)
                .toList();
    }

    private SignatureFilingResponse filingResponse(long signatureId, Channel channel, FilingRow filing) {
        var decision = channelEligibility.evaluate(channel);
        return new SignatureFilingResponse(
                signatureId,
                channel.getId(),
                channel.getChannelName(),
                channel.getProtocol() == null ? null : channel.getProtocol().name(),
                channel.getOperator() == null ? null : channel.getOperator().name(),
                filing == null ? "NONE" : filing.status(),
                filing == null ? null : filing.providerRequestId(),
                filing == null ? null : filing.resultMessage(),
                filing == null ? 0 : filing.attemptCount(),
                decision.eligible(),
                decision.reasonCode()
        );
    }

    private Map<Long, FilingRow> existingFilings(long signatureId) {
        Map<Long, FilingRow> rows = new HashMap<>();
        jdbc.query("""
                SELECT signature_id, channel_id, reg_status, provider_request_id, result_message, attempt_count
                FROM signature_channel_registrations WHERE signature_id=?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> rows.put(rs.getLong("channel_id"), new FilingRow(rs.getLong("signature_id"),
                rs.getLong("channel_id"), rs.getString("reg_status"), rs.getString("provider_request_id"),
                rs.getString("result_message"), rs.getInt("attempt_count"))), signatureId);
        return rows;
    }

    private Optional<FilingRow> existingFiling(long signatureId, long channelId) {
        List<FilingRow> rows = jdbc.query("""
                SELECT signature_id, channel_id, reg_status, provider_request_id, result_message, attempt_count
                FROM signature_channel_registrations WHERE signature_id=? AND channel_id=?
                """, (rs, rowNum) -> new FilingRow(rs.getLong("signature_id"), rs.getLong("channel_id"),
                rs.getString("reg_status"), rs.getString("provider_request_id"),
                rs.getString("result_message"), rs.getInt("attempt_count")), signatureId, channelId);
        return rows.stream().findFirst();
    }

    private Channel channel(long channelId) {
        return channels.findById(channelId).orElseThrow(() -> failure("CHANNEL_NOT_FOUND", "通道不存在"));
    }

    private SignatureResponse toResponse(SignatureRow row) {
        return new SignatureResponse(row.id(), row.tenantId(), row.signCode(), row.signContent(), row.signType(),
                row.usageType(), row.riskLevel(), row.evidenceRef(), row.applicantName(), row.auditStatus(),
                row.auditComment(), row.auditTime(), row.createdAt(), history(row.id()));
    }

    private List<SignatureResponse.History> history(long signatureId) {
        return jdbc.query("""
                SELECT event_type, actor, opinion, risk_level, created_at
                FROM signature_review_history WHERE signature_id=? ORDER BY created_at, id
                """, (rs, rowNum) -> new SignatureResponse.History(rs.getString("event_type"),
                rs.getString("actor"), rs.getString("opinion"), rs.getString("risk_level"),
                rs.getTimestamp("created_at").toLocalDateTime()), signatureId);
    }

    private List<SignatureRow> rows(String sql, Object... args) {
        return jdbc.query(sql, (rs, rowNum) -> new SignatureRow(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getString("sign_code"), rs.getString("sign_content"), rs.getString("sign_type"),
                rs.getString("usage_type"), rs.getString("risk_level"), rs.getString("evidence_url"),
                rs.getString("applicant_name"), rs.getString("audit_status"), rs.getString("audit_comment"),
                rs.getTimestamp("audit_time") == null ? null : rs.getTimestamp("audit_time").toLocalDateTime(),
                rs.getTimestamp("created_at").toLocalDateTime()), args);
    }

    private SignatureRow row(long signatureId) {
        List<SignatureRow> rows = rows("SELECT * FROM signatures WHERE id=?", signatureId);
        if (rows.isEmpty()) {
            throw failure("SIGNATURE_NOT_FOUND", "签名不存在");
        }
        return rows.getFirst();
    }

    private void appendHistory(long signatureId, String event, String actor, String opinion, String risk) {
        jdbc.update("""
                INSERT INTO signature_review_history(signature_id,event_type,actor,opinion,risk_level)
                VALUES (?,?,?,?,?)
                """, signatureId, event, actor, opinion, risk);
    }

    private long count(String sql) {
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private static boolean evidenceRequired(String signType, String usageType) {
        return "TRADEMARK".equals(signType) || "OTHER".equals(usageType);
    }

    private static String risk(String signType, String usageType) {
        if ("TRADEMARK".equals(signType)) {
            return "HIGH";
        }
        return "OTHER".equals(usageType) ? "MEDIUM" : "LOW";
    }

    private static String signCode(long tenantId, String content, String signType, String usageType) {
        return "SGN" + tenantId + Integer.toHexString(Objects.hash(content, signType, usageType)).toUpperCase(Locale.ROOT);
    }

    private static String enumValue(String value, Set<String> allowed, String code) {
        if (value == null || !allowed.contains(value.trim().toUpperCase(Locale.ROOT))) {
            throw failure(code, "枚举值不合法");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeStatus(String status) {
        return enumValue(status, Set.of("PENDING", "APPROVED", "REJECTED", "SUPPLEMENT_REQUIRED"), "SIGNATURE_STATUS_INVALID");
    }

    private static String safeOpinion(String value) {
        String opinion = cleanRequired(value, "SIGNATURE_REVIEW_OPINION_REQUIRED", 500);
        if (opinion.contains("\n") || opinion.contains("\r") || opinion.contains("\t")) {
            throw failure("SIGNATURE_REVIEW_OPINION_INVALID", "审核意见格式不合法");
        }
        String lower = opinion.toLowerCase(Locale.ROOT);
        if (lower.contains("password") || lower.contains("token") || lower.contains("secret") || lower.contains("hash=")) {
            throw failure("SIGNATURE_REVIEW_OPINION_INVALID", "审核意见包含敏感片段");
        }
        return opinion;
    }

    private static String cleanRequired(String value, String code, int max) {
        String cleaned = cleanOptional(value, max);
        if (cleaned == null || cleaned.isBlank()) {
            throw failure(code, "必填字段不能为空");
        }
        return cleaned;
    }

    private static String cleanOptional(String value, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() > max) {
            throw failure("SIGNATURE_FIELD_TOO_LONG", "字段超过长度限制");
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    private record SignatureRow(long id, long tenantId, String signCode, String signContent, String signType,
                                String usageType, String riskLevel, String evidenceRef, String applicantName,
                                String auditStatus, String auditComment, LocalDateTime auditTime,
                                LocalDateTime createdAt) { }

    private record FilingRow(long signatureId, long channelId, String status, String providerRequestId,
                             String resultMessage, int attemptCount) { }
}
