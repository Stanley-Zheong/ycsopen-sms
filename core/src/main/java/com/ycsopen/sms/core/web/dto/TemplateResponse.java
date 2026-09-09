package com.ycsopen.sms.core.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TemplateResponse(
        long id,
        long tenantId,
        String templateCode,
        String templateName,
        String content,
        String templateType,
        long signatureId,
        String paramCheckRule,
        String description,
        List<String> variableNames,
        int versionNo,
        Long previousTemplateId,
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
            String snapshotContent,
            List<String> variableNames,
            LocalDateTime createdAt
    ) { }
}
