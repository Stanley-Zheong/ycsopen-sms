package com.ycsopen.sms.core.service.delivery;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageStatusQueryService {
    private final JdbcTemplate jdbc;

    public MessageStatusQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public MessageStatusDetail status(long tenantId, String messageId) {
        if (tenantId <= 0 || messageId == null || messageId.isBlank()) {
            throw new BusinessException("INVALID_STATUS_QUERY", "查询参数不合法");
        }
        List<MessageStatusDetail> matches = jdbc.query("""
                SELECT t.message_id, t.tenant_id, t.send_status, t.channel_id, t.channel_msg_id,
                       t.error_code, t.error_message, t.send_time, t.deliver_time, t.created_at,
                       s.submit_id AS client_submit_id, s.status AS submission_status,
                       o.state AS outbox_state, b.billing_status
                  FROM message_tasks t
             LEFT JOIN message_submits s ON s.id=t.submit_id
             LEFT JOIN message_send_outbox o ON o.task_id=t.id
             LEFT JOIN billing_records b ON b.task_ref_id=t.id
                 WHERE t.tenant_id=? AND t.message_id=?
                 LIMIT 1
                """, (rs, row) -> new MessageStatusDetail(
                rs.getString("message_id"),
                rs.getLong("tenant_id"),
                rs.getString("client_submit_id"),
                rs.getString("submission_status"),
                rs.getString("send_status"),
                rs.getObject("channel_id", Long.class),
                rs.getString("channel_msg_id"),
                rs.getString("outbox_state"),
                rs.getString("billing_status"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("send_time")),
                timestamp(rs.getTimestamp("deliver_time"))), tenantId, messageId.trim());
        if (matches.isEmpty()) {
            throw new BusinessException("MESSAGE_STATUS_NOT_FOUND", "消息不存在或无权查询");
        }
        return matches.get(0);
    }

    private static LocalDateTime timestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record MessageStatusDetail(String messageId, long tenantId, String submitId,
                                      String submissionStatus, String sendStatus, Long channelId,
                                      String providerMessageId, String outboxState, String billingStatus,
                                      String errorCode, String errorMessage, LocalDateTime acceptedAt,
                                      LocalDateTime sentAt, LocalDateTime deliveredAt) { }
}
