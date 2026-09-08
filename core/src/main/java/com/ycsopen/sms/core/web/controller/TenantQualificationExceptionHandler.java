package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.service.tenant.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {TenantRegistrationController.class, TenantQualificationController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantQualificationExceptionHandler {
    @ExceptionHandler({TenantRegistrationService.SubmissionFailure.class, ContactVerificationService.ChallengeFailure.class,
            TenantQualificationValidator.ValidationFailure.class})
    ResponseEntity<Failure> rejected(RuntimeException failure) {
        String code = failure.getMessage();
        int status = code.endsWith("RATE_LIMITED") ? 429 : code.endsWith("UNAVAILABLE") ? 503 : code.endsWith("EXPIRED") ? 410
                : code.equals("DUPLICATE_REGISTRATION") || code.contains("ALREADY_") || code.equals("QUALIFICATION_NOT_AVAILABLE") ? 409 : 422;
        return error(status, code);
    }

    @ExceptionHandler(TenantRegistrationProtectionAdapter.Failure.class)
    ResponseEntity<Failure> protectedFailure(TenantRegistrationProtectionAdapter.Failure failure) {
        int status = switch (failure.category()) {
            case REGISTRATION_UPLOAD_TOKEN_INVALID -> 403;
            case REGISTRATION_OBJECT_SESSION_NOT_OPEN, REGISTRATION_OBJECT_BINDING_MISMATCH,
                    REGISTRATION_OBJECT_ALREADY_CLAIMED, REGISTRATION_OBJECT_NOT_STAGED,
                    REGISTRATION_OBJECT_PARTIAL_CLAIM -> 409;
            case REGISTRATION_OBJECT_SESSION_EXPIRED, REGISTRATION_OBJECT_EXPIRED -> 410;
            case REGISTRATION_PROTECTION_UNAVAILABLE -> 503;
            default -> 422;
        };
        return error(status, failure.category().name());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Failure> unreadable() { return error(422, "INVALID_QUALIFICATION_REQUEST"); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Failure> forbidden() { return error(403, "TENANT_ACCESS_DENIED"); }

    private ResponseEntity<Failure> error(int status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").body(new Failure(code));
    }
    public record Failure(String code) { }
}
