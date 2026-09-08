package com.ycsopen.sms.core.service.tenant;

import com.ycsopen.sms.core.notification.provider.PlatformMessageBootstrapService;
import com.ycsopen.sms.core.notification.provider.PlatformNotificationSpi;
import com.ycsopen.sms.core.notification.provider.PlatformNotificationTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** Issues a short-lived verification challenge without ever returning or logging its code. */
@Service
public final class ContactVerificationService {
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final int MAX_ATTEMPTS = 5;

    private final PlatformMessageBootstrapService notifications;
    private final ChallengeStore store;
    private final Clock clock;
    private final SecureRandom random;
    private final BCryptPasswordEncoder codeEncoder = new BCryptPasswordEncoder();

    @Autowired
    public ContactVerificationService(ObjectProvider<PlatformMessageBootstrapService> notifications,
                                      JdbcContactChallengeStore store) {
        this.notifications = notifications.getIfAvailable();
        this.store = store;
        this.clock = Clock.systemUTC();
        this.random = new SecureRandom();
    }

    public ContactVerificationService(PlatformMessageBootstrapService notifications,
                                      ChallengeStore store, Clock clock, SecureRandom random) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public ChallengeReceipt request(String phone, String requestIp) {
        requirePhone(phone);
        if (notifications == null) throw new ChallengeFailure("CONTACT_VERIFICATION_DELIVERY_UNAVAILABLE");
        if (requestIp == null || requestIp.length() > 45) throw new ChallengeFailure("CONTACT_VERIFICATION_INVALID");
        String challengeId = UUID.randomUUID().toString();
        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expiresAt = clock.instant().plus(CHALLENGE_TTL);
        store.put(new StoredChallenge(challengeId, phone, codeEncoder.encode(code), expiresAt, 0,
                false, false, requestIp));
        PlatformNotificationSpi.DeliveryOutcome outcome = notifications.send(phone,
                PlatformNotificationTemplate.REGISTRATION, "您的验证码为" + code, challengeId);
        if (outcome.status() != PlatformNotificationSpi.DeliveryOutcome.Status.ACCEPTED) {
            throw new ChallengeFailure("CONTACT_VERIFICATION_DELIVERY_UNAVAILABLE");
        }
        return new ChallengeReceipt(challengeId, expiresAt);
    }

    public void verify(String challengeId, String phone, String code) {
        requirePhone(phone);
        String failure = store.locked(challengeId, challenge -> {
            String invalid = activeFailure(challenge, phone);
            if (invalid != null) return invalid;
            if (challenge.verified()) return "CONTACT_VERIFICATION_ALREADY_VERIFIED";
            if (challenge.attempts() >= MAX_ATTEMPTS) return "CONTACT_VERIFICATION_INVALID";
            if (code == null || !code.matches("[0-9]{6}") || !codeEncoder.matches(code, challenge.codeHash())) {
                challenge.attempts++;
                return "CONTACT_VERIFICATION_INVALID";
            }
            challenge.verified = true;
            return null;
        });
        // Throw after the store transaction commits so invalid attempts cannot roll back.
        if (failure != null) throw new ChallengeFailure(failure);
    }

    public void consumeVerified(String challengeId, String phone) {
        requirePhone(phone);
        String failure = store.locked(challengeId, challenge -> {
            String invalid = activeFailure(challenge, phone);
            if (invalid != null) return invalid;
            if (!challenge.verified()) return "CONTACT_VERIFICATION_NOT_VERIFIED";
            challenge.consumed = true;
            return null;
        });
        if (failure != null) throw new ChallengeFailure(failure);
    }

    private String activeFailure(StoredChallenge challenge, String phone) {
        if (challenge == null || !challenge.phone().equals(phone)) return "CONTACT_VERIFICATION_INVALID";
        if (!clock.instant().isBefore(challenge.expiresAt())) {
            return "CONTACT_VERIFICATION_EXPIRED";
        }
        if (challenge.consumed()) return "CONTACT_VERIFICATION_ALREADY_CONSUMED";
        return null;
    }

    private static void requirePhone(String phone) {
        if (phone == null || !phone.matches("1[3-9]\\d{9}")) {
            throw new ChallengeFailure("INVALID_CONTACT_PHONE");
        }
    }

    public record ChallengeReceipt(String challengeId, Instant expiresAt) {
        @Override public String toString() { return "ChallengeReceipt[challengeId=" + challengeId + ", expiresAt=" + expiresAt + ']'; }
    }

    public interface ChallengeStore {
        void put(StoredChallenge challenge);
        String locked(String challengeId, Function<StoredChallenge, String> operation);
    }

    static final class StoredChallenge {
        private final String challengeId;
        private final String phone;
        private final String codeHash;
        private final Instant expiresAt;
        private int attempts;
        private boolean verified;
        private boolean consumed;
        private final String requestIp;

        StoredChallenge(String challengeId, String phone, String codeHash, Instant expiresAt,
                                int attempts, boolean verified, boolean consumed, String requestIp) {
            this.challengeId = challengeId; this.phone = phone; this.codeHash = codeHash;
            this.expiresAt = expiresAt; this.attempts = attempts; this.verified = verified;
            this.consumed = consumed; this.requestIp = requestIp;
        }
        String challengeId() { return challengeId; }
        String phone() { return phone; }
        String codeHash() { return codeHash; }
        Instant expiresAt() { return expiresAt; }
        int attempts() { return attempts; }
        boolean verified() { return verified; }
        boolean consumed() { return consumed; }
        String requestIp() { return requestIp; }
    }

    public static final class ChallengeFailure extends RuntimeException {
        ChallengeFailure(String code) { super(code); }
    }
}
