package com.ycsopen.sms.core.service.routing;

import lombok.Builder;
import lombok.Getter;

/**
 * 路由引擎的输入上下文，对应 PRD ycsansms.md 5.5 节流程图里"提交"这一步携带的信息。
 * 手机号已经过 {@link com.ycsopen.sms.core.common.security.HashUtil} 哈希，路由引擎全程不接触明文号码，
 * 只有真正投递给通道连接器前才由上层解密——这是"敏感字段加密收敛到数据访问层"（PRD 6.2.1）的具体体现。
 */
@Getter
@Builder
public class RoutingContext {
    private final Long tenantId;
    private final String mobileHash;
    private final String clientIp;
    private final String operatorHint;   // 号码归属识别结果（F-5.7），可为 null 表示尚未识别
    private final String content;        // 模板 + 变量拼接后的最终文本（见 PRD 检视 Finding #2：变量也要审）
    private final Long templateId;
    private final Long signatureId;
}
