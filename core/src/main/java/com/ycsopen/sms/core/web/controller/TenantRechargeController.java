package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.TenantRechargeService;
import com.ycsopen.sms.core.service.billing.TenantRechargeService.RechargeCommand;
import com.ycsopen.sms.core.service.billing.TenantRechargeService.RechargeRecord;
import com.ycsopen.sms.core.service.billing.TenantRechargeService.ReviewCommand;
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
@RequestMapping("/api/v1/console/recharges")
public class TenantRechargeController {
    private final TenantRechargeService service;
    private final UserRepository users;

    public TenantRechargeController(TenantRechargeService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<RechargeRecord> submit(@PathVariable long tenantId, @RequestBody RechargeCommand command,
                                              Authentication authentication) {
        return ApiResponse.ok(service.submit(scopedTenantId(authentication, tenantId), command, actor(authentication)));
    }

    @GetMapping("/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<RechargeRecord>> tenantRecords(@PathVariable long tenantId, Authentication authentication) {
        return ApiResponse.ok(service.tenantRecords(scopedTenantId(authentication, tenantId)));
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<RechargeRecord>> reviews(@RequestParam(required = false) String status,
                                                     Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.reviewQueue(status));
    }

    @PostMapping("/{rechargeId}/review")
    @PreAuthorize("hasAuthority('trial-prepaid:write')")
    public ApiResponse<RechargeRecord> review(@PathVariable long rechargeId, @RequestBody ReviewCommand command,
                                              Authentication authentication) {
        requireFinance(authentication);
        return ApiResponse.ok(service.review(rechargeId, command, actor(authentication)));
    }

    private long scopedTenantId(Authentication authentication, long requestedTenantId) {
        if (isPlatform(authentication)) return requestedTenantId;
        Long ownTenantId = ownTenantId(authentication);
        if (ownTenantId == null || ownTenantId != requestedTenantId) {
            throw new BusinessException("TENANT_SCOPE_FORBIDDEN", "不能访问其他机构充值记录");
        }
        return requestedTenantId;
    }

    private void requireFinance(Authentication authentication) {
        if (!(hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_FINANCE"))) {
            throw new BusinessException("FINANCE_ROLE_REQUIRED", "仅管理员或财务可审核充值");
        }
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
