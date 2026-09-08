package com.ycsopen.sms.core.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.regex.Pattern;
import java.time.LocalDate;

/**
 * Fixed F-2.1 registration wire contract.
 *
 * <p>Evidence is represented only by a staged session and opaque protected-object IDs. Unknown
 * input is recorded as a rejection flag rather than retained, and legacy URL-shaped values are
 * distinguished so the controller can return the stable compatibility error.</p>
 */
public final class TenantRegistrationRequest {

    private static final Pattern HTTP_VALUE = Pattern.compile("(?i)^https?://.*");

    private final String shortName;
    private final String fullName;
    private final String unifiedSocialCreditCode;
    private final String registrationObjectSessionId;
    private final String businessLicenseObjectId;
    private final String legalRepName;
    private final String legalRepIdNo;
    private final String legalRepIdFrontObjectId;
    private final String legalRepIdBackObjectId;
    private final String contactName;
    private final String contactIdNo;
    private final String contactPhone;
    private final String shortlinkDomainProofObjectId;
    private final String trademarkProofObjectId;
    private final boolean trademarkUse;
    private final String registeredCapital;
    private final String businessScope;
    private final String registeredAddress;
    private final String businessAddress;
    private final LocalDate licenseValidUntil;
    private boolean unknownFields;
    private boolean legacyObjectUrlInput;

    @JsonCreator
    public TenantRegistrationRequest(
            @JsonProperty("shortName") String shortName,
            @JsonProperty("fullName") String fullName,
            @JsonProperty("unifiedSocialCreditCode") String unifiedSocialCreditCode,
            @JsonProperty("registrationObjectSessionId") String registrationObjectSessionId,
            @JsonProperty("businessLicenseObjectId") String businessLicenseObjectId,
            @JsonProperty("legalRepName") String legalRepName,
            @JsonProperty("legalRepIdNo") String legalRepIdNo,
            @JsonProperty("legalRepIdFrontObjectId") String legalRepIdFrontObjectId,
            @JsonProperty("legalRepIdBackObjectId") String legalRepIdBackObjectId,
            @JsonProperty("contactName") String contactName,
            @JsonProperty("contactIdNo") String contactIdNo,
            @JsonProperty("contactPhone") String contactPhone,
            @JsonProperty("shortlinkDomainProofObjectId") String shortlinkDomainProofObjectId,
            @JsonProperty("trademarkProofObjectId") String trademarkProofObjectId,
            @JsonProperty("trademarkUse") Boolean trademarkUse,
            @JsonProperty("registeredCapital") String registeredCapital,
            @JsonProperty("businessScope") String businessScope,
            @JsonProperty("registeredAddress") String registeredAddress,
            @JsonProperty("businessAddress") String businessAddress,
            @JsonProperty("licenseValidUntil") LocalDate licenseValidUntil) {
        this.shortName = trim(shortName);
        this.fullName = trim(fullName);
        this.unifiedSocialCreditCode = upper(unifiedSocialCreditCode);
        this.registrationObjectSessionId = registrationObjectSessionId;
        this.businessLicenseObjectId = businessLicenseObjectId;
        this.legalRepName = trim(legalRepName);
        this.legalRepIdNo = upper(legalRepIdNo);
        this.legalRepIdFrontObjectId = legalRepIdFrontObjectId;
        this.legalRepIdBackObjectId = legalRepIdBackObjectId;
        this.contactName = trim(contactName);
        this.contactIdNo = upper(contactIdNo);
        this.contactPhone = trim(contactPhone);
        this.shortlinkDomainProofObjectId = shortlinkDomainProofObjectId;
        this.trademarkProofObjectId = trademarkProofObjectId;
        this.trademarkUse = Boolean.TRUE.equals(trademarkUse);
        this.registeredCapital = trim(registeredCapital);
        this.businessScope = trim(businessScope);
        this.registeredAddress = trim(registeredAddress);
        this.businessAddress = trim(businessAddress);
        this.licenseValidUntil = licenseValidUntil;
    }

    /** The legacy Phase 03 protection path remains callable without invented business values. */
    public TenantRegistrationRequest(String shortName, String fullName, String unifiedSocialCreditCode,
                                     String registrationObjectSessionId, String businessLicenseObjectId,
                                     String legalRepName, String legalRepIdNo,
                                     String legalRepIdFrontObjectId, String legalRepIdBackObjectId,
                                     String contactName, String contactIdNo, String contactPhone,
                                     String shortlinkDomainProofObjectId, String trademarkProofObjectId,
                                     Boolean trademarkUse) {
        this(shortName, fullName, unifiedSocialCreditCode, registrationObjectSessionId,
                businessLicenseObjectId, legalRepName, legalRepIdNo, legalRepIdFrontObjectId,
                legalRepIdBackObjectId, contactName, contactIdNo, contactPhone,
                shortlinkDomainProofObjectId, trademarkProofObjectId, trademarkUse,
                null, null, null, null, null);
    }

    /** Compatibility constructor for Phase 03 callers that did not declare trademark use. */
    public TenantRegistrationRequest(String shortName, String fullName, String unifiedSocialCreditCode,
                                     String registrationObjectSessionId, String businessLicenseObjectId,
                                     String legalRepName, String legalRepIdNo,
                                     String legalRepIdFrontObjectId, String legalRepIdBackObjectId,
                                     String contactName, String contactIdNo, String contactPhone,
                                     String shortlinkDomainProofObjectId, String trademarkProofObjectId) {
        this(shortName, fullName, unifiedSocialCreditCode, registrationObjectSessionId,
                businessLicenseObjectId, legalRepName, legalRepIdNo, legalRepIdFrontObjectId,
                legalRepIdBackObjectId, contactName, contactIdNo, contactPhone,
                shortlinkDomainProofObjectId, trademarkProofObjectId, false);
    }

    /** Drops every unknown value while retaining only its rejection class. */
    @JsonAnySetter
    public void rejectUnknown(String fieldName, Object value) {
        unknownFields = true;
        if ((fieldName != null && fieldName.endsWith("Url")) || containsHttpValue(value)) {
            legacyObjectUrlInput = true;
        }
    }

    public String shortName() { return shortName; }
    public String fullName() { return fullName; }
    public String unifiedSocialCreditCode() { return unifiedSocialCreditCode; }
    public String registrationObjectSessionId() { return registrationObjectSessionId; }
    public String businessLicenseObjectId() { return businessLicenseObjectId; }
    public String legalRepName() { return legalRepName; }
    public String legalRepIdNo() { return legalRepIdNo; }
    public String legalRepIdFrontObjectId() { return legalRepIdFrontObjectId; }
    public String legalRepIdBackObjectId() { return legalRepIdBackObjectId; }
    public String contactName() { return contactName; }
    public String contactIdNo() { return contactIdNo; }
    public String contactPhone() { return contactPhone; }
    public String shortlinkDomainProofObjectId() { return shortlinkDomainProofObjectId; }
    public String trademarkProofObjectId() { return trademarkProofObjectId; }
    public boolean trademarkUse() { return trademarkUse; }
    public String registeredCapital() { return registeredCapital; }
    public String businessScope() { return businessScope; }
    public String registeredAddress() { return registeredAddress; }
    public String businessAddress() { return businessAddress; }
    public LocalDate licenseValidUntil() { return licenseValidUntil; }

    /** Returns the same safe wire model with the explicit trademark-proof condition enabled. */
    public TenantRegistrationRequest withTrademarkUse(boolean value) {
        TenantRegistrationRequest copy = new TenantRegistrationRequest(shortName, fullName, unifiedSocialCreditCode,
                registrationObjectSessionId, businessLicenseObjectId, legalRepName, legalRepIdNo,
                legalRepIdFrontObjectId, legalRepIdBackObjectId, contactName, contactIdNo,
                contactPhone, shortlinkDomainProofObjectId, trademarkProofObjectId, value,
                registeredCapital, businessScope, registeredAddress, businessAddress, licenseValidUntil);
        copy.unknownFields = unknownFields;
        copy.legacyObjectUrlInput = legacyObjectUrlInput;
        return copy;
    }
    public boolean hasUnknownFields() { return unknownFields; }
    public boolean hasLegacyObjectUrlInput() { return legacyObjectUrlInput; }

    private static boolean containsHttpValue(Object value) {
        return value instanceof String text && HTTP_VALUE.matcher(text.trim()).matches();
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT); }
}
