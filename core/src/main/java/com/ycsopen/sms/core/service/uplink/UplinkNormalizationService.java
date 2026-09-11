package com.ycsopen.sms.core.service.uplink;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.cmpp.CmppClientSession;
import com.ycsopen.sms.core.service.unsubscribe.UnsubscribeComplianceService;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService;
import com.ycsopen.sms.core.service.webhook.WebhookDeliveryTransportService.DeliveryResult;
import org.springframework.beans.factory.ObjectProvider;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class UplinkNormalizationService {
    private final JdbcTemplate jdbc;
    private final WebhookDeliveryTransportService webhookTransport;
    private final ObjectProvider<UnsubscribeComplianceService> unsubscribeService;

    @Autowired
    public UplinkNormalizationService(JdbcTemplate jdbc, WebhookDeliveryTransportService webhookTransport,
                                      ObjectProvider<UnsubscribeComplianceService> unsubscribeService) {
        this.jdbc = jdbc;
        this.webhookTransport = webhookTransport;
        this.unsubscribeService = unsubscribeService;
    }

    public UplinkNormalizationService(JdbcTemplate jdbc, WebhookDeliveryTransportService webhookTransport) {
        this.jdbc = jdbc;
        this.webhookTransport = webhookTransport;
        this.unsubscribeService = null;
    }

    @Transactional
    public UplinkRecord normalizeHttpUplink(long tenantId, String sourceConnector, String sourceEventId,
                                            String messageId, String phoneNumber, String content,
                                            String contentKeyword, String carrier, String province, String city,
                                            String destination, Long channelId, Long signatureId,
                                            String productCode, boolean pushRequested,
                                            LocalDateTime receiveTime) {
        return normalize(new NormalizeCommand(tenantId, "HTTP", sourceConnector, sourceEventId, messageId,
                phoneNumber, content, contentKeyword, carrier, province, city, destination, channelId,
                signatureId, productCode, pushRequested, receiveTime));
    }

    @Transactional
    public UplinkRecord normalizeCmppUplink(long tenantId, String sourceConnector, String sourceEventId,
                                            String messageId, String carrier, String province, String city,
                                            Long channelId, Long signatureId, String productCode,
                                            CmppClientSession.NormalizedEvent event) {
        if (event == null || !"UPLINK".equals(event.kind())) {
            throw new BusinessException("UPLINK_CMPP_EVENT_INVALID", "CMPP 上行事件不合法");
        }
        return normalize(new NormalizeCommand(tenantId, "CMPP", sourceConnector, sourceEventId, messageId,
                event.sourceAddress(), event.providerStatusOrContent(), null, carrier, province, city,
                null, channelId, signatureId, productCode, true, LocalDateTime.now()));
    }

    @Transactional
    public UplinkRecord normalize(NormalizeCommand command) {
        NormalizeCommand checked = command.checked();
        String phoneHash = phoneHash(checked.phoneNumber());
        String phoneMasked = maskPhone(checked.phoneNumber());
        String keyword = contentKeyword(checked.contentKeyword(), checked.content());
        try {
            jdbc.update("""
                    INSERT INTO uplink_records(tenant_id, source_protocol, source_connector, source_event_id,
                        message_id, phone_masked, phone_hash, content, content_keyword, state, carrier, province,
                        city, destination, channel_id, signature_id, product_code, push_state, receive_time)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, checked.tenantId(), checked.sourceProtocol(), checked.sourceConnector(), checked.sourceEventId(),
                    text(checked.messageId()), phoneMasked, phoneHash, checked.content(), keyword, "NORMALIZED",
                    text(checked.carrier()), text(checked.province()), text(checked.city()), text(checked.destination()),
                    checked.channelId(), checked.signatureId(), text(checked.productCode()), "NOT_REQUESTED",
                    checked.receiveTime() == null ? LocalDateTime.now() : checked.receiveTime());
        } catch (DuplicateKeyException duplicate) {
            return bySource(checked.tenantId(), checked.sourceProtocol(), checked.sourceConnector(), checked.sourceEventId());
        }
        UplinkRecord record = bySource(checked.tenantId(), checked.sourceProtocol(), checked.sourceConnector(), checked.sourceEventId());
        handleUnsubscribeIfMatched(record, checked.phoneNumber());
        if (checked.pushRequested()) {
            enqueuePush(record);
            return detail(record.id(), record.tenantId());
        }
        return record;
    }

    @Transactional(readOnly = true)
    public List<UplinkRecord> adminSearch(SearchFilter filter) {
        return search(filter, null);
    }

    @Transactional(readOnly = true)
    public List<UplinkRecord> tenantSearch(long tenantId, SearchFilter filter) {
        return search(filter == null ? new SearchFilter(null, null, null, null, null, null, null)
                : filter, tenantId);
    }

    @Transactional(readOnly = true)
    public UplinkRecord detail(long id, Long requiredTenantId) {
        List<UplinkRecord> rows;
        if (requiredTenantId == null) {
            rows = jdbc.query("""
                    SELECT * FROM uplink_records WHERE id=?
                    """, (rs, row) -> record(rs), id);
        } else {
            rows = jdbc.query("""
                    SELECT * FROM uplink_records WHERE id=? AND tenant_id=?
                    """, (rs, row) -> record(rs), id, requiredTenantId);
        }
        if (rows.isEmpty()) {
            throw new BusinessException("UPLINK_RECORD_NOT_FOUND", "上行记录不存在或无权访问");
        }
        return rows.get(0);
    }

    @Transactional
    public DeliveryResult replayUplink(long id, String actor, String reason) {
        UplinkRecord record = detail(id, null);
        if (record.pushEventId() == null) {
            enqueuePush(record);
            record = detail(id, null);
        }
        DeliveryResult result = webhookTransport.replay(record.pushEventId(), actor, reason);
        syncPushState(record.pushEventId(), result.state());
        return result;
    }

    @Transactional
    public DeliveryResult replayPushEvent(long eventId, String actor, String reason) {
        DeliveryResult result = webhookTransport.replay(eventId, actor, reason);
        syncPushState(eventId, result.state());
        return result;
    }

    @Transactional
    public AutoReplyDecision planAutoReply(long tenantId, String phoneNumber, String content, Long uplinkRecordId,
                                           LocalDateTime receiveTime) {
        AutoReplyConfig config = autoReplyConfig(tenantId);
        if (!config.enabled() || text(config.keyword()) == null || text(content) == null
                || !content.contains(config.keyword())) {
            return new AutoReplyDecision(tenantId, "SKIPPED", config.keyword(), null, null, "未命中启用的自动回复规则");
        }
        String hash = phoneHash(phoneNumber);
        LocalDateTime at = receiveTime == null ? LocalDateTime.now() : receiveTime;
        Integer recent = jdbc.queryForObject("""
                SELECT COUNT(*) FROM tenant_uplink_auto_reply_attempts
                 WHERE tenant_id=? AND phone_hash=? AND decision='SHOULD_REPLY' AND created_at>=?
                """, Integer.class, tenantId, hash, at.minusMinutes(config.loopGuardMinutes()));
        if (recent != null && recent > 0) {
            return recordAutoReplyDecision(tenantId, hash, uplinkRecordId, config, "SUPPRESSED_LOOP_GUARD",
                    "防循环窗口内已回复");
        }
        return recordAutoReplyDecision(tenantId, hash, uplinkRecordId, config, "SHOULD_REPLY", "允许自动回复");
    }

    @Transactional
    public DeliveryResult pausePushEvent(long eventId, String actor, String reason) {
        DeliveryResult result = webhookTransport.pause(eventId, actor, reason);
        syncPushState(eventId, result.state());
        return result;
    }

    @Transactional
    public DeliveryResult resumePushEvent(long eventId, String actor, String reason) {
        DeliveryResult result = webhookTransport.resume(eventId, actor, reason);
        syncPushState(eventId, result.state());
        return result;
    }

    @Transactional(readOnly = true)
    public List<PushMonitorRow> pushMonitor(PushMonitorFilter filter) {
        PushMonitorFilter checked = filter == null ? new PushMonitorFilter(null, null, null) : filter;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.tenant_id, e.source_id, e.logical_id, e.destination_url, e.state,
                       e.attempt_count, e.max_attempts, e.next_attempt_at, e.created_at, e.updated_at,
                       COUNT(a.id) AS attempt_rows
                  FROM webhook_delivery_events e
                  LEFT JOIN webhook_delivery_attempts a ON a.event_id=e.id
                 WHERE e.event_type='UPLINK'
                """);
        if (checked.tenantId() != null) {
            sql.append(" AND e.tenant_id=?");
            params.add(checked.tenantId());
        }
        if (text(checked.state()) != null) {
            sql.append(" AND e.state=?");
            params.add(checked.state().trim().toUpperCase(Locale.ROOT));
        }
        if (text(checked.destination()) != null) {
            sql.append(" AND e.destination_url LIKE ?");
            params.add("%" + checked.destination().trim() + "%");
        }
        sql.append("""
                 GROUP BY e.id, e.tenant_id, e.source_id, e.logical_id, e.destination_url, e.state,
                          e.attempt_count, e.max_attempts, e.next_attempt_at, e.created_at, e.updated_at
                 ORDER BY e.updated_at DESC, e.id DESC
                 LIMIT 200
                """);
        return jdbc.query(sql.toString(), (rs, row) -> pushRow(rs), params.toArray());
    }

    @Transactional(readOnly = true)
    public AutoReplyConfig autoReplyConfig(long tenantId) {
        List<AutoReplyConfig> rows = jdbc.query("""
                SELECT tenant_id, enabled, keyword, template_id, response_content, loop_guard_minutes,
                       audit_reason, updated_by, updated_at
                  FROM tenant_uplink_auto_reply_configs
                 WHERE tenant_id=?
                """, (rs, row) -> autoReply(rs), tenantId);
        return rows.isEmpty() ? new AutoReplyConfig(tenantId, false, null, null, null, 30,
                null, "system", null) : rows.get(0);
    }

    @Transactional
    public AutoReplyConfig saveAutoReplyConfig(long tenantId, AutoReplyCommand command, String actor) {
        AutoReplyCommand checked = command.checked();
        int updated = jdbc.update("""
                UPDATE tenant_uplink_auto_reply_configs
                   SET enabled=?, keyword=?, template_id=?, response_content=?, loop_guard_minutes=?,
                       audit_reason=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                 WHERE tenant_id=?
                """, checked.enabled(), text(checked.keyword()), text(checked.templateId()),
                text(checked.responseContent()), checked.loopGuardMinutes(), checked.auditReason(), actor, tenantId);
        if (updated == 0) {
            try {
                jdbc.update("""
                        INSERT INTO tenant_uplink_auto_reply_configs(tenant_id, enabled, keyword, template_id,
                            response_content, loop_guard_minutes, audit_reason, updated_by)
                        VALUES (?,?,?,?,?,?,?,?)
                        """, tenantId, checked.enabled(), text(checked.keyword()), text(checked.templateId()),
                        text(checked.responseContent()), checked.loopGuardMinutes(), checked.auditReason(), actor);
            } catch (DuplicateKeyException duplicate) {
                jdbc.update("""
                        UPDATE tenant_uplink_auto_reply_configs
                           SET enabled=?, keyword=?, template_id=?, response_content=?, loop_guard_minutes=?,
                               audit_reason=?, updated_by=?, updated_at=CURRENT_TIMESTAMP
                         WHERE tenant_id=?
                        """, checked.enabled(), text(checked.keyword()), text(checked.templateId()),
                        text(checked.responseContent()), checked.loopGuardMinutes(), checked.auditReason(), actor, tenantId);
            }
        }
        return autoReplyConfig(tenantId);
    }

    private void enqueuePush(UplinkRecord record) {
        try {
            var result = webhookTransport.enqueueUplinkEvent(record.tenantId(), String.valueOf(record.id()),
                    record.messageId(), record.phoneMasked(), record.content());
            jdbc.update("""
                    UPDATE uplink_records
                       SET push_state='PENDING', push_event_id=?, destination=?, updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, result.eventId(), result.destinationUrl(), record.id());
        } catch (BusinessException ex) {
            String state = "WEBHOOK_DESTINATION_NOT_CONFIGURED".equals(ex.getErrorCode()) ? "NOT_CONFIGURED" : "PUSH_FAILED";
            jdbc.update("""
                    UPDATE uplink_records
                       SET push_state=?, updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, state, record.id());
        } catch (RuntimeException ex) {
            jdbc.update("""
                    UPDATE uplink_records
                       SET push_state='PUSH_FAILED', updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, record.id());
        }
    }

    private void handleUnsubscribeIfMatched(UplinkRecord record, String phoneNumber) {
        if (unsubscribeService == null) {
            return;
        }
        unsubscribeService.ifAvailable(service -> service.handleMatchedUplink(
                new UnsubscribeComplianceService.HandleUplinkCommand(record.tenantId(), record.id(), phoneNumber,
                        record.content(), record.messageId(), record.signatureId(), record.productCode()),
                "uplink-normalization"));
    }

    private AutoReplyDecision recordAutoReplyDecision(long tenantId, String phoneHash, Long uplinkRecordId,
                                                      AutoReplyConfig config, String decision, String reason) {
        jdbc.update("""
                INSERT INTO tenant_uplink_auto_reply_attempts(tenant_id, phone_hash, uplink_record_id, keyword,
                    response_content, template_id, decision, created_at)
                VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
                """, tenantId, phoneHash, uplinkRecordId, config.keyword(), config.responseContent(),
                config.templateId(), decision);
        return new AutoReplyDecision(tenantId, decision, config.keyword(), config.templateId(),
                config.responseContent(), reason);
    }

    private void syncPushState(long eventId, String state) {
        jdbc.update("""
                UPDATE uplink_records
                   SET push_state=?, updated_at=CURRENT_TIMESTAMP
                 WHERE push_event_id=?
                """, state, eventId);
    }

    private UplinkRecord bySource(long tenantId, String protocol, String sourceConnector, String sourceEventId) {
        return jdbc.queryForObject("""
                SELECT * FROM uplink_records
                 WHERE tenant_id=? AND source_protocol=? AND source_connector=? AND source_event_id=?
                """, (rs, row) -> record(rs), tenantId, protocol, sourceConnector, sourceEventId);
    }

    private List<UplinkRecord> search(SearchFilter filter, Long requiredTenantId) {
        SearchFilter checked = filter == null ? new SearchFilter(null, null, null, null, null, null, null) : filter;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM uplink_records WHERE 1=1");
        if (requiredTenantId != null) {
            sql.append(" AND tenant_id=?");
            params.add(requiredTenantId);
        } else if (checked.tenantId() != null) {
            sql.append(" AND tenant_id=?");
            params.add(checked.tenantId());
        }
        if (text(checked.phoneNumber()) != null) {
            sql.append(" AND phone_hash=?");
            params.add(phoneHash(checked.phoneNumber()));
        }
        if (text(checked.keyword()) != null) {
            sql.append(" AND content LIKE ?");
            params.add("%" + checked.keyword().trim() + "%");
        }
        if (text(checked.carrier()) != null) {
            sql.append(" AND carrier=?");
            params.add(checked.carrier().trim());
        }
        if (text(checked.pushState()) != null) {
            sql.append(" AND push_state=?");
            params.add(checked.pushState().trim().toUpperCase(Locale.ROOT));
        }
        if (checked.startTime() != null) {
            sql.append(" AND receive_time>=?");
            params.add(checked.startTime());
        }
        if (checked.endTime() != null) {
            sql.append(" AND receive_time<=?");
            params.add(checked.endTime());
        }
        sql.append(" ORDER BY receive_time DESC, id DESC LIMIT 200");
        return jdbc.query(sql.toString(), (rs, row) -> record(rs), params.toArray());
    }

    private static UplinkRecord record(ResultSet rs) throws SQLException {
        return new UplinkRecord(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("source_protocol"),
                rs.getString("source_connector"), rs.getString("source_event_id"), rs.getString("message_id"),
                rs.getString("phone_masked"), rs.getString("content"), rs.getString("content_keyword"),
                rs.getString("state"), rs.getString("carrier"), rs.getString("province"), rs.getString("city"),
                rs.getString("destination"), nullableLong(rs, "channel_id"), nullableLong(rs, "signature_id"),
                rs.getString("product_code"), rs.getString("push_state"), nullableLong(rs, "push_event_id"),
                timestamp(rs.getTimestamp("receive_time")), timestamp(rs.getTimestamp("created_at")),
                timestamp(rs.getTimestamp("updated_at")));
    }

    private static PushMonitorRow pushRow(ResultSet rs) throws SQLException {
        LocalDateTime createdAt = timestamp(rs.getTimestamp("created_at"));
        LocalDateTime updatedAt = timestamp(rs.getTimestamp("updated_at"));
        long latency = createdAt == null || updatedAt == null ? 0 : Math.max(0, Duration.between(createdAt, updatedAt).toMillis());
        return new PushMonitorRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("source_id"),
                rs.getString("logical_id"), rs.getString("destination_url"), rs.getString("state"),
                rs.getInt("attempt_count"), rs.getInt("max_attempts"), rs.getInt("attempt_rows"),
                timestamp(rs.getTimestamp("next_attempt_at")), updatedAt, latency);
    }

    private static AutoReplyConfig autoReply(ResultSet rs) throws SQLException {
        return new AutoReplyConfig(rs.getLong("tenant_id"), rs.getBoolean("enabled"), rs.getString("keyword"),
                rs.getString("template_id"), rs.getString("response_content"), rs.getInt("loop_guard_minutes"),
                rs.getString("audit_reason"), rs.getString("updated_by"), timestamp(rs.getTimestamp("updated_at")));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String phoneHash(String phone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(phone.trim().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("UPLINK_PHONE_HASH_UNAVAILABLE", ex);
        }
    }

    private static String maskPhone(String phone) {
        String clean = phone.trim();
        if (clean.length() <= 7) {
            return clean.charAt(0) + "****" + clean.charAt(clean.length() - 1);
        }
        return clean.substring(0, 3) + "****" + clean.substring(clean.length() - 4);
    }

    private static String contentKeyword(String explicit, String content) {
        String provided = text(explicit);
        if (provided != null) {
            return provided.substring(0, Math.min(64, provided.length()));
        }
        String normalized = content.trim();
        return normalized.substring(0, Math.min(32, normalized.length()));
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record NormalizeCommand(long tenantId, String sourceProtocol, String sourceConnector, String sourceEventId,
                                   String messageId, String phoneNumber, String content, String contentKeyword,
                                   String carrier, String province, String city, String destination, Long channelId,
                                   Long signatureId, String productCode, Boolean pushRequested,
                                   LocalDateTime receiveTime) {
        NormalizeCommand checked() {
            if (tenantId <= 0 || text(sourceProtocol) == null || text(sourceConnector) == null
                    || text(sourceEventId) == null || text(phoneNumber) == null || text(content) == null) {
                throw new BusinessException("UPLINK_NORMALIZATION_INVALID", "上行归一化字段不完整");
            }
            String protocol = sourceProtocol.trim().toUpperCase(Locale.ROOT);
            if (!List.of("HTTP", "CMPP").contains(protocol)) {
                throw new BusinessException("UPLINK_SOURCE_PROTOCOL_INVALID", "上行来源协议不支持");
            }
            if (phoneNumber.trim().length() > 32 || content.trim().length() > 500
                    || sourceConnector.trim().length() > 64 || sourceEventId.trim().length() > 128) {
                throw new BusinessException("UPLINK_NORMALIZATION_TOO_LONG", "上行归一化字段超过长度限制");
            }
            return new NormalizeCommand(tenantId, protocol, sourceConnector.trim(), sourceEventId.trim(),
                    text(messageId), phoneNumber.trim(), content.trim(), text(contentKeyword), text(carrier),
                    text(province), text(city), text(destination), channelId, signatureId, text(productCode),
                    Boolean.TRUE.equals(pushRequested), receiveTime);
        }
    }

    public record SearchFilter(Long tenantId, String phoneNumber, String keyword, String carrier, String pushState,
                               LocalDateTime startTime, LocalDateTime endTime) { }

    public record PushMonitorFilter(Long tenantId, String state, String destination) { }

    public record AutoReplyCommand(Boolean enabled, String keyword, String templateId, String responseContent,
                                   Integer loopGuardMinutes, String auditReason) {
        AutoReplyCommand checked() {
            boolean active = Boolean.TRUE.equals(enabled);
            int guard = loopGuardMinutes == null ? 30 : loopGuardMinutes;
            if (guard < 1 || guard > 1440) {
                throw new BusinessException("UPLINK_AUTO_REPLY_LOOP_GUARD_INVALID", "自动回复防循环窗口不合法");
            }
            if (active && (text(keyword) == null || text(responseContent) == null)) {
                throw new BusinessException("UPLINK_AUTO_REPLY_CONFIG_INVALID", "自动回复启用时必须填写关键词和回复内容");
            }
            if (text(auditReason) == null) {
                throw new BusinessException("UPLINK_AUTO_REPLY_AUDIT_REASON_REQUIRED", "自动回复配置原因不能为空");
            }
            return new AutoReplyCommand(active, text(keyword), text(templateId), text(responseContent), guard,
                    auditReason.trim());
        }
    }

    public record UplinkRecord(long id, long tenantId, String sourceProtocol, String sourceConnector,
                               String sourceEventId, String messageId, String phoneMasked, String content,
                               String contentKeyword, String state, String carrier, String province, String city,
                               String destination, Long channelId, Long signatureId, String productCode,
                               String pushState, Long pushEventId, LocalDateTime receiveTime,
                               LocalDateTime createdAt, LocalDateTime updatedAt) { }

    public record PushMonitorRow(long eventId, long tenantId, String sourceId, String logicalId,
                                 String destinationUrl, String state, int attemptCount, int maxAttempts,
                                 int attemptRows, LocalDateTime nextAttemptAt, LocalDateTime updatedAt,
                                 long latencyMs) { }

    public record AutoReplyConfig(long tenantId, boolean enabled, String keyword, String templateId,
                                  String responseContent, int loopGuardMinutes, String auditReason,
                                  String updatedBy, LocalDateTime updatedAt) { }

    public record AutoReplyDecision(long tenantId, String decision, String keyword, String templateId,
                                    String responseContent, String reason) { }
}
