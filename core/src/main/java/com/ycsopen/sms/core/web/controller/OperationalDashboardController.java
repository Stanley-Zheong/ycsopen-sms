package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService.Actor;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService.ApiStatus;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService.DashboardConfiguration;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService.DashboardConfigurationCommand;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService.PlatformDashboard;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService.ResourceStatistics;
import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService.TenantOverview;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/console/operational-dashboards")
public class OperationalDashboardController {
    private static final List<String> PLATFORM_ROLES = List.of("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_FINANCE");

    private final OperationalDashboardService service;
    private final UserRepository users;

    public OperationalDashboardController(OperationalDashboardService service, UserRepository users) {
        this.service = Objects.requireNonNull(service);
        this.users = Objects.requireNonNull(users);
    }

    @GetMapping("/platform")
    @PreAuthorize("hasAuthority('operational-dashboard:read')")
    public ApiResponse<PlatformDashboard> platform() {
        return ApiResponse.ok(service.platformDashboard());
    }

    @GetMapping("/tenant-overview")
    @PreAuthorize("hasAuthority('operational-dashboard:read')")
    public ApiResponse<TenantOverview> tenantOverview(Authentication authentication,
                                                      @RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(service.tenantOverview(actor(authentication), tenantId));
    }

    @GetMapping("/resource-statistics")
    @PreAuthorize("hasAuthority('operational-dashboard:read')")
    public ApiResponse<ResourceStatistics> resourceStatistics(Authentication authentication,
                                                             @RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(service.resourceStatistics(actor(authentication), tenantId));
    }

    @GetMapping("/api-status")
    @PreAuthorize("hasAuthority('operational-dashboard:read')")
    public ApiResponse<ApiStatus> apiStatus() {
        return ApiResponse.ok(service.apiStatus());
    }

    @GetMapping("/configuration")
    @PreAuthorize("hasAuthority('operational-dashboard:read')")
    public ApiResponse<DashboardConfiguration> configuration(@RequestParam(defaultValue = "ADMIN") String role) {
        return ApiResponse.ok(service.configuration(role));
    }

    @PostMapping("/configuration")
    @PreAuthorize("hasAuthority('operational-dashboard:write')")
    public ApiResponse<DashboardConfiguration> saveConfiguration(@RequestBody DashboardConfigurationCommand command,
                                                                 Authentication authentication) {
        return ApiResponse.ok(service.saveConfiguration(command, actorName(authentication)));
    }

    private Actor actor(Authentication authentication) {
        String name = actorName(authentication);
        if (isPlatform(authentication)) {
            return Actor.platform(name);
        }
        long userId;
        try {
            userId = Long.parseLong(name);
        } catch (RuntimeException failure) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空"));
        if (user.getTenantId() == null) {
            throw new BusinessException("OPERATIONAL_DASHBOARD_TENANT_REQUIRED", "租户范围不能为空");
        }
        return Actor.tenant(name, user.getTenantId());
    }

    private static boolean isPlatform(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> PLATFORM_ROLES.contains(authority.getAuthority().toUpperCase(Locale.ROOT)));
    }

    private static String actorName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }
}
