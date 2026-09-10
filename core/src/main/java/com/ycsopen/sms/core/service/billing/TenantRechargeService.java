package com.ycsopen.sms.core.service.billing;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Phase 36: tenant recharge submission and finance review. Monetary values are stored in mil. */
@Service
public class TenantRechargeService {
    private static final List<String> METHODS = List.of("BANK_TRANSFER", "ALIPAY", "WECHAT", "OFFLINE");

    private final JdbcTemplate jdbc;
    private final TrialPrepaidLedgerService prepaidLedger;

    public TenantRechargeService(JdbcTemplate jdbc, TrialPrepaidLedgerService prepaidLedger) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.prepaidLedger = Objects.requireNonNull(prepaidLedger);
    }

    @Transactional
    public RechargeRecord submit(long tenantId, RechargeCommand command, String actor) {
        RechargeCommand checked = command.checked();
        String refHash = sha256(checked.transactionRef());
        try {
            jdbc.update("""
                    INSERT INTO tenant_recharge_records(tenant_id, amount_mil, recharge_method,
                        transaction_ref_hash, transaction_ref_mask, evidence_text, status, submitter_actor)
                    VALUES (?,?,?,?,?,?, 'PENDING', ?)
                    """, tenantId, checked.amountMil(), checked.rechargeMethod(), refHash,
                    mask(checked.transactionRef()), checked.evidenceText(), actor(actor));
        } catch (DataIntegrityViolationException duplicate) {
            throw new BusinessException("RECHARGE_TRANSACTION_DUPLICATE", "充值交易号已存在");
        }
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM tenant_recharge_records WHERE transaction_ref_hash=?",
                Long.class, refHash);
        return requireRecord(id == null ? 0 : id);
    }

    @Transactional(readOnly = true)
    public List<RechargeRecord> tenantRecords(long tenantId) {
        return jdbc.query("""
                SELECT * FROM tenant_recharge_records
                 WHERE tenant_id=?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> record(rs), tenantId);
    }

    @Transactional(readOnly = true)
    public List<RechargeRecord> reviewQueue(String status) {
        String effectiveStatus = blankToNull(status);
        return jdbc.query("""
                SELECT * FROM tenant_recharge_records
                 WHERE (? IS NULL OR status=?)
                 ORDER BY created_at DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> record(rs), effectiveStatus, effectiveStatus);
    }

    @Transactional
    public RechargeRecord review(long rechargeId, ReviewCommand command, String actor) {
        ReviewCommand checked = command.checked();
        RechargeRecord current = recordForUpdate(rechargeId);
        if (!"PENDING".equals(current.status())) {
            return current;
        }
        if (checked.approved()) {
            prepaidLedger.creditRecharge(current.tenantId(), businessDocId(current.id()), current.amountMil(), actor(actor));
            jdbc.update("""
                    UPDATE tenant_recharge_records
                       SET status='APPROVED', reviewer_actor=?, review_reason=?, reviewed_at=CURRENT_TIMESTAMP,
                           updated_at=CURRENT_TIMESTAMP
                     WHERE id=? AND status='PENDING'
                    """, actor(actor), checked.reason(), rechargeId);
        } else {
            jdbc.update("""
                    UPDATE tenant_recharge_records
                       SET status='REJECTED', reviewer_actor=?, review_reason=?, reviewed_at=CURRENT_TIMESTAMP,
                           updated_at=CURRENT_TIMESTAMP
                     WHERE id=? AND status='PENDING'
                    """, actor(actor), checked.reason(), rechargeId);
        }
        return requireRecord(rechargeId);
    }

    private RechargeRecord recordForUpdate(long id) {
        return jdbc.query("SELECT * FROM tenant_recharge_records WHERE id=? FOR UPDATE",
                        (rs, row) -> record(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("RECHARGE_RECORD_NOT_FOUND", "充值申请不存在"));
    }

    private RechargeRecord requireRecord(long id) {
        return jdbc.query("SELECT * FROM tenant_recharge_records WHERE id=?",
                        (rs, row) -> record(rs), id)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException("RECHARGE_RECORD_NOT_FOUND", "充值申请不存在"));
    }

    private static RechargeRecord record(ResultSet rs) throws SQLException {
        return new RechargeRecord(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("amount_mil"),
                rs.getString("recharge_method"), rs.getString("transaction_ref_mask"), rs.getString("evidence_text"),
                rs.getString("status"), rs.getString("submitter_actor"), rs.getString("reviewer_actor"),
                rs.getString("review_reason"), timestamp(rs, "reviewed_at"), timestamp(rs, "created_at"));
    }

    private static String businessDocId(long rechargeId) {
        return "RECHARGE-" + rechargeId;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String mask(String value) {
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****" + trimmed.substring(Math.max(0, trimmed.length() - 2));
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static String actor(String value) {
        String actor = blankToNull(value);
        if (actor == null || actor.length() > 64) {
            throw new BusinessException("ACTOR_REQUIRED", "操作人不能为空且不能超过长度限制");
        }
        return actor;
    }

    private static String text(String value, String code, int max) {
        String trimmed = blankToNull(value);
        if (trimmed == null || trimmed.length() > max) {
            throw new BusinessException(code, "文本不能为空且不能超过长度限制");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record RechargeCommand(long amountMil, String rechargeMethod, String transactionRef, String evidenceText) {
        RechargeCommand checked() {
            if (amountMil <= 0) {
                throw new BusinessException("RECHARGE_AMOUNT_INVALID", "充值金额必须为正");
            }
            String method = text(rechargeMethod, "RECHARGE_METHOD_REQUIRED", 32).toUpperCase(Locale.ROOT);
            if (!METHODS.contains(method)) {
                throw new BusinessException("RECHARGE_METHOD_UNSUPPORTED", "充值方式不支持");
            }
            return new RechargeCommand(amountMil, method,
                    text(transactionRef, "RECHARGE_TRANSACTION_REF_REQUIRED", 128),
                    text(evidenceText, "RECHARGE_EVIDENCE_REQUIRED", 500));
        }
    }

    public record ReviewCommand(boolean approved, String reason) {
        ReviewCommand checked() {
            return new ReviewCommand(approved, text(reason, "RECHARGE_REVIEW_REASON_REQUIRED", 255));
        }
    }

    public record RechargeRecord(long id, long tenantId, long amountMil, String rechargeMethod,
                                 String transactionRefMask, String evidenceText, String status,
                                 String submitterActor, String reviewerActor, String reviewReason,
                                 LocalDateTime reviewedAt, LocalDateTime createdAt) { }
}
