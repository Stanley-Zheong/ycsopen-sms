package com.ycsopen.sms.core.web.dto;

import java.time.LocalDateTime;

public record TenantApiKeyResponse(long id, String appKey, String name, String description,
                                   String status, String ipWhitelist, int perSecond,
                                   int perMinute, int perHour, int perDay,
                                   LocalDateTime expireTime, LocalDateTime lastUsedTime,
                                   String secret) { }
