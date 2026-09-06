package com.ycsopen.sms.core.common.exception;

import com.ycsopen.sms.core.web.dto.ApiResponse;
import com.ycsopen.sms.core.common.security.logging.SafeLogValue;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger.Category;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger.Event;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一异常处理。对应 PRD 5.15 节"异常流与边界情况"：
 * 不向前端暴露堆栈信息，记录 traceId 便于排查（MDC 由日志过滤器写入）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SecurityEventLogger security;

    public GlobalExceptionHandler(SecurityEventLogger security) {
        this.security = security;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        security.warn(Event.BUSINESS_REJECTION, Category.BUSINESS,
                SafeLogValue.correlation(MDC.get("traceId")));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        security.error(Event.UNEXPECTED_FAILURE, Category.UNEXPECTED,
                SafeLogValue.correlation(MDC.get("traceId")));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "系统繁忙，请稍后再试"));
    }
}
