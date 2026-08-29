package com.ycsopen.sms.core.service.routing;

import lombok.Builder;
import lombok.Getter;

/**
 * 路由引擎的输出。allowed=false 时 rejectStage/rejectReason 必须非空，
 * 用于写入 message_submits.reject_reason（PRD 10.7 节），保证"命中原因需在详单中可追溯"（F-5.1 验收标准）。
 */
@Getter
@Builder
public class RoutingDecision {
    private final boolean allowed;
    private final RejectStage rejectStage;
    private final String rejectReason;
    private final Long selectedChannelId;
    /** 内容审核动作为 REPLACE 时，替换后的文本；其余情况等于原文。 */
    private final String finalContent;

    public enum RejectStage {
        BLACKLIST, CONTENT_REVIEW, FREQUENCY_LIMIT, NO_AVAILABLE_CHANNEL
    }

    public static RoutingDecision allow(Long channelId, String finalContent) {
        return RoutingDecision.builder().allowed(true).selectedChannelId(channelId).finalContent(finalContent).build();
    }

    public static RoutingDecision reject(RejectStage stage, String reason) {
        return RoutingDecision.builder().allowed(false).rejectStage(stage).rejectReason(reason).build();
    }
}
