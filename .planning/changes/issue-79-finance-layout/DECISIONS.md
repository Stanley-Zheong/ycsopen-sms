# Issue 79 Finance Layout Decisions

| Decision | Outcome | Evidence |
| --- | --- | --- |
| Acceptance-standard source | Consume `docs/frontend页面实现规范.md` from merged PR #82 / `main` commit `e91ae04d28d7b12e8789303d0405fc94454f2837`. | Issue #79 explicitly names the path and this issue branch is based on the merged documentation revision. |
| Query implementation owner | Reuse the shared `QueryPanel`/`QueryField` and extend it with optional rendered actions and a submit label. | The existing Phase 39 page predates the current shared query contract; the repo UI skill requires shared component reuse. |
| Existing source action | Keep source drilldown in the query action group and preserve its current applied-filter/API behavior. | Issue #79 asks query/source actions to align but does not authorize drilldown contract changes. |
| Data tables | Keep the three Phase 39 semantic table selectors; add the generic `data-table` region only to the two always-present tables named by the issue. | This satisfies generic acceptance without breaking existing Playwright and unit selectors. |
| State ownership | Render loading/result/empty/error rows in both always-present summary tables from the existing React Query state. | Issue acceptance requires an explicit post-query list state; no API or dependency is needed. |
