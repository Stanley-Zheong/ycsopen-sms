package com.ycsopen.sms.core.common.security.key;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Opaque, version-aware mobile equality-index boundary. */
public interface BlindIndexPort {

    enum Purpose {
        MOBILE_ROUTING("mobile-routing");

        private final String wireValue;

        Purpose(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }
    }

    /** Canonical metadata bound into every mobile-sha256-v1 HMAC input. */
    record Context(String targetType, String field, Purpose purpose, String scope) {

        private static final Pattern COMPONENT = Pattern.compile("[A-Z0-9][A-Z0-9_]{0,63}");
        private static final Pattern FIELD = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
        private static final Pattern SCOPE = Pattern.compile("(?:global|tenant:[A-Za-z0-9][A-Za-z0-9._-]{0,127})");

        public Context {
            if (targetType == null || !COMPONENT.matcher(targetType).matches()
                    || field == null || !FIELD.matcher(field).matches()
                    || purpose == null
                    || scope == null || !SCOPE.matcher(scope).matches()) {
                throw new IllegalArgumentException("invalid blind-index context");
            }
        }

        byte[] targetTypeBytes() {
            return targetType.getBytes(StandardCharsets.US_ASCII);
        }

        byte[] fieldBytes() {
            return field.getBytes(StandardCharsets.US_ASCII);
        }

        byte[] purposeBytes() {
            return purpose.wireValue.getBytes(StandardCharsets.US_ASCII);
        }

        byte[] scopeBytes() {
            return scope.getBytes(StandardCharsets.US_ASCII);
        }
    }

    /** Immutable ascending set; duplicate key versions fail closed. */
    record OrderedIndexes(List<VersionedBlindIndex> values) {

        public OrderedIndexes {
            Objects.requireNonNull(values, "values");
            values = List.copyOf(values);
            if (values.isEmpty()) {
                throw new IllegalArgumentException("blind-index set is empty");
            }
            Set<Integer> versions = new HashSet<>();
            int previous = 0;
            for (VersionedBlindIndex value : values) {
                Objects.requireNonNull(value, "blind index");
                if (!versions.add(value.keyVersion()) || value.keyVersion() <= previous) {
                    throw new IllegalArgumentException("blind-index versions are not canonical");
                }
                previous = value.keyVersion();
            }
        }

        @Override
        public String toString() {
            return "OrderedIndexes[count=" + values.size() + ", values=[redacted]]";
        }
    }

    OrderedIndexes writeIndexes(String normalizedMobile, Context context);

    OrderedIndexes queryIndexes(String normalizedMobile, Context context);

    KeyHealth health();
}
