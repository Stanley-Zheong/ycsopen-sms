# Phase 45 Design

`ComplaintRatioService` remains the aggregation writer. `ComplaintRatioDashboardService` is the dashboard/intervention read model using `JdbcTemplate` to avoid adding duplicated repositories for cross-table evidence.

Intervention rules:

- Only `data_quality='COMPLETE'` and breached threshold rows are actionable.
- Channel intervention updates the exact channel and inserts one `channel_pause_events` row keyed by complaint-ratio source.
- Tenant intervention inserts an alert record, freezes the exact tenant, and inserts one `tenant_risk_episodes` row keyed by complaint-ratio source.
- Duplicate source keys return existing evidence instead of creating duplicate pauses.
