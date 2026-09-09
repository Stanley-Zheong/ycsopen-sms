package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.LoginHistoryService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.LoginHistoryPageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/console/login-history")
public class LoginHistoryController {

    private final LoginHistoryService histories;

    public LoginHistoryController(LoginHistoryService histories) {
        this.histories = histories;
    }

    @GetMapping
    public ApiResponse<LoginHistoryPageResponse> query(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        long subject = Long.parseLong(authentication.getName());
        boolean administrator = hasAuthority(authentication, "ROLE_ADMIN");
        boolean broad = administrator
                || hasAuthority(authentication, "identity:history:read")
                && hasAuthority(authentication, "identity:history:all");
        if (all && !broad || userId != null && userId != subject && !broad) {
            throw new AccessDeniedException("login history scope is not allowed");
        }
        Long effectiveUserId = all ? null : userId == null ? subject : userId;
        return ApiResponse.ok(histories.query(effectiveUserId, page, size));
    }

    private static boolean hasAuthority(Authentication authentication, String required) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> required.equals(authority.getAuthority()));
    }
}
