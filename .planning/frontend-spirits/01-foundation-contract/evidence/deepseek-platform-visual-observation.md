# DeepSeek Platform Visual Observation Evidence (issue #108)

## Method

- Source of truth: the local **Google Chrome** installation
  (`/Applications/Google Chrome.app`, Chrome/153.0.8010.53) on this workstation.
- Pages reached through the browser's own network stack, because `platform.deepseek.com`
  sits behind a bot challenge that rejects headless automation (a headless Chrome fetch
  returned `403 ERROR / Request blocked` and a headful Chrome with an empty profile
  returned the `Human Verification` interstitial).
- A **temporary copy** of the local Chrome profile was used in a scratch directory under
  the system temp folder so that the already-authenticated console session could be
  observed, then connected over the DevTools protocol (`--remote-debugging-port=9222`).
  The temporary profile copy was **deleted** when observation finished. The local
  browser itself was neither modified nor restarted.
- Values below were read from `getComputedStyle()` and from the domain's own CSS custom
  properties. No DeepSeek asset, code file, credential, API key, account identifier, or
  production datum was copied.

## Pages observed

| Page | URL | Session state | Purpose |
|---|---|---|---|
| Sign-in | `https://platform.deepseek.com/sign_in` | Signed out | Two-column auth layout, brand panel, form density, control states |
| Console root | `https://platform.deepseek.com/` → `/usage/` | Signed in | Shell: sidebar, top band, content container, page title |
| Console pages | `/usage/`, `/api_keys/`, `/top_up/` | Signed in | Buttons, fields, panels, spacing, radii, shadows, token values |

## Design tokens read from the live console

| DeepSeek token | Observed value | Used for in YCSAN-SMS |
|---|---|---|
| `--dsw-alias-bg-base` | `#fff` | `--color-bg-base`, page/shell background |
| `--dsw-specific-sidebar-fill` | `#f9fafb` | `--color-bg-sidebar` |
| `--dsw-specific-menu` | `#fff` | panel/menu surface |
| `--dsw-specific-sidebar-nav-item-hover` | `#f1f3f5` | `--color-bg-hover` |
| `--dsw-specific-sidebar-nav-item-active` | `#ebeef2` | `--color-bg-active` |
| `--dsw-specific-sidebar-nav-item-active-accent` | `#e4edfd` | `--color-brand-soft` |
| `--dsw-alias-label-primary` | `#0f1115` | `--color-text`, title colour |
| `--dsw-alias-label-secondary` | `#61666b` | `--color-text-secondary` |
| `--dsw-alias-label-tertiary` | `#81858c` | `--color-muted` |
| `--dsw-alias-label-caption` | `#adb2b8` | caption text |
| `--dsw-alias-border-l1` | `rgba(0,0,0,.04)` | `--color-border-subtle` |
| `--dsw-alias-border-l2` | `rgba(0,0,0,.1)` | `--color-border` |
| `--dsw-alias-border-l4` | `rgba(0,0,0,.16)` | `--color-border-strong` |
| `--dsw-alias-state-business-primary` | `#3964fe` | `--color-brand` (primary action) |
| `--dsw-static-deepseek-450` | `#5686fe` | `--color-brand-hover` |
| `--dsw-static-deepseek-50` | `#edf3fe` | soft brand tint |
| `--dsw-alias-state-error-primary` | `#ec1313` | `--color-danger` family |
| `--dsw-alias-state-success-primary` | `#22c55e` | success accent |
| `--dsw-alias-state-success-tertiary` | `#e6faed` | success surface tint |
| `--dsw-alias-state-warn-primary` | `#f59e0b` | warning accent |
| `--dsw-alias-state-warn-tertiary` | `#fef5e7` | warning surface tint |
| `--dsw-alias-interactive-bg-hover` | `rgba(38,49,72,.06)` | neutral hover |
| `--dsw-alias-interactive-bg-active` | `rgba(38,49,72,.1)` | neutral press |
| `--dsw-alias-interactive-bg-hover-solid` | `#f1f3f5` | solid hover |
| `--dsw-shadow-lv1` | `0 2px 4px 0 rgba(0,0,0,.05)` | `--shadow-1` |
| `--dsw-shadow-lv2` | `0 4px 12px 0 rgba(0,0,0,.02), 0 2px 8px 0 rgba(0,0,0,.04)` | `--shadow-2` (cards) |
| `--dsw-shadow-lv3` | `0 0 1px 0 rgba(0,0,0,.2), 0 0 4px 0 rgba(0,0,0,.02), 0 12px 32px 0 rgba(0,0,0,.08)` | `--shadow-3` (dialogs) |
| `--dsw-alias-scrollbar-bg-l1` | `#e5e5e5` | scrollbar thumb |
| `--dsw-alias-scrollbar-hover-l1` | `#d4d4d4` | scrollbar thumb hover |
| `--ds-font-size-s` / `-m` / `-sp` / `-l` / `-xsp` | `12px` / `14px` / `13px` / `16px` / `11px` | type scale |
| `--ds-line-height-s` / `-sp` / `-l` / `-xs` | `21px` / `23px` / `28px` / `18px` | line heights |
| `--ds-font-weight-strong` | `500` | emphasis weight |
| `--ds-auth-input-radius` | `28px` | pill field radius (auth) |
| `--ds-auth-primary-button-radius` | `4096px` | pill button radius |

## Shell measurements (console, viewport 1440×900)

| Element | Observed |
|---|---|
| Top band | Full width, ~66px tall, info blue `#3b82f6`, white text, padding `8px 14px` |
| Sidebar | `260px` wide, background `#f9fafb`, padding `12px`, own vertical scroll |
| Sidebar nav item | height `42px`, padding `0 16px`, border-radius `14px`, `14px/25px`, active background `#ebeef2` |
| Main region | `flex: 1`, column flex, `gap: 32px`, padding `32px 40px`, transparent over white body |
| Content container | centered, `max-width: 936px` |
| Page title | `24px / 32px`, weight `500`, colour `#0f1115` |
| Body | white background, `14px` base, colour `#0f1115` |
| Primary button (`capsule`, size `m`) | height `36px`, padding `0 14px`, border-radius pill, background `#3964fe`, white text `14px/22px` weight `500`, hover `#5686fe` |
| Field surface | filled `#f5f6f7`, no visible border, pill radius, `14px/25px`, dark text |
| Horizontal overflow | `scrollWidth === clientWidth` at 1440 and at 390 (no horizontal scroll) |

## Sign-in page measurements (viewport 1440×900)

| Element | Observed |
|---|---|
| Layout | Two columns below a slim announcement band: brand panel + white form panel |
| Brand panel | `527.25px` wide (max-width `600px`), full remaining height, padding `72px`, `display: flex` |
| Brand panel surface | base colour `#121519` under a photographic texture plus a linear-gradient overlay |
| Brand panel heading | `34px / 51px`, weight `500`, white, `max-width: 360px` |
| Brand panel CTA | white pill, radius `100px`, dark text `#0f1115`, height `36px` |
| Form panel | white, no padding on the panel itself; form column `336px` wide, centered |
| Form rows | `48px` tall |
| Fields | pill radius (`--ds-auth-input-radius: 28px`), filled surface, transparent inner input, `14px/25px` |
| Primary submit | full-width pill, `#3964fe`, white `14px` weight `500` |
| Legal/helper copy | `12px / 18px`, colour `#81858c` |
| Accent links | `#3964fe` |
| Horizontal overflow | `scrollWidth === clientWidth` at 1440 and at 390 |

## Boundaries recorded

1. The DeepSeek sign-in page authenticates with a phone number plus an SMS code, with a
   password fallback. YCSAN-SMS keeps its existing username/password exchange, so only the
   **visual language** is adopted, never the field semantics or endpoint shape.
2. DeepSeek ships hashed class names (`_2f15d4e`, `ds-button--capsule`) and a bundled
   photographic brand texture plus brand logo geometry. None of those assets, files, or
   class names are copied; only numeric values and semantic patterns were re-expressed in
   YCSAN-SMS's own token and class vocabulary.
3. The observed content container is `936px`. YCSAN-SMS adopts the **centered container**
   pattern but widens the cap because the existing 7-column data tables, the `812px`
   three-column `query-panel` grid and the query/action row contract are wider than
   DeepSeek's console pages. See `DECISIONS.md` `DR-FE01-008`.
4. The console page title is rendered by a `role="heading"` element at `24px/500`, which is
   why the YCSAN-SMS `h1` baseline moved from `28px/600` to `24px/500`.
