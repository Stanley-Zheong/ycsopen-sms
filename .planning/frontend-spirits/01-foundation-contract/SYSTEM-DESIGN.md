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
