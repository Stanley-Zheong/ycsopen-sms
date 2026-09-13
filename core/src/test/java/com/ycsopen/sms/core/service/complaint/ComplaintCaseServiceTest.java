package com.ycsopen.sms.core.service.complaint;

import com.ycsopen.sms.core.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

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
                "MARKETING", "13800138000", null, "24小时内反馈"), "operator-a");

        assertThat(created.id()).isPositive();
        assertThat(created.tenantId()).isEqualTo(7L);
        assertThat(created.channelId()).isNull();
        assertThat(created.attributionQuality()).isEqualTo("UNKNOWN");
        assertThat(created.status()).isEqualTo("PENDING");
        assertThat(created.summary()).contains("监管投诉");
    }

    @Test
    void stateTransitionsRequireEvidenceAndRecordActorHistory() {
        long id = service.create(sampleCreate(), "operator-a").id();

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
        long id = service.create(new ComplaintCaseService.CreateCommand(
                "OPERATOR", "用户投诉营销内容", 7L, 404L, 8L, 9L, "MSG-1",
                "MARKETING", "13800138000", "COMPLETE", "24小时内反馈"), "operator-a").id();
        service.accept(id, new ComplaintCaseService.StateCommand(null, "已接单", null, null, "operator-b"));
        service.handle(id, new ComplaintCaseService.StateCommand(null, "属实", "暂停不存在通道", "整改", "operator-b"));

        assertThatThrownBy(() -> service.remediate(id, new ComplaintCaseService.RemediationCommand(
                "SUSPEND_CHANNEL", "channel:404", "operator-b", "review-41-missing-target", "投诉集中")))
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
        assertThat(failed.failureReason()).contains("provider timeout");

        var recovered = serviceWithFailure.recover(id, new ComplaintCaseService.RecoveryCommand(
                failed.id(), "review-41-recovery", "operator-c", "复核后补偿完成"));
        assertThat(recovered.status()).isEqualTo("RECOVERED");
        assertThat(recovered.originalComplaintId()).isEqualTo(id);
        assertThat(recovered.authorizedReviewId()).isEqualTo("review-41-recovery");
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
        service.create(sampleCreate(), "operator-a");
        service.create(new ComplaintCaseService.CreateCommand(
                "USER_REPORT", "用户投诉：未知通道", null, null, null, null, null,
                "MARKETING", "13900139000", null, "联系用户"), "operator-a");

        var analytics = service.analytics();

        assertThat(analytics.totalCount()).isEqualTo(2);
        assertThat(analytics.unknownAttributionCount()).isEqualTo(1);
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
