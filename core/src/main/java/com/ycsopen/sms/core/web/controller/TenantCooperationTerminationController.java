package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.tenant.TenantCooperationTerminationService;
import com.ycsopen.sms.core.service.tenant.TenantCooperationTerminationService.ApprovalCommand;
import com.ycsopen.sms.core.service.tenant.TenantCooperationTerminationService.ParticipantDefinition;
import com.ycsopen.sms.core.service.tenant.TenantCooperationTerminationService.TerminationCommand;
import com.ycsopen.sms.core.service.tenant.TenantCooperationTerminationService.TerminationDetail;
import com.ycsopen.sms.core.service.tenant.TenantCooperationTerminationService.TerminationRequestView;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/console/tenant-terminations")
public class TenantCooperationTerminationController {
    private final TenantCooperationTerminationService service;

    public TenantCooperationTerminationController(TenantCooperationTerminationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('tenant-termination:read')")
    public ApiResponse<List<TerminationRequestView>> list(@RequestParam(required = false) Long tenantId,
                                                          @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.list(tenantId, status));
    }

    @GetMapping("/participants")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('tenant-termination:read')")
    public ApiResponse<List<ParticipantDefinition>> participants() {
        return ApiResponse.ok(service.participantInventory());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('tenant-termination:read')")
    public ApiResponse<TerminationDetail> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('tenant-termination:write')")
    public ApiResponse<TerminationDetail> request(@RequestBody TerminationRequest request,
                                                  Authentication authentication) {
        TerminationRequest checked = request == null
                ? new TerminationRequest(0, "", "")
                : request;
        return ApiResponse.ok(service.requestTermination(new TerminationCommand(checked.tenantId(),
                checked.reason(), checked.requestEvidence()), actor(authentication)));
    }

    @PostMapping("/{id}/refresh-clearance")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('tenant-termination:write')")
    public ApiResponse<TerminationDetail> refreshClearance(@PathVariable long id, Authentication authentication) {
        return ApiResponse.ok(service.refreshClearance(id, actor(authentication)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant-termination:approve')")
    public ApiResponse<TerminationDetail> approve(@PathVariable long id,
                                                  @RequestBody ApprovalRequest request,
                                                  Authentication authentication) {
        ApprovalRequest checked = request == null ? new ApprovalRequest("") : request;
        return ApiResponse.ok(service.approve(id, new ApprovalCommand(checked.opinion()), actor(authentication)));
    }

    @PostMapping("/{id}/effect")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant-termination:effect')")
    public ApiResponse<TerminationDetail> effect(@PathVariable long id, Authentication authentication) {
        return ApiResponse.ok(service.effect(id, actor(authentication)));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }

    public record TerminationRequest(long tenantId, String reason, String requestEvidence) { }

    public record ApprovalRequest(String opinion) { }
}
