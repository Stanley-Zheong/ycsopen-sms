package com.ycsopen.sms.core.notification.provider;

import java.util.HashSet;
import java.util.Set;

/** In-process fence preventing the same bootstrap request from re-entering dispatch. */
public class PlatformMessageBootstrapRecursionGuard {
    private final Set<String> activeRequests = new HashSet<>();

    public synchronized boolean enter(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        return activeRequests.add(requestId);
    }

    public synchronized void exit(String requestId) {
        activeRequests.remove(requestId);
    }

    public boolean isActive(String requestId) {
        return activeRequests.contains(requestId);
    }
}
