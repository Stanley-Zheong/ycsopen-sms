package com.ycsopen.sms.core.common.security.key.lifecycle;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotChunkStore;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnapshotEnvelopeInventoryTest {

    @TempDir
    Path directory;

    @Test
    void retainedCanonicalSnapshotBlocksRetirementUntilDeleted() throws Exception {
        JdbcTemplate jdbc = jdbc();
        insertKey(jdbc, 1, KeyState.DECRYPT_ONLY);
        insertKey(jdbc, 2, KeyState.ACTIVE);
        SnapshotChunkStore.FileStore store = new SnapshotChunkStore.FileStore(
                Files.createDirectory(directory.resolve("snapshot-store")));
        byte[] envelope = envelope("snapshot-recovery.v1");
        String snapshotId = "retained-v1";
        store.beginSnapshot(snapshotId);
        store.putComplete(snapshotId, 0, envelope);
        SnapshotManifest manifest = manifest(snapshotId, "snapshot-recovery.v1", envelope);
        store.putManifest(snapshotId, manifest.canonicalBytes());
        EnvelopeReferenceInventory.Source source =
                EnvelopeReferenceInventory.snapshotEnvelopeSource(jdbc, store);
        EnvelopeReferenceInventory inventory = new EnvelopeReferenceInventory(
                Set.of(source.sourceId()), List.of(source));
        KeyLifecycleService lifecycle = new KeyLifecycleService(
                new KeyReferenceRepository.Jdbc(jdbc, transaction(jdbc)), inventory);

        assertThat(inventory.snapshot(KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY)
                .count(KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1)).isOne();
        assertThatThrownBy(() -> lifecycle.retire(
                KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);

        store.deleteSnapshot(snapshotId);
        assertThat(lifecycle.retire(KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1)
                .liveReferences()).isZero();
    }

    @Test
    void incompleteReservationFailsRetirementClosedUntilExplicitlyDeleted() throws Exception {
        JdbcTemplate jdbc = jdbc();
        insertKey(jdbc, 1, KeyState.DECRYPT_ONLY);
        insertKey(jdbc, 2, KeyState.ACTIVE);
        SnapshotChunkStore.FileStore store = new SnapshotChunkStore.FileStore(
                Files.createDirectory(directory.resolve("incomplete-snapshot-store")));
        String snapshotId = "crashed-before-first-chunk";
        store.beginSnapshot(snapshotId);
        EnvelopeReferenceInventory.Source source =
                EnvelopeReferenceInventory.snapshotEnvelopeSource(jdbc, store);
        KeyLifecycleService lifecycle = new KeyLifecycleService(
                new KeyReferenceRepository.Jdbc(jdbc, transaction(jdbc)),
                new EnvelopeReferenceInventory(Set.of(source.sourceId()), List.of(source)));

        assertThatThrownBy(() -> lifecycle.retire(
                KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);

        store.deleteSnapshot(snapshotId);
        assertThat(lifecycle.retire(KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1)
                .liveReferences()).isZero();
    }

    @Test
    void missingOrUnavailableCanonicalSnapshotSourceFailsClosed() {
        JdbcTemplate jdbc = jdbc();
        insertKey(jdbc, 1, KeyState.DECRYPT_ONLY);
        insertKey(jdbc, 2, KeyState.ACTIVE);
        KeyReferenceRepository keys = new KeyReferenceRepository.Jdbc(jdbc, transaction(jdbc));

        assertThatThrownBy(() -> new KeyLifecycleService(keys,
                new EnvelopeReferenceInventory(Set.of(), List.of())).retire(
                KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);

        EnvelopeReferenceInventory.Source unavailable =
                EnvelopeReferenceInventory.unavailableSnapshotEnvelopeSource();
        assertThatThrownBy(() -> new KeyLifecycleService(keys,
                new EnvelopeReferenceInventory(Set.of(unavailable.sourceId()),
                        List.of(unavailable))).retire(
                KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);
    }

    private static SnapshotManifest manifest(
            String snapshotId, String keyReference, byte[] envelope) throws Exception {
        SnapshotManifest.Subject subject = new SnapshotManifest.Subject(
                "phase03-plan14", "test", "a".repeat(64), "ycsopen_sms",
                "b".repeat(64), 1, "signer-v1");
        return new SnapshotManifest(subject, snapshotId, keyReference, 1, envelope.length,
                List.of(new SnapshotManifest.Chunk(
                        0, true, 1, envelope.length, sha256(envelope))));
    }

    private static byte[] envelope(String keyReference) {
        return new EnvelopeCodec().encode(new CipherEnvelope(
                "pkcs11", keyReference, new byte[12], new byte[48], new byte[12], new byte[17]),
                EnvelopeCodec.Target.MYSQL_ENCRYPTED_SNAPSHOT_CHUNK);
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static JdbcTemplate jdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:snapshot-inventory-" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE ycs_crypto_key_references ("
                + "purpose VARCHAR(48) NOT NULL, key_version BIGINT NOT NULL, "
                + "provider_id VARCHAR(32) NOT NULL, provider_key_reference VARCHAR(128) NOT NULL, "
                + "key_state VARCHAR(24) NOT NULL, wrap_operation_count BIGINT NOT NULL DEFAULT 0, "
                + "rotation_required BOOLEAN NOT NULL DEFAULT FALSE, "
                + "optimistic_version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY (purpose,key_version))");
        return jdbc;
    }

    private static TransactionTemplate transaction(JdbcTemplate jdbc) {
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    private static void insertKey(JdbcTemplate jdbc, long version, KeyState state) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES ('SNAPSHOT_RECOVERY',?,'pkcs11',?,?)",
                version, "snapshot-recovery.v" + version, state.name());
    }
}
