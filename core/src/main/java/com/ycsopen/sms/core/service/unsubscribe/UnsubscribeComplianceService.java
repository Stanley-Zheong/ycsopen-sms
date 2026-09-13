package com.ycsopen.sms.core.service.unsubscribe;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.BlacklistEntryProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService;
import com.ycsopen.sms.core.service.export.SecureAsyncExportService.ExportCreateCommand;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UnsubscribeComplianceService {
    private static final Pattern MOBILE = Pattern.compile("1[3-9][0-9]{9}");

    private final JdbcTemplate jdbc;
    private final TenantBlacklistWriter blacklistWriter;
    private final WebhookDeliveryTransportService webhookTransport;
    private final SecureAsyncExportService exports;

    @Autowired
    public UnsubscribeComplianceService(JdbcTemplate jdbc,
                                        BlacklistEntryProtectionAdapter adapter,
                                        WebhookDeliveryTransportService webhookTransport,
                                        SecureAsyncExportService exports) {
        this(jdbc, (tenantId, mobile, reason) -> adapter.create(tenantId, mobile,
                BlacklistEntry.ListType.BLACK, BlacklistEntry.Source.UNSUBSCRIBE_AUTO, reason), webhookTransport, exports);
    }

    UnsubscribeComplianceService(JdbcTemplate jdbc,
                                 TenantBlacklistWriter blacklistWriter,
                                 WebhookDeliveryTransportService webhookTransport) {
        this(jdbc, blacklistWriter, webhookTransport, null);
    }

    UnsubscribeComplianceService(JdbcTemplate jdbc,
                                 TenantBlacklistWriter blacklistWriter,
                                 WebhookDeliveryTransportService webhookTransport,
                                 SecureAsyncExportService exports) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.blacklistWriter = Objects.requireNonNull(blacklistWriter);
        this.webhookTransport = Objects.requireNonNull(webhookTransport);
        this.exports = exports;
    }

    @Transactional(readOnly = true)
    public List<KeywordRow> keywords(Long tenantId) {
        if (tenantId == null) {
            return jdbc.query("""
                    SELECT id, keyword, keyword_normalized, scope, tenant_id, status, created_by, updated_at
                      FROM unsubscribe_keywords
                     ORDER BY scope, keyword_normalized
                     LIMIT 200
                    """, (rs, row) -> keyword(rs));
        }
        return jdbc.query("""
                SELECT id, keyword, keyword_normalized, scope, tenant_id, status, created_by, updated_at
                  FROM unsubscribe_keywords
                 WHERE scope='GLOBAL' OR tenant_id=?
                 ORDER BY scope, keyword_normalized
                 LIMIT 200
                """, (rs, row) -> keyword(rs), tenantId);
    }

    @Transactional
    public KeywordRow saveKeyword(Long tenantId, KeywordCommand command, String actor) {
        KeywordCommand checked = command.checked(tenantId);
        try {
            jdbc.update("""
                    INSERT INTO unsubscribe_keywords(keyword, keyword_normalized, scope, tenant_id, scope_key, status, created_by)
                    VALUES (?,?,?,?,?,?,?)
                    """, checked.keyword(), normalizeKeyword(checked.keyword()), checked.scope(), checked.tenantId(),
                    scopeKey(checked.scope(), checked.tenantId()), checked.status(), actor(actor));
        } catch (DuplicateKeyException duplicate) {
            jdbc.update("""
                    UPDATE unsubscribe_keywords
                       SET keyword=?, status=?, created_by=?, updated_at=CURRENT_TIMESTAMP
                     WHERE scope_key=? AND keyword_normalized=?
                    """, checked.keyword(), checked.status(), actor(actor),
                    scopeKey(checked.scope(), checked.tenantId()), normalizeKeyword(checked.keyword()));
        }
        return jdbc.queryForObject("""
                SELECT id, keyword, keyword_normalized, scope, tenant_id, status, created_by, updated_at
                  FROM unsubscribe_keywords
                 WHERE scope_key=? AND keyword_normalized=?
                """, (rs, row) -> keyword(rs), scopeKey(checked.scope(), checked.tenantId()),
                normalizeKeyword(checked.keyword()));
    }

    @Transactional
    public HandleResult handleMatchedUplink(HandleUplinkCommand command, String actor) {
        HandleUplinkCommand checked = command.checked();
        MatchResult match = matchIntent(checked.tenantId(), checked.content());
        if (!match.matched()) {
            return new HandleResult(false, null, null, "NO_MATCH", "未命中退订关键词");
        }
        List<UnsubscribeRecordRow> existing = jdbc.query("""
                SELECT * FROM unsubscribe_records WHERE uplink_record_id=?
                """, (rs, row) -> record(rs), checked.uplinkRecordId());
        if (!existing.isEmpty()) {
            return new HandleResult(true, existing.get(0).id(), null, "IDEMPOTENT", "上行退订证据已存在");
        }

        long blacklistId = blacklistWriter.create(checked.tenantId(), checked.mobile(), "退订自动加入：" + match.keyword());
        long unsubscribeId = insertEvidence(checked, match.keyword());
        updateNotificationState(unsubscribeId, checked, match.keyword());
        return new HandleResult(true, unsubscribeId, blacklistId, "TENANT_BLACKLISTED", "已加入租户黑名单并记录退订证据");
    }

    @Transactional(readOnly = true)
    public MatchResult matchIntent(long tenantId, String content) {
        String normalized = normalizeKeyword(content);
        List<String> keywords = jdbc.queryForList("""
                SELECT keyword_normalized FROM unsubscribe_keywords
                 WHERE status='ACTIVE' AND (scope='GLOBAL' OR tenant_id=?)
                """, String.class, tenantId);
        for (String keyword : keywords) {
            if (matchesKeyword(normalized, keyword)) {
                return new MatchResult(true, keyword);
            }
        }
        return new MatchResult(false, null);
    }

    @Transactional(readOnly = true)
    public List<UnsubscribeRecordRow> adminSearch(SearchFilter filter) {
        return search(filter, null);
    }

    @Transactional(readOnly = true)
    public List<UnsubscribeRecordRow> tenantSearch(long tenantId, SearchFilter filter) {
        return search(filter, tenantId);
    }

    @Transactional
    public ExportRequestResponse requestTenantExport(long tenantId, SearchFilter filter, String actor) {
        long rows = countSearch(filter, tenantId);
        if (exports != null) {
            var job = exports.create(new ExportCreateCommand("UNSUB-" + UUID.randomUUID(), tenantId,
                    "UNSUBSCRIBE_EVIDENCE", "UNSUBSCRIBE_COMPLIANCE", "退订证据导出", actor(actor), "CSV",
                    Map.of("tenantId", tenantId), List.of("unsubscribed_at DESC", "id DESC"),
                    "secure-async-export:create", List.of("mobile"),
                    search(filter, tenantId).stream().map(row -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", row.id());
                        item.put("tenant_id", row.tenantId());
                        item.put("masked_mobile", row.maskedMobile());
                        item.put("trigger_keyword", row.triggerKeyword());
                        item.put("handling_state", row.handlingState());
                        item.put("notification_state", row.notificationState());
                        item.put("unsubscribed_at", row.unsubscribedAt());
                        return item;
                    }).toList()));
            return new ExportRequestResponse(job.id(), job.status(), job.recordCount(), job.format());
        }
        jdbc.update("""
                INSERT INTO export_tasks(export_type, created_by, file_format, status, progress_pct, record_count)
                VALUES ('UNSUBSCRIBE_EVIDENCE', ?, 'CSV', 'PENDING', 0, ?)
                """, actor(actor), rows);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM export_tasks WHERE export_type='UNSUBSCRIBE_EVIDENCE' AND created_by=?",
                Long.class, actor(actor));
        return new ExportRequestResponse(id == null ? 0 : id, "PENDING", rows, "CSV");
    }

    @Transactional(readOnly = true)
    public List<StatisticsRow> statistics(StatisticsFilter filter) {
        StatisticsFilter checked = filter == null
                ? new StatisticsFilter(null, null, null, null, null)
                : filter;
        LocalDateTime start = checked.startTime() == null ? LocalDateTime.now().minusDays(30) : checked.startTime();
        LocalDateTime end = checked.endTime() == null ? LocalDateTime.now() : checked.endTime();
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT ur.tenant_id, ur.signature_id, ur.product_code, COUNT(*) AS unsubscribe_count,
                       COALESCE(den.final_sent_count, 0) AS final_sent_count
                  FROM unsubscribe_records ur
                  LEFT JOIN (
                    SELECT tenant_id, signature_id, COUNT(*) AS final_sent_count
                      FROM message_tasks
                     WHERE send_status IN ('SENT','DELIVERED') AND created_at>=? AND created_at<=?
                     GROUP BY tenant_id, signature_id
                  ) den ON den.tenant_id=ur.tenant_id
                       AND (den.signature_id=ur.signature_id OR (den.signature_id IS NULL AND ur.signature_id IS NULL))
                 WHERE ur.unsubscribed_at>=? AND ur.unsubscribed_at<=?
                """);
        params.add(start);
        params.add(end);
        params.add(start);
        params.add(end);
        appendStatsFilters(sql, params, checked, "ur.");
        sql.append("""
                 GROUP BY ur.tenant_id, ur.signature_id, ur.product_code, den.final_sent_count
                 ORDER BY unsubscribe_count DESC, ur.tenant_id
                 LIMIT 200
                """);
        return jdbc.query(sql.toString(), (rs, row) -> statistics(rs), params.toArray());
    }

    @Transactional
    public List<AlertEventRow> evaluateAlerts(StatisticsFilter filter, double thresholdRate) {
        if (thresholdRate <= 0 || thresholdRate > 1) {
            throw new BusinessException("UNSUBSCRIBE_ALERT_THRESHOLD_INVALID", "退订率阈值不合法");
        }
        List<StatisticsRow> rows = statistics(filter);
        LocalDateTime start = filter == null || filter.startTime() == null ? LocalDateTime.now().minusDays(30) : filter.startTime();
        LocalDateTime end = filter == null || filter.endTime() == null ? LocalDateTime.now() : filter.endTime();
        for (StatisticsRow row : rows) {
            if (row.finalSentCount() > 0 && row.rate() > thresholdRate) {
                jdbc.update("""
                        INSERT INTO unsubscribe_alert_events(tenant_id, signature_id, product_code, period_start, period_end,
                            unsubscribe_count, final_sent_count, rate, threshold_rate, formula, freshness_at)
                        VALUES (?,?,?,?,?,?,?,?,?, 'unsubscribe_count/final_sent_count', CURRENT_TIMESTAMP)
                        """, row.tenantId(), row.signatureId(), row.productCode(), start, end, row.unsubscribeCount(),
                        row.finalSentCount(), row.rate(), thresholdRate);
            }
        }
        return alertEvents(filter);
    }

    @Transactional(readOnly = true)
    public List<AlertEventRow> alertEvents(StatisticsFilter filter) {
        StatisticsFilter checked = filter == null
                ? new StatisticsFilter(null, null, null, null, null)
                : filter;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT id, tenant_id, signature_id, product_code, period_start, period_end, unsubscribe_count,
                       final_sent_count, rate, threshold_rate, formula, freshness_at, source_event
                  FROM unsubscribe_alert_events
                 WHERE 1=1
                """);
        appendStatsFilters(sql, params, checked, "");
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT 200");
        return jdbc.query(sql.toString(), (rs, row) -> alert(rs), params.toArray());
    }

    private long insertEvidence(HandleUplinkCommand command, String keyword) {
        jdbc.update("""
                INSERT INTO unsubscribe_records(mobile_encrypted, mobile_hash, trigger_keyword, tenant_id, signature_id,
                    result, confirmed_reply_sent, notified_tenant, uplink_record_id, masked_mobile, method,
                    product_code, handling_state, notification_state, confirmation_state, created_at, updated_at)
                VALUES (?,?,?,?,?, 'TENANT_BLACKLISTED', 0, 0, ?, ?, 'UPLINK', ?, 'TENANT_BLACKLISTED',
                    'NOT_CONFIGURED', 'DISABLED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, protectedDigest(command.mobile()), phoneHash(command.mobile()), keyword, command.tenantId(),
                command.signatureId(), command.uplinkRecordId(), mask(command.mobile()), text(command.productCode()));
        return jdbc.queryForObject("SELECT id FROM unsubscribe_records WHERE uplink_record_id=?", Long.class,
                command.uplinkRecordId());
    }

    private void updateNotificationState(long unsubscribeId, HandleUplinkCommand command, String keyword) {
        try {
            var result = webhookTransport.enqueueUnsubscribeEvent(command.tenantId(), String.valueOf(unsubscribeId),
                    command.uplinkRecordId(), command.messageId(), mask(command.mobile()), keyword, "TENANT_BLACKLISTED");
            jdbc.update("""
                    UPDATE unsubscribe_records
                       SET notification_state='PENDING', notification_event_id=?, notified_tenant=1,
                           updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, result.eventId(), unsubscribeId);
        } catch (BusinessException ex) {
            String state = "WEBHOOK_DESTINATION_NOT_CONFIGURED".equals(ex.getErrorCode()) ? "NOT_CONFIGURED" : "NOTIFY_FAILED";
            jdbc.update("""
                    UPDATE unsubscribe_records
                       SET notification_state=?, updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, state, unsubscribeId);
        } catch (RuntimeException ex) {
            jdbc.update("""
                    UPDATE unsubscribe_records
                       SET notification_state='NOTIFY_FAILED', updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, unsubscribeId);
        }
    }

    private List<UnsubscribeRecordRow> search(SearchFilter filter, Long requiredTenantId) {
        SearchFilter checked = filter == null
                ? new SearchFilter(null, null, null, null, null, null, null, null, null)
                : filter;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM unsubscribe_records WHERE 1=1");
        if (requiredTenantId != null) {
            sql.append(" AND tenant_id=?");
            params.add(requiredTenantId);
        } else if (checked.tenantId() != null) {
            sql.append(" AND tenant_id=?");
            params.add(checked.tenantId());
        }
        if (text(checked.mobile()) != null) {
            sql.append(" AND mobile_hash=?");
            params.add(phoneHash(checked.mobile()));
        }
        if (text(checked.keyword()) != null) {
            sql.append(" AND trigger_keyword=?");
            params.add(normalizeKeyword(checked.keyword()));
        }
        if (text(checked.outcome()) != null) {
            sql.append(" AND result=?");
            params.add(checked.outcome().trim().toUpperCase(Locale.ROOT));
        }
        if (checked.signatureId() != null) {
            sql.append(" AND signature_id=?");
            params.add(checked.signatureId());
        }
        if (text(checked.productCode()) != null) {
            sql.append(" AND product_code=?");
            params.add(checked.productCode().trim());
        }
        if (text(checked.notificationState()) != null) {
            sql.append(" AND notification_state=?");
            params.add(checked.notificationState().trim().toUpperCase(Locale.ROOT));
        }
        if (checked.startTime() != null) {
            sql.append(" AND unsubscribed_at>=?");
            params.add(checked.startTime());
        }
        if (checked.endTime() != null) {
            sql.append(" AND unsubscribed_at<=?");
            params.add(checked.endTime());
        }
        sql.append(" ORDER BY unsubscribed_at DESC, id DESC LIMIT 200");
        return jdbc.query(sql.toString(), (rs, row) -> record(rs), params.toArray());
    }

    private long countSearch(SearchFilter filter, Long requiredTenantId) {
        return search(filter, requiredTenantId).size();
    }

    private static void appendStatsFilters(StringBuilder sql, List<Object> params, StatisticsFilter checked,
                                           String prefix) {
        if (checked.tenantId() != null) {
            sql.append(" AND ").append(prefix).append("tenant_id=?");
            params.add(checked.tenantId());
        }
        if (checked.signatureId() != null) {
            sql.append(" AND ").append(prefix).append("signature_id=?");
            params.add(checked.signatureId());
        }
        if (text(checked.productCode()) != null) {
            sql.append(" AND ").append(prefix).append("product_code=?");
            params.add(checked.productCode().trim());
        }
    }

    private static boolean matchesKeyword(String content, String keyword) {
        return content.equals(keyword) || List.of(content.split("[\\s,;，；。.!！?？]+")).contains(keyword);
    }

    private static KeywordRow keyword(ResultSet rs) throws SQLException {
        return new KeywordRow(rs.getLong("id"), rs.getString("keyword"), rs.getString("keyword_normalized"),
                rs.getString("scope"), nullableLong(rs, "tenant_id"), rs.getString("status"),
                rs.getString("created_by"), timestamp(rs.getTimestamp("updated_at")));
    }

    private static UnsubscribeRecordRow record(ResultSet rs) throws SQLException {
        return new UnsubscribeRecordRow(rs.getLong("id"), rs.getLong("tenant_id"), nullableLong(rs, "signature_id"),
                rs.getString("product_code"), rs.getString("masked_mobile"), rs.getString("trigger_keyword"),
                rs.getString("method"), rs.getString("result"), rs.getString("handling_state"),
                rs.getString("notification_state"), nullableLong(rs, "notification_event_id"),
                rs.getString("confirmation_state"), nullableLong(rs, "reply_event_id"),
                nullableLong(rs, "uplink_record_id"), timestamp(rs.getTimestamp("unsubscribed_at")),
                timestamp(rs.getTimestamp("updated_at")));
    }

    private static StatisticsRow statistics(ResultSet rs) throws SQLException {
        int unsubscribes = rs.getInt("unsubscribe_count");
        int denominator = rs.getInt("final_sent_count");
        double rate = denominator == 0 ? 0 : (double) unsubscribes / denominator;
        return new StatisticsRow(rs.getLong("tenant_id"), nullableLong(rs, "signature_id"),
                rs.getString("product_code"), unsubscribes, denominator, rate);
    }

    private static AlertEventRow alert(ResultSet rs) throws SQLException {
        return new AlertEventRow(rs.getLong("id"), rs.getLong("tenant_id"), nullableLong(rs, "signature_id"),
                rs.getString("product_code"), timestamp(rs.getTimestamp("period_start")),
                timestamp(rs.getTimestamp("period_end")), rs.getInt("unsubscribe_count"),
                rs.getInt("final_sent_count"), rs.getDouble("rate"), rs.getDouble("threshold_rate"),
                rs.getString("formula"), timestamp(rs.getTimestamp("freshness_at")), rs.getString("source_event"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String normalizeKeyword(String value) {
        String text = text(value);
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC).trim().toUpperCase(Locale.ROOT);
    }

    private static String phoneHash(String phone) {
        return sha256Hex("tenant-unsubscribe:" + phone.trim());
    }

    private static byte[] protectedDigest(String phone) {
        return ("UNSUBSCRIBE:v1:" + phoneHash(phone)).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("UNSUBSCRIBE_HASH_UNAVAILABLE", ex);
        }
    }

    private static String mask(String phone) {
        String clean = phone.trim();
        return clean.substring(0, 3) + "****" + clean.substring(clean.length() - 4);
    }

    private static String scopeKey(String scope, Long tenantId) {
        return "GLOBAL".equals(scope) ? "GLOBAL" : "TENANT:" + tenantId;
    }

    private static String actor(String actor) {
        return text(actor) == null ? "system" : actor.trim();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @FunctionalInterface
    interface TenantBlacklistWriter {
        long create(long tenantId, String mobile, String reason);
    }

    public record KeywordCommand(String keyword, String scope, Long tenantId, String status) {
        KeywordCommand checked(Long requiredTenantId) {
            String keywordText = text(keyword);
            if (keywordText == null || keywordText.length() > 32) {
                throw new BusinessException("UNSUBSCRIBE_KEYWORD_INVALID", "退订关键词不合法");
            }
            String effectiveScope = text(scope) == null ? "TENANT" : scope.trim().toUpperCase(Locale.ROOT);
            if (!List.of("GLOBAL", "TENANT").contains(effectiveScope)) {
                throw new BusinessException("UNSUBSCRIBE_KEYWORD_SCOPE_INVALID", "退订关键词范围不合法");
            }
            Long effectiveTenant = "TENANT".equals(effectiveScope) ? requiredTenantId : tenantId;
            if ("TENANT".equals(effectiveScope) && (effectiveTenant == null || effectiveTenant < 1)) {
                throw new BusinessException("UNSUBSCRIBE_KEYWORD_TENANT_REQUIRED", "租户关键词必须绑定租户");
            }
            if (requiredTenantId != null && "GLOBAL".equals(effectiveScope)) {
                throw new BusinessException("UNSUBSCRIBE_KEYWORD_SCOPE_DENIED", "租户不能维护全局关键词");
            }
            String effectiveStatus = text(status) == null ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
            if (!List.of("ACTIVE", "DISABLED").contains(effectiveStatus)) {
                throw new BusinessException("UNSUBSCRIBE_KEYWORD_STATUS_INVALID", "退订关键词状态不合法");
            }
            return new KeywordCommand(keywordText, effectiveScope, effectiveTenant, effectiveStatus);
        }
    }

    public record HandleUplinkCommand(long tenantId, long uplinkRecordId, String mobile, String content,
                                      String messageId, Long signatureId, String productCode) {
        HandleUplinkCommand checked() {
            if (tenantId < 1 || uplinkRecordId < 1 || text(content) == null || text(mobile) == null
                    || !MOBILE.matcher(mobile.trim()).matches()) {
                throw new BusinessException("UNSUBSCRIBE_UPLINK_INVALID", "退订上行字段不完整或不合法");
            }
            return new HandleUplinkCommand(tenantId, uplinkRecordId, mobile.trim(), content.trim(), text(messageId),
                    signatureId, text(productCode));
        }
    }

    public record SearchFilter(Long tenantId, String mobile, String keyword, String outcome, Long signatureId,
                               String productCode, String notificationState,
                               LocalDateTime startTime, LocalDateTime endTime) { }

    public record StatisticsFilter(Long tenantId, Long signatureId, String productCode,
                                   LocalDateTime startTime, LocalDateTime endTime) { }

    public record KeywordRow(long id, String keyword, String keywordNormalized, String scope, Long tenantId,
                             String status, String createdBy, LocalDateTime updatedAt) { }

    public record MatchResult(boolean matched, String keyword) { }

    public record HandleResult(boolean matched, Long unsubscribeId, Long blacklistEntryId,
                               String state, String message) { }

    public record UnsubscribeRecordRow(long id, long tenantId, Long signatureId, String productCode,
                                       String maskedMobile, String triggerKeyword, String method, String result,
                                       String handlingState, String notificationState, Long notificationEventId,
                                       String confirmationState, Long replyEventId, Long uplinkRecordId,
                                       LocalDateTime unsubscribedAt, LocalDateTime updatedAt) { }

    public record ExportRequestResponse(long taskId, String status, long recordCount, String fileFormat) { }

    public record StatisticsRow(long tenantId, Long signatureId, String productCode, int unsubscribeCount,
                                int finalSentCount, double rate) { }

    public record AlertEventRow(long id, long tenantId, Long signatureId, String productCode,
                                LocalDateTime periodStart, LocalDateTime periodEnd, int unsubscribeCount,
                                int finalSentCount, double rate, double thresholdRate, String formula,
                                LocalDateTime freshnessAt, String sourceEvent) { }
}
