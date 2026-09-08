package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;

/** Server-side authority for qualification field validation before protected data is claimed. */
public final class TenantQualificationValidator {
    private static final String CREDIT_ALPHABET = "0123456789ABCDEFGHJKLMNPQRTUWXY";
    private static final int[] ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECKS = "10X98765432".toCharArray();
    private static final Pattern CREDIT_CODE = Pattern.compile("[0-9A-Z]{18}");
    private static final Pattern ID_NUMBER = Pattern.compile("[0-9]{17}[0-9X]");
    private static final Pattern PHONE = Pattern.compile("1[3-9][0-9]{9}");

    public void validate(TenantRegistrationRequest request) {
        if (request == null) {
            throw new ValidationFailure("INVALID_QUALIFICATION_REQUEST");
        }
        requireText(request.shortName(), 20);
        requireText(request.fullName(), 100);
        requireText(request.legalRepName(), 50);
        requireText(request.contactName(), 50);
        // Capital is license text (including currency/units); TEXT scope is capped below MySQL's
        // byte capacity even for four-byte UTF-8 characters. A dated license is valid through today.
        requireText(request.registeredCapital(), 50);
        requireText(request.businessScope(), 10_000);
        requireText(request.registeredAddress(), 255);
        requireText(request.businessAddress(), 255);
        LocalDate licenseExpiry = request.licenseValidUntil();
        if (licenseExpiry == null || licenseExpiry.isBefore(LocalDate.now()) || licenseExpiry.getYear() > 9999) {
            throw new ValidationFailure("INVALID_LICENSE_VALID_UNTIL");
        }
        String creditCode = requiredUpper(request.unifiedSocialCreditCode(), CREDIT_CODE,
                "INVALID_UNIFIED_SOCIAL_CREDIT_CODE");
        if (!hasValidCreditCodeCheckDigit(creditCode)) {
            throw new ValidationFailure("INVALID_UNIFIED_SOCIAL_CREDIT_CODE");
        }
        validateResidentId(request.legalRepIdNo());
        validateResidentId(request.contactIdNo());
        if (request.contactPhone() == null || !PHONE.matcher(request.contactPhone()).matches()) {
            throw new ValidationFailure("INVALID_CONTACT_PHONE");
        }
        if (request.trademarkUse() && blank(request.trademarkProofObjectId())) {
            throw new ValidationFailure("TRADEMARK_PROOF_REQUIRED");
        }
    }

    private static void validateResidentId(String value) {
        String identity = requiredUpper(value, ID_NUMBER, "INVALID_RESIDENT_ID");
        try {
            LocalDate.parse(identity.substring(6, 14), java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeException exception) {
            throw new ValidationFailure("INVALID_RESIDENT_ID");
        }
        int total = 0;
        for (int index = 0; index < ID_WEIGHTS.length; index++) {
            total += (identity.charAt(index) - '0') * ID_WEIGHTS[index];
        }
        if (ID_CHECKS[total % 11] != identity.charAt(17)) {
            throw new ValidationFailure("INVALID_RESIDENT_ID");
        }
    }

    private static boolean hasValidCreditCodeCheckDigit(String code) {
        int total = 0;
        for (int index = 0; index < 17; index++) {
            int position = CREDIT_ALPHABET.indexOf(code.charAt(index));
            if (position < 0) {
                return false;
            }
            total += position * (int) Math.pow(3, index) % 31;
        }
        int expected = (31 - total % 31) % 31;
        return expected < CREDIT_ALPHABET.length() && CREDIT_ALPHABET.charAt(expected) == code.charAt(17);
    }

    private static String requiredUpper(String value, Pattern pattern, String failure) {
        String normalized = value == null ? null : value.trim().toUpperCase(Locale.ROOT);
        if (normalized == null || !pattern.matcher(normalized).matches()) {
            throw new ValidationFailure(failure);
        }
        return normalized;
    }

    private static void requireText(String value, int maximum) {
        if (blank(value) || value.trim().length() > maximum) {
            throw new ValidationFailure("INVALID_QUALIFICATION_REQUEST");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** Stable, safe failure category: it never echoes submitted protected values. */
    public static final class ValidationFailure extends RuntimeException {
        public ValidationFailure(String code) {
            super(code);
        }
    }
}
