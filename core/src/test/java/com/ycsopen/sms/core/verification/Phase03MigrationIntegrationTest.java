package com.ycsopen.sms.core.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.AnchorState;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.migration.LegacyValueClassifier;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataManifest;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationCommand;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationLauncher;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataMigrationRunner;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.JdbcConfiguration;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.ManifestConfiguration;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.Pkcs11Configuration;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.ProductionConfiguration;
import com.ycsopen.sms.core.common.security.migration.Pkcs11MigrationBlindIndexPort;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort;
import com.ycsopen.sms.core.common.security.migration.snapshot.EncryptedMySqlSnapshotService;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotChunkStore;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotManifest;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final String PRODUCTION_MIGRATION_CONFIG_PROPERTY =
            "ycsopen.phase03.migration.config";
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

    @Test
    void createsAndRestoresEncryptedSnapshotIntoFreshSchema() throws Exception {
        Phase03EncryptedSnapshotHarness.runRealProof();
    }

    /** Child entrypoint so SoftHSM reads the run-owned configuration from its real environment. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"real-snapshot-proof".equals(args[0])) {
            throw new IllegalArgumentException("unsupported integration proof mode");
        }
        runSnapshotChild();
        System.out.print("PHASE03_SNAPSHOT_REAL_PROOF_PASS\n");
    }

    private static void runSnapshotChild() throws Exception {
        DriverManagerDataSource sourceDataSource = snapshotDataSource("phase01");
        migrateSnapshotQuietly(sourceDataSource);
        JdbcTemplate source = new JdbcTemplate(sourceDataSource);
        JdbcTemplate admin = new JdbcTemplate(snapshotAdminDataSource("mysql"));
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(sourceDataSource);
        seedSnapshotKeyMetadata(source);

        String snapshotId = "snapshot-" + UUID.randomUUID().toString().substring(0, 8);
        String restoreSchema = "restore_" + UUID.randomUUID().toString().replace("-", "");
        String rejectedSchema = "restore_rejected_" + UUID.randomUUID().toString().replace("-", "");
        byte[] marker = ("phase03-plaintext-canary-" + snapshotId)
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] payload = repeated(marker, 11 * 1_024 * 1_024 + 257);
        source.execute("CREATE TABLE phase03_snapshot_canary (id BIGINT PRIMARY KEY, payload LONGBLOB NOT NULL)");
        source.update("INSERT INTO phase03_snapshot_canary (id, payload) VALUES (1, ?)", payload);
        String sourcePayloadDigest = source.queryForObject(
                "SELECT LOWER(SHA2(payload, 256)) FROM phase03_snapshot_canary WHERE id = 1",
                String.class);

        Path storeRoot = Path.of(requiredEnvironment("PHASE03_SNAPSHOT_STORE"))
                .toAbsolutePath().normalize();
        assertProductionMigrationLauncherReachable(source, storeRoot);
        SnapshotChunkStore.FileStore store = new SnapshotChunkStore.FileStore(storeRoot);
        EnvelopeCodec envelopeCodec = new EnvelopeCodec();
        try (AdapterRuntime runtime = openSnapshotAdapter(sourceDataSource, transactions)) {
            ProtectedFieldCodec recoveryCodec = new ProtectedFieldCodec(
                    envelopeCodec, runtime.adapter(), new SecureRandom(), "snapshot-recovery.v1");
            MySqlSnapshotProcess process = new MySqlSnapshotProcess.FixedArgumentClient(
                    findExecutable("docker"), requiredEnvironment("PHASE03_MYSQL_CONTAINER"));
            EncryptedMySqlSnapshotService service = new EncryptedMySqlSnapshotService(
                    recoveryCodec, process, store,
                    (original, target) -> requireFreshSchema(admin, original, target));
            MySqlSnapshotProcess.Database sourceDatabase = database("phase01", false);
            SnapshotManifest.Subject subject = new SnapshotManifest.Subject(
                    "migration-set-plan14", "integration", "2".repeat(64), "phase01",
                    "3".repeat(64), 1, "signer-v1");
            SnapshotManifest manifest = service.create(
                    new EncryptedMySqlSnapshotService.CreateRequest(
                            sourceDatabase, subject, snapshotId, "snapshot-recovery.v1"));
            assertThat(manifest.chunks()).hasSizeGreaterThan(1);
            assertThat(manifest.totalPlaintextBytes()).isGreaterThan(10_485_760L);
            assertEncryptedChunksExclude(storeRoot, marker, manifest.chunks().size());

            AdmissionProof proof = admitWithRealCommand(
                    manifest, sourceDataSource, transactions, runtime.adapter(), envelopeCodec,
                    storeRoot);
            assertThat(proof.exit()).isZero();
            assertThat(proof.admission().snapshotDigest()).isEqualTo(manifest.digest());

            admin.execute("CREATE DATABASE `" + restoreSchema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            MySqlSnapshotProcess.Database targetDatabase = database(restoreSchema, true);
            EncryptedMySqlSnapshotService.RestoreResult result = service.restore(
                    manifest.canonicalBytes(), proof.admission(), sourceDatabase, targetDatabase);
            assertThat(result.alreadyComplete()).isFalse();
            assertThat(store.recoveryComplete(snapshotId, restoreSchema, manifest.digest())).isTrue();

            JdbcTemplate restored = new JdbcTemplate(snapshotAdminDataSource(restoreSchema));
            assertThat(restored.queryForObject(
                    "SELECT COUNT(*) FROM phase03_snapshot_canary", Long.class)).isOne();
            assertThat(restored.queryForObject(
                    "SELECT LOWER(SHA2(payload, 256)) FROM phase03_snapshot_canary WHERE id = 1",
                    String.class)).isEqualTo(sourcePayloadDigest);
            assertThat(restored.queryForList(
                    "SELECT version FROM flyway_schema_history WHERE success = 1 "
                            + "AND version IS NOT NULL ORDER BY installed_rank", String.class))
                    .containsExactly("1", "1200");

            admin.execute("CREATE DATABASE `" + rejectedSchema
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            Path firstChunk;
            try (var paths = Files.walk(storeRoot)) {
                firstChunk = paths.filter(path -> path.getFileName().toString()
                                .equals("chunk-000000.ycse"))
                        .findFirst().orElseThrow();
            }
            byte[] corrupted = Files.readAllBytes(firstChunk);
            corrupted[corrupted.length - 1] ^= 1;
            Files.write(firstChunk, corrupted);
            java.util.Arrays.fill(corrupted, (byte) 0);
            assertThatThrownBy(() -> service.restore(
                    manifest.canonicalBytes(), proof.admission(), sourceDatabase,
                    database(rejectedSchema, true)))
                    .isInstanceOf(SnapshotManifest.SnapshotException.class);
            assertThat(store.recoveryComplete(snapshotId, rejectedSchema, manifest.digest())).isFalse();
            assertThat(admin.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?",
                    Long.class, rejectedSchema)).isZero();

            store.deleteSnapshot(snapshotId);
            try (var remaining = Files.walk(storeRoot)) {
                assertThat(remaining.filter(path -> path.getFileName().toString()
                        .endsWith(".ycse")).toList()).isEmpty();
            }
            assertThat(store.recoveryComplete(snapshotId, restoreSchema, manifest.digest())).isFalse();
            runMigrationCommandProof(sourceDataSource, transactions, runtime.adapter(), envelopeCodec,
                    storeRoot.resolve("migration-command"));
        } finally {
            java.util.Arrays.fill(payload, (byte) 0);
            admin.execute("DROP DATABASE IF EXISTS `" + restoreSchema + "`");
            admin.execute("DROP DATABASE IF EXISTS `" + rejectedSchema + "`");
        }
    }

    /** Proves the operator entrypoint composes real MySQL and SoftHSM without injected services. */
    private static void assertProductionMigrationLauncherReachable(
            JdbcTemplate jdbc, Path directory) throws Exception {
        String runId = "33333333-3333-4333-8333-333333333333";
        byte[] pairDigest = sha256Bytes("production-entry-pair".getBytes(StandardCharsets.US_ASCII));
        byte[] subjectDigest = sha256Bytes("production-entry-subject".getBytes(StandardCharsets.US_ASCII));
        byte[] signerDigest = sha256Bytes("production-entry-signer".getBytes(StandardCharsets.US_ASCII));
        byte[] writerDigest = sha256Bytes("production-entry-writer".getBytes(StandardCharsets.US_ASCII));
        byte[] snapshotDigest = sha256Bytes("production-entry-snapshot".getBytes(StandardCharsets.US_ASCII));
        byte[] manifestDigest = sha256Bytes("production-entry-manifest".getBytes(StandardCharsets.US_ASCII));
        jdbc.update("INSERT INTO ycs_crypto_manifest_pair_admission "
                        + "(singleton_id,migration_set_id,canonical_subject_digest,global_sequence,"
                        + "signer_key_version,signer_fingerprint,writer_digest,snapshot_digest,pair_digest) "
                        + "VALUES (1,'phase03-production-entry',?,1,'signer-v1',?,?,?,?)",
                subjectDigest, signerDigest, writerDigest, snapshotDigest, pairDigest);
        jdbc.update("INSERT INTO ycs_crypto_migration_runs "
                        + "(migration_run_id,admitted_singleton_id,admitted_pair_digest,run_state,"
                        + "manifest_digest) VALUES (?,1,?,'READY',?)",
                runId, pairDigest, manifestDigest);

        Path inventory = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/src/main/resources/security/protected-data-inventory.json")
                .toRealPath();
        byte[] inventoryBytes = Files.readAllBytes(inventory);
        KeyPair signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] publicKey = signer.getPublic().getEncoded();
        MigrationPreflightProperties.SignerAnchor anchor =
                new MigrationPreflightProperties.SignerAnchor(
                        "signer-v1", AnchorState.ACTIVE,
                        hex(sha256Bytes(publicKey)),
                        Base64.getEncoder().encodeToString(publicKey), null);
        Path library = Path.of(requiredEnvironment("PHASE03_HSM_LIBRARY"))
                .toAbsolutePath().normalize().toRealPath();
        String jdbcUrl = "jdbc:mysql://" + requiredEnvironment("PHASE03_MYSQL_HOST") + ":"
                + requiredEnvironment("PHASE03_MYSQL_PORT") + "/phase01"
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        ProductionConfiguration configuration = new ProductionConfiguration(
                "phase03-migration-production/v1", "phase03-production-entry",
                new JdbcConfiguration(jdbcUrl, requiredEnvironment("PHASE03_MYSQL_USER"),
                        "PHASE03_MYSQL_PASSWORD"),
                new ManifestConfiguration(inventory.toString(),
                        ProtectedDataManifest.canonicalDigest(inventoryBytes)),
                new Pkcs11Configuration(library.toString(), List.of(library.toString()),
                        Long.parseUnsignedLong(requiredEnvironment("PHASE03_HSM_SLOT")),
                        "phase03-snapshot", "PHASE03_HSM_USER_PIN", productionDescriptors()),
                List.of(anchor),
                Set.of(new MigrationPreflightProperties.WriterIdentity(
                        "ycsopen-sms-core", "1.0.0", "3".repeat(64))),
                Set.of("snapshot-recovery.v1"));
        Path configurationPath = directory.resolve("production-migration-config.json");
        ObjectMapper json = new ObjectMapper();
        com.fasterxml.jackson.databind.node.ObjectNode configurationJson =
                json.valueToTree(configuration);
        configurationJson.withObject("pkcs11").withArray("keys")
                .forEach(key -> ((com.fasterxml.jackson.databind.node.ObjectNode) key)
                        .remove("wrappingKey"));
        Files.write(configurationPath, json.writeValueAsBytes(configurationJson));
        String previous = System.getProperty(PRODUCTION_MIGRATION_CONFIG_PROPERTY);
        System.setProperty(PRODUCTION_MIGRATION_CONFIG_PROPERTY,
                configurationPath.toRealPath().toString());
        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        try (PrintStream stdout = new PrintStream(stdoutBytes, true, StandardCharsets.UTF_8);
             PrintStream stderr = new PrintStream(stderrBytes, true, StandardCharsets.UTF_8)) {
            int exit = ProtectedDataMigrationLauncher.run(
                    new String[]{"status", "--run-id", runId}, stdout, stderr);
            assertThat(exit).isZero();
            assertThat(stderrBytes.toString(StandardCharsets.UTF_8)).isEmpty();
            assertThat(stdoutBytes.toString(StandardCharsets.UTF_8))
                    .contains("\"status\":\"accepted\"")
                    .contains("\"run_id\":\"" + runId + "\"")
                    .contains(hex(pairDigest));
        } finally {
            if (previous == null) {
                System.clearProperty(PRODUCTION_MIGRATION_CONFIG_PROPERTY);
            } else {
                System.setProperty(PRODUCTION_MIGRATION_CONFIG_PROPERTY, previous);
            }
            jdbc.update("DELETE FROM ycs_crypto_migration_runs WHERE migration_run_id=?", runId);
            jdbc.update("DELETE FROM ycs_crypto_manifest_pair_admission WHERE singleton_id=1 "
                    + "AND pair_digest=?", pairDigest);
            Files.deleteIfExists(configurationPath);
            Arrays.fill(inventoryBytes, (byte) 0);
            Arrays.fill(publicKey, (byte) 0);
        }
    }

    private static List<Pkcs11KeyDescriptor> productionDescriptors() {
        return List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK,
                        "field-kek.v1", "ycs.field-encryption-kek.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY,
                        "snapshot-recovery.v1", "ycs.snapshot-recovery.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX,
                        "mobile-index.v1", "ycs.mobile-blind-index.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST,
                        "object-digest.v1", "ycs.object-capability-digest.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST,
                        "registration-digest.v1", "ycs.registration-upload-digest.v1"));
    }

    private static AdmissionProof admitWithRealCommand(
            SnapshotManifest snapshot,
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            SunPkcs11KeyAdapter adapter,
            EnvelopeCodec envelopeCodec,
            Path directory) throws Exception {
        Instant now = Instant.parse("2026-09-01T00:00:00Z");
        KeyPair signer = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String signerFingerprint = hex(MessageDigest.getInstance("SHA-256")
                .digest(signer.getPublic().getEncoded()));
        String writerSourceDigest = "4".repeat(64);
        byte[] writer = canonicalWriter(snapshot.subject(), now, writerSourceDigest);
        byte[] snapshotBytes = snapshot.canonicalBytes();
        byte[] writerDigest = MessageDigest.getInstance("SHA-256").digest(writer);
        byte[] snapshotDigest = MessageDigest.getInstance("SHA-256").digest(snapshotBytes);
        byte[] pairDigest = pairDigest(snapshot.subject(), writerDigest, snapshotDigest);

        Path writerPath = directory.resolve("writer.json");
        Path snapshotPath = directory.resolve("snapshot.json");
        Path writerSignature = directory.resolve("writer.sig");
        Path snapshotSignature = directory.resolve("snapshot.sig");
        Files.write(writerPath, writer);
        Files.write(snapshotPath, snapshotBytes);
        Files.write(writerSignature, sign(signer, (byte) 1, pairDigest, writerDigest));
        Files.write(snapshotSignature, sign(signer, (byte) 2, pairDigest, snapshotDigest));

        MigrationPreflightProperties.WriterIdentity writerIdentity =
                new MigrationPreflightProperties.WriterIdentity(
                        "ycsopen-sms-core", "1.0.0", writerSourceDigest);
        MigrationPreflightProperties properties = new MigrationPreflightProperties(
                List.of(new MigrationPreflightProperties.SignerAnchor(
                        "signer-v1", MigrationPreflightProperties.AnchorState.ACTIVE,
                        signerFingerprint,
                        Base64.getEncoder().encodeToString(signer.getPublic().getEncoded()), null)),
                Set.of(writerIdentity), Set.of("snapshot-recovery.v1"));
        MigrationStateRepository repository = new MigrationStateRepository.Jdbc(
                new JdbcTemplate(dataSource), new org.springframework.transaction.support.TransactionTemplate(
                transactions));
        SignedMigrationManifestVerifier verifier = new SignedMigrationManifestVerifier(
                properties, new SignedMigrationManifestVerifier.JdbcPairAdmissionStore(
                new JdbcTemplate(dataSource),
                new org.springframework.transaction.support.TransactionTemplate(transactions)),
                Clock.fixed(now, java.time.ZoneOffset.UTC));

        Path protectedInventory = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/src/main/resources/security/protected-data-inventory.json");
        byte[] inventoryBytes = Files.readAllBytes(protectedInventory);
        ProtectedDataManifest protectedManifest = ProtectedDataManifest.load(
                protectedInventory, ProtectedDataManifest.canonicalDigest(inventoryBytes));
        ProtectedFieldCodec fieldCodec = new ProtectedFieldCodec(
                envelopeCodec, adapter, new SecureRandom(), "field-kek.v1");
        ProtectedDataMigrationRunner runner = new ProtectedDataMigrationRunner(
                protectedManifest, repository, new LegacyValueClassifier(envelopeCodec), fieldCodec,
                value -> sha256Bytes(value),
                (value, targetType, fieldId, tenantScope) -> List.of(),
                Clock.fixed(now, java.time.ZoneOffset.UTC));
        AtomicReference<WriterFencePort.PairedAdmission> admitted = new AtomicReference<>();
        ProtectedDataMigrationCommand.DefaultServices services =
                new ProtectedDataMigrationCommand.DefaultServices(invocation -> {
                    WriterFencePort.PairedAdmission value = verifier.verifyAndAdmit(
                            new WriterFencePort.PairedAdmissionRequest(
                                    invocation.writerManifest(), invocation.writerSignature(),
                                    invocation.snapshotManifest(), invocation.snapshotSignature(),
                                    new WriterFencePort.DeploymentSubject(
                                            snapshot.subject().migrationSetId(), invocation.environment(),
                                            invocation.databaseInstanceFingerprint(), invocation.schema(),
                                            invocation.flywaySetDigest())));
                    admitted.set(value);
                    return value;
                }, repository, runner);

        ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrBytes = new ByteArrayOutputStream();
        int exit;
        try (PrintStream stdout = new PrintStream(stdoutBytes, true,
                java.nio.charset.StandardCharsets.UTF_8);
             PrintStream stderr = new PrintStream(stderrBytes, true,
                     java.nio.charset.StandardCharsets.UTF_8)) {
            exit = ProtectedDataMigrationLauncher.run(new String[]{
                    "preflight",
                    "--writer-manifest", writerPath.toRealPath().toString(),
                    "--writer-signature", writerSignature.toRealPath().toString(),
                    "--snapshot-manifest", snapshotPath.toRealPath().toString(),
                    "--snapshot-signature", snapshotSignature.toRealPath().toString(),
                    "--environment", snapshot.subject().environment(),
                    "--database-instance-fingerprint",
                    snapshot.subject().databaseInstanceFingerprint(),
                    "--schema", snapshot.subject().schema(),
                    "--flyway-set-digest", snapshot.subject().flywaySetDigest()
            }, stdout, stderr, services);
        }
        assertThat(stderrBytes.toString(java.nio.charset.StandardCharsets.UTF_8)).isEmpty();
        assertThat(stdoutBytes.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"status\":\"accepted\"")
                .contains(hex(pairDigest));
        return new AdmissionProof(exit, admitted.get());
    }

    private static void runMigrationCommandProof(
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            SunPkcs11KeyAdapter adapter,
            EnvelopeCodec envelopeCodec,
            Path directory) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Path v1 = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/src/main/resources/db/migration/V1__init_schema.sql");
        assertThat(sha256(v1)).isEqualTo(V1_SHA256);
        resetMigrationProof(jdbc);
        assertClassifierOnlyBoundaries(envelopeCodec);
        Phase03MigrationCommandFixture fixture = new Phase03MigrationCommandFixture(directory);
        KeyPair oldSigner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        KeyPair activeSigner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        MigrationRuntime runtime = migrationRuntime(
                dataSource, transactions, adapter, adapter, envelopeCodec,
                Phase03MigrationCommandFixture.properties(List.of(
                        Phase03MigrationCommandFixture.anchor(
                                oldSigner, "signer-v1", AnchorState.ACTIVE, null))));

        assertSignedPreflightRejectionMatrix(
                jdbc, transactions, fixture, oldSigner, activeSigner, runtime);
        AcceptedCommand accepted = admitRotationSuccessPair(
                jdbc, dataSource, transactions, adapter, envelopeCodec,
                fixture, oldSigner, activeSigner);

        List<IndexedFixture> indexed = seedIndexedTargets(jdbc);
        assertIndexedTargetMigrations(jdbc, accepted, adapter, indexed);
        assertNoIndexTargetMigration(
                jdbc, accepted, adapter, envelopeCodec,
                "bulk_sending_items.mobile_encrypted", "BULK_SENDING_ITEM_MOBILE",
                9_100_001L, "13800138000");
        assertConcurrentMutationAndNoIndexMigration(
                jdbc, dataSource, transactions, accepted, adapter, envelopeCodec,
                9_200_001L, "13900139000");
        writeMigrationEvidence(jdbc, accepted.pairDigest());
        assertThat(sha256(v1)).isEqualTo(V1_SHA256);
    }

    private static void assertClassifierOnlyBoundaries(EnvelopeCodec envelopeCodec) throws Exception {
        Path inventoryPath = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/src/main/resources/security/protected-data-inventory.json");
        byte[] inventory = Files.readAllBytes(inventoryPath);
        ProtectedDataManifest manifest = ProtectedDataManifest.load(
                inventoryPath, ProtectedDataManifest.canonicalDigest(inventory));
        LegacyValueClassifier classifier = new LegacyValueClassifier(envelopeCodec);
        var boundary = manifest.requireTarget("channels.account_encrypted");
        assertThat(classifier.classify(
                boundary, "a".repeat(110).getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(LegacyValueClassifier.Classification.APPROVED_LEGACY);
        assertThat(classifier.classify(
                boundary, "a".repeat(111).getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(LegacyValueClassifier.Classification.AMBIGUOUS);
        assertThat(classifier.classify(boundary, null))
                .isEqualTo(LegacyValueClassifier.Classification.NULL_ALLOWED);
    }

    private static void assertSignedPreflightRejectionMatrix(
            JdbcTemplate jdbc,
            DataSourceTransactionManager transactions,
            Phase03MigrationCommandFixture fixture,
            KeyPair oldSigner,
            KeyPair newSigner,
            MigrationRuntime activeRuntime) throws Exception {
        Phase03MigrationCommandFixture.PairFiles valid = fixture.pair(1, "signer-v1", oldSigner);

        Path absent = valid.writerManifest().resolveSibling("absent-writer.json");
        assertRejectedPreflight(jdbc, activeRuntime.services(), 20,
                new String[]{
                        "preflight", "--writer-manifest", absent.toString(),
                        "--writer-signature", valid.writerSignature().toString(),
                        "--snapshot-manifest", valid.snapshotManifest().toString(),
                        "--snapshot-signature", valid.snapshotSignature().toString(),
                        "--environment", Phase03MigrationCommandFixture.ENVIRONMENT,
                        "--database-instance-fingerprint", Phase03MigrationCommandFixture.DATABASE,
                        "--schema", Phase03MigrationCommandFixture.SCHEMA,
                        "--flyway-set-digest", Phase03MigrationCommandFixture.FLYWAY
                });

        Path empty = absent.resolveSibling("empty-writer.json");
        Files.write(empty, new byte[0]);
        Phase03MigrationCommandFixture.PairFiles emptyPair = new Phase03MigrationCommandFixture.PairFiles(
                empty, valid.writerSignature(), valid.snapshotManifest(), valid.snapshotSignature(),
                valid.pairDigest(), valid.subject());
        assertRejectedPreflight(jdbc, activeRuntime.services(), 20,
                Phase03MigrationCommandFixture.preflightArguments(emptyPair));

        Phase03MigrationCommandFixture.PairFiles noncanonical = fixture.pair(1, "signer-v1", oldSigner);
        Files.writeString(noncanonical.writerManifest(), "\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        assertRejectedPreflight(jdbc, activeRuntime.services(), 21,
                Phase03MigrationCommandFixture.preflightArguments(noncanonical));

        Phase03MigrationCommandFixture.PairFiles unknown = fixture.pair(1, "signer-unknown", newSigner);
        assertRejectedPreflight(jdbc, activeRuntime.services(), 22,
                Phase03MigrationCommandFixture.preflightArguments(unknown));

        for (AnchorState state : List.of(AnchorState.RETIRED, AnchorState.REVOKED)) {
            resetPairState(jdbc);
            MigrationRuntime untrusted = migrationRuntime(
                    activeRuntime.dataSource(), transactions, activeRuntime.adapter(),
                    activeRuntime.adapter(), activeRuntime.envelopeCodec(),
                    Phase03MigrationCommandFixture.properties(List.of(
                            Phase03MigrationCommandFixture.anchor(oldSigner, "signer-v1", state, null),
                            Phase03MigrationCommandFixture.anchor(
                                    newSigner, "signer-v2", AnchorState.ACTIVE, null))));
            assertRejectedPreflight(jdbc, untrusted.services(), 22,
                    Phase03MigrationCommandFixture.preflightArguments(valid));
        }

        resetPairState(jdbc);
        MigrationRuntime stale = migrationRuntime(
                activeRuntime.dataSource(), transactions, activeRuntime.adapter(),
                activeRuntime.adapter(), activeRuntime.envelopeCodec(),
                Phase03MigrationCommandFixture.properties(List.of(
                        Phase03MigrationCommandFixture.anchor(
                                oldSigner, "signer-v1", AnchorState.RETIRING, 0L),
                        Phase03MigrationCommandFixture.anchor(
                                newSigner, "signer-v2", AnchorState.ACTIVE, null))));
        assertRejectedPreflight(jdbc, stale.services(), 24,
                Phase03MigrationCommandFixture.preflightArguments(valid));

        resetPairState(jdbc);
        Phase03MigrationCommandFixture.PairFiles future = fixture.pair(
                1, "signer-v1", oldSigner,
                writer -> writer.put("issued_at", Phase03MigrationCommandFixture.NOW.plusSeconds(1).toString()),
                snapshot -> { });
        assertRejectedPreflight(jdbc, activeRuntime.services(), 24,
                Phase03MigrationCommandFixture.preflightArguments(future));

        Phase03MigrationCommandFixture.PairFiles forged = fixture.pair(1, "signer-v1", oldSigner);
        byte[] forgedSignature = Files.readAllBytes(forged.writerSignature());
        forgedSignature[0] ^= 1;
        Files.write(forged.writerSignature(), forgedSignature);
        Arrays.fill(forgedSignature, (byte) 0);
        assertRejectedPreflight(jdbc, activeRuntime.services(), 22,
                Phase03MigrationCommandFixture.preflightArguments(forged));

        resetPairState(jdbc);
        ProtectedDataMigrationCommand.DefaultServices fingerprintDrift = servicesWithPreflight(
                activeRuntime, invocation -> {
                    MigrationPreflightProperties.SignerAnchor drifted =
                            new MigrationPreflightProperties.SignerAnchor(
                                    "signer-v1", AnchorState.ACTIVE, "0".repeat(64),
                                    Base64.getEncoder().encodeToString(oldSigner.getPublic().getEncoded()), null);
                    return new SignedMigrationManifestVerifier(
                            Phase03MigrationCommandFixture.properties(List.of(drifted)),
                            new SignedMigrationManifestVerifier.JdbcPairAdmissionStore(
                                    jdbc, new org.springframework.transaction.support.TransactionTemplate(
                                    transactions)),
                            Clock.fixed(Phase03MigrationCommandFixture.NOW, ZoneOffset.UTC))
                            .verifyAndAdmit(preflightRequest(invocation,
                                    Phase03MigrationCommandFixture.MIGRATION_SET));
                });
        assertRejectedPreflight(jdbc, fingerprintDrift, 20,
                Phase03MigrationCommandFixture.preflightArguments(valid));

        for (String mismatch : List.of("migration-set", "environment", "database", "schema", "flyway")) {
            resetPairState(jdbc);
            String expectedMigrationSet = "migration-set".equals(mismatch)
                    ? "phase03-other" : Phase03MigrationCommandFixture.MIGRATION_SET;
            ProtectedDataMigrationCommand.DefaultServices services = servicesWithPreflight(
                    activeRuntime, invocation -> activeRuntime.verifier().verifyAndAdmit(
                            preflightRequest(invocation, expectedMigrationSet)));
            String environment = "environment".equals(mismatch)
                    ? "other" : Phase03MigrationCommandFixture.ENVIRONMENT;
            String database = "database".equals(mismatch)
                    ? "5".repeat(64) : Phase03MigrationCommandFixture.DATABASE;
            String schema = "schema".equals(mismatch)
                    ? "other_schema" : Phase03MigrationCommandFixture.SCHEMA;
            String flyway = "flyway".equals(mismatch)
                    ? "6".repeat(64) : Phase03MigrationCommandFixture.FLYWAY;
            assertRejectedPreflight(jdbc, services, 23,
                    Phase03MigrationCommandFixture.preflightArguments(
                            valid, environment, database, schema, flyway));
        }

        resetPairState(jdbc);
        Phase03MigrationCommandFixture.PairFiles sharedMismatch = fixture.pair(
                1, "signer-v1", oldSigner,
                writer -> writer.put("environment", "other"), snapshot -> { });
        assertRejectedPreflight(jdbc, activeRuntime.services(), 23,
                Phase03MigrationCommandFixture.preflightArguments(sharedMismatch));

        Phase03MigrationCommandFixture.PairFiles left = fixture.pair(1, "signer-v1", oldSigner);
        Phase03MigrationCommandFixture.PairFiles right = fixture.pair(
                1, "signer-v1", oldSigner, writer -> { },
                snapshot -> snapshot.put("snapshot_id", "cross-pair"));
        Phase03MigrationCommandFixture.PairFiles splice = new Phase03MigrationCommandFixture.PairFiles(
                left.writerManifest(), left.writerSignature(),
                right.snapshotManifest(), right.snapshotSignature(),
                left.pairDigest(), left.subject());
        assertRejectedPreflight(jdbc, activeRuntime.services(), 22,
                Phase03MigrationCommandFixture.preflightArguments(splice));

        resetPairState(jdbc);
        assertHalfAdmissionRollsBack(
                jdbc, transactions, activeRuntime, valid);
    }

    private static AcceptedCommand admitRotationSuccessPair(
            JdbcTemplate jdbc,
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            SunPkcs11KeyAdapter adapter,
            EnvelopeCodec envelopeCodec,
            Phase03MigrationCommandFixture fixture,
            KeyPair oldSigner,
            KeyPair newSigner) throws Exception {
        resetPairState(jdbc);
        MigrationRuntime oldRuntime = migrationRuntime(
                dataSource, transactions, adapter, adapter, envelopeCodec,
                Phase03MigrationCommandFixture.properties(List.of(
                        Phase03MigrationCommandFixture.anchor(
                                oldSigner, "signer-v1", AnchorState.ACTIVE, null))));
        Phase03MigrationCommandFixture.PairFiles oldPair = fixture.pair(10, "signer-v1", oldSigner);
        assertAcceptedPreflight(oldRuntime.services(), oldPair);
        long version = pairOptimisticVersion(jdbc);
        assertAcceptedPreflight(oldRuntime.services(), oldPair);
        assertThat(pairOptimisticVersion(jdbc)).isEqualTo(version);
        Phase03MigrationCommandFixture.PairFiles sameSequenceChange = fixture.pair(
                10, "signer-v1", oldSigner, writer -> { },
                snapshot -> snapshot.put("snapshot_id", "same-sequence-change"));
        StateCounts beforeSameSequence = stateCounts(jdbc);
        Phase03MigrationCommandFixture.CommandResult sameSequenceRejected =
                Phase03MigrationCommandFixture.invoke(
                        oldRuntime.services(),
                        Phase03MigrationCommandFixture.preflightArguments(sameSequenceChange));
        assertThat(sameSequenceRejected.exit()).isEqualTo(24);
        assertThat(stateCounts(jdbc)).isEqualTo(beforeSameSequence);

        MigrationRuntime rollout = migrationRuntime(
                dataSource, transactions, adapter, adapter, envelopeCodec,
                Phase03MigrationCommandFixture.properties(List.of(
                        Phase03MigrationCommandFixture.anchor(
                                oldSigner, "signer-v1", AnchorState.RETIRING, 10L),
                        Phase03MigrationCommandFixture.anchor(
                                newSigner, "signer-v2", AnchorState.ACTIVE, null))));
        assertAcceptedPreflight(rollout.services(), oldPair);
        Phase03MigrationCommandFixture.PairFiles replay = fixture.pair(11, "signer-v1", oldSigner);
        StateCounts beforeReplay = stateCounts(jdbc);
        Phase03MigrationCommandFixture.CommandResult rejected = Phase03MigrationCommandFixture.invoke(
                rollout.services(), Phase03MigrationCommandFixture.preflightArguments(replay));
        assertThat(rejected.exit()).isEqualTo(24);
        assertThat(stateCounts(jdbc)).isEqualTo(beforeReplay);

        Phase03MigrationCommandFixture.PairFiles higher = fixture.pair(11, "signer-v2", newSigner);
        assertAcceptedPreflight(rollout.services(), higher);
        Map<String, Object> admitted = jdbc.queryForMap(
                "SELECT global_sequence, signer_key_version, "
                        + "LOWER(HEX(pair_digest)) pair_digest FROM ycs_crypto_manifest_pair_admission "
                        + "WHERE singleton_id = 1");
        assertThat(((Number) admitted.get("global_sequence")).longValue()).isEqualTo(11L);
        assertThat(admitted)
                .containsEntry("signer_key_version", "signer-v2")
                .containsEntry("pair_digest", higher.pairDigest());
        return new AcceptedCommand(rollout.services(), higher.pairDigest(), rollout);
    }

    private static void assertHalfAdmissionRollsBack(
            JdbcTemplate jdbc,
            DataSourceTransactionManager transactions,
            MigrationRuntime runtime,
            Phase03MigrationCommandFixture.PairFiles pair) {
        org.springframework.transaction.support.TransactionTemplate transaction =
                new org.springframework.transaction.support.TransactionTemplate(transactions);
        SignedMigrationManifestVerifier.PairAdmissionStore halfStore =
                new SignedMigrationManifestVerifier.PairAdmissionStore() {
                    @Override
                    public java.util.Optional<SignedMigrationManifestVerifier.PairTuple> current() {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public SignedMigrationManifestVerifier.AdmissionDecision compareAndSet(
                            java.util.Optional<SignedMigrationManifestVerifier.PairTuple> expected,
                            SignedMigrationManifestVerifier.PairTuple candidate,
                            MigrationPreflightProperties.SignerAnchor signer) {
                        transaction.executeWithoutResult(status -> {
                            jdbc.update("INSERT INTO ycs_crypto_manifest_pair_admission "
                                            + "(singleton_id,migration_set_id,canonical_subject_digest,"
                                            + "global_sequence,signer_key_version,signer_fingerprint,"
                                            + "writer_digest,snapshot_digest,pair_digest) VALUES "
                                            + "(1,?,UNHEX(?),?,?,UNHEX(?),UNHEX(?),UNHEX(?),UNHEX(?))",
                                    candidate.migrationSetId(), candidate.subjectDigest(),
                                    candidate.globalSequence(), candidate.signerKeyVersion(),
                                    candidate.signerFingerprint(), candidate.writerDigest(),
                                    candidate.snapshotDigest(), candidate.pairDigest());
                            throw new IllegalStateException("simulated pair admission failure");
                        });
                        throw new IllegalStateException("unreachable");
                    }
                };
        SignedMigrationManifestVerifier verifier = new SignedMigrationManifestVerifier(
                Phase03MigrationCommandFixture.properties(List.of(
                        runtime.properties().signerAnchors().getFirst())),
                halfStore, Clock.fixed(Phase03MigrationCommandFixture.NOW, ZoneOffset.UTC));
        ProtectedDataMigrationCommand.DefaultServices services = servicesWithPreflight(
                runtime, invocation -> verifier.verifyAndAdmit(preflightRequest(
                        invocation, Phase03MigrationCommandFixture.MIGRATION_SET)));
        assertRejectedPreflight(jdbc, services, 24,
                Phase03MigrationCommandFixture.preflightArguments(pair));
    }

    private static void assertRejectedPreflight(
            JdbcTemplate jdbc,
            ProtectedDataMigrationCommand.DefaultServices services,
            int expectedExit,
            String[] arguments) {
        resetPairState(jdbc);
        StateCounts before = stateCounts(jdbc);
        Phase03MigrationCommandFixture.CommandResult result =
                Phase03MigrationCommandFixture.invoke(services, arguments);
        assertThat(result.exit()).isEqualTo(expectedExit);
        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).startsWith("phase03-migration:error:");
        assertThat(stateCounts(jdbc)).isEqualTo(before);
    }

    private static void assertAcceptedPreflight(
            ProtectedDataMigrationCommand.DefaultServices services,
            Phase03MigrationCommandFixture.PairFiles pair) {
        Phase03MigrationCommandFixture.CommandResult result = Phase03MigrationCommandFixture.invoke(
                services, Phase03MigrationCommandFixture.preflightArguments(pair));
        assertThat(result.exit()).isZero();
        assertThat(result.stderr()).isEmpty();
        assertThat(result.stdout()).contains("\"status\":\"accepted\"")
                .contains(pair.pairDigest());
    }

    private static WriterFencePort.PairedAdmissionRequest preflightRequest(
            ProtectedDataMigrationCommand.PreflightInvocation invocation,
            String migrationSet) {
        return new WriterFencePort.PairedAdmissionRequest(
                invocation.writerManifest(), invocation.writerSignature(),
                invocation.snapshotManifest(), invocation.snapshotSignature(),
                new WriterFencePort.DeploymentSubject(
                        migrationSet, invocation.environment(),
                        invocation.databaseInstanceFingerprint(), invocation.schema(),
                        invocation.flywaySetDigest()));
    }

    private static MigrationRuntime migrationRuntime(
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            SunPkcs11KeyAdapter adapter,
            KeyProtectionPort fieldKeyPort,
            EnvelopeCodec envelopeCodec,
            MigrationPreflightProperties properties) throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        org.springframework.transaction.support.TransactionTemplate transaction =
                new org.springframework.transaction.support.TransactionTemplate(transactions);
        MigrationStateRepository repository = new MigrationStateRepository.Jdbc(jdbc, transaction);
        SignedMigrationManifestVerifier verifier = new SignedMigrationManifestVerifier(
                properties,
                new SignedMigrationManifestVerifier.JdbcPairAdmissionStore(jdbc, transaction),
                Clock.fixed(Phase03MigrationCommandFixture.NOW, ZoneOffset.UTC));
        Path inventoryPath = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/src/main/resources/security/protected-data-inventory.json");
        byte[] inventory = Files.readAllBytes(inventoryPath);
        ProtectedDataManifest manifest = ProtectedDataManifest.load(
                inventoryPath, ProtectedDataManifest.canonicalDigest(inventory));
        ProtectedFieldCodec fieldCodec = new ProtectedFieldCodec(
                envelopeCodec, fieldKeyPort, new SecureRandom(), "field-kek.v1");
        OpaqueTokenDigestPort.Binding fingerprintBinding =
                new OpaqueTokenDigestPort.Binding("phase03", "migration", "row-fingerprint");
        ProtectedDataMigrationRunner runner = new ProtectedDataMigrationRunner(
                manifest, repository, new LegacyValueClassifier(envelopeCodec), fieldCodec,
                value -> {
                    byte[] bounded = sha256Bytes(value);
                    try {
                        return adapter.issue(
                                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                                fingerprintBinding, bounded).digest();
                    } finally {
                        Arrays.fill(bounded, (byte) 0);
                    }
                },
                new Pkcs11MigrationBlindIndexPort(adapter, jdbc),
                Clock.fixed(Phase03MigrationCommandFixture.NOW, ZoneOffset.UTC));
        MigrationRuntime runtime = new MigrationRuntime(
                dataSource, adapter, envelopeCodec, properties, verifier, repository, runner, null);
        ProtectedDataMigrationCommand.DefaultServices services = servicesWithPreflight(
                runtime, invocation -> verifier.verifyAndAdmit(preflightRequest(
                        invocation, Phase03MigrationCommandFixture.MIGRATION_SET)));
        return runtime.withServices(services);
    }

    private static ProtectedDataMigrationCommand.DefaultServices servicesWithPreflight(
            MigrationRuntime runtime,
            ProtectedDataMigrationCommand.PreflightOperation preflight) {
        return new ProtectedDataMigrationCommand.DefaultServices(
                preflight, runtime.repository(), runtime.runner());
    }

    private static List<IndexedFixture> seedIndexedTargets(JdbcTemplate jdbc) {
        IndexedFixture portability = new IndexedFixture(
                "mobile_portability.mobile_hash", "MOBILE_PORTABILITY",
                "mobile_portability", "mobile_hash", "13700000001", "global", null, 1);
        IndexedFixture blacklist = new IndexedFixture(
                "blacklist_entries.mobile_hash", "BLACKLIST_ENTRY",
                "blacklist_entries", "mobile_hash", "13700000002", "global", 9_300_001L, 2);
        IndexedFixture risk = new IndexedFixture(
                "third_party_risk_check_logs.mobile_hash", "THIRD_PARTY_RISK_CHECK_LOG",
                "third_party_risk_check_logs", "mobile_hash", "13700000003", "global", 9_300_002L, 1);
        IndexedFixture message = new IndexedFixture(
                "message_tasks.mobile_hash", "MESSAGE_TASK",
                "message_tasks", "mobile_hash", "13700000004", "tenant:101", 9_300_003L, 1);
        IndexedFixture unsubscribe = new IndexedFixture(
                "unsubscribe_records.mobile_hash", "UNSUBSCRIBE_RECORD",
                "unsubscribe_records", "mobile_hash", "13700000005", "tenant:101", 9_300_004L, 1);
        jdbc.update("INSERT INTO mobile_portability "
                        + "(mobile_encrypted,mobile_hash,original_operator,current_operator) "
                        + "VALUES (?,?,'MOBILE','UNICOM')",
                "legacy-mobile".getBytes(StandardCharsets.US_ASCII), portability.rawDigest());
        jdbc.update("INSERT INTO blacklist_entries "
                        + "(id,tenant_id,mobile_encrypted,mobile_hash,list_type,source,status) "
                        + "VALUES (9300001,NULL,?,?,'BLACK','MANUAL','ACTIVE')",
                "legacy-mobile".getBytes(StandardCharsets.US_ASCII), blacklist.rawDigest());
        jdbc.update("INSERT INTO blacklist_entries "
                        + "(id,tenant_id,mobile_encrypted,mobile_hash,list_type,source,status) "
                        + "VALUES (9300005,101,?,?,'WHITE','MANUAL','ACTIVE')",
                "legacy-mobile".getBytes(StandardCharsets.US_ASCII), blacklist.rawDigest());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM blacklist_entries WHERE mobile_hash = ? "
                        + "AND ((tenant_id = 101 AND list_type = 'WHITE') "
                        + "OR (tenant_id IS NULL AND list_type = 'BLACK'))",
                Long.class, blacklist.rawDigest())).isEqualTo(2L);
        jdbc.update("INSERT INTO third_party_risk_check_logs "
                        + "(id,request_id,mobile_hash,is_hit) VALUES (9300002,'plan14-risk',?,0)",
                risk.rawDigest());
        jdbc.update("INSERT INTO message_tasks "
                        + "(id,message_id,tenant_id,mobile_encrypted,mobile_hash,content) "
                        + "VALUES (9300003,'plan14-message',101,?,?,'migration proof')",
                "legacy-mobile".getBytes(StandardCharsets.US_ASCII), message.rawDigest());
        jdbc.update("INSERT INTO unsubscribe_records "
                        + "(id,mobile_encrypted,mobile_hash,tenant_id) VALUES (9300004,?,?,101)",
                "legacy-mobile".getBytes(StandardCharsets.US_ASCII), unsubscribe.rawDigest());
        return List.of(portability, blacklist, risk, message, unsubscribe);
    }

    private static void assertIndexedTargetMigrations(
            JdbcTemplate jdbc,
            AcceptedCommand accepted,
            SunPkcs11KeyAdapter adapter,
            List<IndexedFixture> targets) {
        for (IndexedFixture target : targets) {
            assertThat(legacyMatches(jdbc, target)).isEqualTo(target.expectedRows());
            String runId = UUID.randomUUID().toString();
            Phase03MigrationCommandFixture.CommandResult result =
                    Phase03MigrationCommandFixture.invoke(
                            accepted.services(),
                            Phase03MigrationCommandFixture.batchArguments(
                                    "start", runId, target.targetId(), accepted.pairDigest(),
                                    "a".repeat(64), 10));
            assertThat(result.exit()).as(target.targetId()).isZero();
            assertThat(result.stdout()).contains(
                    "\"scanned\":" + target.expectedRows(),
                    "\"migrated\":" + target.expectedRows());
            assertThat(invokeBatch(accepted, "resume", runId, target.targetId(), 10).stdout())
                    .contains("\"scanned\":0", "\"end_of_target\":true");

            List<Map<String, Object>> metadata = jdbc.queryForList(
                    "SELECT legacy_row_id,key_version,index_value,index_status,"
                            + "LOWER(HEX(original_row_digest)) original_digest "
                            + "FROM ycs_crypto_blind_indexes WHERE target_type = ? "
                            + "AND field_id = 'mobile' ORDER BY legacy_row_id,key_version",
                    target.targetType());
            assertThat(metadata).hasSize(target.expectedRows());
            com.ycsopen.sms.core.common.security.key.BlindIndexPort.OrderedIndexes online =
                    adapter.queryIndexes(
                            target.mobile(), new com.ycsopen.sms.core.common.security.key.BlindIndexPort.Context(
                                    target.targetType(), "mobile",
                                    com.ycsopen.sms.core.common.security.key.BlindIndexPort.Purpose.MOBILE_ROUTING,
                                    target.tenantScope()));
            assertThat(metadata.stream().map(row -> row.get("index_value")).toList())
                    .contains(online.values().getFirst().canonicalValue());
            assertThat(((Number) metadata.getFirst().get("key_version")).longValue()).isOne();
            assertThat(metadata.getFirst().get("index_status")).isEqualTo("ACTIVE");
            if ("BLACKLIST_ENTRY".equals(target.targetType())) {
                var tenantOnline = adapter.queryIndexes(
                        target.mobile(), new com.ycsopen.sms.core.common.security.key.BlindIndexPort.Context(
                                target.targetType(), "mobile",
                                com.ycsopen.sms.core.common.security.key.BlindIndexPort.Purpose.MOBILE_ROUTING,
                                "tenant:101"));
                assertThat(metadata.stream().map(row -> row.get("index_value")).toList())
                        .contains(tenantOnline.values().getFirst().canonicalValue());
            }
            assertThat(legacyMatches(jdbc, target)).isEqualTo(target.expectedRows());

            advanceOne(accepted, runId, target.targetId(), "BACKFILLED");
            advanceOne(accepted, runId, target.targetId(), "VERIFIED");
            advanceOne(accepted, runId, target.targetId(), "CUTOVER");
            assertThat(legacyMatches(jdbc, target)).isEqualTo(target.expectedRows());
            assertThat(jdbc.queryForObject(
                    "SELECT legacy_fallback_allowed FROM ycs_crypto_migration_targets "
                            + "WHERE target_type = ?", Boolean.class, target.targetType())).isTrue();

            advanceOne(accepted, runId, target.targetId(), "SCRUBBED");
            assertThat(legacyMatches(jdbc, target)).isZero();
            assertScrubBinding(jdbc, target, metadata);
            advanceOne(accepted, runId, target.targetId(), "COMPLETE");
            assertThat(jdbc.queryForObject(
                    "SELECT CONCAT(target_state, ':', legacy_fallback_allowed) "
                            + "FROM ycs_crypto_migration_targets WHERE target_type = ?",
                    String.class, target.targetType())).isEqualTo("COMPLETE:0");
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_blind_indexes", Long.class)).isEqualTo(6L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_migration_targets "
                        + "WHERE target_disposition <> 'PROTECTED_NO_INDEX' "
                        + "AND target_state = 'COMPLETE' AND legacy_fallback_allowed = FALSE",
                Long.class)).isEqualTo(5L);
    }

    private static long legacyMatches(JdbcTemplate jdbc, IndexedFixture target) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + target.table() + " WHERE " + target.column() + " = ?",
                Long.class, target.rawDigest());
    }

    private static void assertScrubBinding(
            JdbcTemplate jdbc, IndexedFixture target, List<Map<String, Object>> metadata) {
        String scrubbed;
        if (target.rowId() == null) {
            scrubbed = jdbc.queryForObject(
                    "SELECT mobile_hash FROM mobile_portability", String.class);
            String expectedPrefix = String.format("%016x",
                    ((Number) metadata.getFirst().get("legacy_row_id")).longValue());
            assertThat(scrubbed).startsWith(expectedPrefix);
        } else {
            scrubbed = jdbc.queryForObject(
                    "SELECT " + target.column() + " FROM " + target.table() + " WHERE id = ?",
                    String.class, target.rowId());
            assertThat(scrubbed).startsWith(String.format("%016x", target.rowId()));
        }
        assertThat(scrubbed).hasSize(64).isNotEqualTo(target.rawDigest());
        assertThat(metadata.getFirst().get("original_digest")).isEqualTo(
                HexFormat.of().formatHex(sha256Bytes(
                        target.rawDigest().getBytes(StandardCharsets.US_ASCII))));
        if ("BLACKLIST_ENTRY".equals(target.targetType())) {
            assertThat(jdbc.queryForList(
                    "SELECT mobile_hash FROM blacklist_entries WHERE id IN (9300001,9300005) "
                            + "ORDER BY id", String.class))
                    .allSatisfy(locator -> assertThat(locator).hasSize(64)
                            .isNotEqualTo(target.rawDigest()));
        }
    }

    private static void assertNoIndexTargetMigration(
            JdbcTemplate jdbc,
            AcceptedCommand accepted,
            SunPkcs11KeyAdapter adapter,
            EnvelopeCodec envelopeCodec,
            String target,
            String targetType,
            long firstId,
            String mobile) {
        String table = target.substring(0, target.indexOf('.'));
        byte[] malformedMagic = "YCSE-not-an-envelope".getBytes(StandardCharsets.US_ASCII);
        insertNoIndexRow(jdbc, table, firstId - 3, malformedMagic);
        assertBatchRejectedWithoutState(jdbc, accepted, target);
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", firstId - 3);

        insertNoIndexRow(jdbc, table, firstId - 2, new byte[]{(byte) 0xc3, 0x28});
        assertBatchRejectedWithoutState(jdbc, accepted, target);
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", firstId - 2);

        insertNoIndexRow(jdbc, table, firstId - 1,
                "1".repeat(12).getBytes(StandardCharsets.US_ASCII));
        assertBatchRejectedWithoutState(jdbc, accepted, target);
        jdbc.update("DELETE FROM " + table + " WHERE id = ?", firstId - 1);

        assertThatThrownBy(() -> insertNoIndexRow(jdbc, table, firstId - 4, null))
                .isInstanceOf(DataAccessException.class);

        byte[] plaintext = mobile.getBytes(StandardCharsets.US_ASCII);
        byte[] existingPlaintext = "13600136000".getBytes(StandardCharsets.US_ASCII);
        ProtectedFieldCodec codec = new ProtectedFieldCodec(
                envelopeCodec, adapter, new SecureRandom(), "field-kek.v1");
        byte[] existingEnvelope = codec.protect(
                existingPlaintext, migrationContext(table, firstId + 1),
                EnvelopeCodec.Target.DATABASE_FIELD);
        insertNoIndexRow(jdbc, table, firstId, plaintext);
        insertNoIndexRow(jdbc, table, firstId + 1, existingEnvelope);

        String runId = UUID.randomUUID().toString();
        Phase03MigrationCommandFixture.CommandResult start = invokeBatch(
                accepted, "start", runId, target, 1);
        assertThat(start.exit()).isZero();
        assertThat(start.stdout()).contains("\"scanned\":1", "\"migrated\":1");
        Phase03MigrationCommandFixture.CommandResult pause = Phase03MigrationCommandFixture.invoke(
                accepted.services(), "pause", "--run-id", runId,
                "--pair-digest", accepted.pairDigest());
        assertThat(pause.exit()).isZero();
        Phase03MigrationCommandFixture.CommandResult resume = invokeBatch(
                accepted, "resume", runId, target, 1);
        assertThat(resume.exit()).isZero();
        assertThat(invokeBatch(accepted, "resume", runId, target, 1).stdout())
                .contains("\"scanned\":0", "\"end_of_target\":true");

        advanceAll(accepted, runId, target);
        assertThat(jdbc.queryForObject(
                "SELECT CONCAT(target_state, ':', legacy_fallback_allowed) "
                        + "FROM ycs_crypto_migration_targets WHERE target_type = ?",
                String.class, targetType)).isEqualTo("COMPLETE:0");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_blind_indexes WHERE target_type = ?",
                Long.class, targetType)).isZero();

        byte[] migrated = jdbc.queryForObject(
                "SELECT mobile_encrypted FROM " + table + " WHERE id = ?",
                byte[].class, firstId);
        byte[] retained = jdbc.queryForObject(
                "SELECT mobile_encrypted FROM " + table + " WHERE id = ?",
                byte[].class, firstId + 1);
        assertThat(migrated).startsWith((byte) 'Y', (byte) 'C', (byte) 'S', (byte) 'E');
        assertThat(retained).isEqualTo(existingEnvelope);
        assertThat(codec.unprotect(
                migrated, migrationContext(table, firstId), EnvelopeCodec.Target.DATABASE_FIELD))
                .isEqualTo(plaintext);
        assertThat(codec.unprotect(
                retained, migrationContext(table, firstId + 1), EnvelopeCodec.Target.DATABASE_FIELD))
                .isEqualTo(existingPlaintext);
        Arrays.fill(plaintext, (byte) 0);
        Arrays.fill(existingPlaintext, (byte) 0);
        Arrays.fill(existingEnvelope, (byte) 0);
        Arrays.fill(migrated, (byte) 0);
        Arrays.fill(retained, (byte) 0);
    }

    private static void assertConcurrentMutationAndNoIndexMigration(
            JdbcTemplate jdbc,
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            AcceptedCommand accepted,
            SunPkcs11KeyAdapter adapter,
            EnvelopeCodec envelopeCodec,
            long rowId,
            String original) throws Exception {
        byte[] originalBytes = original.getBytes(StandardCharsets.US_ASCII);
        insertNoIndexRow(jdbc, "uplink_records", rowId, originalBytes);
        BlockingKeyPort blocking = new BlockingKeyPort(adapter);
        MigrationRuntime racing = migrationRuntime(
                dataSource, transactions, adapter, blocking, envelopeCodec,
                accepted.runtime().properties());
        String runId = UUID.randomUUID().toString();
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<Phase03MigrationCommandFixture.CommandResult> result = executor.submit(() ->
                    Phase03MigrationCommandFixture.invoke(
                            racing.services(), Phase03MigrationCommandFixture.batchArguments(
                                    "start", runId, "uplink_records.mobile_encrypted",
                                    accepted.pairDigest(), "a".repeat(64), 10)));
            blocking.awaitWrap();
            jdbc.update("UPDATE uplink_records SET mobile_encrypted = ? WHERE id = ?",
                    "13500135000".getBytes(StandardCharsets.US_ASCII), rowId);
            blocking.release();
            assertThat(result.get().exit()).isEqualTo(26);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_migration_runs WHERE migration_run_id = ?",
                Long.class, runId)).isZero();

        String retryRun = UUID.randomUUID().toString();
        assertThat(invokeBatch(
                accepted, "start", retryRun, "uplink_records.mobile_encrypted", 10).exit()).isZero();
        advanceAll(accepted, retryRun, "uplink_records.mobile_encrypted");
        byte[] stored = jdbc.queryForObject(
                "SELECT mobile_encrypted FROM uplink_records WHERE id = ?", byte[].class, rowId);
        assertThat(stored).startsWith((byte) 'Y', (byte) 'C', (byte) 'S', (byte) 'E');
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ycs_crypto_blind_indexes "
                        + "WHERE target_type = 'UPLINK_RECORD_MOBILE'",
                Long.class)).isZero();
        Arrays.fill(originalBytes, (byte) 0);
        Arrays.fill(stored, (byte) 0);
    }

    private static void assertBatchRejectedWithoutState(
            JdbcTemplate jdbc, AcceptedCommand accepted, String target) {
        StateCounts before = stateCounts(jdbc);
        Phase03MigrationCommandFixture.CommandResult result = invokeBatch(
                accepted, "start", UUID.randomUUID().toString(), target, 10);
        assertThat(result.exit()).isEqualTo(26);
        assertThat(stateCounts(jdbc)).isEqualTo(before);
    }

    private static Phase03MigrationCommandFixture.CommandResult invokeBatch(
            AcceptedCommand accepted,
            String operation,
            String runId,
            String target,
            int batchSize) {
        return Phase03MigrationCommandFixture.invoke(
                accepted.services(), Phase03MigrationCommandFixture.batchArguments(
                        operation, runId, target, accepted.pairDigest(), "a".repeat(64), batchSize));
    }

    private static void advanceAll(
            AcceptedCommand accepted, String runId, String target) {
        for (String state : List.of("BACKFILLED", "VERIFIED", "CUTOVER", "SCRUBBED", "COMPLETE")) {
            advanceOne(accepted, runId, target, state);
        }
    }

    private static void advanceOne(
            AcceptedCommand accepted, String runId, String target, String state) {
        Phase03MigrationCommandFixture.CommandResult result =
                Phase03MigrationCommandFixture.invoke(
                        accepted.services(), Phase03MigrationCommandFixture.advanceArguments(
                                runId, target, accepted.pairDigest(), "a".repeat(64), state));
        assertThat(result.exit()).as(target + ":" + state).isZero();
        assertThat(result.stdout()).contains("\"current_state\":\"" + state + "\"");
    }

    private static void insertNoIndexRow(
            JdbcTemplate jdbc, String table, long id, byte[] value) {
        if ("bulk_sending_items".equals(table)) {
            jdbc.update("INSERT INTO bulk_sending_items (id,bulk_id,mobile_encrypted) VALUES (?,?,?)",
                    id, 9000, value);
        } else if ("uplink_records".equals(table)) {
            jdbc.update("INSERT INTO uplink_records (id,tenant_id,mobile_encrypted,content) "
                            + "VALUES (?,101,?,'migration proof')",
                    id, value);
        } else {
            throw new IllegalArgumentException("unreviewed no-index table");
        }
    }

    private static ProtectionContext migrationContext(String table, long id) {
        return new ProtectionContext(
                ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", table, "mobile_encrypted",
                "uplink_records".equals(table) ? "tenant:101" : "global",
                "id=" + id);
    }

    private static StateCounts stateCounts(JdbcTemplate jdbc) {
        return new StateCounts(
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_manifest_pair_admission", Long.class),
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_migration_runs", Long.class),
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_migration_checkpoints", Long.class),
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ycs_crypto_migration_events", Long.class),
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(optimistic_version),0) "
                                + "FROM ycs_crypto_migration_targets", Long.class));
    }

    private static long pairOptimisticVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT optimistic_version FROM ycs_crypto_manifest_pair_admission "
                        + "WHERE singleton_id = 1", Long.class);
    }

    private static void resetPairState(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM ycs_crypto_migration_events");
        jdbc.update("DELETE FROM ycs_crypto_migration_checkpoints");
        jdbc.update("DELETE FROM ycs_crypto_migration_runs");
        jdbc.update("DELETE FROM ycs_crypto_manifest_pair_admission");
    }

    private static void resetMigrationProof(JdbcTemplate jdbc) {
        resetPairState(jdbc);
        jdbc.update("DELETE FROM ycs_crypto_blind_indexes");
        jdbc.update("UPDATE ycs_crypto_migration_targets SET target_state = 'DISCOVERED', "
                + "legacy_fallback_allowed = CASE WHEN target_disposition = 'PROTECTED_NO_INDEX' "
                + "THEN FALSE ELSE TRUE END, optimistic_version = 0");
        jdbc.update("DELETE FROM mobile_portability");
        jdbc.update("DELETE FROM blacklist_entries WHERE id BETWEEN 9000000 AND 9999999");
        jdbc.update("DELETE FROM third_party_risk_check_logs WHERE id BETWEEN 9000000 AND 9999999");
        jdbc.update("DELETE FROM message_tasks WHERE id BETWEEN 9000000 AND 9999999");
        jdbc.update("DELETE FROM unsubscribe_records WHERE id BETWEEN 9000000 AND 9999999");
        jdbc.update("DELETE FROM bulk_sending_items WHERE id BETWEEN 9000000 AND 9999999");
        jdbc.update("DELETE FROM uplink_records WHERE id BETWEEN 9000000 AND 9999999");
    }

    private static String legacyDigest(String mobile) {
        return HexFormat.of().formatHex(sha256Bytes(
                mobile.getBytes(StandardCharsets.US_ASCII)));
    }

    private static void writeMigrationEvidence(JdbcTemplate jdbc, String pairDigest) throws Exception {
        List<Map<String, Object>> targetRows = jdbc.queryForList(
                "SELECT target_type,target_state,legacy_fallback_allowed "
                        + "FROM ycs_crypto_migration_targets ORDER BY target_type");
        List<Map<String, Object>> counts = new ArrayList<>();
        for (Map<String, Object> row : targetRows) {
            String targetType = row.get("target_type").toString();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("target_type", targetType);
            value.put("target_state", row.get("target_state").toString());
            value.put("legacy_fallback_allowed",
                    Boolean.TRUE.equals(row.get("legacy_fallback_allowed"))
                            || Integer.valueOf(1).equals(row.get("legacy_fallback_allowed")));
            value.put("checkpoint_count", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ycs_crypto_migration_checkpoints WHERE target_type = ?",
                    Long.class, targetType));
            value.put("event_count", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ycs_crypto_migration_events WHERE target_type = ?",
                    Long.class, targetType));
            value.put("blind_index_count", jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ycs_crypto_blind_indexes WHERE target_type = ?",
                    Long.class, targetType));
            counts.add(value);
        }
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema_version", "phase03-migration-inventory/v1");
        evidence.put("accepted_pair_digest", pairDigest);
        evidence.put("v1_sha256", V1_SHA256);
        evidence.put("target_count", counts.size());
        evidence.put("complete_target_count", counts.stream()
                .filter(row -> "COMPLETE".equals(row.get("target_state"))).count());
        evidence.put("blocking_target_count", counts.stream()
                .filter(row -> "DISCOVERED".equals(row.get("target_state"))).count());
        evidence.put("indexed_target_set_digest", indexedTargetSetDigest());
        evidence.put("targets", counts);
        Path output = Phase01ServiceHarness.repositoryRoot()
                .resolve("core/target/phase03/migration-inventory.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output,
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .writerWithDefaultPrettyPrinter().writeValueAsString(evidence) + "\n",
                StandardCharsets.UTF_8);
    }

    private static String indexedTargetSetDigest() throws Exception {
        String targets = String.join("\n", List.of(
                "blacklist_entries.mobile_hash",
                "message_tasks.mobile_hash",
                "mobile_portability.mobile_hash",
                "third_party_risk_check_logs.mobile_hash",
                "unsubscribe_records.mobile_hash"));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(targets.getBytes(StandardCharsets.US_ASCII)));
    }

    private record StateCounts(
            long admittedPairs,
            long runs,
            long checkpoints,
            long events,
            long targetOptimisticVersionSum) {
    }

    private record AcceptedCommand(
            ProtectedDataMigrationCommand.DefaultServices services,
            String pairDigest,
            MigrationRuntime runtime) {
    }

    private record IndexedFixture(
            String targetId,
            String targetType,
            String table,
            String column,
            String mobile,
            String tenantScope,
            Long rowId,
            int expectedRows) {

        private String rawDigest() {
            return legacyDigest(mobile);
        }
    }

    private record MigrationRuntime(
            DataSource dataSource,
            SunPkcs11KeyAdapter adapter,
            EnvelopeCodec envelopeCodec,
            MigrationPreflightProperties properties,
            SignedMigrationManifestVerifier verifier,
            MigrationStateRepository repository,
            ProtectedDataMigrationRunner runner,
            ProtectedDataMigrationCommand.DefaultServices services) {

        private MigrationRuntime withServices(
                ProtectedDataMigrationCommand.DefaultServices commandServices) {
            return new MigrationRuntime(
                    dataSource, adapter, envelopeCodec, properties, verifier,
                    repository, runner, commandServices);
        }
    }

    private static final class BlockingKeyPort implements KeyProtectionPort {
        private final KeyProtectionPort delegate;
        private final CountDownLatch wrapping = new CountDownLatch(1);
        private final CountDownLatch continueWrap = new CountDownLatch(1);
        private final AtomicBoolean first = new AtomicBoolean(true);

        private BlockingKeyPort(KeyProtectionPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public WrappedDataKey wrap(
                byte[] dataEncryptionKey,
                byte[] authenticatedHeader,
                ProtectionContext semanticContext) {
            if (first.compareAndSet(true, false)) {
                wrapping.countDown();
                try {
                    continueWrap.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("migration race proof interrupted", interrupted);
                }
            }
            return delegate.wrap(dataEncryptionKey, authenticatedHeader, semanticContext);
        }

        @Override
        public byte[] unwrap(
                WrappedDataKey wrappedDataKey,
                byte[] authenticatedHeader,
                ProtectionContext semanticContext) {
            return delegate.unwrap(wrappedDataKey, authenticatedHeader, semanticContext);
        }

        @Override
        public com.ycsopen.sms.core.common.security.key.KeyHealth health() {
            return delegate.health();
        }

        private void awaitWrap() throws InterruptedException {
            wrapping.await();
        }

        private void release() {
            continueWrap.countDown();
        }
    }

    private static byte[] canonicalWriter(
            SnapshotManifest.Subject subject, Instant now, String sourceDigest) {
        String json = "{"
                + "\"database_instance_fingerprint\":\"" + subject.databaseInstanceFingerprint() + "\","
                + "\"environment\":\"" + subject.environment() + "\","
                + "\"expires_at\":\"" + now.plusSeconds(3600) + "\","
                + "\"flyway_set_digest\":\"" + subject.flywaySetDigest() + "\","
                + "\"global_sequence\":" + subject.globalSequence() + ","
                + "\"issued_at\":\"" + now.minusSeconds(1) + "\","
                + "\"manifest_schema\":\"ycs-writer-fence/v1\","
                + "\"migration_set_id\":\"" + subject.migrationSetId() + "\","
                + "\"schema\":\"" + subject.schema() + "\","
                + "\"signer_key_version\":\"" + subject.signerKeyVersion() + "\","
                + "\"writers\":[{\"artifact_id\":\"ycsopen-sms-core\","
                + "\"migration_compatible\":true,\"source_digest\":\"" + sourceDigest + "\","
                + "\"version\":\"1.0.0\"}]}";
        return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] pairDigest(
            SnapshotManifest.Subject subject, byte[] writerDigest, byte[] snapshotDigest)
            throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes("YCS-MIGRATION-PAIR/v1\0"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        for (String value : List.of(
                subject.migrationSetId(), subject.environment(),
                subject.databaseInstanceFingerprint(), subject.schema(),
                subject.flywaySetDigest())) {
            writeLengthPrefixed(output, value);
        }
        output.writeBytes(java.nio.ByteBuffer.allocate(Long.BYTES)
                .putLong(subject.globalSequence()).array());
        writeLengthPrefixed(output, subject.signerKeyVersion());
        output.writeBytes(writerDigest);
        output.writeBytes(snapshotDigest);
        return MessageDigest.getInstance("SHA-256").digest(output.toByteArray());
    }

    private static byte[] sign(
            KeyPair signer, byte role, byte[] pairDigest, byte[] roleDigest) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(signer.getPrivate());
        signature.update("YCS-MIGRATION-PAIR-SIGNATURE/v1\0"
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        signature.update(role);
        signature.update(pairDigest);
        signature.update(roleDigest);
        return signature.sign();
    }

    private static void writeLengthPrefixed(ByteArrayOutputStream output, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        output.writeBytes(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        output.writeBytes(bytes);
    }

    private static void requireFreshSchema(
            JdbcTemplate jdbc,
            MySqlSnapshotProcess.Database source,
            MySqlSnapshotProcess.Database target) {
        if (!source.host().equals(target.host()) || source.port() != target.port()
                || source.schema().equals(target.schema())) {
            throw new IllegalStateException("fresh restore schema boundary rejected");
        }
        Long schemas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Long.class, target.schema());
        Long tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?",
                Long.class, target.schema());
        if (!Long.valueOf(1).equals(schemas) || !Long.valueOf(0).equals(tables)) {
            throw new IllegalStateException("fresh restore schema boundary rejected");
        }
    }

    private static void assertEncryptedChunksExclude(
            Path root, byte[] marker, int expectedCount) throws Exception {
        List<Path> chunkPaths;
        try (var paths = Files.walk(root)) {
            chunkPaths = paths.filter(path -> path.getFileName().toString().endsWith(".ycse"))
                    .sorted().toList();
        }
        assertThat(chunkPaths).hasSize(expectedCount);
        for (Path path : chunkPaths) {
            assertThat(contains(Files.readAllBytes(path), marker)).isFalse();
        }
        try (var paths = Files.walk(root)) {
            assertThat(paths.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .toList()).isEmpty();
        }
    }

    private static boolean contains(byte[] input, byte[] marker) {
        outer:
        for (int offset = 0; offset <= input.length - marker.length; offset++) {
            for (int index = 0; index < marker.length; index++) {
                if (input[offset + index] != marker[index]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] repeated(byte[] marker, int size) {
        byte[] value = new byte[size];
        for (int offset = 0; offset < size; offset += marker.length) {
            System.arraycopy(marker, 0, value, offset, Math.min(marker.length, size - offset));
        }
        return value;
    }

    private static void seedSnapshotKeyMetadata(JdbcTemplate jdbc) {
        insertSnapshotKey(jdbc, "FIELD_ENCRYPTION_KEK", "field-kek.v1");
        insertSnapshotKey(jdbc, "SNAPSHOT_RECOVERY", "snapshot-recovery.v1");
        insertSnapshotKey(jdbc, "MOBILE_BLIND_INDEX", "mobile-index.v1");
        insertSnapshotKey(jdbc, "OBJECT_CAPABILITY_DIGEST", "object-digest.v1");
        insertSnapshotKey(jdbc, "REGISTRATION_UPLOAD_DIGEST", "registration-digest.v1");
    }

    private static void insertSnapshotKey(JdbcTemplate jdbc, String purpose, String reference) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES (? ,1,'pkcs11',?,'ACTIVE')",
                purpose, reference);
    }

    private static AdapterRuntime openSnapshotAdapter(
            DataSource dataSource, DataSourceTransactionManager transactions) throws Exception {
        Path library = Path.of(requiredEnvironment("PHASE03_HSM_LIBRARY"))
                .toAbsolutePath().normalize().toRealPath();
        Path pinSource = Path.of(requiredEnvironment("PHASE03_HSM_PIN_SOURCE"));
        List<String> pins = Files.readAllLines(pinSource);
        char[] userPin = pins.get(1).toCharArray();
        List<Pkcs11KeyDescriptor> descriptors = List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK,
                        "field-kek.v1", "ycs.field-encryption-kek.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY,
                        "snapshot-recovery.v1", "ycs.snapshot-recovery.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX,
                        "mobile-index.v1", "ycs.mobile-blind-index.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST,
                        "object-digest.v1", "ycs.object-capability-digest.v1"),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST,
                        "registration-digest.v1", "ycs.registration-upload-digest.v1"));
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                library, List.of(library), Long.parseUnsignedLong(requiredEnvironment("PHASE03_HSM_SLOT")),
                "phase03-snapshot", () -> userPin.clone(), descriptors);
        Pkcs11FailureMapper mapper = new Pkcs11FailureMapper();
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(mapper).open(properties);
        try {
            SunPkcs11KeyAdapter adapter = new SunPkcs11KeyAdapter(
                    session, properties,
                    new KekWrapUsageRepository(new JdbcTemplate(dataSource), transactions, mapper), mapper);
            java.util.Arrays.fill(userPin, '\0');
            return new AdapterRuntime(session, adapter);
        } catch (RuntimeException failure) {
            java.util.Arrays.fill(userPin, '\0');
            session.close();
            throw failure;
        }
    }

    private static Pkcs11KeyDescriptor descriptor(
            Pkcs11KeyDescriptor.Purpose purpose, String reference, String alias) {
        return new Pkcs11KeyDescriptor(
                purpose, 1, reference, alias, Pkcs11KeyDescriptor.State.ACTIVE,
                purpose.isWrappingKey() ? "AES" : "HmacSHA256", 256);
    }

    private static DriverManagerDataSource snapshotDataSource(String schema) {
        String url = "jdbc:mysql://" + requiredEnvironment("PHASE03_MYSQL_HOST") + ":"
                + requiredEnvironment("PHASE03_MYSQL_PORT") + "/" + schema
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        return new DriverManagerDataSource(
                url, requiredEnvironment("PHASE03_MYSQL_USER"),
                requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
    }

    private static void migrateSnapshotQuietly(DataSource dataSource) {
        PrintStream original = System.out;
        try (PrintStream discarded = new PrintStream(
                java.io.OutputStream.nullOutputStream(), true,
                java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(discarded);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .placeholderReplacement(false).load().migrate();
        } finally {
            System.setOut(original);
        }
    }

    private static DriverManagerDataSource snapshotAdminDataSource(String schema) {
        String url = "jdbc:mysql://" + requiredEnvironment("PHASE03_MYSQL_HOST") + ":"
                + requiredEnvironment("PHASE03_MYSQL_PORT") + "/" + schema
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        return new DriverManagerDataSource(
                url, "root", requiredEnvironment("PHASE03_MYSQL_ROOT_PASSWORD"));
    }

    private static MySqlSnapshotProcess.Database database(String schema, boolean root) {
        return new MySqlSnapshotProcess.Database(
                "127.0.0.1", 3306,
                root ? "root" : requiredEnvironment("PHASE03_MYSQL_USER"),
                requiredEnvironment(root
                        ? "PHASE03_MYSQL_ROOT_PASSWORD"
                        : "PHASE03_MYSQL_PASSWORD").toCharArray(), schema);
    }

    private static Path findExecutable(String name) {
        String path = requiredEnvironment("PATH");
        for (String entry : path.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(entry).resolve(name);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("required MySQL client unavailable");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("integration environment unavailable: " + name);
        }
        return value;
    }

    private static byte[] sha256Bytes(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    private record AdmissionProof(int exit, WriterFencePort.PairedAdmission admission) {
    }

    private record AdapterRuntime(
            Pkcs11ProviderFactory.Session session,
            SunPkcs11KeyAdapter adapter) implements AutoCloseable {
        @Override
        public void close() {
            session.close();
        }
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
