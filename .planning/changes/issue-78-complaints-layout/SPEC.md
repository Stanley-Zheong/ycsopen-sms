# Issue 78 Complaint Layout

GitHub issue #78 restructures `/admin/complaints` without changing the then-current
complaint workflow semantics. Issue #124 supersedes the page-level handling-evidence
card: complaint intake, attribution and requirements, complaint list, and the
selected-case workspace now form the four ordered cards. Existing layout geometry
continues to apply; Issue #124 owns the new values, validation, permissions, API
effects, and feedback.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-78-complaints-layout | Render four titled cards in intake, attribution/requirements, complaint-list, and selected-case-workspace order. Use a responsive field grid with 40 px single-line controls, place the registration action in the corresponding action cell, and keep the complaint table within the page width. | At 1440 px, all four cards and headings are visible in order without overlap; each intake input/select and the registration button is 40 px ±2 px high; the action aligns with its neighboring field control; the table exposes its headers through `data-table`, and an empty result exposes `table-empty`; empty and populated long-value states have no horizontal overflow, escaped cells, or overlapping actions. |

## Layout contract

- Cards use the console's existing surface, border, radius, and spacing tokens.
- Issue #87 supersedes the original local column count: intake and
  attribution/requirements use the shared 4/3/2/1 Admin field-grid breakpoints
  while preserving the same field order and action cell. Issue #124 owns the
  selected-case workspace layout.
- The registration action occupies the final attribution-grid cell so it stays
  adjacent to the fields and shares their control baseline.
- Table headers remain rendered during loading, failure, and empty states.
  Fixed column proportions, truncated long values, and wrapped row actions
  prevent content from widening the page.
- Shared acceptance markers `entity-form`, `form-actions`, `form-submit`,
  `data-table`, and `table-empty` are attached to their semantic elements;
  Phase 41 namespaced selectors remain on page-owned regions.

## Exclusions

- Issue #78 itself does not change complaint behavior. Issue #124 owns the later
  handling, remediation, recovery, authorization, and API amendments.
- This issue does not add pagination, sorting, filtering, or table columns.

## Verification boundary

The browser case renders the real React page at 1440 px and intercepts
authentication, dashboard, and complaint APIs because this issue is limited to
rendered layout. It checks card order, control geometry, action alignment,
table headers and empty state, populated long-value cell truncation, action
overlap, and document overflow. It does not claim backend-service acceptance.
Run from `web/`:

```bash
LD_LIBRARY_PATH=<chromium-libraries> YCSOPEN_USE_BUNDLED_CHROMIUM=true YCSOPEN_WEB_PORT=<unused-port> YCSOPEN_E2E_ISOLATED=1 ./node_modules/.bin/playwright test test/scripts/complaint-case.spec.ts --config playwright.config.ts --project=bundled-chromium --workers=1 --grep pw-issue-78-complaints-layout --timeout=120000
```
