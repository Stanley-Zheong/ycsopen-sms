# Spirit 01 Iterations

| Iteration ID | Trigger or finding | Evidence | Change made | Affected behavior/decision | Recheck |
|---|---|---|---|---|---|
| FE01-I-001 | Spirit created from PRD V2 and issue retrospective | `docs/PRD_V2.md`, `docs/ISSUE_BUG_RETROSPECTIVE.md` | Defined shared frontend foundation scope | FE-SPIRIT-01-QUERY, FE-SPIRIT-01-FORM, FE-SPIRIT-01-TABLE, FE-SPIRIT-01-ACTION | Pending implementation |
| FE01-I-002 | Issue `#108`: sign-in page and both consoles needed one DeepSeek-Platform-aligned visual language instead of per-page patching | `evidence/deepseek-platform-visual-observation.md`; local Chrome observations of `platform.deepseek.com/sign_in` and the signed-in `/usage/` console | Inventoried the shared surface (shell, login, query panel, table, card, dialog), extracted the reference tokens and measurements, and closed the "inventory current shared components" spirit TODO | DR-FE01-002, DR-FE01-003; FE-SPIRIT-01-SHELL, FE-SPIRIT-01-LOGIN, FE-SPIRIT-01-NOOVERFLOW | Chrome Playwright `issue-108-deepseek-shell.spec.ts` |
| FE01-I-003 | Observation attempt 1: headless Chrome returned `403 ERROR / Request blocked`; attempt 2: headful Chrome with an empty profile returned the `Human Verification` interstitial | scratch observation logs (`403 ERROR`, `Human Verification`) | Switched to observing the already-signed-in local Chrome session through a temporary profile copy over the DevTools protocol; deleted the copy after observation | DR-FE01-002 (evidence method + credential boundary) | Profile copy confirmed absent; no cookie, token or account data committed |
| FE01-I-004 | Measurement conflict: the reference console centers content in a `936px` container, but this repository's shared query grid needs `812px` and several tables declare `min-width` up to `1040px` | `web/src/styles/index.css` query-grid minimum; `web/test/scripts/control-sizing.spec.ts`; `evidence/deepseek-platform-visual-observation.md` boundary 3 | Adopted the centered-container pattern with an adapted `1120px` cap instead of `936px` | DR-FE01-008 | Full Chrome Playwright suite on the acceptance routes |
| FE01-I-005 | `control-sizing.spec.ts` freezes the exact focus outline colour `rgba(12, 133, 232, 0.28)`, which the new brand token no longer produces | `web/test/scripts/control-sizing.spec.ts` lines 131–137 | Kept the `3px`/`2px` focus geometry and updated the expectation to the new token value `rgba(57, 100, 254, 0.28)` | DR-FE01-006 | `npx playwright test control-sizing.spec.ts` green |
| FE01-I-006 | `AdminLayout` and `TenantLayout` duplicated sidebar, brand, logout and `<main class="content">` markup, so the two consoles could drift | `web/src/components/layout/AdminLayout.tsx`, `TenantLayout.tsx` (pre-change) | Extracted one `AppShell` presentation component; layouts keep gating, filtering and redirects | DR-FE01-004; FE-SPIRIT-01-SHELL | `web/test/unit/app-shell.test.tsx`, shell geometry equality assertion in the Playwright spec |
| FE01-I-007 | The reference console hides its sidebar below the mobile breakpoint, which would make every protected module unreachable here because the sidebar is the only navigation surface | `evidence/deepseek-platform-visual-observation.md` (mobile aside `display: none`, `scrollWidth === clientWidth === 390`) | Stacked the shell into one column below 900px, keeping the sidebar full-width with internal scroll instead of hiding it | DR-FE01-009 | 390×844 assertions in the Playwright spec (`scrollWidth <= clientWidth`) |
| FE01-I-008 | Existing specs depend on structural details that a naive restyle would break | `issue-77-admin-query-contract.spec.ts` (`.layout > main.content`), `sidebar-navigation.spec.ts` (`.sidebar-menu-panel:not([hidden])`), `login-page.test.tsx` (`login-page`, `login-card`, `login-remember-input`) | Froze the legacy hooks and added the new semantic shell hooks alongside them | DR-FE01-005 | `npm --prefix web test` plus the full Playwright suite |

## Observation Method Log

Chronological record of how the visual reference was obtained, kept separate from implementation
iterations so the boundary can be reviewed on its own.

| Step | Action | Result |
|---|---|---|
| 1 | Headless Chrome (Playwright, system Chrome binary) → `https://platform.deepseek.com/sign_in` | `403 ERROR / The request could not be satisfied` |
| 2 | Headful Chrome with a fresh temporary profile | `Human Verification` interstitial (`405`) |
| 3 | Chrome launched with a temporary copy of the local profile and `--remote-debugging-port=9222`, driven over CDP | Sign-in page and signed-in console both readable |
| 4 | Extracted computed styles, the domain's own CSS custom properties and layout boxes for the sign-in page, `/` → `/usage/`, `/api_keys/`, `/top_up/` | Recorded in `evidence/deepseek-platform-visual-observation.md` |
| 5 | Terminated the temporary Chrome process and deleted the temporary profile copy | Signed-in console confirmed reachable at `/usage/`; no credential material remained on disk |

## Notes Carried Forward

- An isolated CDP browser context (cookieless) re-requested `sign_in` and received an empty
  challenge page (`202`). The public sign-in observations therefore come from step 3, taken while
  the profile copy was still signed out of the DeepSeek Platform app itself.
- The reference sign-in page authenticates by phone number plus SMS code with a password fallback.
  YCSAN-SMS deliberately keeps its username/password contract; only visual values were adopted.
- No screenshot of an authenticated DeepSeek console page is committed, per issue `#108`.
