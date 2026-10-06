# Spirit 06 Decisions

## DR-FE06-001: One Canonical API Key Route

### Status

Accepted

### Decision

`/tenant/api/keys` remains the canonical page. `/tenant/config` redirects to it,
and the sidebar removes the duplicate configuration-overview item. Existing
bookmarks keep working without maintaining two credential pages.

## DR-FE06-002: Separate Secret Handoff From Safe Summary

### Status

Accepted

### Decision

List responses expose `appSecretMask`; create responses add `appSecret` for the
single successful handoff. Frontend types do not model list rows as objects that
may contain plaintext. The Java response string representation redacts the
create-only value. The handoff is deliberately volatile: explicit
acknowledgement clears it while mounted, and navigation, reload, tab close, or
unmount discards it without browser persistence or recovery.

## DR-FE06-003: Tenant-Scoped Credential Audit Readback

### Status

Accepted

### Decision

The tenant API Key API exposes a read-only audit collection backed by
`OperationAuditService`. The query derives the tenant from the authenticated
user and includes only `TENANT_API_KEY` resources. The platform audit route and
its broader permission model remain unchanged. The tenant endpoint is
`GET /api/v1/console/tenant/api-keys/audits`; it returns the newest 100 rows in
descending audit-ID order with only ID, actor, operation, resource ID, result,
and occurrence time.

## DR-FE06-004: Approval Result Uses An Explicit Allowlist

### Status

Accepted

### Decision

The qualification decision projection adds only tenant ID, tenant number,
trial quota, and trial start/end time. The review UI renders those named fields
instead of iterating over the response object. Login passwords, App Secrets,
protected object identifiers, and encrypted values remain absent.

## DR-FE06-005: Reuse The Approved Phase 08/09 Visual Baseline

### Status

Accepted

### Decision

The change reuses the existing tenant shell, cards, tables, form controls, and
`ModalDialog`. The shared dialog gains a viewport backdrop and an explicit
non-dismissible mode so focus and pointer interaction remain inside the active
dialog; default Escape behavior stays compatible for existing owners. It adds
no visual system, mobile contract, or browser target. Desktop Google Chrome at
1440 by 900 remains the acceptance browser.

## DR-FE06-006: Fail Closed On Unknown Create Outcome

### Status

Accepted

### Decision

A definite 4xx create rejection is editable and retryable. A missing response
or 5xx is treated as an unknown outcome: the current create dialog cannot
submit again, list readback runs, and the mounted page retains every unresolved
name after the dialog closes. Reopening the form cannot POST any such name;
the user must inspect and revoke any matching credential or use a new name.
The client never retries the secret-producing POST automatically and cannot
recover a lost plaintext.

## DR-FE06-007: Fail Closed On Any Credential 403

### Status

Accepted

### Decision

A 403 from a key list, audit list, create, or revoke request atomically enters
the page denied state. The page clears credential and audit rows, discards any
one-time secret, closes create/revoke dialogs, ignores later results from
already in-flight reads, and exposes no further mutation action for the
mounted session.

## DR-FE06-008: Preserve Legacy Dialog Positioning

### Status

Accepted

### Decision

The legacy `[role="dialog"].card` rule remains fixed and centered for existing
owners that do not yet use `ModalDialog`; `:not(.modal-dialog)` makes that
exclusion explicit instead of depending on source order or lower-specificity
overrides. Relative positioning, transform reset, and the smaller shadow apply
only to `.modal-dialog` inside the shared viewport backdrop. Google Chrome
verifies both computed-style paths.

## DR-FE06-009: Authorization Reads Gate Credential Mutations

### Status

Accepted

### Decision

Create and revoke controls remain disabled while any authorization read is
unresolved, including non-visual list and audit refreshes after a mutation. An
independent in-flight counter covers reads that deliberately do not show a
loading row, while a synchronous ref also guards event handlers before React
can render the disabled state. A 403 sets a synchronous denied latch before
clearing page state. Every create or revoke completion checks that latch before
publishing a secret, success message, row, or follow-up request, so a result
arriving after a concurrent denial cannot restore privileged state.
