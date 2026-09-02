---
phase: 01-engineering-verification-foundation
plan: 09
subsystem: verification-orchestration
tags: [ruby, github-actions, java-21, node-20, mysql, redis, local-google-chrome, fail-closed]

requires:
  - phase: 01-00
    provides: independently authorized fixed-path local Chrome entry and 13-plan bootstrap
  - phase: 01-02
    provides: trace closure and lifecycle validators
  - phase: 01-03
    provides: delivery-attestation fixtures
  - phase: 01-04
    provides: UI AST drift validator
  - phase: 01-06
    provides: current local Chrome runtime evidence and validator
  - phase: 01-07
    provides: simplified-Chinese copy/export contract
  - phase: 01-08
    provides: real MySQL, Redis, and timezone verification
provides:
  - one literal 23-check registry covering exactly seven Phase 1 obligations after the Claude CI/browser-path split
  - portable CI selector with zero browser-layer commands
  - local selector using the exact copy command and current standard-path Google Chrome validator
  - Java 21 and Node 20 CI workflow with digest-pinned disposable services and redacted failure artifacts
  - verified seven-row Nyquist validation contract
affects: [01-10, 01-11, 01-12, phase-01-evidence, phase-01-delivery]

tech-stack:
  added: []
  patterns: [literal fixed-argv registry, selector-safe portable CI, ordered composite child lane, source-only canonical subject]

key-files:
  created:
    - .planning/phases/01-engineering-verification-foundation/01-09-SUMMARY.md
  modified:
    - .github/workflows/ci.yml
    - scripts/lib/phase-01/run_checks.rb
    - .planning/phases/01-engineering-verification-foundation/01-VALIDATION.md

key-decisions:
  - "A public selector validates the complete literal registry before filtering, so CI cannot silently remove the exact local copy command or bind a browser-layer check."
  - "Portable CI runs copy static/mutation checks and local-Chrome schema/destructive checks, but only local --all executes the browser-bearing copy command and OBL-NFR-BROWSER runtime validator."
  - "Runtime evidence, mutable TODO/review state, entry evidence, and delivery-attestation state are validation targets rather than canonical source-subject members."

requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-empty-only
---

# Phase 01 Plan 09: Shared root registry and portable CI Summary

**One literal fixed-argv registry now drives both portable CI and local pre-push verification across exactly seven obligations, while browser runtime authority remains exclusively with the current standard-path Google Chrome path.**

## Scope Result

- Tasks executed: 2/2.
- Registry: 23 total checks, 20 portable `--ci` checks, and 19 local `--all` checks. The structural login contract and local-Chrome visual mutation command are physically separate entries.
- Obligation set: exactly `OBL-FOUND-TRACE-001..004`, `OBL-FOUND-UI-DRIFT-001..002`, and `OBL-NFR-BROWSER`.
- Files changed: the three plan-owned files plus this summary only.
- Phase status: not asserted. All owned obligation TODOs remain open for Plan 10 evidence and later review/delivery gates.
- Git status: no staging, commit, branch, tag, push, or remote mutation was performed.

## Root Registry Contract

The runner now owns explicit argv arrays, cwd, obligation/case links, timeout, output contract, scopes, and path/role source inputs for every check. It rejects an empty selection, duplicate check ID, duplicate input, malformed argv/input/timeout/output contract, unknown selector, duplicate/conflicting selector, missing executable, timeout, interruption, malformed child output, and child FAIL/BLOCKED.

The stable registry reaches:

- TRACE-001/002 catalog and bidirectional trace destructive cases;
- evidence-kernel self-test;
- TRACE-004 as one ordered composite lane that runs lifecycle and then delivery fixtures, emits both child transcripts, and stops nonzero on missing/FAIL/BLOCKED;
- Java unit and real-service integration suites;
- npm lockfile install, lint, unit, and build;
- UI AST route/page/selector/test and row-key drift checks;
- real-loopback `/login` server and scenario contract fixtures;
- exact local `npm --prefix web run test:copy:zh-cn` plus separate portable copy static/mutation checks;
- digest-pinned real MySQL and Redis plus timezone/IANA fixtures;
- Plan 06 local-Chrome contract/destructive fixtures and the single runtime validator.

Every public run validates the whole registry first. The contract specifically proves that the exact copy argv exists only in local `all`, CI contains both portable copy checks, CI contains no `browser` layer, and only `local-chrome-runtime` owns `OBL-NFR-BROWSER`.

## Local and CI Boundary

| Selector | Checks | Browser behavior | Result |
| --- | ---: | --- | --- |
| `--ci` | 20 | Runs portable copy checks, synthetic local-Chrome destructive fixtures, and structural validation of the supplied runtime artifact; records `runtime_claim=false` and launches no browser | PASS, aggregate PASS, manifest PASS |
| `--all` | 19 | Executes the exact copy command against current local Chrome and runs the Plan 06 live standard-path/version validator | PASS, aggregate PASS, manifest PASS |

The local browser result observed Google Chrome `151.0.7922.174` at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`, viewport `1440x900`, scenario `LOGIN-SMOKE-V1`, and visual rule `LOGIN-CARD-IN-VIEWPORT-V2`. No browser or driver was installed or downloaded.

The portable selector intentionally does not execute the browser-bearing copy command. It proves that command remains present through the full-registry contract, then executes only `validate-copy-zh-cn.mjs` and `test-copy-zh-cn.mjs`. OBL-NFR-BROWSER has no CI envelope, so a portable PASS cannot substitute for local runtime acceptance.

## CI Workflow

`.github/workflows/ci.yml` now has one Phase 1 portable job that:

- configures Temurin Java 21 and Node.js 20;
- pulls the exact MySQL and Redis image digests declared by `service_checks.rb`;
- invokes only `./scripts/verify-phase-01 --ci ...` instead of duplicating registry logic;
- uploads only runner-redacted generated diagnostics on failure with bounded retention;
- contains no browser install/download, ChromeDriver, provider, tunnel, remote-host, VM, or alternative-browser job.

The workflow was YAML-parsed and statically checked locally. A GitHub-hosted workflow run is a later PR/remote boundary and is not claimed by this plan.

## TDD Record

- RED: the initial runner exposed only one evidence-kernel check, lacked six obligations and `--ci`, and failed the seven-obligation registry assertion.
- GREEN: the completed registry contract reports 23 checks, exactly seven obligations, 20 portable checks, 19 local checks, zero CI browser checks, one exact local copy argv, and one exact local visual argv carrying `--run-local-chrome`.
- Destructive regression: the existing runner suite remains `PASS cases=11`, including later-child failure retention, BLOCKED, timeout, interruption, malformed output, missing executable, duplicate ID, illegal historical browser subject membership, outside evidence path, and unknown wrapper option.
- Missing local runtime artifact returns internal status 75/BLOCKED; it never becomes a portable or local skipped PASS.

Task-level commits are intentionally deferred to the user's single final Phase 1 delivery commit rule.

## Deviations from Plan

### Auto-fixed Issues

1. **[Rule 1 - Bug] Normalized child output to UTF-8 before redaction and JSON persistence.**
   - The first complete portable run had 20 PASS children but aggregate validation rejected the Vitest/build envelopes because Chinese UTF-8 bytes were retained in memory as `ASCII-8BIT` and parsed back from JSON as UTF-8.
   - The runner now force-decodes bounded child bytes as UTF-8, scrubs invalid sequences, then redacts. Persisted envelopes equal in-memory envelopes, and both final selectors produce PASS aggregates/manifests.

2. **[Rule 2 - Missing critical] Kept mutable gate/delivery records outside the canonical source subject.**
   - The evidence kernel deliberately rejects TODO, review, entry evidence, delivery-attestation, and delivery-tag paths as source inputs. TRACE-004 still executes lifecycle and delivery fixtures through fixed code-owned argv and preserves both results, but producer-controlled gate/delivery state cannot alter the canonical source digest.

3. **[Rule 1 - Bug] Removed runtime evidence from its own future source digest.**
   - `local-chrome-runtime.json` is a validation target, not implementation/config/contract source. Including it in `LOCAL_CHROME_INPUTS` would make a later Plan 06 evidence regeneration hash the previous artifact and then overwrite it.
   - The final registry retains the Plan 06 implementation, test, config, schema, scenario, and validator inputs while both portable and local validators still read the fixed runtime artifact.

No architecture, product behavior, route, database schema, dependency, browser scope, evidence authority, or delivery protocol changed.

## Verification Evidence

Passed final commands and results:

- `./scripts/verify-phase-01 --ci --evidence-dir <temporary>` — 20/20 PASS, aggregate PASS, evidence manifest PASS.
- `./scripts/verify-phase-01 --all --evidence-dir <temporary>` — 19/19 PASS, exact copy local Chrome PASS, local runtime PASS, aggregate PASS, evidence manifest PASS.
- `/usr/bin/env ruby scripts/lib/phase-01/test_run_checks.rb` — 11 cases PASS.
- `/usr/bin/env ruby .planning/tools/test-phase-lifecycle.rb` — 20 cases PASS.
- `/usr/bin/env ruby .planning/tools/test-delivery-attestation.rb` — 29 cases PASS.
- `/usr/bin/env ruby .planning/tools/test-repository-verification.rb` — 18 mutations PASS.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — positive and destructive suite PASS.
- `/usr/bin/env ruby .planning/tools/test-bootstrap-phase-01.rb` — 17 mutations, 13 plans, seven obligations PASS.
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner engineering-verification-foundation --assert-unique --assert-traced` — 522 records, seven selected obligations PASS.
- Real bootstrap — PASS, 13 plans, seven obligations, fixed local Chrome path.
- CI YAML parse/static contract, runner Ruby syntax, registry contract, and scoped `git diff --check` — PASS.
- Missing portable Chrome artifact fixture — BLOCKED/status 75 as required.
- Docker owner-label residuals after execution — zero containers, zero networks, zero volumes.

The final `--ci` and `--all` evidence directories, Maven/npm/build/test outputs, and TypeScript build metadata were moved by exact path to the system Trash after verification. They are recoverable and are not delivery evidence. The existing Plan 06 `EVIDENCE/local-chrome-runtime.json` was consumed but not rewritten.

## Validation Map

`01-VALIDATION.md` now contains exactly seven obligation rows. Chinese copy/export and timezone are supporting TRACE-003 lanes, not product-level obligations. `wave_0_complete` and `nyquist_compliant` were set true only after both selectors, destructive fixtures, real services, local Chrome, YAML, repository, planning, catalog, bootstrap, residual, generated-output, and diff checks passed.

Those validation flags describe executable Phase 1 verification infrastructure only. They do not close obligation evidence, TODO, requirement, GSD/Claude review, remote CI, delivery, tag, or PR gates.

## Security and Privacy Check

- Commands are literal fixed argv arrays; no command or subject exclusion is loaded from a manifest.
- CI browser execution is structurally forbidden by the registry contract.
- Service images are exact official digests, use disposable isolated resources, and leave no owner-labeled residuals.
- Child stdout/stderr are bounded, UTF-8 normalized, redacted, and persisted before reduction.
- Browser checks use synthetic credentials/data and fixed loopback HTTP only.
- No secret, production data, browser profile, credential, private repository content, or agent state was added.

## Known Stubs

None.

## Remaining Boundaries

- The GitHub-hosted workflow has not yet run on a pull request; only YAML parsing, static CI assertions, and the complete local portable selector are proven here.
- Plan 08's recorded Flyway/MySQL support warning and existing npm audit findings remain unchanged; neither was introduced or represented as resolved by this plan.
- Plan 10 must generate and seal the seven formal obligation evidence targets. Plan 11 must complete GSD/Claude review, and Plan 12 must perform delivery attestation before the Phase 1 TODO can become empty.

## Self-Check: PASSED

- All three plan-owned files and this summary exist.
- Both final selectors and every focused verification listed above passed against the final registry.
- Registry obligation count is exactly seven; CI browser-layer count is zero; exact local copy argv count is one.
- Docker residual count is zero and plan-generated outputs were moved to Trash by explicit path.
- No file outside the three-file plan boundary plus this summary was modified by this executor.
- No formal obligation evidence, TODO, STATE, ROADMAP, requirement checkbox, Git staging area, commit, branch, tag, push, or remote state was changed.
