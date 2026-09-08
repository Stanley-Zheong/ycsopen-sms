package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.common.exception.BusinessException;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.*;
import com.ycsopen.sms.core.repository.*;
import com.ycsopen.sms.core.web.dto.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

/** Registration and resubmission share validation, protected claims, and one-use contact receipts. */
@Service
public class TenantRegistrationService {
    private final TenantRepository tenants;
    private final UserRepository users;
    private final TenantService tenantService;
    private final TenantRegistrationProtectionAdapter protection;
    private final ContactVerificationService contacts;
    private final PasswordEncoder passwords;
    private final TenantQualificationValidator validator = new TenantQualificationValidator();

    public TenantRegistrationService(TenantRepository tenants, UserRepository users, TenantService tenantService,
                                     TenantRegistrationProtectionAdapter protection, ContactVerificationService contacts,
                                     PasswordEncoder passwords) {
        this.tenants = tenants; this.users = users; this.tenantService = tenantService;
        this.protection = protection; this.contacts = contacts; this.passwords = passwords;
    }

    @Transactional
    public TenantRegistrationResponse register(TenantRegistrationRequest request, String uploadToken,
                                                String challengeId, String username, String password, String email) {
        validateCredentials(username, password, email);
        validator.validate(request);
        protection.validateRequest(request, uploadToken);
        if (tenants.findByUnifiedSocialCreditCode(request.unifiedSocialCreditCode()).isPresent()
                || users.findByUsername(username).isPresent()) throw new SubmissionFailure("DUPLICATE_REGISTRATION");
        contacts.consumeVerified(challengeId, request.contactPhone());
        try {
            Tenant tenant = tenantService.submitRegistration(request, uploadToken);
            User admin = new User();
            admin.setUsername(username);
            admin.setEmail(email.trim().toLowerCase(Locale.ROOT));
            admin.setPasswordHash(passwords.encode(password));
            admin.setUserType(User.UserType.TENANT_ADMIN);
            admin.setStatus(User.UserStatus.DISABLED);
            admin.setTenantId(tenant.getId());
            admin.setCreatedBy("public-registration");
            User saved = users.saveAndFlush(admin);
            tenant.setInitialAdminUserId(saved.getId());
            submitted(tenant, request);
            Tenant result = tenants.saveAndFlush(tenant);
            tenants.appendSubmissionEvent(result.getId(), "UNVERIFIED", result.getLifecycleStatus().name(), "public-registration");
            return TenantRegistrationResponse.from(result);
        } catch (DataIntegrityViolationException failure) {
            throw new SubmissionFailure("DUPLICATE_REGISTRATION");
        } catch (BusinessException failure) {
            if ("DUPLICATE_TENANT".equals(failure.getErrorCode())) throw new SubmissionFailure("DUPLICATE_REGISTRATION");
            throw failure;
        }
    }

    @Transactional
    public TenantRegistrationResponse submitOwn(String authenticatedSubject, TenantRegistrationRequest request,
                                                 String uploadToken, String challengeId) {
        Long tenantId = ownTenant(authenticatedSubject);
        validator.validate(request);
        protection.validateRequest(request, uploadToken);
        Tenant tenant = tenants.findByIdForUpdate(tenantId).orElseThrow(() -> new AccessDeniedException("Tenant access denied"));
        if (tenant.getVerificationStatus() == Tenant.VerificationStatus.PENDING) throw new SubmissionFailure("QUALIFICATION_ALREADY_PENDING");
        if (tenant.getLifecycleStatus() == Tenant.LifecycleStatus.TERMINATED) throw new SubmissionFailure("QUALIFICATION_NOT_AVAILABLE");
        String beforeVerification = tenant.getVerificationStatus().name();
        tenants.findByUnifiedSocialCreditCode(request.unifiedSocialCreditCode()).ifPresent(other -> {
            if (!other.getId().equals(tenantId)) throw new SubmissionFailure("DUPLICATE_REGISTRATION");
        });
        contacts.consumeVerified(challengeId, request.contactPhone());
        protection.protectRegistration(tenant, request, uploadToken);
        tenant.setShortName(request.shortName().trim());
        tenant.setFullName(request.fullName().trim());
        tenant.setUnifiedSocialCreditCode(request.unifiedSocialCreditCode());
        tenant.setLegalRepName(request.legalRepName().trim());
        tenant.setContactName(request.contactName().trim());
        submitted(tenant, request);
        try {
            Tenant result = tenants.saveAndFlush(tenant);
            tenants.appendSubmissionEvent(tenantId, beforeVerification, result.getLifecycleStatus().name(), authenticatedSubject);
            return TenantRegistrationResponse.from(result);
        }
        catch (DataIntegrityViolationException failure) { throw new SubmissionFailure("DUPLICATE_REGISTRATION"); }
    }

    @Transactional(readOnly = true)
    public TenantRegistrationResponse statusOwn(String authenticatedSubject) {
        return TenantRegistrationResponse.fromOwnStatus(tenants.findById(ownTenant(authenticatedSubject))
                .orElseThrow(() -> new AccessDeniedException("Tenant access denied")));
    }

    private Long ownTenant(String subject) {
        long userId;
        try { userId = Long.parseLong(subject); } catch (RuntimeException failure) { throw new AccessDeniedException("Tenant access denied"); }
        User user = users.findById(userId).orElseThrow(() -> new AccessDeniedException("Tenant access denied"));
        if (user.getStatus() != User.UserStatus.ACTIVE || user.getUserType() != User.UserType.TENANT_ADMIN
                || user.getTenantId() == null || user.isAccountExpired(java.time.LocalDate.now())
                || user.isPasswordExpired(LocalDateTime.now())) throw new AccessDeniedException("Tenant access denied");
        return user.getTenantId();
    }

    private static void submitted(Tenant tenant, TenantRegistrationRequest request) {
        tenant.setRegisteredCapital(request.registeredCapital());
        tenant.setBusinessScope(request.businessScope());
        tenant.setRegisteredAddress(request.registeredAddress());
        tenant.setBusinessAddress(request.businessAddress());
        tenant.setLicenseValidUntil(request.licenseValidUntil());
        tenant.setVerificationStatus(Tenant.VerificationStatus.PENDING);
        tenant.setTrademarkUse(request.trademarkUse());
        tenant.setQualificationSubmittedAt(LocalDateTime.now());
        tenant.setVerificationUpdatedAt(LocalDateTime.now());
        tenant.setQualificationReason(null);
        tenant.setInspectionStatus(Tenant.InspectionStatus.NOT_STARTED);
        tenant.setInspectionCompanyName(null);
        tenant.setInspectionCreditCode(null);
        tenant.setInspectionConfidence(null);
        tenant.setInspectionProviderRequestId(null);
        tenant.setInspectionCompletedAt(null);
    }

    private static void validateCredentials(String username, String password, String email) {
        if (username == null || !username.matches("[A-Za-z][A-Za-z0-9_.-]{3,49}")
                || password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*") || !password.matches(".*[0-9].*")
                || password.chars().anyMatch(Character::isISOControl)
                || email == null || email.length() > 100 || !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+")) {
            throw new SubmissionFailure("INVALID_ADMIN_CREDENTIALS");
        }
    }

    public static final class SubmissionFailure extends RuntimeException {
        public SubmissionFailure(String code) { super(code); }
    }
}
