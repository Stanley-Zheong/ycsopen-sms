package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.service.tenant.*;
import com.ycsopen.sms.core.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/tenant-registrations")
public class TenantRegistrationController {
    private final TenantRegistrationService registrations;
    private final ContactVerificationService contacts;
    public TenantRegistrationController(TenantRegistrationService registrations, ContactVerificationService contacts) {
        this.registrations = registrations; this.contacts = contacts;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TenantRegistrationResponse>> register(
            @RequestHeader(value = TenantRegistrationProtectionAdapter.UPLOAD_TOKEN_HEADER, required = false) String uploadToken,
            @RequestBody RegistrationSubmission request) {
        return safe(registrations.register(request.qualification(), uploadToken, request.contactChallengeId(),
                request.adminUsername(), request.adminPassword(), request.adminEmail()));
    }

    @PostMapping("/contact-challenges")
    public ResponseEntity<ApiResponse<ContactVerificationService.ChallengeReceipt>> requestContact(
            @RequestBody ContactRequest request, HttpServletRequest servletRequest) {
        return safe(contacts.request(request.phone(), servletRequest.getRemoteAddr()));
    }

    @PostMapping("/contact-challenges/{challengeId}/verify")
    public ResponseEntity<ApiResponse<Void>> verifyContact(@PathVariable String challengeId, @RequestBody ContactVerification request) {
        contacts.verify(challengeId, request.phone(), request.code());
        return safe(null);
    }

    static <T> ResponseEntity<ApiResponse<T>> safe(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").body(ApiResponse.ok(value));
    }

    public record RegistrationSubmission(TenantRegistrationRequest qualification, String contactChallengeId,
                                         String adminUsername, String adminPassword, String adminEmail) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) { throw new IllegalArgumentException("Unknown registration field"); }
        @Override public String toString() { return "RegistrationSubmission[redacted]"; }
    }
    public record ContactRequest(String phone) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) { throw new IllegalArgumentException("Unknown contact field"); }
        @Override public String toString() { return "ContactRequest[redacted]"; }
    }
    public record ContactVerification(String phone, String code) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) { throw new IllegalArgumentException("Unknown contact field"); }
        @Override public String toString() { return "ContactVerification[redacted]"; }
    }
}
