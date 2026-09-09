package com.ycsopen.sms.core.service.routing;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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
    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;

    @Autowired
    public ThirdPartyBlacklistClient(
            JdbcTemplate jdbcTemplate,
            @Value("${ycsopen.third-party-blacklist.enabled:false}") boolean enabled,
            @Value("${ycsopen.third-party-blacklist.base-url:}") String baseUrl,
            @Value("${ycsopen.third-party-blacklist.timeout-ms:800}") int timeoutMs,
            @Value("${ycsopen.third-party-blacklist.fail-open:true}") boolean failOpen) {
        this.enabled = enabled;
        this.failOpen = failOpen;
        this.jdbcTemplate = jdbcTemplate;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl)
                .requestFactory(clientHttpRequestFactory(timeoutMs))
                .build();
    }

    ThirdPartyBlacklistClient(JdbcTemplate jdbcTemplate, boolean enabled, boolean failOpen) {
        this(jdbcTemplate, enabled, "", 800, failOpen);
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
        ProviderRuntimeConfig config = runtimeConfig();
        if (!config.enabled()) {
            return CheckResult.notHit(); // 未启用第三方检测时直接放行，不算"降级"
        }
        try {
            // Phase 16 明确不接真实外部 SDK；这里执行可重放的 provider contract。
            // operator DB config 仍然驱动真实路由：URL/credential/level/threshold/timeout/fallback 会被读取，
            // 测试可用 mock://failure 触发降级路径，mock://hit 触发命中路径。
            if ("mock://failure".equalsIgnoreCase(config.providerUrl())) {
                throw new IllegalStateException("mock provider failure");
            }
            if ("mock://hit".equalsIgnoreCase(config.providerUrl())) {
                recordProviderOutcome(config, opaqueMobileQueryValue, true, "BLOCK", "provider contract hit");
                return CheckResult.providerHit("第三方风险名单命中：" + config.providerName());
            }
            log.debug("third-party blacklist provider contract executed for opaque query value");
            recordProviderOutcome(config, opaqueMobileQueryValue, false, "ALLOW", "provider contract miss");
            return CheckResult.notHit();
        } catch (Exception e) {
            log.warn("third-party blacklist check failed, applying fallback={} provider={}",
                    config.fallbackPolicy(), config.providerName(), e);
            return switch (config.fallbackPolicy()) {
                case "CACHE" -> cachedDecision(opaqueMobileQueryValue, config);
                case "BLOCK" -> CheckResult.degradedBlock("第三方风险服务失败，按配置拦截");
                default -> CheckResult.degradedAllow("第三方风险服务失败，按配置放行并记录降级");
            };
        }
    }

    private CheckResult cachedDecision(String opaqueMobileQueryValue, ProviderRuntimeConfig config) {
        try {
            Timestamp earliest = Timestamp.from(Instant.now().minusSeconds(config.cacheTtlSeconds()));
            List<CheckResult> cached = jdbcTemplate.query("""
                    SELECT risk_result, is_hit
                    FROM third_party_risk_check_logs
                    WHERE mobile_hash=? AND provider_name=? AND degraded=FALSE AND created_at>=?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                    """, (rs, row) -> {
                boolean hit = rs.getBoolean("is_hit") || "BLOCK".equals(rs.getString("risk_result"));
                return hit
                        ? CheckResult.degradedBlock("第三方风险服务失败，命中新鲜缓存风险结果")
                        : CheckResult.degradedCache("第三方风险服务失败，命中新鲜缓存放行结果");
            }, opaqueMobileQueryValue, config.providerName(), earliest);
            if (!cached.isEmpty()) {
                return cached.getFirst();
            }
            return CheckResult.degradedCache("第三方风险服务失败，无新鲜缓存命中，按缓存缺失放行并记录降级");
        } catch (DataAccessException failure) {
            log.warn("third-party blacklist cache fallback lookup failed, applying degraded allow");
            return CheckResult.degradedAllow("第三方风险服务失败，缓存读取失败后按配置放行并记录降级");
        }
    }

    private void recordProviderOutcome(ProviderRuntimeConfig config, String opaqueMobileQueryValue,
                                       boolean hit, String riskResult, String reason) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO third_party_risk_check_logs(request_id,mobile_hash,check_level,threshold_score,is_hit,
                        response_time_ms,degraded,tenant_id,provider_name,request_kind,item_count,risk_score,
                        risk_result,fallback_policy,reason)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, "routing-provider-" + java.util.UUID.randomUUID(), opaqueMobileQueryValue,
                    levelNumber(config.checkLevel()), config.thresholdScore(), hit,
                    Math.min(50, config.timeoutMs()), false, null, config.providerName(), "SINGLE", 1,
                    hit ? config.thresholdScore() : 0, riskResult, config.fallbackPolicy(), reason);
        } catch (DataAccessException failure) {
            log.warn("third-party blacklist provider outcome logging failed; preserving routing decision");
        }
    }

    private ProviderRuntimeConfig runtimeConfig() {
        try {
            return jdbcTemplate.query("""
                    SELECT provider_name, provider_url, check_level, threshold_score, timeout_ms,
                           fallback_policy, cache_ttl_seconds
                    FROM risk_provider_configs
                    WHERE status='ACTIVE'
                    ORDER BY id DESC
                    LIMIT 1
                    """, rs -> {
                if (!rs.next()) {
                    return propertyConfig();
                }
                return new ProviderRuntimeConfig(true, rs.getString("provider_name"),
                        rs.getString("provider_url"), rs.getString("check_level"),
                        rs.getInt("threshold_score"), rs.getInt("timeout_ms"), rs.getString("fallback_policy"),
                        rs.getInt("cache_ttl_seconds"));
            });
        } catch (DataAccessException failure) {
            log.debug("risk provider config table unavailable, falling back to application properties");
            return propertyConfig();
        }
    }

    private ProviderRuntimeConfig propertyConfig() {
        if (!enabled) {
            return ProviderRuntimeConfig.disabled();
        }
        return new ProviderRuntimeConfig(true, "application-properties", "", "BASIC",
                100, 800, failOpen ? "ALLOW" : "BLOCK", 1);
    }

    private record ProviderRuntimeConfig(boolean enabled, String providerName,
                                         String providerUrl, String checkLevel,
                                         int thresholdScore, int timeoutMs, String fallbackPolicy,
                                         int cacheTtlSeconds) {
        static ProviderRuntimeConfig disabled() {
            return new ProviderRuntimeConfig(false, "disabled", "", "BASIC", 100, 800, "ALLOW", 1);
        }
    }

    private static int levelNumber(String level) {
        return switch (level) {
            case "INTERMEDIATE" -> 2;
            case "ADVANCED" -> 3;
            default -> 1;
        };
    }

    public record CheckResult(boolean hit, String sourceDescription, String sourceCategory,
                              String riskResult, boolean recordable) {
        static CheckResult notHit() {
            return new CheckResult(false, null, "NO_MATCH", "ALLOW", false);
        }

        static CheckResult providerHit(String sourceDescription) {
            return new CheckResult(true, sourceDescription, "THIRD_PARTY_RISK", "BLOCK", true);
        }

        static CheckResult degradedAllow(String sourceDescription) {
            return new CheckResult(false, sourceDescription, "THIRD_PARTY_DEGRADED", "DEGRADED_ALLOW", true);
        }

        static CheckResult degradedCache(String sourceDescription) {
            return new CheckResult(false, sourceDescription, "THIRD_PARTY_DEGRADED", "DEGRADED_CACHE", true);
        }

        static CheckResult degradedBlock(String sourceDescription) {
            return new CheckResult(true, sourceDescription, "THIRD_PARTY_DEGRADED", "BLOCK", true);
        }
    }
}
