# Phase 38 Context

Phase 38 owns `reconciliation-settlement-invoices`: source-backed statements, tenant/finance reconciliation, postpaid settlement state, and invoice request/issue state.

Upstream dependencies already available in code:

- Phase37 `postpaid_usage_ledger` supplies source amounts by tenant and accounting period.
- Phase37 `tenant_contracts` supplies effective billing mode and price-book version.
- Phase2 navigation/test-id rules are reused.

Scope control:

- No export file generation.
- No cost/profit analytics.
- No fee warning or credit block rules.
- No mobile implementation.
- Chrome-only UI verification.
