# Spirit 06 System Design

## Ownership

- `TenantReviewService` owns the approval transaction and its safe result
  projection.
- `TenantApiKeyService` owns tenant identity resolution, credential lifecycle,
  secret protection, and credential audit selection.
- `OperationAuditService` owns append-only audit storage and tenant/resource
  scoped reads.
- `TenantListPage` renders the approval result from the decision response.
- `TenantApiKeysPage` owns credential and audit presentation state. It never
  reconstructs tenant identity or secret values.

## Data Flow

Qualification approval writes trial fields and returns an allowlisted review
projection. The review dialog renders the returned tenant and trial fields.

The API Key page performs independent list and audit reads. List rows contain
safe credential metadata plus `appSecretMask`. A successful create response
adds `appSecret`; the page stores it only in handoff state until the user
acknowledges it. Create and revoke append redacted audit records whose tenant is
resolved from the authenticated actor.

## Command Flow

1. Create validates the form and takes a synchronous in-memory submit lock.
2. A successful response closes the form and opens the non-dismissible secret
   handoff before any list refresh can fail.
3. Acknowledgement clears `appSecret` from state. Navigation, reload, tab close,
   or component unmount also discards the in-memory value without persistence
   or recovery.
4. Revoke opens a target-aware dialog, takes its own submit lock, and sends one
   request only after confirmation.
5. Successful mutations refresh list and audit state independently.

`ModalDialog` is extended with a viewport backdrop and an explicit
non-dismissible mode. The backdrop intercepts page interaction and the existing
focus trap retains keyboard focus. Secret handoff is non-dismissible; create
and revoke owners pass latch-aware close handlers and disable cancel while a
request is pending.

## Failure Model

- List failure retains the page shell, table header, create action, and retry.
- Audit failure does not hide credential rows or a pending secret handoff.
- A 403 response or a locally known unsupported tenant role renders the access
  denied state without tenant data.
- Create failure keeps all editable values. Revoke failure keeps the target and
  consequence visible for retry.
- A definite create rejection releases the create latch. A transport
  interruption or 5xx is an unknown outcome: that dialog remains unable to
  resubmit, list readback runs, and the mounted page retains every unresolved
  name after close/reopen so another POST for any such name is refused. The user
  inspects and, if necessary, revokes a matching key before using a new name.
- A post-create refresh failure cannot hide or clear the one-time secret.

## Security And Audit

The server derives tenant identity from the authenticated user on every list,
create, revoke, and audit operation. The audit endpoint returns no more than the
newest 100 matching rows, ordered by descending audit ID. Audit rows contain
only ID, actor, operation, resource ID, result, and occurrence time. UI tests
use synthetic values and assert that the created plaintext never appears after
acknowledgement or page-lifecycle disposal.

## Verification Model

- Java unit/H2 tests cover approval projection, response redaction, role and
  cross-tenant isolation, mask/create separation, revoke, and audit readback.
- Vitest covers route/page state, form locks, handoff acknowledgement, mask,
  revoke failure, and audit state.
- Google Chrome Playwright covers the approval result and the configuration
  redirect through create, acknowledgement, masked list, revoke, and audit.
