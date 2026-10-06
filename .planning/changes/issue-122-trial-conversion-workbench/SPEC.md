# Issue 122 Trial Conversion Workbench

This change closes the missing operational loop in Phase 37. Canonical owners
remain `contract-pricing-postpaid-02`, `contract-pricing-postpaid-04`, and
`contract-pricing-postpaid-05`.

## Goal

An authorized platform operator can find a persisted trial tenant, inspect its
trial-period message and complaint evidence, and convert that selected tenant
to a formal prepaid or postpaid contract without typing a tenant identity or
an arbitrary price/configuration version.

## Contract

- Filters cover tenant name/number, sales owner, industry, and trial status.
- Candidate rows expose tenant identity, sales/industry, traceable trial
  configuration, quota/window, trial-window metrics, freshness/quality,
  lifecycle state, and server-owned eligibility reasons.
- Analysis exposes daily trend, message status distribution, complaint detail,
  source registry, freshness, and loading/error/no-data states.
- Trial adjustment and conversion are opened from a concrete row. Tenant
  identity is read-only in both dialogs.
- Conversion selects an active server-returned price book and collects contract
  number, signed date, protected attachment reference, billing mode, and the
  postpaid-only credit/period fields.
- Eligibility is rechecked under lock. Contract creation and both lifecycle
  transitions commit or roll back together; the existing console interceptor
  records the authenticated HTTP outcome separately and fail closed.

## Boundaries

- No schema migration, CRM workflow, contract amendment, settlement, invoice,
  payment gateway, or external upload implementation.
- The attachment is an externally provisioned single-segment
  `oss://contracts/<object>` reference; object existence is not claimed.
- The configuration identifier is a SHA-256 content address over persisted
  quota/window values, not the optimistic-lock revision.
