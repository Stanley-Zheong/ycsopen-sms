package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.complaint.ComplaintCaseService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phase41 complaint case management API. */
@RestController
@RequestMapping("/api/v1/console")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'FINANCE')")
public class ComplaintCaseController {
    private final ComplaintCaseService service;

    public ComplaintCaseController(ComplaintCaseService service) {
        this.service = service;
    }

    @GetMapping("/complaints")
    public ApiResponse<List<ComplaintCaseService.CaseRow>> cases() {
        return ApiResponse.ok(service.cases());
    }

    @PostMapping("/complaints")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ComplaintCaseService.CaseRow> create(
            @RequestBody ComplaintCaseService.CreateCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.create(command, actor(authentication)));
    }

    @PostMapping("/complaints/{id}/accept")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ComplaintCaseService.CaseRow> accept(
            @PathVariable long id, @RequestBody ComplaintCaseService.StateCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.accept(id, stateWithActor(command, authentication)));
    }

    @PostMapping("/complaints/{id}/handle")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ComplaintCaseService.CaseRow> handle(
            @PathVariable long id, @RequestBody ComplaintCaseService.StateCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.handle(id, stateWithActor(command, authentication)));
    }

    @PostMapping("/complaints/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ComplaintCaseService.CaseRow> close(
            @PathVariable long id, @RequestBody ComplaintCaseService.StateCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.close(id, stateWithActor(command, authentication)));
    }

    @PostMapping("/complaints/{id}/remediations")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ComplaintCaseService.RemediationRow> remediate(
            @PathVariable long id, @RequestBody ComplaintCaseService.RemediationCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.remediate(id, remediationWithActor(command, authentication)));
    }

    @PostMapping("/complaints/{id}/recoveries")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    public ApiResponse<ComplaintCaseService.RemediationRow> recover(
            @PathVariable long id, @RequestBody ComplaintCaseService.RecoveryCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.recover(id, recoveryWithActor(command, authentication)));
    }

    @GetMapping("/complaint-analytics")
    public ApiResponse<ComplaintCaseService.AnalyticsResponse> analytics() {
        return ApiResponse.ok(service.analytics());
    }

    private static ComplaintCaseService.StateCommand stateWithActor(
            ComplaintCaseService.StateCommand command, Authentication authentication) {
        return new ComplaintCaseService.StateCommand(
                command == null ? null : command.status(),
                command == null ? null : command.opinion(),
                command == null ? null : command.remediation(),
                command == null ? null : command.requirement(),
                actor(authentication));
    }

    private static ComplaintCaseService.RemediationCommand remediationWithActor(
            ComplaintCaseService.RemediationCommand command, Authentication authentication) {
        return new ComplaintCaseService.RemediationCommand(
                command == null ? null : command.disposalType(),
                command == null ? null : command.targetRef(),
                actor(authentication),
                command == null ? null : command.authorizedReviewId(),
                command == null ? null : command.reason());
    }

    private static ComplaintCaseService.RecoveryCommand recoveryWithActor(
            ComplaintCaseService.RecoveryCommand command, Authentication authentication) {
        return new ComplaintCaseService.RecoveryCommand(
                command == null ? 0L : command.disposalRecordId(),
                command == null ? null : command.authorizedReviewId(),
                actor(authentication),
                command == null ? null : command.resumeCondition());
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "console";
        }
        return authentication.getName().trim();
    }
}
