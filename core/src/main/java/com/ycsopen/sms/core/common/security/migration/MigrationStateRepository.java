package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.CheckpointState;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskRowBinding;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional state boundary for the reviewed Phase-03 migration targets.
 *
 * <p>The runner receives a {@link Transaction} only inside {@link #transaction(Function)}. A
 * production implementation must therefore commit a business-row change, its sanitized outcome
 * and the new checkpoint together. Implementations must never retain plaintext in state or events.
 * Dynamic SQL identifiers are selected only from {@link Jdbc#TARGETS}; callers cannot supply an
 * arbitrary table or column.</p>
 */
public interface MigrationStateRepository {

    int MAXIMUM_BATCH_SIZE = 1_000;
    Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    <T> T transaction(Function<Transaction, T> work);

    interface Transaction {
        void requireAcceptedPair(String pairDigest);

        void ensureRun(String runId, String pairDigest, String manifestDigest);

        Lease claimLease(String runId, String targetType, String ownerDigest, Instant expiresAt);

        Checkpoint checkpoint(String runId, String targetType);

        List<LegacyRow> readBatch(ProtectedDataTarget target, long afterRowId, int batchSize);

        /** Returns false when the PK/original-cell-digest optimistic predicate did not match. */
        boolean updateProtectedValue(ProtectedDataTarget target, LegacyRow row, byte[] envelope);

        /** Inserts or exactly re-verifies one row for every supplied key version. */
        boolean upsertBlindIndexes(
                String targetType, LegacyRow row, String fieldId, List<BlindIndexEntry> indexes);

        boolean blindIndexesMatch(
                String targetType, LegacyRow row, String fieldId, List<BlindIndexEntry> indexes);

        /** Validates a Phase-03 current message row without treating its locator as mobile data. */
        default boolean currentMessageBindingMatches(LegacyRow row, String fieldId) {
            return false;
        }

        long remainingLegacyRows(ProtectedDataTarget target);

        boolean integrityAndBindingComplete(ProtectedDataTarget target, String targetType);

        boolean deployedWritersCompatible(String targetType);

        long scrubLegacyDigests(ProtectedDataTarget target, String targetType);

        void setLegacyFallback(String targetType, boolean allowed);

        void setTargetState(String runId, String targetType, CheckpointState state);

        void saveCheckpoint(String runId, String targetType, Checkpoint checkpoint);

        void recordOutcome(
                String runId, String targetType, Outcome outcome, byte[] rowLocatorDigest,
                long affectedCount);

        void setRunState(String runId, RunState expected, RunState next, String pairDigest);

        RunStatus status(String runId);
    }

    enum Outcome {
        SUCCEEDED,
        SKIPPED,
        REJECTED,
        QUARANTINED
    }

    enum RunState {
        READY,
        RUNNING,
        PAUSED,
        ABORTED,
        COMPLETED,
        FAILED
    }

    record Lease(String runId, String targetType, String ownerDigest, Instant expiresAt) {
        public Lease {
            requireIdentifier(runId, "runId");
            requireIdentifier(targetType, "targetType");
            requireDigest(ownerDigest, "ownerDigest");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    record Checkpoint(
            CheckpointState state,
            Long lastRowId,
            byte[] lastOriginalDigest,
            long scanned,
            long migrated,
            long verified,
            long quarantined,
            long optimisticVersion) {

        public Checkpoint {
            Objects.requireNonNull(state, "state");
            if ((lastRowId == null) != (lastOriginalDigest == null)) {
                throw new IllegalArgumentException("checkpoint cursor is incomplete");
            }
            if (lastRowId != null && lastRowId < 0) {
                throw new IllegalArgumentException("checkpoint cursor must be unsigned");
            }
            lastOriginalDigest = copyDigest(lastOriginalDigest, "lastOriginalDigest", true);
            if (scanned < 0 || migrated < 0 || verified < 0 || quarantined < 0
                    || migrated > scanned || verified > migrated || quarantined > scanned
                    || optimisticVersion < 0) {
                throw new IllegalArgumentException("checkpoint counters are invalid");
            }
        }

        @Override
        public byte[] lastOriginalDigest() {
            return lastOriginalDigest == null ? null : lastOriginalDigest.clone();
        }

        public static Checkpoint discovered() {
            return new Checkpoint(CheckpointState.DISCOVERED, null, null, 0, 0, 0, 0, 0);
        }
    }

    /** One bounded row. The source bytes and digest are defensive copies and never stringify. */
    record LegacyRow(
            long bindingRowId,
            Long checkpointCursor,
            String resourceIdentity,
            String tenantScope,
            byte[] storedValue,
            byte[] originalCellDigest,
            StoredValueKind storedValueKind) {

        public LegacyRow(
                long bindingRowId,
                Long checkpointCursor,
                String resourceIdentity,
                String tenantScope,
                byte[] storedValue,
                byte[] originalCellDigest) {
            this(bindingRowId, checkpointCursor, resourceIdentity, tenantScope,
                    storedValue, originalCellDigest, StoredValueKind.LEGACY_CANDIDATE);
        }

        public LegacyRow {
            if (bindingRowId < 1 || checkpointCursor != null && checkpointCursor < 0
                    || resourceIdentity == null || resourceIdentity.isBlank()
                    || tenantScope == null
                    || !("global".equals(tenantScope) || tenantScope.startsWith("tenant:"))
                    || storedValueKind == null) {
                throw new IllegalArgumentException("legacy row identity is invalid");
            }
            storedValue = storedValue == null ? null : storedValue.clone();
            originalCellDigest = copyDigest(originalCellDigest, "originalCellDigest", false);
        }

        @Override
        public byte[] storedValue() {
            return storedValue == null ? null : storedValue.clone();
        }

        @Override
        public byte[] originalCellDigest() {
            return originalCellDigest.clone();
        }

        @Override
        public String toString() {
            return "LegacyRow[bindingRowId=" + bindingRowId
                    + ", tenantScope=[redacted], value=[redacted]]";
        }
    }

    enum StoredValueKind {
        LEGACY_CANDIDATE,
        CURRENT_MESSAGE_LOCATOR
    }

    record BlindIndexEntry(long keyVersion, String canonicalValue, String status) {
        private static final Pattern VALUE = Pattern.compile("[a-z2-7]{53}");
        private static final Set<String> STATUSES = Set.of("ACTIVE", "RETIRING");

        public BlindIndexEntry {
            if (keyVersion < 1 || keyVersion > 255
                    || canonicalValue == null || !VALUE.matcher(canonicalValue).matches()
                    || !STATUSES.contains(status)) {
                throw new IllegalArgumentException("blind-index entry is invalid");
            }
        }

        @Override
        public String toString() {
            return "BlindIndexEntry[keyVersion=" + keyVersion + ", value=[redacted], status=" + status + "]";
        }
    }

    record RunStatus(
            String runId,
            RunState state,
            String acceptedPairDigest,
            long scanned,
            long migrated,
            long verified,
            long quarantined) {

        public RunStatus {
            requireIdentifier(runId, "runId");
            Objects.requireNonNull(state, "state");
            requireDigest(acceptedPairDigest, "acceptedPairDigest");
            if (scanned < 0 || migrated < 0 || verified < 0 || quarantined < 0
                    || migrated > scanned || verified > migrated || quarantined > scanned) {
                throw new IllegalArgumentException("run counters are invalid");
            }
        }
    }

    /** Production JDBC implementation over the V1200 state tables and seven reviewed targets. */
    final class Jdbc implements MigrationStateRepository {
        private static final Map<String, TargetSql> TARGETS = Map.of(
                "mobile_portability.mobile_hash", new TargetSql(
                        "MOBILE_PORTABILITY", "mobile_portability", "mobile_hash", "mobile_hash", null, true),
                "blacklist_entries.mobile_hash", new TargetSql(
                        "BLACKLIST_ENTRY", "blacklist_entries", "mobile_hash", "id", "tenant_id", false),
                "third_party_risk_check_logs.mobile_hash", new TargetSql(
                        "THIRD_PARTY_RISK_CHECK_LOG", "third_party_risk_check_logs", "mobile_hash", "id", null, false),
                "message_tasks.mobile_hash", new TargetSql(
                        "MESSAGE_TASK", "message_tasks", "mobile_hash", "id", "tenant_id", false),
                "unsubscribe_records.mobile_hash", new TargetSql(
                        "UNSUBSCRIBE_RECORD", "unsubscribe_records", "mobile_hash", "id", "tenant_id", false),
                "bulk_sending_items.mobile_encrypted", new TargetSql(
                        "BULK_SENDING_ITEM_MOBILE", "bulk_sending_items", "mobile_encrypted", "id", null, false),
                "uplink_records.mobile_encrypted", new TargetSql(
                        "UPLINK_RECORD_MOBILE", "uplink_records", "mobile_encrypted", "id", "tenant_id", false));

        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;
        private final LongSupplier migrationRowIds;
        private final SecureRandom locatorRandom;

        public Jdbc(JdbcTemplate jdbc, TransactionTemplate transactions) {
            this(jdbc, transactions, new SecureRandom()::nextLong, new SecureRandom());
        }

        Jdbc(
                JdbcTemplate jdbc,
                TransactionTemplate transactions,
                LongSupplier migrationRowIds,
                SecureRandom locatorRandom) {
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.transactions = Objects.requireNonNull(transactions, "transactions");
            this.migrationRowIds = Objects.requireNonNull(migrationRowIds, "migrationRowIds");
            this.locatorRandom = Objects.requireNonNull(locatorRandom, "locatorRandom");
        }

        @Override
        public <T> T transaction(Function<Transaction, T> work) {
            Objects.requireNonNull(work, "work");
            T result = transactions.execute(status -> work.apply(
                    new JdbcTransaction(jdbc, migrationRowIds, locatorRandom)));
            if (result == null) {
                throw new IllegalStateException("migration transaction returned no result");
            }
            return result;
        }

        public static String targetType(ProtectedDataTarget target) {
            return descriptor(target).targetType();
        }

        private static TargetSql descriptor(ProtectedDataTarget target) {
            Objects.requireNonNull(target, "target");
            TargetSql selected = TARGETS.get(target.id());
            if (selected == null
                    || !selected.table().equals(target.table())
                    || !selected.column().equals(target.column())) {
                throw new IllegalArgumentException("target is not in the reviewed migration SQL map");
            }
            return selected;
        }

        private static final class JdbcTransaction implements Transaction {
            private static final int MAXIMUM_BINDING_ID_ATTEMPTS = 8;
            private final JdbcTemplate jdbc;
            private final LongSupplier migrationRowIds;
            private final SecureRandom locatorRandom;

            private JdbcTransaction(
                    JdbcTemplate jdbc, LongSupplier migrationRowIds, SecureRandom locatorRandom) {
                this.jdbc = jdbc;
                this.migrationRowIds = migrationRowIds;
                this.locatorRandom = locatorRandom;
            }

            @Override
            public void requireAcceptedPair(String pairDigest) {
                requireDigest(pairDigest, "pairDigest");
                Long count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_manifest_pair_admission "
                                + "WHERE singleton_id = 1 AND pair_digest = UNHEX(?)",
                        Long.class, pairDigest);
                if (!Long.valueOf(1).equals(count)) {
                    throw rejected();
                }
            }

            @Override
            public void ensureRun(String runId, String pairDigest, String manifestDigest) {
                requireIdentifier(runId, "runId");
                requireDigest(pairDigest, "pairDigest");
                requireDigest(manifestDigest, "manifestDigest");
                int inserted = jdbc.update("INSERT IGNORE INTO ycs_crypto_migration_runs "
                                + "(migration_run_id, admitted_singleton_id, admitted_pair_digest, run_state, "
                                + "manifest_digest) SELECT ?, 1, pair_digest, 'READY', UNHEX(?) "
                                + "FROM ycs_crypto_manifest_pair_admission "
                                + "WHERE singleton_id = 1 AND pair_digest = UNHEX(?)",
                        runId, manifestDigest, pairDigest);
                Long matching = jdbc.queryForObject("SELECT COUNT(*) FROM ycs_crypto_migration_runs "
                                + "WHERE migration_run_id = ? AND admitted_pair_digest = UNHEX(?) "
                                + "AND manifest_digest = UNHEX(?)",
                        Long.class, runId, pairDigest, manifestDigest);
                if ((inserted != 0 && inserted != 1) || !Long.valueOf(1).equals(matching)) {
                    throw rejected();
                }
            }

            @Override
            public Lease claimLease(
                    String runId, String targetType, String ownerDigest, Instant expiresAt) {
                requireIdentifier(targetType, "targetType");
                requireDigest(ownerDigest, "ownerDigest");
                Objects.requireNonNull(expiresAt, "expiresAt");
                List<String> lockedTargets = jdbc.queryForList(
                        "SELECT target_type FROM ycs_crypto_migration_targets "
                                + "WHERE target_type = ? FOR UPDATE",
                        String.class, targetType);
                if (lockedTargets.size() != 1) {
                    throw rejected();
                }
                Long competing = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_migration_checkpoints "
                                + "WHERE target_type = ? AND migration_run_id <> ? "
                                + "AND lease_expires_at > CURRENT_TIMESTAMP(6)",
                        Long.class, targetType, runId);
                if (!Long.valueOf(0).equals(competing)) {
                    throw rejected();
                }
                int updated = jdbc.update("UPDATE ycs_crypto_migration_runs SET run_state = 'RUNNING', "
                                + "lease_owner_digest = UNHEX(?), lease_expires_at = ?, "
                                + "optimistic_version = optimistic_version + 1 "
                                + "WHERE migration_run_id = ? AND run_state IN ('READY','RUNNING','PAUSED') "
                                + "AND (lease_expires_at IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(6) "
                                + "OR lease_owner_digest = UNHEX(?))",
                        ownerDigest, java.sql.Timestamp.from(expiresAt), runId, ownerDigest);
                if (updated != 1) {
                    throw rejected();
                }
                jdbc.update("INSERT IGNORE INTO ycs_crypto_migration_checkpoints "
                                + "(migration_run_id, target_type, target_state) VALUES (?, ?, 'DISCOVERED')",
                        runId, targetType);
                int checkpointLease = jdbc.update("UPDATE ycs_crypto_migration_checkpoints SET "
                                + "lease_owner_digest = UNHEX(?), lease_expires_at = ?, "
                                + "optimistic_version = optimistic_version + 1 "
                                + "WHERE migration_run_id = ? AND target_type = ? "
                                + "AND (lease_expires_at IS NULL OR lease_expires_at <= CURRENT_TIMESTAMP(6) "
                                + "OR lease_owner_digest = UNHEX(?))",
                        ownerDigest, java.sql.Timestamp.from(expiresAt), runId, targetType, ownerDigest);
                if (checkpointLease != 1) {
                    throw rejected();
                }
                return new Lease(runId, targetType, ownerDigest, expiresAt);
            }

            @Override
            public Checkpoint checkpoint(String runId, String targetType) {
                List<Checkpoint> rows = jdbc.query("SELECT target_state, last_legacy_row_id, "
                                + "last_original_row_digest, scanned_count, migrated_count, verified_count, "
                                + "quarantined_count, optimistic_version "
                                + "FROM ycs_crypto_migration_checkpoints "
                                + "WHERE migration_run_id = ? AND target_type = ? FOR UPDATE",
                        (resultSet, rowNumber) -> new Checkpoint(
                                CheckpointState.valueOf(resultSet.getString(1)),
                                resultSet.getObject(2, Long.class), resultSet.getBytes(3),
                                resultSet.getLong(4), resultSet.getLong(5), resultSet.getLong(6),
                                resultSet.getLong(7), resultSet.getLong(8)),
                        runId, targetType);
                if (rows.size() != 1) {
                    throw rejected();
                }
                return rows.getFirst();
            }

            @Override
            public List<LegacyRow> readBatch(
                    ProtectedDataTarget target, long afterRowId, int batchSize) {
                if (afterRowId < 0 || batchSize < 1 || batchSize > MAXIMUM_BATCH_SIZE) {
                    throw new IllegalArgumentException("migration batch is outside its bound");
                }
                TargetSql sql = descriptor(target);
                String tenant = sql.tenant() == null
                        ? "'global'"
                        : "CONCAT('tenant:', COALESCE(CAST(" + sql.tenant() + " AS CHAR), 'global'))";
                if (sql.randomBinding()) {
                    // The string PK is the sensitive raw SHA value. Never derive a numeric locator
                    // or cursor from it: bounded rescan excludes exact already-bound cell digests.
                    Set<Long> allocatedInBatch = new HashSet<>();
                    String qualifiedTenant = sql.tenant() == null
                            ? "'global'"
                            : "CONCAT('tenant:', COALESCE(CAST(legacy." + sql.tenant()
                                    + " AS CHAR), 'global'))";
                    String statement = "SELECT legacy." + sql.identity() + ", "
                            + qualifiedTenant
                            + ", legacy." + sql.column() + " FROM " + sql.table() + " legacy "
                            + "WHERE EXISTS (SELECT 1 FROM ycs_crypto_key_references key_ref "
                            + "WHERE key_ref.purpose = 'MOBILE_BLIND_INDEX' "
                            + "AND key_ref.key_state IN ('ACTIVE','RETIRING') "
                            + "AND NOT EXISTS (SELECT 1 FROM ycs_crypto_blind_indexes idx "
                            + "WHERE idx.target_type = ? AND idx.original_row_digest = "
                            + "UNHEX(SHA2(legacy." + sql.column() + ", 256)) "
                            + "AND idx.key_version = key_ref.key_version "
                            + "AND idx.index_status = key_ref.key_state)) "
                            + "ORDER BY legacy." + sql.identity() + " LIMIT ?";
                    return jdbc.query(statement, (resultSet, rowNumber) -> {
                        byte[] value = resultSet.getBytes(3);
                        byte[] digest = cellDigest(value);
                        long bindingId = existingOrAllocateBindingId(
                                sql.targetType(), digest, allocatedInBatch);
                        return new LegacyRow(bindingId, null, resultSet.getString(1),
                                normalizeTenant(resultSet.getString(2)), value, digest);
                    }, sql.targetType(), batchSize);
                }
                String statement = "SELECT " + sql.identity() + ", CAST(" + sql.identity()
                        + " AS CHAR), " + tenant + ", " + sql.column() + " FROM " + sql.table()
                        + " WHERE " + sql.identity() + " > ? ORDER BY " + sql.identity() + " LIMIT ?";
                return jdbc.query(statement, (resultSet, rowNumber) -> {
                    byte[] value = resultSet.getBytes(4);
                    long id = resultSet.getLong(1);
                    StoredValueKind kind = "MESSAGE_TASK".equals(sql.targetType())
                            && MessageTaskRowBinding.isCurrentLocator(
                            new String(value, java.nio.charset.StandardCharsets.US_ASCII))
                            ? StoredValueKind.CURRENT_MESSAGE_LOCATOR
                            : StoredValueKind.LEGACY_CANDIDATE;
                    return new LegacyRow(id, id, resultSet.getString(2),
                            normalizeTenant(resultSet.getString(3)), value, cellDigest(value), kind);
                }, afterRowId, batchSize);
            }

            @Override
            public boolean updateProtectedValue(
                    ProtectedDataTarget target, LegacyRow row, byte[] envelope) {
                TargetSql sql = descriptor(target);
                if (target.kind() != ProtectedDataTarget.Kind.DATABASE_FIELD || envelope == null) {
                    throw new IllegalArgumentException("protected field update is invalid");
                }
                int updated = jdbc.update("UPDATE " + sql.table() + " SET " + sql.column()
                                + " = ? WHERE " + sql.identity() + " = ? AND SHA2(" + sql.column()
                                + ", 256) = ?",
                        envelope.clone(), row.resourceIdentity(), hex(row.originalCellDigest()));
                return updated == 1;
            }

            @Override
            public boolean upsertBlindIndexes(
                    String targetType, LegacyRow row, String fieldId,
                    List<BlindIndexEntry> indexes) {
                requireIdentifier(targetType, "targetType");
                requireIdentifier(fieldId, "fieldId");
                byte[] rowBinding = "BLACKLIST_ENTRY".equals(targetType)
                        ? blacklistRowBinding(row.bindingRowId(), row.originalCellDigest()) : null;
                for (BlindIndexEntry index : canonicalIndexes(indexes)) {
                    jdbc.update("INSERT IGNORE INTO ycs_crypto_blind_indexes "
                                    + "(target_type, legacy_row_id, field_id, key_purpose, key_version, "
                                    + "index_value, index_status, original_row_digest, row_binding_digest) "
                                    + "SELECT ?, ?, ?, 'MOBILE_BLIND_INDEX', ?, ?, ?, UNHEX(?), ? "
                                    + "FROM ycs_crypto_key_references WHERE purpose = 'MOBILE_BLIND_INDEX' "
                                    + "AND key_version = ? AND key_state = ?",
                            targetType, row.bindingRowId(), fieldId, index.keyVersion(), index.canonicalValue(),
                            index.status(), hex(row.originalCellDigest()), rowBinding,
                            index.keyVersion(), index.status());
                }
                return blindIndexesMatch(targetType, row, fieldId, indexes);
            }

            @Override
            public boolean blindIndexesMatch(
                    String targetType, LegacyRow row, String fieldId,
                    List<BlindIndexEntry> indexes) {
                List<String> stored = jdbc.queryForList("SELECT CONCAT(key_version, ':', index_status, ':', "
                                + "index_value, ':', LOWER(HEX(original_row_digest)), ':', "
                                + "COALESCE(LOWER(HEX(row_binding_digest)), 'none')) "
                                + "FROM ycs_crypto_blind_indexes WHERE target_type = ? "
                                + "AND legacy_row_id = ? AND field_id = ? ORDER BY key_version",
                        String.class, targetType, row.bindingRowId(), fieldId);
                List<String> expected = canonicalIndexes(indexes).stream()
                        .map(value -> value.keyVersion() + ":" + value.status() + ":"
                                + value.canonicalValue() + ":" + hex(row.originalCellDigest()) + ":"
                                + ("BLACKLIST_ENTRY".equals(targetType)
                                ? hex(blacklistRowBinding(row.bindingRowId(), row.originalCellDigest()))
                                : "none"))
                        .toList();
                return stored.equals(expected);
            }

            private byte[] blacklistRowBinding(long rowId, byte[] originalRowDigest) {
                List<Map<String, Object>> rows = jdbc.queryForList("""
                        SELECT tenant_id, mobile_encrypted, mobile_hash, list_type, status
                        FROM blacklist_entries WHERE id = ?
                        """, rowId);
                if (rows.size() != 1) {
                    throw new IllegalStateException("migration blacklist binding failed");
                }
                Map<String, Object> row = rows.getFirst();
                Object tenant = row.get("tenant_id");
                Long tenantId = tenant == null ? null : ((Number) tenant).longValue();
                byte[] envelope = (byte[]) row.get("mobile_encrypted");
                String mobileHash = Objects.toString(row.get("mobile_hash"), null);
                if (mobileHash == null || !MessageDigest.isEqual(originalRowDigest,
                        cellDigest(mobileHash.getBytes(StandardCharsets.UTF_8)))) {
                    throw new IllegalStateException("migration blacklist binding failed");
                }
                return com.ycsopen.sms.core.common.security.persistence.BlindIndexLookupService
                        .blacklistBinding(rowId, tenantId,
                                com.ycsopen.sms.core.domain.entity.BlacklistEntry.ListType.valueOf(
                                        Objects.toString(row.get("list_type"), "")),
                                com.ycsopen.sms.core.domain.entity.BlacklistEntry.Status.valueOf(
                                        Objects.toString(row.get("status"), "")),
                                envelope, originalRowDigest);
            }

            @Override
            public boolean currentMessageBindingMatches(LegacyRow row, String fieldId) {
                if (row.storedValueKind() != StoredValueKind.CURRENT_MESSAGE_LOCATOR
                        || !"mobile".equals(fieldId)) {
                    return false;
                }
                List<CurrentMessageRow> messages = jdbc.query(
                        "SELECT tenant_id, message_id, mobile_hash, mobile_encrypted "
                                + "FROM message_tasks WHERE id = ?",
                        (resultSet, rowNumber) -> new CurrentMessageRow(
                                resultSet.getLong(1), resultSet.getString(2),
                                resultSet.getString(3), resultSet.getBytes(4)),
                        row.bindingRowId());
                if (messages.size() != 1) {
                    return false;
                }
                CurrentMessageRow message = messages.getFirst();
                byte[] expectedDigest = null;
                try {
                    byte[] storedLocatorBytes = row.storedValue();
                    try {
                        String storedLocator = new String(
                                storedLocatorBytes, java.nio.charset.StandardCharsets.US_ASCII);
                        if (!MessageTaskRowBinding.isCurrentLocator(message.locator())
                                || !message.locator().equals(storedLocator)
                                || message.envelope() == null || message.envelope().length < 4
                                || message.envelope()[0] != 'Y' || message.envelope()[1] != 'C'
                                || message.envelope()[2] != 'S' || message.envelope()[3] != 'E') {
                            return false;
                        }
                    } finally {
                        java.util.Arrays.fill(storedLocatorBytes, (byte) 0);
                    }
                    expectedDigest = MessageTaskRowBinding.originalRowDigest(
                            message.tenantId(), row.bindingRowId(), message.messageId(),
                            message.locator(), message.envelope());
                    List<CurrentBinding> stored = jdbc.query(
                            "SELECT idx.key_version, idx.index_status, idx.key_purpose, "
                                    + "idx.index_value, idx.original_row_digest "
                                    + "FROM ycs_crypto_blind_indexes idx "
                                    + "WHERE idx.target_type = 'MESSAGE_TASK' "
                                    + "AND idx.legacy_row_id = ? AND idx.field_id = ? "
                                    + "ORDER BY idx.key_version",
                            (resultSet, rowNumber) -> new CurrentBinding(
                                    resultSet.getLong(1), resultSet.getString(2),
                                    resultSet.getString(3), resultSet.getString(4),
                                    resultSet.getBytes(5)),
                            row.bindingRowId(), fieldId);
                    List<KeyState> required = jdbc.query(
                            "SELECT key_version, key_state FROM ycs_crypto_key_references "
                                    + "WHERE purpose = 'MOBILE_BLIND_INDEX' "
                                    + "AND key_state IN ('ACTIVE','RETIRING') ORDER BY key_version",
                            (resultSet, rowNumber) -> new KeyState(
                                    resultSet.getLong(1), resultSet.getString(2)));
                    if (stored.size() != required.size() || stored.isEmpty()) {
                        return false;
                    }
                    for (int index = 0; index < stored.size(); index++) {
                        CurrentBinding binding = stored.get(index);
                        KeyState key = required.get(index);
                        if (binding.keyVersion() != key.version()
                                || !binding.status().equals(key.state())
                                || !"MOBILE_BLIND_INDEX".equals(binding.purpose())
                                || !BlindIndexEntry.VALUE.matcher(binding.value()).matches()
                                || binding.originalDigest() == null
                                || binding.originalDigest().length != 32
                                || !MessageDigest.isEqual(expectedDigest, binding.originalDigest())) {
                            return false;
                        }
                    }
                    return true;
                } finally {
                    java.util.Arrays.fill(message.envelope(), (byte) 0);
                    if (expectedDigest != null) {
                        java.util.Arrays.fill(expectedDigest, (byte) 0);
                    }
                }
            }

            @Override
            public long remainingLegacyRows(ProtectedDataTarget target) {
                TargetSql sql = descriptor(target);
                Long count;
                if (target.kind() == ProtectedDataTarget.Kind.LEGACY_DIGEST) {
                    String scrubbedBinding = sql.randomBinding()
                            ? "LOWER(LPAD(HEX(idx.legacy_row_id), 16, '0')) = LEFT(legacy."
                                    + sql.column() + ", 16)"
                            : "idx.legacy_row_id = legacy." + sql.identity();
                    String legacyOnly = "MESSAGE_TASK".equals(sql.targetType())
                            ? "legacy." + sql.column() + " REGEXP '^[0-9a-f]{64}$' AND "
                            : "";
                    count = jdbc.queryForObject("SELECT COUNT(*) FROM " + sql.table() + " legacy "
                                    + "WHERE " + legacyOnly
                                    + "NOT EXISTS (SELECT 1 FROM ycs_crypto_blind_indexes idx "
                                    + "WHERE idx.target_type = ? AND " + scrubbedBinding + " "
                                    + "AND idx.original_row_digest <> UNHEX(SHA2(legacy."
                                    + sql.column() + ", 256)))",
                            Long.class, sql.targetType());
                } else {
                    count = jdbc.queryForObject("SELECT COUNT(*) FROM " + sql.table() + " WHERE "
                                    + sql.column() + " IS NOT NULL AND LEFT(" + sql.column()
                                    + ", 4) <> X'59435345'",
                            Long.class);
                }
                return Objects.requireNonNull(count, "remaining legacy count");
            }

            @Override
            public boolean integrityAndBindingComplete(
                    ProtectedDataTarget target, String targetType) {
                if (target.kind() != ProtectedDataTarget.Kind.LEGACY_DIGEST) {
                    return remainingLegacyRows(target) == 0;
                }
                TargetSql sql = descriptor(target);
                String legacyOnly = "MESSAGE_TASK".equals(sql.targetType())
                        ? "legacy." + sql.column() + " REGEXP '^[0-9a-f]{64}$' AND "
                        : "";
                Long missing = jdbc.queryForObject("SELECT COUNT(*) FROM " + sql.table()
                                + " legacy CROSS JOIN ycs_crypto_key_references key_ref "
                                + "WHERE " + legacyOnly
                                + "key_ref.purpose = 'MOBILE_BLIND_INDEX' "
                                + "AND key_ref.key_state IN ('ACTIVE','RETIRING') "
                                + "AND NOT EXISTS (SELECT 1 FROM ycs_crypto_blind_indexes idx "
                                + "WHERE idx.target_type = ? AND idx.original_row_digest = UNHEX(SHA2(legacy."
                                + sql.column() + ", 256)) "
                                + "AND idx.key_version = key_ref.key_version "
                                + "AND idx.index_status = key_ref.key_state)",
                        Long.class, targetType);
                Long orphans = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_blind_indexes idx "
                                + "WHERE idx.target_type = ? AND NOT EXISTS (SELECT 1 FROM "
                                + sql.table() + " legacy WHERE legacy." + sql.column()
                                + " REGEXP '^[0-9a-f]{64}$' AND idx.original_row_digest = "
                                + "UNHEX(SHA2(legacy." + sql.column() + ", 256)))"
                                + ("MESSAGE_TASK".equals(sql.targetType())
                                ? " AND NOT EXISTS (SELECT 1 FROM message_tasks current_row "
                                + "WHERE current_row.id = idx.legacy_row_id "
                                + "AND current_row.mobile_hash REGEXP '^p3c1_[A-Za-z0-9_-]{43}$')"
                                : ""),
                        Long.class, targetType);
                if (!Long.valueOf(0).equals(missing) || !Long.valueOf(0).equals(orphans)) {
                    return false;
                }
                if (!"MESSAGE_TASK".equals(sql.targetType())) {
                    return true;
                }
                List<LegacyRow> currentRows = jdbc.query(
                        "SELECT id, CAST(id AS CHAR), "
                                + "CONCAT('tenant:', CAST(tenant_id AS CHAR)), mobile_hash "
                                + "FROM message_tasks WHERE mobile_hash "
                                + "REGEXP '^p3c1_[A-Za-z0-9_-]{43}$' ORDER BY id",
                        (resultSet, rowNumber) -> {
                            byte[] value = resultSet.getBytes(4);
                            long id = resultSet.getLong(1);
                            return new LegacyRow(
                                    id, id, resultSet.getString(2), resultSet.getString(3),
                                    value, cellDigest(value), StoredValueKind.CURRENT_MESSAGE_LOCATOR);
                        });
                return currentRows.stream().allMatch(row ->
                        currentMessageBindingMatches(row, "mobile"));
            }

            @Override
            public boolean deployedWritersCompatible(String targetType) {
                Long admitted = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_manifest_pair_admission WHERE singleton_id = 1",
                        Long.class);
                return Long.valueOf(1).equals(admitted);
            }

            @Override
            public long scrubLegacyDigests(ProtectedDataTarget target, String targetType) {
                if (target.kind() != ProtectedDataTarget.Kind.LEGACY_DIGEST) {
                    return 0;
                }
                TargetSql sql = descriptor(target);
                String bindingQuery = "MESSAGE_TASK".equals(sql.targetType())
                        ? "SELECT DISTINCT idx.legacy_row_id, idx.original_row_digest "
                                + "FROM ycs_crypto_blind_indexes idx JOIN message_tasks legacy "
                                + "ON legacy.id = idx.legacy_row_id "
                                + "AND legacy.mobile_hash REGEXP '^[0-9a-f]{64}$' "
                                + "AND idx.original_row_digest = UNHEX(SHA2(legacy.mobile_hash, 256)) "
                                + "WHERE idx.target_type = ? ORDER BY idx.legacy_row_id"
                        : "SELECT DISTINCT legacy_row_id, original_row_digest "
                                + "FROM ycs_crypto_blind_indexes WHERE target_type = ? "
                                + "ORDER BY legacy_row_id";
                List<StoredBinding> bindings = jdbc.query(
                        bindingQuery,
                        (resultSet, rowNumber) -> new StoredBinding(
                                resultSet.getLong(1), resultSet.getBytes(2)), targetType);
                long scrubbed = 0;
                for (StoredBinding binding : bindings) {
                    byte[] suffix = new byte[24];
                    locatorRandom.nextBytes(suffix);
                    String locator = String.format(java.util.Locale.ROOT, "%016x", binding.rowId())
                            + HexFormat.of().formatHex(suffix);
                    java.util.Arrays.fill(suffix, (byte) 0);
                    String identityPredicate = sql.randomBinding()
                            ? "" : " AND " + sql.identity() + " = ?";
                    Object[] parameters = sql.randomBinding()
                            ? new Object[]{locator, hex(binding.originalDigest())}
                            : new Object[]{locator, hex(binding.originalDigest()), binding.rowId()};
                    int updated = jdbc.update("UPDATE " + sql.table() + " SET " + sql.column()
                                    + " = ? WHERE SHA2(" + sql.column() + ", 256) = ? "
                                    + "AND " + sql.column() + " REGEXP '^[0-9a-f]{64}$'"
                                    + identityPredicate,
                            parameters);
                    if (updated != 1) {
                        throw rejected();
                    }
                    scrubbed++;
                }
                return scrubbed;
            }

            @Override
            public void setLegacyFallback(String targetType, boolean allowed) {
                int updated = jdbc.update("UPDATE ycs_crypto_migration_targets SET "
                                + "legacy_fallback_allowed = ?, optimistic_version = optimistic_version + 1 "
                                + "WHERE target_type = ?",
                        allowed, targetType);
                if (updated != 1) {
                    throw rejected();
                }
            }

            @Override
            public void setTargetState(
                    String runId, String targetType, CheckpointState state) {
                int target = jdbc.update("UPDATE ycs_crypto_migration_targets SET target_state = ?, "
                                + "optimistic_version = optimistic_version + 1 WHERE target_type = ?",
                        state.name(), targetType);
                int checkpoint = jdbc.update("UPDATE ycs_crypto_migration_checkpoints SET target_state = ?, "
                                + "optimistic_version = optimistic_version + 1 "
                                + "WHERE migration_run_id = ? AND target_type = ?",
                        state.name(), runId, targetType);
                if (target != 1 || checkpoint != 1) {
                    throw rejected();
                }
            }

            @Override
            public void saveCheckpoint(String runId, String targetType, Checkpoint checkpoint) {
                int updated = jdbc.update("UPDATE ycs_crypto_migration_checkpoints SET "
                                + "last_legacy_row_id = ?, last_original_row_digest = ?, "
                                + "scanned_count = ?, migrated_count = ?, verified_count = ?, "
                                + "quarantined_count = ?, optimistic_version = optimistic_version + 1 "
                                + "WHERE migration_run_id = ? AND target_type = ? AND optimistic_version = ?",
                        checkpoint.lastRowId(), checkpoint.lastOriginalDigest(), checkpoint.scanned(),
                        checkpoint.migrated(), checkpoint.verified(), checkpoint.quarantined(),
                        runId, targetType, checkpoint.optimisticVersion());
                if (updated != 1) {
                    throw rejected();
                }
                jdbc.update("UPDATE ycs_crypto_migration_runs run SET "
                                + "scanned_count = (SELECT COALESCE(SUM(scanned_count), 0) "
                                + "FROM ycs_crypto_migration_checkpoints WHERE migration_run_id = ?), "
                                + "migrated_count = (SELECT COALESCE(SUM(migrated_count), 0) "
                                + "FROM ycs_crypto_migration_checkpoints WHERE migration_run_id = ?), "
                                + "verified_count = (SELECT COALESCE(SUM(verified_count), 0) "
                                + "FROM ycs_crypto_migration_checkpoints WHERE migration_run_id = ?), "
                                + "quarantined_count = (SELECT COALESCE(SUM(quarantined_count), 0) "
                                + "FROM ycs_crypto_migration_checkpoints WHERE migration_run_id = ?), "
                                + "optimistic_version = optimistic_version + 1 "
                                + "WHERE migration_run_id = ?",
                        runId, runId, runId, runId, runId);
            }

            @Override
            public void recordOutcome(
                    String runId, String targetType, Outcome outcome,
                    byte[] rowLocatorDigest, long affectedCount) {
                if (affectedCount < 0) {
                    throw new IllegalArgumentException("affected count must be unsigned");
                }
                jdbc.update("INSERT INTO ycs_crypto_migration_events "
                                + "(migration_run_id, target_type, event_category, outcome, "
                                + "row_locator_digest, affected_count) "
                                + "VALUES (?, ?, 'ROW_OUTCOME', ?, ?, ?)",
                        runId, targetType, outcome.name(),
                        copyDigest(rowLocatorDigest, "rowLocatorDigest", true), affectedCount);
            }

            @Override
            public void setRunState(
                    String runId, RunState expected, RunState next, String pairDigest) {
                requireDigest(pairDigest, "pairDigest");
                int updated = jdbc.update("UPDATE ycs_crypto_migration_runs SET run_state = ?, "
                                + "lease_owner_digest = NULL, lease_expires_at = NULL, "
                                + "optimistic_version = optimistic_version + 1 "
                                + "WHERE migration_run_id = ? AND run_state = ? "
                                + "AND admitted_pair_digest = UNHEX(?)",
                        next.name(), runId, expected.name(), pairDigest);
                if (updated != 1) {
                    throw rejected();
                }
                jdbc.update("UPDATE ycs_crypto_migration_checkpoints SET "
                                + "lease_owner_digest = NULL, lease_expires_at = NULL, "
                                + "optimistic_version = optimistic_version + 1 "
                                + "WHERE migration_run_id = ?",
                        runId);
            }

            @Override
            public RunStatus status(String runId) {
                List<RunStatus> rows = jdbc.query("SELECT run_state, LOWER(HEX(admitted_pair_digest)), "
                                + "scanned_count, migrated_count, verified_count, quarantined_count "
                                + "FROM ycs_crypto_migration_runs WHERE migration_run_id = ?",
                        (resultSet, rowNumber) -> new RunStatus(runId,
                                RunState.valueOf(resultSet.getString(1)), resultSet.getString(2),
                                resultSet.getLong(3), resultSet.getLong(4), resultSet.getLong(5),
                                resultSet.getLong(6)), runId);
                if (rows.size() != 1) {
                    throw rejected();
                }
                return rows.getFirst();
            }

            private long allocateBindingId(String targetType, Set<Long> allocatedInBatch) {
                for (int attempt = 0; attempt < MAXIMUM_BINDING_ID_ATTEMPTS; attempt++) {
                    long candidate = migrationRowIds.getAsLong() & Long.MAX_VALUE;
                    if (candidate == 0 || !allocatedInBatch.add(candidate)) {
                        continue;
                    }
                    Long existing = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM ycs_crypto_blind_indexes "
                                    + "WHERE target_type = ? AND legacy_row_id = ?",
                            Long.class, targetType, candidate);
                    if (Long.valueOf(0).equals(existing)) {
                        return candidate;
                    }
                    allocatedInBatch.remove(candidate);
                }
                throw rejected();
            }

            private long existingOrAllocateBindingId(
                    String targetType, byte[] originalDigest, Set<Long> allocatedInBatch) {
                List<Long> existing = jdbc.queryForList(
                        "SELECT DISTINCT legacy_row_id FROM ycs_crypto_blind_indexes "
                                + "WHERE target_type = ? AND original_row_digest = ? "
                                + "ORDER BY legacy_row_id",
                        Long.class, targetType, originalDigest.clone());
                if (existing.size() > 1) {
                    throw rejected();
                }
                if (existing.size() == 1) {
                    long bindingId = existing.getFirst();
                    if (!allocatedInBatch.add(bindingId)) {
                        throw rejected();
                    }
                    return bindingId;
                }
                return allocateBindingId(targetType, allocatedInBatch);
            }
        }

        private record TargetSql(
                String targetType, String table, String column, String identity,
                String tenant, boolean randomBinding) {
        }

        private record StoredBinding(long rowId, byte[] originalDigest) {
            private StoredBinding {
                originalDigest = copyDigest(originalDigest, "originalDigest", false);
            }

            @Override
            public byte[] originalDigest() {
                return originalDigest.clone();
            }
        }

        private record CurrentMessageRow(
                long tenantId, String messageId, String locator, byte[] envelope) {
        }

        private record CurrentBinding(
                long keyVersion,
                String status,
                String purpose,
                String value,
                byte[] originalDigest) {
        }

        private record KeyState(long version, String state) {
        }
    }

    private static String normalizeTenant(String value) {
        if (value == null || value.equals("tenant:global")) {
            return "global";
        }
        return value;
    }

    private static List<BlindIndexEntry> canonicalIndexes(List<BlindIndexEntry> indexes) {
        Objects.requireNonNull(indexes, "indexes");
        List<BlindIndexEntry> copy = List.copyOf(indexes);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("blind-index set is empty");
        }
        long previous = 0;
        for (BlindIndexEntry index : copy) {
            Objects.requireNonNull(index, "blindIndex");
            if (index.keyVersion() <= previous) {
                throw new IllegalArgumentException("blind-index versions are not canonical");
            }
            previous = index.keyVersion();
        }
        return copy;
    }

    private static byte[] cellDigest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value == null ? new byte[0] : value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 21 must provide SHA-256", exception);
        }
    }

    private static byte[] copyDigest(byte[] value, String field, boolean nullable) {
        if (value == null) {
            if (nullable) {
                return null;
            }
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length != 32) {
            throw new IllegalArgumentException(field + " must be a SHA-256 digest");
        }
        return value.clone();
    }

    private static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128
                || value.codePoints().anyMatch(code -> Character.isISOControl(code))) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException("protected-data migration state rejected");
    }
}
