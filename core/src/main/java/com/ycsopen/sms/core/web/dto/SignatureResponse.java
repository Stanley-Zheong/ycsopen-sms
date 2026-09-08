package com.ycsopen.sms.core.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SignatureResponse(
        long id,
        long tenantId,
        String signCode,
        String signContent,
        String signType,
        String usageType,
        String riskLevel,
        String evidenceRef,
        String applicantName,
        String auditStatus,
        String auditComment,
        LocalDateTime auditTime,
        LocalDateTime createdAt,
        List<History> history
) {
    public record History(
            String eventType,
            String actor,
            String opinion,
            String riskLevel,
            LocalDateTime createdAt
    ) { }
}
