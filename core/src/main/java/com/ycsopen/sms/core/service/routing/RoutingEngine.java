package com.ycsopen.sms.core.service.routing;

import org.springframework.stereotype.Service;

/**
 * 路由引擎主入口，严格按 ycsansms.md 5.5 节的顺序执行：
 *
 * <pre>
 * 提交 → ① 鉴权/参数校验（在 Controller 层完成，不在本类）
 *      → ② 签名+模板合规校验（在 MessageSubmitService 完成，不在本类）
 *      → ③ 黑名单检测（系统级/机构级/第三方风险名单）        [BlacklistChecker]
 *      → ④ 内容审核检测                                    [ContentReviewChecker]
 *      → ⑤ 频次拦截检测                                    [FrequencyChecker]
 *      → ⑥ 号码归属识别（含携号转网）                        [调用方在构造 RoutingContext 时完成]
 *      → ⑦ 分流规则匹配选通道                                [ChannelSelector]
 *      → ⑧ 通道健康度/权重决策（并入 ChannelSelector 简化实现）
 *      → ⑨ 生成发送任务（在 MessageSubmitService 完成，不在本类）
 * </pre>
 *
 * 任一前置拦截命中即短路返回，不执行后续步骤——这是 F-5 系列需求"任一命中即拦截"的核心约束，
 * 也是本类存在的全部意义：把分散的检查器串成一条不可绕过的责任链。
 */
@Service
public class RoutingEngine {

    private final BlacklistChecker blacklistChecker;
    private final ContentReviewChecker contentReviewChecker;
    private final FrequencyChecker frequencyChecker;
    private final ChannelSelector channelSelector;

    public RoutingEngine(BlacklistChecker blacklistChecker,
                          ContentReviewChecker contentReviewChecker,
                          FrequencyChecker frequencyChecker,
                          ChannelSelector channelSelector) {
        this.blacklistChecker = blacklistChecker;
        this.contentReviewChecker = contentReviewChecker;
        this.frequencyChecker = frequencyChecker;
        this.channelSelector = channelSelector;
    }

    public RoutingDecision route(RoutingContext ctx) {
        BlacklistChecker.Result blacklistResult = blacklistChecker.check(ctx);
        if (blacklistResult.blocked()) {
            return RoutingDecision.reject(RoutingDecision.RejectStage.BLACKLIST, blacklistResult.reason());
        }

        ContentReviewChecker.Result contentResult = contentReviewChecker.check(ctx.getContent(), ctx.getTenantId());
        if (contentResult.blocked()) {
            return RoutingDecision.reject(RoutingDecision.RejectStage.CONTENT_REVIEW, contentResult.reason());
        }

        FrequencyChecker.Result frequencyResult = frequencyChecker.check(ctx);
        if (frequencyResult.blocked()) {
            return RoutingDecision.reject(RoutingDecision.RejectStage.FREQUENCY_LIMIT, frequencyResult.reason());
        }

        return channelSelector.select(ctx)
                .map(channelId -> RoutingDecision.allow(channelId, contentResult.finalContent()))
                .orElseGet(() -> RoutingDecision.reject(
                        RoutingDecision.RejectStage.NO_AVAILABLE_CHANNEL, "无可用通道（所有通道均不可路由）"));
    }
}
