# Issue 80 Review-History Empty Table

GitHub issue #80 restores the Admin review-history page's query and empty-table
contract. The page reuses the shared `QueryPanel`, keeps its existing protected
review-history requests and permissions, and renders the same eight table
columns during loading, failure, empty, and populated states.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-80-review-history-empty-table | `/admin/review-history` exposes working search/reset actions and an eight-column data table at all times. An empty successful response renders “暂无审核记录” inside one table row whose cell spans all eight columns. | In the Chrome Playwright case, the operator expands the query panel, verifies all controls and action buttons are 40 px within the documented tolerance, confirms draft input does not query until search is submitted, then resets to the default empty result. The case reads all eight column headers and the spanning empty row, verifies the query actions, header, and empty row do not overlap, and proves the document width does not exceed the viewport. |

## Scope

- Restore `ResourceReviewHistoryPage` to the existing shared query-panel
  interaction and retain the Phase 15 protected API, permission, pagination,
  and detail behavior.
- Add the `query-fields` and `query-actions` aliases required by
  `docs/frontend页面实现规范.md` to the shared query-panel DOM without removing
  existing selectors.
- Keep the review-history table header mounted and represent loading, failure,
  and empty results as full-width table rows.
- Add focused unit and Chrome Playwright coverage for the empty result.

The global query-panel migration requested by issue #77, backend changes, new
review-history fields, export, sorting, and changes to review decisions are
outside this change.

## Verification boundary

The Playwright case renders the production React route in the repository's
Chrome project and intercepts account and review-history API calls with
deterministic fixtures, including the default empty result. It proves DOM structure, query behavior, browser
geometry, and horizontal containment. It does not claim live backend or
database integration.
