package com.ycsopen.sms.core.service.risk;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.BlacklistEntryProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.BlindIndexLookupService;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageRouting;
import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.service.routing.BlacklistChecker;
import com.ycsopen.sms.core.service.routing.RiskDecisionRecorder;
import com.ycsopen.sms.core.service.routing.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Phase 16: protected black/white-list management and deterministic risk decision evidence. */
@Service
public class BlacklistRiskControlService implements RiskDecisionRecorder {
    private static final Logger log = LoggerFactory.getLogger(BlacklistRiskControlService.class);
    private static final Pattern MOBILE = Pattern.compile("1[3-9][0-9]{9}");
    private static final Set<String> LIST_TYPES = Set.of("BLACK", "WHITE");
    private static final Set<String> SOURCES = Set.of("MANUAL", "BATCH_IMPORT", "THIRD_PARTY_RISK", "COMPLAINT_LINKED");
    private static final Set<String> LEVELS = Set.of("BASIC", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> FALLBACKS = Set.of("ALLOW", "CACHE");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");

    private final JdbcTemplate jdbc;
    private final ProtectedListWriter protectedWriter;
    private final ProtectedListDisabler protectedDisabler;
    private final RiskLookupPort riskLookup;

    @Autowired
    public BlacklistRiskControlService(JdbcTemplate jdbc,
                                       BlacklistEntryProtectionAdapter adapter,
                                       MessageTaskProtectionAdapter messageTaskProtectionAdapter,
                                       BlindIndexLookupService blindIndexLookupService) {
        this(jdbc, (tenantId, mobile, listType, source, reason) ->
                adapter.create(tenantId, mobile, BlacklistEntry.ListType.valueOf(listType),
                        BlacklistEntry.Source.valueOf(source), reason),
                adapter::disable,
                new ProtectedRiskLookup(messageTaskProtectionAdapter, blindIndexLookupService));
    }

    BlacklistRiskControlService(JdbcTemplate jdbc,
                                ProtectedListWriter protectedWriter,
                                ProtectedListDisabler protectedDisabler,
                                RiskLookupPort riskLookup) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.protectedWriter = Objects.requireNonNull(protectedWriter);
        this.protectedDisabler = Objects.requireNonNull(protectedDisabler);
        this.riskLookup = Objects.requireNonNull(riskLookup);
    }

    @Transactional(readOnly = true)
    public List<BlacklistEntryRow> entries(String tenantId, String listType, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, masked_mobile, mobile_hash, list_type, reason, source, status,
                       created_by, created_at, expires_at
                FROM blacklist_entries
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND tenant_id=?");
            args.add(longValue(tenantId, "BLACKLIST_TENANT_INVALID"));
        }
        if (listType != null && !listType.isBlank()) {
            sql.append(" AND list_type=?");
            args.add(enumValue(listType, LIST_TYPES, "BLACKLIST_TYPE_INVALID"));
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=?");
            args.add(enumValue(status, STATUSES, "BLACKLIST_STATUS_INVALID"));
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT 200");
        return jdbc.query(sql.toString(), (rs, row) -> new BlacklistEntryRow(
                rs.getLong("id"), rs.getObject("tenant_id", Long.class), rs.getString("masked_mobile"),
                rs.getString("mobile_hash"), rs.getString("list_type"), rs.getString("reason"),
                rs.getString("source"), rs.getString("status"), rs.getString("created_by"),
                rs.getTimestamp("created_at").toLocalDateTime(), nullableDateTime(rs.getTimestamp("expires_at"))), args.toArray());
    }

    @Transactional
    public BlacklistEntryRow createEntry(BlacklistEntryCreateRequest request, String actor) {
        if (request == null) {
            throw failure("BLACKLIST_REQUEST_REQUIRED", "黑白名单请求不能为空");
        }
        Long tenantId = optionalTenant(request.tenantId());
        String mobile = mobile(request.mobile());
        String listType = enumValue(request.listType(), LIST_TYPES, "BLACKLIST_TYPE_INVALID");
        String source = enumValue(request.source(), SOURCES, "BLACKLIST_SOURCE_INVALID");
        String reason = text(request.reason(), "BLACKLIST_REASON_REQUIRED", 255);
        if ("WHITE".equals(listType) && tenantId == null) {
            throw failure("BLACKLIST_WHITE_TENANT_REQUIRED", "白名单必须绑定机构");
        }
        long id = protectedWriter.create(tenantId, mobile, listType, source, reason);
        jdbc.update("UPDATE blacklist_entries SET created_by=?, expires_at=? WHERE id=?",
                actor(actor), request.expiresAt(), id);
        return entriesById(id);
    }

    @Transactional
    public BlacklistImportResponse importEntries(BlacklistImportRequest request, String actor) {
        if (request == null || request.mobiles() == null || request.mobiles().isEmpty()) {
            throw failure("BLACKLIST_IMPORT_REQUIRED", "导入号码不能为空");
        }
        if (request.mobiles().size() > 500) {
            throw failure("BLACKLIST_IMPORT_TOO_LARGE", "单次最多导入500个号码");
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (String candidate : request.mobiles()) {
            try {
                createEntry(new BlacklistEntryCreateRequest(request.tenantId(), candidate,
                        request.listType(), request.source(), request.reason(), request.expiresAt()), actor);
                success += 1;
            } catch (RuntimeException failure) {
                errors.add(maskUntrusted(candidate) + ": " + failure.getMessage());
            }
        }
        return new BlacklistImportResponse(success, errors.size(), errors);
    }

    @Transactional
    public BlacklistEntryRow disableEntry(long id) {
        if (id < 1) {
            throw failure("BLACKLIST_ID_INVALID", "黑白名单编号不合法");
        }
        protectedDisabler.disable(id);
        return entriesById(id);
    }

    @Transactional(readOnly = true)
    public ExportRequestResponse exportRequest(String tenantId, String listType) {
        List<BlacklistEntryRow> rows = entries(tenantId, listType, "ACTIVE");
        return new ExportRequestResponse("risk-export-" + UUID.randomUUID(), rows.size(), "REQUESTED");
    }

    @Transactional(readOnly = true)
    public List<RiskProviderConfigRow> providerConfigs() {
        return jdbc.query("""
                SELECT id, provider_name, provider_url, credential_ref, check_level, threshold_score,
                       timeout_ms, fallback_policy, status, cache_ttl_seconds, created_by, created_at
                FROM risk_provider_configs
                ORDER BY id DESC
                """, (rs, row) -> new RiskProviderConfigRow(rs.getLong("id"), rs.getString("provider_name"),
                rs.getString("provider_url"), rs.getString("credential_ref"), rs.getString("check_level"),
                rs.getInt("threshold_score"), rs.getInt("timeout_ms"), rs.getString("fallback_policy"),
                rs.getString("status"), rs.getInt("cache_ttl_seconds"), rs.getString("created_by"),
                rs.getTimestamp("created_at").toLocalDateTime()));
    }

    @Transactional
    public RiskProviderConfigRow saveProvider(RiskProviderConfigRequest request, String actor) {
        if (request == null) {
            throw failure("RISK_PROVIDER_REQUIRED", "风控服务配置不能为空");
        }
        String name = text(request.providerName(), "RISK_PROVIDER_NAME_REQUIRED", 64);
        String url = text(request.providerUrl(), "RISK_PROVIDER_URL_REQUIRED", 255);
        String credential = text(request.credentialRef(), "RISK_PROVIDER_CREDENTIAL_REQUIRED", 128);
        String level = enumValue(request.checkLevel(), LEVELS, "RISK_PROVIDER_LEVEL_INVALID");
        int threshold = boundedInt(request.thresholdScore(), 0, 100, "RISK_PROVIDER_THRESHOLD_INVALID");
        int timeout = boundedInt(request.timeoutMs(), 100, 10_000, "RISK_PROVIDER_TIMEOUT_INVALID");
        String fallback = enumValue(request.fallbackPolicy(), FALLBACKS, "RISK_PROVIDER_FALLBACK_INVALID");
        String status = enumValue(request.status(), STATUSES, "RISK_PROVIDER_STATUS_INVALID");
        int cacheTtl = boundedInt(request.cacheTtlSeconds(), 1, 86_400, "RISK_PROVIDER_CACHE_TTL_INVALID");
        jdbc.update("""
                INSERT INTO risk_provider_configs(provider_name,provider_url,credential_ref,check_level,threshold_score,
                    timeout_ms,fallback_policy,status,cache_ttl_seconds,created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE provider_url=VALUES(provider_url), credential_ref=VALUES(credential_ref),
                    check_level=VALUES(check_level), threshold_score=VALUES(threshold_score), timeout_ms=VALUES(timeout_ms),
                    fallback_policy=VALUES(fallback_policy), status=VALUES(status), cache_ttl_seconds=VALUES(cache_ttl_seconds)
                """, name, url, credential, level, threshold, timeout, fallback, status, cacheTtl, actor(actor));
        return providerConfigs().stream().filter(row -> row.providerName().equals(name)).findFirst()
                .orElseThrow(() -> failure("RISK_PROVIDER_SAVE_FAILED", "风控服务配置保存失败"));
    }

    @Transactional
    public List<RiskDecisionRow> evaluate(RiskCheckRequest request, String actor) {
        if (request == null || request.mobileRefs() == null || request.mobileRefs().isEmpty()) {
            throw failure("RISK_CHECK_REQUIRED", "风控检测请求不能为空");
        }
        if (request.mobileRefs().size() > 500) {
            throw failure("RISK_CHECK_BATCH_TOO_LARGE", "批量检测最多500个号码");
        }
        long tenantId = requiredTenant(request.tenantId());
        String requestId = request.requestId() == null || request.requestId().isBlank()
                ? "risk-" + UUID.randomUUID() : text(request.requestId(), "RISK_REQUEST_ID_INVALID", 64);
        Set<String> uniqueRefs = new java.util.LinkedHashSet<>();
        for (String rawRef : request.mobileRefs()) {
            String normalized = mobile(rawRef);
            if (!uniqueRefs.add(normalized)) {
                throw failure("RISK_CHECK_DUPLICATE_REF", "批量检测号码不能重复");
            }
        }
        List<RiskDecisionRow> result = new ArrayList<>();
        int itemCount = request.mobileRefs().size();
        for (String mobile : uniqueRefs) {
            RiskDecision decision = decide(tenantId, requestId, mobile, request.forceProviderFailure(), itemCount);
            long id = recordDecision(requestId, tenantId, decision.mobileRef(), decision, actor(actor));
            result.add(new RiskDecisionRow(id, requestId, tenantId, decision.mobileRef(), decision.sourceCategory(),
                    decision.riskResult(), decision.traceReason(), false, false, LocalDateTime.now()));
        }
        return result;
    }

    @Override
    @Transactional
    public void recordBlacklistDecision(RoutingContext context, BlacklistChecker.Result result) {
        try {
            if (context == null || result == null || !result.recordable()) {
                return;
            }
            long tenantId = requiredTenant(context.getTenantId());
            String mobileRef = routingMobileRef(context);
            RiskDecision decision = new RiskDecision(mobileRef, result.sourceCategory(), result.riskResult(), result.reason());
            recordDecision("routing-" + UUID.randomUUID(), tenantId, mobileRef, decision, "routing-engine");
        } catch (RuntimeException failure) {
            log.warn("risk decision evidence recording failed while routing blacklist rejection; preserving reject decision");
        }
    }

    @Transactional(readOnly = true)
    public RiskAnalyticsResponse analytics() {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN risk_result='BLOCK' THEN 1 ELSE 0 END) AS blocked,
                       SUM(CASE WHEN risk_result IN ('ALLOW','DEGRADED_ALLOW','DEGRADED_CACHE') THEN 1 ELSE 0 END) AS allowed,
                       SUM(CASE WHEN source_category='SYSTEM_BLACKLIST' THEN 1 ELSE 0 END) AS system_hits,
                       SUM(CASE WHEN source_category='TENANT_BLACKLIST' THEN 1 ELSE 0 END) AS tenant_hits,
                       SUM(CASE WHEN source_category IN ('THIRD_PARTY_RISK','THIRD_PARTY_DEGRADED') THEN 1 ELSE 0 END) AS provider_hits,
                       SUM(CASE WHEN source_category='THIRD_PARTY_DEGRADED' THEN 1 ELSE 0 END) AS degraded
                FROM risk_intercept_decisions
                """, (rs, row) -> new RiskAnalyticsResponse(rs.getLong("total"), rs.getLong("blocked"),
                rs.getLong("allowed"), rs.getLong("system_hits"), rs.getLong("tenant_hits"),
                rs.getLong("provider_hits"), rs.getLong("degraded"), appealCount()));
    }

    @Transactional
    public AppealResponse appeal(AppealRequest request, String actor) {
        if (request == null) {
            throw failure("RISK_APPEAL_REQUIRED", "申诉请求不能为空");
        }
        long decisionId = requiredId(request.decisionId(), "RISK_DECISION_ID_INVALID");
        List<String> originals = jdbc.query("SELECT risk_result FROM risk_intercept_decisions WHERE id=?",
                (rs, row) -> rs.getString(1), decisionId);
        if (originals.isEmpty()) {
            throw failure("RISK_DECISION_NOT_FOUND", "风控决策不存在");
        }
        String original = originals.getFirst();
        jdbc.update("""
                INSERT INTO risk_intercept_appeals(decision_id,original_result,appeal_result,reason,actor)
                VALUES (?,?,?,?,?)
                """, decisionId, original, "FALSE_POSITIVE",
                text(request.reason(), "RISK_APPEAL_REASON_REQUIRED", 255), actor(actor));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM risk_intercept_appeals WHERE decision_id=?", Long.class, decisionId);
        return new AppealResponse(id == null ? 0 : id, decisionId, original, "FALSE_POSITIVE");
    }

    private RiskDecision decide(long tenantId, String requestId, String normalizedMobile,
                                Boolean forceProviderFailure, int itemCount) {
        RiskLookupResult lookup = riskLookup.lookup(tenantId, requestId, normalizedMobile);
        if (!"NO_MATCH".equals(lookup.sourceCategory())) {
            return new RiskDecision(lookup.opaqueMobileRef(), lookup.sourceCategory(),
                    lookup.riskResult(), lookup.traceReason());
        }
        RiskProviderConfigRow provider = providerConfigs().stream()
                .filter(row -> "ACTIVE".equals(row.status())).findFirst().orElse(null);
        if (provider == null) {
            return new RiskDecision(lookup.opaqueMobileRef(), "NO_MATCH", "ALLOW",
                    "未启用第三方风险服务且未命中黑名单");
        }
        boolean degraded = Boolean.TRUE.equals(forceProviderFailure);
        boolean hit = !degraded && normalizedMobile.endsWith("9999");
        recordProviderLog(tenantId, provider, lookup.opaqueMobileRef(), hit, degraded, requestKind(itemCount), itemCount);
        if (degraded && "CACHE".equals(provider.fallbackPolicy())) {
            return new RiskDecision(lookup.opaqueMobileRef(), "THIRD_PARTY_DEGRADED",
                    "DEGRADED_CACHE", "第三方风险服务失败，按新鲜缓存策略降级");
        }
        if (degraded) {
            return new RiskDecision(lookup.opaqueMobileRef(), "THIRD_PARTY_DEGRADED",
                    "DEGRADED_ALLOW", "第三方风险服务失败，按配置放行并记录降级");
        }
        return hit
                ? new RiskDecision(lookup.opaqueMobileRef(), "THIRD_PARTY_RISK",
                "BLOCK", "第三方风险名单命中，发送任务未创建且未计费")
                : new RiskDecision(lookup.opaqueMobileRef(), "NO_MATCH", "ALLOW", "未命中任何风险来源");
    }

    private void recordProviderLog(long tenantId, RiskProviderConfigRow provider, String mobileRef,
                                   boolean hit, boolean degraded, String requestKind, int itemCount) {
        jdbc.update("""
                INSERT INTO third_party_risk_check_logs(request_id,mobile_hash,check_level,threshold_score,is_hit,
                    response_time_ms,degraded,tenant_id,provider_name,request_kind,item_count,risk_score,
                    risk_result,fallback_policy,reason)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, "provider-" + UUID.randomUUID(), mobileRef, levelNumber(provider.checkLevel()),
                provider.thresholdScore(), hit, degraded ? provider.timeoutMs() : Math.min(50, provider.timeoutMs()),
                degraded, tenantId, provider.providerName(), requestKind, itemCount,
                hit ? provider.thresholdScore() : 0, hit ? "BLOCK" : "ALLOW",
                provider.fallbackPolicy(), degraded ? "provider failure fallback" : "provider response");
    }

    private long recordDecision(String requestId, long tenantId, String mobileRef, RiskDecision decision, String actor) {
        jdbc.update("""
                INSERT INTO risk_intercept_decisions(request_id,tenant_id,mobile_ref,source_category,risk_result,
                    trace_reason,task_created,charged,actor)
                VALUES (?,?,?,?,?,?,?,?,?)
                """, requestId, tenantId, mobileRef, decision.sourceCategory(), decision.riskResult(),
                decision.traceReason(), false, false, actor);
        Long id = jdbc.queryForObject("""
                SELECT id FROM risk_intercept_decisions WHERE request_id=? AND mobile_ref=?
                """, Long.class, requestId, mobileRef);
        return id == null ? 0 : id;
    }

    private BlacklistEntryRow entriesById(long id) {
        List<BlacklistEntryRow> rows = jdbc.query("""
                SELECT id, tenant_id, masked_mobile, mobile_hash, list_type, reason, source, status,
                       created_by, created_at, expires_at
                FROM blacklist_entries WHERE id=?
                """, (rs, row) -> new BlacklistEntryRow(rs.getLong("id"), rs.getObject("tenant_id", Long.class),
                rs.getString("masked_mobile"), rs.getString("mobile_hash"), rs.getString("list_type"),
                rs.getString("reason"), rs.getString("source"), rs.getString("status"),
                rs.getString("created_by"), rs.getTimestamp("created_at").toLocalDateTime(),
                nullableDateTime(rs.getTimestamp("expires_at"))), id);
        if (rows.size() != 1) {
            throw failure("BLACKLIST_NOT_FOUND", "黑白名单记录不存在");
        }
        return rows.getFirst();
    }

    private long appealCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM risk_intercept_appeals", Long.class);
        return count == null ? 0 : count;
    }

    private static String requestKind(int itemCount) {
        return itemCount > 1 ? "BATCH" : "SINGLE";
    }

    private static int levelNumber(String level) {
        return switch (level) {
            case "INTERMEDIATE" -> 2;
            case "ADVANCED" -> 3;
            default -> 1;
        };
    }

    private static String mask(String mobile) {
        return mobile.substring(0, 3) + "****" + mobile.substring(7);
    }

    private static LocalDateTime nullableDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String routingMobileRef(RoutingContext context) {
        try {
            return text(context.getOpaqueMobileQueryValue(), "RISK_ROUTING_MOBILE_REF_REQUIRED", 128);
        } catch (RuntimeException ignored) {
            return "protected-ref-unavailable";
        }
    }

    private static String maskUntrusted(String value) {
        if (value == null || value.length() < 7) {
            return "***";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private static String mobile(String raw) {
        String value = text(raw, "BLACKLIST_MOBILE_REQUIRED", 16);
        if (!MOBILE.matcher(value).matches()) {
            throw failure("BLACKLIST_MOBILE_INVALID", "手机号格式不合法");
        }
        return value;
    }

    private static Long optionalTenant(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        if (tenantId <= 0) {
            throw failure("BLACKLIST_TENANT_INVALID", "机构编号不合法");
        }
        return tenantId;
    }

    private static long requiredTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw failure("RISK_TENANT_REQUIRED", "机构编号不能为空");
        }
        return tenantId;
    }

    private static long requiredId(Long id, String code) {
        if (id == null || id <= 0) {
            throw failure(code, "编号不合法");
        }
        return id;
    }

    private static int boundedInt(Integer value, int min, int max, String code) {
        if (value == null || value < min || value > max) {
            throw failure(code, "数值范围不合法");
        }
        return value;
    }

    private static long longValue(String raw, String code) {
        try {
            long value = Long.parseLong(raw.trim());
            if (value <= 0) {
                throw failure(code, "编号不合法");
            }
            return value;
        } catch (NumberFormatException ex) {
            throw failure(code, "编号不合法");
        }
    }

    private static String enumValue(String raw, Set<String> allowed, String code) {
        if (raw == null) {
            throw failure(code, "枚举值不合法");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw failure(code, "枚举值不合法");
        }
        return value;
    }

    private static String text(String raw, String code, int max) {
        if (raw == null || raw.trim().isEmpty()) {
            throw failure(code, "必填字段不能为空");
        }
        String value = raw.trim();
        if (value.length() > max || value.contains("\n") || value.contains("\r") || value.contains("\t")) {
            throw failure(code, "字段格式不合法");
        }
        return value;
    }

    private static String actor(String actor) {
        return text(actor, "RISK_ACTOR_REQUIRED", 64);
    }

    private static BusinessException failure(String code, String message) {
        return new BusinessException(code, message);
    }

    @FunctionalInterface
    interface ProtectedListWriter {
        long create(Long tenantId, String mobile, String listType, String source, String reason);
    }

    @FunctionalInterface
    interface ProtectedListDisabler {
        void disable(long id);
    }

    @FunctionalInterface
    interface RiskLookupPort {
        RiskLookupResult lookup(long tenantId, String requestId, String normalizedMobile);
    }

    private record RiskDecision(String mobileRef, String sourceCategory, String riskResult, String traceReason) {
    }

    record RiskLookupResult(String opaqueMobileRef, String sourceCategory, String riskResult,
                            String traceReason) {
    }

    public record BlacklistEntryRow(long id, Long tenantId, String maskedMobile, String mobileRef,
                                    String listType, String reason, String source, String status,
                                    String createdBy, LocalDateTime createdAt, LocalDateTime expiresAt) {
    }

    public record BlacklistEntryCreateRequest(Long tenantId, String mobile, String listType,
                                              String source, String reason, LocalDateTime expiresAt) {
    }

    public record BlacklistImportRequest(Long tenantId, List<String> mobiles, String listType,
                                         String source, String reason, LocalDateTime expiresAt) {
    }

    public record BlacklistImportResponse(int success, int failed, List<String> errors) {
    }

    public record ExportRequestResponse(String requestId, int matchedRows, String status) {
    }

    public record RiskProviderConfigRequest(String providerName, String providerUrl, String credentialRef,
                                            String checkLevel, Integer thresholdScore, Integer timeoutMs,
                                            String fallbackPolicy, String status, Integer cacheTtlSeconds) {
    }

    public record RiskProviderConfigRow(long id, String providerName, String providerUrl, String credentialRef,
                                        String checkLevel, int thresholdScore, int timeoutMs, String fallbackPolicy,
                                        String status, int cacheTtlSeconds, String createdBy, LocalDateTime createdAt) {
    }

    public record RiskCheckRequest(Long tenantId, String requestId, List<String> mobileRefs,
                                   Boolean forceProviderFailure) {
    }

    public record RiskDecisionRow(long id, String requestId, long tenantId, String mobileRef,
                                  String sourceCategory, String riskResult, String traceReason,
                                  boolean taskCreated, boolean charged, LocalDateTime createdAt) {
    }

    public record RiskAnalyticsResponse(long total, long blocked, long allowed, long systemHits,
                                        long tenantHits, long providerHits, long degraded, long appeals) {
    }

    public record AppealRequest(Long decisionId, String reason) {
    }

    public record AppealResponse(long id, long decisionId, String originalResult, String appealResult) {
    }

    private static final class ProtectedRiskLookup implements RiskLookupPort {
        private final MessageTaskProtectionAdapter messageTaskProtectionAdapter;
        private final BlindIndexLookupService blindIndexLookupService;

        private ProtectedRiskLookup(MessageTaskProtectionAdapter messageTaskProtectionAdapter,
                                    BlindIndexLookupService blindIndexLookupService) {
            this.messageTaskProtectionAdapter = Objects.requireNonNull(messageTaskProtectionAdapter);
            this.blindIndexLookupService = Objects.requireNonNull(blindIndexLookupService);
        }

        @Override
        public RiskLookupResult lookup(long tenantId, String requestId, String normalizedMobile) {
            PreparedMessageRouting routing = messageTaskProtectionAdapter.prepareForRouting(
                    tenantId, messageId(requestId), normalizedMobile);
            BlindIndexLookupService.BlacklistLookupResult lookup = blindIndexLookupService.lookupBlacklist(
                    tenantId, routing.legacyLookupToken(), BlacklistEntry.Status.ACTIVE);
            String opaque = routing.queryIndexes().values().getLast().canonicalValue();
            if (lookup.tenantWhitelist()) {
                return new RiskLookupResult(opaque, "WHITELIST", "ALLOW",
                        "机构白名单命中，跳过黑名单和第三方风险");
            }
            if (lookup.blockReason() == BlindIndexLookupService.BlacklistLookupResult.BlockReason.SYSTEM_BLACKLIST) {
                return new RiskLookupResult(opaque, "SYSTEM_BLACKLIST", "BLOCK",
                        "系统级黑名单命中，发送任务未创建且未计费");
            }
            if (lookup.blockReason() == BlindIndexLookupService.BlacklistLookupResult.BlockReason.TENANT_BLACKLIST) {
                return new RiskLookupResult(opaque, "TENANT_BLACKLIST", "BLOCK",
                        "机构级黑名单命中，发送任务未创建且未计费");
            }
            return new RiskLookupResult(opaque, "NO_MATCH", "ALLOW", "未命中黑白名单");
        }

        private static String messageId(String requestId) {
            String suffix = UUID.nameUUIDFromBytes(requestId.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    .toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
            long numeric = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
            return "MSG_" + numeric + "_" + suffix;
        }
    }
}
