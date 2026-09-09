package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrator-only state transitions with immediate session revocation. */
@Service
public class AccountStateService {
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public AccountStateService(UserRepository users, JdbcTemplate jdbc) {
        this.users = users;
        this.jdbc = jdbc;
    }

    @Transactional
    public void transition(long userId, Action action, long administratorId) {
        if (administratorId == userId) {
            throw new BusinessException("SELF_STATE_CHANGE_FORBIDDEN", "管理员不能变更自己的账号状态");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "账号不存在"));
        switch (action) {
            case DISABLE -> requireAndSet(user, User.UserStatus.ACTIVE, User.UserStatus.DISABLED);
            case ENABLE -> requireAndSet(user, User.UserStatus.DISABLED, User.UserStatus.ACTIVE);
            case UNLOCK -> {
                requireAndSet(user, User.UserStatus.LOCKED, User.UserStatus.ACTIVE);
                user.setFailedLoginCount(0);
            }
        }
        users.save(user);
        jdbc.update("""
                UPDATE user_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
                WHERE user_id = ? AND revoked_at IS NULL
                """, userId);
        jdbc.update("""
                INSERT INTO account_change_history(user_id, actor_user_id, action, occurred_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """, userId, administratorId, action.name());
    }

    private static void requireAndSet(User user, User.UserStatus expected, User.UserStatus target) {
        if (user.getStatus() != expected) {
            throw new BusinessException("INVALID_ACCOUNT_TRANSITION", "账号状态不允许此操作");
        }
        user.setStatus(target);
    }

    public enum Action { DISABLE, ENABLE, UNLOCK }
}
