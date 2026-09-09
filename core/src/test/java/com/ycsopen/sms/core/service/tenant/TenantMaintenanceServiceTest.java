package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TenantMaintenanceServiceTest {
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final TenantAccountRepository accounts = mock(TenantAccountRepository.class);
    private final Tenant tenant = new Tenant();
    private final TenantAccount account = new TenantAccount();
    private TenantMaintenanceService service;

    @BeforeEach
    void setUp() {
        tenant.setId(42L);
        tenant.setShortName("旧简称");
        tenant.setContactName("旧联系人");
        tenant.setBusinessAddress("旧经营地址");
        tenant.setCustomerLevel(1);
        tenant.setBizManager("旧经理");
        tenant.setIndustry("软件");
        tenant.setVerificationStatus(Tenant.VerificationStatus.VERIFIED);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.TRIAL);
        tenant.setQualificationRevision(4L);
        account.setId(73L);
        account.setTenantId(42L);
        account.setStatus(TenantAccount.Status.NORMAL);
        account.setVersion(2);
        when(tenants.findByIdForUpdate(42L)).thenReturn(Optional.of(tenant));
        when(tenants.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(accounts.findByTenantId(42L)).thenReturn(Optional.of(account));
        when(accounts.findByTenantIdForUpdate(42L)).thenReturn(Optional.of(account));
        when(accounts.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        service = new TenantMaintenanceService(tenants, accounts);
    }

    @Test
    void authorizedProfileUpdateChangesOnlyAllowedBusinessMetadataAndWritesSafeFieldNames() {
        TenantMaintenanceService.ProfileResult result = service.updateProfile(42L, 4L,
                new TenantMaintenanceService.ProfileUpdate("新简称", "新联系人", "新经营地址", 3,
                        "运营经理", "互联网"), "客户确认基础资料更新", "101");

        assertThat(result.shortName()).isEqualTo("新简称");
        assertThat(result.contactName()).isEqualTo("新联系人");
        assertThat(result.businessAddress()).isEqualTo("新经营地址");
        assertThat(result.customerLevel()).isEqualTo(3);
        assertThat(result.bizManager()).isEqualTo("运营经理");
        assertThat(result.industry()).isEqualTo("互联网");
        assertThat(tenant.getUnifiedSocialCreditCode()).isNull();
        assertThat(tenant.getVerificationStatus()).isEqualTo(Tenant.VerificationStatus.VERIFIED);
        verify(tenants).appendProfileEvent(42L, "VERIFIED", "TRIAL", "NORMAL",
                "shortName,contactName,businessAddress,customerLevel,bizManager,industry",
                "客户确认基础资料更新", "101");
    }

    @Test
    void staleProfileRevisionAndUnsafeReasonFailBeforeMutationOrEvent() {
        assertThatThrownBy(() -> service.updateProfile(42L, 3L,
                new TenantMaintenanceService.ProfileUpdate("新简称", null, null, null, null, null),
                "正常维护", "101")).hasMessage("QUALIFICATION_REVISION_STALE");
        assertThatThrownBy(() -> service.updateProfile(42L, 4L,
                new TenantMaintenanceService.ProfileUpdate("新简称", null, null, null, null, null),
                "password: leaked", "101")).hasMessage("UNSAFE_MAINTENANCE_REASON");

        assertThat(tenant.getShortName()).isEqualTo("旧简称");
        verify(tenants, never()).saveAndFlush(any());
        verify(tenants, never()).appendProfileEvent(anyLong(), anyString(), anyString(),
                any(), anyString(), anyString(), anyString());
    }

    @Test
    void unchangedOrInvalidProfileDoesNotCreateAuditNoise() {
        assertThatThrownBy(() -> service.updateProfile(42L, 4L,
                new TenantMaintenanceService.ProfileUpdate("旧简称", "旧联系人", "旧经营地址", 1,
                        "旧经理", "软件"), "无实际变化", "101"))
                .hasMessage("TENANT_PROFILE_UNCHANGED");
        assertThatThrownBy(() -> service.updateProfile(42L, 4L,
                new TenantMaintenanceService.ProfileUpdate("x".repeat(21), null, null, 9, null, null),
                        "字段越界", "101"))
                .hasMessage("TENANT_PROFILE_INVALID");
        verify(tenants, never()).appendProfileEvent(anyLong(), anyString(), anyString(),
                any(), anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @EnumSource(value = TenantAccount.Status.class, names = "NORMAL", mode = EnumSource.Mode.EXCLUDE)
    void expectedVersionStatusActionPersistsOneTransitionAndSafeEvent(TenantAccount.Status target) {
        TenantMaintenanceService.AccountStatusResult result = service.changeAccountStatus(
                42L, 2, target, "运营确认状态变更", "102");

        assertThat(result.status()).isEqualTo(target);
        verify(accounts).saveAndFlush(account);
        verify(tenants).appendAccountStatusEvent(42L, "VERIFIED", "TRIAL", "NORMAL",
                target.name(), "运营确认状态变更", "102");
    }

    @Test
    void staleOrUnchangedStatusActionCannotWriteOrAppendEvent() {
        assertThatThrownBy(() -> service.changeAccountStatus(42L, 1,
                TenantAccount.Status.DISABLED, "停用", "102"))
                .hasMessage("ACCOUNT_REVISION_STALE");
        assertThatThrownBy(() -> service.changeAccountStatus(42L, 2,
                TenantAccount.Status.NORMAL, "维持正常", "102"))
                .hasMessage("ACCOUNT_STATUS_UNCHANGED");

        assertThat(account.getStatus()).isEqualTo(TenantAccount.Status.NORMAL);
        verify(accounts, never()).saveAndFlush(any());
        verify(tenants, never()).appendAccountStatusEvent(anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }
}
