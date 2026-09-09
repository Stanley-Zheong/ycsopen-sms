package com.ycsopen.sms.core.web.dto;

import java.time.LocalDateTime;

public record ExemptionPolicyResponse(
        long id,
        long tenantId,
        String exemptionType,
        String resourceId,
        String productCode,
        String scopeExpression,
        String approvalStatus,
        LocalDateTime validFrom,
        LocalDateTime validUntil,
        boolean revoked,
        Integer versionNo,
        long usageCount,
        String reason,
        String createdBy,
        String revokedBy,
        String revokeReason,
        LocalDateTime revokedAt,
        LocalDateTime createdAt
) { }
