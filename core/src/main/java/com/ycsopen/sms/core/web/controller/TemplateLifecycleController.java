package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.template.TemplateLifecycleService;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Phase 13 template lifecycle and review endpoints. */
@RestController
public class TemplateLifecycleController {
    private final TemplateLifecycleService service;
    private final UserRepository users;

    public TemplateLifecycleController(TemplateLifecycleService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/tenant/templates")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<List<TemplateResponse>> tenantList(Authentication authentication) {
        return ApiResponse.ok(service.listTenant(tenantIdFor(authentication)));
    }

    @PostMapping("/api/v1/console/tenant/templates")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<TemplateResponse> submit(@RequestBody TemplateApplicationRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(service.submitApplication(tenantIdFor(authentication), request));
    }

    @PostMapping("/api/v1/console/tenant/templates/{templateId}/preview")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<TemplatePreviewResponse> preview(@PathVariable long templateId,
                                                        @RequestBody TemplatePreviewRequest request,
                                                        Authentication authentication) {
        return ApiResponse.ok(service.preview(tenantIdFor(authentication), templateId,
                request == null ? null : request.variables()));
    }

    @PostMapping("/api/v1/console/tenant/templates/{templateId}/resubmit")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<TemplateResponse> resubmit(@PathVariable long templateId,
                                                  @RequestBody TemplateApplicationRequest request,
                                                  Authentication authentication) {
        return ApiResponse.ok(service.resubmit(tenantIdFor(authentication), templateId, request));
    }

    @GetMapping("/api/v1/console/templates/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<TemplateReviewQueueResponse> reviewQueue(@RequestParam(required = false) String keyword,
                                                                @RequestParam(required = false) String tenantId,
                                                                @RequestParam(required = false) String state,
                                                                @RequestParam(required = false) String type) {
        return ApiResponse.ok(service.reviewQueue(keyword, tenantId, state, type));
    }

    @PostMapping("/api/v1/console/templates/{templateId}/decisions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<TemplateResponse> decide(@PathVariable long templateId,
                                                @RequestBody TemplateDecisionRequest request,
                                                Authentication authentication) {
        return ApiResponse.ok(service.decide(templateId, withActor(request, authentication)));
    }

    private static TemplateDecisionRequest withActor(TemplateDecisionRequest request, Authentication authentication) {
        return new TemplateDecisionRequest(request == null ? null : request.decision(),
                request == null ? null : request.opinion(), actor(authentication));
    }

    private long tenantIdFor(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        var user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        if (user.getTenantId() == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文不能为空");
        }
        return user.getTenantId();
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }
}
