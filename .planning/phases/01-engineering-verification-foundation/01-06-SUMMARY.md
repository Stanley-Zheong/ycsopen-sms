---
phase: 01-engineering-verification-foundation
plan: 06
subsystem: browser-runtime-evidence
tags: [playwright, local-google-chrome, real-http, visual-evidence, destructive-validation]
requires:
  - phase: 01-00
    provides: independently reviewed standard-path local Chrome entry and zero-exit bootstrap
  - phase: 01-05
    provides: LOGIN-SMOKE-V1, LOGIN-CARD-IN-VIEWPORT-V2, stable selectors, and repository-owned responder
provides:
  - Fixed-path local Google Chrome filesystem, identity, and Playwright launch probe
  - One real 1440x900 LOGIN-SMOKE-V1 browser run with real loopback HTTP evidence
  - Independently recomputable runtime, visual, transcript, console, and embedded screenshot checksums
affects: [01-09, 01-10, OBL-NFR-BROWSER, browser-evidence]
tech-stack:
  added: []
  patterns: [fixed-code-owned-browser-path, atomic-runtime-evidence, embedded-checksummed-artifacts, table-driven-destructive-fixtures]
key-files:
  created:
    - web/scripts/probe-local-chrome.mjs
    - web/scripts/run-local-chrome-smoke.mjs
    - web/scripts/validate-local-chrome-evidence.mjs
    - web/scripts/test-local-chrome-evidence.mjs
    - web/verification/local-chrome-runtime.schema.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/local-chrome-runtime.json
  modified:
    - web/playwright.config.ts
key-decisions:
  - "Use only the code-owned standard macOS Google Chrome path; version is observed at execution and never pinned."
  - "Retain screenshot, DOM/ancestor/hit-test, transcript, and console facts as embedded checksummed evidence so the validator can independently recompute every artifact digest."
patterns-established:
  - "A runtime PASS requires fixed path and argv, matching CLI/Playwright versions, one 1440x900 run, real browser-observed HTTP semantics, and complete visual evidence."
  - "Negative fixtures may mention prohibited substitutions, but active runtime/config/evidence contains no download, driver, source admission, browser/version matrix, or external browser infrastructure."
requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-empty-only
---

# Phase 01 Plan 06: Current local Chrome runtime evidence Summary

**The current standard-path Google Chrome executed one real 1440x900 `/login` smoke-and-visual scenario whose runtime, response, selector, geometry, transcript, console, and screenshot facts independently validate.**

## Scope Result

- Tasks executed: 2/2.
- The seven implementation/evidence files declared by `01-06-PLAN.md` are the complete Plan 06 file boundary; this summary is the only additional output.
- No browser, driver, archive, provider, tunnel, VM, remote service, browser secret, version pair, or viewport matrix was downloaded, installed, started, or configured.
- Plan 06 does not read or rewrite `local-chrome-entry.json` or `ENTRY-REVIEW.md`.
- `OBL-NFR-BROWSER`, `REQ-NFR-COMPATIBILITY`, Phase 1 TODOs, ROADMAP, and STATE remain open/unchanged. Plan 10 owns durable obligation sealing.
- Git staging, commit, push, checkout, reset, rebase, stash, and clean were not performed.

## Runtime and Browser Evidence

| Fact | Accepted value |
| --- | --- |
| Executable | `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` |
| CLI identity | `Google Chrome 151.0.7922.174` |
| Playwright browser version | `151.0.7922.174` |
| Version policy | Runtime observation only; no pinned major or patch |
| Project | `local-google-chrome` |
| Viewport | `1440x900`, one accepted run |
| Scenario | `LOGIN-SMOKE-V1` |
| Scenario SHA-256 | `98415469dd74730e29a7bafa82cd8dafe016ea8247a6a0b78463a3437922b67a` |
| Visual rule | `LOGIN-CARD-IN-VIEWPORT-V2` |
| Visual-rule SHA-256 | `4e67aaa77fb9649f2d665d036465be02dced712d0f8e98fb84ad3c2d554c4062` |
| Browser response | `POST /api/v1/console/auth/login`, HTTP 401, `application/json`, `X-YCS-Scenario: LOGIN-SMOKE-V1` |
| Safe response body SHA-256 | `f05401e30be56f48f3a98c6682f60ad72b9b1c25155e89d2a675b94282fc345e` |
| Selector/action facts | 7 visible selector facts and 8 ordered PASS actions |
| Visual facts | 6 element observations, ancestor/style checks, 30 accepted hit tests, zero failures |
| Transcript/error facts | 9 transcript events; only the exact expected 401 console diagnostic; zero page/request failures |
| Screenshot | Embedded PNG, 24,163 bytes, SHA-256 `d5980a3fa871eef8a1749e5ff5a8f88f291342d4d5b5a8d361ab9158a9e25a61` |
| Runtime evidence SHA-256 | `932c1e839b56b2cc47b9b01e774dd3bbbea7127cf92456c01aaa93af246ca802` |

The runner first verifies repository-owned loopback health against the generated subject/scenario digests. The browser then navigates to `/login`, proves native required validation, fills only fixed synthetic credentials, checks Remember, submits without request interception, observes the mapped real response, asserts the stable Chinese error, executes the canonical visual evaluator, and captures the evidence artifacts. Credential values are not retained in the evidence transcript.

## Files Created/Modified

- `web/playwright.config.ts` — one `local-google-chrome` project using the fixed standard executable and 1440x900 viewport.
- `web/scripts/probe-local-chrome.mjs` — regular/non-symlink/executable/canonical-path checks, bounded fixed-argv version command, same-version Playwright launch, and atomic evidence writing.
- `web/scripts/run-local-chrome-smoke.mjs` — fixed local build/subject/server flow and the real browser smoke-and-visual run.
- `web/scripts/validate-local-chrome-evidence.mjs` — strict schema, live path/version, semantic, geometry, transcript, error-policy, and artifact-digest recomputation.
- `web/scripts/test-local-chrome-evidence.mjs` — 21 probe/config cases and 36 complete-evidence cases, including prohibited substitutions.
- `web/verification/local-chrome-runtime.schema.json` — closed-field runtime evidence schema with a single run/project/viewport contract.
- `.planning/phases/01-engineering-verification-foundation/EVIDENCE/local-chrome-runtime.json` — final PASS facts and embedded checksummed artifacts.

## TDD and Destructive Coverage

- RED: the first `--case probe` command exited nonzero because the planned probe module did not exist.
- GREEN: the probe/config suite passes 21 cases: known-good fixed-path behavior plus missing/non-regular/symlink/non-executable/non-canonical paths, path override, command exit/timeout/overflow, wrong brand/version, launch/version/viewport/close failures, environment/channel/other-project substitution, and wrong configured viewport.
- GREEN: the complete runtime suite passes 36 cases: known-good evidence plus schema/status/count/path/brand/version/major/live-version/launch/viewport, subject/health, loopback, selector/action, browser-response, body/error, screenshot/DOM/transcript/console, visual/ancestor/hit-test, request-interception, and matrix mutations.
- The real producer writes only acceptance evidence; injected adapters exist only in table-driven fixtures and cannot produce the committed runtime PASS.

## Verification Evidence

All commands exited zero after their intentional RED predecessors.

| Verification | Result |
| --- | --- |
| `node web/scripts/test-local-chrome-evidence.mjs --case probe` | PASS — 21 probe/config cases |
| `node web/scripts/probe-local-chrome.mjs --output .../local-chrome-runtime.json` | PASS — real fixed-path Playwright launch |
| `node web/scripts/test-local-chrome-evidence.mjs` | PASS — 21 probe/config + 36 runtime cases |
| `node web/scripts/run-local-chrome-smoke.mjs --output .../local-chrome-runtime.json` | PASS — real scenario and final atomic evidence |
| `node web/scripts/validate-local-chrome-evidence.mjs --runtime ... --scenario web/verification/browser-scenarios.json` | PASS — independently recomputed final evidence |
| `npm --prefix web run test:browser:compat` | PASS — 8/8 Playwright tests with current local Chrome |
| `npm --prefix web ci` | PASS — lockfile reproduced with 405 packages |
| `npm --prefix web run lint` plus focused ESLint for the four Plan 06 scripts | PASS — zero errors/warnings |
| `npm --prefix web test` | PASS — 4/4 Vitest tests |
| `npm --prefix web run build` | PASS |
| Plan 05 scenario/server/visual/UI drift regressions | PASS — server contract, 15 visual cases, 1 page/7 selectors, 41 drift cases |
| Plan 00 producer/bootstrap/repository/lifecycle/delivery/root-runner regressions | PASS — 14/17/18/20/29/11 cases |
| Planning validators and Phase 1 catalog | PASS — 522 records, 7 selected obligations, 13 plans |
| Real Phase 1 bootstrap | PASS — standard Chrome path, 7 obligations, 13 plans |
| `git diff --check` | PASS |

## Deviations from Plan

### Auto-fixed Issue

**1. [Rule 3 - Blocking] Used browser-safe viewport globals in the Playwright probe.**

- **Found during:** Task 1 focused ESLint.
- **Issue:** Unqualified `innerWidth` and `innerHeight` were correct in the browser but violated the repository's Node-script lint boundary.
- **Fix:** Referenced them through `globalThis` inside `page.evaluate` without changing the observed viewport semantics.
- **Files modified:** `web/scripts/probe-local-chrome.mjs`.
- **Verification:** Focused ESLint, 21 probe/config cases, real Playwright probe, and final runtime validator all pass.

No architectural scope was added.

## Security and Evidence Boundaries

- Browser execution is fixed in code to one canonical local path; no manifest or environment value can substitute another executable.
- The version command has fixed argv, `shell: false`, bounded output, and a command timeout.
- The scenario server binds only `127.0.0.1`, validates subject/scenario health, performs no proxying, and is closed after the run.
- Browser-observed response facts are mandatory; runner-only HTTP output and request interception cannot pass.
- Screenshot existence is insufficient: the validator requires its bytes/digest plus DOM rectangles, ancestor styles, five-point hit tests, transcript, and console/page/request facts.
- Evidence contains synthetic non-production values only and retains no plaintext credential values or browser profile contents.

## Known Stubs

None.

## Remaining Boundaries

- This proves the repository-owned deterministic `/login` scenario with the current Chrome installed on this machine. It does not claim backend authentication, production connectivity, other viewport/browser support, or Phase 56 product acceptance.
- The subject manifest used by the local server is intentionally ephemeral. Plan 09 registers the final source roles and Plan 10 seals the canonical durable subject/evidence manifests before closing the obligation.
- The installed Chrome version may change. Any change requires rerunning entry, runtime smoke/evidence, and affected reviews; no version is pinned.

## Self-Check: PASSED

- All seven declared implementation/evidence files and this summary exist.
- Final evidence is PASS and independently validates against the live standard-path Chrome version and canonical scenario.
- No Plan 06 server, runner, or Playwright process remains.
- Generated `web/dist`, `web/test-results`, and `web/playwright-report` directories were precisely removed after verification and are untracked/absent.
- Active runtime/config/evidence paths contain no browser/driver download, ChromeDriver, source admission, archive checksum, provider, tunnel, secret, remote host, version/viewport matrix, or non-Chrome project path; prohibited substitutions appear only in destructive tests/diagnostics.
- No requirement, obligation, TODO, STATE, ROADMAP, commit, or remote state was changed.

## Next Phase Readiness

Plan 09 can register the Plan 06 source roles and exact browser command; Plan 10 can seal `OBL-NFR-BROWSER` only after its canonical subject and evidence checks pass. No Plan 06 blocker remains.
