# Phase 37 Context

Phase 37 owns `contract-pricing-postpaid`: trial conversion, contract fields, immutable price-book version, billing mode, and postpaid credit ceiling.

Dependencies used:

- Phase 22 trial state and prepaid finance permissions.
- Existing tenant overview route.
- Chrome-only Playwright validation.

Scope control:

- No reconciliation, settlement, invoice, export, or fee-warning implementation.
- No payment gateway.
- No broad finance rewrite.
