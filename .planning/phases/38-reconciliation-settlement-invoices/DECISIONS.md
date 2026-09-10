# Phase 38 Decisions

- Reuse one finance workbench for `/admin/reconciliation`, `/admin/settlements`, and `/admin/invoices`; separate implementation pages would add navigation duplication without functional gain.
- Reuse one tenant workbench for `/tenant/statements` and `/tenant/invoices`; invoice request depends on selected statement.
- Use `postpaid_usage_ledger` as the source-backed billing input for this phase.
- Do not implement export file generation; export center integration is out of this phase.
- Do not add mobile scope or non-Chrome browser validation.
