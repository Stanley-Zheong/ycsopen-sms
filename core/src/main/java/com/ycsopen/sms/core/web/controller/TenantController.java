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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** F-2.1/F-2.2/F-2.8 机构注册、审核、试用激活（平台管理后台"机构管理"调用）。 */
@RestController
@RequestMapping("/api/v1/console/tenants")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class TenantController {

    private final TenantService tenantService;
    @Autowired
    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TenantRegistrationResponse>> register(
            @RequestHeader(value = TenantRegistrationProtectionAdapter.UPLOAD_TOKEN_HEADER,
                    required = false) String uploadToken,
            @RequestBody TenantRegistrationRequest request) {
        // This route predates the public qualification flow and has no contact receipt or
        // initial-admin contract.  Keep the mapping for old clients, but never allow it to
        // create a partially qualified tenant.
        if (request == null) {
            throw new LegacyRouteException("LEGACY_REGISTRATION_ROUTE_REMOVED");
        }
        if (request.hasLegacyObjectUrlInput()) {
            throw TenantRegistrationProtectionAdapter.Failure.legacyObjectUrlNotAccepted();
        }
        throw new LegacyRouteException("LEGACY_REGISTRATION_ROUTE_REMOVED");
    }

    /** Compatibility adapters retain the old URLs while requiring the authoritative review contract. */
    @PostMapping("/{tenantId}/approve-and-activate-trial")
    public ResponseEntity<ApiResponse<Void>> legacyApprove(@PathVariable long tenantId,
            @RequestParam(required = false) Long expectedRevision,
            @RequestParam(required = false) String approvedBy,
            @RequestParam(required = false) Integer trialQuota,
            @RequestParam(required = false) Integer trialDays) {
        throw new LegacyRouteException("LEGACY_APPROVAL_REQUIRES_REVIEW");
    }

    @PostMapping("/{tenantId}/reject")
    public ResponseEntity<ApiResponse<Void>> legacyReject(@PathVariable long tenantId,
            @RequestParam(required = false) Long expectedRevision,
            @RequestParam(required = false) String reason) {
        throw new LegacyRouteException("LEGACY_REJECTION_REQUIRES_REVIEW");
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

    record RegistrationError(String code, String message) {
    }

    @ExceptionHandler(LegacyRouteException.class)
    ResponseEntity<RegistrationError> handleLegacyRoute(LegacyRouteException failure) {
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(new RegistrationError(failure.getMessage(), failure.getMessage()));
    }

    static final class LegacyRouteException extends RuntimeException {
        LegacyRouteException(String code) { super(code); }
    }
}
