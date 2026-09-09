# Phase 29 Claude Review

## Initial review findings

Claude review identified blocker/high issues:

- Scheduled tasks were being submitted immediately instead of remaining pending.
- Bulk creation used one transaction around all valid item submissions, creating rollback/tracking risk.
- Preview/import snapshot exposed raw phone numbers.
- Import metadata validation accepted missing size or missing scan verdict.
- CANCEL/FAIL/RESTART and scheduled behavior needed executable tests.

## Fixes applied

- `BulkScheduledTaskService.create` no longer has an outer transaction and records the batch before item submission.
- Scheduled tasks with `scheduleAt` are returned as `PENDING` and do not call `MessageSubmitService.submit`.
- `PreviewRow` exposes only `maskedPhone`; stored import snapshots are generated from masked preview rows.
- Import validation now rejects missing size, oversized files, unsupported extensions, missing scan verdict, and non-`CLEAN` scan verdict.
- Tests now cover scheduled no-submit behavior, import metadata rejection, all-invalid imports, mid-batch failure persistence, and CANCEL/FAIL/RESTART controls.

## Closure review

Claude closure review verdict: no remaining blocking or high-severity issue from the prior review list.

Non-blocking observations retained:

- No due-time scheduled dispatcher exists in this phase. Current behavior is intentionally state-correct: scheduled tasks remain `PENDING` and are not silently sent early.
- Mid-batch submit failure is fail-fast; already-linked items remain tracked and the batch is marked `FAILED`.
