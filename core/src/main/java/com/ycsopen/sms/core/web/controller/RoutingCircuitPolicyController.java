package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.CircuitState;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.PolicyImportRequest;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.PolicyImportResponse;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.PolicyRuleView;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.PolicyVersion;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.RetryPolicy;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.SimulationRequest;
import com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService.SimulationResult;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/console/routing-policy")
public class RoutingCircuitPolicyController {
    private final RoutingCircuitPolicyService service;

    public RoutingCircuitPolicyController(RoutingCircuitPolicyService service) {
        this.service = service;
    }

    @GetMapping("/versions")
    @PreAuthorize("hasAuthority('routing-policy:read')")
    public ApiResponse<List<PolicyVersion>> versions() {
        return ApiResponse.ok(service.versions());
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('routing-policy:read')")
    public ApiResponse<List<PolicyRuleView>> rules() {
        return ApiResponse.ok(service.rules());
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('routing-policy:import')")
    public ApiResponse<PolicyImportResponse> importPolicy(@RequestBody PolicyImportRequest request,
                                                          Authentication authentication) {
        return ApiResponse.ok(service.importPolicy(request, actor(authentication)));
    }

    @PostMapping("/simulate")
    @PreAuthorize("hasAuthority('routing-policy:read')")
    public ApiResponse<SimulationResult> simulate(@RequestBody SimulationRequest request) {
        return ApiResponse.ok(service.simulate(request));
    }

    @GetMapping("/circuits")
    @PreAuthorize("hasAuthority('routing-policy:read')")
    public ApiResponse<List<CircuitState>> circuits() {
        return ApiResponse.ok(service.circuitStates());
    }

    @PostMapping("/circuits/{channelCode}/record")
    @PreAuthorize("hasAuthority('routing-policy:write')")
    public ApiResponse<CircuitState> recordCircuit(@PathVariable String channelCode,
                                                   @RequestParam boolean success,
                                                   @RequestParam(defaultValue = "0") int latencyMs) {
        return ApiResponse.ok(service.recordCircuit(channelCode, success, latencyMs));
    }

    @GetMapping("/retry/{category}")
    @PreAuthorize("hasAuthority('routing-policy:read')")
    public ApiResponse<RetryPolicy> retryPolicy(@PathVariable String category) {
        return ApiResponse.ok(service.retryPolicy(category));
    }

    @PostMapping("/retry")
    @PreAuthorize("hasAuthority('routing-policy:write')")
    public ApiResponse<RetryPolicy> saveRetryPolicy(@RequestBody RetryPolicy request) {
        return ApiResponse.ok(service.saveRetryPolicy(request));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }
}
