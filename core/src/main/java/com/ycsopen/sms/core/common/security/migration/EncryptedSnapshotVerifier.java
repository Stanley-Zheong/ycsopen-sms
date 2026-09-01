package com.ycsopen.sms.core.common.security.migration;

/**
 * Snapshot-side role of paired migration admission.
 *
 * <p>There is intentionally no snapshot-only verification result or acceptance method. Callers
 * can enter the boundary only through {@link WriterFencePort.PairedBoundary}.</p>
 */
public interface EncryptedSnapshotVerifier {

    long MAXIMUM_CHUNK_PLAINTEXT_BYTES = 10_485_760L;
    long MAXIMUM_CHUNK_ENVELOPE_BYTES = 10_485_905L;
    long MAXIMUM_SNAPSHOT_PLAINTEXT_BYTES = 1_099_511_627_776L;
    long MAXIMUM_SNAPSHOT_ENVELOPE_BYTES = 1_099_526_832_186L;
    int MAXIMUM_CHUNK_COUNT = 104_858;
    int MAXIMUM_MANIFEST_BYTES = 33_554_432;
}
