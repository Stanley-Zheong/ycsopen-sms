package com.ycsopen.sms.core.service.tool;

import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService.ImportRequest;
import com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService.MappingRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderStatusTaxonomyServiceTest {
    private JdbcTemplate jdbc;
    private ProviderStatusTaxonomyService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:provider-status-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE provider_status_versions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version_no VARCHAR(32) NOT NULL UNIQUE,
                    status VARCHAR(16) NOT NULL,
                    source_name VARCHAR(64) NOT NULL,
                    effective_at TIMESTAMP NOT NULL,
                    conflict_count INT NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE provider_status_mappings (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version_id BIGINT NOT NULL,
                    provider_name VARCHAR(64) NOT NULL,
                    protocol VARCHAR(16) NOT NULL,
                    provider_code VARCHAR(64) NOT NULL,
                    platform_category VARCHAR(64) NOT NULL,
                    final_state BOOLEAN NOT NULL,
                    billable BOOLEAN NOT NULL,
                    retryable BOOLEAN NOT NULL,
                    severity VARCHAR(16) NOT NULL,
                    advice VARCHAR(255) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE provider_status_normalization_events (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    provider_name VARCHAR(64) NOT NULL,
                    protocol VARCHAR(16) NOT NULL,
                    provider_code VARCHAR(64) NOT NULL,
                    version_no VARCHAR(32) NULL,
                    platform_category VARCHAR(64) NOT NULL,
                    final_state BOOLEAN NOT NULL,
                    billable BOOLEAN NOT NULL,
                    retryable BOOLEAN NOT NULL,
                    severity VARCHAR(16) NOT NULL,
                    advice VARCHAR(255) NOT NULL,
                    source VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE provider_status_export_requests (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    request_id VARCHAR(64) NOT NULL,
                    provider_name VARCHAR(64) NULL,
                    protocol VARCHAR(16) NULL,
                    matched_rows INT NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        service = new ProviderStatusTaxonomyService(jdbc);
    }

    @Test
    void mappedStatusProvidesOneEffectiveFinalBillingRetryAdviceContract() {
        importVersion("ST20260909", "YTO", "HTTP", "DELIVRD", "SUCCESS", true, true, false, "INFO", "确认送达");

        var normalized = service.normalize("YTO", "HTTP", "DELIVRD");

        assertThat(normalized.versionNo()).isEqualTo("ST20260909");
        assertThat(normalized.platformCategory()).isEqualTo("SUCCESS");
        assertThat(normalized.finalState()).isTrue();
        assertThat(normalized.billable()).isTrue();
        assertThat(normalized.retryable()).isFalse();
        assertThat(normalized.advice()).isEqualTo("确认送达");
        assertThat(normalized.source()).isEqualTo("MAPPED");
    }

    @Test
    void newVersionSupersedesOlderMappingsWithoutRewritingHistoricalEvents() {
        importVersion("ST20260909", "YTO", "HTTP", "E001", "FAILURE", true, false, true, "ERROR", "重试");
        service.normalize("YTO", "HTTP", "E001");
        importVersion("ST20260910", "YTO", "HTTP", "E001", "FAILURE", true, false, false, "ERROR", "人工处理");

        var normalized = service.normalize("YTO", "HTTP", "E001");

        assertThat(normalized.versionNo()).isEqualTo("ST20260910");
        assertThat(normalized.retryable()).isFalse();
        List<String> versions = jdbc.queryForList(
                "SELECT version_no FROM provider_status_normalization_events ORDER BY id", String.class);
        assertThat(versions).containsExactly("ST20260909", "ST20260910");
    }

    @Test
    void unknownCodesUseSafeExplicitOperationsFallback() {
        var normalized = service.normalize("YTO", "HTTP", "NEVER_SEEN");

        assertThat(normalized.platformCategory()).isEqualTo("UNKNOWN_REVIEW_REQUIRED");
        assertThat(normalized.finalState()).isFalse();
        assertThat(normalized.billable()).isFalse();
        assertThat(normalized.retryable()).isFalse();
        assertThat(normalized.source()).isEqualTo("UNKNOWN_SAFE_FALLBACK");
    }

    @Test
    void httpAndCmppConsumersUseSamePortContract() {
        service.importMappings(new ImportRequest("ST20260911", "供应商文档", LocalDateTime.now(),
                List.of(new MappingRow("YTO", "HTTP", "DELIVRD", "SUCCESS", true, true, false, "INFO", "HTTP送达"),
                        new MappingRow("YTO", "CMPP", "000", "SUCCESS", true, true, false, "INFO", "CMPP送达"))),
                "operator");
        ProviderStatusTaxonomyPort port = service;

        assertThat(port.normalize("YTO", "HTTP", "DELIVRD").billable()).isTrue();
        assertThat(port.normalize("YTO", "CMPP", "000").billable()).isTrue();
    }

    @Test
    void importRecordsPartialFailureAndExportRequestUsesCurrentMappings() {
        var result = service.importMappings(new ImportRequest("ST20260913", "供应商文档", LocalDateTime.now(),
                List.of(new MappingRow("YTO", "HTTP", "OK", "SUCCESS", true, true, false, "INFO", "成功"),
                        new MappingRow("YTO", "FTP", "BAD", "SUCCESS", true, true, false, "INFO", "非法协议"))), "operator");

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(service.exportRequest("YTO", "HTTP", "operator").matchedRows()).isEqualTo(1);
    }

    private void importVersion(String versionNo, String providerName, String protocol, String code,
                               String category, boolean finalState, boolean billable, boolean retryable,
                               String severity, String advice) {
        service.importMappings(new ImportRequest(versionNo, "供应商文档", LocalDateTime.now(),
                List.of(new MappingRow(providerName, protocol, code, category, finalState, billable, retryable,
                        severity, advice))), "operator");
    }
}
