package com.ycsopen.sms.core.common.security.key.pkcs11;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

/** Converts all provider and metadata failures into a stable redacted boundary. */
public final class Pkcs11FailureMapper {

    public enum Category {
        CONFIGURATION,
        TOKEN_UNAVAILABLE,
        KEY_UNAVAILABLE,
        KEY_POLICY,
        MECHANISM_UNAVAILABLE,
        WRAP_LIMIT_REACHED,
        OPERATION_FAILED
    }

    private final Supplier<String> correlationIds;

    public Pkcs11FailureMapper() {
        SecureRandom random = new SecureRandom();
        this.correlationIds = () -> {
            byte[] value = new byte[16];
            random.nextBytes(value);
            return HexFormat.of().formatHex(value);
        };
    }

    Pkcs11FailureMapper(Supplier<String> correlationIds) {
        this.correlationIds = Objects.requireNonNull(correlationIds, "correlationIds");
    }

    public Pkcs11OperationException failure(Category category,
                                             Pkcs11KeyDescriptor descriptor,
                                             Throwable ignoredInternalFailure) {
        Objects.requireNonNull(category, "category");
        String descriptorHash = descriptor == null ? "none" : descriptor.hashedIdentity();
        String correlation = correlationIds.get();
        if (correlation == null || !correlation.matches("[a-f0-9]{32}")) {
            correlation = "00000000000000000000000000000000";
        }
        return new Pkcs11OperationException(category, correlation, descriptorHash);
    }

    public static final class Pkcs11OperationException extends IllegalStateException {
        private final Category category;
        private final String correlation;
        private final String descriptorHash;

        private Pkcs11OperationException(Category category,
                                         String correlation,
                                         String descriptorHash) {
            super("PKCS11_" + category + " correlation=" + correlation
                    + " descriptor=" + descriptorHash);
            this.category = category;
            this.correlation = correlation;
            this.descriptorHash = descriptorHash;
        }

        public Category category() {
            return category;
        }

        public String correlation() {
            return correlation;
        }

        public String descriptorHash() {
            return descriptorHash;
        }
    }
}
