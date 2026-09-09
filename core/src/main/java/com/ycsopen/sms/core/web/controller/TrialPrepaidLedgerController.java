package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.TrialPrepaidLedgerService;
import com.ycsopen.sms.core.service.billing.TrialPrepaidLedgerService.BalanceAuditEntry;
import com.ycsopen.sms.core.service.billing.TrialPrepaidLedgerService.ConsumptionEntry;
import com.ycsopen.sms.core.service.billing.TrialPrepaidLedgerService.ConversionRequest;
import com.ycsopen.sms.core.service.billing.TrialPrepaidLedgerService.PrepaidResult;
import com.ycsopen.sms.core.service.billing.TrialPrepaidLedgerService.TrialOverview;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/console/trial-prepaid")
public class TrialPrepaidLedgerController {
    private final TrialPrepaidLedgerService service;
    private final UserRepository users;

    public TrialPrepaidLedgerController(TrialPrepaidLedgerService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/tenants/{tenantId}/overview")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<TrialOverview> overview(@PathVariable long tenantId, Authentication authentication) {
        return ApiResponse.ok(service.overview(scopedTenantId(authentication, tenantId)));
    }

    @PostMapping("/tenants/{tenantId}/trial")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<TrialOverview> activateTrial(@PathVariable long tenantId,
                                                    @RequestBody TrialActivationRequest request,
                                                    Authentication authentication) {
        requirePlatform(authentication);
        return ApiResponse.ok(service.activateTrial(tenantId, request.quota(), request.startAt(), request.endAt(), actor(authentication)));
    }

    @PostMapping("/tenants/{tenantId}/trial/consume")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<TrialOverview> consumeTrial(@PathVariable long tenantId,
                                                   @RequestBody TrialConsumeRequest request,
                                                   Authentication authentication) {
        return ApiResponse.ok(service.consumeTrial(scopedTenantId(authentication, tenantId),
                request.messageRef(), request.businessType(), actor(authentication)));
    }

    @PostMapping("/tenants/{tenantId}/conversion-request")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<ConversionRequest> conversion(@PathVariable long tenantId, Authentication authentication) {
        return ApiResponse.ok(service.requestConversion(scopedTenantId(authentication, tenantId), actor(authentication)));
    }

    @PostMapping("/tenants/{tenantId}/prepaid/reserve")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<PrepaidResult> reserve(@PathVariable long tenantId,
                                              @RequestBody PrepaidReserveRequest request,
                                              Authentication authentication) {
        requirePlatform(authentication);
        return ApiResponse.ok(service.reservePrepaid(tenantId, request.businessDocId(), request.businessType(),
                request.channelCode(), request.priceMil(), request.quantity(), actor(authentication)));
    }

    @PostMapping("/prepaid/{businessDocId}/confirm")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<PrepaidResult> confirm(@PathVariable String businessDocId,
                                              @RequestParam String transactionRef,
                                              Authentication authentication) {
        requirePlatform(authentication);
        return ApiResponse.ok(service.confirmPrepaid(businessDocId, transactionRef, actor(authentication)));
    }

    @PostMapping("/prepaid/{businessDocId}/reverse")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<PrepaidResult> reverse(@PathVariable String businessDocId,
                                              Authentication authentication) {
        requirePlatform(authentication);
        return ApiResponse.ok(service.reversePrepaid(businessDocId, actor(authentication)));
    }

    @GetMapping("/consumption")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<ConsumptionEntry>> consumption(@RequestParam(required = false) Long tenantId,
                                                           @RequestParam(required = false) String businessType,
                                                           Authentication authentication) {
        return ApiResponse.ok(service.consumption(scopedQueryTenantId(authentication, tenantId), businessType));
    }

    @GetMapping("/balance-audits")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<BalanceAuditEntry>> audits(@RequestParam(required = false) Long tenantId,
                                                       Authentication authentication) {
        requirePlatform(authentication);
        return ApiResponse.ok(service.audits(tenantId));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }

    private long scopedTenantId(Authentication authentication, long requestedTenantId) {
        if (isPlatform(authentication)) return requestedTenantId;
        Long ownTenantId = ownTenantId(authentication);
        if (ownTenantId == null || ownTenantId != requestedTenantId) {
            throw new BusinessException("TENANT_SCOPE_FORBIDDEN", "不能访问其他机构账本");
        }
        return requestedTenantId;
    }

    private Long scopedQueryTenantId(Authentication authentication, Long requestedTenantId) {
        if (isPlatform(authentication)) return requestedTenantId;
        Long ownTenantId = ownTenantId(authentication);
        if (ownTenantId == null) throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文不能为空");
        if (requestedTenantId != null && !requestedTenantId.equals(ownTenantId)) {
            throw new BusinessException("TENANT_SCOPE_FORBIDDEN", "不能访问其他机构账本");
        }
        return ownTenantId;
    }

    private void requirePlatform(Authentication authentication) {
        if (!isPlatform(authentication)) throw new BusinessException("PLATFORM_ROLE_REQUIRED", "仅平台账号可执行此操作");
    }

    private boolean isPlatform(Authentication authentication) {
        return hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_OPERATOR")
                || hasRole(authentication, "ROLE_FINANCE");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private Long ownTenantId(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        return user.getTenantId();
    }

    public record TrialActivationRequest(Integer quota, LocalDateTime startAt, LocalDateTime endAt) { }
    public record TrialConsumeRequest(String messageRef, String businessType) { }
    public record PrepaidReserveRequest(String businessDocId, String businessType, String channelCode,
                                        long priceMil, int quantity) { }
}
