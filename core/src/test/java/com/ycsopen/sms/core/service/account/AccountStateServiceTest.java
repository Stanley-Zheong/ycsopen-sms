package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class AccountStateServiceTest {
    @Mock UserRepository users;
    @Mock JdbcTemplate jdbc;

    @Test
    void manualUnlockResetsFailuresAndRevokesSessions() {
        User user = new User();
        user.setStatus(User.UserStatus.LOCKED);
        user.setFailedLoginCount(5);
        when(users.findById(7L)).thenReturn(Optional.of(user));

        new AccountStateService(users, jdbc).transition(7L, AccountStateService.Action.UNLOCK, 1L);

        assertThat(user.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(user.getFailedLoginCount()).isZero();
        verify(users).save(user);
        verify(jdbc).update("""
                UPDATE user_sessions SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
                WHERE user_id = ? AND revoked_at IS NULL
                """, 7L);
        verify(jdbc).update(contains("INSERT INTO account_change_history"),
                eq(7L), eq(1L), eq("UNLOCK"));
    }
}
