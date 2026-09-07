package com.ycsopen.sms.core.service.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ycsopen.sms.core.service.audit.SecurityEventService;

/** Detects a simple source-address change and emits a durable, deduplicated notification handoff. */
@Service
public class LoginAnomalyService {
    private final JdbcTemplate jdbc;
    private final SecurityEventService securityEvents;

    public LoginAnomalyService(JdbcTemplate jdbc, SecurityEventService securityEvents) {
        this.jdbc = jdbc;
        this.securityEvents = securityEvents;
    }

    public boolean isUnusual(String previousIp, String currentIp) {
        return previousIp != null && currentIp != null && !previousIp.equals(currentIp);
    }

    @Transactional
    public void enqueue(long userId, Long tenantId, String sessionId, String clientIp,
                        String traceId) {
        jdbc.update("""
                INSERT INTO identity_notification_outbox
                    (event_type, target_user_id, source_ref, status, occurred_at)
                VALUES ('UNUSUAL_LOGIN', ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """, userId, sessionId);
        securityEvents.recordUnusualLogin(userId, tenantId, sessionId, clientIp, traceId);
    }

    @Transactional
    public void repeatedFailure(long userId, Long tenantId, long loginHistoryId,
                                String clientIp, String traceId) {
        securityEvents.recordRepeatedLoginFailure(
                userId, tenantId, loginHistoryId, clientIp, traceId);
    }
}
