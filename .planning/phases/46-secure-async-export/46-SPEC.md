# Phase 46 SPEC — Secure asynchronous export

## Scope

Package: `secure-async-export`

Goal: every scoped export action creates an authorized immutable snapshot and a secure asynchronous export job with downloadable encrypted artifact metadata.

## Owned obligations

- OBL-F-7-2-C — send-detail export creates a secure asynchronous snapshot job.
- OBL-F-7-4-C — receipt export creates a secure asynchronous snapshot job.
- OBL-F-7-8-A — export center exposes totals, running/complete/failed, records, job ID, name, type, EXCEL/CSV/JSON/PDF, progress, size, created time.
- OBL-F-7-8-B — large/failing jobs preserve snapshot, expose partial failure and support idempotent retry.
- OBL-F-7-8-C — artifacts reconcile headers, rows, typed values, ordering, masked fields, encryption, expiry download and audit.
- OBL-F-7-9-B — tenant unsubscribe export uses the secure export center with tenant scope.
- OBL-F-8-9-B — balance audit export uses the secure export contract.
- OBL-EDGE-EXPORT-FAILURE — failed export exposes reason and retry.
- OBL-DATA-10-7-EXPORT — export job stores type, actor, authorization snapshot, format, state, progress, count, size, encryption, retry, identity and creation time.

## Non-goals

- No archive/retention/restore.
- No external object-store production adapter in this phase.
- No multi-browser validation; Chrome only.

## Deliverables

- `export_tasks` schema extension for secure jobs.
- `SecureAsyncExportService` and `/api/v1/console/exports` controller.
- Integration of send detail, receipt detail, tenant unsubscribe and balance audit export entry points.
- Admin export center UI with stable test IDs.
- Unit, migration and local-Chrome Playwright verification.
