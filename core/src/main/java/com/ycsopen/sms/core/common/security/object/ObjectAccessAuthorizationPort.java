package com.ycsopen.sms.core.common.security.object;

import java.time.Instant;
import java.util.Objects;

/**
 * Narrow server-side policy seam for protected-object access before Phase 6.
 * Implementations receive only capability metadata and request bindings, never
 * the complete capability token or its secret.
 */
@FunctionalInterface
public interface ObjectAccessAuthorizationPort {

    boolean authorize(Request request);

    enum CapabilityState {
        ACTIVE,
        REVOKED,
        EXPIRED
    }

    record Request(String protectedObjectId,
                   String tenant,
                   String subject,
                   String purpose,
                   CapabilityState capabilityState,
                   Instant expiresAt) {

        public Request {
            Objects.requireNonNull(protectedObjectId, "protectedObjectId");
            Objects.requireNonNull(tenant, "tenant");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(capabilityState, "capabilityState");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }

        @Override
        public String toString() {
            return "Request[bindings=[redacted], purpose=" + purpose
                    + ", capabilityState=" + capabilityState + ", expiresAt=" + expiresAt + "]";
        }
    }
}
