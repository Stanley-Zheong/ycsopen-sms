package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.AlertEventRow;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.ExportRequestResponse;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.KeywordCommand;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.KeywordRow;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.SearchFilter;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.StatisticsFilter;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.StatisticsRow;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService.UnsubscribeRecordRow;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class UnsubscribeComplianceController {
    private final UnsubscribeComplianceService service;
    private final UserRepository users;

    public UnsubscribeComplianceController(UnsubscribeComplianceService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/unsubscribe/keywords")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<KeywordRow>> adminKeywords(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(service.keywords(tenantId));
    }

    @PutMapping("/api/v1/console/unsubscribe/keywords")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<KeywordRow> saveAdminKeyword(@RequestBody KeywordCommand request,
                                                    Authentication authentication) {
        return ApiResponse.ok(service.saveKeyword(null, request, actor(authentication)));
    }

    @GetMapping("/api/v1/console/unsubscribes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<UnsubscribeRecordRow>> adminSearch(@RequestParam(required = false) Long tenantId,
                                                               @RequestParam(required = false) String mobile,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String outcome,
                                                               @RequestParam(required = false) Long signatureId,
                                                               @RequestParam(required = false) String productCode,
                                                               @RequestParam(required = false) String notificationState,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                               LocalDateTime startTime,
                                                               @RequestParam(required = false)
                                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                               LocalDateTime endTime) {
        return ApiResponse.ok(service.adminSearch(new SearchFilter(tenantId, mobile, keyword, outcome, signatureId,
                productCode, notificationState, startTime, endTime)));
    }

    @GetMapping("/api/v1/console/unsubscribe/statistics")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<StatisticsRow>> statistics(@RequestParam(required = false) Long tenantId,
                                                       @RequestParam(required = false) Long signatureId,
                                                       @RequestParam(required = false) String productCode,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                       LocalDateTime startTime,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                       LocalDateTime endTime) {
        return ApiResponse.ok(service.statistics(new StatisticsFilter(tenantId, signatureId, productCode,
                startTime, endTime)));
    }

    @PostMapping("/api/v1/console/unsubscribe/alerts/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<AlertEventRow>> evaluateAlerts(@RequestBody AlertEvaluateRequest request) {
        AlertEvaluateRequest checked = request == null ? new AlertEvaluateRequest(null, null, null, null, null, 0.03)
                : request;
        return ApiResponse.ok(service.evaluateAlerts(new StatisticsFilter(checked.tenantId(), checked.signatureId(),
                checked.productCode(), checked.startTime(), checked.endTime()), checked.thresholdRate()));
    }

    @GetMapping("/api/v1/console/tenant/unsubscribe/keywords")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<List<KeywordRow>> tenantKeywords(Authentication authentication) {
        return ApiResponse.ok(service.keywords(tenantIdFor(authentication)));
    }

    @PutMapping("/api/v1/console/tenant/unsubscribe/keywords")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<KeywordRow> saveTenantKeyword(@RequestBody KeywordCommand request,
                                                     Authentication authentication) {
        return ApiResponse.ok(service.saveKeyword(tenantIdFor(authentication), request, actor(authentication)));
    }

    @GetMapping("/api/v1/console/tenant/unsubscribes")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<List<UnsubscribeRecordRow>> tenantSearch(@RequestParam(required = false) String mobile,
                                                                @RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String outcome,
                                                                @RequestParam(required = false) Long signatureId,
                                                                @RequestParam(required = false) String productCode,
                                                                @RequestParam(required = false) String notificationState,
                                                                @RequestParam(required = false)
                                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                LocalDateTime startTime,
                                                                @RequestParam(required = false)
                                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                                LocalDateTime endTime,
                                                                Authentication authentication) {
        return ApiResponse.ok(service.tenantSearch(tenantIdFor(authentication),
                new SearchFilter(null, mobile, keyword, outcome, signatureId, productCode, notificationState,
                        startTime, endTime)));
    }

    @PostMapping("/api/v1/console/tenant/unsubscribes/export-request")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<ExportRequestResponse> requestTenantExport(@RequestBody TenantExportRequest request,
                                                                  Authentication authentication) {
        TenantExportRequest checked = request == null
                ? new TenantExportRequest(null, null, null, null, null, null, null, null)
                : request;
        return ApiResponse.ok(service.requestTenantExport(tenantIdFor(authentication),
                new SearchFilter(null, checked.mobile(), checked.keyword(), checked.outcome(), checked.signatureId(),
                        checked.productCode(), checked.notificationState(), checked.startTime(), checked.endTime()),
                actor(authentication)));
    }

    private long tenantIdFor(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        if (user.getTenantId() == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文不能为空");
        }
        return user.getTenantId();
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }

    public record AlertEvaluateRequest(Long tenantId, Long signatureId, String productCode,
                                       LocalDateTime startTime, LocalDateTime endTime, double thresholdRate) { }

    public record TenantExportRequest(String mobile, String keyword, String outcome, Long signatureId,
                                      String productCode, String notificationState,
                                      LocalDateTime startTime, LocalDateTime endTime) { }
}
