package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.config.CryptoStorageConfiguration;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectCapabilityServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-02T03:04:05Z");
    private static final Instant EXPIRY = NOW.plusSeconds(300);
    private static final String OBJECT_ID = "pobj_v1_object41";
    private static final String TENANT = "tenant:17";
    private static final String SUBJECT = "subject:29";
    private static final String PURPOSE = "registration-review";

    @Test
    void productionConfigurationProvidesOnlyTheConditionalDenyAllDefault() throws Exception {
        Method beanMethod = CryptoStorageConfiguration.class
                .getDeclaredMethod("objectAccessAuthorizationPort");
        beanMethod.setAccessible(true);

        assertThat(beanMethod.getAnnotation(Bean.class)).isNotNull();
        assertThat(beanMethod.getAnnotation(ConditionalOnMissingBean.class)).isNotNull();
        ObjectAccessAuthorizationPort configured = (ObjectAccessAuthorizationPort)
                beanMethod.invoke(new CryptoStorageConfiguration());
        assertThat(configured).isExactlyInstanceOf(DenyAllObjectAccessAuthorization.class);
        assertThat(configured.authorize(new ObjectAccessAuthorizationPort.Request(
                OBJECT_ID, TENANT, SUBJECT, PURPOSE,
                ObjectAccessAuthorizationPort.CapabilityState.ACTIVE, EXPIRY))).isFalse();
    }

    @Test
    void issuesCompleteTokenOnceInRedactedApplicationRelativePathAndStoresOnlyDigest() {
        Fixture fixture = fixture(true, 2);

        ObjectCapabilityToken issued = fixture.service.issue(issueRequest());
        String path = issued.claimApplicationRelativePath();
        String token = tokenFrom(path);

        assertThat(path).startsWith(ObjectCapabilityService.CAPABILITY_PATH_PREFIX);
        assertThat(token).matches("ocap_v1_[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}");
        assertThat(issued.toString()).doesNotContain(token).contains("[redacted]");
        assertThatThrownBy(issued::claimApplicationRelativePath)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("capability path already claimed");

        ObjectCapabilityService.StoredCapability stored = fixture.store.only();
        assertThat(stored.lookupId()).isEqualTo(token.substring(8, 30));
        assertThat(stored.credentialDigest().keyVersion()).isEqualTo(2);
        assertThat(stored.credentialDigest().digest()).hasSize(32);
        assertThat(stored.tenantBindingDigest()).hasSize(32);
        assertThat(stored.subjectBindingDigest()).hasSize(32);
        assertThat(stored.toString()).doesNotContain(token)
                .doesNotContain(token.substring(token.indexOf('.') + 1))
                .contains("[redacted]");
    }

    @Test
    void validCapabilityPassesFullAuthorizationContextBeforeOneFetch() {
        AtomicReference<ObjectAccessAuthorizationPort.Request> authorized = new AtomicReference<>();
        Fixture fixture = fixture(request -> {
            authorized.set(request);
            return true;
        }, 2);
        String token = issueToken(fixture);
        AtomicInteger fetches = new AtomicInteger();

        String result = fixture.service.authorizeAndFetch(token, accessRequest(), () -> {
            fetches.incrementAndGet();
            return "authenticated-object";
        });

        assertThat(result).isEqualTo("authenticated-object");
        assertThat(fetches).hasValue(1);
        assertThat(authorized.get()).isEqualTo(new ObjectAccessAuthorizationPort.Request(
                OBJECT_ID, TENANT, SUBJECT, PURPOSE,
                ObjectAccessAuthorizationPort.CapabilityState.ACTIVE, EXPIRY));
        assertThat(fixture.digest.verifyCalls).hasValue(1);
        assertThat(fixture.store.only().state())
                .isEqualTo(ObjectAccessAuthorizationPort.CapabilityState.REVOKED);

        assertDenied(() -> fixture.service.authorizeAndFetch(
                token, accessRequest(), () -> "replay-must-not-fetch"));
    }

    @Test
    void concurrentUsesAtomicallyProduceExactlyOneFetchWinner() throws Exception {
        Fixture fixture = fixture(true, 2);
        String token = issueToken(fixture);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger fetches = new AtomicInteger();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptAfter(start, fixture, token, fetches));
            Future<Boolean> second = executor.submit(() -> attemptAfter(start, fixture, token, fetches));
            start.countDown();
            assertThat(java.util.List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(fetches).hasValue(1);
        assertThat(fixture.store.only().state())
                .isEqualTo(ObjectAccessAuthorizationPort.CapabilityState.REVOKED);
    }

    @Test
    void downstreamFailureBurnsCapabilityAndRequiresFreshIssue() {
        Fixture fixture = fixture(true, 2);
        String token = issueToken(fixture);

        assertThatThrownBy(() -> fixture.service.authorizeAndFetch(
                token, accessRequest(), () -> {
                    throw new IllegalStateException("downstream unavailable");
                })).isInstanceOf(IllegalStateException.class);
        assertDenied(() -> fixture.service.authorizeAndFetch(
                token, accessRequest(), () -> "replay-must-not-fetch"));
    }

    @Test
    void productionDefaultAndExplicitDenialBothPrecedeFetch() {
        assertThat(new DenyAllObjectAccessAuthorization().authorize(
                new ObjectAccessAuthorizationPort.Request(OBJECT_ID, TENANT, SUBJECT, PURPOSE,
                        ObjectAccessAuthorizationPort.CapabilityState.ACTIVE, EXPIRY))).isFalse();

        Fixture fixture = fixture(false, 2);
        String token = issueToken(fixture);
        CountingFetch fetch = new CountingFetch();

        assertDenied(() -> fixture.service.authorizeAndFetch(token, accessRequest(), fetch));
        assertThat(fetch.calls).hasValue(0);
        assertThat(fixture.digest.verifyCalls).hasValue(1);
    }

    @Test
    void expiredRevokedAndConsumedCapabilitiesFailBeforeFetch() {
        Fixture expiredByClock = fixture(true, 2);
        String expiredToken = issueToken(expiredByClock);
        ObjectCapabilityService expiredService = expiredByClock.serviceWithClock(
                Clock.fixed(EXPIRY, ZoneOffset.UTC));
        CountingFetch expiredFetch = new CountingFetch();
        assertDenied(() -> expiredService.authorizeAndFetch(expiredToken, accessRequest(), expiredFetch));
        assertThat(expiredFetch.calls).hasValue(0);

        Fixture expiredState = fixture(true, 2);
        String expiredStateToken = issueToken(expiredState);
        expiredState.store.transition(ObjectAccessAuthorizationPort.CapabilityState.EXPIRED);
        CountingFetch expiredStateFetch = new CountingFetch();
        assertDenied(() -> expiredState.service.authorizeAndFetch(
                expiredStateToken, accessRequest(), expiredStateFetch));
        assertThat(expiredStateFetch.calls).hasValue(0);

        Fixture revoked = fixture(true, 2);
        String revokedToken = issueToken(revoked);
        revoked.store.transition(ObjectAccessAuthorizationPort.CapabilityState.REVOKED);
        CountingFetch revokedFetch = new CountingFetch();
        assertDenied(() -> revoked.service.authorizeAndFetch(revokedToken, accessRequest(), revokedFetch));
        assertThat(revokedFetch.calls).hasValue(0);

        Fixture consumed = fixture(true, 2);
        String consumedToken = issueToken(consumed);
        consumed.store.consume();
        CountingFetch consumedFetch = new CountingFetch();
        assertDenied(() -> consumed.service.authorizeAndFetch(consumedToken, accessRequest(), consumedFetch));
        assertThat(consumedFetch.calls).hasValue(0);
    }

    @Test
    void tamperedSecretAndEveryWrongBindingFailBeforeFetch() {
        Fixture fixture = fixture(true, 2);
        String token = issueToken(fixture);

        String tampered = tamperSecret(token);
        assertNoFetchDenied(fixture.service, tampered, accessRequest());
        assertNoFetchDenied(fixture.service, nonCanonicalSecretEncoding(token), accessRequest());
        assertNoFetchDenied(fixture.service, token,
                new ObjectCapabilityService.AccessRequest("pobj_v1_object42", TENANT, SUBJECT, PURPOSE));
        assertNoFetchDenied(fixture.service, token,
                new ObjectCapabilityService.AccessRequest(OBJECT_ID, "tenant:18", SUBJECT, PURPOSE));
        assertNoFetchDenied(fixture.service, token,
                new ObjectCapabilityService.AccessRequest(OBJECT_ID, TENANT, "subject:30", PURPOSE));
        assertNoFetchDenied(fixture.service, token,
                new ObjectCapabilityService.AccessRequest(OBJECT_ID, TENANT, SUBJECT, "download"));
    }

    @Test
    void registrationUploadDomainAndMalformedCapabilityFailBeforeMetadataOrObjectStoreAccess() {
        Fixture fixture = fixture(true, 2);
        String token = issueToken(fixture);
        int lookupsBefore = fixture.store.lookupCalls.get();

        assertNoFetchDenied(fixture.service, token.replace("ocap_v1_", "regup_v1_"), accessRequest());
        assertNoFetchDenied(fixture.service, "ocap_v1_not-a-token", accessRequest());

        assertThat(fixture.store.lookupCalls).hasValue(lookupsBefore);
        assertThat(fixture.digest.verifyCalls).hasValue(0);
    }

    @Test
    void oldLiveCapabilityVerifiesDuringRotationAndFailsWhenVersionIsNoLongerLive() {
        Fixture fixture = fixture(true, 1);
        String token = issueToken(fixture);
        assertThat(fixture.store.only().credentialDigest().keyVersion()).isEqualTo(1);

        fixture.digest.rotateTo(2);
        assertThat(fixture.service.authorizeAndFetch(token, accessRequest(), () -> "old-live"))
                .isEqualTo("old-live");

        for (FakeDigestPort.KeyState state : java.util.List.of(
                FakeDigestPort.KeyState.RETIRED, FakeDigestPort.KeyState.REVOKED)) {
            Fixture unavailable = fixture(true, 1);
            String unavailableToken = issueToken(unavailable);
            unavailable.digest.rotateTo(2);
            unavailable.digest.setState(1, state);
            assertNoFetchDenied(unavailable.service, unavailableToken, accessRequest());
        }
        Fixture missing = fixture(true, 1);
        String missingToken = issueToken(missing);
        missing.digest.rotateTo(2);
        missing.digest.removeVersion(1);
        assertNoFetchDenied(missing.service, missingToken, accessRequest());
    }

    @Test
    void digestProviderFailureAndAuthorizationFailureAreSanitizedAndPrecedeFetch() {
        Fixture digestFailure = fixture(true, 2);
        String digestFailureToken = issueToken(digestFailure);
        digestFailure.digest.failVerification = true;
        CountingFetch digestFetch = new CountingFetch();
        assertDenied(() -> digestFailure.service.authorizeAndFetch(
                digestFailureToken, accessRequest(), digestFetch));
        assertThat(digestFetch.calls).hasValue(0);

        Fixture authorizationFailure = fixture(request -> {
            throw new IllegalStateException("provider-token-canary");
        }, 2);
        String authorizationFailureToken = issueToken(authorizationFailure);
        CountingFetch authorizationFetch = new CountingFetch();
        assertThatThrownBy(() -> authorizationFailure.service.authorizeAndFetch(
                authorizationFailureToken, accessRequest(), authorizationFetch))
                .isInstanceOf(ObjectCapabilityService.Failure.class)
                .hasMessage("object capability access denied")
                .satisfies(failure -> {
                    assertThat(failure.getMessage()).doesNotContain("provider-token-canary");
                    assertThat(((ObjectCapabilityService.Failure) failure).category())
                            .isEqualTo(ObjectCapabilityService.Failure.Category.CAPABILITY_DENIED);
                });
        assertThat(authorizationFetch.calls).hasValue(0);
    }

    @Test
    void issuanceRejectsExpiredInputAndRegistrationDigestStorage() {
        Fixture fixture = fixture(true, 2);
        ObjectCapabilityService.IssueRequest expired = new ObjectCapabilityService.IssueRequest(
                OBJECT_ID, TENANT, SUBJECT, PURPOSE, NOW);

        assertThatThrownBy(() -> fixture.service.issue(expired))
                .isInstanceOf(ObjectCapabilityService.Failure.class)
                .extracting("category")
                .isEqualTo(ObjectCapabilityService.Failure.Category.CAPABILITY_INPUT_INVALID);
        assertThat(fixture.store.values).isEmpty();

        assertThatThrownBy(() -> new ObjectCapabilityService.StoredCapability(
                "AAAAAAAAAAAAAAAAAAAAAA", OBJECT_ID, new byte[32], new byte[32], PURPOSE,
                new VersionedTokenDigest(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                        1, new byte[32]),
                ObjectAccessAuthorizationPort.CapabilityState.ACTIVE, EXPIRY))
                .isInstanceOf(ObjectCapabilityService.Failure.class)
                .extracting("category")
                .isEqualTo(ObjectCapabilityService.Failure.Category.CAPABILITY_INPUT_INVALID);
    }

    private static Fixture fixture(boolean allow, int activeVersion) {
        return fixture(request -> allow, activeVersion);
    }

    private static Fixture fixture(ObjectAccessAuthorizationPort authorization, int activeVersion) {
        FakeDigestPort digest = new FakeDigestPort(activeVersion);
        FakeStore store = new FakeStore();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ObjectCapabilityService service = new ObjectCapabilityService(
                digest, store, authorization, clock, new SecureRandom());
        return new Fixture(digest, store, authorization, service);
    }

    private static ObjectCapabilityService.IssueRequest issueRequest() {
        return new ObjectCapabilityService.IssueRequest(OBJECT_ID, TENANT, SUBJECT, PURPOSE, EXPIRY);
    }

    private static ObjectCapabilityService.AccessRequest accessRequest() {
        return new ObjectCapabilityService.AccessRequest(OBJECT_ID, TENANT, SUBJECT, PURPOSE);
    }

    private static String issueToken(Fixture fixture) {
        return tokenFrom(fixture.service.issue(issueRequest()).claimApplicationRelativePath());
    }

    private static String tokenFrom(String path) {
        return path.substring(ObjectCapabilityService.CAPABILITY_PATH_PREFIX.length());
    }

    private static String tamperSecret(String token) {
        int secretStart = token.indexOf('.') + 1;
        char replacement = token.charAt(secretStart) == 'A' ? 'B' : 'A';
        return token.substring(0, secretStart) + replacement + token.substring(secretStart + 1);
    }

    private static String nonCanonicalSecretEncoding(String token) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
        char canonicalLast = token.charAt(token.length() - 1);
        int canonicalIndex = alphabet.indexOf(canonicalLast);
        if (canonicalIndex < 0 || canonicalIndex % 4 != 0) {
            throw new AssertionError("unexpected canonical Base64url tail");
        }
        char nonCanonicalLast = alphabet.charAt(canonicalIndex + 1);
        return token.substring(0, token.length() - 1) + nonCanonicalLast;
    }

    private static void assertNoFetchDenied(ObjectCapabilityService service,
                                            String token,
                                            ObjectCapabilityService.AccessRequest request) {
        CountingFetch fetch = new CountingFetch();
        assertDenied(() -> service.authorizeAndFetch(token, request, fetch));
        assertThat(fetch.calls).hasValue(0);
    }

    private static void assertDenied(Runnable invocation) {
        assertThatThrownBy(invocation::run)
                .isInstanceOf(ObjectCapabilityService.Failure.class)
                .hasMessage("object capability access denied")
                .extracting("category")
                .isEqualTo(ObjectCapabilityService.Failure.Category.CAPABILITY_DENIED);
    }

    private static boolean attemptAfter(CountDownLatch start,
                                        Fixture fixture,
                                        String token,
                                        AtomicInteger fetches) throws InterruptedException {
        start.await();
        try {
            fixture.service.authorizeAndFetch(token, accessRequest(), () -> {
                fetches.incrementAndGet();
                return "winner";
            });
            return true;
        } catch (ObjectCapabilityService.Failure denied) {
            assertThat(denied.category())
                    .isEqualTo(ObjectCapabilityService.Failure.Category.CAPABILITY_DENIED);
            return false;
        }
    }

    private record Fixture(FakeDigestPort digest,
                           FakeStore store,
                           ObjectAccessAuthorizationPort authorization,
                           ObjectCapabilityService service) {
        ObjectCapabilityService serviceWithClock(Clock clock) {
            return new ObjectCapabilityService(digest, store, authorization, clock, new SecureRandom());
        }
    }

    private static final class CountingFetch implements Supplier<String> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public String get() {
            calls.incrementAndGet();
            return "must-not-fetch";
        }
    }

    private static final class FakeStore implements ObjectCapabilityService.CapabilityStore {
        private final Map<String, ObjectCapabilityService.StoredCapability> values = new java.util.HashMap<>();
        private final AtomicInteger lookupCalls = new AtomicInteger();

        @Override
        public synchronized boolean create(ObjectCapabilityService.StoredCapability capability) {
            return values.putIfAbsent(capability.lookupId(), capability) == null;
        }

        @Override
        public synchronized Optional<ObjectCapabilityService.StoredCapability> findByLookupId(String lookupId) {
            lookupCalls.incrementAndGet();
            return Optional.ofNullable(values.get(lookupId));
        }

        @Override
        public synchronized boolean consumeActive(String lookupId, Instant now) {
            ObjectCapabilityService.StoredCapability current = values.get(lookupId);
            if (current == null
                    || current.state() != ObjectAccessAuthorizationPort.CapabilityState.ACTIVE
                    || !now.isBefore(current.expiresAt())) {
                return false;
            }
            values.put(lookupId,
                    current.withState(ObjectAccessAuthorizationPort.CapabilityState.REVOKED));
            return true;
        }

        ObjectCapabilityService.StoredCapability only() {
            assertThat(values).hasSize(1);
            return values.values().iterator().next();
        }

        void transition(ObjectAccessAuthorizationPort.CapabilityState state) {
            ObjectCapabilityService.StoredCapability current = only();
            values.put(current.lookupId(), current.withState(state));
        }

        void consume() {
            transition(ObjectAccessAuthorizationPort.CapabilityState.REVOKED);
        }
    }

    private static final class FakeDigestPort implements OpaqueTokenDigestPort {
        private final Map<Integer, KeyState> states = new java.util.HashMap<>();
        private final AtomicInteger verifyCalls = new AtomicInteger();
        private int activeVersion;
        private boolean failVerification;

        FakeDigestPort(int activeVersion) {
            this.activeVersion = activeVersion;
            states.put(activeVersion, KeyState.ACTIVE);
        }

        @Override
        public VersionedTokenDigest issue(Purpose purpose, Binding binding, byte[] tokenSecret) {
            if (purpose != Purpose.OBJECT_CAPABILITY || states.get(activeVersion) != KeyState.ACTIVE) {
                throw new IllegalStateException("test digest unavailable");
            }
            return digest(purpose, binding, tokenSecret, activeVersion);
        }

        @Override
        public boolean verify(Purpose purpose,
                              Binding binding,
                              byte[] tokenSecret,
                              VersionedTokenDigest storedDigest) {
            verifyCalls.incrementAndGet();
            if (failVerification) {
                throw new IllegalStateException("provider-token-canary");
            }
            KeyState state = states.get(Math.toIntExact(storedDigest.keyVersion()));
            if (purpose != Purpose.OBJECT_CAPABILITY || storedDigest.purpose() != purpose
                    || state != KeyState.ACTIVE && state != KeyState.RETIRING) {
                return false;
            }
            return MessageDigest.isEqual(
                    digest(purpose, binding, tokenSecret, Math.toIntExact(storedDigest.keyVersion())).digest(),
                    storedDigest.digest());
        }

        @Override
        public KeyHealth health(Purpose purpose) {
            return new KeyHealth(states.get(activeVersion) == KeyState.ACTIVE
                    ? KeyHealth.Status.READY : KeyHealth.Status.UNAVAILABLE);
        }

        void rotateTo(int version) {
            states.put(activeVersion, KeyState.RETIRING);
            activeVersion = version;
            states.put(version, KeyState.ACTIVE);
        }

        void setState(int version, KeyState state) {
            states.put(version, state);
        }

        void removeVersion(int version) {
            states.remove(version);
        }

        private static VersionedTokenDigest digest(Purpose purpose,
                                                   Binding binding,
                                                   byte[] secret,
                                                   int version) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(
                        MessageDigest.getInstance("SHA-256").digest(
                                ("object-capability-v" + version).getBytes(StandardCharsets.US_ASCII)),
                        "HmacSHA256"));
                mac.update((byte) purpose.ordinal());
                update(mac, binding.tenant());
                update(mac, binding.subject());
                update(mac, binding.resourceOrSession());
                return new VersionedTokenDigest(purpose, version, mac.doFinal(secret));
            } catch (GeneralSecurityException failure) {
                throw new IllegalStateException("test digest unavailable");
            }
        }

        private static void update(Mac mac, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            mac.update(ByteBuffer.allocate(2).putShort((short) bytes.length).array());
            mac.update(bytes);
        }

        private enum KeyState {
            ACTIVE,
            RETIRING,
            RETIRED,
            REVOKED
        }
    }
}
