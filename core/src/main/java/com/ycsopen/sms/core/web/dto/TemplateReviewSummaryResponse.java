package com.ycsopen.sms.core.web.dto;

public record TemplateReviewSummaryResponse(
        long total,
        long pending,
        long approved,
        long rejected,
        long amendmentRequired
) { }
