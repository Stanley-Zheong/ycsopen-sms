package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ycsopen.sms.core.common.security.envelope.EnvelopeCodec;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11CryptoStorageProperties;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11FailureMapper;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11ProviderFactory;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.key.pkcs11.VersionedKeyDescriptorRegistry;
import com.ycsopen.sms.core.common.security.key.lifecycle.ActiveFieldKeyReference;
import com.ycsopen.sms.core.common.security.key.lifecycle.KeyReferenceRepository;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.SignerAnchor;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.WriterIdentity;
import com.ycsopen.sms.core.common.security.migration.SignedMigrationManifestVerifier.JdbcPairAdmissionStore;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.DeploymentSubject;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmissionRequest;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Production-only composition root discovered by the shipped migration CLI. */
public final class ProductionMigrationCommandServicesFactory
        implements ProtectedDataMigrationLauncher.CommandServicesFactory {

    static final String CONFIG_PROPERTY = "ycsopen.phase03.migration.config";
    static final String CONFIG_ENVIRONMENT = "YCSOPEN_PHASE03_MIGRATION_CONFIG";

    private static final int MAXIMUM_CONFIG_BYTES = 1_048_576;
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern MIGRATION_SET = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern CANONICAL_MANIFEST_DIGEST =
            Pattern.compile("sha256:[a-f0-9]{64}");

    @Override
    public ProtectedDataMigrationCommand.CommandServices create() {
        ProductionConfiguration configuration = loadConfiguration(configurationPath());
        DriverManagerDataSource dataSource = dataSource(configuration.jdbc());
        Pkcs11ProviderFactory.Session session = null;
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            DataSourceTransactionManager transactionManager =
                    new DataSourceTransactionManager(dataSource);
            TransactionTemplate transactions = new TransactionTemplate(transactionManager);
            KeyReferenceRepository keyReferences =
                    new KeyReferenceRepository.Jdbc(jdbc, transactions);
            Pkcs11FailureMapper failureMapper = new Pkcs11FailureMapper();
            Pkcs11CryptoStorageProperties pkcs11 =
                    pkcs11(configuration.pkcs11(), keyReferences);
            session = new Pkcs11ProviderFactory(failureMapper).open(pkcs11);
            SunPkcs11KeyAdapter adapter = new SunPkcs11KeyAdapter(
                    session, pkcs11,
                    new KekWrapUsageRepository(jdbc, transactionManager, failureMapper),
                    failureMapper, keyReferences);

            ProtectedDataManifest manifest = ProtectedDataManifest.load(
                    canonicalRegularFile(configuration.manifest().path()),
                    configuration.manifest().sha256());
            MigrationStateRepository repository =
                    new MigrationStateRepository.Jdbc(jdbc, transactions);
            MigrationPreflightProperties trust = new MigrationPreflightProperties(
                    configuration.signerAnchors(), configuration.compatibleWriters(),
                    configuration.recoveryKeyReferences());
            SignedMigrationManifestVerifier verifier = new SignedMigrationManifestVerifier(
                    trust, new JdbcPairAdmissionStore(jdbc, transactions), Clock.systemUTC());
            EnvelopeCodec envelopeCodec = new EnvelopeCodec();
            ProtectedDataMigrationRunner runner = new ProtectedDataMigrationRunner(
                    manifest, repository, new LegacyValueClassifier(envelopeCodec),
                    new ProtectedFieldCodec(
                            envelopeCodec, adapter, new SecureRandom(),
                            new ActiveFieldKeyReference(keyReferences)::current),
                    ProductionMigrationCommandServicesFactory::sha256,
                    new Pkcs11MigrationBlindIndexPort(adapter, jdbc), Clock.systemUTC());
            ProtectedDataMigrationCommand.DefaultServices delegate =
                    new ProtectedDataMigrationCommand.DefaultServices(invocation ->
                            verifier.verifyAndAdmit(new PairedAdmissionRequest(
                                    invocation.writerManifest(), invocation.writerSignature(),
                                    invocation.snapshotManifest(), invocation.snapshotSignature(),
                                    new DeploymentSubject(
                                            configuration.migrationSetId(), invocation.environment(),
                                            invocation.databaseInstanceFingerprint(), invocation.schema(),
                                            invocation.flywaySetDigest()))), repository, runner);
            return new ManagedServices(delegate, session);
        } catch (RuntimeException failure) {
            if (session != null) {
                session.close();
            }
            throw failure;
        }
    }

    static ProductionConfiguration loadConfiguration(Path path) {
        try {
            Path canonical = canonicalRegularFile(path == null ? null : path.toString());
            long size = Files.size(canonical);
            if (size < 2 || size > MAXIMUM_CONFIG_BYTES) {
                throw new IllegalArgumentException("migration configuration is unavailable");
            }
            ObjectMapper json = new ObjectMapper(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
            json.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            json.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            ProductionConfiguration configuration = json.readValue(
                    Files.readAllBytes(canonical), ProductionConfiguration.class);
            configuration.validate();
            return configuration;
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("migration configuration is unavailable");
        }
    }

    private static Path configurationPath() {
        String configured = System.getProperty(CONFIG_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(CONFIG_ENVIRONMENT);
        }
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("migration configuration is unavailable");
        }
        return Path.of(configured);
    }

    private static DriverManagerDataSource dataSource(JdbcConfiguration configuration) {
        return new DriverManagerDataSource(configuration.url(), configuration.username(),
                secret(configuration.passwordEnvironment()));
    }

    private static Pkcs11CryptoStorageProperties pkcs11(
            Pkcs11Configuration configuration,
            KeyReferenceRepository keyReferences) {
        List<Path> allowed = configuration.allowedModulePaths().stream().map(Path::of).toList();
        return new Pkcs11CryptoStorageProperties(
                Path.of(configuration.modulePath()), allowed, configuration.slotId(),
                configuration.tokenIdentity(),
                () -> secret(configuration.pinEnvironment()).toCharArray(),
                new VersionedKeyDescriptorRegistry(keyReferences, configuration.keys()).load());
    }

    private static String secret(String environmentName) {
        if (environmentName == null || !ENVIRONMENT_NAME.matcher(environmentName).matches()) {
            throw new IllegalStateException("migration credential is unavailable");
        }
        String value = System.getenv(environmentName);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("migration credential is unavailable");
        }
        return value;
    }

    private static Path canonicalRegularFile(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("migration path is unavailable");
        }
        try {
            Path requested = Path.of(value).toAbsolutePath().normalize();
            if (!Files.isRegularFile(requested, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("migration path is unavailable");
            }
            Path canonical = requested.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!requested.equals(canonical)) {
                throw new IllegalArgumentException("migration path is unavailable");
            }
            return canonical;
        } catch (IOException failure) {
            throw new IllegalArgumentException("migration path is unavailable");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("migration integrity provider is unavailable");
        }
    }

    public record ProductionConfiguration(
            String schema,
            String migrationSetId,
            JdbcConfiguration jdbc,
            ManifestConfiguration manifest,
            Pkcs11Configuration pkcs11,
            List<SignerAnchor> signerAnchors,
            Set<WriterIdentity> compatibleWriters,
            Set<String> recoveryKeyReferences) {

        public ProductionConfiguration {
            signerAnchors = signerAnchors == null ? List.of() : List.copyOf(signerAnchors);
            compatibleWriters = compatibleWriters == null
                    ? Set.of() : Set.copyOf(compatibleWriters);
            recoveryKeyReferences = recoveryKeyReferences == null
                    ? Set.of() : Set.copyOf(recoveryKeyReferences);
        }

        void validate() {
            if (!"phase03-migration-production/v1".equals(schema)
                    || migrationSetId == null || !MIGRATION_SET.matcher(migrationSetId).matches()) {
                throw new IllegalArgumentException("migration configuration schema is invalid");
            }
            Objects.requireNonNull(jdbc, "jdbc").validate();
            Objects.requireNonNull(manifest, "manifest").validate();
            Objects.requireNonNull(pkcs11, "pkcs11").validate();
            new MigrationPreflightProperties(
                    signerAnchors, compatibleWriters, recoveryKeyReferences);
        }
    }

    public record JdbcConfiguration(
            String url, String username, String passwordEnvironment) {

        void validate() {
            if (url == null || !url.startsWith("jdbc:mysql://")
                    || url.length() > 2048 || username == null || username.isBlank()
                    || username.length() > 128 || passwordEnvironment == null
                    || !ENVIRONMENT_NAME.matcher(passwordEnvironment).matches()) {
                throw new IllegalArgumentException("migration JDBC configuration is invalid");
            }
        }
    }

    public record ManifestConfiguration(String path, String sha256) {
        void validate() {
            if (path == null || path.isBlank() || sha256 == null
                    || !CANONICAL_MANIFEST_DIGEST.matcher(sha256).matches()) {
                throw new IllegalArgumentException("migration manifest configuration is invalid");
            }
        }
    }

    public record Pkcs11Configuration(
            String modulePath,
            List<String> allowedModulePaths,
            long slotId,
            String tokenIdentity,
            String pinEnvironment,
            List<Pkcs11KeyDescriptor> keys) {

        public Pkcs11Configuration {
            allowedModulePaths = allowedModulePaths == null
                    ? List.of() : List.copyOf(allowedModulePaths);
            keys = keys == null ? List.of() : List.copyOf(keys);
        }

        void validate() {
            if (modulePath == null || modulePath.isBlank() || allowedModulePaths.isEmpty()
                    || slotId < 0 || tokenIdentity == null || tokenIdentity.isBlank()
                    || pinEnvironment == null || !ENVIRONMENT_NAME.matcher(pinEnvironment).matches()
                    || keys.isEmpty()) {
                throw new IllegalArgumentException("migration PKCS11 configuration is invalid");
            }
        }
    }

    private static final class ManagedServices
            implements ProtectedDataMigrationCommand.CommandServices, AutoCloseable {
        private final ProtectedDataMigrationCommand.CommandServices delegate;
        private final Pkcs11ProviderFactory.Session session;

        private ManagedServices(
                ProtectedDataMigrationCommand.CommandServices delegate,
                Pkcs11ProviderFactory.Session session) {
            this.delegate = delegate;
            this.session = session;
        }

        @Override
        public WriterFencePort.PairedAdmission preflight(
                ProtectedDataMigrationCommand.PreflightInvocation invocation) {
            return delegate.preflight(invocation);
        }

        @Override
        public boolean acceptedPair(String pairDigest) {
            return delegate.acceptedPair(pairDigest);
        }

        @Override
        public ProtectedDataMigrationRunner.BatchResult start(
                ProtectedDataMigrationRunner.MigrationRequest request) {
            return delegate.start(request);
        }

        @Override
        public ProtectedDataMigrationRunner.BatchResult resume(
                ProtectedDataMigrationRunner.MigrationRequest request) {
            return delegate.resume(request);
        }

        @Override
        public ProtectedDataMigrationRunner.TransitionResult advance(
                ProtectedDataMigrationRunner.TransitionRequest request) {
            return delegate.advance(request);
        }

        @Override
        public void pause(ProtectedDataMigrationRunner.RunControlRequest request) {
            delegate.pause(request);
        }

        @Override
        public void abort(ProtectedDataMigrationRunner.RunControlRequest request) {
            delegate.abort(request);
        }

        @Override
        public MigrationStateRepository.RunStatus status(String runId) {
            return delegate.status(runId);
        }

        @Override
        public void close() {
            session.close();
        }
    }
}
