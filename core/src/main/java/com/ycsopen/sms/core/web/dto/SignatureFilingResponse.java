package com.ycsopen.sms.core.web.dto;

public record SignatureFilingResponse(
        long signatureId,
        long channelId,
        String channelName,
        String protocol,
        String operator,
        String status,
        String providerRequestId,
        String resultMessage,
        int attemptCount,
        boolean channelEligible,
        String channelEligibilityReason
) { }
