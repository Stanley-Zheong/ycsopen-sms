package com.ycsopen.sms.core.common.security.key;

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable adapter-owned result of one admitted DEK-wrap operation. */
public final class WrappedDataKey {

    public static final int WRAP_NONCE_BYTES = 12;
    public static final int WRAPPED_DEK_BYTES = 48;
    private static final Pattern KEY_REFERENCE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");

    private final String keyReference;
    private final byte[] wrapNonce;
    private final byte[] wrappedDek;

    public WrappedDataKey(String keyReference, byte[] wrapNonce, byte[] wrappedDek) {
        if (keyReference == null || !KEY_REFERENCE.matcher(keyReference).matches()) {
            throw new IllegalArgumentException("invalid wrapped data key");
        }
        requireLength(wrapNonce, WRAP_NONCE_BYTES);
        requireLength(wrappedDek, WRAPPED_DEK_BYTES);
        this.keyReference = keyReference;
        this.wrapNonce = wrapNonce.clone();
        this.wrappedDek = wrappedDek.clone();
    }

    public String keyReference() {
        return keyReference;
    }

    public byte[] wrapNonce() {
        return wrapNonce.clone();
    }

    public byte[] wrappedDek() {
        return wrappedDek.clone();
    }

    private static void requireLength(byte[] value, int expectedLength) {
        if (value == null || value.length != expectedLength) {
            throw new IllegalArgumentException("invalid wrapped data key");
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WrappedDataKey that)) {
            return false;
        }
        return keyReference.equals(that.keyReference)
                && Arrays.equals(wrapNonce, that.wrapNonce)
                && Arrays.equals(wrappedDek, that.wrappedDek);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(keyReference);
        result = 31 * result + Arrays.hashCode(wrapNonce);
        result = 31 * result + Arrays.hashCode(wrappedDek);
        return result;
    }

    @Override
    public String toString() {
        return "WrappedDataKey[keyReference=[redacted], bytes=[redacted]]";
    }
}
