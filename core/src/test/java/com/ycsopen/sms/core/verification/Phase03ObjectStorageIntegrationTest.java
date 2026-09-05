package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.key.lifecycle.JdbcFieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.object.ObjectCapabilityService;
import com.ycsopen.sms.core.common.security.object.ObjectCapabilityToken;
import com.ycsopen.sms.core.common.security.object.ObjectStoreProperties;
import com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectMetadataRepository;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectService;
import com.ycsopen.sms.core.common.security.object.S3PrivateObjectStoreAdapter;
import com.ycsopen.sms.core.common.security.object.StoredObjectMetadata;
import com.ycsopen.sms.core.common.security.object.TenantRegistrationObjectSessionService;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import com.ycsopen.sms.core.common.security.persistence.TenantRegistrationProtectionAdapter;
import com.ycsopen.sms.core.domain.entity.Tenant;
import com.ycsopen.sms.core.service.tenant.TenantService;
import com.ycsopen.sms.core.web.controller.TenantController;
import com.ycsopen.sms.core.web.dto.TenantRegistrationRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/** Real MinIO + MySQL + SunPKCS11 proof for the complete staged-registration object boundary. */
@EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
class Phase03ObjectStorageIntegrationTest {

    private static final String FIELD_REFERENCE = "field-kek.v1";
    private static final String FIELD_ALIAS = "ycs.field-encryption-kek.v1";
    private static final String SNAPSHOT_ALIAS = "ycs.snapshot-recovery.v1";
    private static final String MOBILE_ALIAS = "ycs.mobile-blind-index.v1";
    private static final String OBJECT_ACTIVE_ALIAS = "ycs.object-capability-digest.v1";
    private static final String OBJECT_RETIRING_ALIAS = "ycs.object-capability-digest.v2";
    private static final String UPLOAD_ACTIVE_ALIAS = "ycs.registration-upload-digest.v1";
    private static final String UPLOAD_RETIRING_ALIAS = "ycs.registration-upload-digest.v2";
    private static final String BUCKET_PREFIX = "phase03-registration-";
    private static final String SUBJECT = "reviewer:phase03";
    private static final String ACCESS_PURPOSE = "registration-review";
    private static final String CANARY = "phase03-object-plaintext-canary";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger ASSERTIONS = new AtomicInteger();

    @Test
    void provesProductionObjectAndRegistrationBoundariesAgainstRealServices() throws Exception {
        String output;
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            Phase03ServiceHarness.ServiceSession mysql = fixtures.mysql();
            Phase03ServiceHarness.ServiceSession minio = fixtures.minio();
            Phase03ServiceHarness.SoftHsmHandoff handoff = fixtures.softHsm().softHsm();
            Path destination = handoff.config().getParent().getParent();
            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
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

            Phase03ServiceHarness.CommandResult proof = Phase03ServiceHarness.runChecked(
                    List.of(javaExecutable.toString(), "-cp", classpath,
                            Phase03ObjectStorageIntegrationTest.class.getName(), "real-proof"),
                    environment);
            output = proof.stdout().strip();
            List<String> passLines = output.lines()
                    .filter(line -> line.startsWith("PHASE03_OBJECT_STORAGE_PASS ")).toList();
            assertThat(passLines).hasSize(1);
            assertThat(passLines.getFirst()).matches("PHASE03_OBJECT_STORAGE_PASS "
                    + "mysql_sha256=[a-f0-9]{64} minio_sha256=[a-f0-9]{64} "
                    + "pkcs11_sha256=[a-f0-9]{64} purposes=5 attempts=15 "
                    + "claimed=5 cleaned=1 assertions=[0-9]+");
            assertThat(output.toLowerCase()).doesNotContain(
                    "password", "secret", "pin", "alias", "path", "library=", "token=",
                    CANARY, "pobj_v1_", "obj_v1_");
        }
        Phase03ServiceHarness.runChecked(List.of("/usr/bin/env", "ruby",
                repositoryRoot().resolve("scripts/lib/phase-03/service_checks.rb").toString(),
                "assert-clean", "--service", "minio"), Map.of());
        assertThat(output).contains("PHASE03_OBJECT_STORAGE_PASS");
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
        provisionKeys(destination, handoff);

        DataSource dataSource = mysqlDataSource();
        migrateQuietly(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        seedKeyMetadata(jdbc);

        URI endpoint = URI.create("http://" + requiredEnvironment("PHASE03_MINIO_HOST") + ":"
                + requiredEnvironment("PHASE03_MINIO_PORT"));
        String bucket = BUCKET_PREFIX + randomHex(6);
        RetiringDigestProof retiringDigests = issueRetiringDigests(
                handoff, dataSource, transactions);
        try (S3Client s3 = s3(endpoint, false); S3Client anonymous = s3(endpoint, true);
             AdapterRuntime runtime = openAdapter(handoff, dataSource, transactions, false)) {
            s3.createBucket(request -> request.bucket(bucket));
            Fixture fixture = compose(jdbc, transactions, s3, endpoint, bucket, runtime.adapter(),
                    Clock.fixed(Instant.parse("2036-05-01T00:00:00Z"), ZoneOffset.UTC));
            try {
                proveTokenKeyDomains(runtime.adapter(), retiringDigests);
                proveExactAndOverBoundaries(fixture);
                RegistrationObjects registration = uploadCompleteRegistration(fixture);
                String initialCapability = proveDatabaseSafeShapes(fixture, registration);
                proveCapabilityAndCiphertextFaults(fixture, registration.businessLicense(),
                        initialCapability, anonymous, s3);
                proveConcurrentCapabilityConsumption(fixture, registration.businessLicense());
                proveDeleteClaimRace(fixture);
                proveClaimDenialsRollbackAndCommit(fixture, registration);
                proveCloseAndExpiry(fixture);
                provePostReservationFailure(fixture);
                proveRacedAttemptCeilings(fixture);
                proveOrphanReconciliation(fixture);
                proveLegacyUrlIsUnprocessable();
                proveRetirementBlockedByLiveReferences(fixture);

                String mysqlIdentity = sha256(jdbc.queryForObject(
                        "SELECT CONCAT(@@version, '|', DATABASE())", String.class));
                String minioIdentity = sha256("minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e");
                String pkcs11Identity = runtime.session().tokenIdentityHash();
                cleanup(fixture);
                assertThat(s3.listObjectsV2(request -> request.bucket(bucket)).contents()).isEmpty();
                s3.deleteBucket(request -> request.bucket(bucket));
                passed(2);

                System.out.println("PHASE03_OBJECT_STORAGE_PASS mysql_sha256=" + mysqlIdentity
                        + " minio_sha256=" + minioIdentity + " pkcs11_sha256=" + pkcs11Identity
                        + " purposes=5 attempts=15 claimed=5 cleaned=1 assertions="
                        + ASSERTIONS.get());
            } finally {
                bestEffortCleanup(jdbc, fixture.objectService());
                try {
                    for (var object : s3.listObjectsV2(request -> request.bucket(bucket)).contents()) {
                        s3.deleteObject(request -> request.bucket(bucket).key(object.key()));
                    }
                    s3.deleteBucket(request -> request.bucket(bucket));
                } catch (RuntimeException ignored) {
                    // The harness-owned ephemeral MinIO volume is the final containment boundary.
                }
            }
        }
    }

    private static Fixture compose(JdbcTemplate jdbc,
                                   DataSourceTransactionManager transactions,
                                   S3Client s3,
                                   URI endpoint,
                                   String bucket,
                                   SunPkcs11KeyAdapter keyAdapter,
                                   Clock clock) {
        ObjectStoreProperties properties = new ObjectStoreProperties(
                true, bucket, "us-east-1", endpoint, Set.of(endpoint),
                ObjectStoreProperties.CredentialProvider.DEFAULT_CHAIN, true, true);
        S3PrivateObjectStoreAdapter store = new S3PrivateObjectStoreAdapter(
                s3, properties, new EnvelopeCodec(), new SecureRandom());
        ProtectedObjectMetadataRepository metadata =
                new ProtectedObjectMetadataRepository(jdbc, transactions);
        ObjectCapabilityService capabilities = new ObjectCapabilityService(
                keyAdapter, metadata, request -> true, clock, new SecureRandom());
        ProtectedFieldCodec codec = new ProtectedFieldCodec(
                new EnvelopeCodec(), keyAdapter, new SecureRandom(), FIELD_REFERENCE);
        ProtectedObjectService objects = new ProtectedObjectService(
                codec, store, metadata, capabilities, new SecureRandom(), clock);
        TenantRegistrationObjectSessionService sessions =
                new TenantRegistrationObjectSessionService(keyAdapter, objects, jdbc, transactions,
                        clock, new SecureRandom());
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("registrationObjectSessionService", sessions);
        TenantRegistrationProtectionAdapter registration = new TenantRegistrationProtectionAdapter(
                keyAdapter, jdbc,
                beanFactory.getBeanProvider(TenantRegistrationObjectSessionService.class),
                new ActiveFieldKeyReference(new KeyReferenceRepository.Jdbc(
                        jdbc, new TransactionTemplate(transactions))),
                new JdbcFieldReferencePublicationFence(jdbc));
        return new Fixture(jdbc, transactions, store, metadata, capabilities, objects, sessions,
                registration, keyAdapter, clock, bucket);
    }

    private static void proveTokenKeyDomains(SunPkcs11KeyAdapter adapter,
                                             RetiringDigestProof retiring) {
        byte[] secret = randomBytes(OpaqueTokenDigestPort.TOKEN_SECRET_BYTES);
        OpaqueTokenDigestPort.Binding binding = new OpaqueTokenDigestPort.Binding(
                "tenant:domain-proof", "subject:domain-proof", "resource:domain-proof");
        try {
            VersionedTokenDigest object = adapter.issue(
                    OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, binding, secret);
            VersionedTokenDigest upload = adapter.issue(
                    OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD, binding, secret);
            assertThat(object.keyVersion()).isOne();
            assertThat(upload.keyVersion()).isOne();
            assertThat(object.digest()).isNotEqualTo(upload.digest());
            assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                    binding, secret, object)).isTrue();
            assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                    binding, secret, upload)).isTrue();
            assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                    binding, secret, object)).isFalse();
            assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                    binding, secret, upload)).isFalse();
            byte[] retiringSecret = retiring.secret();
            try {
                assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                        retiring.binding(), retiringSecret, retiring.objectDigest())).isTrue();
                assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                        retiring.binding(), retiringSecret, retiring.uploadDigest())).isTrue();
                assertThat(retiring.objectDigest().keyVersion()).isEqualTo(2);
                assertThat(retiring.uploadDigest().keyVersion()).isEqualTo(2);
                passed(11);
            } finally {
                Arrays.fill(retiringSecret, (byte) 0);
            }
        } finally {
            Arrays.fill(secret, (byte) 0);
            retiring.destroy();
        }
    }

    private static void proveExactAndOverBoundaries(Fixture fixture) {
        var created = fixture.sessions().createSession();
        byte[] exact = pdf(Math.toIntExact(
                TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE
                        .maximumPlaintextBytes()), CANARY);
        try {
            var uploaded = upload(fixture.sessions(), created,
                    TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE,
                    "application/pdf", exact);
            long envelopeSize = fixture.jdbc().queryForObject("SELECT envelope_size FROM "
                    + "ycs_crypto_protected_objects WHERE protected_object_id=?", Long.class,
                    uploaded.protectedObjectId());
            assertThat(envelopeSize).isEqualTo(new EnvelopeCodec().maximumCompleteEnvelopeLength(
                    FIELD_REFERENCE, exact.length, EnvelopeCodec.Target.BUSINESS_LICENSE));
            assertThat(envelopeSize).isLessThanOrEqualTo(
                    TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE
                            .maximumEnvelopeBytes());
        } finally {
            Arrays.fill(exact, (byte) 0);
        }
        long over = TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE
                .maximumPlaintextBytes() + 1;
        TenantRegistrationObjectSessionService.Failure failure = catchUploadFailure(() ->
                fixture.sessions().upload(new TenantRegistrationObjectSessionService.UploadRequest(
                        created.registrationObjectSessionId(), created.registrationUploadToken(),
                        TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE,
                        "application/pdf", new CountingInputStream(over), over)));
        assertThat(failure.category()).isEqualTo(
                TenantRegistrationObjectSessionService.Failure.Category
                        .REGISTRATION_UPLOAD_SIZE_LIMIT_EXCEEDED);
        assertThat(sessionAttempts(fixture.jdbc(), created.registrationObjectSessionId())).isOne();
        fixture.sessions().close(created.registrationObjectSessionId(),
                created.registrationUploadToken());
        assertThat(fixture.objectService().reconcile(100).deleted()).isOne();
        passed(4);
    }

    private static RegistrationObjects uploadCompleteRegistration(Fixture fixture) {
        var session = fixture.sessions().createSession();
        Map<TenantRegistrationObjectSessionService.UploadPurpose, String> objects =
                new java.util.EnumMap<>(TenantRegistrationObjectSessionService.UploadPurpose.class);
        for (var purpose : TenantRegistrationObjectSessionService.UploadPurpose.values()) {
            var uploaded = upload(fixture.sessions(), session, purpose,
                    media(purpose), body(purpose, purpose.wireName()));
            objects.put(purpose, uploaded.protectedObjectId());
        }
        String replaced = objects.get(
                TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE);
        var replacement = upload(fixture.sessions(), session,
                TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE,
                "application/pdf", pdf(256, "replacement-canary"));
        objects.put(TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE,
                replacement.protectedObjectId());
        assertThat(fixture.jdbc().queryForObject("SELECT object_state FROM "
                + "ycs_crypto_protected_objects WHERE protected_object_id=?", String.class, replaced))
                .isEqualTo("DELETED");
        assertThat(sessionAttempts(fixture.jdbc(), session.registrationObjectSessionId())).isEqualTo(6);
        assertThat(objects).hasSize(5);
        passed(3);
        return new RegistrationObjects(session, objects);
    }

    private static void proveCapabilityAndCiphertextFaults(Fixture fixture,
                                                            String objectId,
                                                            String token,
                                                            S3Client anonymous,
                                                            S3Client s3) {
        ObjectRow row = objectRow(fixture, objectId);
        String tenant = "tenant:" + row.tenantDraftId();
        CountingStore countingStore = new CountingStore(fixture.store());
        ProtectedObjectService reading = new ProtectedObjectService(
                new ProtectedFieldCodec(new EnvelopeCodec(), fixture.keyAdapter(),
                        new SecureRandom(), FIELD_REFERENCE), countingStore, fixture.metadata(),
                fixture.capabilities(), new SecureRandom(), fixture.clock());
        ProtectedObjectService.ReadRequest request = readRequest(objectId, token, tenant,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE);
        byte[] plaintext = reading.read(request).bytes();
        assertThat(new String(plaintext, StandardCharsets.ISO_8859_1))
                .contains("replacement-canary");
        Arrays.fill(plaintext, (byte) 0);
        int fetched = countingStore.fetches();

        assertDenied(() -> reading.read(request));
        assertDenied(() -> reading.read(readRequest(objectId, token + "x", tenant,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        assertDenied(() -> reading.read(readRequest(objectId, token, "tenant:" + UUID.randomUUID(),
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        assertDenied(() -> reading.read(readRequest(objectId, token, tenant,
                PrivateObjectStorePort.ObjectPurpose.TRADEMARK_PROOF)));
        assertThat(countingStore.fetches()).isEqualTo(fetched);

        assertThatThrownBy(() -> anonymous.getObjectAsBytes(
                requestBuilder -> requestBuilder.bucket(row.bucket()).key(row.storageKey())))
                .isInstanceOf(S3Exception.class)
                .satisfies(error -> assertThat(((S3Exception) error).statusCode()).isEqualTo(403));
        byte[] raw = s3.getObjectAsBytes(
                requestBuilder -> requestBuilder.bucket(row.bucket()).key(row.storageKey())).asByteArray();
        assertThat(Arrays.copyOf(raw, 4)).containsExactly(
                "YCSE".getBytes(StandardCharsets.US_ASCII));
        assertThat(new String(raw, StandardCharsets.ISO_8859_1))
                .doesNotContain("replacement-canary", CANARY);

        byte[] tampered = raw.clone();
        tampered[tampered.length - 1] ^= 1;
        rawPut(s3, row, tampered, row.sha256());
        assertIntegrity(() -> reading.read(readRequest(objectId,
                issueCapabilityToken(fixture, objectId, tenant), tenant,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        rawPut(s3, row, raw, row.sha256());

        String tamperedSha = sha256(tampered);
        rawPut(s3, row, tampered, tamperedSha);
        fixture.jdbc().update("UPDATE ycs_crypto_protected_objects SET envelope_digest=UNHEX(?) "
                + "WHERE protected_object_id=?", tamperedSha, objectId);
        assertIntegrity(() -> reading.read(readRequest(objectId,
                issueCapabilityToken(fixture, objectId, tenant), tenant,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        rawPut(s3, row, raw, row.sha256());
        fixture.jdbc().update("UPDATE ycs_crypto_protected_objects SET envelope_digest=UNHEX(?) "
                + "WHERE protected_object_id=?", row.sha256(), objectId);

        String revokedToken = issueCapabilityToken(fixture, objectId, tenant);
        fixture.jdbc().update("UPDATE ycs_crypto_object_capabilities SET capability_state='REVOKED' "
                + "WHERE capability_lookup_id=?", lookupId(revokedToken));
        assertDenied(() -> reading.read(readRequest(objectId, revokedToken, tenant,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        String expiredToken = issueCapabilityToken(fixture, objectId, tenant);
        fixture.jdbc().update("UPDATE ycs_crypto_object_capabilities SET expires_at=? "
                        + "WHERE capability_lookup_id=?",
                Timestamp.from(fixture.clock().instant().minusSeconds(1)), lookupId(expiredToken));
        assertDenied(() -> reading.read(readRequest(objectId, expiredToken, tenant,
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)));
        passed(14);
    }

    private static String issueCapabilityToken(Fixture fixture, String objectId, String tenant) {
        return token(fixture.capabilities().issue(new ObjectCapabilityService.IssueRequest(
                objectId, tenant, SUBJECT, ACCESS_PURPOSE,
                fixture.clock().instant().plus(Duration.ofMinutes(20)))));
    }

    private static String lookupId(String token) {
        return token.substring("ocap_v1_".length(), "ocap_v1_".length() + 22);
    }

    private static void proveConcurrentCapabilityConsumption(Fixture fixture, String objectId)
            throws Exception {
        ObjectRow row = objectRow(fixture, objectId);
        String tenant = "tenant:" + row.tenantDraftId();
        String token = issueCapabilityToken(fixture, objectId, tenant);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> concurrentRead(
                    fixture, objectId, tenant, token, start));
            Future<Boolean> second = executor.submit(() -> concurrentRead(
                    fixture, objectId, tenant, token, start));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        assertThat(fixture.jdbc().queryForObject("SELECT capability_state FROM "
                        + "ycs_crypto_object_capabilities WHERE capability_lookup_id=?",
                String.class, lookupId(token))).isEqualTo("REVOKED");
        passed(2);
    }

    private static boolean concurrentRead(Fixture fixture,
                                          String objectId,
                                          String tenant,
                                          String token,
                                          CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            byte[] value = fixture.objectService().read(readRequest(objectId, token, tenant,
                    PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE)).bytes();
            Arrays.fill(value, (byte) 0);
            return true;
        } catch (ProtectedObjectService.Failure failure) {
            assertThat(failure.category()).isEqualTo(
                    ProtectedObjectService.Failure.Category.PROTECTED_OBJECT_ACCESS_DENIED);
            return false;
        }
    }

    private static void proveDeleteClaimRace(Fixture fixture) throws Exception {
        RegistrationObjects race = uploadCompleteRegistration(fixture);
        ObjectRow row = objectRow(fixture, race.businessLicense());
        BlockingDeleteStore blocking = new BlockingDeleteStore(fixture.store());
        ProtectedObjectService deleting = new ProtectedObjectService(
                new ProtectedFieldCodec(new EnvelopeCodec(), fixture.keyAdapter(),
                        new SecureRandom(), FIELD_REFERENCE), blocking, fixture.metadata(),
                fixture.capabilities(), new SecureRandom(), fixture.clock());
        ProtectedObjectService.DeleteRequest request = new ProtectedObjectService.DeleteRequest(
                race.businessLicense(), "tenant:" + row.tenantDraftId(),
                PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE);
        String credit = creditCode("delete-race");

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> deletion = executor.submit(() -> deleting.delete(request));
            blocking.awaitEntered();
            assertThat(fixture.jdbc().queryForObject("SELECT object_state FROM "
                            + "ycs_crypto_protected_objects WHERE protected_object_id=?",
                    String.class, race.businessLicense())).isEqualTo("DELETING");
            assertRegistrationFailure(() -> transaction(fixture).executeWithoutResult(status -> {
                Tenant tenant = insertTenant(fixture.jdbc(), "delete-race", credit);
                fixture.registration().protectRegistration(tenant, race.request(),
                        race.session().registrationUploadToken());
            }), TenantRegistrationProtectionAdapter.Failure.Category
                    .REGISTRATION_OBJECT_NOT_STAGED);
            blocking.release();
            deletion.get();
        } finally {
            blocking.release();
        }
        assertThat(fixture.jdbc().queryForObject("SELECT object_state FROM "
                        + "ycs_crypto_protected_objects WHERE protected_object_id=?",
                String.class, race.businessLicense())).isEqualTo("DELETED");
        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE unified_social_credit_code=?",
                Long.class, credit)).isZero();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM "
                        + "ycs_crypto_protected_objects WHERE protected_object_id=? "
                        + "AND object_state='CLAIMED'", Long.class, race.businessLicense())).isZero();
        fixture.sessions().close(race.session().registrationObjectSessionId(),
                race.session().registrationUploadToken());
        fixture.objectService().reconcile(100);
        passed(4);
    }

    private static String proveDatabaseSafeShapes(Fixture fixture,
                                                  RegistrationObjects registration) {
        ObjectRow object = objectRow(fixture, registration.businessLicense());
        String initialCapability = issueCapabilityToken(fixture, registration.businessLicense(),
                "tenant:" + object.tenantDraftId());
        fixture.jdbc().queryForObject("SELECT upload_digest_key_version, "
                        + "upload_credential_digest, CONCAT(registration_session_id,'|',"
                        + "tenant_draft_id,'|',HEX(upload_credential_digest)) "
                        + "FROM ycs_crypto_registration_sessions WHERE registration_session_id=?",
                (rs, row) -> {
            assertThat(rs.getLong(1)).isOne();
            assertThat(rs.getBytes(2)).hasSize(32);
            assertThat(rs.getString(3)).doesNotContain(
                    registration.session().registrationUploadToken());
            return true;
        }, registration.session().registrationObjectSessionId());
        fixture.jdbc().queryForObject("SELECT digest_key_version, capability_credential_digest, "
                        + "CONCAT(capability_lookup_id,'|',HEX(capability_credential_digest)) "
                        + "FROM ycs_crypto_object_capabilities WHERE protected_object_id=?",
                (rs, row) -> {
            assertThat(rs.getLong(1)).isOne();
            assertThat(rs.getBytes(2)).hasSize(32);
            assertThat(rs.getString(3)).doesNotContain("ocap_v1_");
            return true;
        }, registration.businessLicense());
        List<Map<String, Object>> rows = fixture.jdbc().queryForList(
                "SELECT protected_object_id, opaque_store_locator FROM "
                        + "ycs_crypto_protected_objects WHERE registration_session_id=? "
                        + "AND object_state='STAGED'",
                registration.session().registrationObjectSessionId());
        assertThat(rows).hasSize(5).allSatisfy(row -> {
            assertThat(row.get("protected_object_id").toString())
                    .matches("pobj_v1_[A-Za-z0-9_-]{32}").doesNotContain("http://", "https://");
            assertThat(row.get("opaque_store_locator").toString())
                    .matches("obj_v1_[a-f0-9]{64}").doesNotContain("http://", "https://", CANARY);
        });
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema=DATABASE() AND table_name IN "
                + "('ycs_crypto_registration_sessions','ycs_crypto_object_capabilities') "
                + "AND column_name IN ('upload_token','capability_token','raw_url')", Long.class))
                .isZero();
        passed(8);
        return initialCapability;
    }

    private static void proveClaimDenialsRollbackAndCommit(Fixture fixture,
                                                           RegistrationObjects objects) {
        var foreign = fixture.sessions().createSession();
        var foreignObject = upload(fixture.sessions(), foreign,
                TenantRegistrationObjectSessionService.UploadPurpose.LEGAL_REP_ID_FRONT,
                "image/png", png(64, "foreign"));
        TenantRegistrationRequest crossSession = objects.requestWithFront(
                foreignObject.protectedObjectId());
        assertRegistrationFailure(() -> transaction(fixture).executeWithoutResult(status -> {
            Tenant tenant = insertTenant(fixture.jdbc(), "cross-session");
            fixture.registration().protectRegistration(tenant, crossSession,
                    objects.session().registrationUploadToken());
        }), TenantRegistrationProtectionAdapter.Failure.Category
                .REGISTRATION_OBJECT_BINDING_MISMATCH);

        TenantRegistrationRequest crossPurpose = objects.requestWithFront(objects.businessLicense());
        assertRegistrationFailure(() -> transaction(fixture).executeWithoutResult(status -> {
            Tenant tenant = insertTenant(fixture.jdbc(), "cross-purpose");
            fixture.registration().protectRegistration(tenant, crossPurpose,
                    objects.session().registrationUploadToken());
        }), TenantRegistrationProtectionAdapter.Failure.Category
                .REGISTRATION_OBJECT_BINDING_MISMATCH);

        String rolledBackCredit = creditCode("rollback");
        assertThatThrownBy(() -> transaction(fixture).executeWithoutResult(status -> {
            Tenant tenant = insertTenant(fixture.jdbc(), "rollback", rolledBackCredit);
            fixture.registration().protectRegistration(tenant, objects.request(),
                    objects.session().registrationUploadToken());
            throw new RollbackProbe();
        })).isInstanceOf(RollbackProbe.class);
        assertThat(fixture.jdbc().queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE unified_social_credit_code=?", Long.class,
                rolledBackCredit)).isZero();
        assertSessionAndObjects(fixture.jdbc(), objects, "OPEN", "STAGED");

        Tenant committed = transaction(fixture).execute(status -> {
            Tenant tenant = insertTenant(fixture.jdbc(), "committed");
            fixture.registration().protectRegistration(tenant, objects.request(),
                    objects.session().registrationUploadToken());
            persistProtectedTenant(fixture.jdbc(), tenant);
            return tenant;
        });
        assertThat(committed).isNotNull();
        assertSessionAndObjects(fixture.jdbc(), objects, "CLAIMED", "CLAIMED");
        assertProtectedTenantRow(fixture, committed.getId(), objects);
        TenantRegistrationObjectSessionService.Failure claimed = catchUploadFailure(() -> upload(
                fixture.sessions(), objects.session(),
                TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE,
                "application/pdf", pdf(64, "claimed")));
        assertThat(claimed.category()).isEqualTo(
                TenantRegistrationObjectSessionService.Failure.Category
                        .REGISTRATION_UPLOAD_SESSION_NOT_OPEN);
        fixture.sessions().close(foreign.registrationObjectSessionId(),
                foreign.registrationUploadToken());
        fixture.objectService().reconcile(100);
        passed(10);
    }

    private static void proveCloseAndExpiry(Fixture fixture) {
        var closing = fixture.sessions().createSession();
        upload(fixture.sessions(), closing,
                TenantRegistrationObjectSessionService.UploadPurpose.TRADEMARK_PROOF,
                "application/pdf", pdf(64, "close"));
        assertThat(fixture.sessions().close(closing.registrationObjectSessionId(),
                closing.registrationUploadToken())).isEqualTo(
                TenantRegistrationObjectSessionService.SessionState.CLOSED);
        assertThat(fixture.objectService().reconcile(100).deleted()).isGreaterThanOrEqualTo(1);
        assertThat(catchUploadFailure(() -> upload(fixture.sessions(), closing,
                TenantRegistrationObjectSessionService.UploadPurpose.TRADEMARK_PROOF,
                "application/pdf", pdf(64, "closed"))).category()).isEqualTo(
                TenantRegistrationObjectSessionService.Failure.Category
                        .REGISTRATION_UPLOAD_SESSION_NOT_OPEN);

        var expiring = fixture.sessions().createSession();
        Clock later = Clock.offset(fixture.clock(),
                TenantRegistrationObjectSessionService.SESSION_TTL.plusSeconds(1));
        TenantRegistrationObjectSessionService expiredService =
                new TenantRegistrationObjectSessionService(fixture.keyAdapter(), fixture.objectService(),
                        fixture.jdbc(), fixture.transactions(), later, new SecureRandom());
        assertThat(catchUploadFailure(() -> upload(expiredService, expiring,
                TenantRegistrationObjectSessionService.UploadPurpose.TRADEMARK_PROOF,
                "application/pdf", pdf(64, "expired"))).category()).isEqualTo(
                TenantRegistrationObjectSessionService.Failure.Category
                        .REGISTRATION_UPLOAD_SESSION_EXPIRED);
        assertThat(sessionState(fixture.jdbc(), expiring.registrationObjectSessionId()))
                .isEqualTo("EXPIRED");
        passed(6);
    }

    private static void provePostReservationFailure(Fixture fixture) {
        var session = fixture.sessions().createSession();
        ProtectedObjectService failingObjects = new ProtectedObjectService(
                new ProtectedFieldCodec(new EnvelopeCodec(), fixture.keyAdapter(),
                        new SecureRandom(), FIELD_REFERENCE), new PutUnavailableStore(fixture.store()),
                fixture.metadata(), fixture.capabilities(), new SecureRandom(), fixture.clock());
        TenantRegistrationObjectSessionService failingSessions =
                new TenantRegistrationObjectSessionService(fixture.keyAdapter(), failingObjects,
                        fixture.jdbc(), fixture.transactions(), fixture.clock(), new SecureRandom());
        assertThat(catchUploadFailure(() -> upload(failingSessions, session,
                TenantRegistrationObjectSessionService.UploadPurpose.SHORTLINK_DOMAIN_PROOF,
                "application/pdf", pdf(64, "store-failure"))).category()).isEqualTo(
                TenantRegistrationObjectSessionService.Failure.Category.REGISTRATION_UPLOAD_UNAVAILABLE);
        assertThat(purposeAttempts(fixture.jdbc(), session.registrationObjectSessionId(),
                "SHORT_LINK_PROOF")).isOne();
        upload(fixture.sessions(), session,
                TenantRegistrationObjectSessionService.UploadPurpose.SHORTLINK_DOMAIN_PROOF,
                "application/pdf", pdf(64, "retry-two"));
        upload(fixture.sessions(), session,
                TenantRegistrationObjectSessionService.UploadPurpose.SHORTLINK_DOMAIN_PROOF,
                "application/pdf", pdf(64, "retry-three"));
        assertThat(catchUploadFailure(() -> upload(fixture.sessions(), session,
                TenantRegistrationObjectSessionService.UploadPurpose.SHORTLINK_DOMAIN_PROOF,
                "application/pdf", pdf(64, "retry-four"))).category()).isEqualTo(
                TenantRegistrationObjectSessionService.Failure.Category
                        .REGISTRATION_UPLOAD_LIMIT_REACHED);
        fixture.sessions().close(session.registrationObjectSessionId(),
                session.registrationUploadToken());
        fixture.objectService().reconcile(100);
        passed(5);
    }

    private static void proveRacedAttemptCeilings(Fixture fixture) throws Exception {
        var session = fixture.sessions().createSession();
        CountDownLatch ready = new CountDownLatch(15);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(15)) {
            for (var purpose : TenantRegistrationObjectSessionService.UploadPurpose.values()) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        try {
                            upload(fixture.sessions(), session, purpose, media(purpose),
                                    body(purpose, "race"));
                        } catch (TenantRegistrationObjectSessionService.Failure failure) {
                            assertThat(failure.category()).isEqualTo(
                                    TenantRegistrationObjectSessionService.Failure.Category
                                            .REGISTRATION_UPLOAD_UNAVAILABLE);
                        }
                        return null;
                    }));
                }
            }
            ready.await();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }
        }
        assertThat(sessionAttempts(fixture.jdbc(), session.registrationObjectSessionId()))
                .isEqualTo(15);
        assertThat(fixture.jdbc().queryForList("SELECT admitted_attempt_count FROM "
                + "ycs_crypto_registration_upload_attempts WHERE registration_session_id=?",
                Integer.class, session.registrationObjectSessionId()))
                .containsOnly(3).hasSize(5);
        assertThat(catchUploadFailure(() -> upload(fixture.sessions(), session,
                TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE,
                "application/pdf", pdf(64, "sixteenth"))).category()).isEqualTo(
                TenantRegistrationObjectSessionService.Failure.Category
                        .REGISTRATION_UPLOAD_LIMIT_REACHED);
        passed(3);
    }

    private static void proveOrphanReconciliation(Fixture fixture) {
        var session = fixture.sessions().createSession();
        var uploaded = upload(fixture.sessions(), session,
                TenantRegistrationObjectSessionService.UploadPurpose.LEGAL_REP_ID_BACK,
                "image/png", png(64, "orphan"));
        assertThat(fixture.jdbc().update("UPDATE ycs_crypto_protected_objects "
                + "SET object_state='ORPHANED' WHERE protected_object_id=? "
                + "AND object_state='STAGED'", uploaded.protectedObjectId())).isOne();
        assertThat(fixture.jdbc().update("UPDATE ycs_crypto_object_operations "
                + "SET operation_state='RECONCILE_DELETE' WHERE protected_object_id=? "
                + "AND operation_state='COMPLETED'", uploaded.protectedObjectId())).isOne();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM "
                + "ycs_crypto_protected_objects WHERE registration_session_id=? "
                + "AND object_state='ORPHANED'", Long.class,
                session.registrationObjectSessionId())).isOne();
        assertThat(fixture.objectService().reconcile(100).deleted()).isGreaterThanOrEqualTo(1);
        passed(3);
    }

    private static void proveLegacyUrlIsUnprocessable() throws Exception {
        TenantService service = mock(TenantService.class);
        when(service.submitRegistration(any(), any())).thenThrow(
                TenantRegistrationProtectionAdapter.Failure.legacyObjectUrlNotAccepted());
        MockMvc mvc = standaloneSetup(new TenantController(service)).build();
        mvc.perform(post("/api/v1/console/tenants/register")
                        .header(TenantRegistrationProtectionAdapter.UPLOAD_TOKEN_HEADER, "redacted")
                        .contentType("application/json")
                        .content("{\"businessLicenseUrl\":\"https://storage.invalid/raw\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LEGACY_OBJECT_URL_NOT_ACCEPTED"));
        passed();
    }

    private static void proveRetirementBlockedByLiveReferences(Fixture fixture) {
        String liveObject = fixture.jdbc().queryForObject("SELECT protected_object_id FROM "
                + "ycs_crypto_protected_objects WHERE object_state IN ('STAGED','CLAIMED') "
                + "ORDER BY protected_object_id LIMIT 1", String.class);
        ObjectRow liveRow = objectRow(fixture, liveObject);
        issueCapabilityToken(fixture, liveObject, "tenant:" + liveRow.tenantDraftId());
        assertThatThrownBy(() -> fixture.jdbc().update("DELETE FROM ycs_crypto_key_references "
                        + "WHERE purpose='OBJECT_CAPABILITY_DIGEST' AND key_version=1"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> fixture.jdbc().update("DELETE FROM ycs_crypto_key_references "
                        + "WHERE purpose='REGISTRATION_UPLOAD_DIGEST' AND key_version=1"))
                .isInstanceOf(DataAccessException.class);
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM "
                + "ycs_crypto_object_capabilities WHERE capability_state='ACTIVE'", Long.class))
                .isPositive();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM "
                + "ycs_crypto_registration_sessions WHERE session_state='OPEN'", Long.class))
                .isPositive();
        passed(4);
    }

    private static void cleanup(Fixture fixture) {
        bestEffortCleanup(fixture.jdbc(), fixture.objectService());
        fixture.jdbc().update("DELETE FROM ycs_crypto_object_capabilities");
        fixture.jdbc().update("DELETE FROM ycs_crypto_object_operations");
        fixture.jdbc().update("UPDATE ycs_crypto_protected_objects SET replaces_object_id=NULL "
                + "WHERE replaces_object_id IS NOT NULL");
        fixture.jdbc().update("DELETE FROM ycs_crypto_protected_objects");
        fixture.jdbc().update("DELETE FROM ycs_crypto_registration_upload_attempts");
        fixture.jdbc().update("DELETE FROM ycs_crypto_registration_sessions");
        fixture.jdbc().update("DELETE FROM tenants");
        assertThat(fixture.jdbc().update("DELETE FROM ycs_crypto_key_references "
                + "WHERE purpose IN ('OBJECT_CAPABILITY_DIGEST','REGISTRATION_UPLOAD_DIGEST')"))
                .isEqualTo(4);
        fixture.jdbc().update("DELETE FROM ycs_crypto_key_references");
        passed();
    }

    private static void bestEffortCleanup(JdbcTemplate jdbc, ProtectedObjectService service) {
        try {
            jdbc.update("UPDATE ycs_crypto_protected_objects SET object_state='EXPIRED', "
                    + "claim_reference=NULL WHERE object_state IN ('STAGED','CLAIMED')");
            for (int pass = 0; pass < 20; pass++) {
                ProtectedObjectService.ReconciliationResult result = service.reconcile(100);
                if (result.examined() == 0) {
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // The fixture volume is still run-scoped; outer cleanup remains authoritative.
        }
    }

    private static TenantRegistrationObjectSessionService.UploadedObject upload(
            TenantRegistrationObjectSessionService service,
            TenantRegistrationObjectSessionService.CreatedSession session,
            TenantRegistrationObjectSessionService.UploadPurpose purpose,
            String mediaType,
            byte[] body) {
        return service.upload(new TenantRegistrationObjectSessionService.UploadRequest(
                session.registrationObjectSessionId(), session.registrationUploadToken(), purpose,
                mediaType, new ByteArrayInputStream(body), body.length));
    }

    private static TenantRegistrationObjectSessionService.Failure catchUploadFailure(
            Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("registration upload failure expected");
        } catch (TenantRegistrationObjectSessionService.Failure failure) {
            return failure;
        }
    }

    private static void assertDenied(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ProtectedObjectService.Failure.class)
                .satisfies(error -> assertThat(((ProtectedObjectService.Failure) error).category())
                        .isEqualTo(ProtectedObjectService.Failure.Category
                                .PROTECTED_OBJECT_ACCESS_DENIED));
    }

    private static void assertIntegrity(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(ProtectedObjectService.Failure.class)
                .satisfies(error -> assertThat(((ProtectedObjectService.Failure) error).category())
                        .isEqualTo(ProtectedObjectService.Failure.Category
                                .PROTECTED_OBJECT_INTEGRITY_INVALID));
    }

    private static void assertRegistrationFailure(Runnable operation,
                                                  TenantRegistrationProtectionAdapter.Failure.Category category) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(TenantRegistrationProtectionAdapter.Failure.class)
                .satisfies(error -> assertThat(
                        ((TenantRegistrationProtectionAdapter.Failure) error).category())
                        .isEqualTo(category));
    }

    private static ProtectedObjectService.ReadRequest readRequest(
            String objectId, String token, String tenant,
            PrivateObjectStorePort.ObjectPurpose purpose) {
        return new ProtectedObjectService.ReadRequest(
                objectId, token, tenant, SUBJECT, ACCESS_PURPOSE, purpose);
    }

    private static String token(ObjectCapabilityToken issued) {
        String path = issued.claimApplicationRelativePath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void rawPut(S3Client s3, ObjectRow row, byte[] body, String sha256) {
        String bodyChecksum = sha256(body);
        s3.putObject(PutObjectRequest.builder().bucket(row.bucket()).key(row.storageKey())
                        .contentLength((long) body.length).contentType(row.mediaType())
                        .checksumSHA256(Base64.getEncoder().encodeToString(
                                HexFormat.of().parseHex(bodyChecksum)))
                        .metadata(Map.of("purpose", row.purpose(),
                                "envelope-length", Integer.toString(body.length), "sha256", sha256))
                        .build(), RequestBody.fromBytes(body));
    }

    private static ObjectRow objectRow(Fixture fixture, String objectId) {
        return fixture.jdbc().queryForObject("SELECT registration_session_id, tenant_draft_id, object_purpose, "
                        + "opaque_store_locator, LOWER(HEX(envelope_digest)), media_type "
                        + "FROM ycs_crypto_protected_objects WHERE protected_object_id=?",
                (rs, row) -> new ObjectRow(objectId, rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        fixture.bucket()), objectId);
    }

    private static TransactionTemplate transaction(Fixture fixture) {
        return new TransactionTemplate(fixture.transactions());
    }

    private static Tenant insertTenant(JdbcTemplate jdbc, String marker) {
        return insertTenant(jdbc, marker, creditCode(marker));
    }

    private static Tenant insertTenant(JdbcTemplate jdbc, String marker, String creditCode) {
        String tenantNo = "T" + randomHex(15);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO tenants "
                    + "(tenant_no,short_name,full_name,unified_social_credit_code,legal_rep_name,"
                    + "contact_name,verification_status,lifecycle_status) "
                    + "VALUES (?,?,?,?,?,?,'PENDING','SUBMITTED')", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, tenantNo);
            statement.setString(2, "phase03");
            statement.setString(3, "Phase03 Registration " + marker);
            statement.setString(4, creditCode);
            statement.setString(5, "Representative");
            statement.setString(6, "Contact");
            return statement;
        }, key);
        Number generatedKey = key.getKey();
        if (generatedKey == null || generatedKey.longValue() < 1) {
            throw new IllegalStateException("tenant identity allocation failed");
        }
        Tenant tenant = new Tenant();
        tenant.setId(generatedKey.longValue());
        tenant.setTenantNo(tenantNo);
        return tenant;
    }

    private static void persistProtectedTenant(JdbcTemplate jdbc, Tenant tenant) {
        jdbc.update("UPDATE tenants SET legal_rep_id_no_encrypted=?, contact_id_no_encrypted=?, "
                        + "contact_phone_encrypted=?, business_license_url=?, legal_rep_id_front_url=?, "
                        + "legal_rep_id_back_url=?, shortlink_domain_proof_url=?, trademark_proof_url=? "
                        + "WHERE id=?",
                field(tenant, "legalRepIdNoEncrypted"), field(tenant, "contactIdNoEncrypted"),
                field(tenant, "contactPhoneEncrypted"), field(tenant, "businessLicenseObjectId"),
                field(tenant, "legalRepIdFrontObjectId"), field(tenant, "legalRepIdBackObjectId"),
                field(tenant, "shortlinkDomainProofObjectId"), field(tenant, "trademarkProofObjectId"),
                tenant.getId());
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof byte[] bytes ? bytes.clone() : value;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("protected entity inspection unavailable", failure);
        }
    }

    private static void assertProtectedTenantRow(Fixture fixture,
                                                 long tenantId,
                                                 RegistrationObjects expected) {
        fixture.jdbc().queryForObject("SELECT legal_rep_id_no_encrypted, contact_id_no_encrypted, "
                        + "contact_phone_encrypted, business_license_url, legal_rep_id_front_url, "
                        + "legal_rep_id_back_url, shortlink_domain_proof_url, trademark_proof_url "
                        + "FROM tenants WHERE id=?", (rs, row) -> {
            ProtectedFieldCodec codec = new ProtectedFieldCodec(
                    new EnvelopeCodec(), fixture.keyAdapter(), new SecureRandom(), FIELD_REFERENCE);
            List<String> fields = List.of("legal_rep_id_no_encrypted",
                    "contact_id_no_encrypted", "contact_phone_encrypted");
            List<String> expectedPlaintext = List.of(
                    "11010519491231002X", "11010519491231002X", "13800138000");
            for (int column = 1; column <= 3; column++) {
                byte[] envelope = rs.getBytes(column);
                assertThat(Arrays.copyOf(envelope, 4)).containsExactly(
                        "YCSE".getBytes(StandardCharsets.US_ASCII));
                String encoded = new String(envelope, StandardCharsets.ISO_8859_1);
                assertThat(encoded).doesNotContain("11010519491231002X", "13800138000");
                byte[] recovered = codec.unprotect(envelope, new ProtectionContext(
                                ProtectionContext.Purpose.DATABASE_FIELD,
                                "crypto-storage-bootstrap", "tenants", fields.get(column - 1),
                                "tenant:" + tenantId, "tenant_id=" + tenantId),
                        EnvelopeCodec.Target.DATABASE_FIELD);
                assertThat(new String(recovered, StandardCharsets.US_ASCII))
                        .isEqualTo(expectedPlaintext.get(column - 1));
                Arrays.fill(recovered, (byte) 0);
            }
            assertThat(List.of(rs.getString(4), rs.getString(5), rs.getString(6),
                    rs.getString(7), rs.getString(8))).containsExactly(
                    expected.businessLicense(), expected.front(), expected.back(),
                    expected.shortlink(), expected.trademark());
            return true;
        }, tenantId);
        passed(8);
    }

    private static void assertSessionAndObjects(JdbcTemplate jdbc,
                                                RegistrationObjects objects,
                                                String sessionState,
                                                String objectState) {
        assertThat(sessionState(jdbc, objects.session().registrationObjectSessionId()))
                .isEqualTo(sessionState);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ycs_crypto_protected_objects "
                + "WHERE registration_session_id=? AND object_state=?", Long.class,
                objects.session().registrationObjectSessionId(), objectState)).isEqualTo(5);
        if ("CLAIMED".equals(objectState)) {
            List<String> claims = jdbc.queryForList("SELECT claim_reference FROM "
                    + "ycs_crypto_protected_objects WHERE registration_session_id=? "
                    + "AND object_state='CLAIMED'", String.class,
                    objects.session().registrationObjectSessionId());
            assertThat(claims).hasSize(5).doesNotHaveDuplicates()
                    .allMatch(value -> value.matches("claim_v1_[a-f0-9]{32}"));
        }
    }

    private static int sessionAttempts(JdbcTemplate jdbc, String sessionId) {
        return jdbc.queryForObject("SELECT admitted_attempt_count FROM "
                + "ycs_crypto_registration_sessions WHERE registration_session_id=?",
                Integer.class, sessionId);
    }

    private static int purposeAttempts(JdbcTemplate jdbc, String sessionId, String purpose) {
        return jdbc.queryForObject("SELECT admitted_attempt_count FROM "
                + "ycs_crypto_registration_upload_attempts WHERE registration_session_id=? "
                + "AND object_purpose=?", Integer.class, sessionId, purpose);
    }

    private static String sessionState(JdbcTemplate jdbc, String sessionId) {
        return jdbc.queryForObject("SELECT session_state FROM ycs_crypto_registration_sessions "
                + "WHERE registration_session_id=?", String.class, sessionId);
    }

    private static byte[] body(TenantRegistrationObjectSessionService.UploadPurpose purpose,
                               String marker) {
        return switch (purpose) {
            case BUSINESS_LICENSE, SHORTLINK_DOMAIN_PROOF, TRADEMARK_PROOF -> pdf(256, marker);
            case LEGAL_REP_ID_FRONT, LEGAL_REP_ID_BACK -> png(256, marker);
        };
    }

    private static String media(TenantRegistrationObjectSessionService.UploadPurpose purpose) {
        return switch (purpose) {
            case BUSINESS_LICENSE, SHORTLINK_DOMAIN_PROOF, TRADEMARK_PROOF -> "application/pdf";
            case LEGAL_REP_ID_FRONT, LEGAL_REP_ID_BACK -> "image/png";
        };
    }

    private static byte[] pdf(int size, String marker) {
        byte[] body = new byte[size];
        Arrays.fill(body, (byte) 'P');
        byte[] prefix = ("%PDF-1.7\n" + marker).getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(prefix, 0, body, 0, Math.min(prefix.length, body.length));
        return body;
    }

    private static byte[] png(int size, String marker) {
        byte[] body = new byte[size];
        byte[] prefix = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        byte[] text = marker.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(text, 0, body, prefix.length, Math.min(text.length, size - prefix.length));
        return body;
    }

    private static String creditCode(String marker) {
        return "91" + sha256(marker).substring(0, 16).toUpperCase(java.util.Locale.ROOT);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String randomHex(int size) {
        return HexFormat.of().formatHex(randomBytes(size));
    }

    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception failure) {
            throw new IllegalStateException("digest unavailable");
        }
    }

    private static S3Client s3(URI endpoint, boolean anonymous) {
        AwsCredentialsProvider credentials = anonymous
                ? AnonymousCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(
                requiredEnvironment("PHASE03_MINIO_USER"),
                requiredEnvironment("PHASE03_MINIO_PASSWORD")));
        return S3Client.builder().endpointOverride(endpoint).region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .credentialsProvider(credentials).httpClient(UrlConnectionHttpClient.create()).build();
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

    private static AdapterRuntime openAdapter(
            Phase03ServiceHarness.SoftHsmHandoff handoff,
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            boolean retiringIssueMode) {
        List<Pkcs11KeyDescriptor> descriptors = List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                        FIELD_REFERENCE, FIELD_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, 1,
                        "snapshot-recovery.v1", SNAPSHOT_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 1,
                        "mobile-index.v1", MOBILE_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                        "object-digest.v1", OBJECT_ACTIVE_ALIAS, retiringIssueMode
                                ? Pkcs11KeyDescriptor.State.RETIRING
                                : Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 2,
                        "object-digest.v2", OBJECT_RETIRING_ALIAS, retiringIssueMode
                                ? Pkcs11KeyDescriptor.State.ACTIVE
                                : Pkcs11KeyDescriptor.State.RETIRING),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                        "registration-digest.v1", UPLOAD_ACTIVE_ALIAS, retiringIssueMode
                                ? Pkcs11KeyDescriptor.State.RETIRING
                                : Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 2,
                        "registration-digest.v2", UPLOAD_RETIRING_ALIAS, retiringIssueMode
                                ? Pkcs11KeyDescriptor.State.ACTIVE
                                : Pkcs11KeyDescriptor.State.RETIRING));
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                handoff.library(), List.of(handoff.library()), handoff.slot(),
                "phase03-object-storage", () -> handoff.userPin().clone(), descriptors);
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

    private static RetiringDigestProof issueRetiringDigests(
            Phase03ServiceHarness.SoftHsmHandoff handoff,
            DataSource dataSource,
            DataSourceTransactionManager transactions) {
        byte[] secret = randomBytes(OpaqueTokenDigestPort.TOKEN_SECRET_BYTES);
        OpaqueTokenDigestPort.Binding binding = new OpaqueTokenDigestPort.Binding(
                "tenant:retiring-proof", "subject:retiring-proof", "resource:retiring-proof");
        try (AdapterRuntime runtime = openAdapter(handoff, dataSource, transactions, true)) {
            return new RetiringDigestProof(binding, secret,
                    runtime.adapter().issue(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                            binding, secret),
                    runtime.adapter().issue(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                            binding, secret));
        } catch (RuntimeException failure) {
            Arrays.fill(secret, (byte) 0);
            throw failure;
        }
    }

    private static Pkcs11KeyDescriptor descriptor(Pkcs11KeyDescriptor.Purpose purpose,
                                                  long version,
                                                  String reference,
                                                  String alias,
                                                  Pkcs11KeyDescriptor.State state) {
        return new Pkcs11KeyDescriptor(purpose, version, reference, alias, state,
                purpose.isWrappingKey()
                        ? "AES" : "HmacSHA256", 256);
    }

    private static void seedKeyMetadata(JdbcTemplate jdbc) {
        insertKey(jdbc, "FIELD_ENCRYPTION_KEK", 1, FIELD_REFERENCE, "ACTIVE");
        insertKey(jdbc, "SNAPSHOT_RECOVERY", 1, "snapshot-recovery.v1", "ACTIVE");
        insertKey(jdbc, "MOBILE_BLIND_INDEX", 1, "mobile-index.v1", "ACTIVE");
        insertKey(jdbc, "OBJECT_CAPABILITY_DIGEST", 1, "object-digest.v1", "ACTIVE");
        insertKey(jdbc, "OBJECT_CAPABILITY_DIGEST", 2, "object-digest.v2", "RETIRING");
        insertKey(jdbc, "REGISTRATION_UPLOAD_DIGEST", 1, "registration-digest.v1", "ACTIVE");
        insertKey(jdbc, "REGISTRATION_UPLOAD_DIGEST", 2, "registration-digest.v2", "RETIRING");
    }

    private static void insertKey(JdbcTemplate jdbc, String purpose, long version,
                                  String reference, String state) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES (?,?,'pkcs11',?,?)", purpose, version, reference, state);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("integration environment unavailable");
        }
        return value;
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

    private static void passed() {
        ASSERTIONS.incrementAndGet();
    }

    private static void passed(int count) {
        ASSERTIONS.addAndGet(count);
    }

    private static void provisionKeys(Path destination,
                                      Phase03ServiceHarness.SoftHsmHandoff handoff) throws Exception {
        Path header;
        try (var files = Files.walk(destination.resolve("source"))) {
            List<Path> headers = files.filter(path -> path.getFileName().toString().equals("cryptoki.h"))
                    .filter(Files::isRegularFile).toList();
            assertThat(headers).hasSize(1);
            header = headers.getFirst();
        }
        Path source = destination.resolve("runtime/plan30-key-provisioner.c");
        Path helper = destination.resolve("runtime/plan30-key-provisioner");
        Files.writeString(source, NATIVE_KEY_PROVISIONER, StandardCharsets.US_ASCII);
        Phase03ServiceHarness.runChecked(List.of("/usr/bin/cc", "-std=c11", "-O2",
                        "-I", header.getParent().toString(), source.toString(),
                        handoff.library().toString(), "-Wl,-rpath," + handoff.library().getParent(),
                        "-o", helper.toString()), Map.of());
        Phase03ServiceHarness.runChecked(List.of(helper.toString(), handoff.pinSource().toString(),
                        Long.toUnsignedString(handoff.slot())),
                Map.of("SOFTHSM2_CONF", handoff.config().toString()));
    }

    private record Fixture(JdbcTemplate jdbc,
                           DataSourceTransactionManager transactions,
                           S3PrivateObjectStoreAdapter store,
                           ProtectedObjectMetadataRepository metadata,
                           ObjectCapabilityService capabilities,
                           ProtectedObjectService objectService,
                           TenantRegistrationObjectSessionService sessions,
                           TenantRegistrationProtectionAdapter registration,
                           SunPkcs11KeyAdapter keyAdapter,
                           Clock clock,
                           String bucket) {
    }

    private static final class RetiringDigestProof {
        private final OpaqueTokenDigestPort.Binding binding;
        private final byte[] secret;
        private final VersionedTokenDigest objectDigest;
        private final VersionedTokenDigest uploadDigest;

        private RetiringDigestProof(OpaqueTokenDigestPort.Binding binding,
                                    byte[] secret,
                                    VersionedTokenDigest objectDigest,
                                    VersionedTokenDigest uploadDigest) {
            this.binding = binding;
            this.secret = secret.clone();
            this.objectDigest = objectDigest;
            this.uploadDigest = uploadDigest;
            Arrays.fill(secret, (byte) 0);
        }

        OpaqueTokenDigestPort.Binding binding() { return binding; }
        byte[] secret() { return secret.clone(); }
        VersionedTokenDigest objectDigest() { return objectDigest; }
        VersionedTokenDigest uploadDigest() { return uploadDigest; }

        void destroy() {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private record AdapterRuntime(Pkcs11ProviderFactory.Session session,
                                  SunPkcs11KeyAdapter adapter) implements AutoCloseable {
        @Override
        public void close() {
            session.close();
        }
    }

    private record ObjectRow(String objectId,
                             String sessionId,
                             String tenantDraftId,
                             String purpose,
                             String storageKey,
                             String sha256,
                             String mediaType,
                             String bucket) {
    }

    private record RegistrationObjects(
            TenantRegistrationObjectSessionService.CreatedSession session,
            Map<TenantRegistrationObjectSessionService.UploadPurpose, String> objects) {
        RegistrationObjects {
            objects = Map.copyOf(objects);
        }

        String businessLicense() { return objects.get(TenantRegistrationObjectSessionService.UploadPurpose.BUSINESS_LICENSE); }
        String front() { return objects.get(TenantRegistrationObjectSessionService.UploadPurpose.LEGAL_REP_ID_FRONT); }
        String back() { return objects.get(TenantRegistrationObjectSessionService.UploadPurpose.LEGAL_REP_ID_BACK); }
        String shortlink() { return objects.get(TenantRegistrationObjectSessionService.UploadPurpose.SHORTLINK_DOMAIN_PROOF); }
        String trademark() { return objects.get(TenantRegistrationObjectSessionService.UploadPurpose.TRADEMARK_PROOF); }

        TenantRegistrationRequest request() {
            return requestWithFront(front());
        }

        TenantRegistrationRequest requestWithFront(String frontObject) {
            return new TenantRegistrationRequest("phase03", "Phase03 Registration Proof",
                    creditCode("registration"), session.registrationObjectSessionId(),
                    businessLicense(), "Representative", "11010519491231002X", frontObject,
                    back(), "Contact", "11010519491231002X", "13800138000", shortlink(), trademark());
        }
    }

    private static final class CountingStore implements PrivateObjectStorePort {
        private final PrivateObjectStorePort delegate;
        private int fetches;

        private CountingStore(PrivateObjectStorePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public StoredObjectMetadata put(ObjectPurpose purpose, String mediaType,
                                        InputStream ciphertext, Long declaredContentLength) {
            return delegate.put(purpose, mediaType, ciphertext, declaredContentLength);
        }

        @Override
        public StoredCiphertext get(String storageKey, ObjectPurpose purpose) {
            fetches++;
            return delegate.get(storageKey, purpose);
        }

        @Override
        public StoredObjectMetadata head(String storageKey, ObjectPurpose purpose) {
            fetches++;
            return delegate.head(storageKey, purpose);
        }

        @Override
        public void delete(String storageKey, ObjectPurpose purpose) {
            delegate.delete(storageKey, purpose);
        }

        int fetches() {
            return fetches;
        }
    }

    private static final class BlockingDeleteStore implements PrivateObjectStorePort {
        private final PrivateObjectStorePort delegate;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch proceed = new CountDownLatch(1);

        private BlockingDeleteStore(PrivateObjectStorePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public StoredObjectMetadata put(ObjectPurpose purpose, String mediaType,
                                        InputStream ciphertext, Long declaredContentLength) {
            return delegate.put(purpose, mediaType, ciphertext, declaredContentLength);
        }

        @Override
        public StoredCiphertext get(String storageKey, ObjectPurpose purpose) {
            return delegate.get(storageKey, purpose);
        }

        @Override
        public StoredObjectMetadata head(String storageKey, ObjectPurpose purpose) {
            return delegate.head(storageKey, purpose);
        }

        @Override
        public void delete(String storageKey, ObjectPurpose purpose) {
            entered.countDown();
            try {
                if (!proceed.await(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("delete race release unavailable");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            delegate.delete(storageKey, purpose);
        }

        void awaitEntered() throws InterruptedException {
            assertThat(entered.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }

        void release() {
            proceed.countDown();
        }
    }

    private static final class PutUnavailableStore implements PrivateObjectStorePort {
        private final PrivateObjectStorePort delegate;

        private PutUnavailableStore(PrivateObjectStorePort delegate) {
            this.delegate = delegate;
        }

        @Override
        public StoredObjectMetadata put(ObjectPurpose purpose, String mediaType,
                                        InputStream ciphertext, Long declaredContentLength) {
            throw Failure.unavailable();
        }

        @Override public StoredCiphertext get(String storageKey, ObjectPurpose purpose) { return delegate.get(storageKey, purpose); }
        @Override public StoredObjectMetadata head(String storageKey, ObjectPurpose purpose) { return delegate.head(storageKey, purpose); }
        @Override public void delete(String storageKey, ObjectPurpose purpose) { delegate.delete(storageKey, purpose); }
    }

    private static final class CountingInputStream extends InputStream {
        private long remaining;

        private CountingInputStream(long remaining) {
            this.remaining = remaining;
        }

        @Override
        public int read() {
            if (remaining == 0) {
                return -1;
            }
            remaining--;
            return 'P';
        }
    }

    private static final class RollbackProbe extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private RollbackProbe() {
            super("rollback probe", null, false, false);
        }
    }

    private static final String NATIVE_KEY_PROVISIONER = """
            #include "cryptoki.h"
            #include <stdio.h>
            #include <stdlib.h>
            #include <string.h>

            static void trim(char *value) {
              size_t size = strlen(value);
              while (size > 0 && (value[size - 1] == '\\n' || value[size - 1] == '\\r')) value[--size] = 0;
            }
            static int generate(CK_SESSION_HANDLE session, const char *label, int aes) {
              CK_BBOOL yes = CK_TRUE, no = CK_FALSE;
              CK_OBJECT_CLASS klass = CKO_SECRET_KEY;
              CK_KEY_TYPE type = aes ? CKK_AES : CKK_GENERIC_SECRET;
              CK_ULONG length = 32;
              CK_ATTRIBUTE attrs[] = {
                {CKA_CLASS, &klass, sizeof(klass)}, {CKA_KEY_TYPE, &type, sizeof(type)},
                {CKA_TOKEN, &yes, sizeof(yes)}, {CKA_PRIVATE, &yes, sizeof(yes)},
                {CKA_SENSITIVE, &yes, sizeof(yes)}, {CKA_EXTRACTABLE, &no, sizeof(no)},
                {CKA_ENCRYPT, aes ? &yes : &no, sizeof(yes)}, {CKA_DECRYPT, aes ? &yes : &no, sizeof(yes)},
                {CKA_WRAP, aes ? &yes : &no, sizeof(yes)}, {CKA_UNWRAP, aes ? &yes : &no, sizeof(yes)},
                {CKA_SIGN, aes ? &no : &yes, sizeof(yes)}, {CKA_VERIFY, aes ? &no : &yes, sizeof(yes)},
                {CKA_VALUE_LEN, &length, sizeof(length)},
                {(CK_ATTRIBUTE_TYPE)CKA_LABEL, (void *)label, strlen(label)}
              };
              CK_MECHANISM mechanism = {aes ? CKM_AES_KEY_GEN : CKM_GENERIC_SECRET_KEY_GEN, NULL_PTR, 0};
              CK_OBJECT_HANDLE key = 0;
              return C_GenerateKey(session, &mechanism, attrs, sizeof(attrs) / sizeof(attrs[0]), &key) == CKR_OK ? 0 : 1;
            }
            int main(int argc, char **argv) {
              if (argc != 3) return 64;
              FILE *pins = fopen(argv[1], "r");
              char so_pin[128] = {0}, user_pin[128] = {0};
              if (!pins || !fgets(so_pin, sizeof(so_pin), pins) || !fgets(user_pin, sizeof(user_pin), pins)) return 65;
              fclose(pins); trim(user_pin);
              if (C_Initialize(NULL_PTR) != CKR_OK) return 66;
              char *end = NULL;
              unsigned long long parsed = strtoull(argv[2], &end, 10);
              if (!end || *end != 0) return 67;
              CK_SESSION_HANDLE session = 0;
              if (C_OpenSession((CK_SLOT_ID)parsed, CKF_SERIAL_SESSION | CKF_RW_SESSION,
                                NULL_PTR, NULL_PTR, &session) != CKR_OK) return 68;
              if (C_Login(session, CKU_USER, (CK_UTF8CHAR_PTR)user_pin, strlen(user_pin)) != CKR_OK) return 69;
              const char *aes[] = {"ycs.field-encryption-kek.v1", "ycs.snapshot-recovery.v1"};
              const char *hmac[] = {"ycs.mobile-blind-index.v1", "ycs.object-capability-digest.v1",
                "ycs.object-capability-digest.v2", "ycs.registration-upload-digest.v1",
                "ycs.registration-upload-digest.v2"};
              int failed = 0;
              for (size_t i = 0; i < sizeof(aes) / sizeof(aes[0]); i++) failed |= generate(session, aes[i], 1);
              for (size_t i = 0; i < sizeof(hmac) / sizeof(hmac[0]); i++) failed |= generate(session, hmac[i], 0);
              C_Logout(session); C_CloseSession(session); C_Finalize(NULL_PTR);
              memset(so_pin, 0, sizeof(so_pin)); memset(user_pin, 0, sizeof(user_pin));
              return failed ? 70 : 0;
            }
            """;
}
