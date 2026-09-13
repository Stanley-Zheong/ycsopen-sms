# Phase 46 DESIGN

## Backend

`SecureAsyncExportService` owns job creation, immutable snapshot persistence, artifact rendering, AES-GCM encryption, retry and download authorization checks.

Supported first-release formats are EXCEL, CSV, JSON and PDF. The EXCEL/PDF implementations are protected textual test artifacts in this phase; file-format-specific production encoders can replace the renderer without changing the snapshot/job contract.

## Frontend

`/admin/export-center` shows export cards, filters, rows, encrypted download and failed-job retry.

Scoped producer entry points:

- `/admin/send/details` — send-detail export.
- `/admin/receipt/details` — receipt export.
- `/tenant/unsubscribes` — tenant-scoped unsubscribe export.
- `/admin/balance-audit` — balance-audit export.

## Security boundaries

- Source rows are copied into an immutable source digest and artifact manifest.
- Authorization context is stored with actor, tenant, producer and permission.
- Phone/mobile-like fields are masked before artifact rendering.
- Download returns encrypted artifact payload only and rechecks tenant scope plus expiry.
- Retry only accepts failed jobs and records retry count.
