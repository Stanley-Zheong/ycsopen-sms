package com.ycsopen.sms.core.service.routing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/** Shared Redis fixed-window counter using one Lua script so INCR and EXPIRE are atomic. */
@Component
public class RedisFixedWindowCounter {
    private static final RedisScript<Long> INCREMENT_WITH_TTL = RedisScript.of("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    public RedisFixedWindowCounter(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    RedisFixedWindowCounter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public long increment(String stablePrefix, int windowSeconds) {
        if (stablePrefix == null || stablePrefix.isBlank() || windowSeconds < 1) {
            throw new IllegalArgumentException("counter prefix/window is invalid");
        }
        long bucket = clock.instant().getEpochSecond() / windowSeconds;
        Long value = redis.execute(INCREMENT_WITH_TTL, List.of(stablePrefix + ":" + bucket),
                String.valueOf(windowSeconds));
        return value == null ? 0 : value;
    }
}
