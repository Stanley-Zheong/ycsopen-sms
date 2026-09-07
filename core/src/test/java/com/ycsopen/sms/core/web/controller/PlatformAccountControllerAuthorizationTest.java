package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.AccountStateService;
import com.ycsopen.sms.core.service.account.PlatformAccountService;
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

@SpringJUnitConfig(PlatformAccountControllerAuthorizationTest.TestConfig.class)
class PlatformAccountControllerAuthorizationTest {

    @Autowired PlatformAccountController controller;
    @Autowired PlatformAccountService accounts;
    @Autowired AccountStateService states;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonAdminCannotListPlatformAccounts() {
        authenticate("9", "ROLE_OPERATOR");

        assertThatThrownBy(controller::list)
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void currentFourGranularityPermissionsAllowAccountDataAccess() {
        authenticate("9", "ROLE_OPERATOR", "identity:menu", "identity:accounts:read", "identity:accounts:all");

        controller.list();

        verify(accounts).list();
    }

    @Test
    void stateEndpointUsesAuthenticatedAdminAsActor() {
        authenticate("7", "ROLE_ADMIN");

        controller.disable(21L, SecurityContextHolder.getContext().getAuthentication());

        verify(states).transition(21L, AccountStateService.Action.DISABLE, 7L);
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
        @Bean PlatformAccountService accounts() {
            return mock(PlatformAccountService.class);
        }

        @Bean AccountStateService states() {
            return mock(AccountStateService.class);
        }

        @Bean PlatformAccountController controller(PlatformAccountService accounts,
                                                   AccountStateService states) {
            return new PlatformAccountController(accounts, states);
        }
    }
}
