# Phase 45 UI SPEC

Route: `/admin/dashboard`

Canonical surfaces:

- Channel complaint-ratio table: `admin-complaint-ratio-dashboard-complaint-ratio-channel`
- Tenant complaint-ratio table: `admin-complaint-ratio-dashboard-complaint-ratio-tenant`
- Threshold/version summary: `admin-complaint-ratio-dashboard-complaint-ratio-threshold`
- Month/TopN/refresh controls: `admin-complaint-ratio-dashboard-complaint-ratio-period`
- Drill-down dialog: `admin-complaint-ratio-dashboard-complaint-ratio-drilldown`

Display contract:

- Ratio is shown as per-mille with two decimals.
- `COMPLETE` rows can become actionable when threshold result is `BREACHED`.
- `ZERO_DENOMINATOR` and `UNKNOWN` rows show non-actionable state and disabled pause action.
- Drill-down shows only complaints for the selected dimension and natural month.
