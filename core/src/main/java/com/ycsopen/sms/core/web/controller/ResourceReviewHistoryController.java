package com.ycsopen.sms.core.web.controller;

import com.ycsopen.sms.core.service.review.ResourceReviewHistoryService;
import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.web.dto.ResourceReviewHistoryItem;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Phase 15: unified read-only Admin review-history endpoints. */
@RestController
@RequestMapping("/api/v1/console/review-history")
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ResourceReviewHistoryController {
    private final ResourceReviewHistoryService service;

    public ResourceReviewHistoryController(ResourceReviewHistoryService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('review-history:read')")
    public ApiResponse<List<ResourceReviewHistoryItem>> search(@RequestParam(required = false) String resourceType,
                                                               @RequestParam(required = false) String tenantId,
                                                               @RequestParam(required = false) String decisionState,
                                                               @RequestParam(required = false) String actor,
                                                               @RequestParam(required = false) String riskLevel,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String createdFrom,
                                                               @RequestParam(required = false) String createdTo,
                                                               @RequestParam(required = false) String page,
                                                               @RequestParam(required = false) String pageSize) {
        return ApiResponse.ok(service.search(resourceType, tenantId, decisionState, actor, riskLevel, keyword, createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/detail")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('review-history:read')")
    public ApiResponse<ResourceReviewHistoryItem> detail(@RequestParam String decisionId) {
        return ApiResponse.ok(service.detail(decisionId));
    }
}
