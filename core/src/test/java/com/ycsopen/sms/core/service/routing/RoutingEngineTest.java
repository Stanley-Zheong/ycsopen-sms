package com.ycsopen.sms.core.service.routing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 覆盖 ycsansms.md 5.5 节路由决策链的"任一命中即拦截"约束，以及 F-5.1 验收标准
 * ——命中黑名单必须直接拒绝、不产生发送任务。
 * <p>本测试用 Mockito 隔离四个检查器，只验证 {@link RoutingEngine} 的编排顺序与短路逻辑，
 * 各检查器自身的规则细节由各自的单元测试覆盖（见同目录下的其他 *Test 类，逐步补齐）。</p>
 */
@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {

    @Mock BlacklistChecker blacklistChecker;
    @Mock ContentReviewChecker contentReviewChecker;
    @Mock FrequencyChecker frequencyChecker;
    @Mock ChannelSelector channelSelector;

    private RoutingEngine newEngine() {
        return new RoutingEngine(blacklistChecker, contentReviewChecker, frequencyChecker, channelSelector);
    }

    private RoutingContext sampleContext() {
        return RoutingContext.builder()
                .tenantId(1L)
                .clientIp("127.0.0.1")
                .content("【优创硕安】您的验证码是：123456，5分钟内有效。")
                .build();
    }

    @Test
    void blacklistHit_shouldRejectImmediately_andNeverCallContentReviewOrFrequency() {
        when(blacklistChecker.check(any())).thenReturn(new BlacklistChecker.Result(true, "机构级黑名单命中"));

        RoutingDecision decision = newEngine().route(sampleContext());

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getRejectStage()).isEqualTo(RoutingDecision.RejectStage.BLACKLIST);
        assertThat(decision.getRejectReason()).contains("黑名单");
        org.mockito.Mockito.verifyNoInteractions(contentReviewChecker, frequencyChecker, channelSelector);
    }

    @Test
    void contentReviewHit_shouldRejectAfterBlacklistPasses_beforeFrequencyCheck() {
        when(blacklistChecker.check(any())).thenReturn(BlacklistChecker.Result.pass());
        when(contentReviewChecker.check(any(), any())).thenReturn(ContentReviewChecker.Result.blocked("命中内容审核词库"));

        RoutingDecision decision = newEngine().route(sampleContext());

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getRejectStage()).isEqualTo(RoutingDecision.RejectStage.CONTENT_REVIEW);
        org.mockito.Mockito.verifyNoInteractions(frequencyChecker, channelSelector);
    }

    @Test
    void frequencyLimitHit_shouldRejectAfterEarlierStagesPass() {
        when(blacklistChecker.check(any())).thenReturn(BlacklistChecker.Result.pass());
        when(contentReviewChecker.check(any(), any())).thenReturn(ContentReviewChecker.Result.pass("最终文本"));
        when(frequencyChecker.check(any())).thenReturn(new FrequencyChecker.Result(true, "1分钟内超过10次"));

        RoutingDecision decision = newEngine().route(sampleContext());

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getRejectStage()).isEqualTo(RoutingDecision.RejectStage.FREQUENCY_LIMIT);
        org.mockito.Mockito.verifyNoInteractions(channelSelector);
    }

    @Test
    void allChecksPass_butNoChannelAvailable_shouldRejectWithNoAvailableChannel() {
        when(blacklistChecker.check(any())).thenReturn(BlacklistChecker.Result.pass());
        when(contentReviewChecker.check(any(), any())).thenReturn(ContentReviewChecker.Result.pass("最终文本"));
        when(frequencyChecker.check(any())).thenReturn(FrequencyChecker.Result.pass());
        when(channelSelector.select(any())).thenReturn(Optional.empty());

        RoutingDecision decision = newEngine().route(sampleContext());

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getRejectStage()).isEqualTo(RoutingDecision.RejectStage.NO_AVAILABLE_CHANNEL);
    }

    @Test
    void allChecksPass_andChannelAvailable_shouldAllowWithSelectedChannelAndFinalContent() {
        when(blacklistChecker.check(any())).thenReturn(BlacklistChecker.Result.pass());
        when(contentReviewChecker.check(any(), any())).thenReturn(ContentReviewChecker.Result.pass("敏感词已替换后的文本"));
        when(frequencyChecker.check(any())).thenReturn(FrequencyChecker.Result.pass());
        when(channelSelector.select(any())).thenReturn(Optional.of(42L));

        RoutingDecision decision = newEngine().route(sampleContext());

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.getSelectedChannelId()).isEqualTo(42L);
        assertThat(decision.getFinalContent()).isEqualTo("敏感词已替换后的文本");
    }

    // Mockito's any() import shortcut
    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
