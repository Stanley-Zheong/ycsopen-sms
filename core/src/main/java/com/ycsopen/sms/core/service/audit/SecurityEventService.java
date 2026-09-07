package com.ycsopen.sms.core.service.audit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/** Durable, attributable, exact-idempotency handoff for Phase 06 security detections. */
@Service
public class SecurityEventService {

    public static final int BULK_EXPORT_THRESHOLD = 1000;
    private final JdbcTemplate jdbc;

    public SecurityEventService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long recordUnusualLogin(long actorUserId, Long tenantId, String sessionId,
                                   String clientIp, String traceId) {
        return emit(command("UNUSUAL_LOGIN", actorUserId, tenantId, "DETECTED", clientIp,
                traceId, "rule=SOURCE_IP_CHANGED", "login-session:" + sessionId));
    }

    @Transactional
    public long recordRepeatedLoginFailure(long actorUserId, Long tenantId, long loginHistoryId,
                                           String clientIp, String traceId) {
        return emit(command("REPEATED_LOGIN_FAILURE", actorUserId, tenantId, "DETECTED", clientIp,
                traceId, "rule=FAILED_LOGIN_THRESHOLD;count=5", "login-history:" + loginHistoryId));
    }

    @Transactional
    public OptionalLong recordBulkExport(long actorUserId, Long tenantId, long exportTaskId,
                                         long recordCount, SecurityEventResult result, String clientIp,
                                         String traceId) {
        if (recordCount < BULK_EXPORT_THRESHOLD) {
            return OptionalLong.empty();
        }
        String stableResult = Objects.requireNonNull(result, "result").name();
        return OptionalLong.of(emit(command("BULK_EXPORT", actorUserId, tenantId, stableResult,
                clientIp, traceId, "rule=BULK_EXPORT_THRESHOLD;minimum=1000;count=" + recordCount,
                "export-task:" + exportTaskId)));
    }

    long emit(EventCommand command) {
        try {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO security_events
                            (event_type, actor_user_id, tenant_id, result_code, client_ip, trace_id,
                             safe_summary, source_digest, dedup_key, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """, new String[] {"id"});
                statement.setString(1, command.eventType());
                statement.setLong(2, command.actorUserId());
                setNullableLong(statement, 3, command.tenantId());
                statement.setString(4, command.result());
                statement.setString(5, command.clientIp());
                statement.setString(6, command.traceId());
                statement.setString(7, command.safeSummary());
                statement.setString(8, command.sourceDigest());
                statement.setString(9, command.dedupKey());
                return statement;
            }, keys);
            Number id = keys.getKey();
            if (id == null) throw new IllegalStateException("security event key was not returned");
            return id.longValue();
        } catch (DuplicateKeyException duplicate) {
            return exactExisting(command);
        }
    }

    @Transactional(readOnly = true)
    public SecurityEventPage search(Search search, Long scopedActorUserId) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> parameters = new ArrayList<>();
        if (scopedActorUserId != null) {
            where.append(" AND e.actor_user_id = ?");
            parameters.add(scopedActorUserId);
        }
        addEquals(where, parameters, "e.event_type", search.eventType());
        if (search.actor() != null && !search.actor().isBlank()) {
            where.append(" AND (u.username LIKE ? OR CAST(e.actor_user_id AS CHAR) = ?)");
            String actor = bounded(search.actor().trim(), 64);
            parameters.add("%" + actor + "%");
            parameters.add(actor);
        }
        addEquals(where, parameters, "e.result_code", search.result());
        if (search.from() != null) {
            where.append(" AND e.occurred_at >= ?");
            parameters.add(Timestamp.from(search.from()));
        }
        if (search.to() != null) {
            where.append(" AND e.occurred_at <= ?");
            parameters.add(Timestamp.from(search.to()));
        }
        String joins = " FROM security_events e LEFT JOIN users u ON u.id = e.actor_user_id";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + joins + where,
                Long.class, parameters.toArray());
        int page = Math.max(0, search.page());
        int size = Math.min(100, Math.max(1, search.size()));
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(size);
        pageParameters.add((long) page * size);
        List<SecurityEvent> items = jdbc.query("""
                        SELECT e.id, e.event_type, e.actor_user_id, u.username, e.tenant_id,
                               e.result_code, e.client_ip, e.trace_id, e.safe_summary, e.occurred_at
                        """ + joins + where + " ORDER BY e.id DESC LIMIT ? OFFSET ?",
                (row, index) -> {
                    long actorId = row.getLong("actor_user_id");
                    String username = row.getString("username");
                    return new SecurityEvent(row.getLong("id"), row.getString("event_type"),
                            username == null ? "user#" + actorId : username,
                            nullableLong(row, "tenant_id"), row.getString("result_code"),
                            row.getString("client_ip"), row.getString("trace_id"),
                            row.getString("safe_summary"), row.getTimestamp("occurred_at").toInstant());
                }, pageParameters.toArray());
        return new SecurityEventPage(items, page, size, total);
    }

    private long exactExisting(EventCommand expected) {
        List<StoredEvent> rows = jdbc.query("""
                        SELECT id, event_type, actor_user_id, tenant_id, result_code, client_ip,
                               trace_id, safe_summary, source_digest, dedup_key
                        FROM security_events WHERE dedup_key = ?
                        """,
                (row, index) -> new StoredEvent(row.getLong("id"), row.getString("event_type"),
                        row.getLong("actor_user_id"), nullableLong(row, "tenant_id"),
                        row.getString("result_code"), row.getString("client_ip"),
                        row.getString("trace_id"), row.getString("safe_summary"),
                        row.getString("source_digest"), row.getString("dedup_key")),
                expected.dedupKey());
        if (rows.size() != 1 || !rows.getFirst().sameAs(expected)) {
            throw new IllegalStateException("security event deduplication conflict");
        }
        return rows.getFirst().id();
    }

    private static EventCommand command(String type, long actor, Long tenant, String result,
                                        String ip, String trace, String summary, String source) {
        String digest = digest(source);
        return new EventCommand(type, actor, tenant, normalizeCode(result), boundedNullable(ip, 45),
                validTrace(trace), bounded(summary, 255), digest, type + ":" + digest);
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java 21 must provide SHA-256", impossible);
        }
    }

    private static String normalizeCode(String value) {
        if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,31}")) {
            throw new IllegalArgumentException("result code is invalid");
        }
        return value;
    }

    private static void addEquals(StringBuilder where, List<Object> parameters,
                                  String column, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            parameters.add(normalizeCode(value.trim()));
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

    private static String bounded(String value, int max) {
        Objects.requireNonNull(value, "value");
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String boundedNullable(String value, int max) {
        return value == null || value.isBlank() ? null : bounded(value, max);
    }

    private static String validTrace(String trace) {
        return trace != null && trace.matches("[A-Fa-f0-9]{32}") ? trace.toLowerCase() : null;
    }

    private record EventCommand(String eventType, long actorUserId, Long tenantId, String result,
                                String clientIp, String traceId, String safeSummary,
                                String sourceDigest, String dedupKey) { }

    private record StoredEvent(long id, String eventType, long actorUserId, Long tenantId,
                               String result, String clientIp, String traceId, String safeSummary,
                               String sourceDigest, String dedupKey) {
        boolean sameAs(EventCommand expected) {
            return eventType.equals(expected.eventType()) && actorUserId == expected.actorUserId()
                    && Objects.equals(tenantId, expected.tenantId())
                    && result.equals(expected.result())
                    && Objects.equals(clientIp, expected.clientIp())
                    && Objects.equals(traceId, expected.traceId())
                    && safeSummary.equals(expected.safeSummary())
                    && sourceDigest.equals(expected.sourceDigest())
                    && dedupKey.equals(expected.dedupKey());
        }
    }

    public record Search(String eventType, String actor, String result, Instant from, Instant to,
                         int page, int size) { }

    public record SecurityEvent(long id, String eventType, String actor, Long tenantId,
                                String result, String ipAddress, String traceId, String summary,
                                Instant detectedAt) { }

    public record SecurityEventPage(List<SecurityEvent> items, int page, int size,
                                    long totalElements) { }

    public enum SecurityEventResult { DETECTED, BLOCKED, SUCCESS, FAILURE }
}
