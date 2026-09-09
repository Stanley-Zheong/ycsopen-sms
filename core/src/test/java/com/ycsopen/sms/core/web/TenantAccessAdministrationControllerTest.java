package com.ycsopen.sms.core.web;

import com.ycsopen.sms.core.service.tenant.TenantAccessAdministrationService;
import com.ycsopen.sms.core.web.controller.TenantAccessAdministrationController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(TenantAccessAdministrationControllerTest.Config.class)
class TenantAccessAdministrationControllerTest {
    @Autowired TenantAccessAdministrationController controller;
    @Autowired TenantAccessAdministrationService service;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void tenantUserCannotManageSubaccounts() {
        authenticate("11", "ROLE_TENANT_USER");
        assertThatThrownBy(() -> controller.list(SecurityContextHolder.getContext().getAuthentication()))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    private static void authenticate(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(id, null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(role))));
    }
    @Configuration @EnableMethodSecurity static class Config {
        @Bean TenantAccessAdministrationService service() { return mock(TenantAccessAdministrationService.class); }
        @Bean TenantAccessAdministrationController controller(TenantAccessAdministrationService s) { return new TenantAccessAdministrationController(s); }
    }
}
