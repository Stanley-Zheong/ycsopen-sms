package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmission;
import com.ycsopen.sms.core.common.security.migration.WriterFencePort.PairedAdmissionRequest;
import com.ycsopen.sms.core.common.security.migration.snapshot.EncryptedMySqlSnapshotService;
import com.ycsopen.sms.core.common.security.migration.snapshot.EncryptedMySqlSnapshotService.FreshSchemaGate;
import com.ycsopen.sms.core.common.security.migration.snapshot.EncryptedMySqlSnapshotService.SnapshotAdmission;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.Database;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotChunkStore;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotManifest;
import com.ycsopen.sms.core.common.security.persistence.ProtectedFieldCodec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
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
    static final String CONFIG_SIGNATURE_PROPERTY =
            "ycsopen.phase03.migration.config-signature";
    static final String CONFIG_SIGNATURE_ENVIRONMENT =
            "YCSOPEN_PHASE03_MIGRATION_CONFIG_SIGNATURE";
    static final String CONFIG_PUBLIC_KEY_PROPERTY =
            "ycsopen.phase03.migration.config-public-key";
    static final String CONFIG_PUBLIC_KEY_ENVIRONMENT =
            "YCSOPEN_PHASE03_MIGRATION_CONFIG_PUBLIC_KEY";
    static final String SNAPSHOT_STORE_PROPERTY =
            "ycsopen.security.crypto-storage.snapshot-store-root";
    static final String SNAPSHOT_STORE_ENVIRONMENT =
            "YCSOPEN_ENCRYPTED_SNAPSHOT_STORE_ROOT";

    private static final int MAXIMUM_CONFIG_BYTES = 1_048_576;
    private static final int ED25519_SIGNATURE_BYTES = 64;
    private static final int ED25519_SIGNATURE_BASE64_BYTES = 88;
    private static final int ED25519_X509_PUBLIC_KEY_BYTES = 44;
    private static final int MAXIMUM_PUBLIC_KEY_BASE64_CHARACTERS = 128;
    private static final byte[] CONFIG_SIGNATURE_DOMAIN =
            "YCS-PHASE03-PRODUCTION-CONFIG-SIGNATURE/v1\0"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern MIGRATION_SET = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern CANONICAL_MANIFEST_DIGEST =
            Pattern.compile("sha256:[a-f0-9]{64}");

    @Override
    public ProtectedDataMigrationCommand.CommandServices create() {
        ConfigurationTrust trust = configurationTrust();
        ProductionConfiguration configuration = loadConfiguration(
                trust.configurationPath(), trust.signaturePath(), trust.publicKeyBase64(),
                ConfigurationReadObserver.NONE);
        DriverManagerDataSource dataSource = dataSource(configuration.jdbc());
        Database snapshotSource = snapshotDatabase(configuration.jdbc());
        return compose(
                configuration, dataSource, snapshotSource,
                (jdbc, transactionManager, keyReferences) -> openHsmRuntime(
                        configuration, jdbc, transactionManager, keyReferences),
                ProductionMigrationCommandServicesFactory::fixedArgumentSnapshotProcess,
                ProductionMigrationCommandServicesFactory::productionFreshSchemaGate);
    }

    ProtectedDataMigrationCommand.CommandServices compose(
            ProductionConfiguration configuration,
            MySqlSnapshotProcess snapshotProcess) {
        Objects.requireNonNull(snapshotProcess, "snapshotProcess");
        DriverManagerDataSource dataSource = dataSource(configuration.jdbc());
        Database snapshotSource = snapshotDatabase(configuration.jdbc());
        return compose(
                configuration, dataSource, snapshotSource,
                (jdbc, transactionManager, keyReferences) -> openHsmRuntime(
                        configuration, jdbc, transactionManager, keyReferences),
                (canonicalStoreRoot, snapshotConfiguration) -> snapshotProcess,
                ProductionMigrationCommandServicesFactory::productionFreshSchemaGate);
    }

    /**
     * Single production composition path. Tests may replace only service boundaries; all stores,
     * database-owned key resolution, snapshot operations and managed delegation remain identical
     * to {@link #create()}.
     */
    ProtectedDataMigrationCommand.CommandServices compose(
            ProductionConfiguration configuration,
            DriverManagerDataSource dataSource,
            Database snapshotSource,
            HsmRuntimeFactory hsmFactory,
            SnapshotProcessFactory processFactory,
            FreshSchemaGateFactory freshSchemaGateFactory) {
        Objects.requireNonNull(configuration, "configuration").validate();
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(snapshotSource, "snapshotSource");
        Objects.requireNonNull(hsmFactory, "hsmFactory");
        Objects.requireNonNull(processFactory, "processFactory");
        Objects.requireNonNull(freshSchemaGateFactory, "freshSchemaGateFactory");
        ManagedResources resources = new ManagedResources();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            DataSourceTransactionManager transactionManager =
                    new DataSourceTransactionManager(dataSource);
            TransactionTemplate transactions = new TransactionTemplate(transactionManager);
            KeyReferenceRepository keyReferences =
                    new KeyReferenceRepository.Jdbc(jdbc, transactions);
            ProtectedDataManifest manifest = ProtectedDataManifest.load(
                    canonicalRegularFile(configuration.manifest().path()),
                    configuration.manifest().sha256());
            Path signedSnapshotRoot = canonicalDirectory(
                    Path.of(configuration.snapshot().storeRoot()));
            Path bootstrapSnapshotRoot = canonicalDirectory(Path.of(startupSetting(
                    SNAPSHOT_STORE_PROPERTY, SNAPSHOT_STORE_ENVIRONMENT)));
            if (!signedSnapshotRoot.equals(bootstrapSnapshotRoot)) {
                throw unavailable();
            }
            SnapshotChunkStore.FileStore snapshotStore =
                    new SnapshotChunkStore.FileStore(signedSnapshotRoot);
            MySqlSnapshotProcess snapshotProcess = processFactory.create(
                    signedSnapshotRoot, configuration.snapshot());
            if (snapshotProcess instanceof AutoCloseable closeable) {
                resources.add(closeable);
            }
            HsmRuntime hsm = hsmFactory.open(jdbc, transactionManager, keyReferences);
            SunPkcs11KeyAdapter adapter = hsm.adapter();
            resources.add(hsm.resource());

            MigrationStateRepository repository =
                    new MigrationStateRepository.Jdbc(jdbc, transactions);
            MigrationPreflightProperties migrationTrust = new MigrationPreflightProperties(
                    configuration.signerAnchors(), configuration.compatibleWriters(),
                    configuration.recoveryKeyReferences());
            EnvelopeCodec envelopeCodec = new EnvelopeCodec();
            ProtectedDataMigrationRunner runner = new ProtectedDataMigrationRunner(
                    manifest, repository, new LegacyValueClassifier(envelopeCodec),
                    new ProtectedFieldCodec(
                            envelopeCodec, adapter, new SecureRandom(),
                            new ActiveFieldKeyReference(keyReferences)::current),
                    ProductionMigrationCommandServicesFactory::sha256,
                    new Pkcs11MigrationBlindIndexPort(adapter, jdbc), Clock.systemUTC());
            EncryptedMySqlSnapshotService snapshots = new EncryptedMySqlSnapshotService(
                    new ProtectedFieldCodec(
                            envelopeCodec, adapter, new SecureRandom(),
                            () -> activeSnapshotReference(keyReferences)),
                    snapshotProcess,
                    snapshotStore,
                    freshSchemaGateFactory.create(jdbc, snapshotSource));
            ProtectedDataMigrationCommand.SnapshotOperations snapshotOperations =
                    new ProductionSnapshotOperations(
                            configuration.migrationSetId(), snapshots, snapshotStore,
                            snapshotSource, jdbc,
                            () -> activeSnapshotReference(keyReferences));
            SignedMigrationManifestVerifier verifier = new SignedMigrationManifestVerifier(
                    migrationTrust, new JdbcPairAdmissionStore(jdbc, transactions),
                    Clock.systemUTC(), snapshots::requireCompleteRetainedSnapshot);
            ProtectedDataMigrationCommand.DefaultServices delegate =
                    new ProtectedDataMigrationCommand.DefaultServices(invocation ->
                            verifier.verifyAndAdmit(new PairedAdmissionRequest(
                                    invocation.writerManifest(), invocation.writerSignature(),
                                    invocation.snapshotManifest(), invocation.snapshotSignature(),
                                    new DeploymentSubject(
                                            configuration.migrationSetId(), invocation.environment(),
                                            invocation.databaseInstanceFingerprint(), invocation.schema(),
                                            invocation.flywaySetDigest()))), repository, runner,
                            snapshotOperations);
            return manage(delegate, resources);
        } catch (RuntimeException failure) {
            try {
                resources.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static HsmRuntime openHsmRuntime(
            ProductionConfiguration configuration,
            JdbcTemplate jdbc,
            DataSourceTransactionManager transactionManager,
            KeyReferenceRepository keyReferences) {
        Pkcs11FailureMapper failureMapper = new Pkcs11FailureMapper();
        Pkcs11CryptoStorageProperties pkcs11 = pkcs11(configuration.pkcs11(), keyReferences);
        Pkcs11ProviderFactory.Session session = new Pkcs11ProviderFactory(failureMapper).open(pkcs11);
        try {
            SunPkcs11KeyAdapter adapter = new SunPkcs11KeyAdapter(
                    session, pkcs11,
                    new KekWrapUsageRepository(jdbc, transactionManager, failureMapper),
                    failureMapper, keyReferences);
            return new HsmRuntime(adapter, session);
        } catch (RuntimeException failure) {
            session.close();
            throw failure;
        }
    }

    private static MySqlSnapshotProcess fixedArgumentSnapshotProcess(
            Path canonicalStoreRoot, SnapshotConfiguration configuration) {
        Objects.requireNonNull(canonicalStoreRoot, "canonicalStoreRoot");
        return new MySqlSnapshotProcess.FixedArgumentClient(
                Path.of(configuration.mysqldumpPath()), configuration.mysqldumpSha256(),
                Path.of(configuration.mysqlPath()), configuration.mysqlSha256());
    }

    private static FreshSchemaGate productionFreshSchemaGate(
            JdbcTemplate jdbc, Database snapshotSource) {
        return new FreshSchemaGate() {
            @Override
            public void requireSnapshotSource(Database source) {
                if (!sameDatabaseIdentity(snapshotSource, source)) {
                    throw SnapshotManifest.invalid();
                }
                requireSnapshotCatalog(jdbc, source.schema());
            }

            @Override
            public void requireFresh(Database source, Database target) {
                requireFreshSchema(jdbc, snapshotSource, source, target);
            }

            @Override
            public void requireRestored(Database target) {
                requireSnapshotCatalog(jdbc, target.schema());
            }
        };
    }

    @FunctionalInterface
    interface HsmRuntimeFactory {
        HsmRuntime open(
                JdbcTemplate jdbc,
                DataSourceTransactionManager transactionManager,
                KeyReferenceRepository keyReferences);
    }

    record HsmRuntime(SunPkcs11KeyAdapter adapter, AutoCloseable resource) {
        HsmRuntime {
            Objects.requireNonNull(adapter, "adapter");
            Objects.requireNonNull(resource, "resource");
        }
    }

    @FunctionalInterface
    interface SnapshotProcessFactory {
        MySqlSnapshotProcess create(
                Path canonicalStoreRoot, SnapshotConfiguration configuration);
    }

    @FunctionalInterface
    interface FreshSchemaGateFactory {
        FreshSchemaGate create(JdbcTemplate jdbc, Database snapshotSource);
    }

    private static final class ManagedResources implements AutoCloseable {
        private final List<AutoCloseable> resources = new ArrayList<>();
        private boolean closed;

        private void add(AutoCloseable resource) {
            if (closed) {
                throw unavailable();
            }
            resources.add(Objects.requireNonNull(resource, "resource"));
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            IllegalStateException failure = null;
            for (int index = resources.size() - 1; index >= 0; index--) {
                try {
                    resources.get(index).close();
                } catch (Exception cleanupFailure) {
                    if (failure == null) {
                        failure = unavailable();
                    }
                    failure.addSuppressed(cleanupFailure);
                }
            }
            resources.clear();
            if (failure != null) {
                throw failure;
            }
        }
    }

    ProtectedDataMigrationCommand.CommandServices manage(
            ProtectedDataMigrationCommand.CommandServices delegate,
            AutoCloseable resource) {
        return new ManagedServices(delegate, resource);
    }

    static ProductionConfiguration loadConfiguration(Path path) {
        ConfigurationTrust trust = configurationTrust(path);
        return loadConfiguration(path, trust.signaturePath(), trust.publicKeyBase64(),
                ConfigurationReadObserver.NONE);
    }

    static ProductionConfiguration loadConfiguration(
            Path path, ConfigurationReadObserver observer) {
        ConfigurationTrust trust = configurationTrust(path);
        return loadConfiguration(path, trust.signaturePath(), trust.publicKeyBase64(), observer);
    }

    static ProductionConfiguration loadConfiguration(
            Path path,
            Path signaturePath,
            String publicKeyBase64,
            ConfigurationReadObserver observer) {
        byte[] content = null;
        byte[] encodedSignature = null;
        byte[] signature = null;
        byte[] publicKeyBytes = null;
        try {
            content = readStableConfiguration(
                    path, MAXIMUM_CONFIG_BYTES, 2,
                    Objects.requireNonNull(observer, "observer"));
            encodedSignature = readStableConfiguration(
                    signaturePath, ED25519_SIGNATURE_BASE64_BYTES,
                    ED25519_SIGNATURE_BASE64_BYTES, ConfigurationReadObserver.NONE);
            signature = decodeCanonicalBase64(encodedSignature, ED25519_SIGNATURE_BYTES);
            publicKeyBytes = decodeCanonicalBase64(
                    requireBoundedPublicKey(publicKeyBase64), ED25519_X509_PUBLIC_KEY_BYTES);
            ObjectMapper json = new ObjectMapper(JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
            json.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            json.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
            JsonNode root = json.readTree(content);
            byte[] canonical = canonicalJsonBytes(root);
            if (root == null || !root.isObject() || !MessageDigest.isEqual(content, canonical)) {
                clear(canonical);
                throw unavailable();
            }
            clear(canonical);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            if (!Arrays.equals(publicKey.getEncoded(), publicKeyBytes)) {
                throw unavailable();
            }
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(CONFIG_SIGNATURE_DOMAIN);
            verifier.update(ByteBuffer.allocate(Integer.BYTES).putInt(content.length).array());
            verifier.update(content);
            if (!verifier.verify(signature)) {
                throw unavailable();
            }
            ProductionConfiguration configuration = json.treeToValue(
                    root, ProductionConfiguration.class);
            configuration.validate();
            return configuration;
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            throw new IllegalStateException("migration configuration is unavailable");
        } finally {
            clear(content);
            clear(encodedSignature);
            clear(signature);
            clear(publicKeyBytes);
        }
    }

    /**
     * Reads through descriptor-relative, no-follow handles. Providers without
     * {@link SecureDirectoryStream} cannot establish this boundary and are rejected.
     */
    private static byte[] readStableConfiguration(
            Path value, int maximumBytes, int minimumBytes,
            ConfigurationReadObserver observer) throws IOException {
        if (value == null) {
            throw unavailable();
        }
        Path requested = value.toAbsolutePath().normalize();
        if (requested.getRoot() == null || requested.getNameCount() < 1) {
            throw unavailable();
        }
        assertNoSymlinkComponents(requested);
        observer.beforeBaselineRead(requested);
        assertNoSymlinkComponents(requested);
        List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
        try {
            DirectoryStream<Path> rootStream = Files.newDirectoryStream(requested.getRoot());
            if (!(rootStream instanceof SecureDirectoryStream<Path> secureRoot)) {
                rootStream.close();
                return readStablePortable(requested, maximumBytes, minimumBytes, observer);
            }
            opened.add(secureRoot);
            SecureDirectoryStream<Path> parent = secureRoot;
            for (int index = 0; index < requested.getNameCount() - 1; index++) {
                Path component = requested.getName(index);
                BasicFileAttributes attributes = attributes(parent, component);
                if (!attributes.isDirectory()) {
                    throw unavailable();
                }
                SecureDirectoryStream<Path> child = parent.newDirectoryStream(
                        component, LinkOption.NOFOLLOW_LINKS);
                opened.add(child);
                parent = child;
            }
            Path file = requested.getFileName();
            BasicFileAttributes before = attributes(parent, file);
            requireSafeConfigurationMetadata(parent, file, before);
            byte[] baseline = readDescriptor(
                    parent, file, before.size(), maximumBytes, minimumBytes);
            observer.beforeFirstRead(requested);
            byte[] first = readDescriptor(
                    parent, file, before.size(), maximumBytes, minimumBytes);
            observer.afterFirstRead(requested);
            BasicFileAttributes between = attributes(parent, file);
            byte[] second = readDescriptor(
                    parent, file, between.size(), maximumBytes, minimumBytes);
            observer.afterStableReads(requested);
            BasicFileAttributes after = attributes(parent, file);
            assertStable(before, between);
            assertStable(between, after);
            requireSafeConfigurationMetadata(parent, file, after);
            assertNoSymlinkComponents(requested);
            if (!MessageDigest.isEqual(baseline, first)
                    || !MessageDigest.isEqual(first, second)) {
                Arrays.fill(baseline, (byte) 0);
                Arrays.fill(first, (byte) 0);
                Arrays.fill(second, (byte) 0);
                throw unavailable();
            }
            Arrays.fill(baseline, (byte) 0);
            Arrays.fill(second, (byte) 0);
            return first;
        } finally {
            for (int index = opened.size() - 1; index >= 0; index--) {
                opened.get(index).close();
            }
        }
    }

    /**
     * Portable fallback for providers without secure directory handles (notably the macOS JDK).
     * Three independently opened no-follow reads must agree while the complete path and identity
     * metadata remain unchanged. A race can therefore only cause rejection or identical bytes.
     */
    private static byte[] readStablePortable(
            Path requested, int maximumBytes, int minimumBytes,
            ConfigurationReadObserver observer) throws IOException {
        BasicFileAttributes before = Files.readAttributes(
                requested, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        requireSafeConfigurationMetadata(requested, before);
        byte[] baseline = readDescriptor(
                requested, before.size(), maximumBytes, minimumBytes);
        observer.beforeFirstRead(requested);
        assertNoSymlinkComponents(requested);
        byte[] first = readDescriptor(
                requested, before.size(), maximumBytes, minimumBytes);
        observer.afterFirstRead(requested);
        assertNoSymlinkComponents(requested);
        BasicFileAttributes between = Files.readAttributes(
                requested, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        byte[] second = readDescriptor(
                requested, between.size(), maximumBytes, minimumBytes);
        observer.afterStableReads(requested);
        BasicFileAttributes after = Files.readAttributes(
                requested, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        assertStable(before, between);
        assertStable(between, after);
        requireSafeConfigurationMetadata(requested, after);
        assertNoSymlinkComponents(requested);
        if (!MessageDigest.isEqual(baseline, first)
                || !MessageDigest.isEqual(first, second)) {
            Arrays.fill(baseline, (byte) 0);
            Arrays.fill(first, (byte) 0);
            Arrays.fill(second, (byte) 0);
            throw unavailable();
        }
        Arrays.fill(baseline, (byte) 0);
        Arrays.fill(second, (byte) 0);
        return first;
    }

    private static byte[] readDescriptor(
            Path path, long size, int maximumBytes, int minimumBytes) throws IOException {
        if (size < minimumBytes || size > maximumBytes) {
            throw unavailable();
        }
        byte[] content = new byte[Math.toIntExact(size)];
        try (SeekableByteChannel input = Files.newByteChannel(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            readExact(input, content);
        }
        return content;
    }

    private static byte[] readDescriptor(
            SecureDirectoryStream<Path> parent, Path file, long size,
            int maximumBytes, int minimumBytes) throws IOException {
        if (size < minimumBytes || size > maximumBytes) {
            throw unavailable();
        }
        byte[] content = new byte[Math.toIntExact(size)];
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel input = parent.newByteChannel(file, options)) {
            readExact(input, content);
        }
        return content;
    }

    private static void readExact(SeekableByteChannel input, byte[] content) throws IOException {
        ByteBuffer target = ByteBuffer.wrap(content);
        while (target.hasRemaining()) {
            if (input.read(target) < 0) {
                Arrays.fill(content, (byte) 0);
                throw unavailable();
            }
        }
        if (input.read(ByteBuffer.allocate(1)) != -1) {
            Arrays.fill(content, (byte) 0);
            throw unavailable();
        }
    }

    private static BasicFileAttributes attributes(
            SecureDirectoryStream<Path> parent, Path path) throws IOException {
        var view = parent.getFileAttributeView(
                path, java.nio.file.attribute.BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw unavailable();
        }
        return view.readAttributes();
    }

    private static void requireSafeConfigurationMetadata(
            SecureDirectoryStream<Path> parent,
            Path file,
            BasicFileAttributes basic) throws IOException {
        PosixFileAttributeView view = parent.getFileAttributeView(
                file, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null || !basic.isRegularFile() || basic.fileKey() == null) {
            throw unavailable();
        }
        PosixFileAttributes posix = view.readAttributes();
        String expectedOwner = Files.getOwner(
                Path.of(System.getProperty("user.home")), LinkOption.NOFOLLOW_LINKS).getName();
        if (!expectedOwner.equals(posix.owner().getName())
                || !posix.permissions().contains(PosixFilePermission.OWNER_READ)
                || posix.permissions().contains(PosixFilePermission.GROUP_WRITE)
                || posix.permissions().contains(PosixFilePermission.OTHERS_WRITE)) {
            throw unavailable();
        }
    }

    private static void requireSafeConfigurationMetadata(
            Path path, BasicFileAttributes basic) throws IOException {
        PosixFileAttributes posix = Files.readAttributes(
                path, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        requireSafeConfigurationMetadata(basic, posix);
    }

    private static void requireSafeConfigurationMetadata(
            BasicFileAttributes basic, PosixFileAttributes posix) throws IOException {
        String expectedOwner = Files.getOwner(
                Path.of(System.getProperty("user.home")), LinkOption.NOFOLLOW_LINKS).getName();
        if (!basic.isRegularFile() || basic.fileKey() == null
                || !expectedOwner.equals(posix.owner().getName())
                || !posix.permissions().contains(PosixFilePermission.OWNER_READ)
                || posix.permissions().contains(PosixFilePermission.GROUP_WRITE)
                || posix.permissions().contains(PosixFilePermission.OTHERS_WRITE)) {
            throw unavailable();
        }
    }

    private static void assertStable(
            BasicFileAttributes before, BasicFileAttributes after) {
        if (!after.isRegularFile() || before.fileKey() == null
                || !before.fileKey().equals(after.fileKey())
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !before.creationTime().equals(after.creationTime())) {
            throw unavailable();
        }
    }

    private static void assertNoSymlinkComponents(Path requested) throws IOException {
        Path current = requested.getRoot();
        String processOwner = Files.getOwner(
                Path.of(System.getProperty("user.home")), LinkOption.NOFOLLOW_LINKS).getName();
        for (Path component : requested) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw unavailable();
            }
            if (!current.equals(requested)) {
                PosixFileAttributes attributes = Files.readAttributes(
                        current, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                String owner = attributes.owner().getName();
                if (!attributes.isDirectory()
                        || (!processOwner.equals(owner) && !"root".equals(owner))
                        || attributes.permissions().contains(PosixFilePermission.GROUP_WRITE)
                        || attributes.permissions().contains(PosixFilePermission.OTHERS_WRITE)) {
                    throw unavailable();
                }
            }
        }
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("migration configuration is unavailable");
    }

    interface ConfigurationReadObserver {
        ConfigurationReadObserver NONE = new ConfigurationReadObserver() { };

        default void beforeBaselineRead(Path path) {
        }

        default void beforeFirstRead(Path path) {
        }

        default void afterFirstRead(Path path) {
        }

        default void afterStableReads(Path path) {
        }
    }

    private static byte[] requireBoundedPublicKey(String value) {
        if (value == null || value.isBlank()
                || value.length() > MAXIMUM_PUBLIC_KEY_BASE64_CHARACTERS
                || !StandardCharsets.US_ASCII.newEncoder().canEncode(value)) {
            throw unavailable();
        }
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] decodeCanonicalBase64(byte[] encoded, int expectedBytes) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            if (decoded.length != expectedBytes
                    || !MessageDigest.isEqual(encoded, Base64.getEncoder().encode(decoded))) {
                clear(decoded);
                throw unavailable();
            }
            return decoded;
        } catch (IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    public static byte[] canonicalJsonBytes(JsonNode node) {
        if (node == null) {
            throw unavailable();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeCanonicalJson(node, output);
        return output.toByteArray();
    }

    private static void writeCanonicalJson(JsonNode node, ByteArrayOutputStream output) {
        try {
            if (node.isObject()) {
                output.write('{');
                List<String> names = new ArrayList<>();
                node.fieldNames().forEachRemaining(names::add);
                names.sort(String::compareTo);
                for (int index = 0; index < names.size(); index++) {
                    if (index > 0) {
                        output.write(',');
                    }
                    output.writeBytes(new ObjectMapper().writeValueAsBytes(names.get(index)));
                    output.write(':');
                    writeCanonicalJson(node.get(names.get(index)), output);
                }
                output.write('}');
            } else if (node.isArray()) {
                output.write('[');
                for (int index = 0; index < node.size(); index++) {
                    if (index > 0) {
                        output.write(',');
                    }
                    writeCanonicalJson(node.get(index), output);
                }
                output.write(']');
            } else if (node.isTextual()) {
                output.writeBytes(new ObjectMapper().writeValueAsBytes(node.textValue()));
            } else if (node.isBoolean()) {
                output.writeBytes(Boolean.toString(node.booleanValue())
                        .getBytes(StandardCharsets.US_ASCII));
            } else if (node.isIntegralNumber()) {
                output.writeBytes(node.bigIntegerValue().toString()
                        .getBytes(StandardCharsets.US_ASCII));
            } else if (node.isNull()) {
                output.writeBytes("null".getBytes(StandardCharsets.US_ASCII));
            } else {
                throw unavailable();
            }
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private static void clear(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
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

    private static ConfigurationTrust configurationTrust() {
        return configurationTrust(configurationPath());
    }

    private static ConfigurationTrust configurationTrust(Path configurationPath) {
        String signature = startupSetting(
                CONFIG_SIGNATURE_PROPERTY, CONFIG_SIGNATURE_ENVIRONMENT);
        String publicKey = startupSetting(
                CONFIG_PUBLIC_KEY_PROPERTY, CONFIG_PUBLIC_KEY_ENVIRONMENT);
        return new ConfigurationTrust(configurationPath, Path.of(signature), publicKey);
    }

    private static String startupSetting(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        if (value == null || value.isBlank()) {
            throw unavailable();
        }
        return value;
    }

    private record ConfigurationTrust(
            Path configurationPath, Path signaturePath, String publicKeyBase64) {
        private ConfigurationTrust {
            Objects.requireNonNull(configurationPath, "configurationPath");
            Objects.requireNonNull(signaturePath, "signaturePath");
            Objects.requireNonNull(publicKeyBase64, "publicKeyBase64");
        }
    }

    private static DriverManagerDataSource dataSource(JdbcConfiguration configuration) {
        return new DriverManagerDataSource(configuration.url(), configuration.username(),
                secret(configuration.passwordEnvironment()));
    }

    private static Database snapshotDatabase(JdbcConfiguration configuration) {
        try {
            String prefix = "jdbc:mysql://";
            URI uri = URI.create("mysql://" + configuration.url().substring(prefix.length()));
            String path = uri.getPath();
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || path == null || path.length() < 2 || path.indexOf('/', 1) >= 0) {
                throw unavailable();
            }
            int port = uri.getPort() < 0 ? 3306 : uri.getPort();
            char[] password = secret(configuration.passwordEnvironment()).toCharArray();
            try {
                return new Database(uri.getHost(), port, configuration.username(), password,
                        path.substring(1));
            } finally {
                Arrays.fill(password, '\0');
            }
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private static String activeSnapshotReference(KeyReferenceRepository references) {
        KeyReferenceRepository.KeyReference active = references
                .uniqueActive(KeyReferenceRepository.Purpose.SNAPSHOT_RECOVERY)
                .filter(reference -> "pkcs11".equals(reference.providerId()))
                .orElseThrow(ProductionMigrationCommandServicesFactory::unavailable);
        return active.providerKeyReference();
    }

    private static Path canonicalDirectory(Path value) {
        try {
            Path requested = value.toAbsolutePath().normalize();
            assertNoSymlinkComponents(requested);
            if (!Files.isDirectory(requested, LinkOption.NOFOLLOW_LINKS)) {
                throw unavailable();
            }
            Path canonical = requested.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!requested.equals(canonical)) {
                throw unavailable();
            }
            return canonical;
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private static void requireFreshSchema(
            JdbcTemplate jdbc, Database configuredSource, Database source, Database target) {
        if (!sameDatabaseIdentity(configuredSource, source)
                || !configuredSource.host().equals(target.host())
                || configuredSource.port() != target.port()
                || !configuredSource.username().equals(target.username())
                || source.schema().equals(target.schema())) {
            throw SnapshotManifest.invalid();
        }
        try {
            Long sourceCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                    Long.class, source.schema());
            Long targetCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                    Long.class, target.schema());
            Long tableCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ?",
                    Long.class, target.schema());
            Long routineCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = ?",
                    Long.class, target.schema());
            Long eventCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.events WHERE event_schema = ?",
                    Long.class, target.schema());
            if (!Long.valueOf(1).equals(sourceCount) || !Long.valueOf(1).equals(targetCount)
                    || !Long.valueOf(0).equals(tableCount)
                    || !Long.valueOf(0).equals(routineCount)
                    || !Long.valueOf(0).equals(eventCount)) {
                throw SnapshotManifest.invalid();
            }
        } catch (RuntimeException failure) {
            throw SnapshotManifest.invalid();
        }
    }

    private static void requireSnapshotCatalog(JdbcTemplate jdbc, String schema) {
        try {
            Long unsupported = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables t "
                            + "WHERE t.table_schema = ? AND t.table_type = 'BASE TABLE' AND ("
                            + "UPPER(COALESCE(t.engine,'')) <> 'INNODB' OR ("
                            + "t.table_name <> 'flyway_schema_history' AND NOT EXISTS ("
                            + "SELECT 1 FROM information_schema.statistics s "
                            + "JOIN information_schema.columns c ON c.table_schema=s.table_schema "
                            + "AND c.table_name=s.table_name AND c.column_name=s.column_name "
                            + "WHERE s.table_schema=t.table_schema AND s.table_name=t.table_name "
                            + "AND s.non_unique=0 GROUP BY s.index_name "
                            + "HAVING SUM(CASE WHEN c.is_nullable='YES' THEN 1 ELSE 0 END)=0)))",
                    Long.class, schema);
            Long routineCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema = ?",
                    Long.class, schema);
            Long eventCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.events WHERE event_schema = ?",
                    Long.class, schema);
            Long flywayRankDrift = jdbc.queryForObject(
                    "SELECT CASE WHEN COUNT(*) = COUNT(DISTINCT installed_rank) THEN 0 ELSE 1 END "
                            + "FROM `" + schema + "`.flyway_schema_history",
                    Long.class);
            if (!Long.valueOf(0).equals(unsupported)
                    || !Long.valueOf(0).equals(routineCount)
                    || !Long.valueOf(0).equals(eventCount)
                    || !Long.valueOf(0).equals(flywayRankDrift)) {
                throw SnapshotManifest.invalid();
            }
        } catch (RuntimeException failure) {
            throw SnapshotManifest.invalid();
        }
    }

    private static boolean sameDatabaseIdentity(Database left, Database right) {
        return left.host().equals(right.host()) && left.port() == right.port()
                && left.username().equals(right.username()) && left.schema().equals(right.schema());
    }

    /** Production snapshot implementation used by the shipped CLI composition root. */
    static final class ProductionSnapshotOperations
            implements ProtectedDataMigrationCommand.SnapshotOperations {
        private final String migrationSetId;
        private final EncryptedMySqlSnapshotService service;
        private final SnapshotChunkStore.FileStore store;
        private final Database source;
        private final JdbcTemplate jdbc;
        private final Supplier<String> recoveryKeyReference;

        ProductionSnapshotOperations(
                String migrationSetId,
                EncryptedMySqlSnapshotService service,
                SnapshotChunkStore.FileStore store,
                Database source,
                JdbcTemplate jdbc,
                Supplier<String> recoveryKeyReference) {
            this.migrationSetId = Objects.requireNonNull(migrationSetId, "migrationSetId");
            this.service = Objects.requireNonNull(service, "service");
            this.store = Objects.requireNonNull(store, "store");
            this.source = Objects.requireNonNull(source, "source");
            this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
            this.recoveryKeyReference = Objects.requireNonNull(
                    recoveryKeyReference, "recoveryKeyReference");
        }

        @Override
        public SnapshotManifest create(
                ProtectedDataMigrationCommand.SnapshotCreateInvocation invocation) {
            Objects.requireNonNull(invocation, "invocation");
            SnapshotManifest.Subject subject = new SnapshotManifest.Subject(
                    migrationSetId, invocation.environment(),
                    invocation.databaseInstanceFingerprint(), invocation.schema(),
                    invocation.flywaySetDigest(), invocation.globalSequence(),
                    invocation.signerKeyVersion());
            return service.create(new EncryptedMySqlSnapshotService.CreateRequest(
                    source, subject, invocation.snapshotId(), recoveryKeyReference.get()));
        }

        @Override
        public EncryptedMySqlSnapshotService.RestoreResult restore(
                ProtectedDataMigrationCommand.SnapshotRestoreInvocation invocation) {
            Objects.requireNonNull(invocation, "invocation");
            byte[] manifestBytes = store.retainedManifest(
                    invocation.snapshotId()).canonicalManifest();
            try {
                SnapshotManifest manifest = SnapshotManifest.parse(manifestBytes);
                SnapshotAdmission admission = requireCurrentAdmission(
                        invocation.pairDigest(), manifest);
                char[] password = source.password();
                try {
                    Database target = new Database(
                            source.host(), source.port(), source.username(), password,
                            invocation.targetSchema());
                    return service.restore(manifestBytes, admission, source, target);
                } finally {
                    Arrays.fill(password, '\0');
                }
            } finally {
                clear(manifestBytes);
            }
        }

        @Override
        public String delete(ProtectedDataMigrationCommand.SnapshotDeleteInvocation invocation) {
            Objects.requireNonNull(invocation, "invocation");
            return service.delete(invocation.snapshotId(), invocation.snapshotDigest());
        }

        Path canonicalStoreRoot() {
            return store.canonicalRoot();
        }

        private SnapshotAdmission requireCurrentAdmission(
                String pairDigest, SnapshotManifest manifest) {
            if (pairDigest == null || !pairDigest.matches("[a-f0-9]{64}")
                    || !migrationSetId.equals(manifest.subject().migrationSetId())
                    || !source.schema().equals(manifest.subject().schema())) {
                throw SnapshotManifest.invalid();
            }
            List<SnapshotAdmission> rows = jdbc.query(
                    "SELECT global_sequence, signer_key_version, LOWER(HEX(snapshot_digest)) "
                            + "FROM ycs_crypto_manifest_pair_admission "
                            + "WHERE singleton_id = 1 AND pair_digest = UNHEX(?) "
                            + "AND migration_set_id = ?",
                    (result, row) -> new SnapshotAdmission(
                            result.getLong(1), result.getString(2), result.getString(3),
                            manifest.snapshotId(), manifest.recoveryKeyReference()),
                    pairDigest, migrationSetId);
            if (rows.size() != 1) {
                throw SnapshotManifest.invalid();
            }
            SnapshotAdmission admission = rows.getFirst();
            if (!manifest.digest().equals(admission.snapshotDigest())
                    || manifest.subject().globalSequence() != admission.globalSequence()
                    || !manifest.subject().signerKeyVersion().equals(admission.signerKeyVersion())) {
                throw SnapshotManifest.invalid();
            }
            return admission;
        }
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
            SnapshotConfiguration snapshot,
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
            Objects.requireNonNull(snapshot, "snapshot").validate();
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

    public record SnapshotConfiguration(
            String storeRoot,
            String mysqldumpPath,
            String mysqldumpSha256,
            String mysqlPath,
            String mysqlSha256) {
        void validate() {
            if (storeRoot == null || storeRoot.isBlank() || storeRoot.length() > 4_096
                    || mysqldumpPath == null || mysqldumpPath.isBlank()
                    || mysqldumpPath.length() > 4_096
                    || mysqldumpSha256 == null
                    || !CANONICAL_MANIFEST_DIGEST.matcher(mysqldumpSha256).matches()
                    || mysqlPath == null || mysqlPath.isBlank() || mysqlPath.length() > 4_096
                    || mysqlSha256 == null
                    || !CANONICAL_MANIFEST_DIGEST.matcher(mysqlSha256).matches()) {
                throw new IllegalArgumentException("migration snapshot configuration is invalid");
            }
        }
    }

    private static final class ManagedServices
            implements ProtectedDataMigrationCommand.CommandServices, AutoCloseable {
        private final ProtectedDataMigrationCommand.CommandServices delegate;
        private final AutoCloseable resource;

        private ManagedServices(
                ProtectedDataMigrationCommand.CommandServices delegate,
                AutoCloseable resource) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.resource = Objects.requireNonNull(resource, "resource");
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
        public SnapshotManifest createSnapshot(
                ProtectedDataMigrationCommand.SnapshotCreateInvocation invocation) {
            return delegate.createSnapshot(invocation);
        }

        @Override
        public EncryptedMySqlSnapshotService.RestoreResult restoreSnapshot(
                ProtectedDataMigrationCommand.SnapshotRestoreInvocation invocation) {
            return delegate.restoreSnapshot(invocation);
        }

        @Override
        public String deleteSnapshot(
                ProtectedDataMigrationCommand.SnapshotDeleteInvocation invocation) {
            return delegate.deleteSnapshot(invocation);
        }

        @Override
        public void close() {
            try {
                resource.close();
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw unavailable();
            }
        }
    }
}
