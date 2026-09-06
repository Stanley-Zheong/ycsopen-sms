package com.ycsopen.sms.core.notification.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

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

    @Test
    void recordsRedactedAuditAndClassifiesTransientFailure() {
        List<PlatformNotificationAudit> audits = new ArrayList<>();
        var service = new PlatformMessageBootstrapService(request -> {
            throw new RuntimeException(new TimeoutException("provider secret=do-not-log"));
        }, new PlatformMessageBootstrapRecursionGuard(), audits::add);

        var outcome = service.send("13800138000", PlatformNotificationTemplate.OPERATIONAL,
                "status", "req-2");

        assertThat(outcome.status()).isEqualTo(PlatformNotificationSpi.DeliveryOutcome.Status.FAILED);
        assertThat(audits).singleElement().satisfies(audit -> {
            assertThat(audit.recipient()).isEqualTo("***8000");
            assertThat(audit.retryClass()).isEqualTo("TRANSIENT");
            assertThat(audit.toString()).doesNotContain("secret");
        });
    }
}
