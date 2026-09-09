package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.bulk.BulkScheduledTaskService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phase 29 bulk, scheduled, and task operation APIs. */
@RestController
public class BulkScheduledTaskController {
    private final BulkScheduledTaskService service;
    private final UserRepository users;

    public BulkScheduledTaskController(BulkScheduledTaskService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @PostMapping("/api/v1/console/tenant/bulk/preview")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_USER') or hasRole('TENANT_DEV')")
    public ApiResponse<BulkScheduledTaskService.PreviewResult> preview(
            @Valid @RequestBody BulkScheduledTaskService.BulkCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.preview(tenantIdFor(authentication), command));
    }

    @PostMapping("/api/v1/console/tenant/bulk/tasks")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_USER') or hasRole('TENANT_DEV')")
    public ApiResponse<BulkScheduledTaskService.BulkTaskView> create(
            @Valid @RequestBody BulkScheduledTaskService.BulkCommand command,
            Authentication authentication,
            HttpServletRequest request) {
        return ApiResponse.ok(service.create(tenantIdFor(authentication), command,
                request == null ? null : request.getRemoteAddr(), actor(authentication)));
    }

    @GetMapping("/api/v1/console/tenant/scheduled-tasks")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_USER') or hasRole('TENANT_DEV')")
    public ApiResponse<List<BulkScheduledTaskService.BulkTaskView>> tenantTasks(Authentication authentication) {
        return ApiResponse.ok(service.tenantTasks(tenantIdFor(authentication)));
    }

    @PostMapping("/api/v1/console/tenant/scheduled-tasks/{bulkId}/{action}")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_USER') or hasRole('TENANT_DEV')")
    public ApiResponse<BulkScheduledTaskService.BulkTaskView> tenantControl(
            @PathVariable long bulkId,
            @PathVariable String action,
            @RequestBody ControlRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.controlForTenant(tenantIdFor(authentication), bulkId, action,
                actor(authentication), request.reason()));
    }

    @GetMapping("/api/v1/console/bulk/tasks")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ApiResponse<List<BulkScheduledTaskService.BulkTaskView>> adminTasks(
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String state) {
        return ApiResponse.ok(service.adminTasks(tenantId, state));
    }

    @GetMapping("/api/v1/console/bulk/tasks/{bulkId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ApiResponse<BulkScheduledTaskService.BulkTaskDetail> adminDetail(@PathVariable long bulkId) {
        return ApiResponse.ok(service.detail(bulkId));
    }

    @PostMapping("/api/v1/console/bulk/tasks/{bulkId}/{action}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('OPERATOR')")
    public ApiResponse<BulkScheduledTaskService.BulkTaskView> adminControl(
            @PathVariable long bulkId,
            @PathVariable String action,
            @RequestBody ControlRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.control(bulkId, action, actor(authentication), request.reason()));
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

    public record ControlRequest(String reason) { }
}
