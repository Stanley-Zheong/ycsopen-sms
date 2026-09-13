# Issue 64 Query Panel Action Layout

GitHub issue #64 amends the shared query-panel contract introduced by issue
#58. The change applies through `QueryPanel` to all current consumers: 21 page
source files, 22 mounted panel instances, 24 distinct routes, and 25
route-panel surfaces. Page-specific query parameters, permissions, pagination,
result rendering, and API behavior remain unchanged.

Issue #71 supersedes only the action-position detail below: actions use the
final field row when the configured field columns and action group fit, and
otherwise move to a separate right-aligned row. They never begin at the left
edge or overlap a field.

## Behaviors

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-64-inline-actions | Visible query actions share the field-content region and the action buttons never split onto separate lines. Issue #71 owns whether the group uses the final field row or a separate right-aligned row. | In a rendered browser, search and reset have the same vertical position; the action group is right-aligned and does not overlap any field. |
| issue-64-single-field-search | A panel with exactly one `QueryField` renders search without reset. | `query-submit` is visible and `query-reset` is absent. |
| issue-64-collapse-actions | A collapsible panel treats fields and actions as one disclosure region. | When `query-panel-fields` is collapsed, its field controls, search, and reset are all hidden; expanding it reveals all of them together. |

## Affected surfaces

The current route and selector inventory remains in
`.planning/changes/issue-58-query-panel/UI-ELEMENTS.md`. It is the current
shared-component inventory and records the issue #64 amendments. The shared
implementation is the semantic owner; page components continue to own only
their filter state and API effects.

Single-field panels are `/admin/tenant-recharge-review`,
`/admin/bulk/details`, `/admin/system/login-history`,
`/admin/signatures/review`, `/admin/templates/review`, and
`/tenant/consumption-ledger`. All other current panels contain multiple fields
and retain reset.

## Verification boundary

Component tests cover one-, two-, and multi-row field counts. Browser tests
cover a collapsed multi-field panel and a visible single-field panel with
geometry assertions. Existing page tests and the complete Web suite retain
ownership of page-specific query behavior. The repository-wide source
inventory confirms that every current query surface imports the shared
component; no page-by-page production fork is introduced.
