package com.ycsopen.sms.core.common.security.logging;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A typed, bounded and single-line fact accepted by {@link SecurityEventLogger}. */
public final class SafeLogValue {

    private static final int MAXIMUM_VALUE_LENGTH = 64;
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String UNAVAILABLE = "unavailable";
    private static final String INVALID = "invalid";

    private final Kind kind;
    private final String value;

    private SafeLogValue(Kind kind, String value) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.value = Objects.requireNonNull(value, "value");
    }

    public static SafeLogValue correlation(String value) {
        return new SafeLogValue(Kind.CORRELATION, safeIdentifier(value));
    }

    public static SafeLogValue purpose(String value) {
        return new SafeLogValue(Kind.PURPOSE, safeIdentifier(value));
    }

    /** Accepts an already-computed SHA-256 locator; raw locators are deliberately unsupported. */
    public static SafeLogValue hashedLocator(String sha256) {
        String canonical = sha256 == null ? "" : sha256.toLowerCase(Locale.ROOT);
        return new SafeLogValue(Kind.LOCATOR_SHA256,
                SHA256.matcher(canonical).matches() ? canonical : INVALID);
    }

    public static SafeLogValue status(Enum<?> status) {
        return new SafeLogValue(Kind.STATUS,
                status == null ? UNAVAILABLE : status.name());
    }

    public static SafeLogValue count(long count) {
        if (count < 0) {
            throw new IllegalArgumentException("safe log count must not be negative");
        }
        return new SafeLogValue(Kind.COUNT, Long.toString(count));
    }

    String render() {
        return kind.key + '=' + value;
    }

    @Override
    public String toString() {
        return render();
    }

    private static String safeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE;
        }
        if (value.length() > MAXIMUM_VALUE_LENGTH || !SAFE_IDENTIFIER.matcher(value).matches()) {
            return INVALID;
        }
        return value;
    }

    private enum Kind {
        CORRELATION("correlation"),
        PURPOSE("purpose"),
        LOCATOR_SHA256("locator_sha256"),
        STATUS("status"),
        COUNT("count");

        private final String key;

        Kind(String key) {
            this.key = key;
        }
    }
}
