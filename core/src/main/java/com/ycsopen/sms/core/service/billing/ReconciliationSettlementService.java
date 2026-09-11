package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Phase 38: source-backed statements, reconciliation, settlement, and invoice state. */
@Service
public class ReconciliationSettlementService {
    private static final List<String> DIFFERENCE_TYPES = List.of("COUNT", "AMOUNT", "PERIOD", "OTHER");
    private static final List<String> INVOICE_TYPES = List.of("VAT_SPECIAL", "VAT_NORMAL", "ELECTRONIC");

    private final JdbcTemplate jdbc;

    public ReconciliationSettlementService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional
    public StatementRow generateStatement(long tenantId, StatementGenerateCommand command, String actor) {
        StatementGenerateCommand checked = command.checked();
        StatementRow existing = statementByPeriod(tenantId, checked.periodStart(), checked.periodEnd());
        if (existing != null) {
            return existing;
        }
        ContractView contract = contract(tenantId);
        SourceTotals totals = sourceTotals(tenantId, checked.periodStart(), checked.periodEnd());
        try {
            jdbc.update("""
                    INSERT INTO statements(tenant_id, statement_no, period_start, period_end, send_count,
                        success_count, billed_count, amount_due, reconcile_status, settlement_status, billing_mode,
                        price_book_version, source_snapshot_json)
                    VALUES (?,?,?,?,?,?,?,?, 'PENDING', 'NOT_SETTLED', ?, ?, ?)
                    """, tenantId, statementNo(tenantId, checked.periodStart(), checked.periodEnd()),
                    checked.periodStart(), checked.periodEnd(), totals.sendCount(), totals.successCount(),
                    totals.billedCount(), totals.amountMil(), contract.billingMode(), contract.priceBookVersion(),
                    sourceSnapshot(totals));
        } catch (DataIntegrityViolationException duplicate) {
            return statementByPeriod(tenantId, checked.periodStart(), checked.periodEnd());
        }
        return statementByPeriod(tenantId, checked.periodStart(), checked.periodEnd());
    }

    @Transactional(readOnly = true)
    public List<StatementRow> adminStatements() {
        return jdbc.query("""
                SELECT * FROM statements
                 ORDER BY period_start DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> statement(rs));
    }

    @Transactional(readOnly = true)
    public List<StatementRow> tenantStatements(long tenantId) {
        return jdbc.query("""
                SELECT * FROM statements
                 WHERE tenant_id=?
                 ORDER BY period_start DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> statement(rs), tenantId);
    }

    @Transactional(readOnly = true)
    public List<DifferenceRow> differences(long statementId) {
        return jdbc.query("""
                SELECT * FROM statement_differences
                 WHERE statement_id=?
                 ORDER BY created_at DESC, id DESC
                """, (rs, row) -> difference(rs), statementId);
    }

    @Transactional(readOnly = true)
    public StatementRow getStatement(long statementId) {
        return statement(statementId);
    }

    @Transactional
    public StatementRow tenantConfirm(long statementId, ConfirmationCommand command, String actor) {
        return confirm(statementId, command, actor, true);
    }

    @Transactional
    public StatementRow financeConfirm(long statementId, ConfirmationCommand command, String actor) {
        return confirm(statementId, command, actor, false);
    }

    @Transactional
    public StatementRow resolveDifference(long differenceId, ResolveCommand command, String actor) {
        ResolveCommand checked = command.checked();
        DifferenceRow current = differenceForUpdate(differenceId);
        if (!"OPEN".equals(current.status())) {
            return statement(current.statementId());
        }
        jdbc.update("""
                UPDATE statement_differences
                   SET status='RESOLVED', resolution_note=?, resolved_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status='OPEN'
                """, checked.resolutionNote(), differenceId);
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM statement_differences
                 WHERE statement_id=? AND status='OPEN'
                """, Integer.class, current.statementId());
        if (open == null || open == 0) {
            jdbc.update("""
                    UPDATE statements
                       SET reconcile_status='PENDING', tenant_confirmed_at=NULL, finance_confirmed_at=NULL,
                           confirmed_by_tenant=NULL, confirmed_by_finance=NULL, resolution_note=?,
                           updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, checked.resolutionNote() + " / resolved by " + actor(actor), current.statementId());
        }
        return statement(current.statementId());
    }

    @Transactional
    public SettlementRow startSettlement(long statementId, EvidenceCommand command, String actor) {
        EvidenceCommand checked = command.checked("SETTLEMENT_START_EVIDENCE_REQUIRED");
        StatementRow statement = statementForUpdate(statementId);
        if (!"CONFIRMED".equals(statement.reconcileStatus())) {
            throw new BusinessException("STATEMENT_CONFIRMATION_REQUIRED", "对账单未完成双方确认");
        }
        if (!"NOT_SETTLED".equals(statement.settlementStatus())) {
            return settlementByStatement(statementId);
        }
        jdbc.update("""
                INSERT INTO settlement_records(statement_id, tenant_id, amount_mil, status, start_evidence, started_by)
                VALUES (?,?,?,?,?,?)
                """, statementId, statement.tenantId(), statement.amountDue(), "PENDING_SETTLEMENT",
                checked.evidenceRef(), actor(actor));
        jdbc.update("""
                UPDATE statements
                   SET settlement_status='PENDING_SETTLEMENT', updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND settlement_status='NOT_SETTLED'
                """, statementId);
        return settlementByStatement(statementId);
    }

    @Transactional
    public SettlementRow completeSettlement(long settlementId, EvidenceCommand command, String actor) {
        EvidenceCommand checked = command.checked("SETTLEMENT_COMPLETE_EVIDENCE_REQUIRED");
        SettlementRow current = settlementForUpdate(settlementId);
        if (!"PENDING_SETTLEMENT".equals(current.status())) {
            return current;
        }
        jdbc.update("""
                UPDATE settlement_records
                   SET status='SETTLED', complete_evidence=?, completed_by=?, completed_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status='PENDING_SETTLEMENT'
                """, checked.evidenceRef(), actor(actor), settlementId);
        jdbc.update("""
                UPDATE statements
                   SET settlement_status='SETTLED', updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND settlement_status='PENDING_SETTLEMENT'
                """, current.statementId());
        return settlement(settlementId);
    }

    @Transactional
    public SettlementRow markReceived(long settlementId, EvidenceCommand command, String actor) {
        EvidenceCommand checked = command.checked("SETTLEMENT_RECEIVED_EVIDENCE_REQUIRED");
        SettlementRow current = settlementForUpdate(settlementId);
        if (!"SETTLED".equals(current.status())) {
            return current;
        }
        jdbc.update("""
                UPDATE settlement_records
                   SET status='RECEIVED', received_evidence=?, received_by=?, received_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status='SETTLED'
                """, checked.evidenceRef(), actor(actor), settlementId);
        jdbc.update("""
                UPDATE statements
                   SET settlement_status='PAID', updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND settlement_status='SETTLED'
                """, current.statementId());
        return settlement(settlementId);
    }

    @Transactional(readOnly = true)
    public List<SettlementRow> settlements() {
        return jdbc.query("""
                SELECT * FROM settlement_records
                 ORDER BY created_at DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> settlement(rs));
    }

    @Transactional
    public InvoiceRow requestInvoice(long tenantId, InvoiceRequestCommand command, String actor) {
        InvoiceRequestCommand checked = command.checked();
        StatementRow statement = statementForUpdate(checked.statementId());
        if (statement.tenantId() != tenantId) {
            throw new BusinessException("TENANT_SCOPE_FORBIDDEN", "不能访问其他机构发票");
        }
        if (!("SETTLED".equals(statement.settlementStatus()) || "PAID".equals(statement.settlementStatus()))) {
            throw new BusinessException("INVOICE_SETTLEMENT_REQUIRED", "未结算账单不能申请发票");
        }
        long requested = invoicedAmount(checked.statementId());
        if (requested + checked.amountMil() > statement.amountDue()) {
            throw new BusinessException("INVOICE_AMOUNT_EXCEEDS_ENTITLEMENT", "发票金额超过可开票额度");
        }
        jdbc.update("""
                INSERT INTO invoices(tenant_id, statement_id, amount, invoice_type, status, request_evidence, requested_by)
                VALUES (?,?,?,?, 'PENDING', ?, ?)
                """, tenantId, checked.statementId(), checked.amountMil(), checked.invoiceType(),
                checked.evidenceRef(), actor(actor));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM invoices WHERE tenant_id=? AND statement_id=?",
                Long.class, tenantId, checked.statementId());
        return invoice(id == null ? 0 : id);
    }

    @Transactional
    public InvoiceRow issueInvoice(long invoiceId, InvoiceIssueCommand command, String actor) {
        InvoiceIssueCommand checked = command.checked();
        InvoiceRow current = invoiceForUpdate(invoiceId);
        if (!"PENDING".equals(current.status())) {
            return current;
        }
        jdbc.update("""
                UPDATE invoices
                   SET status='ISSUED', invoice_no=?, issued_by=?, issued_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND status='PENDING'
                """, checked.invoiceNo(), actor(actor), invoiceId);
        return invoice(invoiceId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceRow> tenantInvoices(long tenantId) {
        return jdbc.query("""
                SELECT * FROM invoices
                 WHERE tenant_id=?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> invoice(rs), tenantId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceRow> adminInvoices() {
        return jdbc.query("""
                SELECT * FROM invoices
                 ORDER BY created_at DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> invoice(rs));
    }

    private StatementRow confirm(long statementId, ConfirmationCommand command, String actor, boolean tenantSide) {
        ConfirmationCommand checked = command.checked();
        StatementRow current = statementForUpdate(statementId);
        if ("CONFIRMED".equals(current.reconcileStatus())) {
            return current;
        }
        if (!checked.agree()) {
            jdbc.update("""
                    INSERT INTO statement_differences(statement_id, tenant_id, difference_type, claimed_amount_mil,
                        note, evidence_ref, owner_actor, status)
                    VALUES (?,?,?,?,?,?,?, 'OPEN')
                    """, statementId, current.tenantId(), checked.differenceType(), checked.claimedAmountMil(),
                    checked.note(), checked.evidenceRef(), actor(actor));
            jdbc.update("""
                    UPDATE statements
                       SET reconcile_status='DISPUTED', dispute_note=?, updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, checked.note(), statementId);
            return statement(statementId);
        }
        if (hasOpenDifference(statementId)) {
            throw new BusinessException("STATEMENT_DIFFERENCE_OPEN", "存在未解决差异，不能确认");
        }
        if (tenantSide) {
            jdbc.update("""
                    UPDATE statements
                       SET tenant_confirmed_at=CURRENT_TIMESTAMP, confirmed_by_tenant=?, updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, actor(actor), statementId);
        } else {
            jdbc.update("""
                    UPDATE statements
                       SET finance_confirmed_at=CURRENT_TIMESTAMP, confirmed_by_finance=?, updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, actor(actor), statementId);
        }
        StatementRow updated = statement(statementId);
        if (updated.tenantConfirmedAt() != null && updated.financeConfirmedAt() != null) {
            jdbc.update("""
                    UPDATE statements
                       SET reconcile_status='CONFIRMED', confirmed_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                     WHERE id=? AND reconcile_status='PENDING'
                    """, statementId);
        }
        return statement(statementId);
    }

    private SourceTotals sourceTotals(long tenantId, LocalDate start, LocalDate end) {
        return jdbc.query("""
                SELECT COUNT(*) AS send_count,
                       COUNT(*) AS success_count,
                       COUNT(*) AS billed_count,
                       COALESCE(SUM(amount_mil),0) AS amount_mil
                  FROM postpaid_usage_ledger
                 WHERE tenant_id=? AND period_start>=? AND period_end<=? AND state='RECORDED'
                """, rs -> {
            rs.next();
            return new SourceTotals(rs.getLong("send_count"), rs.getLong("success_count"),
                    rs.getLong("billed_count"), rs.getLong("amount_mil"));
        }, tenantId, start, end);
    }

    private ContractView contract(long tenantId) {
        return jdbc.query("""
                SELECT billing_mode, price_book_version FROM tenant_contracts
                 WHERE tenant_id=? AND contract_status='ACTIVE'
                """, (rs, row) -> new ContractView(rs.getString("billing_mode"), rs.getString("price_book_version")),
                tenantId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("TENANT_CONTRACT_NOT_FOUND", "机构有效合同不存在"));
    }

    private StatementRow statementByPeriod(long tenantId, LocalDate start, LocalDate end) {
        return jdbc.query("SELECT * FROM statements WHERE tenant_id=? AND period_start=? AND period_end=?",
                        (rs, row) -> statement(rs), tenantId, start, end)
                .stream().findFirst().orElse(null);
    }

    private StatementRow statement(long id) {
        return jdbc.query("SELECT * FROM statements WHERE id=?", (rs, row) -> statement(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("STATEMENT_NOT_FOUND", "对账单不存在"));
    }

    private StatementRow statementForUpdate(long id) {
        return jdbc.query("SELECT * FROM statements WHERE id=? FOR UPDATE", (rs, row) -> statement(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("STATEMENT_NOT_FOUND", "对账单不存在"));
    }

    private DifferenceRow differenceForUpdate(long id) {
        return jdbc.query("SELECT * FROM statement_differences WHERE id=? FOR UPDATE",
                        (rs, row) -> difference(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("STATEMENT_DIFFERENCE_NOT_FOUND", "对账差异不存在"));
    }

    private SettlementRow settlement(long id) {
        return jdbc.query("SELECT * FROM settlement_records WHERE id=?", (rs, row) -> settlement(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("SETTLEMENT_NOT_FOUND", "结算记录不存在"));
    }

    private SettlementRow settlementForUpdate(long id) {
        return jdbc.query("SELECT * FROM settlement_records WHERE id=? FOR UPDATE", (rs, row) -> settlement(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("SETTLEMENT_NOT_FOUND", "结算记录不存在"));
    }

    private SettlementRow settlementByStatement(long statementId) {
        return jdbc.query("SELECT * FROM settlement_records WHERE statement_id=?", (rs, row) -> settlement(rs), statementId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("SETTLEMENT_NOT_FOUND", "结算记录不存在"));
    }

    private InvoiceRow invoice(long id) {
        return jdbc.query("SELECT * FROM invoices WHERE id=?", (rs, row) -> invoice(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "发票记录不存在"));
    }

    private InvoiceRow invoiceForUpdate(long id) {
        return jdbc.query("SELECT * FROM invoices WHERE id=? FOR UPDATE", (rs, row) -> invoice(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("INVOICE_NOT_FOUND", "发票记录不存在"));
    }

    private long invoicedAmount(long statementId) {
        Long value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount),0) FROM invoices
                 WHERE statement_id=? AND status IN ('PENDING','ISSUED')
                """, Long.class, statementId);
        return value == null ? 0 : value;
    }

    private boolean hasOpenDifference(long statementId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM statement_differences
                 WHERE statement_id=? AND status='OPEN'
                """, Integer.class, statementId);
        return count != null && count > 0;
    }

    private static StatementRow statement(ResultSet rs) throws SQLException {
        return new StatementRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("statement_no"),
                date(rs, "period_start"), date(rs, "period_end"), rs.getLong("send_count"),
                rs.getLong("success_count"), rs.getLong("billed_count"), rs.getLong("amount_due"),
                rs.getString("reconcile_status"), rs.getString("settlement_status"),
                rs.getString("billing_mode"), rs.getString("price_book_version"), rs.getString("dispute_note"),
                timestamp(rs, "tenant_confirmed_at"), timestamp(rs, "finance_confirmed_at"),
                timestamp(rs, "confirmed_at"));
    }

    private static DifferenceRow difference(ResultSet rs) throws SQLException {
        return new DifferenceRow(rs.getLong("id"), rs.getLong("statement_id"), rs.getLong("tenant_id"),
                rs.getString("difference_type"), rs.getLong("claimed_amount_mil"), rs.getString("note"),
                rs.getString("evidence_ref"), rs.getString("owner_actor"), rs.getString("status"),
                rs.getString("resolution_note"));
    }

    private static SettlementRow settlement(ResultSet rs) throws SQLException {
        return new SettlementRow(rs.getLong("id"), rs.getLong("statement_id"), rs.getLong("tenant_id"),
                rs.getLong("amount_mil"), rs.getString("status"), rs.getString("start_evidence"),
                rs.getString("complete_evidence"), rs.getString("received_evidence"));
    }

    private static InvoiceRow invoice(ResultSet rs) throws SQLException {
        return new InvoiceRow(rs.getLong("id"), rs.getLong("tenant_id"), nullableLong(rs, "statement_id"),
                rs.getLong("amount"), rs.getString("invoice_type"), rs.getString("status"),
                rs.getString("invoice_no"), rs.getString("request_evidence"), timestamp(rs, "issued_at"));
    }

    private static String statementNo(long tenantId, LocalDate start, LocalDate end) {
        return "STMT-" + tenantId + "-" + start + "-" + end;
    }

    private static String sourceSnapshot(SourceTotals totals) {
        return "{\"source\":\"postpaid_usage_ledger\",\"sendCount\":" + totals.sendCount()
                + ",\"successCount\":" + totals.successCount()
                + ",\"billedCount\":" + totals.billedCount()
                + ",\"amountMil\":" + totals.amountMil() + "}";
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String actor(String value) {
        return text(value, "ACTOR_REQUIRED", 64);
    }

    private static String text(String value, String code, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw new BusinessException(code, "文本不能为空且不能超过长度限制");
        }
        return trimmed;
    }

    public record StatementGenerateCommand(LocalDate periodStart, LocalDate periodEnd) {
        StatementGenerateCommand checked() {
            if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
                throw new BusinessException("STATEMENT_PERIOD_INVALID", "账期范围不合法");
            }
            return this;
        }
    }

    public record ConfirmationCommand(boolean agree, String differenceType, Long claimedAmountMil,
                                      String note, String evidenceRef) {
        ConfirmationCommand checked() {
            if (agree) {
                return new ConfirmationCommand(true, null, null, null, null);
            }
            String type = text(differenceType, "STATEMENT_DIFFERENCE_TYPE_REQUIRED", 32).toUpperCase(Locale.ROOT);
            if (!DIFFERENCE_TYPES.contains(type)) {
                throw new BusinessException("STATEMENT_DIFFERENCE_TYPE_INVALID", "对账差异类型不支持");
            }
            long amount = claimedAmountMil == null ? 0 : claimedAmountMil;
            if (amount < 0) {
                throw new BusinessException("STATEMENT_DIFFERENCE_AMOUNT_INVALID", "差异金额不能为负");
            }
            return new ConfirmationCommand(false, type, amount,
                    text(note, "STATEMENT_DIFFERENCE_NOTE_REQUIRED", 500),
                    text(evidenceRef, "STATEMENT_DIFFERENCE_EVIDENCE_REQUIRED", 255));
        }
    }

    public record ResolveCommand(String resolutionNote) {
        ResolveCommand checked() {
            return new ResolveCommand(text(resolutionNote, "STATEMENT_RESOLUTION_REQUIRED", 500));
        }
    }

    public record EvidenceCommand(String evidenceRef) {
        EvidenceCommand checked(String code) {
            return new EvidenceCommand(text(evidenceRef, code, 255));
        }
    }

    public record InvoiceRequestCommand(long statementId, long amountMil, String invoiceType, String evidenceRef) {
        InvoiceRequestCommand checked() {
            if (statementId <= 0 || amountMil <= 0) {
                throw new BusinessException("INVOICE_REQUEST_INVALID", "发票申请账单和金额不合法");
            }
            String type = text(invoiceType, "INVOICE_TYPE_REQUIRED", 32).toUpperCase(Locale.ROOT);
            if (!INVOICE_TYPES.contains(type)) {
                throw new BusinessException("INVOICE_TYPE_INVALID", "发票类型不支持");
            }
            return new InvoiceRequestCommand(statementId, amountMil, type,
                    text(evidenceRef, "INVOICE_REQUEST_EVIDENCE_REQUIRED", 255));
        }
    }

    public record InvoiceIssueCommand(String invoiceNo) {
        InvoiceIssueCommand checked() {
            return new InvoiceIssueCommand(text(invoiceNo, "INVOICE_NO_REQUIRED", 64));
        }
    }

    public record StatementRow(long id, long tenantId, String statementNo, LocalDate periodStart, LocalDate periodEnd,
                               long sendCount, long successCount, long billedCount, long amountDue,
                               String reconcileStatus, String settlementStatus, String billingMode,
                               String priceBookVersion, String disputeNote, LocalDateTime tenantConfirmedAt,
                               LocalDateTime financeConfirmedAt, LocalDateTime confirmedAt) { }

    public record DifferenceRow(long id, long statementId, long tenantId, String differenceType,
                                long claimedAmountMil, String note, String evidenceRef, String ownerActor,
                                String status, String resolutionNote) { }

    public record SettlementRow(long id, long statementId, long tenantId, long amountMil, String status,
                                String startEvidence, String completeEvidence, String receivedEvidence) { }

    public record InvoiceRow(long id, long tenantId, Long statementId, long amount, String invoiceType,
                             String status, String invoiceNo, String requestEvidence, LocalDateTime issuedAt) { }

    private record SourceTotals(long sendCount, long successCount, long billedCount, long amountMil) { }

    private record ContractView(String billingMode, String priceBookVersion) { }
}
