package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.GlobalExceptionHandler;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import com.ycsopen.sms.core.service.audit.SecurityEventService;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(SecurityEventControllerTest.Config.class)
class SecurityEventControllerTest {
    @Autowired SecurityEventController controller;
    @Autowired SecurityEventService events;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void filteredSearchRequiresPermissionAndKeepsActorScope() {
        authenticate("7", "ROLE_OPERATOR", "audit:security-events:read");
        controller.search("UNUSUAL_LOGIN", "operator", "DETECTED", null, null, 0, 20,
                SecurityContextHolder.getContext().getAuthentication());
        verify(events).search(org.mockito.ArgumentMatchers.argThat(search ->
                search.eventType().equals("UNUSUAL_LOGIN") && search.actor().equals("operator")
                        && search.result().equals("DETECTED")), eq(7L));

        authenticate("8", "ROLE_OPERATOR");
        assertThatThrownBy(() -> controller.search(null, null, null, null, null, 0, 20,
                SecurityContextHolder.getContext().getAuthentication()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsUnknownEventAndMalformedResultFiltersAsRealHttp400() throws Exception {
        var authentication = UsernamePasswordAuthenticationToken.authenticated("7", null,
                java.util.List.of(new SimpleGrantedAuthority("audit:security-events:read")));
        var mvc = MockMvcBuilders.standaloneSetup(new SecurityEventController(events))
                .setControllerAdvice(new GlobalExceptionHandler(mock(SecurityEventLogger.class)))
                .build();

        mvc.perform(get("/api/v1/console/security-events")
                        .param("eventType", "PASSWORD_RESET")
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/v1/console/security-events")
                        .param("result", "detected")
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/v1/console/security-events")
                        .param("from", "not-an-instant")
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/v1/console/security-events")
                        .param("page", "not-a-number")
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private static void authenticate(String subject, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(subject, null,
                        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean SecurityEventService events() { return mock(SecurityEventService.class); }
        @Bean SecurityEventController controller(SecurityEventService events) {
            return new SecurityEventController(events);
        }
    }
}
