# engineering-verification-foundation: Engineering verification and drift control

## Intent

Provide one fail-closed, machine-readable verification foundation that later phases can invoke without inventing commands, evidence formats, or trace rules.

## Scope

### In

- Deterministic backend, frontend, database, cache, browser, copy/export-contract, viewport, and timezone-verifier commands.
- Exact PRD obligation ownership, behavior, test, evidence, TODO, and phase-artifact validation.
- Bidirectional reconciliation across route registry, UI manifest, rendered `data-testid` values, repeated-row selector metadata, and Playwright locators.
- Structured diagnostic evidence that preserves the command, environment identity, result, and failure details.
- A sole pre-entry remediation plan that proves the standard-path local Google Chrome version/brand and isolated-profile headless synthetic-page launch in `local-chrome-entry.json`, followed by an independent four-column review and a real bootstrap over exactly 13 plans.
- Pre-entry migration of the six active evidence, lifecycle, root-runner, and delivery consumers from the superseded browser-source chain to `local-chrome-entry.json`, while preserving generic tested-subject, evidence-digest, remote-delivery, and fail-closed rules.

### Out

- Business feature implementation, owned by Phases 3-50.
- Visual language and complete portal information architecture, owned by Phase 2.
- Final security, performance, reliability, observability, extension, and release assurance, owned by Phases 51-56.
- Product-level first-release simplified-Chinese coverage and international-message timezone-identity persistence acceptance, owned by Phase 56.
- Edge, Safari, Firefox, Internet Explorer, and every browser other than desktop Google Chrome; Phase 1 adds no unsupported-browser blocking UI.

## External behavior

### engineering-verification-foundation-01

Where the nine-field PRD obligation catalog is the declared completion source, when the trace validator runs, it shall reject a missing owner, behavior, automated test, evidence target, requirement link, or unknown reference and emit the exact affected IDs.

### engineering-verification-foundation-02

Where requirements, obligations, behaviors, tests, evidence targets, and owners form a bidirectional graph, when the graph is queried, it shall report orphan and duplicate ownership separately and return nonzero for either condition.

### engineering-verification-foundation-03

Where a supported verification layer is selected, when its repository command runs, it shall produce deterministic pass/fail output and structured evidence for backend, frontend, MySQL, Redis, browser compatibility, a reusable simplified-Chinese copy/export contract, supported desktop viewports, and a reusable UTC+8/IANA-timezone verifier without overstating an unexecuted environment or future product coverage.

### engineering-verification-foundation-04

Where a phase package is entering or closing execution, when its gate query runs, it shall reject missing artifacts, invalid dependency evidence, incomplete atomic traces, nonempty scoped TODOs at exit, blocking GSD/Claude reviews, or a missing remote commit identity.

While Phase 1 entry is BLOCKED, only `01-00` may execute. Its entry probe has no Playwright dependency and is separate from the Plan 06 runtime/smoke artifact. No downstream plan is authorized until an independent reviewer records current ENTRY PASS and the real bootstrap validates all 13 plans with zero exit.

The first independent review is retained as `6 PASS / 2 BLOCKER`. ENTRY-04 and ENTRY-07 require Plan 00 to migrate exactly `.planning/tools/verification-evidence.rb`, `.planning/tools/test-repository-verification.rb`, `.planning/tools/test-phase-lifecycle.rb`, `scripts/lib/phase-01/test_run_checks.rb`, `.planning/tools/validate-delivery-attestation.rb`, and `.planning/tools/test-delivery-attestation.rb` before re-review. Superseded browser-source JSON remains historical on disk but cannot enter current subject, evidence, lifecycle, runner, or delivery validation.

### engineering-verification-foundation-05

Where a UI package declares routes, manifest pages, DOM selectors, repeated-row contracts, and Playwright locators, when reconciliation runs, it shall reject missing and stale references in both directions and reject selectors that encode mutable or sensitive row data.

## Internal behavior

### engineering-verification-foundation-06

Where a verification command executes multiple checks, when any check fails or cannot run, the orchestrator shall preserve every completed check result, label the unavailable check explicitly, and return nonzero without converting absence into PASS.

### engineering-verification-foundation-07

Where entry browser evidence is recorded, when browser identity is evaluated, the entry verifier shall accept only the current desktop Google Chrome at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`, record its execution-time version, and prove Chrome's own headless fixed-argv launch of a synthetic local page using an isolated profile. Plan 06 separately proves a Playwright launch and one `/login` smoke-and-visual run at exactly 1440x900 in `local-chrome-runtime.json`. A missing path, a version string not identifying Google Chrome, either launch failure, or any other browser is `BLOCKED`/nonzero. No browser download, ChromeDriver, fixed version, or multi-version matrix is part of either contract.

## Errors and boundaries

| Case | Required outcome | Behavior ID |
| --- | --- | --- |
| Catalog row has fewer or more than nine fields | Fail with catalog row and field count | engineering-verification-foundation-01 |
| Requirement or obligation has no reverse link | Fail and identify the orphan direction | engineering-verification-foundation-02 |
| MySQL, Redis, or browser environment is unavailable | Record `BLOCKED`/`UNAVAILABLE`, retain diagnostics, and return nonzero | engineering-verification-foundation-03 |
| Phase exit still contains an unchecked TODO | Fail with the exact file and line | engineering-verification-foundation-04 |
| A plan other than `01-00` attempts to run before independent ENTRY PASS and revised 13-plan bootstrap PASS | Fail authorization and retain the blocking criterion | engineering-verification-foundation-04 |
| The Plan 00 executor approves its own entry result or the current review omits one of the exact four columns | Fail entry review validation | engineering-verification-foundation-04 |
| An active evidence/lifecycle/runner/delivery consumer requires a browser-source admission, dual probe, attestation, legacy digest, or removed contract API | Fail ENTRY-04 and keep the real bootstrap BLOCKED | engineering-verification-foundation-04 |
| Manifest contains a route absent from React or vice versa | Fail with missing/stale route sets | engineering-verification-foundation-05 |
| Repeated-row selector embeds a phone number, database ID, or mutable label | Fail with selector and source location | engineering-verification-foundation-05 |
| Bundled Chromium or another browser is labelled as supported Google Chrome | Fail identity validation | engineering-verification-foundation-07 |

## Verification

### engineering-verification-foundation-V01

Where positive and destructive catalog fixtures exist, when the obligation validator runs, the suite shall prove complete trace acceptance and exact orphan/duplicate rejection [[engineering-verification-foundation-01](#engineering-verification-foundation-01)] [[engineering-verification-foundation-02](#engineering-verification-foundation-02)].

### engineering-verification-foundation-V02

Where repository verification fixtures include successful and unavailable dependencies, when the verification orchestrator runs, the suite shall assert deterministic status, evidence schema, and nonzero failure behavior [[engineering-verification-foundation-03](#engineering-verification-foundation-03)] [[engineering-verification-foundation-06](#engineering-verification-foundation-06)].

### engineering-verification-foundation-V03

Where valid and corrupted phase packages exist, when entry and exit queries run, the suite shall reject every missing artifact, trace, review, TODO, and remote identity contract, including missing Plan 00, a plan count other than 13, executor self-approval, malformed four-column review rows, active browser-source consumer membership, removed legacy contract API calls, or a downstream plan running before entry authorization [[engineering-verification-foundation-04](#engineering-verification-foundation-04)].

### engineering-verification-foundation-V04

Where aligned and drifted UI fixtures exist, when UI reconciliation runs, the suite shall reject route, page, DOM, repeated-row, and Playwright drift in both directions [[engineering-verification-foundation-05](#engineering-verification-foundation-05)].

### engineering-verification-foundation-V05

Where a local Chrome runtime probe and executed evidence exist, when browser evidence is verified, the suite shall accept exactly one Google Chrome full-version/major/path/1440x900 execution with a successful Playwright launch and complete smoke/visual artifacts [[engineering-verification-foundation-03](#engineering-verification-foundation-03)] [[engineering-verification-foundation-07](#engineering-verification-foundation-07)].

## Requirement and obligation trace

| Obligation ID | Requirement | Behavior IDs | Verification IDs |
| --- | --- | --- | --- |
| OBL-FOUND-TRACE-001 | PROJECT-PLANNING-TRACE | engineering-verification-foundation-01 | engineering-verification-foundation-V01 |
| OBL-FOUND-TRACE-002 | PROJECT-PLANNING-TRACE | engineering-verification-foundation-02 | engineering-verification-foundation-V01 |
| OBL-FOUND-TRACE-003 | PROJECT-PLANNING-TRACE | engineering-verification-foundation-03, engineering-verification-foundation-06 | engineering-verification-foundation-V02 |
| OBL-FOUND-TRACE-004 | PROJECT-PLANNING-TRACE | engineering-verification-foundation-04 | engineering-verification-foundation-V03 |
| OBL-FOUND-UI-DRIFT-001 | PROJECT-UI-CONTRACT | engineering-verification-foundation-05 | engineering-verification-foundation-V04 |
| OBL-FOUND-UI-DRIFT-002 | PROJECT-UI-CONTRACT | engineering-verification-foundation-05 | engineering-verification-foundation-V04 |
| OBL-NFR-BROWSER | REQ-NFR-COMPATIBILITY | engineering-verification-foundation-03, engineering-verification-foundation-07 | engineering-verification-foundation-V05 |
