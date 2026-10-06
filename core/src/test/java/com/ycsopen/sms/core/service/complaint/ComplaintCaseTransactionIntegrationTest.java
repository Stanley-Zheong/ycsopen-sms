package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class ComplaintCaseTransactionIntegrationTest {

    @Test
    void resourceTransactionRollsBackBeforeFailureEvidenceCommitsSeparately() {
        EmbeddedDatabase database = database("issue124-transaction-");
        JdbcTemplate jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE resource_tx_probe(id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO tenants(id, lifecycle_status, full_name, short_name) VALUES (7, 'SIGNED', '示例机构', '示例')");
        ComplaintCaseService.BlacklistPort rollbackOnlyPort = (tenantId, mobile, actor, reason) -> {
            jdbc.update("INSERT INTO resource_tx_probe(id) VALUES (1)");
            throw new IllegalStateException("provider timeout");
        };
        ComplaintCaseService service = new ComplaintCaseService(
                jdbc, rollbackOnlyPort, new DataSourceTransactionManager(database));
        long complaintId = service.create(new ComplaintCaseService.CreateCommand(
                "USER_REPORT", "用户投诉营销短信", 7L, null, null, null, null,
                "MARKETING", "13800138000", "PARTIAL", "联系用户"), "operator-a").id();
        service.accept(complaintId, new ComplaintCaseService.StateCommand(
                null, "受理", null, null, "operator-b"));
        service.handle(complaintId, new ComplaintCaseService.StateCommand(
                null, "投诉属实", "加入黑名单", "完成整改", "operator-b"));

        var failed = service.remediate(complaintId, new ComplaintCaseService.RemediationCommand(
                "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-rollback", "provider timeout"));

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM resource_tx_probe", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disposal_records WHERE complaint_id=?", Long.class,
                complaintId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='REMEDIATION_FAILED' AND related_disposal_id=?
                """, Long.class, complaintId, failed.id())).isEqualTo(1L);
    }

    @Test
    void remediationLockSerializesCloseAndKeepsTimelineOrder() throws Exception {
        EmbeddedDatabase database = database("issue124-close-race-");
        JdbcTemplate jdbc = new JdbcTemplate(database);
        seedTenant(jdbc);
        CountDownLatch actionEntered = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        CountDownLatch closeStarted = new CountDownLatch(1);
        ComplaintCaseService service = service(database, jdbc, (tenantId, mobile, actor, reason) -> {
            actionEntered.countDown();
            await(releaseAction);
            return 41L;
        });
        long complaintId = processedComplaint(service);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var remediation = executor.submit(() -> service.remediate(complaintId,
                    new ComplaintCaseService.RemediationCommand(
                            "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-race", "投诉集中")));
            assertThat(actionEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var close = executor.submit(() -> {
                closeStarted.countDown();
                return service.close(complaintId,
                        new ComplaintCaseService.StateCommand(null, "整改复核通过", null, null, "operator-c"));
            });

            assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(150);
            assertThat(close).isNotDone();
            releaseAction.countDown();

            assertThat(remediation.get(5, TimeUnit.SECONDS).status()).isEqualTo("APPLIED");
            assertThat(close.get(5, TimeUnit.SECONDS).status()).isEqualTo("CLOSED");
            assertThat(service.caseDetail(complaintId).timeline())
                    .extracting(ComplaintCaseService.CaseEventRow::eventType)
                    .containsExactly("REGISTERED", "ACCEPTED", "HANDLED", "REMEDIATION_APPLIED", "CLOSED");
        } finally {
            releaseAction.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void newerFailedRemediationMakesConcurrentRecoveryOfOlderFailureStale() throws Exception {
        EmbeddedDatabase database = database("issue124-recovery-race-");
        JdbcTemplate jdbc = new JdbcTemplate(database);
        seedTenant(jdbc);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch secondActionEntered = new CountDownLatch(1);
        CountDownLatch releaseSecondAction = new CountDownLatch(1);
        CountDownLatch recoveryStarted = new CountDownLatch(1);
        ComplaintCaseService service = service(database, jdbc, (tenantId, mobile, actor, reason) -> {
            if (attempts.incrementAndGet() == 2) {
                secondActionEntered.countDown();
                await(releaseSecondAction);
            }
            throw new IllegalStateException("provider timeout");
        });
        long complaintId = processedComplaint(service);
        var olderFailure = service.remediate(complaintId, new ComplaintCaseService.RemediationCommand(
                "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-old", "first failure"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var newerRemediation = executor.submit(() -> service.remediate(complaintId,
                    new ComplaintCaseService.RemediationCommand(
                            "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-new", "second failure")));
            assertThat(secondActionEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var recovery = executor.submit(() -> {
                recoveryStarted.countDown();
                return service.recover(complaintId,
                        new ComplaintCaseService.RecoveryCommand(
                                olderFailure.id(), "review-recovery", "operator-c", "manual compensation"));
            });

            assertThat(recoveryStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(150);
            assertThat(recovery).isNotDone();
            releaseSecondAction.countDown();
            var newerFailure = newerRemediation.get(5, TimeUnit.SECONDS);
            assertThat(newerFailure.status()).isEqualTo("FAILED");
            try {
                recovery.get(5, TimeUnit.SECONDS);
                fail("older recovery must be rejected after a newer failure");
            } catch (ExecutionException ex) {
                assertThat(ex.getCause()).isInstanceOf(BusinessException.class)
                        .extracting("errorCode").isEqualTo("COMPLAINT_STATE_STALE");
            }
            assertThat(service.caseDetail(complaintId).remediations().get(0).id()).isEqualTo(newerFailure.id());
            assertThat(service.caseDetail(complaintId).timeline())
                    .extracting(ComplaintCaseService.CaseEventRow::eventType)
                    .containsExactly("REGISTERED", "ACCEPTED", "HANDLED",
                            "REMEDIATION_FAILED", "REMEDIATION_FAILED");
        } finally {
            releaseSecondAction.countDown();
            executor.shutdownNow();
        }
    }

    private static EmbeddedDatabase database(String prefix) {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName(prefix + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
                .addScript("db/migration/V5000__complaint_case_management.sql")
                .addScript("db/migration/V6600__complaint_case_events.sql")
                .build();
    }

    private static ComplaintCaseService service(EmbeddedDatabase database, JdbcTemplate jdbc,
                                                ComplaintCaseService.BlacklistPort port) {
        return new ComplaintCaseService(jdbc, port, new DataSourceTransactionManager(database));
    }

    private static void seedTenant(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO tenants(id, lifecycle_status, full_name, short_name) VALUES (7, 'SIGNED', '示例机构', '示例')");
    }

    private static long processedComplaint(ComplaintCaseService service) {
        long complaintId = service.create(new ComplaintCaseService.CreateCommand(
                "USER_REPORT", "用户投诉营销短信", 7L, null, null, null, null,
                "MARKETING", "13800138000", null, "联系用户"), "operator-a").id();
        service.accept(complaintId, new ComplaintCaseService.StateCommand(
                null, "受理", null, null, "operator-b"));
        service.handle(complaintId, new ComplaintCaseService.StateCommand(
                null, "投诉属实", "加入黑名单", "完成整改", "operator-b"));
        return complaintId;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timeout");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", ex);
        }
    }
}
