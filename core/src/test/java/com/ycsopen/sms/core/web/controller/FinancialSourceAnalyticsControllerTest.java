package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.billing.FinancialSourceAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FinancialSourceAnalyticsControllerTest {

    @Test
    void endpointsDeclareAdminFinanceAuthorization() throws Exception {
        Method summaries = FinancialSourceAnalyticsController.class.getMethod(
                "summaries", java.time.LocalDate.class, java.time.LocalDate.class, Long.class, Long.class,
                org.springframework.security.core.Authentication.class);
        Method drilldown = FinancialSourceAnalyticsController.class.getMethod(
                "drilldown", java.time.LocalDate.class, java.time.LocalDate.class, Long.class, Long.class,
                org.springframework.security.core.Authentication.class);

        assertThat(summaries.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN', 'FINANCE')");
        assertThat(drilldown.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAnyRole('ADMIN', 'FINANCE')");
    }

    @Test
    void endpointsRequireAuthenticatedActorBeforeQueryingFinancialData() {
        var service = mock(FinancialSourceAnalyticsService.class);
        var controller = new FinancialSourceAnalyticsController(service);

        assertThatThrownBy(() -> controller.summaries(null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录操作人不能为空");
        assertThatThrownBy(() -> controller.drilldown(null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录操作人不能为空");

        verify(service, never()).summaries(any());
        verify(service, never()).drilldown(any());
    }
}
