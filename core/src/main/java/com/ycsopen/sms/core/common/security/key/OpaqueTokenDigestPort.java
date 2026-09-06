package com.ycsopen.sms.core.common.security.key;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Purpose-separated keyed digest boundary for server-issued opaque tokens.
 * Complete token strings never cross this port; callers pass only the parsed
 * 32-byte secret and its canonical server-side binding.
 */
public interface OpaqueTokenDigestPort {

    int TOKEN_SECRET_BYTES = 32;

    enum Purpose {
        OBJECT_CAPABILITY((byte) 1, "OBJECT_CAPABILITY_DIGEST"),
        REGISTRATION_UPLOAD((byte) 2, "REGISTRATION_UPLOAD_DIGEST");

        private final byte domainByte;
        private final String storagePurpose;

        Purpose(byte domainByte, String storagePurpose) {
            this.domainByte = domainByte;
            this.storagePurpose = storagePurpose;
        }

        byte domainByte() {
            return domainByte;
        }

        public String storagePurpose() {
            return storagePurpose;
        }
    }

    /** Three unambiguous ASCII components encoded with u16 length prefixes. */
    record Binding(String tenant, String subject, String resourceOrSession) {

        private static final Pattern COMPONENT =
                Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:@/-]{0,255}");

        public Binding {
            requireComponent(tenant);
            requireComponent(subject);
            requireComponent(resourceOrSession);
        }

        byte[] tenantBytes() {
            return tenant.getBytes(StandardCharsets.US_ASCII);
        }

        byte[] subjectBytes() {
            return subject.getBytes(StandardCharsets.US_ASCII);
        }

        byte[] resourceOrSessionBytes() {
            return resourceOrSession.getBytes(StandardCharsets.US_ASCII);
        }

        private static void requireComponent(String value) {
            if (value == null || !COMPONENT.matcher(value).matches()) {
                throw new IllegalArgumentException("invalid token binding");
            }
        }

        @Override
        public String toString() {
            return "Binding[values=[redacted]]";
        }
    }

    VersionedTokenDigest issue(Purpose purpose, Binding binding, byte[] tokenSecret);

    boolean verify(Purpose purpose,
                   Binding binding,
                   byte[] tokenSecret,
                   VersionedTokenDigest storedDigest);

    KeyHealth health(Purpose purpose);
}
