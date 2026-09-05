package com.ycsopen.sms.core.common.security.envelope;

/**
 * Stable failure boundary for malformed or unauthenticated protected data.
 * Deliberately omits parser and cryptographic detail so callers cannot turn failures into an oracle.
 */
public final class ProtectionFailure extends RuntimeException {

    public static final String SANITIZED_MESSAGE = "protected data is invalid";

    public enum Category {
        PROTECTED_DATA_INVALID
    }

    private ProtectionFailure() {
        super(SANITIZED_MESSAGE, null, false, false);
    }

    public Category category() {
        return Category.PROTECTED_DATA_INVALID;
    }

    static ProtectionFailure invalid() {
        return new ProtectionFailure();
    }
}
