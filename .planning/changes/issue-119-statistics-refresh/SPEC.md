# Issue 119 Statistics Refresh

GitHub issue `#119` amends the completed Phase 34 statistics pipeline and the
Phase 44 operational dashboard. The current product can persist successful
message tasks while leaving `statistics_aggregates` empty indefinitely, then
renders aggregate absence as a real zero on `/admin/dashboard`.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| issue-119-automatic-refresh | A serialized scheduler discovers accepted task changes, late delivery/billing changes, and durable rejected submissions, then rebuilds every affected Asia/Shanghai business date. | Existing unaggregated rows are recovered after deployment; repeated refresh is idempotent; a late receipt replaces the earlier aggregate without double counting. |
| issue-119-business-day | Source timestamps remain UTC while hourly buckets and `bucket_date` use the Asia/Shanghai business clock. | UTC events around 16:00 map to the next Shanghai date and appear on that date's dashboard. |
| issue-119-refresh-state | Each successful business-date rebuild persists a checkpoint even when there are no source rows. Dashboard reads distinguish `NOT_REFRESHED`, `EMPTY`, `STALE`, and `FRESH`. | Missing refresh is never displayed as a true zero; successful empty refresh is explicit; stale values remain visible only when the checkpoint contains aggregate rows, with a warning and source timestamps. |
| issue-119-dashboard-refresh | The admin dashboard exposes source, business date/time zone, refresh state, freshness, and stable selectors; manual refresh replaces the response. | React and Google Chrome cases prove loading/error, not-refreshed, empty, stale, fresh, and data replacement states. |

## Scope

- Additive V6700 refresh coordination/checkpoint and submit-claim lease persistence.
- Phase 34 scheduler, source-change discovery, serialized/idempotent rebuild,
  Asia/Shanghai bucketing, and durable HTTP rejection evidence.
- Phase 44 platform dashboard response and `/admin/dashboard` state rendering.
- Focused Java, React, and Google Chrome Playwright coverage, plus API/operator
  documentation and Spirit 04 records.

## Out of Scope

- Replacing the existing aggregate schema or report/custom-report contracts.
- Changing delivery, billing, routing, retry, or provider state semantics.
- Adding dashboard polling or a state-changing refresh endpoint.
- Copying fixtures, configuration, or implementation from a private system.
