package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.complaint.ComplaintCaseService;
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
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ComplaintCaseAuthorizationTest.TestConfig.class)
class ComplaintCaseAuthorizationTest {
    @Autowired ComplaintCaseController controller;
    @Autowired ComplaintCaseService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void financeReadsCaseTimelineButCannotReadMutationChoicesOrMutate() {
        when(service.caseDetail(1L)).thenReturn(new ComplaintCaseService.CaseDetail(null, List.of(), List.of()));
        authenticate("finance", "ROLE_FINANCE");

        controller.cases();
        controller.detail(1L);
        verify(service).cases();
        verify(service).caseDetail(1L);
        assertThatThrownBy(controller::referenceOptions).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.accept(1L,
                new ComplaintCaseService.StateCommand(null, "受理", null, null, null),
                SecurityContextHolder.getContext().getAuthentication()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void operatorCanReadReferenceChoices() {
        authenticate("operator", "ROLE_OPERATOR");
        controller.referenceOptions();
        verify(service).referenceOptions();
    }

    private static void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null,
                        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {
        @Bean ComplaintCaseService service() {
            return mock(ComplaintCaseService.class);
        }

        @Bean ComplaintCaseController controller(ComplaintCaseService service) {
            return new ComplaintCaseController(service);
        }
    }
}
