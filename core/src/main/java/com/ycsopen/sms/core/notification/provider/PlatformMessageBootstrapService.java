package com.ycsopen.sms.core.notification.provider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Consumer;

/** Thin orchestration service for provider-backed platform bootstrap messages. */
@Service
@ConditionalOnBean(PlatformNotificationSpi.class)
public class PlatformMessageBootstrapService {
    private final PlatformNotificationSpi provider;
    private final PlatformMessageBootstrapRecursionGuard guard;
    private final Consumer<PlatformNotificationAudit> auditSink;

    @Autowired
    public PlatformMessageBootstrapService(PlatformNotificationSpi provider) {
        this(provider, new PlatformMessageBootstrapRecursionGuard(), audit -> { });
    }

    public PlatformMessageBootstrapService(PlatformNotificationSpi provider,
                                           PlatformMessageBootstrapRecursionGuard guard,
                                           Consumer<PlatformNotificationAudit> auditSink) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.guard = Objects.requireNonNull(guard, "guard");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    public PlatformNotificationSpi.DeliveryOutcome send(String recipient,
                                                        PlatformNotificationTemplate template,
                                                        String content,
                                                        String requestId) {
        if (!guard.enter(requestId)) {
            var blocked = PlatformNotificationSpi.DeliveryOutcome.blocked("RECURSION");
            auditSink.accept(new PlatformNotificationAudit(requestId, template.code(), recipient,
                    blocked.status(), "NONE", "BLOCKED_REENTRY"));
            return blocked;
        }
        try {
            var outcome = provider.send(new PlatformNotificationSpi.NotificationRequest(
                    recipient, template, content, requestId));
            auditSink.accept(new PlatformNotificationAudit(requestId, template.code(), recipient,
                    outcome.status(), "NONE", "ENTERED"));
            return outcome;
        } catch (RuntimeException failure) {
            var retry = PlatformMessageBootstrapRecursionGuard.classifyRetry(failure);
            var failed = PlatformNotificationSpi.DeliveryOutcome.failed(retry.name());
            auditSink.accept(new PlatformNotificationAudit(requestId, template.code(), recipient,
                    failed.status(), retry.name(), "ENTERED"));
            return failed;
        } finally {
            guard.exit(requestId);
        }
    }
}
