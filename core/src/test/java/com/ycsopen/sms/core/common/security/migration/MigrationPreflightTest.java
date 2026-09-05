package com.ycsopen.sms.core.common.security.migration;

import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.AnchorState;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.SignerAnchor;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.AdmissionDecision;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.JdbcPairAdmissionStore;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.PairTuple;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedBoundary;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Locks the production migration entry point to one inseparable writer/snapshot result. */
class MigrationPreflightTest {

    @Test
    void productionVerifierImplementsOnlyTheCombinedAdmissionBoundary() {
        assertThat(PairedBoundary.class).isAssignableFrom(SignedMigrationManifestVerifier.class);
        assertThat(abstractMethodNames(WriterFencePort.class)).isEmpty();
        assertThat(abstractMethodNames(EncryptedSnapshotVerifier.class)).isEmpty();
        assertThat(abstractMethodNames(PairedBoundary.class)).containsExactly("verifyAndAdmit");
    }

    @Test
    void combinedAdmissionContainsBothRoleDigestsAndNoRoleOnlyResult() {
        assertThat(Arrays.stream(PairedAdmission.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly(
                        "globalSequence",
                        "signerKeyVersion",
                        "writerDigest",
                        "snapshotDigest",
                        "pairDigest",
                        "compatibleWriterArtifacts",
                        "snapshotId",
                        "recoveryKeyReference");

        assertThat(Arrays.stream(WriterFencePort.class.getDeclaredClasses())
                .map(Class::getSimpleName))
                .doesNotContain("WriterAdmission", "WriterVerification");
        assertThat(Arrays.stream(EncryptedSnapshotVerifier.class.getDeclaredClasses())
                .map(Class::getSimpleName))
                .doesNotContain("SnapshotAdmission", "SnapshotVerification");
    }

    @Test
    void jdbcStoreAtomicallySelectsOneCompleteHigherSequencePair() throws Exception {
        JdbcTemplate jdbc = jdbc();
        JdbcPairAdmissionStore store = new JdbcPairAdmissionStore(
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())));
        SignerAnchor active = new SignerAnchor(
                "signer-v1", AnchorState.ACTIVE, hex('f'), "AA==", null);
        PairTuple first = tuple(1, '1');
        assertThat(store.compareAndSet(Optional.empty(), first, active))
                .isEqualTo(AdmissionDecision.INSERTED);
        assertThat(store.compareAndSet(Optional.of(first), first, active))
                .isEqualTo(AdmissionDecision.IDEMPOTENT);

        PairTuple second = tuple(2, '2');
        PairTuple third = tuple(3, '3');
        List<Callable<AdmissionDecision>> competitors = List.of(
                () -> store.compareAndSet(Optional.of(first), second, active),
                () -> store.compareAndSet(Optional.of(first), third, active));
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<AdmissionDecision>> results = executor.invokeAll(competitors);
            assertThat(results.stream()
                    .map(MigrationPreflightTest::result)
                    .filter(AdmissionDecision.ADVANCED::equals)
                    .count()).isOne();
        }

        PairTuple accepted = store.current().orElseThrow();
        assertThat(accepted).isIn(second, third);
        assertThat(accepted.writerDigest()).hasSize(64);
        assertThat(accepted.snapshotDigest()).hasSize(64);
        assertThat(accepted.pairDigest()).hasSize(64);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_manifest_pair_admission", Long.class)).isOne();
    }

    private static Set<String> abstractMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .map(method -> method.getName())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:phase03_pair_admission;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE ALIAS IF NOT EXISTS HEX FOR "
                + "'org.h2.util.StringUtils.convertBytesToHex(byte[])'");
        jdbc.execute("CREATE ALIAS IF NOT EXISTS UNHEX FOR "
                + "'org.h2.util.StringUtils.convertHexToBytes'");
        jdbc.execute("DROP TABLE IF EXISTS ycs_crypto_manifest_pair_admission");
        jdbc.execute("CREATE TABLE ycs_crypto_manifest_pair_admission ("
                + "singleton_id TINYINT NOT NULL PRIMARY KEY, "
                + "migration_set_id VARCHAR(64) NOT NULL, "
                + "canonical_subject_digest BINARY(32) NOT NULL, "
                + "global_sequence BIGINT NOT NULL UNIQUE, "
                + "signer_key_version VARCHAR(32) NOT NULL, "
                + "signer_fingerprint BINARY(32) NOT NULL, "
                + "writer_digest BINARY(32) NOT NULL, "
                + "snapshot_digest BINARY(32) NOT NULL, "
                + "pair_digest BINARY(32) NOT NULL UNIQUE, "
                + "optimistic_version BIGINT NOT NULL DEFAULT 0)");
        return jdbc;
    }

    private static PairTuple tuple(long sequence, char digest) {
        return new PairTuple(
                "migration-set-v1",
                hex('a'),
                sequence,
                "signer-v1",
                hex('f'),
                hex(digest),
                hex((char) (digest + 1)),
                hex((char) (digest + 2)));
    }

    private static String hex(char value) {
        return Character.toString(value).repeat(64);
    }

    private static AdmissionDecision result(Future<AdmissionDecision> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new AssertionError("pair admission competitor failed", exception);
        }
    }
}
