# Phase 12 UI Spec

## Routes

- `/tenant/signatures` — tenant signature application, status/history, and usable-channel inspection.
- `/admin/signatures/review` — operator review queue, decisions, and filing matrix.

## Style

Reuse the existing console shell, card, `ratio-table`, form spacing, and neutral blue-gray visual language used by Phase 10/11 pages.

## Selector contract

Every production selector is listed in `UI-ELEMENTS.md`. The required owned selectors are:

- `tenant-signature-lifecycle-signatures-application-submit`
- `admin-signature-lifecycle-signature-review-filters`
- `admin-signature-lifecycle-signature-review-decision`
- `admin-signature-lifecycle-signature-filing-matrix`
- `admin-signature-lifecycle-signature-filing-retry`

Chrome is the only browser target.
