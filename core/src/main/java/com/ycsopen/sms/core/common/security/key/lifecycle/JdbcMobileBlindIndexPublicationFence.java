package com.ycsopen.sms.core.common.security.key.lifecycle;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** JDBC implementation that takes the same complete purpose lock as lifecycle transitions. */
public final class JdbcMobileBlindIndexPublicationFence
        implements MobileBlindIndexPublicationFence {

    private final JdbcTemplate jdbc;

    public JdbcMobileBlindIndexPublicationFence(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public boolean lockAndValidate(List<ExpectedKey> expected) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || expected == null || expected.isEmpty()) {
            return false;
        }
        List<ExpectedKey> requested;
        try {
            requested = List.copyOf(expected);
        } catch (RuntimeException invalid) {
            return false;
        }
        long previous = 0;
        int active = 0;
        for (ExpectedKey key : requested) {
            if (key == null || key.keyVersion() <= previous) {
                return false;
            }
            previous = key.keyVersion();
            if (key.state() == KeyState.ACTIVE) {
                active++;
            }
        }
        if (active != 1) {
            return false;
        }

        List<KeyRow> locked = jdbc.query("""
                SELECT key_version, key_state
                  FROM ycs_crypto_key_references
                 WHERE purpose = 'MOBILE_BLIND_INDEX'
                 ORDER BY key_version
                 FOR UPDATE
                """, (resultSet, row) -> new KeyRow(
                resultSet.getLong("key_version"),
                KeyState.valueOf(resultSet.getString("key_state"))));
        List<ExpectedKey> writable = locked.stream()
                .filter(key -> key.state() == KeyState.ACTIVE || key.state() == KeyState.RETIRING)
                .map(key -> new ExpectedKey(key.keyVersion(), key.state()))
                .toList();
        return writable.equals(requested)
                && writable.stream().filter(key -> key.state() == KeyState.ACTIVE).count() == 1;
    }

    private record KeyRow(long keyVersion, KeyState state) {
    }
}
