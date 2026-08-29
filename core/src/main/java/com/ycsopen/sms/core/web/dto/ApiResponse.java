package com.ycsopen.sms.core.web.dto;

import lombok.Getter;

import java.time.Instant;

/** PRD 9.1 节约定的统一响应结构：{code, message, data, timestamp, traceId}。 */
@Getter
public class ApiResponse<T> {
    private final int code;
    private final String message;
    private final T data;
    private final Instant timestamp = Instant.now();
    private final String traceId;

    private ApiResponse(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data, org.slf4j.MDC.get("traceId"));
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(400, message, null, org.slf4j.MDC.get("traceId"));
    }
}
