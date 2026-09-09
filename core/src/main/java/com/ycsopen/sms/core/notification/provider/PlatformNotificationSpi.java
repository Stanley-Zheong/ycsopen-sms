package com.ycsopen.sms.core.notification.provider;

/** Provider boundary for platform bootstrap notifications. */
public interface PlatformNotificationSpi {
    DeliveryOutcome send(NotificationRequest request);

    record NotificationRequest(String recipient, PlatformNotificationTemplate template,
                               String content, String requestId) {
        public NotificationRequest {
            if (recipient == null || recipient.isBlank()) {
                throw new IllegalArgumentException("recipient is required");
            }
            if (template == null || content == null || content.isBlank()
                    || requestId == null || requestId.isBlank()) {
                throw new IllegalArgumentException("template, content and requestId are required");
            }
        }
    }

    record DeliveryOutcome(Status status, String providerMessageId, String reason) {
        public enum Status { ACCEPTED, FAILED, BLOCKED }

        public static DeliveryOutcome accepted(String providerMessageId) {
            return new DeliveryOutcome(Status.ACCEPTED, providerMessageId, null);
        }

        public static DeliveryOutcome failed(String reason) {
            return new DeliveryOutcome(Status.FAILED, null, reason);
        }

        public static DeliveryOutcome blocked(String reason) {
            return new DeliveryOutcome(Status.BLOCKED, null, reason);
        }
    }
}
