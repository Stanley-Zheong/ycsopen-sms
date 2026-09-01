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
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
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
    private static final String MOBILE_ACTIVE_ALIAS = "ycs.mobile-blind-index.v1";
    private static final String MOBILE_RETIRING_ALIAS = "ycs.mobile-blind-index.v2";
    private static final String OBJECT_ALIAS = "ycs.object-capability-digest.v1";
    private static final String REGISTRATION_ALIAS = "ycs.registration-upload-digest.v1";
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
        KeyLifecycleService lifecycle = new KeyLifecycleService(keys,
                new EnvelopeReferenceInventory(Set.of(databaseEnvelopes.sourceId()),
                        List.of(databaseEnvelopes)));
        byte[] plaintext = "phase03-rotation-recovery-canary".getBytes(StandardCharsets.US_ASCII);
        byte[] oldEncoded;
        String tokenIdentity;

        try (AdapterRuntime before = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.ACTIVE, Pkcs11KeyDescriptor.State.PREPARED)) {
            tokenIdentity = before.session().tokenIdentityHash();
            ProtectedFieldCodec oldCodec = new ProtectedFieldCodec(
                    ENVELOPES, before.adapter(), new SecureRandom(), OLD_REFERENCE);
            oldEncoded = oldCodec.protect(
                    plaintext, FIELD_CONTEXT, EnvelopeCodec.Target.DATABASE_FIELD);
            store.insert(oldEncoded);

            BlindIndexPort.Context indexContext = new BlindIndexPort.Context(
                    "MESSAGE_TASK", "mobile", BlindIndexPort.Purpose.MOBILE_ROUTING, "tenant:21");
            BlindIndexRotationService rotation = new BlindIndexRotationService(
                    before.adapter(), keys, new BlindIndexRotationService.JdbcStore(jdbc, transactions));
            List<BlindIndexRotationService.MetadataRow> indexes = rotation.backfill(
                    new BlindIndexRotationService.Row("MESSAGE_TASK", 21_001L, "mobile",
                            sha256("message-task-21001"), "13800138000", indexContext));
            assertThat(indexes).extracting(BlindIndexRotationService.MetadataRow::status)
                    .containsExactly(KeyState.ACTIVE, KeyState.RETIRING);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ycs_crypto_blind_indexes "
                    + "WHERE target_type='MESSAGE_TASK' AND legacy_row_id=21001 "
                    + "AND field_id='mobile'", Long.class)).isEqualTo(2L);
        }

        KeyLifecycleService.Activation activation = lifecycle.activate(
                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK, 2);
        assertThat(activation.previous().state()).isEqualTo(KeyState.DECRYPT_ONLY);
        assertThat(activation.active().state()).isEqualTo(KeyState.ACTIVE);

        CipherEnvelope beforeRewrap = ENVELOPES.decode(
                oldEncoded, EnvelopeCodec.Target.DATABASE_FIELD);
        byte[] newWrite;
        try (AdapterRuntime after = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.DECRYPT_ONLY, Pkcs11KeyDescriptor.State.ACTIVE)) {
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
                    keys, ENVELOPES, after.adapter(), store).rewrap(OLD_REFERENCE, 8);
            assertThat(rewrapped.applied()).isOne();
            assertThat(rewrapped.exhausted()).isTrue();
            CipherEnvelope rewritten = ENVELOPES.decode(
                    store.envelope(), EnvelopeCodec.Target.DATABASE_FIELD);
            assertThat(rewritten.keyReference()).isEqualTo(NEW_REFERENCE);
            assertThat(rewritten.dataNonce()).containsExactly(beforeRewrap.dataNonce());
            assertThat(rewritten.ciphertext()).containsExactly(beforeRewrap.ciphertext());
        }

        try (AdapterRuntime restarted = openAdapter(dataSource, manager,
                Pkcs11KeyDescriptor.State.DECRYPT_ONLY, Pkcs11KeyDescriptor.State.ACTIVE)) {
            ProtectedFieldCodec codec = new ProtectedFieldCodec(
                    ENVELOPES, restarted.adapter(), new SecureRandom(), NEW_REFERENCE);
            assertThat(codec.unprotect(store.envelope(), FIELD_CONTEXT,
                    EnvelopeCodec.Target.DATABASE_FIELD)).containsExactly(plaintext);
            assertThat(ENVELOPES.decode(newWrite,
                    EnvelopeCodec.Target.DATABASE_FIELD).keyReference()).isEqualTo(NEW_REFERENCE);
        }

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
                Pkcs11KeyDescriptor.State.COMPROMISED, Pkcs11KeyDescriptor.State.ACTIVE)) {
            ProtectedFieldCodec denied = new ProtectedFieldCodec(
                    ENVELOPES, compromised.adapter(), new SecureRandom(), OLD_REFERENCE);
            assertThatThrownBy(() -> denied.unprotect(oldEncoded, FIELD_CONTEXT,
                    EnvelopeCodec.Target.DATABASE_FIELD)).isInstanceOf(RuntimeException.class);
        }

        Arrays.fill(plaintext, (byte) 0);
        Arrays.fill(oldEncoded, (byte) 0);
        Arrays.fill(newWrite, (byte) 0);
        System.out.println("PHASE03_ROTATION_LIFECYCLE_PASS pkcs11_sha256=" + tokenIdentity
                + " activation=1 rewrap=1 restart=1 retire=1 compromised_denial=1 blind_indexes=2");
    }

    private static AdapterRuntime openAdapter(
            DataSource dataSource,
            DataSourceTransactionManager transactions,
            Pkcs11KeyDescriptor.State oldState,
            Pkcs11KeyDescriptor.State newState) {
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
                        1, "mobile-index.v1", MOBILE_ACTIVE_ALIAS, Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX,
                        2, "mobile-index.v2", MOBILE_RETIRING_ALIAS, Pkcs11KeyDescriptor.State.RETIRING),
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

    private static void resetLifecyclePrerequisites(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM ycs_crypto_blind_indexes");
        jdbc.update("DELETE FROM ycs_crypto_key_references");
        insertKey(jdbc, "FIELD_ENCRYPTION_KEK", 1, OLD_REFERENCE, "ACTIVE");
        // PREPARED is a deliberately seeded prerequisite; no provisioning behavior is claimed.
        insertKey(jdbc, "FIELD_ENCRYPTION_KEK", 2, NEW_REFERENCE, "PREPARED");
        insertKey(jdbc, "SNAPSHOT_RECOVERY", 1, SNAPSHOT_REFERENCE, "ACTIVE");
        insertKey(jdbc, "MOBILE_BLIND_INDEX", 1, "mobile-index.v1", "ACTIVE");
        insertKey(jdbc, "MOBILE_BLIND_INDEX", 2, "mobile-index.v2", "RETIRING");
        insertKey(jdbc, "OBJECT_CAPABILITY_DIGEST", 1, "object-digest.v1", "ACTIVE");
        insertKey(jdbc, "REGISTRATION_UPLOAD_DIGEST", 1, "registration-digest.v1", "ACTIVE");
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
                return jdbc.query("SELECT sequence_id, key_reference FROM "
                                + "phase03_rotation_envelopes ORDER BY sequence_id",
                        (rs, row) -> new EnvelopeReferenceInventory.Reference(sourceId(),
                                EnvelopeReferenceInventory.Kind.DATABASE_ENVELOPE,
                                KeyReferenceRepository.Purpose.FIELD_ENCRYPTION_KEK,
                                version(rs.getString("key_reference")),
                                sha256("rotation-envelope:" + rs.getLong("sequence_id"))));
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
            return values.stream().findFirst();
        }

        @Override
        public EnvelopeRewrapService.CommitOutcome replaceByOriginalDigestAndCheckpoint(
                String oldKeyReference,
                String newKeyReference,
                EnvelopeRewrapService.Candidate candidate,
                byte[] rewrittenEnvelope,
                byte[] rewrittenEnvelopeDigest) {
            EnvelopeRewrapService.CommitOutcome result = transactions.execute(status -> {
                long checkpoint = jdbc.queryForObject("SELECT checkpoint FROM "
                        + "phase03_rotation_rewrap_checkpoint WHERE singleton_id=1 FOR UPDATE",
                        Long.class);
                if (checkpoint != candidate.sequence() - 1) {
                    status.setRollbackOnly();
                    return EnvelopeRewrapService.CommitOutcome.DRIFT;
                }
                int replaced = jdbc.update("UPDATE phase03_rotation_envelopes SET "
                                + "key_reference=?,envelope=?,envelope_digest=? "
                                + "WHERE sequence_id=? AND key_reference=? AND envelope_digest=?",
                        newKeyReference, rewrittenEnvelope, rewrittenEnvelopeDigest,
                        candidate.sequence(), oldKeyReference, candidate.originalEnvelopeDigest());
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

        private static byte[] digest(byte[] value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value);
            } catch (Exception failure) {
                throw new IllegalStateException("digest unavailable", failure);
            }
        }
    }
}
