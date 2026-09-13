package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeeWarningCreditServiceTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private FeeWarningCreditService service;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase40-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false")
                .build();
        jdbc = new JdbcTemplate(database);
        createSchema();
        service = new FeeWarningCreditService(jdbc);
    }

    @Test
    void prepaidAmountWarningCreatesOneEpisodeWithDeliveryEvidenceAndDedupe() {
        jdbc.update("""
                INSERT INTO prepaid_accounts(tenant_id,balance_mil,frozen_mil,status,version)
                VALUES (7,1000,0,'NORMAL',0)
                """);
        service.saveRule(new FeeWarningCreditService.RuleCommand(
                "预付费低余额", 7L, "PREPAID_AMOUNT", new BigDecimal("1500"),
                "WARN_ONLY", "[\"SMS\",\"EMAIL\"]", "[\"tenant:7\",\"finance\"]", "ACTIVE"), "finance");

        List<FeeWarningCreditService.EpisodeRow> first = service.evaluateTenant(7, 50, "finance");
        List<FeeWarningCreditService.EpisodeRow> second = service.evaluateTenant(7, 50, "finance");

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(first.get(0).metricType()).isEqualTo("PREPAID_AMOUNT");
        assertThat(first.get(0).sourceAmountMil()).isEqualTo(1000);
        assertThat(first.get(0).action()).isEqualTo("WARN_ONLY");
        assertThat(first.get(0).deliveryState()).isEqualTo("DELIVERED");
        assertThat(count("fee_warning_episodes")).isEqualTo(1);
        assertThat(count("alert_records")).isEqualTo(1);
        assertThat(count("alert_delivery_attempts")).isEqualTo(2);
    }

    @Test
    void postpaidCreditRatioManualApprovalBlocksUntilAuthorizedApproval() {
        postpaidTenant(8, 1000);
        jdbc.update("""
                INSERT INTO postpaid_usage_ledger(tenant_id,business_doc_id,billing_period,period_start,period_end,amount_mil,state,actor)
                VALUES (8,'MSG-A','MONTHLY',CURRENT_DATE,DATEADD('DAY', 30, CURRENT_DATE),900,'RECORDED','billing')
                """);
        service.saveRule(new FeeWarningCreditService.RuleCommand(
                "授信比例预警", 8L, "POSTPAID_CREDIT_RATIO", new BigDecimal("0.85"),
                "MANUAL_APPROVAL", "[\"EMAIL\"]", "[\"finance\",\"operations\"]", "ACTIVE"), "finance");

        service.evaluateTenant(8, 50, "finance");

        assertThatThrownBy(() -> service.assertSubmissionAllowed(8))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("FEE_WARNING_MANUAL_APPROVAL_REQUIRED");

        FeeWarningCreditService.EpisodeRow approved = service.approveEpisode(1, "允许本次提交", "finance");

        assertThat(approved.approvalState()).isEqualTo("APPROVED");
        service.assertSubmissionAllowed(8);
    }

    @Test
    void postpaidCreditBlockActionRejectsIngressWithSingleBlockedEpisode() {
        postpaidTenant(9, 1000);
        jdbc.update("""
                INSERT INTO postpaid_usage_ledger(tenant_id,business_doc_id,billing_period,period_start,period_end,amount_mil,state,actor)
                VALUES (9,'MSG-B','MONTHLY',CURRENT_DATE,DATEADD('DAY', 30, CURRENT_DATE),980,'RECORDED','billing')
                """);
        service.saveRule(new FeeWarningCreditService.RuleCommand(
                "授信封停", 9L, "POSTPAID_CREDIT_RATIO", new BigDecimal("0.95"),
                "BLOCK", "[\"EMAIL\"]", "[\"finance\"]", "ACTIVE"), "finance");

        assertThatThrownBy(() -> service.enforceSubmission(9, 50, "message-submit"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("FEE_WARNING_CREDIT_BLOCKED");
        assertThatThrownBy(() -> service.enforceSubmission(9, 50, "message-submit"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo("FEE_WARNING_CREDIT_BLOCKED");
        assertThat(count("fee_warning_episodes")).isEqualTo(1);
    }

    private void postpaidTenant(long tenantId, long creditLimitMil) {
        jdbc.update("""
                INSERT INTO tenant_contracts(tenant_id,billing_mode,price_book_version,contract_no,signed_at,
                    attachment_ref,credit_limit_mil,billing_period,contract_status,approved_by)
                VALUES (?,'POSTPAID','SMS_STANDARD_V1',?,CURRENT_DATE,'contract.pdf',?,'MONTHLY','ACTIVE','finance')
                """, tenantId, "C-" + tenantId, creditLimitMil);
    }

    private int count(String table) {
        Integer value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private void createSchema() {
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
                  contract_status VARCHAR(32) NOT NULL,
                  approved_by VARCHAR(64) NOT NULL
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
                  state VARCHAR(32) NOT NULL,
                  actor VARCHAR(64) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE alert_rules(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  rule_name VARCHAR(128) NOT NULL,
                  rule_type VARCHAR(32) NOT NULL,
                  metric_name VARCHAR(64) NOT NULL,
                  metric_source VARCHAR(64) NOT NULL,
                  threshold_value DECIMAL(12,4) NOT NULL,
                  comparison_op VARCHAR(8) NOT NULL,
                  duration_minutes INT NOT NULL,
                  severity VARCHAR(16) NOT NULL,
                  notify_channels VARCHAR(1000),
                  notification_targets VARCHAR(1000),
                  source_scope VARCHAR(64) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  created_by VARCHAR(64),
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE alert_records(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  rule_id BIGINT NOT NULL,
                  title VARCHAR(255) NOT NULL,
                  content VARCHAR(1000),
                  metric_value DECIMAL(12,4),
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  severity VARCHAR(16) NOT NULL DEFAULT 'MEDIUM',
                  source_module VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
                  source_key VARCHAR(128) NOT NULL DEFAULT 'UNKNOWN',
                  impact_scope VARCHAR(255),
                  triggered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  acknowledged_at TIMESTAMP,
                  acknowledged_by VARCHAR(64),
                  resolved_at TIMESTAMP,
                  resolved_by VARCHAR(64),
                  resolution_note VARCHAR(255),
                  delivery_state VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  muted_until TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE alert_delivery_attempts(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  alert_record_id BIGINT NOT NULL,
                  channel VARCHAR(32) NOT NULL,
                  target_snapshot VARCHAR(500) NOT NULL,
                  provider_result VARCHAR(64) NOT NULL,
                  retry_count INT NOT NULL DEFAULT 0,
                  status VARCHAR(32) NOT NULL,
                  failure_reason VARCHAR(255),
                  attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE fee_warning_rules(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  rule_name VARCHAR(128) NOT NULL,
                  tenant_id BIGINT,
                  metric_type VARCHAR(32) NOT NULL,
                  threshold_value DECIMAL(12,4) NOT NULL,
                  action VARCHAR(32) NOT NULL,
                  notify_channels VARCHAR(500) NOT NULL,
                  notification_targets VARCHAR(500) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  created_by VARCHAR(64) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE fee_warning_episodes(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  rule_id BIGINT NOT NULL,
                  alert_record_id BIGINT,
                  metric_type VARCHAR(32) NOT NULL,
                  source_key VARCHAR(128) NOT NULL UNIQUE,
                  source_amount_mil BIGINT NOT NULL,
                  credit_limit_mil BIGINT,
                  used_amount_mil BIGINT,
                  threshold_value DECIMAL(12,4) NOT NULL,
                  ratio DECIMAL(12,4),
                  action VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  approval_state VARCHAR(32) NOT NULL,
                  delivery_state VARCHAR(32) NOT NULL,
                  source_snapshot VARCHAR(1000) NOT NULL,
                  actor VARCHAR(64) NOT NULL,
                  resolution_note VARCHAR(255),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
