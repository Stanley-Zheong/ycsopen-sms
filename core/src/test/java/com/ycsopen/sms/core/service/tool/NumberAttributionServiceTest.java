package com.ycsopen.sms.core.service.tool;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.PrefixImportRequest;
import com.ycsopen.sms.core.service.tool.NumberAttributionService.PrefixRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumberAttributionServiceTest {
    private JdbcTemplate jdbc;
    private NumberAttributionService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:number-attribution-" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE number_prefix_versions (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version_no VARCHAR(32) NOT NULL UNIQUE,
                    update_type VARCHAR(16) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    source_name VARCHAR(64) NOT NULL,
                    total_rows INT NOT NULL,
                    conflict_count INT NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    activated_at TIMESTAMP NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE number_prefix_mappings (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    version_id BIGINT NOT NULL,
                    prefix VARCHAR(7) NOT NULL,
                    carrier VARCHAR(16) NOT NULL,
                    province VARCHAR(32) NOT NULL,
                    city VARCHAR(32) NOT NULL,
                    source_name VARCHAR(64) NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE mobile_portability (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    mobile_hash VARCHAR(64) NOT NULL UNIQUE,
                    masked_mobile VARCHAR(32) NOT NULL,
                    original_carrier VARCHAR(16) NOT NULL,
                    current_carrier VARCHAR(16) NOT NULL,
                    ported_at DATE NULL,
                    source_name VARCHAR(64) NOT NULL,
                    freshness_expires_at TIMESTAMP NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        service = new NumberAttributionService(jdbc);
    }

    @Test
    void longestPrefixReturnsCarrierProvinceCityFromVersionedMappings() {
        var result = service.importPrefixes(new PrefixImportRequest("V20260909", "FULL", "官方号段",
                List.of(new PrefixRow("139", "MOBILE", "广东", "广州"),
                        new PrefixRow("1391234", "UNICOM", "广东", "深圳"))), "operator");

        assertThat(result.success()).isEqualTo(2);
        var lookup = service.lookup("13912345678", false);
        assertThat(lookup.carrier()).isEqualTo("UNICOM");
        assertThat(lookup.prefixCarrier()).isEqualTo("UNICOM");
        assertThat(lookup.province()).isEqualTo("广东");
        assertThat(lookup.city()).isEqualTo("深圳");
        assertThat(lookup.source()).isEqualTo("PREFIX");
    }

    @Test
    void currentPortabilityCacheOverridesPrefixOnlyWithinFreshness() {
        service.importPrefixes(new PrefixImportRequest("V20260910", "FULL", "官方号段",
                List.of(new PrefixRow("139", "MOBILE", "广东", "广州"))), "operator");
        jdbc.update("""
                INSERT INTO mobile_portability(mobile_hash, masked_mobile, original_carrier, current_carrier,
                    source_name, freshness_expires_at, status)
                VALUES (?, '139****0001', 'MOBILE', 'TELECOM', '携转缓存', ?, 'ACTIVE')
                """, "d786bae3d6432af2ab50bdad0f9644e434f4686a3ff37d6db5037b00d2b08750",
                LocalDateTime.now().plusHours(1));

        var lookup = service.lookup("13900000001", false);

        assertThat(lookup.carrier()).isEqualTo("TELECOM");
        assertThat(lookup.prefixCarrier()).isEqualTo("MOBILE");
        assertThat(lookup.source()).isEqualTo("PORTABILITY_CACHE");
        assertThat(lookup.sourceName()).isEqualTo("携转缓存");
    }

    @Test
    void providerFailureReturnsSourceLabelledPrefixFallback() {
        service.importPrefixes(new PrefixImportRequest("V20260911", "FULL", "官方号段",
                List.of(new PrefixRow("139", "MOBILE", "广东", "广州"))), "operator");

        var lookup = service.lookup("13900000002", true);

        assertThat(lookup.carrier()).isEqualTo("MOBILE");
        assertThat(lookup.source()).isEqualTo("PREFIX_FALLBACK_DEGRADED");
        assertThat(lookup.providerFailure()).isTrue();
    }

    @Test
    void importValidatesPrefixAndRecordsPartialFailureEvidence() {
        var result = service.importPrefixes(new PrefixImportRequest("V20260912", "INCREMENTAL", "官方号段",
                List.of(new PrefixRow("12", "MOBILE", "广东", "广州"),
                        new PrefixRow("138", "MOBILE", "广东", "广州"))), "operator");

        assertThat(result.success()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        Integer conflictCount = jdbc.queryForObject(
                "SELECT conflict_count FROM number_prefix_versions WHERE version_no='V20260912'", Integer.class);
        assertThat(conflictCount).isEqualTo(1);
    }

    @Test
    void invalidMobileIsRejectedBeforeLookup() {
        assertThatThrownBy(() -> service.lookup("not-a-mobile", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("手机号");
    }
}
