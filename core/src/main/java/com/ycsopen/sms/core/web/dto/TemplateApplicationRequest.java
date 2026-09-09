package com.ycsopen.sms.core.web.dto;

public record TemplateApplicationRequest(
        String templateName,
        String content,
        String templateType,
        Long signatureId,
        String paramCheckRule,
        String description
) { }
