package com.ycsopen.sms.core.service.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomReportServiceTest {
    private JdbcTemplate jdbc;
    private CustomReportService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase43-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new CustomReportService(jdbc, new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void rejectsUnsupportedDimensionsAndMeasuresBeforeQueryingAggregates() {
        var command = new CustomReportService.ReportCommand("异常字段报表", "CHANNEL_DELIVERY",
                List.of("mobile"), List.of("success_count"), 7L, 11L, null, null,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0),
                "PLATFORM");

        assertThatThrownBy(() -> service.preview(command, CustomReportService.Actor.platform("admin")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表维度不支持");
    }

    @Test
    void capabilitiesHideActiveRegistryMetricsWithoutSupportedAuthoringContract() {
        jdbc.update("""
                INSERT INTO statistics_metric_registry(metric_code, metric_name, source_tables, formula,
                    freshness_rule, permission_scope, formula_version, status)
                VALUES ('UNMAPPED_METRIC','未映射指标','source_table','custom formula','freshness','PLATFORM','v1','ACTIVE')
                """);

        assertThat(service.capabilities()).extracting(CustomReportService.CapabilityRow::metricCode)
                .containsExactly("CHANNEL_DELIVERY", "TENANT_BEHAVIOR");
    }

    @Test
    void previewsAuthorizedAggregateRowsWithFormulaFreshnessQualityAndAccessibleColumns() {
        var row = service.preview(new CustomReportService.ReportCommand("通道日报", "CHANNEL_DELIVERY",
                List.of("period", "tenant_id", "channel_id", "carrier", "province", "message_type"),
                List.of("send_count", "success_count", "fee_amount"), 7L, 11L, "verify", "广东",
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0),
                "PLATFORM"), CustomReportService.Actor.platform("finance"));

        assertThat(row.metricCode()).isEqualTo("CHANNEL_DELIVERY");
        assertThat(row.formulaVersion()).isEqualTo("v1");
        assertThat(row.formula()).contains("send/success/failure");
        assertThat(row.freshnessAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 1, 3));
        assertThat(row.qualityState()).isEqualTo("FRESH");
        assertThat(row.rows()).hasSize(1);
        assertThat(row.rows().get(0).values()).containsEntry("period", "2026-09-01");
        assertThat(row.rows().get(0).values()).containsEntry("tenant_id", 7L);
        assertThat(row.rows().get(0).values()).containsEntry("channel_id", 11L);
        assertThat(row.rows().get(0).values()).containsEntry("carrier", "MOBILE");
        assertThat(row.rows().get(0).values()).containsEntry("province", "广东");
        assertThat(row.rows().get(0).values()).containsEntry("message_type", "VERIFY");
        assertThat(row.rows().get(0).values()).containsEntry("send_count", 20);
        assertThat(row.rows().get(0).values()).containsEntry("success_count", 18);
        assertThat(row.rows().get(0).values()).containsEntry("fee_amount", new BigDecimal("0.3200"));
        assertThat(row.rows().get(0).drilldownKey()).isEqualTo("CHANNEL_DELIVERY|2026-09-01T01:00|7|11|-|-|MOBILE|VERIFY|广东|深圳");
        assertThat(row.accessibleColumns()).containsExactly("period", "tenant_id", "channel_id", "carrier",
                "province", "message_type", "send_count", "success_count", "fee_amount");
    }

    @Test
    void tenantScopedMetricRequiresExplicitTenantFilter() {
        assertThatThrownBy(() -> service.preview(new CustomReportService.ReportCommand("租户发送", "TENANT_BEHAVIOR",
                List.of("period", "tenant_id", "message_type"), List.of("submit_count"), null, null,
                null, null, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 2, 0, 0), "TENANT"), CustomReportService.Actor.platform("finance")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户指标必须限定租户范围");
    }

    @Test
    void tenantActorCannotPreviewAnotherTenantScope() {
        assertThatThrownBy(() -> service.preview(new CustomReportService.ReportCommand("租户发送", "TENANT_BEHAVIOR",
                List.of("period", "tenant_id", "message_type"), List.of("submit_count"), 8L, null,
                null, null, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 2, 0, 0), "TENANT"), CustomReportService.Actor.tenant("tenant-7", 7L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户范围无权访问");
    }

    @Test
    void tenantActorSavedDefinitionsAreForcedToTenantRoleScope() {
        var saved = service.save(new CustomReportService.ReportCommand("租户发送日报", "TENANT_BEHAVIOR",
                List.of("period", "tenant_id", "message_type"), List.of("submit_count", "success_count"),
                7L, null, null, null, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 2, 0, 0), "PLATFORM"), CustomReportService.Actor.tenant("tenant-7", 7L));

        assertThat(saved.roleScope()).isEqualTo("TENANT");
        assertThat(saved.definitionSnapshot()).contains("\"roleScope\":\"TENANT\"");
    }

    @Test
    void rejectsReportNamesLongerThanStorageLimit() {
        String longName = "报".repeat(129);

        assertThatThrownBy(() -> service.preview(new CustomReportService.ReportCommand(longName, "CHANNEL_DELIVERY",
                List.of("period", "tenant_id"), List.of("send_count"), 7L, null, null, null,
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0),
                "PLATFORM"), CustomReportService.Actor.platform("finance")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报表名称不能超过128个字符");
    }

    @Test
    void previewReportsWhenRowsAreTruncatedAtFiveHundred() {
        for (int index = 0; index < 500; index++) {
            jdbc.update("""
                    INSERT INTO statistics_aggregates(metric_code, bucket_start, bucket_date, tenant_id, channel_id,
                        carrier, message_type, province, city, submit_count, send_count, success_count, fee_amount,
                        correction_identity, drilldown_key, formula_version, freshness_at, quality_state)
                    VALUES ('CHANNEL_DELIVERY', ?, ?, 7, 11, 'MOBILE', 'VERIFY', '广东', '深圳', 22, 20, 18, 0.3200,
                        ?, ?, 'v1', ?, 'FRESH')
                    """, LocalDateTime.of(2026, 9, 1, 2, 0).plusMinutes(index),
                    LocalDate.of(2026, 9, 1), "identity-43-extra-" + index,
                    "drill-43-extra-" + index, LocalDateTime.of(2026, 9, 1, 2, 3));
        }

        var result = service.preview(new CustomReportService.ReportCommand("通道日报", "CHANNEL_DELIVERY",
                List.of("period", "tenant_id"), List.of("send_count"), 7L, 11L, null, "广东",
                LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0),
                "PLATFORM"), CustomReportService.Actor.platform("finance"));

        assertThat(result.rows()).hasSize(500);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void savedDefinitionsAndExportRequestsKeepImmutableSnapshots() {
        var command = new CustomReportService.ReportCommand("租户发送日报", "TENANT_BEHAVIOR",
                List.of("period", "tenant_id", "message_type"), List.of("submit_count", "success_count"),
                7L, null, null, null, LocalDateTime.of(2026, 9, 1, 0, 0),
                LocalDateTime.of(2026, 9, 2, 0, 0), "TENANT");

        var saved = service.save(command, CustomReportService.Actor.tenant("tenant-7", 7L));
        jdbc.update("UPDATE statistics_metric_registry SET formula='mutated formula', formula_version='v2' WHERE metric_code='TENANT_BEHAVIOR'");
        var export = service.requestExport(saved.id(), CustomReportService.Actor.tenant("tenant-7", 7L));

        assertThat(saved.definitionSnapshot()).contains("\"formulaVersion\":\"v1\"");
        assertThat(saved.definitionSnapshot()).contains("accepted/rejected/send/success");
        assertThat(export.definitionSnapshot()).isEqualTo(saved.definitionSnapshot());
        assertThat(export.status()).isEqualTo("REQUESTED");
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE statistics_metric_registry(
                  metric_code VARCHAR(64) PRIMARY KEY,
                  metric_name VARCHAR(128) NOT NULL,
                  source_tables VARCHAR(255) NOT NULL,
                  formula VARCHAR(255) NOT NULL,
                  freshness_rule VARCHAR(128) NOT NULL,
                  permission_scope VARCHAR(32) NOT NULL DEFAULT 'PLATFORM',
                  formula_version VARCHAR(32) NOT NULL DEFAULT 'v1',
                  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO statistics_metric_registry(metric_code, metric_name, source_tables, formula,
                    freshness_rule, permission_scope, formula_version, status)
                VALUES ('CHANNEL_DELIVERY','通道发送成功成本延迟指标','message_tasks,delivery_reports,billing_records',
                        'send/success/failure/cost/latency grouped by channel and geography','freshness','PLATFORM','v1','ACTIVE'),
                       ('TENANT_BEHAVIOR','租户发送消费活跃指标','message_submits,message_tasks,billing_records',
                        'accepted/rejected/send/success/failure/consumption grouped by tenant','freshness','TENANT','v1','ACTIVE')
                """);
        jdbc.execute("""
                CREATE TABLE statistics_aggregates(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  metric_code VARCHAR(64) NOT NULL,
                  bucket_grain VARCHAR(8) NOT NULL DEFAULT 'HOUR',
                  bucket_start TIMESTAMP NOT NULL,
                  bucket_date DATE NOT NULL,
                  tenant_id BIGINT,
                  channel_id BIGINT,
                  carrier VARCHAR(32),
                  message_type VARCHAR(32),
                  province VARCHAR(64),
                  city VARCHAR(64),
                  signature_id BIGINT,
                  template_id BIGINT,
                  submit_count INT NOT NULL DEFAULT 0,
                  accepted_count INT NOT NULL DEFAULT 0,
                  rejected_count INT NOT NULL DEFAULT 0,
                  send_count INT NOT NULL DEFAULT 0,
                  success_count INT NOT NULL DEFAULT 0,
                  failure_count INT NOT NULL DEFAULT 0,
                  fee_amount DECIMAL(18,4) NOT NULL DEFAULT 0,
                  avg_response_ms BIGINT NOT NULL DEFAULT 0,
                  source_version BIGINT NOT NULL DEFAULT 0,
                  correction_identity VARCHAR(128) NOT NULL,
                  drilldown_key VARCHAR(255) NOT NULL,
                  formula_version VARCHAR(32) NOT NULL DEFAULT 'v1',
                  freshness_at TIMESTAMP NOT NULL,
                  quality_state VARCHAR(16) NOT NULL DEFAULT 'FRESH'
                )
                """);
        jdbc.update("""
                INSERT INTO statistics_aggregates(metric_code, bucket_start, bucket_date, tenant_id, channel_id,
                    carrier, message_type, province, city, submit_count, send_count, success_count, fee_amount,
                    correction_identity, drilldown_key, formula_version, freshness_at, quality_state)
                VALUES ('CHANNEL_DELIVERY', ?, ?, 7, 11, 'MOBILE', 'VERIFY', '广东', '深圳', 22, 20, 18, 0.3200,
                    'identity-43', 'CHANNEL_DELIVERY|2026-09-01T01:00|7|11|-|-|MOBILE|VERIFY|广东|深圳',
                    'v1', ?, 'FRESH')
                """, LocalDateTime.of(2026, 9, 1, 1, 0), LocalDate.of(2026, 9, 1),
                LocalDateTime.of(2026, 9, 1, 1, 3));
        jdbc.execute("""
                CREATE TABLE custom_report_definitions(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  report_name VARCHAR(128) NOT NULL,
                  metric_code VARCHAR(64) NOT NULL,
                  tenant_id BIGINT,
                  role_scope VARCHAR(32) NOT NULL,
                  dimensions_json VARCHAR(1000) NOT NULL,
                  measures_json VARCHAR(1000) NOT NULL,
                  filters_json VARCHAR(1000) NOT NULL,
                  definition_snapshot VARCHAR(4000) NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                  created_by VARCHAR(128) NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE custom_report_export_requests(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  report_definition_id BIGINT NOT NULL,
                  definition_snapshot VARCHAR(4000) NOT NULL,
                  status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
                  requested_by VARCHAR(128) NOT NULL,
                  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
