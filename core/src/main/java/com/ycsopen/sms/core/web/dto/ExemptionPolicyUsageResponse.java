package com.ycsopen.sms.core.web.dto;

import java.time.LocalDateTime;

public record ExemptionPolicyUsageResponse(
        long id,
        Long exemptionRuleId,
        Integer versionNo,
        long tenantId,
        String subjectType,
        String subjectId,
        String productCode,
        String scopeExpression,
        String controlCode,
        String actor,
        String reason,
        String result,
        LocalDateTime createdAt
) { }
