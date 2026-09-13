# Phase 38 Spec

Build a focused finance loop:

1. Finance generates a statement for a tenant and accounting period from `postpaid_usage_ledger`.
2. The statement preserves source counts, billed amount, billing mode, price-book version, and period.
3. Tenant and finance can confirm agreement; both confirmations are required before `CONFIRMED`.
4. Tenant can submit a difference with type, amount, note, evidence, and owner.
5. Finance resolves open differences and returns the statement to pending confirmation.
6. Finance starts, completes, and marks settlement received exactly once with evidence.
7. Tenant requests an invoice only against settled/paid entitlement.
8. Finance issues invoice number/status/time without exceeding eligible amount.

Verification is executable only:

- Backend service state tests.
- Migration test.
- Frontend unit tests.
- Chrome Playwright tests.
- PRD and UI validators.
