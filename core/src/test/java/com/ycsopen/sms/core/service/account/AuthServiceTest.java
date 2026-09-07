package com.ycsopen.sms.core.service.account;

import com.ycsopen.sms.core.common.security.JwtTokenProvider;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.time.LocalDateTime;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtTokenProvider tokens;
    @Mock IdentitySessionService sessions;
    @Mock LoginAnomalyService anomalies;

    @Test
    void locksAccountAfterFifthWrongPassword() {
        User user = user();
        user.setFailedLoginCount(4);
        when(users.findByUsernameForUpdate("admin")).thenReturn(Optional.of(user));
        when(encoder.matches("bad", "hash")).thenReturn(false);
        when(sessions.recordWithId(1L, "admin", "127.0.0.1", "INVALID_CREDENTIALS", null))
                .thenReturn(501L);

        assertThatThrownBy(() -> service().login(new LoginRequest("admin", "bad"), "127.0.0.1"))
                .hasMessageContaining("用户名或密码错误");

        org.assertj.core.api.Assertions.assertThat(user.getStatus()).isEqualTo(User.UserStatus.LOCKED);
        verify(users).save(user);
        verify(anomalies).repeatedFailure(1L, null, 501L, "127.0.0.1", null);
    }

    @Test
    void deniesDisabledAccountBeforePasswordCheck() {
        User user = user();
        user.setStatus(User.UserStatus.DISABLED);
        when(users.findByUsernameForUpdate("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().login(new LoginRequest("admin", "secret"), "127.0.0.1"))
                .hasMessageContaining("账号已被禁用");
    }

    @Test
    void deniesExpiredPassword() {
        User user = user();
        user.setPasswordExpireTime(LocalDateTime.now().minusMinutes(1));
        when(users.findByUsernameForUpdate("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().login(new LoginRequest("admin", "secret"), "127.0.0.1"))
                .hasMessageContaining("密码已过期");
    }

    @Test
    void deniesExpiredAccountValidity() {
        User user = user();
        user.setValidUntil(LocalDate.now().minusDays(1));
        when(users.findByUsernameForUpdate("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().login(new LoginRequest("admin", "secret"), "127.0.0.1"))
                .hasMessageContaining("账号有效期已结束");
    }

    @Test
    void unusualLoginRecordsClientAndEnqueuesControlledNotification() {
        User user = user();
        user.setLastLoginIp("192.0.2.1");
        when(users.findByUsernameForUpdate("admin")).thenReturn(Optional.of(user));
        when(encoder.matches("secret", "hash")).thenReturn(true);
        when(anomalies.isUnusual("192.0.2.1", "192.0.2.2")).thenReturn(true);
        JwtTokenProvider.IssuedToken issued = new JwtTokenProvider.IssuedToken(
                "token", "session-7", java.time.Instant.now().plusSeconds(60));
        when(tokens.issueToken(1L, "ADMIN", null)).thenReturn(issued);

        service().login(new LoginRequest("admin", "secret"), "192.0.2.2", "Chrome/152");

        verify(sessions).open(user, issued, "192.0.2.2", "Chrome/152", true);
        verify(anomalies).enqueue(1L, null, "session-7", "192.0.2.2", null);
    }

    private AuthService service() {
        return new AuthService(users, encoder, tokens, sessions, anomalies);
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("hash");
        user.setUserType(User.UserType.ADMIN);
        user.setFailedLoginCount(0);
        return user;
    }
}
