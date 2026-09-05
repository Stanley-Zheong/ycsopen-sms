package com.ycsopen.sms.core.web.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.regex.Pattern;

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
            @JsonProperty("trademarkProofObjectId") String trademarkProofObjectId) {
        this.shortName = shortName;
        this.fullName = fullName;
        this.unifiedSocialCreditCode = unifiedSocialCreditCode;
        this.registrationObjectSessionId = registrationObjectSessionId;
        this.businessLicenseObjectId = businessLicenseObjectId;
        this.legalRepName = legalRepName;
        this.legalRepIdNo = legalRepIdNo;
        this.legalRepIdFrontObjectId = legalRepIdFrontObjectId;
        this.legalRepIdBackObjectId = legalRepIdBackObjectId;
        this.contactName = contactName;
        this.contactIdNo = contactIdNo;
        this.contactPhone = contactPhone;
        this.shortlinkDomainProofObjectId = shortlinkDomainProofObjectId;
        this.trademarkProofObjectId = trademarkProofObjectId;
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
    public boolean hasUnknownFields() { return unknownFields; }
    public boolean hasLegacyObjectUrlInput() { return legacyObjectUrlInput; }

    private static boolean containsHttpValue(Object value) {
        return value instanceof String text && HTTP_VALUE.matcher(text.trim()).matches();
    }
}
