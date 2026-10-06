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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Phase 37: contract pricing, billing mode, and postpaid credit ceiling. */
@Service
public class ContractPricingService {
    private static final List<String> BILLING_MODES = List.of("PREPAID", "POSTPAID");
    private static final List<String> BILLING_PERIODS = List.of("MONTHLY", "QUARTERLY");
    private static final List<String> TRIAL_STATUSES = List.of("TRIAL", "TRIAL_FROZEN");
    private static final List<String> CONVERTIBLE_LIFECYCLES = List.of("TRIAL", "TRIAL_FROZEN");
    private static final String ANALYTICS_SOURCE = "tenants:trial_accounts:message_tasks:complaints";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern CONTRACT_ATTACHMENT =
            Pattern.compile("oss://contracts/[A-Za-z0-9][A-Za-z0-9._-]{0,199}");
    private static final String CANDIDATE_SELECT = """
            WITH trial_windows AS (
                SELECT tenant.id AS tenant_id,
                       COALESCE(trial.status, tenant.lifecycle_status) AS status,
                       COALESCE(trial.quota_total, tenant.trial_quota) AS quota_total,
                       COALESCE(trial.quota_remaining,
                                GREATEST(0, tenant.trial_quota-tenant.trial_quota_used)) AS quota_remaining,
                       COALESCE(trial.start_at, tenant.trial_start_at) AS start_at,
                       COALESCE(trial.end_at, tenant.trial_end_at) AS end_at,
                       COALESCE(trial.updated_at, tenant.updated_at) AS updated_at
                  FROM tenants tenant
             LEFT JOIN trial_accounts trial ON trial.tenant_id=tenant.id
                 WHERE tenant.lifecycle_status IN ('TRIAL','TRIAL_FROZEN')
                   AND (trial.status IN ('TRIAL','TRIAL_FROZEN')
                        OR (trial.id IS NULL
                            AND tenant.trial_quota IS NOT NULL
                            AND tenant.trial_start_at IS NOT NULL
                            AND tenant.trial_end_at IS NOT NULL))
            ), message_stats AS (
                SELECT trial.tenant_id, COUNT(message.id) AS message_count,
                       COALESCE(SUM(CASE WHEN message.send_status='DELIVERED' THEN 1 ELSE 0 END),0) AS success_count,
                       MAX(message.updated_at) AS message_freshness
                  FROM trial_windows trial
             LEFT JOIN message_tasks message
                    ON message.tenant_id=trial.tenant_id
                   AND message.created_at>=trial.start_at AND message.created_at<trial.end_at
                 GROUP BY trial.tenant_id
            ), complaint_stats AS (
                SELECT trial.tenant_id, COUNT(complaint.id) AS complaint_count,
                       MAX(complaint.created_at) AS complaint_freshness
                  FROM trial_windows trial
             LEFT JOIN complaints complaint
                    ON complaint.tenant_id=trial.tenant_id
                   AND complaint.created_at>=trial.start_at AND complaint.created_at<trial.end_at
                 GROUP BY trial.tenant_id
            )
            SELECT tenant.id AS tenant_id, tenant.tenant_no, tenant.short_name, tenant.full_name,
                   tenant.biz_manager, tenant.industry, tenant.lifecycle_status,
                   tenant.verification_status, account.status AS account_status,
                   trial.status AS trial_status, trial.quota_total, trial.quota_remaining,
                   trial.start_at, trial.end_at, trial.updated_at,
                   COALESCE(messages.message_count,0) AS message_count,
                   COALESCE(messages.success_count,0) AS success_count,
                   messages.message_freshness,
                   COALESCE(complaints.complaint_count,0) AS complaint_count,
                   complaints.complaint_freshness,
                   contract.id AS contract_id,
                   (SELECT COUNT(*) FROM tenant_price_books price WHERE price.status='ACTIVE') AS active_price_count
              FROM trial_windows trial
              JOIN tenants tenant ON tenant.id=trial.tenant_id
         LEFT JOIN tenant_accounts account ON account.tenant_id=tenant.id
         LEFT JOIN message_stats messages ON messages.tenant_id=trial.tenant_id
         LEFT JOIN complaint_stats complaints ON complaints.tenant_id=trial.tenant_id
         LEFT JOIN tenant_contracts contract ON contract.tenant_id=trial.tenant_id
            """;

    private final JdbcTemplate jdbc;

    public ContractPricingService(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Transactional
    public ContractRow approveContract(long tenantId, ContractCommand command, String actor) {
        ContractCommand checked = command.checked();
        Eligibility eligibility = conversionEligibilityForUpdate(tenantId);
        if (!eligibility.eligible()) {
            throw new BusinessException("TRIAL_CONVERSION_NOT_ELIGIBLE", String.join("；", eligibility.reasons()));
        }
        requirePriceBookForUpdate(checked.priceBookVersion());
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
        int trialUpdated = jdbc.update("""
                UPDATE trial_accounts
                   SET status='CONTRACTED', version=version+1, updated_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=? AND status IN ('TRIAL','TRIAL_FROZEN')
                """, tenantId);
        if (trialUpdated != 1) {
            throw new BusinessException("TRIAL_CONVERSION_CONFLICT", "试用状态已变化，请刷新后重试");
        }
        int tenantUpdated = jdbc.update("""
                UPDATE tenants
                   SET lifecycle_status='SIGNED', billing_mode=?, contract_signed_at=?
                 WHERE id=? AND lifecycle_status IN ('TRIAL','TRIAL_FROZEN')
                """, checked.billingMode(), checked.signedAt(), tenantId);
        if (tenantUpdated != 1) {
            throw new BusinessException("TENANT_LIFECYCLE_CONFLICT", "机构生命周期已变化，请刷新后重试");
        }
        return contract(tenantId);
    }

    /** Issue #122: server-filtered workbench; no protected tenant fields are selected. */
    @Transactional(readOnly = true)
    public List<TrialCandidate> workbench(WorkbenchQuery query) {
        WorkbenchQuery checked = query == null ? new WorkbenchQuery(null, null, null, null) : query.checked();
        String keyword = like(checked.keyword());
        String salesOwner = like(checked.salesOwner());
        String industry = like(checked.industry());
        String trialStatus = blankToNull(checked.trialStatus());
        return jdbc.query(CANDIDATE_SELECT + """
                 WHERE trial.status IN ('TRIAL','TRIAL_FROZEN')
                   AND (? IS NULL OR LOWER(tenant.short_name) LIKE ? OR LOWER(tenant.full_name) LIKE ?
                        OR LOWER(tenant.tenant_no) LIKE ?)
                   AND (? IS NULL OR LOWER(COALESCE(tenant.biz_manager,'')) LIKE ?)
                   AND (? IS NULL OR LOWER(COALESCE(tenant.industry,'')) LIKE ?)
                   AND (? IS NULL OR trial.status=?)
                 ORDER BY trial.updated_at DESC, tenant.id ASC
                 LIMIT 200
                """, (rs, row) -> candidate(rs),
                keyword, keyword, keyword, keyword,
                salesOwner, salesOwner,
                industry, industry,
                trialStatus, trialStatus);
    }

    /** Issue #122: selected tenant trial-period analysis, using the same persisted window as the list. */
    @Transactional(readOnly = true)
    public TrialAnalysis analysis(long tenantId) {
        TrialCandidate candidate = candidateById(tenantId);
        Map<LocalDate, MutableTrend> trend = new LinkedHashMap<>();
        jdbc.query("""
                SELECT CAST(message.created_at AS DATE) AS metric_date, COUNT(*) AS message_count,
                       SUM(CASE WHEN message.send_status='DELIVERED' THEN 1 ELSE 0 END) AS success_count,
                       SUM(CASE WHEN message.send_status='FAILED' THEN 1 ELSE 0 END) AS failure_count
                  FROM message_tasks message
                 WHERE message.tenant_id=?
                   AND message.created_at>=? AND message.created_at<?
                 GROUP BY CAST(message.created_at AS DATE)
                 ORDER BY metric_date
                """, rs -> {
            LocalDate day = rs.getDate("metric_date").toLocalDate();
            trend.put(day, new MutableTrend(rs.getLong("message_count"), rs.getLong("success_count"),
                    rs.getLong("failure_count"), 0));
        }, tenantId, candidate.trialStartAt(), candidate.trialEndAt());
        jdbc.query("""
                SELECT CAST(complaint.created_at AS DATE) AS metric_date, COUNT(*) AS complaint_count
                  FROM complaints complaint
                 WHERE complaint.tenant_id=?
                   AND complaint.created_at>=? AND complaint.created_at<?
                 GROUP BY CAST(complaint.created_at AS DATE)
                 ORDER BY metric_date
                """, rs -> {
            LocalDate day = rs.getDate("metric_date").toLocalDate();
            MutableTrend current = trend.getOrDefault(day, new MutableTrend(0, 0, 0, 0));
            trend.put(day, new MutableTrend(current.messageCount(), current.successCount(), current.failureCount(),
                    rs.getLong("complaint_count")));
        }, tenantId, candidate.trialStartAt(), candidate.trialEndAt());
        List<StatusCount> statuses = jdbc.query("""
                SELECT message.send_status, COUNT(*) AS status_count
                  FROM message_tasks message
                 WHERE message.tenant_id=?
                   AND message.created_at>=? AND message.created_at<?
                 GROUP BY message.send_status ORDER BY message.send_status
                """, (rs, row) -> new StatusCount(rs.getString("send_status"), rs.getLong("status_count")),
                tenantId, candidate.trialStartAt(), candidate.trialEndAt());
        List<ComplaintDetail> complaints = jdbc.query("""
                SELECT complaint.id, complaint.source, complaint.message_id, complaint.summary,
                       complaint.status, complaint.created_at
                  FROM complaints complaint
                 WHERE complaint.tenant_id=?
                   AND complaint.created_at>=? AND complaint.created_at<?
                 ORDER BY complaint.created_at DESC, complaint.id DESC LIMIT 100
                """, (rs, row) -> new ComplaintDetail(rs.getLong("id"), rs.getString("source"),
                rs.getString("message_id"), rs.getString("summary"), rs.getString("status"),
                timestamp(rs, "created_at")), tenantId, candidate.trialStartAt(), candidate.trialEndAt());
        List<DailyTrend> daily = trend.entrySet().stream()
                .map(entry -> new DailyTrend(entry.getKey(), entry.getValue().messageCount(),
                        entry.getValue().successCount(), entry.getValue().failureCount(),
                        entry.getValue().complaintCount()))
                .toList();
        return new TrialAnalysis(candidate, candidate.trialStartAt(), candidate.trialEndAt(), daily, statuses,
                complaints, ANALYTICS_SOURCE, candidate.statisticsAt(), candidate.dataQuality());
    }

    @Transactional(readOnly = true)
    public List<PriceBookOption> activePriceBooks() {
        return jdbc.query("""
                SELECT price_book_version,product_code,unit_price_mil
                  FROM tenant_price_books WHERE status='ACTIVE'
                 ORDER BY created_at DESC,id DESC
                """, (rs, row) -> new PriceBookOption(rs.getString("price_book_version"),
                rs.getString("product_code"), rs.getLong("unit_price_mil")));
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

    private void requirePriceBookForUpdate(String version) {
        List<String> rows = jdbc.query("""
                SELECT status FROM tenant_price_books
                 WHERE price_book_version=? FOR UPDATE
                """, (rs, row) -> rs.getString("status"), version);
        if (rows.isEmpty() || !"ACTIVE".equals(rows.getFirst())) {
            throw new BusinessException("PRICE_BOOK_VERSION_INVALID", "价目表版本不存在或不可用");
        }
    }

    private TrialCandidate candidateById(long tenantId) {
        return jdbc.query(CANDIDATE_SELECT + " WHERE tenant.id=? AND trial.status IN ('TRIAL','TRIAL_FROZEN')",
                        (rs, row) -> candidate(rs), tenantId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("TRIAL_CANDIDATE_NOT_FOUND", "试用机构不存在或已不在试用阶段"));
    }

    private Eligibility conversionEligibilityForUpdate(long tenantId) {
        List<TenantEligibilityState> tenantStates = jdbc.query("""
                SELECT tenant.lifecycle_status, tenant.verification_status, account.status AS account_status,
                       tenant.trial_quota, tenant.trial_quota_used,
                       tenant.trial_start_at, tenant.trial_end_at
                  FROM tenants tenant
             LEFT JOIN tenant_accounts account ON account.tenant_id=tenant.id
                 WHERE tenant.id=? FOR UPDATE
                """, (rs, row) -> new TenantEligibilityState(rs.getString("lifecycle_status"),
                rs.getString("verification_status"), rs.getString("account_status"),
                nullableInteger(rs, "trial_quota"), rs.getInt("trial_quota_used"),
                timestamp(rs, "trial_start_at"), timestamp(rs, "trial_end_at")), tenantId);
        if (tenantStates.isEmpty()) {
            return new Eligibility(false, List.of("机构或试用账户不存在"));
        }
        List<String> trialStates = jdbc.query("""
                SELECT status FROM trial_accounts WHERE tenant_id=? FOR UPDATE
                """, (rs, row) -> rs.getString("status"), tenantId);
        TenantEligibilityState tenant = tenantStates.getFirst();
        if (trialStates.isEmpty() && CONVERTIBLE_LIFECYCLES.contains(tenant.lifecycleStatus())
                && tenant.trialQuota() != null && tenant.trialStartAt() != null && tenant.trialEndAt() != null) {
            jdbc.update("""
                    INSERT INTO trial_accounts(tenant_id,status,quota_total,quota_remaining,start_at,end_at,version)
                    VALUES (?,?,?,?,?,?,0)
                    """, tenantId, tenant.lifecycleStatus(), tenant.trialQuota(),
                    Math.max(0, tenant.trialQuota() - tenant.trialQuotaUsed()),
                    tenant.trialStartAt(), tenant.trialEndAt());
            trialStates = List.of(tenant.lifecycleStatus());
        }
        int contracts = count("SELECT COUNT(*) FROM tenant_contracts WHERE tenant_id=?", tenantId);
        int activePrices = count("SELECT COUNT(*) FROM tenant_price_books WHERE status='ACTIVE'");
        String trialStatus = trialStates.isEmpty() ? null : trialStates.getFirst();
        return eligibility(trialStatus, tenant.lifecycleStatus(), tenant.verificationStatus(),
                tenant.accountStatus(), contracts > 0, activePrices > 0);
    }

    private TrialCandidate candidate(ResultSet rs) throws SQLException {
        long messages = rs.getLong("message_count");
        long successes = rs.getLong("success_count");
        long complaints = rs.getLong("complaint_count");
        LocalDateTime trialUpdated = timestamp(rs, "updated_at");
        LocalDateTime statisticsAt = latest(trialUpdated, timestamp(rs, "message_freshness"),
                timestamp(rs, "complaint_freshness"));
        boolean hasContract = nullableLong(rs, "contract_id") != null;
        boolean hasActivePrice = rs.getInt("active_price_count") > 0;
        Eligibility eligibility = eligibility(rs.getString("trial_status"), rs.getString("lifecycle_status"),
                rs.getString("verification_status"), rs.getString("account_status"), hasContract, hasActivePrice);
        LocalDateTime start = timestamp(rs, "start_at");
        LocalDateTime end = timestamp(rs, "end_at");
        int quotaTotal = rs.getInt("quota_total");
        int quotaRemaining = rs.getInt("quota_remaining");
        return new TrialCandidate(rs.getLong("tenant_id"), rs.getString("tenant_no"),
                rs.getString("short_name"), rs.getString("full_name"), rs.getString("biz_manager"),
                rs.getString("industry"), configurationSnapshotVersion(quotaTotal, start, end),
                rs.getString("lifecycle_status"), rs.getString("trial_status"), start, end,
                Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(BUSINESS_ZONE), end.toLocalDate())),
                Math.max(0, quotaTotal - quotaRemaining), quotaTotal, messages, successes,
                ratio(successes, messages), complaints, ratio(complaints, messages), statisticsAt,
                dataQuality(messages, complaints), ANALYTICS_SOURCE, eligibility.eligible(), eligibility.reasons());
    }

    private static Eligibility eligibility(String trialStatus, String lifecycleStatus,
                                           String verificationStatus, String accountStatus,
                                           boolean hasContract, boolean hasActivePrice) {
        List<String> reasons = new ArrayList<>();
        if (!TRIAL_STATUSES.contains(trialStatus)) reasons.add("试用账户状态不允许转正式");
        if (!CONVERTIBLE_LIFECYCLES.contains(lifecycleStatus)) reasons.add("机构生命周期不允许转正式");
        if (!"VERIFIED".equals(verificationStatus)) reasons.add("机构资质未通过审核");
        if (accountStatus == null) reasons.add("机构账户不存在");
        else if (!"NORMAL".equals(accountStatus)) reasons.add("机构账户已停用或冻结");
        if (hasContract) reasons.add("机构已存在合同");
        if (!hasActivePrice) reasons.add("当前没有有效价目表版本");
        return new Eligibility(reasons.isEmpty(), List.copyOf(reasons));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
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

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDateTime latest(LocalDateTime... values) {
        LocalDateTime latest = null;
        for (LocalDateTime value : values) {
            if (value != null && (latest == null || value.isAfter(latest))) latest = value;
        }
        return latest;
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) return null;
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private static String dataQuality(long messages, long complaints) {
        if (messages == 0 && complaints > 0) return "INCOMPLETE";
        if (messages == 0) return "NO_DATA";
        return "COMPLETE";
    }

    private static String configurationSnapshotVersion(int quotaTotal, LocalDateTime start, LocalDateTime end) {
        String canonical = quotaTotal + "|" + start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                + "|" + end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "TRIAL-SNAPSHOT-V1-" + HexFormat.of().withUpperCase().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String like(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
            if (!CONTRACT_ATTACHMENT.matcher(attachment).matches() || attachment.contains("..")) {
                throw new BusinessException("CONTRACT_ATTACHMENT_INVALID",
                        "合同附件引用必须是安全的 oss://contracts/<object> 格式");
            }
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

    public record WorkbenchQuery(String keyword, String salesOwner, String industry, String trialStatus) {
        WorkbenchQuery checked() {
            String status = blankToNull(trialStatus);
            if (status != null) {
                status = status.toUpperCase(Locale.ROOT);
                if (!TRIAL_STATUSES.contains(status)) {
                    throw new BusinessException("TRIAL_STATUS_FILTER_INVALID", "试用状态筛选值无效");
                }
            }
            return new WorkbenchQuery(bounded(keyword, 128), bounded(salesOwner, 64), bounded(industry, 64), status);
        }
    }

    public record TrialCandidate(long tenantId, String tenantNo, String shortName, String fullName,
                                 String salesOwner, String industry, String configurationSnapshotVersion,
                                 String lifecycleStatus, String trialStatus, LocalDateTime trialStartAt,
                                 LocalDateTime trialEndAt, long remainingDays, int quotaUsed, int quotaTotal,
                                 long messageCount, long successCount, BigDecimal successRate,
                                 long complaintCount, BigDecimal complaintRate, LocalDateTime statisticsAt,
                                 String dataQuality, String sourceRegistry, boolean conversionEligible,
                                 List<String> ineligibilityReasons) { }

    public record TrialAnalysis(TrialCandidate tenant, LocalDateTime periodStart, LocalDateTime periodEnd,
                                List<DailyTrend> trend, List<StatusCount> messageStatuses,
                                List<ComplaintDetail> complaints, String sourceRegistry,
                                LocalDateTime statisticsAt, String dataQuality) { }

    public record DailyTrend(LocalDate date, long messageCount, long successCount,
                             long failureCount, long complaintCount) { }
    public record StatusCount(String status, long count) { }
    public record ComplaintDetail(long id, String source, String messageId, String summary,
                                  String status, LocalDateTime createdAt) { }
    public record PriceBookOption(String priceBookVersion, String productCode, long unitPriceMil) { }

    private record Period(LocalDate start, LocalDate end) { }
    private record Eligibility(boolean eligible, List<String> reasons) { }
    private record MutableTrend(long messageCount, long successCount, long failureCount, long complaintCount) { }

    private record TenantEligibilityState(String lifecycleStatus, String verificationStatus,
                                          String accountStatus, Integer trialQuota, int trialQuotaUsed,
                                          LocalDateTime trialStartAt, LocalDateTime trialEndAt) { }

    private static String bounded(String value, int max) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        if (normalized.length() > max) {
            throw new BusinessException("WORKBENCH_FILTER_TOO_LONG", "筛选条件超过长度限制");
        }
        return normalized;
    }
}
