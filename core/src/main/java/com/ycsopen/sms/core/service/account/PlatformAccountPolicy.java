package com.ycsopen.sms.core.service.account;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Phase 5 validation contract for platform account fields. */
public final class PlatformAccountPolicy {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{4,20}");
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern UPPER = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWER = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");

    private PlatformAccountPolicy() { }

    public static void validate(String username, String password, String phone,
                                String userType, LocalDate validity, LocalDate today) {
        validateProfile(username, phone, userType, validity, today);
        validatePassword(password);
    }

    public static void validateProfile(String username, String phone,
                                       String userType, LocalDate validity, LocalDate today) {
        validateEditableProfile(username, userType, validity, today);
        validatePhone(phone);
    }

    public static void validateEditableProfile(String username, String userType,
                                               LocalDate validity, LocalDate today) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("username must be 4-20 letters, digits, or underscores");
        }
        if (!("ADMIN".equals(userType) || "OPERATOR".equals(userType) || "FINANCE".equals(userType))) {
            throw new IllegalArgumentException("unsupported platform user type");
        }
        if (today == null || validity != null && validity.isBefore(today)) {
            throw new IllegalArgumentException("validity cannot be in the past");
        }
    }

    public static void validatePhone(String phone) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new IllegalArgumentException("phone must be a domestic 11-digit number");
        }
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8 || !UPPER.matcher(password).matches()
                || !LOWER.matcher(password).matches() || !DIGIT.matcher(password).matches()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("password does not meet complexity policy");
        }
    }

    public static void validateRealName(String realName) {
        if (realName == null || realName.isBlank() || realName.trim().length() > 50) {
            throw new IllegalArgumentException("real name is required and limited to 50 characters");
        }
    }

    public static void validateEmail(String email) {
        if (email != null && email.trim().length() > 100) {
            throw new IllegalArgumentException("email is limited to 100 characters");
        }
    }

    public static boolean fitsBcrypt(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
