package com.ycsopen.sms.core.common.security.key;

import java.util.Arrays;
import java.util.Objects;

/**
 * One canonical mobile blind index: one version byte plus a 32-byte HMAC,
 * encoded as 53 lowercase RFC 4648 Base32 characters without padding.
 */
public final class VersionedBlindIndex {

    public static final int HMAC_BYTES = 32;
    public static final int CANONICAL_CHARACTERS = 53;
    private static final char[] BASE32 = "abcdefghijklmnopqrstuvwxyz234567".toCharArray();

    private final int keyVersion;
    private final String canonicalValue;

    public VersionedBlindIndex(int keyVersion, byte[] hmac) {
        if (keyVersion < 1 || keyVersion > 255 || hmac == null || hmac.length != HMAC_BYTES) {
            throw new IllegalArgumentException("invalid blind index");
        }
        this.keyVersion = keyVersion;
        byte[] canonicalBytes = new byte[HMAC_BYTES + 1];
        canonicalBytes[0] = (byte) keyVersion;
        System.arraycopy(hmac, 0, canonicalBytes, 1, HMAC_BYTES);
        this.canonicalValue = encodeBase32(canonicalBytes);
        Arrays.fill(canonicalBytes, (byte) 0);
    }

    public int keyVersion() {
        return keyVersion;
    }

    public String canonicalValue() {
        return canonicalValue;
    }

    private static String encodeBase32(byte[] input) {
        StringBuilder output = new StringBuilder(CANONICAL_CHARACTERS);
        int buffer = 0;
        int bufferedBits = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | Byte.toUnsignedInt(value);
            bufferedBits += 8;
            while (bufferedBits >= 5) {
                output.append(BASE32[(buffer >>> (bufferedBits - 5)) & 0x1f]);
                bufferedBits -= 5;
            }
        }
        if (bufferedBits > 0) {
            output.append(BASE32[(buffer << (5 - bufferedBits)) & 0x1f]);
        }
        if (output.length() != CANONICAL_CHARACTERS) {
            throw new IllegalStateException("blind index encoding failed");
        }
        return output.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VersionedBlindIndex that)) {
            return false;
        }
        return keyVersion == that.keyVersion && canonicalValue.equals(that.canonicalValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyVersion, canonicalValue);
    }

    @Override
    public String toString() {
        return "VersionedBlindIndex[keyVersion=" + keyVersion + ", value=[redacted]]";
    }
}
