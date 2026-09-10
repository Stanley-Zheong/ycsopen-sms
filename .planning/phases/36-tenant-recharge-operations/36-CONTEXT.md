# Phase 36 Context

Phase 36 owns `tenant-recharge-operations` and only covers PRD F-8.3 recharge submission and finance review.

Dependencies used:

- Phase 22 `prepaid_accounts` and `balance_audit_entries`.
- Existing `trial-prepaid:read/write` permissions for finance/account pages.
- Chrome-only Playwright verification.

Scope control:

- No payment gateway.
- No invoice/settlement.
- No mobile/browser matrix.
