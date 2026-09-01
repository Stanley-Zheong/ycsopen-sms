package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
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
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
        } finally {
            java.util.Arrays.fill(payload, (byte) 0);
            admin.execute("DROP DATABASE IF EXISTS `" + restoreSchema + "`");
            admin.execute("DROP DATABASE IF EXISTS `" + rejectedSchema + "`");
        }
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
