# Phase 34 Spec

Phase 34 must expose a canonical statistics aggregation pipeline that can be rebuilt idempotently for a time window.

Required behavior:

- Register metric metadata for resource usage, channel delivery, and tenant behavior.
- Build hourly aggregate rows from existing submit/task/receipt/billing source records.
- Use final delivery report state when present; fall back to task send status.
- Use confirmed billing amount when present; fall back to task cost.
- Count rejected submissions that do not already have tasks.
- Preserve source version and correction identity in each aggregate row.
- Mark aggregate rows as `CORRECTED` when source task versions indicate correction.
- Provide console APIs to read metric registry rows, read aggregate rows, rebuild a window, and record correction events.
- Keep rebuild idempotent: re-running the same window replaces scoped aggregate rows, not duplicates them.

Data contract:

- Migration namespace: V4300-V4399.
- Tables: `statistics_metric_registry`, `statistics_aggregates`, `statistics_correction_events`.
- No destructive changes to source tables.
