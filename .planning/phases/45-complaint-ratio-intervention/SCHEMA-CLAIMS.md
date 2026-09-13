# Phase 45 Schema Claims

| Object | Claim |
|---|---|
| `complaint_ratio_stats.data_quality` | Persists `COMPLETE`, `ZERO_DENOMINATOR`, or `UNKNOWN` so non-actionable data is auditable. |
| `complaint_ratio_stats.source_registry` | Persists the source registry used by the dashboard. |
| `channel_pause_events.source_event_key` | Provides idempotent exact channel pause evidence for ratio intervention. |
| `tenant_risk_episodes.source_key` | Provides idempotent exact tenant pause/alert evidence for ratio intervention. |
