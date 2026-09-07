package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.PrivilegedDataService;
import com.ycsopen.sms.core.common.web.TrustedProxyClientIpResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(PrivilegedDataControllerTest.Config.class)
class PrivilegedDataControllerTest {
    @Autowired PrivilegedDataController controller;
    @Autowired PrivilegedDataService service;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void authorizedRevealIsNoStoreAndUsesAuthenticatedActor() {
        authenticate("7", "ROLE_OPERATOR", "privileged:data:reveal:api", "identity:accounts:all");
        when(service.revealPhone(21L, 7L, PrivilegedDataService.RevealPurpose.CUSTOMER_SUPPORT,
                "192.0.2.9", null)).thenReturn(new PrivilegedDataService.RevealedValue(
                "13812345678", Instant.parse("2026-09-07T08:01:00Z"), 91L));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.9");

        var response = controller.revealPhone(21L,
                new PrivilegedDataController.RevealRequest(
                        PrivilegedDataService.RevealPurpose.CUSTOMER_SUPPORT),
                SecurityContextHolder.getContext().getAuthentication(), request);

        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
        assertThat(response.getBody().getData().auditId()).isEqualTo(91L);
        verify(service).revealPhone(21L, 7L, PrivilegedDataService.RevealPurpose.CUSTOMER_SUPPORT,
                "192.0.2.9", null);
    }

    @Test
    void missingExactApiPermissionIsDeniedBeforeDecryption() {
        authenticate("7", "ROLE_OPERATOR", "privileged:data:reveal", "identity:accounts:all");
        assertThatThrownBy(() -> controller.revealPhone(21L,
                new PrivilegedDataController.RevealRequest(
                        PrivilegedDataService.RevealPurpose.CUSTOMER_SUPPORT),
                SecurityContextHolder.getContext().getAuthentication(), new MockHttpServletRequest()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null,
                        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean PrivilegedDataService service() { return mock(PrivilegedDataService.class); }
        @Bean PrivilegedDataController controller(PrivilegedDataService service) {
            return new PrivilegedDataController(service, new TrustedProxyClientIpResolver());
        }
    }
}
