package com.ycsopen.sms.core.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * F-2 机构主档 + 全生命周期字段（PRD 第 12 章）。
 * 生命周期状态机： SUBMITTED -> TRIAL -> (TRIAL_FROZEN | SIGNED) -> FROZEN -> TERMINATED
 * 见 ycsansms.md 5.2 节"状态机（机构状态）"。
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_no", nullable = false, unique = true)
    private String tenantNo;

    @Column(name = "short_name", nullable = false)
    private String shortName;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "unified_social_credit_code", nullable = false, unique = true)
    private String unifiedSocialCreditCode;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "business_license_url")
    private String businessLicenseObjectId;

    @Column(name = "legal_rep_name")
    private String legalRepName;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "legal_rep_id_no_encrypted", length = 255)
    private byte[] legalRepIdNoEncrypted;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "legal_rep_id_front_url")
    private String legalRepIdFrontObjectId;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "legal_rep_id_back_url")
    private String legalRepIdBackObjectId;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "registered_capital", length = 50)
    private String registeredCapital;
    @Column(name = "business_scope", columnDefinition = "TEXT")
    private String businessScope;
    @Column(name = "registered_address", length = 255)
    private String registeredAddress;
    @Column(name = "business_address", length = 255)
    private String businessAddress;
    @Column(name = "license_valid_until")
    private LocalDate licenseValidUntil;
    @Column(name = "customer_level")
    private Integer customerLevel = 1;
    @Column(name = "biz_manager", length = 64)
    private String bizManager;
    @Column(name = "industry", length = 64)
    private String industry;
    @Column(name = "trademark_use", nullable = false)
    @org.hibernate.annotations.ColumnDefault("false")
    private boolean trademarkUse;
    @Column(name = "qualification_submitted_at")
    private LocalDateTime qualificationSubmittedAt;
    @Column(name = "verification_time")
    private LocalDateTime verificationTime;
    @Column(name = "verification_updated_at")
    private LocalDateTime verificationUpdatedAt;
    @Column(name = "qualification_reason", length = 500)
    private String qualificationReason;
    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_status", nullable = false)
    @org.hibernate.annotations.ColumnDefault("'NOT_STARTED'")
    private InspectionStatus inspectionStatus = InspectionStatus.NOT_STARTED;
    @Column(name = "inspection_company_name", length = 100)
    private String inspectionCompanyName;
    @Column(name = "inspection_credit_code", length = 18)
    private String inspectionCreditCode;
    @Column(name = "inspection_confidence")
    private Double inspectionConfidence;
    @Column(name = "inspection_provider_request_id", length = 100)
    private String inspectionProviderRequestId;
    @Column(name = "inspection_completed_at")
    private LocalDateTime inspectionCompletedAt;
    @JsonIgnore
    @Column(name = "initial_admin_user_id")
    private Long initialAdminUserId;
    @Version
    @Column(name = "qualification_revision", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long qualificationRevision;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "contact_id_no_encrypted", length = 255)
    private byte[] contactIdNoEncrypted;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "contact_phone_encrypted", length = 255)
    private byte[] contactPhoneEncrypted;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "shortlink_domain_proof_url")
    private String shortlinkDomainProofObjectId;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @ToString.Exclude
    @Column(name = "trademark_proof_url")
    private String trademarkProofObjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status")
    private LifecycleStatus lifecycleStatus = LifecycleStatus.SUBMITTED;

    @Column(name = "trial_quota")
    private Integer trialQuota;

    @Column(name = "trial_quota_used")
    private Integer trialQuotaUsed = 0;

    @Column(name = "trial_start_at")
    private LocalDateTime trialStartAt;

    @Column(name = "trial_end_at")
    private LocalDateTime trialEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_mode")
    private BillingMode billingMode;

    @Column(name = "contract_signed_at")
    private LocalDate contractSignedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum VerificationStatus { UNVERIFIED, PENDING, VERIFIED, REJECTED, SUPPLEMENT_REQUIRED }
    public enum InspectionStatus { NOT_STARTED, COMPLETED, FAILED }
    public enum LifecycleStatus { SUBMITTED, TRIAL, TRIAL_FROZEN, SIGNED, FROZEN, TERMINATED }
    public enum BillingMode { PREPAID, POSTPAID }

    /** Presence describes stored material without disclosing or decrypting its protected value. */
    @JsonIgnore
    public boolean hasBusinessLicense() { return present(businessLicenseObjectId); }
    @JsonIgnore
    public boolean hasLegalRepresentativeIdentity() { return present(legalRepIdNoEncrypted); }
    @JsonIgnore
    public boolean hasLegalRepresentativeIdFront() { return present(legalRepIdFrontObjectId); }
    @JsonIgnore
    public boolean hasLegalRepresentativeIdBack() { return present(legalRepIdBackObjectId); }
    @JsonIgnore
    public boolean hasContactIdentity() { return present(contactIdNoEncrypted); }
    @JsonIgnore
    public boolean hasContactPhone() { return present(contactPhoneEncrypted); }
    @JsonIgnore
    public boolean hasShortlinkProof() { return present(shortlinkDomainProofObjectId); }
    @JsonIgnore
    public boolean hasTrademarkProof() { return present(trademarkProofObjectId); }

    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private static boolean present(byte[] value) { return value != null && value.length > 0; }

    /**
     * Assigns the complete protected registration state through the sole persistence adapter.
     * The permit cannot be constructed by controllers or ordinary services, and all mutable
     * inputs are defensively copied before the adapter clears its working buffers.
     */
    @JsonIgnore
    public void assignProtectedRegistration(
            TenantRegistrationProtectionAdapter.PreparedRegistration prepared,
            TenantRegistrationProtectionAdapter.AssignmentPermit permit) {
        if (permit == null || prepared == null
                || prepared.businessLicenseObjectId() == null
                || prepared.legalRepIdFrontObjectId() == null
                || prepared.legalRepIdBackObjectId() == null) {
            throw new IllegalStateException("protected registration assignment is invalid");
        }
        this.legalRepIdNoEncrypted = prepared.copyLegalRepresentativeIdEnvelope();
        this.contactIdNoEncrypted = prepared.copyContactIdEnvelope();
        this.contactPhoneEncrypted = prepared.copyContactPhoneEnvelope();
        this.businessLicenseObjectId = prepared.businessLicenseObjectId();
        this.legalRepIdFrontObjectId = prepared.legalRepIdFrontObjectId();
        this.legalRepIdBackObjectId = prepared.legalRepIdBackObjectId();
        this.shortlinkDomainProofObjectId = prepared.shortlinkDomainProofObjectId();
        this.trademarkProofObjectId = prepared.trademarkProofObjectId();
    }

    /** F-2.8：试用额度是否已耗尽。 */
    public boolean isTrialQuotaExhausted() {
        return trialQuota != null && trialQuotaUsed != null && trialQuotaUsed >= trialQuota;
    }

    /** F-2.8：试用是否已过有效期。 */
    public boolean isTrialExpired() {
        return trialEndAt != null && LocalDateTime.now().isAfter(trialEndAt);
    }
}
