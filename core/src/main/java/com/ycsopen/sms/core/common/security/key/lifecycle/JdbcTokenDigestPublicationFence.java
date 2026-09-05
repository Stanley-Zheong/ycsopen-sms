package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Shares the exact purpose-row locks used by {@link KeyReferenceRepository.Jdbc}. */
public final class JdbcTokenDigestPublicationFence implements TokenDigestPublicationFence {

    private final JdbcTemplate jdbc;

    public JdbcTokenDigestPublicationFence(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void lockAndValidate(VersionedTokenDigest digest) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || digest == null) {
            throw rejected();
        }
        String purpose = digest.purpose().storagePurpose();
        List<KeyRow> locked = jdbc.query("""
                SELECT key_version, key_state
                  FROM ycs_crypto_key_references
                 WHERE purpose = ?
                 ORDER BY key_version
                 FOR UPDATE
                """, (resultSet, row) -> new KeyRow(
                resultSet.getLong("key_version"), resultSet.getString("key_state")), purpose);
        List<KeyRow> active = locked.stream().filter(row -> "ACTIVE".equals(row.state())).toList();
        if (active.size() != 1 || active.getFirst().version() != digest.keyVersion()) {
            throw rejected();
        }
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }

    private record KeyRow(long version, String state) {
    }
}
