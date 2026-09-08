package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.tenant.TenantAccessAdministrationService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.TenantAdministratorCreateRequest;
import com.ycsopen.sms.core.web.dto.TenantAdministratorResponse;
import com.ycsopen.sms.core.web.dto.TenantAdministratorUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tenant administrator subaccount endpoints; tenant scope comes from JWT identity. */
@RestController
@RequestMapping("/api/v1/console/tenant/administrators")
public class TenantAccessAdministrationController {
    private final TenantAccessAdministrationService service;
    public TenantAccessAdministrationController(TenantAccessAdministrationService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<List<TenantAdministratorResponse>> list(Authentication auth) {
        return ApiResponse.ok(service.list(actor(auth)));
    }

    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<TenantAdministratorResponse> create(@Valid @RequestBody TenantAdministratorCreateRequest request,
                                                           Authentication auth) {
        return ApiResponse.ok(service.create(actor(auth), request));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ApiResponse<TenantAdministratorResponse> update(@PathVariable long userId,
                                                           @Valid @RequestBody TenantAdministratorUpdateRequest request,
                                                           Authentication auth) {
        return ApiResponse.ok(service.update(actor(auth), userId, request));
    }
    private static long actor(Authentication auth) { return Long.parseLong(auth.getName()); }
}
