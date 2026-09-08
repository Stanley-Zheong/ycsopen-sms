package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.tenant.TenantApiKeyService;
import com.ycsopen.sms.core.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/console/tenant/api-keys")
public class TenantApiKeyController {
    private final TenantApiKeyService service;
    public TenantApiKeyController(TenantApiKeyService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<List<TenantApiKeyResponse>> list(Authentication a) { return ApiResponse.ok(service.list(id(a))); }
    @PostMapping @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<TenantApiKeyResponse> create(@Valid @RequestBody TenantApiKeyCreateRequest r, Authentication a) { return ApiResponse.ok(service.create(id(a),r)); }
    @PostMapping("/{keyId}/revoke") @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<Void> revoke(@PathVariable long keyId, Authentication a) { service.revoke(id(a),keyId); return ApiResponse.ok(null); }
    private static long id(Authentication a) { return Long.parseLong(a.getName()); }
}
