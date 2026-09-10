package com.ycsopen.sms.core.service.extension;

import java.util.List;

/** Immutable description of a supported extension seam and its conformance boundary. */
public record ExtensionPointDefinition(
        String id,
        ExtensionKind kind,
        String contractClassName,
        List<String> referenceImplementations,
        List<String> conformanceTests,
        boolean versionedConfiguration,
        boolean independentlyScalable,
        List<String> protectedCoreContracts) {

    public ExtensionPointDefinition {
        if (id == null || !id.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("extension point id is invalid");
        }
        if (kind == null) {
            throw new IllegalArgumentException("extension point kind is required");
        }
        if (contractClassName == null || contractClassName.isBlank()) {
            throw new IllegalArgumentException("extension point contract class is required");
        }
        referenceImplementations = List.copyOf(referenceImplementations);
        conformanceTests = List.copyOf(conformanceTests);
        protectedCoreContracts = List.copyOf(protectedCoreContracts);
        if (conformanceTests.isEmpty()) {
            throw new IllegalArgumentException("extension point conformance tests are required");
        }
        if (protectedCoreContracts.isEmpty()) {
            throw new IllegalArgumentException("extension point protected core contracts are required");
        }
    }

    public boolean protects(String coreContract) {
        return protectedCoreContracts.contains(coreContract);
    }

    public enum ExtensionKind {
        CONNECTOR,
        POLICY_CONFIGURATION,
        NOTIFICATION_ADAPTER,
        REVIEW_PROVIDER,
        QUEUE_CONSUMER
    }
}
