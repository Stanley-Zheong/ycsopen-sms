# Phase 46 UI SPEC

Routes:

- `/admin/export-center`
- `/admin/send/details`
- `/admin/receipt/details`
- `/tenant/unsubscribes`
- `/admin/balance-audit`

Canonical selectors:

- `admin-secure-async-export-center-page`
- `admin-secure-async-export-center-cards`
- `admin-secure-async-export-center-table`
- `admin-secure-async-export-center-download`
- `admin-secure-async-export-center-retry`
- `admin-secure-async-send-details-export`
- `admin-secure-async-receipt-export`
- `tenant-secure-async-unsubscribes-export`
- `admin-secure-async-balance-audit-export`

Display contract:

- Export center cards show total, running, complete, failed, record count and supported formats EXCEL/CSV/JSON/PDF.
- Rows show job ID, job name, export type, format, status, progress, record count, file size, created time and failure reason.
- Download is enabled only for completed jobs.
- Retry is enabled only for failed jobs.
- Producer export buttons show “请求安全异步导出” and surface the returned job ID/status in existing message areas.
