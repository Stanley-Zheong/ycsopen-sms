package com.ycsopen.sms.core.service.configuration;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformConfigurationRegistryTest {
    private final PlatformConfigurationRegistry registry = new PlatformConfigurationRegistry();

    @Test
    void mergesOnlyChangedKeysAndPreservesServerSideSecretReference() {
        Map<String, String> current = registry.defaults();

        Map<String, String> merged = registry.mergeAndValidate(
                current, Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8"));

        assertThat(merged).containsEntry(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "8")
                .containsEntry(PlatformConfigurationRegistry.EXPORT_SIGNING_KEY_REFERENCE,
                        "env:YCS_SMS_EXPORT_SIGNING_KEY");
        assertThat(registry.displayValue(PlatformConfigurationRegistry.EXPORT_SIGNING_KEY_REFERENCE,
                merged.get(PlatformConfigurationRegistry.EXPORT_SIGNING_KEY_REFERENCE)))
                .isEqualTo(PlatformConfigurationRegistry.MASKED_SECRET_REFERENCE);
    }

    @Test
    void rejectsUnknownOutOfRangeAndMaskedOrRawSecretValues() {
        Map<String, String> current = registry.defaults();

        assertThatThrownBy(() -> registry.mergeAndValidate(current, Map.of("unknown.key", "1")))
                .isInstanceOf(PlatformConfigurationRegistry.ValidationFailure.class)
                .hasMessageContaining("unknown.key");
        assertThatThrownBy(() -> registry.mergeAndValidate(current,
                Map.of(PlatformConfigurationRegistry.LOGIN_MAX_FAILURES, "2")))
                .isInstanceOf(PlatformConfigurationRegistry.ValidationFailure.class);
        assertThatThrownBy(() -> registry.mergeAndValidate(current,
                Map.of(PlatformConfigurationRegistry.EXPORT_SIGNING_KEY_REFERENCE,
                        PlatformConfigurationRegistry.MASKED_SECRET_REFERENCE)))
                .isInstanceOf(PlatformConfigurationRegistry.ValidationFailure.class);
        assertThatThrownBy(() -> registry.mergeAndValidate(current,
                Map.of(PlatformConfigurationRegistry.EXPORT_SIGNING_KEY_REFERENCE, "plain-secret")))
                .isInstanceOf(PlatformConfigurationRegistry.ValidationFailure.class);
    }

    @Test
    void exposesThreeTypedDefinitionsAndCanonicalChecksum() {
        assertThat(registry.definitions()).extracting(PlatformConfigurationRegistry.Definition::type)
                .containsExactly(
                        PlatformConfigurationRegistry.ValueType.INTEGER,
                        PlatformConfigurationRegistry.ValueType.BOOLEAN,
                        PlatformConfigurationRegistry.ValueType.SECRET_REFERENCE);
        assertThat(registry.checksum(registry.defaults())).matches("[0-9a-f]{64}");
        assertThat(registry.checksum(registry.defaults())).isEqualTo(registry.checksum(registry.defaults()));
    }

    @Test
    void olderPersistedSnapshotReceivesDefaultsForKeysAddedLater() {
        Map<String, String> olderSnapshot = new LinkedHashMap<>(registry.defaults());
        olderSnapshot.remove(PlatformConfigurationRegistry.UNUSUAL_IP_ENABLED);
        PlatformConfigurationRuntime runtime = new PlatformConfigurationRuntime(registry);

        runtime.apply(runtime.prepare(3, olderSnapshot));

        assertThat(runtime.version()).isEqualTo(3);
        assertThat(runtime.unusualIpEnabled()).isTrue();
        assertThatThrownBy(() -> runtime.prepare(4, Map.of("removed.key", "value")))
                .isInstanceOf(PlatformConfigurationRegistry.ValidationFailure.class);
    }
}
