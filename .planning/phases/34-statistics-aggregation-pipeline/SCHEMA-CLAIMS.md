# Phase 34 Schema Claims

Schema owner: `SCHEMA-P34 | Statistics aggregates | V4300-V4399`.

Claims:

- V4300 creates `statistics_metric_registry`.
- V4300 creates `statistics_aggregates`.
- V4300 creates `statistics_correction_events`.
- V4300 does not alter or drop existing source tables.
- Source-table reads are limited to `message_submits`, `message_tasks`, `delivery_reports`, and `billing_records`.
