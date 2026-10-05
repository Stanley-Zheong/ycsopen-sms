# Spirit 06: Tenant Onboarding And API Access

## Intent

Connect qualification approval to tenant API onboarding without disclosing a
login password or App Secret. Platform reviewers receive the tenant and trial
identifiers created by approval. Tenant administrators and developers manage
tenant-scoped HTTP API credentials from one production page.

## Owned Routes And Objects

| Route | Page goal | Primary object | Actor | Data source |
| --- | --- | --- | --- | --- |
| `/admin/tenants` | Confirm the result of a qualification decision. | Approved tenant and trial entitlement. | Platform actor with `tenant:qualification:review`. | Existing Admin tenant list/detail/decision APIs. |
| `/tenant/config` | Preserve the existing configuration entry without a second implementation. | Route alias. | `TENANT_ADMIN`, `TENANT_DEV`. | Client-side redirect to `/tenant/api/keys`. |
| `/tenant/api/keys` | Create, list, revoke, and audit tenant-owned HTTP API credentials. | API Key summary and audit entry. | `TENANT_ADMIN`, `TENANT_DEV`; other tenant roles receive a denied state. | Tenant API Key list/create/revoke/audit APIs; tenant scope is derived by the server. |

## Behaviors

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| FE-SPIRIT-06-APPROVAL | A successful approval displays `tenantId`, `tenantNo`, trial quota, and trial start/end time from the decision response. | The result appears in the open review dialog immediately after approval and contains no password or App Secret field. |
| FE-SPIRIT-06-ROUTE | The configuration placeholder is retired in favor of the existing API Key owner. | `/tenant/config` redirects to `/tenant/api/keys`; the sidebar has one API Key entry under configuration. |
| FE-SPIRIT-06-CREDENTIAL | The API Key page keeps its table header in loading, empty, error, and populated states; list rows show only `appSecretMask`. | Chrome observes loading, empty, retryable error, denied, masked list, create, and revoke states without horizontal overflow. |
| FE-SPIRIT-06-SECRET | `appSecret` exists only in the successful create response and the in-memory handoff dialog. | While the page remains mounted, the dialog cannot be dismissed before explicit acknowledgement and survives refresh failures. Navigation, reload, tab close, or unmount discards the value without persistence or recovery. Later list/audit responses and UI do not contain the plaintext. |
| FE-SPIRIT-06-AUDIT | Credential create and revoke records are visible to the current tenant without exposing another tenant's records. | `GET /api/v1/console/tenant/api-keys/audits` returns the newest 100 redacted current-tenant records in descending audit-ID order; the page refreshes the table after successful mutations. |

## Action Contract

| Action | Target and preconditions | Request and result | Failure and duplicate-submission behavior |
| --- | --- | --- | --- |
| Approve qualification | Selected pending tenant, completed inspection, human confirmation, reason, current revision. | Existing decision request returns safe approval/trial fields. | Existing stale/provider/validation handling retains input. |
| Create API Key | Current tenant; required name; optional description, future expiry, allow-list; ordered positive rate limits. | One POST creates the key and returns `appSecret` once. | A synchronous submit lock sends one request. A definite 4xx rejection releases the lock for corrected input. A transport interruption or 5xx has an unknown outcome: the dialog cannot resubmit, the mounted page retains every unresolved name and refuses another POST for any of them after close/reopen, and list readback prompts inspection or revoke before a different named credential is created. No secret is fabricated or recovered. |
| Acknowledge secret | Successful create handoff on the mounted page. | Clears plaintext from React state; no request. | Escape and implicit close do not clear the value. Navigation, reload, tab close, or unmount ends the handoff and deliberately provides no recovery. |
| Revoke API Key | Selected active row. | One POST disables the key, then refreshes the list and audit trail. | A synchronous lock prevents double submit; cancel sends no request; failure remains in the dialog. |
| Refresh page data | Current tenant. | Repeats list and tenant-scoped audit reads. | Key and audit errors have independent retry controls. |

## States And Boundaries

- The API Key table renders a stable header for loading, empty, error, denied,
  and populated results. The audit table has its own loading, empty, error, and
  populated states. Within each scoped region the table and empty row also use
  the shared `data-table` and `table-empty` selectors.
- `TENANT_USER` receives the documented access-denied state and triggers no
  credential request. Server authorization remains authoritative.
- App Key is a public credential identifier. App Secret is secret material.
  The UI never writes App Secret to a URL, log, analytics event, storage API,
  list row, or audit record.
- Audit rows contain only `id`, `actor`, `operation`, `resourceId`, `result`, and
  `occurredAt`. Audit failures are independently retryable and never hide the
  credential table or a mounted secret handoff.
- `ModalDialog` supplies a viewport backdrop and focus trap. The secret handoff
  uses its non-dismissible mode. Create and revoke dialogs guard Escape, cancel,
  close, and repeated confirmation with the same synchronous pending latch.
- Existing HTTP HMAC authentication, rate enforcement, and CMPP credentials are
  outside this spirit.

## Scoped TODO

- [x] Extend the qualification decision projection and result UI.
- [x] Replace the configuration placeholder with the canonical API Key route.
- [x] Separate create-only secret and list-only mask contracts.
- [x] Add tenant-scoped credential audit readback.
- [x] Implement explicit page, table, form, handoff, revoke, and audit states.
- [x] Add focused backend, frontend, and Chrome coverage.
- [x] Complete the quality gateway and independent review.
