package com.ycsopen.sms.core.web.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ChannelConfigurationRequest(
        String name,
        String protocol,
        String operator,
        String host,
        Integer port,
        String account,
        String password,
        String spId,
        String serviceId,
        String srcId,
        Integer maxConnections,
        Integer windowSize,
        Integer tpsLimit,
        BigDecimal price,
        Integer priority,
        String activeWindow,
        String availability,
        Map<String, Object> extraConfig,
        Long expectedEffectiveVersion) {
}
