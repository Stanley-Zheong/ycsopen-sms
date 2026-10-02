# Spirit 01: Frontend Foundation Contract Spec

## Intent

Create the reusable frontend foundation that prevents repeated layout, form, table, and action-context defects across Admin and Tenant pages.

## Scope

### In

- Admin and Tenant shell conventions: sidebar, breadcrumb, page header, content container.
- Shared `QueryPanel`, form field sizing, four-column layout, button placement, empty state table, and modal contracts.
- Shared state-changing action confirmation pattern with target, effect, reason, duplicate-submission lock, and failure retry.
- Login page baseline and stable `data-testid` conventions.
- Issue `#108`: DeepSeek-Platform-aligned visual foundation — one shared light shell, one token set,
  and a two-column sign-in layout used by both login routes.

### Out

- Page-specific business implementations for complaints, finance, delivery, or release.
- Backend API changes, authentication protocol changes, or login payload changes.
- Tenant commercial or billing policy changes.
- Per-page visual patchwork. Issue `#108` explicitly forbids solving the console restyle by
  editing individual page stylesheets; page-level styles must keep working through the shared
  shell and token contract.

## Issue #108 Scope

### Issue reference

- GitHub issue `#108` — `[UI] 参考 DeepSeek Platform 重做登录页与登录后控制台风格`.
- Branch: `fix/108-deepseek-platform-shell`, cut from `origin/main`. The repository has no
  `master` branch: `git ls-remote --heads origin master main` returns only `refs/heads/main`,
  which the issue names as the fallback trunk.

### Owned routes

| Route | Owner | Purpose |
|---|---|---|
| `/login` | `LoginPage` | Primary sign-in entry |
| `/admin/auth/login` | `LoginPage` | Alias sign-in route, same component contract |
| `/admin/*` protected | `AdminLayout` | Platform console shell |
| `/tenant/*` protected | `TenantLayout` | Tenant console shell |

Representative acceptance routes: `/login`, `/admin/dashboard`, `/tenant/overview`.

### Page goal

Give every console user, and every unauthenticated visitor, one coherent DeepSeek-Platform-style
visual language: a two-column brand/form sign-in surface, and an authenticated light shell with a
left navigation rail, a consistent page header band, and a centered content container.

### Primary object

- Sign-in page: the **console session** — the user proves identity and is routed to the console
  workspace matching the issued `userType`.
- Console shell: the **authenticated console workspace** — the shared frame that presents the
  current module, its navigation, and its account actions around the page owned by the route.

### Action contract

| Action | Trigger | Target | Preconditions | Result state | Failure feedback |
|---|---|---|---|---|---|
| Sign in | Submit `shared-auth-login-submit` | Console session | Username and password non-empty, `maxLength=20` respected | Session stored; route moves to `/admin/dashboard` or `/tenant/overview` by `userType` | `shared-auth-login-error` shows the controlled auth message; password cleared; other input preserved |
| Remember username | `shared-auth-login-remember` checkbox | Local storage key `ycsopen.console.remembered-username` | None | Username persisted on success, removed when unchecked | Not applicable |
| Open navigation group | `*-group-toggle` button | `SidebarMenu` group | Group is rendered for the current role and permissions | `aria-expanded` updated; prior group collapses | Not applicable |
| Navigate module | Sidebar link | Router location | Link visible for the current role and permissions | Route changes; `aria-current="page"` moves | Not applicable |
| Sign out | `shared-console-identity-profile-logout` | Console session | None | Session cleared; route moves to `/login` | Session is cleared even when revocation fails |

### Data source

- Sign-in: `POST /api/v1/console/auth/login` through `web/src/api/auth.ts`; unchanged.
- Console shell: the auth store (`userType`, `tenantId`), the identity access hook for Admin
  permission filtering, and the static PRD navigation configuration in each layout. The shell
  introduces **no new request** and **no new business data**.
- Shell labels (workspace name, breadcrumb) are derived from the existing navigation
  configuration and the current route, not fetched.

### Empty / loading / error states

| Surface | Loading | Empty | Error |
|---|---|---|---|
| Sign-in form | Submit disabled, label switches to `登录中…`, no layout shift or button overflow | Not applicable | `role="alert"` paragraph `shared-auth-login-error` with the controlled message |
| Sidebar group | Not applicable | A group with zero permitted items is not rendered | Not applicable |
| Page content | Delegated to the page's own `query-panel` / table states, unchanged | Delegated to `table-empty`, unchanged | Delegated to page-level `role="alert"` and `InternalErrorNotice`, unchanged |

### Acceptance rules

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-01-QUERY | Every query region uses the shared QueryPanel contract with visible label, stable input test id, search, reset, and optional collapse behavior. | Chrome Playwright can locate `query-panel`, fill a field, run query, reset, and observe result state without horizontal overflow. |
| FE-SPIRIT-01-FORM | Every create/edit form uses a shared modal or drawer contract with validation, cancel confirmation, submit feedback, and unchanged user input after validation failure. | Unit and Playwright coverage prove required-field errors, success toast, refresh, and unsaved-change confirmation. |
| FE-SPIRIT-01-TABLE | Every data table renders headers, loading, empty, error, and result states consistently. | Empty results still show table header and `table-empty`; error state has retry or explanatory feedback. |
| FE-SPIRIT-01-ACTION | State-changing actions never show detached reason inputs and always bind target, effect, reason, latch, audit payload, and retry behavior in a modal. | Existing issue `#91` pattern is reusable and no page-level orphan reason field remains. |
| FE-SPIRIT-01-SHELL | Both consoles render the same shell contract: `shared-console-shell`, `shared-console-shell-sidebar`, `shared-console-shell-topbar`, `shared-console-shell-content`, and one shared token set. Admin and Tenant must not diverge into two sidebars, headers, or backgrounds. | Chrome Playwright reads the shell test ids on an Admin route and a Tenant route and compares sidebar width, content container maximum width, and shell background for equality. |
| FE-SPIRIT-01-LOGIN | `/login` and `/admin/auth/login` render the same two-column DeepSeek-style sign-in surface, keep the authentication flow, and keep `login-card`, username, password, remember-username, submit and error selectors stable. | Unit tests assert the preserved class hooks; Chrome Playwright asserts a brand panel beside a white form panel at 1440 and no horizontal scroll at 390. |
| FE-SPIRIT-01-NOOVERFLOW | No unexpected horizontal scrolling and no overlapping text on the sign-in page or either console shell at desktop and mobile widths. | Playwright asserts `documentElement.scrollWidth <= clientWidth` on the acceptance routes at both viewports. |

### Stable selector contract (issue #108)

Preserved unchanged:

- Sign-in: `shared-auth-login-page`, `shared-auth-login-background`, `shared-auth-login-card`,
  `shared-auth-login-username`, `shared-auth-login-password`, `shared-auth-login-remember`,
  `shared-auth-login-submit`, `shared-auth-login-error`,
  `admin-console-identity-auth-login-submit`.
- Shell: `admin-console-navigation-*`, `tenant-console-navigation-*`,
  `shared-console-identity-profile-logout`.
- Class hooks asserted by tests: `login-page`, `login-card`, `login-remember-input`, `card`,
  `layout`, `content`, `sidebar-menu-panel`.

Added by issue #108:

- `shared-console-shell`, `shared-console-shell-sidebar`, `shared-console-shell-brand`,
  `shared-console-shell-workspace`, `shared-console-shell-topbar`,
  `shared-console-shell-breadcrumb`, `shared-console-shell-content`,
  `shared-console-shell-content-container`.

## Issue #87: Admin Form Geometry

### Goal, object, and scope

The goal is to make Admin query and edit surfaces scannable: a field must not expand merely
because adjacent grid cells are empty. The primary object is the geometry of an existing Admin
single-line field and its containing field grid. The owned surface is the 56 terminal `/admin/*`
routes in `web/src/router/routes.tsx`; 45 expose an ordinary input or single-value select under the
layout fixture recorded by the issue acceptance test.

The authorized platform user, page-owned data sources, validation, submit/reset commands, API
payloads, loading messages, empty results, and error feedback are unchanged. This issue adds no
editable field, request, mutation, or asynchronous link behavior. Loading, empty, success, and
error states keep the same width and containment rules as the populated state.

### Acceptance rules

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-01-ADMIN-CONTROL-WIDTH | Every visible ordinary Admin single-line input/select is no wider than 420px or 30% of its query panel/Admin content container at the 1440px acceptance viewport. Checkbox, radio, file, hidden, range, colour, button-like inputs, multi-select, and textarea are excluded. | Chrome traverses all 56 terminal Admin routes and compares every visible ordinary control box with its actual container box; the explicit 45-route form allowlist must match. |
| FE-SPIRIT-01-ADMIN-FOUR-COLUMN | At 1440px and wider a shared query/form field grid has exactly four aligned columns; it changes to three, two, and one column at 1439px, 1200px, and 900px. QueryPanel labels stack above controls at the four-column breakpoint. Composite card/panel and 720px modal grids remain page-owned. | Unit coverage freezes QueryPanel collapse parity; Chrome measures seven representative grids, a nested full-width group, child containment, three composite grids, and two modal grids. |
| FE-SPIRIT-01-ADMIN-SELECT-FIT | Select options that fit the desktop cap receive their intrinsic width; selects do not fill an otherwise empty row and remain subject to the Admin cap. | Chrome checks every visible select in the 56-route traversal, five stable selectors, and a data-backed option long enough to exercise the 30% cap. |
| FE-SPIRIT-01-ADMIN-NOOVERFLOW | Form geometry never creates document-level horizontal scrolling on an Admin route. | Chrome independently traverses all 56 routes and requires `documentElement.scrollWidth === clientWidth`. |

## Issue #58: Real-Service Query Acceptance Closure

### Goal, object, and scope

The goal is to close the production-evidence gap left by the merged Issue `#58` implementation and
PR `#62`. The primary object is the existing shared `QueryPanel` behavior on three representative
Admin routes: `/admin/tenants`, `/admin/system/logs`, and `/admin/uplink`. The page components,
permissions, selectors, and API contracts remain owned by their existing modules; this closure adds
Google Chrome evidence through the repository's Docker Web/Core/MySQL topology without intercepting
platform API requests.

The later owner decisions remain authoritative: Issue `#64` removes reset from single-field query
panels; Issue `#77` adds the `query-fields` and `query-actions` semantic regions, the 40px control
contract, and the route inventory; and Issue `#87` makes collapse responsive to the 4/3/2/1
field-grid contract. Issue `#77`'s mocked route traversal does not replace this real-service gate.
The webhook push-failure default called out by PR `#62` review is already corrected on current
`main` and remains `PUSH_FAILED`. The three
representative routes all retain reset; the five- and seven-field routes start collapsed at the
1440px acceptance viewport, while the three-field tenant route fits one row and starts expanded.

| Route | Page goal and primary object | Data source and action contract | Required states and readback |
|---|---|---|---|
| `/admin/tenants` | Find and page through tenant qualification records. | Real `GET /api/v1/console/admin/tenants`; query applies keyword, verification, and operating-status filters to the returned tenant collection; reset clears all filters and returns to page one. | Loaded, filtered, second page, reset page one, stable table and pagination selectors. |
| `/admin/system/logs` | Find append-only privileged operation audit records. | Real `GET /api/v1/console/operation-audits`; query serializes actor, operation, result, from/to time bounds, and paging parameters; reset removes filters and returns to page one. | Initial collapsed, expanded, filtered result, reset result, collapsed after reload, loading/error ownership unchanged. |
| `/admin/uplink` | Find normalized uplink records without changing push state. | Real `GET /api/v1/console/uplinks`; query serializes tenant, phone, keyword, carrier, push state, and start/end time bounds; reset removes them and restores the initial rows. | Initial collapsed, expanded, filtered target row, reset baseline rows, collapsed after reload, loading/error ownership unchanged. |

### Acceptance rules

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-01-QUERY-REAL-SERVICE | Installed Google Chrome drives the production Web bundle through Nginx to the real Core and MySQL services; no platform API route is fulfilled, aborted, or replaced by Playwright. | The Docker release spec authenticates through the real login endpoint, receives HTTP 200 responses from each page-owned query API, observes submitted query parameters where the API supports server-side criteria, and reads the filtered and reset results from the real React tables. |
| FE-SPIRIT-01-QUERY-RESET-PAGING | Query submission uses the existing page-owned parameters, and reset restores initial criteria and page one. | Tenant fixtures produce two pages before reset; operation-audit and uplink filters reach their real service queries; every reset restores baseline controls and result state. |
| FE-SPIRIT-01-QUERY-DISCLOSURE | Multi-row panels start collapsed after first entry and reload under the current responsive grid; one-row panels remain expanded. | Chrome verifies the operation-audit and uplink panels are collapsed before expansion and again after reload, while the tenant panel is visible without a redundant toggle at 1440px. |

The tenant page's existing loading, error, empty, table, and pagination states move into the shared
result region so the documented `query-result-table` selector names the real result. No backend
endpoint, permission, schema, or release seed migration is added. Deterministic test-owned rows are
prepared only inside the disposable Docker release database and are removed with that Compose
project.

### Stable selectors

- Every shared query surface retains `query-panel`, `query-panel-fields`, `query-fields`, and
  `query-actions`. The three Issue #58 acceptance routes also retain `query-result-table`; query,
  reset, refresh, toggle, and page-owned field selectors remain scoped to their owning panel.
- The #108 shell selector remains `shared-console-shell`; `data-console-kind="admin"` is an
  audience-scoping attribute, not a replacement selector or wrapper.
- Static select evidence uses `admin-auditable-exemption-exemption-policy-type`,
  `admin-blacklist-risk-black-white-lists-type`,
  `admin-runtime-content-content-safety-category`,
  `admin-frequency-api-frequency-rules-type`, and `admin-prefixes-update-type`.
- The data-backed cap boundary uses `admin-channel-health-channel-pools-member-channel`.

## Acceptance

- Layout follows `docs/frontend页面实现规范.md`.
- Existing page-specific selectors are preserved or mapped in the owning spirit.
- No editable input remains without query, submit, or explicit async-link behavior.
- Changed shared components include unit tests and at least one representative Chrome Playwright route check.
- The visual source of truth for issue `#108` is recorded in
  `evidence/deepseek-platform-visual-observation.md`.

## Remaining TODO

- Open item: Assign each open layout/form issue to this spirit or an explicit later spirit.
- Open item: Inventory current shared components and routes before implementation.
  Closed for the shell, login, query panel, table, card and dialog surfaces by issue `#108`
  — see `ITERATIONS.md` `FE01-I-002`.
- Open item: Record final verification commands in `QUALITY-GATEWAY.md`.
  Closed by issue `#108` — see `QUALITY-GATEWAY.md` "Issue #108 Verification Record".
