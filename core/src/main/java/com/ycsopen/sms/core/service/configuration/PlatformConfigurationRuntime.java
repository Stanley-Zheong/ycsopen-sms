package com.ycsopen.sms.core.service.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** One validated immutable process-local configuration snapshot. */
@Component
public final class PlatformConfigurationRuntime {
    private final PlatformConfigurationRegistry registry;
    private final ReloadGuard reloadGuard;
    private final AtomicReference<Snapshot> current;

    @Autowired
    public PlatformConfigurationRuntime(PlatformConfigurationRegistry registry) {
        this(registry, prepared -> { });
    }

    PlatformConfigurationRuntime(PlatformConfigurationRegistry registry, ReloadGuard reloadGuard) {
        this.registry = registry;
        this.reloadGuard = reloadGuard;
        this.current = new AtomicReference<>(new Snapshot(0, registry.defaults()));
    }

    public Prepared prepare(long version, Map<String, String> values) {
        Prepared prepared = new Prepared(version, registry.normalizeStored(values));
        reloadGuard.verify(prepared);
        return prepared;
    }

    public void apply(Prepared prepared) {
        current.updateAndGet(existing -> prepared.version() > existing.version()
                ? new Snapshot(prepared.version(), prepared.values())
                : existing);
    }

    public long version() {
        return current.get().version();
    }

    public int loginMaxFailures() {
        return Integer.parseInt(current.get().values().get(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES));
    }

    public boolean unusualIpEnabled() {
        return Boolean.parseBoolean(current.get().values().get(PlatformConfigurationRegistry.UNUSUAL_IP_ENABLED));
    }

    public Map<String, String> values() {
        return current.get().values();
    }

    @FunctionalInterface
    interface ReloadGuard {
        void verify(Prepared prepared);
    }

    public record Prepared(long version, Map<String, String> values) {
        public Prepared {
            values = Map.copyOf(values);
        }
    }

    private record Snapshot(long version, Map<String, String> values) {
        private Snapshot {
            values = Map.copyOf(values);
        }
    }

    public static final class ReloadRejected extends RuntimeException {
        private final String safeCode;

        public ReloadRejected(String safeCode) {
            super("runtime configuration reload rejected");
            this.safeCode = safeCode;
        }

        public String safeCode() {
            return safeCode;
        }
    }
}
