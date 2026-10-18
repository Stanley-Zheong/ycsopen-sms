# Phase 37 Design

Backend:

- `tenant_price_books` stores immutable pricing versions.
- `tenant_contracts` stores one effective tenant contract.
- `postpaid_usage_ledger` stores idempotent usage records against the contract period.
- `ContractPricingService` validates contract fields and postpaid usage credit.
- It owns the issue #122 candidate/analysis read model, active-price projection,
  and the single eligibility rule used by both list and locked approval.
- Metrics use `[trial.start_at,trial.end_at)`. Message count is all tenant tasks
  created in that window; delivered tasks are successes; complaint count is
  all tenant complaints in the same window. Ratios are null at a zero message
  denominator. Analysis returns daily trend, status distribution, and the
  newest 100 complaint details.
- Configuration snapshot is `TRIAL-SNAPSHOT-V1-` plus the first 16 uppercase
  SHA-256 hex characters of quota/start/end content. It is not an optimistic
  lock revision.
- Approval locks tenant/account, trial, and submitted price book in that order,
  then checks VERIFIED/NORMAL state, lifecycle, duplicate contract, and ACTIVE
  price. Contract and both lifecycle writes share one transaction.
- `TenantReviewService` provisions the authoritative trial row at tenant
  approval. Legacy tenant trial columns are a compatibility fallback and are
  synchronized by trial adjustment.

Frontend:

- Existing admin trial/prepaid page gains a filterable workbench with explicit
  query states and selected-row analysis, trial adjustment, and conversion
  dialogs. Dialog tenant identity is read-only.
- Tenant overview gains a read-only contract status section.

Validation:

- Backend tests cover mode validation, state transition, and postpaid credit ceiling.
- Playwright covers filter/analysis/conversion/readback, rejection
  preservation, query recovery, analysis states, and tenant contract status in
  bundled Chromium.

Issue #122 APIs are platform-only `GET /console/contracts/workbench`,
`GET /console/contracts/workbench/tenants/{id}/analysis`, and
`GET /console/contracts/price-books`, plus the existing approval POST. Reads
require `trial-prepaid:read`; approval requires `trial-prepaid:write`.

Schema migrations: none. Attachment input is an externally provisioned opaque
single-segment `oss://contracts/<object>` reference; no upload or object
existence claim is made.
