package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.service.channel.health.ChannelCandidateEligibilityService;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthService;
import com.ycsopen.sms.core.service.channel.health.ChannelPoolService;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/console/channel-health")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ChannelHealthController {
    private final ChannelHealthService health;
    private final ChannelPoolService pools;
    private final ChannelCandidateEligibilityService eligibility;
    private final ChannelRepository channels;

    public ChannelHealthController(ChannelHealthService health, ChannelPoolService pools,
                                   ChannelCandidateEligibilityService eligibility, ChannelRepository channels) {
        this.health = health;
        this.pools = pools;
        this.eligibility = eligibility;
        this.channels = channels;
    }

    @GetMapping("/monitor")
    public ApiResponse<List<ChannelHealthMonitorResponse>> monitor() {
        return ApiResponse.ok(health.monitor());
    }

    @PostMapping("/channels/{id}/observations")
    public ApiResponse<ChannelHealthMonitorResponse> recordObservation(@PathVariable long id,
                                                                       @RequestBody ChannelHealthObservationRequest request) {
        return ApiResponse.ok(health.recordObservation(id, request));
    }

    @PostMapping("/channels/{id}/pause")
    public ApiResponse<ChannelHealthMonitorResponse> pause(@PathVariable long id, @RequestBody ChannelPauseRequest request,
                                                           Authentication authentication) {
        return ApiResponse.ok(health.pause(id, withAuthenticatedActor(request, authentication)));
    }

    @PostMapping("/channels/{id}/maintenance/start")
    public ApiResponse<ChannelHealthMonitorResponse> startMaintenance(@PathVariable long id,
                                                                      @RequestBody ChannelPauseRequest request,
                                                                      Authentication authentication) {
        return ApiResponse.ok(health.startMaintenance(id, withAuthenticatedActor(request, authentication)));
    }

    @PostMapping("/channels/{id}/maintenance/end")
    public ApiResponse<ChannelHealthMonitorResponse> endMaintenance(@PathVariable long id,
                                                                    @RequestBody ChannelPauseRequest request,
                                                                    Authentication authentication) {
        return ApiResponse.ok(health.endMaintenance(id, withAuthenticatedActor(request, authentication)));
    }

    @GetMapping("/channels/{id}/candidate")
    public ApiResponse<ChannelCandidateResponse> candidate(@PathVariable long id) {
        var channel = channels.findById(id).orElse(null);
        var decision = eligibility.evaluate(channel);
        return ApiResponse.ok(new ChannelCandidateResponse(id, decision.eligible(), decision.reasonCode()));
    }

    @GetMapping("/pools")
    public ApiResponse<List<ChannelPoolResponse>> pools() {
        return ApiResponse.ok(pools.list());
    }

    @PostMapping("/pools")
    public ApiResponse<ChannelPoolResponse> createPool(@RequestBody ChannelPoolRequest request) {
        return ApiResponse.ok(pools.create(request));
    }

    @PutMapping("/pools/{id}")
    public ApiResponse<ChannelPoolResponse> updatePool(@PathVariable long id, @RequestBody ChannelPoolRequest request) {
        return ApiResponse.ok(pools.update(id, request));
    }

    private static ChannelPauseRequest withAuthenticatedActor(ChannelPauseRequest request, Authentication authentication) {
        String actor = actor(authentication);
        if (request == null) {
            return new ChannelPauseRequest(null, actor, null);
        }
        return new ChannelPauseRequest(request.trigger(), actor, request.reason());
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().trim().isEmpty()) {
            throw new BusinessException("CHANNEL_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }
}
