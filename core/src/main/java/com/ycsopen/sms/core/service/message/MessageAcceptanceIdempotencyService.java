package com.ycsopen.sms.core.service.message;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;

/** Owns the tenant submitId idempotency row and the durable send intent for accepted HTTP requests. */
@Service
public class MessageAcceptanceIdempotencyService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration leaseDuration;

    public MessageAcceptanceIdempotencyService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC(), Duration.ofMinutes(2));
    }

    @Autowired
    public MessageAcceptanceIdempotencyService(
            JdbcTemplate jdbc,
            @Value("${ycsopen.message.submit-claim-lease:PT2M}") Duration leaseDuration) {
        this(jdbc, Clock.systemUTC(), leaseDuration);
    }

    MessageAcceptanceIdempotencyService(JdbcTemplate jdbc, Clock clock, Duration leaseDuration) {
        this.jdbc = jdbc;
        this.clock = Objects.requireNonNull(clock);
        this.leaseDuration = requirePositive(leaseDuration);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Claim claim(long tenantId, String submitId, String requestDigest) {
        String normalizedSubmitId = requireSubmitId(submitId);
        String digest = requireDigest(requestDigest);
        String processingToken = UUID.randomUUID().toString();
        try {
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO message_submits(tenant_id, submit_id, request_digest,
                          source_protocol, product_type, status)
                        VALUES (?, ?, ?, 'HTTP', 'NOTIFY', 'QUEUED')
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, tenantId);
                statement.setString(2, normalizedSubmitId);
                statement.setString(3, digest);
                return statement;
            }, key);
            long submissionId = key.getKey().longValue();
            jdbc.update("""
                    INSERT INTO message_submit_claim_leases(submission_id, processing_token, lease_expires_at)
                    VALUES (?,?,?)
                    """, submissionId, processingToken, leaseExpiresAt());
            return Claim.newSubmission(submissionId, normalizedSubmitId, digest, processingToken);
        } catch (DuplicateKeyException duplicate) {
            return existingClaim(tenantId, normalizedSubmitId, digest);
        }
    }

    public void attachResources(long submissionId, long templateId, long signatureId) {
        jdbc.update("""
                UPDATE message_submits
                   SET template_id = ?, signature_id = ?
                 WHERE id = ?
                """, templateId, signatureId, submissionId);
    }

    public void markAccepted(long submissionId) {
        jdbc.update("UPDATE message_submits SET status='ACCEPTED' WHERE id=?", submissionId);
    }

    /** Locks and renews the fenced claim for the entire caller-owned business transaction. */
    public void lockClaim(long submissionId, String processingToken) {
        ClaimLease lease = claimLeaseForUpdate(submissionId);
        if (!Objects.equals(lease.processingToken(), processingToken)) {
            throw new BusinessException("SUBMIT_CLAIM_LOST", "提交处理权已被其他请求接管");
        }
        String status = jdbc.queryForObject("SELECT status FROM message_submits WHERE id=?", String.class, submissionId);
        if (!"QUEUED".equals(status)) {
            throw new BusinessException("SUBMIT_CLAIM_TERMINAL", "提交请求已经结束处理");
        }
        jdbc.update("""
                UPDATE message_submit_claim_leases
                   SET lease_expires_at=?, updated_at=CURRENT_TIMESTAMP
                 WHERE submission_id=? AND processing_token=?
                """, leaseExpiresAt(), submissionId, processingToken);
    }

    /** Called by MessageRejectionRecorder after the business transaction has rolled back. */
    public void recordRejected(Claim claim, Long templateId, Long signatureId, String errorCode) {
        ClaimLease lease = claimLeaseForUpdate(claim.submissionId());
        if (!Objects.equals(lease.processingToken(), claim.processingToken())
                || lease.leaseExpiresAt().isBefore(now())) {
            throw new BusinessException("SUBMIT_CLAIM_LOST", "提交拒绝结果已失去写入权");
        }
        String safeCode = safeErrorCode(errorCode);
        int updated = jdbc.update("""
                UPDATE message_submits
                   SET status='REJECTED', reject_reason=?, template_id=?, signature_id=?
                 WHERE id=? AND request_digest=? AND status='QUEUED'
                """, safeCode, templateId, signatureId, claim.submissionId(), claim.requestDigest());
        if (updated == 1) {
            return;
        }
        ExistingSubmission existing = submissionForUpdate(claim.submissionId());
        if ("REJECTED".equals(existing.submitStatus())
                && Objects.equals(existing.rejectReason(), safeCode)
                && Objects.equals(existing.requestDigest(), claim.requestDigest())) {
            return;
        }
        throw new BusinessException("SUBMIT_CLAIM_TERMINAL", "提交请求已有其他终态");
    }

    public void enqueueSendIntent(long tenantId, long taskId, String messageId, long channelId) {
        jdbc.update("""
                INSERT INTO message_send_outbox(tenant_id, task_id, message_id, channel_id, state)
                VALUES (?, ?, ?, ?, 'READY')
                """, tenantId, taskId, messageId, channelId);
    }

    private Claim existingClaim(long tenantId, String submitId, String requestDigest) {
        ExistingSubmission snapshot = jdbc.query("""
                SELECT ms.id, ms.request_digest, ms.status, ms.reject_reason, mt.message_id, mt.send_status
                  FROM message_submits ms
                  LEFT JOIN message_tasks mt ON mt.submit_id = ms.id
                 WHERE ms.tenant_id = ? AND ms.submit_id = ?
                """, rs -> rs.next()
                ? existingSubmission(rs)
                : null, tenantId, submitId);
        if (snapshot == null) {
            throw new BusinessException("IDEMPOTENCY_NOT_FOUND", "业务流水不存在");
        }
        if (!"QUEUED".equals(snapshot.submitStatus())) {
            return terminalClaim(submitId, requestDigest, snapshot);
        }

        ClaimLease lease = claimLeaseForUpdate(snapshot.id());
        ExistingSubmission existing = submissionForUpdate(snapshot.id());
        if (!Objects.equals(existing.requestDigest(), requestDigest)) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT", "相同业务流水号的请求内容不一致");
        }
        if (!"QUEUED".equals(existing.submitStatus())) {
            return terminalClaim(submitId, requestDigest, existing);
        }
        if (!lease.leaseExpiresAt().isBefore(now())) {
            throw new BusinessException("REQUEST_IN_PROGRESS", "请求正在处理，请稍后重试");
        }
        String nextToken = UUID.randomUUID().toString();
        int reclaimed = jdbc.update("""
                UPDATE message_submit_claim_leases
                   SET processing_token=?, lease_expires_at=?, updated_at=CURRENT_TIMESTAMP
                 WHERE submission_id=? AND processing_token=?
                """, nextToken, leaseExpiresAt(), existing.id(), lease.processingToken());
        if (reclaimed != 1) {
            throw new BusinessException("REQUEST_IN_PROGRESS", "请求正在处理，请稍后重试");
        }
        return Claim.newSubmission(existing.id(), submitId, requestDigest, nextToken);
    }

    private Claim terminalClaim(String submitId, String requestDigest, ExistingSubmission existing) {
        if (!Objects.equals(existing.requestDigest(), requestDigest)) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT", "相同业务流水号的请求内容不一致");
        }
        if ("REJECTED".equals(existing.submitStatus())) {
            throw new BusinessException(safeErrorCode(existing.rejectReason()), "请求已被拒绝");
        }
        if (existing.messageId() == null || existing.taskStatus() == null) {
            throw new BusinessException("REQUEST_IN_PROGRESS", "请求正在处理，请稍后重试");
        }
        return Claim.duplicate(existing.id(), submitId, requestDigest,
                new SmsSendResponse(existing.messageId(), existing.taskStatus()));
    }

    private ClaimLease claimLeaseForUpdate(long submissionId) {
        ClaimLease lease = jdbc.query("""
                SELECT processing_token, lease_expires_at
                  FROM message_submit_claim_leases
                 WHERE submission_id=?
                 FOR UPDATE
                """, rs -> rs.next()
                ? new ClaimLease(rs.getString("processing_token"),
                        rs.getObject("lease_expires_at", LocalDateTime.class))
                : null, submissionId);
        if (lease == null) {
            throw new BusinessException("SUBMIT_CLAIM_LEASE_MISSING", "提交处理租约不存在");
        }
        return lease;
    }

    private ExistingSubmission submissionForUpdate(long submissionId) {
        ExistingSubmission existing = jdbc.query("""
                SELECT ms.id, ms.request_digest, ms.status, ms.reject_reason, mt.message_id, mt.send_status
                  FROM message_submits ms
                  LEFT JOIN message_tasks mt ON mt.submit_id=ms.id
                 WHERE ms.id=?
                 FOR UPDATE
                """, rs -> rs.next() ? existingSubmission(rs) : null, submissionId);
        if (existing == null) {
            throw new BusinessException("IDEMPOTENCY_NOT_FOUND", "业务流水不存在");
        }
        return existing;
    }

    private static ExistingSubmission existingSubmission(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ExistingSubmission(rs.getLong("id"), rs.getString("request_digest"),
                rs.getString("status"), rs.getString("reject_reason"),
                rs.getString("message_id"), rs.getString("send_status"));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private LocalDateTime leaseExpiresAt() {
        return now().plus(leaseDuration);
    }

    private static Duration requirePositive(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("submit claim lease must be positive");
        }
        return value;
    }

    private static String safeErrorCode(String errorCode) {
        String safe = errorCode == null ? "SUBMISSION_REJECTED" : errorCode.trim().toUpperCase(Locale.ROOT);
        return safe.matches("^[A-Z0-9_]{1,64}$") ? safe : "SUBMISSION_REJECTED";
    }

    private static String requireSubmitId(String submitId) {
        String normalized = submitId == null ? "" : submitId.trim();
        if (normalized.isBlank() || normalized.length() > 64
                || !normalized.matches("^[A-Za-z0-9_.:-]+$")) {
            throw new BusinessException("INVALID_SUBMIT_ID", "业务流水号不合法");
        }
        return normalized;
    }

    private static String requireDigest(String requestDigest) {
        if (requestDigest == null || !requestDigest.matches("^[0-9a-f]{64}$")) {
            throw new BusinessException("INVALID_REQUEST_DIGEST", "请求摘要不合法");
        }
        return requestDigest;
    }

    private record ExistingSubmission(long id, String requestDigest, String submitStatus, String rejectReason,
                                      String messageId, String taskStatus) { }

    private record ClaimLease(String processingToken, LocalDateTime leaseExpiresAt) { }

    public record Claim(long submissionId, String submitId, String requestDigest, String processingToken,
                        Optional<SmsSendResponse> existingResponse) {
        static Claim newSubmission(long submissionId, String submitId, String requestDigest) {
            return newSubmission(submissionId, submitId, requestDigest, "test-processing-token");
        }

        static Claim newSubmission(long submissionId, String submitId, String requestDigest, String processingToken) {
            return new Claim(submissionId, submitId, requestDigest, processingToken, Optional.empty());
        }

        static Claim duplicate(long submissionId, String submitId, String requestDigest, SmsSendResponse response) {
            return new Claim(submissionId, submitId, requestDigest, null, Optional.of(response));
        }
    }
}
