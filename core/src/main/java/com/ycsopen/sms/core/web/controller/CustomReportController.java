package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.report.CustomReportService;
import com.ycsopen.sms.core.service.report.CustomReportService.CapabilityRow;
import com.ycsopen.sms.core.service.report.CustomReportService.DefinitionRow;
import com.ycsopen.sms.core.service.report.CustomReportService.ExportRequestRow;
import com.ycsopen.sms.core.service.report.CustomReportService.PreviewResult;
import com.ycsopen.sms.core.service.report.CustomReportService.ReportCommand;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CustomReportController {
    private final CustomReportService service;
    private final UserRepository users;

    public CustomReportController(CustomReportService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/custom-reports/capabilities")
    @PreAuthorize("hasAuthority('custom-report:read')")
    public ApiResponse<List<CapabilityRow>> capabilities() {
        return ApiResponse.ok(service.capabilities());
    }

    @PostMapping("/api/v1/console/custom-reports/preview")
    @PreAuthorize("hasAuthority('custom-report:read')")
    public ApiResponse<PreviewResult> preview(@RequestBody ReportCommand request, Authentication authentication) {
        return ApiResponse.ok(service.preview(request, actor(authentication)));
    }

    @PostMapping("/api/v1/console/custom-reports/definitions")
    @PreAuthorize("hasAuthority('custom-report:write')")
    public ApiResponse<DefinitionRow> save(@RequestBody ReportCommand request, Authentication authentication) {
        return ApiResponse.ok(service.save(request, actor(authentication)));
    }

    @GetMapping("/api/v1/console/custom-reports/definitions")
    @PreAuthorize("hasAuthority('custom-report:read')")
    public ApiResponse<List<DefinitionRow>> definitions(Authentication authentication) {
        return ApiResponse.ok(service.definitions(actor(authentication)));
    }

    @PostMapping("/api/v1/console/custom-reports/definitions/{id}/export")
    @PreAuthorize("hasAuthority('custom-report:write')")
    public ApiResponse<ExportRequestRow> requestExport(@PathVariable long id, Authentication authentication) {
        return ApiResponse.ok(service.requestExport(id, actor(authentication)));
    }

    private CustomReportService.Actor actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        if (hasRole(authentication, "ROLE_ADMIN") || hasRole(authentication, "ROLE_OPERATOR")
                || hasRole(authentication, "ROLE_FINANCE")) {
            return CustomReportService.Actor.platform(authentication.getName());
        }
        long userId;
        try {
            userId = Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        if (user.getTenantId() == null) {
            throw new BusinessException("CUSTOM_REPORT_TENANT_REQUIRED", "租户账号缺少机构范围");
        }
        return CustomReportService.Actor.tenant(authentication.getName(), user.getTenantId());
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
