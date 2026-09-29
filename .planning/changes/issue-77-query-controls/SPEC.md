# Issue 77 Query Control Unification

GitHub issue #77 restores the shared query-area contract across the current
admin and tenant consoles. The implementation follows the owner-authored
`docs/frontend页面实现规范.md`, merged by PR #81. Existing API, permission,
mutation, and data-isolation contracts remain page-owned.

GitHub issue #88 strengthens the same surface: each query field must cause an
observable request-signature or declared local-result change; every panel has
Search, Reset, and Refresh; and its result region exposes one deterministic
loading, error, empty, or success state. Submitting unchanged applied criteria
must retry the exact owning query after failure.

## Behavior

| Obligation ID | Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- | --- |
| OBL-ISSUE-77-ADMIN-QUERY-CONTROLS | issue-77-admin-query-controls | Every Admin list or read-only lookup filter uses the shared query structure, associated labels, page-owned stable test ids, 40 px controls, scoped actions, and contained results. | Chrome traverses all 56 concrete Admin routes and the 38 query-panel route instances. |
| OBL-ISSUE-77-TENANT-QUERY-CONTROLS | issue-77-tenant-query-controls | The four Tenant query routes use the same semantic, geometry, action, and result-order contract. | Chrome traverses the ledger, uplink, unsubscribe, and help routes. |
| OBL-ISSUE-77-NO-ORPHAN-INPUTS | issue-77-form-payloads | Every editable API Key and CMPP creation value is serialized into its owning request. | Lower-layer form tests assert the complete edited request payload. |
| OBL-ISSUE-88-ADMIN-ACTIONABLE-CONTROLS | issue-88-admin-actionable-controls | Every visible editable Admin control has a stable page-owned test id and accessible label. Every Admin QueryPanel provides Search, Reset, Refresh and one page-owned four-state status. Each filter independently changes the request URL/body or an explicitly declared local result; unchanged Search retries only the applied query. | Chrome traverses all 56 Admin routes and 38 query-panel route instances; focused tests prove exact single-request pagination retry and permission gating. |
| OBL-ISSUE-88-TENANT-ACTIONABLE-CONTROLS | issue-88-tenant-actionable-controls | Every Tenant QueryPanel provides the same actions and four-state status while preserving its page-owned selectors and request ownership. | Chrome traverses all four Tenant query routes and changes every filter independently. |
| OBL-ISSUE-88-QUERY-STATE-AND-RETRY | issue-88-query-state-and-retry | The shared component deterministically dispatches changed criteria to submit and unchanged criteria to refresh, synchronizes canonical resets, gates both query actions together, and selects one loading/error/empty/success state. | Focused component and page tests cover dispatch, reset, permissions, live-region ownership, and retry. |
| OBL-ISSUE-88-ARCHIVE-RESULT | issue-88-archive-result | The archive manifest table remains inside its query result region with one four-state status. | Chrome visits `/admin/archive` and asserts the manifest table and successful status within `query-result-table`; unit tests cover exceptional states. |
| OBL-ISSUE-88-EXPORT-CARDS | issue-88-export-card-layout | All six export summary cards wrap without clipping at narrow desktop width. | Chrome verifies the complete card grid at 1024 by 900. |

## Scope

- Cover the complete current route inventory: 38 query-panel instances across
  36 admin routes, plus four tenant query routes. Alias routes that render the
  same page retain the same query contract.
- Include dashboard complaint-ratio queries, number lookup aliases, provider
  status normalization, retention archives, secure exports, financial
  analytics, fee warnings, tenant risk, trial/balance audit, termination lists,
  and the tenant help guide in addition to the existing list filters.
- Keep mutation inputs such as operation reasons, approval evidence, keyword
  maintenance, import payloads, and auto-reply configuration outside query
  panels. They have real behavior and are not disposable query inputs.
- Preserve existing page-specific selectors as compatibility aliases while
  adding the shared semantic regions `query-fields` and `query-actions`.
- Preserve page ownership of API requests and data while making `onRefresh`
  and `queryStatus` required QueryPanel inputs.
- Keep the recharge-review and send-job reason controls outside QueryPanel;
  they are row-mutation inputs, not list filters.
- Keep the archive manifest table inside its query result region, including
  explicit loading, error, empty, and success feedback.
- Let export summary cards wrap at narrow desktop widths; clipping overflow is
  not an accepted responsive strategy.
- Wire every editable API Key and CMPP creation field into its request payload;
  no editable control is retained as a presentation-only placeholder.

## Verification boundary

The route acceptance uses the repository Playwright application and intercepts
business API requests to make cross-route rendering deterministic. It proves
real React DOM structure, query interaction, and Chrome CSS geometry, but does
not prove live backend data rendering on every route. Page-owned unit and E2E
tests cover request serialization and domain behavior. Backend behavior is
unchanged, while the repository backend test suite remains a delivery gate.
