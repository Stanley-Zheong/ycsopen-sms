package com.ycsopen.sms.core.web.dto;

import java.time.LocalDateTime;

/** Phase 15: normalized read-only decision row across signature, template, and exemption review histories. */
public record ResourceReviewHistoryItem(
        String decisionId,
        String resourceType,
        long resourceId,
        String resourceCode,
        String resourceVersion,
        long tenantId,
        String decisionState,
        String actor,
        String reason,
        String riskLevel,
        String evidenceRef,
        String submittedSnapshot,
        String lifecycleLink,
        LocalDateTime createdAt
) {
}
