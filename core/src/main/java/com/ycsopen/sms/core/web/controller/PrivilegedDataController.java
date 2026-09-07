package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.PrivilegedDataService;
import com.ycsopen.sms.core.common.web.TrustedProxyClientIpResolver;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.MDC;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicit no-store reveal API for the allowlisted platform-account phone field. */
@RestController
@RequestMapping("/api/v1/console/platform-accounts")
public class PrivilegedDataController {

    private final PrivilegedDataService privilegedData;
    private final TrustedProxyClientIpResolver clientIps;

    public PrivilegedDataController(PrivilegedDataService privilegedData,
                                    TrustedProxyClientIpResolver clientIps) {
        this.privilegedData = privilegedData;
        this.clientIps = clientIps;
    }

    @PostMapping("/{userId}/phone/reveal")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('privileged:data:reveal:api') and hasAuthority('identity:accounts:all'))")
    public ResponseEntity<ApiResponse<PrivilegedDataService.RevealedValue>> revealPhone(
            @PathVariable long userId, @Valid @RequestBody RevealRequest request,
            Authentication authentication, HttpServletRequest servletRequest) {
        var value = privilegedData.revealPhone(userId, Long.parseLong(authentication.getName()),
                request.purpose(), clientIps.resolve(servletRequest), MDC.get("traceId"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(ApiResponse.ok(value));
    }

    public record RevealRequest(@NotNull PrivilegedDataService.RevealPurpose purpose) { }
}
