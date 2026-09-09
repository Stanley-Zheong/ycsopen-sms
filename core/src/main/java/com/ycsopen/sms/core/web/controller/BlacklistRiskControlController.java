package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.risk.BlacklistRiskControlService;
import com.ycsopen.sms.core.service.risk.BlacklistRiskControlService.*;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Phase 16: blacklist and third-party risk control console endpoints. */
@RestController
@RequestMapping("/api/v1/console/risk")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class BlacklistRiskControlController {
    private final BlacklistRiskControlService service;

    public BlacklistRiskControlController(BlacklistRiskControlService service) {
        this.service = service;
    }

    @GetMapping("/blacklist")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('blacklist:read')")
    public ApiResponse<List<BlacklistEntryRow>> entries(@RequestParam(required = false) String tenantId,
                                                        @RequestParam(required = false) String listType,
                                                        @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.entries(tenantId, listType, status));
    }

    @PostMapping("/blacklist")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('blacklist:write')")
    public ApiResponse<BlacklistEntryRow> create(@RequestBody BlacklistEntryCreateRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(service.createEntry(request, actor(authentication)));
    }

    @PostMapping("/blacklist/import")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('blacklist:import')")
    public ApiResponse<BlacklistImportResponse> importEntries(@RequestBody BlacklistImportRequest request,
                                                              Authentication authentication) {
        return ApiResponse.ok(service.importEntries(request, actor(authentication)));
    }

    @PostMapping("/blacklist/{id}/disable")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('blacklist:write')")
    public ApiResponse<BlacklistEntryRow> disable(@PathVariable long id) {
        return ApiResponse.ok(service.disableEntry(id));
    }

    @PostMapping("/blacklist/export-request")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('blacklist:export')")
    public ApiResponse<ExportRequestResponse> exportRequest(@RequestParam(required = false) String tenantId,
                                                            @RequestParam(required = false) String listType) {
        return ApiResponse.ok(service.exportRequest(tenantId, listType));
    }

    @GetMapping("/provider")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('risk-provider:read')")
    public ApiResponse<List<RiskProviderConfigRow>> providers() {
        return ApiResponse.ok(service.providerConfigs());
    }

    @PostMapping("/provider")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('risk-provider:write')")
    public ApiResponse<RiskProviderConfigRow> saveProvider(@RequestBody RiskProviderConfigRequest request,
                                                           Authentication authentication) {
        return ApiResponse.ok(service.saveProvider(request, actor(authentication)));
    }

    @PostMapping("/check")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('risk-analysis:check')")
    public ApiResponse<List<RiskDecisionRow>> check(@RequestBody RiskCheckRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(service.evaluate(request, actor(authentication)));
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('risk-analysis:read')")
    public ApiResponse<RiskAnalyticsResponse> analytics() {
        return ApiResponse.ok(service.analytics());
    }

    @PostMapping("/appeals")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('risk-analysis:appeal')")
    public ApiResponse<AppealResponse> appeal(@RequestBody AppealRequest request,
                                              Authentication authentication) {
        return ApiResponse.ok(service.appeal(request, actor(authentication)));
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }
}
