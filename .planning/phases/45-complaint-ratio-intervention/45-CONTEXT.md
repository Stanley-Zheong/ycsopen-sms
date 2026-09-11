# Phase 45 Context

Dependencies are present from phases 11, 34, 35, 41, 42, 44:

- `complaint_ratio_stats` exists in V1 and stores month/dimension/count/ratio/threshold/calculated_at.
- `complaints` stores tenant/channel attribution and `attribution_quality`.
- `channel_pause_events` stores channel pause evidence and supports `trigger_type='RATIO'`.
- `tenant_risk_episodes` and `alert_records` store tenant-level pause/alert evidence.
- Phase 44 dashboard already mounts complaint-ratio panels under `/admin/dashboard`.

Implementation decision: keep the original `ComplaintRatioService` as the calculation owner and add `ComplaintRatioDashboardService` as the read/intervention boundary.
