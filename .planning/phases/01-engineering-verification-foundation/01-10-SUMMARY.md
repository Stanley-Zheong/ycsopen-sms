---
phase: 01-engineering-verification-foundation
plan: 10
subsystem: formal-obligation-evidence
tags: [ruby, evidence, exact-seven, local-google-chrome, checksums, fail-closed]

requires:
  - phase: 01-09
    provides: literal 23-check registry, complete local selector, and compatible per-check evidence manifest v1
  - phase: 01-06
    provides: independently produced standard-path local Chrome runtime artifact
provides:
  - closed-field durable obligation summary and exact-seven manifest contracts
  - independently validated 23-check registry with a 19-local-check-to-seven-obligation evidence reduction
  - canonical 194-input tested subject and seven PASS obligation summaries
affects: [01-11, 01-12, phase-01-review, phase-01-delivery]

tech-stack:
  added: []
  patterns: [schema-version dispatch, embedded durable check facts, exact owner-set reduction, source-subject/runtime separation]

key-files:
  created:
    - .planning/tools/produce-phase-01-obligation-evidence.rb
    - .planning/tools/test-phase-01-obligation-evidence.rb
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/obligation-summary.schema.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/schema/obligation-evidence-manifest.schema.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/evidence-manifest.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-TRACE-001.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-TRACE-002.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-TRACE-003.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-TRACE-004.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-UI-DRIFT-001.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-FOUND-UI-DRIFT-002.json
    - .planning/phases/01-engineering-verification-foundation/EVIDENCE/OBL-NFR-BROWSER.json
  modified:
    - scripts/lib/phase-01/run_checks.rb
    - .planning/tools/verification-evidence.rb
    - .planning/tools/validate-verification-evidence.rb
    - .planning/phases/01-engineering-verification-foundation/TEST-MATRIX.md

key-decisions:
  - "The existing per-check phase01-evidence-manifest-v1 remains unchanged; phase01-obligation-evidence-manifest-v1 is a separate exact-seven delivery contract selected by schema version."
  - "Formal summaries embed bounded, redacted, checksummed child facts and never depend on transient EVIDENCE/runs paths."
  - "The final local runtime is regenerated only after the durable source subject is sealed, records that exact subject path and both digests, and is separately checksummed by the exact-seven manifest."

requirements-completed: []
requirements-addressed: [REQ-NFR-COMPATIBILITY]
completion-policy: todo-empty-only
---

# Phase 01 Plan 10: Exact-seven formal obligation evidence Summary

**After Claude found that a validator-labelled CI command still launched Chrome, the corrected local and portable Phase 1 matrices now reduce into exactly seven durable obligation summaries bound to one canonical 194-file source subject and a freshly generated matching local-Chrome runtime.**

## Scope Result

- Tasks executed: 2/2.
- Formal owner set: exactly seven engineering-verification-foundation obligations, all PASS.
- Canonical subject: 194 stable path/mode/SHA-256/role entries: 103 implementation, 31 test, 13 config, 35 contract, and 12 validator files.
- Existing Plan 09 per-check manifest v1 remains valid; the new exact-seven manifest uses a distinct schema and validator dispatch.
- No TODO, obligation checkbox, requirement status, STATE, ROADMAP, delivery metadata, review result, application route, database schema, or product-acceptance claim was changed.
- No Git staging, commit, branch, tag, push, checkout, reset, stash, or remote mutation was performed.

## Evidence-Kernel Gap and Resolution

The first execution attempt stopped before generating evidence because the original validator required all 22 runner check IDs while Plan 10 required exactly seven obligation summaries. Producing either shape would have violated a hard contract.

The revised plan explicitly added evidence-kernel ownership. Its implementation now:

- preserves `phase01-evidence-manifest-v1` and its Plan 09 runner behavior;
- adds closed-field `phase01-obligation-summary-v1` and `phase01-obligation-evidence-manifest-v1` contracts;
- accepts only an explicitly named local `--all` check manifest whose ordered check set and every envelope first pass the existing independent validator;
- reduces fixed check contracts to exactly seven code-owned obligations;
- embeds bounded/redacted check facts, artifact checksum facts, and recomputable per-result digests so formal evidence has no dependency on transient run directories;
- rejects missing, extra, duplicate, foreign, FAIL/BLOCKED, argv/case/subject/status/exit, catalog-relation, product-scope, runtime, CI-locator, and checksum mutations;
- dispatches the public validator by schema version without weakening the legacy v1 path.

## Canonical Subject

The first Plan 11 GSD attempt invalidated the original 105-input seal. Later review cycles produced and hardened a 193-input seal. Claude Attempt 1 then proved that `login-scenario-contract` was labelled as a validator but unconditionally launched the standard-path macOS Chrome, invalidating the portable claim and all review bindings. The current correction physically separates a pure structural CI file from a dedicated local visual file, binds exact argv/scopes/layers for both scenario commands and the structural server, and content-addresses the complete five-file portable scenario call graph. Any byte drift in the validator, its validation dependency, the server, the bounded probe helper, or the server helper blocks the registry until an explicit reviewed rebind. The structural validator/server and portable artifact all pass under an OS sandbox that denies Chrome reads and execution. `File::NOFOLLOW` absence now fails closed. The other execution/evidence hardening remains intact: TRACE-004 inputs are complete; lifecycle and delivery dispatch the exact-seven schema; portable runtime facts independently bind durable subject content, manifest/subject digests, exact-seven checksums, the verified runtime snapshot SHA/size/path, and fixed CI locators; path components use a stable `O_NOFOLLOW` descriptor snapshot; PNG, OCI, Java descendant cleanup, browser cleanup, redaction, static containment, and explicit CLI roots fail closed.

- Subject manifest: `.planning/phases/01-engineering-verification-foundation/EVIDENCE/tested-inputs.json`
- `subject_manifest_digest`: `94d9fa6dabd318e7e651a622182e357ec11c539ab25505d59eae9da475b5dd53`
- `tested_subject_digest`: `c3118bfde865d59b918c3032ae93570fc946d32eda043aed21801405963098e4`
- Evidence manifest SHA-256: `7174fecb6ac826a1f91b3a0b3f9fb0d9ae8280674c52f4fbdd1cfe5d8c606579`

The code-owned subject union excludes generated summaries/manifests, Plan 06 runtime evidence, TODO/SUMMARY/review/delivery state, and all superseded browser-source artifacts. It includes the new producer, destructive suite, both schemas, runner, evidence kernel, CLI, matrix, and every registered implementation/test/config/contract/validator input.

## Exact-Seven Evidence

| Obligation | Summary SHA-256 | Result |
| --- | --- | --- |
| OBL-FOUND-TRACE-001 | `c7f2843aa382354b5e31e2686642380905d1f13515ae6dc7c66cc87d7ee68231` | PASS |
| OBL-FOUND-TRACE-002 | `4f7b44e049a9a0bbbaff5a33053b3a449e5f0a29da91fbea43cac68457e639b1` | PASS |
| OBL-FOUND-TRACE-003 | `1e3b569a2a266451d056537278cadae3fc3503dc7b4f8a7f36ce470dd6f28b5d` | PASS |
| OBL-FOUND-TRACE-004 | `61b7fa432f8812f61db83042793227ff1365d11d6d3b974458a9e36ca5744e26` | PASS |
| OBL-FOUND-UI-DRIFT-001 | `cd1cf54ce9c99463a0b7e9ad1a64acc508741a41e424dca73a23c6dd401da053` | PASS |
| OBL-FOUND-UI-DRIFT-002 | `f1345158bd5a9ad43f3eb64247c94004029e0d91554c90c1bd13d7207e03579c` | PASS |
| OBL-NFR-BROWSER | `4a68849a2ef397105476df4e61f815669497d984aadce1d9f493cf4ccd767087` | PASS |

TRACE-001 and TRACE-002 each include the destructive trace-closure result and a real owner query proving `selected=7`. TRACE-004 retains the ordered lifecycle and delivery child outcome. TRACE-003 includes Java unit/integration, npm install/lint/test/build, structural HTTP scenario, exact browser-bearing Chinese-copy command, real MySQL/Redis, timezone, and local-Chrome contract results. Chinese copy/export and timezone remain supporting contract results only; no Phase 56 product acceptance was asserted.

## Current Local Chrome Facts

- Brand/version: Google Chrome `151.0.7922.174` (major 151, observed rather than pinned).
- Executable: `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`.
- Viewport: `1440x900`.
- Scenario: `LOGIN-SMOKE-V1`; visual rule: `LOGIN-CARD-IN-VIEWPORT-V2`.
- Browser-observed response: HTTP 401 with the fixed safe body digest and `X-YCS-Scenario: LOGIN-SMOKE-V1` marker.
- Runtime artifact SHA-256: `05535c103fda9308f5dde454a8dbbf311f25292311c6dba399afbb9541d1bfde`.
- Fresh structural browser command: 9/9 Playwright tests PASS against the standard-path local Chrome, with real loopback HTTP.
- No Chrome, ChromeDriver, Chromium bundle, alternate browser, or version matrix was downloaded.

## Verification Evidence

Passed commands and checks include:

- `/usr/bin/env ruby .planning/tools/test-phase-01-obligation-evidence.rb` — 21 destructive mutations, seven summaries, runtime/subject binding, and legacy v1 compatibility PASS.
- `/usr/bin/env ruby .planning/tools/test-repository-verification.rb` — 19 evidence-kernel mutations plus 12 path/descriptor/bounded-read cases PASS, including explicit `NOFOLLOW`-unavailable rejection.
- `/usr/bin/env ruby scripts/lib/phase-01/test_run_checks.rb` — 40 runner cases PASS, including complete compiled-input membership, independent portable/formal runtime-fact equality, bounded JSON/stdin handling, exact CI/local scenario bindings, and browser-source bypass mutations.
- TRACE-001/002 exact trace-closure commands plus the real owner query — PASS, seven selected obligations.
- `./scripts/verify-phase-01 --all --evidence-dir .../EVIDENCE` — 19/19 local checks and aggregate PASS after the final schema seal.
- `./scripts/verify-phase-01 --ci --evidence-dir .../EVIDENCE` — 20/20 portable checks and aggregate PASS; `login-scenario-contract` reports `cases=3 local_google_chrome=not-run viewport=not-run`, and the portable artifact lane reports `live_browser_launched=false`.
- Chrome-denial control — `sandbox-exec` denied read/execute access to `/Applications/Google Chrome.app`; the structural scenario command and portable artifact command still passed. The sandbox was not used as the aggregate result because macOS also denied Java's `/bin/ps` descendant scan; the normal portable aggregate is the authoritative 20/20 run.
- Exact TRACE-004 command chain — producer/bootstrap/repository/lifecycle/delivery/runner/planning/catalog/bootstrap PASS.
- `node web/scripts/test-ui-drift-validator.mjs` — 41 UI drift cases PASS.
- `npm --prefix web run test:browser:compat` — 9/9 standard-path local Chrome Playwright tests PASS.
- `validate-verification-evidence.rb --subject .../tested-inputs.json` — PASS.
- `validate-verification-evidence.rb --manifest .../evidence-manifest.json --require-owner engineering-verification-foundation` — PASS before and after transient-run cleanup.
- Local-Chrome contract fixture suite — 81 evidence cases plus five pre-decode boundary cases PASS, including complete critical-chunk state, palette rules, bounded header-gated inflate, CRC/zlib/scanline/filter/trailing-data failures, and legal ancillary/multiple-IDAT compatibility.
- Java process harness — 5/5 focused process cases PASS, including detached scoped descendant cleanup; process plus MySQL/Redis integration set 9/9 PASS.
- Service runtime contract — digest-anchored OCI index/child/config and Docker image/container identity PASS.
- Ruby syntax, JSON parsing, closed-field schema assertions, scoped whitespace, catalog, planning, bootstrap, Docker residual, process, and generated-output checks — PASS.

The installed lockfile supplies Ajv 6.15, which does not compile the repository's draft-2020-12 schemas. No dependency was upgraded or substituted. The standard-library Ruby validator is the executable closed-field authority, while JSON parsing and explicit schema closure assertions passed.

## Deviations from Plan

### Auto-fixed Issues

1. **[Rule 2 - Missing critical] Added the missing exact-seven obligation evidence layer.**
   - The initial execution correctly stopped because the existing 22-check manifest could not satisfy an exact-seven summary contract.
   - After the plan expanded ownership, a separate schema/reducer/validator path was added without changing legacy v1 semantics.

2. **[Rule 3 - Blocking environment] Restored lockfile dependencies before runtime validation.**
   - Prior cleanup had removed `web/node_modules`, so the existing local-Chrome validator could not import the locked `@playwright/test` package.
   - `npm --prefix web ci` restored only lockfile-declared dependencies. No package declaration or browser binary changed.

3. **[Rule 2 - Missing critical] Closed the runtime object in the JSON Schema itself.**
   - Ruby already rejected runtime extras by exact equality, but the first schema draft left the nested object open.
   - The runtime field now has a complete required-property set with `additionalProperties: false`; the source subject and full matrix were resealed afterward.

4. **[Rule 1/2 - Review correction] Removed portable self-binding and local-path substitution gaps.**
   - Portable CI now recomputes the complete durable source binding and exact-seven/runtime/CI locator relations without launching Chrome.
   - Verification and delivery reads share one component-by-component, hardlink/symlink-rejecting descriptor snapshot primitive with TOCTOU rechecks.

5. **[Rule 1/2 - Review correction] Tightened media, service provenance, and child-process cleanup.**
   - PNG validation now verifies CRC, complete bounded zlib consumption, legal image layout, and terminal structure.
   - OCI validation binds index bytes to the selected platform child/config and Docker runtime identities.
   - Java process execution scopes and cleans reparented descendants on success and all failure paths before a result can pass.

6. **[Rule 1 - Review correction] Enforced PNG chunk-type semantics.**
   - A correct-CRC unknown critical chunk found by Attempt 3 now fails closed.
   - Chunk names require four ASCII letters, the reserved bit must be zero, critical chunks are allowlisted, and legal unknown ancillary chunks remain accepted.

7. **[Rule 1/2 - Review and preflight correction] Completed PNG critical-state and resource-bound validation.**
   - IHDR/PLTE/IDAT/IEND now enforce complete singleton, order, color-type, palette-capacity, and terminal rules with positive compatibility cases.
   - IDAT concatenation and zlib inflate occur only after a CRC-valid, fixed `1440x900`, format-valid IHDR passes; invalid headers cannot select decompression output size or trigger pixel inflate.

8. **[Rule 1/2 - Review correction] Moved JSON and screenshot bounds before allocation-heavy work.**
   - Ruby validates descriptor size and performs bounded reads before JSON parse, with explicit limits for every portable input and combined validator stdin.
   - Node validates runtime/contract/subject file sizes before reads and screenshot base64/byte-length bounds before decode, canonical re-encode, hashing, PNG inspection, or zlib work; the schema carries the same limits.

9. **[Rule 1/2 - Claude correction] Physically separated portable scenario validation from local Chrome execution.**
   - The CI file contains only three structural contract cases and no Playwright, observer, launch, visual-case, local-file, or Chrome-flag primitive.
   - The local file is an `all`/`browser` check that requires the sole `--run-local-chrome` argument and runs the original twelve visual cases plus the three structural cases against the current standard-path Chrome.
   - Registry and source mutations reject scope/argv/layer drift, missing local source, injected dynamic imports, extra Chrome calls, or CI references to the local source.
   - Follow-up GSD review closed two remaining future-drift bypasses: the server accepts only no args or the sole explicit local `--run-playwright` mode, while the CI registry binds the no-arg argv and forbids both local flags; the portable scenario's five-file repo-local call graph is bound by exact SHA-256.

10. **[Rule 1 - Claude correction] Made descriptor guarantees fail closed.**
    - Platforms without `File::NOFOLLOW` now return a stable `*_NOFOLLOW_UNAVAILABLE` diagnostic instead of silently opening with weaker flags.

No product behavior, route, database schema, browser scope, application dependency, or delivery protocol changed.

## Security and Privacy Check

- The producer accepts fixed paths and an explicit prior check manifest; it executes no manifest-provided command.
- Every accepted child result is independently checked against the code-owned argv/case/layer/cwd contract before reduction.
- Diagnostics are bounded, UTF-8 safe, redacted, and rejected if secret-bearing or transient-path-bearing.
- Formal summaries contain hashes and bounded facts rather than raw transient logs or browser profiles.
- Browser data is synthetic and loopback-only; no production data, credential, private source, or agent state entered formal evidence.
- Superseded browser-source artifacts were neither read nor included in the tested subject.
- Embedded OCI index/child/config documents are captured immutable trust roots: the checks prove byte/digest/platform/config/runtime consistency for those pins, not freshness against a live registry. Updating a pin requires a separately reviewable recapture; routine verification remains offline and deterministic.

## Known Stubs

None.

## Remaining Boundaries

- A GitHub-hosted pull-request workflow run remains a later delivery boundary; this plan validates its stable workflow locator and complete local portable/runtime contract but does not claim remote CI execution.
- Plan 08's recorded Flyway/MySQL support warning and existing npm audit findings remain unchanged and are not represented as resolved.
- Plan 11 must perform GSD and Claude review. Plan 12 owns TODO closure, single atomic commit, PR/check verification, annotated tag, and live delivery attestation.

## Cleanup

The final exact-source-bound local and portable run directories plus their aggregate/log artifacts, Playwright test-results, frontend dist/tsbuild metadata, and Maven target output were moved by exact path to `/Users/laosanzheong/.Trash/ycsopen-sms-phase01-ci-exact-source-JMeymG`. They are recoverable and are not formal delivery inputs. The formal subject, exact-seven manifest, portable artifact validator, and local runtime validator all passed again after cleanup. Earlier diagnostic Chrome-denial artifacts remain recoverable in the previously recorded Trash directory and are not delivery inputs. Docker residuals for `com.ycsopen.phase01.owner=engineering-verification-foundation` are zero containers, zero networks, and zero volumes. No Plan 10 browser/scenario or Java fixture process remains.

## Self-Check: PASSED

- All 17 plan-owned files and this Summary exist.
- The formal manifest contains exactly seven ordered, unique, owner-scoped PASS entries.
- Every summary checksum, result digest, catalog/matrix relation, subject digest, runtime checksum, and CI locator independently resolves.
- The final validator passes after transient run directories are absent, proving formal evidence has no hidden run-directory dependency.
- Changes remain inside the 17-file plan boundary plus this Summary; STATE, ROADMAP, TODO, REQUIREMENTS, PRD obligation checkboxes, reviews, and delivery state were not modified by this executor.
- No Git write or remote operation was performed.
