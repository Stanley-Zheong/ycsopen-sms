---
phase: 01-engineering-verification-foundation
plan: 05
subsystem: ui-browser-verification
tags: [playwright, google-chrome, react, test-id, real-http, visual-contract]
requires:
  - phase: 01-00
    provides: standard-path local Google Chrome ENTRY contract and reviewed bootstrap
  - phase: 01-01
    provides: tested-subject manifest validation and canonical subject digests
  - phase: 01-04
    provides: UI manifest relation and static drift validation
provides:
  - Stable shared-auth-login-* selectors on the existing /login route
  - One schema-validated LOGIN-SMOKE-V1 contract backed by a deterministic real HTTP 401 responder
  - Canonical LOGIN-CARD-IN-VIEWPORT-V2 evaluator with destructive visual mutations
  - One Playwright local-google-chrome structural project at 1440x900
affects: [01-06, browser-evidence, ui-drift, playwright-automation]
tech-stack:
  added: ["@playwright/test@1.62.1"]
  patterns: [real-http-scenario-without-interception, digest-bound-browser-contract, stable-data-testid-relations]
key-files:
  created:
    - web/playwright.config.ts
    - web/test/phase01/login-scenario.spec.ts
    - web/verification/browser-scenarios.schema.json
    - web/verification/browser-scenarios.json
    - web/scripts/serve-browser-scenario.mjs
    - web/scripts/test-browser-scenario-server.mjs
    - web/scripts/validate-browser-scenario.mjs
    - web/scripts/test-browser-scenario-validator.mjs
  modified:
    - web/package.json
    - web/package-lock.json
    - web/.eslintrc.cjs
    - web/src/pages/LoginPage.tsx
    - web/src/pages/tenant/send/SendPage.tsx
    - web/verification/ui-manifest.json
key-decisions:
  - "Run only the standard-path current local Google Chrome through Playwright channel chrome; do not download or version-pin a browser and do not use ChromeDriver or a browser matrix."
  - "Bind UI evidence to real local HTTP 401 response semantics and canonical subject/scenario/rule digests; Playwright request interception is forbidden."
  - "Treat the browser-emitted diagnostic for the one expected, already-bound 401 as an allowed exact observation while failing every other warning/error."
patterns-established:
  - "Every asserted or interactive /login element has a stable data-testid represented in both the UI manifest and Playwright test."
  - "Visual evidence combines element/ancestor style checks, clipping intersections, five-point hit testing, and screenshot SHA-256."
requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-empty-only
---

# Phase 01 Plan 05: Local Chrome login scenario Summary

**The existing `/login` page now has a digest-bound, real-HTTP structural scenario executed by the current standard-path Google Chrome, with stable selectors and destructive visibility checks.**

## Scope Result

- Tasks executed: 3/3.
- This plan is implemented and its focused checks pass; Phase 1 remains open and no obligation, requirement, or TODO was closed by this summary.
- All implementation changes stay within the 14 files declared by `01-05-PLAN.md`; this summary is the only additional output.
- Task and metadata commits are deliberately deferred to the single final Phase 1 commit required by the user.

## `/login` UI Automation Contract

| Page element | Stable `data-testid` | State/assertion | Automated action |
| --- | --- | --- | --- |
| Login page root | `shared-auth-login-page` | Visible, positive rectangle, inside viewport, unobstructed | Navigate to `/login` and assert |
| Login card/form | `shared-auth-login-card` | Visible, positive rectangle, inside viewport, unclipped, unobstructed | Submit and assert |
| Username input | `shared-auth-login-username` | Required, visible, hit-testable | Empty-submit validation, then fill synthetic username |
| Password input | `shared-auth-login-password` | Required, visible, hit-testable | Empty-submit validation, then fill synthetic password |
| Remember checkbox | `shared-auth-login-remember` | Visible and checked after action | Check, then assert checked |
| Submit button | `shared-auth-login-submit` | Visible and hit-testable | Empty submit, then real scenario submit |
| Rejected-login error | `shared-auth-login-error` | Hidden before rejection; visible with the exact simplified-Chinese error afterward | Assert rejected branch after the bound 401 response |

The scenario action order is: submit empty form, prove native required validation, fill username, fill password, check Remember, assert checked, click Submit, await the mapped responder, and assert the error. It observes the real `POST /api/v1/console/auth/login` request mapped to the canonical `POST /console/auth/login` responder, exact HTTP 401, `application/json`, marker `X-YCS-Scenario: LOGIN-SMOKE-V1`, fixed safe JSON body, and the exact UI error.

Dialogs/modals, popovers, floating controls, drawers, menus, tables, pagination, and table actions are **not applicable** to this page because none exist on `/login`; no synthetic element or selector was invented for them. The existing router remains unchanged.

## Browser and Evidence Contract

- Browser source: `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` through Playwright `channel: "chrome"`.
- Project: `local-google-chrome`; viewport: `1440x900`; the verified runtime reported Google Chrome `151.0.7922.174`.
- The runtime version is observed and attached to evidence, not pinned. There is no browser download, Chrome for Testing archive, ChromeDriver, executable-path override, version/viewport matrix, Chromium project, Edge/Safari/Firefox lane, provider, tunnel, or VM.
- Playwright's `chromium` engine API is used only as the documented launcher for `channel: "chrome"`; neither a bundled Chromium executable nor a Chromium-named project is launched.
- No `page.route`, request interception, or runner-side synthetic response can satisfy the scenario.
- Scenario digest: `98415469dd74730e29a7bafa82cd8dafe016ea8247a6a0b78463a3437922b67a`.
- Visual rule digest: `4e67aaa77fb9649f2d665d036465be02dced712d0f8e98fb84ad3c2d554c4062`.
- Evidence also records subject-manifest/tested-subject digests, computed styles, ancestor intersections, five hit-test points, optional IntersectionObserver corroboration, console/page/network observations, and screenshot SHA-256.

## Files Created/Modified

- `web/package.json`, `web/package-lock.json` — exact Playwright dependency and deterministic structural scripts.
- `web/.eslintrc.cjs` — ESLint 8 configuration covering business source and relevant Plan 05 tests/config/scripts with zero-warning failure.
- `web/playwright.config.ts` — one local Google Chrome project, deterministic reports, failure screenshots/traces, and required local base URL.
- `web/src/pages/LoginPage.tsx` — seven stable selectors, required input semantics, and controlled Remember state without changing the route.
- `web/src/pages/tenant/send/SendPage.tsx` — authorized minimal `unknown`/Axios type guard repair only.
- `web/test/phase01/login-scenario.spec.ts` — exact relation checks and real-browser scenario execution.
- `web/verification/ui-manifest.json` — `/login` selector closure consumed by static drift validation.
- `web/verification/browser-scenarios.schema.json`, `web/verification/browser-scenarios.json` — strict scenario vocabulary, responder, error policy, visual rule, and canonical digests.
- `web/scripts/serve-browser-scenario.mjs`, `web/scripts/test-browser-scenario-server.mjs` — validated static/SPA server, health binding, deterministic 401 responder, and local Chrome identity checks.
- `web/scripts/validate-browser-scenario.mjs`, `web/scripts/test-browser-scenario-validator.mjs` — canonical validator/evaluator plus known-good and destructive fixtures.

## Verification Evidence

All commands exited zero unless explicitly described as a required RED mutation.

| Verification | Result |
| --- | --- |
| `npm --prefix web ci` | PASS; lockfile reproduced with 405 packages |
| `npm --prefix web run lint` | PASS; zero errors and zero warnings |
| `npm --prefix web test` | PASS; 1 file / 4 tests |
| `npm --prefix web run build` | PASS |
| `test -z "$(git ls-files web/dist web/node_modules)"` | PASS; no tracked generated output |
| `node web/scripts/validate-browser-scenario.mjs --contract web/verification/browser-scenarios.json` | PASS; canonical scenario/rule digests recomputed |
| `node web/scripts/validate-ui-drift.mjs --manifest web/verification/ui-manifest.json --routes web/src/router/routes.tsx --check-static` | PASS; 1 page / 7 selectors |
| `node web/scripts/test-ui-drift-validator.mjs` | PASS; 41 mutations |
| `node web/scripts/test-browser-scenario-server.mjs` | PASS; real HTTP health/401 and digest rejection cases |
| `node web/scripts/test-browser-scenario-validator.mjs` | PASS; 15 local-Chrome/visual cases at 1440x900 |
| `npm --prefix web run test:browser:structural` | PASS; 8/8 Playwright tests using `local-google-chrome` |
| `/usr/bin/env ruby .planning/tools/test-produce-phase-01-chrome-entry.rb` | PASS; 14 mutations |
| `/usr/bin/env ruby .planning/tools/test-bootstrap-phase-01.rb` | PASS; 7 obligations / 13 plans / 17 mutations |
| `/usr/bin/env ruby .planning/tools/test-repository-verification.rb` | PASS; 18 mutations |
| `/usr/bin/env ruby .planning/tools/test-phase-lifecycle.rb` | PASS; 20 cases |
| `/usr/bin/env ruby .planning/tools/test-delivery-attestation.rb` | PASS; 29 cases |
| `/usr/bin/env ruby scripts/lib/phase-01/test_run_checks.rb` | PASS; 11 cases |
| `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` | PASS |
| `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner engineering-verification-foundation --assert-unique --assert-traced` | PASS; 522 catalog entries / 7 selected obligations |
| `/usr/bin/env ruby .planning/tools/bootstrap-phase-01.rb --phase-dir .planning/phases/01-engineering-verification-foundation --catalog .planning/PRD-OBLIGATIONS.md` | PASS; 7 obligations / 13 plans / standard local Chrome path |
| `git diff --check` and router/generated/boundary scans | PASS |

The plan's UI-drift example used a non-existent `--playwright` option for the current validator. The semantically exact supported command is `--check-static`; it validates the manifest against the router and the manifest's registered Playwright source, then the 41-mutation validator verifies fail-closed behavior.

## TDD and Destructive Coverage

- RED coverage rejects stale/wrong tested-subject, scenario, and visual-rule digests before browser execution.
- Destructive visual fixtures reject ancestor overflow clipping, element/ancestor clip-path, element/ancestor mask, hidden/zero-opacity state, transparent pointer-capturing overlay, opaque center/corner obstruction, and viewport escape with stable diagnostics.
- A normal input whose internal content clips while its registered border box and ancestors remain fully visible passes.
- GREEN coverage passes the canonical real-server `/login` scenario and all exact selector relations.
- TDD commits are deferred under the Phase 1 single-commit policy; RED/GREEN command evidence is recorded here instead of separate commits.

## Deviations from Plan

### Auto-fixed Issues

1. **[Rule 3 - Blocking] Fixed the pre-existing SendPage explicit-any lint blocker.** Replaced only `catch (err: any)` with `unknown` and used `isAxiosError<{ message?: string }>` for safe message extraction. No SendPage UI, selector, API call, data flow, or business behavior changed. Verified by strict lint, web tests, and build.
2. **[Rule 1 - Bug] Made viewport-escape and overlay fixtures geometrically destructive.** The initial negative offsets were absorbed by the fixture container or missed the target. Fixed positions now actually violate the canonical visual rule and produce the intended stable diagnostic.
3. **[Rule 1 - Bug] Moved five-point corner probes inside rounded controls.** A 2px inset fell outside the painted area of an 8px rounded corner. The canonical inset is now 12px and obstruction fixtures cover those points; the rule/scenario digests were regenerated.
4. **[Rule 1 - Bug] Distinguished the expected 401 browser diagnostic from unexpected console failures.** Local Chrome emits one deterministic console error for the scenario's already-proven 401. The policy permits at most that exact bound diagnostic and still fails every other warning/error, page error, request failure, unexpected response, timeout, or missing artifact.
5. **[Rule 1 - Bug] Corrected the TypeScript ESM JSON import syntax.** The structural spec now uses the supported import attribute so strict lint/build/Playwright loading succeeds.

No architectural scope was added. All fixes are inside declared Plan 05 ownership.

## Security and Evidence Boundaries

- Credentials are fixed synthetic values only; the scenario server does not persist or print submitted credentials.
- The server accepts validated code-owned arguments, validates the subject manifest independently, and does not proxy, evaluate commands, or read secrets.
- This proves a frontend structural scenario against a repository-owned deterministic responder. It does not claim backend authentication, production integration, broad browser compatibility, or Phase 56 product acceptance.
- Remember-me evidence proves only the current checkbox toggle state; persistence across sessions is outside this plan and is not claimed.

## Known Stubs

None. The responder is intentionally deterministic verification infrastructure, not a product backend stub, and is labelled accordingly.

## Remaining Risks

- `npm ci` reports seven dependency audit findings (five moderate, one high, one critical). No audit rewrite was attempted because dependency remediation is outside this plan and could change application behavior; the owning security/dependency work must assess them.
- Plan 06 still owns durable single-run evidence consumption. Plan 05 only supplies and proves the executable structural contract.
- Phase 1 obligations and TODOs remain open until their owning plans and final phase verification close them.

## Self-Check: PASSED

- All 14 declared implementation files and this summary exist.
- No generated `web/test-results`, `web/dist`, or `web/node_modules` output is tracked or left by this plan run.
- No Plan 05 Chrome/server child process remains.
- `web/src/router/routes.tsx` has no diff.
- Active owned files contain no Chrome-for-Testing download, archive SHA, ChromeDriver, executable-path override, browser/version matrix, or non-Chrome project contract.
- No commit was created, as required by the Phase 1 single-final-commit policy.

## Next Phase Readiness

Plan 06 can consume the exact subject/scenario/rule identities and the single `local-google-chrome` command when it creates durable evidence. Readiness does not close any obligation or TODO.
