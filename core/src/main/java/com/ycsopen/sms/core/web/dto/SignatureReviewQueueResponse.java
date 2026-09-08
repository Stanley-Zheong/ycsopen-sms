package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record SignatureReviewQueueResponse(
        SignatureReviewSummaryResponse summary,
        List<SignatureResponse> items
) { }
