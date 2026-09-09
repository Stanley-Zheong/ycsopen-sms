package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyPort.NormalizedStatus;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService.ExportResponse;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService.ImportRequest;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService.ImportResponse;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService.MappingView;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService.VersionRow;
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

@RestController
@RequestMapping("/api/v1/console/provider-status")
public class ProviderStatusTaxonomyController {
    private final ProviderStatusTaxonomyService service;

    public ProviderStatusTaxonomyController(ProviderStatusTaxonomyService service) {
        this.service = service;
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('provider-status:read')")
    public ApiResponse<List<VersionRow>> versions() {
        return ApiResponse.ok(service.versions());
    }

    @GetMapping("/mappings")
    @PreAuthorize("hasAuthority('provider-status:read')")
    public ApiResponse<List<MappingView>> mappings() {
        return ApiResponse.ok(service.mappings());
    }

    @GetMapping("/normalize")
    @PreAuthorize("hasAuthority('provider-status:read')")
    public ApiResponse<NormalizedStatus> normalize(@RequestParam String providerName,
                                                   @RequestParam String protocol,
                                                   @RequestParam String providerCode) {
        return ApiResponse.ok(service.normalize(providerName, protocol, providerCode));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('provider-status:import')")
    public ApiResponse<ImportResponse> importMappings(@RequestBody ImportRequest request,
                                                      Authentication authentication) {
        return ApiResponse.ok(service.importMappings(request, actor(authentication)));
    }

    @PostMapping("/export-request")
    @PreAuthorize("hasAuthority('provider-status:export')")
    public ApiResponse<ExportResponse> exportRequest(@RequestParam(required = false) String providerName,
                                                     @RequestParam(required = false) String protocol,
                                                     Authentication authentication) {
        return ApiResponse.ok(service.exportRequest(providerName, protocol, actor(authentication)));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }
}
