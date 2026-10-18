# Issue 122 UI Elements

| Region | Contract | States | Stable selectors |
|---|---|---|---|
| Query | name/number, sales, industry, status; submit/refresh | loading/error/empty/success | `admin-trial-conversion-workbench-filters`, `admin-trial-conversion-workbench-query-status` |
| Results | source-backed candidate table and row actions | eligible/ineligible/complete/incomplete/no-data | `data-table`, `table-empty`, `admin-trial-conversion-workbench-row`, `admin-trial-conversion-workbench-row-analysis`, `admin-trial-conversion-workbench-row-adjust`, `admin-trial-conversion-workbench-row-convert` |
| Analysis | selected identity, trend, statuses, complaints, provenance | loading/error/no-data/success | `admin-trial-conversion-workbench-analysis-dialog`, `admin-trial-conversion-workbench-analysis-close` |
| Trial adjustment | selected identity, positive quota, ordered start/end | validation/pending/error/success | `admin-trial-conversion-workbench-adjust-dialog`, `entity-form`, `form-submit`, `form-cancel`, `admin-trial-conversion-workbench-adjust-cancel` |
| Conversion | selected identity, active price, contract and conditional billing fields | price loading/error/empty, validation/pending/rejected/success | `admin-trial-conversion-workbench-selected-tenant`, `entity-form`, `form-submit`, `form-cancel`, retained `admin-contract-pricing-tenant-contract-*` selectors |
