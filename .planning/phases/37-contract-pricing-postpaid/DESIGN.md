# Phase 37 Design

Backend:

- `tenant_price_books` stores immutable pricing versions.
- `tenant_contracts` stores one effective tenant contract.
- `postpaid_usage_ledger` stores idempotent usage records against the contract period.
- `ContractPricingService` validates contract fields and postpaid usage credit.

Frontend:

- Existing admin trial/prepaid page gains a compact contract approval section.
- Tenant overview gains a read-only contract status section.

Validation:

- Backend tests cover mode validation, state transition, and postpaid credit ceiling.
- Playwright covers admin contract fields and tenant contract status on local Chrome.
