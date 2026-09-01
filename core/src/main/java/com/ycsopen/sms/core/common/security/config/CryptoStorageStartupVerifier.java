package com.ycsopen.sms.core.common.security.config;

import com.ycsopen.sms.core.common.security.key.KeyHealth;
import com.ycsopen.sms.core.common.security.key.pkcs11.KekWrapUsageRepository;
import com.ycsopen.sms.core.common.security.key.pkcs11.Pkcs11KeyDescriptor;
import com.ycsopen.sms.core.common.security.key.pkcs11.SunPkcs11KeyAdapter;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Fail-closed startup proof for the production crypto-storage boundary. */
public final class CryptoStorageStartupVerifier implements SmartInitializingSingleton {

    public static final String PROVIDER_ID = "pkcs11";
    public static final String ADAPTER_ID = "SUN_PKCS11";
    public static final Set<String> REQUIRED_MECHANISMS = Set.of("CKM_AES_GCM", "CKM_SHA256_HMAC");
    public static final Set<String> REQUIRED_ATTRIBUTES =
            Set.of("CKA_TOKEN", "CKA_PRIVATE", "CKA_SENSITIVE", "CKA_NOT_EXTRACTABLE");

    public static final String FIELD_KEK_ALIAS = "ycs.field-encryption-kek.v1";
    public static final String MOBILE_INDEX_ALIAS = "ycs.mobile-blind-index.v1";
    public static final String OBJECT_DIGEST_ALIAS = "ycs.object-capability-digest.v1";
    public static final String REGISTRATION_DIGEST_ALIAS = "ycs.registration-upload-digest.v1";

    private static final Set<String> TEST_PROFILES = Set.of("test", "phase01-integration");

    private final Settings settings;
    private final Object adapter;
    private final Set<String> activeProfiles;

    public CryptoStorageStartupVerifier(Settings settings, Object adapter, Set<String> activeProfiles) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.activeProfiles = activeProfiles == null ? Set.of() : Set.copyOf(activeProfiles);
    }

    @Override
    public void afterSingletonsInstantiated() {
        verify();
    }

    public void verify() {
        settings.validate();
        if (!settings.enabled()) {
            if (!(adapter instanceof DisabledCryptoStorageAdapter)) {
                throw invalid("adapter");
            }
            return;
        }

        boolean testProfile = activeProfiles.stream()
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(TEST_PROFILES::contains);
        if (!testProfile && adapter.getClass() != SunPkcs11KeyAdapter.class) {
            throw invalid("adapter");
        }
        if (adapter instanceof SunPkcs11KeyAdapter productionAdapter
                && productionAdapter.health().status() == KeyHealth.Status.UNAVAILABLE) {
            throw invalid("provider-preflight");
        }
    }

    public record Settings(boolean enabled,
                           String adapterId,
                           String providerId,
                           Path modulePath,
                           List<Path> allowedModulePaths,
                           long slotId,
                           String tokenIdentity,
                           CredentialSource credentialSource,
                           String credentialReference,
                           Set<String> mechanisms,
                           Set<String> keyAttributes,
                           long rotationRequiredAt,
                           long hardCeiling,
                           String fieldKekAlias,
                           String mobileIndexAlias,
                           String objectDigestAlias,
                           String registrationDigestAlias) {

        public Settings {
            allowedModulePaths = allowedModulePaths == null ? List.of() : List.copyOf(allowedModulePaths);
            mechanisms = mechanisms == null ? Set.of() : Set.copyOf(mechanisms);
            keyAttributes = keyAttributes == null ? Set.of() : Set.copyOf(keyAttributes);
        }

        public void validate() {
            if (!enabled) {
                return;
            }
            require(ADAPTER_ID.equals(adapterId), "adapter");
            require(PROVIDER_ID.equals(providerId), "provider-id");
            require(modulePath != null, "module-path");
            require(!allowedModulePaths.isEmpty(), "allowed-module-paths");
            require(slotId >= 0, "slot-id");
            require(tokenIdentity != null
                    && tokenIdentity.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"), "token-identity");
            require(credentialSource != null, "credential-source");
            require(credentialReference != null
                    && credentialReference.matches("[A-Z][A-Z0-9_]{0,127}"), "credential-reference");
            require(REQUIRED_MECHANISMS.equals(mechanisms), "mechanisms");
            require(REQUIRED_ATTRIBUTES.equals(keyAttributes), "key-attributes");
            require(rotationRequiredAt == KekWrapUsageRepository.ROTATION_REQUIRED_AT,
                    "rotation-required-at");
            require(hardCeiling == KekWrapUsageRepository.HARD_CEILING, "hard-ceiling");
            require(FIELD_KEK_ALIAS.equals(fieldKekAlias), "aliases.field-encryption-kek");
            require(MOBILE_INDEX_ALIAS.equals(mobileIndexAlias), "aliases.mobile-blind-index");
            require(OBJECT_DIGEST_ALIAS.equals(objectDigestAlias), "aliases.object-capability-digest");
            require(REGISTRATION_DIGEST_ALIAS.equals(registrationDigestAlias),
                    "aliases.registration-upload-digest");
            require(Set.of(fieldKekAlias, mobileIndexAlias, objectDigestAlias,
                    registrationDigestAlias).size() == 4, "aliases");
        }

        public List<Pkcs11KeyDescriptor> descriptors() {
            validate();
            return List.of(
                    descriptor(Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK,
                            "field-kek.v1", fieldKekAlias),
                    descriptor(Pkcs11KeyDescriptor.Purpose.MOBILE_BLIND_INDEX,
                            "mobile-index.v1", mobileIndexAlias),
                    descriptor(Pkcs11KeyDescriptor.Purpose.OBJECT_CAPABILITY_DIGEST,
                            "object-digest.v1", objectDigestAlias),
                    descriptor(Pkcs11KeyDescriptor.Purpose.REGISTRATION_UPLOAD_DIGEST,
                            "registration-digest.v1", registrationDigestAlias));
        }

        private static Pkcs11KeyDescriptor descriptor(Pkcs11KeyDescriptor.Purpose purpose,
                                                       String reference,
                                                       String alias) {
            return new Pkcs11KeyDescriptor(purpose, 1, reference, alias,
                    Pkcs11KeyDescriptor.State.ACTIVE,
                    purpose == Pkcs11KeyDescriptor.Purpose.FIELD_ENCRYPTION_KEK
                            ? "AES" : "HmacSHA256",
                    256);
        }

        private static void require(boolean condition, String property) {
            if (!condition) {
                throw invalid(property);
            }
        }
    }

    public enum CredentialSource {
        ENVIRONMENT
    }

    public static Supplier<char[]> environmentCredential(String reference) {
        if (reference == null || !reference.matches("[A-Z][A-Z0-9_]{0,127}")) {
            throw invalid("credential-reference");
        }
        return () -> {
            String value = System.getenv(reference);
            if (value == null) {
                throw invalid("credential-source");
            }
            return value.toCharArray();
        };
    }

    static IllegalStateException invalid(String property) {
        return new IllegalStateException("CRYPTO_STORAGE_CONFIGURATION property=" + property);
    }
}
