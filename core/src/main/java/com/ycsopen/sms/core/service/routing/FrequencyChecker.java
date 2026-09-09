package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.FrequencyRule;
import com.ycsopen.sms.core.repository.FrequencyRuleRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * F-5.6 频次拦截，同时承担"防刷机制"职责（PRD 6.2 节）。
 * 用 Redis INCR + EXPIRE 实现滑动窗口计数（简化为固定窗口，足以满足"N 次/M 秒"语义）；
 * key 设计：freq:{limitType}:{dimensionValue}:{ruleId}。
 */
@Component
public class FrequencyChecker {

    public static final String MOBILE_IDENTITY_NOT_READY = "FREQUENCY_MOBILE_IDENTITY_NOT_READY";

    private final FrequencyRuleRepository frequencyRuleRepository;
    private final RedisFixedWindowCounter counter;
    private final JdbcTemplate jdbc;

    public FrequencyChecker(FrequencyRuleRepository frequencyRuleRepository,
                            RedisFixedWindowCounter counter,
                            JdbcTemplate jdbc) {
        this.frequencyRuleRepository = frequencyRuleRepository;
        this.counter = counter;
        this.jdbc = jdbc;
    }

    public Result check(RoutingContext ctx) {
        List<FrequencyRule> rules = frequencyRuleRepository.findAllByStatus(FrequencyRule.Status.ACTIVE);
        for (FrequencyRule rule : rules) {
            if (!appliesToScope(rule, ctx)) continue;
            String dimensionValue;
            try {
                dimensionValue = dimensionValue(rule, ctx);
            } catch (IllegalStateException failure) {
                if (MOBILE_IDENTITY_NOT_READY.equals(failure.getMessage())) {
                    return Result.blocked(MOBILE_IDENTITY_NOT_READY);
                }
                throw failure;
            }
            if (dimensionValue == null) continue;
            if (isExempt(rule, ctx, dimensionValue)) continue;

            String key = "freq:%s:%s:%s:%d".formatted(rule.getLimitType(), scopeKey(rule), dimensionValue, rule.getId());
            long current = counter.increment(key, rule.getLimitWindowSeconds());
            if (current > rule.getLimitCount()) {
                recordHit(rule, ctx, dimensionValue, current);
                if (rule.getAction() == FrequencyRule.Action.BLOCK) {
                    return Result.blocked("命中频次规则「%s」：%s 秒内超过 %d 次"
                            .formatted(rule.getRuleName(), rule.getLimitWindowSeconds(), rule.getLimitCount()));
                }
                if (rule.getAction() == FrequencyRule.Action.DELAY) {
                    return Result.delayed("命中延迟频控规则「%s」".formatted(rule.getRuleName()),
                            rule.getLimitWindowSeconds());
                }
            }
        }
        return Result.pass();
    }

    private String dimensionValue(FrequencyRule rule, RoutingContext ctx) {
        return switch (rule.getLimitType()) {
            case MOBILE -> mobileIdentity(ctx);
            case TENANT_LEVEL -> ctx.getTenantId() == null ? null : String.valueOf(ctx.getTenantId());
            case IP -> ctx.getClientIp();
            case CONTENT_SIMILARITY -> ctx.getContent() == null || ctx.getContent().isBlank()
                    ? null : sha256(Normalizer.normalize(ctx.getContent(), Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim());
        };
    }

    private String mobileIdentity(RoutingContext ctx) {
        if (ctx.getMobileQueryIndexes() == null || ctx.getMobileQueryIndexes().values().isEmpty()) {
            throw new IllegalStateException(MOBILE_IDENTITY_NOT_READY);
        }
        return ctx.getMobileQueryIndexes().values().stream()
                .map(index -> index.keyVersion() + ":" + index.canonicalValue())
                .collect(Collectors.joining("."));
    }

    private boolean appliesToScope(FrequencyRule rule, RoutingContext ctx) {
        FrequencyRule.Scope scope = rule.getScope() == null ? FrequencyRule.Scope.GLOBAL : rule.getScope();
        return switch (scope) {
            case GLOBAL -> true;
            case TENANT -> ctx.getTenantId() != null && ctx.getTenantId().equals(rule.getScopeRefId());
            case API_KEY -> ctx.getApiKeyId() != null && ctx.getApiKeyId().equals(rule.getScopeRefId());
        };
    }

    private boolean isExempt(FrequencyRule rule, RoutingContext ctx, String dimensionValue) {
        try {
            Integer found = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM frequency_rule_exemptions
                     WHERE tenant_id=?
                       AND (api_key_id IS NULL OR api_key_id=?)
                       AND limit_type=?
                       AND (dimension_value IS NULL OR dimension_value=?)
                       AND status='ACTIVE'
                    """, Integer.class, ctx.getTenantId(), ctx.getApiKeyId(), rule.getLimitType().name(), dimensionValue);
            return found != null && found > 0;
        } catch (DataAccessException failure) {
            return false;
        }
    }

    private String scopeKey(FrequencyRule rule) {
        FrequencyRule.Scope scope = rule.getScope() == null ? FrequencyRule.Scope.GLOBAL : rule.getScope();
        return scope + ":" + (rule.getScopeRefId() == null ? "ALL" : rule.getScopeRefId());
    }

    private void recordHit(FrequencyRule rule, RoutingContext ctx, String dimensionValue, long current) {
        rule.setHitCount((rule.getHitCount() == null ? 0 : rule.getHitCount()) + 1);
        frequencyRuleRepository.save(rule);
        try {
            jdbc.update("""
                    INSERT INTO frequency_rule_hits
                    (rule_id, tenant_id, api_key_id, limit_type, dimension_value, action,
                     exceeded_count, window_seconds, blocked, created_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?)
                    """, rule.getId(), ctx.getTenantId(), ctx.getApiKeyId(), rule.getLimitType().name(),
                    dimensionValue, rule.getAction().name(), current, rule.getLimitWindowSeconds(),
                    rule.getAction() == FrequencyRule.Action.BLOCK, LocalDateTime.now());
        } catch (DataAccessException failure) {
            // The routing decision remains authoritative even if evidence persistence is unavailable.
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    public record Result(boolean blocked, boolean delayed, String reason, int retryAfterSeconds) {
        static Result pass() { return new Result(false, false, null, 0); }
        static Result blocked(String reason) { return new Result(true, false, reason, 0); }
        static Result delayed(String reason, int retryAfterSeconds) {
            return new Result(false, true, reason, retryAfterSeconds);
        }
    }
}
