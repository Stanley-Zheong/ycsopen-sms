# Phase 55 Extension Registry

The executable source of truth is `core/src/main/java/com/ycsopen/sms/core/service/extension/ExtensionPointRegistry.java`.

| Extension point | Kind | Contract |
| --- | --- | --- |
| sms-upstream-provider | CONNECTOR | `SmsUpstreamProviderClient` |
| platform-notification-provider | NOTIFICATION_ADAPTER | `PlatformNotificationSpi` |
| qualification-inspection-provider | REVIEW_PROVIDER | `QualificationInspectionSpi` |
| provider-status-taxonomy | POLICY_CONFIGURATION | `ProviderStatusTaxonomyPort` |
| routing-circuit-policy | POLICY_CONFIGURATION | `RoutingCircuitPolicyService` |
| contract-pricing-policy | POLICY_CONFIGURATION | `ContractPricingService` |
| resource-review-policy | POLICY_CONFIGURATION | `ResourceReviewHistoryService` |
| dispatch-worker-consumer | QUEUE_CONSUMER | `HttpMessageDeliveryService` |

Connector and queue-consumer extension points protect acceptance, routing, receipt, billing, and observability contracts.
