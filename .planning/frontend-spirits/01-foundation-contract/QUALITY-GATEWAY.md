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

## Issue #87 Verification Record

Issue: `#87` — `统一所有表单组件宽度与四列布局`.
Branch: `fix/87-form-control-layout`, based on post-#108 `main` (`d49828a`).

| Gate | Command | Current result | Durable evidence |
|---|---|---|---|
| Frontend install | `npm --prefix web ci` | Pass; 357 packages installed, npm reported 7 audit findings | Issue verification record |
| Targeted unit | `npm --prefix web test -- --run test/unit/query-panel.test.tsx test/unit/app-shell.test.tsx` | Pass, 16/16 after red-green confirmation | Issue verification record |
| Full unit | `npm --prefix web test` | Pass, 50 files / 186 tests | Issue verification record |
| Build | `npm --prefix web run build` | Pass, 306 modules; existing chunk-size warning only | Issue verification record |
| Issue Chrome | `form-control-layout.spec.ts`, local Chrome project, one worker | Pass, one contract / four behaviors / 56 routes / exact 45 form routes | Raw and summary JSON under `.planning/changes/issue-87-form-control-layout/EVIDENCE/` |
| Affected Chrome | Seven QueryPanel, three control-sizing, and three issue #108 shell cases, isolated on constrained-host Chrome | Pass, 13/13 | Issue verification record |
| Diff hygiene | `git diff --check` | Pass | Issue verification record |
| Review | Independent goal and code review | Pass, no remaining BLOCKER/HIGH | `.planning/changes/issue-87-form-control-layout/REVIEW.md` |

The full backend boundary command ran 986 tests but did not pass in this runner: 7 failures and 4
errors are confined to missing Ruby process harnesses, process-reaping assumptions, and unavailable
migration configuration; 33 tests were skipped. The focused repository release acceptance test
passes 5/5. No backend file changed. The Docker release check was not run because no container,
build, or deployment input changed. Raw Chrome evidence SHA-256 is
`96af3e425fb9f3b79cbd3157d40cf353ff0968daa8dd18bae1aba37ed336a668`.

## Issue #58 Real-Service Closure Gate

Issue: `#58` — `统一所有业务页面查询表单与筛选区`.
Branch: `feature/58-query-panel-production-acceptance`, based on `main` at `1091f637177a8781707e586b9dee8354c36d834e`.

| Gate | Command | Current result | Evidence boundary |
|---|---|---|---|
| Frontend install | `npm --prefix web ci` | PASS; 357 packages installed. npm reported 8 dependency-audit findings (5 moderate, 2 high, 1 critical), unchanged by this issue. | Required because Web source and Docker Playwright source change. |
| Focused unit | `npm --prefix web test -- --run test/unit/tenant-qualification.test.tsx` | RED before implementation: 1 failed/15 passed because `query-result-table` was absent. GREEN after implementation: 16/16 passed; the affected assertion also passed 1/1 after final review fixes. | Proves the tenant result uses the shared result boundary. Existing jsdom `ECONNREFUSED 127.0.0.1:3000` stderr remained non-failing. |
| Targeted Docker spec discovery | `npm --prefix web run test:docker-release -- --list` | PASS; 4 cases discovered, including the unique Issue #58 real-service case. | Proves the new case is discoverable without claiming browser execution. |
| Frontend unit | `npm --prefix web test` | FAIL in the constrained local runner after 1231.65s: 42/50 files and 176/187 tests passed; 11 failures were cross-suite five-second timeouts/unsettled async state. The affected focused file passes 16/16. | Must be adjudicated by the clean CI frontend lane; no completion claim is based on this local full-suite result. |
| Frontend build | `npm --prefix web run build` | PASS after final fixes; 306 modules transformed in 51.19s, with the pre-existing chunk-size warning. | Production bundle gate. |
| Backend regression | `mvn -f core/pom.xml test` | FAIL in this constrained runner after two dependency-download TLS interruptions: Ruby-dependent harness assertions failed because Ruby is absent, then the Surefire fork was killed with exit 137 during concurrent workspace load. The relevant `FinalReleaseAcceptanceTest` completed 5/5. | No Core source changed; the clean CI backend lane must adjudicate the full suite. |
| Planning validators | `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | Pending | This runner currently has no Ruby; CI execution is required if the local boundary remains unavailable. |
| Real Google Chrome + services | `scripts/verify-docker-release` | Four PR runs closed fixture charset/invocation defects and proved the tenant plus audit-filter/page-two path; the fourth exhausted the original 120-second case budget at audit reset. Audit seed reads are now concurrent and the three-page case budget is 300 seconds; a repeat is pending. | Must execute in the registered Docker-capable CI lane; the local Docker daemon is unavailable. No API interception is permitted. |
| Diff hygiene | `bash -n scripts/verify-docker-release`; `shellcheck scripts/verify-docker-release`; `git diff --check` | PASS after final fixes. | Script syntax, static shell analysis, and whitespace hygiene. |
| Independent review | Two bounded semantic reviews plus post-fix rechecks of the implementation and acceptance contract | PASS; all three test-stability and two documentation MEDIUM findings were repaired, and the final reviews found no BLOCKER/HIGH/MEDIUM. | CI remains the final production-environment gate. |

The scoped work is exactly the real-service evidence gap from PR `#62` plus the discovered tenant
result-selector mismatch. Implementation, independent review, and final local rechecks are complete;
pull request and CI evidence remain open, so this record does not yet claim delivery completion.
