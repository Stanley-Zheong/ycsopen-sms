package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.web.dto.LoginHistoryItemResponse;
import com.ycsopen.sms.core.web.dto.LoginHistoryPageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LoginHistoryService {

    private final JdbcTemplate jdbc;

    public LoginHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public LoginHistoryPageResponse query(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("INVALID_PAGE", "分页参数不合法");
        }
        long offset = Math.multiplyExact((long) page, size);
        Integer total;
        List<LoginHistoryItemResponse> items;
        if (userId == null) {
            total = jdbc.queryForObject("SELECT COUNT(*) FROM login_history", Integer.class);
            items = jdbc.query("""
                    SELECT id, user_id, username, login_ip, user_agent, outcome, occurred_at
                    FROM login_history
                    ORDER BY occurred_at DESC, id DESC
                    LIMIT ? OFFSET ?
                    """, (rs, rowNum) -> item(rs), size, offset);
        } else {
            total = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM login_history WHERE user_id = ?", Integer.class, userId);
            items = jdbc.query("""
                    SELECT id, user_id, username, login_ip, user_agent, outcome, occurred_at
                    FROM login_history
                    WHERE user_id = ?
                    ORDER BY occurred_at DESC, id DESC
                    LIMIT ? OFFSET ?
                    """, (rs, rowNum) -> item(rs), userId, size, offset);
        }
        return new LoginHistoryPageResponse(items, page, size, total == null ? 0 : total.longValue());
    }

    private static LoginHistoryItemResponse item(java.sql.ResultSet rs) throws java.sql.SQLException {
        java.sql.Timestamp occurredAt = rs.getTimestamp("occurred_at");
        return new LoginHistoryItemResponse(
                rs.getLong("id"),
                rs.getObject("user_id", Long.class),
                rs.getString("username"),
                rs.getString("login_ip"),
                rs.getString("user_agent"),
                rs.getString("outcome"),
                occurredAt.toLocalDateTime());
    }
}
