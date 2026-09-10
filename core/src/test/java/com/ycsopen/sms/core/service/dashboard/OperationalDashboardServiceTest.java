package com.ycsopen.sms.core.service.dashboard;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalDashboardServiceTest {
    private JdbcTemplate jdbc;
    private OperationalDashboardService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase44-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        seedData();
        service = new OperationalDashboardService(jdbc);
    }

    @Test
    void buildsPlatformDashboardFromSourceBackedAggregatesAndOperationalTables() {
        var dashboard = service.platformDashboard();

        assertThat(dashboard.realtime().totalUsers()).isEqualTo(3);
        assertThat(dashboard.realtime().todayMessages()).isEqualTo(150);
        assertThat(dashboard.realtime().successRate()).isEqualByComparingTo("0.9000");
        assertThat(dashboard.realtime().activeTenants()).isEqualTo(1);
        assertThat(dashboard.kpi().todayRevenue()).isEqualByComparingTo("1.2300");
        assertThat(dashboard.hourlyTrend()).hasSize(2);
        assertThat(dashboard.tenantRank().getFirst().tenantId()).isEqualTo(7L);
        assertThat(dashboard.channelHealth().normal()).isEqualTo(1);
        assertThat(dashboard.channelHealth().maintenance()).isEqualTo(1);
        assertThat(dashboard.channelHealth().abnormal()).isEqualTo(1);
        assertThat(dashboard.financeWarning().warningCount()).isEqualTo(1);
        assertThat(dashboard.source().registry()).isEqualTo("statistics_aggregates");
        assertThat(dashboard.source().formula()).contains("success_count/send_count");
        assertThat(dashboard.source().freshnessAt()).isEqualTo(LocalDateTime.of(2026, 9, 10, 9, 5));
        assertThat(dashboard.source().permissionScope()).isEqualTo("PLATFORM");
    }

    @Test
    void tenantOverviewIsPinnedToActorTenantAndIncludesUsageBalanceContractAndServiceStatus() {
        var overview = service.tenantOverview(OperationalDashboardService.Actor.tenant("tenant-7", 7L), null);

        assertThat(overview.tenantId()).isEqualTo(7L);
        assertThat(overview.balanceMil()).isEqualTo(120000);
        assertThat(overview.trialStatus()).isEqualTo("TRIAL");
        assertThat(overview.contractStatus()).isEqualTo("ACTIVE");
        assertThat(overview.todayMessages()).isEqualTo(100);
        assertThat(overview.successRate()).isEqualByComparingTo("0.9000");
        assertThat(overview.serviceStatus()).isEqualTo("NORMAL");
        assertThat(overview.source().permissionScope()).isEqualTo("TENANT");
    }

    @Test
    void tenantOverviewRejectsCrossTenantAccess() {
        assertThatThrownBy(() -> service.tenantOverview(OperationalDashboardService.Actor.tenant("tenant-7", 7L), 8L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户范围无权访问");
    }

    @Test
    void resourceAndChannelStatisticsExposeFormulaFreshnessAndAccessibleRows() {
        var stats = service.resourceStatistics(OperationalDashboardService.Actor.platform("finance"), null);

        assertThat(stats.resources()).hasSize(1);
        assertThat(stats.resources().getFirst().signatureId()).isEqualTo(55L);
        assertThat(stats.resources().getFirst().templateId()).isEqualTo(66L);
        assertThat(stats.channelComparisons()).hasSize(2);
        assertThat(stats.channelComparisons().getFirst().channelId()).isEqualTo(11L);
        assertThat(stats.accessibleColumns()).contains("tenant_id", "signature_id", "template_id", "success_count");
        assertThat(stats.source().formula()).contains("RESOURCE_USAGE");
        assertThat(stats.empty()).isFalse();
        assertThat(stats.errorState()).isEqualTo("NONE");
    }

    @Test
    void apiStatusUsesLiveSourceRowsAndDrilldownKeys() {
        var status = service.apiStatus();

        assertThat(status.rows()).hasSize(3);
        assertThat(status.rows()).extracting(OperationalDashboardService.HealthRow::component)
                .contains("DATABASE", "PROVIDER", "CHANNEL");
        assertThat(status.rows().getFirst().drilldownKey()).startsWith("health|");
        assertThat(status.source().registry()).isEqualTo("operational_source_tables");
    }

    @Test
    void roleConfigurationKeepsTenantCardsTenantOnly() {
        var config = service.saveConfiguration(new OperationalDashboardService.DashboardConfigurationCommand(
                "TENANT_ADMIN", true, true, "MANUAL", 300, "0.0030"), "admin");

        assertThat(config.role()).isEqualTo("TENANT_ADMIN");
        assertThat(config.globalCards()).isFalse();
        assertThat(config.tenantCards()).isTrue();
        assertThat(service.configuration("TENANT_ADMIN").complaintThreshold()).isEqualTo("0.0030");
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, user_type VARCHAR(32), status VARCHAR(16))");
        jdbc.execute("CREATE TABLE tenants(id BIGINT PRIMARY KEY, lifecycle_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE prepaid_accounts(tenant_id BIGINT PRIMARY KEY, balance_mil BIGINT, frozen_mil BIGINT, status VARCHAR(32), version INT, updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE tenant_contracts(tenant_id BIGINT PRIMARY KEY, contract_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE trial_accounts(tenant_id BIGINT PRIMARY KEY, status VARCHAR(32), quota_remaining INT)");
        jdbc.execute("CREATE TABLE channels(id BIGINT PRIMARY KEY, status VARCHAR(32))");
        jdbc.execute("CREATE TABLE fee_warning_episodes(id BIGINT PRIMARY KEY, tenant_id BIGINT, status VARCHAR(32), updated_at TIMESTAMP)");
        jdbc.execute("""
                CREATE TABLE statistics_metric_registry(
                  metric_code VARCHAR(64) PRIMARY KEY,
                  metric_name VARCHAR(128),
                  source_tables VARCHAR(255),
                  formula VARCHAR(255),
                  freshness_rule VARCHAR(128),
                  permission_scope VARCHAR(32),
                  formula_version VARCHAR(32),
                  status VARCHAR(16),
                  updated_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE statistics_aggregates(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  metric_code VARCHAR(64),
                  bucket_start TIMESTAMP,
                  bucket_date DATE,
                  tenant_id BIGINT,
                  channel_id BIGINT,
                  carrier VARCHAR(32),
                  message_type VARCHAR(32),
                  province VARCHAR(64),
                  city VARCHAR(64),
                  signature_id BIGINT,
                  template_id BIGINT,
                  submit_count INT,
                  accepted_count INT,
                  rejected_count INT,
                  send_count INT,
                  success_count INT,
                  failure_count INT,
                  fee_amount DECIMAL(18,4),
                  avg_response_ms BIGINT,
                  source_version BIGINT,
                  correction_identity VARCHAR(128),
                  drilldown_key VARCHAR(255),
                  formula_version VARCHAR(32),
                  freshness_at TIMESTAMP,
                  quality_state VARCHAR(16)
                )
                """);
        jdbc.execute("""
                CREATE TABLE operational_dashboard_configs(
                  role_code VARCHAR(32) PRIMARY KEY,
                  global_cards BOOLEAN NOT NULL,
                  tenant_cards BOOLEAN NOT NULL,
                  refresh_mode VARCHAR(16) NOT NULL,
                  polling_seconds INT NOT NULL,
                  complaint_threshold VARCHAR(32) NOT NULL,
                  updated_by VARCHAR(128) NOT NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void seedData() {
        jdbc.update("INSERT INTO users VALUES (1,'ADMIN','ACTIVE'),(2,'TENANT_ADMIN','ACTIVE'),(3,'OPERATOR','DISABLED')");
        jdbc.update("INSERT INTO tenants VALUES (7,'TRIAL'),(8,'FROZEN')");
        jdbc.update("INSERT INTO prepaid_accounts VALUES (7,120000,0,'NORMAL',1,?)", LocalDateTime.of(2026, 9, 10, 9, 2));
        jdbc.update("INSERT INTO tenant_contracts VALUES (7,'ACTIVE')");
        jdbc.update("INSERT INTO trial_accounts VALUES (7,'TRIAL',98)");
        jdbc.update("INSERT INTO channels VALUES (11,'NORMAL'),(12,'MAINTENANCE'),(13,'ABNORMAL')");
        jdbc.update("INSERT INTO fee_warning_episodes VALUES (1,7,'ACTIVE',?)",
                LocalDateTime.of(2026, 9, 10, 9, 6));
        jdbc.update("""
                INSERT INTO statistics_metric_registry VALUES
                ('CHANNEL_DELIVERY','通道指标','message_tasks,delivery_reports,billing_records','success_count/send_count by channel','freshness','PLATFORM','v1','ACTIVE',?),
                ('TENANT_BEHAVIOR','租户指标','message_submits,message_tasks,billing_records','success_count/send_count by tenant','freshness','TENANT','v1','ACTIVE',?),
                ('RESOURCE_USAGE','资源指标','message_submits,message_tasks,delivery_reports','RESOURCE_USAGE success/reject by signature/template','freshness','TENANT','v1','ACTIVE',?)
                """, LocalDateTime.of(2026, 9, 10, 9, 0), LocalDateTime.of(2026, 9, 10, 9, 0),
                LocalDateTime.of(2026, 9, 10, 9, 0));
        jdbc.update("""
                INSERT INTO statistics_aggregates(metric_code,bucket_start,bucket_date,tenant_id,channel_id,carrier,
                    message_type,province,city,signature_id,template_id,submit_count,accepted_count,rejected_count,
                    send_count,success_count,failure_count,fee_amount,avg_response_ms,source_version,correction_identity,
                    drilldown_key,formula_version,freshness_at,quality_state)
                VALUES
                ('CHANNEL_DELIVERY',?,?,7,11,'MOBILE','VERIFY','广东','深圳',55,66,100,100,0,100,90,10,1.0000,30,1,'c1','channel|11|09','v1',?,'FRESH'),
                ('CHANNEL_DELIVERY',?,?,8,12,'UNICOM','VERIFY','北京','北京',55,66,50,50,0,50,45,5,0.2300,40,1,'c2','channel|12|09','v1',?,'FRESH'),
                ('TENANT_BEHAVIOR',?,?,7,NULL,NULL,'VERIFY',NULL,NULL,NULL,NULL,100,100,0,100,90,10,1.0000,30,1,'t1','tenant|7|09','v1',?,'FRESH'),
                ('RESOURCE_USAGE',?,?,7,NULL,NULL,'VERIFY',NULL,NULL,55,66,100,95,5,100,90,10,1.0000,30,1,'r1','resource|55|66','v1',?,'FRESH')
                """, LocalDateTime.of(2026, 9, 10, 9, 0), LocalDate.of(2026, 9, 10),
                LocalDateTime.of(2026, 9, 10, 9, 5),
                LocalDateTime.of(2026, 9, 10, 8, 0), LocalDate.of(2026, 9, 10),
                LocalDateTime.of(2026, 9, 10, 8, 5),
                LocalDateTime.of(2026, 9, 10, 9, 0), LocalDate.of(2026, 9, 10),
                LocalDateTime.of(2026, 9, 10, 9, 4),
                LocalDateTime.of(2026, 9, 10, 9, 0), LocalDate.of(2026, 9, 10),
                LocalDateTime.of(2026, 9, 10, 9, 4));
    }
}
