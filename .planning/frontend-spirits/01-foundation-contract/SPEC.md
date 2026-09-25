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
