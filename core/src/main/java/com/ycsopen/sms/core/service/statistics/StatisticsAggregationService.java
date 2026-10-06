package com.ycsopen.sms.core.service.statistics;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class StatisticsAggregationService {
    private static final List<String> METRIC_CODES = List.of("RESOURCE_USAGE", "CHANNEL_DELIVERY", "TENANT_BEHAVIOR");
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PIPELINE_CODE = "STATISTICS_AGGREGATION";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public StatisticsAggregationService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    StatisticsAggregationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<MetricRow> metrics() {
        return jdbc.query("""
                SELECT metric_code, metric_name, source_tables, formula, freshness_rule, permission_scope,
                       formula_version, status, updated_at
                  FROM statistics_metric_registry
                 ORDER BY metric_code
                """, (rs, row) -> metric(rs));
    }

    @Transactional
    public RebuildResult rebuild(LocalDateTime startInclusive, LocalDateTime endExclusive, String actor) {
        lockPipeline();
        LocalDateTime utcNow = utcNow();
        LocalDateTime start = startInclusive == null ? utcNow.minusDays(1).truncatedTo(ChronoUnit.HOURS)
                : startInclusive.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime end = endExclusive == null ? utcNow.plusHours(1).truncatedTo(ChronoUnit.HOURS)
                : endExclusive.truncatedTo(ChronoUnit.HOURS);
        if (!end.isAfter(start)) {
            throw new BusinessException("STATISTICS_REBUILD_PERIOD_INVALID", "统计重建时间范围不合法");
        }
        return rebuildRange(start, end, actor(actor), null, utcNow);
    }

    /** Discovers every changed source date and atomically advances the scan watermark after replacement. */
    @Transactional
    public RefreshResult refreshAutomatically() {
        LocalDateTime scannedThrough = lockPipeline();
        LocalDateTime scanEnd = later(scannedThrough, utcNow());
        LocalDateTime scanStart = scannedThrough.minusMinutes(1);
        if (scanStart.isBefore(LocalDateTime.of(1970, 1, 1, 0, 0))) {
            scanStart = LocalDateTime.of(1970, 1, 1, 0, 0);
        }
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>(changedBusinessDates(scanStart, scanEnd));
        dates.add(businessDate(scanEnd));
        List<LocalDate> ordered = dates.stream().sorted().toList();
        int aggregateRows = 0;
        for (LocalDate date : ordered) {
            SourceWindow window = sourceWindow(date);
            RebuildResult result = rebuildRange(window.start(), window.end(), "statistics-scheduler", date, scanEnd);
            aggregateRows += result.aggregateRows();
        }
        jdbc.update("""
                UPDATE statistics_refresh_state
                   SET scanned_through=?, updated_at=CURRENT_TIMESTAMP
                 WHERE pipeline_code=?
                """, scanEnd, PIPELINE_CODE);
        return new RefreshResult(scannedThrough, scanEnd, ordered, aggregateRows);
    }

    private RebuildResult rebuildRange(LocalDateTime start, LocalDateTime end, String actor,
                                       LocalDate businessDate, LocalDateTime refreshedAt) {
        if (businessDate == null) {
            jdbc.update("""
                    DELETE FROM statistics_aggregates
                     WHERE metric_code IN ('RESOURCE_USAGE','CHANNEL_DELIVERY','TENANT_BEHAVIOR')
                       AND bucket_start>=? AND bucket_start<?
                    """, businessTime(start), businessTime(end));
        } else {
            jdbc.update("""
                    DELETE FROM statistics_aggregates
                     WHERE metric_code IN ('RESOURCE_USAGE','CHANNEL_DELIVERY','TENANT_BEHAVIOR')
                       AND bucket_date=?
                    """, businessDate);
        }

        Map<String, MutableAggregate> aggregates = new LinkedHashMap<>();
        List<SourceTask> tasks = sourceTasks(start, end);
        List<RejectedSubmit> rejected = rejectedSubmits(start, end);
        for (SourceTask task : tasks) {
            applyTask(aggregates, task);
        }
        for (RejectedSubmit submit : rejected) {
            applyRejectedSubmit(aggregates, submit);
        }
        for (MutableAggregate aggregate : aggregates.values()) {
            insertAggregate(aggregate.freeze());
        }
        if (businessDate != null) {
            upsertCheckpoint(businessDate, start, end, sourceChangedAt(start, end), refreshedAt,
                    tasks.size() + rejected.size(), aggregates.size());
        }
        return new RebuildResult(start, end, aggregates.size(), actor);
    }

    @Transactional(readOnly = true)
    public List<AggregateRow> aggregates(AggregateFilter filter) {
        AggregateFilter checked = filter == null
                ? new AggregateFilter(null, null, null, null, null, null, null)
                : filter;
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM statistics_aggregates WHERE 1=1
                """);
        if (text(checked.metricCode()) != null) {
            sql.append(" AND metric_code=?");
            params.add(checked.metricCode().trim().toUpperCase(Locale.ROOT));
        }
        if (checked.tenantId() != null) {
            sql.append(" AND tenant_id=?");
            params.add(checked.tenantId());
        }
        if (checked.channelId() != null) {
            sql.append(" AND channel_id=?");
            params.add(checked.channelId());
        }
        if (checked.signatureId() != null) {
            sql.append(" AND signature_id=?");
            params.add(checked.signatureId());
        }
        if (checked.templateId() != null) {
            sql.append(" AND template_id=?");
            params.add(checked.templateId());
        }
        if (checked.startTime() != null) {
            sql.append(" AND bucket_start>=?");
            params.add(checked.startTime());
        }
        if (checked.endTime() != null) {
            sql.append(" AND bucket_start<?");
            params.add(checked.endTime());
        }
        sql.append(" ORDER BY bucket_start DESC, metric_code, tenant_id LIMIT 500");
        return jdbc.query(sql.toString(), (rs, row) -> aggregate(rs), params.toArray());
    }

    @Transactional
    public CorrectionEventRow recordCorrection(CorrectionCommand command) {
        CorrectionCommand checked = command.checked();
        String identity = sha256Hex(checked.sourceTable() + ":" + checked.sourceId() + ":"
                + checked.metricCode() + ":" + checked.currentState());
        try {
            jdbc.update("""
                    INSERT INTO statistics_correction_events(source_table, source_id, metric_code, previous_state,
                        current_state, correction_identity)
                    VALUES (?,?,?,?,?,?)
                    """, checked.sourceTable(), checked.sourceId(), checked.metricCode(), checked.previousState(),
                    checked.currentState(), identity);
        } catch (DuplicateKeyException ignored) {
            jdbc.update("""
                    UPDATE statistics_correction_events
                       SET current_state=?
                     WHERE correction_identity=?
                    """, checked.currentState(), identity);
        }
        return jdbc.queryForObject("""
                SELECT id, source_table, source_id, metric_code, previous_state, current_state,
                       correction_identity, occurred_at
                  FROM statistics_correction_events
                 WHERE correction_identity=?
                """, (rs, row) -> correction(rs), identity);
    }

    private void applyTask(Map<String, MutableAggregate> aggregates, SourceTask task) {
        LocalDateTime bucket = businessTime(task.createdAt()).truncatedTo(ChronoUnit.HOURS);
        String messageType = task.messageType() == null ? "UNKNOWN" : task.messageType();
        long latency = latencyMs(task);
        boolean success = List.of("SENT", "DELIVERED").contains(task.sendStatus());
        boolean failure = "FAILED".equals(task.sendStatus());
        MutableAggregate channel = aggregate(aggregates, "CHANNEL_DELIVERY", bucket, task.tenantId(), task.channelId(),
                task.carrier(), messageType, task.province(), task.city(), task.signatureId(), task.templateId());
        channel.recordTask(success, failure, task.cost(), latency, task.sourceVersion());
        MutableAggregate tenant = aggregate(aggregates, "TENANT_BEHAVIOR", bucket, task.tenantId(), null,
                null, messageType, null, null, null, null);
        tenant.recordTask(success, failure, task.cost(), latency, task.sourceVersion());
        MutableAggregate resource = aggregate(aggregates, "RESOURCE_USAGE", bucket, task.tenantId(), null,
                null, messageType, null, null, task.signatureId(), task.templateId());
        resource.recordTask(success, failure, task.cost(), latency, task.sourceVersion());
    }

    private void applyRejectedSubmit(Map<String, MutableAggregate> aggregates, RejectedSubmit submit) {
        LocalDateTime bucket = businessTime(submit.createdAt()).truncatedTo(ChronoUnit.HOURS);
        MutableAggregate tenant = aggregate(aggregates, "TENANT_BEHAVIOR", bucket, submit.tenantId(), null,
                null, submit.messageType(), null, null, null, null);
        tenant.recordRejected(submit.sourceVersion());
        MutableAggregate resource = aggregate(aggregates, "RESOURCE_USAGE", bucket, submit.tenantId(), null,
                null, submit.messageType(), null, null, submit.signatureId(), submit.templateId());
        resource.recordRejected(submit.sourceVersion());
    }

    private MutableAggregate aggregate(Map<String, MutableAggregate> aggregates, String metricCode, LocalDateTime bucket,
                                       long tenantId, Long channelId, String carrier, String messageType,
                                       String province, String city, Long signatureId, Long templateId) {
        String key = String.join("|", metricCode, bucket.toString(), String.valueOf(tenantId),
                value(channelId), value(carrier), value(messageType), value(province), value(city),
                value(signatureId), value(templateId));
        return aggregates.computeIfAbsent(key, ignored -> new MutableAggregate(metricCode, bucket, tenantId,
                channelId, carrier, messageType, province, city, signatureId, templateId));
    }

    private void insertAggregate(AggregateRow row) {
        jdbc.update("""
                INSERT INTO statistics_aggregates(metric_code, bucket_grain, bucket_start, bucket_date, tenant_id,
                    channel_id, carrier, message_type, province, city, signature_id, template_id, submit_count,
                    accepted_count, rejected_count, send_count, success_count, failure_count, fee_amount,
                    avg_response_ms, source_version, correction_identity, drilldown_key, formula_version,
                    freshness_at, quality_state)
                VALUES (?, 'HOUR', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'v1',
                    CURRENT_TIMESTAMP, ?)
                """, row.metricCode(), row.bucketStart(), row.bucketDate(), row.tenantId(), row.channelId(),
                row.carrier(), row.messageType(), row.province(), row.city(), row.signatureId(), row.templateId(),
                row.submitCount(), row.acceptedCount(), row.rejectedCount(), row.sendCount(), row.successCount(),
                row.failureCount(), row.feeAmount(), row.avgResponseMs(), row.sourceVersion(),
                row.correctionIdentity(), row.drilldownKey(), row.qualityState());
    }

    private List<SourceTask> sourceTasks(LocalDateTime start, LocalDateTime end) {
        return jdbc.query("""
                SELECT t.id, t.tenant_id, t.channel_id, t.operator AS carrier,
                       COALESCE(ms.product_type, 'UNKNOWN') AS message_type,
                       t.province, t.city, t.signature_id, t.template_id,
                       COALESCE((
                           SELECT r.report_status
                             FROM delivery_reports r
                            WHERE r.message_id=t.message_id
                            ORDER BY r.report_time DESC, r.id DESC
                            LIMIT 1
                       ), t.send_status) AS send_status,
                       COALESCE((
                           SELECT CAST(SUM(b.amount) AS DECIMAL(18,4)) / 1000
                             FROM billing_records b
                            WHERE b.task_ref_id=t.id AND b.billing_status='CONFIRMED'
                       ), t.cost) AS cost,
                       t.send_time, t.deliver_time, t.created_at, t.updated_at,
                       (COALESCE(t.version, 1)
                        + COALESCE((SELECT MAX(r.id) FROM delivery_reports r WHERE r.message_id=t.message_id), 0)
                        + COALESCE((SELECT MAX(b.id) FROM billing_records b WHERE b.task_ref_id=t.id), 0)) AS version
                  FROM message_tasks t
                  LEFT JOIN message_submits ms ON ms.id=t.submit_id
                 WHERE t.created_at>=? AND t.created_at<?
                """, (rs, row) -> sourceTask(rs), start, end);
    }

    private List<RejectedSubmit> rejectedSubmits(LocalDateTime start, LocalDateTime end) {
        return jdbc.query("""
                SELECT id, tenant_id, product_type, signature_id, template_id, created_at
                  FROM message_submits
                 WHERE status='REJECTED' AND created_at>=? AND created_at<?
                   AND NOT EXISTS (SELECT 1 FROM message_tasks t WHERE t.submit_id=message_submits.id)
                """, (rs, row) -> rejectedSubmit(rs), start, end);
    }

    private LocalDateTime lockPipeline() {
        LocalDateTime scannedThrough = jdbc.query("""
                SELECT scanned_through
                  FROM statistics_refresh_state
                 WHERE pipeline_code=?
                 FOR UPDATE
                """, rs -> rs.next() ? rs.getObject(1, LocalDateTime.class) : null, PIPELINE_CODE);
        if (scannedThrough == null) {
            throw new IllegalStateException("STATISTICS_REFRESH_STATE_MISSING");
        }
        return scannedThrough;
    }

    private List<LocalDate> changedBusinessDates(LocalDateTime start, LocalDateTime end) {
        LinkedHashSet<LocalDate> dates = new LinkedHashSet<>();
        collectDates(dates, """
                SELECT created_at FROM message_tasks
                 WHERE updated_at>? AND updated_at<=?
                """, start, end);
        collectDates(dates, """
                SELECT t.created_at
                  FROM delivery_reports r
                  JOIN message_tasks t ON t.message_id=r.message_id
                 WHERE r.report_time>? AND r.report_time<=?
                """, start, end);
        collectDates(dates, """
                SELECT t.created_at
                  FROM billing_records b
                  JOIN message_tasks t ON t.id=b.task_ref_id
                 WHERE b.created_at>? AND b.created_at<=?
                """, start, end);
        collectDates(dates, """
                SELECT created_at FROM message_submits
                 WHERE status='REJECTED' AND updated_at>? AND updated_at<=?
                """, start, end);
        return List.copyOf(dates);
    }

    private void collectDates(LinkedHashSet<LocalDate> dates, String sql,
                              LocalDateTime start, LocalDateTime end) {
        jdbc.query(sql, rs -> {
            while (rs.next()) {
                LocalDateTime sourceTime = rs.getObject(1, LocalDateTime.class);
                if (sourceTime != null) {
                    dates.add(businessDate(sourceTime));
                }
            }
            return null;
        }, start, end);
    }

    private LocalDateTime sourceChangedAt(LocalDateTime start, LocalDateTime end) {
        LocalDateTime latest = null;
        latest = later(latest, timestampValue("""
                SELECT MAX(updated_at) FROM message_tasks
                 WHERE created_at>=? AND created_at<?
                """, start, end));
        latest = later(latest, timestampValue("""
                SELECT MAX(r.report_time)
                  FROM delivery_reports r
                  JOIN message_tasks t ON t.message_id=r.message_id
                 WHERE t.created_at>=? AND t.created_at<?
                """, start, end));
        latest = later(latest, timestampValue("""
                SELECT MAX(b.created_at)
                  FROM billing_records b
                  JOIN message_tasks t ON t.id=b.task_ref_id
                 WHERE t.created_at>=? AND t.created_at<?
                """, start, end));
        return later(latest, timestampValue("""
                SELECT MAX(updated_at) FROM message_submits
                 WHERE status='REJECTED' AND created_at>=? AND created_at<?
                """, start, end));
    }

    private LocalDateTime timestampValue(String sql, Object... args) {
        return jdbc.queryForObject(sql, LocalDateTime.class, args);
    }

    private void upsertCheckpoint(LocalDate businessDate, LocalDateTime start, LocalDateTime end,
                                  LocalDateTime sourceChangedAt, LocalDateTime refreshedAt,
                                  int sourceRecords, int aggregateRows) {
        int updated = jdbc.update("""
                UPDATE statistics_refresh_checkpoints
                   SET source_window_start=?, source_window_end=?, source_changed_at=?, refreshed_at=?,
                       source_record_count=?, aggregate_row_count=?, refresh_status='SUCCESS',
                       updated_at=CURRENT_TIMESTAMP
                 WHERE business_date=?
                """, start, end, sourceChangedAt, refreshedAt, sourceRecords, aggregateRows, businessDate);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO statistics_refresh_checkpoints(
                        business_date, source_window_start, source_window_end, source_changed_at,
                        refreshed_at, source_record_count, aggregate_row_count, refresh_status)
                    VALUES (?,?,?,?,?,?,?,'SUCCESS')
                    """, businessDate, start, end, sourceChangedAt, refreshedAt, sourceRecords, aggregateRows);
        }
    }

    private static LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        return left == null || right != null && right.isAfter(left) ? right : left;
    }

    public static LocalDate businessDate(LocalDateTime utcSourceTime) {
        return businessTime(utcSourceTime).toLocalDate();
    }

    public static LocalDateTime businessTime(LocalDateTime utcSourceTime) {
        return LocalDateTime.ofInstant(utcSourceTime.toInstant(ZoneOffset.UTC), BUSINESS_ZONE);
    }

    public static SourceWindow sourceWindow(LocalDate date) {
        Instant start = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return new SourceWindow(LocalDateTime.ofInstant(start, ZoneOffset.UTC),
                LocalDateTime.ofInstant(end, ZoneOffset.UTC));
    }

    private LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static long latencyMs(SourceTask task) {
        if (task.sendTime() == null || task.deliverTime() == null || task.deliverTime().isBefore(task.sendTime())) {
            return 0;
        }
        return ChronoUnit.MILLIS.between(task.sendTime(), task.deliverTime());
    }

    private static SourceTask sourceTask(ResultSet rs) throws SQLException {
        return new SourceTask(rs.getLong("id"), rs.getLong("tenant_id"), nullableLong(rs, "channel_id"),
                rs.getString("carrier"), rs.getString("message_type"), rs.getString("province"), rs.getString("city"),
                nullableLong(rs, "signature_id"), nullableLong(rs, "template_id"), rs.getString("send_status"),
                rs.getBigDecimal("cost"), rs.getObject("send_time", LocalDateTime.class),
                rs.getObject("deliver_time", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class), rs.getLong("version"));
    }

    private static RejectedSubmit rejectedSubmit(ResultSet rs) throws SQLException {
        return new RejectedSubmit(rs.getLong("id"), rs.getLong("tenant_id"), rs.getString("product_type"),
                nullableLong(rs, "signature_id"), nullableLong(rs, "template_id"),
                rs.getObject("created_at", LocalDateTime.class));
    }

    private static MetricRow metric(ResultSet rs) throws SQLException {
        return new MetricRow(rs.getString("metric_code"), rs.getString("metric_name"),
                rs.getString("source_tables"), rs.getString("formula"), rs.getString("freshness_rule"),
                rs.getString("permission_scope"), rs.getString("formula_version"), rs.getString("status"),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    private static AggregateRow aggregate(ResultSet rs) throws SQLException {
        return new AggregateRow(rs.getString("metric_code"), rs.getObject("bucket_start", LocalDateTime.class),
                rs.getDate("bucket_date").toLocalDate(), rs.getLong("tenant_id"), nullableLong(rs, "channel_id"),
                rs.getString("carrier"), rs.getString("message_type"), rs.getString("province"), rs.getString("city"),
                nullableLong(rs, "signature_id"), nullableLong(rs, "template_id"), rs.getInt("submit_count"),
                rs.getInt("accepted_count"), rs.getInt("rejected_count"), rs.getInt("send_count"),
                rs.getInt("success_count"), rs.getInt("failure_count"), rs.getBigDecimal("fee_amount"),
                rs.getLong("avg_response_ms"), rs.getLong("source_version"), rs.getString("correction_identity"),
                rs.getString("drilldown_key"), rs.getString("quality_state"),
                rs.getObject("freshness_at", LocalDateTime.class));
    }

    private static CorrectionEventRow correction(ResultSet rs) throws SQLException {
        return new CorrectionEventRow(rs.getLong("id"), rs.getString("source_table"), rs.getString("source_id"),
                rs.getString("metric_code"), rs.getString("previous_state"), rs.getString("current_state"),
                rs.getString("correction_identity"), rs.getObject("occurred_at", LocalDateTime.class));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String actor(String actor) {
        return text(actor) == null ? "system" : actor.trim();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("STATISTICS_HASH_UNAVAILABLE", ex);
        }
    }

    private static final class MutableAggregate {
        private final String metricCode;
        private final LocalDateTime bucketStart;
        private final long tenantId;
        private final Long channelId;
        private final String carrier;
        private final String messageType;
        private final String province;
        private final String city;
        private final Long signatureId;
        private final Long templateId;
        private int submitCount;
        private int acceptedCount;
        private int rejectedCount;
        private int sendCount;
        private int successCount;
        private int failureCount;
        private BigDecimal feeAmount = BigDecimal.ZERO;
        private long latencyTotal;
        private int latencySamples;
        private long sourceVersion;
        private boolean corrected;

        private MutableAggregate(String metricCode, LocalDateTime bucketStart, long tenantId, Long channelId,
                                 String carrier, String messageType, String province, String city,
                                 Long signatureId, Long templateId) {
            this.metricCode = metricCode;
            this.bucketStart = bucketStart;
            this.tenantId = tenantId;
            this.channelId = channelId;
            this.carrier = carrier;
            this.messageType = messageType;
            this.province = province;
            this.city = city;
            this.signatureId = signatureId;
            this.templateId = templateId;
        }

        private void recordTask(boolean success, boolean failure, BigDecimal cost, long latency, long version) {
            submitCount += 1;
            acceptedCount += 1;
            sendCount += 1;
            successCount += success ? 1 : 0;
            failureCount += failure ? 1 : 0;
            feeAmount = feeAmount.add(cost == null ? BigDecimal.ZERO : cost);
            if (latency > 0) {
                latencyTotal += latency;
                latencySamples += 1;
            }
            sourceVersion = Math.max(sourceVersion, version);
            corrected |= version > 1;
        }

        private void recordRejected(long version) {
            submitCount += 1;
            rejectedCount += 1;
            sourceVersion = Math.max(sourceVersion, version);
        }

        private AggregateRow freeze() {
            long avg = latencySamples == 0 ? 0 : latencyTotal / latencySamples;
            String drilldown = String.join("|", metricCode, bucketStart.toString(), String.valueOf(tenantId),
                    value(channelId), value(signatureId), value(templateId), value(carrier), value(messageType),
                    value(province), value(city));
            String identity = sha256Hex(drilldown + ":" + submitCount + ":" + acceptedCount + ":" + rejectedCount
                    + ":" + sendCount + ":" + successCount + ":" + failureCount + ":" + feeAmount + ":" + sourceVersion);
            return new AggregateRow(metricCode, bucketStart, bucketStart.toLocalDate(), tenantId, channelId,
                    carrier, messageType, province, city, signatureId, templateId, submitCount, acceptedCount,
                    rejectedCount, sendCount, successCount, failureCount, feeAmount, avg, sourceVersion, identity,
                    drilldown, corrected ? "CORRECTED" : "FRESH", LocalDateTime.now());
        }
    }

    private record SourceTask(long id, long tenantId, Long channelId, String carrier, String messageType,
                              String province, String city, Long signatureId, Long templateId, String sendStatus,
                              BigDecimal cost, LocalDateTime sendTime, LocalDateTime deliverTime,
                              LocalDateTime createdAt, LocalDateTime updatedAt, long sourceVersion) { }

    private record RejectedSubmit(long id, long tenantId, String messageType, Long signatureId, Long templateId,
                                  LocalDateTime createdAt) {
        long sourceVersion() {
            return id;
        }
    }

    public record AggregateFilter(String metricCode, Long tenantId, Long channelId, Long signatureId, Long templateId,
                                  LocalDateTime startTime, LocalDateTime endTime) { }

    public record RebuildResult(LocalDateTime startTime, LocalDateTime endTime, int aggregateRows, String actor) { }

    public record RefreshResult(LocalDateTime previousWatermark, LocalDateTime scannedThrough,
                                List<LocalDate> businessDates, int aggregateRows) { }

    public record SourceWindow(LocalDateTime start, LocalDateTime end) { }

    public record MetricRow(String metricCode, String metricName, String sourceTables, String formula,
                            String freshnessRule, String permissionScope, String formulaVersion,
                            String status, LocalDateTime updatedAt) { }

    public record AggregateRow(String metricCode, LocalDateTime bucketStart, LocalDate bucketDate, long tenantId,
                               Long channelId, String carrier, String messageType, String province, String city,
                               Long signatureId, Long templateId, int submitCount, int acceptedCount,
                               int rejectedCount, int sendCount, int successCount, int failureCount,
                               BigDecimal feeAmount, long avgResponseMs, long sourceVersion,
                               String correctionIdentity, String drilldownKey, String qualityState,
                               LocalDateTime freshnessAt) { }

    public record CorrectionCommand(String sourceTable, String sourceId, String metricCode,
                                    String previousState, String currentState) {
        CorrectionCommand checked() {
            if (text(sourceTable) == null || text(sourceId) == null || text(metricCode) == null
                    || text(currentState) == null) {
                throw new BusinessException("STATISTICS_CORRECTION_INVALID", "统计修正事件字段不完整");
            }
            String metric = metricCode.trim().toUpperCase(Locale.ROOT);
            if (!METRIC_CODES.contains(metric)) {
                throw new BusinessException("STATISTICS_METRIC_INVALID", "统计指标不支持");
            }
            return new CorrectionCommand(sourceTable.trim(), sourceId.trim(), metric, text(previousState),
                    currentState.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record CorrectionEventRow(long id, String sourceTable, String sourceId, String metricCode,
                                     String previousState, String currentState, String correctionIdentity,
                                     LocalDateTime occurredAt) { }
}
