---
phase: 01-engineering-verification-foundation
plan: 07
subsystem: copy-export-verification
tags: [zh-CN, typescript-ast, csv, playwright, local-google-chrome, fail-closed]
requires:
  - phase: 01-00
    provides: independently reviewed standard-path local Chrome entry and zero-exit bootstrap
  - phase: 01-05
    provides: stable shared-auth-login selectors and the real-loopback LOGIN-SMOKE-V1 responder
provides:
  - Versioned exact simplified-Chinese source/runtime/error/export registry with fixture/production labels
  - TypeScript AST and RFC 4180-style CSV validation with stable fail-closed diagnostics
  - Existing /login runtime copy verification through the current standard-path local Google Chrome
affects: [01-09, 01-10, OBL-FOUND-TRACE-003, final-release-acceptance]
tech-stack:
  added: []
  patterns: [exact-copy-registry, classified-foundation-fixtures, real-http-runtime-copy-check, table-driven-destructive-mutations]
key-files:
  created:
    - web/verification/copy.zh-CN.json
    - web/verification/fixtures/zh-cn-export.csv
    - web/scripts/validate-copy-zh-cn.mjs
    - web/scripts/test-copy-zh-cn.mjs
    - web/test/phase01/chinese-copy.spec.ts
  modified:
    - web/package.json
    - web/playwright.config.ts
key-decisions:
  - "Require exact registered copy and exact technical tokens; Han-character presence alone never satisfies the contract."
  - "Classify /login entries as production surfaces and CSV data as a foundation fixture that cannot close product acceptance."
  - "Reuse an already-valid Plan 05 loopback server during the full structural suite and start the same server contract only for an isolated copy run."
  - "Use one code-owned 127.0.0.1 default when PHASE01_BASE_URL is absent and reject every non-loopback or component-bearing override."
patterns-established:
  - "Every registered copy entry carries production-surface or foundation-fixture classification."
  - "Runtime copy validation scans hidden and visible DOM text plus ARIA/placeholder attributes and rejects any unregistered value."
requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-empty-only
---

# Phase 01 Plan 07: Simplified-Chinese copy and export contract Summary

**A versioned exact-copy registry now validates `/login` source, real-Chrome runtime/error behavior, and a robustly parsed synthetic CSV export without claiming product-wide Chinese acceptance.**

## Scope Result

- Tasks executed: 2/2.
- Changes are limited to the seven files declared by the revised `01-07-PLAN.md`; this summary is the only additional output.
- `LoginPage.tsx`, the router, STATE, ROADMAP, TODO, requirement status, obligation evidence, and product-acceptance artifacts were not modified.
- `OBL-FOUND-TRACE-003` remains open for Plan 10 evidence sealing; `OBL-NFR-CHINESE` remains owned by final release acceptance.
- No Git staging, commit, stash, push, checkout, reset, rebase, or clean operation was performed.

## Copy and Export Contract

| Surface | Classification | Exact coverage |
| --- | --- | --- |
| `/login` JSX text | `production-surface` | Heading, Remember label, submit label |
| `/login` attributes | `production-surface` | Username/password ARIA labels and placeholders |
| Rejected-login error | `production-surface` | Exact simplified-Chinese error after the real 401 response |
| Product name token | `production-surface` | Exact reviewed token `YCSAN-SMS`; wildcard and regex-shaped entries are forbidden |
| Synthetic CSV export | `foundation-fixture` | Three exact Chinese headers and two exact synthetic rows |

The source validator uses the TypeScript Compiler API to traverse TSX nodes and extract JSX text, user-visible attributes, and `setError` literals. It compares normalized relation entries in both directions, so an extra untranslated string and a missing registered value both fail. Traditional-only characters are rejected before general relation mismatch reporting.

The CSV parser is a state machine rather than delimiter splitting. It handles quoted commas, escaped double quotes, CRLF/LF records, and embedded newlines, and rejects unclosed quotes, invalid characters after closing quotes, quotes inside unquoted fields, and inconsistent column counts. The committed fixture proves quoted-comma and escaped-quote paths.

Every registry section explicitly says whether it is a production surface or foundation fixture. The top-level contract fixes `productAcceptance: false` and retains `final-release-acceptance` as the product owner.

## `/login` Runtime Contract

| Element | Stable `data-testid` | Runtime action/assertion |
| --- | --- | --- |
| Page root | `shared-auth-login-page` | Navigate to `/login`, require visible, scope DOM copy scan |
| Login form | `shared-auth-login-card` | Require registered presence through the selector contract |
| Username | `shared-auth-login-username` | Fill fixed synthetic username; scan ARIA and placeholder text |
| Password | `shared-auth-login-password` | Fill fixed synthetic password; scan ARIA and placeholder text |
| Remember checkbox | `shared-auth-login-remember` | Require selector and scan its label text |
| Submit | `shared-auth-login-submit` | Click and await the real mapped responder |
| Error | `shared-auth-login-error` | Require exact simplified-Chinese error after rejection |

The runtime test is linked to `CASE-FOUND-TRACE-003` and `PW-FOUND-LOGIN-SMOKE`. It uses the Plan 05 server, observes the browser's actual `POST /api/v1/console/auth/login`, HTTP 401, JSON body, and `X-YCS-Scenario: LOGIN-SMOKE-V1` marker, then validates all DOM text and user-visible attributes inside the existing page root. Hidden text is deliberately included in the scan. There is no `page.route`, context routing, request interception, new route, or JSX owner.

Dialogs, drawers, popovers, floating controls, menus, tables, pagination, and row actions are not applicable because the existing `/login` surface contains none; no synthetic UI element was added.

## Destructive Coverage

The mutation suite passes 20 cases including the known-good control:

- unregistered English source copy;
- traditional-only source copy;
- hidden unregistered source copy;
- missing source surface;
- overbroad technical-token allowlist;
- missing export header;
- unclosed quoted CSV field;
- export fixture mislabeled as production;
- missing environment selects the fixed code-owned loopback default;
- explicit free-port loopback input remains accepted;
- remote origin is rejected;
- URL credentials are rejected;
- path/query/fragment components are rejected;
- malformed URL is rejected;
- hidden unregistered runtime copy;
- English ARIA label;
- missing runtime error selector;
- missing responder marker;
- missing trace linkage or forbidden interception contract;
- canonical source/export/runtime control.

Each destructive case changes one invariant and requires its stable diagnostic. A mutation is never accepted merely because it still contains Han characters.

## Verification Evidence

The first implementation was not accepted as complete: it passed only when `PHASE01_BASE_URL` was supplied. The main agent independently removed that variable and found that Playwright configuration failed before test discovery. The revised plan added `web/playwright.config.ts` to Plan 07 ownership, and the corrected standalone command now passes without caller configuration.

| Verification | Result |
| --- | --- |
| Initial validator command before implementation | RED — module absent, nonzero as expected |
| Independent main-agent standalone acceptance before config fix | RED — `PHASE01_BASE_URL is required` before Playwright test discovery |
| Static copy/export validator | PASS — 8 source entries, 3 headers, 2 parsed rows |
| `node web/scripts/test-copy-zh-cn.mjs` | PASS — 20 known-good/destructive/config cases |
| `env -u PHASE01_BASE_URL npm --prefix web run test:copy:zh-cn` | PASS — exact standalone chain, 1/1 current local Chrome runtime plus validator and mutation suite |
| `npm --prefix web ci` | PASS — 405 packages reproduced from lockfile |
| `npm --prefix web test` | PASS — 4/4 Vitest tests |
| `npm --prefix web run build` | PASS |
| `npm --prefix web run lint` | PASS — zero errors and warnings |
| Plan 05 scenario/server/visual/UI-drift regressions | PASS — server, 15 visual cases, 1 page/7 selectors, 41 drift cases |
| Plan 06 evidence regressions | PASS — 21 probe/config and 36 runtime destructive cases; durable evidence revalidated |
| `npm --prefix web run test:browser:compat` | PASS — 9/9 Playwright tests using current standard-path Google Chrome at 1440x900 |
| Plan 00 producer/bootstrap/repository/lifecycle/delivery/root-runner regressions | PASS — 14/17/18/20/29/11 cases |
| Planning validators, obligation catalog, real bootstrap | PASS — 522 records, 7 selected obligations, 13 plans, standard Chrome path |
| `git diff --check` | PASS |

The package script contains exactly three fail-fast commands, starting with `playwright test test/phase01/chinese-copy.spec.ts`, followed by the static validator and mutation runner using paths relative to the web working directory. When `PHASE01_BASE_URL` is absent, Playwright selects the code-owned `http://127.0.0.1:41737` default and the test starts the Plan 05 server there. A caller may still select a free port, but only through a plain `http://127.0.0.1:<port>` origin. Remote hosts, HTTPS substitution, credentials, paths, queries, fragments, and malformed URLs fail during configuration. The combined Plan 05 suite validates and reuses its already-running `LOGIN-SMOKE-V1` server.

## TDD Record

- RED: the planned validator path did not exist and the canonical command exited nonzero with `MODULE_NOT_FOUND`.
- First GREEN was incomplete: the canonical source/export fixture, 14 mutations, and real local-Chrome runtime passed only with an explicitly configured loopback URL.
- Independent RED: the main agent ran the exact command without `PHASE01_BASE_URL`; the existing Playwright config rejected it before loading the test.
- Corrected GREEN: the exact environment-free command passed, followed by 20 mutation/config cases and one real local-Chrome runtime test.
- Regression: the combined Plan 05 suite passed all 9 tests after server reuse was made explicit.
- TDD commits are deferred by the user's single final Phase 1 commit rule; command evidence replaces per-task commit claims.

## Deviations from Plan

### Auto-fixed Issues

1. **[Rule 3 - Blocking] Kept the runtime test buildable without adding Node type packages.**
   - The frontend TypeScript project intentionally has no Node type declarations, while the Playwright setup needs existing Node runtime modules to build and start the Plan 05 server.
   - Narrow `@ts-expect-error` annotations cover only those built-in module imports; runtime values receive explicit local types. No dependency or lockfile was added.
   - Verified by TypeScript build, ESLint, isolated copy runtime, and the combined Playwright suite.
2. **[Rule 1 - Bug] Reused the existing validated Plan 05 server in the combined suite.**
   - The first combined regression had the Plan 05 runner and copy test bind the same loopback port, producing `EADDRINUSE`; all eight existing Plan 05 tests still passed.
   - The copy test now validates the existing health endpoint and reuses it. An isolated run still starts and closes the same repository-owned server itself.
   - Verified by isolated 1/1 and combined 9/9 local-Chrome runs.
3. **[Rule 3 - Blocking] Replaced one unnecessary regex escape flagged by strict ESLint.**
   - Broad-token metacharacters are now checked through an exact character set without changing policy.
   - Verified by lint and all 20 mutation/config cases.
4. **[Rule 1 - Bug] Made the exact package command genuinely standalone after independent acceptance rejected the first result.**
   - The initial summary incorrectly treated a run with an externally supplied `PHASE01_BASE_URL` as proof of the exact standalone command. The main agent's environment-free rerun failed before Playwright loaded the test.
   - The revised plan formally added `web/playwright.config.ts` to ownership. Its missing-variable path now selects one fixed code-owned loopback origin, while explicit inputs are parsed and restricted to `http://127.0.0.1:<port>` without credentials or URL components.
   - Six configuration cases cover default/explicit success and remote, credential-bearing, component-bearing, and malformed failures. The exact `env -u PHASE01_BASE_URL npm --prefix web run test:copy:zh-cn` command now passes.

No architecture, route, business behavior, dependency, product claim, or evidence ownership changed.

## Security and Evidence Boundaries

- Runtime credentials, CSV values, and error cases are fixed synthetic values only; no production data, secrets, cookies, profiles, or logs are retained.
- The runtime test reaches only `127.0.0.1` and accepts only the validated Plan 05 scenario identity.
- The default base URL is a code-owned loopback origin. Configuration rejects external origins and does not expose a browser executable, channel, or origin override path.
- Technical tokens are exact strings with reviewed reasons. Wildcards, regex metacharacters, duplicate tokens, missing classifications, and producer-controlled product-acceptance claims fail closed.
- The validator emits structured command results that Plan 10 can incorporate into the existing trace evidence envelope. Plan 07 creates no separate obligation evidence artifact.
- This plan proves the verifier against one existing production surface and one synthetic export fixture. It does not prove all future UI, errors, exports, storage, APIs, displays, or international-message behavior.

## Known Stubs

None. HTML `placeholder` attributes are real registered `/login` copy surfaces, not implementation placeholders. The CSV is intentionally synthetic verification data and is explicitly classified as a foundation fixture.

## Remaining Boundaries

- Plan 09 must register these source/contract/validator/test roles in the canonical tested-input subject.
- Plan 10 must consume and seal the copy subresults under `OBL-FOUND-TRACE-003` before that obligation can close.
- Phase 56 must rerun the contract against every delivered production surface before product-level simplified-Chinese acceptance can close.
- `npm ci` continues to report the existing seven dependency audit findings (five moderate, one high, one critical); dependency remediation is outside this plan.

## Self-Check: PASSED

- All seven declared files and this summary exist.
- The package command is the exact planned three-segment fail-fast chain.
- No generated `web/dist`, `web/test-results`, `web/playwright-report`, or `web/node_modules` directory remains, and none is tracked.
- No Plan 07 server, Playwright test, or temporary copy-runtime process remains.
- No copy/Chinese product-obligation evidence artifact was created.
- No commit or remote state was created or changed.

## Next Phase Readiness

Plan 09 can register the seven owned inputs, and Plan 10 can consume the structured validator/runtime results without reinterpreting the foundation fixture as product acceptance. Plan 07 has no remaining blocker.
