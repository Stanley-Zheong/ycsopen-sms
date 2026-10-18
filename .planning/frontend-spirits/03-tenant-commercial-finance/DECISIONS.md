# Spirit 03 Decisions

## DR-FE03-001: Commercial State Is Not A Single Balance

### Status
Accepted

### Context
PRD V2 separates trial quota, prepaid balance, postpaid credit, contract pricing, and billing snapshots.

### Decision
Frontend finance views must render these as separate concepts with labels and source notes. A single “余额” display is insufficient when multiple commercial controls affect sending eligibility.

### Consequences

- Finance and tenant pages need explicit terminology alignment.
- Tests must assert the correct label, not only numeric rendering.

## DR-FE03-002: Trial Conversion Is Tenant-Bound And Server-Gated

### Status
Accepted

### Decision

The workbench lists persisted trial accounts joined to tenant and account
state. The server computes conversion eligibility and repeats it under lock;
the browser cannot supply a tenant ID, price version, or configuration label
that was not selected from the server response. The dialog displays immutable
tenant name/number and accepts only ACTIVE price-book options.

Trial configuration is shown as a content address over quota/window values,
not the optimistic-lock revision. Attachment input is an externally
provisioned single-segment reference, not an upload control.

### Consequences

- Stale UI state cannot bypass tenant/account lifecycle, duplicate-contract,
  trial, or price-book checks.
- Contract, trial, and tenant lifecycle changes are atomic; authenticated HTTP
  audit remains a separate fail-closed concern.
- Zero-message ratios are unavailable rather than a misleading zero.
