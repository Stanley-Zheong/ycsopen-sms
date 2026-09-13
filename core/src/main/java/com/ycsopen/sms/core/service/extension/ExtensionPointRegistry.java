package com.ycsopen.sms.core.service.extension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Central extension registry for the PRD 6.5 conformance contract. */
public final class ExtensionPointRegistry {

    private static final List<String> MESSAGE_CORE = List.of(
            "acceptance",
            "routing",
            "receipt",
            "billing",
            "observability");

    private static final List<ExtensionPointDefinition> EXTENSION_POINTS = List.of(
            extension("sms-upstream-provider",
                    ExtensionPointDefinition.ExtensionKind.CONNECTOR,
                    "com.ycsopen.sms.core.service.delivery.SmsUpstreamProviderClient",
                    List.of(
                            "com.ycsopen.sms.core.service.delivery.HttpSmsUpstreamProviderClient",
                            "com.ycsopen.sms.core.cmpp.CmppSmsUpstreamProviderClient"),
                    List.of(
                            "com.ycsopen.sms.core.service.delivery.HttpSmsUpstreamProviderClientTest",
                            "com.ycsopen.sms.core.cmpp.CmppClientSessionTest",
                            "com.ycsopen.sms.core.service.channel.ChannelConnectivityConformanceTest"),
                    true,
                    true,
                    MESSAGE_CORE),
            extension("platform-notification-provider",
                    ExtensionPointDefinition.ExtensionKind.NOTIFICATION_ADAPTER,
                    "com.ycsopen.sms.core.notification.provider.PlatformNotificationSpi",
                    List.of("com.ycsopen.sms.core.notification.provider.HttpPlatformNotificationSpi"),
                    List.of("com.ycsopen.sms.core.notification.provider.HttpPlatformNotificationSpiTest"),
                    true,
                    true,
                    List.of("template-control", "provider-result", "audit", "observability")),
            extension("qualification-inspection-provider",
                    ExtensionPointDefinition.ExtensionKind.REVIEW_PROVIDER,
                    "com.ycsopen.sms.core.service.tenant.QualificationInspectionSpi",
                    List.of("com.ycsopen.sms.core.service.tenant.HttpQualificationInspectionSpi"),
                    List.of("com.ycsopen.sms.core.service.tenant.HttpQualificationInspectionSpiTest"),
                    true,
                    true,
                    List.of("tenant-admission", "bounded-provider-facts", "protected-documents")),
            extension("provider-status-taxonomy",
                    ExtensionPointDefinition.ExtensionKind.POLICY_CONFIGURATION,
                    "com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyPort",
                    List.of("com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyService"),
                    List.of("com.ycsopen.sms.core.service.tool.ProviderStatusTaxonomyServiceTest"),
                    true,
                    false,
                    List.of("receipt", "retry", "billing", "analytics")),
            extension("routing-circuit-policy",
                    ExtensionPointDefinition.ExtensionKind.POLICY_CONFIGURATION,
                    "com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService",
                    List.of("com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyService"),
                    List.of("com.ycsopen.sms.core.service.routing.RoutingCircuitPolicyServiceTest"),
                    true,
                    false,
                    List.of("routing", "retry", "channel-health", "observability")),
            extension("contract-pricing-policy",
                    ExtensionPointDefinition.ExtensionKind.POLICY_CONFIGURATION,
                    "com.ycsopen.sms.core.service.billing.ContractPricingService",
                    List.of("com.ycsopen.sms.core.service.billing.ContractPricingService"),
                    List.of("com.ycsopen.sms.core.service.billing.ContractPricingServiceTest"),
                    true,
                    false,
                    List.of("billing", "credit", "settlement")),
            extension("resource-review-policy",
                    ExtensionPointDefinition.ExtensionKind.POLICY_CONFIGURATION,
                    "com.ycsopen.sms.core.service.review.ResourceReviewHistoryService",
                    List.of("com.ycsopen.sms.core.service.review.ResourceReviewHistoryService"),
                    List.of("com.ycsopen.sms.core.service.review.ResourceReviewHistoryServiceTest"),
                    true,
                    false,
                    List.of("signature-review", "template-review", "audit")),
            extension("dispatch-worker-consumer",
                    ExtensionPointDefinition.ExtensionKind.QUEUE_CONSUMER,
                    "com.ycsopen.sms.core.service.delivery.HttpMessageDeliveryService",
                    List.of("com.ycsopen.sms.core.service.delivery.HttpMessageDeliveryService"),
                    List.of(
                            "com.ycsopen.sms.core.service.delivery.HttpMessageDeliveryServiceTest",
                            "com.ycsopen.sms.core.service.delivery.DispatchTaskRecoveryServiceTest",
                            "com.ycsopen.sms.core.service.message.MessageAcceptanceIdempotencyServiceTest"),
                    true,
                    true,
                    MESSAGE_CORE)
    );

    private static final Map<String, ExtensionPointDefinition> BY_ID = byId();

    private ExtensionPointRegistry() {
    }

    public static List<ExtensionPointDefinition> all() {
        return EXTENSION_POINTS;
    }

    public static ExtensionPointDefinition require(String id) {
        ExtensionPointDefinition extension = BY_ID.get(id);
        if (extension == null) {
            throw new IllegalArgumentException("unknown extension point: " + id);
        }
        return extension;
    }

    public static List<String> ids() {
        return EXTENSION_POINTS.stream().map(ExtensionPointDefinition::id).toList();
    }

    private static Map<String, ExtensionPointDefinition> byId() {
        Map<String, ExtensionPointDefinition> extensions = new LinkedHashMap<>();
        for (ExtensionPointDefinition extension : EXTENSION_POINTS) {
            if (extensions.put(extension.id(), extension) != null) {
                throw new IllegalStateException("duplicate extension point: " + extension.id());
            }
        }
        return Map.copyOf(extensions);
    }

    private static ExtensionPointDefinition extension(
            String id,
            ExtensionPointDefinition.ExtensionKind kind,
            String contractClassName,
            List<String> referenceImplementations,
            List<String> conformanceTests,
            boolean versionedConfiguration,
            boolean independentlyScalable,
            List<String> protectedCoreContracts) {
        return new ExtensionPointDefinition(id, kind, contractClassName, referenceImplementations,
                conformanceTests, versionedConfiguration, independentlyScalable, protectedCoreContracts);
    }
}
