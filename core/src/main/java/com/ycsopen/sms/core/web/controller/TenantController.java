package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.service.tenant.TenantService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** F-2.1/F-2.2/F-2.8 机构注册、审核、试用激活（平台管理后台"机构管理"调用）。 */
@RestController
@RequestMapping("/api/v1/console/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/register")
    public ApiResponse<Tenant> register(@Valid @RequestBody TenantRegistrationRequest request) {
        return ApiResponse.ok(tenantService.submitRegistration(request));
    }

    @PostMapping("/{tenantId}/approve-and-activate-trial")
    public ApiResponse<Tenant> approveAndActivateTrial(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "500") int trialQuota,
            @RequestParam(defaultValue = "14") int trialDays,
            @RequestParam String approvedBy) {
        return ApiResponse.ok(tenantService.approveAndActivateTrial(tenantId, trialQuota, trialDays, approvedBy));
    }

    @PostMapping("/{tenantId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long tenantId, @RequestParam String reason) {
        tenantService.rejectRegistration(tenantId, reason);
        return ApiResponse.ok(null);
    }
}
