package com.ycsopen.sms.core.notification.provider;

import org.springframework.stereotype.Service;

import java.util.Objects;

/** Thin orchestration service for provider-backed platform bootstrap messages. */
@Service
public class PlatformMessageBootstrapService {
    private final PlatformNotificationSpi provider;

    public PlatformMessageBootstrapService(PlatformNotificationSpi provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    public PlatformNotificationSpi.DeliveryOutcome send(String recipient,
                                                        PlatformNotificationTemplate template,
                                                        String content,
                                                        String requestId) {
        return provider.send(new PlatformNotificationSpi.NotificationRequest(
                recipient, template, content, requestId));
    }
}
