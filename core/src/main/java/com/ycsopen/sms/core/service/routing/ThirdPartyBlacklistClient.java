package com.ycsopen.sms.core.service.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * F-5.3 第三方风险名单服务对接（参考 ycsansms.md 9.3 节接口约定，形如"泰迪短信黑名单"）。
 * <p>接口协议：POST {baseUrl}/api/check/v2/forbid，见 PRD 9.3 节；本类只做单号检测，
 * 批量接口 {@code /batch/forbid} 见 core/docs/API.md 的 TODO 列表。</p>
 * <p><b>超时降级策略是这里的核心设计点</b>：外部依赖不可用时，按 {@code fail-open} 配置决定放行还是拦截，
 * 避免第三方服务故障拖垮整条发送链路（PRD 5.15 节"第三方风控/黑名单服务异常"处理规范）。</p>
 */
@Component
public class ThirdPartyBlacklistClient {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyBlacklistClient.class);

    private final boolean enabled;
    private final boolean failOpen;
    private final RestClient restClient;

    public ThirdPartyBlacklistClient(
            @Value("${ycsopen.third-party-blacklist.enabled:false}") boolean enabled,
            @Value("${ycsopen.third-party-blacklist.base-url:}") String baseUrl,
            @Value("${ycsopen.third-party-blacklist.timeout-ms:800}") int timeoutMs,
            @Value("${ycsopen.third-party-blacklist.fail-open:true}") boolean failOpen) {
        this.enabled = enabled;
        this.failOpen = failOpen;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl)
                .requestFactory(clientHttpRequestFactory(timeoutMs))
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory clientHttpRequestFactory(int timeoutMs) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return factory;
    }

    /**
     * @param opaqueMobileQueryValue 版本化 HMAC 查询值；不接收手机号明文或原始 SHA-256。
     */
    public CheckResult check(String opaqueMobileQueryValue) {
        if (!enabled) {
            return CheckResult.notHit(); // 未启用第三方检测时直接放行，不算"降级"
        }
        try {
            // TODO(F-5.3): 补全真实请求体（appId/callee/level/score/timestamp/sign）与响应解析，
            // 当前先返回"未命中"占位，保证路由链路可跑通，便于先验证黑名单/内容审核/频控三段逻辑。
            log.debug("third-party blacklist check called with opaque query value (stub, not yet wired to real endpoint)");
            return CheckResult.notHit();
        } catch (Exception e) {
            log.warn("third-party blacklist check failed, applying fail-open={} degrade policy", failOpen, e);
            return failOpen ? CheckResult.notHit() : CheckResult.degradedBlock();
        }
    }

    public record CheckResult(boolean hit, String sourceDescription) {
        static CheckResult notHit() { return new CheckResult(false, null); }
        static CheckResult degradedBlock() { return new CheckResult(true, "第三方服务超时降级：按拦截策略处理"); }
    }
}
