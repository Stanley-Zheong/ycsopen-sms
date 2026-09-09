package com.ycsopen.sms.core.service.channel;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChannelConfigurationSnapshotRegistry {
    private final ConcurrentHashMap<Long, Snapshot> snapshots = new ConcurrentHashMap<>();

    public void swap(Snapshot snapshot) {
        snapshots.put(snapshot.channelId(), snapshot);
    }

    public Snapshot effective(long channelId) {
        return snapshots.get(channelId);
    }

    public void restore(long channelId, Snapshot previous) {
        if (previous == null) {
            snapshots.remove(channelId);
        } else {
            snapshots.put(channelId, previous);
        }
    }

    public record Snapshot(long channelId, long versionId, String name, String protocol, String operator,
                           String host, Integer port, String spId, String serviceId, String srcId,
                           int maxConnections, int windowSize, int tpsLimit,
                           BigDecimal price, int priority, String activeWindow,
                           String availability, Map<String, Object> extraConfig) { }
}
