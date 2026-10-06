package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Timestamp;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplaintCaseServiceTest {

    private JdbcTemplate jdbc;
    private ComplaintCaseService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("phase41-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")
                .addScript("db/migration/V5000__complaint_case_management.sql")
                .addScript("db/migration/V6600__complaint_case_events.sql")
                .build();
        jdbc = new JdbcTemplate(database);
        jdbc.update("INSERT INTO tenants(id, lifecycle_status, full_name, short_name) VALUES (7, 'SIGNED', '示例机构', '示例')");
        jdbc.update("INSERT INTO channels(id, status, channel_name) VALUES (11, 'NORMAL', '移动主通道')");
        jdbc.update("INSERT INTO signatures(id, tenant_id, audit_status, sign_content) VALUES (8, 7, 'APPROVED', '营销签名')");
        jdbc.update("INSERT INTO templates(id, tenant_id, audit_status, template_name) VALUES (9, 7, 'APPROVED', '营销模板')");
        jdbc.update("INSERT INTO tenants(id, lifecycle_status, full_name, short_name) VALUES (17, 'SIGNED', '其他机构', '其他')");
        jdbc.update("INSERT INTO channels(id, status, channel_name) VALUES (22, 'NORMAL', '其他通道')");
        jdbc.update("INSERT INTO signatures(id, tenant_id, audit_status, sign_content) VALUES (18, 17, 'APPROVED', '其他签名')");
        jdbc.update("INSERT INTO templates(id, tenant_id, audit_status, template_name) VALUES (19, 17, 'APPROVED', '其他模板')");
        service = new ComplaintCaseService(jdbc, Mockito.mock(ComplaintCaseService.BlacklistPort.class));
    }

    @Test
    void intakePreservesAvailableLinksAndMarksMissingAttributionUnknown() {
        var created = service.create(new ComplaintCaseService.CreateCommand(
                "REGULATOR", "监管投诉：营销短信扰民", 7L, null, 8L, 9L, "MSG-1",
                "MARKETING", "13800138000", "COMPLETE", "24小时内反馈"), "operator-a");

        assertThat(created.id()).isPositive();
        assertThat(created.tenantId()).isEqualTo(7L);
        assertThat(created.channelId()).isNull();
        assertThat(created.attributionQuality()).isEqualTo("UNKNOWN");
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.summary()).contains("监管投诉");
    }

    @Test
    void intakeRejectsMissingOrCrossTenantReferencesWithoutMutation() {
        var missingReferences = java.util.List.of(
                new ComplaintCaseService.CreateCommand(
                        "OPERATOR", "missing tenant", 404L, null, null, null, null,
                        null, null, null, null),
                new ComplaintCaseService.CreateCommand(
                        "OPERATOR", "missing channel", null, 404L, null, null, null,
                        null, null, null, null),
                new ComplaintCaseService.CreateCommand(
                        "OPERATOR", "missing signature", null, null, 404L, null, null,
                        null, null, null, null),
                new ComplaintCaseService.CreateCommand(
                        "OPERATOR", "missing template", null, null, null, 404L, null,
                        null, null, null, null));
        for (var command : missingReferences) {
            assertThatThrownBy(() -> service.create(command, "operator-a"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo("COMPLAINT_REFERENCE_NOT_FOUND");
        }

        var mismatchedReferences = java.util.List.of(
                new ComplaintCaseService.CreateCommand(
                        "OPERATOR", "tenant signature mismatch", 7L, null, 18L, null, null,
                        null, null, null, null),
                new ComplaintCaseService.CreateCommand(
                        "OPERATOR", "tenant template mismatch", 7L, null, null, 19L, null,
                        null, null, null, null),
                new ComplaintCaseService.CreateCommand(
                        "OPERATOR", "signature template mismatch", null, null, 8L, 19L, null,
                        null, null, null, null));
        for (var command : mismatchedReferences) {
            assertThatThrownBy(() -> service.create(command, "operator-a"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo("COMPLAINT_REFERENCE_TENANT_MISMATCH");
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM complaints", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM complaint_case_events", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=7", String.class))
                .isEqualTo("SIGNED");
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=11", String.class))
                .isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject("SELECT audit_status FROM signatures WHERE id=8", String.class))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT audit_status FROM templates WHERE id=9", String.class))
                .isEqualTo("APPROVED");
    }

    @Test
    void stateTransitionsRequireEvidenceAndRecordActorHistory() {
        long id = service.create(sampleCreate(), "operator-a").id();

        assertThatThrownBy(() -> service.accept(id, new ComplaintCaseService.StateCommand(
                null, " ", null, null, "operator-b")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("COMPLAINT_ACCEPT_OPINION_REQUIRED");

        assertThat(service.accept(id, new ComplaintCaseService.StateCommand(
                null, "已接单核查", null, null, "operator-b")).status()).isEqualTo("PROCESSING");

        assertThatThrownBy(() -> service.handle(id, new ComplaintCaseService.StateCommand(
                null, "", "暂停涉事模板", "整改说明", "operator-b")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("处理意见");

        var handled = service.handle(id, new ComplaintCaseService.StateCommand(
                null, "投诉属实", "暂停涉事模板", "机构需提交整改说明", "operator-b"));
        assertThat(handled.status()).isEqualTo("PROCESSED");
        assertThat(handled.opinion()).isEqualTo("投诉属实");

        var closed = service.close(id, new ComplaintCaseService.StateCommand(
                null, "复核通过", null, null, "operator-c"));
        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT closed_by FROM complaints WHERE id=?", String.class, id))
                .isEqualTo("operator-c");
    }

    @Test
    void handledEvidenceSupportsThreeExactMaximumLengthFields() {
        long id = service.create(sampleCreate(), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(
                null, "accepted", null, null, "operator-b"));
        String opinion = "O".repeat(500);
        String remediation = "R".repeat(500);
        String requirement = "Q".repeat(500);

        service.handle(id, new ComplaintCaseService.StateCommand(
                null, opinion, remediation, requirement, "operator-b"));

        String evidence = jdbc.queryForObject("""
                SELECT evidence_text FROM complaint_case_events
                 WHERE complaint_id=? AND event_type='HANDLED'
                """, String.class, id);
        assertThat(evidence).contains(opinion, remediation, requirement).hasSize(1517);
    }

    @Test
    void detailReturnsCompleteOrderedTimelineAndCaseRemediations() {
        long id = service.create(sampleCreate(), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(
                null, "受理并开始核查", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(
                null, "投诉属实", "暂停涉事通道", "提交整改报告", "operator-c"));
        var remediation = service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-c", "review-124", "投诉集中"));
        service.close(id, new ComplaintCaseService.StateCommand(
                null, "整改结果复核通过", null, null, "operator-d"));

        var detail = service.caseDetail(id);

        assertThat(detail.complaint().status()).isEqualTo("CLOSED");
        assertThat(detail.remediations()).extracting(ComplaintCaseService.RemediationRow::id)
                .containsExactly(remediation.id());
        assertThat(detail.timeline()).extracting(ComplaintCaseService.CaseEventRow::eventType)
                .containsExactly("REGISTERED", "ACCEPTED", "HANDLED", "REMEDIATION_APPLIED", "CLOSED");
        assertThat(detail.timeline()).element(1).satisfies(event -> {
            assertThat(event.actor()).isEqualTo("operator-b");
            assertThat(event.fromStatus()).isEqualTo("PENDING");
            assertThat(event.toStatus()).isEqualTo("PROCESSING");
            assertThat(event.evidenceText()).isEqualTo("受理并开始核查");
            assertThat(event.result()).isEqualTo("SUCCESS");
        });
        assertThat(detail.timeline()).element(3).satisfies(event -> {
            assertThat(event.relatedDisposalId()).isEqualTo(remediation.id());
            assertThat(event.targetRef()).isEqualTo("channel:11");
            assertThat(event.reviewId()).isEqualTo("review-124");
            assertThat(event.evidenceText()).isEqualTo("投诉集中");
        });
    }

    @Test
    void referenceOptionsExposeOnlyMinimalLabelsAndTruthfulTenantOwnership() {
        var options = service.referenceOptions();

        assertThat(options.tenants()).contains(new ComplaintCaseService.ReferenceOption(7L, "示例", null));
        assertThat(options.channels()).contains(new ComplaintCaseService.ReferenceOption(11L, "移动主通道", null));
        assertThat(options.signatures()).contains(new ComplaintCaseService.ReferenceOption(8L, "营销签名", 7L));
        assertThat(options.templates()).contains(new ComplaintCaseService.ReferenceOption(9L, "营销模板", 7L));
    }

    @Test
    void compareAndSetTransitionReportsStableStaleCode() {
        long id = service.create(sampleCreate(), "operator-a").id();
        jdbc.update("UPDATE complaints SET status='PROCESSING' WHERE id=?", id);

        assertThatThrownBy(() -> service.accept(id, new ComplaintCaseService.StateCommand(
                null, "受理", null, null, "operator-b")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("COMPLAINT_STATE_STALE");
        assertThat(service.caseDetail(id).timeline()).extracting(ComplaintCaseService.CaseEventRow::eventType)
                .containsExactly("REGISTERED");
    }

    @Test
    void remediationIsExactIdempotentAndUpdatesTargetResource() {
        long id = service.create(sampleCreate(), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "暂停通道", "整改", "operator-b"));

        var first = service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-b", "review-41-1", "投诉集中"));
        var second = service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-b", "review-41-1", "投诉集中"));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=11", String.class)).isEqualTo("PAUSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disposal_records WHERE complaint_id=?", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    void remediationRequiresExistingTargetResourceBeforeRecordingApplied() {
        long id = service.create(sampleCreate(), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "暂停不存在通道", "整改", "operator-b"));
        jdbc.update("DELETE FROM channels WHERE id=11");

        assertThatThrownBy(() -> service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-b", "review-41-missing-target", "投诉集中")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("处置目标不存在");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disposal_records WHERE complaint_id=?", Long.class, id))
                .isEqualTo(0L);
    }

    @Test
    void remediationRequiresTargetToBelongToComplaintAttribution() {
        ComplaintCaseService.BlacklistPort blacklist = Mockito.mock(ComplaintCaseService.BlacklistPort.class);
        ComplaintCaseService serviceWithBlacklist = new ComplaintCaseService(jdbc, blacklist);
        long id = serviceWithBlacklist.create(sampleCreate(), "operator-a").id();
        serviceWithBlacklist.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        serviceWithBlacklist.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "暂停关联资源", "整改", "operator-b"));

        assertThatThrownBy(() -> serviceWithBlacklist.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_TENANT", "tenant:17", "operator-b", "review-41-wrong-tenant", "投诉集中")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于投诉");
        assertThatThrownBy(() -> serviceWithBlacklist.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:22", "operator-b", "review-41-wrong-channel", "投诉集中")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于投诉");
        assertThatThrownBy(() -> serviceWithBlacklist.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_SIGNATURE_OR_TEMPLATE", "signature:18", "operator-b", "review-41-wrong-signature", "投诉集中")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于投诉");
        assertThatThrownBy(() -> serviceWithBlacklist.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_SIGNATURE_OR_TEMPLATE", "template:19", "operator-b", "review-41-wrong-template", "投诉集中")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于投诉");
        assertThatThrownBy(() -> serviceWithBlacklist.remediate(id, new ComplaintCaseService.RemediationCommand(
                "BLACKLIST_MOBILE", "mobile:13900139000", "operator-b", "review-41-wrong-mobile", "用户投诉")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于投诉");

        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM tenants WHERE id=17", String.class)).isEqualTo("SIGNED");
        assertThat(jdbc.queryForObject("SELECT status FROM channels WHERE id=22", String.class)).isEqualTo("NORMAL");
        assertThat(jdbc.queryForObject("SELECT audit_status FROM signatures WHERE id=18", String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT audit_status FROM templates WHERE id=19", String.class)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM disposal_records WHERE complaint_id=?", Long.class, id))
                .isEqualTo(0L);
        Mockito.verifyNoInteractions(blacklist);
    }

    @Test
    void carrierComplaintAndMobileRemediationKeepExactMobileTarget() {
        ComplaintCaseService.BlacklistPort blacklist = Mockito.mock(ComplaintCaseService.BlacklistPort.class);
        Mockito.when(blacklist.create(Mockito.eq(7L), Mockito.eq("13800138000"),
                Mockito.eq("operator-b"), Mockito.anyString())).thenReturn(99L);
        ComplaintCaseService serviceWithBlacklist = new ComplaintCaseService(jdbc, blacklist);

        long id = serviceWithBlacklist.create(new ComplaintCaseService.CreateCommand(
                "CARRIER", "运营商投诉：用户举报短信", 7L, 11L, 8L, 9L, "MSG-2",
                "MARKETING", "13800138000", null, "反馈运营商"), "operator-a").id();

        serviceWithBlacklist.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        serviceWithBlacklist.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "加入黑名单", "整改", "operator-b"));
        var remediation = serviceWithBlacklist.remediate(id, new ComplaintCaseService.RemediationCommand(
                "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-41-mobile", "用户投诉"));

        assertThat(remediation.status()).isEqualTo("APPLIED");
        assertThat(serviceWithBlacklist.cases()).anySatisfy(row -> {
            assertThat(row.id()).isEqualTo(id);
            assertThat(row.attributionQuality()).isEqualTo("COMPLETE");
        });
        Mockito.verify(blacklist).create(7L, "13800138000", "operator-b", "投诉案件:" + id);
    }

    @Test
    void mobileBlacklistRemediationRequiresTenantAttribution() {
        ComplaintCaseService.BlacklistPort blacklist = Mockito.mock(ComplaintCaseService.BlacklistPort.class);
        ComplaintCaseService serviceWithBlacklist = new ComplaintCaseService(jdbc, blacklist);

        long id = serviceWithBlacklist.create(new ComplaintCaseService.CreateCommand(
                "USER_REPORT", "用户投诉：未知归属机构", null, null, null, null, null,
                "MARKETING", "13800138000", null, "联系用户"), "operator-a").id();
        serviceWithBlacklist.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        serviceWithBlacklist.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "加入黑名单", "整改", "operator-b"));

        assertThatThrownBy(() -> serviceWithBlacklist.remediate(id, new ComplaintCaseService.RemediationCommand(
                "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-41-mobile-missing-tenant", "用户投诉")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机构归因");
        Mockito.verifyNoInteractions(blacklist);
    }

    @Test
    void partialFailureIsVisibleAndRecoveryReferencesOriginalCaseAndReview() {
        long id = service.create(sampleCreate(), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "加入黑名单失败", "整改", "operator-b"));
        ComplaintCaseService.BlacklistPort failingBlacklist = Mockito.mock(ComplaintCaseService.BlacklistPort.class);
        Mockito.when(failingBlacklist.create(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new IllegalStateException("provider timeout"));
        ComplaintCaseService serviceWithFailure = new ComplaintCaseService(jdbc, failingBlacklist);

        var failed = serviceWithFailure.remediate(id, new ComplaintCaseService.RemediationCommand(
                "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-41-fail", "provider timeout"));

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.failureReason()).isEqualTo("处置执行失败，请根据安全审计日志排查");
        assertThat(serviceWithFailure.remediations())
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.id()).isEqualTo(failed.id());
                    assertThat(row.complaintId()).isEqualTo(id);
                    assertThat(row.status()).isEqualTo("FAILED");
                    assertThat(row.failureReason()).isEqualTo("处置执行失败，请根据安全审计日志排查");
                });

        var recovered = serviceWithFailure.recover(id, new ComplaintCaseService.RecoveryCommand(
                failed.id(), "review-41-recovery", "operator-c", "复核后补偿完成"));
        assertThat(recovered.status()).isEqualTo("RECOVERED");
        assertThat(recovered.originalComplaintId()).isEqualTo(id);
        assertThat(recovered.authorizedReviewId()).isEqualTo("review-41-recovery");
    }

    @Test
    void downstreamFailurePersistsBoundedSafeReasonAndLogsNoRawMessage() {
        long id = service.create(sampleCreate(), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "加入黑名单", "整改", "operator-b"));
        String secret = "password=provider-secret token=" + "X".repeat(700);
        ComplaintCaseService.BlacklistPort failingBlacklist = Mockito.mock(ComplaintCaseService.BlacklistPort.class);
        Mockito.when(failingBlacklist.create(Mockito.any(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new IllegalStateException(secret));
        ComplaintCaseService serviceWithFailure = new ComplaintCaseService(jdbc, failingBlacklist);
        Logger securityLogger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(securityLogger.getLoggerContext());
        appender.start();
        securityLogger.addAppender(appender);
        MDC.put("traceId", "trace-complaint-124");
        try {
            var failed = serviceWithFailure.remediate(id, new ComplaintCaseService.RemediationCommand(
                    "BLACKLIST_MOBILE", "mobile:13800138000", "operator-b", "review-safe-failure", "用户投诉"));

            assertThat(failed.failureReason())
                    .isEqualTo("处置执行失败，请根据安全审计日志排查")
                    .hasSizeLessThanOrEqualTo(500)
                    .doesNotContain("provider-secret", "password=", "token=");
            assertThat(serviceWithFailure.caseDetail(id).timeline()).last().satisfies(event ->
                    assertThat(event.failureReason()).isEqualTo(failed.failureReason()));
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage()).isEqualTo(
                        "security_event event=PROVIDER_REJECTION category=PROVIDER"
                                + " purpose=complaint:" + id
                                + " purpose=type:BLACKLIST_MOBILE"
                                + " purpose=exception:IllegalStateException"
                                + " correlation=trace-complaint-124");
                assertThat(event.getFormattedMessage()).doesNotContain(secret, "provider-secret");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getArgumentArray()).isNull();
            });
        } finally {
            MDC.clear();
            securityLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void remediationReasonHonorsExactStorageBoundaries() {
        long id = service.create(sampleCreate(), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "暂停资源", "整改", "operator-b"));
        String reasonAtLimit = "R".repeat(255);
        String maximumActor = "A".repeat(64);

        service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", maximumActor, "review-channel-boundary", reasonAtLimit));
        service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_SIGNATURE_OR_TEMPLATE", "signature:8", maximumActor,
                "review-signature-boundary", reasonAtLimit));

        assertThat(jdbc.queryForObject("SELECT pause_reason FROM channels WHERE id=11", String.class))
                .isEqualTo(reasonAtLimit).hasSize(255);
        assertThat(jdbc.queryForObject("SELECT audit_comment FROM signatures WHERE id=8", String.class))
                .endsWith(reasonAtLimit).hasSizeLessThanOrEqualTo(500);
        assertThatThrownBy(() -> service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", maximumActor, "review-over-boundary", "R".repeat(256))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("COMPLAINT_REMEDIATION_REASON_REQUIRED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM disposal_records WHERE complaint_id=?", Long.class, id)).isEqualTo(2L);
    }

    @Test
    void remediationReadbackKeepsVisibleCaseFailureBeyondGlobalRecordCutoff() {
        long failedCaseId = service.create(sampleCreate(), "operator-a").id();
        long noisyCaseId = service.create(sampleCreate(), "operator-a").id();
        Timestamp base = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        jdbc.update("""
                INSERT INTO disposal_records(
                    complaint_id, disposal_type, target_ref, disposed_by, disposed_at, status,
                    authorized_review_id, failure_reason, original_complaint_id)
                VALUES (?, 'SUSPEND_CHANNEL', 'channel:11', 'operator-a', ?, 'FAILED',
                        'review-failed', 'provider-secret=legacy-canary', ?)
                """, failedCaseId, base, failedCaseId);
        for (int index = 1; index <= 201; index++) {
            jdbc.update("""
                    INSERT INTO disposal_records(
                        complaint_id, disposal_type, target_ref, disposed_by, disposed_at, status,
                        authorized_review_id, original_complaint_id)
                    VALUES (?, 'SUSPEND_CHANNEL', 'channel:11', 'operator-a', ?, 'APPLIED', ?, ?)
                    """, noisyCaseId, Timestamp.valueOf(base.toLocalDateTime().plusSeconds(index)),
                    "review-noisy-" + index, noisyCaseId);
        }

        assertThat(service.remediations())
                .hasSize(2)
                .anySatisfy(row -> {
                    assertThat(row.complaintId()).isEqualTo(failedCaseId);
                    assertThat(row.status()).isEqualTo("FAILED");
                    assertThat(row.failureReason()).isEqualTo("处置执行失败，请根据安全审计日志排查")
                            .doesNotContain("provider-secret");
                })
                .anySatisfy(row -> {
                    assertThat(row.complaintId()).isEqualTo(noisyCaseId);
                    assertThat(row.status()).isEqualTo("APPLIED");
                });
        assertThat(service.caseDetail(failedCaseId).remediations()).singleElement().satisfies(row ->
                assertThat(row.failureReason()).isEqualTo("处置执行失败，请根据安全审计日志排查")
                        .doesNotContain("provider-secret"));
    }

    @Test
    void remediationAndRecoveryRequireHandledFailedStateBoundaries() {
        long id = service.create(sampleCreate(), "operator-a").id();

        assertThatThrownBy(() -> service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-b", "review-41-early", "投诉集中")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("投诉状态");

        service.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "暂停通道", "整改", "operator-b"));
        var applied = service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:11", "operator-b", "review-41-applied", "投诉集中"));

        assertThatThrownBy(() -> service.recover(id, new ComplaintCaseService.RecoveryCommand(
                applied.id(), "review-41-recovery", "operator-c", "复核后补偿完成")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("失败处置");
    }

    @Test
    void analyticsReconcilesCasesAndUnknownAttributionQuality() {
        LocalDate today = LocalDate.now();
        long previousDayId = service.create(sampleCreate(), "operator-a").id();
        service.create(new ComplaintCaseService.CreateCommand(
                "USER_REPORT", "用户投诉：未知通道", null, null, null, null, null,
                "MARKETING", "13900139000", null, "联系用户"), "operator-a");
        jdbc.update("UPDATE complaints SET created_at=? WHERE id=?",
                Timestamp.valueOf(today.minusDays(1).atStartOfDay()), previousDayId);

        var analytics = service.analytics();

        assertThat(analytics.totalCount()).isEqualTo(2);
        assertThat(analytics.unknownAttributionCount()).isEqualTo(1);
        assertThat(analytics.trend()).containsExactly(
                new ComplaintCaseService.TrendRow(today.minusDays(1).toString(), 1),
                new ComplaintCaseService.TrendRow(today.toString(), 1));
        assertThat(analytics.byTenant()).anySatisfy(row -> {
            assertThat(row.dimension()).isEqualTo("tenant:7");
            assertThat(row.count()).isEqualTo(1);
        });
        assertThat(analytics.byContentType()).anySatisfy(row -> {
            assertThat(row.dimension()).isEqualTo("MARKETING");
            assertThat(row.count()).isEqualTo(2);
        });
    }

    private static ComplaintCaseService.CreateCommand sampleCreate() {
        return new ComplaintCaseService.CreateCommand(
                "OPERATOR", "用户投诉营销内容", 7L, 11L, 8L, 9L, "MSG-1",
                "MARKETING", "13800138000", "COMPLETE", "24小时内反馈");
    }
}
