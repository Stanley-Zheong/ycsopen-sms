# Phase 55 Design

## Added contract

- `ExtensionPointDefinition` describes one extension seam.
- `ExtensionPointRegistry` lists supported connector, policy, notification, review-provider, and queue-consumer extension points.
- `ExtensionPointRegistryTest` verifies registry coverage, loadable contracts, loadable conformance tests, versioned configuration, and independent scale declarations.

## Protected core contracts

Message connector and queue-consumer extensions explicitly protect:

- acceptance
- routing
- receipt
- billing
- observability

## Boundaries

No classpath scanning, dynamic plugin loader, remote plugin marketplace, or UI configuration page is added. Those are separate product decisions, not required for current conformance assurance.
