# Phase 37 UI Spec

Routes:

- `/admin/tenant-trial-contracts`: trial and contract approval page.
- `/tenant/overview`: tenant trial and contract overview page.

Primary elements:

- `admin-contract-pricing-tenant-contract-page`
- `admin-contract-pricing-tenant-contract-billing-mode`
- `admin-contract-pricing-tenant-contract-credit-period`
- `admin-contract-pricing-tenant-contract-postpaid-fields`
- `tenant-contract-pricing-overview-contract-status`
- `admin-trial-conversion-workbench-filters`
- `admin-trial-conversion-workbench-query-status`
- `data-table`, `table-empty`, `admin-trial-conversion-workbench-row`
- `admin-trial-conversion-workbench-analysis-dialog`
- `admin-trial-conversion-workbench-row-adjust`, `admin-trial-conversion-workbench-adjust-dialog`, `admin-trial-conversion-workbench-adjust-cancel`
- shared `entity-form`, `form-submit`, and `form-cancel`

Supplemental elements are listed in `UI-ELEMENTS.md`.

Design notes:

- Keep the page route and retained contract-field selectors stable.
- Open analysis, trial adjustment, and conversion from one selected result row;
  repeat tenant name/number and do not expose editable tenant identity.
- Define workbench loading/error/empty/success, analysis
  loading/error/no-data/success, and price loading/error/empty/success states.
- Tenant overview shows contract state as read-only evidence.
