# Phase 09 UI Specification — Tenant access administration

## Visual source and browser boundary

The Phase 02 tenant shell, tokens, typography, spacing, table, dialog, and
notification patterns are the visual source. The local phase baseline is
`design-output/tenant-access-baseline.pen`; the clickable interaction source is
`design-output/tenant-access-prototype.html`. Production acceptance is desktop
Google Chrome installed on the machine at a runtime-asserted viewport of
1440x900 (D-09-001). Mobile and all other browser engines are outside this
contract.

## Page registry

| Page ID | Route | Roles | Purpose |
| --- | --- | --- | --- |
| tenant-administrators | `/tenant/administrators` | TENANT_ADMIN | Create, edit, list, and disable tenant business/developer subaccounts. |
| tenant-api-keys | `/tenant/api/keys` | TENANT_ADMIN, TENANT_DEV | Create policy-bound HTTP API keys, show one-time secret handoff, and revoke keys. |
| tenant-cmpp-access | `/tenant/cmpp/access` | TENANT_ADMIN, TENANT_DEV | Request/list CMPP connection metadata, show controlled password handoff, and revoke credentials. |

## Permission matrix

| Role | Administrators page | API-key page | CMPP page | Mutation policy |
| --- | --- | --- | --- | --- |
| TENANT_ADMIN | allow | allow | allow | Tenant-scoped server authorization is authoritative. |
| TENANT_DEV | deny | allow | allow | Cannot manage subaccounts or assign roles. |
| TENANT_USER | deny | deny | deny | Render an explicit access-denied state; no API fetch for forbidden pages. |

## Interaction and state contract

### Tenant administrators

The table shows username, role, status, created time, and row actions. The create
dialog requires username, display name, password, and exactly one allowed
tenant role (`business` or `developer`). Edit retains username and changes
display name/role/status according to API policy. Submit shows inline validation,
loading, server error/conflict, and success feedback. A disabled/locked account
row is visible but cannot be used to mutate another tenant.

### HTTP API keys

The table shows App Key, name/description, status, expiry, IP allow-list, four
rate values, and last-use time; it never shows App Secret. Create opens a dialog
with name, description, expiry, allow-list, and second/minute/hour/day limits.
Success opens a one-time handoff dialog with a copy-disabled or explicit
acknowledgement control; closing or navigating clears the value. Revoke uses a
destructive confirmation dialog and preserves the table state on cancel or
error. Expired/revoked rows remain readable as masked metadata.

### CMPP access

The page lists protocol, masked account, SPID, endpoint host/port, max
connections, window size, TPS, allow-list, and status. Request opens a form for
endpoint/policy values; server-generated account/password handoff appears only
on successful create and is cleared after close. Password is never rendered by
the list/detail query. Revoke requires confirmation and shows immediate status
feedback. No socket or CMPP protocol session is started by this UI.

## Shared states and feedback

Each page explicitly documents loading, empty, service-error with retry,
permission-denied, validation error, conflict/stale response, mutation pending,
success toast/status, and destructive confirmation/cancel. Network/500 errors
use the existing safe internal-error notice and trace ID pattern; no stack,
secret, object identifier, or server payload is shown.

## Selector policy

Every row in `UI-ELEMENTS.md` has a stable full-form `data-testid`. Repeated
table rows use the semantic row ID plus a separate non-sensitive business-key
attribute. Selector names do not include username, App Key, tenant ID, or
generated IDs. The six canonical rows map the six direct UI obligations; shared
element rows add the remaining page controls while preserving those canonical
links.

## Accessibility and keyboard behavior

Labels are associated with inputs, table headers are explicit, dialogs trap
focus and return focus to their trigger, destructive actions require keyboard
confirmation, and status/error messages use the existing accessible live-region
pattern. No color-only meaning is used for status.

