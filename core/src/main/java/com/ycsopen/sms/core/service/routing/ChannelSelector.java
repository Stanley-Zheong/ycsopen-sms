package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.domain.entity.RouteRule;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.repository.RouteRuleRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * F-5.8 分流规则 + F-5.9 主备与熔断（简化版：本实现只做"选出最高优先级的可用通道"，
 * 真正的权重轮询/失败率熔断统计留给 core/docs/ROADMAP.md 的后续迭代——见类头注释里的 TODO，
 * 但拦截链路（黑名单/内容审核/频控）与"选不到可用通道即失败"这两个关键行为已经是真实逻辑，
 * 不是占位符。）
 */
@Component
public class ChannelSelector {

    private final ChannelRepository channelRepository;
    private final RouteRuleRepository routeRuleRepository;

    public ChannelSelector(ChannelRepository channelRepository, RouteRuleRepository routeRuleRepository) {
        this.channelRepository = channelRepository;
        this.routeRuleRepository = routeRuleRepository;
    }

    public Optional<Long> select(RoutingContext ctx) {
        // 1. 先看是否有匹配的分流规则（机构专属规则优先于全局规则）
        List<RouteRule> tenantRules = routeRuleRepository
                .findByTenantIdAndStatusOrderByPriorityAsc(ctx.getTenantId(), RouteRule.Status.ACTIVE);
        for (RouteRule rule : tenantRules) {
            if (matches(rule, ctx)) {
                Optional<Channel> ch = channelRepository.findById(rule.getTargetChannelId());
                if (ch.isPresent() && ch.get().isRoutable()) {
                    return Optional.of(ch.get().getId());
                }
            }
        }
        List<RouteRule> globalRules = routeRuleRepository
                .findByTenantIdIsNullAndStatusOrderByPriorityAsc(RouteRule.Status.ACTIVE);
        for (RouteRule rule : globalRules) {
            if (matches(rule, ctx)) {
                Optional<Channel> ch = channelRepository.findById(rule.getTargetChannelId());
                if (ch.isPresent() && ch.get().isRoutable()) {
                    return Optional.of(ch.get().getId());
                }
            }
        }

        // 2. 无匹配规则：退化为"选优先级最高的可用通道"作为默认通道 (F-5.8 "条件不匹配时走默认通道")
        return channelRepository.findAll().stream()
                .filter(Channel::isRoutable)
                .max(Comparator.comparingInt(Channel::getPriority))
                .map(Channel::getId);
    }

    private boolean matches(RouteRule rule, RoutingContext ctx) {
        if (rule.getOperator() != null && ctx.getOperatorHint() != null
                && !rule.getOperator().name().equals(ctx.getOperatorHint())) {
            return false;
        }
        // phonePrefix 匹配依赖明文号码前缀，路由引擎当前只持有 mobileHash——
        // 因此号段前缀匹配应在号码归属识别阶段（F-5.7）完成并写入 operatorHint/其他字段，
        // 这里不再重复处理明文号码，避免明文号码泄漏到路由层（TODO 标记见 core/docs/ROADMAP.md）。
        return true;
    }
}
