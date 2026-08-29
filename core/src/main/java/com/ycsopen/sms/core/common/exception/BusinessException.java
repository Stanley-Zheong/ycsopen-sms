package com.ycsopen.sms.core.common.exception;

import lombok.Getter;

/** 业务规则拒绝（如黑名单命中、余额不足）时抛出，由 GlobalExceptionHandler 统一转换为 PRD 9.1 节的统一响应结构。 */
@Getter
public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
