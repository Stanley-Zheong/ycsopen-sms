package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRechargeServiceTest {
    private JdbcTemplate jdbc;
    private TenantRechargeService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase36-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        var prepaid = new TrialPrepaidLedgerService(jdbc);
        service = new TenantRechargeService(jdbc, prepaid);
    }

    @Test
    void tenantSubmitsProtectedRechargeAndSeesPendingState() {
        var record = service.submit(7, new TenantRechargeService.RechargeCommand(
                123_450, "bank_transfer", "BANK-ORDER-20260910-0001", "bank slip oss://proof/1"), "tenant-user");

        assertThat(record.tenantId()).isEqualTo(7);
        assertThat(record.amountMil()).isEqualTo(123_450);
        assertThat(record.rechargeMethod()).isEqualTo("BANK_TRANSFER");
        assertThat(record.transactionRefMask()).isEqualTo("BANK****0001");
        assertThat(record.status()).isEqualTo("PENDING");
        assertThat(record.submitterActor()).isEqualTo("tenant-user");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_recharge_records WHERE transaction_ref_hash <> ?",
                Integer.class, "BANK-ORDER-20260910-0001")).isEqualTo(1);
        assertThat(service.tenantRecords(7)).singleElement().satisfies(row -> {
            assertThat(row.status()).isEqualTo("PENDING");
            assertThat(row.evidenceText()).contains("proof");
        });
    }

    @Test
    void financeApprovalCreditsBalanceExactlyOnceAndWritesAudit() {
        var record = service.submit(7, new TenantRechargeService.RechargeCommand(
                500_000, "ALIPAY", "ALI-RECHARGE-001", "支付宝流水截图"), "tenant-user");

        var approved = service.review(record.id(), new TenantRechargeService.ReviewCommand(true, "到账一致"), "finance");
        var duplicateReview = service.review(record.id(), new TenantRechargeService.ReviewCommand(true, "重复点击"), "finance");

        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(duplicateReview.status()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT balance_mil FROM prepaid_accounts WHERE tenant_id=7", Long.class))
                .isEqualTo(500_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM balance_audit_entries WHERE business_doc_id=?",
                Integer.class, "RECHARGE-" + record.id())).isEqualTo(1);
    }

    @Test
    void rejectionDoesNotCreditAndDuplicateTransactionReferenceFailsSafely() {
        var record = service.submit(8, new TenantRechargeService.RechargeCommand(
                100_000, "WECHAT", "WX-RECHARGE-001", "微信支付凭证"), "tenant-user");

        var rejected = service.review(record.id(), new TenantRechargeService.ReviewCommand(false, "金额未到账"), "finance");

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prepaid_accounts WHERE tenant_id=8", Integer.class))
                .isZero();
        assertThatThrownBy(() -> service.submit(8, new TenantRechargeService.RechargeCommand(
                100_000, "WECHAT", "WX-RECHARGE-001", "重复提交"), "tenant-user"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("充值交易号已存在");
    }

    private void createSchema() {
        jdbc.execute("""
                CREATE TABLE tenant_recharge_records(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  amount_mil BIGINT NOT NULL,
                  recharge_method VARCHAR(32) NOT NULL,
                  transaction_ref_hash CHAR(64) NOT NULL UNIQUE,
                  transaction_ref_mask VARCHAR(64) NOT NULL,
                  evidence_text VARCHAR(500) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  submitter_actor VARCHAR(64) NOT NULL,
                  reviewer_actor VARCHAR(64),
                  review_reason VARCHAR(255),
                  reviewed_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE prepaid_accounts(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL UNIQUE,
                  balance_mil BIGINT NOT NULL,
                  frozen_mil BIGINT NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  version INT NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE balance_audit_entries(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  business_doc_id VARCHAR(64) NOT NULL,
                  mutation_type VARCHAR(32) NOT NULL,
                  amount_mil BIGINT NOT NULL,
                  before_balance_mil BIGINT NOT NULL,
                  after_balance_mil BIGINT NOT NULL,
                  before_frozen_mil BIGINT NOT NULL,
                  after_frozen_mil BIGINT NOT NULL,
                  account_version INT NOT NULL,
                  actor VARCHAR(64) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
