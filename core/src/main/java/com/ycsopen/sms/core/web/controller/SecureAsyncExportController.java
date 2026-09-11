package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService.DownloadArtifact;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService.ExportCreateCommand;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService.ExportJob;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService.ExportSearch;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService.RetryCommand;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/console/exports")
public class SecureAsyncExportController {
    private final SecureAsyncExportService exports;
    private final UserRepository users;

    public SecureAsyncExportController(SecureAsyncExportService exports, UserRepository users) {
        this.exports = exports;
        this.users = users;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE','TENANT_ADMIN','TENANT_DEV') or hasAuthority('secure-async-export:read')")
    public ApiResponse<List<ExportJob>> list(@RequestParam(required = false) Long tenantId,
                                             @RequestParam(required = false) String exportType,
                                             @RequestParam(required = false) String status,
                                             Authentication authentication) {
        return ApiResponse.ok(exports.list(new ExportSearch(tenantId, exportType, status), scopedTenant(authentication)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('secure-async-export:create')")
    public ApiResponse<ExportJob> create(@RequestBody ConsoleExportRequest request, Authentication authentication) {
        ConsoleExportRequest checked = request == null
                ? new ConsoleExportRequest(null, null, "SEND_DETAIL", "CONSOLE", "手工导出", "CSV", Map.of(), List.of())
                : request;
        return ApiResponse.ok(exports.create(new ExportCreateCommand(checked.requestId(), checked.tenantId(),
                checked.exportType(), checked.producer(), checked.jobName(), actor(authentication), checked.format(),
                checked.filters(), List.of("id ASC"), "secure-async-export:create", List.of("mobile", "phone"),
                checked.rows())));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('secure-async-export:retry')")
    public ApiResponse<ExportJob> retry(@PathVariable long id, @RequestBody RetryRequest request,
                                        Authentication authentication) {
        RetryRequest checked = request == null ? new RetryRequest("人工确认重试") : request;
        return ApiResponse.ok(exports.retry(id, new RetryCommand(actor(authentication), checked.reason())));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE','TENANT_ADMIN','TENANT_DEV') or hasAuthority('secure-async-export:download')")
    public ApiResponse<DownloadArtifact> download(@PathVariable long id, Authentication authentication) {
        return ApiResponse.ok(exports.download(id, new SecureAsyncExportService.DownloadCommand(
                actor(authentication), scopedTenant(authentication))));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }

    private Long scopedTenant(Authentication authentication) {
        if (authentication == null) return null;
        boolean platform = authentication.getAuthorities().stream()
                .anyMatch(a -> List.of("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_FINANCE").contains(a.getAuthority()));
        if (platform) return null;
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        if (user.getTenantId() == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文不能为空");
        }
        return user.getTenantId();
    }

    public record ConsoleExportRequest(String requestId, Long tenantId, String exportType, String producer,
                                       String jobName, String format, Map<String, Object> filters,
                                       List<Map<String, Object>> rows) { }

    public record RetryRequest(String reason) { }
}
