package com.ycsopen.sms.core.web.dto;

import java.math.BigDecimal;

public record ChannelHealthObservationRequest(Boolean connected, BigDecimal timeoutRate,
                                              BigDecimal failureRate, Long averageLatencyMs,
                                              String reasonCode) { }
