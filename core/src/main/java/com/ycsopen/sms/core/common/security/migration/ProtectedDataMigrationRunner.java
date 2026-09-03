package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.migration.LegacyValueClassifier.Classification;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.CheckpointState;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.BlindIndexEntry;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.Checkpoint;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.LegacyRow;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.RunState;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.StoredValueKind;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget.Kind;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bounded, explicit and restart-safe owner of the Phase-03 in-place migration.
 *
 * <p>This type is deliberately not a Spring component and has no scheduled or startup hook. Every
 * mutation requires a previously accepted pair digest and an explicit caller operation. Row
 * transition, blind-index metadata, sanitized outcome and checkpoint are performed through one
 * {@link MigrationStateRepository#transaction} callback.</p>
 */
public final class ProtectedDataMigrationRunner {

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(2);
    public static final String SANITIZED_FAILURE = "protected-data migration rejected";

    private static final String INDEX_FIELD = "mobile";
    private static final Pattern RUN_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Map<String, String> TARGET_TYPES = Map.of(
            "mobile_portability.mobile_hash", "MOBILE_PORTABILITY",
            "blacklist_entries.mobile_hash", "BLACKLIST_ENTRY",
            "third_party_risk_check_logs.mobile_hash", "THIRD_PARTY_RISK_CHECK_LOG",
            "message_tasks.mobile_hash", "MESSAGE_TASK",
            "unsubscribe_records.mobile_hash", "UNSUBSCRIBE_RECORD",
            "bulk_sending_items.mobile_encrypted", "BULK_SENDING_ITEM_MOBILE",
            "uplink_records.mobile_encrypted", "UPLINK_RECORD_MOBILE");
    private static final Set<String> INDEX_TARGETS = Set.of(
            "mobile_portability.mobile_hash",
            "blacklist_entries.mobile_hash",
            "third_party_risk_check_logs.mobile_hash",
            "message_tasks.mobile_hash",
            "unsubscribe_records.mobile_hash");
    private static final Set<String> NO_INDEX_TARGETS = Set.of(
            "bulk_sending_items.mobile_encrypted", "uplink_records.mobile_encrypted");

    private final ProtectedDataManifest manifest;
    private final MigrationStateRepository repository;
    private final LegacyValueClassifier classifier;
    private final ProtectedFieldCodec fieldCodec;
    private final IntegrityFingerprintPort fingerprintPort;
    private final LegacyBlindIndexPort blindIndexPort;
    private final Clock clock;

    public ProtectedDataMigrationRunner(
            ProtectedDataManifest manifest,
            MigrationStateRepository repository,
            LegacyValueClassifier classifier,
            ProtectedFieldCodec fieldCodec,
            IntegrityFingerprintPort fingerprintPort,
            LegacyBlindIndexPort blindIndexPort,
            Clock clock) {
        this.manifest = Objects.requireNonNull(manifest, "manifest");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.fieldCodec = Objects.requireNonNull(fieldCodec, "fieldCodec");
        this.fingerprintPort = Objects.requireNonNull(fingerprintPort, "fingerprintPort");
        this.blindIndexPort = Objects.requireNonNull(blindIndexPort, "blindIndexPort");
        this.clock = Objects.requireNonNull(clock, "clock");
        validateReviewedMigrationSet();
    }

    /** Processes at most one stable-PK batch and atomically advances its checkpoint. */
    public BatchResult migrateBatch(MigrationRequest request) {
        Objects.requireNonNull(request, "request");
        ProtectedDataTarget target = reviewedTarget(request.targetId());
        String targetType = targetType(target);
        try {
            return repository.transaction(transaction -> {
                transaction.requireAcceptedPair(request.acceptedPairDigest());
                transaction.ensureRun(request.runId(), request.acceptedPairDigest(), manifestHexDigest());
                Instant leaseExpiry = clock.instant().plus(request.leaseDuration());
                transaction.claimLease(
                        request.runId(), targetType, request.leaseOwnerDigest(), leaseExpiry);
                Checkpoint current = transaction.checkpoint(request.runId(), targetType);
                if (current.state() != CheckpointState.DISCOVERED) {
                    throw failure(FailureCode.CHECKPOINT_STATE_INVALID);
                }

                long after = current.lastRowId() == null ? 0 : current.lastRowId();
                List<LegacyRow> rows = transaction.readBatch(target, after, request.batchSize());
                long scanned = 0;
                long migrated = 0;
                long verified = 0;
                long skipped = 0;
                LegacyRow last = null;
                for (LegacyRow row : rows) {
                    last = row;
                    scanned++;
                    RowResult result = migrateRow(
                            transaction, request.runId(), target, targetType, row);
                    migrated += result.migrated();
                    verified += result.verified();
                    skipped += result.skipped();
                }

                Checkpoint next = new Checkpoint(
                        current.state(),
                        last == null || last.checkpointCursor() == null
                                ? current.lastRowId() : last.checkpointCursor(),
                        last == null || last.checkpointCursor() == null
                                ? current.lastOriginalDigest() : last.originalCellDigest(),
                        Math.addExact(current.scanned(), scanned),
                        Math.addExact(current.migrated(), migrated),
                        Math.addExact(current.verified(), verified),
                        current.quarantined(),
                        current.optimisticVersion());
                transaction.saveCheckpoint(request.runId(), targetType, next);
                return new BatchResult(
                        request.runId(), target.id(), rows.size(), migrated, verified, skipped,
                        next.lastRowId(), rows.size() < request.batchSize());
            });
        } catch (MigrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(FailureCode.TRANSACTION_REJECTED);
        }
    }

    /** Advances exactly one reviewed target-state edge; skipped or backward edges fail closed. */
    public TransitionResult advance(TransitionRequest request) {
        Objects.requireNonNull(request, "request");
        ProtectedDataTarget target = reviewedTarget(request.targetId());
        String targetType = targetType(target);
        try {
            return repository.transaction(transaction -> {
                transaction.requireAcceptedPair(request.acceptedPairDigest());
                transaction.ensureRun(request.runId(), request.acceptedPairDigest(), manifestHexDigest());
                transaction.claimLease(request.runId(), targetType, request.leaseOwnerDigest(),
                        clock.instant().plus(request.leaseDuration()));
                Checkpoint current = transaction.checkpoint(request.runId(), targetType);
                requireNextState(current.state(), request.nextState());

                switch (request.nextState()) {
                    case BACKFILLED, VERIFIED -> {
                        if (!transaction.integrityAndBindingComplete(target, targetType)) {
                            throw failure(FailureCode.INTEGRITY_OR_BINDING_INVALID);
                        }
                    }
                    case CUTOVER -> {
                        if (!transaction.deployedWritersCompatible(targetType)) {
                            throw failure(FailureCode.WRITER_FENCE_REJECTED);
                        }
                    }
                    case SCRUBBED -> {
                        transaction.scrubLegacyDigests(target, targetType);
                        if (transaction.remainingLegacyRows(target) != 0) {
                            throw failure(FailureCode.LEGACY_VALUE_REMAINS);
                        }
                    }
                    case COMPLETE -> {
                        if (transaction.remainingLegacyRows(target) != 0) {
                            throw failure(FailureCode.LEGACY_VALUE_REMAINS);
                        }
                        transaction.setLegacyFallback(targetType, false);
                    }
                    case DISCOVERED -> throw failure(FailureCode.CHECKPOINT_STATE_INVALID);
                }
                transaction.setTargetState(request.runId(), targetType, request.nextState());
                return new TransitionResult(target.id(), current.state(), request.nextState());
            });
        } catch (MigrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(FailureCode.TRANSACTION_REJECTED);
        }
    }

    /** Explicit run lifecycle; there is no plaintext rollback operation. */
    public void changeRunState(RunControlRequest request, RunState expected, RunState next) {
        Objects.requireNonNull(request, "request");
        if (next != RunState.RUNNING && next != RunState.PAUSED && next != RunState.ABORTED) {
            throw new IllegalArgumentException("run transition is not exposed by this command");
        }
        try {
            repository.transaction(transaction -> {
                transaction.requireAcceptedPair(request.acceptedPairDigest());
                transaction.setRunState(
                        request.runId(), Objects.requireNonNull(expected, "expected"), next,
                        request.acceptedPairDigest());
                return Boolean.TRUE;
            });
        } catch (RuntimeException exception) {
            throw failure(FailureCode.TRANSACTION_REJECTED);
        }
    }

    public MigrationStateRepository.RunStatus status(String runId) {
        requireIdentifier(runId, "runId");
        try {
            return repository.transaction(transaction -> transaction.status(runId));
        } catch (RuntimeException exception) {
            throw failure(FailureCode.TRANSACTION_REJECTED);
        }
    }

    private RowResult migrateRow(
            MigrationStateRepository.Transaction transaction,
            String runId,
            ProtectedDataTarget target,
            String targetType,
            LegacyRow row) {
        byte[] stored = row.storedValue();
        byte[] recovered = null;
        byte[] protectedValue = null;
        byte[] beforeFingerprint = null;
        byte[] afterFingerprint = null;
        try {
            if (target.kind() == Kind.LEGACY_DIGEST
                    && row.storedValueKind() == StoredValueKind.CURRENT_MESSAGE_LOCATOR) {
                if (!"message_tasks.mobile_hash".equals(target.id())
                        || !transaction.currentMessageBindingMatches(row, INDEX_FIELD)) {
                    throw failure(FailureCode.INTEGRITY_OR_BINDING_INVALID);
                }
                transaction.recordOutcome(
                        runId, targetType, MigrationStateRepository.Outcome.SKIPPED,
                        rowLocatorDigest(targetType, row.bindingRowId()), 0);
                return RowResult.currentProtectedResult();
            }
            Classification classification = classifier.classify(target, stored);
            if (classification == Classification.CORRUPT
                    || classification == Classification.AMBIGUOUS) {
                throw failure(FailureCode.LEGACY_CLASSIFICATION_REJECTED);
            }
            if (classification == Classification.NULL_ALLOWED) {
                transaction.recordOutcome(
                        runId, targetType, MigrationStateRepository.Outcome.SKIPPED,
                        rowLocatorDigest(targetType, row.bindingRowId()), 0);
                return RowResult.skippedResult();
            }

            if (target.kind() == Kind.LEGACY_DIGEST) {
                if (classification != Classification.APPROVED_LEGACY) {
                    throw failure(FailureCode.LEGACY_CLASSIFICATION_REJECTED);
                }
                byte[] historicalDigest = stored.clone();
                List<BlindIndexEntry> indexes;
                try {
                    indexes = blindIndexPort.indexes(
                            historicalDigest, targetType, INDEX_FIELD, row.tenantScope());
                } finally {
                    clear(historicalDigest);
                }
                if (!transaction.upsertBlindIndexes(targetType, row, INDEX_FIELD, indexes)
                        || !transaction.blindIndexesMatch(targetType, row, INDEX_FIELD, indexes)) {
                    throw failure(FailureCode.INTEGRITY_OR_BINDING_INVALID);
                }
                transaction.recordOutcome(
                        runId, targetType, MigrationStateRepository.Outcome.SUCCEEDED,
                        rowLocatorDigest(targetType, row.bindingRowId()), 1);
                return RowResult.migratedResult();
            }

            ProtectionContext context = context(target, row);
            if (classification == Classification.VALID_ENVELOPE) {
                // Restart validates AEAD, key reference and row context but never re-encrypts.
                recovered = fieldCodec.unprotect(
                        stored, context, EnvelopeCodec.Target.DATABASE_FIELD);
                // Exercise the opaque HMAC integrity provider on restart. AEAD authenticates the
                // row context; no plaintext or fingerprint is persisted.
                afterFingerprint = fingerprint(recovered);
                transaction.recordOutcome(
                        runId, targetType, MigrationStateRepository.Outcome.SKIPPED,
                        rowLocatorDigest(targetType, row.bindingRowId()), 0);
                return RowResult.existingEnvelopeResult();
            }
            if (classification != Classification.APPROVED_LEGACY
                    || !NO_INDEX_TARGETS.contains(target.id())) {
                throw failure(FailureCode.LEGACY_CLASSIFICATION_REJECTED);
            }

            beforeFingerprint = fingerprint(stored);
            protectedValue = fieldCodec.protect(
                    stored, context, EnvelopeCodec.Target.DATABASE_FIELD);
            recovered = fieldCodec.unprotect(
                    protectedValue, context, EnvelopeCodec.Target.DATABASE_FIELD);
            afterFingerprint = fingerprint(recovered);
            if (!MessageDigest.isEqual(beforeFingerprint, afterFingerprint)) {
                throw failure(FailureCode.INTEGRITY_OR_BINDING_INVALID);
            }
            if (!transaction.updateProtectedValue(target, row, protectedValue)) {
                throw failure(FailureCode.CONCURRENT_ROW_CHANGE);
            }
            transaction.recordOutcome(
                    runId, targetType, MigrationStateRepository.Outcome.SUCCEEDED,
                    rowLocatorDigest(targetType, row.bindingRowId()), 1);
            return RowResult.migratedResult();
        } finally {
            clear(stored);
            clear(recovered);
            clear(protectedValue);
            clear(beforeFingerprint);
            clear(afterFingerprint);
        }
    }

    private byte[] fingerprint(byte[] value) {
        byte[] input = value.clone();
        try {
            byte[] digest = fingerprintPort.hmacFingerprint(input);
            if (digest == null || digest.length != 32) {
                throw failure(FailureCode.INTEGRITY_OR_BINDING_INVALID);
            }
            return digest.clone();
        } finally {
            clear(input);
        }
    }

    private void validateReviewedMigrationSet() {
        if (!manifest.resolvedForMigration()) {
            throw new IllegalArgumentException("migration manifest is unresolved");
        }
        Set<String> actualIndexes = manifest.targets().values().stream()
                .filter(target -> target.kind() == Kind.LEGACY_DIGEST)
                .map(ProtectedDataTarget::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!actualIndexes.equals(INDEX_TARGETS)
                || !manifest.noIndexTargetIds().equals(NO_INDEX_TARGETS)) {
            throw new IllegalArgumentException("reviewed migration target set has drifted");
        }
        for (String id : TARGET_TYPES.keySet()) {
            manifest.requireTarget(id);
        }
    }

    private ProtectedDataTarget reviewedTarget(String id) {
        if (id == null || !TARGET_TYPES.containsKey(id)) {
            throw failure(FailureCode.TARGET_NOT_REVIEWED);
        }
        return manifest.requireTarget(id);
    }

    private static String targetType(ProtectedDataTarget target) {
        return TARGET_TYPES.get(target.id());
    }

    private String manifestHexDigest() {
        String digest = manifest.digest();
        String normalized = digest.startsWith("sha256:") ? digest.substring(7) : digest;
        if (!MigrationStateRepository.SHA256.matcher(normalized).matches()) {
            throw failure(FailureCode.TARGET_NOT_REVIEWED);
        }
        return normalized;
    }

    private static ProtectionContext context(ProtectedDataTarget target, LegacyRow row) {
        return new ProtectionContext(
                ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap",
                target.table(),
                target.column(),
                row.tenantScope(),
                target.identityColumn() + "=" + row.resourceIdentity());
    }

    private static void requireNextState(CheckpointState current, CheckpointState next) {
        Objects.requireNonNull(next, "nextState");
        if (next.ordinal() != current.ordinal() + 1) {
            throw failure(FailureCode.CHECKPOINT_STATE_INVALID);
        }
    }

    private static byte[] rowLocatorDigest(String targetType, long rowId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("YCS-MIGRATION-ROW/v1\0".getBytes(StandardCharsets.US_ASCII));
            digest.update(targetType.getBytes(StandardCharsets.US_ASCII));
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(rowId).array());
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 21 must provide SHA-256", exception);
        }
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !MigrationStateRepository.SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !RUN_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static MigrationException failure(FailureCode code) {
        return new MigrationException(code);
    }

    @FunctionalInterface
    public interface IntegrityFingerprintPort {
        /** Returns a 32-byte HMAC under a purpose-separated nonextractable key. */
        byte[] hmacFingerprint(byte[] value);
    }

    @FunctionalInterface
    public interface LegacyBlindIndexPort {
        /** Computes ACTIVE/RETIRING HMAC indexes from one historical SHA-256 value. */
        List<BlindIndexEntry> indexes(
                byte[] historicalSha256Hex, String targetType, String fieldId, String tenantScope);
    }

    public record MigrationRequest(
            String runId,
            String targetId,
            String acceptedPairDigest,
            String leaseOwnerDigest,
            int batchSize,
            Duration leaseDuration) {

        public MigrationRequest {
            requireIdentifier(runId, "runId");
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("targetId is required");
            }
            requireDigest(acceptedPairDigest, "acceptedPairDigest");
            requireDigest(leaseOwnerDigest, "leaseOwnerDigest");
            if (batchSize < 1 || batchSize > MigrationStateRepository.MAXIMUM_BATCH_SIZE) {
                throw new IllegalArgumentException("batchSize is outside its bound");
            }
            if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                    || leaseDuration.compareTo(Duration.ofMinutes(10)) > 0) {
                throw new IllegalArgumentException("leaseDuration is outside its bound");
            }
        }
    }

    public record TransitionRequest(
            String runId,
            String targetId,
            String acceptedPairDigest,
            String leaseOwnerDigest,
            Duration leaseDuration,
            CheckpointState nextState) {

        public TransitionRequest {
            requireIdentifier(runId, "runId");
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("targetId is required");
            }
            requireDigest(acceptedPairDigest, "acceptedPairDigest");
            requireDigest(leaseOwnerDigest, "leaseOwnerDigest");
            if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()
                    || leaseDuration.compareTo(Duration.ofMinutes(10)) > 0) {
                throw new IllegalArgumentException("leaseDuration is outside its bound");
            }
            Objects.requireNonNull(nextState, "nextState");
        }
    }

    public record RunControlRequest(String runId, String acceptedPairDigest) {
        public RunControlRequest {
            requireIdentifier(runId, "runId");
            requireDigest(acceptedPairDigest, "acceptedPairDigest");
        }
    }

    public record BatchResult(
            String runId,
            String targetId,
            long scanned,
            long migrated,
            long verified,
            long skipped,
            Long lastRowId,
            boolean endOfTarget) {
    }

    public record TransitionResult(
            String targetId, CheckpointState previous, CheckpointState current) {
    }

    private record RowResult(long migrated, long verified, long skipped) {
        private static RowResult migratedResult() {
            return new RowResult(1, 1, 0);
        }

        private static RowResult existingEnvelopeResult() {
            return new RowResult(1, 1, 1);
        }

        private static RowResult skippedResult() {
            return new RowResult(0, 0, 1);
        }

        private static RowResult currentProtectedResult() {
            return new RowResult(1, 1, 1);
        }
    }

    public enum FailureCode {
        TARGET_NOT_REVIEWED,
        CHECKPOINT_STATE_INVALID,
        LEGACY_CLASSIFICATION_REJECTED,
        INTEGRITY_OR_BINDING_INVALID,
        CONCURRENT_ROW_CHANGE,
        WRITER_FENCE_REJECTED,
        LEGACY_VALUE_REMAINS,
        TRANSACTION_REJECTED
    }

    public static final class MigrationException extends IllegalStateException {
        private final FailureCode code;

        private MigrationException(FailureCode code) {
            super(SANITIZED_FAILURE);
            this.code = code;
        }

        public FailureCode code() {
            return code;
        }
    }
}
