package com.ycsopen.sms.core.common.security.object;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtectedObjectServiceTest {

    private static final String KEY_REFERENCE = "kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk";
    private static final String SESSION = "11111111-1111-4111-8111-111111111111";
    private static final String TENANT_DRAFT = "22222222-2222-4222-8222-222222222222";
    private static final String TENANT_SCOPE = "tenant:" + TENANT_DRAFT;
    private static final String SUBJECT = "subject:reviewer-7";
    private static final String ACCESS_PURPOSE = "registration-review";
    private static final Instant NOW = Instant.parse("2030-01-02T03:04:05Z");
    private static final Instant EXPIRY = NOW.plusSeconds(3_600);

    @ParameterizedTest
    @EnumSource(PrivateObjectStorePort.ObjectPurpose.class)
    void everyPurposeAcceptsExactPlaintextAndEnvelopeAndRejectsOneByteOverBeforePut(
            PrivateObjectStorePort.ObjectPurpose purpose) {
        Fixture fixture = fixture(true);
        int maximum = Math.toIntExact(purpose.envelopeTarget().maximumPlaintextBytes());

        ProtectedObjectService.CreatedObject created = fixture.service.create(request(
                purpose, admittedMedia(purpose), new RepeatingInputStream(maximum),
                (long) maximum, null));

        assertThat(created.plaintextSize()).isEqualTo(maximum);
        assertThat(fixture.objectStore.onlyBody()).hasSize(Math.toIntExact(purpose.maximumEnvelopeBytes()));
        assertThat(fixture.repository.onlyObject().envelopeSize()).isEqualTo(purpose.maximumEnvelopeBytes());

        CountingInputStream declaredOver = new CountingInputStream(new RepeatingInputStream(maximum + 1));
        assertFailure(() -> fixture.service.create(request(purpose, admittedMedia(purpose),
                declaredOver, (long) maximum + 1, null)),
                ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_INPUT_INVALID);
        assertThat(declaredOver.readCount).isZero();
        assertThat(fixture.objectStore.putCalls).hasValue(1);
    }

    @ParameterizedTest
    @EnumSource(PrivateObjectStorePort.ObjectPurpose.class)
    void everyPurposeRejectsUnknownPlaintextAndHeadEnvelopeAtLimitPlusOne(
            PrivateObjectStorePort.ObjectPurpose purpose) {
        Fixture fixture = fixture(true);
        int maximum = Math.toIntExact(purpose.envelopeTarget().maximumPlaintextBytes());
        CountingInputStream unknownOver = new CountingInputStream(new RepeatingInputStream(maximum + 2));

        assertFailure(() -> fixture.service.create(request(purpose, admittedMedia(purpose),
                unknownOver, null, null)),
                ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_INPUT_INVALID);
        assertThat(unknownOver.readCount).isEqualTo(maximum + 1L);
        assertThat(fixture.objectStore.putCalls).hasValue(0);

        ProtectedObjectService.CreatedObject created = fixture.service.create(request(
                purpose, admittedMedia(purpose), new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L, null));
        String token = fixture.issueCapability(created.protectedObjectId());
        fixture.objectStore.forcedHeadSize = purpose.maximumEnvelopeBytes() + 1;

        assertFailure(() -> fixture.service.read(readRequest(created.protectedObjectId(), token, purpose)),
                ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_INTEGRITY_INVALID);
        assertThat(fixture.objectStore.headCalls).hasValue(1);
        assertThat(fixture.objectStore.getCalls).hasValue(0);
    }

    @Test
    void declaredActualMismatchU32OverflowAndMediaFaultsFailBeforeCryptographyOrStore() {
        Fixture fixture = fixture(true);
        CountingInputStream shortBody = new CountingInputStream(new ByteArrayInputStream(new byte[]{1, 2}));
        CountingInputStream longBody = new CountingInputStream(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        CountingInputStream overflow = new CountingInputStream(new ByteArrayInputStream(new byte[]{1}));

        assertInputFailure(() -> fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                shortBody, 3L, null)));
        assertInputFailure(() -> fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                longBody, 3L, null)));
        assertInputFailure(() -> fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                overflow, 0x1_0000_0000L, null)));
        assertInputFailure(() -> fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.REPRESENTATIVE_ID_FRONT, "application/pdf",
                new ByteArrayInputStream(new byte[]{1}), 1L, null)));

        assertThat(shortBody.readCount).isEqualTo(2);
        assertThat(longBody.readCount).isEqualTo(4);
        assertThat(overflow.readCount).isZero();
        assertThat(fixture.keyPort.wrapCalls).hasValue(0);
        assertThat(fixture.objectStore.putCalls).hasValue(0);
        assertThat(fixture.repository.beginCalls).hasValue(0);
    }

    @Test
    void encryptsBeforePutThenAuthorizesBeforeHeadAndReturnsOnlyAfterChecksumAndTag() {
        Fixture fixture = fixture(true);
        byte[] plaintext = "sensitive-object-canary".getBytes(StandardCharsets.US_ASCII);
        ProtectedObjectService.CreatedObject created = fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(plaintext), (long) plaintext.length, null));
        byte[] stored = fixture.objectStore.onlyBody();

        assertThat(stored).startsWith('Y', 'C', 'S', 'E');
        assertThat(indexOf(stored, plaintext)).isNegative();
        assertThat(fixture.events).containsSubsequence("encrypt-wrap", "store-put", "metadata-commit");

        fixture.events.clear();
        String token = fixture.issueCapability(created.protectedObjectId());
        fixture.events.clear();
        ProtectedObjectService.ProtectedObjectData result = fixture.service.read(
                readRequest(created.protectedObjectId(), token,
                        PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE));

        assertThat(result.bytes()).containsExactly(plaintext);
        assertThat(fixture.events).containsExactly(
                "capability-lookup", "capability-digest", "authorization",
                "metadata-find", "store-head", "store-get", "decrypt-unwrap");
    }

    @Test
    void denialPrecedesMetadataHeadBodyAndKeyAccessWithProductionDefaultAlsoDenying() {
        Fixture denied = fixture(false);
        ProtectedObjectService.CreatedObject created = denied.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(new byte[]{7}), 1L, null));
        String token = denied.issueCapability(created.protectedObjectId());
        int findsBefore = denied.repository.findCalls.get();
        int headsBefore = denied.objectStore.headCalls.get();
        int unwrapsBefore = denied.keyPort.unwrapCalls.get();

        assertFailure(() -> denied.service.read(readRequest(created.protectedObjectId(), token,
                        PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)),
                ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_ACCESS_DENIED);

        assertThat(denied.repository.findCalls).hasValue(findsBefore);
        assertThat(denied.objectStore.headCalls).hasValue(headsBefore);
        assertThat(denied.objectStore.getCalls).hasValue(0);
        assertThat(denied.keyPort.unwrapCalls).hasValue(unwrapsBefore);
        assertThat(new DenyAllObjectAccessAuthorization().authorize(
                new ObjectAccessAuthorizationPort.Request(created.protectedObjectId(),
                        TENANT_SCOPE, SUBJECT, ACCESS_PURPOSE,
                        ObjectAccessAuthorizationPort.CapabilityState.ACTIVE, EXPIRY))).isFalse();
    }

    @Test
    void checksumTagAndOversizedHeaderProviderKeyLengthsFailWithoutPlaintext() {
        Fixture fixture = fixture(true);
        ProtectedObjectService.CreatedObject created = fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(new byte[]{9, 8, 7, 6}), 4L, null));
        String token = fixture.issueCapability(created.protectedObjectId());

        fixture.objectStore.tamperBodyWithoutChecksum(fixture.objectStore.onlyKey(), -1);
        assertIntegrity(() -> fixture.service.read(readRequest(created.protectedObjectId(), token,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));

        for (int offset : List.of(9, 10, 13, 15)) {
            Fixture malformed = fixture(true);
            ProtectedObjectService.CreatedObject malformedCreated = malformed.service.create(request(
                    PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                    new ByteArrayInputStream(new byte[]{4, 3, 2, 1}), 4L, null));
            String malformedToken = malformed.issueCapability(malformedCreated.protectedObjectId());
            malformed.objectStore.tamperBodyAndRehash(malformed.objectStore.onlyKey(), offset);
            malformed.repository.refreshFrom(malformed.objectStore.onlyMetadata());
            assertIntegrity(() -> malformed.service.read(readRequest(
                    malformedCreated.protectedObjectId(), malformedToken,
                    PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
            assertThat(malformed.keyPort.unwrapCalls).hasValue(0);
        }

        Fixture tag = fixture(true);
        ProtectedObjectService.CreatedObject tagCreated = tag.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(new byte[]{5, 6, 7, 8}), 4L, null));
        String tagToken = tag.issueCapability(tagCreated.protectedObjectId());
        tag.objectStore.tamperBodyAndRehash(tag.objectStore.onlyKey(), -1);
        tag.repository.refreshFrom(tag.objectStore.onlyMetadata());
        assertIntegrity(() -> tag.service.read(readRequest(tagCreated.protectedObjectId(), tagToken,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        assertThat(tag.keyPort.unwrapCalls).hasValue(1);
    }

    @Test
    void metadataFailureDeletesSplitWriteOrPersistsOpaqueOrphanForRetry() {
        Fixture contained = fixture(true);
        contained.repository.failComplete = true;
        assertUnavailable(() -> contained.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(new byte[]{1}), 1L, null)));
        assertThat(contained.objectStore.deleteCalls).hasValue(1);
        assertThat(contained.objectStore.bodies).isEmpty();
        assertThat(contained.repository.orphanCalls).hasValue(0);

        Fixture orphaned = fixture(true);
        orphaned.repository.failComplete = true;
        orphaned.objectStore.failDelete = true;
        assertUnavailable(() -> orphaned.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(new byte[]{2}), 1L, null)));
        assertThat(orphaned.repository.orphanCalls).hasValue(1);
        assertThat(orphaned.repository.onlyObject().state())
                .isEqualTo(ProtectedObjectMetadataRepository.ObjectState.ORPHANED);

        orphaned.objectStore.failDelete = false;
        ProtectedObjectService.ReconciliationResult result = orphaned.service.reconcile(10);
        assertThat(result).isEqualTo(new ProtectedObjectService.ReconciliationResult(1, 1));
        assertThat(orphaned.objectStore.bodies).isEmpty();
        assertThat(orphaned.repository.onlyObject().state())
                .isEqualTo(ProtectedObjectMetadataRepository.ObjectState.DELETED);
    }

    @Test
    void replacementAndDeleteRemainRetryableAndFailuresLeakNoSensitiveValues() {
        Fixture fixture = fixture(true);
        ProtectedObjectService.CreatedObject original = fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(new byte[]{1}), 1L, null));
        fixture.objectStore.failDelete = true;
        ProtectedObjectService.CreatedObject replacement = fixture.service.create(request(
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                new ByteArrayInputStream(new byte[]{2}), 1L, original.protectedObjectId()));
        assertThat(fixture.repository.object(original.protectedObjectId()).state())
                .isEqualTo(ProtectedObjectMetadataRepository.ObjectState.REPLACED);
        assertThat(fixture.repository.object(replacement.protectedObjectId()).state())
                .isEqualTo(ProtectedObjectMetadataRepository.ObjectState.STAGED);

        fixture.objectStore.failDelete = false;
        assertThat(fixture.service.reconcile(10).deleted()).isEqualTo(1);

        fixture.objectStore.providerCanary =
                "token=ocap_secret bucket=private key=obj url=https://secret.invalid ciphertext=canary provider=raw";
        fixture.objectStore.failHead = true;
        String token = fixture.issueCapability(replacement.protectedObjectId());
        assertThatThrownBy(() -> fixture.service.read(readRequest(replacement.protectedObjectId(), token,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)))
                .isInstanceOf(ProtectedObjectService.Failure.class)
                .hasMessage("protected object service is unavailable")
                .hasNoCause()
                .satisfies(failure -> assertThat(failure.toString())
                        .doesNotContain("ocap_secret", "secret.invalid", "bucket=", "key=",
                                "ciphertext=", "provider="));
    }

    private static Fixture fixture(boolean authorize) {
        List<String> events = new ArrayList<>();
        FakeRepositoryStore repositoryStore = new FakeRepositoryStore(events);
        ProtectedObjectMetadataRepository repository =
                new ProtectedObjectMetadataRepository(repositoryStore);
        FakeKeyPort keyPort = new FakeKeyPort(events);
        ProtectedFieldCodec codec = new ProtectedFieldCodec(
                new EnvelopeCodec(), keyPort, new SecureRandom(), KEY_REFERENCE);
        FakeObjectStore objectStore = new FakeObjectStore(events);
        FakeDigestPort digestPort = new FakeDigestPort(events);
        ObjectCapabilityService capabilityService = new ObjectCapabilityService(
                digestPort, repository, request -> {
                    events.add("authorization");
                    return authorize;
                }, Clock.fixed(NOW, ZoneOffset.UTC), new SecureRandom());
        ProtectedObjectService service = new ProtectedObjectService(
                codec, objectStore, repository, capabilityService,
                new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));
        return new Fixture(events, repositoryStore, repository, objectStore,
                keyPort, capabilityService, service);
    }

    private static ProtectedObjectService.CreateRequest request(
            PrivateObjectStorePort.ObjectPurpose purpose,
            String mediaType,
            InputStream body,
            Long declaredLength,
            String replacesObjectId) {
        return new ProtectedObjectService.CreateRequest(
                SESSION, TENANT_DRAFT, purpose, mediaType, body, declaredLength,
                1, EXPIRY, replacesObjectId);
    }

    private static ProtectedObjectService.ReadRequest readRequest(
            String objectId, String token, PrivateObjectStorePort.ObjectPurpose purpose) {
        return new ProtectedObjectService.ReadRequest(
                objectId, token, TENANT_SCOPE, SUBJECT, ACCESS_PURPOSE, purpose);
    }

    private static String admittedMedia(PrivateObjectStorePort.ObjectPurpose purpose) {
        return switch (purpose) {
            case REPRESENTATIVE_ID_FRONT, REPRESENTATIVE_ID_BACK -> "image/png";
            case BUSINESS_LICENSE, SHORT_LINK_DOMAIN_PROOF, TRADEMARK_PROOF -> "application/pdf";
        };
    }

    private static int indexOf(byte[] value, byte[] candidate) {
        outer:
        for (int offset = 0; offset <= value.length - candidate.length; offset++) {
            for (int index = 0; index < candidate.length; index++) {
                if (value[offset + index] != candidate[index]) {
                    continue outer;
                }
            }
            return offset;
        }
        return -1;
    }

    private static void assertInputFailure(ThrowingCall call) {
        assertFailure(call,
                ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_INPUT_INVALID);
    }

    private static void assertIntegrity(ThrowingCall call) {
        assertFailure(call,
                ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_INTEGRITY_INVALID);
    }

    private static void assertUnavailable(ThrowingCall call) {
        assertFailure(call,
                ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_UNAVAILABLE);
    }

    private static void assertFailure(ThrowingCall call,
                                      ProtectedObjectService.Failure.Category category) {
        assertThatThrownBy(call::run)
                .isExactlyInstanceOf(ProtectedObjectService.Failure.class)
                .hasNoCause()
                .satisfies(failure -> assertThat(((ProtectedObjectService.Failure) failure).category())
                        .isEqualTo(category));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }

    private record Fixture(List<String> events,
                           FakeRepositoryStore repository,
                           ProtectedObjectMetadataRepository repositoryBoundary,
                           FakeObjectStore objectStore,
                           FakeKeyPort keyPort,
                           ObjectCapabilityService capabilityService,
                           ProtectedObjectService service) {
        String issueCapability(String objectId) {
            String path = capabilityService.issue(new ObjectCapabilityService.IssueRequest(
                    objectId, TENANT_SCOPE, SUBJECT, ACCESS_PURPOSE, EXPIRY))
                    .claimApplicationRelativePath();
            return path.substring(ObjectCapabilityService.CAPABILITY_PATH_PREFIX.length());
        }
    }

    private static final class RepeatingInputStream extends InputStream {
        private long remaining;

        private RepeatingInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 0x5a;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) {
            if (remaining == 0) {
                return -1;
            }
            int count = (int) Math.min(remaining, length);
            Arrays.fill(bytes, offset, offset + count, (byte) 0x5a);
            remaining -= count;
            return count;
        }
    }

    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private long readCount;

        private CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                readCount++;
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int count = delegate.read(bytes, offset, length);
            if (count > 0) {
                readCount += count;
            }
            return count;
        }
    }

    private static final class FakeKeyPort implements KeyProtectionPort {
        private final List<String> events;
        private final AtomicInteger wrapCalls = new AtomicInteger();
        private final AtomicInteger unwrapCalls = new AtomicInteger();

        private FakeKeyPort(List<String> events) {
            this.events = events;
        }

        @Override
        public WrappedDataKey wrap(byte[] dataEncryptionKey,
                                  byte[] authenticatedHeader,
                                  ProtectionContext semanticContext) {
            wrapCalls.incrementAndGet();
            events.add("encrypt-wrap");
            byte[] nonce = new byte[12];
            ByteBuffer.wrap(nonce).putInt(wrapCalls.get());
            byte[] wrapped = new byte[48];
            System.arraycopy(dataEncryptionKey, 0, wrapped, 0, dataEncryptionKey.length);
            return new WrappedDataKey(KEY_REFERENCE, nonce, wrapped);
        }

        @Override
        public byte[] unwrap(WrappedDataKey wrappedDataKey,
                             byte[] authenticatedHeader,
                             ProtectionContext semanticContext) {
            unwrapCalls.incrementAndGet();
            events.add("decrypt-unwrap");
            return Arrays.copyOf(wrappedDataKey.wrappedDek(), DATA_ENCRYPTION_KEY_BYTES);
        }

        @Override
        public KeyHealth health() {
            return new KeyHealth(KeyHealth.Status.READY);
        }
    }

    private static final class FakeDigestPort implements OpaqueTokenDigestPort {
        private final List<String> events;

        private FakeDigestPort(List<String> events) {
            this.events = events;
        }

        @Override
        public VersionedTokenDigest issue(Purpose purpose, Binding binding, byte[] tokenSecret) {
            return digest(purpose, binding, tokenSecret);
        }

        @Override
        public boolean verify(Purpose purpose,
                              Binding binding,
                              byte[] tokenSecret,
                              VersionedTokenDigest storedDigest) {
            events.add("capability-digest");
            return storedDigest.keyVersion() == 1
                    && MessageDigest.isEqual(digest(purpose, binding, tokenSecret).digest(),
                    storedDigest.digest());
        }

        @Override
        public KeyHealth health(Purpose purpose) {
            return new KeyHealth(KeyHealth.Status.READY);
        }

        private static VersionedTokenDigest digest(Purpose purpose, Binding binding, byte[] secret) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                digest.update((byte) purpose.ordinal());
                update(digest, binding.tenant());
                update(digest, binding.subject());
                update(digest, binding.resourceOrSession());
                digest.update(secret);
                return new VersionedTokenDigest(purpose, 1, digest.digest());
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("required digest unavailable", impossible);
            }
        }

        private static void update(MessageDigest digest, String value) {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            digest.update(ByteBuffer.allocate(2).putShort((short) bytes.length).array());
            digest.update(bytes);
        }
    }

    private static final class FakeObjectStore implements PrivateObjectStorePort {
        private final List<String> events;
        private final Map<String, byte[]> bodies = new HashMap<>();
        private final Map<String, StoredObjectMetadata> metadata = new HashMap<>();
        private final AtomicInteger putCalls = new AtomicInteger();
        private final AtomicInteger headCalls = new AtomicInteger();
        private final AtomicInteger getCalls = new AtomicInteger();
        private final AtomicInteger deleteCalls = new AtomicInteger();
        private long forcedHeadSize = -1;
        private boolean failDelete;
        private boolean failHead;
        private String providerCanary;

        private FakeObjectStore(List<String> events) {
            this.events = events;
        }

        @Override
        public StoredObjectMetadata put(ObjectPurpose purpose,
                                        String mediaType,
                                        InputStream ciphertext,
                                        Long declaredContentLength) {
            events.add("store-put");
            putCalls.incrementAndGet();
            try {
                byte[] body = ciphertext.readAllBytes();
                if (declaredContentLength == null || body.length != declaredContentLength) {
                    throw Failure.invalidInput();
                }
                String key = "obj_v1_" + "%064x".formatted(putCalls.get());
                StoredObjectMetadata stored = new StoredObjectMetadata(
                        key, purpose, body.length, sha256(body), mediaType);
                bodies.put(key, body);
                metadata.put(key, stored);
                return stored;
            } catch (IOException failure) {
                throw Failure.unavailable();
            }
        }

        @Override
        public StoredCiphertext get(String storageKey, ObjectPurpose purpose) {
            events.add("store-get");
            getCalls.incrementAndGet();
            byte[] body = bodies.get(storageKey);
            StoredObjectMetadata stored = metadata.get(storageKey);
            if (body == null || stored == null) {
                throw Failure.unavailable();
            }
            return new StoredCiphertext(body, stored);
        }

        @Override
        public StoredObjectMetadata head(String storageKey, ObjectPurpose purpose) {
            events.add("store-head");
            headCalls.incrementAndGet();
            if (failHead) {
                throw new IllegalStateException(providerCanary);
            }
            StoredObjectMetadata stored = metadata.get(storageKey);
            if (stored == null) {
                throw Failure.unavailable();
            }
            return forcedHeadSize < 0 ? stored : new StoredObjectMetadata(
                    storageKey, purpose, forcedHeadSize, stored.sha256(), stored.mediaType());
        }

        @Override
        public void delete(String storageKey, ObjectPurpose purpose) {
            deleteCalls.incrementAndGet();
            if (failDelete) {
                throw new IllegalStateException(providerCanary);
            }
            bodies.remove(storageKey);
            metadata.remove(storageKey);
        }

        byte[] onlyBody() {
            assertThat(bodies).hasSize(1);
            return bodies.values().iterator().next().clone();
        }

        String onlyKey() {
            assertThat(bodies).hasSize(1);
            return bodies.keySet().iterator().next();
        }

        StoredObjectMetadata onlyMetadata() {
            assertThat(metadata).hasSize(1);
            return metadata.values().iterator().next();
        }

        void tamperBodyWithoutChecksum(String key, int offset) {
            byte[] body = bodies.get(key);
            body[offset < 0 ? body.length - 1 : offset] ^= 1;
        }

        void tamperBodyAndRehash(String key, int offset) {
            tamperBodyWithoutChecksum(key, offset);
            byte[] body = bodies.get(key);
            StoredObjectMetadata prior = metadata.get(key);
            metadata.put(key, new StoredObjectMetadata(key, prior.purpose(), body.length,
                    sha256(body), prior.mediaType()));
        }

        private static String sha256(byte[] value) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException("required digest unavailable", impossible);
            }
        }
    }

    private static final class FakeRepositoryStore implements ProtectedObjectMetadataRepository.Store {
        private final List<String> events;
        private final Map<String, ProtectedObjectMetadataRepository.ProtectedObjectMetadata> objects =
                new HashMap<>();
        private final Map<String, ObjectCapabilityService.StoredCapability> capabilities = new HashMap<>();
        private final AtomicInteger beginCalls = new AtomicInteger();
        private final AtomicInteger findCalls = new AtomicInteger();
        private final AtomicInteger orphanCalls = new AtomicInteger();
        private ProtectedObjectMetadataRepository.CreateOperation active;
        private boolean failComplete;

        private FakeRepositoryStore(List<String> events) {
            this.events = events;
        }

        @Override
        public void beginCreate(ProtectedObjectMetadataRepository.CreateOperation operation) {
            beginCalls.incrementAndGet();
            active = operation;
        }

        @Override
        public Optional<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> completeCreate(
                ProtectedObjectMetadataRepository.CreateOperation operation,
                StoredObjectMetadata stored) {
            if (failComplete) {
                throw new IllegalStateException("metadata-provider-canary");
            }
            events.add("metadata-commit");
            Optional<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> replaced =
                    Optional.ofNullable(operation.replacesObjectId()).map(objects::get);
            replaced.ifPresent(value -> objects.put(value.protectedObjectId(), copy(value,
                    ProtectedObjectMetadataRepository.ObjectState.REPLACED,
                    value.envelopeSha256(), value.envelopeSize())));
            objects.put(operation.protectedObjectId(), metadata(operation, stored,
                    ProtectedObjectMetadataRepository.ObjectState.STAGED));
            active = null;
            return replaced;
        }

        @Override
        public void recordOrphan(ProtectedObjectMetadataRepository.CreateOperation operation,
                                 StoredObjectMetadata stored) {
            orphanCalls.incrementAndGet();
            objects.put(operation.protectedObjectId(), metadata(operation, stored,
                    ProtectedObjectMetadataRepository.ObjectState.ORPHANED));
            active = null;
        }

        @Override
        public void failCreate(String operationId) {
            active = null;
        }

        @Override
        public Optional<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> find(
                String protectedObjectId) {
            events.add("metadata-find");
            findCalls.incrementAndGet();
            return Optional.ofNullable(objects.get(protectedObjectId));
        }

        @Override
        public void markDeleted(String protectedObjectId) {
            ProtectedObjectMetadataRepository.ProtectedObjectMetadata current = objects.get(protectedObjectId);
            objects.put(protectedObjectId, copy(current,
                    ProtectedObjectMetadataRepository.ObjectState.DELETED,
                    current.envelopeSha256(), current.envelopeSize()));
        }

        @Override
        public void markOrphaned(String protectedObjectId) {
            ProtectedObjectMetadataRepository.ProtectedObjectMetadata current = objects.get(protectedObjectId);
            objects.put(protectedObjectId, copy(current,
                    ProtectedObjectMetadataRepository.ObjectState.ORPHANED,
                    current.envelopeSha256(), current.envelopeSize()));
        }

        @Override
        public List<ProtectedObjectMetadataRepository.ProtectedObjectMetadata> reconciliationCandidates(int limit) {
            return objects.values().stream()
                    .filter(value -> value.state() == ProtectedObjectMetadataRepository.ObjectState.REPLACED
                            || value.state() == ProtectedObjectMetadataRepository.ObjectState.EXPIRED
                            || value.state() == ProtectedObjectMetadataRepository.ObjectState.ORPHANED)
                    .limit(limit).toList();
        }

        @Override
        public boolean createCapability(ObjectCapabilityService.StoredCapability capability) {
            return capabilities.putIfAbsent(capability.lookupId(), capability) == null;
        }

        @Override
        public Optional<ObjectCapabilityService.StoredCapability> findCapability(String lookupId) {
            events.add("capability-lookup");
            return Optional.ofNullable(capabilities.get(lookupId));
        }

        ProtectedObjectMetadataRepository.ProtectedObjectMetadata onlyObject() {
            assertThat(objects).hasSize(1);
            return objects.values().iterator().next();
        }

        ProtectedObjectMetadataRepository.ProtectedObjectMetadata object(String id) {
            return objects.get(id);
        }

        void refreshFrom(StoredObjectMetadata stored) {
            ProtectedObjectMetadataRepository.ProtectedObjectMetadata current = onlyObject();
            objects.put(current.protectedObjectId(), copy(current, current.state(),
                    stored.sha256(), stored.size()));
        }

        private static ProtectedObjectMetadataRepository.ProtectedObjectMetadata metadata(
                ProtectedObjectMetadataRepository.CreateOperation operation,
                StoredObjectMetadata stored,
                ProtectedObjectMetadataRepository.ObjectState state) {
            return new ProtectedObjectMetadataRepository.ProtectedObjectMetadata(
                    operation.protectedObjectId(), operation.registrationSessionId(),
                    operation.tenantDraftId(), operation.purpose(), state,
                    stored.storageKey(), stored.sha256(), stored.size(), stored.mediaType(),
                    operation.expiresAt());
        }

        private static ProtectedObjectMetadataRepository.ProtectedObjectMetadata copy(
                ProtectedObjectMetadataRepository.ProtectedObjectMetadata current,
                ProtectedObjectMetadataRepository.ObjectState state,
                String sha256,
                long size) {
            return new ProtectedObjectMetadataRepository.ProtectedObjectMetadata(
                    current.protectedObjectId(), current.registrationSessionId(),
                    current.tenantDraftId(), current.purpose(), state, current.storageKey(),
                    sha256, size, current.mediaType(), current.expiresAt());
        }
    }
}
