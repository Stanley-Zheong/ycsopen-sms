package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.risk.ContentSafetyService;
import com.ycsopen.sms.core.service.risk.ContentSafetyService.*;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Phase 17: runtime final-content safety console endpoints. */
@RestController
@RequestMapping("/api/v1/console/content-safety")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ContentSafetyController {
    private final ContentSafetyService service;

    public ContentSafetyController(ContentSafetyService service) {
        this.service = service;
    }

    @GetMapping("/policies")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('content-safety:read')")
    public ApiResponse<List<PolicyRow>> policies(@RequestParam(required = false) String word,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String level,
                                                 @RequestParam(required = false) String action,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.policies(word, category, level, action, status));
    }

    @PostMapping("/policies")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('content-safety:write')")
    public ApiResponse<PolicyRow> save(@RequestBody PolicyRequest request) {
        return ApiResponse.ok(service.save(request));
    }

    @PostMapping("/policies/import")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('content-safety:import')")
    public ApiResponse<ImportResponse> importPolicies(@RequestBody ImportRequest request) {
        return ApiResponse.ok(service.importPolicies(request));
    }

    @PostMapping("/policies/{id}/delete")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('content-safety:write')")
    public ApiResponse<PolicyRow> delete(@PathVariable long id) {
        return ApiResponse.ok(service.disable(id));
    }

    @PostMapping("/policies/export-request")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('content-safety:export')")
    public ApiResponse<ExportRequestResponse> exportRequest(@RequestParam(required = false) String word,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(required = false) String action,
                                                            @RequestParam(required = false) String status,
                                                            Authentication authentication) {
        return ApiResponse.ok(service.exportRequest(word, category, action, status, actor(authentication)));
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('content-safety:read')")
    public ApiResponse<AnalyticsResponse> analytics() {
        return ApiResponse.ok(service.analytics());
    }

    @PostMapping("/scan")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('content-safety:scan')")
    public ApiResponse<ScanResponse> scan(@RequestBody ScanRequest request) {
        return ApiResponse.ok(service.scan(request));
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }
}
