package com.ycsopen.sms.core.service.extension;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionPointRegistryTest {

    @Test
    void registryCoversDeclaredExtensionClasses() {
        assertThat(ExtensionPointRegistry.ids())
                .containsExactly(
                        "sms-upstream-provider",
                        "platform-notification-provider",
                        "qualification-inspection-provider",
                        "provider-status-taxonomy",
                        "routing-circuit-policy",
                        "contract-pricing-policy",
                        "resource-review-policy",
                        "dispatch-worker-consumer")
                .doesNotHaveDuplicates();

        assertThat(ExtensionPointRegistry.all())
                .extracting(ExtensionPointDefinition::kind)
                .containsAll(EnumSet.allOf(ExtensionPointDefinition.ExtensionKind.class));
    }

    @Test
    void everyExtensionHasLoadableContractAndConformanceTest() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (ExtensionPointDefinition extension : ExtensionPointRegistry.all()) {
            assertThat(Class.forName(extension.contractClassName(), false, loader))
                    .as(extension.id() + " contract is loadable")
                    .isNotNull();
            for (String testClass : extension.conformanceTests()) {
                assertThat(Class.forName(testClass, false, loader))
                        .as(extension.id() + " conformance test is loadable: " + testClass)
                        .isNotNull();
            }
        }
    }

    @Test
    void connectorAndQueueExtensionsProtectCoreMessageContractsAndScaleIndependently() {
        List<ExtensionPointDefinition> scalable = ExtensionPointRegistry.all().stream()
                .filter(ExtensionPointDefinition::independentlyScalable)
                .toList();

        assertThat(scalable)
                .extracting(ExtensionPointDefinition::id)
                .contains("sms-upstream-provider", "dispatch-worker-consumer", "platform-notification-provider");

        for (String id : List.of("sms-upstream-provider", "dispatch-worker-consumer")) {
            ExtensionPointDefinition extension = ExtensionPointRegistry.require(id);
            assertThat(extension.protectedCoreContracts())
                    .contains("acceptance", "routing", "receipt", "billing", "observability");
        }
    }

    @Test
    void configurablePoliciesAreVersionedAndDoNotRequireHardCodedBranches() {
        List<ExtensionPointDefinition> configurablePolicies = ExtensionPointRegistry.all().stream()
                .filter(extension -> extension.kind()
                        == ExtensionPointDefinition.ExtensionKind.POLICY_CONFIGURATION)
                .toList();

        assertThat(configurablePolicies)
                .extracting(ExtensionPointDefinition::id)
                .contains("provider-status-taxonomy", "routing-circuit-policy",
                        "contract-pricing-policy", "resource-review-policy");
        assertThat(configurablePolicies)
                .allMatch(ExtensionPointDefinition::versionedConfiguration);
    }
}
