package com.ycsopen.sms.core.web.dto;

import java.util.List;

public record TemplateReviewQueueResponse(TemplateReviewSummaryResponse summary, List<TemplateResponse> items) { }
