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
