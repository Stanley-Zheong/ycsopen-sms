# Phase 43 UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
|---|---|---|---|---|---|---|---|---|---|---|---|
| admin-custom-reports `/admin/custom/reports` | ADMIN/OPERATOR/FINANCE with `custom-report:menu` + `custom-report:read` | report builder | page/form | report name, metric, supported dimensions, supported measures, tenant, channel, message type, province, period | GET `/console/custom-reports/capabilities`; POST `/console/custom-reports/preview`; POST `/console/custom-reports/definitions`; POST `/console/custom-reports/definitions/{id}/export` | loading/error/previewed/saved/export requested | admin-custom-report-custom-reports-page | OBL-F-11-4-A; REQ-F-11-4 | custom-report-authoring-01 | T-F-11-4-A:playwright | pw-p43-custom-reports |
| admin-custom-reports `/admin/custom/reports` | ADMIN/OPERATOR/FINANCE with `custom-report:read` | results | table/status | aggregate rows, formula, formula version, freshness, quality, drilldown key, accessible columns | Uses aggregate registry-backed preview response; no client-side unsupported column injection | empty/data/stale/fresh/corrected | admin-custom-report-custom-reports-results | OBL-F-11-4-B; REQ-F-11-4 | custom-report-authoring-01 | T-F-11-4-B:integration | pw-p43-results |

Implementation-only selectors:

- `admin-custom-report-custom-reports-nav-menu`: sidebar navigation.
- `admin-custom-report-custom-reports-builder`: report builder card.
- `admin-custom-report-custom-reports-name`: report name input.
- `admin-custom-report-custom-reports-metric`: metric selector.
- `admin-custom-report-custom-reports-tenant`: tenant id filter.
- `admin-custom-report-custom-reports-channel`: channel id filter.
- `admin-custom-report-custom-reports-message-type`: message type filter.
- `admin-custom-report-custom-reports-province`: province filter.
- `admin-custom-report-custom-reports-dimensions`: selected dimensions display.
- `admin-custom-report-custom-reports-measures`: selected measures display.
- `admin-custom-report-custom-reports-preview`: preview action.
- `admin-custom-report-custom-reports-save`: save definition action.
- `admin-custom-report-custom-reports-registry`: aggregate registry card.
- `admin-custom-report-custom-reports-formula`: formula/version display.
- `admin-custom-report-custom-reports-freshness`: freshness display.
- `admin-custom-report-custom-reports-quality`: quality display.
- `admin-custom-report-custom-reports-truncated`: 500-row truncation indicator.
- `admin-custom-report-custom-reports-accessible-table`: accessible table alternative.
- `admin-custom-report-custom-reports-error`: operation error feedback.
- `admin-custom-report-custom-reports-row`: result row.
- `admin-custom-report-custom-reports-saved-definition`: saved definition card.
- `admin-custom-report-custom-reports-export`: export request action.
- `admin-custom-report-custom-reports-export-status`: export request status.
- `admin-custom-report-custom-reports-definition-list`: saved definitions list.
