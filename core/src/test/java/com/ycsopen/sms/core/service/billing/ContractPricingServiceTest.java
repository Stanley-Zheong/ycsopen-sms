package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractPricingServiceTest {
    private JdbcTemplate jdbc;
    private ContractPricingService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase37-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new ContractPricingService(jdbc);
    }

    @Test
    void postpaidContractRequiresCreditPeriodAndChangesTenantToContracted() {
        var contract = service.approveContract(7, new ContractPricingService.ContractCommand(
                "POSTPAID", "SMS_STANDARD_V1", "HT-2026-0001", LocalDate.of(2026, 9, 10),
                "oss://contracts/HT-2026-0001.pdf", 1_000_000L, "MONTHLY"), "operator");

        assertThat(contract.billingMode()).isEqualTo("POSTPAID");
        assertThat(contract.creditLimitMil()).isEqualTo(1_000_000L);
        assertThat(contract.billingPeriod()).isEqualTo("MONTHLY");
        assertThat(jdbc.queryForObject("SELECT status FROM trial_accounts WHERE tenant_id=7", String.class))
                .isEqualTo("CONTRACTED");
        assertThat(service.overview(7).tenantState()).isEqualTo("CONTRACTED");
    }

    @Test
    void prepaidContractRejectsPostpaidFieldsAndPostpaidRequiresThem() {
        assertThatThrownBy(() -> service.approveContract(7, new ContractPricingService.ContractCommand(
                "POSTPAID", "SMS_STANDARD_V1", "HT-POSTPAID-MISSING", LocalDate.of(2026, 9, 10),
                "oss://contracts/missing.pdf", null, "MONTHLY"), "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("后付费授信额度必须为正");

        assertThatThrownBy(() -> service.approveContract(7, new ContractPricingService.ContractCommand(
                "PREPAID", "SMS_STANDARD_V1", "HT-PREPAID-BAD", LocalDate.of(2026, 9, 10),
                "oss://contracts/prepaid.pdf", 1_000_000L, "MONTHLY"), "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预付费合同不能填写授信额度或账期");
    }

    @Test
    void postpaidUsageConsumesEffectivePeriodCreditCeilingSafely() {
        service.approveContract(7, new ContractPricingService.ContractCommand(
                "POSTPAID", "SMS_STANDARD_V1", "HT-2026-0002", LocalDate.of(2026, 9, 10),
                "oss://contracts/HT-2026-0002.pdf", 500_000L, "MONTHLY"), "operator");

        var first = service.recordPostpaidUsage(7, new ContractPricingService.PostpaidUsageCommand("MSG-37-A", 200_000), "billing");
        var duplicate = service.recordPostpaidUsage(7, new ContractPricingService.PostpaidUsageCommand("MSG-37-A", 200_000), "billing");

        assertThat(first.businessDocId()).isEqualTo("MSG-37-A");
        assertThat(duplicate.businessDocId()).isEqualTo("MSG-37-A");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM postpaid_usage_ledger", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.recordPostpaidUsage(7,
                new ContractPricingService.PostpaidUsageCommand("MSG-37-B", 400_001), "billing"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("后付费授信额度不足");
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
        jdbc.execute("INSERT INTO tenant_price_books(price_book_version, product_code, unit_price_mil, status) VALUES ('SMS_STANDARD_V1','SMS',50,'ACTIVE')");
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
                CREATE TABLE postpaid_usage_ledger(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  business_doc_id VARCHAR(64) NOT NULL UNIQUE,
                  billing_period VARCHAR(16) NOT NULL,
                  period_start DATE NOT NULL,
                  period_end DATE NOT NULL,
                  amount_mil BIGINT NOT NULL,
                  state VARCHAR(32) NOT NULL DEFAULT 'RECORDED',
                  actor VARCHAR(64) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE trial_accounts(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL UNIQUE,
                  status VARCHAR(32) NOT NULL,
                  quota_total INT NOT NULL,
                  quota_remaining INT NOT NULL,
                  start_at TIMESTAMP NOT NULL,
                  end_at TIMESTAMP NOT NULL,
                  version INT NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO trial_accounts(tenant_id,status,quota_total,quota_remaining,start_at,end_at,version)
                VALUES (7,'TRIAL_FROZEN',500,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1)
                """);
    }
}
