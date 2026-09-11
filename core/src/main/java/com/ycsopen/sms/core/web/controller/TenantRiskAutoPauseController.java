package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.risk.TenantRiskAutoPauseService;
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

/** Phase42 tenant risk warning and auto-pause API. */
@RestController
@RequestMapping("/api/v1/console/tenant-risk")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
public class TenantRiskAutoPauseController {
    private final TenantRiskAutoPauseService service;

    public TenantRiskAutoPauseController(TenantRiskAutoPauseService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    public ApiResponse<List<TenantRiskAutoPauseService.RuleRow>> rules(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(service.rules(tenantId));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<TenantRiskAutoPauseService.RuleRow> saveRule(
            @RequestBody TenantRiskAutoPauseService.RuleCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.saveRule(command, actor(authentication)));
    }

    @PostMapping("/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<TenantRiskAutoPauseService.EvaluationResult> evaluate(
            @RequestBody TenantRiskAutoPauseService.EvaluationCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.evaluate(command, actor(authentication)));
    }

    @GetMapping("/episodes")
    public ApiResponse<List<TenantRiskAutoPauseService.EpisodeRow>> episodes(@RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(service.episodes(tenantId));
    }

    @PostMapping("/episodes/{id}/recover")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<TenantRiskAutoPauseService.EpisodeRow> recover(
            @PathVariable long id,
            @RequestBody TenantRiskAutoPauseService.RecoveryCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.recover(id, command, actor(authentication)));
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "console";
        }
        return authentication.getName().trim();
    }
}
