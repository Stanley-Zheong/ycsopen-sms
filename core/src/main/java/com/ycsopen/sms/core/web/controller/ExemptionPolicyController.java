package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.exemption.ExemptionPolicyService;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Phase 14 exemption policy endpoints. */
@RestController
@RequestMapping("/api/v1/console/exemptions")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ExemptionPolicyController {
    private final ExemptionPolicyService service;

    public ExemptionPolicyController(ExemptionPolicyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:read')")
    public ApiResponse<List<ExemptionPolicyResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:write')")
    public ApiResponse<ExemptionPolicyResponse> create(@RequestBody ExemptionPolicyCreateRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(service.create(new ExemptionPolicyCreateRequest(request == null ? null : request.tenantId(),
                request == null ? null : request.exemptionType(), request == null ? null : request.resourceId(),
                request == null ? null : request.productCode(), request == null ? null : request.scopeExpression(),
                request == null ? null : request.approvalStatus(), request == null ? null : request.validFrom(),
                request == null ? null : request.validUntil(), request == null ? null : request.reason(),
                actor(authentication))));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:write')")
    public ApiResponse<ExemptionPolicyResponse> revoke(@PathVariable long id,
                                                       @RequestBody ExemptionPolicyRevokeRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(service.revoke(id, new ExemptionPolicyRevokeRequest(
                request == null ? null : request.reason(), actor(authentication))));
    }

    @PostMapping("/effective-preview")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:read')")
    public ApiResponse<ExemptionPolicyPreviewResponse> preview(@RequestBody ExemptionPolicyPreviewRequest request,
                                                               Authentication authentication) {
        return ApiResponse.ok(service.preview(new ExemptionPolicyPreviewRequest(request == null ? null : request.tenantId(),
                request == null ? null : request.exemptionType(), request == null ? null : request.resourceId(),
                request == null ? null : request.productCode(), request == null ? null : request.scopeExpression(),
                request == null ? null : request.controlCode(), request == null ? null : request.reason(),
                actor(authentication))));
    }

    @GetMapping("/usage-history")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('exemption:audit')")
    public ApiResponse<List<ExemptionPolicyUsageResponse>> usageHistory() {
        return ApiResponse.ok(service.usageHistory());
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }
}
