# Phase 10 UI Specification — Channel configuration lifecycle

## Visual and browser boundary

Reuse the Phase 02 Admin shell, ycsan-web blue/teal tokens, dense white
management surfaces, table, modal, status, and notification patterns. The
visual source is `design-output/channel-configuration-baseline.pen`; the
clickable interaction source is
`design-output/channel-configuration-prototype.html`. Production acceptance
uses only the installed desktop Google Chrome at 1440x900 (D-10-001). Mobile
and every other browser engine are outside this contract.

## Page registry

| Page ID | Route | Roles | Purpose |
| --- | --- | --- | --- |
| admin-channel-configuration | `/admin/channel/configuration` | operator with channel configuration permission | List, create, validate, activate, migrate dependencies, and take channels offline. |

The existing `/admin/channels` link is a compatibility redirect to the
registered route; it does not create a second page contract.

## Permission matrix

| Role/permission | Page read | Create/edit/test | Activate/rollback | Dependency migration/offline |
| --- | --- | --- | --- | --- |
| operator with channel configuration permission | allow | allow | allow | allow |
| authenticated platform user without channel configuration permission | denied state; no configuration fetch | hidden | hidden | hidden |
| tenant administrator/developer/user | denied response; no platform data | hidden | hidden | hidden |

## Interaction and state contract

The table displays channel name, protocol, carrier, endpoint, effective
version, price, priority, availability, status, updated time, and row actions.
The create/edit dialog contains name, protocol, carrier, host, port, account,
password, SPID/service ID, source number, maximum connections, window size,
unit price, tier/province price rows, priority, availability, and extension
JSON. Protocol changes apply protocol-specific required-field rules.

The connectivity test runs before activation and reports a safe pass/failure
reason. Activation shows the target and effective version plus success,
rejected, stale, and retained-prior-version states. Dependency preview lists
route, pool, filing, price, and active-task references with destination fields;
the migration wizard requires explicit resolution for every item. Offline
requires a destructive confirmation, is blocked while dependencies remain, and
turns the row immutable after success: edit, connectivity test, activate, and
offline actions are disabled for OFFLINE rows while dependency preview remains
available for audit.

## States, feedback, and accessibility

Loading, empty, service error with retry, permission denied, validation error,
duplicate-name conflict, stale version, connectivity failure, activation
rollback, dependency-blocked, mutation pending, success, and terminal offline
states are visible through text and accessible live regions. Dialogs trap focus,
return focus to their trigger, support Escape cancellation, and require a
keyboard-confirmable destructive action. Status is never color-only; table
headers and labels are explicit. No secrets, stack traces, internal IDs, or
arbitrary response payloads appear in the DOM.

## Selector contract

The exact 14 UI rows and stable IDs are in `UI-ELEMENTS.md`. Repeated rows use
`admin-channel-configuration-channel-row` plus a non-sensitive business-key
attribute; generated database IDs and channel names never enter selector
names. The two lower-layer obligations (hot-load core and channel data model)
are represented as `not-applicable: lower-layer-only` in `TEST-MATRIX.md` and
do not receive fake browser claims.
