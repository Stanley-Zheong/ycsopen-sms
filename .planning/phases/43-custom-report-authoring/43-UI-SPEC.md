# Phase 43 UI Spec

## Page

- Route: `/admin/custom/reports`
- Page test id: `admin-custom-report-custom-reports-page`
- Role: `ADMIN`, `OPERATOR`, `FINANCE`
- Permissions: `custom-report:menu`, `custom-report:read`; write actions use `custom-report:write` server-side policy.

## Elements

- Builder card: `admin-custom-report-custom-reports-builder`
- Name input: `admin-custom-report-custom-reports-name`
- Metric selector: `admin-custom-report-custom-reports-metric`
- Tenant filter: `admin-custom-report-custom-reports-tenant`
- Channel filter: `admin-custom-report-custom-reports-channel`
- Message type filter: `admin-custom-report-custom-reports-message-type`
- Province filter: `admin-custom-report-custom-reports-province`
- Dimensions display: `admin-custom-report-custom-reports-dimensions`
- Measures display: `admin-custom-report-custom-reports-measures`
- Preview action: `admin-custom-report-custom-reports-preview`
- Save action: `admin-custom-report-custom-reports-save`
- Registry/formula card: `admin-custom-report-custom-reports-registry`
- Formula display: `admin-custom-report-custom-reports-formula`
- Results region: `admin-custom-report-custom-reports-results`
- Freshness display: `admin-custom-report-custom-reports-freshness`
- Quality display: `admin-custom-report-custom-reports-quality`
- Truncation display: `admin-custom-report-custom-reports-truncated`
- Accessible table: `admin-custom-report-custom-reports-accessible-table`
- Error feedback: `admin-custom-report-custom-reports-error`
- Result row: `admin-custom-report-custom-reports-row`
- Saved definition card: `admin-custom-report-custom-reports-saved-definition`
- Export action: `admin-custom-report-custom-reports-export`
- Export status: `admin-custom-report-custom-reports-export-status`
- Definition list: `admin-custom-report-custom-reports-definition-list`

## States

- Empty: no preview rows yet.
- Data: preview table renders aggregate rows.
- Fresh/corrected/stale: displayed from backend `qualityState`.
- Saved: saved definition card appears.
- Export requested: status displays `REQUESTED`.
