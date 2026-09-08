package com.ycsopen.sms.core.service.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.web.dto.ChannelActivationResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ChannelConfigurationVersionService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ChannelConfigurationService configurations;
    private final ChannelConnectivityAdapter connectivity;
    private final ChannelConfigurationSnapshotRegistry registry;
    private final ObjectMapper json;
    private final OperationAuditService audits;

    public ChannelConfigurationVersionService(JdbcTemplate jdbc, ChannelConfigurationService configurations,
                                              ChannelConnectivityAdapter connectivity,
                                              ChannelConfigurationSnapshotRegistry registry,
                                              ObjectMapper json, OperationAuditService audits) {
        this.jdbc = jdbc;
        this.configurations = configurations;
        this.connectivity = connectivity;
        this.registry = registry;
        this.json = json;
        this.audits = audits;
    }

    @Transactional
    public ChannelActivationResponse activate(long actorUserId, long channelId, Long expectedEffectiveVersion) {
        assertOnlineMutable(channelId);
        Long expected = normalizeExpected(expectedEffectiveVersion);
        Long current = configurations.effectiveVersion(channelId);
        if (!same(current, expected)) {
            audit(actorUserId, channelId, null, "CHANNEL_CONFIGURATION_ACTIVATE_STALE", "CLIENT_FAILURE");
            return new ChannelActivationResponse(channelId, null, current,
                    "STALE_EXPECTED_VERSION", true, "STALE_EXPECTED_VERSION");
        }
        ChannelPayload payload = configurations.payload(channelId);
        return activatePayload(actorUserId, channelId, payload, expected, current, payload.configurationVersion(),
                "CHANNEL_CONFIGURATION_ACTIVATE", "CHANNEL_CONFIGURATION_ACTIVATE_REJECTED");
    }

    @Transactional
    public ChannelActivationResponse retry(long actorUserId, long channelId, long versionId) {
        assertOnlineMutable(channelId);
        Long current = configurations.effectiveVersion(channelId);
        ChannelPayload payload = versionPayload(channelId, versionId, Set.of("REJECTED", "STALE", "FAILED"));
        return activatePayload(actorUserId, channelId, payload, current, current, null,
                "CHANNEL_CONFIGURATION_RETRY", "CHANNEL_CONFIGURATION_RETRY_REJECTED");
    }

    @Transactional
    public ChannelActivationResponse rollback(long actorUserId, long channelId, long sourceVersionId) {
        assertOnlineMutable(channelId);
        Long current = configurations.effectiveVersion(channelId);
        ChannelPayload payload = versionPayload(channelId, sourceVersionId, Set.of("EFFECTIVE"));
        return activatePayload(actorUserId, channelId, payload, current, current, null,
                "CHANNEL_CONFIGURATION_ROLLBACK", "CHANNEL_CONFIGURATION_ROLLBACK_REJECTED");
    }

    private ChannelActivationResponse activatePayload(long actorUserId, long channelId, ChannelPayload payload,
                                                      Long expected, Long current, Long expectedConfigurationVersion,
                                                      String successOperation, String rejectedOperation) {
        long versionId = insertVersion(channelId, payload, "CREATED", null);
        var conformance = connectivity.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                com.ycsopen.sms.core.domain.entity.Channel.Protocol.valueOf(payload.protocol()),
                payload.host(), payload.port(), payload.maxConnections(), payload.windowSize()));
        if (!conformance.passed()) {
            markVersion(versionId, "REJECTED", conformance.reasonCode());
            audit(actorUserId, channelId, versionId, rejectedOperation, "CLIENT_FAILURE");
            return new ChannelActivationResponse(channelId, versionId, current,
                    "REJECTED", conformance.retryable(), conformance.reasonCode());
        }
        ChannelConfigurationSnapshotRegistry.Snapshot previousSnapshot = registry.effective(channelId);
        ChannelConfigurationSnapshotRegistry.Snapshot nextSnapshot = snapshot(payload, versionId);
        try {
            registry.swap(nextSnapshot);
        } catch (RuntimeException ex) {
            registry.restore(channelId, previousSnapshot);
            markVersion(versionId, "FAILED", "SNAPSHOT_LOAD_FAILED");
            audit(actorUserId, channelId, versionId, "CHANNEL_CONFIGURATION_ACTIVATE_FAILED", "SERVER_FAILURE");
            return new ChannelActivationResponse(channelId, versionId, current,
                    "HOT_LOAD_FAILED", true, "SNAPSHOT_LOAD_FAILED");
        }
        int updated = jdbc.update("""
                UPDATE channels
                   SET effective_version_id=?, configuration_version=configuration_version+1
                 WHERE id=? AND ((effective_version_id IS NULL AND ? IS NULL) OR effective_version_id=?)
                   AND (? IS NULL OR configuration_version=?)
                   AND status<>'OFFLINE'
                """, versionId, channelId, expected, expected, expectedConfigurationVersion,
                expectedConfigurationVersion);
        if (updated != 1) {
            registry.restore(channelId, previousSnapshot);
            if (isOffline(channelId)) {
                throw new BusinessException("CHANNEL_OFFLINE_IMMUTABLE", "已下线通道不能变更或激活");
            }
            Long effective = configurations.effectiveVersion(channelId);
            markVersion(versionId, "STALE", "STALE_EXPECTED_VERSION");
            audit(actorUserId, channelId, versionId, "CHANNEL_CONFIGURATION_ACTIVATE_STALE", "CLIENT_FAILURE");
            return new ChannelActivationResponse(channelId, versionId, effective,
                    "STALE_EXPECTED_VERSION", true, "STALE_EXPECTED_VERSION");
        }
        markVersion(versionId, "EFFECTIVE", null);
        audit(actorUserId, channelId, versionId, successOperation, "SUCCESS");
        return new ChannelActivationResponse(channelId, versionId, versionId, "EFFECTIVE", false, "PASSED");
    }

    private long insertVersion(long channelId, ChannelPayload payload, String status, String reason) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO channel_configuration_versions(channel_id, payload_json, status, reason_code, created_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, channelId);
            statement.setString(2, payloadJson(payload));
            statement.setString(3, status);
            statement.setString(4, reason);
            return statement;
        }, keys);
        if (!keys.getKeyList().isEmpty() && keys.getKeyList().getFirst().containsKey("ID")) {
            return ((Number) keys.getKeyList().getFirst().get("ID")).longValue();
        }
        return keys.getKey().longValue();
    }

    private void markVersion(long versionId, String status, String reason) {
        jdbc.update("UPDATE channel_configuration_versions SET status=?, reason_code=? WHERE id=?",
                status, reason, versionId);
    }

    private String payloadJson(ChannelPayload payload) {
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("channelId", payload.channelId());
            values.put("configurationVersion", payload.configurationVersion());
            values.put("name", payload.name());
            values.put("protocol", payload.protocol());
            values.put("operator", payload.operator());
            values.put("host", payload.host());
            values.put("port", payload.port());
            values.put("spId", payload.spId());
            values.put("serviceId", payload.serviceId());
            values.put("srcId", payload.srcId());
            values.put("maxConnections", payload.maxConnections());
            values.put("windowSize", payload.windowSize());
            values.put("tpsLimit", payload.tpsLimit());
            values.put("price", payload.price());
            values.put("priority", payload.priority());
            values.put("activeWindow", payload.activeWindow() == null ? "" : payload.activeWindow());
            values.put("availability", payload.availability());
            values.put("extraConfig", payload.extraConfig());
            return json.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("channel payload serialization failed", ex);
        }
    }

    private ChannelConfigurationSnapshotRegistry.Snapshot snapshot(ChannelPayload payload, long versionId) {
        return new ChannelConfigurationSnapshotRegistry.Snapshot(payload.channelId(), versionId, payload.name(),
                payload.protocol(), payload.operator(), payload.host(), payload.port(), payload.spId(),
                payload.serviceId(), payload.srcId(), payload.maxConnections(), payload.windowSize(),
                payload.tpsLimit(), payload.price(), payload.priority(), payload.activeWindow(), payload.availability(),
                payload.extraConfig());
    }

    private boolean same(Long current, Long expected) {
        if (current == null) return expected == null;
        return current.equals(expected);
    }

    private Long normalizeExpected(Long expected) {
        return expected != null && expected.longValue() == 0L ? null : expected;
    }

    private void assertOnlineMutable(long channelId) {
        try {
            String status = jdbc.queryForObject("SELECT status FROM channels WHERE id=?", String.class, channelId);
            if ("OFFLINE".equals(status)) {
                throw new BusinessException("CHANNEL_OFFLINE_IMMUTABLE", "已下线通道不能变更或激活");
            }
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("CHANNEL_NOT_FOUND", "通道不存在");
        }
    }

    private boolean isOffline(long channelId) {
        try {
            return "OFFLINE".equals(jdbc.queryForObject("SELECT status FROM channels WHERE id=?", String.class,
                    channelId));
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("CHANNEL_NOT_FOUND", "通道不存在");
        }
    }

    private ChannelPayload versionPayload(long channelId, long versionId, Set<String> allowedStatuses) {
        VersionRecord record;
        try {
            record = jdbc.queryForObject("""
                    SELECT payload_json, status
                      FROM channel_configuration_versions
                     WHERE id=? AND channel_id=?
                    """, (row, i) -> new VersionRecord(row.getString("payload_json"), row.getString("status")),
                    versionId, channelId);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("CHANNEL_CONFIGURATION_VERSION_NOT_FOUND", "通道配置版本不存在");
        }
        if (!allowedStatuses.contains(record.status())) {
            throw new BusinessException("CHANNEL_CONFIGURATION_VERSION_STATUS_INVALID", "通道配置版本状态不允许执行该操作");
        }
        try {
            Map<String, Object> values = json.readerFor(MAP_TYPE)
                    .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(record.payloadJson());
            return new ChannelPayload(channelId, longValue(values, "configurationVersion"), string(values, "name"), string(values, "protocol"),
                    string(values, "operator"), string(values, "host"), integer(values, "port"),
                    string(values, "spId"), string(values, "serviceId"), string(values, "srcId"),
                    integer(values, "maxConnections"), integer(values, "windowSize"),
                    integer(values, "tpsLimit"), decimal(values, "price"), integer(values, "priority"),
                    string(values, "activeWindow"), string(values, "availability"), map(values, "extraConfig"));
        } catch (JsonProcessingException | ClassCastException ex) {
            throw new BusinessException("CHANNEL_CONFIGURATION_VERSION_INVALID", "通道配置版本载荷不合法");
        }
    }

    private static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integer(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        return Integer.parseInt(String.valueOf(value));
    }

    private static long longValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return 0L;
        return Long.parseLong(String.valueOf(value));
    }

    private static BigDecimal decimal(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private record VersionRecord(String payloadJson, String status) { }

    private void audit(long actorUserId, long channelId, Long versionId, String operation, String result) {
        if (audits != null) {
            audits.append(new OperationAuditService.AuditCommand(actorUserId, operation, "CHANNEL",
                    String.valueOf(channelId), "INTERNAL", "/api/v1/console/channels/configuration/activate",
                    "{\"channelId\":" + channelId + ",\"versionId\":" + (versionId == null ? "null" : versionId) + "}",
                    result, 200, "internal", null, 0));
        }
    }

    public record ChannelPayload(long channelId, long configurationVersion, String name, String protocol, String operator,
                                 String host, Integer port, String spId, String serviceId, String srcId,
                                 int maxConnections, int windowSize, int tpsLimit, BigDecimal price, int priority,
                                 String activeWindow, String availability, Map<String, Object> extraConfig) { }
}
