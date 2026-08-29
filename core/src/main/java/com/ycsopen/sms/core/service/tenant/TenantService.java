package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * F-2.1 机构资质认证 / F-2.2 机构准入审核 / F-2.8 试用管理，
 * 对应 PRD 第 12 章生命周期阶段一"注册（含试用）"与场景四。
 * <p>状态机：SUBMITTED -[运营审核通过]-> TRIAL -[额度用尽/到期]-> TRIAL_FROZEN
 * -[转正申请通过，见 TODO F-2.9]-> SIGNED。</p>
 */
@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantAccountRepository tenantAccountRepository;

    public TenantService(TenantRepository tenantRepository, TenantAccountRepository tenantAccountRepository) {
        this.tenantRepository = tenantRepository;
        this.tenantAccountRepository = tenantAccountRepository;
    }

    /** F-2.1：机构提交注册资料，进入 SUBMITTED 状态，等待运营审核。 */
    @Transactional
    public Tenant submitRegistration(TenantRegistrationRequest req) {
        tenantRepository.findByUnifiedSocialCreditCode(req.unifiedSocialCreditCode()).ifPresent(t -> {
            throw new BusinessException("DUPLICATE_TENANT", "统一社会信用代码已注册，不可重复提交");
        });

        Tenant tenant = new Tenant();
        tenant.setTenantNo(generateTenantNo());
        tenant.setShortName(req.shortName());
        tenant.setFullName(req.fullName());
        tenant.setUnifiedSocialCreditCode(req.unifiedSocialCreditCode());
        tenant.setBusinessLicenseUrl(req.businessLicenseUrl());
        tenant.setLegalRepName(req.legalRepName());
        tenant.setContactName(req.contactName());
        tenant.setShortlinkDomainProofUrl(req.shortlinkDomainProofUrl());
        tenant.setTrademarkProofUrl(req.trademarkProofUrl());
        tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.SUBMITTED);
        return tenantRepository.save(tenant);
    }

    /**
     * F-2.2 机构准入审核（通过）+ F-2.8 试用管理：审核通过的同一动作里自动开通试用，
     * 无需机构额外申请——这是 PRD 场景四"运营审核通过，系统自动开通 500 条 / 14 天的试用额度"的直接实现。
     */
    @Transactional
    public Tenant approveAndActivateTrial(Long tenantId, int trialQuota, int trialDays, String approvedBy) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "机构不存在"));
        if (tenant.getVerificationStatus() != Tenant.VerificationStatus.PENDING) {
            throw new BusinessException("INVALID_STATE", "只有待审核状态的机构可以被审核通过");
        }

        tenant.setVerificationStatus(Tenant.VerificationStatus.VERIFIED);
        tenant.setLifecycleStatus(Tenant.LifecycleStatus.TRIAL);
        tenant.setTrialQuota(trialQuota);
        tenant.setTrialQuotaUsed(0);
        tenant.setTrialStartAt(LocalDateTime.now());
        tenant.setTrialEndAt(LocalDateTime.now().plusDays(trialDays));
        tenantRepository.save(tenant);

        // 初始化机构账户（余额为 0，试用期发送走额度计数而非账户扣费——见 PRD 检视 Finding #4，
        // 两套机制在数据层是分离的，账单口径的打通留待 F-8/F-2.9 打通时补充关联字段）。
        TenantAccount account = new TenantAccount();
        account.setTenantId(tenantId);
        account.setBalance(0L);
        tenantAccountRepository.save(account);

        return tenant;
    }

    public void rejectRegistration(Long tenantId, String reason) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "机构不存在"));
        tenant.setVerificationStatus(Tenant.VerificationStatus.REJECTED);
        tenantRepository.save(tenant);
    }

    /**
     * F-2.8：试用期内每次发送前调用，检查并扣减试用额度。
     * @return true 表示试用额度充足，可继续按试用身份发送；false 表示应转入正常计费或拒绝。
     */
    @Transactional
    public boolean tryConsumeTrialQuota(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new BusinessException("TENANT_NOT_FOUND", "机构不存在"));
        if (tenant.getLifecycleStatus() != Tenant.LifecycleStatus.TRIAL) {
            return false;
        }
        if (tenant.isTrialExpired() || tenant.isTrialQuotaExhausted()) {
            tenant.setLifecycleStatus(Tenant.LifecycleStatus.TRIAL_FROZEN);
            tenantRepository.save(tenant);
            return false;
        }
        tenant.setTrialQuotaUsed(tenant.getTrialQuotaUsed() + 1);
        tenantRepository.save(tenant);
        return true;
    }

    private String generateTenantNo() {
        return "T" + System.currentTimeMillis();
    }
}
