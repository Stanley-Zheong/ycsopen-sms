package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Set;

/** Permission-scoped search over redacted, append-only console audit records. */
@RestController
@RequestMapping("/api/v1/console/operation-audits")
public class OperationAuditController {

    private static final Set<String> RESULTS = Set.of(
            "STARTED", "SUCCESS", "CLIENT_FAILURE", "SERVER_FAILURE", "DENIED");

    private final OperationAuditService audits;

    public OperationAuditController(OperationAuditService audits) {
        this.audits = audits;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('audit:operations:read')")
    public ApiResponse<OperationAuditService.AuditPage> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        validateOptional(result, RESULTS, "result");
        Long scope = has(authentication, "ROLE_ADMIN") || has(authentication, "audit:operations:all")
                ? null : Long.parseLong(authentication.getName());
        return ApiResponse.ok(audits.search(
                new OperationAuditService.Search(actor, operation, result, from, to, page, size), scope));
    }

    private static boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals(authority));
    }

    private static void validateOptional(String value, Set<String> allowed, String field) {
        if (value != null && !value.isBlank() && !allowed.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " filter is invalid");
        }
    }
}
