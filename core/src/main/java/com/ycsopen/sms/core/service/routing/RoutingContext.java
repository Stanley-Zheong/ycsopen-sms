package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.persistence.LegacyMobileLookupToken;
import lombok.Builder;
import lombok.Getter;

/**
 * 路由引擎的输入上下文，对应 PRD ycsansms.md 5.5 节流程图里"提交"这一步携带的信息。
 * 手机号只以按密钥版本有序的不透明 HMAC 查询值进入路由链路；本对象不携带手机号明文或原始 SHA-256
 * (never raw SHA-256)。
 */
@Getter
@Builder
public class RoutingContext {
    private final Long tenantId;
    private final BlindIndexPort.OrderedIndexes mobileQueryIndexes;
    private final LegacyMobileLookupToken legacyMobileLookupToken;
    private final String clientIp;
    private final String operatorHint;   // 号码归属识别结果（F-5.7），可为 null 表示尚未识别
    private final String content;        // 模板 + 变量拼接后的最终文本（见 PRD 检视 Finding #2：变量也要审）
    private final Long templateId;
    private final Long signatureId;

    /** Current-version opaque HMAC value for non-legacy routing dimensions. */
    public String getOpaqueMobileQueryValue() {
        if (mobileQueryIndexes == null || mobileQueryIndexes.values().isEmpty()) {
            throw new IllegalStateException("opaque mobile query value is required");
        }
        return mobileQueryIndexes.values().getLast().canonicalValue();
    }

}
