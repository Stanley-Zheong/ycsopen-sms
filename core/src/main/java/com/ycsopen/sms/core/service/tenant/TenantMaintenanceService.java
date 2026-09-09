package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.domain.entity.TenantAccount;
import com.ycsopen.sms.core.repository.TenantAccountRepository;
import com.ycsopen.sms.core.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Controls attributable non-certification profile edits and tenant-account operating actions. */
@Service
public class TenantMaintenanceService {
    private static final Pattern IDENTITY_NUMBER = Pattern.compile(
            "(?<![0-9A-Za-z])[0-9]{17}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern PROTECTED_OBJECT_ID = Pattern.compile("pobj_v1_[A-Za-z0-9_-]{8,}");
    private static final Pattern CAPABILITY_TOKEN = Pattern.compile(
            "ocap_v1_[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(?:password|passwd|secret|credential|token|hash|api[-_ ]?key|app[-_ ]?secret|otp|verification[-_ ]?code|authorization|验证码|校验码|口令|密码|密钥)\\s*[:=：]\\s*\\S+");
    private static final Pattern BCRYPT_OR_HASH = Pattern.compile(
            "(?:\\$2[aby]\\$[0-9]{2}\\$[A-Za-z0-9./]{53}|(?<![0-9A-Fa-f])[0-9A-Fa-f]{32,}(?![0-9A-Fa-f]))");

    private final TenantRepository tenants;
    private final TenantAccountRepository accounts;

    public TenantMaintenanceService(TenantRepository tenants, TenantAccountRepository accounts) {
        this.tenants = Objects.requireNonNull(tenants);
        this.accounts = Objects.requireNonNull(accounts);
    }

    @Transactional
    public ProfileResult updateProfile(long tenantId, long expectedRevision, ProfileUpdate update,
                                       String reason, String actor) {
        requireReason(reason);
        requireActor(actor);
        validate(update);
        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> failure("TENANT_NOT_FOUND"));
        if (tenant.getQualificationRevision() != expectedRevision) {
            throw failure("QUALIFICATION_REVISION_STALE");
        }

        List<String> changed = new ArrayList<>();
        change(update.shortName(), tenant.getShortName(), tenant::setShortName, "shortName", changed, false);
        change(update.contactName(), tenant.getContactName(), tenant::setContactName, "contactName", changed, false);
        change(update.businessAddress(), tenant.getBusinessAddress(), tenant::setBusinessAddress,
                "businessAddress", changed, true);
        if (update.customerLevel() != null && !update.customerLevel().equals(tenant.getCustomerLevel())) {
            tenant.setCustomerLevel(update.customerLevel());
            changed.add("customerLevel");
        }
        change(update.bizManager(), tenant.getBizManager(), tenant::setBizManager, "bizManager", changed, true);
        change(update.industry(), tenant.getIndustry(), tenant::setIndustry, "industry", changed, true);
        if (changed.isEmpty()) throw failure("TENANT_PROFILE_UNCHANGED");

        Tenant saved = tenants.saveAndFlush(tenant);
        String accountStatus = accounts.findByTenantId(tenantId)
                .map(account -> account.getStatus().name()).orElse(null);
        tenants.appendProfileEvent(tenantId, saved.getVerificationStatus().name(),
                saved.getLifecycleStatus().name(), accountStatus, String.join(",", changed),
                reason.trim(), actor);
        return ProfileResult.from(saved);
    }

    @Transactional
    public AccountStatusResult changeAccountStatus(long tenantId, int expectedRevision,
                                                   TenantAccount.Status target, String reason,
                                                   String actor) {
        requireReason(reason);
        requireActor(actor);
        if (target == null) throw failure("ACCOUNT_STATUS_INVALID");
        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> failure("TENANT_NOT_FOUND"));
        TenantAccount account = accounts.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> failure("TENANT_ACCOUNT_NOT_FOUND"));
        int revision = account.getVersion() == null ? 0 : account.getVersion();
        if (revision != expectedRevision) throw failure("ACCOUNT_REVISION_STALE");
        TenantAccount.Status before = account.getStatus();
        if (before == target) throw failure("ACCOUNT_STATUS_UNCHANGED");
        account.setStatus(target);
        TenantAccount saved = accounts.saveAndFlush(account);
        tenants.appendAccountStatusEvent(tenantId, tenant.getVerificationStatus().name(),
                tenant.getLifecycleStatus().name(), before.name(), target.name(), reason.trim(), actor);
        return new AccountStatusResult(tenantId, target,
                saved.getVersion() == null ? expectedRevision + 1 : saved.getVersion());
    }

    @Transactional(readOnly = true)
    public List<QualificationEventView> history(long tenantId) {
        return tenants.findQualificationEvents(tenantId).stream()
                .map(event -> new QualificationEventView(event.getId(), event.getAction(),
                        event.getBeforeVerificationStatus(), event.getAfterVerificationStatus(),
                        event.getBeforeLifecycleStatus(), event.getAfterLifecycleStatus(),
                        event.getBeforeAccountStatus(), event.getAfterAccountStatus(),
                        event.getChangedFields(), event.getReason(), event.getActor(), event.getCreatedAt()))
                .toList();
    }

    private static void validate(ProfileUpdate update) {
        if (update == null
                || invalidRequired(update.shortName(), 20)
                || invalidRequired(update.contactName(), 50)
                || invalidOptional(update.businessAddress(), 255)
                || update.customerLevel() != null && (update.customerLevel() < 1 || update.customerLevel() > 5)
                || invalidOptional(update.bizManager(), 64)
                || invalidOptional(update.industry(), 64)) {
            throw failure("TENANT_PROFILE_INVALID");
        }
    }

    private static boolean invalidRequired(String value, int max) {
        return value != null && (value.isBlank() || value.length() > max
                || value.chars().anyMatch(Character::isISOControl));
    }

    private static boolean invalidOptional(String value, int max) {
        return value != null && (value.length() > max || value.chars().anyMatch(Character::isISOControl));
    }

    private static void change(String requested, String current, java.util.function.Consumer<String> setter,
                               String field, List<String> changed, boolean blankAsNull) {
        if (requested == null) return;
        String normalized = requested.trim();
        if (blankAsNull && normalized.isEmpty()) normalized = null;
        if (!Objects.equals(normalized, current)) {
            setter.accept(normalized);
            changed.add(field);
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500
                || reason.chars().anyMatch(Character::isISOControl)) {
            throw failure("INVALID_MAINTENANCE_REASON");
        }
        if (IDENTITY_NUMBER.matcher(reason).find() || PROTECTED_OBJECT_ID.matcher(reason).find()
                || CAPABILITY_TOKEN.matcher(reason).find() || SECRET_ASSIGNMENT.matcher(reason).find()
                || BCRYPT_OR_HASH.matcher(reason).find()) {
            throw failure("UNSAFE_MAINTENANCE_REASON");
        }
    }

    private static void requireActor(String actor) {
        if (actor == null || !actor.matches("[0-9]{1,19}")) throw failure("MAINTENANCE_ACTOR_INVALID");
    }

    private static MaintenanceFailure failure(String code) { return new MaintenanceFailure(code); }

    public record ProfileUpdate(String shortName, String contactName, String businessAddress,
                                Integer customerLevel, String bizManager, String industry) { }

    public record ProfileResult(long tenantId, String shortName, String contactName,
                                String businessAddress, Integer customerLevel, String bizManager,
                                String industry, Tenant.VerificationStatus verificationStatus,
                                Tenant.LifecycleStatus lifecycleStatus, long revision) {
        static ProfileResult from(Tenant tenant) {
            return new ProfileResult(tenant.getId(), tenant.getShortName(), tenant.getContactName(),
                    tenant.getBusinessAddress(), tenant.getCustomerLevel(), tenant.getBizManager(),
                    tenant.getIndustry(), tenant.getVerificationStatus(), tenant.getLifecycleStatus(),
                    tenant.getQualificationRevision());
        }
    }

    public record AccountStatusResult(long tenantId, TenantAccount.Status status, int revision) { }

    public record QualificationEventView(long id, String action,
                                         String beforeVerificationStatus, String afterVerificationStatus,
                                         String beforeLifecycleStatus, String afterLifecycleStatus,
                                         String beforeAccountStatus, String afterAccountStatus,
                                         String changedFields, String reason, String actor,
                                         java.time.LocalDateTime createdAt) { }

    public static final class MaintenanceFailure extends RuntimeException {
        MaintenanceFailure(String code) { super(code, null, false, false); }
    }
}
