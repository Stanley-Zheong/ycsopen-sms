package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.security.JwtTokenProvider;
import com.ycsopen.sms.core.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentitySessionServiceTest {
    @Mock JdbcTemplate jdbc;

    @Test
    void opensAndValidatesDurableSession() {
        User user = new User();
        user.setId(7L);
        user.setUsername("admin");
        user.setUserType(User.UserType.ADMIN);
        var token = new JwtTokenProvider.IssuedToken("jwt", "session-1", Instant.parse("2026-09-08T00:00:00Z"));
        var service = new IdentitySessionService(jdbc);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("session-1"), eq(7L))).thenReturn(1);

        service.open(user, token, "127.0.0.1");

        assertThat(service.isActive("session-1", 7L)).isTrue();
        verify(jdbc).update(anyString(), eq("session-1"), eq(7L), eq("ADMIN"), isNull(),
                eq("127.0.0.1"), isNull(), org.mockito.ArgumentMatchers.any(java.sql.Timestamp.class), eq(false));
    }

    @Test
    void revokesExactSession() {
        new IdentitySessionService(jdbc).revoke("session-1", 7L);
        verify(jdbc).update(anyString(), eq("session-1"), eq(7L));
    }
}
