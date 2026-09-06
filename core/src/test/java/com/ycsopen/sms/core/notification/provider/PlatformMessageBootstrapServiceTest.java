package com.ycsopen.sms.core.notification.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformMessageBootstrapServiceTest {
    @Test
    void sendsTypedRequestToProvider() {
        PlatformNotificationSpi provider = request ->
                PlatformNotificationSpi.DeliveryOutcome.accepted("provider-1");

        var outcome = new PlatformMessageBootstrapService(provider)
                .send("13800138000", PlatformNotificationTemplate.REGISTRATION,
                        "welcome", "req-1");

        assertThat(outcome.status()).isEqualTo(PlatformNotificationSpi.DeliveryOutcome.Status.ACCEPTED);
        assertThat(outcome.providerMessageId()).isEqualTo("provider-1");
    }

    @Test
    void guardRejectsReentryUntilExit() {
        var guard = new PlatformMessageBootstrapRecursionGuard();

        assertThat(guard.enter("req-1")).isTrue();
        assertThat(guard.enter("req-1")).isFalse();
        guard.exit("req-1");
        assertThat(guard.enter("req-1")).isTrue();
    }
}
