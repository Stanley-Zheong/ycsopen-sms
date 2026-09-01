package com.ycsopen.sms.core.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @Column(name = "contact_name")
    private String contactName;

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

    public enum VerificationStatus { UNVERIFIED, PENDING, VERIFIED, REJECTED }
    public enum LifecycleStatus { SUBMITTED, TRIAL, TRIAL_FROZEN, SIGNED, FROZEN, TERMINATED }
    public enum BillingMode { PREPAID, POSTPAID }

    /**
     * Temporary write-only compatibility seam for the current registration service.
     * Plan 03-29 replaces the request writer with the purpose-bound object claim owner.
     */
    @Deprecated(forRemoval = true)
    @JsonIgnore
    public void setBusinessLicenseUrl(String objectId) {
        this.businessLicenseObjectId = objectId;
    }

    /** @see #setBusinessLicenseUrl(String) */
    @Deprecated(forRemoval = true)
    @JsonIgnore
    public void setShortlinkDomainProofUrl(String objectId) {
        this.shortlinkDomainProofObjectId = objectId;
    }

    /** @see #setBusinessLicenseUrl(String) */
    @Deprecated(forRemoval = true)
    @JsonIgnore
    public void setTrademarkProofUrl(String objectId) {
        this.trademarkProofObjectId = objectId;
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
