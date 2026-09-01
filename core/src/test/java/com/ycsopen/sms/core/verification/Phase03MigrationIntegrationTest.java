package com.ycsopen.sms.core.verification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real-MySQL proof for the Phase-3-owned V1200 expand schema. */
@SpringBootTest(
        classes = Phase03MigrationIntegrationTest.MigrationVerificationApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("phase03-integration")
@EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
class Phase03MigrationIntegrationTest {
    private static final String V1_SHA256 =
            "fcea0ad774f8b0e245484c435ce951e0b4337b8ef837d959e2a7b184058e08a9";
    private static final Set<String> PHASE03_TABLES = Set.of(
            "ycs_crypto_key_references",
            "ycs_crypto_migration_targets",
            "ycs_crypto_blind_indexes",
            "ycs_crypto_manifest_pair_admission",
            "ycs_crypto_migration_runs",
            "ycs_crypto_migration_checkpoints",
            "ycs_crypto_migration_events",
            "ycs_crypto_registration_sessions",
            "ycs_crypto_registration_upload_attempts",
            "ycs_crypto_protected_objects",
            "ycs_crypto_object_capabilities",
            "ycs_crypto_object_operations"
    );
    private static final List<String> OBJECT_PURPOSES = List.of(
            "LEGAL_REPRESENTATIVE_ID_FRONT",
            "LEGAL_REPRESENTATIVE_ID_BACK",
            "BUSINESS_LICENSE",
            "SHORT_LINK_PROOF",
            "TRADEMARK_PROOF"
    );
    private static Phase01ServiceSession mysql;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        mysql = Phase01ServiceHarness.startMySql();
        registry.add("spring.datasource.url", mysql::jdbcUrl);
        registry.add("spring.datasource.username", mysql::username);
        registry.add("spring.datasource.password", mysql::password);
        // V1 contains the literal template marker ${var} in a SQL comment.
        registry.add("spring.flyway.placeholder-replacement", () -> "false");
    }

    @AfterAll
    static void stopMySql() {
        if (mysql != null) {
            mysql.close();
        }
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    DataSource dataSource;

    @Autowired
    Flyway flyway;

    @Test
    void appliesV1200WithoutChangingV1() throws Exception {
        assertFlywayAndOwnerBoundary();
        assertPhysicalSchemaContract();
        assertLegacyMetadataLockDoesNotBlockPhaseOwnedState();
        insertPurposeSeparatedKeyReferences();
        assertExactPurposeAndVersionForeignKeys();
        assertAtomicWrapReservationCeiling();
        assertAtomicManifestPairAdmission();
        assertConcurrentRegistrationAttemptCeilings();
    }

    private void assertFlywayAndOwnerBoundary() throws Exception {
        Path migrationDirectory = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/src/main/resources/db/migration");
        Path v1 = migrationDirectory.resolve("V1__init_schema.sql");
        Path v1200 = migrationDirectory.resolve("V1200__create_crypto_storage_metadata.sql");
        assertThat(sha256(v1)).isEqualTo(V1_SHA256);
        assertThat(mysql.migrationSha256()).isEqualTo(V1_SHA256);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank",
                String.class)).containsExactly("1", "1200");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT checksum FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class)).isNotNull();

        String sql = Files.readString(v1200);
        Matcher createdTables = Pattern.compile("(?im)^CREATE TABLE ([a-z0-9_]+)\\s*\\(").matcher(sql);
        while (createdTables.find()) {
            assertThat(createdTables.group(1)).startsWith("ycs_crypto_");
        }
        for (String legacyTable : List.of(
                "users", "tenants", "mobile_portability", "blacklist_entries",
                "third_party_risk_check_logs", "message_tasks", "bulk_sending_items",
                "uplink_records", "unsubscribe_records")) {
            Pattern destructiveLegacyStatement = Pattern.compile(
                    "(?is)\\b(?:ALTER|DROP|RENAME|TRUNCATE|UPDATE|DELETE)\\s+"
                            + "(?:TABLE\\s+)?`?" + Pattern.quote(legacyTable) + "`?\\b");
            assertThat(sql).doesNotContainPattern(destructiveLegacyStatement);
        }
    }

    private void assertPhysicalSchemaContract() {
        assertThat(Set.copyOf(jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name LIKE 'ycs_crypto_%'",
                String.class))).containsExactlyInAnyOrderElementsOf(PHASE03_TABLES);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.triggers "
                        + "WHERE trigger_schema = DATABASE() AND trigger_name LIKE 'ycs_crypto_%'",
                Integer.class)).isZero();

        List<String> blindIndexColumns = jdbcTemplate.queryForList(
                "SELECT CONCAT(index_name, ':', GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')) "
                        + "FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'ycs_crypto_blind_indexes' "
                        + "GROUP BY index_name",
                String.class);
        assertThat(blindIndexColumns).contains(
                "uk_ycs_crypto_blind_target_version:target_type,legacy_row_id,field_id,key_version",
                "idx_ycs_crypto_blind_lookup:target_type,field_id,index_status,key_version,index_value"
        );
        assertThat(jdbcTemplate.queryForMap(
                "SELECT character_maximum_length, character_set_name, collation_name "
                        + "FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name = 'ycs_crypto_blind_indexes' AND column_name = 'index_value'"))
                .containsEntry("character_maximum_length", 53L)
                .containsEntry("character_set_name", "ascii")
                .containsEntry("collation_name", "ascii_bin");

        assertThat(jdbcTemplate.queryForList(
                "SELECT CONCAT(legacy_table_name, '.', legacy_column_name, ':', target_disposition) "
                        + "FROM ycs_crypto_migration_targets ORDER BY legacy_table_name, legacy_column_name",
                String.class)).containsExactlyInAnyOrder(
                "blacklist_entries.mobile_hash:BLIND_INDEX",
                "bulk_sending_items.mobile_encrypted:PROTECTED_NO_INDEX",
                "message_tasks.mobile_hash:BLIND_INDEX",
                "mobile_portability.mobile_hash:BLIND_INDEX",
                "third_party_risk_check_logs.mobile_hash:MIGRATABLE_SCHEMA_ONLY",
                "unsubscribe_records.mobile_hash:BLIND_INDEX",
                "uplink_records.mobile_encrypted:PROTECTED_NO_INDEX"
        );
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
                        + "AND table_name LIKE 'ycs_crypto_%' "
                        + "AND column_name REGEXP '(^|_)(plaintext|raw_token|raw_url|pin|dek|hmac_key|kek_material)(_|$)'",
                Integer.class)).isZero();
    }

    private void insertPurposeSeparatedKeyReferences() {
        insertKey("FIELD_ENCRYPTION_KEK", 1, "ROTATION_REQUIRED", 1_048_575, true);
        insertKey("MOBILE_BLIND_INDEX", 1, "ACTIVE", 0, false);
        insertKey("OBJECT_CAPABILITY_DIGEST", 1, "ACTIVE", 0, false);
        insertKey("REGISTRATION_UPLOAD_DIGEST", 2, "ACTIVE", 0, false);
        insertKey("SNAPSHOT_RECOVERY", 1, "ACTIVE", 0, false);
    }

    private void assertLegacyMetadataLockDoesNotBlockPhaseOwnedState() throws Exception {
        try (Connection legacyLock = dataSource.getConnection();
             Connection phaseWriter = dataSource.getConnection();
             var lockStatement = legacyLock.createStatement();
             var phaseStatement = phaseWriter.createStatement()) {
            lockStatement.execute("LOCK TABLES message_tasks READ");
            try {
                phaseStatement.execute("SET SESSION lock_wait_timeout = 1");
                assertThat(phaseStatement.executeUpdate(
                        "UPDATE ycs_crypto_migration_targets "
                                + "SET optimistic_version = optimistic_version + 1 "
                                + "WHERE target_type = 'MESSAGE_TASK' AND optimistic_version = 0"))
                        .isOne();
            } finally {
                lockStatement.execute("UNLOCK TABLES");
            }
        }
    }

    private void insertKey(String purpose, long version, String state, long wrapCount, boolean rotationRequired) {
        jdbcTemplate.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose, key_version, provider_id, provider_key_reference, key_state, "
                        + "wrap_operation_count, rotation_required) VALUES (?, ?, 'pkcs11', ?, ?, ?, ?)",
                purpose, version, "phase03/" + purpose.toLowerCase() + "/" + version,
                state, wrapCount, rotationRequired);
    }

    private void assertExactPurposeAndVersionForeignKeys() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ycs_crypto_registration_sessions "
                        + "(registration_session_id, tenant_draft_id, session_state, "
                        + "upload_digest_key_version, upload_credential_digest, expires_at) "
                        + "VALUES ('wrong-purpose', 'draft-wrong-purpose', 'OPEN', 1, ?, "
                        + "DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR))",
                digest(10)))
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ycs_crypto_blind_indexes "
                        + "(target_type, legacy_row_id, field_id, key_version, index_value, "
                        + "index_status, original_row_digest) "
                        + "VALUES ('MESSAGE_TASK', 1, 'mobile_hash', 2, ?, 'ACTIVE', ?)",
                "a".repeat(53), digest(11)))
                .isInstanceOf(DataAccessException.class);

        jdbcTemplate.update("INSERT INTO ycs_crypto_blind_indexes "
                        + "(target_type, legacy_row_id, field_id, key_version, index_value, "
                        + "index_status, original_row_digest) "
                        + "VALUES ('MESSAGE_TASK', 1, 'mobile_hash', 1, ?, 'ACTIVE', ?)",
                "a".repeat(53), digest(12));
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO ycs_crypto_blind_indexes "
                        + "(target_type, legacy_row_id, field_id, key_version, index_value, "
                        + "index_status, original_row_digest) "
                        + "VALUES ('MESSAGE_TASK', 1, 'mobile_hash', 1, ?, 'RETIRING', ?)",
                "b".repeat(53), digest(13)))
                .isInstanceOf(DataAccessException.class);
    }

    private void assertAtomicWrapReservationCeiling() throws Exception {
        String reserve = "UPDATE ycs_crypto_key_references SET "
                + "wrap_operation_count = wrap_operation_count + 1, rotation_required = TRUE, "
                + "optimistic_version = optimistic_version + 1 "
                + "WHERE purpose = 'FIELD_ENCRYPTION_KEK' AND key_version = 1 "
                + "AND wrap_operation_count < 1048576 AND optimistic_version = 0";
        assertThat(runConcurrently(() -> jdbcTemplate.update(reserve), () -> jdbcTemplate.update(reserve)))
                .containsExactlyInAnyOrder(0, 1);
        String keyPredicate = " FROM ycs_crypto_key_references "
                + "WHERE purpose = 'FIELD_ENCRYPTION_KEK' AND key_version = 1";
        assertThat(jdbcTemplate.queryForObject(
                "SELECT wrap_operation_count" + keyPredicate, Long.class)).isEqualTo(1_048_576L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT rotation_required" + keyPredicate, Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT optimistic_version" + keyPredicate, Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.update(reserve)).isZero();
        assertThat(jdbcTemplate.update(
                "UPDATE ycs_crypto_key_references SET wrap_operation_count = wrap_operation_count - 1 "
                        + "WHERE purpose = 'FIELD_ENCRYPTION_KEK' AND key_version = 1 "
                        + "AND optimistic_version = 0 AND wrap_operation_count < 1048576")).isZero();
    }

    private void assertAtomicManifestPairAdmission() throws Exception {
        jdbcTemplate.update("INSERT INTO ycs_crypto_manifest_pair_admission "
                        + "(singleton_id, migration_set_id, canonical_subject_digest, global_sequence, "
                        + "signer_key_version, signer_fingerprint, writer_digest, snapshot_digest, pair_digest) "
                        + "VALUES (1, 'migration-set-1', ?, 1, 'signer-v1', ?, ?, ?, ?)",
                digest(20), digest(21), digest(22), digest(23), digest(24));

        Callable<Integer> admitSecond = () -> updateManifestPair(
                2, "migration-set-2", digest(30), digest(31), digest(32), digest(33));
        Callable<Integer> admitSpliced = () -> updateManifestPair(
                2, "migration-set-spliced", digest(40), digest(41), digest(42), digest(43));
        assertThat(runConcurrently(admitSecond, admitSpliced)).containsExactlyInAnyOrder(0, 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT global_sequence FROM ycs_crypto_manifest_pair_admission WHERE singleton_id = 1",
                Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.update(
                "UPDATE ycs_crypto_manifest_pair_admission SET global_sequence = global_sequence "
                        + "WHERE singleton_id = 1 AND global_sequence = 2 "
                        + "AND canonical_subject_digest IS NOT NULL AND writer_digest IS NOT NULL "
                        + "AND snapshot_digest IS NOT NULL AND pair_digest IS NOT NULL")).isOne();
        assertThat(jdbcTemplate.update(
                "UPDATE ycs_crypto_manifest_pair_admission SET pair_digest = ?, "
                        + "optimistic_version = optimistic_version + 1 "
                        + "WHERE singleton_id = 1 AND optimistic_version = 0 AND global_sequence < 2",
                digest(50))).isZero();
        assertThat(jdbcTemplate.update(
                "UPDATE ycs_crypto_manifest_pair_admission SET global_sequence = 1, "
                        + "optimistic_version = optimistic_version + 1 "
                        + "WHERE singleton_id = 1 AND optimistic_version = 0 AND global_sequence < 1"))
                .isZero();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO ycs_crypto_manifest_pair_admission "
                        + "(singleton_id, migration_set_id, canonical_subject_digest, global_sequence, "
                        + "signer_key_version, signer_fingerprint, writer_digest, snapshot_digest, pair_digest) "
                        + "VALUES (2, 'half-pair', ?, 3, 'signer-v1', ?, ?, NULL, ?)",
                digest(51), digest(52), digest(53), digest(54)))
                .isInstanceOf(DataAccessException.class);
    }

    private int updateManifestPair(long sequence, String migrationSet, byte[] subject,
                                   byte[] writer, byte[] snapshot, byte[] pair) {
        return jdbcTemplate.update("UPDATE ycs_crypto_manifest_pair_admission SET "
                        + "migration_set_id = ?, canonical_subject_digest = ?, global_sequence = ?, "
                        + "writer_digest = ?, snapshot_digest = ?, pair_digest = ?, "
                        + "optimistic_version = optimistic_version + 1 "
                        + "WHERE singleton_id = 1 AND optimistic_version = 0 AND global_sequence < ?",
                migrationSet, subject, sequence, writer, snapshot, pair, sequence);
    }

    private void assertConcurrentRegistrationAttemptCeilings() throws Exception {
        String sessionId = "11111111-1111-1111-1111-111111111111";
        jdbcTemplate.update("INSERT INTO ycs_crypto_registration_sessions "
                        + "(registration_session_id, tenant_draft_id, session_state, "
                        + "upload_digest_key_version, upload_credential_digest, expires_at) "
                        + "VALUES (?, '22222222-2222-2222-2222-222222222222', 'OPEN', 2, ?, "
                        + "DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR))",
                sessionId, digest(60));
        for (String purpose : OBJECT_PURPOSES) {
            jdbcTemplate.update("INSERT INTO ycs_crypto_registration_upload_attempts "
                            + "(registration_session_id, object_purpose) VALUES (?, ?)",
                    sessionId, purpose);
        }

        List<Callable<Integer>> fourConcurrent = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            fourConcurrent.add(() -> reserveUploadAttempt(sessionId, OBJECT_PURPOSES.getFirst()));
        }
        assertThat(runConcurrently(fourConcurrent)).containsExactlyInAnyOrder(0, 1, 1, 1);
        for (String purpose : OBJECT_PURPOSES.subList(1, OBJECT_PURPOSES.size())) {
            assertThat(reserveUploadAttempt(sessionId, purpose)).isOne();
            assertThat(reserveUploadAttempt(sessionId, purpose)).isOne();
            assertThat(reserveUploadAttempt(sessionId, purpose)).isOne();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT admitted_attempt_count FROM ycs_crypto_registration_sessions "
                        + "WHERE registration_session_id = ?", Integer.class, sessionId)).isEqualTo(15);
        assertThat(reserveUploadAttempt(sessionId, OBJECT_PURPOSES.get(1))).isZero();

        jdbcTemplate.update("UPDATE ycs_crypto_registration_sessions SET session_state = 'CLOSED', "
                        + "optimistic_version = optimistic_version + 1 WHERE registration_session_id = ?",
                sessionId);
        assertThat(reserveUploadAttempt(sessionId, OBJECT_PURPOSES.get(2))).isZero();
        assertThat(jdbcTemplate.update(
                "UPDATE ycs_crypto_registration_sessions SET session_state = 'OPEN', "
                        + "optimistic_version = optimistic_version + 1 "
                        + "WHERE registration_session_id = ? AND session_state = 'OPEN'",
                sessionId)).isZero();
        assertThat(jdbcTemplate.update(
                "UPDATE ycs_crypto_registration_upload_attempts "
                        + "SET admitted_attempt_count = admitted_attempt_count - 1, "
                        + "optimistic_version = optimistic_version + 1 "
                        + "WHERE registration_session_id = ? AND object_purpose = ? "
                        + "AND admitted_attempt_count = 0",
                sessionId, OBJECT_PURPOSES.getFirst())).isZero();
    }

    private int reserveUploadAttempt(String sessionId, String purpose) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var reserveSession = connection.prepareStatement(
                    "UPDATE ycs_crypto_registration_sessions SET "
                            + "admitted_attempt_count = admitted_attempt_count + 1, "
                            + "optimistic_version = optimistic_version + 1 "
                            + "WHERE registration_session_id = ? AND session_state = 'OPEN' "
                            + "AND expires_at > CURRENT_TIMESTAMP(6) AND admitted_attempt_count < 15");
                 var reservePurpose = connection.prepareStatement(
                         "UPDATE ycs_crypto_registration_upload_attempts SET "
                                 + "admitted_attempt_count = admitted_attempt_count + 1, "
                                 + "optimistic_version = optimistic_version + 1 "
                                 + "WHERE registration_session_id = ? AND object_purpose = ? "
                                 + "AND admitted_attempt_count < 3")) {
                reserveSession.setString(1, sessionId);
                if (reserveSession.executeUpdate() != 1) {
                    connection.rollback();
                    return 0;
                }
                reservePurpose.setString(1, sessionId);
                reservePurpose.setString(2, purpose);
                if (reservePurpose.executeUpdate() != 1) {
                    connection.rollback();
                    return 0;
                }
                connection.commit();
                return 1;
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    @SafeVarargs
    private static <T> List<T> runConcurrently(Callable<T>... tasks) throws Exception {
        return runConcurrently(List.of(tasks));
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return task.call();
                    }))
                    .toList();
            ready.await();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private static byte[] digest(int marker) {
        byte[] digest = new byte[32];
        digest[0] = (byte) marker;
        digest[31] = (byte) (marker ^ 0x5a);
        return digest;
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    static class MigrationVerificationApplication {
    }
}
