package com.ycsopen.sms.core.common.security.migration.snapshot;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.migration.EncryptedSnapshotVerifier;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.Database;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Production owner of bounded dump chunking, YCSE protection, complete inventory admission and
 * direct-to-MySQL restore. It never creates a plaintext file or retains a whole dump buffer.
 */
public final class EncryptedMySqlSnapshotService {

    private static final int CHUNK_BYTES = Math.toIntExact(
            EncryptedSnapshotVerifier.MAXIMUM_CHUNK_PLAINTEXT_BYTES);
    private final ProtectedFieldCodec recoveryCodec;
    private final MySqlSnapshotProcess processes;
    private final SnapshotChunkStore chunks;
    private final FreshSchemaGate freshSchemaGate;

    public EncryptedMySqlSnapshotService(
            ProtectedFieldCodec recoveryCodec,
            MySqlSnapshotProcess processes,
            SnapshotChunkStore chunks,
            FreshSchemaGate freshSchemaGate) {
        this.recoveryCodec = Objects.requireNonNull(recoveryCodec, "recoveryCodec");
        this.processes = Objects.requireNonNull(processes, "processes");
        this.chunks = Objects.requireNonNull(chunks, "chunks");
        this.freshSchemaGate = Objects.requireNonNull(freshSchemaGate, "freshSchemaGate");
    }

    /** Streams a fixed-argument mysqldump into atomically persisted encrypted chunks. */
    public SnapshotManifest create(CreateRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.source().schema().equals(request.subject().schema())) {
            throw SnapshotManifest.invalid();
        }
        boolean complete = false;
        try (MySqlSnapshotProcess.DumpSession dump = processes.startDump(request.source())) {
            SnapshotManifest manifest = protectDump(dump.stdout(), request);
            dump.awaitSuccess();
            complete = true;
            return manifest;
        } catch (RuntimeException exception) {
            cleanupFailedSnapshot(request.snapshotId(), exception);
            throw exception;
        } finally {
            if (!complete) {
                // The catch owns cleanup for runtime failures; this covers close-time failures.
                cleanupQuietly(request.snapshotId());
            }
        }
    }

    /**
     * Restores only an admitted manifest. Every encrypted chunk is fetched, digested and
     * authenticated before the MySQL client is started; plaintext is then streamed one chunk at
     * a time and a completion marker is admitted only after the client exits successfully.
     */
    public RestoreResult restore(
            byte[] admittedManifestBytes,
            PairedAdmission admission,
            Database source,
            Database freshTarget) {
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(freshTarget, "freshTarget");
        SnapshotManifest manifest = SnapshotManifest.parse(admittedManifestBytes);
        String manifestDigest = manifest.digest();
        if (!manifestDigest.equals(admission.snapshotDigest())
                || !manifest.snapshotId().equals(admission.snapshotId())
                || !manifest.recoveryKeyReference().equals(admission.recoveryKeyReference())
                || manifest.subject().globalSequence() != admission.globalSequence()
                || !manifest.subject().schema().equals(source.schema())
                || source.schema().equals(freshTarget.schema())) {
            throw SnapshotManifest.invalid();
        }
        if (chunks.recoveryComplete(
                manifest.snapshotId(), freshTarget.schema(), manifestDigest)) {
            return new RestoreResult(manifest.snapshotId(), freshTarget.schema(), true);
        }

        requireCompleteInventory(manifest);
        authenticateCompleteInventory(manifest);
        freshSchemaGate.requireFresh(source, freshTarget);

        try (MySqlSnapshotProcess.RestoreSession restore = processes.startRestore(freshTarget)) {
            streamPlaintext(manifest, restore.stdin());
            restore.awaitSuccess();
        }
        chunks.markRecoveryComplete(manifest.snapshotId(), freshTarget.schema(), manifestDigest);
        if (!chunks.recoveryComplete(
                manifest.snapshotId(), freshTarget.schema(), manifestDigest)) {
            throw SnapshotManifest.invalid();
        }
        return new RestoreResult(manifest.snapshotId(), freshTarget.schema(), false);
    }

    private SnapshotManifest protectDump(InputStream input, CreateRequest request) {
        Objects.requireNonNull(input, "input");
        List<SnapshotManifest.Chunk> inventory = new ArrayList<>();
        long totalPlaintext = 0;
        long totalEnvelope = 0;
        int index = 0;
        int carried = -1;
        while (true) {
            if (index >= EncryptedSnapshotVerifier.MAXIMUM_CHUNK_COUNT) {
                throw SnapshotManifest.invalid();
            }
            byte[] plaintext = new byte[CHUNK_BYTES];
            int length = 0;
            if (carried >= 0) {
                plaintext[length++] = (byte) carried;
                carried = -1;
            }
            try {
                length = fill(input, plaintext, length);
                if (length == 0) {
                    throw SnapshotManifest.invalid();
                }
                int lookahead = input.read();
                boolean terminal = lookahead == -1;
                if (!terminal) {
                    carried = lookahead;
                }
                totalPlaintext = checkedTotal(
                        totalPlaintext, length,
                        EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES);
                byte[] envelope = null;
                try {
                    envelope = recoveryCodec.protect(
                            plaintext, length, context(request, index, terminal),
                            EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
                    if (envelope.length > EncryptedSnapshotVerifier.MAXIMUM_CHUNK_ENVELOPE_BYTES) {
                        throw SnapshotManifest.invalid();
                    }
                    requireRecoveryKey(envelope, request.recoveryKeyReference());
                    totalEnvelope = checkedTotal(
                            totalEnvelope, envelope.length,
                            EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES);
                    chunks.putComplete(request.snapshotId(), index, envelope);
                    inventory.add(new SnapshotManifest.Chunk(
                            index, terminal, length, envelope.length, sha256(envelope)));
                } finally {
                    clear(envelope);
                }
                index++;
                if (terminal) {
                    return new SnapshotManifest(
                            request.subject(), request.snapshotId(), request.recoveryKeyReference(),
                            totalPlaintext, totalEnvelope, inventory);
                }
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            } finally {
                clear(plaintext);
            }
        }
    }

    private void requireCompleteInventory(SnapshotManifest manifest) {
        List<SnapshotChunkStore.StoredChunk> stored = chunks.inventory(manifest.snapshotId());
        if (stored.size() != manifest.chunks().size()) {
            throw SnapshotManifest.invalid();
        }
        long total = 0;
        for (int index = 0; index < stored.size(); index++) {
            SnapshotChunkStore.StoredChunk actual = stored.get(index);
            SnapshotManifest.Chunk expected = manifest.chunks().get(index);
            if (actual.index() != expected.index()
                    || actual.envelopeSize() != expected.envelopeSize()) {
                throw SnapshotManifest.invalid();
            }
            total = checkedTotal(total, actual.envelopeSize(),
                    EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_ENVELOPE_BYTES);
        }
        if (total != manifest.totalEnvelopeBytes()) {
            throw SnapshotManifest.invalid();
        }
    }

    private void authenticateCompleteInventory(SnapshotManifest manifest) {
        for (SnapshotManifest.Chunk chunk : manifest.chunks()) {
            byte[] envelope = readEnvelope(manifest.snapshotId(), chunk);
            byte[] plaintext = null;
            try {
                if (!sha256(envelope).equals(chunk.sha256Digest())) {
                    throw SnapshotManifest.invalid();
                }
                requireRecoveryKey(envelope, manifest.recoveryKeyReference());
                plaintext = recoveryCodec.unprotect(
                        envelope, context(manifest, chunk.index(), chunk.terminal()),
                        EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
                if (plaintext.length != chunk.plaintextSize()) {
                    throw SnapshotManifest.invalid();
                }
            } finally {
                clear(plaintext);
                clear(envelope);
            }
        }
    }

    private void streamPlaintext(SnapshotManifest manifest, OutputStream target) {
        for (SnapshotManifest.Chunk chunk : manifest.chunks()) {
            byte[] envelope = readEnvelope(manifest.snapshotId(), chunk);
            byte[] plaintext = null;
            try {
                // Recheck digest in the streaming pass to close a mutation race after preflight.
                if (!sha256(envelope).equals(chunk.sha256Digest())) {
                    throw SnapshotManifest.invalid();
                }
                requireRecoveryKey(envelope, manifest.recoveryKeyReference());
                plaintext = recoveryCodec.unprotect(
                        envelope, context(manifest, chunk.index(), chunk.terminal()),
                        EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
                if (plaintext.length != chunk.plaintextSize()) {
                    throw SnapshotManifest.invalid();
                }
                target.write(plaintext);
            } catch (IOException exception) {
                throw SnapshotManifest.invalid();
            } finally {
                clear(plaintext);
                clear(envelope);
            }
        }
        try {
            target.flush();
        } catch (IOException exception) {
            throw SnapshotManifest.invalid();
        }
    }

    private byte[] readEnvelope(String snapshotId, SnapshotManifest.Chunk chunk) {
        try (InputStream input = chunks.open(snapshotId, chunk.index(), chunk.envelopeSize())) {
            int expected = Math.toIntExact(chunk.envelopeSize());
            byte[] output = new byte[expected];
            int offset = 0;
            int remaining = expected;
            while (remaining > 0) {
                int read = input.read(output, offset, remaining);
                if (read < 0) {
                    throw SnapshotManifest.invalid();
                }
                if (read == 0) {
                    continue;
                }
                offset += read;
                remaining -= read;
            }
            if (input.read() != -1) {
                throw SnapshotManifest.invalid();
            }
            return output;
        } catch (IOException exception) {
            throw SnapshotManifest.invalid();
        }
    }

    private static int fill(InputStream input, byte[] buffer, int offset) throws IOException {
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                return offset;
            }
            if (read == 0) {
                int one = input.read();
                if (one < 0) {
                    return offset;
                }
                buffer[offset++] = (byte) one;
            } else {
                offset += read;
            }
        }
        return offset;
    }

    private static ProtectionContext context(
            CreateRequest request, int index, boolean terminal) {
        return context(request.subject(), request.snapshotId(), index, terminal);
    }

    private static ProtectionContext context(
            SnapshotManifest manifest, int index, boolean terminal) {
        return context(manifest.subject(), manifest.snapshotId(), index, terminal);
    }

    private static ProtectionContext context(
            SnapshotManifest.Subject subject, String snapshotId, int index, boolean terminal) {
        return new ProtectionContext(
                ProtectionContext.Purpose.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK,
                subject.migrationSetId(), snapshotId,
                "chunk:" + index + ":final:" + terminal,
                "global",
                subject.databaseInstanceFingerprint() + ":" + subject.schema()
                        + ":" + subject.flywaySetDigest());
    }

    private static long checkedTotal(long current, long addition, long maximum) {
        try {
            long result = Math.addExact(current, addition);
            if (result > maximum) {
                throw SnapshotManifest.invalid();
            }
            return result;
        } catch (ArithmeticException exception) {
            throw SnapshotManifest.invalid();
        }
    }

    private static void requireRecoveryKey(byte[] envelope, String recoveryKeyReference) {
        if (!new EnvelopeCodec().decode(
                envelope, EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK)
                .keyReference().equals(recoveryKeyReference)) {
            throw SnapshotManifest.invalid();
        }
    }

    private void cleanupFailedSnapshot(String snapshotId, RuntimeException primary) {
        try {
            chunks.deleteSnapshot(snapshotId);
        } catch (RuntimeException cleanup) {
            primary.addSuppressed(cleanup);
        }
    }

    private void cleanupQuietly(String snapshotId) {
        try {
            chunks.deleteSnapshot(snapshotId);
        } catch (RuntimeException ignored) {
            // A selected create failure remains primary; external cleanup verifies residue.
        }
    }

    private static String sha256(byte[] input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 21 must provide SHA-256", exception);
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    public record CreateRequest(
            Database source,
            SnapshotManifest.Subject subject,
            String snapshotId,
            String recoveryKeyReference) {
        public CreateRequest {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(subject, "subject");
            if (snapshotId == null || recoveryKeyReference == null) {
                throw SnapshotManifest.invalid();
            }
        }
    }

    public record RestoreResult(String snapshotId, String targetSchema, boolean alreadyComplete) {
        public RestoreResult {
            if (snapshotId == null || targetSchema == null) {
                throw SnapshotManifest.invalid();
            }
        }
    }

    @FunctionalInterface
    public interface FreshSchemaGate {
        /** Must reject a missing, nonempty, source-equal or otherwise unowned target schema. */
        void requireFresh(Database source, Database target);
    }
}
