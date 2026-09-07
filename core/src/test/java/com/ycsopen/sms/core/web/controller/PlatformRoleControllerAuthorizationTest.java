package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.RoleAdministrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(PlatformRoleControllerAuthorizationTest.TestConfig.class)
class PlatformRoleControllerAuthorizationTest {
    @Autowired PlatformRoleController controller;
    @Autowired RoleAdministrationService roles;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleApiPermissionWithoutMenuPermissionIsDenied() {
        authenticate("9", "ROLE_OPERATOR", "identity:roles:read");
        assertThatThrownBy(controller::roles).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void currentMenuAndApiPermissionsAllowRoleRead() {
        authenticate("9", "ROLE_OPERATOR", "identity:menu", "identity:roles:read");
        controller.roles();
        verify(roles).listPlatformRoles();
    }

    @Test
    void currentApiPermissionAllowsPermissionMutationAndAttributesActor() {
        authenticate("9", "ROLE_OPERATOR", "identity:roles:grant:api");
        controller.permissions(7L, new PlatformRoleController.PermissionAssignmentRequest(List.of(1L, 2L)),
                SecurityContextHolder.getContext().getAuthentication());
        verify(roles).replacePermissions(7L, List.of(1L, 2L), 9L);
    }

    private static void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        subject, null, java.util.Arrays.stream(authorities)
                                .map(SimpleGrantedAuthority::new)
                                .toList()));
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean RoleAdministrationService roles() {
            return mock(RoleAdministrationService.class);
        }

        @Bean PlatformRoleController controller(RoleAdministrationService roles) {
            return new PlatformRoleController(roles);
        }
    }
}
