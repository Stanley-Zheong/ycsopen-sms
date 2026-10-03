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

## Issue #88 Query Ownership

| Owner | Owns | Must not own |
|---|---|---|
| `QueryPanel` | Form semantics, named controls, deterministic applied-value signature, Search/Reset/Refresh placement, unchanged-query retry dispatch, and the accessible four-state result envelope. | Business parameters, API endpoints, permissions, or mutation state. |
| Page query hook/mutation | Draft and applied criteria, exact request/refetch callback, error and fetching flags, data count, and page-specific result content. | Shared action layout or state precedence. |
| Route acceptance | Complete route/panel inventory, independent field edits, request URL/body or local-result observation, stable selectors, labels, action geometry, result state, and overflow. | Production-service correctness; business responses are deterministic interceptions. |

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

For issue `#88`, each `QueryField` supplies its semantic name to the native control. `QueryPanel`
sorts the current `FormData` entries to create a stable signature. A changed signature applies the
page's draft state through `onSubmit` only. An unchanged signature invokes the exact-owner
`onRefresh` path only, which permits recovery after an initial failure without coupling retry to a
state change or dispatching the same request twice. If the raw signature changes but the page's
canonical query criteria do not, the page returns `false` from `onSubmit` and the panel invokes the
same exact-owner refresh path once. Reset
increments a synchronization version; a layout effect captures the controlled post-reset values
before the user can submit or edit again.

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
| Query draft/applied criteria and data | Owning page | Page fields and result content |
| Query signature and result-state envelope | `QueryPanel` | Search retry dispatch and the page-owned `*-query-status` node |

## Failure Model

- Validation errors stay beside fields and preserve user input.
- Business rejections stay in the action or form context.
- System failures provide retry without duplicating already latched submissions.
- Shell failure isolation: the shell renders from store/route data only, so a failing page query
  degrades inside the content container and never blanks the navigation frame.
- Sign-out failure is non-blocking: revocation errors are swallowed by design, the local session is
  always cleared, and the user always returns to `/login` rather than being stranded with a stale
  sidebar.
- Query request failure is visible as an assertive page-owned status inside the result region.
  Search with unchanged criteria and Refresh both retain a retry path; no error recovery relies on
  changing a filter value.

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

## Issue #76 Ownership and Verification

| Owner | Issue #76 responsibility |
|---|---|
| `LoginPage` | Expose the standard `login-card` boundary around the existing `shared-auth-login-card` form; preserve username, password, remember, submit, error, pending, storage, API, and routing behavior. |
| `login.css` | Keep the boundary within the form panel, preserve 16px checkbox geometry, force the remember label to one line, and prevent card/document horizontal overflow. |
| `login-page.test.tsx` | Freeze the additive selector structure without treating a class hook as a test-id substitute. |
| `issue-76-login-card.spec.ts` | At 1280×800, measure checkbox and label geometry, assert selectors/containment/no overflow, and prove exact `admin` / `Admin@123456` submit payload and ADMIN dashboard routing. |
| `Docker release / Google Chrome` CI lane | Run the issue-specific browser contract with the runner's branded Google Chrome; local ARM verification remains explicitly bundled Chromium evidence. |

The outer boundary is presentational and introduces no data or command flow. The form remains the
only submit owner, loading stays on the existing pending latch, and the existing reserved error
slot remains the sole authentication feedback region.
## Issue #88 Ownership and Verification

Issue `#88` adds shared-component state-transition tests, focused page retry tests, one Chrome
route inventory covering every Admin query field independently, and a 1024×900 export-card check.
