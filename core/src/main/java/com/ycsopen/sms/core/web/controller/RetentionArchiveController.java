package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.ArchiveCommand;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.ArchiveManifest;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.ArchivePolicy;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.ArchiveSearch;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.PolicyCommand;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.RestoreCommand;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.RestoreJob;
import com.ycsopen.sms.core.service.archive.RetentionArchiveService.ScanCommand;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/console/archive")
public class RetentionArchiveController {
    private final RetentionArchiveService archive;

    public RetentionArchiveController(RetentionArchiveService archive) {
        this.archive = archive;
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('retention-archive:read')")
    public ApiResponse<List<ArchivePolicy>> policies() {
        return ApiResponse.ok(archive.policies());
    }

    @PutMapping("/policies/{dataDomain}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('retention-archive:write')")
    public ApiResponse<ArchivePolicy> savePolicy(@PathVariable String dataDomain,
                                                  @RequestBody PolicyRequest request,
                                                  Authentication authentication) {
        PolicyRequest checked = request == null ? new PolicyRequest(730, 3, null) : request;
        return ApiResponse.ok(archive.savePolicy(new PolicyCommand(dataDomain, checked.retentionDays(),
                checked.hotMonths(), checked.legalHoldUntil(), actor(authentication))));
    }

    @GetMapping("/manifests")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('retention-archive:read')")
    public ApiResponse<List<ArchiveManifest>> manifests(@RequestParam(required = false) String dataDomain,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) Long tenantId) {
        return ApiResponse.ok(archive.search(new ArchiveSearch(dataDomain, status, tenantId)));
    }

    @PostMapping("/manifests")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('retention-archive:write')")
    public ApiResponse<ArchiveManifest> archive(@RequestBody ArchiveRequest request, Authentication authentication) {
        ArchiveRequest checked = request == null
                ? new ArchiveRequest("MESSAGE_TASKS", null, null, List.of(), false)
                : request;
        return ApiResponse.ok(archive.archive(new ArchiveCommand(checked.dataDomain(), checked.tenantId(),
                checked.partitionKey(), checked.rows(), checked.forceFailure(), actor(authentication))));
    }

    @PostMapping("/manifests/scan")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('retention-archive:write')")
    public ApiResponse<ArchiveManifest> scan(@RequestBody ScanRequest request, Authentication authentication) {
        ScanRequest checked = request == null ? new ScanRequest("MESSAGE_TASKS", null) : request;
        return ApiResponse.ok(archive.archiveEligible(new ScanCommand(checked.dataDomain(), checked.tenantId(),
                actor(authentication))));
    }

    @PostMapping("/manifests/{id}/verify")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('retention-archive:verify')")
    public ApiResponse<ArchiveManifest> verify(@PathVariable long id) {
        return ApiResponse.ok(archive.verify(id));
    }

    @PostMapping("/manifests/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('retention-archive:restore')")
    public ApiResponse<RestoreJob> restore(@PathVariable long id, Authentication authentication) {
        return ApiResponse.ok(archive.restore(id, new RestoreCommand(actor(authentication))));
    }

    @PostMapping("/manifests/{id}/export")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','FINANCE') or hasAuthority('retention-archive:export')")
    public ApiResponse<RestoreJob> export(@PathVariable long id, Authentication authentication) {
        return ApiResponse.ok(archive.export(id, new RestoreCommand(actor(authentication))));
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName();
    }

    public record PolicyRequest(Integer retentionDays, Integer hotMonths, LocalDateTime legalHoldUntil) { }

    public record ArchiveRequest(String dataDomain, Long tenantId, String partitionKey,
                                 List<Map<String, Object>> rows, Boolean forceFailure) { }

    public record ScanRequest(String dataDomain, Long tenantId) { }
}
