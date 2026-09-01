package com.ycsopen.sms.core.verification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.exception.GlobalExceptionHandler;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.logging.LeakScanReport;
import com.ycsopen.sms.core.common.security.logging.Phase03LeakScanCommand;
import com.ycsopen.sms.core.common.security.logging.SecurityEventLogger;
import com.ycsopen.sms.core.common.security.logging.SecurityRedactionConverter;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.CanaryKind;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.Surface;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.SurfaceItem;
import com.ycsopen.sms.core.common.security.logging.SensitiveDataLeakScanner.SurfaceTarget;
import com.ycsopen.sms.core.common.security.object.ObjectStoreProperties;
import com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort;
import com.ycsopen.sms.core.common.security.object.S3PrivateObjectStoreAdapter;
import com.ycsopen.sms.core.common.security.object.StoredObjectMetadata;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real MySQL, MinIO and SoftHSM proof that canaries do not escape protected boundaries. */
@EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
class Phase03LeakScanIntegrationTest {

    private static final String FIELD_REFERENCE = "field-main.v1";
    private static final String FIELD_ALIAS = "ycs.field-encryption-kek.v1";
    private static final String SNAPSHOT_ALIAS = "ycs.snapshot-recovery.v1";
    private static final String MOBILE_ALIAS = "ycs.mobile-blind-index.v1";
    private static final String OBJECT_ALIAS = "ycs.object-capability-digest.v1";
    private static final String UPLOAD_ALIAS = "ycs.registration-upload-digest.v1";
    private static final String MINIO_IMAGE =
            "minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e";
    private static final String ARTIFACT_REPORT =
            "core/target/phase03/artifact-leak-integration.json";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void provesGeneratedCanariesAcrossRealProductionBoundaries() throws Exception {
        String output;
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            Phase03ServiceHarness.ServiceSession mysql = fixtures.mysql();
            Phase03ServiceHarness.ServiceSession minio = fixtures.minio();
            Phase03ServiceHarness.SoftHsmHandoff handoff = fixtures.softHsm().softHsm();
            Path destination = handoff.config().getParent().getParent();
            Path java = Path.of(System.getProperty("java.home"), "bin", "java");
            String classpath = System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path"));
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("SOFTHSM2_CONF", handoff.config().toString());
            environment.put("PHASE03_SOFTHSM_DESTINATION", destination.toString());
            environment.put("PHASE03_MYSQL_HOST", mysql.host());
            environment.put("PHASE03_MYSQL_PORT", Integer.toString(mysql.port()));
            environment.put("PHASE03_MYSQL_USER", mysql.username());
            environment.put("PHASE03_MYSQL_PASSWORD", mysql.password());
            environment.put("PHASE03_MINIO_HOST", minio.host());
            environment.put("PHASE03_MINIO_PORT", Integer.toString(minio.port()));
            environment.put("PHASE03_MINIO_USER", minio.username());
            environment.put("PHASE03_MINIO_PASSWORD", minio.password());

            Phase03ServiceHarness.runChecked(List.of("/usr/bin/env", "ruby",
                    repositoryRoot().resolve(".planning/tools/scan-phase-03-artifacts.rb").toString(),
                    "--phase-dir", ".planning/phases/03-crypto-storage-bootstrap",
                    "--generated-root", "core/target/phase03",
                    "--output", ARTIFACT_REPORT), Map.of());
            // Reuse the real integration provisioner; it creates the purpose-separated keys and
            // database metadata consumed below without exposing fixture credentials to this JVM.
            Phase03ServiceHarness.runChecked(List.of(java.toString(), "-cp", classpath,
                    Phase03Pkcs11IntegrationTest.class.getName(), "real-proof"), environment);
            output = Phase03ServiceHarness.runChecked(List.of(java.toString(), "-cp", classpath,
                    Phase03LeakScanIntegrationTest.class.getName(), "real-proof"), environment)
                    .stdout().strip();
            List<String> passLines = output.lines()
                    .filter(line -> line.startsWith("PHASE03_LEAK_SCAN_PASS ")).toList();
            assertThat(passLines).hasSize(1);
            assertThat(passLines.getFirst()).matches(
                    "PHASE03_LEAK_SCAN_PASS subject_sha256=[a-f0-9]{64} "
                            + "mysql_sha256=[a-f0-9]{64} minio_sha256=[a-f0-9]{64} "
                            + "pkcs11_sha256=[a-f0-9]{64} result_sha256=[a-f0-9]{64} "
                            + "targets=5 seeded_detection=1 cleaned=1");
            assertThat(output.toLowerCase()).doesNotContain(
                    "password", "secret", "credential=", "pin", "alias", "path=", "token=");
        }
        assertThat(output).contains("PHASE03_LEAK_SCAN_PASS");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"real-proof".equals(args[0])) {
            throw new IllegalArgumentException("closed integration invocation required");
        }
        runRealProof();
    }

    private static void runRealProof() throws Exception {
        Path destination = Path.of(requiredEnvironment("PHASE03_SOFTHSM_DESTINATION"));
        Phase03ServiceHarness.SoftHsmHandoff handoff = Phase03ServiceHarness.readHandoff(destination);
        DataSource dataSource = mysqlDataSource();
        migrateQuietly(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        URI endpoint = URI.create("http://" + requiredEnvironment("PHASE03_MINIO_HOST") + ":"
                + requiredEnvironment("PHASE03_MINIO_PORT"));
        String suffix = randomHex(6);
        String table = "phase03_leak_scan_" + suffix;
        String bucket = "phase03-leak-scan-" + suffix;
        String storageKey = null;

        SensitiveDataLeakScanner.ScanSession scan = new SensitiveDataLeakScanner().begin();
        var canaries = scan.canaries();
        try (S3Client s3 = s3(endpoint);
             AdapterRuntime runtime = openAdapter(handoff, dataSource, transactions)) {
            s3.createBucket(request -> request.bucket(bucket));
            jdbc.execute("CREATE TABLE " + table + " ("
                    + "id BIGINT PRIMARY KEY, phone_envelope LONGBLOB NOT NULL, "
                    + "identity_envelope LONGBLOB NOT NULL, credential_digest BINARY(32) NOT NULL, "
                    + "capability_digest BINARY(32) NOT NULL, object_key VARCHAR(80) NOT NULL)");
            try {
                ProtectionContext databaseContext = new ProtectionContext(
                        ProtectionContext.Purpose.DATABASE_FIELD, "phase03-leak-proof",
                        table, "canary-fields", "tenant:leak-proof", "row:1");
                ProtectedFieldCodec codec = new ProtectedFieldCodec(
                        new EnvelopeCodec(), runtime.adapter(), new SecureRandom(), FIELD_REFERENCE);
                byte[] phone = utf8(canaries.value(CanaryKind.PHONE));
                byte[] identity = utf8(canaries.value(CanaryKind.IDENTITY));
                byte[] phoneEnvelope = codec.protect(
                        phone, databaseContext, EnvelopeCodec.Target.DATABASE_FIELD);
                byte[] identityEnvelope = codec.protect(
                        identity, databaseContext, EnvelopeCodec.Target.DATABASE_FIELD);
                assertThat(MessageDigest.isEqual(phone,
                        codec.unprotect(phoneEnvelope, databaseContext,
                                EnvelopeCodec.Target.DATABASE_FIELD))).isTrue();
                assertThat(MessageDigest.isEqual(identity,
                        codec.unprotect(identityEnvelope, databaseContext,
                                EnvelopeCodec.Target.DATABASE_FIELD))).isTrue();
                byte[] tamperedField = phoneEnvelope.clone();
                tamperedField[tamperedField.length - 1] ^= 1;
                assertThatThrownBy(() -> codec.unprotect(tamperedField, databaseContext,
                        EnvelopeCodec.Target.DATABASE_FIELD)).isInstanceOf(RuntimeException.class);

                byte[] credentialSecret = sha256Bytes(canaries.value(CanaryKind.CREDENTIAL));
                byte[] capabilitySecret = sha256Bytes(canaries.value(CanaryKind.CAPABILITY));
                OpaqueTokenDigestPort.Binding credentialBinding = new OpaqueTokenDigestPort.Binding(
                        "tenant:leak-proof", "subject:credential", "session:" + suffix);
                OpaqueTokenDigestPort.Binding capabilityBinding = new OpaqueTokenDigestPort.Binding(
                        "tenant:leak-proof", "subject:capability", "object:" + suffix);
                VersionedTokenDigest credentialDigest = runtime.adapter().issue(
                        OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                        credentialBinding, credentialSecret);
                VersionedTokenDigest capabilityDigest = runtime.adapter().issue(
                        OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                        capabilityBinding, capabilitySecret);
                assertThat(runtime.adapter().verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                        credentialBinding, credentialSecret, credentialDigest)).isTrue();
                assertThat(runtime.adapter().verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                        capabilityBinding, capabilitySecret, capabilityDigest)).isTrue();
                byte[] wrongCapability = capabilitySecret.clone();
                wrongCapability[0] ^= 1;
                assertThat(runtime.adapter().verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                        capabilityBinding, wrongCapability, capabilityDigest)).isFalse();

                byte[] canaryDek = sha256Bytes(canaries.value(CanaryKind.DEK));
                byte[] header = new EnvelopeCodec().authenticatedHeader(
                        FIELD_REFERENCE, canaryDek.length, EnvelopeCodec.Target.DATABASE_FIELD);
                WrappedDataKey wrappedDek = runtime.adapter().wrap(canaryDek, header, databaseContext);
                assertThat(MessageDigest.isEqual(canaryDek,
                        runtime.adapter().unwrap(wrappedDek, header, databaseContext))).isTrue();
                byte[] damagedWrap = wrappedDek.wrappedDek();
                damagedWrap[damagedWrap.length - 1] ^= 1;
                assertThatThrownBy(() -> runtime.adapter().unwrap(new WrappedDataKey(
                        wrappedDek.keyReference(), wrappedDek.wrapNonce(), damagedWrap),
                        header, databaseContext)).isInstanceOf(RuntimeException.class);

                ProtectionContext objectContext = new ProtectionContext(
                        ProtectionContext.Purpose.PROTECTED_OBJECT, "phase03-leak-proof",
                        "registration-object", "business-license", "tenant:leak-proof",
                        "object:" + suffix);
                byte[] objectPlaintext = utf8("%PDF-1.4\n" + canaries.value(CanaryKind.OBJECT)
                        + "\n%%EOF");
                byte[] objectEnvelope = codec.protect(
                        objectPlaintext, objectContext, EnvelopeCodec.Target.BUSINESS_LICENSE);
                ObjectStoreProperties properties = new ObjectStoreProperties(
                        true, bucket, "us-east-1", endpoint, Set.of(endpoint),
                        ObjectStoreProperties.CredentialProvider.DEFAULT_CHAIN, true, true);
                S3PrivateObjectStoreAdapter store = new S3PrivateObjectStoreAdapter(
                        s3, properties, new EnvelopeCodec(), new SecureRandom());
                StoredObjectMetadata stored = store.put(
                        PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                        new ByteArrayInputStream(objectEnvelope), (long) objectEnvelope.length);
                storageKey = stored.storageKey();
                assertThat(MessageDigest.isEqual(objectEnvelope, store.get(storageKey,
                        PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE).ciphertext())).isTrue();
                byte[] damagedObject = Arrays.copyOf(
                        objectEnvelope, EnvelopeCodec.FIXED_HEADER_BYTES - 1);
                assertThatThrownBy(() -> store.put(
                        PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, "application/pdf",
                        new ByteArrayInputStream(damagedObject), (long) damagedObject.length))
                        .isInstanceOf(PrivateObjectStorePort.Failure.class);

                jdbc.update("INSERT INTO " + table + " (id,phone_envelope,identity_envelope,"
                                + "credential_digest,capability_digest,object_key) VALUES (1,?,?,?,?,?)",
                        phoneEnvelope, identityEnvelope, credentialDigest.digest(),
                        capabilityDigest.digest(), storageKey);

                byte[] logOutput = capturedAppenderOutput(canaries);
                byte[] jdbcCells = readRawJdbcCells(dataSource, table);
                byte[] objectBytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(bucket).key(storageKey).build()).asByteArray();
                HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder()
                        .bucket(bucket).key(storageKey).build());
                byte[] objectMetadata = canonicalObjectMetadata(head);

                String mysqlIdentity = sha256(jdbc.queryForObject(
                        "SELECT CONCAT(@@version, '|', DATABASE())", String.class));
                String minioIdentity = sha256(MINIO_IMAGE);
                String pkcs11Identity = runtime.session().tokenIdentityHash();
                String subjectDigest = testedSubjectDigest(mysqlIdentity, minioIdentity, pkcs11Identity);
                byte[] artifactReport = readArtifactReport();
                byte[] cleanBytes = executeScan(scan, subjectDigest, List.of(
                        surface(SurfaceTarget.DATABASE_CELLS,
                                SurfaceItem.bytes("jdbc-cells", jdbcCells)),
                        surface(SurfaceTarget.LOGS,
                                SurfaceItem.bytes("appender-output", logOutput)),
                        surface(SurfaceTarget.OBJECT_BYTES,
                                SurfaceItem.bytes("private-object-bytes", objectBytes),
                                SurfaceItem.bytes("private-object-metadata", objectMetadata))),
                        artifactReport);
                JsonNode clean = JSON.readTree(cleanBytes);
                assertThat(clean.path("status").asText()).isEqualTo("PASS");
                assertThat(clean.path("exit_code").asInt(-1)).isZero();
                assertThat(clean.path("subject_digest").asText()).isEqualTo(subjectDigest);
                assertThat(clean.path("targets")).hasSize(SurfaceTarget.values().length)
                        .allSatisfy(target -> {
                            assertThat(target.path("prohibited_matches").asInt(-1)).isZero();
                            assertThat(target.path("sensitivity_status").asText()).isEqualTo(
                                    LeakScanReport.TargetResult.SENSITIVITY_PROVEN);
                        });

                SensitiveDataLeakScanner.ScanSession seeded = new SensitiveDataLeakScanner().begin();
                String isolatedLeak = seeded.canaries().value(CanaryKind.CREDENTIAL);
                byte[] detectedBytes = executeScan(seeded, subjectDigest, List.of(
                        surface(SurfaceTarget.DATABASE_CELLS, SurfaceItem.bytes("clean-db", utf8("clean"))),
                        surface(SurfaceTarget.LOGS, SurfaceItem.bytes("seeded-log", utf8(isolatedLeak))),
                        surface(SurfaceTarget.OBJECT_BYTES,
                                SurfaceItem.bytes("clean-object", utf8("clean")))),
                        artifactReport);
                JsonNode detected = JSON.readTree(detectedBytes);
                assertThat(detected.path("status").asText()).isEqualTo("FAIL");
                assertThat(detected.path("exit_code").asInt()).isOne();
                List<String> detectedTargets = new ArrayList<>();
                detected.path("targets").forEach(target -> {
                    if (target.path("prohibited_matches").asInt() > 0) {
                        detectedTargets.add(target.path("id").asText());
                    }
                });
                assertThat(detectedTargets).containsExactly(SurfaceTarget.LOGS.id());
                assertThat(new String(detectedBytes, StandardCharsets.UTF_8)).doesNotContain(isolatedLeak);

                Path reportPath = repositoryRoot().resolve(
                        "core/target/phase03/leak-integration-report.json");
                Files.createDirectories(reportPath.getParent());
                Files.write(reportPath, cleanBytes);
                String durable = Files.readString(reportPath, StandardCharsets.UTF_8);
                for (CanaryKind kind : CanaryKind.values()) {
                    assertThat(durable).doesNotContain(canaries.value(kind));
                }

                Arrays.fill(phone, (byte) 0);
                Arrays.fill(identity, (byte) 0);
                Arrays.fill(credentialSecret, (byte) 0);
                Arrays.fill(capabilitySecret, (byte) 0);
                Arrays.fill(wrongCapability, (byte) 0);
                Arrays.fill(canaryDek, (byte) 0);
                Arrays.fill(objectPlaintext, (byte) 0);

                System.out.println("PHASE03_LEAK_SCAN_PASS subject_sha256=" + subjectDigest
                        + " mysql_sha256=" + mysqlIdentity
                        + " minio_sha256=" + minioIdentity
                        + " pkcs11_sha256=" + pkcs11Identity
                        + " result_sha256=" + clean.path("result_digest").asText()
                        + " targets=" + clean.path("targets").size()
                        + " seeded_detection=1 cleaned=1");
            } finally {
                jdbc.execute("DROP TABLE IF EXISTS " + table);
                try {
                    for (var object : s3.listObjectsV2(request -> request.bucket(bucket)).contents()) {
                        s3.deleteObject(request -> request.bucket(bucket).key(object.key()));
                    }
                    s3.deleteBucket(request -> request.bucket(bucket));
                } catch (RuntimeException cleanupFailure) {
                    throw new IllegalStateException("run-owned object cleanup failed", cleanupFailure);
                }
            }
        }
    }

    private static Surface surface(SurfaceTarget target, SurfaceItem... items) {
        return new Surface(target, target.readerIdentity(), List.of(items));
    }

    private static byte[] executeScan(
            SensitiveDataLeakScanner.ScanSession session,
            String subjectDigest,
            List<Surface> surfaces,
            byte[] artifactReport) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new Phase03LeakScanCommand().execute(
                session, subjectDigest, surfaces, artifactReport, output);
        return output.toByteArray();
    }

    private static byte[] readArtifactReport() throws Exception {
        Path root = repositoryRoot().toRealPath();
        Path report = root.resolve(ARTIFACT_REPORT).normalize();
        if (!report.startsWith(root.resolve("core/target/phase03"))
                || Files.isSymbolicLink(report) || !Files.isRegularFile(report)
                || Files.size(report) < 1 || Files.size(report) > 65_536) {
            throw new IllegalStateException("artifact leak report is invalid");
        }
        Path real = report.toRealPath();
        if (!real.startsWith(root.resolve("core/target/phase03").toRealPath())) {
            throw new IllegalStateException("artifact leak report is invalid");
        }
        return Files.readAllBytes(real);
    }

    private static byte[] capturedAppenderOutput(SensitiveDataLeakScanner.CanarySet canaries) {
        Logger logger = (Logger) LoggerFactory.getLogger(SecurityEventLogger.LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        String correlation = sha256(canaries.value(CanaryKind.URL)).substring(0, 32);
        try {
            MDC.put("traceId", correlation);
            String rawFault = "credential=" + canaries.value(CanaryKind.CREDENTIAL)
                    + " capability=" + canaries.value(CanaryKind.CAPABILITY)
                    + " url=" + canaries.value(CanaryKind.URL)
                    + " crlf=" + canaries.value(CanaryKind.CRLF);
            new GlobalExceptionHandler(new SecurityEventLogger())
                    .handleUnexpected(new IllegalStateException(rawFault));
            StringBuilder output = new StringBuilder();
            appender.list.forEach(event -> output.append(
                    SecurityRedactionConverter.redact(event.getFormattedMessage())).append('\n'));
            output.append(SecurityRedactionConverter.redact(rawFault)).append('\n');
            return utf8(output.toString());
        } finally {
            MDC.remove("traceId");
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static byte[] readRawJdbcCells(DataSource dataSource, String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
            ResultSetMetaData metadata = rows.getMetaData();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            while (rows.next()) {
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    output.writeBytes(metadata.getColumnLabel(column)
                            .getBytes(StandardCharsets.US_ASCII));
                    output.write(0);
                    byte[] value = rows.getBytes(column);
                    if (value != null) {
                        output.writeBytes(value);
                    }
                    output.write(0);
                }
            }
            return output.toByteArray();
        }
    }

    private static byte[] canonicalObjectMetadata(HeadObjectResponse head) {
        StringBuilder output = new StringBuilder()
                .append("content-length=").append(head.contentLength()).append('\n')
                .append("content-type=").append(head.contentType()).append('\n')
                .append("checksum-sha256=").append(head.checksumSHA256()).append('\n');
        head.metadata().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> output.append(entry.getKey()).append('=')
                        .append(entry.getValue()).append('\n'));
        return utf8(output.toString());
    }

    private static AdapterRuntime openAdapter(
            Phase03ServiceHarness.SoftHsmHandoff handoff,
            DataSource dataSource,
            DataSourceTransactionManager transactions) {
        List<Pkcs11KeyDescriptor> descriptors = List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                        FIELD_REFERENCE, FIELD_ALIAS, Pkcs11KeyDescriptor.State.ROTATION_REQUIRED),
                descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, 1,
                        "snapshot-recovery.v1", SNAPSHOT_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 1,
                        "mobile-index.v1", MOBILE_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                        "object-digest.v1", OBJECT_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                        "registration-digest.v1", UPLOAD_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE));
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                handoff.library(), List.of(handoff.library()), handoff.slot(),
                "phase03-leak-scan", () -> handoff.userPin().clone(), descriptors);
        Pkcs11FailureMapper mapper = new Pkcs11FailureMapper();
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(mapper).open(properties);
        try {
            return new AdapterRuntime(session, new SunPkcs11KeyAdapter(session, properties,
                    new KekWrapUsageRepository(new JdbcTemplate(dataSource), transactions, mapper), mapper));
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    private static Pkcs11KeyDescriptor descriptor(
            Pkcs11KeyDescriptor.Purpose purpose,
            long version,
            String reference,
            String alias,
            Pkcs11KeyDescriptor.State state) {
        return new Pkcs11KeyDescriptor(purpose, version, reference, alias, state,
                purpose.isWrappingKey() ? "AES" : "HmacSHA256", 256);
    }

    private static S3Client s3(URI endpoint) {
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        requiredEnvironment("PHASE03_MINIO_USER"),
                        requiredEnvironment("PHASE03_MINIO_PASSWORD"))))
                .httpClient(UrlConnectionHttpClient.create())
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static DataSource mysqlDataSource() {
        String url = "jdbc:mysql://" + requiredEnvironment("PHASE03_MYSQL_HOST") + ":"
                + requiredEnvironment("PHASE03_MYSQL_PORT") + "/phase01"
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        return new DriverManagerDataSource(url, requiredEnvironment("PHASE03_MYSQL_USER"),
                requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
    }

    private static void migrateQuietly(DataSource dataSource) {
        PrintStream original = System.out;
        try (PrintStream discarded = new PrintStream(
                OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8)) {
            System.setOut(discarded);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .placeholderReplacement(false).load().migrate();
        } finally {
            System.setOut(original);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("integration environment unavailable");
        }
        return value;
    }

    private static String testedSubjectDigest(
            String mysqlIdentity, String minioIdentity, String pkcs11Identity) {
        String bound = System.getenv("PHASE03_TESTED_SUBJECT_DIGEST");
        if (bound == null) {
            return sha256(mysqlIdentity + "\0" + minioIdentity + "\0" + pkcs11Identity);
        }
        if (!bound.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("tested subject digest is invalid");
        }
        return bound;
    }

    private static byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(utf8(value));
        } catch (Exception failure) {
            throw new IllegalStateException("digest unavailable", failure);
        }
    }

    private static String sha256(String value) {
        return sha256(utf8(value));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception failure) {
            throw new IllegalStateException("digest unavailable", failure);
        }
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("scripts/lib/phase-03/service_checks.rb"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root unavailable");
    }

    private record AdapterRuntime(Pkcs11ProviderFactory.Session session,
                                  SunPkcs11KeyAdapter adapter) implements AutoCloseable {
        @Override
        public void close() {
            session.close();
        }
    }
}
