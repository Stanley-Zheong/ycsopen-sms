package com.ycsopen.sms.core.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ChannelHealthMonitorResponse(long channelId, String channelName, String protocol, String operator,
                                           String status, String healthState, BigDecimal timeoutRate,
                                           BigDecimal failureRate, long averageLatencyMs, String reasonCode,
                                           boolean candidateEligible, String candidateReasonCode, long eventCount,
                                           String pauseReason, String pausedBy, LocalDateTime pausedAt) { }
