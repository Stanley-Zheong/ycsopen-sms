# Design

## Context and constraints

This phase is one focused tenant-access module. It consumes the completed
Phase 05 identity/RBAC and Phase 03 field-protection foundations and the Phase
08 qualified-tenant/status contract. It supports desktop Google Chrome only at
1440x900. The three routes are the Phase 02 registry routes, not new aliases:
`/tenant/administrators`, `/tenant/api/keys`, and `/tenant/cmpp/access`.

## Architecture and ownership

- `TenantAccessAdministrationService` owns tenant-admin subaccount operations.
- `TenantApiKeyService` owns App Key policy/secret lifecycle.
- `TenantProtocolCredentialService` owns downstream CMPP credential lifecycle.
- Controllers derive `tenantId` from the authenticated `User` and pass only
  server-resolved identity to services.
- DTOs are safe projections. No entity is serialized directly.
- Existing `OperationAuditService` records redacted authenticated mutations.

## Data model and migrations

Schema migrations: declared

Use the registered `SCHEMA-P09` namespace (`V1800-V1899`). Add only missing
columns needed by the existing P09 credential tables: API-key description and
revocation timestamp; CMPP SPID/endpoint and revocation timestamp. Reuse the
existing `users`, `roles`, `user_roles`, `tenant_api_keys`, and
`tenant_protocol_credentials` tables and do not duplicate identity, session,
or credential tables. Each migration is expand-compatible, its readers remain
compatible with null new columns, and rollback is a forward-compatible
disable/restore-snapshot operation; no destructive drop is used.

## State machines

### Subaccount

`requested -> ACTIVE` on valid create; `ACTIVE -> DISABLED` on permitted
disable; no tenant-admin operation can assign platform roles or another
tenant's role. Existing `LOCKED` behavior remains owned by Phase 05.

### Credentials

`ACTIVE -> DISABLED` is the only destructive transition and is irreversible in
this phase. Create is idempotency-protected by unique App Key/credential ID;
revoke is terminal and publishes one event for the successful transition.

## API contracts

Tenant account endpoints:

- `GET /api/v1/console/tenant/administrators`
- `POST /api/v1/console/tenant/administrators`
- `PATCH /api/v1/console/tenant/administrators/{userId}`

HTTP API-key endpoints:

- `GET /api/v1/console/tenant/api-keys`
- `POST /api/v1/console/tenant/api-keys` (one-time `appSecret` only here)
- `POST /api/v1/console/tenant/api-keys/{id}/revoke`

CMPP credential endpoints:

- `GET /api/v1/console/tenant/cmpp-credentials`
- `POST /api/v1/console/tenant/cmpp-credentials` (one-time password only here)
- `POST /api/v1/console/tenant/cmpp-credentials/{id}/revoke`

All responses use the existing `ApiResponse` envelope and safe DTOs. List,
detail, failure, and audit responses never contain secret plaintext,
envelopes, password hashes, or arbitrary request JSON.

## Authorization and tenant isolation

Tenant administrator operations require `TENANT_ADMIN` plus the tenant-access
administrator permission. API/CMPP operations require `TENANT_ADMIN` or
`TENANT_DEV` plus their credential permission. A business user receives a
denied response and no navigation action. Every query includes the authenticated
tenant predicate; a path identifier is never sufficient to select a record.
Role lookup requires `role_type='TENANT'`, matching `tenant_id`, `ACTIVE`, and
the allow-list of tenant business/developer roles.

## UI and interaction model

The production React pages reuse the TenantLayout shell and Phase 02 tokens.
The administrators page is a table with create/edit dialog and state feedback.
The API-key page is a table with create dialog, one-time secret handoff dialog,
and revoke confirmation. The CMPP page is a credential table/form with
one-time password handoff and revoke confirmation. Every field, action, dialog,
drawer, empty/loading/error/denied state is listed in `UI-ELEMENTS.md` with an
exact stable `data-testid`; route and selector IDs are unchanged by copy.

## Async, idempotency, retry, and concurrency

Use server uniqueness constraints and transaction boundaries for create/revoke.
Do not retry a create after an unknown response unless the API's idempotency
key is preserved. Revoke re-reads current status under a row lock or uses a
guarded update, and only the first state transition publishes the event. UI
retries are explicit and never resubmit a secret-producing create implicitly.

## Security and privacy

Generate secrets with `SecureRandom`; protect them using the Phase 03 envelope
codec and active field-key reference. Clear temporary plaintext byte arrays
after persistence/response preparation. Do not log secrets, tokens, encrypted
values, full protected account values, or credentials in exception messages.
Rate/IP/expiry policy is stored as typed validated data; request enforcement is
owned elsewhere and must not be fabricated here.

## Observability and audit

Append redacted operation audit records for create/update/revoke and publish a
`TenantCredentialRevokedEvent` after the successful transaction. Event payload
contains tenant, credential type, credential ID, and revocation time only.

## Failure, rollback, and recovery

Migration rollback disables new paths and restores the pre-migration snapshot;
it does not drop shared tables. A failed secret handoff leaves the credential
created but secret unavailable only if the service returns an explicit safe
error and records no secret; tests must prove the user can retry through a
newly-created credential without decrypting an old secret. Revoke failures do
not report success or publish an event.

## Alternatives rejected

- A separate tenant-user or credential table was rejected because existing
  Phase 05/legacy tables already provide the required ownership columns.
- A generic workflow/event-bus layer was rejected; one synchronous domain event
  plus persisted status is sufficient for the current lookup paths.
- Secret reveal endpoints were rejected; controlled create responses provide a
  one-time handoff and later reads remain masked.
- CMPP protocol session implementation was rejected because it is explicitly
  owned by the downstream CMPP phase.
