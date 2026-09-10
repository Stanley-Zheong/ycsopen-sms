# Phase 34 Design

## Tables

- `statistics_metric_registry`: canonical metric metadata, including source tables, formula, freshness rule, permission scope, and formula version.
- `statistics_aggregates`: hourly aggregate facts with tenant/channel/resource/geography dimensions, counts, fee, latency, source version, correction identity, drilldown key, and quality state.
- `statistics_correction_events`: idempotent correction-event evidence keyed by correction identity.

## Aggregation rules

- Bucket: task or rejected-submit `created_at` truncated to hour.
- Final state: latest `delivery_reports.report_status` wins; `message_tasks.send_status` is fallback.
- Fee: confirmed `billing_records.amount / 1000` wins; `message_tasks.cost` is fallback.
- Accepted/send counts: task-backed records.
- Rejected counts: rejected submit records that do not have message tasks.
- Late correction: task `version > 1` marks aggregate `CORRECTED`.
- Idempotency: rebuilding a window deletes existing Phase34 metric rows in the window before recomputing.

## APIs

- `GET /api/v1/console/statistics/metrics`
- `GET /api/v1/console/statistics/aggregates`
- `POST /api/v1/console/statistics/aggregates/rebuild`
- `POST /api/v1/console/statistics/corrections`

Read access is restricted to admin/operator/finance roles. Rebuild and correction write access is restricted to admin/operator roles.
