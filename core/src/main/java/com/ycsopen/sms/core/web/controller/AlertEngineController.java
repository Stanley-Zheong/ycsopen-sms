package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.alert.AlertEngineService;
import com.ycsopen.sms.core.service.alert.AlertEngineService.AlertRow;
import com.ycsopen.sms.core.service.alert.AlertEngineService.Dashboard;
import com.ycsopen.sms.core.service.alert.AlertEngineService.DeliveryAttemptRow;
import com.ycsopen.sms.core.service.alert.AlertEngineService.EvaluationResult;
import com.ycsopen.sms.core.service.alert.AlertEngineService.HistoryFilter;
import com.ycsopen.sms.core.service.alert.AlertEngineService.MuteCommand;
import com.ycsopen.sms.core.service.alert.AlertEngineService.MuteRow;
import com.ycsopen.sms.core.service.alert.AlertEngineService.ResolveCommand;
import com.ycsopen.sms.core.service.alert.AlertEngineService.RuleCommand;
import com.ycsopen.sms.core.service.alert.AlertEngineService.RuleRow;
import com.ycsopen.sms.core.service.alert.AlertEngineService.SourceEvent;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AlertEngineController {
    private final AlertEngineService service;

    public AlertEngineController(AlertEngineService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/console/alerts/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
    public ApiResponse<Dashboard> dashboard() {
        return ApiResponse.ok(service.dashboard());
    }

    @GetMapping("/api/v1/console/alerts/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<List<RuleRow>> rules() {
        return ApiResponse.ok(service.rules());
    }

    @PostMapping("/api/v1/console/alerts/rules")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<RuleRow> saveRule(@RequestBody RuleCommand command, Authentication authentication) {
        return ApiResponse.ok(service.saveRule(command, actor(authentication)));
    }

    @PostMapping("/api/v1/console/alerts/evaluate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<EvaluationResult> evaluate(@RequestBody SourceEvent event, Authentication authentication) {
        return ApiResponse.ok(service.evaluate(event, actor(authentication)));
    }

    @GetMapping("/api/v1/console/alerts/history")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
    public ApiResponse<List<AlertRow>> history(@RequestParam(required = false) String status,
                                               @RequestParam(required = false) String severity) {
        return ApiResponse.ok(service.history(new HistoryFilter(status, severity)));
    }

    @GetMapping("/api/v1/console/alerts/deliveries")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
    public ApiResponse<List<DeliveryAttemptRow>> deliveries(@RequestParam(required = false) Long alertId) {
        return ApiResponse.ok(service.deliveries(alertId));
    }

    @PostMapping("/api/v1/console/alerts/{alertId}/acknowledge")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<AlertRow> acknowledge(@PathVariable long alertId, Authentication authentication) {
        return ApiResponse.ok(service.acknowledge(alertId, actor(authentication)));
    }

    @PostMapping("/api/v1/console/alerts/{alertId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<AlertRow> resolve(@PathVariable long alertId, @RequestBody ResolveCommand command,
                                         Authentication authentication) {
        return ApiResponse.ok(service.resolve(alertId, command, actor(authentication)));
    }

    @PostMapping("/api/v1/console/alerts/{alertId}/mute")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<MuteRow> mute(@PathVariable long alertId, @RequestBody MuteCommand command,
                                     Authentication authentication) {
        return ApiResponse.ok(service.mute(alertId, command, actor(authentication)));
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }
}
