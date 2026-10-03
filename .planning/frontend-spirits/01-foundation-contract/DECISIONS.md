# Spirit 01 Decisions

## DR-FE01-001: Shared Components Are Product Contract

### Status
Accepted

### Context
Repeated issues around control height, query layout, button alignment, empty states, and detached reason inputs show that UI structure cannot be left to page-local implementation.

### Decision
Treat shell, QueryPanel, form, table, modal, action confirmation, and `data-testid` naming as product contract. Page-specific work must reuse or extend shared components instead of cloning local variants.

### Consequences

- Later spirits can focus on business behavior instead of re-solving layout.
- Shared component changes require representative cross-page regression tests.

### References

- `docs/frontend页面实现规范.md`
- `docs/ISSUE_BUG_RETROSPECTIVE.md`
- GitHub issues `#52`, `#58`, `#87`, `#88`, `#89`, `#91`

## DR-FE01-002: DeepSeek Platform Is the Visual Reference, Not a Code Source

### Status
Accepted (issue `#108`)

### Context
Issue `#108` requires the sign-in page and the authenticated console to follow
`platform.deepseek.com`. The repository is an independent open implementation whose AGENTS.md
forbids copying source code, credentials, configuration, test data, or private documentation
from any non-public system, and forbids committing secrets or production data.

### Decision
Use DeepSeek Platform pages **observed through the local Chrome browser** strictly as a *visual*
reference. Reuse only transferable design values — colours, radii, spacing, type sizes, line
heights, shadow recipes, and layout measurements — and re-express them in YCSAN-SMS's own token
names and class vocabulary. Do not copy markup, hashed class names, bundled image assets, logo
geometry, scripts, or any behavioural code.

### Consequences

- Every borrowed value is traceable in
  `evidence/deepseek-platform-visual-observation.md`, which records the source page, the
  observed value, and the destination token.
- DeepSeek's photographic brand texture and logo are **not** redistributed; the sign-in brand
  panel uses an original YCSAN-SMS gradient built from the recorded palette.
- The observation was performed read-only through a temporary profile copy in a scratch
  directory; that copy was deleted afterwards, and no cookies, tokens, API keys, account
  identifiers, or production data were written into the repository.
- Screenshots of authenticated console pages were deliberately **not** committed.

### References

- `evidence/deepseek-platform-visual-observation.md`
- `AGENTS.md` (engineering contract: independence and secret handling)
- GitHub issue `#108`

## DR-FE01-003: Tokens Are the Only Restyle Channel

### Status
Accepted (issue `#108`)

### Context
The console has ~100 routes and 31 page-level stylesheets. Issue `#108` states the restyle must not
be "逐页零散微调" (scattered per-page patching), and AGENTS.md requires each change to stay scoped.

### Decision
Implement the DeepSeek-aligned visual language through exactly three shared channels:

1. `web/src/styles/tokens.css` — the single source of colour, radius, shadow, spacing and type values.
2. `web/src/styles/shell.css` — app shell, sidebar, topbar/page header, content container.
3. `web/src/styles/index.css` — base elements and the shared component vocabulary (`card`,
   `query-panel`, tables, dialogs, buttons, fields).

Page-level stylesheets keep their own class names and are **not** edited by this issue. They
inherit the new look because they consume the same tokens.

### Consequences

- Existing token names (`--color-brand`, `--color-border`, `--radius-control`, …) keep their
  names and change only their values, so the 31 page stylesheets continue to compile and render.
- A page that hard-codes a colour instead of using a token will not follow the shell. That is a
  known limitation, recorded in the quality gateway rather than fixed by per-page edits.
- Any future visual change is a token edit, not a page edit.

### References

- `web/src/styles/tokens.css`, `web/src/styles/shell.css`, `web/src/styles/index.css`
- `docs/frontend页面实现规范.md`
- GitHub issue `#108`

## DR-FE01-004: Admin and Tenant Share One Shell Component

### Status
Accepted (issue `#108`)

### Context
`AdminLayout` and `TenantLayout` previously duplicated the sidebar markup, the brand header, the
logout button and the `<main class="content">` wrapper. Issue `#108` requires Admin and Tenant to
share the same base visual tokens and explicitly forbids two inconsistent sidebars, headers or
content backgrounds.

### Decision
Extract the presentation into one `AppShell` component. `AdminLayout` and `TenantLayout` keep their
distinct responsibilities — role gating, redirect-to-login, and navigation/permission filtering —
and hand the resulting groups to `AppShell`, which owns brand, navigation frame, logout control,
breadcrumb header, and the content container.

### Consequences

- Shell geometry and colour cannot drift between the two consoles; a Playwright check compares
  them directly.
- Permission and routing behaviour stays in the layouts, where it already had tests.
- The shell markup exists once, so a future third workspace reuses it instead of forking it.

### References

- `web/src/components/layout/AppShell.tsx`, `AdminLayout.tsx`, `TenantLayout.tsx`, `SidebarMenu.tsx`
- GitHub issue `#108`, PRD sections 8.1 / 8.2

## DR-FE01-005: Preserved Selector and Behaviour Contract

### Status
Accepted (issue `#108`)

### Context
Issue `#108` requires existing routes, permission protection, clickable navigation, business data
flow, query/table/empty-state/dialog/action behaviour and existing `data-testid` values to be
preserved. Existing specs additionally depend on structural details:
`test/scripts/issue-77-admin-query-contract.spec.ts` asserts `.layout > main.content`,
`test/scripts/sidebar-navigation.spec.ts` asserts exactly one `.sidebar-menu-panel:not([hidden])`,
and `test/unit/login-page.test.tsx` asserts the `login-page`, `login-card` and
`login-remember-input` class hooks.

### Decision
Treat the following as frozen for this issue: all `data-testid` values; the class hooks
`.layout`, `.content`, `.card`, `.login-page`, `.login-card`, `.login-remember-input`,
`.sidebar-menu-panel`; the 40px query-control height; the 3px/2px focus outline geometry; and the
`.layout > main.content` parent-child relationship.

### Consequences

- New semantic shell hooks are **added** rather than replacing the legacy hooks, so older specs
  and page stylesheets keep working.
- `web/src/components/layout/AppShell.tsx` keeps `<main class="content app-content">` as a direct
  child of the `.layout` element.
- The shell restyle cannot be validated by deleting tests; the specs are extended instead.

### References

- `web/test/scripts/issue-77-admin-query-contract.spec.ts`
- `web/test/scripts/sidebar-navigation.spec.ts`
- `web/test/unit/login-page.test.tsx`
- GitHub issue `#108`

## DR-FE01-006: Focus Ring Keeps Its Geometry and Adopts the New Brand Tint

### Status
Accepted (issue `#108`)

### Context
`test/scripts/control-sizing.spec.ts` asserts the exact computed focus outline of a textarea:
`outlineColor: rgba(12, 133, 232, 0.28)`, `outlineWidth: 3px`, `outlineStyle: solid`,
`outlineOffset: 2px`. Issue `#108` moves the product accent from `#0c85e8` to the observed
DeepSeek business blue `#3964fe`, so `--color-focus` no longer matches the frozen assertion.

### Decision
Keep the geometry (`3px solid`, `2px` offset) and move only the colour, so the focus ring stays
visible, consistent and layout-stable. Update the assertion in
`control-sizing.spec.ts` to the new token value `rgba(57, 100, 254, 0.28)` instead of freezing the
old brand colour.

### Consequences

- Focus visibility remains a tested contract rather than an incidental style.
- The change is visible in review as a one-line expectation update with a comment naming issue
  `#108`, so it cannot be mistaken for an accidental test edit.
- Any page that hard-codes the old focus colour would diverge; none does — the outline rule is
  declared once in `index.css`.

### References

- `web/src/styles/tokens.css` (`--color-focus`)
- `web/test/scripts/control-sizing.spec.ts`
- GitHub issue `#108`

## DR-FE01-007: Login Layout Is Two Columns on Desktop and Stacked on Mobile

### Status
Accepted (issue `#108`)

### Context
The observed DeepSeek sign-in page is a full-height two-column split — a dark brand panel beside a
white form panel — and collapses to a single column on narrow viewports. The existing YCSAN-SMS
login page already had an intro panel and a form panel, but the intro was a translucent overlay
inside a floating glass card rather than a true split, and the form panel was not a plain white
surface.

### Decision
Adopt the split directly: at ≥900px the sign-in surface is a two-column grid, a full-bleed brand
panel (dark navy-to-brand-blue gradient, original to YCSAN-SMS) beside a pure white form panel with
a 336–384px form column. Below 900px the shell becomes one column with a compact brand strip above
a white form panel, and the capability list is hidden so the form stays above the fold.

### Consequences

- `shared-auth-login-background` remains the shell element and keeps its test id, but its role
  changes from "glass card on a photo background" to "the two-column sign-in surface".
- Loading, error, disabled, hover and focus states are styled on the shared token set, so they are
  visible and do not move the layout.
- The brand panel no longer depends on `public/login-messaging-network.svg`; the gradient is pure
  CSS, so the page has one less asset to keep.

### References

- `web/src/styles/login.css`, `web/src/pages/LoginPage.tsx`
- `evidence/deepseek-platform-visual-observation.md`
- GitHub issue `#108`

## DR-FE01-008: Centered Content Container With an Adapted Maximum Width

### Status
Accepted (issue `#108`)

### Context
The observed console centers its page content in a container with `max-width: 936px`. YCSAN-SMS
console pages are denser than DeepSeek's console pages: the shared query grid has an `812px`
minimum width for three columns, several tables declare `min-width` up to `1040px`, and existing
Playwright specs assert that table headers never clip (`scrollWidth <= clientWidth` on every `th`)
and that action buttons stay within the query panel.

### Decision
Adopt the centered-container pattern but set the cap to `--content-max-width: 1120px` instead of
`936px`. The container is horizontally centered with `margin-inline: auto`, matching the reference
information architecture, while preserving enough width for the existing data-density contracts.

### Consequences

- On a 1440px viewport the cap is not binding (available width is ~1100px), so existing geometry
  assertions are unaffected; on very wide screens the content stays centered instead of stretching.
- This is a deliberate, recorded deviation from the measured reference value. It is noted in the
  PR body as a known limitation of the approximation.
- If a future change lowers the minimum widths of the query grid and tables, the cap can be moved
  to the reference `936px` by editing one token.

### References

- `web/src/styles/tokens.css` (`--content-max-width`), `web/src/styles/shell.css`
- `web/test/scripts/control-sizing.spec.ts` (812px query grid minimum)
- `evidence/deepseek-platform-visual-observation.md`
- GitHub issue `#108`

## DR-FE01-009: The Shell Sidebar Stacks Instead of Disappearing on Mobile

### Status
Accepted (issue `#108`)

### Context
The observed console sets its sidebar to `display: none` below the mobile breakpoint and relies on
in-page navigation. In YCSAN-SMS the sidebar is the *only* navigation surface: hiding it would make
every protected module unreachable on a phone and would break the issue requirement to preserve
clickable menu behaviour.

### Decision
Below 900px the shell becomes a single column. The sidebar turns into a full-width block with a
bounded height and its own vertical scroll, keeping the brand row, all permitted groups, and the
logout control reachable; the content container follows below it.

### Consequences

- No module becomes unreachable on a narrow viewport, and no horizontal scrollbar is introduced.
- Vertical space is consumed by the menu on small screens; this is an accepted trade-off, recorded
  here so it is not "fixed" later by hiding navigation.
- `SidebarMenu` behaviour — one expanded group, `aria-expanded`, `aria-current` — is unchanged at
  every breakpoint, so its existing specs still cover it.

### References

- `web/src/styles/shell.css`
- `web/test/unit/sidebar-layouts.test.tsx`, `web/test/scripts/sidebar-navigation.spec.ts`
- GitHub issue `#108`

## DR-FE01-010: Scope Admin Geometry Without Changing the Shared Shell Contract

### Status
Accepted (issue `#87`)

### Decision
`AppShell` receives a required `consoleKind` and exposes it as `data-console-kind`. Admin-specific
form CSS is scoped below `[data-console-kind="admin"]`; Tenant fields are unaffected. The frozen
`layout app-shell` class list, `shared-console-shell` test id, `ASIDE, MAIN` child order, and
`.layout > main.content` relationship remain unchanged.

## DR-FE01-011: One 4/3/2/1 Column Contract

### Status
Accepted (issue `#87`)

### Decision
Both QueryPanel's JavaScript collapse calculation and CSS use four columns at 1440px and wider,
three at 1201–1439px, two at 901–1200px, and one at 900px and narrower. At the four-column
breakpoint QueryPanel labels stack above controls so the #108 1120px content container can hold
four fields without overlap.

## DR-FE01-012: Preferred Input Width and Intrinsic Select Width

### Status
Accepted (issue `#87`)

### Decision
Admin ordinary inputs prefer 280px and never exceed 420px. Single-value selects use `max-content`,
capped by their cell, 420px, and—at desktop acceptance widths—30 container-query inline units.
Textareas and choice/file/special input types keep their page-owned geometry.

## DR-FE01-013: Fail-Closed Route Inventory and Layout-Only Browser Evidence

### Status
Accepted (issue `#87`)

### Decision
The browser spec contains a literal 56-route Admin catalog and a literal 45-route set expected to
show ordinary controls. The two sets make route additions or disappeared form surfaces explicit
review events. API interception proves rendered layout only; it does not claim real-service or
backend integration behavior.

## DR-FE01-014: Additive Standard Login Card Boundary

### Status
Accepted (issue `#76`)

### Context
The established login contract exposes `shared-auth-login-card`, while the unified frontend
standard and issue acceptance require the exact selector `data-testid="login-card"`. Replacing the
existing selector would break accepted compatibility coverage, and HTML cannot expose two exact
`data-testid` values on one element.

### Decision
Add one non-interactive outer card boundary with `data-testid="login-card"`. Keep the existing form
as `shared-auth-login-card`, with its authentication semantics and frozen `login-card` class hook
unchanged. Add explicit `white-space: nowrap` to the remember label; retain the existing 16px
checkbox rule and reserved error slot.

### Consequences

- Both the repository compatibility selector and the unified standard selector remain available.
- The wrapper carries no command, field, state, or API responsibility and does not duplicate form
  semantics.
- Browser evidence must prove containment and no horizontal overflow so the additive boundary
  cannot silently change layout.
## DR-FE01-014: QueryPanel Owns Retry Actions and the Result-State Envelope

### Status
Accepted (issue `#88`)

### Context
Page-local query panels could render inputs without Reset or Refresh, suppress retries when the
applied filter did not change, and expose inconsistent or silent loading/error/empty feedback.
Making every page independently rediscover the same state machine caused the contract to drift.

### Decision
`QueryPanel` always renders Search, Reset, and Refresh. It records a deterministic signature from
named native form controls: changed Search delegates once to the page's `onSubmit`; unchanged
Search delegates once, and only once, to the page's exact-owner `onRefresh`. Reset synchronizes the
post-reset controlled values before a later edit is classified. Pages supply a required
`queryStatus` object, while the shared component renders the single
loading/error/empty/success envelope inside `query-result-table`.

When a page canonicalizes raw input before constructing its query key or local predicate,
`onSubmit` compares the canonical draft with the applied criteria and returns `false` for a no-op.
`QueryPanel` then invokes `onRefresh` exactly once. This keeps inputs such as `042` versus `42`, or
trimmed/case-folded local search text, on the same apply-versus-retry decision path as the owner.

### Consequences

- Page owners still own criteria, API calls, permissions, data, and business copy.
- A retry cannot depend on a React state assignment changing a query key.
- Search and Refresh share the page's read-permission/loading disable gate, so neither action can
  start a protected request before access resolution.
- Query fields need native `name` values; `QueryField` derives them from its stable semantic name.
- Loading takes precedence over error, then empty, then success, so stale data cannot hide an
  in-flight request or a failed refresh.
- The envelope is the only assertive error live region; page-specific error details are linked with
  `aria-describedby` and do not declare a second `role="alert"`.
- Route acceptance can require one result state and three actions for every query panel.

### References

- `web/src/components/common/QueryPanel.tsx`
- `web/test/unit/query-panel.test.tsx`
- `web/test/scripts/issue-77-admin-query-contract.spec.ts`
- GitHub issue `#88`
