package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.message.MessageSubmitService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.SmsSendRequest;
import com.ycsopen.sms.core.web.dto.SmsSendResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Phase 26: JWT-authenticated tenant console send adapter over the proven acceptance pipeline. */
@RestController
public class TenantConsoleSendController {
    private final MessageSubmitService messageSubmitService;
    private final UserRepository users;

    public TenantConsoleSendController(MessageSubmitService messageSubmitService, UserRepository users) {
        this.messageSubmitService = messageSubmitService;
        this.users = users;
    }

    @PostMapping("/api/v1/console/tenant/send")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_USER') or hasRole('TENANT_DEV')")
    public ApiResponse<SmsSendResponse> send(@Valid @RequestBody SmsSendRequest request,
                                             Authentication authentication,
                                             HttpServletRequest httpRequest) {
        long tenantId = tenantIdFor(authentication);
        String clientIp = httpRequest == null ? null : httpRequest.getRemoteAddr();
        return ApiResponse.ok(messageSubmitService.submit(tenantId, null, request, clientIp));
    }

    private long tenantIdFor(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        var user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        if (user.getTenantId() == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文不能为空");
        }
        return user.getTenantId();
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }
}
