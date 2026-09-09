package com.ycsopen.sms.core.service.signature;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Channel;
import com.ycsopen.sms.core.repository.ChannelRepository;
import com.ycsopen.sms.core.service.channel.health.ChannelCandidateEligibilityService;
import com.ycsopen.sms.core.service.channel.health.ChannelHealthTestSupport;
import com.ycsopen.sms.core.service.tenant.TenantEligibilityPolicy;
import com.ycsopen.sms.core.web.dto.SignatureApplicationRequest;
import com.ycsopen.sms.core.web.dto.SignatureDecisionRequest;
import com.ycsopen.sms.core.web.dto.SignatureFilingResultRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SignatureLifecycleServiceTest {

    @Test
    void qualifiedTenantSubmitsCompleteSignatureAndHistory() {
        JdbcTemplate jdbc = jdbc("signature-submit");
        TenantEligibilityPolicy tenants = mock(TenantEligibilityPolicy.class);
        SignatureLifecycleService service = service(jdbc, tenants, channel(10L, Channel.Status.NORMAL));

        var response = service.submitApplication(42L, new SignatureApplicationRequest(
                "优创硕安", "TRADEMARK", "SELF", "pobj-proof-1", "申请人A", "138****0000"));

        assertThat(response.tenantId()).isEqualTo(42L);
        assertThat(response.signContent()).isEqualTo("优创硕安");
        assertThat(response.signType()).isEqualTo("TRADEMARK");
        assertThat(response.usageType()).isEqualTo("SELF");
        assertThat(response.riskLevel()).isEqualTo("HIGH");
        assertThat(response.auditStatus()).isEqualTo("PENDING");
        assertThat(response.history()).extracting("eventType").containsExactly("SUBMITTED");
        verify(tenants).requireNewWorkAllowed(42L);

        assertThatThrownBy(() -> service.submitApplication(42L, new SignatureApplicationRequest(
                "优创硕安", "TRADEMARK", "SELF", "pobj-proof-2", "申请人A", "138****0000")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_DUPLICATED"));
    }

    @Test
    void applicationRejectsMissingConditionalProofBeforeInsert() {
        JdbcTemplate jdbc = jdbc("signature-proof");
        SignatureLifecycleService service = service(jdbc, mock(TenantEligibilityPolicy.class));

        assertThatThrownBy(() -> service.submitApplication(42L, new SignatureApplicationRequest(
                "示例商标", "TRADEMARK", "SELF", "", "申请人A", "138****0000")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_EVIDENCE_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM signatures", Integer.class)).isZero();
    }

    @Test
    void operatorDecisionRecordsActorOpinionRiskAndTenantVisibleResult() {
        JdbcTemplate jdbc = jdbc("signature-review");
        SignatureLifecycleService service = service(jdbc, mock(TenantEligibilityPolicy.class));
        long signatureId = seedSignature(jdbc, "PENDING", "HIGH");

        var supplement = service.decide(signatureId,
                new SignatureDecisionRequest("SUPPLEMENT_REQUIRED", "请补充商标授权链路", "operator-7"));

        assertThat(supplement.auditStatus()).isEqualTo("SUPPLEMENT_REQUIRED");
        assertThat(supplement.auditComment()).isEqualTo("请补充商标授权链路");
        assertThat(supplement.history()).extracting("eventType")
                .containsExactly("SUBMITTED", "SUPPLEMENT_REQUIRED");
        assertThat(supplement.history().get(1).actor()).isEqualTo("operator-7");
        assertThat(supplement.history().get(1).riskLevel()).isEqualTo("HIGH");

        var approved = service.decide(signatureId,
                new SignatureDecisionRequest("APPROVE", "材料完整，同意启用", "operator-7"));

        assertThat(approved.auditStatus()).isEqualTo("APPROVED");
        assertThat(approved.history()).extracting("eventType")
                .containsExactly("SUBMITTED", "SUPPLEMENT_REQUIRED", "APPROVED");
    }

    @Test
    void unsafeDecisionOpinionIsRejectedBeforeMutation() {
        JdbcTemplate jdbc = jdbc("signature-review-safe");
        SignatureLifecycleService service = service(jdbc, mock(TenantEligibilityPolicy.class));
        long signatureId = seedSignature(jdbc, "PENDING", "LOW");

        assertThatThrownBy(() -> service.decide(signatureId, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_REVIEW_DECISION_INVALID"));
        assertThatThrownBy(() -> service.decide(signatureId,
                new SignatureDecisionRequest("REJECT", "line\nbreak", "operator-7")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_REVIEW_OPINION_INVALID"));
        assertThat(service.get(signatureId).auditStatus()).isEqualTo("PENDING");
        assertThat(service.get(signatureId).history()).extracting("eventType").containsExactly("SUBMITTED");
    }

    @Test
    void invalidReviewFiltersAndFilingPayloadsReturnBusinessErrors() {
        JdbcTemplate jdbc = jdbc("signature-input-errors");
        SignatureLifecycleService service = service(jdbc, mock(TenantEligibilityPolicy.class),
                channel(10L, Channel.Status.NORMAL));
        long signatureId = seedSignature(jdbc, "APPROVED", "LOW");

        assertThatThrownBy(() -> service.reviewQueue(null, "not-a-number", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_TENANT_ID_INVALID"));
        assertThatThrownBy(() -> service.recordFilingResult(signatureId, 10L, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_FILING_RESULT_INVALID"));
        assertThatThrownBy(() -> service.recordFilingResult(signatureId, 10L,
                new SignatureFilingResultRequest("REGISTERED", "carrier accepted", "operator-7")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_FILING_REQUEST_REQUIRED"));
    }

    @Test
    void filingResultRequiresApprovedSignatureAndActiveRequest() {
        JdbcTemplate jdbc = jdbc("signature-filing-state");
        SignatureLifecycleService service = service(jdbc, mock(TenantEligibilityPolicy.class),
                channel(10L, Channel.Status.NORMAL));
        long pending = seedSignature(jdbc, "PENDING", "LOW");
        long approved = seedSignature(jdbc, "APPROVED", "LOW");
        register(jdbc, pending, 10L, "REGISTERING");
        register(jdbc, approved, 10L, "REGISTERED");

        assertThatThrownBy(() -> service.recordFilingResult(pending, 10L,
                new SignatureFilingResultRequest("REGISTERED", "carrier accepted", "operator-7")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_NOT_APPROVED"));
        assertThatThrownBy(() -> service.recordFilingResult(approved, 10L,
                new SignatureFilingResultRequest("FAILED", "carrier rejected", "operator-7")))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("SIGNATURE_FILING_STATE_INVALID"));
    }

    @Test
    void filingMatrixTracksPerChannelRequestResultAndRetryIdempotently() {
        JdbcTemplate jdbc = jdbc("signature-filing");
        SignatureLifecycleService service = service(jdbc, mock(TenantEligibilityPolicy.class),
                channel(10L, Channel.Status.NORMAL), channel(11L, Channel.Status.NORMAL));
        long signatureId = seedSignature(jdbc, "APPROVED", "LOW");

        assertThat(service.filingMatrix(signatureId)).extracting("channelId").containsExactly(10L, 11L);
        assertThat(service.filingMatrix(signatureId)).extracting("status").containsOnly("NONE");

        var first = service.requestFiling(signatureId, 10L, "operator-7");
        var duplicate = service.requestFiling(signatureId, 10L, "operator-7");

        assertThat(first.status()).isEqualTo("REGISTERING");
        assertThat(duplicate.attemptCount()).isEqualTo(first.attemptCount());
        assertThat(duplicate.providerRequestId()).isEqualTo(first.providerRequestId());

        service.recordFilingResult(signatureId, 10L,
                new SignatureFilingResultRequest("FAILED", "carrier-rejected", "operator-7"));
        var retry = service.requestFiling(signatureId, 10L, "operator-7");
        assertThat(retry.status()).isEqualTo("REGISTERING");
        assertThat(retry.attemptCount()).isEqualTo(2);

        var registered = service.recordFilingResult(signatureId, 10L,
                new SignatureFilingResultRequest("REGISTERED", "carrier-accepted", "operator-7"));
        assertThat(registered.status()).isEqualTo("REGISTERED");
        assertThat(registered.resultMessage()).isEqualTo("carrier-accepted");
    }

    @Test
    void usableChannelsRequireApprovedSignatureRegisteredFilingAndCandidateEligibility() {
        JdbcTemplate jdbc = jdbc("signature-usable");
        SignatureLifecycleService service = service(jdbc, mock(TenantEligibilityPolicy.class),
                channel(10L, Channel.Status.NORMAL), channel(11L, Channel.Status.PAUSED));
        long approved = seedSignature(jdbc, "APPROVED", "LOW");
        long pending = seedSignature(jdbc, "PENDING", "LOW");
        register(jdbc, approved, 10L, "REGISTERED");
        register(jdbc, approved, 11L, "REGISTERED");
        register(jdbc, pending, 10L, "REGISTERED");

        assertThat(service.usableChannels(approved)).extracting("channelId").containsExactly(10L);
        assertThat(service.usableChannels(pending)).isEmpty();
    }

    private static SignatureLifecycleService service(JdbcTemplate jdbc, TenantEligibilityPolicy tenants,
                                                     Channel... channels) {
        ChannelRepository repository = mock(ChannelRepository.class);
        when(repository.findAll()).thenReturn(List.of(channels));
        for (Channel channel : channels) {
            when(repository.findById(channel.getId())).thenReturn(Optional.of(channel));
        }
        return new SignatureLifecycleService(jdbc, tenants, repository, new ChannelCandidateEligibilityService());
    }

    private static JdbcTemplate jdbc(String name) {
        JdbcTemplate jdbc = ChannelHealthTestSupport.jdbc(name);
        jdbc.execute("""
                CREATE TABLE signatures (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT NOT NULL,
                    biz_type VARCHAR(32) NOT NULL DEFAULT 'DOMESTIC',
                    sign_code VARCHAR(32) NOT NULL,
                    sign_content VARCHAR(64) NOT NULL,
                    sign_type VARCHAR(32) NOT NULL,
                    usage_type VARCHAR(16) NOT NULL DEFAULT 'SELF',
                    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
                    evidence_url VARCHAR(255),
                    applicant_name VARCHAR(50),
                    applicant_phone_encrypted VARBINARY(255),
                    applicant_id_no_encrypted VARBINARY(255),
                    audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    audit_time TIMESTAMP NULL,
                    audit_comment VARCHAR(500),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(tenant_id, sign_code)
                )
                """);
        jdbc.execute("""
                CREATE TABLE signature_review_history (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    signature_id BIGINT NOT NULL,
                    event_type VARCHAR(32) NOT NULL,
                    actor VARCHAR(64) NOT NULL,
                    opinion VARCHAR(500) NOT NULL,
                    risk_level VARCHAR(16) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE signature_channel_registrations (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    signature_id BIGINT NOT NULL,
                    channel_id BIGINT NOT NULL,
                    reg_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    provider_request_id VARCHAR(96),
                    result_message VARCHAR(255),
                    attempt_count INT NOT NULL DEFAULT 0,
                    requested_by VARCHAR(64),
                    last_attempt_at TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(signature_id, channel_id)
                )
                """);
        return jdbc;
    }

    private static long seedSignature(JdbcTemplate jdbc, String status, String risk) {
        jdbc.update("""
                INSERT INTO signatures(tenant_id,sign_code,sign_content,sign_type,usage_type,risk_level,evidence_url,applicant_name,audit_status,audit_comment)
                VALUES (42,?,?,?,?,?,?,?,?,?)
                """, "seed-" + System.nanoTime(), "优创硕安", "ENTERPRISE", "SELF", risk, "proof", "申请人", status, "");
        long id = jdbc.queryForObject("SELECT MAX(id) FROM signatures", Long.class);
        jdbc.update("""
                INSERT INTO signature_review_history(signature_id,event_type,actor,opinion,risk_level)
                VALUES (?,'SUBMITTED','tenant:42','提交申请',?)
                """, id, risk);
        return id;
    }

    private static void register(JdbcTemplate jdbc, long signatureId, long channelId, String status) {
        jdbc.update("""
                INSERT INTO signature_channel_registrations(signature_id,channel_id,reg_status,attempt_count)
                VALUES (?,?,?,1)
                """, signatureId, channelId, status);
    }

    private static Channel channel(long id, Channel.Status status) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setChannelName("channel-" + id);
        channel.setProtocol(Channel.Protocol.CMPP);
        channel.setOperator(Channel.Operator.MOBILE);
        channel.setStatus(status);
        channel.setAvailability("AVAILABLE");
        channel.setEffectiveVersionId(100L + id);
        channel.setPrice(BigDecimal.ONE);
        channel.setPriority(50);
        return channel;
    }
}
