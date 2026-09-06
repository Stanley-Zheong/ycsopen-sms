package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.BlindIndexRule;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.Kind;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmissionRequest;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedBoundary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only, fail-closed admission gate that runs before any migration mutation collaborator.
 * Pair admission is invoked only after manifest, target, capacity, schema, checkpoint and key
 * checks have all passed.
 */
public final class MigrationPreflight {

    private static final String THIRD_PARTY_DIGEST = "third_party_risk_check_logs.mobile_hash";
    private static final Set<String> REQUIRED_KEY_PURPOSES = Set.of(
            "FIELD_ENCRYPTION_KEK", "MOBILE_BLIND_INDEX", "SNAPSHOT_RECOVERY");

    private final ProtectedDataManifest manifest;
    private final PairedBoundary pairedBoundary;
    private final MutationObserver mutationObserver;

    public MigrationPreflight(
            ProtectedDataManifest manifest,
            PairedBoundary pairedBoundary,
            MutationObserver mutationObserver) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.pairedBoundary = Objects.requireNonNull(pairedBoundary, "pairedBoundary");
        this.mutationObserver = Objects.requireNonNull(mutationObserver, "mutationObserver");
    }

    public Admission preflight(PreflightRequest request) {
        Objects.requireNonNull(request, "request");
        MutationCounters before = mutationObserver.current();
        try {
            validateManifest();
            validateSchemaAndTargets(request);
            validateKeys(request.keyHealth());
            PairedAdmission pair = pairedBoundary.verifyAndAdmit(request.pairedAdmissionRequest());
            if (pair == null) {
                throw rejected(FailureCode.PAIRED_BOUNDARY_REJECTED);
            }
            return new Admission(manifest.digest(), pair.pairDigest(), pair.globalSequence());
        } catch (RuntimeException exception) {
            MutationCounters after = mutationObserver.current();
            if (!before.equals(after)) {
                throw rejected(FailureCode.MUTATION_ON_REJECTED_PREFLIGHT);
            }
            if (exception instanceof PreflightException preflightException) {
                throw preflightException;
            }
            throw rejected(FailureCode.PAIRED_BOUNDARY_REJECTED);
        }
    }

    private void validateManifest() {
        if (!manifest.resolvedForMigration()) {
            throw rejected(FailureCode.MANIFEST_UNRESOLVED);
        }
        if (!manifest.noIndexTargetIds().equals(Set.of(
                "bulk_sending_items.mobile_encrypted", "uplink_records.mobile_encrypted"))) {
            throw rejected(FailureCode.NO_INDEX_TARGET_SET_INVALID);
        }
        ProtectedDataTarget thirdParty = manifest.requireTarget(THIRD_PARTY_DIGEST);
        if (thirdParty.kind() != Kind.LEGACY_DIGEST
                || thirdParty.migrationState() != ProtectedDataTarget.MigrationState.MIGRATABLE_SCHEMA_ONLY) {
            throw rejected(FailureCode.THIRD_PARTY_TARGET_INVALID);
        }
    }

    private void validateSchemaAndTargets(PreflightRequest request) {
        if (request.schemaDrift()) {
            throw rejected(FailureCode.SCHEMA_DRIFT);
        }
        if (!request.targetInspections().keySet().equals(manifest.targets().keySet())) {
            throw rejected(FailureCode.TARGET_SET_INCOMPLETE);
        }
        for (ProtectedDataTarget target : manifest.targets().values()) {
            TargetInspection inspection = request.targetInspections().get(target.id());
            validateTarget(target, inspection);
        }
    }

    private void validateTarget(ProtectedDataTarget target, TargetInspection inspection) {
        if (inspection == null || !inspection.targetId().equals(target.id())) {
            throw rejected(FailureCode.TARGET_SET_INCOMPLETE);
        }
        if (!inspection.schemaPresent() || inspection.runtimeStorageCapacityBytes() < target.storageCapacityBytes()) {
            throw rejected(FailureCode.SCHEMA_DRIFT);
        }
        if (inspection.maximumObservedStoredBytes() < 0
                || inspection.maximumObservedStoredBytes() > target.maximumStoredValueBytes()) {
            throw rejected(FailureCode.CAPACITY_EXCEEDED);
        }
        if (!inspection.contextAvailable() || !inspection.primaryKeyAvailable()) {
            throw rejected(FailureCode.ROW_CONTEXT_INCOMPLETE);
        }
        if (!inspection.deployedWriterKnown()) {
            throw rejected(FailureCode.DEPLOYED_WRITER_UNKNOWN);
        }
        validateCheckpoint(target, inspection);

        if (target.requiresBlindIndex()) {
            if (!inspection.requiredSchemaIndexPresent()
                    || !inspection.rowBindingComplete()
                    || inspection.orphanBindingCount() != 0) {
                throw rejected(FailureCode.BLIND_INDEX_BINDING_INVALID);
            }
        }
        if (target.blindIndexRule() == BlindIndexRule.EXCLUDED_NO_EQUALITY_CONTRACT
                && (inspection.equalityIndexRowsPresent() || inspection.requiredSchemaIndexPresent())) {
            throw rejected(FailureCode.NO_INDEX_TARGET_SET_INVALID);
        }
        if (THIRD_PARTY_DIGEST.equals(target.id())
                && (!inspection.requiredSchemaIndexPresent() || inspection.currentJavaReaderOrWriterPresent())) {
            throw rejected(FailureCode.THIRD_PARTY_TARGET_INVALID);
        }
    }

    private static void validateCheckpoint(ProtectedDataTarget target, TargetInspection inspection) {
        CheckpointState previous = inspection.previousCheckpointState();
        CheckpointState current = inspection.checkpointState();
        if (previous == null) {
            if (current != CheckpointState.DISCOVERED) {
                throw rejected(FailureCode.CHECKPOINT_TRANSITION_INVALID);
            }
        } else {
            int delta = current.ordinal() - previous.ordinal();
            if (delta < 0 || delta > 1) {
                throw rejected(FailureCode.CHECKPOINT_TRANSITION_INVALID);
            }
        }
        if (current == CheckpointState.COMPLETE && inspection.legacyFallbackAllowed()) {
            throw rejected(FailureCode.LEGACY_FALLBACK_AFTER_COMPLETE);
        }
        if (target.blindIndexRule() == BlindIndexRule.EXCLUDED_NO_EQUALITY_CONTRACT
                && inspection.legacyFallbackAllowed()) {
            throw rejected(FailureCode.NO_INDEX_TARGET_SET_INVALID);
        }
    }

    private static void validateKeys(KeyHealth keyHealth) {
        if (!keyHealth.providerAvailable()) {
            throw rejected(FailureCode.KEY_PROVIDER_UNAVAILABLE);
        }
        if (!keyHealth.versionsByPurpose().keySet().containsAll(REQUIRED_KEY_PURPOSES)) {
            throw rejected(FailureCode.ACTIVE_KEY_MISSING);
        }
        for (String purpose : REQUIRED_KEY_PURPOSES) {
            List<KeyVersion> versions = keyHealth.versionsByPurpose().get(purpose);
            if (versions == null || versions.isEmpty()) {
                throw rejected(FailureCode.ACTIVE_KEY_MISSING);
            }
            Set<Long> unique = new HashSet<>();
            long active = versions.stream().peek(version -> {
                if (!unique.add(version.version())) {
                    throw rejected(FailureCode.KEY_VERSION_SET_INVALID);
                }
            }).filter(version -> version.state() == KeyState.ACTIVE).count();
            if (active != 1) {
                throw rejected(FailureCode.ACTIVE_KEY_MISSING);
            }
        }
    }

    private static PreflightException rejected(FailureCode code) {
        return new PreflightException(code);
    }

    public record PreflightRequest(
            Map<String, TargetInspection> targetInspections,
            KeyHealth keyHealth,
            boolean schemaDrift,
            PairedAdmissionRequest pairedAdmissionRequest) {

        public PreflightRequest {
            targetInspections = Map.copyOf(targetInspections);
            Objects.requireNonNull(keyHealth, "keyHealth");
            Objects.requireNonNull(pairedAdmissionRequest, "pairedAdmissionRequest");
        }
    }

    public record TargetInspection(
            String targetId,
            boolean schemaPresent,
            long runtimeStorageCapacityBytes,
            long maximumObservedStoredBytes,
            boolean contextAvailable,
            boolean primaryKeyAvailable,
            boolean requiredSchemaIndexPresent,
            boolean equalityIndexRowsPresent,
            boolean rowBindingComplete,
            long orphanBindingCount,
            CheckpointState previousCheckpointState,
            CheckpointState checkpointState,
            boolean legacyFallbackAllowed,
            boolean currentJavaReaderOrWriterPresent,
            boolean deployedWriterKnown) {

        public TargetInspection {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(checkpointState, "checkpointState");
            if (runtimeStorageCapacityBytes < 0 || maximumObservedStoredBytes < 0 || orphanBindingCount < 0) {
                throw new IllegalArgumentException("inspection counts must not be negative");
            }
        }
    }

    public record KeyHealth(boolean providerAvailable, Map<String, List<KeyVersion>> versionsByPurpose) {
        public KeyHealth {
            Map<String, List<KeyVersion>> copied = new java.util.HashMap<>();
            versionsByPurpose.forEach((purpose, versions) -> copied.put(purpose, List.copyOf(versions)));
            versionsByPurpose = Map.copyOf(copied);
        }
    }

    public record KeyVersion(long version, KeyState state) {
        public KeyVersion {
            if (version < 0) {
                throw new IllegalArgumentException("key version must be unsigned");
            }
            Objects.requireNonNull(state, "state");
        }
    }

    public enum KeyState {
        ACTIVE,
        RETIRING,
        DECRYPT_ONLY,
        RETIRED,
        COMPROMISED
    }

    public enum CheckpointState {
        DISCOVERED,
        BACKFILLED,
        VERIFIED,
        CUTOVER,
        SCRUBBED,
        COMPLETE
    }

    public record MutationCounters(
            long pairAdmissionWrites,
            long leaseWrites,
            long checkpointWrites,
            long eventWrites,
            long businessRowWrites) {

        public MutationCounters {
            List<Long> counts = new ArrayList<>(List.of(
                    pairAdmissionWrites, leaseWrites, checkpointWrites, eventWrites, businessRowWrites));
            if (counts.stream().anyMatch(count -> count < 0)) {
                throw new IllegalArgumentException("mutation counts must not be negative");
            }
        }
    }

    @FunctionalInterface
    public interface MutationObserver {
        MutationCounters current();
    }

    public record Admission(String manifestDigest, String pairDigest, long globalSequence) {
        public Admission {
            Objects.requireNonNull(manifestDigest, "manifestDigest");
            Objects.requireNonNull(pairDigest, "pairDigest");
            if (globalSequence < 0) {
                throw new IllegalArgumentException("globalSequence must be unsigned");
            }
        }
    }

    public enum FailureCode {
        MANIFEST_UNRESOLVED,
        TARGET_SET_INCOMPLETE,
        SCHEMA_DRIFT,
        CAPACITY_EXCEEDED,
        ROW_CONTEXT_INCOMPLETE,
        CHECKPOINT_TRANSITION_INVALID,
        LEGACY_FALLBACK_AFTER_COMPLETE,
        BLIND_INDEX_BINDING_INVALID,
        NO_INDEX_TARGET_SET_INVALID,
        THIRD_PARTY_TARGET_INVALID,
        DEPLOYED_WRITER_UNKNOWN,
        KEY_PROVIDER_UNAVAILABLE,
        ACTIVE_KEY_MISSING,
        KEY_VERSION_SET_INVALID,
        PAIRED_BOUNDARY_REJECTED,
        MUTATION_ON_REJECTED_PREFLIGHT
    }

    public static final class PreflightException extends IllegalStateException {
        private final FailureCode code;

        private PreflightException(FailureCode code) {
            super("migration preflight rejected");
            this.code = code;
        }

        public FailureCode code() {
            return code;
        }
    }
}
