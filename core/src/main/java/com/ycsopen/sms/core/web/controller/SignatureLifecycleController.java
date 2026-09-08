package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.signature.SignatureLifecycleService;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Phase 12 signature lifecycle and channel filing endpoints. */
@RestController
public class SignatureLifecycleController {
    private final SignatureLifecycleService service;
    private final UserRepository users;

    public SignatureLifecycleController(SignatureLifecycleService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/tenant/signatures")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<List<SignatureResponse>> tenantList(Authentication authentication) {
        return ApiResponse.ok(service.listTenant(tenantIdFor(authentication)));
    }

    @PostMapping("/api/v1/console/tenant/signatures")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<SignatureResponse> submit(@RequestBody SignatureApplicationRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(service.submitApplication(tenantIdFor(authentication), request));
    }

    @GetMapping("/api/v1/console/tenant/signatures/{signatureId}/usable-channels")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<List<SignatureFilingResponse>> usableChannels(@PathVariable long signatureId,
                                                                     Authentication authentication) {
        SignatureResponse signature = service.get(signatureId);
        if (signature.tenantId() != tenantIdFor(authentication)) {
            throw new BusinessException("SIGNATURE_FORBIDDEN", "不能访问其他机构签名");
        }
        return ApiResponse.ok(service.usableChannels(signatureId));
    }

    @GetMapping("/api/v1/console/signatures/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<SignatureReviewQueueResponse> reviewQueue(@RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String tenantId,
                                                                 @RequestParam(required = false) String signType,
                                                                 @RequestParam(required = false) String riskLevel,
                                                                 @RequestParam(required = false) String auditStatus) {
        return ApiResponse.ok(service.reviewQueue(keyword, tenantId, signType, riskLevel, auditStatus));
    }

    @PostMapping("/api/v1/console/signatures/{signatureId}/decisions")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<SignatureResponse> decide(@PathVariable long signatureId,
                                                 @RequestBody SignatureDecisionRequest request,
                                                 Authentication authentication) {
        return ApiResponse.ok(service.decide(signatureId, withActor(request, authentication)));
    }

    @GetMapping("/api/v1/console/signatures/{signatureId}/filings")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<SignatureFilingResponse>> filingMatrix(@PathVariable long signatureId) {
        return ApiResponse.ok(service.filingMatrix(signatureId));
    }

    @PostMapping("/api/v1/console/signatures/{signatureId}/filings/{channelId}/request")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<SignatureFilingResponse> requestFiling(@PathVariable long signatureId,
                                                              @PathVariable long channelId,
                                                              Authentication authentication) {
        return ApiResponse.ok(service.requestFiling(signatureId, channelId, actor(authentication)));
    }

    @PostMapping("/api/v1/console/signatures/{signatureId}/filings/{channelId}/result")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<SignatureFilingResponse> recordFilingResult(@PathVariable long signatureId,
                                                                   @PathVariable long channelId,
                                                                   @RequestBody SignatureFilingResultRequest request,
                                                                   Authentication authentication) {
        return ApiResponse.ok(service.recordFilingResult(signatureId, channelId, withActor(request, authentication)));
    }

    private static SignatureDecisionRequest withActor(SignatureDecisionRequest request, Authentication authentication) {
        return new SignatureDecisionRequest(request == null ? null : request.decision(),
                request == null ? null : request.opinion(), actor(authentication));
    }

    private static SignatureFilingResultRequest withActor(SignatureFilingResultRequest request, Authentication authentication) {
        return new SignatureFilingResultRequest(request == null ? null : request.status(),
                request == null ? null : request.resultMessage(), actor(authentication));
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
