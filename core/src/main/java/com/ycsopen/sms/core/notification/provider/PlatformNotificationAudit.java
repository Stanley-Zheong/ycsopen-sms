package com.ycsopen.sms.core.notification.provider;

import java.util.Objects;

/** Redacted, append-only delivery evidence; transport secrets are never retained. */
public record PlatformNotificationAudit(String requestId, String purpose, String recipient,
                                        PlatformNotificationSpi.DeliveryOutcome.Status result,
                                        String retryClass, String guardState) {
    public PlatformNotificationAudit {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(result, "result");
        recipient = redactRecipient(recipient);
    }

    private static String redactRecipient(String value) {
        if (value.length() <= 4) return "***";
        return "***" + value.substring(value.length() - 4);
    }
}
