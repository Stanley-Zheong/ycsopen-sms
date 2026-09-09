package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.audit.SecurityEventService;
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

/** Current-RBAC search endpoint for deduplicated security-event handoff rows. */
@RestController
@RequestMapping("/api/v1/console/security-events")
public class SecurityEventController {

    private static final Set<String> EVENT_TYPES = Set.of(
            "UNUSUAL_LOGIN", "REPEATED_LOGIN_FAILURE", "BULK_EXPORT");
    private static final Set<String> RESULTS = Set.of("DETECTED", "BLOCKED", "SUCCESS", "FAILURE");

    private final SecurityEventService events;

    public SecurityEventController(SecurityEventService events) {
        this.events = events;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('audit:security-events:read')")
    public ApiResponse<SecurityEventService.SecurityEventPage> search(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        validateEventType(eventType);
        validateResult(result);
        Long scope = has(authentication, "ROLE_ADMIN") || has(authentication, "audit:security-events:all")
                ? null : Long.parseLong(authentication.getName());
        return ApiResponse.ok(events.search(
                new SecurityEventService.Search(eventType, actor, result, from, to, page, size), scope));
    }

    private static boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals(authority));
    }

    private static void validateEventType(String value) {
        if (value != null && !value.isBlank() && !EVENT_TYPES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType filter is invalid");
        }
    }

    private static void validateResult(String value) {
        if (value != null && !value.isBlank() && !RESULTS.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "result filter is invalid");
        }
    }
}
