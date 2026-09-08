package com.ycsopen.sms.core.service.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.service.audit.OperationAuditService;
import com.ycsopen.sms.core.web.dto.ChannelConfigurationRequest;
import com.ycsopen.sms.core.web.dto.ChannelConfigurationResponse;
import com.ycsopen.sms.core.web.dto.ChannelConnectivityResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ChannelConfigurationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbc;
    private final ChannelSecretProtector secrets;
    private final ChannelConnectivityAdapter connectivity;
    private final ObjectMapper json;
    private final OperationAuditService audits;

    public ChannelConfigurationService(JdbcTemplate jdbc, ChannelSecretProtector secrets,
                                       ChannelConnectivityAdapter connectivity, ObjectMapper json,
                                       OperationAuditService audits) {
        this.jdbc = jdbc;
        this.secrets = secrets;
        this.connectivity = connectivity;
        this.json = json;
        this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<ChannelConfigurationResponse> list() {
        return jdbc.query("""
                SELECT id, channel_name, protocol, operator, host, port, sp_id, service_id, src_id,
                       max_connections, window_size, tps_limit, price, priority, active_window,
                       availability, extra_config,
                       status, effective_version_id, configuration_version, updated_at
                FROM channels ORDER BY id DESC
                """, (row, i) -> response(row));
    }

    @Transactional
    public ChannelConfigurationResponse create(long actorUserId, ChannelConfigurationRequest request) {
        Validated validated = validate(request, null);
        var keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO channels(channel_name, protocol, operator, host, port, sp_id, service_id, src_id,
                                         max_connections, window_size, tps_limit, price, priority, active_window,
                                         availability, extra_config,
                                         status, configuration_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NORMAL', 0)
                    """, Statement.RETURN_GENERATED_KEYS);
            bindConfiguration(statement, validated);
            return statement;
        }, keys);
        long id = generatedId(keys);
        protectCredentials(id, validated);
        audit(actorUserId, id, "CHANNEL_CONFIGURATION_CREATE", "SUCCESS");
        return get(id);
    }

    @Transactional
    public ChannelConfigurationResponse update(long actorUserId, long id, ChannelConfigurationRequest request) {
        assertMutable(id);
        Validated validated = validate(request, id);
        int updated = jdbc.update("""
                UPDATE channels
                   SET channel_name=?, protocol=?, operator=?, host=?, port=?, sp_id=?, service_id=?, src_id=?,
                       max_connections=?, window_size=?, tps_limit=?, price=?, priority=?, active_window=?,
                       availability=?, extra_config=?,
                       configuration_version=configuration_version+1
                 WHERE id=? AND status<>'OFFLINE'
                """, parameters(validated, id));
        if (updated != 1) {
            throw new BusinessException("CHANNEL_OFFLINE_IMMUTABLE", "已下线通道不能变更");
        }
        protectCredentials(id, validated);
        audit(actorUserId, id, "CHANNEL_CONFIGURATION_UPDATE", "SUCCESS");
        return get(id);
    }

    @Transactional(readOnly = true)
    public ChannelConfigurationResponse get(long id) {
        return jdbc.query("""
                SELECT id, channel_name, protocol, operator, host, port, sp_id, service_id, src_id,
                       max_connections, window_size, tps_limit, price, priority, active_window,
                       availability, extra_config,
                       status, effective_version_id, configuration_version, updated_at
                FROM channels WHERE id=?
                """, (row, i) -> response(row), id).stream().findFirst()
                .orElseThrow(() -> new BusinessException("CHANNEL_NOT_FOUND", "通道不存在"));
    }

    @Transactional(readOnly = true)
    public ChannelConnectivityResponse testConnectivity(long id) {
        var channel = get(id);
        if ("OFFLINE".equals(channel.status())) {
            throw new BusinessException("CHANNEL_OFFLINE_IMMUTABLE", "已下线通道不能执行连接测试");
        }
        var result = connectivity.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                Channel.Protocol.valueOf(channel.protocol()), channel.host(), channel.port(),
                channel.maxConnections(), channel.windowSize()));
        return new ChannelConnectivityResponse(result.passed(), result.reasonCode(), result.retryable());
    }

    @Transactional(readOnly = true)
    ChannelConfigurationVersionService.ChannelPayload payload(long id) {
        ChannelConfigurationResponse channel = get(id);
        return new ChannelConfigurationVersionService.ChannelPayload(channel.id(), channel.configurationVersion(),
                channel.name(), channel.protocol(), channel.operator(), channel.host(), channel.port(), channel.spId(),
                channel.serviceId(), channel.srcId(), channel.maxConnections(), channel.windowSize(),
                channel.tpsLimit(), channel.price(), channel.priority(), channel.activeWindow(),
                channel.availability(), channel.extraConfig());
    }

    @Transactional(readOnly = true)
    Long effectiveVersion(long id) {
        assertExists(id);
        return jdbc.queryForObject("SELECT effective_version_id FROM channels WHERE id=?", Long.class, id);
    }

    private void protectCredentials(long id, Validated validated) {
        if (validated.account() == null && validated.password() == null) {
            return;
        }
        byte[] account = validated.account() == null ? null
                : secrets.protect(id, "account_encrypted", validated.account().toCharArray());
        byte[] password = validated.password() == null ? null
                : secrets.protect(id, "password_encrypted", validated.password().toCharArray());
        if (account != null && password != null) {
            jdbc.update("UPDATE channels SET account_encrypted=?, password_encrypted=? WHERE id=?",
                    account, password, id);
        } else if (account != null) {
            jdbc.update("UPDATE channels SET account_encrypted=? WHERE id=?", account, id);
        } else {
            jdbc.update("UPDATE channels SET password_encrypted=? WHERE id=?", password, id);
        }
    }

    private Validated validate(ChannelConfigurationRequest request, Long currentId) {
        String name = required(request.name(), "CHANNEL_NAME_REQUIRED", "通道名称不能为空");
        if (name.length() > 50) throw new BusinessException("CHANNEL_NAME_TOO_LONG", "通道名称不能超过50个字符");
        int duplicate = jdbc.queryForObject("""
                SELECT COUNT(*) FROM channels WHERE channel_name=? AND (? IS NULL OR id<>?)
                """, Integer.class, name, currentId, currentId);
        if (duplicate > 0) throw new BusinessException("CHANNEL_NAME_DUPLICATED", "通道名称已存在");
        Channel.Protocol protocol = parse(Channel.Protocol.class, request.protocol(), "CHANNEL_PROTOCOL_INVALID");
        Channel.Operator operator = parse(Channel.Operator.class, request.operator(), "CHANNEL_OPERATOR_INVALID");
        String host = required(request.host(), "CHANNEL_HOST_REQUIRED", "通道地址不能为空");
        Integer port = request.port();
        if (port == null || port < 1 || port > 65535) {
            throw new BusinessException("CHANNEL_PORT_INVALID", "通道端口不合法");
        }
        String account = blankToNull(request.account());
        String password = blankToNull(request.password());
        if (currentId == null && (account == null || password == null)) {
            throw new BusinessException("CHANNEL_CREDENTIAL_REQUIRED", "通道账号和密码不能为空");
        }
        int maxConnections = positive(request.maxConnections(), 10, "CHANNEL_MAX_CONNECTIONS_INVALID");
        int windowSize = positive(request.windowSize(), 8, "CHANNEL_WINDOW_SIZE_INVALID");
        int tpsLimit = positive(request.tpsLimit(), 100, "CHANNEL_TPS_LIMIT_INVALID");
        BigDecimal price = request.price() == null ? null : request.price().setScale(4, RoundingMode.HALF_UP);
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("CHANNEL_PRICE_INVALID", "通道单价不能为负");
        }
        int priority = request.priority() == null ? 50 : request.priority();
        if (priority < 1 || priority > 100) {
            throw new BusinessException("CHANNEL_PRIORITY_INVALID", "通道优先级必须在1到100之间");
        }
        String availability = required(request.availability(), "CHANNEL_AVAILABILITY_REQUIRED", "可用性不能为空");
        if (!availability.matches("[A-Z_]{3,32}")) {
            throw new BusinessException("CHANNEL_AVAILABILITY_INVALID", "可用性不合法");
        }
        String extraConfig = safeJson(request.extraConfig() == null ? Map.of() : request.extraConfig());
        var connectivityResult = connectivity.validate(new ChannelConnectivityAdapter.ChannelConfiguration(
                protocol, host, port, maxConnections, windowSize));
        if (!connectivityResult.passed()) {
            throw new BusinessException("CHANNEL_CONNECTIVITY_INVALID", connectivityResult.reasonCode());
        }
        return new Validated(name, protocol, operator, host, port, account, password,
                blankToNull(request.spId()), blankToNull(request.serviceId()), blankToNull(request.srcId()),
                maxConnections, windowSize, tpsLimit, price, priority, blankToNull(request.activeWindow()),
                availability, extraConfig);
    }

    private void assertExists(long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM channels WHERE id=?", Integer.class, id);
        if (count == null || count == 0) throw new BusinessException("CHANNEL_NOT_FOUND", "通道不存在");
    }

    private void assertMutable(long id) {
        ChannelConfigurationResponse channel = get(id);
        if ("OFFLINE".equals(channel.status())) {
            throw new BusinessException("CHANNEL_OFFLINE_IMMUTABLE", "已下线通道不能变更");
        }
    }

    private void audit(long actorUserId, long id, String operation, String result) {
        if (audits != null) {
            audits.append(new OperationAuditService.AuditCommand(actorUserId, operation, "CHANNEL",
                    String.valueOf(id), "INTERNAL", "/api/v1/console/channels/configuration",
                    "{\"fields\":\"redacted\"}", result, 200, "internal", null, 0));
        }
    }

    private ChannelConfigurationResponse response(java.sql.ResultSet row) throws java.sql.SQLException {
        return new ChannelConfigurationResponse(row.getLong("id"), row.getString("channel_name"),
                row.getString("protocol"), row.getString("operator"), row.getString("host"), row.getInt("port"),
                "******", "******", row.getString("sp_id"), row.getString("service_id"), row.getString("src_id"),
                row.getInt("max_connections"), row.getInt("window_size"), row.getInt("tps_limit"),
                row.getBigDecimal("price").setScale(4, RoundingMode.HALF_UP), row.getInt("priority"),
                row.getString("active_window"), row.getString("availability"),
                readMap(row.getString("extra_config")),
                row.getString("status"), nullableLong(row, "effective_version_id"),
                row.getLong("configuration_version"), toLocalDateTime(row, "updated_at"));
    }

    private static LocalDateTime toLocalDateTime(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        var value = row.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String safeJson(Map<String, Object> value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("CHANNEL_EXTRA_CONFIG_INVALID", "扩展配置不是合法JSON");
        }
    }

    private static String required(String value, String code, String message) {
        String trimmed = blankToNull(value);
        if (trimmed == null) throw new BusinessException(code, message);
        return trimmed;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static int positive(Integer value, int fallback, String code) {
        int normalized = value == null ? fallback : value;
        if (normalized <= 0) throw new BusinessException(code, "连接策略必须为正整数");
        return normalized;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String code) {
        try {
            return Enum.valueOf(type, required(value, code, "枚举值不能为空"));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(code, "枚举值不合法");
        }
    }

    private void bindConfiguration(PreparedStatement statement, Validated validated) throws java.sql.SQLException {
        Object[] values = parameters(validated);
        for (int i = 0; i < values.length; i++) {
            statement.setObject(i + 1, values[i]);
        }
    }

    private long generatedId(GeneratedKeyHolder keys) {
        if (!keys.getKeyList().isEmpty() && keys.getKeyList().getFirst().containsKey("ID")) {
            return ((Number) keys.getKeyList().getFirst().get("ID")).longValue();
        }
        return keys.getKey().longValue();
    }

    private Object[] parameters(Validated v, Object... suffix) {
        Object[] base = {v.name(), v.protocol().name(), v.operator().name(), v.host(), v.port(),
                v.spId(), v.serviceId(), v.srcId(), v.maxConnections(), v.windowSize(), v.tpsLimit(),
                v.price(), v.priority(), v.activeWindow(), v.availability(), v.extraConfig()};
        if (suffix.length == 0) return base;
        Object[] all = java.util.Arrays.copyOf(base, base.length + suffix.length);
        System.arraycopy(suffix, 0, all, base.length, suffix.length);
        return all;
    }

    private record Validated(String name, Channel.Protocol protocol, Channel.Operator operator, String host,
                             Integer port, String account, String password, String spId, String serviceId,
                             String srcId, int maxConnections, int windowSize, int tpsLimit, BigDecimal price,
                             int priority, String activeWindow, String availability, String extraConfig) { }
}
