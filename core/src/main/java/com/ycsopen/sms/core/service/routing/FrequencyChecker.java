package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.FrequencyRule;
import com.ycsopen.sms.core.repository.FrequencyRuleRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * F-5.6 频次拦截，同时承担"防刷机制"职责（PRD 6.2 节）。
 * 用 Redis INCR + EXPIRE 实现滑动窗口计数（简化为固定窗口，足以满足"N 次/M 秒"语义）；
 * key 设计：freq:{limitType}:{dimensionValue}:{ruleId}。
 */
@Component
public class FrequencyChecker {

    private final FrequencyRuleRepository frequencyRuleRepository;
    private final StringRedisTemplate redisTemplate;

    public FrequencyChecker(FrequencyRuleRepository frequencyRuleRepository, StringRedisTemplate redisTemplate) {
        this.frequencyRuleRepository = frequencyRuleRepository;
        this.redisTemplate = redisTemplate;
    }

    public Result check(RoutingContext ctx) {
        List<FrequencyRule> rules = frequencyRuleRepository.findAllByStatus(FrequencyRule.Status.ACTIVE);
        for (FrequencyRule rule : rules) {
            String dimensionValue = dimensionValue(rule, ctx);
            if (dimensionValue == null) continue;

            String key = "freq:%s:%s:%d".formatted(rule.getLimitType(), dimensionValue, rule.getId());
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(rule.getLimitWindowSeconds()));
            }
            if (current != null && current > rule.getLimitCount()) {
                if (rule.getAction() == FrequencyRule.Action.BLOCK) {
                    return Result.blocked("命中频次规则「%s」：%s 秒内超过 %d 次"
                            .formatted(rule.getRuleName(), rule.getLimitWindowSeconds(), rule.getLimitCount()));
                }
                // DELAY/ALERT：不在本方法拦截，交由上层（发送队列延迟处理 / 告警事件）处理。
            }
        }
        return Result.pass();
    }

    private String dimensionValue(FrequencyRule rule, RoutingContext ctx) {
        return switch (rule.getLimitType()) {
            case MOBILE -> ctx.getOpaqueMobileQueryValue();
            case TENANT_LEVEL -> ctx.getTenantId() == null ? null : String.valueOf(ctx.getTenantId());
            case IP -> ctx.getClientIp();
            case CONTENT_SIMILARITY -> null; // TODO: 需要内容指纹算法，先跳过（不影响其余三类规则生效）
        };
    }

    public record Result(boolean blocked, String reason) {
        static Result pass() { return new Result(false, null); }
        static Result blocked(String reason) { return new Result(true, reason); }
    }
}
