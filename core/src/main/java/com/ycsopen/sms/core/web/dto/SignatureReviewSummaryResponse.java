package com.ycsopen.sms.core.web.dto;

public record SignatureReviewSummaryResponse(
        long total,
        long pending,
        long approved,
        long rejected,
        long supplementRequired,
        long highRisk
) { }
