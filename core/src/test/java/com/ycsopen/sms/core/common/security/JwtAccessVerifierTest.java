package com.ycsopen.sms.core.common.security;

import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.account.RoleAdministrationService;
import com.ycsopen.sms.core.service.account.IdentitySessionService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAccessVerifierTest {
    @Mock UserRepository users;
    @Mock RoleAdministrationService roles;
    @Mock IdentitySessionService sessions;
    @Mock Claims claims;

    @Test
    void resolvesCurrentDatabasePermissions() {
        User user = activeAdmin();
        when(claims.getSubject()).thenReturn("7");
        when(claims.get("userType", String.class)).thenReturn("ADMIN");
        when(claims.getId()).thenReturn("session-1");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(sessions.isActive("session-1", 7L)).thenReturn(true);
        when(roles.currentPermissions(7L)).thenReturn(Set.of("account:read", "role:write"));

        var access = new JwtAccessVerifier(users, roles, sessions).verify(claims);

        assertThat(access.authorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "account:read", "role:write");
    }

    @Test
    void rejectsDisabledOrStaleRoleToken() {
        User user = activeAdmin();
        user.setStatus(User.UserStatus.DISABLED);
        when(claims.getSubject()).thenReturn("7");
        when(users.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> new JwtAccessVerifier(users, roles, sessions).verify(claims))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test
    void rejectsAccountPastItsValidityEvenWithAnOtherwiseValidToken() {
        User user = activeAdmin();
        user.setValidUntil(LocalDate.now().minusDays(1));
        when(claims.getSubject()).thenReturn("7");
        when(users.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> new JwtAccessVerifier(users, roles, sessions).verify(claims))
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    private User activeAdmin() {
        User user = new User();
        user.setId(7L);
        user.setUserType(User.UserType.ADMIN);
        user.setStatus(User.UserStatus.ACTIVE);
        return user;
    }
}
