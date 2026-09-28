# Spirit 01 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Not recorded |
| Frontend install | `npm --prefix web ci` | Pass when dependencies change or CI cache is untrusted | Not recorded |
| Unit tests | `npm --prefix web test` | Pass | Not recorded |
| Build | `npm --prefix web run build` | Pass | Not recorded |
| Chrome Playwright | Spirit-specific Chrome command covering QueryPanel, form, table, and action confirmation | Pass | Not recorded |

## Issue #108 Verification Record

Issue: `#108` — `[UI] 参考 DeepSeek Platform 重做登录页与登录后控制台风格`.
Branch: `fix/108-deepseek-platform-shell` (cut from `origin/main`; the repository has no `master`).
Environment: macOS workstation, Node.js 24.2.0, npm 11.3.0, system Google Chrome 153.0.8010.53.
Date of record: 2026-09-25.

### Executed commands

| Gate | Command | Result | Evidence |
|---|---|---|---|
| Frontend install | `npm --prefix web ci` | **Pass** — exit 0 | 7 vulnerabilities reported by npm audit; no install error |
| Unit tests | `npm --prefix web test` | **Pass** — 50 files / 186 tests | Baseline was 49 files / 175 tests; `+1` file and `+11` tests from `app-shell.test.tsx` and the extended `login-page.test.tsx` |
| Build | `npm --prefix web run build` | **Pass** — exit 0 | `tsc -b && vite build`, 306 modules, `dist/assets/index-*.css` 57.05 kB |
| Chrome Playwright (issue scope) | `YCSOPEN_E2E_ISOLATED=true npx playwright test --reporter=list test/scripts/issue-108-deepseek-shell.spec.ts` | **Pass** — 8/8 tests | Covers `/login`, `/admin/auth/login`, `/admin/dashboard`, `/tenant/overview` |
| Chrome Playwright (full suite) | `YCSOPEN_E2E_ISOLATED=true npx playwright test --reporter=list` | **15 failed / 150 passed / 3 skipped / 41 did not run** | Baseline on `origin/main` before any edit: **15 failed / 142 passed / 3 skipped / 41 did not run** |
| Diff hygiene | `git diff --check` | **Pass** — no whitespace errors | Run at repository root |
| ESLint (not a required gate for this issue) | `npm --prefix web run lint` | **Fail — pre-existing** — 0 errors, 2 warnings | Both warnings are in `src/pages/admin/dashboard/ComplaintRatioPanel.tsx` and `src/pages/admin/dashboard/DashboardPage.tsx`, which are byte-identical to `origin/main` (`git diff --stat origin/main -- <files>` is empty). `--max-warnings 0` therefore also fails on the trunk. No warning is introduced by issue #108 |

### Chrome Playwright evidence for the issue acceptance routes

| Route | Assertions recorded |
|---|---|
| `/login` | Brand panel left of a pure-white form panel; `#0f1115` brand surface with a white `32px` heading; the form column centered inside the white panel and no wider than `420px`; brand and form panels do not overlap; `documentElement.scrollWidth <= clientWidth` at 1440×900 and 390×844; the surface stacks to one column at 390 with both panels exactly `390px` wide |
| `/login` control states | `:focus-visible` on the username field with a `3px solid rgba(57, 100, 254, 0.28)` outline at `2px` offset and a brand border once the transition settles; hover on submit changes its background; loading state is disabled with the label `登录中…` and identical submit geometry (`x`, `y`, `width`) before and during; error state shows the controlled message inside the reserved slot, clears the password, re-enables submit, and leaves the submit `x`/`y`/`width` unchanged; the error never pushes submit out of the card or the card out of the panel |
| `/login` contract | All nine stable auth selectors present (`shared-auth-login-page`, `-background`, `-card`, `-username`, `-password`, `-remember`, `-submit`, `shared-auth-login-error` absent while clean, `admin-console-identity-auth-login-submit`); checkbox renders 14–18px; username, password and submit all use the `48px` auth control height |
| `/admin/auth/login` | Same two-column geometry as `/login` (intro width/height/x and panel width/x equal within rounding) and no horizontal scroll |
| `/admin/dashboard` and `/tenant/overview` | Identical shell contract on both consoles: root classes `app-shell layout`, sidebar classes `app-sidebar sidebar` at exactly `260px` and `rgb(249, 250, 251)`, content classes `app-content content`, shell background `rgb(255, 255, 255)`, content container `max-width: 1120px`, equal topbar height, navigation inside the sidebar, `:scope > main.content` present, child order `ASIDE, MAIN` |
| Shell selectors | `shared-console-shell`, `-sidebar`, `-brand`, `-topbar`, `-breadcrumb`, `-workspace`, `-content-container`, `-content` all visible; topbar is a `HEADER`; breadcrumb exposes `aria-label="面包屑"`; the `h1` sits inside the content container below the header band; sidebar and content boxes do not overlap |
| Mobile consoles | At 390×844 the sidebar is `390px` wide and stacks above the content on both consoles; the Admin navigation group toggle remains visible, clickable, and leaves exactly one `.sidebar-menu-panel:not([hidden])`; no horizontal scrolling on either route |

### Boundaries and unexecuted items

1. **Pre-existing Chrome suite failures are not fixed by this issue.** 15 tests fail both before and
   after the change; the normalised failing lists are byte-identical
   (`web/verification/issue-108/playwright-baseline-failing-list.txt` vs
   `playwright-after-failing-list.txt`). They are **not** reported as passing. Categories:
   specs that log in against a live backend at `127.0.0.1:8080` (unavailable here), backend-dependent
   interaction timeouts, a spec that still asserts navigation test ids the Admin layout removed
   before this issue, and an upload-status fixture mismatch. See
   `web/verification/issue-108/README.md`.
2. **No backend was started.** All issue-scope Playwright coverage mocks the console API with
   `page.route`, so it does not require the Java service. No end-to-end backend integration was
   exercised by this issue, and none is claimed.
3. **`test:copy:zh-cn` and `test:browser:structural` were not run.** They are separate npm scripts,
   not part of the issue's acceptance list, and the login page's visible copy is unchanged by this
   issue (the copy registry in `web/verification/copy.zh-CN.json` was already out of sync with
   `src/pages/LoginPage.tsx` on the trunk). No copy change is claimed.
4. **The Docker release check (`test:docker-release`) was not run.** This issue changes no container,
   build, or deployment input.
5. **Visual comparison is measurement-based, not pixel-diff based.** The reference was read through
   `getComputedStyle()` and layout boxes on the live pages; no screenshot of an authenticated
   DeepSeek console page is committed, per issue `#108`.
6. **The content container is `1120px`, not the observed reference `936px`.** Deliberate, recorded
   deviation (`DECISIONS.md` `DR-FE01-008`) to keep the frozen query-grid and table contracts.
7. **Page-level stylesheets were not edited.** Any page that hard-codes a colour instead of using a
   shared token will not follow the new shell; that is the accepted cost of `DR-FE01-003`.
8. **Two frozen expectations were updated, both with inline comments naming issue #108:**
   `test/scripts/control-sizing.spec.ts` (focus outline colour follows the new `--color-focus` token;
   geometry unchanged) and `test/scripts/sidebar-navigation.spec.ts` (the sidebar focus ring is the
   shared focus token now that the rail is light; the previous white-outline expectation was already
   failing on the trunk).
9. **`--color-error-bg` was deliberately left at the pre-#108 value `#fff0f2`** so that the frozen
   `dashboard.spec.ts` threshold-row assertion keeps passing; the observed DeepSeek tint is visually
   equivalent.

### Scoped TODO closure

Issue `#108` checklist, each item closed with evidence:

| Issue TODO | Status | Evidence |
|---|---|---|
| Branch from `master`, else from `origin/main` | Done | `git ls-remote --heads origin master main` returns only `refs/heads/main`; branch `fix/108-deepseek-platform-shell` cut from `origin/main` at `7929877` |
| Observe `platform.deepseek.com` sign-in and console on local Chrome and extract requirements | Done | `evidence/deepseek-platform-visual-observation.md`; method log in `ITERATIONS.md` |
| Update the five `01-foundation-contract` documents with this issue's scope | Done | `SPEC.md`, `DECISIONS.md`, `SYSTEM-DESIGN.md`, `ITERATIONS.md`, this file |
| Implement DeepSeek Sign-In style split for `/login` and `/admin/auth/login` | Done | `web/src/pages/LoginPage.tsx`, `web/src/styles/login.css`; `pw-issue-108-login-split`, `pw-issue-108-login-alias` |
| Implement the unified DeepSeek-Platform console shell for Admin and Tenant | Done | `web/src/components/layout/AppShell.tsx`, `shell.css`, `tokens.css`; `pw-issue-108-shell-shared`, `pw-issue-108-shell-hooks` |
| Preserve auth, routing, permissions, business data and stable `data-testid` contract | Done | `web/test/unit/app-shell.test.tsx`, `login-page.test.tsx`, `sidebar-layouts.test.tsx`; full unit suite 186/186; failing Playwright set identical to baseline |
| Add or update unit/component tests and Chrome Playwright acceptance | Done | `web/test/unit/app-shell.test.tsx` (7 tests), `login-page.test.tsx` (8 tests), `web/test/scripts/issue-108-deepseek-shell.spec.ts` (8 tests) |
| Execute and record acceptance commands; confirm scoped TODO empty | Done | Table "Executed commands" above; this section |
| Commit, push the issue branch, create/update the PR; do not merge | Done | Branch `fix/108-deepseek-platform-shell` pushed; pull request `#109` opened against `main` and deliberately left open and unmerged |

## Merge Gate

- Gate item: Scoped TODO set is empty with evidence.
  Closed for issue `#108` — see "Scoped TODO closure".
- Gate item: Shared component behavior is covered by unit tests.
  Covered by `web/test/unit/app-shell.test.tsx`, `login-page.test.tsx`, `sidebar-layouts.test.tsx`,
  `sidebar-menu.test.tsx`.
- Gate item: Representative Chrome Playwright route evidence is recorded.
  Recorded for `/login`, `/admin/auth/login`, `/admin/dashboard`, `/tenant/overview` at 1440×900 and
  390×844.
- Gate item: PR body lists changed routes, changed selectors, verification commands, and boundaries.
  Recorded in pull request `#109`
  (<https://github.com/Stanley-Zheong/ycsopen-sms/pull/109>).
