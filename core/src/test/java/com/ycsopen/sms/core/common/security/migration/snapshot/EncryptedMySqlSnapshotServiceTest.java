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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedMySqlSnapshotServiceTest {

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
        return new ProtectedFieldCodec(
                new EnvelopeCodec(), new RawTestKeyPort(recoveryKey),
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

        @Override
        public void putComplete(String snapshotId, int index, byte[] completeEnvelope) {
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

        List<byte[]> values() {
            return envelopes.values().stream().map(byte[]::clone).toList();
        }

        MemoryStore copy() {
            MemoryStore copy = new MemoryStore();
            envelopes.forEach((index, value) -> copy.envelopes.put(index, value.clone()));
            copy.puts = puts;
            return copy;
        }
    }

    private static final class FakeProcess implements MySqlSnapshotProcess {
        private final PatternInputStream dumpInput;
        private final ByteArrayOutputStream restoredBytes = new ByteArrayOutputStream();
        private int dumpStarts;
        private int restoreStarts;
        private int failRestoreAfter = -1;

        private FakeProcess(long dumpBytes) {
            this.dumpInput = new PatternInputStream(dumpBytes);
        }

        @Override
        public DumpSession startDump(Database source) {
            dumpStarts++;
            return new DumpSession() {
                @Override
                public InputStream stdout() {
                    return dumpInput;
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

        private RawTestKeyPort(String keyReference) {
            this.keyReference = keyReference;
        }

        @Override
        public WrappedDataKey wrap(
                byte[] dataEncryptionKey,
                byte[] authenticatedHeader,
                ProtectionContext semanticContext) {
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
