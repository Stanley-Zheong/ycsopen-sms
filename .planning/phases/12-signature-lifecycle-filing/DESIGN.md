# Phase 12 Design

## Backend module

- `SignatureLifecycleService` owns application, review, filing, and usable-channel read models.
- `SignatureLifecycleController` exposes tenant and operator endpoints.
- Additive migration extends the existing tables and adds a small `signature_review_history` table.
- Filing provider interaction is represented by explicit request/result endpoints for this phase. A later upstream-provider phase can replace the adapter behind the same state table.

## UI module

- Tenant page `/tenant/signatures`: list, application form, proof field, status/history panel, usable channels.
- Admin page `/admin/signatures/review`: stats cards, filters, review table, decision dialog, filing matrix, filing result controls.

Chrome is the only browser verification target.
