package com.ycsopen.sms.core.web.dto;

public record ExemptionPolicyPreviewRequest(
        Long tenantId,
        String exemptionType,
        String resourceId,
        String productCode,
        String scopeExpression,
        String controlCode,
        String reason,
        String actor
) { }
