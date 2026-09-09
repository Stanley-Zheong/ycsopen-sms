package com.ycsopen.sms.core.web.controller;
import com.ycsopen.sms.core.service.tenant.TenantProtocolCredentialService;
import com.ycsopen.sms.core.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/v1/console/tenant/cmpp-credentials")
public class TenantProtocolCredentialController {
 private final TenantProtocolCredentialService service; public TenantProtocolCredentialController(TenantProtocolCredentialService service){this.service=service;}
 @GetMapping @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')") public ApiResponse<List<TenantProtocolCredentialResponse>> list(Authentication a){return ApiResponse.ok(service.list(id(a)));}
 @PostMapping @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')") public ApiResponse<TenantProtocolCredentialResponse> create(@Valid @RequestBody TenantProtocolCredentialCreateRequest r,Authentication a){return ApiResponse.ok(service.create(id(a),r));}
 @PostMapping("/{credentialId}/revoke") @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')") public ApiResponse<Void> revoke(@PathVariable long credentialId,Authentication a){service.revoke(id(a),credentialId);return ApiResponse.ok(null);}
 private static long id(Authentication a){return Long.parseLong(a.getName());}
}
