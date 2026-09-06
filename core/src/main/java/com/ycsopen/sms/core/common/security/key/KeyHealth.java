package com.ycsopen.sms.core.common.security.key;

import java.util.Objects;

/** Sanitized key-provider health with no provider, alias, or key detail. */
public record KeyHealth(Status status) {

    public enum Status {
        READY,
        ROTATION_REQUIRED,
        UNAVAILABLE
    }

    public KeyHealth {
        Objects.requireNonNull(status, "status");
    }

    public boolean permitsNewWrites() {
        return status != Status.UNAVAILABLE;
    }

    @Override
    public String toString() {
        return "KeyHealth[status=" + status + "]";
    }
}
