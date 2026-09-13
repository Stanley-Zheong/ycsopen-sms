# Phase 47 UI SPEC

Route:

- `/admin/archive`

Required page regions:

- Policy card for data domain, source table, retention days, hot months and legal hold summary.
- Scan card for tenant filter, status filter and scan/archive action.
- Manifest table with id, domain, partition, status, row count, checksum, retention date, deletion eligibility, failure reason and row actions.

Stable selectors:

- `admin-retention-archive-page`
- `admin-retention-archive-policy-card`
- `admin-retention-archive-manifest-restore`

States:

- Loading policies/manifests.
- Successful policy save, scan, verify, restore and export.
- Failed/corrupted manifest with disabled restore/export.
