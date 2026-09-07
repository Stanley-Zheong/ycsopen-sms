---
phase: "07"
slug: platform-system-configuration
status: approved-for-entry
---

# Phase 07 UI contract

## Design source and viewport

Reuse the Phase 02 desktop Admin shell and tokens: `#f4f8fc` page, white cards, `#0c85e8` primary action, red only for destructive/error outcomes, 6px controls, 10px cards/dialogs, and the 4/8/16/24/32/48/64 spacing scale. Acceptance uses only the locally installed Google Chrome at 1440x900. Mobile and alternate-browser variants are outside scope.

Pencil Desktop/MCP is unavailable. The unchanged `.pen` file is only the approved visual baseline; the Phase 07 interaction truth is the checksum-bound `design-output/system-configuration-prototype.html`, this specification, and `UI-ELEMENTS.md`.

## Route and page structure

`/admin/system/configuration` requires `system:configuration:read` and contains:

1. Page header with title and refresh action.
2. Active-version card with version, checksum abbreviation, activation actor/time, and current reload result.
3. Typed settings table with key, label, type, validation, sensitivity, default, active display value, and edit action.
4. Draft card with changed-key summary, reason, discard, and activate action. It is empty until a draft exists.
5. Immutable history table with version/status/actor/reason/time/changed keys and rollback action for an eligible historical version.
6. Edit, activation, and rollback dialogs, plus explicit loading, empty, stale-write, reload-rejected, error, and success feedback.

## Typed editing

Integer settings use a number input with documented bounds; booleans use a select; secret references use text input but accept only `env:UPPER_SNAKE_CASE`. Existing secret references are displayed as `env:••••••`; opening the editor leaves the sensitive value blank and explains that blank means no secret-reference change. Raw secret-like text and the mask token are rejected. Saving edits updates an unsaved client change set only; “保存草稿” submits changed keys and the reason against the displayed active version. The server, not the browser, merges the complete snapshot. “放弃修改” clears only unsaved client edits and is disabled once no local edit remains; a persisted staged version remains auditable and is never presented as deleted.

## Activation and rollback

Activation requires a nonblank reason and confirms the draft version plus changed keys. Pending controls are disabled. Success updates the active-version/reload card and history. HTTP 409 shows the stale alert and requires refresh before another mutation. Reload rejection keeps the prior active version and makes the rejected draft/history entry visible without exposing unsafe detail.

Rollback is available only on eligible historical active/superseded versions and requires a new reason. Confirmation creates a new version; it never edits the selected historical row. The success status states the newly active version.

## Access and feedback

- `system:configuration:menu` plus `system:configuration:read`: visible Admin navigation entry.
- `system:configuration:read`: direct route, registry, active values, draft summary, and history.
- `system:configuration:write`: edit controls, draft save, and draft discard.
- `system:configuration:activate`: activation and rollback controls.
- Missing read permission renders an explicit denied page state; missing write or activate permission renders an explanatory read-only notice and omits the corresponding mutation controls. Platform `ADMIN` retains its explicit server and UI override.
- Loading uses a page-local status. Empty draft/history states remain explicit. Fetch error retains the shell and offers retry. Mutation validation stays beside the dialog/form; no failed mutation silently refreshes or clears the administrator's reason.

## Accessibility

The page has one `h1`; tables have captions and headers; inputs have visible labels and described validation. Dialogs use `role=dialog`, labelled titles, focus containment, Escape close, and trigger focus restoration. Status is never color-only. Destructive rollback language describes that a new version will be created.
