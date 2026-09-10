package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService.AggregateFilter;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService.AggregateRow;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService.CorrectionCommand;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService.CorrectionEventRow;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService.MetricRow;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService.RebuildResult;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class StatisticsAggregationController {
    private final StatisticsAggregationService service;

    public StatisticsAggregationController(StatisticsAggregationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/console/statistics/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
    public ApiResponse<List<MetricRow>> metrics() {
        return ApiResponse.ok(service.metrics());
    }

    @GetMapping("/api/v1/console/statistics/aggregates")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
    public ApiResponse<List<AggregateRow>> aggregates(@RequestParam(required = false) String metricCode,
                                                      @RequestParam(required = false) Long tenantId,
                                                      @RequestParam(required = false) Long channelId,
                                                      @RequestParam(required = false) Long signatureId,
                                                      @RequestParam(required = false) Long templateId,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                      LocalDateTime startTime,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                      LocalDateTime endTime) {
        return ApiResponse.ok(service.aggregates(new AggregateFilter(metricCode, tenantId, channelId, signatureId,
                templateId, startTime, endTime)));
    }

    @PostMapping("/api/v1/console/statistics/aggregates/rebuild")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<RebuildResult> rebuild(@RequestBody RebuildRequest request, Authentication authentication) {
        RebuildRequest checked = request == null ? new RebuildRequest(null, null) : request;
        return ApiResponse.ok(service.rebuild(checked.startTime(), checked.endTime(), actor(authentication)));
    }

    @PostMapping("/api/v1/console/statistics/corrections")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<CorrectionEventRow> correction(@RequestBody CorrectionCommand request) {
        return ApiResponse.ok(service.recordCorrection(request));
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }

    public record RebuildRequest(LocalDateTime startTime, LocalDateTime endTime) { }
}
