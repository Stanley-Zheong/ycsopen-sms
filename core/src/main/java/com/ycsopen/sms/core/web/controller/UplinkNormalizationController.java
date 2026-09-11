package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService.AutoReplyCommand;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService.AutoReplyConfig;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService.PushMonitorFilter;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService.PushMonitorRow;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService.SearchFilter;
import com.ycsopen.sms.core.service.uplink.UplinkNormalizationService.UplinkRecord;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.DeliveryResult;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class UplinkNormalizationController {
    private final UplinkNormalizationService service;
    private final UserRepository users;

    public UplinkNormalizationController(UplinkNormalizationService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/uplinks")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<UplinkRecord>> adminSearch(@RequestParam(required = false) Long tenantId,
                                                       @RequestParam(required = false) String phoneNumber,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) String carrier,
                                                       @RequestParam(required = false) String pushState,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                       LocalDateTime startTime,
                                                       @RequestParam(required = false)
                                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                       LocalDateTime endTime) {
        return ApiResponse.ok(service.adminSearch(new SearchFilter(tenantId, phoneNumber, keyword, carrier,
                pushState, startTime, endTime)));
    }

    @GetMapping("/api/v1/console/uplinks/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<UplinkRecord> detail(@PathVariable long id) {
        return ApiResponse.ok(service.detail(id, null));
    }

    @PostMapping("/api/v1/console/uplinks/{id}/replay")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DeliveryResult> replayUplink(@PathVariable long id, @RequestBody ActionRequest request,
                                                    Authentication authentication) {
        return ApiResponse.ok(service.replayUplink(id, actor(authentication), request.reason()));
    }

    @GetMapping("/api/v1/console/uplinks/push-monitor")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<PushMonitorRow>> pushMonitor(@RequestParam(required = false) Long tenantId,
                                                         @RequestParam(required = false) String state,
                                                         @RequestParam(required = false) String destination) {
        return ApiResponse.ok(service.pushMonitor(new PushMonitorFilter(tenantId, state, destination)));
    }

    @PostMapping("/api/v1/console/uplinks/push-monitor/{eventId}/replay")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DeliveryResult> replayPushEvent(@PathVariable long eventId, @RequestBody ActionRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(service.replayPushEvent(eventId, actor(authentication), request.reason()));
    }

    @PostMapping("/api/v1/console/uplinks/push-monitor/{eventId}/pause")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DeliveryResult> pausePushEvent(@PathVariable long eventId, @RequestBody ActionRequest request,
                                                      Authentication authentication) {
        return ApiResponse.ok(service.pausePushEvent(eventId, actor(authentication), request.reason()));
    }

    @PostMapping("/api/v1/console/uplinks/push-monitor/{eventId}/resume")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<DeliveryResult> resumePushEvent(@PathVariable long eventId, @RequestBody ActionRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(service.resumePushEvent(eventId, actor(authentication), request.reason()));
    }

    @GetMapping("/api/v1/console/tenant/uplinks")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<List<UplinkRecord>> tenantSearch(@RequestParam(required = false) String phoneNumber,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String carrier,
                                                        @RequestParam(required = false) String pushState,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                        LocalDateTime startTime,
                                                        @RequestParam(required = false)
                                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                        LocalDateTime endTime,
                                                        Authentication authentication) {
        return ApiResponse.ok(service.tenantSearch(tenantIdFor(authentication),
                new SearchFilter(null, phoneNumber, keyword, carrier, pushState, startTime, endTime)));
    }

    @GetMapping("/api/v1/console/tenant/uplinks/auto-reply")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<AutoReplyConfig> autoReplyConfig(Authentication authentication) {
        return ApiResponse.ok(service.autoReplyConfig(tenantIdFor(authentication)));
    }

    @PutMapping("/api/v1/console/tenant/uplinks/auto-reply")
    @PreAuthorize("hasRole('TENANT_ADMIN') or hasRole('TENANT_DEV')")
    public ApiResponse<AutoReplyConfig> saveAutoReplyConfig(@RequestBody AutoReplyCommand request,
                                                            Authentication authentication) {
        return ApiResponse.ok(service.saveAutoReplyConfig(tenantIdFor(authentication), request, actor(authentication)));
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

    public record ActionRequest(String reason) { }
}
