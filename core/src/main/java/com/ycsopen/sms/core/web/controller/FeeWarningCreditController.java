package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.service.billing.FeeWarningCreditService;
import com.ycsopen.sms.core.service.billing.FeeWarningCreditService.EpisodeRow;
import com.ycsopen.sms.core.service.billing.FeeWarningCreditService.RuleCommand;
import com.ycsopen.sms.core.service.billing.FeeWarningCreditService.RuleRow;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class FeeWarningCreditController {
    private final FeeWarningCreditService service;
    private final UserRepository users;

    public FeeWarningCreditController(FeeWarningCreditService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/fee-warnings/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<List<RuleRow>> rules(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(service.rules(tenantId));
    }

    @PostMapping("/api/v1/console/fee-warnings/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<RuleRow> saveRule(@RequestBody RuleCommand command, Authentication authentication) {
        return ApiResponse.ok(service.saveRule(command, actor(authentication)));
    }

    @PostMapping("/api/v1/console/fee-warnings/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<List<EpisodeRow>> evaluate(@RequestBody EvaluationRequest request, Authentication authentication) {
        return ApiResponse.ok(service.evaluateTenant(request.tenantId(), request.estimatedAmountMil(), actor(authentication)));
    }

    @GetMapping("/api/v1/console/fee-warnings/episodes")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<List<EpisodeRow>> episodes(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(service.episodes(tenantId));
    }

    @PostMapping("/api/v1/console/fee-warnings/episodes/{episodeId}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ApiResponse<EpisodeRow> approve(@PathVariable long episodeId, @RequestBody ApprovalRequest request,
                                           Authentication authentication) {
        return ApiResponse.ok(service.approveEpisode(episodeId, request.reason(), actor(authentication)));
    }

    @GetMapping("/api/v1/tenant/fee-warnings/episodes")
    @PreAuthorize("hasAuthority('trial-prepaid:read')")
    public ApiResponse<List<EpisodeRow>> tenantEpisodes(Authentication authentication) {
        return ApiResponse.ok(service.episodes(ownTenantId(authentication)));
    }

    private long ownTenantId(Authentication authentication) {
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

    public record EvaluationRequest(long tenantId, long estimatedAmountMil) { }
    public record ApprovalRequest(String reason) { }
}
