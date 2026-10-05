package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.tenant.TenantApiKeyService;
import com.ycsopen.sms.core.web.dto.TenantApiKeyCreateRequest;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringJUnitConfig(TenantApiKeyControllerSecurityTest.Config.class)
class TenantApiKeyControllerSecurityTest {
    @Autowired TenantApiKeyController controller;
    @Autowired TenantApiKeyService service;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        reset(service);
    }

    @Test
    void tenantAdminCanManageAndReadAudit() {
        authenticate("11", "ROLE_TENANT_ADMIN");

        controller.list(authentication());
        controller.audits(authentication());
        TenantApiKeyCreateRequest request = request();
        controller.create(request, authentication());
        controller.revoke(41L, authentication());

        verify(service).list(11L);
        verify(service).auditTrail(11L);
        verify(service).create(11L, request);
        verify(service).revoke(11L, 41L);
    }

    @Test
    void tenantDeveloperCanReadCredentials() {
        authenticate("12", "ROLE_TENANT_DEV");

        controller.list(authentication());

        verify(service).list(12L);
    }

    @Test
    void tenantUserCannotReadCredentialsOrAudit() {
        authenticate("31", "ROLE_TENANT_USER");

        assertThatThrownBy(() -> controller.list(authentication()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.audits(authentication()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    private static org.springframework.security.core.Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static void authenticate(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(id, null,
                        List.of(new SimpleGrantedAuthority(role))));
    }

    private static TenantApiKeyCreateRequest request() {
        return new TenantApiKeyCreateRequest("integration", "synthetic",
                LocalDateTime.of(2099, 1, 1, 0, 0), null, 10, 100, 1_000, 10_000);
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean TenantApiKeyService service() { return mock(TenantApiKeyService.class); }
        @Bean TenantApiKeyController controller(TenantApiKeyService service) {
            return new TenantApiKeyController(service);
        }
    }
}
