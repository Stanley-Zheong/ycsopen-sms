package com.ycsopen.sms.core.cmpp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Tracks active downstream CMPP sessions per credential. */
public final class CmppDownstreamConnectionRegistry {
    private final Map<Long, Integer> activeConnections = new LinkedHashMap<>();

    public synchronized boolean tryOpen(long credentialId, int maxConnections) {
        int current = activeConnections.getOrDefault(credentialId, 0);
        if (current >= maxConnections) {
            return false;
        }
        activeConnections.put(credentialId, current + 1);
        return true;
    }

    public synchronized void close(long credentialId) {
        int current = activeConnections.getOrDefault(credentialId, 0);
        if (current <= 1) {
            activeConnections.remove(credentialId);
        } else {
            activeConnections.put(credentialId, current - 1);
        }
    }

    public synchronized int active(long credentialId) {
        return activeConnections.getOrDefault(credentialId, 0);
    }
}
