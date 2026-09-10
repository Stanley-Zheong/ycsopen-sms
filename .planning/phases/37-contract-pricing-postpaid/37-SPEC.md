# Phase 37 Spec

Goal: authorized platform users approve a trial conversion into an effective contract; tenant sees contract state; postpaid usage respects the selected billing period and credit ceiling.

Functional contract:

- Billing mode is exactly PREPAID or POSTPAID.
- Contract approval records immutable price-book version, contract number, signed date, and attachment reference.
- POSTPAID requires positive credit limit and billing period MONTHLY or QUARTERLY.
- PREPAID rejects postpaid-only credit/period fields.
- Approved contract changes trial state to CONTRACTED atomically.
- Tenant overview exposes contract status and effective pricing fields.
- Postpaid usage ledger is idempotent by business document and rejects usage above credit ceiling within the effective period.
