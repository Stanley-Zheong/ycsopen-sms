# Issue 79 Finance Layout Design

## Information architecture

The existing page order remains: breadcrumb and page title, one query card,
finance-summary data card, channel-summary data card, and the optional source
drilldown card. Query and source actions are co-located visually; query applies
the four-field draft, while source drilldown preserves Phase 39 behavior and
uses the currently applied filter. The two always-present summary tables remain
separate business regions with independent headings and identical query-state
feedback.

## Shared components and tokens

- Reuse `QueryPanel` and `QueryField`; add only optional action/label slots and
  generic `query-fields`/`query-actions` hooks needed by the referenced standard.
- Preserve every Phase 39 page-specific selector on its existing native control,
  table, or row. For the query action, keep `query-submit` on the native shared
  button and retain the old Phase 39 apply selector as the shared component's
  existing click-compatible nested alias.
- Use existing color, border, spacing, radius, focus, and 40 px control tokens.
  Finance-only table CSS owns fixed-layout cells, padding, separators, truncation,
  and responsive wrapping without changing unrelated tables.

## States and accessibility

- Query controls keep visible associated labels and native date/text inputs.
- The four-field shared panel keeps its existing disclosure behavior but starts
  expanded on this page, preserving the existing immediately available actions.
  The toggle controls the fields and every action as one keyboard-operable region.
- Each table always renders its caption-equivalent accessible label and header.
- `role="status"` reports loading, empty, and populated counts; load failure uses
  `role="alert"` and a semantically identified retry button for each table.
  Empty data is a `table-empty` row spanning all columns.
- Long price/formula values are truncated in cells and retain their complete
  value through `title`. Color is not the only status signal.

## Responsive and implicit-requirement decisions

- Desktop Chrome at 1440×900 is the issue acceptance viewport. The query grid
  follows the existing 3/2/1-column shared breakpoints and the action group stays
  intact.
- All three tables use fixed layout and ellipsis rather than a document-width
  minimum, so opening the long-formula drilldown cannot introduce horizontal
  scrolling. Tests exercise 1440 px plus the 1000 px and 800 px breakpoint
  regions; narrow layouts retain headers and expose full long-cell values
  through titles.
- Sorting, pagination, selection, bulk actions, and export are not added because
  Issue #79 changes presentation only and the current Phase 39/API contract does
  not define those behaviors.
- No permission, audit, destructive action, mutation, or tenant-boundary design
  changes are introduced.
