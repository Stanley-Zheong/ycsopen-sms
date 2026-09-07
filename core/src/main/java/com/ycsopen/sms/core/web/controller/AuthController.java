package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.web.TrustedProxyClientIpResolver;
import com.ycsopen.sms.core.service.account.AuthService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.LoginRequest;
import com.ycsopen.sms.core.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** F-1.4 控制台登录（系统管理员/运营/财务/机构用户统一入口，按 user_type 区分）。 */
@RestController
@RequestMapping("/api/v1/console/auth")
public class AuthController {

    private final AuthService authService;
    private final TrustedProxyClientIpResolver clientIps;

    public AuthController(AuthService authService, TrustedProxyClientIpResolver clientIps) {
        this.authService = authService;
        this.clientIps = clientIps;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(
                request,
                clientIps.resolve(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }
}
