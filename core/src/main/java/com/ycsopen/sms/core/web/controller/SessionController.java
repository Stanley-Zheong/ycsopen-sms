package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.IdentitySessionService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated session lifecycle operations. */
@RestController
@RequestMapping("/api/v1/console/session")
public class SessionController {
    private final IdentitySessionService sessions;

    public SessionController(IdentitySessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(Authentication authentication) {
        sessions.revoke(String.valueOf(authentication.getDetails()),
                Long.parseLong(authentication.getName()));
        return ApiResponse.ok(null);
    }
}
