package com.ycsopen.sms.core.common.security.key.lifecycle;

/** Persisted lifecycle state for one purpose-bound key reference. */
public enum KeyState {
    PREPARED,
    ACTIVE,
    ROTATION_REQUIRED,
    DECRYPT_ONLY,
    RETIRING,
    RETIRED,
    COMPROMISED;

    public boolean ownsActiveSlot() {
        return this == ACTIVE || this == ROTATION_REQUIRED;
    }

    public boolean permitsWrap() {
        return ownsActiveSlot();
    }

    public boolean permitsUnwrap() {
        return ownsActiveSlot() || this == DECRYPT_ONLY || this == RETIRING;
    }

    public boolean permitsDigestIssue() {
        return this == ACTIVE;
    }

    public boolean permitsDigestVerification() {
        return this == ACTIVE || this == RETIRING;
    }

    public boolean isTerminal() {
        return this == RETIRED || this == COMPROMISED;
    }
}
