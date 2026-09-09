# Phase 20 UI Spec

## Route

- `/admin/status-codes`

## Required selectors

- `admin-provider-status-taxonomy-status-codes-page`
- `admin-provider-status-status-codes-version-history`

## Interaction contract

1. The page renders current active mappings and version history.
2. The import button submits CSV-derived mapping rows.
3. The normalize button shows the effective taxonomy result.
4. The export button registers an export request.
5. The version history section remains visible for conflict/effective-date inspection.

