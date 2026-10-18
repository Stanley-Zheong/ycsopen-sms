package com.ycsopen.sms.core.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDateTime;

public record TenantApiKeyResponse(long id, String appKey, String name, String description,
                                   String status, String ipWhitelist, int perSecond,
                                   int perMinute, int perHour, int perDay,
                                   LocalDateTime expireTime,
                                   @JsonFormat(shape = JsonFormat.Shape.STRING) Instant lastUsedTime,
                                   String appSecretMask,
                                   @JsonInclude(JsonInclude.Include.NON_NULL) String appSecret) {
    @Override
    public String toString() {
        return "TenantApiKeyResponse[id=%d, appKey=%s, name=%s, status=%s, appSecret=[redacted]]"
                .formatted(id, appKey, name, status);
    }
}
