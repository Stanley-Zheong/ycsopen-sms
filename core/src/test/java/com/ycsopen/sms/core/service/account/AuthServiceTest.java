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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtTokenProvider tokens;

    @Test
    void locksAccountAfterFifthWrongPassword() {
        User user = user();
        user.setFailedLoginCount(4);
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        when(encoder.matches("bad", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service().login(new LoginRequest("admin", "bad"), "127.0.0.1"))
                .hasMessageContaining("用户名或密码错误");

        org.assertj.core.api.Assertions.assertThat(user.getStatus()).isEqualTo(User.UserStatus.LOCKED);
        verify(users).save(user);
    }

    @Test
    void deniesDisabledAccountBeforePasswordCheck() {
        User user = user();
        user.setStatus(User.UserStatus.DISABLED);
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service().login(new LoginRequest("admin", "secret"), "127.0.0.1"))
                .hasMessageContaining("账号已被禁用");
    }

    private AuthService service() {
        return new AuthService(users, encoder, tokens);
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
