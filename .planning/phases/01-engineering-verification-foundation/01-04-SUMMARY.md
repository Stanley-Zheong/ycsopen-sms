---
phase: 01-engineering-verification-foundation
plan: 04
subsystem: verification
tags: [typescript-compiler-api, react-router, jsx, playwright, test-id, row-key, fail-closed]
supersession_notice: "Historical execution record only: all Chrome 151/152, downloaded-source, attestation, 12-plan bootstrap, and current/previous-browser claims are superseded by DR-01-016/017; later plans must regression-check this output after Plan 00 independent ENTRY PASS."

requires:
  - phase: 01-engineering-verification-foundation
    plan: 01
    provides: canonical Phase 1 evidence and tested-subject protocol
  - phase: phase-01-entry
    provides: independently attested Chrome 151/152 Gate D chain and blocking-free entry bootstrap
provides:
  - versioned page-scoped route/component/selector/Playwright relation manifest
  - TypeScript-AST bidirectional UI drift validator with source-located diagnostics
  - semantic repeated-row contract bound to reviewed non-sensitive key expressions
  - isolated destructive relation suite covering missing, stale, dead, duplicate, computed, and sensitive cases
affects: [phase-01-plans-05-through-12, production-ui-phases, playwright-generation, ui-contract-gates]

tech-stack:
  added: []
  patterns: [TypeScript Compiler API traversal, exact relation tuples, JSON Schema 2020-12 contracts, one-mutation temporary fixtures]

key-files:
  created:
    - web/scripts/validate-ui-drift.mjs
    - web/scripts/test-ui-drift-validator.mjs
    - web/verification/ui-manifest.schema.json
    - web/verification/ui-manifest.json
    - web/verification/row-key-registry.schema.json
    - web/verification/row-key-registry.json
    - web/verification/fixtures/ui-drift-cases.json
  modified: []

key-decisions:
  - "The production manifest scopes only the observed /login route/component fact and declares zero selectors, so current placeholder/business routes are not presented as verified UI."
  - "A verified selector relation is one exact route, routed component, DOM selector, trace metadata, Playwright title metadata, navigation, and awaited action/assertion tuple."
  - "A repeated row must be AST-proven inside a map callback and bind a constant semantic test ID to a separate reviewed property-access data-row-key expression."

patterns-established:
  - "Unsupported route, JSX, row-key, and Playwright construction returns UI_UNSUPPORTED_SYNTAX with an AST source location."
  - "Missing and stale page, route, component, selector, redirect, and Playwright relations are reported independently in both directions."
  - "Manifest-declared sources are contained by canonical realpath, so absolute escapes and in-repository symlinks to outside files fail closed."

requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-only; no schedule, duration, percentage, or completion-date metric
---

# Phase 01 Plan 04: TypeScript-AST UI relation drift Summary

**A versioned AST relation graph now rejects route, routed-component, DOM test-ID, repeated-row identity, and Playwright action/assertion drift without treating comments, strings, dead source, or computed syntax as evidence.**

## Scope result

- **Tasks:** 2/2 plan tasks implemented and verified.
- **Files created:** seven validator, schema, manifest, registry, and fixture files; this SUMMARY is the eighth plan artifact.
- **Phase status:** Not asserted. `OBL-FOUND-UI-DRIFT-001`, `OBL-FOUND-UI-DRIFT-002`, and their TODO rows remain open until Plan 05 executes the real route-to-rendered-selector browser closure and Plan 10 seals evidence.
- **Requirement status:** `REQ-NFR-COMPATIBILITY` is addressed by this plan and is not marked complete.

## Accomplishments

- Implemented TypeScript Compiler API extraction for nested `createBrowserRouter` objects, relative/absolute paths, index redirects, redirect targets, parameter patterns, routed imports/components, JSX attributes, and Playwright test blocks.
- Implemented exact bidirectional comparisons for page, route, redirect, component, DOM selector, and Playwright metadata/navigation/action relations with separate missing, stale, duplicate, dead, wrong-route, and unsupported-syntax diagnostics.
- Added strict JSON Schema contracts for the UI manifest and row-key registry, plus validator-side schema enforcement without adding a package.
- Bound repeated rows to a constant semantic `data-testid`, AST-proven `.map(...)` repetition, exact simple-property `data-row-key` expression, and an explicitly reviewed non-sensitive immutable key class/property.
- Kept the real manifest truthful: it records only the existing `/login` to `LoginPage` route fact with no selector or Playwright completion claim. It does not register placeholder routes, Phase 2 inventory, or unimplemented business pages.
- Added 41 isolated cases, including positive nested/index/redirect/parameter behavior and one-mutation failures for both drift directions, comments/strings, prefix collisions, dead components/locators, computed syntax, malformed TypeScript, spreads, repeated-row policy, forbidden key classes, path escape, and symlink escape.

## Task commits

1. **Task 1: Define and extract normalized UI relation tuples** — deferred to the single Phase 1 delivery commit.
2. **Task 2: Prove every bidirectional drift and semantic row-key failure** — deferred to the single Phase 1 delivery commit.

No Git staging or commit was performed. The project-specific single atomic Phase 1 delivery rule overrides GSD's default per-task commit convention.

## Files created

- `web/scripts/validate-ui-drift.mjs` — strict CLI, JSON Schema subset enforcement, TypeScript AST extractors, normalized relation comparator, row policy, source containment, and stable diagnostics.
- `web/scripts/test-ui-drift-validator.mjs` — table-driven isolated temporary-graph runner using fixed argv.
- `web/verification/ui-manifest.schema.json` — versioned page, redirect, route/component, selector, trace, Playwright, and row-contract schema.
- `web/verification/ui-manifest.json` — current observed `/login` production route fact only.
- `web/verification/row-key-registry.schema.json` — reviewed allowed-class and mandatory denied-class schema.
- `web/verification/row-key-registry.json` — one synthetic immutable business-key class plus explicit phone, database-ID, localized-label, mutable-value, credential, and message-content denials.
- `web/verification/fixtures/ui-drift-cases.json` — known-good synthetic graph and 40 isolated destructive mutations.

## Decisions made

- `observed-route-fact` may truthfully record an existing route/component with zero selectors; only `verified-selector-closure` requires selectors and exact Playwright closure.
- Reverse-source reconciliation is bounded by explicit route prefixes. This prevents Phase 1 from claiming all current placeholders while still making every relation inside the declared scope exact and bidirectional.
- Redirects are first-class manifest relations. Index redirects resolve relative to their parent route, normal redirects resolve relative to the redirecting route's parent, and parameter patterns remain literal patterns.
- Row-key review is not a free-text assertion: the manifest's exact expression must match JSX, its property must be allowed by the selected reviewed class, and the selector must occur inside a `.map(...)` callback.
- TypeScript resolved to the repository lockfile's installed `5.9.3` under the declared `^5.6.2` range. No package or lockfile change was made.

## Deviations from plan

### Auto-fixed issues

**1. [Rule 2 - Missing critical] Bound declared row-key class to the real JSX expression**

- **Found during:** Task 2 fail-closed self-review.
- **Issue:** A manifest could label a sensitive source expression such as a phone property with an otherwise allowed key class.
- **Fix:** Added exact `keyExpression`, reviewed allowed-property sets, AST expression extraction, mismatch rejection, and sensitive-property destructive cases.
- **Files modified:** both schemas, row registry, validator, fixture catalog, fixture runner.
- **Verification:** expression mismatch, sensitive property, computed call, missing attribute, and four forbidden-class cases pass as expected failures.
- **Commit:** deferred to the single Phase 1 delivery commit.

**2. [Rule 2 - Missing critical] Proved that a declared repeated row is structurally repeated**

- **Found during:** Task 2 fail-closed self-review.
- **Issue:** A static one-off element could otherwise declare `repeated: true` and carry row metadata without belonging to a repeated source structure.
- **Fix:** Required AST ancestry through a `.map(...)` callback and added a static-row destructive case.
- **Files modified:** validator, fixture catalog, fixture runner.
- **Verification:** the real repeated fixture passes and the static-row mutation returns `UI_ROW_NOT_REPEATED_SOURCE`.
- **Commit:** deferred to the single Phase 1 delivery commit.

**3. [Rule 2 - Missing critical] Closed parser, spread, and canonical-path trust gaps**

- **Found during:** final threat-boundary review.
- **Issue:** Partial TypeScript ASTs, object/JSX spreads, or an in-root symlink to an outside file could conceal relations or admit untrusted source.
- **Fix:** Added parse-diagnostic rejection, route/JSX spread rejection, absolute containment, and canonical-realpath containment.
- **Files modified:** validator, fixture catalog, fixture runner.
- **Verification:** malformed source, route spread, JSX spread, absolute escape, and symlink escape mutations fail with stable diagnostics.
- **Commit:** deferred to the single Phase 1 delivery commit.

**Total deviations:** three missing-critical safeguards. They tighten the declared fail-closed and privacy contract without adding business UI, product behavior, schema migrations, packages, or browser scope.

## Verification evidence

Passed commands:

- `node web/scripts/validate-ui-drift.mjs --manifest web/verification/ui-manifest.json --routes web/src/router/routes.tsx --check-static` — real repository `PASS`, one observed page and zero claimed selectors.
- `node web/scripts/test-ui-drift-validator.mjs` — `PASS`, 41 cases: one known-good graph and 40 isolated destructive mutations.
- `node --check` for both Node scripts and JSON parsing for all five schema/manifest/registry/fixture documents — `PASS`.
- `npm --prefix web ci` — deterministic install completed from the existing lockfile.
- `npm --prefix web test` — one Vitest file and four tests pass.
- `npm --prefix web run build` — TypeScript and Vite production build pass.
- `mvn -f core/pom.xml test` — 24 backend tests pass.
- `/usr/bin/env ruby .planning/tools/test-planning-validators.rb` — existing planning/UI/schema regression suite passes.
- `/usr/bin/env ruby .planning/tools/bootstrap-phase-01.rb --phase-dir .planning/phases/01-engineering-verification-foundation --catalog .planning/PRD-OBLIGATIONS.md` — real bootstrap passes for seven obligations and 12 plans.
- `git diff --check`, owned-file trailing-whitespace scan, and package/production-source scope scan — `PASS`.

`npm ci` reported seven dependency audit findings already present in the unchanged lockfile: five moderate, one high, and one critical. Package remediation is outside Plan 01-04 ownership and no dependency file was changed; Phase review must not mistake this plan for resolving those findings.

## Known deferred registrations

- Plan 05 still owns executed route-to-rendered-selector closure. Static AST PASS cannot close either UI-drift obligation.
- Plan 09 still owns root verification registry integration, and Plan 10 owns obligation summaries and sealed evidence.
- The current production manifest intentionally has no `/login` selectors; Plan 05 must reconcile its production test IDs and Playwright relations after implementing its owned source changes.
- Complete Admin/Tenant UI inventory and visual design remain Phase 2 work. No placeholder route or fixture is production completion evidence.

## Security and privacy check

- No package was added and no production page, route, component, test, API, schema, Gate D artifact, or browser configuration was modified.
- Only fixed argv invokes the validator from isolated temporary directories; test cleanup removes only its own generated directory.
- Actual `data-testid` values must be static semantic literals. Dynamic selectors, JSX spreads, comments, strings, and dead source cannot satisfy a relation.
- Row-key classes explicitly deny phone numbers, database IDs, localized labels, mutable values, credentials, and message content; fixture values are synthetic.
- Manifest source resolution rejects lexical and canonical-realpath escapes, including symlinks to outside files.
- Browser support remains desktop Google Chrome current/previous stable only; this plan introduces no browser implementation or unsupported-browser UI.

## Known stubs

None. The observed `/login` fact and empty selector set are explicit current implementation truth, not a placeholder or completion claim.

## Self-check: PASSED

- All seven plan-owned implementation/test/schema/manifest/registry/fixture files and this SUMMARY exist.
- Fixture cardinality is exactly one known-good graph plus 40 isolated destructive mutations.
- Both plan verification commands pass after the final source-containment and repeated-row safeguards.
- Node/JSON syntax, frontend install/test/build, backend tests, planning regression, real bootstrap, whitespace, and scope checks pass.
- No file outside Plan 01-04 ownership plus this SUMMARY was created or modified by this executor.
- No Gate D evidence, production source, package file, TODO, STATE, ROADMAP, requirement checkbox, Git staging area, branch, commit, stash, remote, or push state was changed by this executor.

## Next plan readiness

The static UI relation and repeated-row trust contract is ready for Plan 01-05 to add the real route/rendered-selector/Playwright closure. Phase 1 remains incomplete while its scoped TODO is nonempty.
