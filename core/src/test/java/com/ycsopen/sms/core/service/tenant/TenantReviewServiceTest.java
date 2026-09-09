package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantReviewServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantAccountRepository accounts = mock(TenantAccountRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC);
    private Tenant tenant;
    private User administrator;
    private TenantReviewService service;

    @BeforeEach
    void setUp() {
        tenant = new Tenant();
        tenant.setId(42L);
        tenant.setTenantNo("T42");
        tenant.setShortName("示例机构");
        tenant.setFullName("示例机构有限公司");
        tenant.setUnifiedSocialCreditCode("91350211M000100Y46");
        tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.SUBMITTED);
        tenant.setQualificationRevision(7);
        tenant.setInspectionStatus(Tenant.InspectionStatus.COMPLETED);
        tenant.setInspectionCompanyName("示例机构有限公司");
        tenant.setInspectionCreditCode("91350211M000100Y46");
        tenant.setInspectionConfidence(0.98);
        tenant.setInspectionProviderRequestId("req-safe-1");
        tenant.setInitialAdminUserId(81L);
        administrator = new User();
        administrator.setId(81L);
        administrator.setTenantId(42L);
        administrator.setUserType(User.UserType.TENANT_ADMIN);
        administrator.setStatus(User.UserStatus.DISABLED);
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.of(tenant));
        when(tenants.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findById(81L)).thenReturn(Optional.of(administrator));
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findByTenantId(42L)).thenReturn(Optional.empty());
        when(accounts.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new TenantReviewService(tenants, accounts, users, clock, 500, 14);
    }

    @Test
    void approveRequiresInspectionAndHumanConfirmationThenCreatesInitialAccessExactlyOnce() {
        assertThatThrownBy(() -> service.decide(42L, 7, TenantReviewService.Decision.APPROVE,
                "资料核验通过", false, "101"))
                .isInstanceOf(TenantReviewService.ReviewFailure.class)
                .hasMessage("HUMAN_CONFIRMATION_REQUIRED");

        TenantReviewService.ReviewView result = service.decide(42L, 7,
                TenantReviewService.Decision.APPROVE, "资料核验通过", true, "101");

        assertThat(result.verificationStatus()).isEqualTo(Tenant.VerificationStatus.VERIFIED);
        assertThat(result.lifecycleStatus()).isEqualTo(Tenant.LifecycleStatus.TRIAL);
        assertThat(administrator.getStatus()).isEqualTo(User.UserStatus.ACTIVE);
        assertThat(tenant.getTrialQuota()).isEqualTo(500);
        assertThat(tenant.getTrialStartAt()).isEqualTo(Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDateTime());
        verify(accounts, times(1)).saveAndFlush(argThat(account ->
                account.getTenantId().equals(42L) && account.getStatus() == TenantAccount.Status.NORMAL));
        verify(tenants).appendReviewEvent(42L, "APPROVED", "PENDING", "VERIFIED",
                "SUBMITTED", "TRIAL", "资料核验通过", "101");

        assertThatThrownBy(() -> service.decide(42L, 7, TenantReviewService.Decision.APPROVE,
                "重复提交", true, "101"))
                .isInstanceOf(TenantReviewService.ReviewFailure.class);
        verify(accounts, times(1)).saveAndFlush(any());
    }

    @Test
    void staleRevisionAndIncompleteInspectionCannotDecide() {
        assertThatThrownBy(() -> service.decide(42L, 6, TenantReviewService.Decision.REJECT,
                "主体信息不符", false, "101")).hasMessage("QUALIFICATION_REVISION_STALE");
        tenant.setInspectionStatus(Tenant.InspectionStatus.FAILED);
        assertThatThrownBy(() -> service.decide(42L, 7, TenantReviewService.Decision.APPROVE,
                "资料核验通过", true, "101")).hasMessage("INSPECTION_REQUIRED");
        verify(accounts, never()).saveAndFlush(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectAndSupplementRemainNonSendingAndCreateNoAccount() {
        TenantReviewService.ReviewView rejected = service.decide(42L, 7,
                TenantReviewService.Decision.REJECT, "证件信息不一致", false, "101");
        assertThat(rejected.verificationStatus()).isEqualTo(Tenant.VerificationStatus.REJECTED);
        assertThat(rejected.lifecycleStatus()).isEqualTo(Tenant.LifecycleStatus.SUBMITTED);
        assertThat(administrator.getStatus()).isEqualTo(User.UserStatus.DISABLED);
        verify(accounts, never()).saveAndFlush(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void supplementRequiredHasItsOwnEventAndCreatesNoAccessOrAccount() {
        TenantReviewService.ReviewView supplemented = service.decide(42L, 7,
                TenantReviewService.Decision.SUPPLEMENT_REQUIRED, "请补充有效期证明", false, "101");
        assertThat(supplemented.verificationStatus())
                .isEqualTo(Tenant.VerificationStatus.SUPPLEMENT_REQUIRED);
        assertThat(supplemented.lifecycleStatus()).isEqualTo(Tenant.LifecycleStatus.SUBMITTED);
        assertThat(administrator.getStatus()).isEqualTo(User.UserStatus.DISABLED);
        verify(accounts, never()).saveAndFlush(any());
        verify(users, never()).saveAndFlush(any());
        verify(tenants).appendReviewEvent(42L, "SUPPLEMENT_REQUIRED", "PENDING",
                "SUPPLEMENT_REQUIRED", "SUBMITTED", "SUBMITTED", "请补充有效期证明", "101");
    }

    @Test
    void unsafeReasonIsRejectedAndSafeViewCannotContainProtectedMaterial() throws Exception {
        assertThatThrownBy(() -> service.decide(42L, 7, TenantReviewService.Decision.REJECT,
                "line\nbreak", false, "101")).hasMessage("INVALID_REVIEW_REASON");
        TenantReviewService.ReviewView view = service.view(tenant, null);
        String json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .writeValueAsString(view);
        assertThat(json).doesNotContain("objectId", "capability", "storage", "identity", "token", "hash");
    }

    @Test
    void safeReviewViewCarriesTheEditableBusinessMetadata() {
        tenant.setCustomerLevel(3);
        tenant.setBizManager("商务经理");
        tenant.setIndustry("互联网");
        tenant.setBusinessAddress("上海市经营地址");
        tenant.setQualificationRevision(91);
        TenantAccount account = new TenantAccount();
        account.setTenantId(42L);
        account.setStatus(TenantAccount.Status.NORMAL);
        account.setVersion(4);

        TenantReviewService.ReviewView view = service.view(tenant, account);

        assertThat(view.customerLevel()).isEqualTo(3);
        assertThat(view.bizManager()).isEqualTo("商务经理");
        assertThat(view.industry()).isEqualTo("互联网");
        assertThat(view.businessAddress()).isEqualTo("上海市经营地址");
        assertThat(view.qualificationRevision()).isEqualTo(91);
        assertThat(view.accountRevision()).isEqualTo(4);
    }

    @Test
    void safeReviewViewUsesNullAccountRevisionBeforeInitialApprovalCreatesAnAccount() {
        TenantReviewService.ReviewView view = service.view(tenant, null);

        assertThat(view.accountRevision()).isNull();
    }

    @Test
    void sensitiveReasonSentinelsAreRejectedBeforeAnyMutationEventOrEcho() {
        for (String reason : new String[]{
                "身份证号 11010519491231002X",
                "对象 pobj_v1_abcdefghijklmnopqrstuvwxyzABCDEF",
                "capability ocap_v1_abcdefghijklmnopqrstuv.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                "password: GoodPassword9!",
                "验证码: 123456",
                "otp=123456",
                "$2b$10$" + "G".repeat(53),
                "hash=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"}) {
            Throwable failure = catchThrowable(() -> service.decide(42L, 7,
                    TenantReviewService.Decision.REJECT, reason, false, "101"));
            assertThat(failure).hasMessage("UNSAFE_REVIEW_REASON");
            assertThat(failure.getMessage()).doesNotContain(reason);
        }
        assertThat(tenant.getVerificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
        verify(tenants, never()).saveAndFlush(any());
        verify(tenants, never()).appendReviewEvent(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
        verify(users, never()).saveAndFlush(any());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void missingDecisionIsAControlledFailureBeforeMutation() {
        assertThatThrownBy(() -> service.decide(42L, 7, null, "资料不符", false, "101"))
                .isInstanceOf(TenantReviewService.ReviewFailure.class)
                .hasMessage("INVALID_REVIEW_DECISION");
        verify(tenants, never()).saveAndFlush(any());
        verify(tenants, never()).appendReviewEvent(anyLong(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }
}
