package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.notification.provider.PlatformMessageBootstrapService;
import com.ycsopen.sms.core.notification.provider.PlatformNotificationSpi;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContactVerificationServiceTest {

    @Test
    void rejectsVerificationReplayAfterSuccessfulVerification() {
        AtomicReference<String> delivered = new AtomicReference<>();
        ContactVerificationService service = service(delivered, Clock.systemUTC());
        var receipt = service.request("13800138000", "127.0.0.1");
        service.verify(receipt.challengeId(), "13800138000", delivered.get());
        assertThatThrownBy(() -> service.verify(receipt.challengeId(), "13800138000", delivered.get()))
                .hasMessage("CONTACT_VERIFICATION_ALREADY_VERIFIED");
    }

    @Test
    void burnsFiveFailedAttemptsAndNeverAcceptsTheCorrectCodeAfterwards() {
        AtomicReference<String> delivered = new AtomicReference<>();
        ContactVerificationService service = service(delivered, Clock.systemUTC());
        var receipt = service.request("13800138000", "127.0.0.1");
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verify(receipt.challengeId(), "13800138000", "invalid"))
                    .hasMessage("CONTACT_VERIFICATION_INVALID");
        }
        assertThatThrownBy(() -> service.verify(receipt.challengeId(), "13800138000", delivered.get()))
                .hasMessage("CONTACT_VERIFICATION_INVALID");
    }

    @Test
    void consumesAVerifiedChallengeOnlyOnceForTheSamePhone() {
        AtomicReference<String> delivered = new AtomicReference<>();
        ContactVerificationService service = service(delivered,
                Clock.fixed(Instant.parse("2026-09-07T00:00:00Z"), ZoneOffset.UTC));

        ContactVerificationService.ChallengeReceipt issued = service.request("13800138000", "127.0.0.1");
        String code = delivered.get();
        service.verify(issued.challengeId(), "13800138000", code);

        service.consumeVerified(issued.challengeId(), "13800138000");
        assertThatThrownBy(() -> service.consumeVerified(issued.challengeId(), "13800138000"))
                .isInstanceOf(ContactVerificationService.ChallengeFailure.class)
                .hasMessage("CONTACT_VERIFICATION_ALREADY_CONSUMED");
    }

    @Test
    void expiresUnverifiedChallengesWithoutDisclosingTheCode() {
        AtomicReference<String> delivered = new AtomicReference<>();
        MutableClock clock = new MutableClock(Instant.parse("2026-09-07T00:00:00Z"));
        ContactVerificationService service = service(delivered, clock);

        ContactVerificationService.ChallengeReceipt issued = service.request("13800138000", "127.0.0.1");
        clock.advance(Duration.ofMinutes(11));

        assertThat(issued.toString()).doesNotContain(delivered.get());
        assertThatThrownBy(() -> service.verify(issued.challengeId(), "13800138000", delivered.get()))
                .isInstanceOf(ContactVerificationService.ChallengeFailure.class)
                .hasMessage("CONTACT_VERIFICATION_EXPIRED");
    }

    private static ContactVerificationService service(AtomicReference<String> delivered, Clock clock) {
        PlatformNotificationSpi provider = request -> {
            delivered.set(request.content().replaceAll("[^0-9]", ""));
            return PlatformNotificationSpi.DeliveryOutcome.accepted("provider-message");
        };
        return new ContactVerificationService(new PlatformMessageBootstrapService(provider),
                new TestChallengeStore(), clock,
                new java.security.SecureRandom());
    }

    private static final class TestChallengeStore implements ContactVerificationService.ChallengeStore {
        private final java.util.Map<String, ContactVerificationService.StoredChallenge> values = new java.util.HashMap<>();
        @Override public synchronized void put(ContactVerificationService.StoredChallenge challenge) { values.put(challenge.challengeId(), challenge); }
        @Override public synchronized String locked(String id, java.util.function.Function<ContactVerificationService.StoredChallenge, String> operation) {
            return operation.apply(values.get(id));
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
