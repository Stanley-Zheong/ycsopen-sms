package com.ycsopen.sms.core.web.dto;

public record ExemptionPolicyPreviewResponse(
        String result,
        Long exemptionRuleId,
        Integer versionNo,
        String reason,
        String subjectType,
        String subjectId,
        String productCode,
        String scopeExpression
) { }
