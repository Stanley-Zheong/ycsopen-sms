package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ycsopen.sms.core.common.security.key.WrappedDataKey;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor.Purpose;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor.State;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.AnchorState;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.SignerAnchor;
import com.ycsopen.sms.core.common.security.migration.MigrationPreflightProperties.WriterIdentity;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.JdbcConfiguration;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.ManifestConfiguration;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.Pkcs11Configuration;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.ProductionConfiguration;
import com.ycsopen.sms.core.common.security.migration.ProductionMigrationCommandServicesFactory.SnapshotConfiguration;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess;
import com.ycsopen.sms.core.common.security.migration.snapshot.MySqlSnapshotProcess.Database;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotChunkStore;
import com.ycsopen.sms.core.common.security.migration.snapshot.SnapshotManifest;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.KeyPairGenerator;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionMigrationCommandServicesFactoryTest {

    @TempDir
    Path directory;
    private KeyPair configurationSigner;

    @BeforeEach
    void canonicalizeTemporaryRoot() throws Exception {
        directory = directory.toRealPath();
        configurationSigner = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    @Test
    void shippedLauncherDiscoversExactlyOneProductionFactory() {
        List<Class<? extends ProtectedDataMigrationLauncher.CommandServicesFactory>> providers =
                ServiceLoader.load(
                        ProtectedDataMigrationLauncher.CommandServicesFactory.class)
                .stream().map(ServiceLoader.Provider::type).toList();

        assertThat(providers).containsExactly(ProductionMigrationCommandServicesFactory.class);
    }

    @Test
    void discoveredFactoryDelegatesAllSnapshotMethodsThroughProductionComposition()
            throws Exception {
        ProductionMigrationCommandServicesFactory factory = ServiceLoader.load(
                        ProtectedDataMigrationLauncher.CommandServicesFactory.class)
                .stream().map(ServiceLoader.Provider::get)
                .map(ProductionMigrationCommandServicesFactory.class::cast)
                .findFirst().orElseThrow();
        Path storeRoot = Files.createDirectory(directory.resolve("reachability-store"));
        DriverManagerDataSource dataSource = reachabilityDataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        initializeReachabilitySchema(jdbc);
        String recoveryKey = "snapshot-db.v1";
        jdbc.update("INSERT INTO ycs_crypto_key_references "
                        + "(purpose,key_version,provider_id,provider_key_reference,key_state,"
                        + "wrap_operation_count,rotation_required,optimistic_version) "
                        + "VALUES ('SNAPSHOT_RECOVERY',1,'pkcs11',?,'ACTIVE',0,FALSE,0)",
                recoveryKey);
        ProductionConfiguration configuration = reachabilityConfiguration(
                storeRoot, recoveryKey);
        Database source = new Database(
                "127.0.0.1", 3306, "snapshot_user", "secret".toCharArray(),
                "source_schema");
        SunPkcs11KeyAdapter adapter = mock(SunPkcs11KeyAdapter.class);
        when(adapter.wrap(any(byte[].class), any(byte[].class), any()))
                .thenAnswer(invocation -> wrapped(
                        recoveryKey, invocation.getArgument(0)));
        when(adapter.unwrap(any(WrappedDataKey.class), any(byte[].class), any()))
                .thenAnswer(invocation -> Arrays.copyOf(
                        ((WrappedDataKey) invocation.getArgument(0)).wrappedDek(), 32));
        AtomicBoolean hsmClosed = new AtomicBoolean();
        FakeSnapshotProcess process = new FakeSnapshotProcess(
                "CREATE TABLE `reachability` (`id` bigint NOT NULL PRIMARY KEY);\n"
                        .getBytes(StandardCharsets.US_ASCII));
        AtomicReference<Path> processStoreRoot = new AtomicReference<>();
        String previousRoot = System.getProperty(
                ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY);
        System.setProperty(ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY,
                storeRoot.toString());
        String pairDigest = "4".repeat(64);
        try {
            ProtectedDataMigrationCommand.CommandServices managed = factory.compose(
                    configuration, dataSource, source,
                    (ignoredJdbc, ignoredTransactions, ignoredReferences) ->
                            new ProductionMigrationCommandServicesFactory.HsmRuntime(
                                    adapter, () -> hsmClosed.set(true)),
                    (canonicalRoot, ignoredConfiguration) -> {
                        processStoreRoot.set(canonicalRoot);
                        return process;
                    },
                    (ignoredJdbc, ignoredSource) -> (original, target) -> { });
            SnapshotManifest created = managed.createSnapshot(
                    new ProtectedDataMigrationCommand.SnapshotCreateInvocation(
                            "reachability-snapshot", "test", "1".repeat(64),
                            source.schema(), "2".repeat(64), 0, "signer-v1"));
            assertThat(created.recoveryKeyReference()).isEqualTo(recoveryKey);
            assertThat(new SnapshotChunkStore.FileStore(storeRoot)
                    .retainedManifest(created.snapshotId()).canonicalManifest())
                    .isEqualTo(created.canonicalBytes());

            jdbc.update("INSERT INTO ycs_crypto_manifest_pair_admission "
                            + "(singleton_id,migration_set_id,global_sequence,signer_key_version,"
                            + "snapshot_digest,pair_digest) VALUES (1,?,0,?,?,?)",
                    configuration.migrationSetId(), created.subject().signerKeyVersion(),
                    HexFormat.of().parseHex(created.digest()),
                    HexFormat.of().parseHex(pairDigest));
            var restored = managed.restoreSnapshot(
                    new ProtectedDataMigrationCommand.SnapshotRestoreInvocation(
                            created.snapshotId(), pairDigest, "restore_schema"));
            String deleted = managed.deleteSnapshot(
                    new ProtectedDataMigrationCommand.SnapshotDeleteInvocation(
                            created.snapshotId(), created.digest()));
            ((AutoCloseable) managed).close();

            assertThat(restored.targetSchema()).isEqualTo("restore_schema");
            assertThat(deleted).isEqualTo(created.snapshotId());
            assertThat(processStoreRoot).hasValue(storeRoot.toRealPath());
            assertThat(process.dumpStarts).isEqualTo(2);
            assertThat(process.restoreStarts).isOne();
            assertThat(process.closed).isTrue();
            assertThat(hsmClosed).isTrue();
            verify(adapter).wrap(any(byte[].class), any(byte[].class), any());
            verify(adapter, atLeastOnce()).unwrap(
                    any(WrappedDataKey.class), any(byte[].class), any());
            assertThatThrownBy(() -> new SnapshotChunkStore.FileStore(storeRoot)
                    .retainedManifest(created.snapshotId()))
                    .isInstanceOf(SnapshotManifest.SnapshotException.class);
        } finally {
            if (previousRoot == null) {
                System.clearProperty(
                        ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY);
            } else {
                System.setProperty(
                        ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY,
                        previousRoot);
            }
        }
    }

    @Test
    void productionCompositionFailureClosesAlreadyAcquiredHsmResource() throws Exception {
        ProductionMigrationCommandServicesFactory factory =
                new ProductionMigrationCommandServicesFactory();
        Path storeRoot = Files.createDirectory(directory.resolve("failed-composition-store"));
        ProductionConfiguration configuration = reachabilityConfiguration(
                storeRoot, "snapshot-db.v1");
        DriverManagerDataSource dataSource = reachabilityDataSource();
        SunPkcs11KeyAdapter adapter = mock(SunPkcs11KeyAdapter.class);
        AtomicBoolean hsmClosed = new AtomicBoolean();
        FakeSnapshotProcess process = new FakeSnapshotProcess(new byte[]{1});
        String previousRoot = System.getProperty(
                ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY);
        System.setProperty(ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY,
                storeRoot.toString());
        try {
            assertThatThrownBy(() -> factory.compose(
                    configuration, dataSource,
                    new Database("127.0.0.1", 3306, "snapshot_user",
                            "secret".toCharArray(), "source_schema"),
                    (jdbc, transactions, references) ->
                            new ProductionMigrationCommandServicesFactory.HsmRuntime(
                                    adapter, () -> hsmClosed.set(true)),
                    (root, snapshot) -> process,
                    (jdbc, source) -> {
                        throw SnapshotManifest.invalid();
                    }))
                    .isInstanceOf(SnapshotManifest.SnapshotException.class);
            assertThat(hsmClosed).isTrue();
            assertThat(process.closed).isTrue();
        } finally {
            if (previousRoot == null) {
                System.clearProperty(
                        ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY);
            } else {
                System.setProperty(
                        ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY,
                        previousRoot);
            }
        }
    }

    @Test
    void executableAuthorityFailurePrecedesHsmAcquisition() throws Exception {
        ProductionMigrationCommandServicesFactory factory =
                new ProductionMigrationCommandServicesFactory();
        Path storeRoot = Files.createDirectory(directory.resolve("authority-before-hsm-store"));
        ProductionConfiguration configuration = reachabilityConfiguration(
                storeRoot, "snapshot-db.v1");
        AtomicBoolean hsmOpened = new AtomicBoolean();
        String previousRoot = System.getProperty(
                ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY);
        System.setProperty(ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY,
                storeRoot.toString());
        try {
            assertThatThrownBy(() -> factory.compose(
                    configuration, reachabilityDataSource(),
                    new Database("127.0.0.1", 3306, "snapshot_user",
                            "secret".toCharArray(), "source_schema"),
                    (jdbc, transactions, references) -> {
                        hsmOpened.set(true);
                        throw new AssertionError("HSM must not open before executable authority");
                    },
                    (root, snapshot) -> {
                        throw SnapshotManifest.invalid();
                    },
                    (jdbc, source) -> (original, target) -> { }))
                    .isInstanceOf(SnapshotManifest.SnapshotException.class);
            assertThat(hsmOpened).isFalse();
        } finally {
            if (previousRoot == null) {
                System.clearProperty(
                        ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY);
            } else {
                System.setProperty(
                        ProductionMigrationCommandServicesFactory.SNAPSHOT_STORE_PROPERTY,
                        previousRoot);
            }
        }
    }

    @Test
    void threeArgumentLauncherReachesProviderAndSanitizesClosedConfigFailure() {
        String previous = System.getProperty(
                ProductionMigrationCommandServicesFactory.CONFIG_PROPERTY);
        Path absent = directory.resolve("absent.json").toAbsolutePath().normalize();
        System.setProperty(ProductionMigrationCommandServicesFactory.CONFIG_PROPERTY,
                absent.toString());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            int exit = ProtectedDataMigrationLauncher.run(
                    new String[]{"status", "--run-id",
                            "00000000-0000-4000-8000-000000000001"}, out, err);

            assertThat(exit).isEqualTo(26);
            assertThat(stdout.toString(StandardCharsets.UTF_8)).isEmpty();
            assertThat(stderr.toString(StandardCharsets.UTF_8))
                    .isEqualTo("phase03-migration:error:key_or_provider\n")
                    .doesNotContain(absent.toString());
        } finally {
            if (previous == null) {
                System.clearProperty(ProductionMigrationCommandServicesFactory.CONFIG_PROPERTY);
            } else {
                System.setProperty(
                        ProductionMigrationCommandServicesFactory.CONFIG_PROPERTY, previous);
            }
        }
    }

    @Test
    void productionConfigurationIsStrictBoundedAndContainsNoCredentialValue() throws Exception {
        ProductionConfiguration expected = configuration();
        Path valid = directory.resolve("migration.json");
        ObjectMapper json = new ObjectMapper();
        ObjectNode root = json.valueToTree(expected);
        root.withObject("pkcs11").withArray("keys")
                .forEach(key -> ((ObjectNode) key).remove("wrappingKey"));
        Files.write(valid, ProductionMigrationCommandServicesFactory.canonicalJsonBytes(root));

        ProductionConfiguration loaded =
                loadConfiguration(valid);

        assertThat(loaded).isEqualTo(expected);
        assertThat(Files.readString(valid)).contains("DB_PASSWORD_ENV", "PKCS11_PIN_ENV")
                .doesNotContain("secret-value");

        byte[] validBytes = Files.readAllBytes(valid);
        Path exactLimit = directory.resolve("exact-limit.json");
        byte[] exactLimitBytes = java.util.Arrays.copyOf(validBytes, 1_048_576);
        java.util.Arrays.fill(exactLimitBytes, validBytes.length, exactLimitBytes.length, (byte) ' ');
        Files.write(exactLimit, exactLimitBytes);
        assertThatThrownBy(() -> loadConfiguration(exactLimit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable");

        Path overLimit = directory.resolve("over-limit.json");
        Files.write(overLimit, java.util.Arrays.copyOf(exactLimitBytes, 1_048_577));
        assertThatThrownBy(() ->
                loadConfiguration(overLimit))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();

        Path symbolic = directory.resolve("symbolic.json");
        Files.createSymbolicLink(symbolic, valid.getFileName());
        assertThatThrownBy(() ->
                loadConfiguration(symbolic))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();

        Path unknown = directory.resolve("unknown.json");
        String invalid = Files.readString(valid).replaceFirst(
                "\\{", "{\"unexpected\":true,");
        Files.writeString(unknown, invalid);
        assertThatThrownBy(() ->
                loadConfiguration(unknown))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();

        Path duplicate = directory.resolve("duplicate.json");
        String duplicated = Files.readString(valid).replaceFirst(
                "\"schema\":", "\"schema\":\"phase03-migration-production/v1\",\"schema\":");
        Files.writeString(duplicate, duplicated);
        assertThatThrownBy(() ->
                loadConfiguration(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();
    }

    @Test
    void sameInodeSameLengthRewriteDuringDescriptorReadIsRejected() throws Exception {
        Path config = writeConfiguration(directory.resolve("same-inode.json"), configuration());
        byte[] original = Files.readAllBytes(config);
        byte[] changed = new String(original, StandardCharsets.UTF_8)
                .replace("phase03-plan14", "phase03-plan15")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(changed).hasSameSizeAs(original);
        FileTime originalTime = Files.getLastModifiedTime(config);

        assertThatThrownBy(() -> loadConfiguration(
                config, new ProductionMigrationCommandServicesFactory.ConfigurationReadObserver() {
                    @Override
                    public void afterFirstRead(Path ignored) {
                        try {
                            Files.write(config, changed);
                            Files.setLastModifiedTime(config, originalTime);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    }
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();
    }

    @Test
    void replacementRestoreAbaCannotSubstituteOpenedContent() throws Exception {
        Path config = writeConfiguration(directory.resolve("aba.json"), configuration());
        Path original = directory.resolve("aba-original.json");
        Path replacement = writeConfiguration(
                directory.resolve("aba-replacement.json"), configurationWithSet("phase03-plan15"));
        Path observedReplacement = directory.resolve("aba-observed.json");

        assertThatThrownBy(() -> loadConfiguration(
                config, new ProductionMigrationCommandServicesFactory.ConfigurationReadObserver() {
                    @Override
                    public void beforeFirstRead(Path ignored) {
                        try {
                            Files.move(config, original, StandardCopyOption.ATOMIC_MOVE);
                            Files.move(replacement, config, StandardCopyOption.ATOMIC_MOVE);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    }

                    @Override
                    public void afterFirstRead(Path ignored) {
                        try {
                            Files.move(config, observedReplacement, StandardCopyOption.ATOMIC_MOVE);
                            Files.move(original, config, StandardCopyOption.ATOMIC_MOVE);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    }
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();
    }

    @Test
    void parentSymlinkSwapAfterTrustedTraversalIsRejected() throws Exception {
        Path trusted = Files.createDirectory(directory.resolve("trusted"));
        Path movedTrusted = directory.resolve("trusted-opened");
        Path attacker = Files.createDirectory(directory.resolve("attacker"));
        Path config = writeConfiguration(trusted.resolve("migration.json"), configuration());
        writeConfiguration(attacker.resolve("migration.json"), configurationWithSet("phase03-plan15"));

        assertThatThrownBy(() -> loadConfiguration(
                config, new ProductionMigrationCommandServicesFactory.ConfigurationReadObserver() {
                    @Override
                    public void beforeFirstRead(Path ignored) {
                        try {
                            Files.move(trusted, movedTrusted, StandardCopyOption.ATOMIC_MOVE);
                            Files.createSymbolicLink(trusted, attacker.getFileName());
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    }
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();
    }

    @Test
    void signedBytesRejectCoordinatedPreBaselineSameInodeRewriteWithRestoredTimestamps()
            throws Exception {
        Path config = writeConfiguration(directory.resolve("pre-baseline.json"), configuration());
        byte[] original = Files.readAllBytes(config);
        byte[] changed = new String(original, StandardCharsets.UTF_8)
                .replace("phase03-plan14", "phase03-plan15")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(changed).hasSameSizeAs(original);
        FileTime originalTime = Files.getLastModifiedTime(config);

        assertThatThrownBy(() -> loadConfiguration(
                config, new ProductionMigrationCommandServicesFactory.ConfigurationReadObserver() {
                    @Override
                    public void beforeBaselineRead(Path ignored) {
                        try {
                            Files.write(config, changed);
                            Files.setLastModifiedTime(config, originalTime);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    }

                    @Override
                    public void afterStableReads(Path ignored) {
                        try {
                            Files.write(config, original);
                            Files.setLastModifiedTime(config, originalTime);
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    }
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();
    }

    @Test
    void badSignatureWrongPinnedKeyAndMissingIntegrityInputsFailClosed() throws Exception {
        Path config = writeConfiguration(directory.resolve("integrity.json"), configuration());
        Path signature = sign(config, configurationSigner);
        byte[] bad = Files.readAllBytes(signature);
        bad[0] = bad[0] == 'A' ? (byte) 'B' : (byte) 'A';
        Files.write(signature, bad);
        assertThatThrownBy(() -> ProductionMigrationCommandServicesFactory.loadConfiguration(
                config, signature, publicKey(configurationSigner),
                ProductionMigrationCommandServicesFactory.ConfigurationReadObserver.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable");

        sign(config, configurationSigner);
        assertThatThrownBy(() -> ProductionMigrationCommandServicesFactory.loadConfiguration(
                directory.resolve("absent-config.json"), signature,
                publicKey(configurationSigner),
                ProductionMigrationCommandServicesFactory.ConfigurationReadObserver.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable");
        KeyPair wrong = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThatThrownBy(() -> ProductionMigrationCommandServicesFactory.loadConfiguration(
                config, signature, publicKey(wrong),
                ProductionMigrationCommandServicesFactory.ConfigurationReadObserver.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable");
        assertThatThrownBy(() -> ProductionMigrationCommandServicesFactory.loadConfiguration(
                config, directory.resolve("absent.sig"), publicKey(configurationSigner),
                ProductionMigrationCommandServicesFactory.ConfigurationReadObserver.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable");
        assertThatThrownBy(() -> ProductionMigrationCommandServicesFactory.loadConfiguration(
                config, signature, "",
                ProductionMigrationCommandServicesFactory.ConfigurationReadObserver.NONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable");
    }

    private ProductionConfiguration loadConfiguration(Path path) throws Exception {
        return loadConfiguration(path,
                ProductionMigrationCommandServicesFactory.ConfigurationReadObserver.NONE);
    }

    private ProductionConfiguration loadConfiguration(
            Path path, ProductionMigrationCommandServicesFactory.ConfigurationReadObserver observer)
            throws Exception {
        Path signature = sign(path, configurationSigner);
        return ProductionMigrationCommandServicesFactory.loadConfiguration(
                path, signature, publicKey(configurationSigner), observer);
    }

    private static Path sign(Path path, KeyPair signer) throws Exception {
        Signature ed25519 = Signature.getInstance("Ed25519");
        ed25519.initSign(signer.getPrivate());
        byte[] configuration = Files.readAllBytes(path);
        ed25519.update("YCS-PHASE03-PRODUCTION-CONFIG-SIGNATURE/v1\0"
                .getBytes(StandardCharsets.US_ASCII));
        ed25519.update(ByteBuffer.allocate(Integer.BYTES).putInt(configuration.length).array());
        ed25519.update(configuration);
        Path signature = path.resolveSibling(path.getFileName() + ".sig");
        Files.write(signature, Base64.getEncoder().encode(ed25519.sign()));
        return signature;
    }

    private static String publicKey(KeyPair signer) {
        return Base64.getEncoder().encodeToString(signer.getPublic().getEncoded());
    }

    private static Path writeConfiguration(Path path, ProductionConfiguration configuration)
            throws Exception {
        ObjectMapper json = new ObjectMapper();
        ObjectNode root = json.valueToTree(configuration);
        root.withObject("pkcs11").withArray("keys")
                .forEach(key -> ((ObjectNode) key).remove("wrappingKey"));
        Files.write(path, ProductionMigrationCommandServicesFactory.canonicalJsonBytes(root));
        return path;
    }

    private ProductionConfiguration configurationWithSet(String migrationSetId)
            throws Exception {
        ProductionConfiguration base = configuration();
        return new ProductionConfiguration(base.schema(), migrationSetId, base.jdbc(),
                base.manifest(), base.pkcs11(), base.snapshot(), base.signerAnchors(),
                base.compatibleWriters(), base.recoveryKeyReferences());
    }

    private ProductionConfiguration configuration() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] publicKey = keyPair.getPublic().getEncoded();
        SignerAnchor signer = new SignerAnchor(
                "signer-v1", AnchorState.ACTIVE,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKey)),
                Base64.getEncoder().encodeToString(publicKey), null);
        return new ProductionConfiguration(
                "phase03-migration-production/v1", "phase03-plan14",
                new JdbcConfiguration(
                        "jdbc:mysql://127.0.0.1:3306/ycsopen_sms", "migration_user",
                        "DB_PASSWORD_ENV"),
                new ManifestConfiguration("/opt/ycsopen/protected-data-inventory.json",
                        "sha256:" + "a".repeat(64)),
                new Pkcs11Configuration(
                        "/usr/lib/softhsm/libsofthsm2.so",
                        List.of("/usr/lib/softhsm/libsofthsm2.so"), 1,
                        "production-token", "PKCS11_PIN_ENV",
                        descriptors()),
                new SnapshotConfiguration(
                        directory.toString(), "/usr/bin/mysqldump", "sha256:" + "c".repeat(64),
                        "/usr/bin/mysql", "sha256:" + "d".repeat(64)),
                List.of(signer),
                Set.of(new WriterIdentity(
                        "ycsopen-sms-core", "1.0.0", "b".repeat(64))),
                Set.of("snapshot-recovery.v1"));
    }

    private static List<Pkcs11KeyDescriptor> descriptors() {
        return List.of(
                descriptor(Purpose.FIELD_ENCRYPTION_KEK, "field-kek.v1", "field-kek"),
                descriptor(Purpose.SNAPSHOT_RECOVERY, "snapshot-recovery.v1", "snapshot"),
                descriptor(Purpose.MOBILE_BLIND_INDEX, "mobile-index.v1", "mobile-index"),
                descriptor(Purpose.OBJECT_CAPABILITY_DIGEST, "object-digest.v1", "object-index"),
                descriptor(Purpose.REGISTRATION_UPLOAD_DIGEST,
                        "registration-digest.v1", "registration-index"));
    }

    private static DriverManagerDataSource reachabilityDataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:production-snapshot-reachability-" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa", "");
    }

    private static void initializeReachabilitySchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE ALIAS HEX AS "
                + "'String hex(byte[] bytes) { return "
                + "java.util.HexFormat.of().formatHex(bytes); }'");
        jdbc.execute("CREATE ALIAS UNHEX AS "
                + "'byte[] unhex(String value) { return "
                + "java.util.HexFormat.of().parseHex(value); }'");
        jdbc.execute("CREATE TABLE ycs_crypto_manifest_pair_admission ("
                + "singleton_id INT PRIMARY KEY,"
                + "migration_set_id VARCHAR(64) NOT NULL,"
                + "global_sequence BIGINT NOT NULL,"
                + "signer_key_version VARCHAR(64) NOT NULL,"
                + "snapshot_digest BINARY(32) NOT NULL,"
                + "pair_digest BINARY(32) NOT NULL)");
        jdbc.execute("CREATE TABLE ycs_crypto_key_references ("
                + "purpose VARCHAR(64) NOT NULL,"
                + "key_version BIGINT NOT NULL,"
                + "provider_id VARCHAR(64) NOT NULL,"
                + "provider_key_reference VARCHAR(64) NOT NULL,"
                + "key_state VARCHAR(32) NOT NULL,"
                + "wrap_operation_count BIGINT NOT NULL,"
                + "rotation_required BOOLEAN NOT NULL,"
                + "optimistic_version BIGINT NOT NULL,"
                + "PRIMARY KEY (purpose,key_version))");
    }

    private ProductionConfiguration reachabilityConfiguration(
            Path storeRoot, String recoveryKey) throws Exception {
        ProductionConfiguration base = configuration();
        Path inventory = Path.of(Objects.requireNonNull(getClass().getResource(
                "/security/protected-data-inventory.json")).toURI()).toRealPath();
        byte[] inventoryBytes = Files.readAllBytes(inventory);
        try {
            return new ProductionConfiguration(
                    base.schema(), "reachability-set", base.jdbc(),
                    new ManifestConfiguration(inventory.toString(),
                            ProtectedDataManifest.canonicalDigest(inventoryBytes)),
                    base.pkcs11(),
                    new SnapshotConfiguration(
                            storeRoot.toString(), "/unused/mysqldump", "sha256:" + "c".repeat(64),
                            "/unused/mysql", "sha256:" + "d".repeat(64)),
                    base.signerAnchors(), base.compatibleWriters(), Set.of(recoveryKey));
        } finally {
            Arrays.fill(inventoryBytes, (byte) 0);
        }
    }

    private static WrappedDataKey wrapped(String recoveryKey, byte[] dataEncryptionKey) {
        byte[] wrapped = new byte[WrappedDataKey.WRAPPED_DEK_BYTES];
        System.arraycopy(dataEncryptionKey, 0, wrapped, 0, dataEncryptionKey.length);
        return new WrappedDataKey(
                recoveryKey, new byte[WrappedDataKey.WRAP_NONCE_BYTES], wrapped);
    }

    private static final class FakeSnapshotProcess
            implements MySqlSnapshotProcess, AutoCloseable {
        private final byte[] sourceDump;
        private final ByteArrayOutputStream restored = new ByteArrayOutputStream();
        private int dumpStarts;
        private int restoreStarts;
        private boolean closed;

        private FakeSnapshotProcess(byte[] sourceDump) {
            this.sourceDump = sourceDump.clone();
        }

        @Override
        public DumpSession startDump(Database source) {
            dumpStarts++;
            byte[] bytes = "source_schema".equals(source.schema())
                    ? sourceDump.clone() : restored.toByteArray();
            return new DumpSession() {
                @Override
                public InputStream stdout() {
                    return new ByteArrayInputStream(bytes);
                }

                @Override
                public void awaitSuccess() {
                }

                @Override
                public void close() {
                    Arrays.fill(bytes, (byte) 0);
                }
            };
        }

        @Override
        public RestoreSession startRestore(Database target) {
            restoreStarts++;
            restored.reset();
            return new RestoreSession() {
                @Override
                public OutputStream stdin() {
                    return restored;
                }

                @Override
                public void awaitSuccess() {
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void close() {
            closed = true;
            Arrays.fill(sourceDump, (byte) 0);
            restored.reset();
        }
    }

    private static Pkcs11KeyDescriptor descriptor(
            Purpose purpose, String reference, String alias) {
        return new Pkcs11KeyDescriptor(
                purpose, 1, reference, alias, State.ACTIVE,
                purpose.isWrappingKey() ? "AES" : "HmacSHA256", 256);
    }
}
