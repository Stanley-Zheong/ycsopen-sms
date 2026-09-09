package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.common.exception.RateLimitExceededException;
import org.springframework.stereotype.Service;

/** Enforces per-API-key second/minute/hour/day limits before any send task or charge is created. */
@Service
public class ApiKeyRateLimitService {
    private final RedisFixedWindowCounter counter;

    public ApiKeyRateLimitService(RedisFixedWindowCounter counter) {
        this.counter = counter;
    }

    public void enforce(Long tenantId, Long apiKeyId, RatePolicy policy) {
        if (tenantId == null || apiKeyId == null || policy == null) {
            throw new RateLimitExceededException("api-key-context-missing", 1);
        }
        check("SECOND", tenantId, apiKeyId, 1, policy.perSecond());
        check("MINUTE", tenantId, apiKeyId, 60, policy.perMinute());
        check("HOUR", tenantId, apiKeyId, 3600, policy.perHour());
        check("DAY", tenantId, apiKeyId, 86400, policy.perDay());
    }

    private void check(String window, long tenantId, long apiKeyId, int windowSeconds, int limit) {
        if (limit < 1) {
            throw new RateLimitExceededException("api-key-%s-invalid".formatted(window), windowSeconds);
        }
        String prefix = "api-rate:%d:%d:%s".formatted(tenantId, apiKeyId, window);
        long current = counter.increment(prefix, windowSeconds);
        if (current > limit) {
            throw new RateLimitExceededException("api-key-%s".formatted(window), windowSeconds);
        }
    }

    public record RatePolicy(int perSecond, int perMinute, int perHour, int perDay) { }
}
