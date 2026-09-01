package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.security.config.CryptoStorageStartupVerifier;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.OpaqueTokenDigestPort;
import com.ycsopen.sms.core.common.security.key.VersionedTokenDigest;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Plan-03 SoftHSM + MySQL proof for the production SunPKCS11 adapter. */
@EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
class Phase03Pkcs11IntegrationTest {

    private static final String SOURCE_SHA256 =
            "be14a5820ec457eac5154462ffae51ba5d8a643f6760514d4b4b83a77be91573";
    private static final String PHYSICAL_HSM_LIMITATION =
            "SoftHSM protocol conformance only; no physical-HSM certification";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String MAIN_ALIAS = CryptoStorageStartupVerifier.FIELD_KEK_ALIAS;
    private static final String SNAPSHOT_ALIAS = "ycs.snapshot-recovery.v1";
    private static final String RETIRING_ALIAS = "ycs.field-encryption-kek.v2";
    private static final String CEILING_ALIAS = "ycs.field-ceiling.v3";
    private static final String CONCURRENT_ALIAS = "ycs.field-concurrent.v4";
    private static final String FAILURE_ALIAS = "ycs.field-failure.v5";
    private static final String EXTRACTABLE_ALIAS = "ycs.field-extractable.v6";
    private static final String MOBILE_ACTIVE_ALIAS = CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS;
    private static final String MOBILE_RETIRING_ALIAS = "ycs.mobile-blind-index.v2";
    private static final String OBJECT_ALIAS = CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS;
    private static final String REGISTRATION_ALIAS = CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS;

    @Test
    void provesProductionAdapterAgainstProvisionedSoftHsmAndRealMySql() throws Exception {
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            Phase03ServiceHarness.ServiceSession mysql = fixtures.mysql();
            Phase03ServiceHarness.SoftHsmHandoff handoff = fixtures.softHsm().softHsm();
            Path destination = handoff.config().getParent().getParent();
            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            String classpath = System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path"));
            Map<String, String> environment = Map.of(
                    "SOFTHSM2_CONF", handoff.config().toString(),
                    "PHASE03_MYSQL_HOST", mysql.host(),
                    "PHASE03_MYSQL_PORT", Integer.toString(mysql.port()),
                    "PHASE03_MYSQL_USER", mysql.username(),
                    "PHASE03_MYSQL_PASSWORD", mysql.password(),
                    "PHASE03_SOFTHSM_DESTINATION", destination.toString());

            Phase03ServiceHarness.CommandResult proof = Phase03ServiceHarness.runChecked(
                    List.of(javaExecutable.toString(), "-cp", classpath,
                            Phase03Pkcs11IntegrationTest.class.getName(), "real-proof"), environment);
            String output = proof.stdout().strip();
            assertThat(output).matches("PHASE03_PKCS11_PASS source_sha256=[a-f0-9]{64} "
                    + "runtime_sha256=[a-f0-9]{64} mechanism_sha256=[a-f0-9]{64} "
                    + "attribute_sha256=[a-f0-9]{64} counts=983040,983041,1048576,1048576,1 "
                    + "concurrency=16");
            assertThat(output.toLowerCase()).doesNotContain(
                    "pin", "password", "secret", "alias", "path", "library=", "token=", "provider=");
            assertThat(PHYSICAL_HSM_LIMITATION).contains("no physical-HSM certification");
        }
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
        provisionPurposeSeparatedKeys(destination, handoff);

        DataSource dataSource = mysqlDataSource();
        migrateWithoutLeakingFixtureCoordinates(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        seedKeyMetadata(jdbc);

        Pkcs11FailureMapper mapper = new Pkcs11FailureMapper();

        byte[] firstNonce;
        String runtimeHash;
        try (AdapterRuntime runtime = open(handoff, dataSource, transactions, mapper,
                field(1, "field-main.v1", MAIN_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE))) {
            runtimeHash = runtime.session().tokenIdentityHash();
            verifyStartup(runtime.adapter(), handoff);
            firstNonce = assertWrapDigestAndTamperSemantics(runtime.adapter());
        }
        assertThat(count(jdbc, 1)).isEqualTo(983_040L);
        assertThat(state(jdbc, 1)).isEqualTo("ROTATION_REQUIRED");

        try (AdapterRuntime reopened = open(handoff, dataSource, transactions, mapper,
                field(1, "field-main.v1", MAIN_ALIAS, Pkcs11KeyDescriptor.State.ROTATION_REQUIRED))) {
            WrappedDataKey restarted = reopened.adapter().wrap(dek(), header("field-main.v1"), context());
            assertThat(restarted.wrapNonce()).isNotEqualTo(firstNonce);
        }
        long restartedCount = count(jdbc, 1);

        try (AdapterRuntime ceiling = open(handoff, dataSource, transactions, mapper,
                field(3, "field-ceiling.v3", CEILING_ALIAS,
                        Pkcs11KeyDescriptor.State.ROTATION_REQUIRED))) {
            ceiling.adapter().wrap(dek(), header("field-ceiling.v3"), context());
            assertThatThrownBy(() -> ceiling.adapter().wrap(
                    dek(), header("field-ceiling.v3"), context()))
                    .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                    .extracting(failure -> ((Pkcs11FailureMapper.Pkcs11OperationException) failure).category())
                    .isEqualTo(Pkcs11FailureMapper.Category.WRAP_LIMIT_REACHED);
        }
        long ceilingCount = count(jdbc, 3);

        int successfulConcurrentWraps;
        try (AdapterRuntime concurrent = open(handoff, dataSource, transactions, mapper,
                field(4, "field-concurrent.v4", CONCURRENT_ALIAS,
                        Pkcs11KeyDescriptor.State.ROTATION_REQUIRED))) {
            successfulConcurrentWraps = concurrentWraps(concurrent.adapter(), "field-concurrent.v4");
        }
        long concurrentCount = count(jdbc, 4);

        long burnedCount;
        try (AdapterRuntime failure = open(handoff, dataSource, transactions, mapper,
                field(5, "field-failure.v5", FAILURE_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE))) {
            disableEncrypt(destination, handoff, FAILURE_ALIAS);
            assertThatThrownBy(() -> failure.adapter().wrap(
                    dek(), header("field-failure.v5"), context()))
                    .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                    .extracting(error -> ((Pkcs11FailureMapper.Pkcs11OperationException) error).category())
                    .isEqualTo(Pkcs11FailureMapper.Category.OPERATION_FAILED);
            burnedCount = count(jdbc, 5);
        }

        assertThat(restartedCount).isEqualTo(983_041L);
        assertThat(ceilingCount).isEqualTo(1_048_576L);
        assertThat(successfulConcurrentWraps).isEqualTo(16);
        assertThat(concurrentCount).isEqualTo(1_048_576L);
        assertThat(burnedCount).isOne();
        assertMissingAndWrongAttributeFailClosed(handoff, mapper);

        System.out.println("PHASE03_PKCS11_PASS source_sha256=" + SOURCE_SHA256
                + " runtime_sha256=" + runtimeHash
                + " mechanism_sha256=" + sha256("CKM_AES_GCM\0CKM_SHA256_HMAC")
                + " attribute_sha256="
                + sha256("CKA_NOT_EXTRACTABLE\0CKA_PRIVATE\0CKA_SENSITIVE\0CKA_TOKEN")
                + " counts=983040," + restartedCount + "," + ceilingCount + ","
                + concurrentCount + "," + burnedCount
                + " concurrency=" + successfulConcurrentWraps);
    }

    private static void migrateWithoutLeakingFixtureCoordinates(DataSource dataSource) {
        PrintStream originalOutput = System.out;
        try (PrintStream discardedOutput = new PrintStream(OutputStream.nullOutputStream(), true,
                StandardCharsets.UTF_8)) {
            System.setOut(discardedOutput);
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .placeholderReplacement(false).load().migrate();
        } finally {
            System.setOut(originalOutput);
        }
    }

    private static void verifyStartup(SunPkcs11KeyAdapter adapter,
                                      Phase03ServiceHarness.SoftHsmHandoff handoff) {
        CryptoStorageStartupVerifier.Settings settings = new CryptoStorageStartupVerifier.Settings(
                true, CryptoStorageStartupVerifier.ADAPTER_ID, CryptoStorageStartupVerifier.PROVIDER_ID,
                handoff.library(), List.of(handoff.library()), handoff.slot(), "phase03-real-token",
                CryptoStorageStartupVerifier.CredentialSource.ENVIRONMENT, "YCSOPEN_PKCS11_PIN",
                CryptoStorageStartupVerifier.REQUIRED_MECHANISMS,
                CryptoStorageStartupVerifier.REQUIRED_ATTRIBUTES,
                KekWrapUsageRepository.ROTATION_REQUIRED_AT, KekWrapUsageRepository.HARD_CEILING,
                MAIN_ALIAS, MOBILE_ACTIVE_ALIAS, OBJECT_ALIAS, REGISTRATION_ALIAS);
        new CryptoStorageStartupVerifier(settings, adapter, Set.of("phase03-integration")).verify();
    }

    private static byte[] assertWrapDigestAndTamperSemantics(SunPkcs11KeyAdapter adapter) {
        byte[] header = header("field-main.v1");
        ProtectionContext context = context();
        WrappedDataKey wrapped = adapter.wrap(dek(), header, context);
        assertThat(adapter.unwrap(wrapped, header, context)).hasSize(32);

        byte[] tamperedWrapped = wrapped.wrappedDek();
        tamperedWrapped[tamperedWrapped.length - 1] ^= 1;
        assertThatThrownBy(() -> adapter.unwrap(
                new WrappedDataKey(wrapped.keyReference(), wrapped.wrapNonce(), tamperedWrapped),
                header, context))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class);
        assertThatThrownBy(() -> adapter.unwrap(wrapped, header,
                new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                        "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted",
                        "tenant:2", "message_id=proof")))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class);

        BlindIndexPort.Context mobileContext = new BlindIndexPort.Context(
                "MESSAGE_TASK", "mobile_encrypted", BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:1");
        BlindIndexPort.OrderedIndexes written = adapter.writeIndexes("13800138000", mobileContext);
        assertThat(written.values()).hasSize(2);
        assertThat(adapter.queryIndexes("13800138000", mobileContext)).isEqualTo(written);

        byte[] token = new byte[OpaqueTokenDigestPort.TOKEN_SECRET_BYTES];
        RANDOM.nextBytes(token);
        OpaqueTokenDigestPort.Binding binding = new OpaqueTokenDigestPort.Binding(
                "tenant:1", "subject:1", "resource:1");
        VersionedTokenDigest object = adapter.issue(
                OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY, binding, token);
        VersionedTokenDigest upload = adapter.issue(
                OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD, binding, token);
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                binding, token, object)).isTrue();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                binding, token, upload)).isTrue();
        token[0] ^= 1;
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.OBJECT_CAPABILITY,
                binding, token, object)).isFalse();
        assertThat(adapter.verify(OpaqueTokenDigestPort.Purpose.REGISTRATION_UPLOAD,
                binding, token, object)).isFalse();
        assertThat(object.digest()).isNotEqualTo(upload.digest());
        return wrapped.wrapNonce();
    }

    private static int concurrentWraps(SunPkcs11KeyAdapter adapter, String reference) throws Exception {
        int callers = 32;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < callers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        adapter.wrap(dek(), header(reference), context());
                        return true;
                    } catch (Pkcs11FailureMapper.Pkcs11OperationException expected) {
                        assertThat(expected.category()).isEqualTo(Pkcs11FailureMapper.Category.WRAP_LIMIT_REACHED);
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();
            int successful = 0;
            for (Future<Boolean> result : results) {
                successful += result.get() ? 1 : 0;
            }
            return successful;
        }
    }

    private static AdapterRuntime open(Phase03ServiceHarness.SoftHsmHandoff handoff,
                                       DataSource dataSource,
                                       DataSourceTransactionManager transactions,
                                       Pkcs11FailureMapper mapper,
                                       Pkcs11KeyDescriptor fieldKey) {
        Pkcs11CryptoStorageProperties properties = properties(handoff, fieldKey);
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(mapper).open(properties);
        try {
            SunPkcs11KeyAdapter adapter = new SunPkcs11KeyAdapter(session, properties,
                    new KekWrapUsageRepository(new JdbcTemplate(dataSource), transactions, mapper), mapper);
            return new AdapterRuntime(session, adapter);
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    private static Pkcs11CryptoStorageProperties properties(
            Phase03ServiceHarness.SoftHsmHandoff handoff, Pkcs11KeyDescriptor fieldKey) {
        return new Pkcs11CryptoStorageProperties(handoff.library(), List.of(handoff.library()),
                handoff.slot(), "phase03-real-token", () -> handoff.userPin().clone(),
                List.of(fieldKey,
                        field(2, "field-retiring.v2", RETIRING_ALIAS, Pkcs11KeyDescriptor.State.RETIRING),
                        new Pkcs11KeyDescriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY,
                                1, "snapshot-recovery.v1", SNAPSHOT_ALIAS,
                                Pkcs11KeyDescriptor.State.ACTIVE, "AES", 256),
                        hmac(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 1,
                                "mobile-index.v1", MOBILE_ACTIVE_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                        hmac(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 2,
                                "mobile-index.v2", MOBILE_RETIRING_ALIAS, Pkcs11KeyDescriptor.State.RETIRING),
                        hmac(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                                "object-digest.v1", OBJECT_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                        hmac(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                                "registration-digest.v1", REGISTRATION_ALIAS,
                                Pkcs11KeyDescriptor.State.ACTIVE)));
    }

    private static void assertMissingAndWrongAttributeFailClosed(
            Phase03ServiceHarness.SoftHsmHandoff handoff, Pkcs11FailureMapper mapper) {
        Pkcs11CryptoStorageProperties missing = properties(handoff,
                field(1, "field-main.v1", "ycs.missing.v1", Pkcs11KeyDescriptor.State.ACTIVE));
        assertSanitizedProviderFailure(() -> new Pkcs11ProviderFactory(mapper).open(missing),
                handoff, "ycs.missing.v1");

        Pkcs11CryptoStorageProperties extractable = properties(handoff,
                field(6, "field-extractable.v6", EXTRACTABLE_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE));
        assertSanitizedProviderFailure(() -> new Pkcs11ProviderFactory(mapper).open(extractable),
                handoff, EXTRACTABLE_ALIAS);
    }

    private static void assertSanitizedProviderFailure(Runnable operation,
                                                       Phase03ServiceHarness.SoftHsmHandoff handoff,
                                                       String alias) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .satisfies(failure -> assertThat(failure.getMessage())
                        .doesNotContain(alias, handoff.library().toString(), new String(handoff.userPin()))
                        .matches("PKCS11_CONFIGURATION correlation=[a-f0-9]{32} descriptor=none"));
    }

    private static void seedKeyMetadata(JdbcTemplate jdbc) {
        insertField(jdbc, 1, "field-main.v1", "ACTIVE", 983_039, false);
        insertField(jdbc, 2, "field-retiring.v2", "RETIRING", 0, false);
        insertField(jdbc, 3, "field-ceiling.v3", "ROTATION_REQUIRED", 1_048_575, true);
        insertField(jdbc, 4, "field-concurrent.v4", "ROTATION_REQUIRED", 1_048_560, true);
        insertField(jdbc, 5, "field-failure.v5", "ACTIVE", 0, false);
        insertField(jdbc, 6, "field-extractable.v6", "ACTIVE", 0, false);
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose, key_version, provider_id, provider_key_reference, key_state) "
                        + "VALUES ('SNAPSHOT_RECOVERY', 1, 'pkcs11', 'snapshot-recovery.v1', 'ACTIVE')");
        insertHmac(jdbc, "MOBILE_BLIND_INDEX", 1, "mobile-index.v1", "ACTIVE");
        insertHmac(jdbc, "MOBILE_BLIND_INDEX", 2, "mobile-index.v2", "RETIRING");
        insertHmac(jdbc, "OBJECT_CAPABILITY_DIGEST", 1, "object-digest.v1", "ACTIVE");
        insertHmac(jdbc, "REGISTRATION_UPLOAD_DIGEST", 1, "registration-digest.v1", "ACTIVE");
    }

    private static void insertField(JdbcTemplate jdbc, long version, String reference,
                                    String state, long count, boolean rotation) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose, key_version, provider_id, provider_key_reference, key_state, "
                        + "wrap_operation_count, rotation_required) VALUES "
                        + "('FIELD_ENCRYPTION_KEK', ?, 'pkcs11', ?, ?, ?, ?)",
                version, reference, state, count, rotation);
    }

    private static void insertHmac(JdbcTemplate jdbc, String purpose, long version,
                                   String reference, String state) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose, key_version, provider_id, provider_key_reference, key_state) "
                        + "VALUES (?, ?, 'pkcs11', ?, ?)", purpose, version, reference, state);
    }

    private static long count(JdbcTemplate jdbc, long version) {
        return jdbc.queryForObject("SELECT wrap_operation_count FROM ycs_crypto_key_references "
                + "WHERE purpose='FIELD_ENCRYPTION_KEK' AND key_version=?", Long.class, version);
    }

    private static String state(JdbcTemplate jdbc, long version) {
        return jdbc.queryForObject("SELECT key_state FROM ycs_crypto_key_references "
                + "WHERE purpose='FIELD_ENCRYPTION_KEK' AND key_version=?", String.class, version);
    }

    private static DataSource mysqlDataSource() {
        String url = "jdbc:mysql://" + requiredEnvironment("PHASE03_MYSQL_HOST") + ":"
                + requiredEnvironment("PHASE03_MYSQL_PORT") + "/phase01"
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        return new DriverManagerDataSource(url, requiredEnvironment("PHASE03_MYSQL_USER"),
                requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
    }

    private static Pkcs11KeyDescriptor field(long version, String reference, String alias,
                                              Pkcs11KeyDescriptor.State state) {
        return new Pkcs11KeyDescriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK,
                version, reference, alias, state, "AES", 256);
    }

    private static Pkcs11KeyDescriptor hmac(Pkcs11KeyDescriptor.Purpose purpose,
                                            long version, String reference, String alias,
                                            Pkcs11KeyDescriptor.State state) {
        return new Pkcs11KeyDescriptor(purpose, version, reference, alias, state, "HmacSHA256", 256);
    }

    private static byte[] dek() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return value;
    }

    private static byte[] header(String reference) {
        return new EnvelopeCodec().authenticatedHeader(reference, 32, EnvelopeCodec.Target.DATABASE_FIELD);
    }

    private static ProtectionContext context() {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted",
                "tenant:1", "message_id=proof");
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("integration environment unavailable");
        }
        return value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception failure) {
            throw new IllegalStateException("digest unavailable");
        }
    }

    private record AdapterRuntime(Pkcs11ProviderFactory.Session session,
                                  SunPkcs11KeyAdapter adapter) implements AutoCloseable {
        @Override
        public void close() {
            session.close();
        }
    }

    private static void provisionPurposeSeparatedKeys(
            Path destination, Phase03ServiceHarness.SoftHsmHandoff handoff) throws Exception {
        Path helper = compileNativeHelper(destination, handoff);
        Phase03ServiceHarness.runChecked(List.of(helper.toString(), handoff.pinSource().toString(),
                        Long.toUnsignedString(handoff.slot()), "provision"),
                Map.of("SOFTHSM2_CONF", handoff.config().toString()));
    }

    private static void disableEncrypt(Path destination,
                                       Phase03ServiceHarness.SoftHsmHandoff handoff,
                                       String alias) throws Exception {
        Path helper = destination.resolve("runtime/plan07-key-provisioner");
        Phase03ServiceHarness.runChecked(
                List.of(helper.toString(), handoff.pinSource().toString(),
                        Long.toUnsignedString(handoff.slot()), "disable-encrypt", alias),
                Map.of("SOFTHSM2_CONF", handoff.config().toString()));
    }

    private static Path compileNativeHelper(
            Path destination, Phase03ServiceHarness.SoftHsmHandoff handoff) throws Exception {
        Path header;
        try (var files = Files.walk(destination.resolve("source"))) {
            List<Path> headers = files.filter(path -> path.getFileName().toString().equals("cryptoki.h"))
                    .filter(Files::isRegularFile).toList();
            assertThat(headers).hasSize(1);
            header = headers.getFirst();
        }
        Path source = destination.resolve("runtime/plan07-key-provisioner.c");
        Path helper = destination.resolve("runtime/plan07-key-provisioner");
        Files.writeString(source, NATIVE_KEY_PROVISIONER, StandardCharsets.US_ASCII);
        Phase03ServiceHarness.runChecked(List.of("/usr/bin/cc", "-std=c11", "-O2",
                        "-I", header.getParent().toString(), source.toString(), handoff.library().toString(),
                        "-Wl,-rpath," + handoff.library().getParent(), "-o", helper.toString()), Map.of());
        return helper;
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
            static int generate(CK_SESSION_HANDLE session, const char *label, int aes, int extractable) {
              CK_BBOOL yes = CK_TRUE, no = CK_FALSE, exportable = extractable ? CK_TRUE : CK_FALSE;
              CK_BBOOL sensitive = extractable ? CK_FALSE : CK_TRUE;
              CK_OBJECT_CLASS klass = CKO_SECRET_KEY;
              CK_KEY_TYPE type = aes ? CKK_AES : CKK_GENERIC_SECRET;
              CK_ULONG length = 32;
              CK_ATTRIBUTE attrs[] = {
                {CKA_CLASS, &klass, sizeof(klass)}, {CKA_KEY_TYPE, &type, sizeof(type)},
                {CKA_TOKEN, &yes, sizeof(yes)}, {CKA_PRIVATE, &yes, sizeof(yes)},
                {CKA_SENSITIVE, &sensitive, sizeof(sensitive)}, {CKA_EXTRACTABLE, &exportable, sizeof(exportable)},
                {CKA_ENCRYPT, aes ? &yes : &no, sizeof(yes)}, {CKA_DECRYPT, aes ? &yes : &no, sizeof(yes)},
                {CKA_WRAP, aes ? &yes : &no, sizeof(yes)}, {CKA_UNWRAP, aes ? &yes : &no, sizeof(yes)},
                {CKA_SIGN, aes ? &no : &yes, sizeof(yes)}, {CKA_VERIFY, aes ? &no : &yes, sizeof(yes)},
                {CKA_VALUE_LEN, &length, sizeof(length)}, {(CK_ATTRIBUTE_TYPE)CKA_LABEL, (void *)label, strlen(label)}
              };
              CK_MECHANISM mechanism = {aes ? CKM_AES_KEY_GEN : CKM_GENERIC_SECRET_KEY_GEN, NULL_PTR, 0};
              CK_OBJECT_HANDLE key = 0;
              CK_RV rv = C_GenerateKey(session, &mechanism, attrs, sizeof(attrs) / sizeof(attrs[0]), &key);
              if (rv != CKR_OK) fprintf(stderr, "native_generation_failed rv=0x%lx\\n", (unsigned long)rv);
              return rv == CKR_OK ? 0 : 1;
            }
            static int disable_encrypt(CK_SESSION_HANDLE session, const char *label) {
              CK_OBJECT_CLASS klass = CKO_SECRET_KEY;
              CK_ATTRIBUTE find[] = {{CKA_CLASS, &klass, sizeof(klass)},
                                     {(CK_ATTRIBUTE_TYPE)CKA_LABEL, (void *)label, strlen(label)}};
              CK_OBJECT_HANDLE key = 0; CK_ULONG count = 0; CK_BBOOL no = CK_FALSE;
              if (C_FindObjectsInit(session, find, 2) != CKR_OK) return 1;
              CK_RV rv = C_FindObjects(session, &key, 1, &count);
              C_FindObjectsFinal(session);
              if (rv != CKR_OK || count != 1) return 1;
              CK_ATTRIBUTE change = {CKA_ENCRYPT, &no, sizeof(no)};
              return C_SetAttributeValue(session, key, &change, 1) == CKR_OK ? 0 : 1;
            }
            int main(int argc, char **argv) {
              if (argc < 4) { fprintf(stderr, "native_stage=arguments\\n"); return 64; }
              FILE *pins = fopen(argv[1], "r");
              char so_pin[128] = {0}, user_pin[128] = {0};
              if (!pins || !fgets(so_pin, sizeof(so_pin), pins) || !fgets(user_pin, sizeof(user_pin), pins)) {
                fprintf(stderr, "native_stage=credential_source\\n"); return 65;
              }
              fclose(pins); trim(user_pin);
              CK_RV rv = C_Initialize(NULL_PTR);
              if (rv != CKR_OK) { fprintf(stderr, "native_stage=initialize rv=0x%lx\\n", (unsigned long)rv); return 66; }
              char *slot_end = NULL;
              unsigned long long parsed_slot = strtoull(argv[2], &slot_end, 10);
              if (!slot_end || *slot_end != 0) { fprintf(stderr, "native_stage=slot_identity\\n"); return 67; }
              CK_SLOT_ID slot = (CK_SLOT_ID)parsed_slot;
              CK_TOKEN_INFO token_info;
              rv = C_GetTokenInfo(slot, &token_info);
              if (rv != CKR_OK) { fprintf(stderr, "native_stage=slot_identity rv=0x%lx\\n", (unsigned long)rv); return 68; }
              CK_SESSION_HANDLE session = 0;
              rv = C_OpenSession(slot, CKF_SERIAL_SESSION | CKF_RW_SESSION, NULL_PTR, NULL_PTR, &session);
              if (rv != CKR_OK) { fprintf(stderr, "native_stage=open_session rv=0x%lx\\n", (unsigned long)rv); return 69; }
              rv = C_Login(session, CKU_USER, (CK_UTF8CHAR_PTR)user_pin, strlen(user_pin));
              if (rv != CKR_OK) { fprintf(stderr, "native_stage=login rv=0x%lx\\n", (unsigned long)rv); return 70; }
              int failed = 0;
              if (strcmp(argv[3], "provision") == 0 && argc == 4) {
                const char *aes[] = {"ycs.field-encryption-kek.v1", "ycs.field-encryption-kek.v2",
                  "ycs.field-ceiling.v3", "ycs.field-concurrent.v4", "ycs.field-failure.v5",
                  "ycs.snapshot-recovery.v1"};
                const char *hmac[] = {"ycs.mobile-blind-index.v1", "ycs.mobile-blind-index.v2",
                  "ycs.object-capability-digest.v1", "ycs.registration-upload-digest.v1"};
                for (size_t i = 0; i < sizeof(aes) / sizeof(aes[0]); i++) failed |= generate(session, aes[i], 1, 0);
                failed |= generate(session, "ycs.field-extractable.v6", 1, 1);
                for (size_t i = 0; i < sizeof(hmac) / sizeof(hmac[0]); i++) failed |= generate(session, hmac[i], 0, 0);
              } else if (strcmp(argv[3], "disable-encrypt") == 0 && argc == 5) {
                failed = disable_encrypt(session, argv[4]);
              } else failed = 1;
              C_Logout(session); C_CloseSession(session); C_Finalize(NULL_PTR);
              memset(so_pin, 0, sizeof(so_pin)); memset(user_pin, 0, sizeof(user_pin));
              return failed ? 71 : 0;
            }
            """;
}
