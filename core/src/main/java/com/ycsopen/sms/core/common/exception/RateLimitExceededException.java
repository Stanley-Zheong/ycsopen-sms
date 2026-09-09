package com.ycsopen.sms.core.common.exception;

import lombok.Getter;

/** HTTP API rate limit rejection that must map to the standard 429 response contract. */
@Getter
public class RateLimitExceededException extends RuntimeException {
    private final String limitKey;
    private final int retryAfterSeconds;

    public RateLimitExceededException(String limitKey, int retryAfterSeconds) {
        super("请求过于频繁，请稍后重试");
        this.limitKey = limitKey;
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
