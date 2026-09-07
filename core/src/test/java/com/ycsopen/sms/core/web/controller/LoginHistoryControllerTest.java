package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.LoginHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LoginHistoryControllerTest {
    private final LoginHistoryService histories = mock(LoginHistoryService.class);
    private final LoginHistoryController controller = new LoginHistoryController(histories);

    @Test
    void ordinaryPlatformUserGetsOwnHistoryByDefault() {
        controller.query(null, false, 0, 20, authentication("7", "ROLE_OPERATOR"));

        verify(histories).query(7L, 0, 20);
    }

    @Test
    void ordinaryPlatformUserCannotReadAnotherUsersHistory() {
        assertThatThrownBy(() -> controller.query(8L, false, 0, 20,
                authentication("7", "ROLE_OPERATOR", "identity:history:read")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void dataScopeAloneCannotReadAllHistory() {
        assertThatThrownBy(() -> controller.query(null, true, 0, 20,
                authentication("7", "ROLE_OPERATOR", "identity:history:all")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void apiPermissionAloneCannotReadAllHistory() {
        assertThatThrownBy(() -> controller.query(null, true, 0, 20,
                authentication("7", "ROLE_OPERATOR", "identity:history:read")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void apiAndDataPermissionsTogetherAllowAllHistory() {
        controller.query(null, true, 0, 20,
                authentication("7", "ROLE_OPERATOR", "identity:history:read", "identity:history:all"));

        verify(histories).query(null, 0, 20);
    }

    private static UsernamePasswordAuthenticationToken authentication(String subject, String... authorities) {
        return UsernamePasswordAuthenticationToken.authenticated(subject, null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
