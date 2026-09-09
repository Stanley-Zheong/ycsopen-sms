package com.ycsopen.sms.core.cmpp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/** Tenant-to-session and durable pending-delivery registry for downstream CMPP sessions. */
public final class CmppDownstreamSessionRegistry {
    private final Map<Long, CmppDownstreamGatewaySession> activeSessions = new LinkedHashMap<>();
    private final Map<Long, Queue<PendingDelivery>> pendingDeliveries = new LinkedHashMap<>();

    public synchronized void register(long tenantId, CmppDownstreamGatewaySession session) {
        activeSessions.put(tenantId, session);
    }

    public synchronized void unregister(long tenantId, CmppDownstreamGatewaySession session) {
        if (activeSessions.get(tenantId) == session) {
            activeSessions.remove(tenantId);
        }
    }

    public synchronized boolean hasActiveSession(long tenantId) {
        return activeSessions.containsKey(tenantId);
    }

    public synchronized void queueReport(PendingDelivery delivery) {
        pendingDeliveries.computeIfAbsent(delivery.tenantId(), ignored -> new ArrayDeque<>()).add(delivery);
    }

    public synchronized List<PendingDelivery> claim(long tenantId, int limit) {
        Queue<PendingDelivery> queue = pendingDeliveries.get(tenantId);
        if (queue == null || queue.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<PendingDelivery> claimed = new ArrayList<>();
        while (claimed.size() < limit && !queue.isEmpty()) {
            claimed.add(queue.remove());
        }
        if (queue.isEmpty()) {
            pendingDeliveries.remove(tenantId);
        }
        return claimed;
    }

    public synchronized void returnToPending(long tenantId, Iterable<PendingDelivery> deliveries) {
        Queue<PendingDelivery> queue = pendingDeliveries.computeIfAbsent(tenantId, ignored -> new ArrayDeque<>());
        for (PendingDelivery delivery : deliveries) {
            queue.add(delivery);
        }
    }

    public synchronized int pendingCount(long tenantId) {
        Queue<PendingDelivery> queue = pendingDeliveries.get(tenantId);
        return queue == null ? 0 : queue.size();
    }

    public record PendingDelivery(long tenantId, String providerMessageId, String messageId, String providerStatus) { }
}
