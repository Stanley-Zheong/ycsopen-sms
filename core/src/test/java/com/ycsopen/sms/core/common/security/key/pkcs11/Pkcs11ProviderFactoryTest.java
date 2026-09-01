package com.ycsopen.sms.core.common.security.key.pkcs11;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.SecretKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Provider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pkcs11ProviderFactoryTest {

    private static final char[] TEST_PIN = "unit-pin-only".toCharArray();
    private static final String UNIT_EVIDENCE_LABEL = "unit-mapping-only-not-pkcs11-evidence";

    @TempDir
    Path temporaryDirectory;

    @Test
    void configuresTheExactCanonicalModuleAndSlotWithoutWritingTokenOrPin() throws Exception {
        Path module = Files.writeString(temporaryDirectory.resolve("libunit-pkcs11.so"), "unit");
        AtomicReference<String> configText = new AtomicReference<>();
        AtomicReference<Path> configPath = new AtomicReference<>();
        Pkcs11FailureMapper mapper = mapper();
        Pkcs11ProviderFactory factory = new Pkcs11ProviderFactory(
                (config, expectedName) -> {
                    configPath.set(config);
                    configText.set(Files.readString(config));
                    return provider(expectedName);
                }, this::validTokenInventory, mapper);

        Pkcs11ProviderFactory.Session session = factory.open(properties(module, descriptors()));

        assertThat(session.tokenIdentityHash()).matches("[a-f0-9]{64}");
        assertThat(configText.get())
                .contains("library=" + properties(module, descriptors()).canonicalModulePath(),
                        "slot=4198401")
                .contains("enabledMechanisms={ 0x7FFFFF21 CKM_AES_KEY_GEN CKM_AES_GCM "
                        + "CKM_GENERIC_SECRET_KEY_GEN CKM_SHA256_HMAC }")
                .doesNotContain("unit-token-exact", "unit-pin-only");
        assertThat(configPath.get()).doesNotExist();
        assertThat(UNIT_EVIDENCE_LABEL).contains("not-pkcs11-evidence");
    }

    @Test
    void normalizesJava21P11KeyStoreByteLengthAndRejectsInvalidValues() {
        assertThat(Pkcs11ProviderFactory.normalizeP11SecretKeyBits("32")).isEqualTo(256);
        assertThatThrownBy(() -> Pkcs11ProviderFactory.normalizeP11SecretKeyBits("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid token key length");
        assertThatThrownBy(() -> Pkcs11ProviderFactory.normalizeP11SecretKeyBits("268435456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid token key length");
    }

    @Test
    void rejectsNonAllowlistedDuplicateAndCrossPurposeAliasesBeforeProviderUse() throws Exception {
        Path allowed = Files.writeString(temporaryDirectory.resolve("allowed.so"), "unit");
        Path rejected = Files.writeString(temporaryDirectory.resolve("rejected.so"), "unit");

        assertThatThrownBy(() -> properties(rejected, List.of(allowed), descriptors()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("PKCS11 module is not allowlisted");

        List<Pkcs11KeyDescriptor> duplicate = List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                        "unit-kek-v1", "shared-alias", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 1,
                        "mobile-v1", "SHARED-ALIAS", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                        "capability-v1", "capability-v1", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                        "upload-v1", "upload-v1", Pkcs11KeyDescriptor.State.ACTIVE));
        assertThatThrownBy(() -> properties(allowed, duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate PKCS11 key identity");
    }

    @Test
    void failsClosedForMissingWrongSizeExtractableAndUnexpectedProvider() throws Exception {
        Path module = Files.writeString(temporaryDirectory.resolve("strict.so"), "unit");
        Pkcs11CryptoStorageProperties properties = properties(module, descriptors());

        assertSanitizedFailure(properties, keys -> keys.remove("unit-mobile-v1"));
        assertSanitizedFailure(properties, keys -> keys.computeIfPresent("unit-kek-v1",
                (alias, key) -> new Pkcs11ProviderFactory.TokenKey(
                        key.handle(), "AES", 128, true, true, false)));
        assertSanitizedFailure(properties, keys -> keys.computeIfPresent("unit-capability-v1",
                (alias, key) -> new Pkcs11ProviderFactory.TokenKey(
                        key.handle(), "Generic Secret", 256, true, true, true)));

        Pkcs11ProviderFactory wrongProvider = new Pkcs11ProviderFactory(
                (config, expectedName) -> provider("SunPKCS11-wrong-token"),
                this::validTokenInventory, mapper());
        assertThatThrownBy(() -> wrongProvider.open(properties))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_CONFIGURATION")
                .hasMessageNotContaining("wrong-token")
                .hasMessageNotContaining(module.toString())
                .hasMessageNotContaining("unit-token-exact");
    }

    private void assertSanitizedFailure(Pkcs11CryptoStorageProperties properties,
                                        java.util.function.Consumer<Map<String, Pkcs11ProviderFactory.TokenKey>> mutation) {
        Pkcs11ProviderFactory factory = new Pkcs11ProviderFactory(
                (config, expectedName) -> provider(expectedName),
                (provider, pin, descriptors) -> {
                    Map<String, Pkcs11ProviderFactory.TokenKey> keys = validTokenInventory(
                            provider, pin, descriptors);
                    mutation.accept(keys);
                    return keys;
                }, mapper());
        assertThatThrownBy(() -> factory.open(properties))
                .isInstanceOf(Pkcs11FailureMapper.Pkcs11OperationException.class)
                .hasMessageContaining("PKCS11_CONFIGURATION")
                .hasMessageNotContaining("unit-pin-only")
                .hasMessageNotContaining("unit-token-exact")
                .hasMessageNotContaining("unit-kek-v1")
                .hasMessageNotContaining(properties.canonicalModulePath().toString());
    }

    private Map<String, Pkcs11ProviderFactory.TokenKey> validTokenInventory(
            Provider provider, char[] pin, List<Pkcs11KeyDescriptor> descriptors) {
        assertThat(pin).containsExactly(TEST_PIN);
        Map<String, Pkcs11ProviderFactory.TokenKey> keys = new HashMap<>();
        for (Pkcs11KeyDescriptor descriptor : descriptors) {
            String algorithm = descriptor.purpose() == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK
                    ? "AES" : "Generic Secret";
            keys.put(descriptor.alias(), new Pkcs11ProviderFactory.TokenKey(
                    new OpaqueUnitKey(algorithm), algorithm, 256, true, true, false));
        }
        return keys;
    }

    private Pkcs11CryptoStorageProperties properties(Path module,
                                                      List<Pkcs11KeyDescriptor> descriptors) {
        return properties(module, List.of(module), descriptors);
    }

    private Pkcs11CryptoStorageProperties properties(Path module,
                                                      List<Path> allowlist,
                                                      List<Pkcs11KeyDescriptor> descriptors) {
        return new Pkcs11CryptoStorageProperties(module, allowlist, 4_198_401L,
                "unit-token-exact", () -> TEST_PIN.clone(), descriptors);
    }

    private static List<Pkcs11KeyDescriptor> descriptors() {
        return List.of(
                descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK, 1,
                        "unit-kek-v1", "unit-kek-v1", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX, 1,
                        "mobile-v1", "unit-mobile-v1", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST, 1,
                        "capability-v1", "unit-capability-v1", Pkcs11KeyDescriptor.State.ACTIVE),
                descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST, 1,
                        "upload-v1", "unit-upload-v1", Pkcs11KeyDescriptor.State.ACTIVE));
    }

    private static Pkcs11KeyDescriptor descriptor(Pkcs11KeyDescriptor.Purpose purpose,
                                                   long version,
                                                   String reference,
                                                   String alias,
                                                   Pkcs11KeyDescriptor.State state) {
        return new Pkcs11KeyDescriptor(purpose, version, reference, alias, state,
                purpose == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK ? "AES" : "HmacSHA256",
                256);
    }

    private static Provider provider(String name) {
        return new Provider(name, "1.0", "unit provider") {
            private static final long serialVersionUID = 1L;
        };
    }

    private static Pkcs11FailureMapper mapper() {
        return new Pkcs11FailureMapper(() -> "0123456789abcdef0123456789abcdef");
    }

    private record OpaqueUnitKey(String algorithm) implements SecretKey {
        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            throw new AssertionError("production code must not export token keys");
        }
    }
}
