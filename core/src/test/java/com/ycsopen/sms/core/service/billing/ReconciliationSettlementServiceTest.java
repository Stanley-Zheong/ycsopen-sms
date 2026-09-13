package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReconciliationSettlementServiceTest {
    private JdbcTemplate jdbc;
    private ReconciliationSettlementService service;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase38-" + System.nanoTime()
                        + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        service = new ReconciliationSettlementService(jdbc);
    }

    @Test
    void periodCloseProducesSourceBackedStatementAndIsIdempotent() {
        var statement = service.generateStatement(42, new ReconciliationSettlementService.StatementGenerateCommand(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), "finance");
        var duplicate = service.generateStatement(42, new ReconciliationSettlementService.StatementGenerateCommand(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), "finance");

        assertThat(statement.sendCount()).isEqualTo(2);
        assertThat(statement.successCount()).isEqualTo(2);
        assertThat(statement.billedCount()).isEqualTo(2);
        assertThat(statement.amountDue()).isEqualTo(300_000L);
        assertThat(statement.billingMode()).isEqualTo("POSTPAID");
        assertThat(statement.priceBookVersion()).isEqualTo("SMS_STANDARD_V1");
        assertThat(duplicate.id()).isEqualTo(statement.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statements", Integer.class)).isEqualTo(1);
    }

    @Test
    void differencesBlockConfirmationUntilResolvedThenBothSidesConfirm() {
        var statement = service.generateStatement(42, new ReconciliationSettlementService.StatementGenerateCommand(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), "finance");

        var disputed = service.tenantConfirm(statement.id(), new ReconciliationSettlementService.ConfirmationCommand(
                false, "AMOUNT", 10_000L, "机构认为金额多计", "oss://diff/42-202609.txt"), "tenant");

        assertThat(disputed.reconcileStatus()).isEqualTo("DISPUTED");
        var difference = service.differences(statement.id()).getFirst();
        assertThat(difference.status()).isEqualTo("OPEN");
        assertThatThrownBy(() -> service.financeConfirm(statement.id(), new ReconciliationSettlementService.ConfirmationCommand(
                true, null, null, null, null), "finance"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在未解决差异");

        var pending = service.resolveDifference(difference.id(),
                new ReconciliationSettlementService.ResolveCommand("核对详单后调回待确认"), "finance");
        assertThat(pending.reconcileStatus()).isEqualTo("PENDING");

        service.tenantConfirm(statement.id(), new ReconciliationSettlementService.ConfirmationCommand(
                true, null, null, null, null), "tenant");
        var confirmed = service.financeConfirm(statement.id(), new ReconciliationSettlementService.ConfirmationCommand(
                true, null, null, null, null), "finance");

        assertThat(confirmed.reconcileStatus()).isEqualTo("CONFIRMED");
        assertThat(confirmed.confirmedAt()).isNotNull();
    }

    @Test
    void settlementAdvancesExactlyOnceAndInvoiceCannotExceedEntitlement() {
        var statement = confirmedStatement();

        var settlement = service.startSettlement(statement.id(),
                new ReconciliationSettlementService.EvidenceCommand("bank-settlement-202609"), "finance");
        var duplicateStart = service.startSettlement(statement.id(),
                new ReconciliationSettlementService.EvidenceCommand("bank-settlement-202609"), "finance");
        assertThat(duplicateStart.id()).isEqualTo(settlement.id());
        assertThat(duplicateStart.status()).isEqualTo("PENDING_SETTLEMENT");

        var settled = service.completeSettlement(settlement.id(),
                new ReconciliationSettlementService.EvidenceCommand("settlement-paid-202609"), "finance");
        var received = service.markReceived(settlement.id(),
                new ReconciliationSettlementService.EvidenceCommand("tenant-received-202609"), "finance");

        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(received.status()).isEqualTo("RECEIVED");
        assertThat(service.tenantStatements(42).getFirst().settlementStatus()).isEqualTo("PAID");

        var invoice = service.requestInvoice(42, new ReconciliationSettlementService.InvoiceRequestCommand(
                statement.id(), 200_000L, "VAT_NORMAL", "开票资料齐全"), "tenant");
        assertThat(invoice.status()).isEqualTo("PENDING");
        assertThatThrownBy(() -> service.requestInvoice(42, new ReconciliationSettlementService.InvoiceRequestCommand(
                statement.id(), 100_001L, "VAT_NORMAL", "超额申请"), "tenant"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发票金额超过可开票额度");

        var issued = service.issueInvoice(invoice.id(), new ReconciliationSettlementService.InvoiceIssueCommand(
                "INV-2026-0001"), "finance");
        assertThat(issued.status()).isEqualTo("ISSUED");
        assertThat(issued.invoiceNo()).isEqualTo("INV-2026-0001");
    }

    private ReconciliationSettlementService.StatementRow confirmedStatement() {
        var statement = service.generateStatement(42, new ReconciliationSettlementService.StatementGenerateCommand(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), "finance");
        service.tenantConfirm(statement.id(), new ReconciliationSettlementService.ConfirmationCommand(
                true, null, null, null, null), "tenant");
        return service.financeConfirm(statement.id(), new ReconciliationSettlementService.ConfirmationCommand(
                true, null, null, null, null), "finance");
    }

    private void createSchema() {
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
                CREATE TABLE statements(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  statement_no VARCHAR(64),
                  period_start DATE NOT NULL,
                  period_end DATE NOT NULL,
                  send_count BIGINT NOT NULL DEFAULT 0,
                  success_count BIGINT NOT NULL DEFAULT 0,
                  billed_count BIGINT NOT NULL DEFAULT 0,
                  amount_due BIGINT NOT NULL DEFAULT 0,
                  reconcile_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  dispute_note VARCHAR(500),
                  settlement_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SETTLED',
                  generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  confirmed_at TIMESTAMP NULL,
                  billing_mode VARCHAR(16),
                  price_book_version VARCHAR(64),
                  source_snapshot_json VARCHAR(2000),
                  tenant_confirmed_at TIMESTAMP NULL,
                  finance_confirmed_at TIMESTAMP NULL,
                  confirmed_by_tenant VARCHAR(64),
                  confirmed_by_finance VARCHAR(64),
                  resolution_note VARCHAR(500),
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(tenant_id, period_start, period_end)
                )
                """);
        jdbc.execute("""
                CREATE TABLE statement_differences(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  statement_id BIGINT NOT NULL,
                  tenant_id BIGINT NOT NULL,
                  difference_type VARCHAR(32) NOT NULL,
                  claimed_amount_mil BIGINT NOT NULL,
                  note VARCHAR(500) NOT NULL,
                  evidence_ref VARCHAR(255) NOT NULL,
                  owner_actor VARCHAR(64) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                  resolution_note VARCHAR(500),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  resolved_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE settlement_records(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  statement_id BIGINT NOT NULL UNIQUE,
                  tenant_id BIGINT NOT NULL,
                  amount_mil BIGINT NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING_SETTLEMENT',
                  start_evidence VARCHAR(255) NOT NULL,
                  complete_evidence VARCHAR(255),
                  received_evidence VARCHAR(255),
                  started_by VARCHAR(64) NOT NULL,
                  completed_by VARCHAR(64),
                  received_by VARCHAR(64),
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  completed_at TIMESTAMP,
                  received_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE invoices(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  tenant_id BIGINT NOT NULL,
                  statement_id BIGINT,
                  amount BIGINT NOT NULL,
                  invoice_type VARCHAR(32),
                  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                  invoice_no VARCHAR(64),
                  request_evidence VARCHAR(255),
                  requested_by VARCHAR(64),
                  issued_by VARCHAR(64),
                  issued_at TIMESTAMP,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE(invoice_no)
                )
                """);
        jdbc.update("""
                INSERT INTO tenant_contracts(tenant_id,billing_mode,price_book_version,contract_no,signed_at,
                    attachment_ref,credit_limit_mil,billing_period,contract_status,approved_by)
                VALUES (42,'POSTPAID','SMS_STANDARD_V1','HT-38','2026-09-01','oss://contract/38.pdf',500000,'MONTHLY','ACTIVE','finance')
                """);
        jdbc.update("""
                INSERT INTO postpaid_usage_ledger(tenant_id,business_doc_id,billing_period,period_start,period_end,
                    amount_mil,state,actor)
                VALUES (42,'MSG-38-A','MONTHLY','2026-09-01','2026-09-30',100000,'RECORDED','billing'),
                       (42,'MSG-38-B','MONTHLY','2026-09-01','2026-09-30',200000,'RECORDED','billing')
                """);
    }
}
