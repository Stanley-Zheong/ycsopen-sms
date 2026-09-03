package com.ycsopen.sms.core.common.security.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionMigrationCommandServicesFactoryTest {

    @TempDir
    Path directory;

    @Test
    void shippedLauncherDiscoversExactlyOneProductionFactory() {
        List<Class<? extends ProtectedDataMigrationLauncher.CommandServicesFactory>> providers =
                ServiceLoader.load(
                        ProtectedDataMigrationLauncher.CommandServicesFactory.class)
                .stream().map(ServiceLoader.Provider::type).toList();

        assertThat(providers).containsExactly(ProductionMigrationCommandServicesFactory.class);
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
        Files.write(valid, json.writeValueAsBytes(root));

        ProductionConfiguration loaded =
                ProductionMigrationCommandServicesFactory.loadConfiguration(valid);

        assertThat(loaded).isEqualTo(expected);
        assertThat(Files.readString(valid)).contains("DB_PASSWORD_ENV", "PKCS11_PIN_ENV")
                .doesNotContain("secret-value");

        Path unknown = directory.resolve("unknown.json");
        String invalid = Files.readString(valid).replaceFirst(
                "\\{", "{\"unexpected\":true,");
        Files.writeString(unknown, invalid);
        assertThatThrownBy(() ->
                ProductionMigrationCommandServicesFactory.loadConfiguration(unknown))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();

        Path duplicate = directory.resolve("duplicate.json");
        String duplicated = Files.readString(valid).replaceFirst(
                "\"schema\":", "\"schema\":\"phase03-migration-production/v1\",\"schema\":");
        Files.writeString(duplicate, duplicated);
        assertThatThrownBy(() ->
                ProductionMigrationCommandServicesFactory.loadConfiguration(duplicate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("migration configuration is unavailable")
                .hasNoCause();
    }

    private static ProductionConfiguration configuration() throws Exception {
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

    private static Pkcs11KeyDescriptor descriptor(
            Purpose purpose, String reference, String alias) {
        return new Pkcs11KeyDescriptor(
                purpose, 1, reference, alias, State.ACTIVE,
                purpose.isWrappingKey() ? "AES" : "HmacSHA256", 256);
    }
}
