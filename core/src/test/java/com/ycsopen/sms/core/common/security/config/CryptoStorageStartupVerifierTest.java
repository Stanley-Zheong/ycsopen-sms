package com.ycsopen.sms.core.common.security.config;

import com.ycsopen.sms.core.common.security.key.KeyHealth;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoStorageStartupVerifierTest {

    @Test
    void disabledRuntimeDeniesEveryProtectedOperation() {
        DisabledCryptoStorageAdapter adapter = new DisabledCryptoStorageAdapter();
        new CryptoStorageStartupVerifier(disabledSettings(), adapter, Set.of()).verify();

        assertThat(adapter.health().status()).isEqualTo(KeyHealth.Status.UNAVAILABLE);
        assertThat(adapter.verify(null, null, null, null)).isFalse();
        assertThatThrownBy(() -> adapter.wrap(new byte[32], new byte[26], null))
                .hasMessage("CRYPTO_STORAGE_CONFIGURATION property=enabled");
        assertThatThrownBy(() -> adapter.writeIndexes("13800138000", null))
                .hasMessage("CRYPTO_STORAGE_CONFIGURATION property=enabled");
        assertThatThrownBy(() -> adapter.issue(null, null, new byte[32]))
                .hasMessage("CRYPTO_STORAGE_CONFIGURATION property=enabled");
    }

    @Test
    void enabledReferenceConfigurationIsCanonicalAndContainsNoKeyMaterial() {
        CryptoStorageStartupVerifier.Settings settings = enabledSettings();

        settings.validate();

        assertThat(settings.descriptors()).hasSize(5)
                .allSatisfy(descriptor -> {
                    assertThat(descriptor.keyBits()).isEqualTo(256);
                    assertThat(descriptor.hashedIdentity()).matches("[a-f0-9]{64}");
                    assertThat(descriptor.toString()).doesNotContain(descriptor.alias());
                });
        assertThat(settings.toString()).doesNotContain("secret", "password", "key-base64");
    }

    @Test
    void incompleteEnabledConfigurationFailsWithPropertyNameOnly() {
        CryptoStorageStartupVerifier.Settings invalid = copy(enabledSettings(),
                null, null, null, null, -1, null, null, null,
                Set.of(), Set.of(), -1, -1,
                null, null, null, null, null, null);

        assertThatThrownBy(invalid::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CRYPTO_STORAGE_CONFIGURATION property=adapter");
    }

    @Test
    void adapterProviderMechanismAttributeAliasAndCeilingDriftFailClosed() {
        assertInvalid(copy(enabledSettings(), "DIRECT_KEY", "pkcs11", Path.of("/module"),
                List.of(Path.of("/module")), 1, "token", CryptoStorageStartupVerifier.CredentialSource.ENVIRONMENT,
                "YCSOPEN_PKCS11_PIN", CryptoStorageStartupVerifier.REQUIRED_MECHANISMS,
                CryptoStorageStartupVerifier.REQUIRED_ATTRIBUTES, 983040, 1048576,
                CryptoStorageStartupVerifier.FIELD_KEK_ALIAS,
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_ALIAS,
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_REFERENCE,
                CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS,
                CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS,
                CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS), "adapter");
        assertInvalid(withProvider(enabledSettings(), "in-memory"), "provider-id");
        assertInvalid(withMechanisms(enabledSettings(), Set.of("CKM_AES_GCM")), "mechanisms");
        assertInvalid(withAttributes(enabledSettings(), Set.of("CKA_TOKEN")), "key-attributes");
        assertInvalid(withAliases(enabledSettings(), "unexpected", CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS,
                CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS,
                CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS),
                "aliases.field-encryption-kek");
        assertInvalid(withSnapshotAlias(enabledSettings(), "unexpected"),
                "aliases.snapshot-recovery");
        assertInvalid(withSnapshotReference(enabledSettings(), "field-kek.v1"),
                "references.snapshot-recovery");
        assertInvalid(withCeiling(enabledSettings(), 983039, 1048576), "rotation-required-at");
        assertInvalid(withCeiling(enabledSettings(), 983040, 1048577), "hard-ceiling");
    }

    @Test
    void nonTestStartupRejectsEveryAdapterOtherThanExactProductionClass() {
        assertThatThrownBy(() -> new CryptoStorageStartupVerifier(
                enabledSettings(), new Object(), Set.of("production")).verify())
                .hasMessage("CRYPTO_STORAGE_CONFIGURATION property=adapter");
    }

    @Test
    void directOrInMemoryIdentityIsAllowedOnlyInsideExplicitTestProfile() {
        new CryptoStorageStartupVerifier(enabledSettings(), new Object(), Set.of("test")).verify();

        assertThatThrownBy(() -> new CryptoStorageStartupVerifier(
                enabledSettings(), new Object(), Set.of("phase03-integration")).verify())
                .hasMessage("CRYPTO_STORAGE_CONFIGURATION property=adapter");
    }

    private static CryptoStorageStartupVerifier.Settings enabledSettings() {
        return new CryptoStorageStartupVerifier.Settings(true,
                CryptoStorageStartupVerifier.ADAPTER_ID, CryptoStorageStartupVerifier.PROVIDER_ID,
                Path.of("/module"), List.of(Path.of("/module")), 1, "token",
                CryptoStorageStartupVerifier.CredentialSource.ENVIRONMENT, "YCSOPEN_PKCS11_PIN",
                CryptoStorageStartupVerifier.REQUIRED_MECHANISMS,
                CryptoStorageStartupVerifier.REQUIRED_ATTRIBUTES, 983040, 1048576,
                CryptoStorageStartupVerifier.FIELD_KEK_ALIAS,
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_ALIAS,
                CryptoStorageStartupVerifier.SNAPSHOT_RECOVERY_REFERENCE,
                CryptoStorageStartupVerifier.MOBILE_INDEX_ALIAS,
                CryptoStorageStartupVerifier.OBJECT_DIGEST_ALIAS,
                CryptoStorageStartupVerifier.REGISTRATION_DIGEST_ALIAS);
    }

    private static CryptoStorageStartupVerifier.Settings disabledSettings() {
        return new CryptoStorageStartupVerifier.Settings(false, null, null, null, List.of(), -1,
                null, null, null, Set.of(), Set.of(), -1, -1,
                null, null, null, null, null, null);
    }

    private static CryptoStorageStartupVerifier.Settings copy(
            CryptoStorageStartupVerifier.Settings source,
            String adapter, String provider, Path module, List<Path> allowlist, long slot,
            String token, CryptoStorageStartupVerifier.CredentialSource credentialSource,
            String credentialReference, Set<String> mechanisms, Set<String> attributes,
            long rotation, long ceiling, String fieldAlias, String snapshotAlias,
            String snapshotReference, String mobileAlias,
            String objectAlias, String registrationAlias) {
        return new CryptoStorageStartupVerifier.Settings(source.enabled(), adapter, provider, module,
                allowlist, slot, token, credentialSource, credentialReference, mechanisms, attributes,
                rotation, ceiling, fieldAlias, snapshotAlias, snapshotReference,
                mobileAlias, objectAlias, registrationAlias);
    }

    private static CryptoStorageStartupVerifier.Settings withProvider(
            CryptoStorageStartupVerifier.Settings source, String provider) {
        return copy(source, source.adapterId(), provider, source.modulePath(), source.allowedModulePaths(),
                source.slotId(), source.tokenIdentity(), source.credentialSource(), source.credentialReference(),
                source.mechanisms(), source.keyAttributes(), source.rotationRequiredAt(), source.hardCeiling(),
                source.fieldKekAlias(), source.snapshotRecoveryAlias(),
                source.snapshotRecoveryReference(), source.mobileIndexAlias(), source.objectDigestAlias(),
                source.registrationDigestAlias());
    }

    private static CryptoStorageStartupVerifier.Settings withMechanisms(
            CryptoStorageStartupVerifier.Settings source, Set<String> mechanisms) {
        return copy(source, source.adapterId(), source.providerId(), source.modulePath(),
                source.allowedModulePaths(), source.slotId(), source.tokenIdentity(), source.credentialSource(),
                source.credentialReference(), mechanisms, source.keyAttributes(), source.rotationRequiredAt(),
                source.hardCeiling(), source.fieldKekAlias(), source.snapshotRecoveryAlias(),
                source.snapshotRecoveryReference(), source.mobileIndexAlias(),
                source.objectDigestAlias(), source.registrationDigestAlias());
    }

    private static CryptoStorageStartupVerifier.Settings withAttributes(
            CryptoStorageStartupVerifier.Settings source, Set<String> attributes) {
        return copy(source, source.adapterId(), source.providerId(), source.modulePath(),
                source.allowedModulePaths(), source.slotId(), source.tokenIdentity(), source.credentialSource(),
                source.credentialReference(), source.mechanisms(), attributes, source.rotationRequiredAt(),
                source.hardCeiling(), source.fieldKekAlias(), source.snapshotRecoveryAlias(),
                source.snapshotRecoveryReference(), source.mobileIndexAlias(),
                source.objectDigestAlias(), source.registrationDigestAlias());
    }

    private static CryptoStorageStartupVerifier.Settings withAliases(
            CryptoStorageStartupVerifier.Settings source, String field, String mobile,
            String object, String registration) {
        return copy(source, source.adapterId(), source.providerId(), source.modulePath(),
                source.allowedModulePaths(), source.slotId(), source.tokenIdentity(), source.credentialSource(),
                source.credentialReference(), source.mechanisms(), source.keyAttributes(),
                source.rotationRequiredAt(), source.hardCeiling(), field,
                source.snapshotRecoveryAlias(), source.snapshotRecoveryReference(),
                mobile, object, registration);
    }

    private static CryptoStorageStartupVerifier.Settings withSnapshotAlias(
            CryptoStorageStartupVerifier.Settings source, String snapshotAlias) {
        return copy(source, source.adapterId(), source.providerId(), source.modulePath(),
                source.allowedModulePaths(), source.slotId(), source.tokenIdentity(), source.credentialSource(),
                source.credentialReference(), source.mechanisms(), source.keyAttributes(),
                source.rotationRequiredAt(), source.hardCeiling(), source.fieldKekAlias(),
                snapshotAlias, source.snapshotRecoveryReference(), source.mobileIndexAlias(),
                source.objectDigestAlias(), source.registrationDigestAlias());
    }

    private static CryptoStorageStartupVerifier.Settings withSnapshotReference(
            CryptoStorageStartupVerifier.Settings source, String snapshotReference) {
        return copy(source, source.adapterId(), source.providerId(), source.modulePath(),
                source.allowedModulePaths(), source.slotId(), source.tokenIdentity(), source.credentialSource(),
                source.credentialReference(), source.mechanisms(), source.keyAttributes(),
                source.rotationRequiredAt(), source.hardCeiling(), source.fieldKekAlias(),
                source.snapshotRecoveryAlias(), snapshotReference, source.mobileIndexAlias(),
                source.objectDigestAlias(), source.registrationDigestAlias());
    }

    private static CryptoStorageStartupVerifier.Settings withCeiling(
            CryptoStorageStartupVerifier.Settings source, long rotation, long ceiling) {
        return copy(source, source.adapterId(), source.providerId(), source.modulePath(),
                source.allowedModulePaths(), source.slotId(), source.tokenIdentity(), source.credentialSource(),
                source.credentialReference(), source.mechanisms(), source.keyAttributes(), rotation, ceiling,
                source.fieldKekAlias(), source.snapshotRecoveryAlias(),
                source.snapshotRecoveryReference(), source.mobileIndexAlias(),
                source.objectDigestAlias(), source.registrationDigestAlias());
    }

    private static void assertInvalid(CryptoStorageStartupVerifier.Settings settings, String property) {
        assertThatThrownBy(settings::validate)
                .hasMessage("CRYPTO_STORAGE_CONFIGURATION property=" + property);
    }
}
