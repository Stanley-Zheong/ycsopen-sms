package com.ycsopen.sms.core.common.security.key.pkcs11;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SunPkcs11KeyAdapterTest {

    private static final String UNIT_EVIDENCE_LABEL = "unit-mapping-only-not-pkcs11-evidence";
    private static final byte[] TOKEN_SECRET = sequence(32, 1);
    private static final OpaqueTokenDigestPort.Binding CAPABILITY_BINDING =
            new OpaqueTokenDigestPort.Binding("tenant:17", "subject:29", "object:41/read");
    private static final OpaqueTokenDigestPort.Binding UPLOAD_BINDING =
            new OpaqueTokenDigestPort.Binding("tenant-draft:17", "subject:29", "session:41");

    @TempDir
    Path temporaryDirectory;

    @Test
    void reserveNonceProviderOrderingIsOwnedByOneWrapAndFailureConsumesReservation() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicLong count = new AtomicLong();
        TestCryptoOperations crypto = new TestCryptoOperations(events);
        SunPkcs11KeyAdapter adapter = adapter(count, events, crypto);
        crypto.clearStartupEvents();
        ProtectionContext context = databaseContext("message-41");
        byte[] header = header(11);

        WrappedDataKey wrapped = adapter.wrap(sequence(32, 11), header, context);

        assertThat(events).containsExactly("reserve", "nonce", "provider");
        assertThat(count).hasValue(1);
        assertThat(wrapped.wrapNonce()).containsExactly(sequence(12, 31));
        assertThat(adapter.unwrap(wrapped, header, context)).containsExactly(sequence(32, 11));

        events.clear();
        crypto.failNextAes.set(true);
        assertThatThrownBy(() -> adapter.wrap(sequence(32, 21), header, context))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_OPERATION_FAILED")
                .hasMessageNotContaining("unit-kek-v1")
                .hasMessageNotContaining("post-reservation-canary");
        assertThat(events).containsExactly("reserve", "nonce", "provider");
        assertThat(count).hasValue(2);
        assertThat(adapter.health().status()).isEqualTo(KeyHealth.Status.UNAVAILABLE);
        assertThat(UNIT_EVIDENCE_LABEL).contains("not-pkcs11-evidence");
    }

    @Test
    void enforcesRotationAndHardCeilingBoundariesWithoutReleaseOrReuse() throws Exception {
        Boundary belowRotation = wrapAt(983_039L);
        assertThat(belowRotation.count()).isEqualTo(983_040L);
        assertThat(belowRotation.health()).isEqualTo(KeyHealth.Status.ROTATION_REQUIRED);

        Boundary atRotation = wrapAt(983_040L);
        assertThat(atRotation.count()).isEqualTo(983_041L);
        assertThat(atRotation.health()).isEqualTo(KeyHealth.Status.ROTATION_REQUIRED);

        Boundary belowCeiling = wrapAt(1_048_575L);
        assertThat(belowCeiling.count()).isEqualTo(1_048_576L);
        assertThat(belowCeiling.health()).isEqualTo(KeyHealth.Status.ROTATION_REQUIRED);

        List<String> events = new ArrayList<>();
        AtomicLong atCeiling = new AtomicLong(1_048_576L);
        TestCryptoOperations crypto = new TestCryptoOperations(events);
        SunPkcs11KeyAdapter adapter = adapter(atCeiling, events, crypto);
        crypto.clearStartupEvents();
        assertThatThrownBy(() -> adapter.wrap(sequence(32, 3), header(11), databaseContext("ceiling")))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_WRAP_LIMIT_REACHED");
        assertThat(atCeiling).hasValue(1_048_576L);
        assertThat(events).containsExactly("reserve");
    }

    @Test
    void concurrentReservationsCannotCrossTheCeiling() throws Exception {
        AtomicLong count = new AtomicLong(1_048_560L);
        ConcurrentLinkedQueue<String> events = new ConcurrentLinkedQueue<>();
        TestCryptoOperations crypto = new TestCryptoOperations(events);
        SunPkcs11KeyAdapter adapter = adapter(count, events, crypto);
        crypto.clearStartupEvents();
        int callers = 48;
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int index = 0; index < callers; index++) {
            int resource = index;
            executor.submit(() -> {
                try {
                    start.await();
                    adapter.wrap(sequence(32, resource), header(11),
                            databaseContext("concurrent-" + resource));
                    successes.incrementAndGet();
                } catch (Pkcs11FailureMapper.Pkcs11OperationException exception) {
                    rejected.incrementAndGet();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(successes).hasValue(16);
        assertThat(rejected).hasValue(32);
        assertThat(count).hasValue(1_048_576L);
    }

    @Test
    void selectsPurposeBoundReferencesUnderCanonicalDomainAndRejectsCrossPurposeUse() throws Exception {
        Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts = new EnumMap<>(
                Pkcs11KeyDescriptor.Purpose.class);
        counts.put(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, new AtomicLong());
        counts.put(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, new AtomicLong());
        List<String> events = new ArrayList<>();
        TestCryptoOperations crypto = new TestCryptoOperations(events);
        SunPkcs11KeyAdapter adapter = adapter(counts, events, crypto);
        crypto.clearStartupEvents();
        byte[] fieldHeader = header(32);
        byte[] snapshotHeader = snapshotHeader(32);
        ProtectionContext fieldContext = databaseContext("purpose-field");
        ProtectionContext snapshotContext = snapshotContext("purpose-snapshot");

        WrappedDataKey field = adapter.wrap(sequence(32, 1), fieldHeader, fieldContext);
        WrappedDataKey snapshot = adapter.wrap(sequence(32, 2), snapshotHeader, snapshotContext);

        assertThat(field.keyReference()).isEqualTo("unit-kek-v1");
        assertThat(snapshot.keyReference()).isEqualTo("unit-snapshot-v1");
        assertThat(counts.get(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK)).hasValue(1);
        assertThat(counts.get(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY)).hasValue(1);
        assertThat(asciiPrefix(crypto.aads.get(0), "YCSE-WRAP-AAD\0".length()))
                .isEqualTo("YCSE-WRAP-AAD\0");
        assertThat(asciiPrefix(crypto.aads.get(1), "YCSE-WRAP-AAD\0".length()))
                .isEqualTo("YCSE-WRAP-AAD\0");
        assertThat(canonicalContextFromWrapAad(crypto.aads.get(0)))
                .containsExactly(fieldContext.canonicalBytes());
        assertThat(canonicalContextFromWrapAad(crypto.aads.get(1)))
                .containsExactly(snapshotContext.canonicalBytes())
                .isNotEqualTo(fieldContext.canonicalBytes());
        assertThat(adapter.unwrap(field, fieldHeader, fieldContext)).containsExactly(sequence(32, 1));
        assertThat(adapter.unwrap(snapshot, snapshotHeader, snapshotContext))
                .containsExactly(sequence(32, 2));

        assertThatThrownBy(() -> adapter.unwrap(snapshot, snapshotHeader, fieldContext))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_KEY_POLICY");
        assertThatThrownBy(() -> adapter.unwrap(field, fieldHeader, snapshotContext))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_KEY_POLICY");
        assertThatThrownBy(() -> adapter.wrap(sequence(32, 4), fieldHeader, snapshotContext))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_KEY_POLICY");
        assertThat(counts.get(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY)).hasValue(1);
    }

    @Test
    void fieldAndSnapshotReservationsEnforceIndependentCeilings() throws Exception {
        Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts = new EnumMap<>(
                Pkcs11KeyDescriptor.Purpose.class);
        AtomicLong field = new AtomicLong(KekWrapUsageRepository.HARD_CEILING);
        AtomicLong snapshot = new AtomicLong();
        counts.put(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, field);
        counts.put(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, snapshot);
        SunPkcs11KeyAdapter adapter = adapter(counts, new ArrayList<>(),
                new TestCryptoOperations(new ArrayList<>()));

        assertThatThrownBy(() -> adapter.wrap(sequence(32, 5), header(32),
                databaseContext("field-ceiling")))
                .hasMessageContaining("PKCS11_WRAP_LIMIT_REACHED");
        adapter.wrap(sequence(32, 6), snapshotHeader(32), snapshotContext("snapshot-open"));
        assertThat(field).hasValue(KekWrapUsageRepository.HARD_CEILING);
        assertThat(snapshot).hasValue(1);

        field.set(0);
        snapshot.set(KekWrapUsageRepository.HARD_CEILING);
        assertThatThrownBy(() -> adapter.wrap(sequence(32, 7), snapshotHeader(32),
                snapshotContext("snapshot-ceiling")))
                .hasMessageContaining("PKCS11_WRAP_LIMIT_REACHED");
        adapter.wrap(sequence(32, 8), header(32), databaseContext("field-open"));
        assertThat(field).hasValue(1);
        assertThat(snapshot).hasValue(KekWrapUsageRepository.HARD_CEILING);
    }

    @Test
    void startupRejectsMoreThanOneActiveOrRotationSnapshotOwner() {
        List<Pkcs11KeyDescriptor> ambiguous = new ArrayList<>(descriptors());
        ambiguous.add(descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, 2,
                "unit-snapshot-kek-v2", Pkcs11KeyDescriptor.State.ROTATION_REQUIRED));
        Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts = new EnumMap<>(
                Pkcs11KeyDescriptor.Purpose.class);
        counts.put(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, new AtomicLong());
        counts.put(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, new AtomicLong());

        assertThatThrownBy(() -> adapter(counts, new ArrayList<>(),
                new TestCryptoOperations(new ArrayList<>()), ambiguous))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_KEY_POLICY");
    }

    @Test
    void preservesKnownMobileAndPurposeSeparatedTokenVectors() throws Exception {
        SunPkcs11KeyAdapter adapter = adapter(new AtomicLong(), new ArrayList<>(),
                new TestCryptoOperations(new ArrayList<>()));
        BlindIndexPort.Context context = new BlindIndexPort.Context(
                "MESSAGE_TASK", "mobile", BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:17");

        var onlineIndexes = adapter.writeIndexes("13800138000", context);
        assertThat(onlineIndexes.values())
                .extracting(value -> value.canonicalValue())
                .containsExactly(
                        "afvx276qfp5gtxpdgvrtboujgm7hs45lcyyrqsuc7w6hwk6bv7pxk",
                        "alesvuhbuvlf3pvcduj5ghm4kicrnfqkxbocojpoeq453uxdjsjc6");
        byte[] historicalDigest = sha256("13800138000".getBytes(StandardCharsets.US_ASCII));
        assertThat(adapter.queryIndexesFromHistoricalDigest(historicalDigest, context))
                .isEqualTo(onlineIndexes);
        java.util.Arrays.fill(historicalDigest, (byte) 0);

        VersionedTokenDigest capability = adapter.issue(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, CAPABILITY_BINDING, TOKEN_SECRET);
        VersionedTokenDigest upload = adapter.issue(
                OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD, UPLOAD_BINDING, TOKEN_SECRET);
        assertThat(capability.keyVersion()).isEqualTo(2);
        assertThat(HexFormat.of().formatHex(capability.digest()))
                .isEqualTo("3e12d6d32c48339739caad700103f744aa5cb1effeaabf2e9f3c8f9ffb5ce81b");
        assertThat(HexFormat.of().formatHex(upload.digest()))
                .isEqualTo("a28ad654a0c6c839ba2fc141e72c4a593348adda74bfcc461307c0a35187b97a");
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, capability)).isTrue();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                UPLOAD_BINDING, TOKEN_SECRET, capability)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                new OpaqueTokenDigestPort.Binding("tenant:17", "subject:29", "object:42/read"),
                TOKEN_SECRET, capability)).isFalse();
    }

    @Test
    void verifiesOnlyActiveOrRetiringStoredVersionsAndUsesConstantTimeComparison() throws Exception {
        SunPkcs11KeyAdapter adapter = adapter(new AtomicLong(), new ArrayList<>(),
                new TestCryptoOperations(new ArrayList<>()));
        VersionedTokenDigest retiring = digestForVersion(1,
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, CAPABILITY_BINDING, TOKEN_SECRET);
        VersionedTokenDigest retired = digestForVersion(3,
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, CAPABILITY_BINDING, TOKEN_SECRET);
        VersionedTokenDigest compromised = digestForVersion(4,
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, CAPABILITY_BINDING, TOKEN_SECRET);

        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, retiring)).isTrue();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, retired)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET, compromised)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                CAPABILITY_BINDING, TOKEN_SECRET,
                new VersionedTokenDigest(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                        99, new byte[32]))).isFalse();
    }

    @Test
    void repositoryUsesRequiresNewAtomicUpdateBeforeReadingReservedCount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jdbc.update(anyString(), anyString(), anyLong(), anyString())).thenReturn(1);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
                anyString(), anyLong(), anyString()))
                .thenReturn(983_040L);
        KekWrapUsageRepository repository = new KekWrapUsageRepository(jdbc, transactions, mapper());

        KekWrapUsageRepository.Reservation reservation = repository.reserve(descriptors().getFirst());

        assertThat(reservation.reservedCount()).isEqualTo(983_040L);
        assertThat(reservation.rotationRequired()).isTrue();
        InOrder order = inOrder(jdbc, transactions);
        order.verify(transactions).getTransaction(any(TransactionDefinition.class));
        order.verify(jdbc).update(org.mockito.ArgumentMatchers.contains("wrap_operation_count < 1048576"),
                org.mockito.ArgumentMatchers.eq("FIELD_ENCRYPTION_KEK"),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq("unit-kek-v1"));
        order.verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("SELECT wrap_operation_count"),
                org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq("FIELD_ENCRYPTION_KEK"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("unit-kek-v1"));
        order.verify(transactions).commit(status);
        verify(transactions).commit(status);
    }

    @Test
    void repositoryBindsSnapshotPurposeToItsIndependentPersistedCounter() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jdbc.update(anyString(), org.mockito.ArgumentMatchers.eq("SNAPSHOT_RECOVERY"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("unit-snapshot-v1"))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq("SNAPSHOT_RECOVERY"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("unit-snapshot-v1"))).thenReturn(1L);
        KekWrapUsageRepository repository = new KekWrapUsageRepository(jdbc, transactions, mapper());
        Pkcs11KeyDescriptor snapshot = descriptors().stream()
                .filter(key -> key.purpose() == Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY)
                .findFirst().orElseThrow();

        assertThat(repository.reserve(snapshot).reservedCount()).isOne();
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains("purpose = ?"),
                org.mockito.ArgumentMatchers.eq("SNAPSHOT_RECOVERY"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("unit-snapshot-v1"));
        verify(jdbc).queryForObject(org.mockito.ArgumentMatchers.contains("purpose = ?"),
                org.mockito.ArgumentMatchers.eq(Long.class),
                org.mockito.ArgumentMatchers.eq("SNAPSHOT_RECOVERY"),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("unit-snapshot-v1"));
    }

    private Boundary wrapAt(long initial) throws Exception {
        AtomicLong count = new AtomicLong(initial);
        List<String> events = new ArrayList<>();
        TestCryptoOperations crypto = new TestCryptoOperations(events);
        SunPkcs11KeyAdapter adapter = adapter(count, events, crypto);
        crypto.clearStartupEvents();
        adapter.wrap(sequence(32, 7), header(11), databaseContext("boundary-" + initial));
        return new Boundary(count.get(), adapter.health().status());
    }

    private SunPkcs11KeyAdapter adapter(AtomicLong count,
                                        java.util.Collection<String> events,
                                        TestCryptoOperations crypto) throws Exception {
        Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts = new EnumMap<>(
                Pkcs11KeyDescriptor.Purpose.class);
        counts.put(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, count);
        counts.put(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, new AtomicLong());
        return adapter(counts, events, crypto);
    }

    private SunPkcs11KeyAdapter adapter(Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts,
                                        java.util.Collection<String> events,
                                        TestCryptoOperations crypto) throws Exception {
        return adapter(counts, events, crypto, descriptors());
    }

    private SunPkcs11KeyAdapter adapter(Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts,
                                        java.util.Collection<String> events,
                                        TestCryptoOperations crypto,
                                        List<Pkcs11KeyDescriptor> descriptors) throws Exception {
        return adapter(counts, events, crypto, descriptors, null);
    }

    private SunPkcs11KeyAdapter adapter(Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts,
                                        java.util.Collection<String> events,
                                        TestCryptoOperations crypto,
                                        List<Pkcs11KeyDescriptor> descriptors,
                                        KeyReferenceRepository lifecycle) throws Exception {
        Path module = temporaryDirectory.resolve("unit-pkcs11.so");
        if (!Files.exists(module)) {
            Files.writeString(module, "unit");
        }
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                module, List.of(module), 41, "unit-token", () -> "unit-pin".toCharArray(), descriptors);
        Pkcs11ProviderFactory factory = new Pkcs11ProviderFactory(
                (config, expectedName) -> provider(expectedName),
                (provider, pin, requested) -> requested.stream().collect(java.util.stream.Collectors.toMap(
                        Pkcs11KeyDescriptor::alias,
                        descriptor -> new Pkcs11ProviderFactory.TokenKey(
                                new OpaqueUnitKey(descriptor.algorithm(), material(descriptor)),
                                descriptor.purpose().isWrappingKey()
                                        ? "AES" : "Generic Secret",
                                256, true, true, false))), mapper());
        Pkcs11ProviderFactory.Session session = factory.open(properties);
        KekWrapUsageRepository repository = new KekWrapUsageRepository(descriptor -> {
            events.add("reserve");
            AtomicLong count = counts.get(descriptor.purpose());
            if (count == null) {
                return null;
            }
            while (true) {
                long current = count.get();
                if (current >= KekWrapUsageRepository.HARD_CEILING) {
                    return null;
                }
                if (count.compareAndSet(current, current + 1)) {
                    return current + 1;
                }
            }
        }, mapper());
        SecureRandom random = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                events.add("nonce");
                System.arraycopy(sequence(bytes.length, 31), 0, bytes, 0, bytes.length);
            }
        };
        return new SunPkcs11KeyAdapter(
                session, properties, repository, random, mapper(), crypto, lifecycle);
    }

    private static final class MutableLifecycleReferences implements KeyReferenceRepository {
        private final Map<String, KeyReference> references = new java.util.LinkedHashMap<>();

        private MutableLifecycleReferences(List<Pkcs11KeyDescriptor> descriptors) {
            for (Pkcs11KeyDescriptor descriptor : descriptors) {
                state(descriptor.purpose(), descriptor.keyVersion(),
                        KeyState.valueOf(descriptor.state().name()), descriptor.keyReference());
            }
        }

        void state(Pkcs11KeyDescriptor.Purpose purpose, long version, KeyState state) {
            KeyReference current = references.get(purpose + ":" + version);
            state(purpose, version, state, current.providerKeyReference());
        }

        private void state(Pkcs11KeyDescriptor.Purpose purpose, long version,
                           KeyState state, String reference) {
            references.put(purpose + ":" + version, new KeyReference(
                    KeyReferenceRepository.Purpose.valueOf(purpose.name()), version,
                    "pkcs11", reference, state, 0, false, 0));
        }

        @Override
        public List<KeyReference> findByPurpose(Purpose purpose) {
            return references.values().stream().filter(value -> value.purpose() == purpose).toList();
        }

        @Override
        public List<KeyReference> findAll() {
            return List.copyOf(references.values());
        }

        @Override
        public boolean transitionAtomicallyGuarded(
                Purpose purpose,
                List<Transition> transitions,
                java.util.function.BooleanSupplier guard) {
            return false;
        }
    }

    private static List<Pkcs11KeyDescriptor> descriptors() {
        return List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                        "unit-kek-v1", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, 1,
                        "unit-snapshot-kek-v1", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 1,
                        "unit-mobile-v1", Pkcs11KeyDescriptor.State.RETIRING),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 2,
                        "unit-mobile-v2", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                        "unit-capability-v1", Pkcs11KeyDescriptor.State.RETIRING),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 2,
                        "unit-capability-v2", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 3,
                        "unit-capability-v3", Pkcs11KeyDescriptor.State.RETIRED),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 4,
                        "unit-capability-v4", Pkcs11KeyDescriptor.State.COMPROMISED),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                        "unit-upload-v1", Pkcs11KeyDescriptor.State.RETIRING),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 2,
                        "unit-upload-v2", Pkcs11KeyDescriptor.State.ACTIVE));
    }

    private static Pkcs11KeyDescriptor descriptor(Pkcs11KeyDescriptor.Purpose purpose,
                                                   long version,
                                                   String alias,
                                                   Pkcs11KeyDescriptor.State state) {
        String reference = switch (purpose) {
            case FIELD_ENCRYPTION_KEK -> "unit-kek-v1";
            case SNAPSHOT_RECOVERY -> "unit-snapshot-v1";
            default -> purpose.name().toLowerCase() + "-v" + version;
        };
        return new Pkcs11KeyDescriptor(purpose, version, reference, alias, state,
                purpose.isWrappingKey() ? "AES" : "HmacSHA256",
                256);
    }

    private static byte[] material(Pkcs11KeyDescriptor descriptor) {
        String label = switch (descriptor.purpose()) {
            case FIELD_ENCRYPTION_KEK -> "field-encryption-kek-v1";
            case SNAPSHOT_RECOVERY -> "snapshot-recovery-kek-v1";
            case MOBILE_BLIND_INDEX -> "mobile-blind-index-v" + descriptor.keyVersion();
            case OBJECT_CAPABILITY_DIGEST -> "opaque-token-object_capability-v" + descriptor.keyVersion();
            case REGISTRATION_UPLOAD_DIGEST -> "opaque-token-registration_upload-v" + descriptor.keyVersion();
        };
        return sha256(("YCS-TEST-KEY/v1\0" + label).getBytes(StandardCharsets.US_ASCII));
    }

    private static VersionedTokenDigest digestForVersion(long version,
                                                          OpaqueTokenDigestPort.Purpose purpose,
                                                          OpaqueTokenDigestPort.Binding binding,
                                                          byte[] tokenSecret) {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        output.writeBytes("YCS-OPAQUE-TOKEN-DIGEST/v1\0".getBytes(StandardCharsets.US_ASCII));
        output.write(purpose == OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY ? 1 : 2);
        writeLength(output, binding.tenant());
        writeLength(output, binding.subject());
        writeLength(output, binding.resourceOrSession());
        output.writeBytes(tokenSecret);
        Pkcs11KeyDescriptor descriptor = descriptors().stream()
                .filter(key -> key.keyVersion() == version
                        && key.purpose().storageValue().equals(purpose.storagePurpose()))
                .findFirst().orElseThrow();
        return new VersionedTokenDigest(purpose, version,
                hmac(material(descriptor), output.toByteArray()));
    }

    private static void writeLength(java.io.ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes.length >>> 8);
        output.write(bytes.length);
        output.writeBytes(bytes);
    }

    private static byte[] header(long plaintextLength) {
        return new EnvelopeCodec().authenticatedHeader(
                "unit-kek-v1", plaintextLength, EnvelopeCodec.Target.DATABASE_FIELD);
    }

    private static byte[] snapshotHeader(long plaintextLength) {
        return new EnvelopeCodec().authenticatedHeader(
                "unit-snapshot-v1", plaintextLength,
                EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
    }

    private static ProtectionContext databaseContext(String resource) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted",
                "tenant:17", resource);
    }

    private static ProtectionContext snapshotContext(String resource) {
        return new ProtectionContext(ProtectionContext.Purpose.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK,
                "crypto-storage-bootstrap", "mysql-snapshot", "encrypted-chunk",
                "global", resource);
    }

    private static String asciiPrefix(byte[] value, int length) {
        return new String(value, 0, length, StandardCharsets.US_ASCII);
    }

    private static byte[] canonicalContextFromWrapAad(byte[] aad) {
        ByteBuffer encoded = ByteBuffer.wrap(aad);
        encoded.position("YCSE-WRAP-AAD\0".getBytes(StandardCharsets.US_ASCII).length);
        int headerLength = encoded.getInt();
        encoded.position(encoded.position() + headerLength);
        int contextLength = encoded.getInt();
        byte[] context = new byte[contextLength];
        encoded.get(context);
        assertThat(encoded.hasRemaining()).isFalse();
        return context;
    }

    private static byte[] sequence(int length, int first) {
        byte[] value = new byte[length];
        for (int index = 0; index < length; index++) {
            value[index] = (byte) (first + index);
        }
        return value;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] hmac(byte[] key, byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Pkcs11FailureMapper mapper() {
        return new Pkcs11FailureMapper(() -> "0123456789abcdef0123456789abcdef");
    }

    private static Provider provider(String name) {
        return new Provider(name, "1.0", "unit provider") {
            private static final long serialVersionUID = 1L;
        };
    }

    private record OpaqueUnitKey(String algorithm, byte[] material) implements SecretKey {
        private OpaqueUnitKey {
            material = material.clone();
        }

        @Override
        public byte[] material() {
            return material.clone();
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            throw new AssertionError("production code must not export token keys");
        }
    }

    private static final class TestCryptoOperations implements SunPkcs11KeyAdapter.CryptoOperations {
        private final java.util.Collection<String> events;
        private final AtomicBoolean failNextAes = new AtomicBoolean();
        private final List<byte[]> aads = new java.util.concurrent.CopyOnWriteArrayList<>();

        private TestCryptoOperations(java.util.Collection<String> events) {
            this.events = events;
        }

        void clearStartupEvents() {
            events.clear();
            aads.clear();
        }

        @Override
        public byte[] aesGcm(boolean encrypt, SecretKey key, byte[] nonce, byte[] aad, byte[] input) {
            events.add("provider");
            aads.add(aad.clone());
            if (failNextAes.compareAndSet(true, false)) {
                throw new IllegalStateException("post-reservation-canary");
            }
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                        new SecretKeySpec(((OpaqueUnitKey) key).material(), "AES"),
                        new GCMParameterSpec(128, nonce));
                cipher.updateAAD(aad);
                return cipher.doFinal(input);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public byte[] hmac(SecretKey key, byte[] input) {
            events.add("provider");
            return SunPkcs11KeyAdapterTest.hmac(((OpaqueUnitKey) key).material(), input);
        }
    }

    private record Boundary(long count, KeyHealth.Status health) {
    }

    @Test
    void liveLifecycleSelectionMovesWritesToV2AndRetainsOnlyPermittedHistoricalReads()
            throws Exception {
        List<Pkcs11KeyDescriptor> descriptors = new ArrayList<>(descriptors());
        descriptors.add(new Pkcs11KeyDescriptor(
                Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 2,
                "unit-kek-v2", "unit-kek-v2", Pkcs11KeyDescriptor.State.PREPARED,
                "AES", 256));
        MutableLifecycleReferences lifecycle = new MutableLifecycleReferences(descriptors);
        Map<Pkcs11KeyDescriptor.Purpose, AtomicLong> counts = new EnumMap<>(
                Pkcs11KeyDescriptor.Purpose.class);
        counts.put(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, new AtomicLong());
        counts.put(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, new AtomicLong());
        SunPkcs11KeyAdapter adapter = adapter(counts, new ArrayList<>(),
                new TestCryptoOperations(new ArrayList<>()), descriptors, lifecycle);
        ProtectionContext context = databaseContext("live-rotation");
        byte[] v1Header = new EnvelopeCodec().authenticatedHeader(
                "unit-kek-v1", 32, EnvelopeCodec.Target.DATABASE_FIELD);
        WrappedDataKey old = adapter.wrap(sequence(32, 71), v1Header, context);

        lifecycle.state(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                KeyState.DECRYPT_ONLY);
        lifecycle.state(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 2,
                KeyState.ACTIVE);
        byte[] v2Header = new EnvelopeCodec().authenticatedHeader(
                "unit-kek-v2", 32, EnvelopeCodec.Target.DATABASE_FIELD);
        assertThat(adapter.wrap(sequence(32, 72), v2Header, context).keyReference())
                .isEqualTo("unit-kek-v2");
        assertThat(adapter.unwrap(old, v1Header, context)).containsExactly(sequence(32, 71));

        lifecycle.state(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                KeyState.RETIRED);
        assertThatThrownBy(() -> adapter.unwrap(old, v1Header, context))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_KEY_UNAVAILABLE");
    }
}
