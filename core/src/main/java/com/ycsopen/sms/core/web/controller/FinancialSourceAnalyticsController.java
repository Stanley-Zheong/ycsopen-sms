package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.billing.FinancialSourceAnalyticsService;
import com.ycsopen.sms.core.service.billing.FinancialSourceAnalyticsService.FinancialAnalyticsFilter;
import com.ycsopen.sms.core.service.billing.FinancialSourceAnalyticsService.FinancialSourceRow;
import com.ycsopen.sms.core.service.billing.FinancialSourceAnalyticsService.FinancialSummaryRow;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/console/financial/analytics")
public class FinancialSourceAnalyticsController {
    private final FinancialSourceAnalyticsService service;

    public FinancialSourceAnalyticsController(FinancialSourceAnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<List<FinancialSummaryRow>> summaries(@RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate startDate,
                                                            @RequestParam(required = false)
                                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                            LocalDate endDate,
                                                            @RequestParam(required = false) Long tenantId,
                                                            @RequestParam(required = false) Long channelId,
                                                            Authentication authentication) {
        requireActor(authentication);
        return ApiResponse.ok(service.summaries(new FinancialAnalyticsFilter(startDate, endDate, tenantId, channelId)));
    }

    @GetMapping("/drilldown")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<List<FinancialSourceRow>> drilldown(@RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                           LocalDate startDate,
                                                           @RequestParam(required = false)
                                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                           LocalDate endDate,
                                                           @RequestParam(required = false) Long tenantId,
                                                           @RequestParam(required = false) Long channelId,
                                                           Authentication authentication) {
        requireActor(authentication);
        return ApiResponse.ok(service.drilldown(new FinancialAnalyticsFilter(startDate, endDate, tenantId, channelId)));
    }

    private static void requireActor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
    }
}
