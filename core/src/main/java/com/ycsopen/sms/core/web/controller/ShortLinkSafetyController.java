package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.shortlink.ShortLinkSafetyService;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.UserRepository;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** Phase 48 short-link tenant/admin/public API. */
@RestController
public class ShortLinkSafetyController {
    private final ShortLinkSafetyService service;
    private final UserRepository users;

    public ShortLinkSafetyController(ShortLinkSafetyService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    @GetMapping("/api/v1/console/tenant/shortlinks")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TENANT_USER','TENANT_DEV') or hasAuthority('shortlink:read')")
    public ApiResponse<List<ShortLinkSafetyService.ShortLinkRow>> tenantLinks(Authentication authentication) {
        return ApiResponse.ok(service.tenantLinks(tenantId(authentication)));
    }

    @PostMapping("/api/v1/console/tenant/shortlinks")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TENANT_USER') or hasAuthority('shortlink:write')")
    public ApiResponse<ShortLinkSafetyService.ShortLinkRow> create(
            @RequestBody ShortLinkSafetyService.CreateCommand command, Authentication authentication) {
        long tenantId = tenantId(authentication);
        return ApiResponse.ok(service.create(new ShortLinkSafetyService.CreateCommand(
                tenantId, command == null ? null : command.originalUrl(),
                command == null ? null : command.customDomain(),
                command == null ? null : command.validUntil(),
                command == null ? null : command.redirectChain(),
                command == null ? null : command.resolvedIps()), actor(authentication)));
    }

    @GetMapping("/api/v1/console/tenant/shortlinks/analytics")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','TENANT_USER','TENANT_DEV') or hasAuthority('shortlink:read')")
    public ApiResponse<ShortLinkSafetyService.Analytics> analytics(Authentication authentication) {
        return ApiResponse.ok(service.analytics(tenantId(authentication)));
    }

    @GetMapping("/api/v1/console/shortlinks/review")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('shortlink-review:read')")
    public ApiResponse<List<ShortLinkSafetyService.ShortLinkRow>> reviewQueue(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(service.reviewQueue(status));
    }

    @PostMapping("/api/v1/console/shortlinks/review/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('shortlink-review:write')")
    public ApiResponse<ShortLinkSafetyService.ShortLinkRow> approve(
            @PathVariable long id, @RequestBody ShortLinkSafetyService.ReviewCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.approve(id, command, actor(authentication)));
    }

    @PostMapping("/api/v1/console/shortlinks/review/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('shortlink-review:write')")
    public ApiResponse<ShortLinkSafetyService.ShortLinkRow> reject(
            @PathVariable long id, @RequestBody ShortLinkSafetyService.ReviewCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.reject(id, command, actor(authentication)));
    }

    @PostMapping("/api/v1/console/shortlinks/review/{id}/inspect")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') or hasAuthority('shortlink-review:write')")
    public ApiResponse<ShortLinkSafetyService.ShortLinkRow> inspect(
            @PathVariable long id, @RequestBody ShortLinkSafetyService.InspectCommand command,
            Authentication authentication) {
        return ApiResponse.ok(service.inspect(id, command, actor(authentication)));
    }

    @GetMapping("/s/{code}")
    public ResponseEntity<String> redirect(@PathVariable String code, HttpServletRequest request) {
        var decision = service.redirect(code, new ShortLinkSafetyService.ClickCommand(
                request.getRemoteAddr(), "UNKNOWN", request.getHeader("User-Agent") == null ? "UNKNOWN" : "BROWSER"));
        if (decision.redirect()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(decision.targetUrl()))
                    .build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body("<!doctype html><html lang=\"zh-CN\"><title>短链安全提示</title><body><h1>"
                        + escape(decision.message()) + "</h1><p>平台不会跳转到未审核、已拒绝、已过期或已下线的目标。</p></body></html>");
    }

    private long tenantId(Authentication authentication) {
        long userId;
        try {
            userId = Long.parseLong(actor(authentication));
        } catch (NumberFormatException ignored) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不合法");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不存在"));
        if (user.getTenantId() == null) {
            throw new BusinessException("TENANT_CONTEXT_REQUIRED", "租户上下文不能为空");
        }
        return user.getTenantId();
    }

    private static String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new BusinessException("AUTHENTICATED_ACTOR_REQUIRED", "登录操作人不能为空");
        }
        return authentication.getName().trim();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
