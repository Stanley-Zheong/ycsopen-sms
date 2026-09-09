package com.ycsopen.sms.core.service.routing;

import com.ycsopen.sms.core.service.channel.health.ChannelHealthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ThirdPartyBlacklistClientTest {

    @Test
    void activeProviderConfigDrivesRoutingDegradedFallbackContract() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("phase16-routing-provider-config");
        createSchema(jdbc);
        jdbc.update("""
                INSERT INTO risk_provider_configs(provider_name,provider_url,credential_ref,check_level,threshold_score,
                    timeout_ms,fallback_policy,status,cache_ttl_seconds,created_by)
                VALUES ('route-risk','mock://failure','secret-ref','ADVANCED',80,500,'CACHE','ACTIVE',300,'test')
                """);

        ThirdPartyBlacklistClient client = new ThirdPartyBlacklistClient(jdbc, false, true);

        ThirdPartyBlacklistClient.CheckResult result = client.check("opaque-mobile-ref");

        assertThat(result.hit()).isFalse();
        assertThat(result.recordable()).isTrue();
        assertThat(result.sourceCategory()).isEqualTo("THIRD_PARTY_DEGRADED");
        assertThat(result.riskResult()).isEqualTo("DEGRADED_CACHE");
        assertThat(result.sourceDescription()).contains("缓存缺失放行");
    }

    @Test
    void cacheFallbackAppliesFreshCachedHitForRouting() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("phase16-routing-provider-cache-hit");
        createSchema(jdbc);
        jdbc.update("""
                INSERT INTO risk_provider_configs(provider_name,provider_url,credential_ref,check_level,threshold_score,
                    timeout_ms,fallback_policy,status,cache_ttl_seconds,created_by)
                VALUES ('route-risk','mock://failure','secret-ref','ADVANCED',80,500,'CACHE','ACTIVE',300,'test')
                """);
        jdbc.update("""
                INSERT INTO third_party_risk_check_logs(request_id,mobile_hash,check_level,threshold_score,is_hit,
                    response_time_ms,degraded,tenant_id,provider_name,request_kind,item_count,risk_score,
                    risk_result,fallback_policy,reason)
                VALUES ('cached-hit','opaque-mobile-ref',3,80,TRUE,20,FALSE,42,'route-risk','SINGLE',1,95,
                    'BLOCK','CACHE','fresh provider hit')
                """);

        ThirdPartyBlacklistClient client = new ThirdPartyBlacklistClient(jdbc, false, true);

        ThirdPartyBlacklistClient.CheckResult result = client.check("opaque-mobile-ref");

        assertThat(result.hit()).isTrue();
        assertThat(result.recordable()).isTrue();
        assertThat(result.sourceCategory()).isEqualTo("THIRD_PARTY_DEGRADED");
        assertThat(result.riskResult()).isEqualTo("BLOCK");
        assertThat(result.sourceDescription()).contains("新鲜缓存风险结果");
    }

    @Test
    void providerSuccessWarmsFreshCacheForLaterFailure() {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc("phase16-routing-provider-cache-warm");
        createSchema(jdbc);
        jdbc.update("""
                INSERT INTO risk_provider_configs(provider_name,provider_url,credential_ref,check_level,threshold_score,
                    timeout_ms,fallback_policy,status,cache_ttl_seconds,created_by)
                VALUES ('route-risk','mock://hit','secret-ref','ADVANCED',80,500,'CACHE','ACTIVE',300,'test')
                """);
        ThirdPartyBlacklistClient client = new ThirdPartyBlacklistClient(jdbc, false, true);

        ThirdPartyBlacklistClient.CheckResult hit = client.check("opaque-mobile-ref");
        assertThat(hit.hit()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM third_party_risk_check_logs
                WHERE provider_name='route-risk' AND mobile_hash='opaque-mobile-ref' AND degraded=FALSE
                """, Integer.class)).isEqualTo(1);

        jdbc.update("UPDATE risk_provider_configs SET provider_url='mock://failure' WHERE provider_name='route-risk'");
        ThirdPartyBlacklistClient.CheckResult cached = client.check("opaque-mobile-ref");

        assertThat(cached.hit()).isTrue();
        assertThat(cached.sourceCategory()).isEqualTo("THIRD_PARTY_DEGRADED");
        assertThat(cached.riskResult()).isEqualTo("BLOCK");
        assertThat(cached.sourceDescription()).contains("新鲜缓存风险结果");
    }

    private static void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE risk_provider_configs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    provider_name VARCHAR(64) NOT NULL UNIQUE,
                    provider_url VARCHAR(255) NOT NULL,
                    credential_ref VARCHAR(128) NOT NULL,
                    check_level VARCHAR(32) NOT NULL,
                    threshold_score INT NOT NULL,
                    timeout_ms INT NOT NULL,
                    fallback_policy VARCHAR(16) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    cache_ttl_seconds INT NOT NULL,
                    created_by VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE third_party_risk_check_logs (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    request_id VARCHAR(64) NOT NULL,
                    mobile_hash VARCHAR(128) NOT NULL,
                    check_level INT NOT NULL,
                    threshold_score INT NOT NULL,
                    is_hit BOOLEAN NOT NULL,
                    response_time_ms INT,
                    degraded BOOLEAN NOT NULL,
                    tenant_id BIGINT,
                    provider_name VARCHAR(64),
                    request_kind VARCHAR(16),
                    item_count INT,
                    risk_score INT,
                    risk_result VARCHAR(32),
                    fallback_policy VARCHAR(16),
                    reason VARCHAR(255),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
