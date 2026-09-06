package com.ycsopen.sms.core.verification;

import com.ycsopen.sms.core.common.security.envelope.CipherEnvelope;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.envelope.ProtectionContext;
import com.ycsopen.sms.core.common.security.key.BlindIndexPort;
import com.ycsopen.sms.core.common.security.key.lifecycle.BlindIndexRotationService;
import com.ycsopen.sms.core.common.security.key.lifecycle.EnvelopeReferenceInventory;
import com.ycsopen.sms.core.common.security.key.lifecycle.EnvelopeRewrapService;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyLifecycleService;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyState;
import com.ycsopen.sms.core.common.security.key.lifecycle.JdbcFieldReferencePublicationFence;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.migration.MigrationStateRepository;
import com.ycsopen.sms.core.common.security.migration.ProtectedDataTarget;
import com.ycsopen.sms.core.common.security.object.PrivateObjectStorePort;
import com.ycsopen.sms.core.common.security.object.ProtectedObjectMetadataRepository;
import com.ycsopen.sms.core.common.security.persistence.MessageTaskRowBinding;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Composed real-service proof for the production rotation boundaries.
 *
 * <p>The exhaustive snapshot and object matrices remain owned by their focused integration
 * proofs and are composed once at the phase boundary. The PREPARED row below is an explicit
 * database prerequisite because the lifecycle service intentionally has no key-provisioning API.</p>
 */
@EnabledIfSystemProperty(named = "phase03.integration.enabled", matches = "true")
class Phase03RotationRecoveryIntegrationTest {

    private static final String OLD_REFERENCE = "field-main.v1";
    private static final String NEW_REFERENCE = "field-retiring.v2";
    private static final String SNAPSHOT_REFERENCE = "snapshot-recovery.v1";
    private static final String OLD_ALIAS = "ycs.field-encryption-kek.v1";
    private static final String NEW_ALIAS = "ycs.field-encryption-kek.v2";
    private static final String SNAPSHOT_ALIAS = "ycs.snapshot-recovery.v1";
    private static final String MOBILE_V1_ALIAS = "ycs.mobile-blind-index.v1";
    private static final String MOBILE_V2_ALIAS = "ycs.mobile-blind-index.v2";
    private static final String OBJECT_ALIAS = "ycs.object-capability-digest.v1";
    private static final String REGISTRATION_ALIAS = "ycs.registration-upload-digest.v1";
    private static final String RESERVED_OPERATION = "32000000-0000-0000-0000-000000000001";
    private static final String RESERVED_OBJECT = "pobj_v1_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String RESERVED_SESSION = "32000000-0000-0000-0000-000000000002";
    private static final String RESERVED_DRAFT = "32000000-0000-0000-0000-000000000003";
    private static final EnvelopeCodec ENVELOPES = new EnvelopeCodec();
    private static final ProtectionContext FIELD_CONTEXT = new ProtectionContext(
            ProtectionContext.Purpose.DATABASE_FIELD, "rotation-recovery", "message_tasks",
            "mobile_encrypted", "tenant:21", "message_id=21001");

    @Test
    void provesRealLifecycleReservationRewrapRestartAndRetirement() throws Exception {
        String lifecycle = runRealLifecycleAndReservationProof();
        assertThat(lifecycle).matches("PHASE03_ROTATION_LIFECYCLE_PASS "
                + "pkcs11_sha256=[a-f0-9]{64} activation=1 rewrap=1 restart=1 retire=1 "
                + "compromised_denial=1 blind_indexes=2");
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"real-lifecycle-proof".equals(args[0])) {
            throw new IllegalArgumentException("closed integration invocation required");
        }
        runLifecycleChild();
    }

    private static String runRealLifecycleAndReservationProof() throws Exception {
        try (Phase03ServiceHarness.FixtureSet fixtures = Phase03ServiceHarness.startAll()) {
            Phase03ServiceHarness.ServiceSession mysql = fixtures.mysql();
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

            Phase03ServiceHarness.CommandResult reservations = Phase03ServiceHarness.runChecked(
                    List.of(javaExecutable.toString(), "-cp", classpath,
                            Phase03Pkcs11IntegrationTest.class.getName(), "real-proof"), environment);
            assertThat(reservations.stdout().strip()).matches("PHASE03_PKCS11_PASS "
                    + "source_sha256=[a-f0-9]{64} runtime_sha256=[a-f0-9]{64} "
                    + "mechanism_sha256=[a-f0-9]{64} attribute_sha256=[a-f0-9]{64} "
                    + "counts=983040,983041,1048576,1048576,1 concurrency=16");

            Phase03ServiceHarness.CommandResult lifecycle = Phase03ServiceHarness.runChecked(
                    List.of(javaExecutable.toString(), "-cp", classpath,
                            Phase03RotationRecoveryIntegrationTest.class.getName(),
                            "real-lifecycle-proof"), environment);
            String output = lifecycle.stdout().strip();
            String combined = (reservations.stdout() + reservations.stderr()
                    + lifecycle.stdout() + lifecycle.stderr()).toLowerCase();
            assertThat(combined).doesNotContain("password", "secret", "pin", "library=", "token=");
            return output;
        }
    }

    private static void runLifecycleChild() throws Exception {
        DataSource dataSource = mysqlDataSource();
        migrateQuietly(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate transactions = new TransactionTemplate(manager);
        resetLifecyclePrerequisites(jdbc);

        KeyReferenceRepository keys = new KeyReferenceRepository.Jdbc(jdbc, transactions);
        JdbcEnvelopeStore store = new JdbcEnvelopeStore(jdbc, transactions);
        EnvelopeReferenceInventory.Source databaseEnvelopes = databaseEnvelopeInventory(jdbc);
        List<EnvelopeReferenceInventory.Source> inventorySources = new ArrayList<>();
        inventorySources.add(databaseEnvelopes);
        inventorySources.addAll(EnvelopeReferenceInventory.jdbcMetadataSources(jdbc));
        KeyLifecycleService lifecycle = new KeyLifecycleService(keys,
                new EnvelopeReferenceInventory(inventorySources.stream()
                        .map(EnvelopeReferenceInventory.Source::sourceId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()), inventorySources));
        byte[] plaintext = "phase03-rotation-recovery-canary".getBytes(StandardCharsets.US_ASCII);
        byte[] oldEncoded;
        byte[] messageEnvelope;
        byte[] messageBinding;
        String tokenIdentity;

        try (AdapterRuntime before = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.ACTIVE, Pkcs11KeyDescriptor.State.PREPARED,
                Pkcs11KeyDescriptor.State.ACTIVE, Pkcs11KeyDescriptor.State.PREPARED)) {
            tokenIdentity = before.session().tokenIdentityHash();
            ProtectedFieldCodec oldCodec = new ProtectedFieldCodec(
                    ENVELOPES, before.adapter(), new SecureRandom(), OLD_REFERENCE);
            oldEncoded = oldCodec.protect(
                    plaintext, FIELD_CONTEXT, EnvelopeCodec.Target.DATABASE_FIELD);
            store.insert(oldEncoded);

            String messageId = "MSG_21001_ROTATEV1";
            String messageLocator = MessageTaskRowBinding.issueCurrentLocator(new SecureRandom());
            byte[] mobile = "13800138000".getBytes(StandardCharsets.US_ASCII);
            try {
                messageEnvelope = oldCodec.protect(
                        mobile, messageContext(messageId), EnvelopeCodec.Target.DATABASE_FIELD);
            } finally {
                Arrays.fill(mobile, (byte) 0);
            }
            jdbc.update("INSERT INTO message_tasks "
                            + "(id,message_id,tenant_id,mobile_encrypted,mobile_hash,content) "
                            + "VALUES (21001,?,21,?,?, 'rotation current-row proof')",
                    messageId, messageEnvelope, messageLocator);
            ProtectedObjectMetadataRepository metadata =
                    new ProtectedObjectMetadataRepository(jdbc, manager);
            ProtectedObjectMetadataRepository.CreateOperation reservedObject =
                    new ProtectedObjectMetadataRepository.CreateOperation(
                            RESERVED_OPERATION, RESERVED_OBJECT, RESERVED_SESSION, RESERVED_DRAFT,
                            PrivateObjectStorePort.ObjectPurpose.BUSINESS_LICENSE, 1,
                            Instant.parse("2099-01-01T00:00:00Z"), null);
            byte[] objectEnvelope = oldCodec.protect(
                    "reserved-before-put".getBytes(StandardCharsets.US_ASCII),
                    new ProtectionContext(ProtectionContext.Purpose.PROTECTED_OBJECT,
                            "crypto-storage-bootstrap", "registration-object", "business-license",
                            "tenant:" + RESERVED_DRAFT, RESERVED_OBJECT),
                    EnvelopeCodec.Target.BUSINESS_LICENSE);
            try {
                metadata.beginCreate(reservedObject, objectEnvelope);
            } finally {
                Arrays.fill(objectEnvelope, (byte) 0);
            }
            messageBinding = MessageTaskRowBinding.originalRowDigest(
                    21, 21_001L, messageId, messageLocator, messageEnvelope);
            BlindIndexPort.Context indexContext = new BlindIndexPort.Context(
                    "MESSAGE_TASK", "mobile", BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:21");
            BlindIndexRotationService rotation = new BlindIndexRotationService(
                    before.adapter(), keys, new BlindIndexRotationService.JdbcStore(jdbc, transactions));
            List<BlindIndexRotationService.MetadataRow> indexes = rotation.backfill(
                    new BlindIndexRotationService.Row("MESSAGE_TASK", 21_001L, "mobile",
                            messageBinding, "13800138000", indexContext));
            assertThat(indexes).extracting(BlindIndexRotationService.MetadataRow::status)
                    .containsExactly(KeyState.ACTIVE);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ycs_crypto_blind_indexes "
                    + "WHERE target_type='MESSAGE_TASK' AND legacy_row_id=21001 "
                    + "AND field_id='mobile'", Long.class)).isEqualTo(1L);
        }

        assertCurrentMessageMigrationBinding(jdbc, transactions);
        KeyLifecycleService.Activation mobileActivation = lifecycle.activate(
                KeyReferenceRepository.Purpose.MOBILE_BLIND_INDEX, 2);
        assertThat(mobileActivation.previous().state()).isEqualTo(KeyState.RETIRING);
        assertThat(mobileActivation.active().state()).isEqualTo(KeyState.ACTIVE);
        assertThat(jdbc.queryForList("SELECT CONCAT(key_version, ':', index_status) "
                        + "FROM ycs_crypto_blind_indexes WHERE target_type='MESSAGE_TASK' "
                        + "AND legacy_row_id=21001 ORDER BY key_version", String.class))
                .containsExactly("1:RETIRING");

        try (AdapterRuntime rotatedMobile = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.ACTIVE, Pkcs11KeyDescriptor.State.PREPARED,
                Pkcs11KeyDescriptor.State.RETIRING, Pkcs11KeyDescriptor.State.ACTIVE)) {
            BlindIndexRotationService rotation = new BlindIndexRotationService(
                    rotatedMobile.adapter(), keys,
                    new BlindIndexRotationService.JdbcStore(jdbc, transactions));
            BlindIndexPort.Context indexContext = new BlindIndexPort.Context(
                    "MESSAGE_TASK", "mobile", BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:21");
            BlindIndexRotationService.Row current = new BlindIndexRotationService.Row(
                    "MESSAGE_TASK", 21_001L, "mobile", messageBinding,
                    "13800138000", indexContext);
            assertThat(rotation.backfill(current))
                    .extracting(BlindIndexRotationService.MetadataRow::keyVersion,
                            BlindIndexRotationService.MetadataRow::status)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(1L, KeyState.RETIRING),
                            org.assertj.core.groups.Tuple.tuple(2L, KeyState.ACTIVE));
            assertThat(rotation.backfill(current)).hasSize(2);
        }
        assertCurrentMessageMigrationBinding(jdbc, transactions);

        KeyLifecycleService.Activation activation = lifecycle.activate(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 2);
        assertThat(activation.previous().state()).isEqualTo(KeyState.DECRYPT_ONLY);
        assertThat(activation.active().state()).isEqualTo(KeyState.ACTIVE);

        CipherEnvelope beforeRewrap = ENVELOPES.decode(
                oldEncoded, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] newWrite;
        try (AdapterRuntime after = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.DECRYPT_ONLY, Pkcs11KeyDescriptor.State.ACTIVE,
                Pkcs11KeyDescriptor.State.RETIRING, Pkcs11KeyDescriptor.State.ACTIVE)) {
            ProtectedFieldCodec codec = new ProtectedFieldCodec(
                    ENVELOPES, after.adapter(), new SecureRandom(), NEW_REFERENCE);
            assertThat(codec.unprotect(oldEncoded, FIELD_CONTEXT,
                    EnvelopeCodec.Target.DATABASE_FIELD)).containsExactly(plaintext);
            newWrite = codec.protect("new-write-after-activation".getBytes(StandardCharsets.US_ASCII),
                    new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                            "rotation-recovery", "message_tasks", "mobile_encrypted",
                            "tenant:21", "message_id=21002"),
                    EnvelopeCodec.Target.DATABASE_FIELD);
            assertThat(ENVELOPES.decode(newWrite,
                    EnvelopeCodec.Target.DATABASE_FIELD).keyReference()).isEqualTo(NEW_REFERENCE);

            assertThatThrownBy(() -> lifecycle.retire(
                    KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);

            EnvelopeRewrapService.BatchResult rewrapped = new EnvelopeRewrapService(
                    keys, ENVELOPES, after.adapter(), store,
                    new JdbcFieldReferencePublicationFence(jdbc)).rewrap(OLD_REFERENCE, 8);
            assertThat(rewrapped.applied()).isEqualTo(2);
            assertThat(rewrapped.exhausted()).isTrue();
            CipherEnvelope rewritten = ENVELOPES.decode(
                    store.envelope(), EnvelopeCodec.Target.DATABASE_FIELD);
            assertThat(rewritten.keyReference()).isEqualTo(NEW_REFERENCE);
            assertThat(rewritten.dataNonce()).containsExactly(beforeRewrap.dataNonce());
            assertThat(rewritten.ciphertext()).containsExactly(beforeRewrap.ciphertext());
        }

        try (AdapterRuntime restarted = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.DECRYPT_ONLY, Pkcs11KeyDescriptor.State.ACTIVE,
                Pkcs11KeyDescriptor.State.RETIRING, Pkcs11KeyDescriptor.State.ACTIVE)) {
            ProtectedFieldCodec codec = new ProtectedFieldCodec(
                    ENVELOPES, restarted.adapter(), new SecureRandom(), NEW_REFERENCE);
            assertThat(codec.unprotect(store.envelope(), FIELD_CONTEXT,
                    EnvelopeCodec.Target.DATABASE_FIELD)).containsExactly(plaintext);
            assertThat(ENVELOPES.decode(newWrite,
                    EnvelopeCodec.Target.DATABASE_FIELD).keyReference()).isEqualTo(NEW_REFERENCE);
            byte[] rewrittenMessage = jdbc.queryForObject(
                    "SELECT mobile_encrypted FROM message_tasks WHERE id=21001", byte[].class);
            byte[] expectedMobile = "13800138000".getBytes(StandardCharsets.US_ASCII);
            try {
                assertThat(ENVELOPES.decode(rewrittenMessage,
                        EnvelopeCodec.Target.DATABASE_FIELD).keyReference()).isEqualTo(NEW_REFERENCE);
                assertThat(codec.unprotect(rewrittenMessage, messageContext("MSG_21001_ROTATEV1"),
                        EnvelopeCodec.Target.DATABASE_FIELD)).containsExactly(expectedMobile);
            } finally {
                Arrays.fill(rewrittenMessage, (byte) 0);
                Arrays.fill(expectedMobile, (byte) 0);
            }
        }
        assertCurrentMessageMigrationBinding(jdbc, transactions);

        EnvelopeReferenceInventory.Source restartedReservations = EnvelopeReferenceInventory
                .jdbcMetadataSources(jdbc).stream()
                .filter(source -> "OBJECT_FIELD_RESERVATIONS".equals(source.sourceId()))
                .findFirst().orElseThrow();
        EnvelopeReferenceInventory restartedInventory = new EnvelopeReferenceInventory(
                Set.of(restartedReservations.sourceId()), List.of(restartedReservations));
        assertThat(restartedInventory.snapshot().count(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1)).isOne();
        assertThatThrownBy(() -> lifecycle.retire(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(KeyLifecycleService.SANITIZED_FAILURE);
        new ProtectedObjectMetadataRepository(jdbc, manager)
                .failCreate(RESERVED_OPERATION, true);
        assertThat(restartedInventory.snapshot().count(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1)).isZero();

        KeyLifecycleService.RetirementProof retirement = lifecycle.retire(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 1);
        assertThat(retirement.liveReferences()).isZero();
        assertThat(keys.findByPurpose(KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK))
                .filteredOn(key -> key.keyVersion() == 1)
                .extracting(KeyReferenceRepository.KeyReference::state)
                .containsExactly(KeyState.RETIRED);

        // COMPROMISED is an externally supplied deployment state, just like PREPARED. The
        // production adapter must reject the old envelope without an application-side mutation API.
        try (AdapterRuntime compromised = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.COMPROMISED, Pkcs11KeyDescriptor.State.ACTIVE,
                Pkcs11KeyDescriptor.State.RETIRING, Pkcs11KeyDescriptor.State.ACTIVE)) {
            ProtectedFieldCodec denied = new ProtectedFieldCodec(
                    ENVELOPES, compromised.adapter(), new SecureRandom(), OLD_REFERENCE);
            assertThatThrownBy(() -> denied.unprotect(oldEncoded, FIELD_CONTEXT,
                    EnvelopeCodec.Target.DATABASE_FIELD)).isInstanceOf(RuntimeException.class);
        }

        Arrays.fill(plaintext, (byte) 0);
        Arrays.fill(oldEncoded, (byte) 0);
        Arrays.fill(newWrite, (byte) 0);
        Arrays.fill(messageEnvelope, (byte) 0);
        Arrays.fill(messageBinding, (byte) 0);
        System.out.println("PHASE03_ROTATION_LIFECYCLE_PASS pkcs11_sha256=" + tokenIdentity
                + " activation=1 rewrap=1 restart=1 retire=1 compromised_denial=1 blind_indexes=2");
    }

    private static AdapterRuntime openAdapter(
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            Pkcs11KeyDescriptor.State oldState,
            Pkcs11KeyDescriptor.State newState,
            Pkcs11KeyDescriptor.State mobileV1State,
            Pkcs11KeyDescriptor.State mobileV2State) {
        Phase03ServiceHarness.SoftHsmHandoff handoff = Phase03ServiceHarness.readHandoff(
                Path.of(requiredEnvironment("PHASE03_SOFTHSM_DESTINATION")));
        List<Pkcs11KeyDescriptor> descriptors = List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK,
                        1, OLD_REFERENCE, OLD_ALIAS, oldState),
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK,
                        2, NEW_REFERENCE, NEW_ALIAS, newState),
                descriptor(Pkcs11KeyDescriptor.Purpose.SNAPSHOT_RECOVERY,
                        1, SNAPSHOT_REFERENCE, SNAPSHOT_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX,
                        1, "mobile-index.v1", MOBILE_V1_ALIAS, mobileV1State),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX,
                        2, "mobile-index.v2", MOBILE_V2_ALIAS, mobileV2State),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST,
                        1, "object-digest.v1", OBJECT_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST,
                        1, "registration-digest.v1", REGISTRATION_ALIAS,
                        Pkcs11KeyDescriptor.State.ACTIVE));
        Pkcs11CryptoStorageProperties properties = new Pkcs11CryptoStorageProperties(
                handoff.library(), List.of(handoff.library()), handoff.slot(),
                "phase03-rotation-recovery", () -> handoff.userPin().clone(), descriptors);
        Pkcs11FailureMapper mapper = new Pkcs11FailureMapper();
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(mapper).open(properties);
        try {
            return new AdapterRuntime(session, new SunPkcs11KeyAdapter(session, properties,
                    new KekWrapUsageRepository(new JdbcTemplate(dataSource), transactions, mapper),
                    mapper));
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

    private static ProtectionContext messageContext(String messageId) {
        return new ProtectionContext(ProtectionContext.Purpose.DATABASE_FIELD,
                "crypto-storage-bootstrap", "message_tasks", "mobile_encrypted",
                "tenant:21", "message_id=" + messageId);
    }

    private static void assertCurrentMessageMigrationBinding(
            JdbcTemplate jdbc,
            TransactionTemplate transactions) {
        ProtectedDataTarget target = new ProtectedDataTarget(
                "message_tasks.mobile_hash", "message_tasks", "mobile_hash",
                ProtectedDataTarget.Kind.LEGACY_DIGEST,
                ProtectedDataTarget.MigrationState.CURRENT_EXECUTABLE,
                ProtectedDataTarget.BlindIndexRule.REQUIRED_VERSIONED_HMAC,
                ProtectedDataTarget.LegacyRule.LOWERCASE_SHA256_HEX,
                ProtectedDataTarget.NullPolicy.FORBIDDEN,
                64, 64, 64, "tenant_id", "id");
        MigrationStateRepository repository = new MigrationStateRepository.Jdbc(jdbc, transactions);
        boolean valid = repository.transaction(transaction -> {
            List<MigrationStateRepository.LegacyRow> rows = transaction.readBatch(target, 21_000, 2);
            assertThat(rows).hasSize(1);
            MigrationStateRepository.LegacyRow row = rows.getFirst();
            assertThat(row.bindingRowId()).isEqualTo(21_001L);
            assertThat(row.storedValueKind())
                    .isEqualTo(MigrationStateRepository.StoredValueKind.CURRENT_MESSAGE_LOCATOR);
            return transaction.currentMessageBindingMatches(row, "mobile")
                    && transaction.integrityAndBindingComplete(target, "MESSAGE_TASK");
        });
        assertThat(valid).isTrue();
    }

    private static void resetLifecyclePrerequisites(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM ycs_crypto_blind_indexes");
        jdbc.update("DELETE FROM message_tasks WHERE id = 21001");
        jdbc.update("DELETE FROM ycs_crypto_key_references");
        insertKey(jdbc, "FIELD_ENCRYPTION_KEK", 1, OLD_REFERENCE, "ACTIVE");
        // PREPARED is a deliberately seeded prerequisite; no provisioning behavior is claimed.
        insertKey(jdbc, "FIELD_ENCRYPTION_KEK", 2, NEW_REFERENCE, "PREPARED");
        insertKey(jdbc, "SNAPSHOT_RECOVERY", 1, SNAPSHOT_REFERENCE, "ACTIVE");
        insertKey(jdbc, "MOBILE_BLIND_INDEX", 1, "mobile-index.v1", "ACTIVE");
        insertKey(jdbc, "MOBILE_BLIND_INDEX", 2, "mobile-index.v2", "PREPARED");
        insertKey(jdbc, "OBJECT_CAPABILITY_DIGEST", 1, "object-digest.v1", "ACTIVE");
        insertKey(jdbc, "REGISTRATION_UPLOAD_DIGEST", 1, "registration-digest.v1", "ACTIVE");
        jdbc.update("INSERT INTO ycs_crypto_registration_sessions "
                        + "(registration_session_id,tenant_draft_id,session_state,"
                        + "upload_digest_key_version,upload_credential_digest,expires_at) "
                        + "VALUES (?,?,'OPEN',1,?,TIMESTAMP('2099-01-01 00:00:00'))",
                RESERVED_SESSION, RESERVED_DRAFT, sha256("reserved-upload-token"));
        jdbc.update("INSERT INTO ycs_crypto_registration_upload_attempts "
                        + "(registration_session_id,object_purpose,admitted_attempt_count) "
                        + "VALUES (?,'BUSINESS_LICENSE',1)", RESERVED_SESSION);
        jdbc.execute("DROP TABLE IF EXISTS phase03_rotation_rewrap_checkpoint");
        jdbc.execute("DROP TABLE IF EXISTS phase03_rotation_envelopes");
        jdbc.execute("CREATE TABLE phase03_rotation_envelopes ("
                + "sequence_id BIGINT UNSIGNED NOT NULL PRIMARY KEY, "
                + "key_reference VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, "
                + "envelope LONGBLOB NOT NULL, envelope_digest BINARY(32) NOT NULL)");
        jdbc.execute("CREATE TABLE phase03_rotation_rewrap_checkpoint ("
                + "singleton_id TINYINT UNSIGNED NOT NULL PRIMARY KEY, "
                + "checkpoint BIGINT UNSIGNED NOT NULL)");
        jdbc.update("INSERT INTO phase03_rotation_rewrap_checkpoint VALUES (1, 0)");
    }

    private static void insertKey(JdbcTemplate jdbc, String purpose, long version,
                                  String reference, String state) {
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state) "
                        + "VALUES (?,?,'pkcs11',?,?)", purpose, version, reference, state);
    }

    private static EnvelopeReferenceInventory.Source databaseEnvelopeInventory(JdbcTemplate jdbc) {
        return new EnvelopeReferenceInventory.Source() {
            @Override
            public String sourceId() {
                return "ROTATION_DATABASE_ENVELOPES";
            }

            @Override
            public List<EnvelopeReferenceInventory.Reference> liveReferences() {
                List<EnvelopeReferenceInventory.Reference> references = new java.util.ArrayList<>(
                        jdbc.query("SELECT sequence_id, key_reference FROM "
                                + "phase03_rotation_envelopes ORDER BY sequence_id",
                        (rs, row) -> new EnvelopeReferenceInventory.Reference(sourceId(),
                                EnvelopeReferenceInventory.Kind.DATABASE_ENVELOPE,
                                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK,
                                version(rs.getString("key_reference")),
                                sha256("rotation-envelope:" + rs.getLong("sequence_id")))));
                references.addAll(jdbc.query(
                        "SELECT id, mobile_encrypted FROM message_tasks WHERE id=21001",
                        (rs, row) -> {
                            byte[] envelope = rs.getBytes("mobile_encrypted");
                            try {
                                String keyReference = ENVELOPES.decode(envelope,
                                        EnvelopeCodec.Target.DATABASE_FIELD).keyReference();
                                return new EnvelopeReferenceInventory.Reference(sourceId(),
                                        EnvelopeReferenceInventory.Kind.DATABASE_ENVELOPE,
                                        KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK,
                                        version(keyReference),
                                        sha256("message-envelope:" + rs.getLong("id")));
                            } finally {
                                Arrays.fill(envelope, (byte) 0);
                            }
                        }));
                return List.copyOf(references);
            }
        };
    }

    private static long version(String keyReference) {
        if (OLD_REFERENCE.equals(keyReference)) {
            return 1;
        }
        if (NEW_REFERENCE.equals(keyReference)) {
            return 2;
        }
        throw new IllegalStateException("unexpected envelope key reference");
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

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception failure) {
            throw new IllegalStateException("digest unavailable", failure);
        }
    }

    private record AdapterRuntime(Pkcs11ProviderFactory.Session session,
                                  SunPkcs11KeyAdapter adapter) implements AutoCloseable {
        @Override
        public void close() {
            session.close();
        }
    }

    /** Real-MySQL atomic CAS/checkpoint seam; production envelope cryptography remains untouched. */
    private static final class JdbcEnvelopeStore implements EnvelopeRewrapService.Store {
        private final JdbcTemplate jdbc;
        private final TransactionTemplate transactions;

        private JdbcEnvelopeStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
            this.jdbc = jdbc;
            this.transactions = transactions;
        }

        void insert(byte[] envelope) {
            jdbc.update("INSERT INTO phase03_rotation_envelopes "
                            + "(sequence_id,key_reference,envelope,envelope_digest) VALUES (1,?,?,?)",
                    OLD_REFERENCE, envelope, digest(envelope));
        }

        byte[] envelope() {
            return jdbc.queryForObject("SELECT envelope FROM phase03_rotation_envelopes "
                    + "WHERE sequence_id=1", byte[].class);
        }

        @Override
        public long checkpoint(String oldKeyReference, String newKeyReference) {
            return jdbc.queryForObject("SELECT checkpoint FROM "
                    + "phase03_rotation_rewrap_checkpoint WHERE singleton_id=1", Long.class);
        }

        @Override
        public Optional<EnvelopeRewrapService.Candidate> next(
                String oldKeyReference, long afterSequence) {
            List<EnvelopeRewrapService.Candidate> values = jdbc.query(
                    "SELECT sequence_id,envelope,envelope_digest FROM phase03_rotation_envelopes "
                            + "WHERE key_reference=? AND sequence_id>? ORDER BY sequence_id LIMIT 1",
                    (rs, row) -> new EnvelopeRewrapService.Candidate(
                            rs.getLong("sequence_id"), sha256("rotation-envelope:"
                            + rs.getLong("sequence_id")), rs.getBytes("envelope_digest"),
                            rs.getBytes("envelope"), FIELD_CONTEXT,
                            EnvelopeCodec.Target.DATABASE_FIELD), oldKeyReference, afterSequence);
            if (!values.isEmpty()) {
                return Optional.of(values.getFirst());
            }
            if (afterSequence >= 2) {
                return Optional.empty();
            }
            List<byte[]> messages = jdbc.query(
                    "SELECT mobile_encrypted FROM message_tasks WHERE id=21001",
                    (rs, row) -> rs.getBytes(1));
            if (messages.size() != 1) {
                return Optional.empty();
            }
            byte[] envelope = messages.getFirst();
            try {
                if (!oldKeyReference.equals(ENVELOPES.decode(envelope,
                        EnvelopeCodec.Target.DATABASE_FIELD).keyReference())) {
                    return Optional.empty();
                }
                return Optional.of(new EnvelopeRewrapService.Candidate(
                        2, sha256("message-envelope:21001"), digest(envelope), envelope,
                        messageContext("MSG_21001_ROTATEV1"),
                        EnvelopeCodec.Target.DATABASE_FIELD));
            } finally {
                Arrays.fill(envelope, (byte) 0);
            }
        }

        @Override
        public EnvelopeRewrapService.CommitOutcome replaceByOriginalDigestAndCheckpoint(
                String oldKeyReference,
                String newKeyReference,
                EnvelopeRewrapService.Candidate candidate,
                byte[] rewrittenEnvelope,
                byte[] rewrittenEnvelopeDigest,
                Runnable publicationFence) {
            EnvelopeRewrapService.CommitOutcome result = transactions.execute(status -> {
                // All producer transactions take the FIELD purpose lock before business rows.
                publicationFence.run();
                long checkpoint = jdbc.queryForObject("SELECT checkpoint FROM "
                        + "phase03_rotation_rewrap_checkpoint WHERE singleton_id=1 FOR UPDATE",
                        Long.class);
                if (checkpoint != candidate.sequence() - 1) {
                    status.setRollbackOnly();
                    return EnvelopeRewrapService.CommitOutcome.DRIFT;
                }
                int replaced = candidate.sequence() == 1
                        ? replaceFixtureEnvelope(oldKeyReference, newKeyReference, candidate,
                        rewrittenEnvelope, rewrittenEnvelopeDigest)
                        : replaceMessageEnvelope(candidate, rewrittenEnvelope);
                int advanced = jdbc.update("UPDATE phase03_rotation_rewrap_checkpoint "
                                + "SET checkpoint=? WHERE singleton_id=1 AND checkpoint=?",
                        candidate.sequence(), candidate.sequence() - 1);
                if (replaced != 1 || advanced != 1) {
                    status.setRollbackOnly();
                    return EnvelopeRewrapService.CommitOutcome.DRIFT;
                }
                return EnvelopeRewrapService.CommitOutcome.APPLIED;
            });
            return result == null ? EnvelopeRewrapService.CommitOutcome.DRIFT : result;
        }

        private int replaceFixtureEnvelope(
                String oldKeyReference,
                String newKeyReference,
                EnvelopeRewrapService.Candidate candidate,
                byte[] rewrittenEnvelope,
                byte[] rewrittenEnvelopeDigest) {
            return jdbc.update("UPDATE phase03_rotation_envelopes SET "
                            + "key_reference=?,envelope=?,envelope_digest=? "
                            + "WHERE sequence_id=? AND key_reference=? AND envelope_digest=?",
                    newKeyReference, rewrittenEnvelope, rewrittenEnvelopeDigest,
                    candidate.sequence(), oldKeyReference, candidate.originalEnvelopeDigest());
        }

        private int replaceMessageEnvelope(
                EnvelopeRewrapService.Candidate candidate,
                byte[] rewrittenEnvelope) {
            if (candidate.sequence() != 2) {
                return 0;
            }
            List<MessageEnvelopeRow> rows = jdbc.query(
                    "SELECT tenant_id,message_id,mobile_hash,mobile_encrypted "
                            + "FROM message_tasks WHERE id=21001 FOR UPDATE",
                    (rs, row) -> new MessageEnvelopeRow(rs.getLong(1), rs.getString(2),
                            rs.getString(3), rs.getBytes(4)));
            if (rows.size() != 1) {
                return 0;
            }
            MessageEnvelopeRow row = rows.getFirst();
            byte[] oldEnvelope = row.envelope();
            byte[] expectedDigest = candidate.originalEnvelopeDigest();
            byte[] oldBinding = null;
            byte[] newBinding = null;
            try {
                if (!MessageDigest.isEqual(digest(oldEnvelope), expectedDigest)) {
                    return 0;
                }
                oldBinding = MessageTaskRowBinding.originalRowDigest(
                        row.tenantId(), 21_001L, row.messageId(), row.locator(), oldEnvelope);
                newBinding = MessageTaskRowBinding.originalRowDigest(
                        row.tenantId(), 21_001L, row.messageId(), row.locator(), rewrittenEnvelope);
                int indexes = jdbc.update("UPDATE ycs_crypto_blind_indexes "
                                + "SET original_row_digest=? WHERE target_type='MESSAGE_TASK' "
                                + "AND legacy_row_id=21001 AND field_id='mobile' "
                                + "AND original_row_digest=?",
                        newBinding, oldBinding);
                int message = jdbc.update("UPDATE message_tasks SET mobile_encrypted=? "
                                + "WHERE id=21001 AND mobile_encrypted=?",
                        rewrittenEnvelope, oldEnvelope);
                return indexes == 2 && message == 1 ? 1 : 0;
            } finally {
                Arrays.fill(oldEnvelope, (byte) 0);
                Arrays.fill(expectedDigest, (byte) 0);
                if (oldBinding != null) {
                    Arrays.fill(oldBinding, (byte) 0);
                }
                if (newBinding != null) {
                    Arrays.fill(newBinding, (byte) 0);
                }
            }
        }

        private record MessageEnvelopeRow(
                long tenantId,
                String messageId,
                String locator,
                byte[] envelope) {

            private MessageEnvelopeRow {
                envelope = envelope.clone();
            }

            @Override
            public byte[] envelope() {
                return envelope.clone();
            }
        }

        private static byte[] digest(byte[] value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value);
            } catch (Exception failure) {
                throw new IllegalStateException("digest unavailable", failure);
            }
        }
    }
}
