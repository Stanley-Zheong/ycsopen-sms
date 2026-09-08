package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.channel.ChannelConfigurationService;
import com.ycsopen.sms.core.service.channel.ChannelConfigurationVersionService;
import com.ycsopen.sms.core.service.channel.ChannelDependencyInventoryService;
import com.ycsopen.sms.core.service.channel.ChannelRetirementService;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/console/channels/configuration")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ChannelConfigurationController {
    private final ChannelConfigurationService configurations;
    private final ChannelConfigurationVersionService versions;
    private final ChannelDependencyInventoryService dependencies;
    private final ChannelRetirementService retirements;

    public ChannelConfigurationController(ChannelConfigurationService configurations,
                                          ChannelConfigurationVersionService versions,
                                          ChannelDependencyInventoryService dependencies,
                                          ChannelRetirementService retirements) {
        this.configurations = configurations;
        this.versions = versions;
        this.dependencies = dependencies;
        this.retirements = retirements;
    }

    @GetMapping
    public ApiResponse<List<ChannelConfigurationResponse>> list() {
        return ApiResponse.ok(configurations.list());
    }

    @PostMapping
    public ApiResponse<ChannelConfigurationResponse> create(@RequestBody ChannelConfigurationRequest request,
                                                            Authentication authentication) {
        return ApiResponse.ok(configurations.create(actorId(authentication), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChannelConfigurationResponse> update(@PathVariable long id,
                                                            @RequestBody ChannelConfigurationRequest request,
                                                            Authentication authentication) {
        return ApiResponse.ok(configurations.update(actorId(authentication), id, request));
    }

    @PostMapping("/{id}/connectivity-test")
    public ApiResponse<ChannelConnectivityResponse> testConnectivity(@PathVariable long id) {
        return ApiResponse.ok(configurations.testConnectivity(id));
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<ChannelActivationResponse> activate(@PathVariable long id,
                                                           @RequestBody(required = false) ChannelConfigurationRequest request,
                                                           Authentication authentication) {
        Long expected = request == null ? null : request.expectedEffectiveVersion();
        return ApiResponse.ok(versions.activate(actorId(authentication), id, expected));
    }

    @PostMapping("/{id}/versions/{versionId}/retry")
    public ApiResponse<ChannelActivationResponse> retry(@PathVariable long id,
                                                        @PathVariable long versionId,
                                                        Authentication authentication) {
        return ApiResponse.ok(versions.retry(actorId(authentication), id, versionId));
    }

    @PostMapping("/{id}/versions/{versionId}/rollback")
    public ApiResponse<ChannelActivationResponse> rollback(@PathVariable long id,
                                                           @PathVariable long versionId,
                                                           Authentication authentication) {
        return ApiResponse.ok(versions.rollback(actorId(authentication), id, versionId));
    }

    @GetMapping("/{id}/dependencies")
    public ApiResponse<List<ChannelDependencyResponse>> dependencies(@PathVariable long id) {
        return ApiResponse.ok(dependencies.inventory(id));
    }

    @PostMapping("/{id}/dependencies/migrate")
    public ApiResponse<List<ChannelDependencyResponse>> migrate(@PathVariable long id,
                                                                @RequestBody ChannelDependencyMigrationRequest request,
                                                                Authentication authentication) {
        return ApiResponse.ok(retirements.migrate(actorId(authentication), id, request));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<ChannelOfflineResponse> offline(@PathVariable long id,
                                                       Authentication authentication) {
        return ApiResponse.ok(retirements.offline(actorId(authentication), id));
    }

    private static long actorId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
