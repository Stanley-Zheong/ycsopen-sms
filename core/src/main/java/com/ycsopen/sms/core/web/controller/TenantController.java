package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.service.tenant.TenantService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import com.ycsopen.sms.core.web.dto.TenantRegistrationResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

/** F-2.1/F-2.2/F-2.8 机构注册、审核、试用激活（平台管理后台"机构管理"调用）。 */
@RestController
@RequestMapping("/api/v1/console/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TenantRegistrationResponse>> register(
            @RequestHeader(value = TenantRegistrationProtectionAdapter.UPLOAD_TOKEN_HEADER,
                    required = false) String uploadToken,
            @RequestBody TenantRegistrationRequest request) {
        Tenant tenant = tenantService.submitRegistration(request, uploadToken);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiResponse.ok(TenantRegistrationResponse.from(tenant)));
    }

    @ExceptionHandler(TenantRegistrationProtectionAdapter.Failure.class)
    ResponseEntity<RegistrationError> handleRegistrationFailure(
            TenantRegistrationProtectionAdapter.Failure failure) {
        HttpStatus status = switch (failure.category()) {
            case REGISTRATION_UPLOAD_TOKEN_INVALID -> HttpStatus.FORBIDDEN;
            case REGISTRATION_OBJECT_SESSION_NOT_OPEN,
                    REGISTRATION_OBJECT_BINDING_MISMATCH,
                    REGISTRATION_OBJECT_ALREADY_CLAIMED,
                    REGISTRATION_OBJECT_NOT_STAGED,
                    REGISTRATION_OBJECT_PARTIAL_CLAIM -> HttpStatus.CONFLICT;
            case REGISTRATION_OBJECT_SESSION_EXPIRED,
                    REGISTRATION_OBJECT_EXPIRED -> HttpStatus.GONE;
            case REGISTRATION_PROTECTION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new RegistrationError(failure.category().name(), failure.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<RegistrationError> handleUnreadableRegistrationInput() {
        TenantRegistrationProtectionAdapter.Failure failure =
                TenantRegistrationProtectionAdapter.Failure.inputInvalid();
        return ResponseEntity.unprocessableEntity()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new RegistrationError(failure.category().name(), failure.getMessage()));
    }

    @PostMapping("/{tenantId}/approve-and-activate-trial")
    public ApiResponse<Tenant> approveAndActivateTrial(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "500") int trialQuota,
            @RequestParam(defaultValue = "14") int trialDays,
            @RequestParam String approvedBy) {
        return ApiResponse.ok(tenantService.approveAndActivateTrial(tenantId, trialQuota, trialDays, approvedBy));
    }

    @PostMapping("/{tenantId}/reject")
    public ApiResponse<Void> reject(@PathVariable Long tenantId, @RequestParam String reason) {
        tenantService.rejectRegistration(tenantId, reason);
        return ApiResponse.ok(null);
    }

    record RegistrationError(String code, String message) {
    }
}
