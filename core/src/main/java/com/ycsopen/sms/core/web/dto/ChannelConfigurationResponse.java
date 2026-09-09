package com.ycsopen.sms.core.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

public record ChannelConfigurationResponse(
        long id,
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
        int maxConnections,
        int windowSize,
        int tpsLimit,
        BigDecimal price,
        int priority,
        String activeWindow,
        String availability,
        Map<String, Object> extraConfig,
        String status,
        Long effectiveVersion,
        long configurationVersion,
        LocalDateTime updatedAt) {
}
