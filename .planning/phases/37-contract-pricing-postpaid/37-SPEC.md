# Phase 37 Spec

Goal: authorized platform users approve a trial conversion into an effective contract; tenant sees contract state; postpaid usage respects the selected billing period and credit ceiling.

Issue #122 closure goal: platform users discover a persisted trial tenant from
a filterable workbench, inspect source-backed trial-period evidence, and
confirm a contract bound to that selected tenant and an active price book.

Functional contract:

- Billing mode is exactly PREPAID or POSTPAID.
- Contract approval records immutable price-book version, contract number, signed date, and attachment reference.
- POSTPAID requires positive credit limit and billing period MONTHLY or QUARTERLY.
- PREPAID rejects postpaid-only credit/period fields.
- Approved contract changes trial state to CONTRACTED atomically.
- Tenant overview exposes contract status and effective pricing fields.
- Postpaid usage ledger is idempotent by business document and rejects usage above credit ceiling within the effective period.
- Candidate and analysis metrics use the half-open persisted trial interval and
  expose source, freshness, and explicit quality.
- Conversion requires VERIFIED tenant state, NORMAL account state, eligible
  trial/lifecycle state, no contract, and an ACTIVE selected price book.
- Approval locks and rechecks current state; contract, trial, and tenant
  lifecycle mutations are atomic.
