package com.ycsopen.sms.core.service.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Append-only, redacted audit boundary for authenticated console operations. */
@Service
public class OperationAuditService {

    private final JdbcTemplate jdbc;

    public OperationAuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long append(AuditCommand command) {
        return insert(command);
    }

    /** Persists an attempt before controller code executes, so audit storage failure blocks the operation. */
    @Transactional
    public long start(AuditCommand command) {
        return insert(new AuditCommand(command.actorUserId(), command.operation(),
                command.resourceType(), command.resourceId(), command.method(), command.route(),
                command.sanitizedRequest(), "STARTED", 0, command.clientIp(), command.traceId(), 0));
    }

    /** Finalizes the one allowed STARTED-to-terminal transition. */
    @Transactional
    public void complete(long auditId, String result, int responseStatus, long latencyMs) {
        String terminal = terminalResult(result);
        jdbc.update("CALL finalize_privileged_operation_audit(?, ?, ?, ?)",
                auditId, terminal, responseStatus, Math.max(0, latencyMs));
    }

    private long insert(AuditCommand command) {
        Actor actor = actor(command.actorUserId());
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO privileged_operation_audits
                        (actor_user_id, actor_username, actor_user_type, tenant_id,
                         operation, resource_type, resource_id, request_method, route_template,
                         sanitized_request, result_code, response_status, client_ip, trace_id,
                         latency_ms, occurred_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """, new String[] {"id"});
            setNullableLong(statement, 1, command.actorUserId());
            statement.setString(2, actor.username());
            statement.setString(3, actor.userType());
            setNullableLong(statement, 4, actor.tenantId());
            statement.setString(5, bounded(command.operation(), 255, "UNKNOWN"));
            statement.setString(6, boundedNullable(command.resourceType(), 64));
            statement.setString(7, boundedNullable(command.resourceId(), 128));
            statement.setString(8, bounded(command.method(), 8, "UNKNOWN"));
            statement.setString(9, bounded(command.route(), 255, "/api/v1/console/unmatched"));
            statement.setString(10, safeJson(command.sanitizedRequest()));
            statement.setString(11, bounded(command.result(), 32, "SERVER_FAILURE"));
            statement.setInt(12, command.responseStatus());
            statement.setString(13, bounded(command.clientIp(), 45, "unknown"));
            statement.setString(14, validTrace(command.traceId()));
            statement.setLong(15, Math.max(0, command.latencyMs()));
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) {
            throw new IllegalStateException("operation audit key was not returned");
        }
        return id.longValue();
    }

    @Transactional(readOnly = true)
    public AuditPage search(Search search, Long scopedActorUserId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (scopedActorUserId != null) {
            where.append(" AND actor_user_id = ?");
            parameters.add(scopedActorUserId);
        }
        addLike(where, parameters, "actor_username", search.actor());
        addLike(where, parameters, "operation", search.operation());
        addEquals(where, parameters, "result_code", search.result());
        if (search.from() != null) {
            where.append(" AND occurred_at >= ?");
            parameters.add(Timestamp.from(search.from()));
        }
        if (search.to() != null) {
            where.append(" AND occurred_at <= ?");
            parameters.add(Timestamp.from(search.to()));
        }
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM privileged_operation_audits" + where,
                Long.class, parameters.toArray());
        int page = Math.max(0, search.page());
        int size = Math.min(100, Math.max(1, search.size()));
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<AuditEntry> items = jdbc.query("""
                        SELECT id, actor_username, tenant_id, operation, resource_type, resource_id,
                               result_code, client_ip, trace_id, latency_ms, sanitized_request, occurred_at
                        FROM privileged_operation_audits
                        """ + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (row, index) -> new AuditEntry(
                        row.getLong("id"), row.getString("actor_username"),
                        nullableLong(row, "tenant_id"), row.getString("operation"),
                        resource(row.getString("resource_type"), row.getString("resource_id")),
                        row.getString("result_code"), row.getString("client_ip"),
                        row.getString("trace_id"), row.getLong("latency_ms"),
                        row.getString("sanitized_request"), row.getTimestamp("occurred_at").toInstant()),
                pageParameters.toArray());
        return new AuditPage(items, page, size, total);
    }

    private Actor actor(Long userId) {
        if (userId == null) {
            return new Actor("system", null, null);
        }
        List<Actor> actors = jdbc.query(
                "SELECT username, user_type, tenant_id FROM users WHERE id = ?",
                (row, index) -> new Actor(row.getString("username"), row.getString("user_type"),
                        nullableLong(row, "tenant_id")), userId);
        return actors.isEmpty() ? new Actor("user#" + userId, null, null) : actors.getFirst();
    }

    private static void addLike(StringBuilder where, List<Object> parameters,
                                String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" LIKE ?");
            parameters.add("%" + bounded(value.trim(), 64, "") + "%");
        }
    }

    private static void addEquals(StringBuilder where, List<Object> parameters,
                                  String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            parameters.add(bounded(value.trim(), 32, ""));
        }
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value)
            throws java.sql.SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT);
        else statement.setLong(index, value);
    }

    private static Long nullableLong(java.sql.ResultSet row, String column) throws java.sql.SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static String resource(String type, String id) {
        if (type == null) return id;
        return id == null ? type : type + ":" + id;
    }

    private static String bounded(String value, int max, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String boundedNullable(String value, int max) {
        return value == null || value.isBlank() ? null : bounded(value, max, null);
    }

    private static String safeJson(String value) {
        if (value == null || value.isBlank()) return "{}";
        String trimmed = value.trim();
        return trimmed.length() <= 4000 ? trimmed : "{\"summary\":\"[redacted]\"}";
    }

    private static String validTrace(String trace) {
        return trace != null && trace.matches("[A-Fa-f0-9]{32}") ? trace.toLowerCase() : null;
    }

    private static String terminalResult(String value) {
        if (!List.of("SUCCESS", "CLIENT_FAILURE", "SERVER_FAILURE", "DENIED").contains(value)) {
            throw new IllegalArgumentException("operation audit result is invalid");
        }
        return value;
    }

    private record Actor(String username, String userType, Long tenantId) { }

    public record AuditCommand(Long actorUserId, String operation, String resourceType,
                               String resourceId, String method, String route,
                               String sanitizedRequest, String result, int responseStatus,
                               String clientIp, String traceId, long latencyMs) { }

    public record Search(String actor, String operation, String result, Instant from, Instant to,
                         int page, int size) { }

    public record AuditEntry(long id, String actor, Long tenantId, String operation,
                             String resource, String result, String ipAddress, String traceId,
                             long latencyMs, String requestSummary, Instant occurredAt) { }

    public record AuditPage(List<AuditEntry> items, int page, int size, long totalElements) { }
}
