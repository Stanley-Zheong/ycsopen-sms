package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Objects;

/** One shared qualification, commercial-lifecycle, and account-state fence for new work. */
@Service
public class TenantEligibilityPolicy {
    private static final EnumSet<Tenant.LifecycleStatus> ELIGIBLE_LIFECYCLES =
            EnumSet.of(Tenant.LifecycleStatus.TRIAL, Tenant.LifecycleStatus.SIGNED);

    private final TenantRepository tenants;
    private final TenantAccountRepository accounts;

    public TenantEligibilityPolicy(TenantRepository tenants, TenantAccountRepository accounts) {
        this.tenants = Objects.requireNonNull(tenants);
        this.accounts = Objects.requireNonNull(accounts);
    }

    /**
     * The caller is normally MessageSubmitService's outer transaction.  Lock both rows in one
     * fixed order so an account disable/freeze cannot commit between this check and message work.
     */
    @Transactional
    public void requireNewWorkAllowed(Long tenantId) {
        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> denied("TENANT_NOT_FOUND", "机构不存在"));
        if (tenant.getVerificationStatus() != Tenant.VerificationStatus.VERIFIED) {
            throw denied("TENANT_QUALIFICATION_REQUIRED", "机构资质未通过审核，不可新增提交");
        }
        if (!ELIGIBLE_LIFECYCLES.contains(tenant.getLifecycleStatus())) {
            throw denied("TENANT_LIFECYCLE_INELIGIBLE", "机构当前商业状态不可新增提交");
        }
        TenantAccount account = accounts.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> denied("TENANT_ACCOUNT_NOT_FOUND", "机构账户不存在"));
        if (account.getStatus() != TenantAccount.Status.NORMAL) {
            throw denied("TENANT_ACCOUNT_INELIGIBLE", "机构账户已停用或冻结，不可新增提交");
        }
    }

    private static BusinessException denied(String code, String message) {
        return new BusinessException(code, message);
    }
}
