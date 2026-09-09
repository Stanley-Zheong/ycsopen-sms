package com.ycsopen.sms.core.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.CorrelationIdFilter;
import com.ycsopen.sms.core.common.web.TrustedProxyClientIpResolver;
import com.ycsopen.sms.core.web.interceptor.OperationAuditInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationAuditInterceptorTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void recordsOnlyRouteStructureAndSafeParameterNames() throws Exception {
        OperationAuditService audits = mock(OperationAuditService.class);
        when(audits.start(org.mockito.ArgumentMatchers.any())).thenReturn(91L);
        OperationAuditInterceptor interceptor = new OperationAuditInterceptor(
                audits, new ObjectMapper(), new TrustedProxyClientIpResolver());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/v1/console/platform-roles/12/permissions");
        request.setRemoteAddr("192.0.2.9");
        request.setParameter("page", "2");
        request.setParameter("password", "never-store-this");
        request.addHeader("Authorization", "Bearer never-store-this-token");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/console/platform-roles/{roleId}/permissions");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("roleId", "12"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("7", null, List.of()));
        MDC.put("traceId", "0123456789abcdef0123456789abcdef");

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        ArgumentCaptor<OperationAuditService.AuditCommand> command =
                ArgumentCaptor.forClass(OperationAuditService.AuditCommand.class);
        verify(audits).start(command.capture());
        verify(audits).complete(org.mockito.ArgumentMatchers.eq(91L),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.eq(200), anyLong());
        assertThat(command.getValue().actorUserId()).isEqualTo(7L);
        assertThat(command.getValue().route())
                .isEqualTo("/api/v1/console/platform-roles/{roleId}/permissions");
        assertThat(command.getValue().resourceId()).isEqualTo("roleId=12");
        assertThat(command.getValue().sanitizedRequest()).contains("page", "other", "[redacted]");
        assertThat(command.getValue().sanitizedRequest())
                .doesNotContain("password", "never-store-this", "never-store-this-token", "/12/");
        assertThat(command.getValue().traceId()).isEqualTo("0123456789abcdef0123456789abcdef");
    }

    @Test
    void auditStartFailureStopsTheHandlerBeforeBusinessCodeRuns() {
        OperationAuditService audits = mock(OperationAuditService.class);
        when(audits.start(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("audit unavailable"));
        OperationAuditInterceptor interceptor = new OperationAuditInterceptor(
                audits, new ObjectMapper(), new TrustedProxyClientIpResolver());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/console/example");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/console/example");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("7", null, List.of()));

        assertThatThrownBy(() -> interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audit unavailable");
    }
}
