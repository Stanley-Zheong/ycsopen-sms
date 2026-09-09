package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.tool.NumberAttributionService;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.AttributionResult;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.PortabilityRequest;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.PortabilityRow;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.PrefixImportRequest;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.PrefixImportResponse;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.PrefixVersionRow;
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
@RequestMapping("/api/v1/console/number-attribution")
public class NumberAttributionController {
    private final NumberAttributionService service;

    public NumberAttributionController(NumberAttributionService service) {
        this.service = service;
    }

    @PostMapping("/prefixes/import")
    @PreAuthorize("hasAuthority('number-attribution:import')")
    public ApiResponse<PrefixImportResponse> importPrefixes(@RequestBody PrefixImportRequest request,
                                                            Authentication authentication) {
        return ApiResponse.ok(service.importPrefixes(request, actor(authentication)));
    }

    @GetMapping("/prefixes/versions")
    @PreAuthorize("hasAuthority('number-attribution:read')")
    public ApiResponse<List<PrefixVersionRow>> versions() {
        return ApiResponse.ok(service.versions());
    }

    @GetMapping("/lookup")
    @PreAuthorize("hasAuthority('number-attribution:read')")
    public ApiResponse<AttributionResult> lookup(@RequestParam String mobile,
                                                 @RequestParam(defaultValue = "false") boolean forceProviderFailure) {
        return ApiResponse.ok(service.lookup(mobile, forceProviderFailure));
    }

    @PostMapping("/portability")
    @PreAuthorize("hasAuthority('number-attribution:portability')")
    public ApiResponse<PortabilityRow> savePortability(@RequestBody PortabilityRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(service.savePortability(request, actor(authentication)));
    }

    @GetMapping("/portability")
    @PreAuthorize("hasAuthority('number-attribution:read')")
    public ApiResponse<List<PortabilityRow>> portabilityRows() {
        return ApiResponse.ok(service.portabilityRows());
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }
}
