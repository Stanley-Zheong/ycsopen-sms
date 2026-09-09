package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Phase 22: trial quota and prepaid ledger contract. Monetary values are stored in mil. */
@Service
public class TrialPrepaidLedgerService {
    private static final int DEFAULT_TRIAL_QUOTA = 500;

    private final JdbcTemplate jdbc;

    public TrialPrepaidLedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public TrialOverview activateTrial(long tenantId, Integer quota, LocalDateTime startAt, LocalDateTime endAt, String actor) {
        actor(actor);
        int effectiveQuota = quota == null ? DEFAULT_TRIAL_QUOTA : quota;
        if (effectiveQuota <= 0) throw failure("TRIAL_QUOTA_INVALID", "试用额度必须为正整数");
        LocalDateTime start = startAt == null ? LocalDateTime.now() : startAt;
        LocalDateTime end = endAt == null ? start.plusDays(14) : endAt;
        if (start.isAfter(end)) throw failure("TRIAL_VALIDITY_INVALID", "试用开始时间不能晚于结束时间");
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM trial_accounts WHERE tenant_id=?", Integer.class, tenantId);
        if (existing != null && existing > 0) {
            jdbc.update("""
                    UPDATE trial_accounts
                    SET status='TRIAL', quota_total=?, quota_remaining=?, start_at=?, end_at=?,
                        version=version+1, updated_at=CURRENT_TIMESTAMP
                    WHERE tenant_id=?
                    """, effectiveQuota, effectiveQuota, start, end, tenantId);
        } else {
            jdbc.update("""
                    INSERT INTO trial_accounts(tenant_id,status,quota_total,quota_remaining,start_at,end_at,version)
                    VALUES (?,'TRIAL',?,?,?,?,0)
                    """, tenantId, effectiveQuota, effectiveQuota, start, end);
        }
        return overview(tenantId);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public TrialOverview consumeTrial(long tenantId, String messageRef, String businessType, String actor) {
        String ref = text(messageRef, "MESSAGE_REF_REQUIRED", 64);
        String effectiveBusinessType = text(businessType, "BUSINESS_TYPE_REQUIRED", 64);
        TrialOverview current = overviewForUpdate(tenantId);
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM trial_consumption_ledger WHERE tenant_id=? AND message_ref=?",
                Integer.class, tenantId, ref);
        if (existing != null && existing > 0) return overview(tenantId);
        if (!"TRIAL".equals(current.trialStatus())) {
            throw failure("TRIAL_NOT_ACTIVE", "当前机构不在可试用发送状态");
        }
        if (current.validUntil().isBefore(LocalDateTime.now())) {
            freezeTrial(tenantId, "EXPIRED", actor);
            throw failure("TRIAL_EXPIRED", "试用有效期已过");
        }
        if (current.quotaRemaining() <= 0) {
            freezeTrial(tenantId, "EXHAUSTED", actor);
            throw failure("TRIAL_QUOTA_EXHAUSTED", "试用额度已耗尽");
        }
        int updated = jdbc.update("UPDATE trial_accounts SET quota_remaining=quota_remaining-1, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND status='TRIAL' AND quota_remaining>0",
                tenantId);
        if (updated != 1) {
            freezeTrial(tenantId, "EXHAUSTED", actor);
            throw failure("TRIAL_QUOTA_EXHAUSTED", "试用额度已耗尽");
        }
        jdbc.update("""
                INSERT INTO trial_consumption_ledger(tenant_id,message_ref,business_type,quota_delta,amount_mil,entry_type,state,actor)
                VALUES (?,?,?,?,0,'TRIAL_CONSUME','CONFIRMED',?)
                """, tenantId, ref, effectiveBusinessType, -1, actor(actor));
        TrialOverview after = overview(tenantId);
        if (after.quotaRemaining() == 0) freezeTrial(tenantId, "EXHAUSTED", actor);
        return overview(tenantId);
    }

    @Transactional
    public TrialOverview freezeTrial(long tenantId, String reason, String actor) {
        int updated = jdbc.update("UPDATE trial_accounts SET status='TRIAL_FROZEN', version=version+1, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND status='TRIAL'",
                tenantId);
        if (updated == 1) {
            jdbc.update("""
                    INSERT INTO trial_consumption_ledger(tenant_id,message_ref,business_type,quota_delta,amount_mil,entry_type,state,actor)
                    VALUES (?,?,?,?,0,'TRIAL_FREEZE',?,?)
                    """, tenantId, "FREEZE-" + UUID.randomUUID().toString().substring(0, 8), reason, 0, "FROZEN", actor(actor));
        }
        return overview(tenantId);
    }

    @Transactional
    public ConversionRequest requestConversion(long tenantId, String actor) {
        TrialOverview overview = overview(tenantId);
        if (!List.of("TRIAL", "TRIAL_FROZEN").contains(overview.trialStatus())) {
            throw failure("TRIAL_CONVERSION_STATE_INVALID", "当前试用状态不能发起转正申请");
        }
        jdbc.update("""
                INSERT INTO trial_conversion_requests(tenant_id,trial_status,status,actor)
                VALUES (?,?, 'REQUESTED', ?)
                """, tenantId, overview.trialStatus(), actor(actor));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM trial_conversion_requests WHERE tenant_id=?", Long.class, tenantId);
        return new ConversionRequest(id, tenantId, overview.trialStatus(), "REQUESTED");
    }

    @Transactional
    public PrepaidResult reservePrepaid(long tenantId, String businessDocId, String businessType,
                                        String channelCode, long priceMil, int quantity, String actor) {
        String docId = text(businessDocId, "BUSINESS_DOC_REQUIRED", 64);
        if (priceMil <= 0 || quantity <= 0) throw failure("PRICE_OR_QUANTITY_INVALID", "价格和数量必须为正");
        long amount = priceMil * quantity;
        PrepaidResult existing = findPrepaidLedger(docId);
        if (existing != null) return existing;
        ensurePrepaidAccount(tenantId);
        PrepaidAccount account = prepaidAccountForUpdate(tenantId);
        existing = findPrepaidLedger(docId);
        if (existing != null) return existing;
        if (account.balanceMil() - account.frozenMil() < amount) {
            jdbc.update("UPDATE prepaid_accounts SET status='INSUFFICIENT_FUNDS' WHERE tenant_id=?", tenantId);
            throw failure("INSUFFICIENT_BALANCE", "可用余额不足");
        }
        int updated = jdbc.update("UPDATE prepaid_accounts SET frozen_mil=frozen_mil+?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND version=?",
                amount, tenantId, account.version());
        if (updated != 1) throw failure("PREPAID_ACCOUNT_CONFLICT", "余额账户版本冲突，请重试");
        jdbc.update("""
                INSERT INTO prepaid_ledger(tenant_id,business_doc_id,business_type,channel_code,price_mil,quantity,amount_mil,state,transaction_ref,actor)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, tenantId, docId, text(businessType, "BUSINESS_TYPE_REQUIRED", 64), channelCode, priceMil,
                quantity, amount, "RESERVED", null, actor(actor));
        audit(tenantId, docId, "RESERVE", account.balanceMil(), account.frozenMil(), amount, actor);
        return prepaidLedger(docId);
    }

    @Transactional
    public PrepaidResult confirmPrepaid(String businessDocId, String transactionRef, String actor) {
        PrepaidResult row = prepaidLedger(businessDocId);
        if (!"RESERVED".equals(row.state())) return row;
        PrepaidAccount before = prepaidAccountForUpdate(row.tenantId());
        int transitioned = jdbc.update("UPDATE prepaid_ledger SET state='CONFIRMED', transaction_ref=? WHERE business_doc_id=? AND state='RESERVED'",
                text(transactionRef, "TRANSACTION_REF_REQUIRED", 128), businessDocId);
        if (transitioned != 1) return prepaidLedger(businessDocId);
        int updated = jdbc.update("UPDATE prepaid_accounts SET frozen_mil=frozen_mil-?, balance_mil=balance_mil-?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND version=?",
                row.amountMil(), row.amountMil(), row.tenantId(), before.version());
        if (updated != 1) throw failure("PREPAID_ACCOUNT_CONFLICT", "余额账户版本冲突，请重试");
        audit(row.tenantId(), businessDocId, "CONFIRM", before.balanceMil(), before.frozenMil(), row.amountMil(), actor);
        return prepaidLedger(businessDocId);
    }

    @Transactional
    public PrepaidResult reversePrepaid(String businessDocId, String actor) {
        PrepaidResult row = prepaidLedger(businessDocId);
        if (!"RESERVED".equals(row.state())) return row;
        PrepaidAccount before = prepaidAccountForUpdate(row.tenantId());
        int transitioned = jdbc.update("UPDATE prepaid_ledger SET state='REVERSED' WHERE business_doc_id=? AND state='RESERVED'", businessDocId);
        if (transitioned != 1) return prepaidLedger(businessDocId);
        int updated = jdbc.update("UPDATE prepaid_accounts SET frozen_mil=frozen_mil-?, version=version+1, updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND version=?",
                row.amountMil(), row.tenantId(), before.version());
        if (updated != 1) throw failure("PREPAID_ACCOUNT_CONFLICT", "余额账户版本冲突，请重试");
        audit(row.tenantId(), businessDocId, "REVERSE", before.balanceMil(), before.frozenMil(), row.amountMil(), actor);
        return prepaidLedger(businessDocId);
    }

    @Transactional(readOnly = true)
    public TrialOverview overview(long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,status,quota_total,quota_remaining,start_at,end_at,version
                FROM trial_accounts WHERE tenant_id=?
                """, (rs, row) -> new TrialOverview(rs.getLong("tenant_id"), rs.getString("status"),
                rs.getInt("quota_total"), rs.getInt("quota_remaining"),
                rs.getTimestamp("start_at").toLocalDateTime(), rs.getTimestamp("end_at").toLocalDateTime(),
                rs.getInt("version")), tenantId).stream()
                .findFirst()
                .orElse(new TrialOverview(tenantId, "UNACTIVATED", DEFAULT_TRIAL_QUOTA, DEFAULT_TRIAL_QUOTA,
                        LocalDateTime.now(), LocalDateTime.now().plusDays(14), 0));
    }

    private TrialOverview overviewForUpdate(long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,status,quota_total,quota_remaining,start_at,end_at,version
                FROM trial_accounts WHERE tenant_id=? FOR UPDATE
                """, (rs, row) -> new TrialOverview(rs.getLong("tenant_id"), rs.getString("status"),
                rs.getInt("quota_total"), rs.getInt("quota_remaining"),
                rs.getTimestamp("start_at").toLocalDateTime(), rs.getTimestamp("end_at").toLocalDateTime(),
                rs.getInt("version")), tenantId).stream()
                .findFirst()
                .orElse(new TrialOverview(tenantId, "UNACTIVATED", DEFAULT_TRIAL_QUOTA, DEFAULT_TRIAL_QUOTA,
                        LocalDateTime.now(), LocalDateTime.now().plusDays(14), 0));
    }

    @Transactional(readOnly = true)
    public List<ConsumptionEntry> consumption(Long tenantId, String businessType) {
        return jdbc.query("""
                SELECT tenant_id,message_ref,business_type,quota_delta,amount_mil,entry_type,state,actor,created_at
                FROM trial_consumption_ledger
                WHERE (? IS NULL OR tenant_id=?) AND (? IS NULL OR business_type=?)
                ORDER BY created_at DESC,id DESC LIMIT 200
                """, (rs, row) -> new ConsumptionEntry(rs.getLong("tenant_id"), rs.getString("message_ref"),
                rs.getString("business_type"), rs.getInt("quota_delta"), rs.getLong("amount_mil"),
                rs.getString("entry_type"), rs.getString("state"), rs.getString("actor"),
                rs.getTimestamp("created_at").toLocalDateTime()), tenantId, tenantId, blankToNull(businessType), blankToNull(businessType));
    }

    @Transactional(readOnly = true)
    public List<BalanceAuditEntry> audits(Long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,business_doc_id,mutation_type,amount_mil,before_balance_mil,after_balance_mil,
                       before_frozen_mil,after_frozen_mil,account_version,actor,created_at
                FROM balance_audit_entries WHERE (? IS NULL OR tenant_id=?)
                ORDER BY created_at DESC,id DESC LIMIT 200
                """, (rs, row) -> new BalanceAuditEntry(rs.getLong("tenant_id"), rs.getString("business_doc_id"),
                rs.getString("mutation_type"), rs.getLong("amount_mil"), rs.getLong("before_balance_mil"),
                rs.getLong("after_balance_mil"), rs.getLong("before_frozen_mil"), rs.getLong("after_frozen_mil"),
                rs.getInt("account_version"), rs.getString("actor"), rs.getTimestamp("created_at").toLocalDateTime()),
                tenantId, tenantId);
    }

    PrepaidAccount creditForTest(long tenantId, long amountMil) {
        ensurePrepaidAccount(tenantId);
        jdbc.update("UPDATE prepaid_accounts SET balance_mil=balance_mil+?, version=version+1 WHERE tenant_id=?", amountMil, tenantId);
        return prepaidAccount(tenantId);
    }

    private void ensurePrepaidAccount(long tenantId) {
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM prepaid_accounts WHERE tenant_id=?", Integer.class, tenantId);
        if (existing == null || existing == 0) {
            try {
                jdbc.update("""
                        INSERT INTO prepaid_accounts(tenant_id,balance_mil,frozen_mil,status,version)
                        VALUES (?,0,0,'NORMAL',0)
                        """, tenantId);
            } catch (DataIntegrityViolationException duplicate) {
                // Another transaction created the account between the existence check and insert.
            }
        }
    }

    private PrepaidAccount prepaidAccount(long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,balance_mil,frozen_mil,status,version FROM prepaid_accounts WHERE tenant_id=?
                """, (rs, row) -> new PrepaidAccount(rs.getLong("tenant_id"), rs.getLong("balance_mil"),
                rs.getLong("frozen_mil"), rs.getString("status"), rs.getInt("version")), tenantId).stream()
                .findFirst().orElseThrow(() -> failure("PREPAID_ACCOUNT_NOT_FOUND", "余额账户不存在"));
    }

    private PrepaidAccount prepaidAccountForUpdate(long tenantId) {
        return jdbc.query("""
                SELECT tenant_id,balance_mil,frozen_mil,status,version FROM prepaid_accounts WHERE tenant_id=? FOR UPDATE
                """, (rs, row) -> new PrepaidAccount(rs.getLong("tenant_id"), rs.getLong("balance_mil"),
                rs.getLong("frozen_mil"), rs.getString("status"), rs.getInt("version")), tenantId).stream()
                .findFirst().orElseThrow(() -> failure("PREPAID_ACCOUNT_NOT_FOUND", "余额账户不存在"));
    }

    private PrepaidResult prepaidLedger(String businessDocId) {
        PrepaidResult found = findPrepaidLedger(businessDocId);
        if (found == null) throw failure("PREPAID_LEDGER_NOT_FOUND", "预付费业务单不存在");
        return found;
    }

    private PrepaidResult findPrepaidLedger(String businessDocId) {
        return jdbc.query("""
                SELECT tenant_id,business_doc_id,business_type,channel_code,price_mil,quantity,amount_mil,state,transaction_ref,actor
                FROM prepaid_ledger WHERE business_doc_id=?
                """, (rs, row) -> new PrepaidResult(rs.getLong("tenant_id"), rs.getString("business_doc_id"),
                rs.getString("business_type"), rs.getString("channel_code"), rs.getLong("price_mil"),
                rs.getInt("quantity"), rs.getLong("amount_mil"), rs.getString("state"),
                rs.getString("transaction_ref"), rs.getString("actor")), text(businessDocId, "BUSINESS_DOC_REQUIRED", 64))
                .stream().findFirst().orElse(null);
    }

    private void audit(long tenantId, String docId, String type, long previousBalance, long previousFrozen, long amount, String actor) {
        PrepaidAccount account = prepaidAccount(tenantId);
        jdbc.update("""
                INSERT INTO balance_audit_entries(tenant_id,business_doc_id,mutation_type,amount_mil,before_balance_mil,
                    after_balance_mil,before_frozen_mil,after_frozen_mil,account_version,actor)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """, tenantId, docId, type, amount, previousBalance, account.balanceMil(),
                previousFrozen, account.frozenMil(), account.version(), actor(actor));
    }

    private String text(String value, String code, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) throw failure(code, "文本不能为空且不能超过长度限制");
        return trimmed;
    }

    private String actor(String value) {
        return text(value, "ACTOR_REQUIRED", 64);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    public record TrialOverview(long tenantId, String trialStatus, int quotaTotal, int quotaRemaining,
                                LocalDateTime validFrom, LocalDateTime validUntil, int version) { }
    public record ConversionRequest(Long id, long tenantId, String trialStatus, String status) { }
    public record PrepaidAccount(long tenantId, long balanceMil, long frozenMil, String status, int version) { }
    public record PrepaidResult(long tenantId, String businessDocId, String businessType, String channelCode,
                                long priceMil, int quantity, long amountMil, String state, String transactionRef,
                                String actor) { }
    public record ConsumptionEntry(long tenantId, String messageRef, String businessType, int quotaDelta,
                                   long amountMil, String entryType, String state, String actor,
                                   LocalDateTime createdAt) { }
    public record BalanceAuditEntry(long tenantId, String businessDocId, String mutationType, long amountMil,
                                    long beforeBalanceMil, long afterBalanceMil, long beforeFrozenMil,
                                    long afterFrozenMil, int accountVersion, String actor, LocalDateTime createdAt) { }
}
