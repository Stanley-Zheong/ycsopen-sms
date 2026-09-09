package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.CallbackConfig;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.CallbackConfigCommand;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.CallbackType;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.DeliveryResult;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.FailureRow;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.TestResult;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class WebhookDeliveryTransportController {
    private final WebhookDeliveryTransportService service;
    private final UserRepository users;

    public WebhookDeliveryTransportController(WebhookDeliveryTransportService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/tenant/webhooks")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<CallbackConfig> tenantConfig(Authentication authentication) {
        return ApiResponse.ok(service.config(tenantIdFor(authentication)));
    }

    @PutMapping("/api/v1/console/tenant/webhooks")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<CallbackConfig> saveTenantConfig(@RequestBody CallbackConfigRequest request,
                                                        Authentication authentication) {
        return ApiResponse.ok(service.saveConfig(tenantIdFor(authentication), new CallbackConfigCommand(
                request.statusCallbackUrl(), request.uplinkCallbackUrl(), request.unsubscribeCallbackUrl(),
                request.retryMaxCount(), request.retryBackoffSeconds())));
    }

    @PostMapping("/api/v1/console/tenant/webhooks/test")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<TestResult> testTenantConfig(@RequestBody CallbackTestRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(service.testDestination(tenantIdFor(authentication), request.type(), request.destinationUrl()));
    }

    @GetMapping("/api/v1/console/webhook-deliveries/failures")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<FailureRow>> failures(@RequestParam(required = false) Long tenantId,
                                                  @RequestParam(required = false) String state) {
        return ApiResponse.ok(service.failures(tenantId, state));
    }

    @PostMapping("/api/v1/console/webhook-deliveries/{eventId}/replay")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DeliveryResult> replay(@PathVariable long eventId,
                                              @RequestBody WebhookActionRequest request,
                                              Authentication authentication) {
        return ApiResponse.ok(service.replay(eventId, actor(authentication), request.reason()));
    }

    @PostMapping("/api/v1/console/webhook-deliveries/{eventId}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DeliveryResult> pause(@PathVariable long eventId,
                                             @RequestBody WebhookActionRequest request,
                                             Authentication authentication) {
        return ApiResponse.ok(service.pause(eventId, actor(authentication), request.reason()));
    }

    @PostMapping("/api/v1/console/webhook-deliveries/{eventId}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DeliveryResult> resume(@PathVariable long eventId,
                                              @RequestBody WebhookActionRequest request,
                                              Authentication authentication) {
        return ApiResponse.ok(service.resume(eventId, actor(authentication), request.reason()));
    }

    private long tenantIdFor(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ex) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        if (user.getTenantId() == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文不能为空");
        }
        return user.getTenantId();
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }

    public record CallbackConfigRequest(String statusCallbackUrl, String uplinkCallbackUrl,
                                        String unsubscribeCallbackUrl, Integer retryMaxCount,
                                        Integer retryBackoffSeconds) { }
    public record CallbackTestRequest(CallbackType type, String destinationUrl) { }
    public record WebhookActionRequest(String reason) { }
}
