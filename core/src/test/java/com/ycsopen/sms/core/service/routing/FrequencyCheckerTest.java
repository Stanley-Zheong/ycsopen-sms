package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.domain.entity.FrequencyRule;
import com.ycsopen.sms.core.repository.FrequencyRuleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FrequencyCheckerTest {

    @Mock FrequencyRuleRepository repository;
    @Mock RedisFixedWindowCounter counter;
    @Mock JdbcTemplate jdbc;

    @Test
    void tenantAndIpRulesUseIsolatedAtomicRedisPrefixes() {
        FrequencyRule tenant = rule(3L, FrequencyRule.LimitType.TENANT_LEVEL);
        FrequencyRule ip = rule(4L, FrequencyRule.LimitType.IP);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE))
                .thenReturn(List.of(tenant, ip));
        when(counter.increment(anyString(), eq(60))).thenReturn(1L);

        FrequencyChecker.Result result = checker().check(context());

        assertThat(result.blocked()).isFalse();
        verify(counter).increment("freq:TENANT_LEVEL:GLOBAL:ALL:17:3", 60);
        verify(counter).increment("freq:IP:GLOBAL:ALL:127.0.0.1:4", 60);
    }

    @Test
    void exceededBlockRuleRecordsHitAndBlocksBeforeTaskCreation() {
        FrequencyRule tenant = rule(3L, FrequencyRule.LimitType.TENANT_LEVEL);
        tenant.setLimitCount(1);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE)).thenReturn(List.of(tenant));
        when(counter.increment(anyString(), eq(60))).thenReturn(2L);

        FrequencyChecker.Result result = checker().check(context());

        assertThat(result.blocked()).isTrue();
        assertThat(result.reason()).contains("TENANT_LEVEL").contains("超过 1 次");
        assertThat(tenant.getHitCount()).isEqualTo(1);
        verify(repository).save(tenant);
        verify(jdbc).update(any(String.class), eq(3L), eq(17L), eq(19L), eq("TENANT_LEVEL"),
                eq("17"), eq("BLOCK"), eq(2L), eq(60), eq(true), any());
    }

    @Test
    void scopedExemptionSkipsRedisMutation() {
        FrequencyRule tenant = rule(3L, FrequencyRule.LimitType.TENANT_LEVEL);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE)).thenReturn(List.of(tenant));
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(17L), eq(19L),
                eq("TENANT_LEVEL"), eq("17"))).thenReturn(1);

        FrequencyChecker.Result result = checker().check(context());

        assertThat(result.blocked()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    void tenantScopedRuleDoesNotLeakAcrossTenants() {
        FrequencyRule tenant = rule(3L, FrequencyRule.LimitType.IP);
        tenant.setScope(FrequencyRule.Scope.TENANT);
        tenant.setScopeRefId(18L);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE)).thenReturn(List.of(tenant));

        FrequencyChecker.Result result = checker().check(context());

        assertThat(result.blocked()).isFalse();
        verifyNoInteractions(counter);
    }

    @Test
    void mobileRuleUsesAllOpaqueIndexesWithoutReadingRotationDependentSingleValue() {
        FrequencyRule mobile = rule(5L, FrequencyRule.LimitType.MOBILE);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE)).thenReturn(List.of(mobile));
        when(counter.increment(anyString(), eq(60))).thenReturn(1L);

        FrequencyChecker.Result result = checker().check(context());

        assertThat(result.blocked()).isFalse();
        verify(counter).increment(
                "freq:MOBILE:GLOBAL:ALL:1:afqwey3emvtgo2djnjvwy3lon5ygcytdmrswmz3infvgw3dnnzxxa:5", 60);
    }

    @Test
    void mobileRuleWithoutIdentityBlocksBeforeRedisMutation() {
        FrequencyRule mobile = rule(5L, FrequencyRule.LimitType.MOBILE);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE)).thenReturn(List.of(mobile));

        FrequencyChecker.Result result = checker().check(RoutingContext.builder().tenantId(17L).build());

        assertThat(result.blocked()).isTrue();
        assertThat(result.reason()).isEqualTo(FrequencyChecker.MOBILE_IDENTITY_NOT_READY);
        verifyNoInteractions(counter);
    }

    @Test
    void contentSimilarityUsesCanonicalFingerprintWithoutRedisWhenContentMissing() {
        FrequencyRule similarity = rule(6L, FrequencyRule.LimitType.CONTENT_SIMILARITY);
        when(repository.findAllByStatus(FrequencyRule.Status.ACTIVE)).thenReturn(List.of(similarity));

        FrequencyChecker.Result result = checker().check(RoutingContext.builder().tenantId(17L).build());

        assertThat(result.blocked()).isFalse();
        verify(counter, never()).increment(anyString(), eq(60));
    }

    private FrequencyChecker checker() {
        return new FrequencyChecker(repository, counter, jdbc);
    }

    private static FrequencyRule rule(long id, FrequencyRule.LimitType type) {
        FrequencyRule rule = new FrequencyRule();
        rule.setId(id);
        rule.setRuleName(type.name());
        rule.setLimitType(type);
        rule.setLimitCount(10);
        rule.setLimitWindowSeconds(60);
        rule.setAction(FrequencyRule.Action.BLOCK);
        rule.setScope(FrequencyRule.Scope.GLOBAL);
        rule.setStatus(FrequencyRule.Status.ACTIVE);
        rule.setHitCount(0L);
        return rule;
    }

    private static RoutingContext context() {
        return RoutingContext.builder()
                .tenantId(17L)
                .apiKeyId(19L)
                .clientIp("127.0.0.1")
                .mobileQueryIndexes(new BlindIndexPort.OrderedIndexes(List.of(
                        new VersionedBlindIndex(1, "abcdefghijklmnopabcdefghijklmnop".getBytes()))))
                .build();
    }
}
