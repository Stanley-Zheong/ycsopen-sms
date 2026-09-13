package com.ycsopen.sms.core.service.delivery;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Continuously drains accepted outbox rows so an accepted API request reaches an upstream connector. */
@Component
public class MessageDispatchScheduler {
    private final HttpMessageDeliveryService delivery;

    public MessageDispatchScheduler(HttpMessageDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${ycsopen.delivery.dispatch-delay-ms:100}")
    public void tick() {
        try {
            delivery.dispatchNext();
        } catch (RuntimeException ignored) {
            // The outbox row remains recoverable; the next tick retries it after claim timeout handling.
        }
    }
}
