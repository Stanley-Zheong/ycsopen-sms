package com.ycsopen.sms.core.web.controller;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.ycsopen.sms.core.service.tenant.QualificationEvidenceService;
import com.ycsopen.sms.core.service.tenant.QualificationInspectionService;
import com.ycsopen.sms.core.service.tenant.TenantReviewService;
import com.ycsopen.sms.core.service.tenant.TenantMaintenanceService;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Safe operator review surface for tenant qualification. */
@RestController
@RequestMapping("/api/v1/console/admin/tenants")
public class AdminTenantController {
    private final TenantReviewService reviews;
    private final ObjectProvider<QualificationInspectionService> inspections;
    private final ObjectProvider<QualificationEvidenceService> evidence;
    private final TenantMaintenanceService maintenance;

    public AdminTenantController(TenantReviewService reviews,
                                 ObjectProvider<QualificationInspectionService> inspections,
                                 ObjectProvider<QualificationEvidenceService> evidence,
                                 TenantMaintenanceService maintenance) {
        this.reviews = reviews;
        this.inspections = inspections;
        this.evidence = evidence;
        this.maintenance = maintenance;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:read')")
    public ResponseEntity<ApiResponse<List<TenantReviewService.ReviewView>>> list() {
        return safe(reviews.list());
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:read')")
    public ResponseEntity<ApiResponse<TenantReviewService.ReviewView>> get(@PathVariable long tenantId) {
        return safe(reviews.get(tenantId));
    }

    @PostMapping("/{tenantId}/inspection")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:qualification:review')")
    public ResponseEntity<ApiResponse<TenantReviewService.ReviewView>> inspect(
            @PathVariable long tenantId, @RequestBody RevisionRequest request) {
        QualificationInspectionService service = inspections.getIfAvailable();
        if (service == null) throw new QualificationInspectionService.InspectionFailure(
                "QUALIFICATION_INSPECTION_UNAVAILABLE");
        return safe(service.inspect(tenantId, request.expectedRevision(), reviews));
    }

    @PostMapping("/{tenantId}/decision")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:qualification:review')")
    public ResponseEntity<ApiResponse<TenantReviewService.ReviewView>> decide(
            @PathVariable long tenantId, @RequestBody DecisionRequest request, Authentication authentication) {
        if (request.decision() == null) throw new IllegalArgumentException("INVALID_REVIEW_DECISION");
        return safe(reviews.decide(tenantId, request.expectedRevision(), request.decision(),
                request.reason(), request.humanConfirmed(), subject(authentication)));
    }

    @PatchMapping("/{tenantId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:update')")
    public ResponseEntity<ApiResponse<TenantMaintenanceService.ProfileResult>> updateProfile(
            @PathVariable long tenantId, @RequestBody ProfileRequest request,
            Authentication authentication) {
        return safe(maintenance.updateProfile(tenantId, request.expectedRevision(),
                new TenantMaintenanceService.ProfileUpdate(request.shortName(), request.contactName(),
                        request.businessAddress(), request.customerLevel(), request.bizManager(),
                        request.industry()), request.reason(), subject(authentication)));
    }

    @PostMapping("/{tenantId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:status:update')")
    public ResponseEntity<ApiResponse<TenantMaintenanceService.AccountStatusResult>> updateStatus(
            @PathVariable long tenantId, @RequestBody AccountStatusRequest request,
            Authentication authentication) {
        return safe(maintenance.changeAccountStatus(tenantId, request.expectedRevision(),
                request.target(), request.reason(), subject(authentication)));
    }

    @GetMapping("/{tenantId}/events")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:read')")
    public ResponseEntity<ApiResponse<List<TenantMaintenanceService.QualificationEventView>>> history(
            @PathVariable long tenantId) {
        return safe(maintenance.history(tenantId));
    }

    @GetMapping("/{tenantId}/evidence/{kind}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:evidence:read')")
    public ResponseEntity<byte[]> evidence(@PathVariable long tenantId,
                                           @PathVariable QualificationEvidenceService.EvidenceKind kind,
                                           Authentication authentication) {
        QualificationEvidenceService service = evidence.getIfAvailable();
        if (service == null) throw QualificationEvidenceService.Failure.unavailable();
        QualificationEvidenceService.EvidenceContent content = service.readForReviewer(
                tenantId, kind, subject(authentication));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .header(HttpHeaders.CONTENT_TYPE, content.mediaType())
                .body(content.bytes());
    }

    @ExceptionHandler({TenantReviewService.ReviewFailure.class,
            TenantMaintenanceService.MaintenanceFailure.class,
            QualificationInspectionService.InspectionFailure.class,
            QualificationEvidenceService.Failure.class, IllegalArgumentException.class})
    ResponseEntity<ApiResponse<Void>> failure(RuntimeException failure) {
        String code = failure.getMessage() == null ? "QUALIFICATION_REQUEST_INVALID" : failure.getMessage();
        int status = code.contains("STALE") || code.contains("NOT_PENDING") ? 409
                : code.contains("UNAVAILABLE") ? 503 : code.contains("DENIED") ? 403
                : code.equals("TENANT_NOT_FOUND") ? 404 : 422;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").body(ApiResponse.error(status, code));
    }

    private static String subject(Authentication authentication) {
        if (authentication == null || authentication.getName() == null
                || !authentication.getName().matches("[0-9]{1,19}")) {
            throw new AccessDeniedException("Tenant review denied");
        }
        return authentication.getName();
    }

    private static <T> ResponseEntity<ApiResponse<T>> safe(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").body(ApiResponse.ok(value));
    }

    public record DecisionRequest(long expectedRevision, TenantReviewService.Decision decision,
                                  String reason, boolean humanConfirmed) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) {
            throw new IllegalArgumentException("unknown decision field");
        }
        @Override public String toString() { return "DecisionRequest[decision=" + decision + ", payload=[redacted]]"; }
    }

    public record RevisionRequest(long expectedRevision) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) {
            throw new IllegalArgumentException("unknown inspection field");
        }
    }

    public record ProfileRequest(long expectedRevision, String shortName, String contactName,
                                 String businessAddress, Integer customerLevel, String bizManager,
                                 String industry, String reason) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) {
            throw new IllegalArgumentException("unknown profile field");
        }
        @Override public String toString() { return "ProfileRequest[payload=[redacted]]"; }
    }

    public record AccountStatusRequest(int expectedRevision, TenantAccount.Status target, String reason) {
        @JsonAnySetter public void rejectUnknown(String key, Object value) {
            throw new IllegalArgumentException("unknown status field");
        }
        @Override public String toString() { return "AccountStatusRequest[target=" + target + ", payload=[redacted]]"; }
    }
}
