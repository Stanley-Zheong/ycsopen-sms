package com.ycsopen.sms.core.service.delivery;

/** Provider boundary for sending one accepted SMS task to an HTTP upstream. */
public interface SmsUpstreamProviderClient {
    ProviderSubmitResult submit(ProviderSubmitRequest request);

    record ProviderSubmitRequest(long tenantId, long taskId, String messageId, long channelId,
                                 String recipient, String content, String idempotencyKey) {
        public ProviderSubmitRequest {
            if (tenantId <= 0 || taskId <= 0 || channelId <= 0
                    || blank(messageId) || blank(recipient) || blank(content) || blank(idempotencyKey)) {
                throw new IllegalArgumentException("invalid provider submit request");
            }
        }
    }

    record ProviderSubmitResult(Status status, String providerMessageId, String errorCode, String errorMessage) {
        public enum Status { ACCEPTED, REJECTED, UNKNOWN }

        public static ProviderSubmitResult accepted(String providerMessageId) {
            return new ProviderSubmitResult(Status.ACCEPTED, required(providerMessageId, "providerMessageId"), null, null);
        }

        public static ProviderSubmitResult rejected(String errorCode, String errorMessage) {
            return new ProviderSubmitResult(Status.REJECTED, null, fallback(errorCode, "PROVIDER_REJECTED"),
                    fallback(errorMessage, "provider rejected"));
        }

        public static ProviderSubmitResult unknown(String errorCode, String errorMessage) {
            return new ProviderSubmitResult(Status.UNKNOWN, null, fallback(errorCode, "UNKNOWN_OUTCOME"),
                    fallback(errorMessage, "provider outcome is unknown"));
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String required(String value, String field) {
        if (blank(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String fallback(String value, String fallback) {
        return blank(value) ? fallback : value;
    }
}
