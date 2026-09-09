package com.ycsopen.sms.core.web.dto;

public record ExemptionPolicyCreateRequest(
        Long tenantId,
        String exemptionType,
        String resourceId,
        String productCode,
        String scopeExpression,
        String approvalStatus,
        String validFrom,
        String validUntil,
        String reason,
        String actor
) { }
