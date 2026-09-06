package com.ycsopen.sms.core.common.security.key.pkcs11;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable, non-secret metadata for one purpose-bound PKCS11 key handle. */
public record Pkcs11KeyDescriptor(Purpose purpose,
                                  long keyVersion,
                                  String keyReference,
                                  String alias,
                                  State state,
                                  String algorithm,
                                  int keyBits) {

    private static final Pattern KEY_REFERENCE = Pattern.compile("[a-z0-9][a-z0-9._-]{0,31}");
    private static final Pattern ALIAS = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");

    public enum Purpose {
        FIELD_ENCRYPTION_KEK("FIELD_ENCRYPTION_KEK", "AES"),
        SNAPSHOT_RECOVERY("SNAPSHOT_RECOVERY", "AES"),
        MOBILE_BLIND_INDEX("MOBILE_BLIND_INDEX", "HmacSHA256"),
        OBJECT_CAPABILITY_DIGEST("OBJECT_CAPABILITY_DIGEST", "HmacSHA256"),
        REGISTRATION_UPLOAD_DIGEST("REGISTRATION_UPLOAD_DIGEST", "HmacSHA256");

        private final String storageValue;
        private final String algorithm;

        Purpose(String storageValue, String algorithm) {
            this.storageValue = storageValue;
            this.algorithm = algorithm;
        }

        public String storageValue() {
            return storageValue;
        }

        String algorithm() {
            return algorithm;
        }

        public boolean isWrappingKey() {
            return this == FIELD_ENCRYPTION_KEK || this == SNAPSHOT_RECOVERY;
        }
    }

    public enum State {
        PREPARED,
        ACTIVE,
        ROTATION_REQUIRED,
        DECRYPT_ONLY,
        RETIRING,
        RETIRED,
        COMPROMISED;

        boolean permitsWrap() {
            return this == ACTIVE || this == ROTATION_REQUIRED;
        }

        boolean permitsUnwrap() {
            return this == ACTIVE || this == ROTATION_REQUIRED || this == DECRYPT_ONLY
                    || this == RETIRING;
        }

        boolean permitsDigestIssue() {
            return this == ACTIVE;
        }

        boolean permitsDigestVerify() {
            return this == ACTIVE || this == RETIRING;
        }
    }

    public Pkcs11KeyDescriptor {
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(state, "state");
        if (keyVersion < 1
                || purpose == Purpose.MOBILE_BLIND_INDEX && keyVersion > 255
                || keyReference == null || !KEY_REFERENCE.matcher(keyReference).matches()
                || alias == null || !ALIAS.matcher(alias).matches()
                || algorithm == null || !purpose.algorithm().equalsIgnoreCase(algorithm)
                || keyBits != 256) {
            throw new IllegalArgumentException("invalid PKCS11 key descriptor");
        }
        algorithm = purpose.algorithm();
    }

    String canonicalIdentity() {
        return purpose.storageValue() + '\0' + keyVersion + '\0' + keyReference + '\0'
                + alias.toLowerCase(Locale.ROOT) + '\0' + state + '\0' + algorithm + '\0' + keyBits;
    }

    public String hashedIdentity() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalIdentity().getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("PKCS11 descriptor hash unavailable");
        }
    }

    @Override
    public String toString() {
        return "Pkcs11KeyDescriptor[purpose=" + purpose + ", keyVersion=" + keyVersion
                + ", identity=" + hashedIdentity() + "]";
    }
}
