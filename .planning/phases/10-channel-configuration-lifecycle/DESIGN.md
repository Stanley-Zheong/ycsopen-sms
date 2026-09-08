# Design

## Context and constraints

The module owns platform-operated upstream channel configuration and its
lifecycle boundary. It reuses Phase 02 Admin shell/tokens, Phase 03 protected
fields, and Phase 06 audit. The production browser boundary is installed
desktop Chrome at 1440x900 (D-10-001). No protocol connection session is
started by configuration tests or UI.

## Architecture and ownership

- `ChannelConfigurationService` owns validation, safe projections, create/edit,
  and protected-field preparation.
- `ChannelConfigurationVersionService` owns immutable snapshots, expected
  version checks, atomic effective-version swaps, retry, and rollback-as-new
  version.
- `ChannelDependencyInventoryService` owns the declared dependency-provider
  list and inventory/migration gate; each provider reads its own reference table
  with a channel predicate.
- `ChannelConnectivityAdapter` owns protocol-specific configuration
  conformance/reachability checks. CMPP, SGIP, SMGP, and HTTP implementations
  are deterministic test adapters in this phase; socket protocol sessions are
  not added.
- Controllers expose safe DTOs only. No entity is serialized and no request
  field can select an unrelated owner or bypass the operator permission.

## Data model and migrations

Schema migrations: declared

Reuse `channels` for canonical current metadata and encrypted account/password.
Add `channel_configuration_versions` as immutable canonical snapshots with
effective/activation result, actor, reason, and timestamps. Add only the
metadata needed to identify `channels.configuration_version` and preserve
compatibility with existing readers. Use namespace `V1900-V1999` and the
claims in `SCHEMA-CLAIMS.md`; migrations are expand-compatible and rollback is
forward-compatible disable/restore-snapshot, never destructive table removal.
Price uses `DECIMAL(10,4)`/`BigDecimal`; tier or carrier-province rows are
versioned data and are never updated retroactively. JSON extension and
availability retain valid JSON/text formats with explicit validation.

## State machines

Configuration version: `DRAFT -> VALIDATED -> EFFECTIVE`, or
`VALIDATED -> RELOAD_REJECTED`; only one version is effective per channel.
`RELOAD_REJECTED -> VALIDATED` is an explicit retry using the same immutable
payload or a new corrected version. Rollback creates a new version from a
historical effective snapshot.

Channel status: `NORMAL/PAUSED/MAINTENANCE -> OFFLINE` only when the dependency
inventory is empty or every item has an explicit migration resolution.
OFFLINE is terminal in this phase; health/pause recovery remains owned by the
channel-health phase.

## API contracts

- `GET /api/v1/console/channels/configuration`
- `POST /api/v1/console/channels/configuration`
- `PUT /api/v1/console/channels/configuration/{id}`
- `POST /api/v1/console/channels/configuration/{id}/connectivity-test`
- `POST /api/v1/console/channels/configuration/{id}/activate`
- `GET /api/v1/console/channels/configuration/{id}/dependencies`
- `POST /api/v1/console/channels/configuration/{id}/dependencies/migrate`
- `POST /api/v1/console/channels/configuration/{id}/offline`

All endpoints use `ApiResponse`, operator authorization, safe DTOs, and a
correlation identity. Credential fields are write-only protected inputs and
masked metadata on list/detail/activation responses.

## Authorization and tenant isolation

Channel configuration is a platform operator surface. Require the existing
platform identity/RBAC permission for channel configuration reads and writes;
tenant JWTs and tenant IDs are rejected. Authorization is enforced in the
service as well as route navigation. Every mutation records actor identity in
the existing redacted audit service.

## UI and interaction model

The page `/admin/channel/configuration` contains a searchable status table,
create/edit modal, protocol-aware form, connectivity-test action, activation
result panel, dependency preview, migration wizard, and offline confirmation.
Fields and actions use the exact IDs in `UI-ELEMENTS.md`. Loading, empty,
error/retry, denied, stale, validation, success, and destructive confirmation
states are explicit. The Pencil source and clickable HTML prototype are phase
local; React implementation is added only during execution.

## Async, idempotency, retry, and concurrency

Create/update uses a uniqueness constraint and an explicit idempotency key when
the caller retries an unknown response. Activation uses an expected effective
version and a guarded transaction; the snapshot swap is atomic in the current
process. A failed adapter or swap never changes the old effective pointer.
Offline/migrate requests are repeat-safe by dependency ID and destination;
repeated offline after success returns the same terminal state.

## Security and privacy

Use Phase 03 `ProtectedFieldCodec` for account/password, clear transient
plaintext buffers after protection, and reject secret values in logs, audit
payloads, list/detail DTOs, error text, and evidence. Validate endpoint host,
port, protocol-specific identifiers, positive connection/window values,
non-negative four-decimal price, priority 1-100, availability, and extension
JSON before persistence.

## Observability and audit

Audit create/update/connectivity test/activation/reload rejection/rollback,
dependency migration, and offline requests with actor, channel, version,
result code, and correlation ID. Event/result payloads contain no secret or
arbitrary request JSON. Consumers expose the effective version identifier.

## Failure, rollback, and recovery

Migration rollback disables new version readers and restores the previous
compatible snapshot; it does not drop `channels` or version rows. Activation
failure keeps the prior effective configuration and records a safe reason.
An operator can retry after correcting the configuration. Offline is blocked by
any unresolved dependency, with the inventory retained for diagnosis.

## Alternatives rejected

- Direct entity serialization is rejected because it can expose protected
  values and couples API shape to persistence.
- A distributed config bus is rejected because this phase requires the current
  process's atomic reload contract; no cross-instance assurance is claimed.
- Protocol socket/session implementations are rejected because real protocol
  interoperability belongs to protocol connector phases.
- A generalized policy/workflow engine is rejected; typed validation,
  dependency providers, and a guarded service are sufficient for this module.
