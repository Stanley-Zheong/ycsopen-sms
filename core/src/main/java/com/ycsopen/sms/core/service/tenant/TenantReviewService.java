package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.domain.entity.User;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import com.ycsopen.sms.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Owns the atomic, attributable decision that admits a qualified tenant. */
@Service
public class TenantReviewService {
    private static final Pattern IDENTITY_NUMBER = Pattern.compile(
            "(?<![0-9A-Za-z])[0-9]{17}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern PROTECTED_OBJECT_ID = Pattern.compile(
            "pobj_v1_[A-Za-z0-9_-]{8,}");
    private static final Pattern CAPABILITY_TOKEN = Pattern.compile(
            "ocap_v1_[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(?:password|passwd|secret|credential|token|hash|api[-_ ]?key|app[-_ ]?secret|otp|verification[-_ ]?code|authorization|验证码|校验码|口令|密码|密钥)\\s*[:=：]\\s*\\S+");
    private static final Pattern BCRYPT_OR_HASH = Pattern.compile(
            "(?:\\$2[aby]\\$[0-9]{2}\\$[A-Za-z0-9./]{53}|(?<![0-9A-Fa-f])[0-9A-Fa-f]{32,}(?![0-9A-Fa-f]))");
    private final TenantRepository tenants;
    private final TenantAccountRepository accounts;
    private final UserRepository users;
    private final Clock clock;
    private final int trialQuota;
    private final int trialDays;

    @Autowired
    public TenantReviewService(TenantRepository tenants, TenantAccountRepository accounts,
                               UserRepository users,
                               @Value("${ycsopen.tenant-qualification.trial-quota:500}") int trialQuota,
                               @Value("${ycsopen.tenant-qualification.trial-days:14}") int trialDays) {
        this(tenants, accounts, users, Clock.systemUTC(), trialQuota, trialDays);
    }

    TenantReviewService(TenantRepository tenants, TenantAccountRepository accounts,
                        UserRepository users, Clock clock, int trialQuota, int trialDays) {
        this.tenants = Objects.requireNonNull(tenants);
        this.accounts = Objects.requireNonNull(accounts);
        this.users = Objects.requireNonNull(users);
        this.clock = Objects.requireNonNull(clock);
        if (trialQuota < 1 || trialDays < 1) throw new IllegalArgumentException("invalid trial policy");
        this.trialQuota = trialQuota;
        this.trialDays = trialDays;
    }

    @Transactional(readOnly = true)
    public List<ReviewView> list() {
        return tenants.findAll().stream().map(tenant -> view(tenant,
                accounts.findByTenantId(tenant.getId()).orElse(null))).toList();
    }

    @Transactional(readOnly = true)
    public ReviewView get(long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() -> failure("TENANT_NOT_FOUND"));
        return view(tenant, accounts.findByTenantId(tenantId).orElse(null));
    }

    @Transactional
    public ReviewView decide(long tenantId, long expectedRevision, Decision decision,
                             String reason, boolean humanConfirmed, String actor) {
        requireReason(reason);
        requireActor(actor);
        if (decision == null) throw failure("INVALID_REVIEW_DECISION");
        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> failure("TENANT_NOT_FOUND"));
        if (tenant.getQualificationRevision() != expectedRevision) {
            throw failure("QUALIFICATION_REVISION_STALE");
        }
        if (tenant.getVerificationStatus() != Tenant.VerificationStatus.PENDING) {
            throw failure("QUALIFICATION_NOT_PENDING");
        }
        if (decision == Decision.APPROVE) {
            if (tenant.getInspectionStatus() != Tenant.InspectionStatus.COMPLETED) {
                throw failure("INSPECTION_REQUIRED");
            }
            if (!humanConfirmed) throw failure("HUMAN_CONFIRMATION_REQUIRED");
        }

        String beforeVerification = tenant.getVerificationStatus().name();
        String beforeLifecycle = tenant.getLifecycleStatus().name();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
        switch (decision) {
            case APPROVE -> approve(tenant, now);
            case REJECT -> tenant.setVerificationStatus(Tenant.VerificationStatus.REJECTED);
            case SUPPLEMENT_REQUIRED -> tenant.setVerificationStatus(Tenant.VerificationStatus.SUPPLEMENT_REQUIRED);
        }
        tenant.setQualificationReason(reason.trim());
        tenant.setVerificationUpdatedAt(now);
        Tenant saved = tenants.saveAndFlush(tenant);
        tenants.appendReviewEvent(tenantId, decision.eventAction(), beforeVerification,
                saved.getVerificationStatus().name(), beforeLifecycle, saved.getLifecycleStatus().name(),
                reason.trim(), actor);
        return view(saved, accounts.findByTenantId(tenantId).orElse(null));
    }

    private void approve(Tenant tenant, LocalDateTime now) {
        tenant.setVerificationStatus(Tenant.VerificationStatus.VERIFIED);
        tenant.setVerificationTime(now);
        if (tenant.getLifecycleStatus() == Tenant.LifecycleStatus.SUBMITTED) {
            tenant.setLifecycleStatus(Tenant.LifecycleStatus.TRIAL);
            tenant.setTrialQuota(trialQuota);
            tenant.setTrialQuotaUsed(0);
            tenant.setTrialStartAt(now);
            tenant.setTrialEndAt(now.plusDays(trialDays));
        }
        Long administratorId = tenant.getInitialAdminUserId();
        if (administratorId == null) throw failure("INITIAL_ADMIN_NOT_FOUND");
        User administrator = users.findById(administratorId)
                .orElseThrow(() -> failure("INITIAL_ADMIN_NOT_FOUND"));
        if (!tenant.getId().equals(administrator.getTenantId())
                || administrator.getUserType() != User.UserType.TENANT_ADMIN
                || administrator.getStatus() == User.UserStatus.LOCKED) {
            throw failure("INITIAL_ADMIN_INVALID");
        }
        administrator.setStatus(User.UserStatus.ACTIVE);
        users.saveAndFlush(administrator);
        if (accounts.findByTenantId(tenant.getId()).isEmpty()) {
            TenantAccount account = new TenantAccount();
            account.setTenantId(tenant.getId());
            account.setStatus(TenantAccount.Status.NORMAL);
            accounts.saveAndFlush(account);
        }
    }

    ReviewView view(Tenant tenant, TenantAccount account) {
        return new ReviewView(tenant.getId(), tenant.getTenantNo(), tenant.getShortName(),
                tenant.getFullName(), tenant.getUnifiedSocialCreditCode(), tenant.getLegalRepName(),
                tenant.getContactName(), tenant.getRegisteredCapital(), tenant.getBusinessScope(),
                tenant.getRegisteredAddress(), tenant.getBusinessAddress(), tenant.getCustomerLevel(),
                tenant.getBizManager(), tenant.getIndustry(), tenant.getLicenseValidUntil(),
                tenant.isTrademarkUse(), tenant.getVerificationStatus(), tenant.getLifecycleStatus(),
                account == null ? null : account.getStatus(),
                account == null ? null : account.getVersion(), tenant.getQualificationRevision(),
                tenant.getQualificationSubmittedAt(), tenant.getQualificationReason(),
                tenant.getInspectionStatus(), tenant.getInspectionCompanyName(),
                tenant.getInspectionCreditCode(), tenant.getInspectionConfidence(),
                tenant.getInspectionProviderRequestId(), tenant.getInspectionCompletedAt());
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500
                || reason.chars().anyMatch(Character::isISOControl)) {
            throw failure("INVALID_REVIEW_REASON");
        }
        if (IDENTITY_NUMBER.matcher(reason).find()
                || PROTECTED_OBJECT_ID.matcher(reason).find()
                || CAPABILITY_TOKEN.matcher(reason).find()
                || SECRET_ASSIGNMENT.matcher(reason).find()
                || BCRYPT_OR_HASH.matcher(reason).find()) {
            throw failure("UNSAFE_REVIEW_REASON");
        }
    }

    private static void requireActor(String actor) {
        if (actor == null || !actor.matches("[0-9]{1,19}")) throw failure("REVIEW_ACTOR_INVALID");
    }

    private static ReviewFailure failure(String code) { return new ReviewFailure(code); }

    public enum Decision {
        APPROVE("APPROVED"), REJECT("REJECTED"), SUPPLEMENT_REQUIRED("SUPPLEMENT_REQUIRED");
        private final String eventAction;
        Decision(String eventAction) { this.eventAction = eventAction; }
        String eventAction() { return eventAction; }
    }

    public record ReviewView(Long tenantId, String tenantNo, String shortName, String fullName,
                             String unifiedSocialCreditCode, String legalRepresentativeName,
                             String contactName, String registeredCapital, String businessScope,
                             String registeredAddress, String businessAddress, Integer customerLevel,
                             String bizManager, String industry,
                             java.time.LocalDate licenseValidUntil, boolean trademarkUse,
                             Tenant.VerificationStatus verificationStatus,
                             Tenant.LifecycleStatus lifecycleStatus, TenantAccount.Status operatingStatus,
                             Integer accountRevision, long qualificationRevision,
                             LocalDateTime submittedAt, String reason,
                             Tenant.InspectionStatus inspectionStatus, String inspectedCompanyName,
                             String inspectedCreditCode, Double inspectionConfidence,
                             String inspectionRequestId, LocalDateTime inspectionCompletedAt) { }

    public static final class ReviewFailure extends RuntimeException {
        ReviewFailure(String code) { super(code, null, false, false); }
    }
}
