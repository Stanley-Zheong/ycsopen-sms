package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class TenantEligibilityPolicyTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantAccountRepository accounts = mock(TenantAccountRepository.class);
    private final Tenant tenant = new Tenant();
    private final TenantAccount account = new TenantAccount();
    private TenantEligibilityPolicy policy;

    @BeforeEach
    void setUp() {
        tenant.setId(42L);
        tenant.setVerificationStatus(Tenant.VerificationStatus.VERIFIED);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.TRIAL);
        account.setTenantId(42L);
        account.setStatus(TenantAccount.Status.NORMAL);
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.of(tenant));
        when(accounts.findByTenantIdForUpdate(42L)).thenReturn(Optional.of(account));
        policy = new TenantEligibilityPolicy(tenants, accounts);
    }

    @Test
    void verifiedTrialOrSignedTenantWithNormalAccountMayCreateNewWork() {
        assertThatCode(() -> policy.requireNewWorkAllowed(42L)).doesNotThrowAnyException();
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.SIGNED);
        assertThatCode(() -> policy.requireNewWorkAllowed(42L)).doesNotThrowAnyException();
    }

    @Test
    void locksTenantThenAccountBeforeReturningEligible() {
        policy.requireNewWorkAllowed(42L);
        var order = inOrder(tenants, accounts);
        order.verify(tenants).findByIdForUpdate(42L);
        order.verify(accounts).findByTenantIdForUpdate(42L);
    }

    @ParameterizedTest
    @EnumSource(value = Tenant.VerificationStatus.class, names = "VERIFIED", mode = EnumSource.Mode.EXCLUDE)
    void everyNonVerifiedQualificationIsRejected(Tenant.VerificationStatus status) {
        tenant.setVerificationStatus(status);

        assertDenied("TENANT_QUALIFICATION_REQUIRED");
    }

    @ParameterizedTest
    @EnumSource(value = Tenant.LifecycleStatus.class, names = {"TRIAL", "SIGNED"}, mode = EnumSource.Mode.EXCLUDE)
    void everyNonOperatingCommercialLifecycleIsRejected(Tenant.LifecycleStatus status) {
        tenant.setLifecycleStatus(status);

        assertDenied("TENANT_LIFECYCLE_INELIGIBLE");
    }

    @ParameterizedTest
    @EnumSource(value = TenantAccount.Status.class, names = "NORMAL", mode = EnumSource.Mode.EXCLUDE)
    void disabledAndArrearsFrozenAccountsAreRejected(TenantAccount.Status status) {
        account.setStatus(status);

        assertDenied("TENANT_ACCOUNT_INELIGIBLE");
    }

    @Test
    void missingTenantOrAccountIsRejectedWithoutInventingAnEligibleDefault() {
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        assertDenied("TENANT_NOT_FOUND");
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.of(tenant));
        when(accounts.findByTenantIdForUpdate(42L)).thenReturn(Optional.empty());
        assertDenied("TENANT_ACCOUNT_NOT_FOUND");
    }

    private void assertDenied(String code) {
        assertThatThrownBy(() -> policy.requireNewWorkAllowed(42L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(code);
    }
}
