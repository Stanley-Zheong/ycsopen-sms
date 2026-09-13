# Issue 78 Complaint Layout

GitHub issue #78 restructures `/admin/complaints` without changing complaint
workflow semantics. Complaint intake, attribution and requirements, handling
evidence, and the complaint list become four ordered cards. Existing values,
validation, permissions, API calls, mutation behavior, and feedback remain
owned by the Phase 41 complaint page.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-78-complaints-layout | Render four titled cards in intake, attribution/requirements, handling evidence, and complaint-list order. Use a responsive field grid with 40 px single-line controls, place the registration action in the corresponding action cell, and keep the complaint table within the page width. | At 1440 px, all four cards and headings are visible in order without overlap; each input/select and the registration button is 40 px ±2 px high; the action aligns with its neighboring field control; the table exposes all seven headers through `data-table`, and an empty result exposes `table-empty`; empty and populated long-value states have no horizontal overflow, escaped cells, or overlapping actions. |

## Layout contract

- Cards use the console's existing surface, border, radius, and spacing tokens.
- Intake, attribution/requirements, and handling evidence use the same
  responsive three-column field grid, reducing to two columns at 1100 px and
  one column at 720 px.
- The registration action occupies the final attribution-grid cell so it stays
  adjacent to the fields and shares their control baseline.
- Table headers remain rendered during loading, failure, and empty states.
  Fixed column proportions, truncated long values, and wrapped row actions
  prevent content from widening the page.
- Shared acceptance markers `entity-form`, `form-actions`, `form-submit`,
  `data-table`, and `table-empty` are attached to their semantic elements;
  Phase 41 namespaced selectors remain on page-owned regions.

## Exclusions

- Complaint creation, handling, remediation, recovery, authorization, and API
  contracts are unchanged.
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
