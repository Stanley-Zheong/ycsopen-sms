package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.FrequencyRule;
import com.ycsopen.sms.core.repository.FrequencyRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FrequencyCheckerTest {

    @Mock FrequencyRuleRepository repository;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;

    @Test
    void activeMobileRuleFailsClosedBeforeAnyRedisMutation() {
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE)).thenReturn(List.of(
                rule(1L, FrequencyRule.LimitType.TENANT_LEVEL),
                rule(2L, FrequencyRule.LimitType.MOBILE)));

        FrequencyChecker.Result result = new FrequencyChecker(repository, redis).check(context());

        assertThat(result.blocked()).isTrue();
        assertThat(result.reason()).isEqualTo(FrequencyChecker.MOBILE_IDENTITY_NOT_READY);
        verifyNoInteractions(redis);
    }

    @Test
    void tenantAndIpRulesKeepTheirExistingRedisBehavior() {
        FrequencyRule tenant = rule(3L, FrequencyRule.LimitType.TENANT_LEVEL);
        FrequencyRule ip = rule(4L, FrequencyRule.LimitType.IP);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE))
                .thenReturn(List.of(tenant, ip));
        when(redis.opsForValue()).thenReturn(values);
        when(values.increment(anyString())).thenReturn(1L);

        FrequencyChecker.Result result = new FrequencyChecker(repository, redis).check(context());

        assertThat(result.blocked()).isFalse();
        verify(values).increment("freq:TENANT_LEVEL:17:3");
        verify(values).increment("freq:IP:127.0.0.1:4");
        verify(redis).expire("freq:TENANT_LEVEL:17:3", Duration.ofSeconds(60));
        verify(redis).expire("freq:IP:127.0.0.1:4", Duration.ofSeconds(60));
    }

    @Test
    void noActiveMobileRuleNeverReadsTheRotationDependentMobileQueryValue() {
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE))
                .thenReturn(List.of(rule(5L, FrequencyRule.LimitType.CONTENT_SIMILARITY)));

        FrequencyChecker.Result result = new FrequencyChecker(repository, redis).check(
                RoutingContext.builder().tenantId(17L).build());

        assertThat(result.blocked()).isFalse();
        verify(redis, never()).opsForValue();
    }

    private static FrequencyRule rule(long id, FrequencyRule.LimitType type) {
        FrequencyRule rule = new FrequencyRule();
        rule.setId(id);
        rule.setRuleName(type.name());
        rule.setLimitType(type);
        rule.setLimitCount(10);
        rule.setLimitWindowSeconds(60);
        rule.setAction(FrequencyRule.Action.BLOCK);
        rule.setStatus(FrequencyRule.Status.ACTIVE);
        return rule;
    }

    private static RoutingContext context() {
        return RoutingContext.builder()
                .tenantId(17L)
                .clientIp("127.0.0.1")
                .build();
    }
}
