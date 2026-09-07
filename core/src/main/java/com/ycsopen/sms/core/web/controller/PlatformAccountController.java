package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.account.AccountStateService;
import com.ycsopen.sms.core.service.account.PlatformAccountService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.PlatformAccountCreateRequest;
import com.ycsopen.sms.core.web.dto.PlatformAccountResponse;
import com.ycsopen.sms.core.web.dto.PlatformAccountUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/console/platform-accounts")
public class PlatformAccountController {

    private final PlatformAccountService accounts;
    private final AccountStateService states;

    public PlatformAccountController(PlatformAccountService accounts, AccountStateService states) {
        this.accounts = accounts;
        this.states = states;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:menu') and hasAuthority('identity:accounts:read') and hasAuthority('identity:accounts:all'))")
    public ApiResponse<List<PlatformAccountResponse>> list() {
        return ApiResponse.ok(accounts.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:accounts:create:api') and hasAuthority('identity:accounts:all'))")
    public ApiResponse<PlatformAccountResponse> create(@Valid @RequestBody PlatformAccountCreateRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(accounts.create(request, actorId(authentication)));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:accounts:update:api') and hasAuthority('identity:accounts:all'))")
    public ApiResponse<PlatformAccountResponse> update(@PathVariable long userId,
                                                       @Valid @RequestBody PlatformAccountUpdateRequest request,
                                                       Authentication authentication) {
        return ApiResponse.ok(accounts.update(userId, request, actorId(authentication)));
    }

    @PostMapping("/{userId}/disable")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:accounts:state:api') and hasAuthority('identity:accounts:all'))")
    public ApiResponse<Void> disable(@PathVariable long userId, Authentication authentication) {
        states.transition(userId, AccountStateService.Action.DISABLE, actorId(authentication));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/enable")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:accounts:state:api') and hasAuthority('identity:accounts:all'))")
    public ApiResponse<Void> enable(@PathVariable long userId, Authentication authentication) {
        states.transition(userId, AccountStateService.Action.ENABLE, actorId(authentication));
        return ApiResponse.ok(null);
    }

    @PostMapping("/{userId}/unlock")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('identity:accounts:state:api') and hasAuthority('identity:accounts:all'))")
    public ApiResponse<Void> unlock(@PathVariable long userId, Authentication authentication) {
        states.transition(userId, AccountStateService.Action.UNLOCK, actorId(authentication));
        return ApiResponse.ok(null);
    }

    private static long actorId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }
}
