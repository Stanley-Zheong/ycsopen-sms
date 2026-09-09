package com.ycsopen.sms.core.service.message;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/** Owns the tenant submitId idempotency row and the durable send intent for accepted HTTP requests. */
@Service
public class MessageAcceptanceIdempotencyService {
    private final JdbcTemplate jdbc;

    public MessageAcceptanceIdempotencyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Claim claim(long tenantId, String submitId, String requestDigest) {
        String normalizedSubmitId = requireSubmitId(submitId);
        String digest = requireDigest(requestDigest);
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
            return Claim.newSubmission(key.getKey().longValue(), normalizedSubmitId, digest);
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

    public void enqueueSendIntent(long tenantId, long taskId, String messageId, long channelId) {
        jdbc.update("""
                INSERT INTO message_send_outbox(tenant_id, task_id, message_id, channel_id, state)
                VALUES (?, ?, ?, ?, 'READY')
                """, tenantId, taskId, messageId, channelId);
    }

    private Claim existingClaim(long tenantId, String submitId, String requestDigest) {
        ExistingSubmission existing = jdbc.query("""
                SELECT ms.id, ms.request_digest, mt.message_id, mt.send_status
                  FROM message_submits ms
                  LEFT JOIN message_tasks mt ON mt.submit_id = ms.id
                 WHERE ms.tenant_id = ? AND ms.submit_id = ?
                 FOR UPDATE
                """, rs -> rs.next()
                ? new ExistingSubmission(rs.getLong("id"), rs.getString("request_digest"),
                        rs.getString("message_id"), rs.getString("send_status"))
                : null, tenantId, submitId);
        if (existing == null) {
            throw new BusinessException("IDEMPOTENCY_NOT_FOUND", "业务流水不存在");
        }
        if (!Objects.equals(existing.requestDigest(), requestDigest)) {
            throw new BusinessException("IDEMPOTENCY_CONFLICT", "相同业务流水号的请求内容不一致");
        }
        if (existing.messageId() == null || existing.status() == null) {
            throw new BusinessException("REQUEST_IN_PROGRESS", "请求正在处理，请稍后重试");
        }
        return Claim.duplicate(existing.id(), submitId, requestDigest,
                new SmsSendResponse(existing.messageId(), existing.status()));
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

    private record ExistingSubmission(long id, String requestDigest, String messageId, String status) { }

    public record Claim(long submissionId, String submitId, String requestDigest,
                        Optional<SmsSendResponse> existingResponse) {
        static Claim newSubmission(long submissionId, String submitId, String requestDigest) {
            return new Claim(submissionId, submitId, requestDigest, Optional.empty());
        }

        static Claim duplicate(long submissionId, String submitId, String requestDigest, SmsSendResponse response) {
            return new Claim(submissionId, submitId, requestDigest, Optional.of(response));
        }
    }
}
