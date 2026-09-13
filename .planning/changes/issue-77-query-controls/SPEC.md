# Issue 77 Query Control Unification

GitHub issue #77 restores the shared query-area contract across the current
admin and tenant consoles. The implementation follows the owner-authored
`docs/frontend页面实现规范.md`, merged by PR #81. Existing API, permission,
mutation, and data-isolation contracts remain page-owned.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
| --- | --- | --- |
| issue-77-query-controls | Every real list or read-only lookup filter is rendered by `QueryPanel`; its native controls are grouped under `query-fields`, and search/reset/refresh query actions are grouped under `query-actions`. Each control keeps a visible associated label, a page-owned stable test id, its applied-query behavior, and a 40 px rendered height. Results remain below the actions in a horizontally contained result region. | Chrome traverses all 56 concrete admin routes, checks the audited panel count on the 36 routes that contain queries, control semantics and geometry, action scoping, result ordering, and absence of page-level horizontal overflow. A second pass checks the four tenant query routes. |

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
- Wire every editable API Key and CMPP creation field into its request payload;
  no editable control is retained as a presentation-only placeholder.

## Verification boundary

The route acceptance uses the repository Playwright application and intercepts
business API requests to make cross-route rendering deterministic. It proves
real React DOM structure, query interaction, and Chrome CSS geometry, but does
not prove live backend data rendering on every route. Page-owned unit and E2E
tests cover request serialization and domain behavior. Backend behavior is
unchanged, while the repository backend test suite remains a delivery gate.
