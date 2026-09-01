package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyLifecycleServiceTest {

    @Test
    void blindIndexRotationWritesSeparateActiveAndRetiringRowsIdempotentlyWithoutLegacyMutation() {
        InMemoryKeys keys = new InMemoryKeys(List.of(
                key(KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 1, "mobile-v1",
                        KeyState.RETIRING, 0, 0),
                key(KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 2, "mobile-v2",
                        KeyState.ACTIVE, 0, 0)));
        RecordingBlindStore store = new RecordingBlindStore();
        BlindIndexRotationService rotation = new BlindIndexRotationService(
                indexes(1, 2), keys, store);
        byte[] originalDigest = digest("legacy-row-42");
        BlindIndexPort.Context context = new BlindIndexPort.Context(
                "MESSAGE_TASK", "mobile", BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:7");
        BlindIndexRotationService.Row legacy = new BlindIndexRotationService.Row(
                "MESSAGE_TASK", 42, "mobile", originalDigest, "13800138000", context);

        List<BlindIndexRotationService.MetadataRow> first = rotation.backfill(legacy);
        List<BlindIndexRotationService.MetadataRow> second = rotation.backfill(legacy);

        assertThat(first).extracting(BlindIndexRotationService.MetadataRow::keyVersion)
                .containsExactly(1L, 2L);
        assertThat(first).extracting(BlindIndexRotationService.MetadataRow::status)
                .containsExactly(KeyState.RETIRING, KeyState.ACTIVE);
        assertThat(second).extracting(BlindIndexRotationService.MetadataRow::keyVersion,
                        BlindIndexRotationService.MetadataRow::status,
                        BlindIndexRotationService.MetadataRow::indexValue)
                .containsExactlyElementsOf(first.stream().map(value -> org.assertj.core.groups.Tuple.tuple(
                        value.keyVersion(), value.status(), value.indexValue())).toList());
        assertThat(store.rows).hasSize(2);
        assertThat(store.upsertCalls).isEqualTo(2);
        assertThat(legacy.targetType()).isEqualTo("MESSAGE_TASK");
        assertThat(legacy.legacyRowId()).isEqualTo(42);
        assertThat(legacy.normalizedMobile()).isEqualTo("13800138000");
        assertThat(legacy.originalRowDigest()).containsExactly(originalDigest);
        assertThat(legacy.context()).isSameAs(context);
    }

    @Test
    void blindIndexRotationFailsClosedOnWrongVersionOrMissingParityRow() {
        InMemoryKeys keys = new InMemoryKeys(List.of(
                key(KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 1, "mobile-v1",
                        KeyState.RETIRING, 0, 0),
                key(KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 2, "mobile-v2",
                        KeyState.ACTIVE, 0, 0)));
        BlindIndexPort.Context context = new BlindIndexPort.Context(
                "MESSAGE_TASK", "mobile", BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:7");
        BlindIndexRotationService.Row row = new BlindIndexRotationService.Row(
                "MESSAGE_TASK", 43, "mobile", digest("legacy-row-43"), "13900139000", context);

        assertThatThrownBy(() -> new BlindIndexRotationService(
                indexes(1, 3), keys, new RecordingBlindStore()).backfill(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(BlindIndexRotationService.SANITIZED_FAILURE);

        RecordingBlindStore missing = new RecordingBlindStore();
        missing.dropLastRow = true;
        assertThatThrownBy(() -> new BlindIndexRotationService(
                indexes(1, 2), keys, missing).backfill(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(BlindIndexRotationService.SANITIZED_FAILURE);
    }

    @Test
    void concurrentActivationNeverCreatesTwoActiveKeys() throws Exception {
        InMemoryKeys keys = new InMemoryKeys(List.of(
                key(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1, "kek-v1",
                        KeyState.ACTIVE, 12, 0),
                key(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 2, "kek-v2",
                        KeyState.PREPARED, 0, 0),
                key(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 3, "kek-v3",
                        KeyState.PREPARED, 0, 0)));
        KeyLifecycleService lifecycle = service(keys, new MutableSource());
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> second = executor.submit(() -> activateAfter(start, lifecycle, 2));
            Future<?> third = executor.submit(() -> activateAfter(start, lifecycle, 3));
            start.countDown();
            awaitIgnoringLifecycleFailure(second);
            awaitIgnoringLifecycleFailure(third);
        }

        List<KeyReferenceRepository.KeyReference> stored = keys.findByPurpose(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK);
        assertThat(stored).filteredOn(key -> key.state().ownsActiveSlot()).hasSize(1);
        assertThat(stored).filteredOn(key -> key.state() == KeyState.DECRYPT_ONLY).isNotEmpty();
    }

    @Test
    void persistedThresholdRequestsRotationAndHardCeilingRemainsBlockedAcrossRestart() {
        InMemoryKeys threshold = new InMemoryKeys(List.of(key(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 7, "kek-v7",
                KeyState.ROTATION_REQUIRED, 983_040, 4)));
        MutableSource source = new MutableSource();

        assertThat(service(threshold, source).wrapDisposition(7))
                .isEqualTo(KeyLifecycleService.WrapDisposition.PREPARE_AND_ACTIVATE);
        assertThat(service(threshold, source).wrapDisposition(7))
                .isEqualTo(KeyLifecycleService.WrapDisposition.PREPARE_AND_ACTIVATE);

        threshold.replace(key(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 7, "kek-v7",
                KeyState.ROTATION_REQUIRED, 1_048_576, 5));
        assertThat(service(threshold, source).wrapDisposition(7))
                .isEqualTo(KeyLifecycleService.WrapDisposition.BLOCKED);
    }

    @Test
    void tokenRetirementIsBlockedUntilExactLiveReferenceCountIsZero() {
        InMemoryKeys keys = new InMemoryKeys(List.of(
                key(KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1, "cap-v1",
                        KeyState.RETIRING, 0, 1),
                key(KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 2, "cap-v2",
                        KeyState.ACTIVE, 0, 0)));
        MutableSource source = new MutableSource();
        source.references = List.of(new EnvelopeReferenceInventory.Reference(source.sourceId(),
                EnvelopeReferenceInventory.Kind.OBJECT_CAPABILITY,
                KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1, digest("capability-1")));
        KeyLifecycleService lifecycle = service(keys, source);

        assertThatThrownBy(() -> lifecycle.retire(
                KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);
        assertThat(keys.findByPurpose(KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST))
                .filteredOn(key -> key.keyVersion() == 1).extracting(KeyReferenceRepository.KeyReference::state)
                .containsExactly(KeyState.RETIRING);

        source.references = List.of();
        KeyLifecycleService.RetirementProof proof = lifecycle.retire(
                KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1);
        assertThat(proof.liveReferences()).isZero();
        assertThat(keys.findByPurpose(KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST))
                .filteredOn(key -> key.keyVersion() == 1).extracting(KeyReferenceRepository.KeyReference::state)
                .containsExactly(KeyState.RETIRED);
    }

    @Test
    void tokenPurposesCannotShareProviderReference() {
        InMemoryKeys keys = new InMemoryKeys(List.of(
                key(KeyReferenceRepository.Purpose.OBJECT_CAPABILITY_DIGEST, 1, "shared-token",
                        KeyState.ACTIVE, 0, 0),
                key(KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 1, "shared-token",
                        KeyState.ACTIVE, 0, 0),
                key(KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 2, "upload-v2",
                        KeyState.PREPARED, 0, 0)));

        assertThatThrownBy(() -> service(keys, new MutableSource()).activate(
                KeyReferenceRepository.Purpose.REGISTRATION_UPLOAD_DIGEST, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);
    }

    private static void activateAfter(CountDownLatch start,
                                      KeyLifecycleService lifecycle,
                                      long version) {
        try {
            start.await();
            lifecycle.activate(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, version);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void awaitIgnoringLifecycleFailure(Future<?> future) throws Exception {
        try {
            future.get();
        } catch (java.util.concurrent.ExecutionException failure) {
            assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class)
                    .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);
        }
    }

    private static KeyLifecycleService service(InMemoryKeys keys, MutableSource source) {
        return new KeyLifecycleService(keys, new EnvelopeReferenceInventory(
                Set.of(source.sourceId()), List.of(source)));
    }

    private static KeyReferenceRepository.KeyReference key(KeyReferenceRepository.Purpose purpose,
                                                            long version,
                                                            String reference,
                                                            KeyState state,
                                                            long wraps,
                                                            long optimistic) {
        return new KeyReferenceRepository.KeyReference(purpose, version, "pkcs11", reference,
                state, wraps, purpose == KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK
                && wraps >= 983_040, optimistic);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static BlindIndexPort indexes(int... versions) {
        return new BlindIndexPort() {
            @Override
            public OrderedIndexes writeIndexes(String normalizedMobile, Context context) {
                List<VersionedBlindIndex> values = new ArrayList<>();
                for (int version : versions) {
                    byte[] hmac = new byte[VersionedBlindIndex.HMAC_BYTES];
                    java.util.Arrays.fill(hmac, (byte) version);
                    values.add(new VersionedBlindIndex(version, hmac));
                }
                return new OrderedIndexes(values);
            }

            @Override
            public OrderedIndexes queryIndexes(String normalizedMobile, Context context) {
                return writeIndexes(normalizedMobile, context);
            }

            @Override
            public KeyHealth health() {
                return new KeyHealth(KeyHealth.Status.READY);
            }
        };
    }

    private static final class RecordingBlindStore implements BlindIndexRotationService.Store {
        private final Map<Long, BlindIndexRotationService.MetadataRow> rows = new LinkedHashMap<>();
        private int upsertCalls;
        private boolean dropLastRow;

        @Override
        public void upsertSeparateRows(List<BlindIndexRotationService.MetadataRow> supplied) {
            upsertCalls++;
            int limit = dropLastRow ? supplied.size() - 1 : supplied.size();
            for (int index = 0; index < limit; index++) {
                BlindIndexRotationService.MetadataRow row = supplied.get(index);
                BlindIndexRotationService.MetadataRow existing = rows.putIfAbsent(row.keyVersion(), row);
                if (existing != null && !same(existing, row)) {
                    throw new IllegalStateException(BlindIndexRotationService.SANITIZED_FAILURE);
                }
            }
        }

        @Override
        public List<BlindIndexRotationService.MetadataRow> find(String targetType,
                                                                 long legacyRowId,
                                                                 String fieldId) {
            return rows.values().stream().filter(row -> row.targetType().equals(targetType)
                    && row.legacyRowId() == legacyRowId && row.fieldId().equals(fieldId)).toList();
        }

        private static boolean same(BlindIndexRotationService.MetadataRow left,
                                    BlindIndexRotationService.MetadataRow right) {
            return left.targetType().equals(right.targetType())
                    && left.legacyRowId() == right.legacyRowId()
                    && left.fieldId().equals(right.fieldId())
                    && left.keyVersion() == right.keyVersion()
                    && left.indexValue().equals(right.indexValue())
                    && left.status() == right.status()
                    && MessageDigest.isEqual(left.originalRowDigest(), right.originalRowDigest());
        }
    }

    private static final class MutableSource implements EnvelopeReferenceInventory.Source {
        private volatile List<EnvelopeReferenceInventory.Reference> references = List.of();

        @Override
        public String sourceId() {
            return "TEST_REFERENCES";
        }

        @Override
        public List<EnvelopeReferenceInventory.Reference> liveReferences() {
            return references;
        }
    }

    private static final class InMemoryKeys implements KeyReferenceRepository {
        private final List<KeyReference> values;

        private InMemoryKeys(List<KeyReference> values) {
            this.values = new ArrayList<>(values);
        }

        @Override
        public synchronized List<KeyReference> findByPurpose(Purpose purpose) {
            return values.stream().filter(key -> key.purpose() == purpose)
                    .sorted(java.util.Comparator.comparingLong(KeyReference::keyVersion)).toList();
        }

        @Override
        public synchronized List<KeyReference> findAll() {
            return List.copyOf(values);
        }

        @Override
        public synchronized boolean transitionAtomically(Purpose purpose, List<Transition> transitions) {
            for (Transition transition : transitions) {
                KeyReference current = values.stream().filter(key -> key.purpose() == purpose
                                && key.keyVersion() == transition.keyVersion()).findFirst().orElse(null);
                if (current == null || current.state() != transition.expectedState()
                        || current.optimisticVersion() != transition.expectedOptimisticVersion()) {
                    return false;
                }
            }
            for (Transition transition : transitions) {
                int index = java.util.stream.IntStream.range(0, values.size())
                        .filter(candidate -> values.get(candidate).purpose() == purpose
                                && values.get(candidate).keyVersion() == transition.keyVersion())
                        .findFirst().orElseThrow();
                KeyReference old = values.get(index);
                values.set(index, new KeyReference(old.purpose(), old.keyVersion(), old.providerId(),
                        old.providerKeyReference(), transition.newState(), old.wrapOperationCount(),
                        old.rotationRequired(), old.optimisticVersion() + 1));
            }
            return values.stream().filter(key -> key.purpose() == purpose
                    && key.state().ownsActiveSlot()).count() <= 1;
        }

        synchronized void replace(KeyReference replacement) {
            values.removeIf(key -> key.purpose() == replacement.purpose()
                    && key.keyVersion() == replacement.keyVersion());
            values.add(replacement);
        }
    }
}
