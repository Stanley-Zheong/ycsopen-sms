package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Phase 37: contract pricing, billing mode, and postpaid credit ceiling. */
@Service
public class ContractPricingService {
    private static final List<String> BILLING_MODES = List.of("PREPAID", "POSTPAID");
    private static final List<String> BILLING_PERIODS = List.of("MONTHLY", "QUARTERLY");

    private final JdbcTemplate jdbc;

    public ContractPricingService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional
    public ContractRow approveContract(long tenantId, ContractCommand command, String actor) {
        ContractCommand checked = command.checked();
        requirePriceBook(checked.priceBookVersion());
        try {
            jdbc.update("""
                    INSERT INTO tenant_contracts(tenant_id, billing_mode, price_book_version, contract_no,
                        signed_at, attachment_ref, credit_limit_mil, billing_period, contract_status, approved_by)
                    VALUES (?,?,?,?,?,?,?,?, 'ACTIVE', ?)
                    """, tenantId, checked.billingMode(), checked.priceBookVersion(), checked.contractNo(),
                    checked.signedAt(), checked.attachmentRef(), checked.creditLimitMil(), checked.billingPeriod(), actor(actor));
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException("TENANT_CONTRACT_DUPLICATE", "机构合同或合同编号已存在");
        }
        jdbc.update("""
                UPDATE trial_accounts
                   SET status='CONTRACTED', version=version+1, updated_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=? AND status IN ('TRIAL','TRIAL_FROZEN','UNACTIVATED')
                """, tenantId);
        return contract(tenantId);
    }

    @Transactional(readOnly = true)
    public ContractOverview overview(long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,billing_mode,price_book_version,contract_no,signed_at,attachment_ref,
                       credit_limit_mil,billing_period,contract_status,approved_by
                  FROM tenant_contracts
                 WHERE tenant_id=?
                """, (rs, row) -> new ContractOverview(rs.getLong("tenant_id"), "CONTRACTED",
                rs.getString("billing_mode"), rs.getString("price_book_version"), rs.getString("contract_no"),
                date(rs, "signed_at"), rs.getString("attachment_ref"), nullableLong(rs, "credit_limit_mil"),
                rs.getString("billing_period"), rs.getString("contract_status"), rs.getString("approved_by")),
                tenantId).stream().findFirst()
                .orElse(new ContractOverview(tenantId, "NOT_CONTRACTED", null, null, null, null,
                        null, null, null, "NONE", null));
    }

    @Transactional
    public PostpaidUsageRow recordPostpaidUsage(long tenantId, PostpaidUsageCommand command, String actor) {
        PostpaidUsageCommand checked = command.checked();
        PostpaidUsageRow existing = findUsage(checked.businessDocId());
        if (existing != null) {
            return existing;
        }
        ContractRow contract = contractForUpdate(tenantId);
        if (!"POSTPAID".equals(contract.billingMode())) {
            throw new BusinessException("POSTPAID_CONTRACT_REQUIRED", "机构不是后付费合同");
        }
        Period period = period(LocalDate.now(), contract.billingPeriod());
        long used = usedAmount(tenantId, period);
        long creditLimit = contract.creditLimitMil() == null ? 0 : contract.creditLimitMil();
        if (used + checked.amountMil() > creditLimit) {
            throw new BusinessException("POSTPAID_CREDIT_EXCEEDED", "后付费授信额度不足");
        }
        jdbc.update("""
                INSERT INTO postpaid_usage_ledger(tenant_id, business_doc_id, billing_period,
                    period_start, period_end, amount_mil, state, actor)
                VALUES (?,?,?,?,?,?,'RECORDED',?)
                """, tenantId, checked.businessDocId(), contract.billingPeriod(), period.start(), period.end(),
                checked.amountMil(), actor(actor));
        return findUsage(checked.businessDocId());
    }

    private void requirePriceBook(String version) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenant_price_books
                 WHERE price_book_version=? AND status='ACTIVE'
                """, Integer.class, version);
        if (count == null || count == 0) {
            throw new BusinessException("PRICE_BOOK_VERSION_INVALID", "价目表版本不存在或不可用");
        }
    }

    private ContractRow contract(long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,billing_mode,price_book_version,contract_no,signed_at,attachment_ref,
                       credit_limit_mil,billing_period,contract_status,approved_by
                  FROM tenant_contracts WHERE tenant_id=?
                """, (rs, row) -> contract(rs), tenantId).stream()
                .findFirst().orElseThrow(() -> new BusinessException("TENANT_CONTRACT_NOT_FOUND", "机构合同不存在"));
    }

    private ContractRow contractForUpdate(long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,billing_mode,price_book_version,contract_no,signed_at,attachment_ref,
                       credit_limit_mil,billing_period,contract_status,approved_by
                  FROM tenant_contracts WHERE tenant_id=? AND contract_status='ACTIVE' FOR UPDATE
                """, (rs, row) -> contract(rs), tenantId).stream()
                .findFirst().orElseThrow(() -> new BusinessException("TENANT_CONTRACT_NOT_FOUND", "机构有效合同不存在"));
    }

    private PostpaidUsageRow findUsage(String businessDocId) {
        return jdbc.query("""
                SELECT tenant_id,business_doc_id,billing_period,period_start,period_end,amount_mil,state,actor
                  FROM postpaid_usage_ledger WHERE business_doc_id=?
                """, (rs, row) -> new PostpaidUsageRow(rs.getLong("tenant_id"), rs.getString("business_doc_id"),
                rs.getString("billing_period"), date(rs, "period_start"), date(rs, "period_end"),
                rs.getLong("amount_mil"), rs.getString("state"), rs.getString("actor")), businessDocId)
                .stream().findFirst().orElse(null);
    }

    private long usedAmount(long tenantId, Period period) {
        Long used = jdbc.queryForObject("""
                SELECT COALESCE(SUM(amount_mil),0) FROM postpaid_usage_ledger
                 WHERE tenant_id=? AND period_start=? AND period_end=? AND state='RECORDED'
                """, Long.class, tenantId, period.start(), period.end());
        return used == null ? 0 : used;
    }

    private static Period period(LocalDate date, String billingPeriod) {
        if ("QUARTERLY".equals(billingPeriod)) {
            int firstMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
            LocalDate start = LocalDate.of(date.getYear(), firstMonth, 1);
            return new Period(start, start.plusMonths(3).minusDays(1));
        }
        LocalDate start = date.withDayOfMonth(1);
        return new Period(start, start.plusMonths(1).minusDays(1));
    }

    private static ContractRow contract(ResultSet rs) throws SQLException {
        return new ContractRow(rs.getLong("tenant_id"), rs.getString("billing_mode"),
                rs.getString("price_book_version"), rs.getString("contract_no"), date(rs, "signed_at"),
                rs.getString("attachment_ref"), nullableLong(rs, "credit_limit_mil"),
                rs.getString("billing_period"), rs.getString("contract_status"), rs.getString("approved_by"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private static String actor(String value) {
        String actor = text(value, "ACTOR_REQUIRED", 64);
        return actor;
    }

    private static String text(String value, String code, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw new BusinessException(code, "文本不能为空且不能超过长度限制");
        }
        return trimmed;
    }

    public record ContractCommand(String billingMode, String priceBookVersion, String contractNo,
                                  LocalDate signedAt, String attachmentRef, Long creditLimitMil,
                                  String billingPeriod) {
        ContractCommand checked() {
            String mode = text(billingMode, "BILLING_MODE_REQUIRED", 16).toUpperCase(Locale.ROOT);
            if (!BILLING_MODES.contains(mode)) {
                throw new BusinessException("BILLING_MODE_INVALID", "计费模式必须为预付费或后付费");
            }
            String priceVersion = text(priceBookVersion, "PRICE_BOOK_VERSION_REQUIRED", 64);
            String no = text(contractNo, "CONTRACT_NO_REQUIRED", 64);
            if (signedAt == null) {
                throw new BusinessException("CONTRACT_SIGNED_DATE_REQUIRED", "签约日期不能为空");
            }
            String attachment = text(attachmentRef, "CONTRACT_ATTACHMENT_REQUIRED", 255);
            if ("POSTPAID".equals(mode)) {
                if (creditLimitMil == null || creditLimitMil <= 0) {
                    throw new BusinessException("POSTPAID_CREDIT_REQUIRED", "后付费授信额度必须为正");
                }
                String period = text(billingPeriod, "POSTPAID_PERIOD_REQUIRED", 16).toUpperCase(Locale.ROOT);
                if (!BILLING_PERIODS.contains(period)) {
                    throw new BusinessException("POSTPAID_PERIOD_INVALID", "后付费账期不支持");
                }
                return new ContractCommand(mode, priceVersion, no, signedAt, attachment, creditLimitMil, period);
            }
            if (creditLimitMil != null || (billingPeriod != null && !billingPeriod.isBlank())) {
                throw new BusinessException("PREPAID_CREDIT_FORBIDDEN", "预付费合同不能填写授信额度或账期");
            }
            return new ContractCommand(mode, priceVersion, no, signedAt, attachment, null, null);
        }
    }

    public record PostpaidUsageCommand(String businessDocId, long amountMil) {
        PostpaidUsageCommand checked() {
            if (amountMil <= 0) {
                throw new BusinessException("POSTPAID_AMOUNT_INVALID", "后付费用量金额必须为正");
            }
            return new PostpaidUsageCommand(text(businessDocId, "BUSINESS_DOC_REQUIRED", 64), amountMil);
        }
    }

    public record ContractRow(long tenantId, String billingMode, String priceBookVersion, String contractNo,
                              LocalDate signedAt, String attachmentRef, Long creditLimitMil, String billingPeriod,
                              String contractStatus, String approvedBy) { }

    public record ContractOverview(long tenantId, String tenantState, String billingMode, String priceBookVersion,
                                   String contractNo, LocalDate signedAt, String attachmentRef, Long creditLimitMil,
                                   String billingPeriod, String contractStatus, String approvedBy) { }

    public record PostpaidUsageRow(long tenantId, String businessDocId, String billingPeriod, LocalDate periodStart,
                                   LocalDate periodEnd, long amountMil, String state, String actor) { }

    private record Period(LocalDate start, LocalDate end) { }
}
