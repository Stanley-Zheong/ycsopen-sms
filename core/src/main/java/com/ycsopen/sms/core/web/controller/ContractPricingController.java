package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.ContractPricingService;
import com.ycsopen.sms.core.service.billing.ContractPricingService.ContractCommand;
import com.ycsopen.sms.core.service.billing.ContractPricingService.ContractOverview;
import com.ycsopen.sms.core.service.billing.ContractPricingService.ContractRow;
import com.ycsopen.sms.core.service.billing.ContractPricingService.PostpaidUsageCommand;
import com.ycsopen.sms.core.service.billing.ContractPricingService.PostpaidUsageRow;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/console/contracts")
public class ContractPricingController {
    private final ContractPricingService service;
    private final UserRepository users;

    public ContractPricingController(ContractPricingService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/tenants/{tenantId}/overview")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<ContractOverview> overview(@PathVariable long tenantId, Authentication authentication) {
        return ApiResponse.ok(service.overview(scopedTenantId(authentication, tenantId)));
    }

    @PostMapping("/tenants/{tenantId}")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<ContractRow> approve(@PathVariable long tenantId, @RequestBody ContractCommand command,
                                            Authentication authentication) {
        requirePlatform(authentication);
        return ApiResponse.ok(service.approveContract(tenantId, command, actor(authentication)));
    }

    @PostMapping("/tenants/{tenantId}/postpaid-usage")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<PostpaidUsageRow> postpaidUsage(@PathVariable long tenantId,
                                                       @RequestBody PostpaidUsageCommand command,
                                                       Authentication authentication) {
        requirePlatform(authentication);
        return ApiResponse.ok(service.recordPostpaidUsage(tenantId, command, actor(authentication)));
    }

    private long scopedTenantId(Authentication authentication, long requestedTenantId) {
        if (isPlatform(authentication)) return requestedTenantId;
        Long ownTenantId = ownTenantId(authentication);
        if (ownTenantId == null || ownTenantId != requestedTenantId) {
            throw new BusinessException("TENANT_SCOPE_FORBIDDEN", "不能访问其他机构合同");
        }
        return requestedTenantId;
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

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }
}
