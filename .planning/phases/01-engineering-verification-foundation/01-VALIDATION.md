---
phase: 1
slug: engineering-verification-foundation
status: verified
nyquist_compliant: true
wave_0_complete: true
created: 2026-08-30
updated: 2026-08-31
---

# Phase 1 — Validation Strategy

> Phase completion is determined only by the Phase 1 TODO becoming empty. This contract contains no schedule, duration, percentage, or effort estimate.

## Entry and execution boundary

Plan 00 entry is independently authorized at `8 PASS / 0 BLOCKER`, and the real bootstrap validates exactly 13 plans and seven owned obligations. The entry proof consumes the fixed standard-path Google Chrome executable only; historical browser-source artifacts remain audit history and are excluded from the current subject and evidence.

Two selectors consume one literal, code-owned registry:

- Local pre-push: `./scripts/verify-phase-01 --all --evidence-dir .planning/phases/01-engineering-verification-foundation/EVIDENCE` executes every portable lane, the exact `npm --prefix web run test:copy:zh-cn` command, and the Plan 06 runtime validator against the current Google Chrome at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` and viewport `1440x900`.
- CI: `./scripts/verify-phase-01 --ci --evidence-dir .planning/phases/01-engineering-verification-foundation/EVIDENCE` executes only portable lanes. It runs copy static/mutation checks and browser evidence schema/destructive checks, but never installs, downloads, launches, or claims a browser runtime PASS.

Both selectors fail nonzero for missing/unknown/duplicate configuration, missing tools or inputs, timeout, interruption, malformed output, child FAIL, or child BLOCKED. Completed child envelopes remain available after a later failure.

## Test infrastructure

| Layer | Contract |
| --- | --- |
| Planning and lifecycle | Standard-library Ruby validators with one-mutation destructive fixtures |
| Backend | Java 21, Maven, Spring Boot Test, digest-pinned disposable real MySQL and Redis |
| Frontend | Node.js 20+, npm lockfile install, ESLint, Vitest, TypeScript/Vite build |
| UI relation | TypeScript Compiler API route/component/selector/test and stable-row-key validation |
| Copy and export | Exact simplified-Chinese source/export registry, static validator, destructive mutations, and local-only real-page check |
| Browser | Project-local Playwright using only the current standard-path Google Chrome, one `1440x900` project, no driver or downloaded browser |
| Evidence | Canonical source subject, per-child envelopes, aggregate, evidence manifest, redaction, and checksums |

## Seven-obligation executable map

| Obligation ID | Required behavior | Supporting lanes | Focused command | Evidence target | Status |
| --- | --- | --- | --- | --- | --- |
| OBL-FOUND-TRACE-001 | Catalog fields, ownership, behavior, test, and evidence edges fail closed | trace closure case 001 plus owner catalog validation | `/usr/bin/env ruby .planning/tools/test-trace-closure.rb --case CASE-FOUND-TRACE-001` | `EVIDENCE/OBL-FOUND-TRACE-001.json` | pending Plan 10 evidence |
| OBL-FOUND-TRACE-002 | Reverse links, orphan rows, duplicate owners, and prose-only spoofing fail closed | trace closure case 002 plus owner catalog validation | `/usr/bin/env ruby .planning/tools/test-trace-closure.rb --case CASE-FOUND-TRACE-002` | `EVIDENCE/OBL-FOUND-TRACE-002.json` | pending Plan 10 evidence |
| OBL-FOUND-TRACE-003 | Java, web install/lint/test/build, structural login, real MySQL/Redis, exact local copy command, portable copy checks, timezone, subject, and evidence reduction are all reachable and fail closed | shared registry `core-unit` through `local-chrome-artifact-portable`; local selector additionally runs `copy-local-browser` | `./scripts/verify-phase-01 --all --evidence-dir .planning/phases/01-engineering-verification-foundation/EVIDENCE` | `EVIDENCE/OBL-FOUND-TRACE-003.json` | pending Plan 10 evidence |
| OBL-FOUND-TRACE-004 | Lifecycle and delivery fixtures execute in fixed order and preserve both results | `phase-lifecycle-delivery` runs lifecycle then delivery with no manifest-provided command | `/usr/bin/env ruby scripts/lib/phase-01/run_checks.rb --internal-trace-004` | `EVIDENCE/OBL-FOUND-TRACE-004.json` | pending Plan 10 evidence |
| OBL-FOUND-UI-DRIFT-001 | Route, page, DOM selector, and Playwright locator relations remain exact | UI AST known-good plus missing/stale relation mutations | `/usr/bin/env node web/scripts/test-ui-drift-validator.mjs` | `EVIDENCE/OBL-FOUND-UI-DRIFT-001.json` | pending Plan 10 evidence |
| OBL-FOUND-UI-DRIFT-002 | Mutable/sensitive selector suffixes and missing row-key metadata are rejected | UI AST row-contract mutations | `/usr/bin/env node web/scripts/test-ui-drift-validator.mjs` | `EVIDENCE/OBL-FOUND-UI-DRIFT-002.json` | pending Plan 10 evidence |
| OBL-NFR-BROWSER | Existing Plan 06 evidence proves one current standard-path Google Chrome launch, LOGIN-SMOKE-V1, LOGIN-CARD-IN-VIEWPORT-V2, real browser-observed 401, selectors, geometry, artifacts, and clean error policy | Plan 06 runtime schema/destructive suite plus live standard-path/version validator; only local `--all` can satisfy runtime acceptance | `/usr/bin/env node web/scripts/validate-local-chrome-evidence.mjs --runtime .planning/phases/01-engineering-verification-foundation/EVIDENCE/local-chrome-runtime.json --scenario web/verification/browser-scenarios.json` | `EVIDENCE/OBL-NFR-BROWSER.json` | pending Plan 10 evidence |

The copy and timezone lanes above support `OBL-FOUND-TRACE-003`; they do not create product-level Chinese or timezone obligations in this phase.

## CI contract

- The workflow configures Java 21 and Node.js 20, pulls only the exact official MySQL and Redis image digests declared by the repository service harness, then invokes the shared `--ci` selector.
- CI does not contain a browser install step, browser executable override, driver, provider, tunnel, remote host, VM, or non-Chrome project.
- The full registry is validated before selector filtering. Therefore CI fails if the exact local copy argv is removed or moved into CI, if the portable copy checks are absent, if any CI check has a browser layer, or if OBL-NFR-BROWSER is bound to anything other than the single local Plan 06 validator.
- Runner output is redacted before persistence. On failure, CI uploads only the generated redacted run directory with bounded retention.
- CI PASS is portable verification only. It cannot close browser acceptance, phase TODOs, requirement status, review, delivery, tag, or PR state.

## Nyquist sign-off gates

- [x] Root runner destructive fixtures pass, including unknown selector, missing executable, duplicate ID, child FAIL/BLOCKED, timeout, interruption, malformed child output, illegal subject membership, and outside evidence path.
- [x] TRACE-004 lifecycle and delivery children both pass in fixed order.
- [x] Java 21 backend, Node frontend install/lint/test/build, UI relation, structural login, copy, MySQL, Redis, and timezone lanes pass.
- [x] Plan 06 local Chrome evidence fixtures and live runtime validation pass using the current standard-path browser.
- [x] Portable `--ci` fixture proves zero browser-layer commands and no local runtime claim.
- [x] YAML parsing, repository verification, planning validators, catalog validation, bootstrap, generated-output, and diff checks pass.
- [x] `wave_0_complete: true` and `nyquist_compliant: true` were set only after every item above was executable and green.

**Current approval:** Plan 09 validation wiring is verified. Owned obligation TODOs remain open for Plan 10 evidence and later review/delivery gates; no phase completion, review, delivery, tag, or PR claim is made here.
