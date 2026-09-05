package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.key.lifecycle.FieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.key.lifecycle.JdbcFieldReferencePublicationFence;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/** Exact SQL owner for Phase-3 blind-index metadata rows. */
@Repository
public class BlindIndexMetadataRepository {

    private static final String TARGET_TYPE = "MESSAGE_TASK";
    private static final String FIELD_ID = "mobile";
    private static final String KEY_PURPOSE = "MOBILE_BLIND_INDEX";

    private final JdbcTemplate jdbcTemplate;
    private final FieldReferencePublicationFence fieldFence;

    @Autowired
    public BlindIndexMetadataRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new JdbcFieldReferencePublicationFence(jdbcTemplate));
    }

    BlindIndexMetadataRepository(
            JdbcTemplate jdbcTemplate,
            FieldReferencePublicationFence fieldFence) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.fieldFence = Objects.requireNonNull(fieldFence, "fieldFence");
    }

    void insertMessageTaskIndexes(MessageTask task, PreparedMessageMobile prepared) {
        if (task == null || task.getId() == null || prepared == null) {
            throw new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE);
        }
        byte[] envelope = prepared.copyEnvelope();
        try {
            fieldFence.lockAndValidate(envelope,
                    com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec.Target.DATABASE_FIELD);
        } finally {
            java.util.Arrays.fill(envelope, (byte) 0);
        }
        lockAndValidatePreparedVersions(prepared);
        Long boundRows = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM message_tasks
                WHERE id = ? AND tenant_id = ? AND message_id = ? AND mobile_hash = ?
                """, Long.class, task.getId(), prepared.tenantId(), prepared.messageId(),
                prepared.legacyLocator());
        if (!Long.valueOf(1L).equals(boundRows)) {
            throw new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE);
        }

        byte[] originalRowDigest = prepared.originalRowDigest(task.getId());
        try {
            for (VersionedBlindIndex index : prepared.writeIndexes().values()) {
                int inserted = jdbcTemplate.update("""
                        INSERT INTO ycs_crypto_blind_indexes
                            (target_type, legacy_row_id, field_id, key_purpose, key_version,
                             index_value, index_status, original_row_digest)
                        SELECT ?, ?, ?, ?, ?, ?, key_state, ?
                        FROM ycs_crypto_key_references
                        WHERE purpose = ? AND key_version = ?
                          AND key_state IN ('ACTIVE', 'RETIRING')
                        """,
                        TARGET_TYPE, task.getId(), FIELD_ID, KEY_PURPOSE,
                        index.keyVersion(), index.canonicalValue(), originalRowDigest,
                        KEY_PURPOSE, index.keyVersion());
                if (inserted != 1) {
                    throw new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE);
                }
            }
        } finally {
            java.util.Arrays.fill(originalRowDigest, (byte) 0);
        }
    }

    /**
     * Serializes a prepared write against activation and rejects a set produced from stale key
     * metadata. All purpose rows are locked so a PREPARED version cannot become ACTIVE until the
     * surrounding message transaction commits or rolls back.
     */
    void lockAndValidatePreparedVersions(PreparedMessageMobile prepared) {
        if (prepared == null) {
            throw new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE);
        }
        List<KeyVersionState> locked = jdbcTemplate.query("""
                SELECT key_version, key_state
                FROM ycs_crypto_key_references
                WHERE purpose = ?
                ORDER BY key_version
                FOR UPDATE
                """, (resultSet, rowNumber) -> new KeyVersionState(
                resultSet.getLong(1), resultSet.getString(2)), KEY_PURPOSE);
        List<Long> currentVersions = locked.stream()
                .filter(key -> "ACTIVE".equals(key.state()) || "RETIRING".equals(key.state()))
                .map(KeyVersionState::version)
                .toList();
        List<Long> preparedVersions = prepared.writeIndexes().values().stream()
                .map(index -> (long) index.keyVersion())
                .toList();
        long active = locked.stream().filter(key -> "ACTIVE".equals(key.state())).count();
        if (active != 1 || !currentVersions.equals(preparedVersions)) {
            throw new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE);
        }
    }

    private record KeyVersionState(long version, String state) {
    }

}
