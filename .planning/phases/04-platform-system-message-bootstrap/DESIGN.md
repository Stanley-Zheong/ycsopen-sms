# Design

## Context and constraints

- Inputs: existing Spring app context, message/task persistence, logging policy, and verification tooling from phases 1/3.
- Constraint: only platform bootstrap owner is implemented; no tenant acceptance/routing/billing modules.
- Exclusions: no mobile channel logic, no channel/tenant notification rule authoring.

## Architecture and ownership

- Introduce phase-owned SPI: `PlatformNotificationSpi` with a single explicit provider implementation boundary.
- Add `PlatformMessageBootstrapService` as the owning component for phase 4 obligations.
- Add `PlatformNotificationAudit` as append-only record mapping.

## Data model and migrations

- No new migration files in phase 4.
- Schema migrations: none

## State machines

- `BOOTSTRAP_READY -> DISPATCHING -> (SUCCESS|FAILURE)` with terminal states.
- Failure state includes deterministic `retryClass` and `guardState` values.

## API or protocol contracts

- `PlatformNotificationSpi.send(...)` and normalized `PlatformNotificationResult`.
- Retry and guard classification returns explicit reason codes used by downstream consumers.

## Authorization and tenant isolation

- Delivery service is internal and environment-backed; only internal admin/system callers may trigger bootstrap notifications.
- Tenant-specific recipient routing is represented as policy identifiers only.

## UI and interaction model

- No UI is implemented in this phase.

## Async, idempotency, retry, and concurrency

- Idempotency keys are derived from purpose + stable recipient set + template key.
- Recursion guard prevents repeated bootstrap re-entry from delivery failure callbacks.
- Retry policy is conservative: retry only on explicit transient classes.

## Security and privacy

- Provider secrets and credentials are never logged or serialized in payload.
- Delivery evidence stores only redacted transport metadata + result class.

## Observability and audit

- Audit captures: obligation ID, purpose, trigger, provider result class, guard state, and retry class.
- Evidence retention IDs map directly to obligation-owned cases.

## Failure, rollback, and recovery

- No rollback side effects in phase 4.
- Retry/backoff and guard state form recovery mechanism.

## Alternatives rejected

- Reusing tenant alert SPI directly: rejected because tenant routing and channel policy are not yet in scope and would increase coupling.
- Immediate production provider fanout: rejected to keep bootstrap bounded and testable.

