package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;

/** JDBC implementation sharing the same purpose-row locks as {@link KeyReferenceRepository.Jdbc}. */
public final class JdbcFieldReferencePublicationFence implements FieldReferencePublicationFence {

    private static final String PURPOSE = "FIELD_ENCRYPTION_KEK";

    private final JdbcTemplate jdbc;
    private final EnvelopeCodec envelopes;

    public JdbcFieldReferencePublicationFence(JdbcTemplate jdbc) {
        this(jdbc, new EnvelopeCodec());
    }

    JdbcFieldReferencePublicationFence(JdbcTemplate jdbc, EnvelopeCodec envelopes) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.envelopes = Objects.requireNonNull(envelopes, "envelopes");
    }

    @Override
    public long lockAndValidate(byte[] encodedEnvelope, EnvelopeCodec.Target target) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || encodedEnvelope == null || target == null) {
            throw rejected();
        }
        CipherEnvelope envelope;
        try {
            envelope = envelopes.decode(encodedEnvelope, target);
        } catch (RuntimeException invalid) {
            throw rejected();
        }
        List<KeyRow> locked = jdbc.query("""
                SELECT key_version, provider_id, provider_key_reference, key_state
                FROM ycs_crypto_key_references
                WHERE purpose = ?
                ORDER BY key_version
                FOR UPDATE
                """, (resultSet, rowNumber) -> new KeyRow(
                resultSet.getLong(1), resultSet.getString(2),
                resultSet.getString(3), resultSet.getString(4)), PURPOSE);
        List<KeyRow> writable = locked.stream()
                .filter(key -> "ACTIVE".equals(key.state())
                        || "ROTATION_REQUIRED".equals(key.state()))
                .toList();
        if (writable.size() != 1
                || !writable.getFirst().providerId().equals(envelope.providerId())
                || !writable.getFirst().reference().equals(envelope.keyReference())) {
            throw rejected();
        }
        return writable.getFirst().version();
    }

    private static IllegalStateException rejected() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }

    private record KeyRow(long version, String providerId, String reference, String state) {
    }
}
