# Phase 42 UI Spec

Primary route: `/admin/tenant-risk`

Design source: local HTML/Pencil artifact under `design-output/`.

Page regions:

- Header: breadcrumb, title, description, refresh action.
- Rule panel: tenant id, rule name, metric, threshold, duration, action, notification targets, save action.
- Source evaluation panel: numerator, denominator, source window, source key, source registry, evaluate action.
- Rules table: current rule list.
- Episode/pause detail: data quality, rate, pause status, episode table, source snapshot.
- Recovery panel: review id, recovery note, recovery action.
- Complaint-ratio flow entry: stable element id reserved for tenant complaint ratio source handoff into this page.

Chrome-only acceptance:

- Playwright runs against project `local-google-chrome`.
- All automation selectors are documented in `UI-ELEMENTS.md`.
