package com.ycsopen.sms.core.common.security.persistence;

import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/** Exact SQL owner for Phase-3 blind-index metadata rows. */
@Repository
public class BlindIndexMetadataRepository {

    private static final String TARGET_TYPE = "MESSAGE_TASK";
    private static final String FIELD_ID = "mobile";
    private static final String KEY_PURPOSE = "MOBILE_BLIND_INDEX";

    private final JdbcTemplate jdbcTemplate;

    public BlindIndexMetadataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    void insertMessageTaskIndexes(MessageTask task, PreparedMessageMobile prepared) {
        if (task == null || task.getId() == null || prepared == null) {
            throw new IllegalStateException(MessageTaskProtectionAdapter.SANITIZED_FAILURE);
        }
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
}
