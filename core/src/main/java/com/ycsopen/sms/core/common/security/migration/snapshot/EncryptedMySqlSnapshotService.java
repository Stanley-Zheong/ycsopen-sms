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
        boolean reserved = false;
        boolean complete = false;
        try {
            freshSchemaGate.requireSnapshotSource(request.source());
            chunks.beginSnapshot(request.snapshotId());
            reserved = true;
            SnapshotManifest manifest;
            try (MySqlSnapshotProcess.DumpSession dump = processes.startDump(request.source())) {
                manifest = protectDump(dump.stdout(), request);
                dump.awaitSuccess();
            }
            chunks.putManifest(request.snapshotId(), manifest.canonicalBytes());
            complete = true;
            return manifest;
        } catch (RuntimeException exception) {
            if (reserved) {
                cleanupFailedSnapshot(request.snapshotId(), exception);
                reserved = false;
            }
            throw exception;
        } finally {
            if (reserved && !complete) {
                // Covers errors whose primary failure is not represented by the catch above.
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
        return restore(admittedManifestBytes, new SnapshotAdmission(
                admission.globalSequence(), admission.signerKeyVersion(),
                admission.snapshotDigest(), admission.snapshotId(),
                admission.recoveryKeyReference()), source, freshTarget);
    }

    /**
     * Restores from the narrow, database-owned portion of pair admission that binds a snapshot.
     * Writer artifacts remain a preflight concern and are deliberately not fabricated here.
     */
    public RestoreResult restore(
            byte[] admittedManifestBytes,
            SnapshotAdmission admission,
            Database source,
            Database freshTarget) {
        Objects.requireNonNull(admission, "admission");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(freshTarget, "freshTarget");
        SnapshotManifest manifest = SnapshotManifest.parse(admittedManifestBytes);
        String manifestDigest = manifest.digest();
        requireAdmittedManifest(manifest, admission);
        if (!manifest.subject().schema().equals(source.schema())
                || source.schema().equals(freshTarget.schema())) {
            throw SnapshotManifest.invalid();
        }
        PlaintextProof expected = proveCompleteRetainedSnapshot(
                manifest, admittedManifestBytes);
        if (chunks.recoveryComplete(
                manifest.snapshotId(), freshTarget.schema(), manifestDigest)) {
            requireTargetMatchesSnapshot(freshTarget, expected);
            return new RestoreResult(manifest.snapshotId(), freshTarget.schema(), true);
        }

        freshSchemaGate.requireFresh(source, freshTarget);

        try (MySqlSnapshotProcess.RestoreSession restore = processes.startRestore(freshTarget)) {
            streamPlaintext(manifest, restore.stdin());
            restore.awaitSuccess();
        }
        PlaintextProof afterRestore = proveCompleteRetainedSnapshot(
                manifest, admittedManifestBytes);
        if (!expected.equals(afterRestore)) {
            throw SnapshotManifest.invalid();
        }
        requireTargetMatchesSnapshot(freshTarget, afterRestore);
        chunks.markRecoveryComplete(manifest.snapshotId(), freshTarget.schema(), manifestDigest);
        if (!chunks.recoveryComplete(
                manifest.snapshotId(), freshTarget.schema(), manifestDigest)) {
            throw SnapshotManifest.invalid();
        }
        return new RestoreResult(manifest.snapshotId(), freshTarget.schema(), false);
    }

    /** Proves the exact retained inventory and authenticates every chunk before pair CAS. */
    public void requireCompleteRetainedSnapshot(byte[] canonicalManifestBytes) {
        SnapshotManifest manifest = SnapshotManifest.parse(canonicalManifestBytes);
        proveCompleteRetainedSnapshot(manifest, canonicalManifestBytes);
    }

    private PlaintextProof proveCompleteRetainedSnapshot(
            SnapshotManifest manifest, byte[] canonicalManifestBytes) {
        byte[] retained = chunks.retainedManifest(manifest.snapshotId()).canonicalManifest();
        try {
            if (!MessageDigest.isEqual(canonicalManifestBytes, retained)) {
                throw SnapshotManifest.invalid();
            }
        } finally {
            clear(retained);
        }
        requireCompleteInventory(manifest);
        return authenticateCompleteInventory(manifest);
    }

    /** Deletes by immutable snapshot identity plus its exact canonical manifest digest. */
    public String delete(String snapshotId, String expectedManifestDigest) {
        byte[] retained = chunks.retainedManifest(snapshotId).canonicalManifest();
        try {
            SnapshotManifest manifest = SnapshotManifest.parse(retained);
            if (!manifest.digest().equals(expectedManifestDigest)) {
                throw SnapshotManifest.invalid();
            }
            chunks.deleteSnapshot(snapshotId);
            return snapshotId;
        } finally {
            clear(retained);
        }
    }

    private static void requireAdmittedManifest(
            SnapshotManifest manifest, SnapshotAdmission admission) {
        if (!manifest.digest().equals(admission.snapshotDigest())
                || !manifest.snapshotId().equals(admission.snapshotId())
                || !manifest.recoveryKeyReference().equals(admission.recoveryKeyReference())
                || manifest.subject().globalSequence() != admission.globalSequence()
                || !manifest.subject().signerKeyVersion().equals(admission.signerKeyVersion())) {
            throw SnapshotManifest.invalid();
        }
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

    private PlaintextProof authenticateCompleteInventory(SnapshotManifest manifest) {
        MessageDigest plaintextDigest = sha256Digest();
        long plaintextBytes = 0;
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
                plaintextBytes = checkedTotal(
                        plaintextBytes, plaintext.length,
                        EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES);
                plaintextDigest.update(plaintext);
            } finally {
                clear(plaintext);
                clear(envelope);
            }
        }
        if (plaintextBytes != manifest.totalPlaintextBytes()) {
            throw SnapshotManifest.invalid();
        }
        return new PlaintextProof(
                plaintextBytes, HexFormat.of().formatHex(plaintextDigest.digest()));
    }

    private void requireTargetMatchesSnapshot(Database target, PlaintextProof expected) {
        freshSchemaGate.requireRestored(target);
        MessageDigest targetDigest = sha256Digest();
        long targetBytes = 0;
        byte[] buffer = new byte[64 * 1_024];
        try (MySqlSnapshotProcess.DumpSession dump = processes.startDump(target)) {
            InputStream input = dump.stdout();
            while (true) {
                int read = input.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                targetBytes = checkedTotal(
                        targetBytes, read,
                        EncryptedSnapshotVerifier.MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES);
                if (targetBytes > expected.bytes()) {
                    throw SnapshotManifest.invalid();
                }
                targetDigest.update(buffer, 0, read);
            }
            dump.awaitSuccess();
        } catch (IOException exception) {
            throw SnapshotManifest.invalid();
        } finally {
            clear(buffer);
        }
        String digest = HexFormat.of().formatHex(targetDigest.digest());
        if (targetBytes != expected.bytes() || !digest.equals(expected.sha256())) {
            throw SnapshotManifest.invalid();
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
        return HexFormat.of().formatHex(sha256Digest().digest(input));
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
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

    /** Immutable snapshot fields reconstructed from the current database pair admission. */
    public record SnapshotAdmission(
            long globalSequence,
            String signerKeyVersion,
            String snapshotDigest,
            String snapshotId,
            String recoveryKeyReference) {
        public SnapshotAdmission {
            if (globalSequence < 0) {
                throw SnapshotManifest.invalid();
            }
            Objects.requireNonNull(signerKeyVersion, "signerKeyVersion");
            Objects.requireNonNull(snapshotDigest, "snapshotDigest");
            Objects.requireNonNull(snapshotId, "snapshotId");
            Objects.requireNonNull(recoveryKeyReference, "recoveryKeyReference");
        }
    }

    private record PlaintextProof(long bytes, String sha256) {
        private PlaintextProof {
            if (bytes < 1 || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
                throw SnapshotManifest.invalid();
            }
        }
    }

    public interface FreshSchemaGate {
        /** Must reject a source whose catalog cannot produce deterministic snapshot SQL. */
        default void requireSnapshotSource(Database source) {
        }

        /** Must reject a missing, nonempty, source-equal or otherwise unowned target schema. */
        void requireFresh(Database source, Database target);

        /** Must reject a restored target whose catalog is outside the deterministic profile. */
        default void requireRestored(Database target) {
        }
    }
}
