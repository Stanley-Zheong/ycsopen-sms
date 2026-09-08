package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.service.tenant.TenantRegistrationService;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

/** Own-tenant identity is resolved from the authenticated user on every operation. */
@RestController
@RequestMapping("/api/v1/console/tenant/qualification")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class TenantQualificationController {
    private final TenantRegistrationService registrations;
    public TenantQualificationController(TenantRegistrationService registrations) { this.registrations = registrations; }

    @GetMapping
    public ResponseEntity<ApiResponse<TenantRegistrationResponse>> status(Principal principal) {
        return TenantRegistrationController.safe(registrations.statusOwn(principal == null ? null : principal.getName()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantRegistrationResponse>> submit(Principal principal,
            @RequestHeader(value = TenantRegistrationProtectionAdapter.UPLOAD_TOKEN_HEADER, required = false) String uploadToken,
            @RequestBody QualificationSubmission request) {
        return TenantRegistrationController.safe(registrations.submitOwn(principal == null ? null : principal.getName(),
                request.qualification(), uploadToken, request.contactChallengeId()));
    }

    public record QualificationSubmission(TenantRegistrationRequest qualification, String contactChallengeId) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) { throw new IllegalArgumentException("Unknown qualification field"); }
        @Override public String toString() { return "QualificationSubmission[redacted]"; }
    }
}
