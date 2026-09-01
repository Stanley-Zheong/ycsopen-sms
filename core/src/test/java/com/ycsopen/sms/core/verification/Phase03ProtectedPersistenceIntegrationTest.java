package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.security.config.CryptoStorageConfiguration;
import com.ycsopen.sms.core.common.security.config.CryptoStorageStartupVerifier;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.envelope.ProtectionFailure;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.KeyProtectionPort;
import com.ycsopen.sms.core.common.security.key.VersionedBlindIndex;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.persistence.BlindIndexLookupService;
import com.ycsopen.sms.core.common.security.persistence.BlindIndexMetadataRepository;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskProtectionAdapter;
import com.ycsopen.sms.core.common.security.persistence.PreparedMessageMobile;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import com.ycsopen.sms.core.domain.entity.BlacklistEntry;
import com.ycsopen.sms.core.domain.entity.MessageTask;
import com.ycsopen.sms.core.repository.BlacklistEntryRepository;
import com.ycsopen.sms.core.repository.MessageTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Physical Connector/J + production SunPKCS11 proof for protected message persistence. */
@EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
class Phase03ProtectedPersistenceIntegrationTest {

    private static final String FIELD_REFERENCE = "field-kek.v1";
    private static final String MOBILE_ACTIVE_REFERENCE = "mobile-index.v1";
    private static final String MOBILE_RETIRING_REFERENCE = "mobile-index.v2";
    private static final String MOBILE_RETIRING_ALIAS = "ycs.mobile-blind-index.v2";
    private static final long TENANT_ID = 73010L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicInteger ASSERTIONS = new AtomicInteger();

    @Test
    void provesCurrentWriterAndCompatibilityAtPhysicalBoundaries() throws Exception {
        String output;
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            Phase03ServiceHarness.ServiceSession mysql = fixtures.mysql();
            Phase03ServiceHarness.SoftHsmHandoff handoff = fixtures.softHsm().softHsm();
            Path destination = handoff.config().getParent().getParent();
            Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
            String classpath = System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path"));
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("SOFTHSM2_CONF", handoff.config().toString());
            environment.put("YCSOPEN_PKCS11_PIN", new String(handoff.userPin()));
            environment.put("PHASE03_MYSQL_HOST", mysql.host());
            environment.put("PHASE03_MYSQL_PORT", Integer.toString(mysql.port()));
            environment.put("PHASE03_MYSQL_USER", mysql.username());
            environment.put("PHASE03_MYSQL_PASSWORD", mysql.password());
            environment.put("PHASE03_SOFTHSM_DESTINATION", destination.toString());

            Phase03ServiceHarness.CommandResult proof = Phase03ServiceHarness.runChecked(
                    List.of(javaExecutable.toString(), "-cp", classpath,
                            Phase03ProtectedPersistenceIntegrationTest.class.getName(), "real-proof"),
                    environment);
            output = proof.stdout().strip();
            assertThat(output).matches("PHASE03_PROTECTED_PERSISTENCE_PASS "
                    + "mysql_sha256=[a-f0-9]{64} softhsm_sha256=[a-f0-9]{64} "
                    + "pkcs11_sha256=[a-f0-9]{64} rows=1 message_indexes=1 "
                    + "blacklist_indexes=2 portability_indexes=2 assertions=[0-9]+");
            assertThat(output.toLowerCase()).doesNotContain(
                    "password", "secret", "pin", "alias", "path", "library=", "token=");
        }
        Phase03ServiceHarness.runChecked(List.of("/usr/bin/env", "ruby",
                repositoryRoot().resolve("scripts/lib/phase-03/service_checks.rb").toString(),
                "assert-clean", "--all"), Map.of());
        assertThat(output).startsWith("PHASE03_PROTECTED_PERSISTENCE_PASS");
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

        DataSource rawDataSource = mysqlDataSource();
        JdbcTemplate rawJdbc = new JdbcTemplate(rawDataSource);
        ConfigurableApplicationContext context = startApplicationQuietly(handoff);
        String successfulMessageId = messageId("BOUNDARY");
        String repositoryFailureMessageId = messageId("ROLLBACK");
        String providerFailureMessageId = messageId("OUTAGE00");
        long blacklistRowId = -1;
        String portabilityLocator = null;
        try {
            seedProductionKeyMetadata(rawJdbc);
            MessageTaskProtectionAdapter writer = context.getBean(MessageTaskProtectionAdapter.class);
            KeyProtectionPort keyProtectionPort = context.getBean(
                    "keyProtectionPort", KeyProtectionPort.class);

            String mobile = generatedMobile();
            String rawMobileSha = sha256(mobile);
            PreparedMessageMobile prepared = writer.prepare(TENANT_ID, successfulMessageId, mobile);
            MessageTask saved = writer.save(message(successfulMessageId), prepared);
            assertThat(saved.getId()).isPositive();
            passed();

            RawMessageRow raw = readRawMessage(rawJdbc, successfulMessageId);
            assertPhysicalMessage(raw, mobile, rawMobileSha);
            assertContextBinding(keyProtectionPort, raw.envelope(), mobile, successfulMessageId);

            PreparedMessageMobile repositoryFailure = writer.prepare(
                    TENANT_ID, repositoryFailureMessageId, generatedMobile());
            rawJdbc.update("UPDATE ycs_crypto_key_references SET key_state='RETIRED' "
                    + "WHERE purpose='MOBILE_BLIND_INDEX' AND key_version=1");
            assertThatThrownBy(() -> writer.save(message(repositoryFailureMessageId), repositoryFailure))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(MessageTaskProtectionAdapter.SANITIZED_FAILURE)
                    .hasNoCause();
            assertCount(rawJdbc, "SELECT COUNT(*) FROM message_tasks WHERE message_id=?",
                    repositoryFailureMessageId, 0L);
            rawJdbc.update("UPDATE ycs_crypto_key_references SET key_state='ACTIVE' "
                    + "WHERE purpose='MOBILE_BLIND_INDEX' AND key_version=1");
            passed(2);

            rawJdbc.update("INSERT INTO ycs_crypto_key_references "
                            + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                            + "VALUES ('MOBILE_BLIND_INDEX',2,'pkcs11',?,'RETIRING')",
                    MOBILE_RETIRING_REFERENCE);

            Pkcs11FailureMapper mapper = new Pkcs11FailureMapper();
            SunPkcs11KeyAdapter dualAdapter;
            String pkcs11Identity;
            try (DualRuntime dual = openDualRuntime(handoff, rawDataSource, mapper)) {
                dualAdapter = dual.adapter();
                pkcs11Identity = dual.session().tokenIdentityHash();
                PreparedMessageMobile lookupPrepared = prepareLookupCapability(
                        dualAdapter, rawDataSource, mobile, messageId("LOOKUP00"));

                blacklistRowId = seedBlacklist(rawJdbc, raw.envelope(), rawMobileSha);
                BlindIndexPort.OrderedIndexes blacklistIndexes = dualAdapter.queryIndexes(
                        mobile, new BlindIndexPort.Context("BLACKLIST_ENTRY", "mobile",
                                BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:" + TENANT_ID));
                seedMetadata(rawJdbc, "BLACKLIST_ENTRY", blacklistRowId, blacklistIndexes);
                BlindIndexLookupService lookup = new BlindIndexLookupService(
                        rawJdbc, context.getBean(BlacklistEntryRepository.class));
                assertThat(lookup.lookupBlacklist(TENANT_ID,
                        lookupPrepared.legacyLookupToken(), BlacklistEntry.Status.ACTIVE).blocked()).isTrue();
                passed();

                String scrubbedBlacklistLocator = randomLocator();
                rawJdbc.update("UPDATE blacklist_entries SET mobile_hash=? WHERE id=?",
                        scrubbedBlacklistLocator, blacklistRowId);
                completeTarget(rawJdbc, "BLACKLIST_ENTRY");
                assertThat(lookup.lookupBlacklist(TENANT_ID,
                        lookupPrepared.legacyLookupToken(), BlacklistEntry.Status.ACTIVE).blocked()).isTrue();
                assertCount(rawJdbc, "SELECT COUNT(*) FROM blacklist_entries WHERE mobile_hash=?",
                        rawMobileSha, 0L);
                passed(2);

                portabilityLocator = rawMobileSha;
                seedPortability(rawJdbc, raw.envelope(), portabilityLocator);
                BlindIndexPort.OrderedIndexes portabilityIndexes = dualAdapter.queryIndexes(
                        mobile, new BlindIndexPort.Context("MOBILE_PORTABILITY", "mobile",
                                BlindIndexPort.Purpose.MOBILE_ROUTING, "global"));
                seedMetadata(rawJdbc, "MOBILE_PORTABILITY", 7301001L, portabilityIndexes);
                assertSchemaOnlyPortabilityParity(rawJdbc, rawMobileSha, portabilityIndexes, true);
                String scrubbedPortabilityLocator = randomLocator();
                rawJdbc.update("UPDATE mobile_portability SET mobile_hash=? WHERE mobile_hash=?",
                        scrubbedPortabilityLocator, portabilityLocator);
                portabilityLocator = scrubbedPortabilityLocator;
                completeTarget(rawJdbc, "MOBILE_PORTABILITY");
                assertSchemaOnlyPortabilityParity(rawJdbc, rawMobileSha, portabilityIndexes, false);

                disableSign(destination, handoff, CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS);
                assertThatThrownBy(() -> dualAdapter.queryIndexes(
                        mobile, new BlindIndexPort.Context("BLACKLIST_ENTRY", "mobile",
                                BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:" + TENANT_ID)))
                        .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                        .hasMessageMatching("PKCS11_OPERATION_FAILED correlation=[a-f0-9]{32} "
                                + "descriptor=[a-f0-9]{64}");
                assertCount(rawJdbc, "SELECT COUNT(*) FROM message_tasks WHERE message_id=?",
                        providerFailureMessageId, 0L);
                passed(2);
            }

            String mysqlIdentity = sha256(rawJdbc.queryForObject(
                    "SELECT CONCAT(@@version, '|', DATABASE())", String.class));
            String softHsmIdentity = sha256("SoftHSM|2.7.0|"
                    + "be14a5820ec457eac5154462ffae51ba5d8a643f6760514d4b4b83a77be91573");
            long messageIndexes = count(rawJdbc, "SELECT COUNT(*) FROM ycs_crypto_blind_indexes "
                    + "WHERE target_type='MESSAGE_TASK' AND legacy_row_id=?", raw.id());
            long blacklistIndexes = count(rawJdbc, "SELECT COUNT(*) FROM ycs_crypto_blind_indexes "
                    + "WHERE target_type='BLACKLIST_ENTRY' AND legacy_row_id=?", blacklistRowId);
            long portabilityIndexes = count(rawJdbc, "SELECT COUNT(*) FROM ycs_crypto_blind_indexes "
                    + "WHERE target_type='MOBILE_PORTABILITY' AND legacy_row_id=7301001");

            context.close();
            assertThatThrownBy(() -> writer.prepare(
                    TENANT_ID, providerFailureMessageId, generatedMobile()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(MessageTaskProtectionAdapter.SANITIZED_FAILURE)
                    .hasNoCause();
            assertCount(rawJdbc, "SELECT COUNT(*) FROM message_tasks WHERE message_id=?",
                    providerFailureMessageId, 0L);
            passed(2);

            System.out.println("PHASE03_PROTECTED_PERSISTENCE_PASS mysql_sha256=" + mysqlIdentity
                    + " softhsm_sha256=" + softHsmIdentity
                    + " pkcs11_sha256=" + pkcs11Identity
                    + " rows=1 message_indexes=" + messageIndexes
                    + " blacklist_indexes=" + blacklistIndexes
                    + " portability_indexes=" + portabilityIndexes
                    + " assertions=" + ASSERTIONS.get());
        } finally {
            if (context.isActive()) {
                context.close();
            }
            cleanupRows(rawJdbc, successfulMessageId, repositoryFailureMessageId,
                    providerFailureMessageId, blacklistRowId, portabilityLocator);
        }
    }

    private static ConfigurableApplicationContext startApplication(
            Phase03ServiceHarness.SoftHsmHandoff handoff) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", mysqlJdbcUrl());
        properties.put("spring.datasource.username", requiredEnvironment("PHASE03_MYSQL_USER"));
        properties.put("spring.datasource.password", requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
        // Flyway owns and validates the real schema. Hibernate validation is intentionally disabled
        // here because the legacy CHAR(64) columns are represented as opaque String projections.
        properties.put("spring.jpa.hibernate.ddl-auto", "none");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("spring.flyway.enabled", "true");
        properties.put("spring.flyway.placeholder-replacement", "false");
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "OFF");
        properties.put("ycsopen.security.crypto-storage.enabled", "true");
        properties.put("ycsopen.security.crypto-storage.adapter", "SUN_PKCS11");
        properties.put("ycsopen.security.crypto-storage.provider-id", "pkcs11");
        properties.put("ycsopen.security.crypto-storage.module-path", handoff.library().toString());
        properties.put("ycsopen.security.crypto-storage.allowed-module-paths", handoff.library().toString());
        properties.put("ycsopen.security.crypto-storage.slot-id", Long.toUnsignedString(handoff.slot()));
        properties.put("ycsopen.security.crypto-storage.token-identity", "phase03-persistence");
        properties.put("ycsopen.security.crypto-storage.credential-source", "ENVIRONMENT");
        properties.put("ycsopen.security.crypto-storage.credential-reference", "YCSOPEN_PKCS11_PIN");
        properties.put("ycsopen.security.crypto-storage.mechanisms", "CKM_AES_GCM,CKM_SHA256_HMAC");
        properties.put("ycsopen.security.crypto-storage.key-attributes",
                "CKA_TOKEN,CKA_PRIVATE,CKA_SENSITIVE,CKA_NOT_EXTRACTABLE");
        properties.put("ycsopen.security.crypto-storage.rotation-required-at", "983040");
        properties.put("ycsopen.security.crypto-storage.hard-ceiling", "1048576");
        properties.put("ycsopen.security.crypto-storage.aliases.field-encryption-kek",
                CryptoStorageStartupVerifier.FIELD_KEK_ALIAS);
        properties.put("ycsopen.security.crypto-storage.aliases.snapshot-recovery",
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_ALIAS);
        properties.put("ycsopen.security.crypto-storage.aliases.mobile-blind-index",
                CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS);
        properties.put("ycsopen.security.crypto-storage.aliases.object-capability-digest",
                CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS);
        properties.put("ycsopen.security.crypto-storage.aliases.registration-upload-digest",
                CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS);
        properties.put("ycsopen.security.crypto-storage.references.snapshot-recovery",
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_REFERENCE);
        String[] arguments = properties.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(ProtectedPersistenceApplication.class)
                .profiles("phase03-integration")
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .run(arguments);
    }

    private static ConfigurableApplicationContext startApplicationQuietly(
            Phase03ServiceHarness.SoftHsmHandoff handoff) {
        PrintStream original = System.out;
        try (PrintStream discarded = new PrintStream(
                OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8)) {
            System.setOut(discarded);
            return startApplication(handoff);
        } finally {
            System.setOut(original);
        }
    }

    private static void seedProductionKeyMetadata(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES ('FIELD_ENCRYPTION_KEK',1,'pkcs11',?,'ACTIVE')",
                FIELD_REFERENCE);
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES ('SNAPSHOT_RECOVERY',1,'pkcs11',?,'ACTIVE')",
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_REFERENCE);
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES ('MOBILE_BLIND_INDEX',1,'pkcs11',?,'ACTIVE')",
                MOBILE_ACTIVE_REFERENCE);
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES ('OBJECT_CAPABILITY_DIGEST',1,'pkcs11','object-digest.v1','ACTIVE')");
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES ('REGISTRATION_UPLOAD_DIGEST',1,'pkcs11','registration-digest.v1','ACTIVE')");
    }

    private static MessageTask message(String messageId) {
        MessageTask task = new MessageTask();
        task.setMessageId(messageId);
        task.setTenantId(TENANT_ID);
        task.setContent("synthetic protected boundary");
        return task;
    }

    private static RawMessageRow readRawMessage(JdbcTemplate jdbc, String messageId) {
        return jdbc.queryForObject("SELECT id, mobile_encrypted, mobile_hash FROM message_tasks "
                        + "WHERE message_id=?",
                (rs, row) -> new RawMessageRow(rs.getLong(1), rs.getBytes(2), rs.getString(3)),
                messageId);
    }

    private static void assertPhysicalMessage(RawMessageRow raw, String mobile, String rawMobileSha) {
        long maximumCapacity = new EnvelopeCodec().maximumCompleteEnvelopeLength(
                "field-reference-1234567890123456", 11, EnvelopeCodec.Target.DATABASE_FIELD);
        assertThat(maximumCapacity).isEqualTo(156);
        assertThat((long) raw.envelope().length).isEqualTo(136L).isLessThanOrEqualTo(maximumCapacity);
        assertThat(Arrays.copyOf(raw.envelope(), 4))
                .containsExactly("YCSE".getBytes(StandardCharsets.US_ASCII));
        assertThat(containsSequence(raw.envelope(), mobile.getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(raw.locator()).matches("[a-f0-9]{64}").isNotEqualTo(rawMobileSha);
        passed(5);
    }

    private static void assertContextBinding(KeyProtectionPort keyProtectionPort,
                                             byte[] envelope,
                                             String mobile,
                                             String messageId) {
        ProtectedFieldCodec codec = new ProtectedFieldCodec(
                new EnvelopeCodec(), keyProtectionPort, new SecureRandom(), FIELD_REFERENCE);
        ProtectionContext correct = fieldContext(TENANT_ID, messageId, "mobile_encrypted");
        byte[] recovered = codec.unprotect(envelope, correct, EnvelopeCodec.Target.DATABASE_FIELD);
        assertThat(recovered).containsExactly(mobile.getBytes(StandardCharsets.US_ASCII));
        Arrays.fill(recovered, (byte) 0);
        List<ProtectionContext> swaps = List.of(
                fieldContext(TENANT_ID, messageId("SWAPPED0"), "mobile_encrypted"),
                fieldContext(TENANT_ID + 1, messageId, "mobile_encrypted"),
                fieldContext(TENANT_ID, messageId, "contact_phone_encrypted"));
        for (ProtectionContext swapped : swaps) {
            assertThatThrownBy(() -> codec.unprotect(
                    envelope, swapped, EnvelopeCodec.Target.DATABASE_FIELD))
                    .isInstanceOf(ProtectionFailure.class)
                    .hasMessage(ProtectionFailure.SANITIZED_MESSAGE)
                    .hasNoCause();
        }
        passed(4);
    }

    private static ProtectionContext fieldContext(long tenantId, String messageId, String field) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", field,
                "tenant:" + tenantId, "message_id=" + messageId);
    }

    private static PreparedMessageMobile prepareLookupCapability(SunPkcs11KeyAdapter adapter,
                                                                 DataSource dataSource,
                                                                 String mobile,
                                                                 String messageId) {
        MessageTaskProtectionAdapter preparer = new MessageTaskProtectionAdapter(
                new ProtectedFieldCodec(new EnvelopeCodec(), adapter, new SecureRandom(), FIELD_REFERENCE),
                adapter, mock(MessageTaskRepository.class),
                new BlindIndexMetadataRepository(new JdbcTemplate(dataSource)),
                new SecureRandom(), new DataSourceTransactionManager(dataSource));
        return preparer.prepare(TENANT_ID, messageId, mobile);
    }

    private static long seedBlacklist(JdbcTemplate jdbc, byte[] envelope, String rawMobileSha) {
        jdbc.update("INSERT INTO blacklist_entries "
                        + "(tenant_id,mobile_encrypted,mobile_hash,list_type,source,status) "
                        + "VALUES (?,?,?,'BLACK','MANUAL','ACTIVE')",
                TENANT_ID, envelope, rawMobileSha);
        return jdbc.queryForObject("SELECT id FROM blacklist_entries "
                + "WHERE tenant_id=? AND mobile_hash=? AND list_type='BLACK'", Long.class,
                TENANT_ID, rawMobileSha);
    }

    private static void seedPortability(JdbcTemplate jdbc, byte[] envelope, String rawMobileSha) {
        jdbc.update("INSERT INTO mobile_portability "
                        + "(mobile_encrypted,mobile_hash,original_operator,current_operator) "
                        + "VALUES (?,?,'MOBILE','UNICOM')",
                envelope, rawMobileSha);
    }

    private static void seedMetadata(JdbcTemplate jdbc,
                                     String target,
                                     long legacyRowId,
                                     BlindIndexPort.OrderedIndexes indexes) {
        byte[] binding = sha256Bytes(target + "|" + legacyRowId);
        try {
            for (VersionedBlindIndex index : indexes.values()) {
                String state = jdbc.queryForObject("SELECT key_state FROM ycs_crypto_key_references "
                                + "WHERE purpose='MOBILE_BLIND_INDEX' AND key_version=?",
                        String.class, index.keyVersion());
                jdbc.update("INSERT INTO ycs_crypto_blind_indexes "
                                + "(target_type,legacy_row_id,field_id,key_purpose,key_version,"
                                + "index_value,index_status,original_row_digest) "
                                + "VALUES (?,?,'mobile','MOBILE_BLIND_INDEX',?,?,?,?)",
                        target, legacyRowId, index.keyVersion(), index.canonicalValue(), state, binding);
            }
        } finally {
            Arrays.fill(binding, (byte) 0);
        }
    }

    private static void assertSchemaOnlyPortabilityParity(JdbcTemplate jdbc,
                                                           String rawMobileSha,
                                                           BlindIndexPort.OrderedIndexes indexes,
                                                           boolean expectLegacy) {
        long legacy = count(jdbc,
                "SELECT COUNT(*) FROM mobile_portability WHERE mobile_hash=?", rawMobileSha);
        List<Object> parameters = new ArrayList<>();
        StringBuilder requested = new StringBuilder();
        for (int position = 0; position < indexes.values().size(); position++) {
            if (position > 0) {
                requested.append(" UNION ALL ");
            }
            requested.append("SELECT CAST(? AS DECIMAL(20,0)) key_version, "
                    + "CAST(? AS CHAR(53)) index_value");
            parameters.add(indexes.values().get(position).keyVersion());
            parameters.add(indexes.values().get(position).canonicalValue());
        }
        long metadata = jdbc.queryForObject(("SELECT COUNT(*) FROM ycs_crypto_blind_indexes bi "
                + "JOIN (" + requested + ") requested "
                + "ON requested.key_version=bi.key_version "
                + "AND requested.index_value=bi.index_value "
                + "WHERE bi.target_type='MOBILE_PORTABILITY' "
                + "AND bi.field_id='mobile'").formatted(), Long.class, parameters.toArray());
        assertThat(legacy).isEqualTo(expectLegacy ? 1L : 0L);
        assertThat(metadata).isEqualTo(2L);
        passed(2);
    }

    private static void completeTarget(JdbcTemplate jdbc, String target) {
        jdbc.update("UPDATE ycs_crypto_migration_targets "
                        + "SET target_state='COMPLETE',legacy_fallback_allowed=FALSE "
                        + "WHERE target_type=?",
                target);
    }

    private static DualRuntime openDualRuntime(Phase03ServiceHarness.SoftHsmHandoff handoff,
                                               DataSource dataSource,
                                               Pkcs11FailureMapper mapper) {
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                handoff.library(), List.of(handoff.library()), handoff.slot(),
                "phase03-persistence-dual", () -> handoff.userPin().clone(),
                List.of(
                        descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                                FIELD_REFERENCE, CryptoStorageStartupVerifier.FIELD_KEK_ALIAS,
                                Pkcs11KeyDescriptor.State.ACTIVE),
                        descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY, 1,
                                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_REFERENCE,
                                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_ALIAS,
                                Pkcs11KeyDescriptor.State.ACTIVE),
                        descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 1,
                                MOBILE_ACTIVE_REFERENCE, CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS,
                                Pkcs11KeyDescriptor.State.ACTIVE),
                        descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 2,
                                MOBILE_RETIRING_REFERENCE, MOBILE_RETIRING_ALIAS,
                                Pkcs11KeyDescriptor.State.RETIRING),
                        descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                                "object-digest.v1", CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS,
                                Pkcs11KeyDescriptor.State.ACTIVE),
                        descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                                "registration-digest.v1",
                                CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS,
                                Pkcs11KeyDescriptor.State.ACTIVE)));
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(mapper).open(properties);
        try {
            SunPkcs11KeyAdapter adapter = new SunPkcs11KeyAdapter(session, properties,
                    new KekWrapUsageRepository(new JdbcTemplate(dataSource),
                            new DataSourceTransactionManager(dataSource), mapper), mapper);
            return new DualRuntime(session, adapter);
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    private static Pkcs11KeyDescriptor descriptor(Pkcs11KeyDescriptor.Purpose purpose,
                                                   long version,
                                                   String reference,
                                                   String alias,
                                                   Pkcs11KeyDescriptor.State state) {
        return new Pkcs11KeyDescriptor(purpose, version, reference, alias, state,
                purpose.isWrappingKey() ? "AES" : "HmacSHA256", 256);
    }

    private static void cleanupRows(JdbcTemplate jdbc,
                                    String successfulMessageId,
                                    String repositoryFailureMessageId,
                                    String providerFailureMessageId,
                                    long blacklistRowId,
                                    String portabilityLocator) {
        jdbc.update("DELETE FROM ycs_crypto_blind_indexes WHERE target_type IN "
                + "('MESSAGE_TASK','BLACKLIST_ENTRY','MOBILE_PORTABILITY')");
        if (blacklistRowId > 0) {
            jdbc.update("DELETE FROM blacklist_entries WHERE id=?", blacklistRowId);
        }
        if (portabilityLocator != null) {
            jdbc.update("DELETE FROM mobile_portability WHERE mobile_hash=?", portabilityLocator);
        }
        jdbc.update("DELETE FROM message_tasks WHERE message_id IN (?,?,?)",
                successfulMessageId, repositoryFailureMessageId, providerFailureMessageId);
        jdbc.update("DELETE FROM ycs_crypto_key_references WHERE purpose IN "
                + "('FIELD_ENCRYPTION_KEK','SNAPSHOT_RECOVERY','MOBILE_BLIND_INDEX',"
                + "'OBJECT_CAPABILITY_DIGEST','REGISTRATION_UPLOAD_DIGEST')");
    }

    private static void assertCount(JdbcTemplate jdbc, String sql, Object parameter, long expected) {
        assertThat(count(jdbc, sql, parameter)).isEqualTo(expected);
    }

    private static long count(JdbcTemplate jdbc, String sql, Object parameter) {
        return jdbc.queryForObject(sql, Long.class, parameter);
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static DataSource mysqlDataSource() {
        return new DriverManagerDataSource(mysqlJdbcUrl(),
                requiredEnvironment("PHASE03_MYSQL_USER"),
                requiredEnvironment("PHASE03_MYSQL_PASSWORD"));
    }

    private static String mysqlJdbcUrl() {
        return "jdbc:mysql://" + requiredEnvironment("PHASE03_MYSQL_HOST") + ":"
                + requiredEnvironment("PHASE03_MYSQL_PORT") + "/phase01"
                + "?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_0900_ai_ci"
                + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("integration environment unavailable");
        }
        return value;
    }

    private static String messageId(String suffix) {
        String normalized = (suffix + "00000000").substring(0, 8).toUpperCase();
        return "MSG_" + Math.abs(RANDOM.nextLong(1, Long.MAX_VALUE)) + "_" + normalized;
    }

    private static String generatedMobile() {
        return "1" + (3 + RANDOM.nextInt(7)) + String.format("%09d", RANDOM.nextInt(1_000_000_000));
    }

    private static String randomLocator() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private static boolean containsSequence(byte[] value, byte[] candidate) {
        for (int offset = 0; offset <= value.length - candidate.length; offset++) {
            boolean matches = true;
            for (int index = 0; index < candidate.length; index++) {
                matches &= value[offset + index] == candidate[index];
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(String value) {
        return HexFormat.of().formatHex(sha256Bytes(value));
    }

    private static byte[] sha256Bytes(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception failure) {
            throw new IllegalStateException("digest unavailable");
        }
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
        Path source = destination.resolve("runtime/plan10-key-provisioner.c");
        Path helper = destination.resolve("runtime/plan10-key-provisioner");
        Files.writeString(source, NATIVE_KEY_PROVISIONER, StandardCharsets.US_ASCII);
        Phase03ServiceHarness.runChecked(List.of("/usr/bin/cc", "-std=c11", "-O2",
                        "-I", header.getParent().toString(), source.toString(),
                        handoff.library().toString(), "-Wl,-rpath," + handoff.library().getParent(),
                        "-o", helper.toString()), Map.of());
        Phase03ServiceHarness.runChecked(List.of(helper.toString(), handoff.pinSource().toString(),
                        Long.toUnsignedString(handoff.slot()), "provision"),
                Map.of("SOFTHSM2_CONF", handoff.config().toString()));
    }

    private static void disableSign(Path destination,
                                    Phase03ServiceHarness.SoftHsmHandoff handoff,
                                    String alias) {
        Path helper = destination.resolve("runtime/plan10-key-provisioner");
        Phase03ServiceHarness.runChecked(List.of(helper.toString(), handoff.pinSource().toString(),
                        Long.toUnsignedString(handoff.slot()), "disable-sign", alias),
                Map.of("SOFTHSM2_CONF", handoff.config().toString()));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = RedisAutoConfiguration.class)
    @EntityScan(basePackageClasses = MessageTask.class)
    @EnableJpaRepositories(basePackageClasses = MessageTaskRepository.class)
    @Import({CryptoStorageConfiguration.class, MessageTaskProtectionAdapter.class,
            BlindIndexMetadataRepository.class})
    @EnableConfigurationProperties
    static class ProtectedPersistenceApplication {
    }

    private record RawMessageRow(long id, byte[] envelope, String locator) {
        RawMessageRow {
            envelope = envelope.clone();
        }

        @Override
        public byte[] envelope() {
            return envelope.clone();
        }
    }

    private record DualRuntime(Pkcs11ProviderFactory.Session session,
                               SunPkcs11KeyAdapter adapter) implements AutoCloseable {
        @Override
        public void close() {
            session.close();
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
            static int disable_sign(CK_SESSION_HANDLE session, const char *label) {
              CK_OBJECT_CLASS klass = CKO_SECRET_KEY;
              CK_ATTRIBUTE find[] = {{CKA_CLASS, &klass, sizeof(klass)},
                                     {(CK_ATTRIBUTE_TYPE)CKA_LABEL, (void *)label, strlen(label)}};
              CK_OBJECT_HANDLE key = 0; CK_ULONG count = 0; CK_BBOOL no = CK_FALSE;
              if (C_FindObjectsInit(session, find, 2) != CKR_OK) return 1;
              CK_RV rv = C_FindObjects(session, &key, 1, &count);
              C_FindObjectsFinal(session);
              if (rv != CKR_OK || count != 1) return 1;
              CK_ATTRIBUTE change = {CKA_SIGN, &no, sizeof(no)};
              return C_SetAttributeValue(session, key, &change, 1) == CKR_OK ? 0 : 1;
            }
            int main(int argc, char **argv) {
              if (argc < 4) return 64;
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
              int failed = 0;
              if (strcmp(argv[3], "provision") == 0 && argc == 4) {
                failed |= generate(session, "ycs.field-encryption-kek.v1", 1);
                failed |= generate(session, "ycs.snapshot-recovery.v1", 1);
                failed |= generate(session, "ycs.mobile-blind-index.v1", 0);
                failed |= generate(session, "ycs.mobile-blind-index.v2", 0);
                failed |= generate(session, "ycs.object-capability-digest.v1", 0);
                failed |= generate(session, "ycs.registration-upload-digest.v1", 0);
              } else if (strcmp(argv[3], "disable-sign") == 0 && argc == 5) {
                failed = disable_sign(session, argv[4]);
              } else failed = 1;
              C_Logout(session); C_CloseSession(session); C_Finalize(NULL_PTR);
              memset(so_pin, 0, sizeof(so_pin)); memset(user_pin, 0, sizeof(user_pin));
              return failed ? 70 : 0;
            }
            """;
}
