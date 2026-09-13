package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.domain.entity.RouteRule;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.repository.RouteRuleRepository;
import com.ycsopen.sms.core.service.channel.health.ChannelCandidateEligibilityService;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChannelSelectorTest {

    @Test
    void excludesPausedRuleTargetAndFallsBackToEligibleChannel() {
        Channel paused = ChannelHealthTestSupport.channel(1L, Channel.Status.PAUSED);
        Channel normal = ChannelHealthTestSupport.channel(2L, Channel.Status.NORMAL);
        RouteRule rule = rule(1L);
        ChannelRepository channels = mock(ChannelRepository.class);
        RouteRuleRepository rules = mock(RouteRuleRepository.class);
        when(rules.findByTenantIdAndStatusOrderByPriorityAsc(9L, RouteRule.Status.ACTIVE)).thenReturn(List.of(rule));
        when(rules.findByTenantIdIsNullAndStatusOrderByPriorityAsc(RouteRule.Status.ACTIVE)).thenReturn(List.of());
        when(channels.findById(1L)).thenReturn(Optional.of(paused));
        when(channels.findAll()).thenReturn(List.of(paused, normal));
        ChannelSelector selector = new ChannelSelector(channels, rules, new ChannelCandidateEligibilityService());

        Optional<Long> selected = selector.select(RoutingContext.builder().tenantId(9L).build());

        assertThat(selected).contains(2L);
    }

    @Test
    void defaultSelectionRequiresEffectiveVersionAndAvailableNormalStatus() {
        Channel draft = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        draft.setEffectiveVersionId(null);
        Channel maintenance = ChannelHealthTestSupport.channel(2L, Channel.Status.MAINTENANCE);
        Channel normal = ChannelHealthTestSupport.channel(3L, Channel.Status.NORMAL);
        ChannelRepository channels = mock(ChannelRepository.class);
        RouteRuleRepository rules = mock(RouteRuleRepository.class);
        when(rules.findByTenantIdAndStatusOrderByPriorityAsc(9L, RouteRule.Status.ACTIVE)).thenReturn(List.of());
        when(rules.findByTenantIdIsNullAndStatusOrderByPriorityAsc(RouteRule.Status.ACTIVE)).thenReturn(List.of());
        when(channels.findAll()).thenReturn(List.of(draft, maintenance, normal));
        ChannelSelector selector = new ChannelSelector(channels, rules, new ChannelCandidateEligibilityService());

        Optional<Long> selected = selector.select(RoutingContext.builder().tenantId(9L).build());

        assertThat(selected).contains(3L);
    }

    @Test
    void verificationMessagesPreferLowestPriceWhenNoRuleMatches() {
        Channel expensive = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        expensive.setPriority(100);
        expensive.setPrice(new java.math.BigDecimal("0.033"));
        Channel cheap = ChannelHealthTestSupport.channel(2L, Channel.Status.NORMAL);
        cheap.setPriority(1);
        cheap.setPrice(new java.math.BigDecimal("0.027"));
        ChannelRepository channels = mock(ChannelRepository.class);
        RouteRuleRepository rules = mock(RouteRuleRepository.class);
        when(rules.findByTenantIdAndStatusOrderByPriorityAsc(9L, RouteRule.Status.ACTIVE)).thenReturn(List.of());
        when(rules.findByTenantIdIsNullAndStatusOrderByPriorityAsc(RouteRule.Status.ACTIVE)).thenReturn(List.of());
        when(channels.findAll()).thenReturn(List.of(expensive, cheap));

        Optional<Long> selected = new ChannelSelector(channels, rules, new ChannelCandidateEligibilityService())
                .select(RoutingContext.builder().tenantId(9L).messageType("VERIFY").build());

        assertThat(selected).contains(2L);
    }

    @Test
    void operatorRuleDoesNotMatchWhenNumberAttributionIsUnknown() {
        Channel targeted = ChannelHealthTestSupport.channel(1L, Channel.Status.NORMAL);
        Channel fallback = ChannelHealthTestSupport.channel(2L, Channel.Status.NORMAL);
        fallback.setPriority(100);
        RouteRule rule = rule(1L);
        rule.setOperator(Channel.Operator.MOBILE);
        ChannelRepository channels = mock(ChannelRepository.class);
        RouteRuleRepository rules = mock(RouteRuleRepository.class);
        when(rules.findByTenantIdAndStatusOrderByPriorityAsc(9L, RouteRule.Status.ACTIVE)).thenReturn(List.of(rule));
        when(rules.findByTenantIdIsNullAndStatusOrderByPriorityAsc(RouteRule.Status.ACTIVE)).thenReturn(List.of());
        when(channels.findById(1L)).thenReturn(Optional.of(targeted));
        when(channels.findAll()).thenReturn(List.of(targeted, fallback));

        assertThat(new ChannelSelector(channels, rules, new ChannelCandidateEligibilityService())
                .select(RoutingContext.builder().tenantId(9L).build())).contains(2L);
    }

    private static RouteRule rule(long targetChannelId) {
        RouteRule rule = new RouteRule();
        rule.setTargetChannelId(targetChannelId);
        rule.setStatus(RouteRule.Status.ACTIVE);
        rule.setPriority(1);
        return rule;
    }
}
