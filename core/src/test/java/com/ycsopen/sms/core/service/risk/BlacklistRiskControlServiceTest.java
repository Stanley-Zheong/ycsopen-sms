package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlacklistRiskControlServiceTest {

    @Test
    void managesProtectedEntriesAndAppliesWhitelistPrecedenceBeforeTaskCreation() {
        JdbcTemplate jdbc = jdbc("phase16-manage");
        BlacklistRiskControlService service = service(jdbc);

        var systemBlack = service.createEntry(new BlacklistRiskControlService.BlacklistEntryCreateRequest(
                null, "13900000001", "BLACK", "MANUAL", "系统风险", null), "7");
        var tenantBlack = service.createEntry(new BlacklistRiskControlService.BlacklistEntryCreateRequest(
                42L, "13900000002", "BLACK", "COMPLAINT_LINKED", "投诉风险", null), "7");
        service.createEntry(new BlacklistRiskControlService.BlacklistEntryCreateRequest(
                42L, "13900000001", "WHITE", "MANUAL", "机构白名单", null), "7");

        assertThat(systemBlack.maskedMobile()).isEqualTo("139****0001");
        assertThat(tenantBlack.maskedMobile()).isEqualTo("139****0002");
        assertThat(service.entries("42", null, "ACTIVE")).extracting("listType").contains("BLACK", "WHITE");

        var whiteDecision = service.evaluate(new BlacklistRiskControlService.RiskCheckRequest(
                42L, "req-white", List.of("13900000001"), false), "7").getFirst();
        assertThat(whiteDecision.riskResult()).isEqualTo("ALLOW");
        assertThat(whiteDecision.sourceCategory()).isEqualTo("WHITELIST");
        assertThat(whiteDecision.mobileRef()).startsWith("opaque-");
        assertThat(whiteDecision.taskCreated()).isFalse();
        assertThat(whiteDecision.charged()).isFalse();

        var tenantDecision = service.evaluate(new BlacklistRiskControlService.RiskCheckRequest(
                42L, "req-tenant", List.of("13900000002"), false), "7").getFirst();
        assertThat(tenantDecision.riskResult()).isEqualTo("BLOCK");
        assertThat(tenantDecision.sourceCategory()).isEqualTo("TENANT_BLACKLIST");

        service.disableEntry(tenantBlack.id());
        assertThat(service.entries("42", "BLACK", "DISABLED")).hasSize(1);
    }

    @Test
    void expiredWhitelistDoesNotBypassActiveBlacklist() {
        JdbcTemplate jdbc = jdbc("phase16-expiry");
        BlacklistRiskControlService service = service(jdbc);
        service.createEntry(new BlacklistRiskControlService.BlacklistEntryCreateRequest(
                null, "13900000004", "BLACK", "MANUAL", "系统风险", null), "7");
        service.createEntry(new BlacklistRiskControlService.BlacklistEntryCreateRequest(
                42L, "13900000004", "WHITE", "MANUAL", "已过期白名单",
                java.time.LocalDateTime.now().minusDays(1)), "7");

        var decision = service.evaluate(new BlacklistRiskControlService.RiskCheckRequest(
                42L, "req-expired-white", List.of("13900000004"), false), "7").getFirst();

        assertThat(decision.sourceCategory()).isEqualTo("SYSTEM_BLACKLIST");
        assertThat(decision.riskResult()).isEqualTo("BLOCK");
    }

    @Test
    void configuresProviderRecordsBatchAndDegradedDecisionsAndAppealsImmutableHistory() {
        JdbcTemplate jdbc = jdbc("phase16-provider");
        BlacklistRiskControlService service = service(jdbc);
        service.saveProvider(new BlacklistRiskControlService.RiskProviderConfigRequest(
                "local-risk", "https://risk.example.test", "secret-ref", "ADVANCED",
                80, 500, "CACHE", "ACTIVE", 300), "7");

        var decisions = service.evaluate(new BlacklistRiskControlService.RiskCheckRequest(
                42L, "req-risk", List.of("13900009999", "13900000005"), false), "7");
        assertThat(decisions).hasSize(2);
        assertThat(decisions).anySatisfy(row -> {
            assertThat(row.mobileRef()).startsWith("opaque-");
            assertThat(row.riskResult()).isEqualTo("BLOCK");
            assertThat(row.sourceCategory()).isEqualTo("THIRD_PARTY_RISK");
        });
        assertThat(jdbc.queryForList("SELECT DISTINCT request_kind,item_count FROM third_party_risk_check_logs"))
                .contains(java.util.Map.of("REQUEST_KIND", "BATCH", "ITEM_COUNT", 2));

        var degraded = service.evaluate(new BlacklistRiskControlService.RiskCheckRequest(
                42L, "req-degraded", List.of("13900000006"), true), "7").getFirst();
        assertThat(degraded.riskResult()).isEqualTo("DEGRADED_CACHE");
        assertThat(degraded.sourceCategory()).isEqualTo("THIRD_PARTY_DEGRADED");

        var analytics = service.analytics();
        assertThat(analytics.total()).isEqualTo(3);
        assertThat(analytics.blocked()).isEqualTo(1);
        assertThat(analytics.degraded()).isEqualTo(1);

        var appeal = service.appeal(new BlacklistRiskControlService.AppealRequest(decisions.getFirst().id(), "误判标记"), "7");
        assertThat(appeal.originalResult()).isEqualTo("BLOCK");
        assertThat(service.analytics().appeals()).isEqualTo(1);

        assertThatThrownBy(() -> service.evaluate(new BlacklistRiskControlService.RiskCheckRequest(
                42L, "too-large", java.util.Collections.nCopies(501, "13900000007"), false), "7"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("RISK_CHECK_BATCH_TOO_LARGE"));
        assertThatThrownBy(() -> service.evaluate(new BlacklistRiskControlService.RiskCheckRequest(
                42L, "dup", List.of("13900000008", "13900000008"), false), "7"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("RISK_CHECK_DUPLICATE_REF"));
        assertThatThrownBy(() -> service.appeal(new BlacklistRiskControlService.AppealRequest(9999L, "误判标记"), "7"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("RISK_DECISION_NOT_FOUND"));
    }

    @Test
    void importProducesPartialFailureEvidenceAndExportRequestDoesNotCreateFiles() {
        JdbcTemplate jdbc = jdbc("phase16-import");
        BlacklistRiskControlService service = service(jdbc);

        var result = service.importEntries(new BlacklistRiskControlService.BlacklistImportRequest(
                42L, List.of("13900000003", "bad-mobile"), "BLACK", "BATCH_IMPORT", "批量导入", null), "7");
        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);

        var export = service.exportRequest("42", "BLACK");
        assertThat(export.status()).isEqualTo("REQUESTED");
        assertThat(export.matchedRows()).isEqualTo(1);
    }

    private static BlacklistRiskControlService service(JdbcTemplate jdbc) {
        return new BlacklistRiskControlService(jdbc, (tenantId, mobile, listType, source, reason) -> {
            long id = jdbc.queryForObject("SELECT COALESCE(MAX(id),0)+1 FROM blacklist_entries", Long.class);
            jdbc.update("""
                    INSERT INTO blacklist_entries(id,tenant_id,masked_mobile,mobile_hash,list_type,reason,source,status,created_by)
                    VALUES (?,?,?,?,?,?,?,'ACTIVE','test')
                    """, id, tenantId, mask(mobile), "ref-" + mobile, listType, reason, source);
            return id;
        }, id -> jdbc.update("UPDATE blacklist_entries SET status='DISABLED' WHERE id=?", id),
                (tenantId, requestId, normalizedMobile) -> {
                    List<BlacklistRiskControlService.RiskLookupResult> matches = jdbc.query("""
                            SELECT tenant_id,list_type,status FROM blacklist_entries
                            WHERE mobile_hash=? AND status='ACTIVE'
                              AND effective_at <= CURRENT_TIMESTAMP
                              AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
                            ORDER BY CASE WHEN tenant_id=? AND list_type='WHITE' THEN 0
                                          WHEN tenant_id IS NULL AND list_type='BLACK' THEN 1
                                          WHEN tenant_id=? AND list_type='BLACK' THEN 2 ELSE 3 END
                            """, (rs, row) -> {
                        Long rowTenant = rs.getObject("tenant_id", Long.class);
                        String listType = rs.getString("list_type");
                        String opaque = "opaque-" + normalizedMobile.substring(7);
                        if (Long.valueOf(tenantId).equals(rowTenant) && "WHITE".equals(listType)) {
                            return new BlacklistRiskControlService.RiskLookupResult(opaque, "WHITELIST", "ALLOW",
                                    "机构白名单命中，跳过黑名单和第三方风险");
                        }
                        if (rowTenant == null && "BLACK".equals(listType)) {
                            return new BlacklistRiskControlService.RiskLookupResult(opaque, "SYSTEM_BLACKLIST", "BLOCK",
                                    "系统级黑名单命中，发送任务未创建且未计费");
                        }
                        if (Long.valueOf(tenantId).equals(rowTenant) && "BLACK".equals(listType)) {
                            return new BlacklistRiskControlService.RiskLookupResult(opaque, "TENANT_BLACKLIST", "BLOCK",
                                    "机构级黑名单命中，发送任务未创建且未计费");
                        }
                        return new BlacklistRiskControlService.RiskLookupResult(opaque, "NO_MATCH", "ALLOW", "未命中黑白名单");
                    }, "ref-" + normalizedMobile, tenantId, tenantId);
                    return matches.isEmpty()
                            ? new BlacklistRiskControlService.RiskLookupResult(
                            "opaque-" + normalizedMobile.substring(7), "NO_MATCH", "ALLOW", "未命中黑白名单")
                            : matches.getFirst();
                });
    }

    private static String mask(String mobile) {
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private static JdbcTemplate jdbc(String name) {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc(name);
        jdbc.execute("""
                CREATE TABLE blacklist_entries (
                    id BIGINT PRIMARY KEY,
                    tenant_id BIGINT NULL,
                    mobile_encrypted VARBINARY(255),
                    masked_mobile VARCHAR(32) NOT NULL DEFAULT '***',
                    mobile_hash VARCHAR(128) NOT NULL,
                    list_type VARCHAR(16) NOT NULL,
                    reason VARCHAR(255),
                    source VARCHAR(32) NOT NULL,
                    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                    created_by VARCHAR(64),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    effective_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NULL
                )
                """);
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
        jdbc.execute("""
                CREATE TABLE risk_intercept_decisions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    request_id VARCHAR(64) NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    mobile_ref VARCHAR(128) NOT NULL,
                    source_category VARCHAR(32) NOT NULL,
                    risk_result VARCHAR(32) NOT NULL,
                    trace_reason VARCHAR(255) NOT NULL,
                    task_created BOOLEAN NOT NULL DEFAULT FALSE,
                    charged BOOLEAN NOT NULL DEFAULT FALSE,
                    actor VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(request_id, mobile_ref)
                )
                """);
        jdbc.execute("""
                CREATE TABLE risk_intercept_appeals (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    decision_id BIGINT NOT NULL,
                    original_result VARCHAR(32) NOT NULL,
                    appeal_result VARCHAR(32) NOT NULL,
                    reason VARCHAR(255) NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        return jdbc;
    }
}
