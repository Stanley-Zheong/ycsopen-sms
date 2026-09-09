package com.ycsopen.sms.core.cmpp;

/** Shared acceptance boundary used by downstream CMPP so it cannot bypass HTTP compliance/routing/billing policy. */
public interface CmppDownstreamAcceptancePort {
    AcceptanceResult accept(SubmitCommand command);

    record SubmitCommand(long tenantId, long credentialId, String clientIp, String submitId,
                         String destination, String serviceId, String templateId, String productCode,
                         String content, boolean registeredDelivery) { }

    record AcceptanceResult(boolean accepted, String messageId, String errorCode, String errorMessage) {
        public static AcceptanceResult accepted(String messageId) {
            return new AcceptanceResult(true, messageId, null, null);
        }

        public static AcceptanceResult rejected(String errorCode, String errorMessage) {
            return new AcceptanceResult(false, null,
                    blank(errorCode) ? "CMPP_ACCEPTANCE_REJECTED" : errorCode,
                    blank(errorMessage) ? "CMPP acceptance rejected" : errorMessage);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
