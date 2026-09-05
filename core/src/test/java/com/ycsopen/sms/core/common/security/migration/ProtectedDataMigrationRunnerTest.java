package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflight.CheckpointState;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.BlindIndexEntry;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.Checkpoint;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.Lease;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.LegacyRow;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.Outcome;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.RunState;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.RunStatus;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.StoredValueKind;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.FailureCode;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.BatchResult;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.MigrationException;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner.MigrationRequest;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskRowBinding;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProtectedDataMigrationRunnerTest {

    private static final String PAIR = "a".repeat(64);
    private static final String OWNER = "b".repeat(64);
    private static final String RUN_1 = "00000000-0000-4000-8000-000000000001";
    private static final String RUN_2 = "00000000-0000-4000-8000-000000000002";
    private static final String RUN_3 = "00000000-0000-4000-8000-000000000003";
    private static final String RUN_4 = "00000000-0000-4000-8000-000000000004";
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void rowEnvelopeOutcomeAndCheckpointCommitTogetherAndRestartDoesNotReencrypt() throws Exception {
        InMemoryRepository repository = new InMemoryRepository(PAIR,
                row(1, "13800138000".getBytes(StandardCharsets.US_ASCII)));
        TestKeyPort keyPort = new TestKeyPort();
        ProtectedDataMigrationRunner runner = runner(repository, keyPort, hmacPort());

        var first = runner.migrateBatch(request(RUN_1));

        assertThat(first.scanned()).isOne();
        assertThat(repository.value(1)).startsWith(
                (byte) 'Y', (byte) 'C', (byte) 'S', (byte) 'E');
        assertThat(repository.outcomes).containsExactly(Outcome.SUCCEEDED);
        assertThat(repository.checkpoint.lastRowId()).isEqualTo(1L);
        assertThat(repository.checkpoint.migrated()).isOne();
        assertThat(repository.checkpoint.verified()).isOne();
        assertThat(keyPort.wrapCalls).isOne();

        // A fresh admitted run must authenticate the existing row/context without re-encryption.
        repository.checkpoint = Checkpoint.discovered();
        var restart = runner.migrateBatch(request(RUN_2));
        assertThat(restart.skipped()).isOne();
        assertThat(keyPort.wrapCalls).isOne();
        assertThat(repository.value(1)).containsExactly(repository.lastCommittedValue);
    }

    @Test
    void integrityFaultRollsBackRowOutcomeAndCheckpoint() throws Exception {
        InMemoryRepository repository = new InMemoryRepository(PAIR,
                row(1, "13800138000".getBytes(StandardCharsets.US_ASCII)));
        byte[] original = repository.value(1);
        AtomicInteger calls = new AtomicInteger();
        ProtectedDataMigrationRunner.IntegrityFingerprintPort mismatch = value -> {
            byte[] digest = sha256(value);
            if (calls.incrementAndGet() == 2) {
                digest[0] ^= 1;
            }
            return digest;
        };
        ProtectedDataMigrationRunner runner = runner(repository, new TestKeyPort(), mismatch);

        assertThatThrownBy(() -> runner.migrateBatch(request(RUN_3)))
                .isInstanceOfSatisfying(MigrationException.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(FailureCode.INTEGRITY_OR_BINDING_INVALID))
                .hasMessage(ProtectedDataMigrationRunner.SANITIZED_FAILURE);
        assertThat(repository.value(1)).containsExactly(original);
        assertThat(repository.outcomes).isEmpty();
        assertThat(repository.checkpoint).isEqualTo(Checkpoint.discovered());
    }

    @Test
    void concurrentOriginalCellChangeAndAmbiguousValueNeverAdvance() throws Exception {
        InMemoryRepository concurrent = new InMemoryRepository(PAIR,
                row(1, "13800138000".getBytes(StandardCharsets.US_ASCII)));
        concurrent.rejectOptimisticUpdate = true;
        ProtectedDataMigrationRunner runner = runner(concurrent, new TestKeyPort(), hmacPort());

        assertFailure(runner, RUN_3, FailureCode.CONCURRENT_ROW_CHANGE);
        assertThat(concurrent.outcomes).isEmpty();
        assertThat(concurrent.checkpoint).isEqualTo(Checkpoint.discovered());

        InMemoryRepository ambiguous = new InMemoryRepository(PAIR,
                row(1, new byte[]{(byte) 0xc3, 0x28}));
        assertFailure(runner(ambiguous, new TestKeyPort(), hmacPort()),
                RUN_4, FailureCode.LEGACY_CLASSIFICATION_REJECTED);
        assertThat(ambiguous.outcomes).isEmpty();
        assertThat(ambiguous.checkpoint).isEqualTo(Checkpoint.discovered());
    }

    @Test
    void currentMessageLocatorIsValidatedAndSkippedWhileHistoricalDigestMigrates() throws Exception {
        String locator = MessageTaskRowBinding.CURRENT_LOCATOR_PREFIX + "A".repeat(43);
        InMemoryRepository repository = new InMemoryRepository(PAIR,
                new LegacyRow(1, 1L, "1", "tenant:17",
                        locator.getBytes(StandardCharsets.US_ASCII),
                        sha256(locator.getBytes(StandardCharsets.US_ASCII)),
                        StoredValueKind.CURRENT_MESSAGE_LOCATOR),
                new LegacyRow(2, 2L, "2", "tenant:17",
                        "a".repeat(64).getBytes(StandardCharsets.US_ASCII),
                        sha256("a".repeat(64).getBytes(StandardCharsets.US_ASCII))));
        ProtectedDataMigrationRunner runner = runner(
                repository, new TestKeyPort(), hmacPort());

        BatchResult result = runner.migrateBatch(request(
                RUN_1, "message_tasks.mobile_hash"));

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.migrated()).isEqualTo(2);
        assertThat(result.verified()).isEqualTo(2);
        assertThat(result.skipped()).isOne();
        assertThat(repository.outcomes).containsExactly(Outcome.SKIPPED, Outcome.SUCCEEDED);

        repository.currentMessageBindingValid = false;
        repository.checkpoint = Checkpoint.discovered();
        repository.outcomes.clear();
        assertFailure(runner, RUN_2, "message_tasks.mobile_hash",
                FailureCode.INTEGRITY_OR_BINDING_INVALID);
        assertThat(repository.outcomes).isEmpty();
    }

    @Test
    void jdbcRepositoryDistinguishesAndValidatesCurrentMessageBinding() throws Exception {
        JdbcTemplate jdbc = messageFixture();
        String locator = MessageTaskRowBinding.CURRENT_LOCATOR_PREFIX + "B".repeat(43);
        String legacy = "c".repeat(64);
        byte[] envelope = "YCSE-current-envelope".getBytes(StandardCharsets.US_ASCII);
        jdbc.update("INSERT INTO message_tasks "
                        + "(id, tenant_id, message_id, mobile_hash, mobile_encrypted) VALUES "
                        + "(1, 17, 'MSG_1700000000000_ABC12345', ?, ?), "
                        + "(2, 17, 'MSG_1700000000001_ABC12345', ?, ?)",
                locator, envelope, legacy, envelope);
        byte[] binding = MessageTaskRowBinding.originalRowDigest(
                17, 1, "MSG_1700000000000_ABC12345", locator, envelope);
        jdbc.update("INSERT INTO ycs_crypto_blind_indexes "
                        + "(target_type, legacy_row_id, field_id, key_purpose, key_version, "
                        + "index_value, index_status, original_row_digest) VALUES "
                        + "('MESSAGE_TASK', 1, 'mobile', 'MOBILE_BLIND_INDEX', 1, ?, 'ACTIVE', ?)",
                "a".repeat(53), binding);
        jdbc.update("INSERT INTO ycs_crypto_blind_indexes "
                        + "(target_type, legacy_row_id, field_id, key_purpose, key_version, "
                        + "index_value, index_status, original_row_digest) VALUES "
                        + "('MESSAGE_TASK', 2, 'mobile', 'MOBILE_BLIND_INDEX', 1, ?, 'ACTIVE', ?)",
                "b".repeat(53), sha256(legacy.getBytes(StandardCharsets.US_ASCII)));
        MigrationStateRepository.Jdbc repository = new MigrationStateRepository.Jdbc(
                jdbc, new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource())));
        ProtectedDataTarget target = resolvedManifest()
                .requireTarget("message_tasks.mobile_hash");

        List<LegacyRow> rows = repository.transaction(
                transaction -> transaction.readBatch(target, 0, 10));

        assertThat(rows).extracting(LegacyRow::storedValueKind)
                .containsExactly(StoredValueKind.CURRENT_MESSAGE_LOCATOR,
                        StoredValueKind.LEGACY_CANDIDATE);
        boolean valid = repository.transaction(transaction ->
                transaction.currentMessageBindingMatches(rows.getFirst(), "mobile"));
        assertThat(valid).isTrue();
        boolean complete = repository.transaction(transaction ->
                transaction.integrityAndBindingComplete(target, "MESSAGE_TASK"));
        assertThat(complete).isTrue();

        jdbc.update("UPDATE ycs_crypto_blind_indexes SET original_row_digest = ? "
                + "WHERE legacy_row_id = 1", new byte[32]);
        boolean tampered = repository.transaction(transaction ->
                transaction.currentMessageBindingMatches(rows.getFirst(), "mobile"));
        assertThat(tampered).isFalse();
    }

    @Test
    void portabilityRowsWithSharedRawHashPrefixReceiveIndependentRandomBindings() throws Exception {
        JdbcTemplate jdbc = portabilityFixture();
        String prefix = "0123456789abcdef".repeat(3) + "0123456789abcde";
        String first = prefix + "0";
        String second = prefix + "1";
        assertThat(first).hasSize(64);
        jdbc.update("INSERT INTO mobile_portability (mobile_hash) VALUES (?), (?)", first, second);
        AtomicLong ids = new AtomicLong(40);
        MigrationStateRepository.Jdbc repository = new MigrationStateRepository.Jdbc(
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())),
                ids::incrementAndGet,
                new SecureRandom());
        ProtectedDataTarget target = resolvedManifest()
                .requireTarget("mobile_portability.mobile_hash");

        List<LegacyRow> rows = repository.transaction(transaction ->
                transaction.readBatch(target, 0, 10));

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(LegacyRow::bindingRowId).containsExactly(41L, 42L);
        assertThat(rows).extracting(LegacyRow::checkpointCursor).containsOnlyNulls();
        assertThat(rows).extracting(LegacyRow::resourceIdentity)
                .containsExactly(first, second);
        assertThat(rows).allSatisfy(row -> assertThat(
                Long.toUnsignedString(row.bindingRowId(), 16)).isNotEqualTo(first.substring(0, 15)));
    }

    @Test
    void portabilityRandomBindingCollisionFailsClosedAfterBoundedRetries() throws Exception {
        JdbcTemplate jdbc = portabilityFixture();
        jdbc.update("INSERT INTO mobile_portability (mobile_hash) VALUES (?)", "1".repeat(64));
        jdbc.update("INSERT INTO ycs_crypto_blind_indexes "
                        + "(target_type, legacy_row_id, field_id, key_version, index_value, "
                        + "index_status, original_row_digest) VALUES "
                        + "('MOBILE_PORTABILITY', 7, 'mobile', 1, ?, 'ACTIVE', ?)",
                "a".repeat(53), new byte[32]);
        AtomicInteger attempts = new AtomicInteger();
        MigrationStateRepository.Jdbc repository = new MigrationStateRepository.Jdbc(
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())),
                () -> {
                    attempts.incrementAndGet();
                    return 7;
                },
                new SecureRandom());
        ProtectedDataTarget target = resolvedManifest()
                .requireTarget("mobile_portability.mobile_hash");

        assertThatThrownBy(() -> repository.transaction(transaction ->
                transaction.readBatch(target, 0, 10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("protected-data migration state rejected");
        assertThat(attempts).hasValue(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ycs_crypto_blind_indexes", Long.class))
                .isOne();
    }

    private static void assertFailure(
            ProtectedDataMigrationRunner runner, String runId, FailureCode code) {
        assertFailure(runner, runId, "bulk_sending_items.mobile_encrypted", code);
    }

    private static void assertFailure(
            ProtectedDataMigrationRunner runner, String runId, String targetId, FailureCode code) {
        assertThatThrownBy(() -> runner.migrateBatch(request(runId, targetId)))
                .isInstanceOfSatisfying(MigrationException.class,
                        failure -> assertThat(failure.code()).isEqualTo(code));
    }

    private static ProtectedDataMigrationRunner runner(
            MigrationStateRepository repository,
            TestKeyPort keyPort,
            ProtectedDataMigrationRunner.IntegrityFingerprintPort fingerprint) throws Exception {
        EnvelopeCodec envelopeCodec = new EnvelopeCodec();
        return new ProtectedDataMigrationRunner(
                resolvedManifest(), repository, new LegacyValueClassifier(envelopeCodec),
                new ProtectedFieldCodec(envelopeCodec, keyPort, new SecureRandom(), "test-kek-v1"),
                fingerprint,
                (historical, targetType, field, scope) -> List.of(
                        new BlindIndexEntry(1, "a".repeat(53), "ACTIVE")),
                CLOCK);
    }

    private static MigrationRequest request(String runId) {
        return request(runId, "bulk_sending_items.mobile_encrypted");
    }

    private static MigrationRequest request(String runId, String targetId) {
        return new MigrationRequest(runId, targetId,
                PAIR, OWNER, 10, ProtectedDataMigrationRunner.DEFAULT_LEASE_DURATION);
    }

    private static LegacyRow row(long id, byte[] value) {
        return new LegacyRow(id, id, Long.toString(id), "global", value, sha256(value));
    }

    private static ProtectedDataMigrationRunner.IntegrityFingerprintPort hmacPort() {
        byte[] key = "phase03-test-integrity-key-32byt".getBytes(StandardCharsets.US_ASCII);
        return value -> {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(key, "HmacSHA256"));
                return mac.doFinal(value);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("test HMAC failed");
            }
        };
    }

    private static JdbcTemplate portabilityFixture() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:phase03-portability-" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS SHA2 FOR '"
                + ProtectedDataMigrationRunnerTest.class.getName() + ".sha2'");
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UNHEX FOR '"
                + ProtectedDataMigrationRunnerTest.class.getName() + ".unhex'");
        jdbc.execute("CREATE TABLE mobile_portability (mobile_hash CHAR(64) PRIMARY KEY)");
        jdbc.execute("CREATE TABLE ycs_crypto_blind_indexes ("
                + "target_type VARCHAR(64) NOT NULL, legacy_row_id BIGINT NOT NULL, "
                + "field_id VARCHAR(64) NOT NULL, key_version BIGINT NOT NULL, "
                + "index_value CHAR(53) NOT NULL, index_status VARCHAR(16) NOT NULL, "
                + "original_row_digest BINARY(32) NOT NULL)");
        jdbc.execute("CREATE TABLE ycs_crypto_key_references ("
                + "purpose VARCHAR(48) NOT NULL, key_version BIGINT NOT NULL, "
                + "key_state VARCHAR(24) NOT NULL)");
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                + "(purpose, key_version, key_state) VALUES ('MOBILE_BLIND_INDEX', 1, 'ACTIVE')");
        return jdbc;
    }

    private static JdbcTemplate messageFixture() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:phase03-message-" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS SHA2 FOR '"
                + ProtectedDataMigrationRunnerTest.class.getName() + ".sha2'");
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UNHEX FOR '"
                + ProtectedDataMigrationRunnerTest.class.getName() + ".unhex'");
        jdbc.execute("CREATE TABLE message_tasks (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, "
                + "message_id VARCHAR(64) NOT NULL, mobile_hash CHAR(64) NOT NULL, "
                + "mobile_encrypted VARBINARY(255) NOT NULL)");
        jdbc.execute("CREATE TABLE ycs_crypto_blind_indexes ("
                + "target_type VARCHAR(64) NOT NULL, legacy_row_id BIGINT NOT NULL, "
                + "field_id VARCHAR(64) NOT NULL, key_purpose VARCHAR(48) NOT NULL, "
                + "key_version BIGINT NOT NULL, index_value VARCHAR(53) NOT NULL, "
                + "index_status VARCHAR(16) NOT NULL, original_row_digest BINARY(32) NOT NULL)");
        jdbc.execute("CREATE TABLE ycs_crypto_key_references ("
                + "purpose VARCHAR(48) NOT NULL, key_version BIGINT NOT NULL, "
                + "key_state VARCHAR(24) NOT NULL)");
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                + "(purpose, key_version, key_state) VALUES ('MOBILE_BLIND_INDEX', 1, 'ACTIVE')");
        return jdbc;
    }

    public static String sha2(byte[] value, int bits) {
        if (bits != 256) {
            throw new IllegalArgumentException("test alias accepts SHA-256 only");
        }
        return java.util.HexFormat.of().formatHex(sha256(value));
    }

    public static byte[] unhex(String value) {
        return java.util.HexFormat.of().parseHex(value);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static ProtectedDataManifest resolvedManifest() throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode root = (ObjectNode) json.readTree(Files.readAllBytes(
                Path.of("src/main/resources/security/protected-data-inventory.json")));
        for (JsonNode surface : root.required("source_surfaces")) {
            ObjectNode row = (ObjectNode) surface;
            row.put("obligation_blocking", false);
            if (row.path("disposition").asText().startsWith("BLOCKING_")) {
                row.put("disposition", "ADOPTED_TEST_PROTECTED_BOUNDARY");
            }
        }
        ObjectNode readiness = (ObjectNode) root.required("obligation_readiness");
        readiness.put("status", "READY");
        readiness.set("blocking_surface_ids", json.createArrayNode());
        readiness.put("reason", "All synthetic migration dependencies are resolved.");
        byte[] pretty = json.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
        byte[] canonical = Arrays.copyOf(pretty, pretty.length + 1);
        canonical[canonical.length - 1] = '\n';
        return ProtectedDataManifest.load(new ByteArrayInputStream(canonical),
                ProtectedDataManifest.canonicalDigest(canonical));
    }

    private static final class InMemoryRepository implements MigrationStateRepository {
        private final String pair;
        private final Map<Long, LegacyRow> identities = new HashMap<>();
        private final Map<Long, byte[]> values = new HashMap<>();
        private Checkpoint checkpoint = Checkpoint.discovered();
        private List<Outcome> outcomes = new ArrayList<>();
        private boolean rejectOptimisticUpdate;
        private boolean currentMessageBindingValid = true;
        private byte[] lastCommittedValue;

        private InMemoryRepository(String pair, LegacyRow... rows) {
            this.pair = pair;
            for (LegacyRow row : rows) {
                identities.put(row.bindingRowId(), row);
                values.put(row.bindingRowId(), row.storedValue());
            }
        }

        @Override
        public <T> T transaction(Function<Transaction, T> work) {
            Map<Long, byte[]> beforeValues = copyValues(values);
            Checkpoint beforeCheckpoint = checkpoint;
            List<Outcome> beforeOutcomes = List.copyOf(outcomes);
            try {
                return work.apply(new MemoryTransaction());
            } catch (RuntimeException exception) {
                values.clear();
                values.putAll(beforeValues);
                checkpoint = beforeCheckpoint;
                outcomes = new ArrayList<>(beforeOutcomes);
                throw exception;
            }
        }

        private byte[] value(long id) {
            return values.get(id).clone();
        }

        private final class MemoryTransaction implements Transaction {
            @Override
            public void requireAcceptedPair(String pairDigest) {
                if (!pair.equals(pairDigest)) {
                    throw new IllegalStateException("pair rejected");
                }
            }

            @Override
            public void ensureRun(String runId, String pairDigest, String manifestDigest) {
            }

            @Override
            public Lease claimLease(String runId, String targetType, String ownerDigest, Instant expiresAt) {
                return new Lease(runId, targetType, ownerDigest, expiresAt);
            }

            @Override
            public Checkpoint checkpoint(String runId, String targetType) {
                return checkpoint;
            }

            @Override
            public List<LegacyRow> readBatch(ProtectedDataTarget target, long afterRowId, int batchSize) {
                return identities.values().stream()
                        .filter(row -> row.checkpointCursor() == null || row.checkpointCursor() > afterRowId)
                        .sorted(java.util.Comparator.comparingLong(LegacyRow::bindingRowId))
                        .limit(batchSize)
                        .map(row -> {
                            byte[] value = values.get(row.bindingRowId());
                            return new LegacyRow(row.bindingRowId(), row.checkpointCursor(),
                                    row.resourceIdentity(), row.tenantScope(), value, sha256(value),
                                    row.storedValueKind());
                        }).toList();
            }

            @Override
            public boolean updateProtectedValue(ProtectedDataTarget target, LegacyRow row, byte[] envelope) {
                if (rejectOptimisticUpdate
                        || !MessageDigest.isEqual(sha256(values.get(row.bindingRowId())),
                        row.originalCellDigest())) {
                    return false;
                }
                values.put(row.bindingRowId(), envelope.clone());
                lastCommittedValue = envelope.clone();
                return true;
            }

            @Override
            public boolean upsertBlindIndexes(
                    String targetType, LegacyRow row, String fieldId, List<BlindIndexEntry> indexes) {
                return true;
            }

            @Override
            public boolean blindIndexesMatch(
                    String targetType, LegacyRow row, String fieldId, List<BlindIndexEntry> indexes) {
                return true;
            }

            @Override
            public boolean currentMessageBindingMatches(LegacyRow row, String fieldId) {
                return currentMessageBindingValid
                        && row.storedValueKind() == StoredValueKind.CURRENT_MESSAGE_LOCATOR;
            }

            @Override
            public long remainingLegacyRows(ProtectedDataTarget target) {
                return 0;
            }

            @Override
            public boolean integrityAndBindingComplete(ProtectedDataTarget target, String targetType) {
                return true;
            }

            @Override
            public boolean deployedWritersCompatible(String targetType) {
                return true;
            }

            @Override
            public long scrubLegacyDigests(ProtectedDataTarget target, String targetType) {
                return 0;
            }

            @Override
            public void setLegacyFallback(String targetType, boolean allowed) {
            }

            @Override
            public void setTargetState(String runId, String targetType, CheckpointState state) {
                checkpoint = new Checkpoint(state, checkpoint.lastRowId(),
                        checkpoint.lastOriginalDigest(), checkpoint.scanned(), checkpoint.migrated(),
                        checkpoint.verified(), checkpoint.quarantined(),
                        checkpoint.optimisticVersion() + 1);
            }

            @Override
            public void saveCheckpoint(String runId, String targetType, Checkpoint next) {
                checkpoint = new Checkpoint(next.state(), next.lastRowId(), next.lastOriginalDigest(),
                        next.scanned(), next.migrated(), next.verified(), next.quarantined(),
                        next.optimisticVersion() + 1);
            }

            @Override
            public void recordOutcome(
                    String runId, String targetType, Outcome outcome,
                    byte[] rowLocatorDigest, long affectedCount) {
                outcomes.add(outcome);
            }

            @Override
            public void setRunState(String runId, RunState expected, RunState next, String pairDigest) {
            }

            @Override
            public RunStatus status(String runId) {
                return new RunStatus(runId, RunState.RUNNING, pair,
                        checkpoint.scanned(), checkpoint.migrated(), checkpoint.verified(),
                        checkpoint.quarantined());
            }
        }

        private static Map<Long, byte[]> copyValues(Map<Long, byte[]> source) {
            Map<Long, byte[]> copy = new HashMap<>();
            source.forEach((key, value) -> copy.put(key, value.clone()));
            return copy;
        }
    }

    private static final class TestKeyPort implements KeyProtectionPort {
        private static final byte[] WRAP_DOMAIN =
                "YCSE-WRAP-AAD\0".getBytes(StandardCharsets.US_ASCII);
        private final byte[] key = sha256("test-wrap-key".getBytes(StandardCharsets.US_ASCII));
        private int wrapCalls;

        @Override
        public WrappedDataKey wrap(
                byte[] dataEncryptionKey, byte[] authenticatedHeader,
                ProtectionContext semanticContext) {
            wrapCalls++;
            byte[] nonce = ByteBuffer.allocate(12).putInt(0x57525031).putLong(wrapCalls).array();
            return new WrappedDataKey("test-kek-v1", nonce,
                    aesGcm(true, nonce, wrapAad(authenticatedHeader, semanticContext), dataEncryptionKey));
        }

        @Override
        public byte[] unwrap(
                WrappedDataKey wrappedDataKey, byte[] authenticatedHeader,
                ProtectionContext semanticContext) {
            return aesGcm(false, wrappedDataKey.wrapNonce(),
                    wrapAad(authenticatedHeader, semanticContext), wrappedDataKey.wrappedDek());
        }

        @Override
        public KeyHealth health() {
            return new KeyHealth(KeyHealth.Status.READY);
        }

        private byte[] aesGcm(boolean encrypt, byte[] nonce, byte[] aad, byte[] input) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                        new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                cipher.updateAAD(aad);
                return cipher.doFinal(input);
            } catch (GeneralSecurityException exception) {
                throw new IllegalStateException("test key operation failed");
            }
        }

        private static byte[] wrapAad(byte[] header, ProtectionContext context) {
            byte[] canonical = context.canonicalBytes();
            return ByteBuffer.allocate(WRAP_DOMAIN.length + 4 + header.length + 4 + canonical.length)
                    .put(WRAP_DOMAIN).putInt(header.length).put(header)
                    .putInt(canonical.length).put(canonical).array();
        }
    }
}
