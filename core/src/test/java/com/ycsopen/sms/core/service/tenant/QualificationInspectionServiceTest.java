package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QualificationInspectionServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final QualificationEvidenceService evidence = mock(QualificationEvidenceService.class);
    private final QualificationInspectionSpi provider = mock(QualificationInspectionSpi.class);
    private final TenantReviewService reviews = mock(TenantReviewService.class);
    private final TransactionOperations transactions = mock(TransactionOperations.class);
    private Tenant tenant;
    private QualificationInspectionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactions.execute(any())).thenAnswer(call ->
                ((TransactionCallback<Object>) call.getArgument(0)).doInTransaction(status));
        doAnswer(call -> {
            ((Consumer<TransactionStatus>) call.getArgument(0)).accept(status);
            return null;
        }).when(transactions).executeWithoutResult(any());
        tenant = new Tenant();
        tenant.setId(42L);
        tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        tenant.setQualificationRevision(7);
        when(tenants.findById(42L)).thenReturn(Optional.of(tenant));
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.of(tenant));
        when(tenants.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(evidence.readForInspection(42L)).thenReturn(new QualificationEvidenceService.EvidenceContent(
                "application/pdf", new byte[]{1, 2, 3}));
        service = new QualificationInspectionService(tenants, evidence, provider,
                Clock.fixed(Instant.parse("2026-09-07T08:00:00Z"), ZoneOffset.UTC), transactions);
    }

    @Test
    void completedProviderCallPersistsOnlySafeFactsAndLeavesDecisionToHuman() {
        when(provider.inspect(any())).thenReturn(new QualificationInspectionSpi.Facts(
                "示例机构有限公司", "91350211M000100Y46", 0.98, "safe-req-1"));
        service.inspect(42, 7, reviews);
        assertThat(tenant.getInspectionStatus()).isEqualTo(Tenant.InspectionStatus.COMPLETED);
        assertThat(tenant.getInspectionCompanyName()).isEqualTo("示例机构有限公司");
        assertThat(tenant.getInspectionProviderRequestId()).isEqualTo("safe-req-1");
        assertThat(tenant.getVerificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
        verify(tenants).saveAndFlush(tenant);
    }

    @Test
    void providerFailurePersistsSafeFailedStateAndKeepsApplicationPending() {
        when(provider.inspect(any())).thenThrow(QualificationInspectionSpi.Failure.unavailable());
        assertThatThrownBy(() -> service.inspect(42, 7, reviews))
                .isInstanceOf(QualificationInspectionService.InspectionFailure.class)
                .hasMessage("QUALIFICATION_INSPECTION_UNAVAILABLE");
        assertThat(tenant.getInspectionStatus()).isEqualTo(Tenant.InspectionStatus.FAILED);
        assertThat(tenant.getInspectionProviderRequestId()).isNull();
        assertThat(tenant.getVerificationStatus()).isEqualTo(Tenant.VerificationStatus.PENDING);
        verify(tenants).saveAndFlush(tenant);
    }

    @Test
    void staleRevisionIsRejectedBeforeEvidenceOrProviderAccess() {
        assertThatThrownBy(() -> service.inspect(42, 6, reviews))
                .hasMessage("QUALIFICATION_REVISION_STALE");
        verifyNoInteractions(evidence, provider);
    }
}
