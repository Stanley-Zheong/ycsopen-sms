package com.ycsopen.sms.core.service.channel.health;

import java.math.BigDecimal;

/** Deterministic adapter boundary; real protocol sockets are owned by later connector phases. */
public interface ChannelHealthCheckAdapter {
    Sample check(long channelId);

    record Sample(boolean connected, BigDecimal timeoutRate, BigDecimal failureRate,
                  long averageLatencyMs, String reasonCode) { }
}
