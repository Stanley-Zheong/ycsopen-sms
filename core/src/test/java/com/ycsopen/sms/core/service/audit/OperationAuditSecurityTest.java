package com.ycsopen.sms.core.service.audit;

import com.ycsopen.sms.core.web.controller.OperationAuditController;
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
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(OperationAuditSecurityTest.Config.class)
class OperationAuditSecurityTest {
    @Autowired OperationAuditController controller;
    @Autowired OperationAuditService audits;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void readPermissionIsRequiredAndDataScopeDefaultsToCurrentActor() {
        authenticate("7", "ROLE_OPERATOR", "audit:operations:read");
        controller.search(null, null, null, null, null, 0, 20,
                SecurityContextHolder.getContext().getAuthentication());
        verify(audits).search(org.mockito.ArgumentMatchers.any(), eq(7L));

        authenticate("8", "ROLE_OPERATOR");
        assertThatThrownBy(() -> controller.search(null, null, null, null, null, 0, 20,
                SecurityContextHolder.getContext().getAuthentication()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allScopePermissionRemovesActorRestriction() {
        authenticate("7", "ROLE_OPERATOR", "audit:operations:read", "audit:operations:all");
        controller.search(null, null, null, null, null, 0, 20,
                SecurityContextHolder.getContext().getAuthentication());
        verify(audits).search(org.mockito.ArgumentMatchers.any(), isNull());
    }

    @Test
    void rejectsUnknownResultFilterAsBadRequest() {
        authenticate("7", "ROLE_OPERATOR", "audit:operations:read");

        assertThatThrownBy(() -> controller.search(null, null, "success", null, null, 0, 20,
                SecurityContextHolder.getContext().getAuthentication()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(400);
    }

    private static void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null,
                        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean OperationAuditService audits() { return mock(OperationAuditService.class); }
        @Bean OperationAuditController controller(OperationAuditService audits) {
            return new OperationAuditController(audits);
        }
    }
}
