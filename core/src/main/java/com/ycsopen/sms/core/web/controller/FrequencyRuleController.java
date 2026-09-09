package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.risk.FrequencyRuleService;
import com.ycsopen.sms.core.service.risk.FrequencyRuleService.AnalyticsResponse;
import com.ycsopen.sms.core.service.risk.FrequencyRuleService.ExportRequestResponse;
import com.ycsopen.sms.core.service.risk.FrequencyRuleService.ImportRequest;
import com.ycsopen.sms.core.service.risk.FrequencyRuleService.ImportResponse;
import com.ycsopen.sms.core.service.risk.FrequencyRuleService.RuleRequest;
import com.ycsopen.sms.core.service.risk.FrequencyRuleService.RuleRow;
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
@RequestMapping("/api/v1/console/frequency")
public class FrequencyRuleController {
    private final FrequencyRuleService service;

    public FrequencyRuleController(FrequencyRuleService service) {
        this.service = service;
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('frequency:read')")
    public ApiResponse<List<RuleRow>> rules(@RequestParam(required = false) String name,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.rules(name, type, action, status));
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('frequency:write')")
    public ApiResponse<RuleRow> save(@RequestBody RuleRequest request) {
        return ApiResponse.ok(service.save(request));
    }

    @PostMapping("/rules/import")
    @PreAuthorize("hasAuthority('frequency:import')")
    public ApiResponse<ImportResponse> importRules(@RequestBody ImportRequest request) {
        return ApiResponse.ok(service.importRules(request));
    }

    @PostMapping("/rules/{id}/enable")
    @PreAuthorize("hasAuthority('frequency:write')")
    public ApiResponse<RuleRow> enable(@PathVariable long id) {
        return ApiResponse.ok(service.enable(id));
    }

    @PostMapping("/rules/{id}/disable")
    @PreAuthorize("hasAuthority('frequency:write')")
    public ApiResponse<RuleRow> disable(@PathVariable long id) {
        return ApiResponse.ok(service.disable(id));
    }

    @PostMapping("/rules/export-request")
    @PreAuthorize("hasAuthority('frequency:export')")
    public ApiResponse<ExportRequestResponse> exportRequest(@RequestParam(required = false) String name,
                                                            @RequestParam(required = false) String type,
                                                            @RequestParam(required = false) String action,
                                                            @RequestParam(required = false) String status,
                                                            Authentication authentication) {
        return ApiResponse.ok(service.exportRequest(name, type, action, status, actor(authentication)));
    }

    @GetMapping("/analytics")
    @PreAuthorize("hasAuthority('frequency:read')")
    public ApiResponse<AnalyticsResponse> analytics() {
        return ApiResponse.ok(service.analytics());
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }
}
