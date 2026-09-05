package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.BlindIndexEntry;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Production migration bridge from legacy lowercase SHA-256 cells to versioned PKCS11 HMAC
 * metadata. It never requires or reconstructs the original mobile number.
 */
public final class Pkcs11MigrationBlindIndexPort
        implements ProtectedDataMigrationRunner.LegacyBlindIndexPort {

    public static final String SANITIZED_FAILURE = "legacy blind-index migration rejected";
    private static final Pattern HISTORICAL_HEX = Pattern.compile("[0-9a-f]{64}");

    private final SunPkcs11KeyAdapter adapter;
    private final JdbcTemplate jdbc;

    public Pkcs11MigrationBlindIndexPort(
            SunPkcs11KeyAdapter adapter, JdbcTemplate jdbc) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<BlindIndexEntry> indexes(
            byte[] historicalSha256Hex,
            String targetType,
            String fieldId,
            String tenantScope) {
        byte[] digest = decodeHistoricalDigest(historicalSha256Hex);
        try {
            BlindIndexPort.Context context = new BlindIndexPort.Context(
                    targetType, fieldId, BlindIndexPort.Purpose.MOBILE_ROUTING, tenantScope);
            BlindIndexPort.OrderedIndexes calculated =
                    adapter.queryIndexesFromHistoricalDigest(digest, context);
            List<KeyState> states = jdbc.query("""
                    SELECT key_version, key_state
                    FROM ycs_crypto_key_references
                    WHERE purpose = 'MOBILE_BLIND_INDEX'
                      AND key_state IN ('ACTIVE', 'RETIRING')
                    ORDER BY key_version
                    """, (resultSet, rowNumber) -> new KeyState(
                    resultSet.getLong(1), resultSet.getString(2)));
            if (states.size() != calculated.values().size()) {
                throw failure();
            }
            for (int index = 0; index < states.size(); index++) {
                if (states.get(index).keyVersion()
                        != calculated.values().get(index).keyVersion()) {
                    throw failure();
                }
            }
            return java.util.stream.IntStream.range(0, states.size())
                    .mapToObj(index -> entry(
                            calculated.values().get(index), states.get(index)))
                    .toList();
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException
                    && SANITIZED_FAILURE.equals(exception.getMessage())) {
                throw exception;
            }
            throw failure();
        } finally {
            Arrays.fill(digest, (byte) 0);
        }
    }

    private static BlindIndexEntry entry(VersionedBlindIndex value, KeyState state) {
        return new BlindIndexEntry(
                state.keyVersion(), value.canonicalValue(), state.status());
    }

    private static byte[] decodeHistoricalDigest(byte[] input) {
        if (input == null) {
            throw failure();
        }
        String encoded = new String(input, java.nio.charset.StandardCharsets.US_ASCII);
        try {
            if (!HISTORICAL_HEX.matcher(encoded).matches()) {
                throw failure();
            }
            return HexFormat.of().parseHex(encoded);
        } catch (IllegalArgumentException exception) {
            throw failure();
        } finally {
            char[] characters = encoded.toCharArray();
            Arrays.fill(characters, '\0');
        }
    }

    private static IllegalStateException failure() {
        return new IllegalStateException(SANITIZED_FAILURE);
    }

    private record KeyState(long keyVersion, String status) {
        private KeyState {
            if (keyVersion < 1 || keyVersion > 255
                    || !("ACTIVE".equals(status) || "RETIRING".equals(status))) {
                throw failure();
            }
        }
    }
}
