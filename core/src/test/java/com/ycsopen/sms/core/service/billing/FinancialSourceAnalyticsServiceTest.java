package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialSourceAnalyticsServiceTest {
    private JdbcTemplate jdbc;
    private FinancialSourceAnalyticsService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase39-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new FinancialSourceAnalyticsService(jdbc);
    }

    @Test
    void summariesReconcileCostRevenueProfitToSourcesAndPriceVersion() {
        var rows = service.summaries(new FinancialSourceAnalyticsService.FinancialAnalyticsFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 42L, 7L));

        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.tenantId()).isEqualTo(42);
        assertThat(row.channelId()).isEqualTo(7);
        assertThat(row.sourceCount()).isEqualTo(3);
        assertThat(row.billableCount()).isEqualTo(2);
        assertThat(row.providerCostMil()).isEqualTo(120);
        assertThat(row.revenueMil()).isEqualTo(100);
        assertThat(row.profitMil()).isEqualTo(-20);
        assertThat(row.priceBookVersion()).isEqualTo("SMS_STANDARD_V1");
        assertThat(row.unitPriceMil()).isEqualTo(50);
        assertThat(row.formula()).contains("message_tasks.cost");
        assertThat(row.freshnessAt()).isNotNull();
    }

    @Test
    void drilldownShowsImmutableSourcesFormulaAndFreshness() {
        var rows = service.drilldown(new FinancialSourceAnalyticsService.FinancialAnalyticsFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 42L, 7L));

        assertThat(rows).extracting(FinancialSourceAnalyticsService.FinancialSourceRow::messageId)
                .contains("MSG-39-A", "MSG-39-B", "MSG-39-C");
        var delivered = rows.stream().filter(row -> "MSG-39-A".equals(row.messageId())).findFirst().orElseThrow();
        assertThat(delivered.finalStatus()).isEqualTo("DELIVERED");
        assertThat(delivered.providerCostMil()).isEqualTo(40);
        assertThat(delivered.revenueMil()).isEqualTo(50);
        assertThat(delivered.profitMil()).isEqualTo(10);
        assertThat(delivered.formulaVersion()).isEqualTo("FINANCIAL_SOURCE_V1");
    }

    @Test
    void latestReceiptCorrectionChangesAnalyticsIdempotently() {
        assertThat(service.summaries(new FinancialSourceAnalyticsService.FinancialAnalyticsFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 42L, 7L))
                .getFirst().revenueMil()).isEqualTo(100);

        jdbc.update("""
                INSERT INTO delivery_reports(message_id, channel_id, upstream_msg_id, report_status, report_time)
                VALUES ('MSG-39-A', 7, 'UP-39-A-2', 'FAILED', '2026-09-03 12:00:00')
                """);

        var corrected = service.summaries(new FinancialSourceAnalyticsService.FinancialAnalyticsFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 42L, 7L)).getFirst();
        assertThat(corrected.billableCount()).isEqualTo(1);
        assertThat(corrected.revenueMil()).isEqualTo(50);
        assertThat(corrected.profitMil()).isEqualTo(-70);
    }

    @Test
    void missingActiveContractKeepsProviderCostVisibleWithZeroRevenue() {
        jdbc.update("""
                INSERT INTO message_tasks(message_id, tenant_id, mobile_encrypted, mobile_hash, content, send_status,
                    channel_id, cost, created_at, updated_at)
                VALUES ('MSG-39-NO-CONTRACT',77,?, 'hash-77', 'x', 'DELIVERED', 8, 0.0600,
                    '2026-09-03 10:09:00', '2026-09-03 10:10:00')
                """, new byte[]{7});

        var row = service.summaries(new FinancialSourceAnalyticsService.FinancialAnalyticsFilter(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 77L, 8L)).getFirst();

        assertThat(row.sourceCount()).isEqualTo(1);
        assertThat(row.billableCount()).isEqualTo(1);
        assertThat(row.providerCostMil()).isEqualTo(60);
        assertThat(row.revenueMil()).isZero();
        assertThat(row.profitMil()).isEqualTo(-60);
        assertThat(row.priceBookVersion()).isEqualTo("NO_ACTIVE_CONTRACT");
    }

    @Test
    void invalidPeriodIsRejected() {
        assertThatThrownBy(() -> service.summaries(new FinancialSourceAnalyticsService.FinancialAnalyticsFilter(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("财务统计周期不合法");
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE tenant_price_books(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  price_book_version VARCHAR(64) NOT NULL UNIQUE,
                  product_code VARCHAR(64) NOT NULL,
                  unit_price_mil BIGINT NOT NULL,
                  tier_rule_json VARCHAR(1000),
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE tenant_contracts(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL UNIQUE,
                  billing_mode VARCHAR(16) NOT NULL,
                  price_book_version VARCHAR(64) NOT NULL,
                  contract_no VARCHAR(64) NOT NULL UNIQUE,
                  signed_at DATE NOT NULL,
                  attachment_ref VARCHAR(255) NOT NULL,
                  credit_limit_mil BIGINT,
                  billing_period VARCHAR(16),
                  contract_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  approved_by VARCHAR(64) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  message_id VARCHAR(64) NOT NULL UNIQUE,
                  submit_id BIGINT,
                  tenant_id BIGINT NOT NULL,
                  template_id BIGINT,
                  signature_id BIGINT,
                  mobile_encrypted VARBINARY(255) NOT NULL,
                  mobile_hash CHAR(64) NOT NULL,
                  content VARCHAR(600) NOT NULL,
                  send_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  channel_id BIGINT,
                  channel_msg_id VARCHAR(64),
                  operator VARCHAR(32),
                  province VARCHAR(32),
                  city VARCHAR(32),
                  error_code VARCHAR(16),
                  error_message VARCHAR(255),
                  cost DECIMAL(10,4) NOT NULL DEFAULT 0,
                  retry_count INT NOT NULL DEFAULT 0,
                  send_time TIMESTAMP,
                  deliver_time TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  version INT NOT NULL DEFAULT 1
                )
                """);
        jdbc.execute("""
                CREATE TABLE delivery_reports(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  message_id VARCHAR(64) NOT NULL,
                  channel_id BIGINT,
                  upstream_msg_id VARCHAR(64),
                  report_status VARCHAR(32) NOT NULL,
                  error_code VARCHAR(16),
                  raw_payload VARCHAR(1000),
                  report_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO tenant_price_books(price_book_version, product_code, unit_price_mil, status)
                VALUES ('SMS_STANDARD_V1', 'SMS', 50, 'ACTIVE')
                """);
        jdbc.update("""
                INSERT INTO tenant_contracts(tenant_id,billing_mode,price_book_version,contract_no,signed_at,
                    attachment_ref,credit_limit_mil,billing_period,contract_status,approved_by)
                VALUES (42,'POSTPAID','SMS_STANDARD_V1','HT-39','2026-09-01','oss://contract/39.pdf',
                    500000,'MONTHLY','ACTIVE','finance')
                """);
        jdbc.update("""
                INSERT INTO message_tasks(message_id, tenant_id, mobile_encrypted, mobile_hash, content, send_status,
                    channel_id, cost, created_at, updated_at)
                VALUES ('MSG-39-A',42,?, 'hash-a', 'a', 'SENT', 7, 0.0400, '2026-09-03 10:00:00', '2026-09-03 10:01:00'),
                       ('MSG-39-B',42,?, 'hash-b', 'b', 'DELIVERED', 7, 0.0500, '2026-09-03 10:05:00', '2026-09-03 10:06:00'),
                       ('MSG-39-C',42,?, 'hash-c', 'c', 'FAILED', 7, 0.0300, '2026-09-03 10:07:00', '2026-09-03 10:08:00')
                """, new byte[]{1}, new byte[]{2}, new byte[]{3});
        jdbc.update("""
                INSERT INTO delivery_reports(message_id, channel_id, upstream_msg_id, report_status, report_time)
                VALUES ('MSG-39-A', 7, 'UP-39-A-1', 'DELIVERED', '2026-09-03 11:00:00')
                """);
    }
}
