package com.ycsopen.sms.core.web.dto;

public record LoginResponse(String accessToken, String userType, Long tenantId) {
}
