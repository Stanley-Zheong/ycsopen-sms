# Spirit 01 System Design

## Component Ownership

- `web` shared layout owns shell, sidebar, breadcrumb, page header, and content container behavior.
- Shared query components own label placement, field width, action row, collapse, reset, loading, and result refresh semantics.
- Shared form components own create/edit modal or drawer behavior, validation display, submit, cancel, close, and unsaved-change confirmation.
- Shared table components own header, loading, empty, error, result, pagination, and action-column alignment.
- Shared action-confirmation components own target/effect display, reason field, duplicate-submission latch, focus lock, cancellation, retry, and payload snapshot.

## Issue #108 Ownership Map

| Component | File | Owns | Must not own |
|---|---|---|---|
| `AppShell` | `web/src/components/layout/AppShell.tsx` | Shell frame markup: brand row, sidebar rail, sidebar footer/logout control, breadcrumb page header band, main region, centered content container. Renders whatever navigation groups it is given. | Role gating, redirects, permission filtering, route table, API calls, business state. |
| `AdminLayout` | `web/src/components/layout/AdminLayout.tsx` | Platform-role gate, redirect to `/login`, the PRD 8.1 navigation tree, role/permission filtering per group and item. | Shell geometry, colours, breadcrumb rendering, logout presentation. |
| `TenantLayout` | `web/src/components/layout/TenantLayout.tsx` | Tenant-role gate, redirect to `/login`, the PRD 8.2 navigation tree, role filtering per group and item. | Shell geometry, colours, breadcrumb rendering, logout presentation. |
| `SidebarMenu` | `web/src/components/layout/SidebarMenu.tsx` | One-expanded-group semantics, `aria-expanded` / `aria-controls` / `aria-labelledby`, `hidden` panel attribute, `aria-current` on the active link, stable `data-testid` per group and item. | Which groups exist, item visibility by role, styling of the surrounding rail. |
| `LoginPage` | `web/src/pages/LoginPage.tsx` | Sign-in form state, `POST /api/v1/console/auth/login`, remembered-username persistence, controlled error mapping, post-login workspace routing, sign-in surface markup, `data-testid` contract. | Shell frame, token definitions, any business module data. |
| `login.css` | `web/src/styles/login.css` | Sign-in-specific surface: brand panel, form panel, field/checkbox/button density for the auth surface, responsive collapse. | Global shell geometry, shared control sizes used by console pages. |
| `tokens.css` | `web/src/styles/tokens.css` | The single source of colour, radius, shadow, spacing, typography and layout-constant values for shell, login and every page stylesheet. | Component selectors. It declares values only. |
| `shell.css` | `web/src/styles/shell.css` | Shell geometry and surface: app shell, sidebar rail, sidebar nav item states, page header band, content container, responsive stacking. | Page-specific layout, business components. |
| `index.css` | `web/src/styles/index.css` | Base element styling plus the shared component vocabulary (`card`, `query-panel`, tables, dialogs, buttons, fields, badges, status text, empty states). | Shell frame geometry (owned by `shell.css`) or auth surface (owned by `login.css`). |

## Dependency Direction

```
tokens.css  ──►  shell.css  ──►  index.css  ──►  login.css
      └───────────────────────────────────────────►  31 page stylesheets (values only)
```

`index.css` is the only stylesheet imported by `main.tsx`; it imports `tokens.css`, `shell.css`
and `login.css` in that order, so page stylesheets always win over base element rules and the
shell keeps a stable geometry contract.

## Data Flow

User input flows into a typed query or form state object, then into a page-owned API adapter. Shared components do not invent business parameters; they expose structured submit/reset/confirm events to page owners.

Issue `#108` adds no data flow. The shell reads only:

- `useAuthStore` → `userType`, `tenantId`, `setSession`, `logout`;
- `useIdentityAccess` → permission predicate for Admin navigation filtering;
- `useLocation` / `useNavigate` (through `SidebarMenu` and the layout) → active group, active item,
  breadcrumb derivation, and post-logout redirect.

No shell component performs a fetch, and no shell component writes business state.

## Command Flow

State-changing commands must pass through confirmation unless the owning spec explicitly declares the action as immediate and no reason-bearing side effect exists.

The two commands the shell owns are sign-in and sign-out:

- **Sign in** — `LoginPage.handleSubmit` latches on `pending`, which disables the submit control,
  swaps its label to `登录中…`, and prevents duplicate submission. The password is cleared on
  failure; the username is preserved. Nothing else in the shell is command-bearing.
- **Sign out** — the logout control awaits `logout()` from `api/auth`, and clears the local session
  in a `finally` block so a failed revocation still ends the session, then navigates to `/login`.

## State Separation

| State | Owner | Rendered by |
|---|---|---|
| Session (`userType`, `tenantId`, token) | `authStore` | Layouts (gating), shell (brand identity) |
| Navigation configuration | `AdminLayout` / `TenantLayout` constants | `AppShell` → `SidebarMenu` |
| Active route and active group | Router + `SidebarMenu` local state | `SidebarMenu` |
| Auth form state (`username`, `password`, `remember`, `error`, `pending`) | `LoginPage` local state | `LoginPage` |
| Remembers-username persistence | `localStorage['ycsopen.console.remembered-username']` | `LoginPage` |

## Failure Model

- Validation errors stay beside fields and preserve user input.
- Business rejections stay in the action or form context.
- System failures provide retry without duplicating already latched submissions.
- Shell failure isolation: the shell renders from store/route data only, so a failing page query
  degrades inside the content container and never blanks the navigation frame.
- Sign-out failure is non-blocking: revocation errors are swallowed by design, the local session is
  always cleared, and the user always returns to `/login` rather than being stranded with a stale
  sidebar.

## Verification Model

Unit tests prove shared component state transitions. Chrome Playwright proves at least one representative route for query, form, table, and action confirmation behavior.

Issue `#108` adds:

- `web/test/unit/app-shell.test.tsx` — shell contract, preserved hooks, breadcrumb derivation,
  sign-out command, sidebar stacking markup.
- `web/test/scripts/issue-108-deepseek-shell.spec.ts` — Chrome Playwright evidence for `/login`,
  `/admin/auth/login`, `/admin/dashboard` and `/tenant/overview`: shell geometry equality between
  the two consoles, brand-panel/form-panel split, control states, and
  `scrollWidth <= clientWidth` at 1440×900 and 390×844.

## Issue #87 Ownership and Verification

| Owner | Issue #87 responsibility |
|---|---|
| `AdminLayout` / `TenantLayout` | Select `consoleKind`; role gates, permissions, and route ownership remain unchanged. |
| `AppShell` | Publish the audience data attribute while preserving every #108 class, test id, and direct-child invariant. |
| `QueryPanel` | Keep collapse state synchronized with the 4/3/2/1 CSS column contract. |
| `index.css` | Own Admin-only width tokens, container-query scope, ordinary-control exclusions, and the explicit field-grid allowlist. |
| Page-local hooks | `custom-report-form-grid` identifies the report builder; the nested postpaid group spans its outer field grid; secure-export cards use shrinkable tracks to preserve document containment. Composite page card/panel grids and 720px modal grids keep their local column contracts. |

There is no new data, command, or persistence flow. A missing expected form route, unexpected form
route, control wider than 420px/30%, field child outside its track, altered composite/modal grid,
select option clipped despite fitting within the cap, or
document horizontal overflow fails `form-control-layout.spec.ts`. The fixture returns page-shaped
empty data where a component requires a collection and otherwise uses controlled service errors;
this is deliberately not real-backend evidence.

## Issue #58 Real-Service Verification Flow

| Owner | Responsibility |
|---|---|
| `TenantListPage` | Place loading, error, empty, table, and pagination states in the shared `QueryPanel` result region so the documented `query-result-table` selector names the real result boundary. |
| `scripts/verify-docker-release` | Create idempotent, test-owned tenant/account and uplink rows in the disposable MySQL volume before each fresh, upgrade, and restart Chrome lane; assert the fixture before browser execution; retain existing cleanup ownership. |
| `web/test/docker-release/release-acceptance.spec.ts` | Authenticate as the release Admin in installed Google Chrome, exercise the three QueryPanels through real HTTP, assert request parameters and table/pagination readback, then prove reset and disclosure defaults. |
| Existing API/Core owners | Continue to own query parameters, permissions, serialization, service filtering, audit append-only behavior, pagination, and response envelopes. No production API is modified for test convenience. |
| Docker release report and CI check | Bind the executed browser result to `BUILD_COMMIT`, the built Web/Core images, the real Compose topology, and the JSON report checksum emitted by the lane. |

The flow is:

```text
test-owned MySQL rows
  -> real Core repositories/services/controllers
  -> Nginx `/api/v1` proxy
  -> production React API adapters and QueryPanel pages
  -> installed Google Chrome actions and DOM readback
```

No `page.route()` or other network substitution is allowed in this acceptance case. Fixture rows
use reserved test identifiers and contain no credential, production, or customer data. The existing
Compose teardown removes their volume on success or failure.
