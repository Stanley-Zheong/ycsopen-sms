# Phase 43 Design

## Backend

- `CustomReportService`
  - Reads active metrics from `statistics_metric_registry`.
  - Allows only hard-coded supported dimensions/measures per metric family.
  - Queries `statistics_aggregates` with validated filters only.
  - Enforces tenant actor scope before querying or exporting.
  - Stores saved report definitions with immutable JSON snapshots.
  - Stores export requests with the saved definition snapshot.

- `CustomReportController`
  - `GET /api/v1/console/custom-reports/capabilities`
  - `POST /api/v1/console/custom-reports/preview`
  - `POST /api/v1/console/custom-reports/definitions`
  - `GET /api/v1/console/custom-reports/definitions`
  - `POST /api/v1/console/custom-reports/definitions/{id}/export`

## Frontend

- Page route: `/admin/custom/reports`
- Primary page id: `admin-custom-report-custom-reports-page`
- Results id: `admin-custom-report-custom-reports-results`
- Accessible result table id: `admin-custom-report-custom-reports-accessible-table`

## Data model

- `custom_report_definitions`: saved immutable definitions.
- `custom_report_export_requests`: export request records; no export file generation.
