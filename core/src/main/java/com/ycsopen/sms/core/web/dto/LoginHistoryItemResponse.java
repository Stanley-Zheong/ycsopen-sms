package com.ycsopen.sms.core.web.dto;

import java.time.LocalDateTime;

public record LoginHistoryItemResponse(
        long id,
        Long userId,
        String username,
        String loginIp,
        String userAgent,
        String outcome,
        LocalDateTime occurredAt) {
}
