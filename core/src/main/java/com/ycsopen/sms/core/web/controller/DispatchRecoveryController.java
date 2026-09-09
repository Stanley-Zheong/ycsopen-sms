package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.delivery.DispatchTaskRecoveryService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.delivery.ChannelRecoveryTestRequest;
import com.ycsopen.sms.core.web.dto.delivery.DispatchRecoveryRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/console/dispatch-recovery")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class DispatchRecoveryController {
    private final DispatchTaskRecoveryService recovery;

    public DispatchRecoveryController(DispatchTaskRecoveryService recovery) {
        this.recovery = recovery;
    }

    @GetMapping("/inventory")
    public ApiResponse<List<DispatchTaskRecoveryService.MigrationInventoryRow>> inventory() {
        return ApiResponse.ok(recovery.inventory());
    }

    @PostMapping("/tasks/{taskId}/migrate")
    public ApiResponse<DispatchTaskRecoveryService.RecoveryResult> migrate(@PathVariable long taskId,
                                                                           @RequestBody DispatchRecoveryRequest request,
                                                                           Authentication authentication) {
        return ApiResponse.ok(recovery.migrateReadyTask(taskId, actor(authentication), request.evidence()));
    }

    @PostMapping("/tasks/{taskId}/retry")
    public ApiResponse<DispatchTaskRecoveryService.RecoveryResult> retry(@PathVariable long taskId,
                                                                         @RequestBody DispatchRecoveryRequest request,
                                                                         Authentication authentication) {
        return ApiResponse.ok(recovery.retryFailedTask(taskId, actor(authentication), request.evidence()));
    }

    @PostMapping("/channels/{channelId}/recovery-tests")
    public ApiResponse<DispatchTaskRecoveryService.RecoveryTestResult> test(@PathVariable long channelId,
                                                                            @RequestBody ChannelRecoveryTestRequest request,
                                                                            Authentication authentication) {
        return ApiResponse.ok(recovery.recordRecoveryTest(channelId, Boolean.TRUE.equals(request.success()),
                actor(authentication), request.evidence()));
    }

    @PostMapping("/channels/{channelId}/resume")
    public ApiResponse<DispatchTaskRecoveryService.RecoveryTestResult> resume(@PathVariable long channelId,
                                                                              @RequestBody DispatchRecoveryRequest request,
                                                                              Authentication authentication) {
        return ApiResponse.ok(recovery.resumeAfterRecovery(channelId, actor(authentication), request.evidence()));
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}
