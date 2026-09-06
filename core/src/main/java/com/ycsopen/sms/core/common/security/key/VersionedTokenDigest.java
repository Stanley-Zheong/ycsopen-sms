package com.ycsopen.sms.core.common.security.key;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/** Immutable stored form of a purpose-bound opaque-token digest. */
public final class VersionedTokenDigest {

    public static final int DIGEST_BYTES = 32;

    private final OpaqueTokenDigestPort.Purpose purpose;
    private final long keyVersion;
    private final byte[] digest;

    public VersionedTokenDigest(OpaqueTokenDigestPort.Purpose purpose,
                                long keyVersion,
                                byte[] digest) {
        if (purpose == null || keyVersion < 1 || digest == null || digest.length != DIGEST_BYTES) {
            throw new IllegalArgumentException("invalid token digest");
        }
        this.purpose = purpose;
        this.keyVersion = keyVersion;
        this.digest = digest.clone();
    }

    public OpaqueTokenDigestPort.Purpose purpose() {
        return purpose;
    }

    public long keyVersion() {
        return keyVersion;
    }

    public byte[] digest() {
        return digest.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VersionedTokenDigest that)) {
            return false;
        }
        return keyVersion == that.keyVersion
                && purpose == that.purpose
                && MessageDigest.isEqual(digest, that.digest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(purpose, keyVersion, Arrays.hashCode(digest));
    }

    @Override
    public String toString() {
        return "VersionedTokenDigest[purpose=" + purpose + ", keyVersion=" + keyVersion
                + ", digest=[redacted]]";
    }
}
