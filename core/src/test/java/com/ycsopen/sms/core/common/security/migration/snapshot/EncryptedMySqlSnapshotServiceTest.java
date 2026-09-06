package com.ycsopen.sms.core.common.security.migration.snapshot;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.migration.EncryptedSnapshotVerifier;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import com.ycsopen.sms.core.common.security.migration.snapshot.EncryptedMySqlSnapshotService.CreateRequest;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.Database;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedMySqlSnapshotServiceTest {

    @TempDir
    Path directory;

    private static final int CHUNK_BYTES = Math.toIntExact(
            EncryptedSnapshotVerifier.MAXIMUM_CHUNK_PLAINTEXT_BYTES);
    private static final String RECOVERY_KEY = "snapshot-recovery.v1";
    private static final String OTHER_RECOVERY_KEY = "snapshot-recovery.v2";
    private static final String SNAPSHOT_ID = "snapshot-test";
    private static final SnapshotManifest.Subject SUBJECT = new SnapshotManifest.Subject(
            "migration-set-test", "test", "1".repeat(64), "source_schema",
            "2".repeat(64), 7, "signer-v1");
    private static final Database SOURCE = new Database(
            "127.0.0.1", 3306, "snapshot_user", "source-secret".toCharArray(),
            SUBJECT.schema());
    private static final Database TARGET = new Database(
            "127.0.0.1", 3306, "snapshot_user", "target-secret".toCharArray(),
            "restore_schema");

    @Test
    void exactBoundaryOneByteLookaheadAndMultipleChunksStayBoundedAndEncrypted() {
        assertChunking(CHUNK_BYTES, List.of((long) CHUNK_BYTES), 1);
        assertChunking(CHUNK_BYTES + 1L, List.of((long) CHUNK_BYTES, 1L), 2);
        assertChunking(CHUNK_BYTES * 2L + 17L,
                List.of((long) CHUNK_BYTES, (long) CHUNK_BYTES, 17L), 3);
    }

    @Test
    void durableReservationIsVisibleBeforeTheFirstRecoveryKeyWrap() {
        MemoryStore store = new MemoryStore();
        AtomicBoolean observed = new AtomicBoolean();
        RawTestKeyPort keyPort = new RawTestKeyPort(RECOVERY_KEY, () -> {
            assertThat(store.reserved).isTrue();
            assertThatThrownBy(store::retainedManifests)
                    .isInstanceOf(SnapshotManifest.SnapshotException.class);
            observed.set(true);
        });
        EncryptedMySqlSnapshotService service = new EncryptedMySqlSnapshotService(
                codec(RECOVERY_KEY, keyPort), new FakeProcess(257), store,
                (source, target) -> { });

        SnapshotManifest manifest = service.create(new CreateRequest(
                SOURCE, SUBJECT, SNAPSHOT_ID, RECOVERY_KEY));

        assertThat(observed).isTrue();
        assertThat(store.retainedManifests())
                .extracting(SnapshotChunkStore.RetainedManifest::snapshotId)
                .containsExactly(manifest.snapshotId());
    }

    @Test
    void rejectsMissingDuplicateReorderedAndPostFinalInventoryBeforeRestoreStarts() {
        Fixture fixture = createFixture(CHUNK_BYTES + 1L, SNAPSHOT_ID, RECOVERY_KEY);
        List<SnapshotChunkStore.StoredChunk> valid = fixture.store.defaultInventory();

        assertPreflightRejected(fixture.copyWithInventory(List.of()), fixture.manifest);
        assertPreflightRejected(fixture.copyWithInventory(List.of(valid.get(0), valid.get(0))),
                fixture.manifest);
        assertPreflightRejected(fixture.copyWithInventory(List.of(valid.get(1), valid.get(0))),
                fixture.manifest);
        assertPreflightRejected(fixture.copyWithInventory(List.of(
                valid.get(0), valid.get(1), new SnapshotChunkStore.StoredChunk(2, 1))),
                fixture.manifest);

        SnapshotManifest.Chunk first = fixture.manifest.chunks().get(0);
        SnapshotManifest.Chunk second = fixture.manifest.chunks().get(1);
        assertThatThrownBy(() -> new SnapshotManifest(
                SUBJECT, SNAPSHOT_ID, RECOVERY_KEY,
                fixture.manifest.totalPlaintextBytes(), fixture.manifest.totalEnvelopeBytes(),
                List.of(chunk(first, 0, true), chunk(second, 1, true))))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
    }

    @Test
    void rejectsTruncatedAndExtraEnvelopeBytesBeforeRestoreStarts() {
        Fixture fixture = createFixture(257, SNAPSHOT_ID, RECOVERY_KEY);
        byte[] envelope = fixture.store.require(0);
        List<SnapshotChunkStore.StoredChunk> reported = fixture.store.defaultInventory();

        Fixture truncated = fixture.copy();
        truncated.store.replace(0, Arrays.copyOf(envelope, envelope.length - 1));
        truncated.store.inventoryOverride = reported;
        assertPreflightRejected(truncated, fixture.manifest);

        Fixture extra = fixture.copy();
        extra.store.replace(0, Arrays.copyOf(envelope, envelope.length + 1));
        extra.store.inventoryOverride = reported;
        assertPreflightRejected(extra, fixture.manifest);
    }

    @Test
    void rejectsWrongContextDigestSizeAndTamperedWrappedKeyBeforeRestoreStarts() {
        Fixture fixture = createFixture(257, SNAPSHOT_ID, RECOVERY_KEY);

        SnapshotManifest badDigest = replaceSingleChunk(
                fixture.manifest, fixture.manifest.chunks().getFirst().envelopeSize(),
                "0".repeat(64));
        assertPreflightRejected(fixture.copy(), badDigest);

        SnapshotManifest.Chunk original = fixture.manifest.chunks().getFirst();
        SnapshotManifest badSize = new SnapshotManifest(
                SUBJECT, SNAPSHOT_ID, RECOVERY_KEY, original.plaintextSize(),
                original.envelopeSize() + 1,
                List.of(new SnapshotManifest.Chunk(0, true, original.plaintextSize(),
                        original.envelopeSize() + 1, original.sha256Digest())));
        assertPreflightRejected(fixture.copy(), badSize);

        Fixture otherContext = createFixture(257, "snapshot-other", RECOVERY_KEY);
        byte[] wrongContextEnvelope = otherContext.store.require(0);
        Fixture wrongContext = fixture.copy();
        wrongContext.store.replace(0, wrongContextEnvelope);
        SnapshotManifest wrongContextManifest = replaceSingleChunk(
                fixture.manifest, wrongContextEnvelope.length, sha256(wrongContextEnvelope));
        assertPreflightRejected(wrongContext, wrongContextManifest);

        Fixture tamperedKey = fixture.copy();
        byte[] changedWrappedKey = tamperWrappedKey(tamperedKey.store.require(0));
        tamperedKey.store.replace(0, changedWrappedKey);
        SnapshotManifest tamperedKeyManifest = replaceSingleChunk(
                fixture.manifest, changedWrappedKey.length, sha256(changedWrappedKey));
        assertPreflightRejected(tamperedKey, tamperedKeyManifest);

        Fixture otherKey = createFixture(257, SNAPSHOT_ID, OTHER_RECOVERY_KEY);
        byte[] wrongReferenceEnvelope = otherKey.store.require(0);
        Fixture wrongReference = fixture.copy();
        wrongReference.store.replace(0, wrongReferenceEnvelope);
        SnapshotManifest wrongReferenceManifest = replaceSingleChunk(
                fixture.manifest, wrongReferenceEnvelope.length, sha256(wrongReferenceEnvelope));
        assertPreflightRejected(wrongReference, wrongReferenceManifest);
    }

    @Test
    void authenticatesTheCompleteInventoryBeforeFreshSchemaOrMysqlProcess() {
        Fixture fixture = createFixture(CHUNK_BYTES + 1L, SNAPSHOT_ID, RECOVERY_KEY);
        byte[] terminal = fixture.store.require(1);
        terminal[terminal.length - 1] ^= 1;
        fixture.store.replace(1, terminal);

        assertThatThrownBy(() -> fixture.restore(fixture.manifest))
                .isInstanceOf(RuntimeException.class);
        assertThat(fixture.process.restoreStarts).isZero();
        assertThat(fixture.freshSchemaChecks()).isZero();
        assertThat(fixture.store.completed).isFalse();
    }

    @Test
    void productionAdmissionProofUsesExactRetainedBytesAndDeleteRequiresExactDigest() {
        Fixture fixture = createFixture(257, SNAPSHOT_ID, RECOVERY_KEY);
        EncryptedMySqlSnapshotService service = fixture.service();

        service.requireCompleteRetainedSnapshot(fixture.manifest.canonicalBytes());
        byte[] different = fixture.manifest.canonicalBytes();
        different[different.length - 1] ^= 1;
        assertThatThrownBy(() -> service.requireCompleteRetainedSnapshot(different))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThatThrownBy(() -> service.delete(SNAPSHOT_ID, "0".repeat(64)))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);

        assertThat(service.delete(SNAPSHOT_ID, fixture.manifest.digest()))
                .isEqualTo(SNAPSHOT_ID);
        assertThat(fixture.store.envelopes).isEmpty();
        assertThat(fixture.store.manifest).isNull();
    }

    @Test
    void deletingDottedPrefixSnapshotPreservesOtherSnapshotCompletionMarkers() throws Exception {
        SnapshotChunkStore.FileStore store = new SnapshotChunkStore.FileStore(
                Files.createDirectory(directory.resolve("completion-isolation")));
        String digestA = "a".repeat(64);
        String digestDotted = "b".repeat(64);
        store.markRecoveryComplete("a", "restore_a", digestA);
        store.markRecoveryComplete("a.b", "restore_ab", digestDotted);

        store.deleteSnapshot("a");

        assertThat(store.recoveryComplete("a", "restore_a", digestA)).isFalse();
        assertThat(store.recoveryComplete("a.b", "restore_ab", digestDotted)).isTrue();
    }

    @Test
    void concurrentFirstMarkersForTwoTargetsShareOneRaceSafeSnapshotDirectory()
            throws Exception {
        CyclicBarrier creators = new CyclicBarrier(2);
        SnapshotChunkStore.FileStore store = new SnapshotChunkStore.FileStore(
                Files.createDirectory(directory.resolve("concurrent-completions")),
                ignored -> await(creators));
        String digest = "c".repeat(64);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() ->
                    store.markRecoveryComplete("snapshot.concurrent", "restore_one", digest));
            var second = executor.submit(() ->
                    store.markRecoveryComplete("snapshot.concurrent", "restore_two", digest));
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }

        assertThat(store.recoveryComplete(
                "snapshot.concurrent", "restore_one", digest)).isTrue();
        assertThat(store.recoveryComplete(
                "snapshot.concurrent", "restore_two", digest)).isTrue();
    }

    @Test
    void partialRestoreNeverCreatesARecoveryCompletionMarker() {
        Fixture fixture = createFixture(1024, SNAPSHOT_ID, RECOVERY_KEY);
        fixture.process.failRestoreAfter = 113;

        assertThatThrownBy(() -> fixture.restore(fixture.manifest))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(fixture.process.restoreStarts).isOne();
        assertThat(fixture.process.restoredBytes.size()).isEqualTo(113);
        assertThat(fixture.freshSchemaChecks()).isOne();
        assertThat(fixture.store.completed).isFalse();
    }

    @Test
    void validRestoreStreamsAllBytesAndIdempotentlyUsesTheCompletionMarker() {
        Fixture fixture = createFixture(65_539, SNAPSHOT_ID, RECOVERY_KEY);

        EncryptedMySqlSnapshotService.RestoreResult first = fixture.restore(fixture.manifest);
        EncryptedMySqlSnapshotService.RestoreResult second = fixture.restore(fixture.manifest);

        assertThat(first.alreadyComplete()).isFalse();
        assertThat(second.alreadyComplete()).isTrue();
        assertThat(fixture.process.restoreStarts).isOne();
        assertThat(fixture.process.restoredBytes.toByteArray())
                .isEqualTo(pattern(65_539));
        assertThat(fixture.store.completed).isTrue();
    }

    @Test
    void staleCompletionRejectsDroppedOrMutatedTargetAndUnchangedTargetRemainsIdempotent() {
        Fixture dropped = createFixture(4_097, SNAPSHOT_ID, RECOVERY_KEY);
        assertThat(dropped.restore(dropped.manifest).alreadyComplete()).isFalse();
        dropped.process.targetAvailable = false;
        assertThatThrownBy(() -> dropped.restore(dropped.manifest))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);

        Fixture mutated = createFixture(4_097, "snapshot-mutated", RECOVERY_KEY);
        assertThat(mutated.restore(mutated.manifest).alreadyComplete()).isFalse();
        byte[] changed = mutated.process.restoredBytes.toByteArray();
        changed[changed.length - 1] ^= 1;
        mutated.process.targetDumpOverride = changed;
        assertThatThrownBy(() -> mutated.restore(mutated.manifest))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);

        mutated.process.targetDumpOverride = null;
        assertThat(mutated.restore(mutated.manifest).alreadyComplete()).isTrue();
        assertThat(mutated.process.restoreStarts).isOne();
    }

    @Test
    void staleCompletionRejectsDeletedOrCorruptedRetainedChunkBeforeTargetDump() {
        Fixture deleted = createFixture(4_097, SNAPSHOT_ID, RECOVERY_KEY);
        deleted.restore(deleted.manifest);
        int dumpsBeforeDelete = deleted.process.targetDumpStarts;
        deleted.store.remove(0);
        assertThatThrownBy(() -> deleted.restore(deleted.manifest))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(deleted.process.targetDumpStarts).isEqualTo(dumpsBeforeDelete);

        Fixture corrupted = createFixture(4_097, "snapshot-corrupted", RECOVERY_KEY);
        corrupted.restore(corrupted.manifest);
        int dumpsBeforeCorruption = corrupted.process.targetDumpStarts;
        byte[] envelope = corrupted.store.require(0);
        envelope[envelope.length - 1] ^= 1;
        corrupted.store.replace(0, envelope);
        assertThatThrownBy(() -> corrupted.restore(corrupted.manifest))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(corrupted.process.targetDumpStarts).isEqualTo(dumpsBeforeCorruption);
    }

    @Test
    void firstRestoreMismatchDoesNotPublishCompletionMarker() {
        Fixture fixture = createFixture(4_097, SNAPSHOT_ID, RECOVERY_KEY);
        fixture.process.targetDumpOverride = new byte[]{1, 2, 3};

        assertThatThrownBy(() -> fixture.restore(fixture.manifest))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
        assertThat(fixture.store.completed).isFalse();
        assertThat(fixture.process.restoreStarts).isOne();
    }

    @Test
    void manifestUsesCheckedSnapshotTotalsAndCountBounds() {
        int fullChunks = Math.toIntExact(
                EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES / CHUNK_BYTES);
        long finalBytes = EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES
                % CHUNK_BYTES;
        List<SnapshotManifest.Chunk> maximum = new ArrayList<>(fullChunks + 1);
        for (int index = 0; index < fullChunks; index++) {
            maximum.add(new SnapshotManifest.Chunk(index, false, CHUNK_BYTES,
                    CHUNK_BYTES + EnvelopeCodec.MAXIMUM_OVERHEAD_BYTES, "a".repeat(64)));
        }
        maximum.add(new SnapshotManifest.Chunk(fullChunks, true, finalBytes,
                finalBytes + EnvelopeCodec.MAXIMUM_OVERHEAD_BYTES, "a".repeat(64)));

        SnapshotManifest boundary = new SnapshotManifest(
                SUBJECT, SNAPSHOT_ID, RECOVERY_KEY,
                EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES,
                EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES, maximum);
        assertThat(boundary.chunks()).hasSize(EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT);

        List<SnapshotManifest.Chunk> overCount = new ArrayList<>(maximum);
        overCount.add(maximum.getLast());
        assertThatThrownBy(() -> new SnapshotManifest(
                SUBJECT, SNAPSHOT_ID, RECOVERY_KEY,
                boundary.totalPlaintextBytes(), boundary.totalEnvelopeBytes(), overCount))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);

        SnapshotManifest.Chunk terminal = maximum.getLast();
        List<SnapshotManifest.Chunk> overTotal = new ArrayList<>(maximum);
        overTotal.set(fullChunks, new SnapshotManifest.Chunk(
                terminal.index(), true, terminal.plaintextSize() + 1,
                terminal.envelopeSize() + 1, terminal.sha256Digest()));
        assertThatThrownBy(() -> new SnapshotManifest(
                SUBJECT, SNAPSHOT_ID, RECOVERY_KEY,
                EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES + 1,
                EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES + 1, overTotal))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);

        assertThatThrownBy(() -> new SnapshotManifest(
                SUBJECT, SNAPSHOT_ID, RECOVERY_KEY, Long.MAX_VALUE, Long.MAX_VALUE,
                List.of(new SnapshotManifest.Chunk(
                        0, true, 1, 146, "a".repeat(64)))))
                .isInstanceOf(SnapshotManifest.SnapshotException.class);
    }

    private static void assertChunking(
            long dumpBytes, List<Long> expectedPlaintextSizes, int expectedLookaheadReads) {
        Fixture fixture = createFixture(dumpBytes, SNAPSHOT_ID, RECOVERY_KEY);

        assertThat(fixture.manifest.chunks())
                .extracting(SnapshotManifest.Chunk::plaintextSize)
                .containsExactlyElementsOf(expectedPlaintextSizes);
        assertThat(fixture.manifest.totalPlaintextBytes()).isEqualTo(dumpBytes);
        assertThat(fixture.process.dumpInput.maximumBulkRead).isLessThanOrEqualTo(CHUNK_BYTES);
        assertThat(fixture.process.dumpInput.singleByteReads).isEqualTo(expectedLookaheadReads);
        assertThat(fixture.store.values())
                .allSatisfy(envelope -> assertThat(envelope)
                        .startsWith(new byte[]{'Y', 'C', 'S', 'E'}));
        assertThat(fixture.store.puts).isEqualTo(expectedPlaintextSizes.size());
        assertThat(fixture.process.dumpStarts).isOne();
        assertThat(fixture.process.restoreStarts).isZero();
    }

    private static void assertPreflightRejected(Fixture fixture, SnapshotManifest manifest) {
        assertThatThrownBy(() -> fixture.restore(manifest)).isInstanceOf(RuntimeException.class);
        assertThat(fixture.process.restoreStarts).isZero();
        assertThat(fixture.freshSchemaChecks()).isZero();
        assertThat(fixture.store.completed).isFalse();
    }

    private static Fixture createFixture(long dumpBytes, String snapshotId, String recoveryKey) {
        MemoryStore store = new MemoryStore();
        FakeProcess process = new FakeProcess(dumpBytes);
        MutableCounter fresh = new MutableCounter();
        EncryptedMySqlSnapshotService service = new EncryptedMySqlSnapshotService(
                codec(recoveryKey), process, store, (source, target) -> fresh.value++);
        SnapshotManifest manifest = service.create(new CreateRequest(
                SOURCE, SUBJECT, snapshotId, recoveryKey));
        return new Fixture(manifest, store, process, fresh, recoveryKey);
    }

    private static ProtectedFieldCodec codec(String recoveryKey) {
        return codec(recoveryKey, new RawTestKeyPort(recoveryKey));
    }

    private static ProtectedFieldCodec codec(String recoveryKey, KeyProtectionPort keyPort) {
        return new ProtectedFieldCodec(
                new EnvelopeCodec(), keyPort,
                new SequenceSecureRandom(), recoveryKey);
    }

    private static SnapshotManifest replaceSingleChunk(
            SnapshotManifest original, long envelopeSize, String digest) {
        SnapshotManifest.Chunk chunk = original.chunks().getFirst();
        return new SnapshotManifest(
                original.subject(), original.snapshotId(), original.recoveryKeyReference(),
                original.totalPlaintextBytes(), envelopeSize,
                List.of(new SnapshotManifest.Chunk(
                        0, true, chunk.plaintextSize(), envelopeSize, digest)));
    }

    private static SnapshotManifest.Chunk chunk(
            SnapshotManifest.Chunk source, int index, boolean terminal) {
        return new SnapshotManifest.Chunk(index, terminal, source.plaintextSize(),
                source.envelopeSize(), source.sha256Digest());
    }

    private static byte[] tamperWrappedKey(byte[] encoded) {
        EnvelopeCodec codec = new EnvelopeCodec();
        CipherEnvelope decoded = codec.decode(
                encoded, EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
        byte[] wrapped = decoded.wrappedDek();
        wrapped[0] ^= 1;
        return codec.encode(new CipherEnvelope(
                decoded.providerId(), decoded.keyReference(), decoded.wrapNonce(), wrapped,
                decoded.dataNonce(), decoded.ciphertext()),
                EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
    }

    private static byte[] pattern(int length) {
        byte[] value = new byte[length];
        for (int index = 0; index < length; index++) {
            value[index] = PatternInputStream.valueAt(index);
        }
        return value;
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static PairedAdmission admission(SnapshotManifest manifest) {
        return new PairedAdmission(
                manifest.subject().globalSequence(), manifest.subject().signerKeyVersion(),
                "3".repeat(64), manifest.digest(), "4".repeat(64), Set.of("core"),
                manifest.snapshotId(), manifest.recoveryKeyReference());
    }

    private record Fixture(
            SnapshotManifest manifest,
            MemoryStore store,
            FakeProcess process,
            MutableCounter fresh,
            String recoveryKey) {

        int freshSchemaChecks() {
            return fresh.value;
        }

        EncryptedMySqlSnapshotService.RestoreResult restore(SnapshotManifest selectedManifest) {
            return service().restore(
                    selectedManifest.canonicalBytes(), admission(selectedManifest), SOURCE, TARGET);
        }

        Fixture copy() {
            return copyWithInventory(null);
        }

        Fixture copyWithInventory(List<SnapshotChunkStore.StoredChunk> inventory) {
            MemoryStore copied = store.copy();
            copied.inventoryOverride = inventory == null ? null : List.copyOf(inventory);
            return new Fixture(manifest, copied, new FakeProcess(1), new MutableCounter(), recoveryKey);
        }

        private EncryptedMySqlSnapshotService service() {
            return new EncryptedMySqlSnapshotService(
                    codec(recoveryKey), process, store,
                    (source, target) -> fresh.value++);
        }
    }

    private static final class MutableCounter {
        private int value;
    }

    private static final class MemoryStore implements SnapshotChunkStore {
        private final Map<Integer, byte[]> envelopes = new HashMap<>();
        private List<StoredChunk> inventoryOverride;
        private int puts;
        private boolean completed;
        private String completionDigest;
        private byte[] manifest;
        private boolean reserved;

        @Override
        public void beginSnapshot(String snapshotId) {
            if (reserved) {
                throw SnapshotManifest.invalid();
            }
            reserved = true;
        }

        @Override
        public void putComplete(String snapshotId, int index, byte[] completeEnvelope) {
            if (!reserved) {
                throw SnapshotManifest.invalid();
            }
            puts++;
            if (envelopes.putIfAbsent(index, completeEnvelope.clone()) != null) {
                throw SnapshotManifest.invalid();
            }
        }

        @Override
        public List<StoredChunk> inventory(String snapshotId) {
            return inventoryOverride == null ? defaultInventory() : inventoryOverride;
        }

        List<StoredChunk> defaultInventory() {
            return envelopes.entrySet().stream()
                    .map(entry -> new StoredChunk(entry.getKey(), entry.getValue().length))
                    .sorted(Comparator.comparingInt(StoredChunk::index))
                    .toList();
        }

        @Override
        public InputStream open(String snapshotId, int index, long expectedEnvelopeSize) {
            return new ByteArrayInputStream(require(index).clone());
        }

        @Override
        public void markRecoveryComplete(
                String snapshotId, String targetSchema, String snapshotDigest) {
            completed = true;
            completionDigest = snapshotDigest;
        }

        @Override
        public boolean recoveryComplete(
                String snapshotId, String targetSchema, String snapshotDigest) {
            return completed && snapshotDigest.equals(completionDigest);
        }

        @Override
        public void deleteSnapshot(String snapshotId) {
            envelopes.clear();
            manifest = null;
            reserved = false;
        }

        @Override
        public void putManifest(String snapshotId, byte[] canonicalManifest) {
            SnapshotManifest parsed = SnapshotManifest.parse(canonicalManifest);
            if (!snapshotId.equals(parsed.snapshotId()) || manifest != null) {
                throw SnapshotManifest.invalid();
            }
            manifest = canonicalManifest.clone();
        }

        @Override
        public List<RetainedManifest> retainedManifests() {
            if (reserved && manifest == null) {
                throw SnapshotManifest.invalid();
            }
            return manifest == null ? List.of() : List.of(new RetainedManifest(
                    SnapshotManifest.parse(manifest).snapshotId(), manifest));
        }

        byte[] require(int index) {
            byte[] value = envelopes.get(index);
            if (value == null) {
                throw SnapshotManifest.invalid();
            }
            return value.clone();
        }

        void replace(int index, byte[] envelope) {
            envelopes.put(index, envelope.clone());
        }

        void remove(int index) {
            envelopes.remove(index);
        }

        List<byte[]> values() {
            return envelopes.values().stream().map(byte[]::clone).toList();
        }

        MemoryStore copy() {
            MemoryStore copy = new MemoryStore();
            envelopes.forEach((index, value) -> copy.envelopes.put(index, value.clone()));
            copy.puts = puts;
            copy.manifest = manifest == null ? null : manifest.clone();
            copy.reserved = reserved;
            return copy;
        }
    }

    private static final class FakeProcess implements MySqlSnapshotProcess {
        private final long dumpBytes;
        private PatternInputStream dumpInput;
        private final ByteArrayOutputStream restoredBytes = new ByteArrayOutputStream();
        private int dumpStarts;
        private int targetDumpStarts;
        private int restoreStarts;
        private int failRestoreAfter = -1;
        private boolean targetAvailable = true;
        private byte[] targetDumpOverride;

        private FakeProcess(long dumpBytes) {
            this.dumpBytes = dumpBytes;
        }

        @Override
        public DumpSession startDump(Database source) {
            dumpStarts++;
            boolean target = !source.schema().equals(SOURCE.schema());
            InputStream output;
            if (target) {
                targetDumpStarts++;
                if (!targetAvailable) {
                    throw SnapshotManifest.invalid();
                }
                output = new ByteArrayInputStream(targetDumpOverride == null
                        ? restoredBytes.toByteArray() : targetDumpOverride.clone());
            } else {
                dumpInput = new PatternInputStream(dumpBytes);
                output = dumpInput;
            }
            return new DumpSession() {
                @Override
                public InputStream stdout() {
                    return output;
                }

                @Override
                public void awaitSuccess() {
                    // Deterministic in-memory process has already produced all requested bytes.
                }

                @Override
                public void close() {
                    // No external resource.
                }
            };
        }

        @Override
        public RestoreSession startRestore(Database target) {
            restoreStarts++;
            OutputStream output = failRestoreAfter < 0
                    ? restoredBytes
                    : new FailingOutputStream(restoredBytes, failRestoreAfter);
            return new RestoreSession() {
                @Override
                public OutputStream stdin() {
                    return output;
                }

                @Override
                public void awaitSuccess() {
                    // Deterministic in-memory process accepts a complete stdin stream.
                }

                @Override
                public void close() {
                    // No external resource.
                }
            };
        }
    }

    private static final class FailingOutputStream extends OutputStream {
        private final ByteArrayOutputStream accepted;
        private int remaining;

        private FailingOutputStream(ByteArrayOutputStream accepted, int acceptedBytes) {
            this.accepted = accepted;
            this.remaining = acceptedBytes;
        }

        @Override
        public void write(int value) throws IOException {
            if (remaining == 0) {
                throw new IOException("controlled restore failure");
            }
            accepted.write(value);
            remaining--;
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            int acceptedLength = Math.min(length, remaining);
            accepted.write(value, offset, acceptedLength);
            remaining -= acceptedLength;
            if (acceptedLength != length) {
                throw new IOException("controlled restore failure");
            }
        }
    }

    private static final class PatternInputStream extends InputStream {
        private long remaining;
        private long position;
        private int maximumBulkRead;
        private int singleByteReads;

        private PatternInputStream(long length) {
            remaining = length;
        }

        @Override
        public int read() {
            singleByteReads++;
            if (remaining == 0) {
                return -1;
            }
            int value = valueAt(position) & 0xff;
            position++;
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] output, int offset, int length) {
            maximumBulkRead = Math.max(maximumBulkRead, length);
            if (remaining == 0) {
                return -1;
            }
            int count = Math.toIntExact(Math.min(remaining, length));
            for (int index = 0; index < count; index++) {
                output[offset + index] = valueAt(position + index);
            }
            position += count;
            remaining -= count;
            return count;
        }

        private static byte valueAt(long index) {
            return (byte) ((index * 31 + 17) & 0xff);
        }
    }

    private static final class SequenceSecureRandom extends SecureRandom {
        private int call;

        @Override
        public void nextBytes(byte[] bytes) {
            call++;
            for (int index = 0; index < bytes.length; index++) {
                bytes[index] = (byte) (call * 17 + index);
            }
        }
    }

    private static final class RawTestKeyPort implements KeyProtectionPort {
        private final String keyReference;
        private final Runnable beforeWrap;

        private RawTestKeyPort(String keyReference) {
            this(keyReference, () -> { });
        }

        private RawTestKeyPort(String keyReference, Runnable beforeWrap) {
            this.keyReference = keyReference;
            this.beforeWrap = beforeWrap;
        }

        @Override
        public WrappedDataKey wrap(
                byte[] dataEncryptionKey,
                byte[] authenticatedHeader,
                ProtectionContext semanticContext) {
            beforeWrap.run();
            byte[] wrapped = new byte[WrappedDataKey.WRAPPED_DEK_BYTES];
            System.arraycopy(dataEncryptionKey, 0, wrapped, 0, dataEncryptionKey.length);
            return new WrappedDataKey(
                    keyReference, new byte[WrappedDataKey.WRAP_NONCE_BYTES], wrapped);
        }

        @Override
        public byte[] unwrap(
                WrappedDataKey wrappedDataKey,
                byte[] authenticatedHeader,
                ProtectionContext semanticContext) {
            return Arrays.copyOf(
                    wrappedDataKey.wrappedDek(), KeyProtectionPort.DATA_ENCRYPTION_KEY_BYTES);
        }

        @Override
        public KeyHealth health() {
            return new KeyHealth(KeyHealth.Status.READY);
        }
    }
}
