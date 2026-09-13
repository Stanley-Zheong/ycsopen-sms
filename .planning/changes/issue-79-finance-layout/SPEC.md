# Issue 79 Finance Query and Table Layout

GitHub issue #79 amends the existing Phase 39 `/admin/finance` production UI.
The page keeps its current financial-summary and source-drilldown APIs,
permissions, filters, formulas, price versions, and numeric formatting. This
change only owns the query/action layout, table readability, rendered result
states, and page overflow behavior.

Issue #79 explicitly identifies `docs/frontend页面实现规范.md` as the acceptance
baseline. Documentation PR #82 merged that contract to `main` at
`e91ae04d28d7b12e8789303d0405fc94454f2837`; this issue branch is based on that
revision and consumes the repository-local document directly.

## Behaviors

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-79-finance-query-layout | The four existing finance filters are grouped in `query-panel`/`query-fields`; query, reset, and source drilldown share `query-actions`. Native controls keep their existing selectors; the shared query action selector remains on the native query button and the Phase 39 apply selector remains a click-compatible alias inside it. The finance panel is initially expanded so existing direct actions remain available. | In rendered Chrome, start date, end date, tenant ID, and channel ID are initially visible and each is 40 px±2 px high. Query and source-drilldown buttons are in the same action region, share the same top coordinate, and have the same 40 px±2 px height at 1440 px; the expanded panel remains within the document at 1000 px and 800 px. |
| issue-79-finance-table-layout | The finance-summary and channel-summary data regions use the shared `data-table` selector while retaining their Phase 39 semantic table selectors. Headers remain visible, have consistent cell padding and visible column separators, and long data in every summary or drilldown table cannot widen the document. | Both rendered data regions contain their expected headers; adjacent header cells remain distinct and readable. After opening the source drilldown, its long formula exposes a full-value `title`, and `document.documentElement.scrollWidth <= window.innerWidth` at 1440 px, 1000 px, and 800 px. |
| issue-79-finance-result-states | A query refresh exposes a deterministic loading state and then either row-count result feedback, a structured cross-column empty row, or an actionable error state in both summary tables. | A populated response exposes both row-count statuses. After changing a filter and clicking query, Chrome observes loading inside each data table; a failed response produces both semantic retry actions, retry re-enters loading, and an empty retry response produces `table-empty` in each table while preserving the headers. |

## Scope boundaries

- Keep `GET /console/financial/analytics` and
  `GET /console/financial/analytics/drilldown` unchanged.
- Keep current `ADMIN`/`FINANCE` access and the existing source-drilldown
  semantics unchanged.
- Do not alter billing formulas, amounts, units, default filter values, or
  backend persistence.
- Do not modify the merged PR #82 acceptance standard or repair unrelated UI
  regressions from other routes.

## Verification boundary

The Issue #79 Playwright cases render the current React page in the host's actual
Google Chrome through its authorized CDP endpoint. Current application modules
are served by local Vite and the existing page APIs use deterministic responses. They
prove DOM structure, geometry, interaction, result states, table readability,
and document overflow. They do not claim real-service integration or backend
financial correctness, which remain owned by the completed Phase 39
service/controller suites.
