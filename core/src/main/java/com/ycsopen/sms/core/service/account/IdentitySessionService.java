package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.security.JwtTokenProvider;
import com.ycsopen.sms.core.domain.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.sql.PreparedStatement;

/** Durable session and login-history boundary used by authentication and logout. */
@Service
public class IdentitySessionService {
    private final JdbcTemplate jdbc;

    public IdentitySessionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void open(User user, JwtTokenProvider.IssuedToken token, String clientIp) {
        open(user, token, clientIp, null, false);
    }

    public void open(User user, JwtTokenProvider.IssuedToken token, String clientIp,
                     String userAgent, boolean abnormalLogin) {
        jdbc.update("""
                INSERT INTO user_sessions
                    (id, user_id, user_type, tenant_id, login_ip, user_agent, expires_at, is_abnormal_login)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, token.sessionId(), user.getId(), user.getUserType().name(), user.getTenantId(),
                clientIp, normalizeUserAgent(userAgent), Timestamp.from(token.expiresAt()), abnormalLogin);
        record(user.getId(), user.getUsername(), clientIp,
                abnormalLogin ? "SUCCESS_UNUSUAL" : "SUCCESS", userAgent);
    }

    public boolean isActive(String sessionId, long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_sessions
                WHERE id = ? AND user_id = ? AND expires_at > CURRENT_TIMESTAMP AND revoked_at IS NULL
                """, Integer.class, sessionId, userId);
        return count != null && count == 1;
    }

    public void revoke(String sessionId, long userId) {
        jdbc.update("UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?",
                sessionId, userId);
    }

    public void record(Long userId, String username, String clientIp, String outcome) {
        record(userId, username, clientIp, outcome, null);
    }

    public void record(Long userId, String username, String clientIp, String outcome, String userAgent) {
        jdbc.update("""
                INSERT INTO login_history(user_id, username, login_ip, user_agent, outcome, occurred_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, userId, username, clientIp, normalizeUserAgent(userAgent), outcome);
    }

    /** Inserts a detection source row and returns its stable database identity. */
    public long recordWithId(Long userId, String username, String clientIp,
                             String outcome, String userAgent) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO login_history(user_id, username, login_ip, user_agent, outcome, occurred_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, new String[] {"id"});
            if (userId == null) statement.setNull(1, java.sql.Types.BIGINT);
            else statement.setLong(1, userId);
            statement.setString(2, username);
            statement.setString(3, clientIp);
            statement.setString(4, normalizeUserAgent(userAgent));
            statement.setString(5, outcome);
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("login history key was not returned");
        return id.longValue();
    }

    private static String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String trimmed = userAgent.trim();
        return trimmed.length() <= 512 ? trimmed : trimmed.substring(0, 512);
    }
}
