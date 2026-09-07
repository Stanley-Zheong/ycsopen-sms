package com.ycsopen.sms.core.service.account;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Detects a simple source-address change and emits a durable, deduplicated notification handoff. */
@Service
public class LoginAnomalyService {
    private final JdbcTemplate jdbc;

    public LoginAnomalyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isUnusual(String previousIp, String currentIp) {
        return previousIp != null && currentIp != null && !previousIp.equals(currentIp);
    }

    public void enqueue(long userId, String sessionId) {
        jdbc.update("""
                INSERT INTO identity_notification_outbox
                    (event_type, target_user_id, source_ref, status, occurred_at)
                VALUES ('UNUSUAL_LOGIN', ?, ?, 'PENDING', CURRENT_TIMESTAMP)
                """, userId, sessionId);
    }
}
