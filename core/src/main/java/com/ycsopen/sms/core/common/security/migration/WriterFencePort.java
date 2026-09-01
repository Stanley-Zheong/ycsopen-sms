package com.ycsopen.sms.core.common.security.migration;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * Writer-side role of the inseparable migration admission boundary.
 *
 * <p>This interface deliberately exposes no writer-only acceptance operation. The production
 * implementation supplied by Plan 27 implements {@link PairedBoundary}, so writer and snapshot
 * evidence can be admitted only by the same call and atomic persistence decision.</p>
 */
public interface WriterFencePort {

    interface PairedBoundary extends WriterFencePort, EncryptedSnapshotVerifier {
        /**
         * Returns only after both roles, their shared subject, the complete snapshot inventory and
         * the atomic pair decision have succeeded. A rejection must throw after rolling back the
         * pair decision, so no role-only or half-pair result can exist.
         */
        PairedAdmission verifyAndAdmit(PairedAdmissionRequest request);
    }

    record PairedAdmissionRequest(
            Path writerManifest,
            Path writerSignature,
            Path snapshotManifest,
            Path snapshotSignature,
            DeploymentSubject expectedSubject) {

        public PairedAdmissionRequest {
            Objects.requireNonNull(writerManifest, "writerManifest");
            Objects.requireNonNull(writerSignature, "writerSignature");
            Objects.requireNonNull(snapshotManifest, "snapshotManifest");
            Objects.requireNonNull(snapshotSignature, "snapshotSignature");
            Objects.requireNonNull(expectedSubject, "expectedSubject");
        }
    }

    record DeploymentSubject(
            String migrationSetId,
            String environment,
            String databaseInstanceFingerprint,
            String schema,
            String flywaySetDigest) {

        public DeploymentSubject {
            requireText(migrationSetId, "migrationSetId");
            requireText(environment, "environment");
            requireHex(databaseInstanceFingerprint, "databaseInstanceFingerprint");
            requireText(schema, "schema");
            requireHex(flywaySetDigest, "flywaySetDigest");
        }
    }

    /** Combined result only; neither manifest role has an independently accepted state. */
    record PairedAdmission(
            long globalSequence,
            String signerKeyVersion,
            String writerDigest,
            String snapshotDigest,
            String pairDigest,
            Set<String> compatibleWriterArtifacts,
            String snapshotId,
            String recoveryKeyReference) {

        public PairedAdmission {
            if (globalSequence < 0) {
                throw new IllegalArgumentException("globalSequence must be unsigned");
            }
            requireText(signerKeyVersion, "signerKeyVersion");
            requireHex(writerDigest, "writerDigest");
            requireHex(snapshotDigest, "snapshotDigest");
            requireHex(pairDigest, "pairDigest");
            compatibleWriterArtifacts = Set.copyOf(compatibleWriterArtifacts);
            if (compatibleWriterArtifacts.isEmpty()) {
                throw new IllegalArgumentException("writer set must not be empty");
            }
            requireText(snapshotId, "snapshotId");
            requireText(recoveryKeyReference, "recoveryKeyReference");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireHex(String value, String field) {
        requireText(value, field);
        if (value.length() != 64) {
            throw new IllegalArgumentException(field + " must be a 32-byte hexadecimal digest");
        }
        try {
            HexFormat.of().parseHex(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be hexadecimal");
        }
    }
}
