package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.service.dashboard.OperationalDashboardService;
import com.ycsopen.sms.core.service.message.MessageAcceptanceIdempotencyService;
import com.ycsopen.sms.core.service.message.MessageRejectionRecorder;
import com.ycsopen.sms.core.service.statistics.StatisticsAggregationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest(classes = Issue119StatisticsRefreshMySqlTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("phase01-integration")
@EnabledIfSystemProperty(named = "phase01.integration.enabled", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class Issue119StatisticsRefreshMySqlTest {
    private static Phase03ServiceHarness.ServiceSession mysql;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        mysql = Phase03ServiceHarness.startMySql();
        registry.add("spring.datasource.url", () -> "jdbc:mysql://" + mysql.host() + ":" + mysql.port()
                + "/phase01?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai");
        registry.add("spring.datasource.username", mysql::username);
        registry.add("spring.datasource.password", mysql::password);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET time_zone = '+00:00'");
        registry.add("spring.flyway.user", mysql::username);
        registry.add("spring.flyway.password", mysql::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("ycsopen.statistics.refresh.max-age", () -> "PT5M");
        registry.add("ycsopen.message.submit-claim-lease", () -> "PT2M");
    }

    @AfterAll
    static void stop() {
        if (mysql != null) {
            mysql.close();
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired StatisticsAggregationService statistics;
    @Autowired OperationalDashboardService dashboards;
    @Autowired MessageAcceptanceIdempotencyService idempotency;
    @Autowired MessageRejectionRecorder rejectionRecorder;

    @Test
    void utcDatetimesKeepLeaseFreshBucketShanghaiBoundaryAndUseRangeIndexes() throws Exception {
        assertThat(TimeZone.getDefault().toZoneId().getRules().getOffset(java.time.Instant.now()))
                .isEqualTo(ZoneOffset.UTC);
        assertThat(jdbc.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo("+00:00");

        var claim = idempotency.claim(91_119L, "ISSUE-119-MYSQL", "a".repeat(64));
        LocalDateTime leaseExpiry = jdbc.queryForObject("""
                SELECT lease_expires_at FROM message_submit_claim_leases WHERE submission_id=?
                """, LocalDateTime.class, claim.submissionId());
        LocalDateTime nowUtc = LocalDateTime.now(ZoneOffset.UTC);
        assertThat(leaseExpiry).isAfter(nowUtc.plusMinutes(1)).isBefore(nowUtc.plusMinutes(3));
        rejectionRecorder.record(claim, 91_119L, 91_120L, "ROUTING_REJECTED");
        assertThat(jdbc.queryForObject("SELECT status FROM message_submits WHERE id=?",
                String.class, claim.submissionId())).isEqualTo("REJECTED");

        insertPlannerHistory();
        insertTask(91_191L, "ISSUE119-BEFORE", LocalDateTime.of(2026, 1, 1, 15, 59, 59));
        insertTask(91_192L, "ISSUE119-AFTER", LocalDateTime.of(2026, 1, 1, 16, 0));
        LocalDateTime currentSourceTime = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1);
        insertTask(91_193L, "ISSUE119-TODAY", currentSourceTime);
        jdbc.update("""
                INSERT INTO delivery_reports(id,message_id,channel_id,report_status,report_time)
                VALUES (91193,'ISSUE119-TODAY',1,'DELIVERED',?)
                """, currentSourceTime);
        jdbc.update("""
                INSERT INTO billing_records(id,tenant_id,task_ref_id,channel_id,unit_price,quantity,amount,
                    billing_status,billing_date,created_at)
                VALUES (91193,91119,91193,1,0.0200,1,20,'CONFIRMED',?,?)
                """, currentSourceTime.toLocalDate(), currentSourceTime);
        for (String table : List.of("message_tasks", "delivery_reports", "billing_records", "message_submits")) {
            jdbc.execute("ANALYZE TABLE " + table);
        }

        statistics.refreshAutomatically();

        List<LocalDate> boundaryDates = jdbc.queryForList("""
                SELECT DISTINCT bucket_date FROM statistics_aggregates
                 WHERE metric_code='CHANNEL_DELIVERY' AND tenant_id=91119
                   AND bucket_date IN ('2026-01-01','2026-01-02')
                 ORDER BY bucket_date
                """, LocalDate.class);
        assertThat(boundaryDates).containsExactly(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        var dashboard = dashboards.platformDashboard();
        assertThat(dashboard.todayAggregation().state()).isEqualTo("FRESH");
        assertThat(dashboard.realtime().todayMessages()).isNotNull().isGreaterThanOrEqualTo(1);

        assertProductionDiscoveryIndexes(currentSourceTime.minusMinutes(2), currentSourceTime.plusMinutes(2));
        assertWaitsForPipelineLock(statistics::refreshAutomatically);
        assertWaitsForPipelineLock(() -> statistics.rebuild(
                currentSourceTime.minusHours(1), currentSourceTime.plusHours(1), "issue-119-mysql"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM statistics_aggregates
                 WHERE metric_code='CHANNEL_DELIVERY' AND tenant_id=91119
                   AND bucket_date IN ('2026-01-01','2026-01-02')
                """, Integer.class)).isEqualTo(2);
    }

    private void insertPlannerHistory() {
        LocalDateTime old = LocalDateTime.of(2020, 1, 1, 0, 0);
        List<Object[]> tasks = new ArrayList<>();
        List<Object[]> reports = new ArrayList<>();
        List<Object[]> billings = new ArrayList<>();
        List<Object[]> submits = new ArrayList<>();
        for (int index = 0; index < 400; index++) {
            long taskId = 920_000L + index;
            String messageId = "ISSUE119-PLAN-" + index;
            LocalDateTime timestamp = old.plusSeconds(index);
            tasks.add(new Object[]{taskId, messageId, new byte[]{1, 2}, String.format("%064d", index),
                    "planner history", timestamp, timestamp, timestamp, timestamp});
            reports.add(new Object[]{930_000L + index, messageId, timestamp});
            billings.add(new Object[]{940_000L + index, taskId, timestamp.toLocalDate(), timestamp});
            submits.add(new Object[]{950_000L + index, "ISSUE119-PLAN-SUBMIT-" + index, timestamp, timestamp});
        }
        jdbc.batchUpdate("""
                INSERT INTO message_tasks(
                    id,message_id,tenant_id,mobile_encrypted,mobile_hash,content,send_status,
                    channel_id,operator,province,city,cost,send_time,deliver_time,created_at,updated_at,version)
                VALUES (?,?,91119,?,?,?,'SENT',1,'MOBILE','上海','上海',0.0200,?,?,?, ?,1)
                """, tasks);
        jdbc.batchUpdate("""
                INSERT INTO delivery_reports(id,message_id,channel_id,report_status,report_time)
                VALUES (?,?,1,'DELIVERED',?)
                """, reports);
        jdbc.batchUpdate("""
                INSERT INTO billing_records(id,tenant_id,task_ref_id,channel_id,unit_price,quantity,amount,
                    billing_status,billing_date,created_at)
                VALUES (?,91119,?,1,0.0200,1,20,'CONFIRMED',?,?)
                """, billings);
        jdbc.batchUpdate("""
                INSERT INTO message_submits(id,submit_id,tenant_id,source_protocol,product_type,status,created_at,updated_at)
                VALUES (?,?,91119,'HTTP','NOTIFY','REJECTED',?,?)
                """, submits);
    }

    private void insertTask(long id, String messageId, LocalDateTime createdAt) {
        jdbc.update("""
                INSERT INTO message_tasks(
                    id, message_id, tenant_id, mobile_encrypted, mobile_hash, content, send_status,
                    channel_id, operator, province, city, cost, send_time, deliver_time,
                    created_at, updated_at, version)
                VALUES (?,?,91119,?,?,?,'SENT',1,'MOBILE','上海','上海',?,?,?, ?,CURRENT_TIMESTAMP,1)
                """, id, messageId, new byte[]{1, 2}, "b".repeat(64), "ISSUE119 " + messageId,
                new BigDecimal("0.0200"), createdAt, createdAt, createdAt);
    }

    private void assertProductionDiscoveryIndexes(LocalDateTime start, LocalDateTime end) {
        assertPlanUses("""
                EXPLAIN SELECT created_at FROM message_tasks
                 WHERE updated_at>? AND updated_at<=?
                """, "message_tasks", "idx_message_tasks_statistics_changed", start, end);
        assertPlanUses("""
                EXPLAIN SELECT t.created_at
                  FROM delivery_reports r JOIN message_tasks t ON t.message_id=r.message_id
                 WHERE r.report_time>? AND r.report_time<=?
                """, "r", "idx_delivery_reports_statistics_changed", start, end);
        assertPlanUses("""
                EXPLAIN SELECT t.created_at
                  FROM billing_records b JOIN message_tasks t ON t.id=b.task_ref_id
                 WHERE b.created_at>? AND b.created_at<=?
                """, "b", "idx_billing_records_statistics_changed", start, end);
        assertPlanUses("""
                EXPLAIN SELECT created_at FROM message_submits
                 WHERE status='REJECTED' AND updated_at>? AND updated_at<=?
                """, "message_submits", "idx_message_submits_statistics_refresh", start, end);
        assertPlanUses("""
                EXPLAIN SELECT MAX(updated_at) FROM message_tasks
                 WHERE created_at>=? AND created_at<?
                """, "message_tasks", "idx_message_tasks_statistics_bucket", start, end);
        assertPlanUses("""
                EXPLAIN SELECT MAX(updated_at) FROM message_submits
                 WHERE status='REJECTED' AND created_at>=? AND created_at<?
                """, "message_submits", "idx_message_submits_statistics_bucket", start, end);
    }

    private void assertPlanUses(String sql, String table, String index, Object... parameters) {
        var plan = jdbc.query(sql, (rs, row) -> new QueryPlan(
                rs.getString("table"), rs.getString("type"), rs.getString("key"), rs.getLong("rows")), parameters)
                .stream()
                .filter(row -> table.equals(row.table()))
                .findFirst()
                .orElseThrow();
        assertThat(plan.key()).isEqualTo(index);
        assertThat(plan.type()).isNotEqualToIgnoringCase("ALL");
        assertThat(plan.rows()).isPositive().isLessThan(400);
    }

    private void assertWaitsForPipelineLock(Callable<?> operation) throws Exception {
        try (Connection lockOwner = Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            lockOwner.setAutoCommit(false);
            try (var statement = lockOwner.prepareStatement("""
                    SELECT scanned_through FROM statistics_refresh_state
                     WHERE pipeline_code='STATISTICS_AGGREGATION' FOR UPDATE
                    """); var ignored = statement.executeQuery()) {
                assertThat(ignored.next()).isTrue();
            }
            try (var worker = Executors.newSingleThreadExecutor()) {
                CountDownLatch started = new CountDownLatch(1);
                var pending = worker.submit(() -> {
                    started.countDown();
                    return operation.call();
                });
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                boolean waited = false;
                try {
                    pending.get(300, TimeUnit.MILLISECONDS);
                } catch (TimeoutException expected) {
                    waited = true;
                } finally {
                    lockOwner.commit();
                }
                assertThat(waited).isTrue();
                assertThatCode(() -> pending.get(10, TimeUnit.SECONDS)).doesNotThrowAnyException();
            }
        }
    }

    private record QueryPlan(String table, String type, String key, long rows) { }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    @Import({StatisticsAggregationService.class, OperationalDashboardService.class,
            MessageAcceptanceIdempotencyService.class, MessageRejectionRecorder.class})
    static class Application { }
}
