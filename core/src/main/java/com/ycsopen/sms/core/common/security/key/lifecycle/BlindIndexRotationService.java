package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Idempotent metadata-only blind-index rotation; legacy cells are outside this boundary. */
public final class BlindIndexRotationService {

    public static final String SANITIZED_FAILURE = "blind-index rotation failed";

    public record Row(String targetType,
                      long legacyRowId,
                      String fieldId,
                      byte[] originalRowDigest,
                      String normalizedMobile,
                      BlindIndexPort.Context context) {
        public Row {
            if (targetType == null || targetType.isBlank() || legacyRowId < 1
                    || fieldId == null || fieldId.isBlank()
                    || originalRowDigest == null || originalRowDigest.length != 32
                    || normalizedMobile == null || context == null
                    || !targetType.equals(context.targetType()) || !fieldId.equals(context.field())) {
                throw new IllegalArgumentException("invalid blind-index rotation row");
            }
            originalRowDigest = originalRowDigest.clone();
        }

        @Override
        public byte[] originalRowDigest() {
            return originalRowDigest.clone();
        }

        @Override
        public String toString() {
            return "Row[targetType=" + targetType + ", legacyRowId=" + legacyRowId
                    + ", fieldId=" + fieldId + ", values=[redacted]]";
        }
    }

    public record MetadataRow(String targetType,
                              long legacyRowId,
                              String fieldId,
                              long keyVersion,
                              String indexValue,
                              KeyState status,
                              byte[] originalRowDigest) {
        public MetadataRow {
            if (targetType == null || legacyRowId < 1 || fieldId == null || keyVersion < 1
                    || indexValue == null || indexValue.length() != VersionedBlindIndex.CANONICAL_CHARACTERS
                    || status != KeyState.ACTIVE && status != KeyState.RETIRING
                    || originalRowDigest == null || originalRowDigest.length != 32) {
                throw new IllegalArgumentException("invalid blind-index metadata row");
            }
            originalRowDigest = originalRowDigest.clone();
        }

        @Override
        public byte[] originalRowDigest() {
            return originalRowDigest.clone();
        }

        @Override
        public String toString() {
            return "MetadataRow[targetType=" + targetType + ", legacyRowId=" + legacyRowId
                    + ", fieldId=" + fieldId + ", keyVersion=" + keyVersion
                    + ", status=" + status + ", value=[redacted]]";
        }
    }

    public interface Store {
        /** Commits all version rows atomically and treats an exact existing row as success. */
        void upsertSeparateRows(List<MetadataRow> rows);

        List<MetadataRow> find(String targetType, long legacyRowId, String fieldId);
    }

    private final BlindIndexPort blindIndexes;
    private final KeyReferenceRepository keys;
    private final Store store;

    public BlindIndexRotationService(BlindIndexPort blindIndexes,
                                     KeyReferenceRepository keys,
                                     Store store) {
        this.blindIndexes = Objects.requireNonNull(blindIndexes, "blindIndexes");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.store = Objects.requireNonNull(store, "store");
    }

    public List<MetadataRow> backfill(Row row) {
        try {
            Objects.requireNonNull(row, "row");
            BlindIndexPort.OrderedIndexes generated = blindIndexes.writeIndexes(
                    row.normalizedMobile(), row.context());
            Map<Long, KeyState> writable = keys.findByPurpose(
                            KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX).stream()
                    .filter(key -> key.state() == KeyState.ACTIVE || key.state() == KeyState.RETIRING)
                    .collect(Collectors.toUnmodifiableMap(
                            KeyReferenceRepository.KeyReference::keyVersion,
                            KeyReferenceRepository.KeyReference::state));
            if (writable.isEmpty() || writable.values().stream()
                    .filter(state -> state == KeyState.ACTIVE).count() != 1
                    || writable.size() != generated.values().size()) {
                throw failure();
            }
            List<MetadataRow> rows = new ArrayList<>();
            for (VersionedBlindIndex index : generated.values()) {
                KeyState status = writable.get((long) index.keyVersion());
                if (status == null) {
                    throw failure();
                }
                rows.add(new MetadataRow(row.targetType(), row.legacyRowId(), row.fieldId(),
                        index.keyVersion(), index.canonicalValue(), status,
                        row.originalRowDigest()));
            }
            store.upsertSeparateRows(rows);
            assertParity(row, rows);
            return List.copyOf(rows);
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw failure();
        }
    }

    private void assertParity(Row row, List<MetadataRow> expected) {
        Map<Long, MetadataRow> actual = store.find(row.targetType(), row.legacyRowId(), row.fieldId())
                .stream().collect(Collectors.toUnmodifiableMap(MetadataRow::keyVersion, Function.identity()));
        if (actual.size() != expected.size()) {
            throw failure();
        }
        for (MetadataRow required : expected) {
            MetadataRow stored = actual.get(required.keyVersion());
            if (stored == null || stored.status() != required.status()
                    || !stored.indexValue().equals(required.indexValue())
                    || !MessageDigest.isEqual(stored.originalRowDigest(), required.originalRowDigest())) {
                throw failure();
            }
        }
    }

    /** Exact SQL owner for rotation rows; it has no legacy-table dependency or write path. */
    public static final class JdbcStore implements Store {

        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;

        public JdbcStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.transactions = Objects.requireNonNull(transactions, "transactions");
        }

        @Override
        public void upsertSeparateRows(List<MetadataRow> rows) {
            List<MetadataRow> requested = List.copyOf(Objects.requireNonNull(rows, "rows"));
            if (requested.isEmpty() || requested.stream().map(MetadataRow::keyVersion).distinct().count()
                    != requested.size()) {
                throw new IllegalArgumentException("invalid blind-index row set");
            }
            transactions.executeWithoutResult(status -> {
                for (MetadataRow row : requested) {
                    List<MetadataRow> existing = select(row.targetType(), row.legacyRowId(),
                            row.fieldId(), row.keyVersion(), true);
                    if (existing.isEmpty()) {
                        int inserted = jdbc.update("""
                                INSERT INTO ycs_crypto_blind_indexes
                                    (target_type, legacy_row_id, field_id, key_purpose, key_version,
                                     index_value, index_status, original_row_digest)
                                SELECT ?, ?, ?, 'MOBILE_BLIND_INDEX', ?, ?, ?, ?
                                FROM ycs_crypto_key_references
                                WHERE purpose = 'MOBILE_BLIND_INDEX' AND key_version = ?
                                  AND key_state = ?
                                """, row.targetType(), row.legacyRowId(), row.fieldId(),
                                row.keyVersion(), row.indexValue(), row.status().name(),
                                row.originalRowDigest(), row.keyVersion(), row.status().name());
                        if (inserted != 1) {
                            throw failure();
                        }
                    } else if (!same(existing.getFirst(), row)) {
                        throw failure();
                    }
                }
            });
        }

        @Override
        public List<MetadataRow> find(String targetType, long legacyRowId, String fieldId) {
            return select(targetType, legacyRowId, fieldId, null, false);
        }

        private List<MetadataRow> select(String targetType,
                                         long legacyRowId,
                                         String fieldId,
                                         Long keyVersion,
                                         boolean lock) {
            String sql = "SELECT target_type, legacy_row_id, field_id, key_version, index_value, "
                    + "index_status, original_row_digest FROM ycs_crypto_blind_indexes "
                    + "WHERE target_type = ? AND legacy_row_id = ? AND field_id = ?"
                    + (keyVersion == null ? "" : " AND key_version = ?")
                    + " ORDER BY key_version" + (lock ? " FOR UPDATE" : "");
            Object[] parameters = keyVersion == null
                    ? new Object[]{targetType, legacyRowId, fieldId}
                    : new Object[]{targetType, legacyRowId, fieldId, keyVersion};
            return jdbc.query(sql, (rs, index) -> new MetadataRow(rs.getString("target_type"),
                    rs.getLong("legacy_row_id"), rs.getString("field_id"),
                    rs.getLong("key_version"), rs.getString("index_value"),
                    KeyState.valueOf(rs.getString("index_status")),
                    rs.getBytes("original_row_digest")), parameters);
        }

        private static boolean same(MetadataRow left, MetadataRow right) {
            return left.targetType().equals(right.targetType())
                    && left.legacyRowId() == right.legacyRowId()
                    && left.fieldId().equals(right.fieldId())
                    && left.keyVersion() == right.keyVersion()
                    && left.indexValue().equals(right.indexValue())
                    && left.status() == right.status()
                    && MessageDigest.isEqual(left.originalRowDigest(), right.originalRowDigest());
        }
    }

    private static IllegalStateException failure() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }
}
