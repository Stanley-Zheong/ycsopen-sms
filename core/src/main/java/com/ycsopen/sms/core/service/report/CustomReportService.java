package com.ycsopen.sms.core.service.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class CustomReportService {
    private static final Map<String, List<String>> DIMENSIONS = Map.of(
            "CHANNEL_DELIVERY", List.of("period", "tenant_id", "channel_id", "carrier", "province", "message_type"),
            "TENANT_BEHAVIOR", List.of("period", "tenant_id", "message_type"),
            "RESOURCE_USAGE", List.of("period", "tenant_id", "signature_id", "template_id", "message_type")
    );
    private static final Map<String, List<String>> MEASURES = Map.of(
            "CHANNEL_DELIVERY", List.of("submit_count", "send_count", "success_count", "failure_count",
                    "fee_amount", "avg_response_ms"),
            "TENANT_BEHAVIOR", List.of("submit_count", "accepted_count", "rejected_count", "send_count",
                    "success_count", "failure_count", "fee_amount"),
            "RESOURCE_USAGE", List.of("submit_count", "accepted_count", "rejected_count", "send_count",
                    "success_count", "failure_count")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public CustomReportService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.json = Objects.requireNonNull(json);
    }

    @Transactional(readOnly = true)
    public List<CapabilityRow> capabilities() {
        List<String> supportedMetricCodes = supportedMetricCodes();
        String placeholders = String.join(",", Collections.nCopies(supportedMetricCodes.size(), "?"));
        return jdbc.query("""
                SELECT metric_code, metric_name, formula, freshness_rule, permission_scope, formula_version
                  FROM statistics_metric_registry
                 WHERE status='ACTIVE'
                   AND metric_code IN (%s)
                 ORDER BY metric_code
                """.formatted(placeholders), (rs, row) -> {
            String metric = rs.getString("metric_code");
            return new CapabilityRow(metric, rs.getString("metric_name"),
                    DIMENSIONS.getOrDefault(metric, List.of()), MEASURES.getOrDefault(metric, List.of()),
                    rs.getString("formula"), rs.getString("freshness_rule"),
                    rs.getString("permission_scope"), rs.getString("formula_version"));
        }, supportedMetricCodes.toArray());
    }

    @Transactional(readOnly = true)
    public PreviewResult preview(ReportCommand command, Actor actor) {
        CheckedCommand checked = check(command, actor);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT *
                  FROM statistics_aggregates
                 WHERE metric_code=?
                """);
        params.add(checked.metric().metricCode());
        if (checked.command().tenantId() != null) {
            sql.append(" AND tenant_id=?");
            params.add(checked.command().tenantId());
        }
        if (checked.command().channelId() != null) {
            sql.append(" AND channel_id=?");
            params.add(checked.command().channelId());
        }
        if (text(checked.command().messageType()) != null) {
            sql.append(" AND message_type=?");
            params.add(checked.command().messageType().trim());
        }
        if (text(checked.command().province()) != null) {
            sql.append(" AND province=?");
            params.add(checked.command().province().trim());
        }
        if (checked.command().startTime() != null) {
            sql.append(" AND bucket_start>=?");
            params.add(checked.command().startTime());
        }
        if (checked.command().endTime() != null) {
            sql.append(" AND bucket_start<?");
            params.add(checked.command().endTime());
        }
        sql.append(" ORDER BY bucket_start, tenant_id, channel_id LIMIT 501");

        List<ReportDataRow> queriedRows = jdbc.query(sql.toString(), (rs, row) -> reportDataRow(rs, checked.command()),
                params.toArray());
        boolean truncated = queriedRows.size() > 500;
        List<ReportDataRow> rows = truncated ? queriedRows.subList(0, 500) : queriedRows;
        return new PreviewResult(checked.metric().metricCode(), checked.metric().metricName(),
                checked.metric().formula(), checked.metric().formulaVersion(), checked.metric().freshnessRule(),
                rows.stream().map(ReportDataRow::freshnessAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null),
                rows.stream().map(ReportDataRow::qualityState).filter(state -> !"FRESH".equals(state)).findFirst().orElse("FRESH"),
                accessibleColumns(checked.command()), truncated, rows);
    }

    @Transactional
    public DefinitionRow save(ReportCommand command, Actor actor) {
        CheckedCommand checked = check(command, actor);
        String dimensions = writeJson(checked.command().dimensions());
        String measures = writeJson(checked.command().measures());
        String filters = writeJson(new FilterSnapshot(checked.command().tenantId(), checked.command().channelId(),
                text(checked.command().messageType()), text(checked.command().province()), checked.command().startTime(),
                checked.command().endTime()));
        String snapshot = writeJson(new DefinitionSnapshot(checked.command().reportName(), checked.metric().metricCode(),
                checked.metric().metricName(), checked.command().dimensions(), checked.command().measures(),
                checked.command().tenantId(), checked.command().roleScope(), filters, checked.metric().formula(),
                checked.metric().formulaVersion(), checked.metric().freshnessRule()));
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO custom_report_definitions(report_name, metric_code, tenant_id, role_scope,
                        dimensions_json, measures_json, filters_json, definition_snapshot, created_by)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, checked.command().reportName().trim());
            ps.setString(2, checked.metric().metricCode());
            setLongOrNull(ps, 3, checked.command().tenantId());
            ps.setString(4, checked.command().roleScope().trim().toUpperCase(Locale.ROOT));
            ps.setString(5, dimensions);
            ps.setString(6, measures);
            ps.setString(7, filters);
            ps.setString(8, snapshot);
            ps.setString(9, checked.actor().name());
            return ps;
        }, key);
        return definition(generatedId(key), checked.command().reportName().trim(), checked.metric().metricCode(),
                checked.command().tenantId(), checked.command().roleScope().trim().toUpperCase(Locale.ROOT),
                dimensions, measures, filters, snapshot, "ACTIVE", checked.actor().name(), null);
    }

    @Transactional(readOnly = true)
    public List<DefinitionRow> definitions(Actor actor) {
        if (actor == null) {
            throw new BusinessException("CUSTOM_REPORT_ACTOR_REQUIRED", "报表操作人不能为空");
        }
        if (actor.tenantId() != null) {
            return jdbc.query("""
                    SELECT * FROM custom_report_definitions
                     WHERE tenant_id=? AND status='ACTIVE'
                     ORDER BY id DESC
                    """, (rs, row) -> definition(rs), actor.tenantId());
        }
        return jdbc.query("""
                SELECT * FROM custom_report_definitions
                 WHERE status='ACTIVE'
                 ORDER BY id DESC
                """, (rs, row) -> definition(rs));
    }

    @Transactional
    public ExportRequestRow requestExport(long definitionId, Actor actor) {
        if (actor == null || text(actor.name()) == null) {
            throw new BusinessException("CUSTOM_REPORT_ACTOR_REQUIRED", "报表操作人不能为空");
        }
        DefinitionRow definition = findDefinition(definitionId);
        if (actor.tenantId() != null && !Objects.equals(actor.tenantId(), definition.tenantId())) {
            throw new BusinessException("CUSTOM_REPORT_TENANT_FORBIDDEN", "租户范围无权访问");
        }
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO custom_report_export_requests(report_definition_id, definition_snapshot, requested_by)
                    VALUES (?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, definition.id());
            ps.setString(2, definition.definitionSnapshot());
            ps.setString(3, actor.name().trim());
            return ps;
        }, key);
        return new ExportRequestRow(generatedId(key), definition.id(), definition.definitionSnapshot(), "REQUESTED",
                actor.name().trim(), null);
    }

    private CheckedCommand check(ReportCommand command, Actor actor) {
        if (command == null || text(command.reportName()) == null || text(command.metricCode()) == null
                || command.dimensions() == null || command.dimensions().isEmpty()
                || command.measures() == null || command.measures().isEmpty()) {
            throw new BusinessException("CUSTOM_REPORT_INVALID", "报表定义字段不完整");
        }
        if (actor == null || text(actor.name()) == null) {
            throw new BusinessException("CUSTOM_REPORT_ACTOR_REQUIRED", "报表操作人不能为空");
        }
        if (command.reportName().trim().length() > 128) {
            throw new BusinessException("CUSTOM_REPORT_NAME_TOO_LONG", "报表名称不能超过128个字符");
        }
        String metricCode = command.metricCode().trim().toUpperCase(Locale.ROOT);
        if (!DIMENSIONS.containsKey(metricCode) || !MEASURES.containsKey(metricCode)) {
            throw new BusinessException("CUSTOM_REPORT_METRIC_UNSUPPORTED", "报表指标来源不支持");
        }
        MetricRow metric = metric(metricCode);
        List<String> allowedDimensions = DIMENSIONS.getOrDefault(metricCode, List.of());
        for (String dimension : command.dimensions()) {
            if (!allowedDimensions.contains(dimension)) {
                throw new BusinessException("CUSTOM_REPORT_DIMENSION_UNSUPPORTED", "报表维度不支持: " + dimension);
            }
        }
        List<String> allowedMeasures = MEASURES.getOrDefault(metricCode, List.of());
        for (String measure : command.measures()) {
            if (!allowedMeasures.contains(measure)) {
                throw new BusinessException("CUSTOM_REPORT_MEASURE_UNSUPPORTED", "报表指标不支持: " + measure);
            }
        }
        if (actor.tenantId() != null) {
            if (command.tenantId() == null || !Objects.equals(actor.tenantId(), command.tenantId())) {
                throw new BusinessException("CUSTOM_REPORT_TENANT_FORBIDDEN", "租户范围无权访问");
            }
        }
        if ("TENANT".equals(metric.permissionScope()) && command.tenantId() == null) {
            throw new BusinessException("CUSTOM_REPORT_TENANT_REQUIRED", "租户指标必须限定租户范围");
        }
        String checkedMessageType = text(command.messageType());
        if (checkedMessageType != null) {
            checkedMessageType = checkedMessageType.toUpperCase(Locale.ROOT);
        }
        String checkedRoleScope = actor.tenantId() == null ? roleScope(command.roleScope()) : "TENANT";
        return new CheckedCommand(new ReportCommand(command.reportName().trim(), metric.metricCode(),
                List.copyOf(command.dimensions()), List.copyOf(command.measures()), command.tenantId(),
                command.channelId(), checkedMessageType, text(command.province()), command.startTime(),
                command.endTime(), checkedRoleScope), metric, actor);
    }

    private MetricRow metric(String metricCode) {
        try {
            return jdbc.queryForObject("""
                    SELECT metric_code, metric_name, formula, freshness_rule, permission_scope, formula_version
                      FROM statistics_metric_registry
                     WHERE metric_code=? AND status='ACTIVE'
                    """, (rs, row) -> new MetricRow(rs.getString("metric_code"), rs.getString("metric_name"),
                    rs.getString("formula"), rs.getString("freshness_rule"), rs.getString("permission_scope"),
                    rs.getString("formula_version")), metricCode);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("CUSTOM_REPORT_METRIC_UNSUPPORTED", "报表指标来源不支持");
        }
    }

    private DefinitionRow findDefinition(long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT * FROM custom_report_definitions WHERE id=? AND status='ACTIVE'
                    """, (rs, row) -> definition(rs), id);
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("CUSTOM_REPORT_DEFINITION_NOT_FOUND", "报表定义不存在");
        }
    }

    private ReportDataRow reportDataRow(ResultSet rs, ReportCommand command) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String dimension : command.dimensions()) {
            values.put(dimension, dimensionValue(rs, dimension));
        }
        for (String measure : command.measures()) {
            values.put(measure, measureValue(rs, measure));
        }
        return new ReportDataRow(values, rs.getString("drilldown_key"), rs.getString("quality_state"),
                timestamp(rs.getTimestamp("freshness_at")));
    }

    private Object dimensionValue(ResultSet rs, String dimension) throws SQLException {
        return switch (dimension) {
            case "period" -> rs.getDate("bucket_date").toLocalDate().toString();
            case "tenant_id" -> nullableLong(rs, "tenant_id");
            case "channel_id" -> nullableLong(rs, "channel_id");
            case "carrier" -> rs.getString("carrier");
            case "province" -> rs.getString("province");
            case "message_type" -> rs.getString("message_type");
            case "signature_id" -> nullableLong(rs, "signature_id");
            case "template_id" -> nullableLong(rs, "template_id");
            default -> throw new BusinessException("CUSTOM_REPORT_DIMENSION_UNSUPPORTED", "报表维度不支持: " + dimension);
        };
    }

    private Object measureValue(ResultSet rs, String measure) throws SQLException {
        return switch (measure) {
            case "submit_count" -> rs.getInt("submit_count");
            case "accepted_count" -> rs.getInt("accepted_count");
            case "rejected_count" -> rs.getInt("rejected_count");
            case "send_count" -> rs.getInt("send_count");
            case "success_count" -> rs.getInt("success_count");
            case "failure_count" -> rs.getInt("failure_count");
            case "fee_amount" -> rs.getBigDecimal("fee_amount");
            case "avg_response_ms" -> rs.getLong("avg_response_ms");
            default -> throw new BusinessException("CUSTOM_REPORT_MEASURE_UNSUPPORTED", "报表指标不支持: " + measure);
        };
    }

    private List<String> accessibleColumns(ReportCommand command) {
        List<String> columns = new ArrayList<>(command.dimensions());
        columns.addAll(command.measures());
        return List.copyOf(columns);
    }

    private DefinitionRow definition(ResultSet rs) throws SQLException {
        return definition(rs.getLong("id"), rs.getString("report_name"), rs.getString("metric_code"),
                nullableLong(rs, "tenant_id"), rs.getString("role_scope"), rs.getString("dimensions_json"),
                rs.getString("measures_json"), rs.getString("filters_json"), rs.getString("definition_snapshot"),
                rs.getString("status"), rs.getString("created_by"), timestamp(rs.getTimestamp("created_at")));
    }

    private DefinitionRow definition(long id, String reportName, String metricCode, Long tenantId, String roleScope,
                                     String dimensionsJson, String measuresJson, String filtersJson,
                                     String definitionSnapshot, String status, String createdBy,
                                     LocalDateTime createdAt) {
        return new DefinitionRow(id, reportName, metricCode, tenantId, roleScope, dimensionsJson, measuresJson,
                filtersJson, definitionSnapshot, status, createdBy, createdAt);
    }

    private static void setLongOrNull(PreparedStatement ps, int parameterIndex, Long value) throws SQLException {
        if (value == null) {
            ps.setObject(parameterIndex, null);
        } else {
            ps.setLong(parameterIndex, value);
        }
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("CUSTOM_REPORT_JSON_INVALID", "报表定义快照无法序列化");
        }
    }

    private static long generatedId(GeneratedKeyHolder key) {
        if (!key.getKeyList().isEmpty() && key.getKeyList().get(0).get("id") instanceof Number id) {
            return id.longValue();
        }
        if (key.getKey() == null) {
            throw new IllegalStateException("CUSTOM_REPORT_GENERATED_ID_MISSING");
        }
        return key.getKey().longValue();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static String roleScope(String value) {
        String scope = text(value) == null ? "PLATFORM" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PLATFORM", "TENANT").contains(scope)) {
            throw new BusinessException("CUSTOM_REPORT_ROLE_SCOPE_INVALID", "报表角色范围不支持");
        }
        return scope;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> supportedMetricCodes() {
        return DIMENSIONS.keySet().stream()
                .filter(MEASURES::containsKey)
                .sorted()
                .toList();
    }

    private record CheckedCommand(ReportCommand command, MetricRow metric, Actor actor) { }

    private record MetricRow(String metricCode, String metricName, String formula, String freshnessRule,
                             String permissionScope, String formulaVersion) { }

    private record FilterSnapshot(Long tenantId, Long channelId, String messageType, String province,
                                  LocalDateTime startTime, LocalDateTime endTime) { }

    private record DefinitionSnapshot(String reportName, String metricCode, String metricName, List<String> dimensions,
                                      List<String> measures, Long tenantId, String roleScope, String filters,
                                      String formula, String formulaVersion, String freshnessRule) { }

    public record Actor(String name, Long tenantId) {
        public static Actor platform(String name) {
            return new Actor(name, null);
        }

        public static Actor tenant(String name, long tenantId) {
            return new Actor(name, tenantId);
        }
    }

    public record ReportCommand(String reportName, String metricCode, List<String> dimensions, List<String> measures,
                                Long tenantId, Long channelId, String messageType, String province,
                                LocalDateTime startTime, LocalDateTime endTime, String roleScope) { }

    public record CapabilityRow(String metricCode, String metricName, List<String> dimensions, List<String> measures,
                                String formula, String freshnessRule, String permissionScope,
                                String formulaVersion) { }

    public record PreviewResult(String metricCode, String metricName, String formula, String formulaVersion,
                                String freshnessRule, LocalDateTime freshnessAt, String qualityState,
                                List<String> accessibleColumns, boolean truncated, List<ReportDataRow> rows) { }

    public record ReportDataRow(Map<String, Object> values, String drilldownKey, String qualityState,
                                LocalDateTime freshnessAt) { }

    public record DefinitionRow(long id, String reportName, String metricCode, Long tenantId, String roleScope,
                                String dimensionsJson, String measuresJson, String filtersJson,
                                String definitionSnapshot, String status, String createdBy,
                                LocalDateTime createdAt) { }

    public record ExportRequestRow(long id, long reportDefinitionId, String definitionSnapshot, String status,
                                   String requestedBy, LocalDateTime requestedAt) { }
}
