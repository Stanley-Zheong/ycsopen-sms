package com.ycsopen.sms.core.service.webhook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookDeliveryTransportService {
    private static final String SIGNING_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECRETS = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final WebhookDeliveryClient client;
    private final ObjectMapper json;

    @Autowired
    public WebhookDeliveryTransportService(JdbcTemplate jdbc, WebhookDeliveryClient client, ObjectMapper json) {
        this.jdbc = jdbc;
        this.client = client;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public CallbackConfig config(long tenantId) {
        List<CallbackConfig> configs = jdbc.query("""
                SELECT tenant_id, delivery_callback_url, uplink_callback_url, unsubscribe_callback_url,
                       retry_max_count, retry_backoff_seconds, config_status, latest_failure_reason, paused_at, version
                  FROM tenant_callback_configs
                 WHERE tenant_id=?
                """, (rs, row) -> new CallbackConfig(
                rs.getLong("tenant_id"),
                rs.getString("delivery_callback_url"),
                rs.getString("uplink_callback_url"),
                rs.getString("unsubscribe_callback_url"),
                rs.getInt("retry_max_count"),
                rs.getInt("retry_backoff_seconds"),
                rs.getString("config_status"),
                rs.getString("latest_failure_reason"),
                timestamp(rs.getTimestamp("paused_at")),
                rs.getInt("version")), tenantId);
        return configs.isEmpty() ? new CallbackConfig(tenantId, null, null, null, 5, 30,
                "ACTIVE", null, null, 0) : configs.get(0);
    }

    @Transactional
    public CallbackConfig saveConfig(long tenantId, CallbackConfigCommand command) {
        CallbackConfigCommand checked = command.checked();
        validateOptional("STATUS", checked.statusCallbackUrl());
        validateOptional("UPLINK", checked.uplinkCallbackUrl());
        validateOptional("UNSUBSCRIBE", checked.unsubscribeCallbackUrl());
        int updated = jdbc.update("""
                UPDATE tenant_callback_configs
                   SET delivery_callback_url=?, uplink_callback_url=?, unsubscribe_callback_url=?,
                       retry_max_count=?, retry_backoff_seconds=?, config_status='ACTIVE',
                       latest_failure_reason=NULL, paused_at=NULL, updated_at=CURRENT_TIMESTAMP, version=version+1,
                       callback_signing_secret=COALESCE(callback_signing_secret, ?)
                 WHERE tenant_id=?
                """, checked.statusCallbackUrl(), checked.uplinkCallbackUrl(), checked.unsubscribeCallbackUrl(),
                checked.retryMaxCount(), checked.retryBackoffSeconds(), newSigningSecret(), tenantId);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO tenant_callback_configs(tenant_id, delivery_callback_url, uplink_callback_url,
                        unsubscribe_callback_url, retry_max_count, retry_backoff_seconds, config_status, version,
                        callback_signing_secret)
                    VALUES (?,?,?,?,?,?, 'ACTIVE', 1, ?)
                    """, tenantId, checked.statusCallbackUrl(), checked.uplinkCallbackUrl(),
                    checked.unsubscribeCallbackUrl(), checked.retryMaxCount(), checked.retryBackoffSeconds(),
                    newSigningSecret());
        }
        return config(tenantId);
    }

    public TestResult testDestination(long tenantId, CallbackType type, String destinationUrl) {
        String target = destinationUrl == null || destinationUrl.isBlank()
                ? configuredDestination(tenantId, type)
                : destinationUrl.trim();
        validateDestination(type.name(), target);
        long eventId = enqueue(tenantId, type.eventType(), "TEST-" + tenantId + "-" + type.name(),
                target, payload(Map.of("kind", "WEBHOOK_TEST", "tenantId", tenantId, "type", type.name())));
        DeliveryResult result = deliverEvent(eventId);
        return new TestResult(eventId, target, result.state(), result.resultCode(), result.resultMessage());
    }

    @Transactional
    public EnqueueResult enqueueStatusEvent(long tenantId, String messageId, String status, String callbackUrl) {
        if (messageId == null || messageId.isBlank() || status == null || status.isBlank()) {
            throw new BusinessException("WEBHOOK_EVENT_INVALID", "回调事件字段不完整");
        }
        String destination = callbackUrl == null || callbackUrl.isBlank()
                ? configuredDestination(tenantId, CallbackType.STATUS)
                : callbackUrl.trim();
        validateDestination("STATUS", destination);
        String logicalId = "STATUS:" + messageId.trim() + ":" + status.trim().toUpperCase(Locale.ROOT);
        String payload = payload(Map.of("kind", "STATUS", "tenantId", tenantId,
                "messageId", messageId.trim(), "status", status.trim().toUpperCase(Locale.ROOT)));
        long eventId = enqueue(tenantId, "STATUS", logicalId, destination, payload);
        return new EnqueueResult(eventId, logicalId, destination);
    }

    @Transactional
    public EnqueueResult enqueueUplinkEvent(long tenantId, String uplinkId, String messageId, String phone,
                                            String content) {
        if (uplinkId == null || uplinkId.isBlank() || phone == null || phone.isBlank()
                || content == null || content.isBlank()) {
            throw new BusinessException("WEBHOOK_EVENT_INVALID", "上行回调事件字段不完整");
        }
        String destination = configuredDestination(tenantId, CallbackType.UPLINK);
        validateDestination("UPLINK", destination);
        String logicalId = "UPLINK:" + uplinkId.trim();
        String payload = payload(Map.of(
                "kind", "UPLINK",
                "tenantId", tenantId,
                "uplinkId", uplinkId.trim(),
                "messageId", messageId == null ? "" : messageId.trim(),
                "phone", phone.trim(),
                "content", content.trim()));
        long eventId = enqueue(tenantId, "UPLINK", logicalId, destination, payload);
        return new EnqueueResult(eventId, logicalId, destination);
    }

    @Transactional
    public EnqueueResult enqueueUnsubscribeEvent(long tenantId, String unsubscribeId, long uplinkRecordId,
                                                 String messageId, String phone, String keyword,
                                                 String handlingState) {
        if (unsubscribeId == null || unsubscribeId.isBlank() || uplinkRecordId < 1
                || phone == null || phone.isBlank() || keyword == null || keyword.isBlank()) {
            throw new BusinessException("WEBHOOK_EVENT_INVALID", "退订通知事件字段不完整");
        }
        String destination = configuredDestination(tenantId, CallbackType.UNSUBSCRIBE);
        validateDestination("UNSUBSCRIBE", destination);
        String logicalId = "UNSUBSCRIBE:" + unsubscribeId.trim();
        String payload = payload(Map.of(
                "kind", "UNSUBSCRIBE",
                "tenantId", tenantId,
                "unsubscribeId", unsubscribeId.trim(),
                "uplinkRecordId", uplinkRecordId,
                "messageId", messageId == null ? "" : messageId.trim(),
                "phone", phone.trim(),
                "keyword", keyword.trim(),
                "handlingState", handlingState == null ? "" : handlingState.trim()));
        long eventId = enqueue(tenantId, "UNSUBSCRIBE", logicalId, destination, payload);
        return new EnqueueResult(eventId, logicalId, destination);
    }

    public Optional<DeliveryResult> deliverNext() {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM webhook_delivery_events
                 WHERE state IN ('PENDING','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP
                 ORDER BY next_attempt_at, id
                 LIMIT 1
                """, Long.class);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(deliverEvent(ids.get(0)));
    }

    @Transactional(readOnly = true)
    public List<FailureRow> failures(Long tenantId, String state) {
        String effectiveState = state == null || state.isBlank() ? "PUSH_FAILED" : state.trim().toUpperCase(Locale.ROOT);
        if (tenantId == null) {
            return jdbc.query("""
                    SELECT id, tenant_id, event_type, source_id, logical_id, destination_url, state,
                           attempt_count, max_attempts, next_attempt_at, updated_at
                      FROM webhook_delivery_events
                     WHERE state=?
                     ORDER BY updated_at DESC, id DESC
                     LIMIT 200
                    """, (rs, row) -> failure(rs), effectiveState);
        }
        return jdbc.query("""
                SELECT id, tenant_id, event_type, source_id, logical_id, destination_url, state,
                       attempt_count, max_attempts, next_attempt_at, updated_at
                  FROM webhook_delivery_events
                 WHERE tenant_id=? AND state=?
                 ORDER BY updated_at DESC, id DESC
                 LIMIT 200
                """, (rs, row) -> failure(rs), tenantId, effectiveState);
    }

    public DeliveryResult replay(long eventId, String actor, String reason) {
        requireReason(reason);
        EventRow row = event(eventId);
        if ("DELIVERED".equals(row.state())) {
            return new DeliveryResult(eventId, row.tenantId(), row.state(), "ALREADY_DELIVERED", null);
        }
        jdbc.update("""
                UPDATE webhook_delivery_events
                   SET state='RETRY', attempt_count=0, next_attempt_at=CURRENT_TIMESTAMP,
                       paused_at=NULL, updated_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, eventId);
        return deliverEvent(eventId);
    }

    @Transactional
    public DeliveryResult pause(long eventId, String actor, String reason) {
        requireReason(reason);
        EventRow row = event(eventId);
        jdbc.update("""
                UPDATE webhook_delivery_events
                   SET state='PAUSED', paused_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND state<>'DELIVERED'
                """, eventId);
        return new DeliveryResult(eventId, row.tenantId(), "PAUSED", "PAUSED", actor);
    }

    @Transactional
    public DeliveryResult resume(long eventId, String actor, String reason) {
        requireReason(reason);
        EventRow row = event(eventId);
        jdbc.update("""
                UPDATE webhook_delivery_events
                   SET state='RETRY', next_attempt_at=CURRENT_TIMESTAMP, paused_at=NULL, updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND state='PAUSED'
                """, eventId);
        return new DeliveryResult(eventId, row.tenantId(), "RETRY", "RESUMED", actor);
    }

    private DeliveryResult deliverEvent(long eventId) {
        EventRow row = event(eventId);
        validateDestination(row.eventType(), row.destinationUrl());
        int claimed = jdbc.update("""
                UPDATE webhook_delivery_events
                   SET state='SENDING', updated_at=CURRENT_TIMESTAMP
                 WHERE id=? AND state IN ('PENDING','RETRY')
                """, eventId);
        if (claimed != 1) {
            return new DeliveryResult(eventId, row.tenantId(), row.state(), "NOT_DELIVERABLE", null);
        }
        int attemptNo = nextAttemptNo(eventId, row.attemptCount());
        WebhookDeliveryClient.DeliveryResponse response = client.post(row.destinationUrl(), row.payloadJson(), Map.of(
                "X-YCS-Webhook-Version", row.envelopeVersion(),
                "X-YCS-Webhook-Idempotency-Key", row.logicalId(),
                "X-YCS-Webhook-Signature", row.signature()));
        recordAttempt(eventId, attemptNo, row.destinationUrl(), response);
        if (response.success()) {
            jdbc.update("""
                    UPDATE webhook_delivery_events
                       SET state='DELIVERED', attempt_count=?, terminal_at=CURRENT_TIMESTAMP,
                           updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, attemptNo, eventId);
            return new DeliveryResult(eventId, row.tenantId(), "DELIVERED", response.resultCode(), response.resultMessage());
        }
        if (attemptNo >= row.maxAttempts()) {
            jdbc.update("""
                    UPDATE webhook_delivery_events
                       SET state='PUSH_FAILED', attempt_count=?, terminal_at=CURRENT_TIMESTAMP,
                           updated_at=CURRENT_TIMESTAMP
                     WHERE id=?
                    """, attemptNo, eventId);
            jdbc.update("""
                    UPDATE tenant_callback_configs
                       SET latest_failure_reason=?, updated_at=CURRENT_TIMESTAMP
                     WHERE tenant_id=?
                    """, safeMessage(response.resultCode(), response.resultMessage()), row.tenantId());
            return new DeliveryResult(eventId, row.tenantId(), "PUSH_FAILED", response.resultCode(), response.resultMessage());
        }
        int backoffSeconds = config(row.tenantId()).retryBackoffSeconds() * (1 << Math.min(attemptNo - 1, 6));
        LocalDateTime nextAttemptAt = LocalDateTime.now().plusSeconds(backoffSeconds);
        jdbc.update("""
                UPDATE webhook_delivery_events
                   SET state='RETRY', attempt_count=?, next_attempt_at=?,
                       updated_at=CURRENT_TIMESTAMP
                 WHERE id=?
                """, attemptNo, nextAttemptAt, eventId);
        return new DeliveryResult(eventId, row.tenantId(), "RETRY", response.resultCode(), response.resultMessage());
    }

    private long enqueue(long tenantId, String eventType, String logicalId, String destinationUrl, String payloadJson) {
        int maxAttempts = Math.max(1, config(tenantId).retryMaxCount());
        String signature = sign(tenantId, logicalId, payloadJson);
        try {
            jdbc.update("""
                    INSERT INTO webhook_delivery_events(tenant_id, event_type, source_id, logical_id, destination_url,
                        envelope_version, payload_json, signature, state, max_attempts, next_attempt_at)
                    VALUES (?,?,?,?,?,?,?,?, 'PENDING', ?, CURRENT_TIMESTAMP)
                    """, tenantId, eventType, logicalId, logicalId, destinationUrl,
                    "v1", payloadJson, signature, maxAttempts);
        } catch (DuplicateKeyException duplicate) {
            // Logical event identity is the idempotency boundary. Reuse the existing row.
        }
        return jdbc.queryForObject("""
                SELECT id FROM webhook_delivery_events WHERE tenant_id=? AND event_type=? AND logical_id=?
                """, Long.class, tenantId, eventType, logicalId);
    }

    private void recordAttempt(long eventId, int attemptNo, String destinationUrl,
                               WebhookDeliveryClient.DeliveryResponse response) {
        jdbc.update("""
                INSERT INTO webhook_delivery_attempts(event_id, attempt_no, destination_url, http_status, result_code, result_message)
                VALUES (?,?,?,?,?,?)
                """, eventId, attemptNo, destinationUrl, response.httpStatus(), response.resultCode(),
                response.resultMessage() == null ? null : response.resultMessage().substring(0, Math.min(255, response.resultMessage().length())));
    }

    private int nextAttemptNo(long eventId, int currentAttemptCount) {
        Integer latest = jdbc.queryForObject("""
                SELECT COALESCE(MAX(attempt_no), 0) FROM webhook_delivery_attempts WHERE event_id=?
                """, Integer.class, eventId);
        return Math.max(currentAttemptCount, latest == null ? 0 : latest) + 1;
    }

    private String configuredDestination(long tenantId, CallbackType type) {
        CallbackConfig config = config(tenantId);
        String destination = switch (type) {
            case STATUS -> config.statusCallbackUrl();
            case UPLINK -> config.uplinkCallbackUrl();
            case UNSUBSCRIBE -> config.unsubscribeCallbackUrl();
        };
        if (destination == null || destination.isBlank()) {
            throw new BusinessException("WEBHOOK_DESTINATION_NOT_CONFIGURED", "未配置回调地址");
        }
        return destination;
    }

    private EventRow event(long eventId) {
        List<EventRow> rows = jdbc.query("""
                SELECT id, tenant_id, event_type, source_id, logical_id, destination_url, envelope_version,
                       payload_json, signature, state, attempt_count, max_attempts
                  FROM webhook_delivery_events
                 WHERE id=?
                """, (rs, row) -> new EventRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("event_type"),
                rs.getString("source_id"), rs.getString("logical_id"), rs.getString("destination_url"),
                rs.getString("envelope_version"), rs.getString("payload_json"), rs.getString("signature"),
                rs.getString("state"), rs.getInt("attempt_count"), rs.getInt("max_attempts")), eventId);
        if (rows.isEmpty()) {
            throw new BusinessException("WEBHOOK_EVENT_NOT_FOUND", "回调事件不存在");
        }
        return rows.get(0);
    }

    private FailureRow failure(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new FailureRow(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("event_type"),
                rs.getString("source_id"), rs.getString("logical_id"), rs.getString("destination_url"),
                rs.getString("state"), rs.getInt("attempt_count"), rs.getInt("max_attempts"),
                timestamp(rs.getTimestamp("next_attempt_at")), timestamp(rs.getTimestamp("updated_at")));
    }

    private static void validateOptional(String type, String url) {
        if (url != null && !url.isBlank()) {
            validateDestination(type, url);
        }
    }

    public static void validateDestination(String type, String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException("WEBHOOK_DESTINATION_REQUIRED", "回调地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("WEBHOOK_DESTINATION_INVALID", "回调地址格式不合法");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new BusinessException("WEBHOOK_DESTINATION_HTTPS_REQUIRED", type + "回调地址必须使用 HTTPS");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".local")) {
            throw new BusinessException("WEBHOOK_DESTINATION_SSRF_REJECTED", "回调地址不允许指向本机或内网");
        }
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new BusinessException("WEBHOOK_DESTINATION_UNRESOLVABLE", "回调地址域名无法解析");
        }
        for (InetAddress address : resolved) {
            if (isBlockedAddress(address)) {
                throw new BusinessException("WEBHOOK_DESTINATION_SSRF_REJECTED", "回调地址不允许指向本机或内网");
            }
        }
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168);
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xff;
            return (first & 0xfe) == 0xfc || first == 0xfe;
        }
        return false;
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("WEBHOOK_ACTION_REASON_REQUIRED", "操作原因不能为空");
        }
    }

    private String sign(long tenantId, String logicalId, String payloadJson) {
        try {
            Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
            mac.init(new SecretKeySpec(ensureSigningSecret(tenantId).getBytes(StandardCharsets.UTF_8), SIGNING_ALGORITHM));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal((logicalId + "\n" + payloadJson).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("WEBHOOK_SIGNATURE_UNAVAILABLE", ex);
        }
    }

    private String ensureSigningSecret(long tenantId) {
        List<String> existing = jdbc.queryForList("""
                SELECT callback_signing_secret FROM tenant_callback_configs WHERE tenant_id=?
                """, String.class, tenantId);
        if (!existing.isEmpty() && existing.get(0) != null && !existing.get(0).isBlank()) {
            return existing.get(0);
        }
        String secret = newSigningSecret();
        try {
            int updated = jdbc.update("""
                    UPDATE tenant_callback_configs
                       SET callback_signing_secret=?, updated_at=CURRENT_TIMESTAMP
                     WHERE tenant_id=?
                    """, secret, tenantId);
            if (updated == 0) {
                jdbc.update("""
                        INSERT INTO tenant_callback_configs(tenant_id, callback_signing_secret)
                        VALUES (?,?)
                        """, tenantId, secret);
            }
            return secret;
        } catch (DuplicateKeyException duplicate) {
            return jdbc.queryForObject("""
                    SELECT callback_signing_secret FROM tenant_callback_configs WHERE tenant_id=?
                    """, String.class, tenantId);
        }
    }

    private String payload(Map<String, Object> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("WEBHOOK_PAYLOAD_UNAVAILABLE", ex);
        }
    }

    private static String newSigningSecret() {
        byte[] bytes = new byte[32];
        SECRETS.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safeMessage(String code, String message) {
        String text = (code == null ? "UNKNOWN" : code) + (message == null ? "" : ":" + message);
        return text.substring(0, Math.min(255, text.length()));
    }

    private static LocalDateTime timestamp(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public enum CallbackType {
        STATUS, UPLINK, UNSUBSCRIBE;

        String eventType() {
            return name();
        }
    }

    private record EventRow(long id, long tenantId, String eventType, String sourceId, String logicalId,
                            String destinationUrl, String envelopeVersion, String payloadJson, String signature,
                            String state, int attemptCount, int maxAttempts) { }

    public record CallbackConfig(long tenantId, String statusCallbackUrl, String uplinkCallbackUrl,
                                 String unsubscribeCallbackUrl, int retryMaxCount, int retryBackoffSeconds,
                                 String status, String latestFailureReason, LocalDateTime pausedAt, int version) { }

    public record CallbackConfigCommand(String statusCallbackUrl, String uplinkCallbackUrl, String unsubscribeCallbackUrl,
                                        Integer retryMaxCount, Integer retryBackoffSeconds) {
        CallbackConfigCommand checked() {
            int max = retryMaxCount == null ? 5 : retryMaxCount;
            int backoff = retryBackoffSeconds == null ? 30 : retryBackoffSeconds;
            if (max < 1 || max > 10 || backoff < 1 || backoff > 3600) {
                throw new BusinessException("WEBHOOK_RETRY_POLICY_INVALID", "重试策略不合法");
            }
            return new CallbackConfigCommand(text(statusCallbackUrl), text(uplinkCallbackUrl),
                    text(unsubscribeCallbackUrl), max, backoff);
        }

        private static String text(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    public record EnqueueResult(long eventId, String logicalId, String destinationUrl) { }
    public record TestResult(long eventId, String destinationUrl, String state, String resultCode, String resultMessage) { }
    public record DeliveryResult(long eventId, long tenantId, String state, String resultCode, String resultMessage) { }
    public record FailureRow(long eventId, long tenantId, String eventType, String sourceId, String logicalId,
                             String destinationUrl, String state, int attemptCount, int maxAttempts,
                             LocalDateTime nextAttemptAt, LocalDateTime updatedAt) { }
}
