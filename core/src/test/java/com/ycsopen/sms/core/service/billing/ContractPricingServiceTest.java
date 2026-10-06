package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractPricingServiceTest {
    private JdbcTemplate jdbc;
    private ContractPricingService service;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
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
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=7", String.class))
                .isEqualTo("SIGNED");
        assertThat(jdbc.queryForObject("SELECT billing_mode FROM tenants WHERE id=7", String.class))
                .isEqualTo("POSTPAID");
        assertThat(service.overview(7)).satisfies(overview -> {
            assertThat(overview.tenantState()).isEqualTo("CONTRACTED");
            assertThat(overview.billingMode()).isEqualTo("POSTPAID");
            assertThat(overview.priceBookVersion()).isEqualTo("SMS_STANDARD_V1");
            assertThat(overview.contractNo()).isEqualTo("HT-2026-0001");
            assertThat(overview.signedAt()).isEqualTo(LocalDate.of(2026, 9, 10));
            assertThat(overview.attachmentRef()).isEqualTo("oss://contracts/HT-2026-0001.pdf");
            assertThat(overview.creditLimitMil()).isEqualTo(1_000_000L);
            assertThat(overview.billingPeriod()).isEqualTo("MONTHLY");
            assertThat(overview.approvedBy()).isEqualTo("operator");
        });
    }

    @Test
    void workbenchFiltersAndAggregatesOnlyThePersistedTrialWindow() {
        jdbc.update("INSERT INTO message_tasks(tenant_id,send_status,created_at,updated_at) VALUES (7,'DELIVERED','2026-09-05 10:00:00','2026-09-05 10:01:00')");
        jdbc.update("INSERT INTO message_tasks(tenant_id,send_status,created_at,updated_at) VALUES (7,'FAILED','2026-09-06 10:00:00','2026-09-06 10:01:00')");
        jdbc.update("INSERT INTO message_tasks(tenant_id,send_status,created_at,updated_at) VALUES (7,'DELIVERED','2026-10-01 10:00:00','2026-10-01 10:01:00')");
        jdbc.update("INSERT INTO message_tasks(tenant_id,send_status,created_at,updated_at) VALUES (7,'DELIVERED','2026-09-30 23:59:59','2026-09-30 23:59:59')");
        jdbc.update("INSERT INTO complaints(tenant_id,source,message_id,summary,status,created_at) VALUES (7,'USER_REPORT','MSG-1','试用投诉','PENDING','2026-09-07 10:00:00')");
        jdbc.update("INSERT INTO complaints(tenant_id,source,message_id,summary,status,created_at) VALUES (7,'USER_REPORT','MSG-2','窗口外投诉','PENDING','2026-10-02 10:00:00')");

        List<ContractPricingService.TrialCandidate> rows = service.workbench(
                new ContractPricingService.WorkbenchQuery("Acme", "Alice", "SaaS", "TRIAL_FROZEN"));

        assertThat(rows).hasSize(1);
        var row = rows.getFirst();
        assertThat(row.tenantNo()).isEqualTo("TENANT-007");
        assertThat(row.configurationSnapshotVersion()).matches("TRIAL-SNAPSHOT-V1-[0-9A-F]{16}");
        assertThat(service.workbench(new ContractPricingService.WorkbenchQuery("Acme", null, null, null))
                .getFirst().configurationSnapshotVersion()).isEqualTo(row.configurationSnapshotVersion());
        assertThat(row.quotaUsed()).isEqualTo(400);
        assertThat(row.messageCount()).isEqualTo(2);
        assertThat(row.successCount()).isEqualTo(1);
        assertThat(row.successRate()).isEqualByComparingTo("0.500000");
        assertThat(row.complaintCount()).isEqualTo(1);
        assertThat(row.complaintRate()).isEqualByComparingTo("0.500000");
        assertThat(row.dataQuality()).isEqualTo("COMPLETE");
        assertThat(row.conversionEligible()).isTrue();
        assertThat(row.ineligibilityReasons()).isEmpty();
    }

    @Test
    void analysisReturnsTrendStatusComplaintsAndNoDataQuality() {
        jdbc.update("INSERT INTO message_tasks(tenant_id,send_status,created_at,updated_at) VALUES (7,'DELIVERED','2026-09-05 10:00:00','2026-09-05 10:01:00')");
        jdbc.update("INSERT INTO message_tasks(tenant_id,send_status,created_at,updated_at) VALUES (7,'FAILED','2026-09-05 11:00:00','2026-09-05 11:01:00')");
        jdbc.update("INSERT INTO complaints(tenant_id,source,message_id,summary,status,created_at) VALUES (7,'OPERATOR','MSG-7','内容投诉','PROCESSING','2026-09-05 12:00:00')");

        var analysis = service.analysis(7);

        assertThat(analysis.trend()).singleElement().satisfies(day -> {
            assertThat(day.date()).isEqualTo(LocalDate.of(2026, 9, 5));
            assertThat(day.messageCount()).isEqualTo(2);
            assertThat(day.successCount()).isEqualTo(1);
            assertThat(day.failureCount()).isEqualTo(1);
            assertThat(day.complaintCount()).isEqualTo(1);
        });
        assertThat(analysis.messageStatuses()).extracting(ContractPricingService.StatusCount::status)
                .containsExactly("DELIVERED", "FAILED");
        assertThat(analysis.complaints()).singleElement().satisfies(complaint -> {
            assertThat(complaint.source()).isEqualTo("OPERATOR");
            assertThat(complaint.summary()).isEqualTo("内容投诉");
        });
        assertThat(analysis.sourceRegistry()).isEqualTo("tenants:trial_accounts:message_tasks:complaints");

        jdbc.update("DELETE FROM complaints");
        jdbc.update("DELETE FROM message_tasks");
        assertThat(service.analysis(7).dataQuality()).isEqualTo("NO_DATA");
    }

    @Test
    void activePriceBooksExcludeInactiveVersions() {
        jdbc.update("INSERT INTO tenant_price_books(price_book_version,product_code,unit_price_mil,status) VALUES ('SMS_RETIRED_V1','SMS',60,'INACTIVE')");

        assertThat(service.activePriceBooks())
                .extracting(ContractPricingService.PriceBookOption::priceBookVersion)
                .containsExactly("SMS_STANDARD_V1");
    }

    @Test
    void approvalRejectsInactivePriceAndUnsafeAttachmentBeforeStateChange() {
        jdbc.update("INSERT INTO tenant_price_books(price_book_version,product_code,unit_price_mil,status) VALUES ('SMS_RETIRED_V1','SMS',60,'INACTIVE')");
        assertThatThrownBy(() -> service.approveContract(7, new ContractPricingService.ContractCommand(
                "POSTPAID", "SMS_RETIRED_V1", "HT-RETIRED", LocalDate.of(2026, 9, 10),
                "oss://contracts/retired.pdf", 1_000_000L, "MONTHLY"), "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价目表版本不存在或不可用");

        assertThatThrownBy(() -> service.approveContract(7, new ContractPricingService.ContractCommand(
                "POSTPAID", "SMS_STANDARD_V1", "HT-UNSAFE", LocalDate.of(2026, 9, 10),
                "oss://contracts/../unsafe.pdf", 1_000_000L, "MONTHLY"), "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("合同附件引用必须是安全的");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_contracts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM trial_accounts WHERE tenant_id=7", String.class))
                .isEqualTo("TRIAL_FROZEN");
    }

    @Test
    void approvalRejectsIneligibleTenantWithoutChangingAnyLifecycleState() {
        jdbc.update("UPDATE tenants SET lifecycle_status='TERMINATED' WHERE id=7");

        assertThatThrownBy(() -> service.approveContract(7, new ContractPricingService.ContractCommand(
                "POSTPAID", "SMS_STANDARD_V1", "HT-REJECTED", LocalDate.of(2026, 9, 10),
                "oss://contracts/rejected.pdf", 1_000_000L, "MONTHLY"), "operator"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机构生命周期不允许转正式");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_contracts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM trial_accounts WHERE tenant_id=7", String.class))
                .isEqualTo("TRIAL_FROZEN");
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=7", String.class))
                .isEqualTo("TERMINATED");
    }

    @Test
    void eligibilityRequiresVerifiedQualificationAndNormalAccount() {
        jdbc.update("UPDATE tenants SET verification_status='PENDING' WHERE id=7");
        assertThat(service.workbench(new ContractPricingService.WorkbenchQuery(null, null, null, null)).getFirst()
                .ineligibilityReasons()).contains("机构资质未通过审核");

        jdbc.update("UPDATE tenants SET verification_status='VERIFIED' WHERE id=7");
        jdbc.update("UPDATE tenant_accounts SET status='FROZEN' WHERE tenant_id=7");
        assertThat(service.workbench(new ContractPricingService.WorkbenchQuery(null, null, null, null)).getFirst()
                .ineligibilityReasons()).contains("机构账户已停用或冻结");
    }

    @Test
    void workbenchExcludesStaleTrialRowsOutsideTrialLifecycle() {
        jdbc.update("UPDATE tenants SET lifecycle_status='TERMINATED' WHERE id=7");

        assertThat(service.workbench(new ContractPricingService.WorkbenchQuery(null, null, null, null)))
                .isEmpty();
        assertThatThrownBy(() -> service.analysis(7))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("试用机构不存在或已不在试用阶段");
    }

    @Test
    void historicalTenantTrialFactsRemainConvertibleAndMaterializeLedgerRow() {
        jdbc.update("DELETE FROM trial_accounts WHERE tenant_id=7");

        assertThat(service.workbench(new ContractPricingService.WorkbenchQuery(null, null, null, null)))
                .singleElement().satisfies(candidate -> {
                    assertThat(candidate.trialStatus()).isEqualTo("TRIAL_FROZEN");
                    assertThat(candidate.quotaTotal()).isEqualTo(500);
                });

        service.approveContract(7, new ContractPricingService.ContractCommand(
                "PREPAID", "SMS_STANDARD_V1", "HT-HISTORICAL", LocalDate.of(2026, 9, 10),
                "oss://contracts/historical.pdf", null, null), "operator");

        assertThat(jdbc.queryForObject("SELECT status FROM trial_accounts WHERE tenant_id=7", String.class))
                .isEqualTo("CONTRACTED");
    }

    @Test
    void conversionRollsBackContractAndTrialWhenTenantTransitionFails() {
        jdbc.execute("ALTER TABLE tenants ADD CONSTRAINT reject_signed CHECK (lifecycle_status <> 'SIGNED')");
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(ignored -> service.approveContract(7,
                new ContractPricingService.ContractCommand(
                        "POSTPAID", "SMS_STANDARD_V1", "HT-ROLLBACK", LocalDate.of(2026, 9, 10),
                        "oss://contracts/rollback.pdf", 1_000_000L, "MONTHLY"), "operator")))
                .isInstanceOf(RuntimeException.class);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant_contracts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM trial_accounts WHERE tenant_id=7", String.class))
                .isEqualTo("TRIAL_FROZEN");
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=7", String.class))
                .isEqualTo("TRIAL_FROZEN");
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
                CREATE TABLE tenants(
                  id BIGINT PRIMARY KEY,
                  tenant_no VARCHAR(64) NOT NULL UNIQUE,
                  short_name VARCHAR(128) NOT NULL,
                  full_name VARCHAR(255) NOT NULL,
                  biz_manager VARCHAR(64),
                  industry VARCHAR(64),
                  lifecycle_status VARCHAR(32) NOT NULL,
                  verification_status VARCHAR(32) NOT NULL,
                  trial_quota INT,
                  trial_quota_used INT NOT NULL DEFAULT 0,
                  trial_start_at TIMESTAMP,
                  trial_end_at TIMESTAMP,
                  billing_mode VARCHAR(16),
                  contract_signed_at DATE,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("""
                INSERT INTO tenants(id,tenant_no,short_name,full_name,biz_manager,industry,lifecycle_status,
                                    verification_status,trial_quota,trial_quota_used,trial_start_at,trial_end_at)
                VALUES (7,'TENANT-007','Acme 短信','Acme Messaging Ltd','Alice','SaaS','TRIAL_FROZEN',
                        'VERIFIED',500,400,'2026-09-01 00:00:00','2026-09-30 23:59:59')
                """);
        jdbc.execute("""
                CREATE TABLE tenant_accounts(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL UNIQUE,
                  status VARCHAR(32) NOT NULL
                )
                """);
        jdbc.update("INSERT INTO tenant_accounts(tenant_id,status) VALUES (7,'NORMAL')");
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
                VALUES (7,'TRIAL_FROZEN',500,100,'2026-09-01 00:00:00','2026-09-30 23:59:59',3)
                """);
        jdbc.execute("""
                CREATE TABLE message_tasks(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  send_status VARCHAR(32) NOT NULL,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE complaints(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT,
                  source VARCHAR(32) NOT NULL,
                  message_id VARCHAR(64),
                  summary VARCHAR(500),
                  status VARCHAR(32) NOT NULL,
                  created_at TIMESTAMP NOT NULL
                )
                """);
    }
}
