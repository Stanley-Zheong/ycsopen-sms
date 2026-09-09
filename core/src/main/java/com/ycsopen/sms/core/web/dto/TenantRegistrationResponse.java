package com.ycsopen.sms.core.web.dto;

import com.ycsopen.sms.core.domain.entity.Tenant;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;

/** Public registration state with no identity plaintext, object ID, token, or storage detail. */
public record TenantRegistrationResponse(
        Long tenantId,
        String tenantNo,
        String shortName,
        String fullName,
        Tenant.VerificationStatus verificationStatus,
        Tenant.LifecycleStatus lifecycleStatus,
        java.time.LocalDateTime submittedAt,
        java.time.LocalDateTime verifiedAt,
        java.time.LocalDateTime verificationUpdatedAt,
        long revision,
        String reason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String unifiedSocialCreditCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) String legalRepresentativeName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String contactName,
        @JsonInclude(JsonInclude.Include.NON_NULL) String registeredCapital,
        @JsonInclude(JsonInclude.Include.NON_NULL) String businessScope,
        @JsonInclude(JsonInclude.Include.NON_NULL) String registeredAddress,
        @JsonInclude(JsonInclude.Include.NON_NULL) String businessAddress,
        @JsonInclude(JsonInclude.Include.NON_NULL) @JsonFormat(shape = JsonFormat.Shape.STRING) java.time.LocalDate licenseValidUntil,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean trademarkUse,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean businessLicensePresent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean legalRepresentativeIdentityPresent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean legalRepresentativeIdFrontPresent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean legalRepresentativeIdBackPresent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean contactIdentityPresent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean contactPhonePresent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean shortlinkProofPresent,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean trademarkProofPresent) {

    /** Public/legacy responses retain their status-only contract; own-status adds safe details. */
    public TenantRegistrationResponse(Long tenantId, String tenantNo, String shortName, String fullName,
                                      Tenant.VerificationStatus verificationStatus, Tenant.LifecycleStatus lifecycleStatus,
                                      java.time.LocalDateTime submittedAt, java.time.LocalDateTime verifiedAt,
                                      java.time.LocalDateTime verificationUpdatedAt, long revision, String reason) {
        this(tenantId, tenantNo, shortName, fullName, verificationStatus, lifecycleStatus,
                submittedAt, verifiedAt, verificationUpdatedAt, revision, reason,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    /** Preserve callers of the existing status projection that have no review feedback. */
    public TenantRegistrationResponse(Long tenantId, String tenantNo, String shortName, String fullName,
                                      Tenant.VerificationStatus verificationStatus, Tenant.LifecycleStatus lifecycleStatus,
                                      java.time.LocalDateTime submittedAt, java.time.LocalDateTime verifiedAt,
                                      java.time.LocalDateTime verificationUpdatedAt, long revision) {
        this(tenantId, tenantNo, shortName, fullName, verificationStatus, lifecycleStatus,
                submittedAt, verifiedAt, verificationUpdatedAt, revision, null);
    }

    public static TenantRegistrationResponse from(Tenant tenant) {
        requirePersisted(tenant);
        return new TenantRegistrationResponse(tenant.getId(), tenant.getTenantNo(),
                tenant.getShortName(), tenant.getFullName(), tenant.getVerificationStatus(),
                tenant.getLifecycleStatus(), tenant.getQualificationSubmittedAt(), tenant.getVerificationTime(),
                tenant.getVerificationUpdatedAt(), tenant.getQualificationRevision(), tenant.getQualificationReason());
    }

    /** Called only after authenticated own-tenant authorization; never returns protected material. */
    public static TenantRegistrationResponse fromOwnStatus(Tenant tenant) {
        requirePersisted(tenant);
        return new TenantRegistrationResponse(tenant.getId(), tenant.getTenantNo(),
                tenant.getShortName(), tenant.getFullName(), tenant.getVerificationStatus(),
                tenant.getLifecycleStatus(), tenant.getQualificationSubmittedAt(), tenant.getVerificationTime(),
                tenant.getVerificationUpdatedAt(), tenant.getQualificationRevision(), tenant.getQualificationReason(),
                tenant.getUnifiedSocialCreditCode(), tenant.getLegalRepName(), tenant.getContactName(),
                tenant.getRegisteredCapital(), tenant.getBusinessScope(), tenant.getRegisteredAddress(),
                tenant.getBusinessAddress(), tenant.getLicenseValidUntil(), tenant.isTrademarkUse(),
                tenant.hasBusinessLicense(), tenant.hasLegalRepresentativeIdentity(), tenant.hasLegalRepresentativeIdFront(),
                tenant.hasLegalRepresentativeIdBack(), tenant.hasContactIdentity(), tenant.hasContactPhone(),
                tenant.hasShortlinkProof(), tenant.hasTrademarkProof());
    }

    private static void requirePersisted(Tenant tenant) {
        if (tenant == null || tenant.getId() == null) throw new IllegalArgumentException("persisted tenant is required");
    }
}
