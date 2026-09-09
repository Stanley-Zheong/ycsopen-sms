package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.configuration.PlatformConfigurationService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Administration API for typed platform configuration versions. */
@RestController
@RequestMapping("/api/v1/console/system-configuration")
public class PlatformConfigurationController {
    private final PlatformConfigurationService service;

    public PlatformConfigurationController(PlatformConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:configuration:read')")
    public ApiResponse<PlatformConfigurationService.ConfigurationView> view() {
        return ApiResponse.ok(service.view());
    }

    @PostMapping("/versions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:configuration:write')")
    public ApiResponse<Long> stage(@Valid @RequestBody StageRequest request,
                                   Authentication authentication) {
        return ApiResponse.ok(service.stage(new PlatformConfigurationService.StageCommand(
                request.expectedActiveVersion(), request.changes(), request.reason(),
                actor(authentication))));
    }

    @PostMapping("/versions/{versionId}/activate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:configuration:activate')")
    public ApiResponse<PlatformConfigurationService.Activation> activate(
            @PathVariable long versionId,
            @Valid @RequestBody ActivationRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.activate(new PlatformConfigurationService.ActivateCommand(
                versionId, request.expectedActiveVersion(), request.reason(), actor(authentication))));
    }

    @PostMapping("/versions/{versionId}/rollback")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('system:configuration:activate')")
    public ApiResponse<PlatformConfigurationService.Activation> rollback(
            @PathVariable long versionId,
            @Valid @RequestBody ActivationRequest request,
            Authentication authentication) {
        return ApiResponse.ok(service.rollback(new PlatformConfigurationService.RollbackCommand(
                versionId, request.expectedActiveVersion(), request.reason(), actor(authentication))));
    }

    @ExceptionHandler(PlatformConfigurationService.Failure.class)
    public ResponseEntity<ApiResponse<FailureView>> failure(PlatformConfigurationService.Failure failure) {
        HttpStatus status = switch (failure.code()) {
            case INVALID_CONFIGURATION, INVALID_REASON, NO_CHANGES -> HttpStatus.BAD_REQUEST;
            case VERSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case STALE_VERSION, VERSION_STATE_INVALID, RELOAD_REJECTED -> HttpStatus.CONFLICT;
        };
        String message = switch (failure.code()) {
            case INVALID_CONFIGURATION -> "配置值不合法";
            case INVALID_REASON -> "变更原因不合法";
            case NO_CHANGES -> "没有可保存的配置变更";
            case STALE_VERSION -> "配置已被其他管理员更新，请刷新后重试";
            case VERSION_NOT_FOUND -> "配置版本不存在";
            case VERSION_STATE_INVALID -> "配置版本当前不可执行该操作";
            case RELOAD_REJECTED -> "运行时拒绝应用该配置，当前版本保持不变";
        };
        return ResponseEntity.status(status).body(ApiResponse.error(
                status.value(), message, new FailureView(failure.code().name())));
    }

    private static long actor(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    public record StageRequest(@PositiveOrZero long expectedActiveVersion,
                               @NotEmpty Map<String, String> changes,
                               @NotBlank @Size(max = 256) String reason) {
    }

    public record ActivationRequest(@PositiveOrZero long expectedActiveVersion,
                                    @NotBlank @Size(max = 256) String reason) {
    }

    public record FailureView(String errorCode) {
    }
}
