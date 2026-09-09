package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.RoleAdministrationService;
import com.ycsopen.sms.core.service.account.RoleAdministrationService.AccountOverview;
import com.ycsopen.sms.core.service.account.RoleAdministrationService.PermissionSummary;
import com.ycsopen.sms.core.service.account.RoleAdministrationService.RoleSummary;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform role, four-granularity permission, and current-account APIs for F-1.2/F-1.5. */
@RestController
@RequestMapping("/api/v1/console")
public class PlatformRoleController {
    private final RoleAdministrationService roles;

    public PlatformRoleController(RoleAdministrationService roles) {
        this.roles = roles;
    }

    @GetMapping("/platform-roles")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:menu') and hasAuthority('identity:roles:read'))")
    public ApiResponse<List<RoleSummary>> roles() {
        return ApiResponse.ok(roles.listPlatformRoles());
    }

    @PostMapping("/platform-roles")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('identity:roles:create:api')")
    public ApiResponse<Long> create(@Valid @RequestBody RoleCreateRequest request,
                                    Authentication authentication) {
        return ApiResponse.ok(roles.createPlatformRole(
                request.code(), request.name(), request.description(), actorId(authentication)));
    }

    @PutMapping("/platform-roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('identity:roles:update:api')")
    public ApiResponse<Void> update(@PathVariable long roleId, @Valid @RequestBody RoleUpdateRequest request,
                                    Authentication authentication) {
        roles.updatePlatformRole(roleId, request.code(), request.name(), request.description(), request.status(),
                actorId(authentication));
        return ApiResponse.ok(null);
    }

    @PutMapping("/platform-roles/{roleId}/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('identity:roles:grant:api')")
    public ApiResponse<Void> permissions(@PathVariable long roleId,
                                         @Valid @RequestBody PermissionAssignmentRequest request,
                                         Authentication authentication) {
        roles.replacePermissions(roleId, request.permissionIds(), actorId(authentication));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/platform-roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('identity:roles:delete:api')")
    public ApiResponse<Void> delete(@PathVariable long roleId,
                                    @RequestBody(required = false) RoleDeletionRequest request,
                                    Authentication authentication) {
        roles.deleteRole(roleId, request == null ? null : request.replacementRoleId(),
                actorId(authentication));
        return ApiResponse.ok(null);
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:menu') and hasAuthority('identity:roles:read'))")
    public ApiResponse<List<PermissionSummary>> permissions() {
        return ApiResponse.ok(roles.listPermissions());
    }

    @GetMapping("/account-overview")
    public ApiResponse<AccountOverview> overview(Authentication authentication) {
        return ApiResponse.ok(roles.accountOverview(Long.parseLong(authentication.getName())));
    }

    private static long actorId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    public record RoleCreateRequest(@NotBlank @Size(max = 50) String code,
                                    @NotBlank @Size(max = 100) String name,
                                    @Size(max = 1000) String description) { }

    public record RoleUpdateRequest(@NotBlank @Size(max = 50) String code,
                                    @NotBlank @Size(max = 100) String name,
                                    @Size(max = 1000) String description,
                                    @NotBlank String status) { }

    public record PermissionAssignmentRequest(@NotNull List<Long> permissionIds) { }

    public record RoleDeletionRequest(Long replacementRoleId) { }
}
