package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 路由引擎的输入上下文，对应 PRD ycsansms.md 5.5 节流程图里"提交"这一步携带的信息。
 * 手机号只以按密钥版本有序的不透明 HMAC 查询值进入路由链路；本对象不携带手机号明文或原始 SHA-256。
 */
@Getter
@Builder
public class RoutingContext {
    private final Long tenantId;
    private final BlindIndexPort.OrderedIndexes mobileQueryIndexes;
    @Getter(AccessLevel.NONE)
    private final String mobileHash;
    private final String clientIp;
    private final String operatorHint;   // 号码归属识别结果（F-5.7），可为 null 表示尚未识别
    private final String content;        // 模板 + 变量拼接后的最终文本（见 PRD 检视 Finding #2：变量也要审）
    private final Long templateId;
    private final Long signatureId;

    /**
     * Temporary compatibility view for routing consumers owned by Plan 03-10.
     * The returned value is a versioned HMAC member of {@link #mobileQueryIndexes}, never raw SHA-256.
     */
    @Deprecated(forRemoval = true)
    public String getMobileHash() {
        if (mobileQueryIndexes != null) {
            return mobileQueryIndexes.values().getLast().canonicalValue();
        }
        return mobileHash;
    }
}
