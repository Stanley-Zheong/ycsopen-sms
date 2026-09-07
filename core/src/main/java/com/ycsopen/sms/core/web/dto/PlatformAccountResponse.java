package com.ycsopen.sms.core.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Safe platform-account projection: it intentionally has no credential or full-phone field. */
public record PlatformAccountResponse(
        long id,
        String username,
        String email,
        String realName,
        String maskedPhone,
        String userType,
        String status,
        LocalDate validUntil,
        List<Long> roleIds,
        LocalDateTime lastLoginAt,
        String createdBy,
        LocalDateTime createdAt) {
}
