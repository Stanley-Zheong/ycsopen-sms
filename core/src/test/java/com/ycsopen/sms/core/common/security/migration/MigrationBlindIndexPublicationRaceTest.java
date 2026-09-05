package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.key.lifecycle.EnvelopeReferenceInventory;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyLifecycleService;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.BlindIndexEntry;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository.LegacyRow;
import com.ycsopen.sms.core.verification.Phase03ServiceHarness;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MigrationBlindIndexPublicationRaceTest {

    private static final LegacyRow ROW = new LegacyRow(
            7_001, 7_001L, "7001", "global", "legacy-mobile".getBytes(),
            sha256("legacy-mobile".getBytes()));
    private static final List<BlindIndexEntry> V1 = List.of(
            new BlindIndexEntry(1, "a".repeat(53), "ACTIVE"));

    @Test
    void retriesTheWholeTransactionAndSanitizesExhaustedTransientFailures() {
        Fixture fixture = h2Fixture();
        AtomicInteger attempts = new AtomicInteger();
        MigrationStateRepository.Jdbc eventually = repository(fixture, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new CannotAcquireLockException("controlled lock conflict");
            }
        });

        boolean published = eventually.transaction(transaction ->
                transaction.upsertBlindIndexes("MESSAGE_TASK", ROW, "mobile", V1));
        assertThat(published).isTrue();
        assertThat(attempts).hasValue(3);
        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_blind_indexes", Long.class)).isOne();
        boolean replayed = eventually.transaction(transaction ->
                transaction.upsertBlindIndexes("MESSAGE_TASK", ROW, "mobile", V1));
        assertThat(replayed).isTrue();
        fixture.jdbc().update("UPDATE ycs_crypto_blind_indexes SET index_value = ?",
                "b".repeat(53));
        assertThatThrownBy(() -> eventually.transaction(transaction ->
                transaction.upsertBlindIndexes("MESSAGE_TASK", ROW, "mobile", V1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("protected-data migration state rejected");

        fixture.jdbc().update("DELETE FROM ycs_crypto_blind_indexes");
        attempts.set(0);
        MigrationStateRepository.Jdbc exhausted = repository(fixture, () -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("must not escape");
        });
        assertThatThrownBy(() -> exhausted.transaction(transaction ->
                transaction.upsertBlindIndexes("MESSAGE_TASK", ROW, "mobile", V1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("protected-data migration state rejected")
                .hasNoCause();
        assertThat(attempts).hasValue(3);
        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_blind_indexes", Long.class)).isZero();

        attempts.set(0);
        MigrationStateRepository.Jdbc nonLockTransient = repository(fixture, () -> {
            attempts.incrementAndGet();
            throw new TransientDataAccessResourceException("not a retryable lock failure");
        });
        assertThatThrownBy(() -> nonLockTransient.transaction(transaction ->
                transaction.upsertBlindIndexes("MESSAGE_TASK", ROW, "mobile", V1)))
                .isInstanceOf(TransientDataAccessResourceException.class)
                .hasMessage("not a retryable lock failure");
        assertThat(attempts).hasValue(1);
    }

    @Test
    @EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
    void realMySqlSerializesActivationFirstAndMigrationPublicationFirst() throws Exception {
        try (Phase03ServiceHarness.ServiceSession mysql = Phase03ServiceHarness.startMySql()) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:mysql://" + mysql.host() + ":" + mysql.port() + "/phase01"
                            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                            + "&allowPublicKeyRetrieval=true&useSSL=false",
                    mysql.username(), mysql.password());
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .placeholderReplacement(false).load().migrate();
            Fixture fixture = fixture(dataSource);
            seedKeys(fixture.jdbc());
            KeyLifecycleService lifecycle = lifecycle(fixture);

            lifecycle.activate(KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 2);
            assertThatThrownBy(() -> repository(fixture, () -> { }).transaction(transaction ->
                    transaction.upsertBlindIndexes("MESSAGE_TASK", ROW, "mobile", V1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("protected-data migration state rejected");
            assertThat(fixture.jdbc().queryForObject(
                    "SELECT COUNT(*) FROM ycs_crypto_blind_indexes", Long.class)).isZero();

            resetKeys(fixture.jdbc());
            CountDownLatch purposeLocked = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            MigrationStateRepository.Jdbc migration = repository(fixture, () -> {
                purposeLocked.countDown();
                await(release);
            });
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                Future<Boolean> publishing = executor.submit(() -> migration.transaction(transaction ->
                        transaction.upsertBlindIndexes("MESSAGE_TASK", ROW, "mobile", V1)));
                assertThat(purposeLocked.await(5, TimeUnit.SECONDS)).isTrue();
                Future<KeyLifecycleService.Activation> activation = executor.submit(() ->
                        lifecycle.activate(KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 2));
                assertThatThrownBy(() -> activation.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                assertThat(publishing.get(5, TimeUnit.SECONDS)).isTrue();
                activation.get(5, TimeUnit.SECONDS);
            }
            assertThat(fixture.jdbc().queryForList(
                    "SELECT CONCAT(key_version, ':', index_status) "
                            + "FROM ycs_crypto_blind_indexes ORDER BY key_version", String.class))
                    .containsExactly("1:RETIRING");
        }
    }

    private static MigrationStateRepository.Jdbc repository(Fixture fixture, Runnable hook) {
        return new MigrationStateRepository.Jdbc(
                fixture.jdbc(), new TransactionTemplate(fixture.manager()),
                new SecureRandom()::nextLong, new SecureRandom(), hook);
    }

    private static KeyLifecycleService lifecycle(Fixture fixture) {
        KeyReferenceRepository keys = new KeyReferenceRepository.Jdbc(
                fixture.jdbc(), new TransactionTemplate(fixture.manager()));
        return new KeyLifecycleService(keys, new EnvelopeReferenceInventory(Set.of(), List.of()));
    }

    private static Fixture h2Fixture() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:migration-mobile-race-" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        Fixture fixture = fixture(dataSource);
        fixture.jdbc().execute("CREATE ALIAS IF NOT EXISTS UNHEX FOR '"
                + MigrationBlindIndexPublicationRaceTest.class.getName() + ".unhex'");
        fixture.jdbc().execute("CREATE ALIAS IF NOT EXISTS HEX FOR '"
                + MigrationBlindIndexPublicationRaceTest.class.getName() + ".hex'");
        fixture.jdbc().execute("CREATE TABLE ycs_crypto_key_references ("
                + "purpose VARCHAR(48) NOT NULL, key_version BIGINT NOT NULL, "
                + "provider_id VARCHAR(32) NOT NULL, provider_key_reference VARCHAR(128) NOT NULL, "
                + "key_state VARCHAR(24) NOT NULL, optimistic_version BIGINT NOT NULL DEFAULT 0, "
                + "PRIMARY KEY (purpose,key_version))");
        fixture.jdbc().execute("CREATE TABLE ycs_crypto_blind_indexes ("
                + "blind_index_id BIGINT AUTO_INCREMENT PRIMARY KEY, target_type VARCHAR(64) NOT NULL, "
                + "legacy_row_id BIGINT NOT NULL, field_id VARCHAR(64) NOT NULL, "
                + "key_purpose VARCHAR(48) NOT NULL, key_version BIGINT NOT NULL, "
                + "index_value VARCHAR(53) NOT NULL, index_status VARCHAR(16) NOT NULL, "
                + "original_row_digest BINARY(32) NOT NULL, row_binding_digest BINARY(32), "
                + "optimistic_version BIGINT NOT NULL DEFAULT 0, "
                + "UNIQUE(target_type,legacy_row_id,field_id,key_version))");
        seedKeys(fixture.jdbc());
        return fixture;
    }

    private static Fixture fixture(DriverManagerDataSource dataSource) {
        return new Fixture(new JdbcTemplate(dataSource), new DataSourceTransactionManager(dataSource));
    }

    private static void seedKeys(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) VALUES "
                        + "('MOBILE_BLIND_INDEX',1,'pkcs11','mobile-index.v1','ACTIVE'),"
                        + "('MOBILE_BLIND_INDEX',2,'pkcs11','mobile-index.v2','PREPARED')");
    }

    private static void resetKeys(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM ycs_crypto_blind_indexes");
        jdbc.update("UPDATE ycs_crypto_key_references SET key_state = CASE key_version "
                + "WHEN 1 THEN 'ACTIVE' ELSE 'PREPARED' END, optimistic_version = 0 "
                + "WHERE purpose = 'MOBILE_BLIND_INDEX'");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("migration lock coordination failed");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static byte[] unhex(String value) {
        return java.util.HexFormat.of().parseHex(value);
    }

    public static String hex(byte[] value) {
        return value == null ? null : java.util.HexFormat.of().formatHex(value);
    }

    private record Fixture(JdbcTemplate jdbc, DataSourceTransactionManager manager) {
    }
}
