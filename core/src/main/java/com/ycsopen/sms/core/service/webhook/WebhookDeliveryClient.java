package com.ycsopen.sms.core.service.webhook;

import java.util.Map;

/** Thin outbound HTTP boundary for signed tenant webhook delivery. */
public interface WebhookDeliveryClient {
    DeliveryResponse post(String destinationUrl, String payloadJson, Map<String, String> headers);

    record DeliveryResponse(int httpStatus, String resultCode, String resultMessage) {
        boolean success() {
            return httpStatus >= 200 && httpStatus < 300;
        }
    }
}
