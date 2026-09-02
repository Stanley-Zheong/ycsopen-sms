# Phase 1: Engineering verification and drift-control foundation - Context

**Gathered:** 2026-08-30
**Status:** Ready for planning
**Mode:** Autonomous infrastructure phase

<domain>
## Phase boundary

Deliver the repository verification, trace, TODO, evidence, browser-compatibility, and UI-drift controls required before any business phase can make a completion claim. Do not implement business behavior or visual design.

</domain>

<decisions>
## Implementation decisions

### Locked project decisions

- D-01: The verified scoped TODO set is the only completion signal; schedules and percentage estimates are forbidden.
- D-02: All validators fail closed on missing, contradictory, stale, unexecuted, or malformed evidence.
- D-03: Phase 1 owns bidirectional route/manifest/DOM/test-ID/Playwright drift checking and the semantic repeated-row selector law.
- D-04: Browser acceptance uses only the current desktop Google Chrome already installed at `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`. Plan 00 entry evidence records execution-time brand/full/major version, canonical path, and a direct isolated-profile headless synthetic-page launch without Playwright; a distinct reviewer reruns it before the 13-plan bootstrap may pass. Plan 06 separately records the same installed browser's 1440x900 Playwright launch, scenario result, and artifacts. The current observed version is `Google Chrome 151.0.7922.174`, but neither contract pins 151. There is no download, archive, checksum-source chain, ChromeDriver, immutable version pair, or matrix.
- D-05: Phase 1 changes no business schema and contains no Flyway migration.
- D-06: Phase output is human-readable at the terminal and machine-readable under the phase `EVIDENCE/` directory.
- D-07: Phase 1 owns reusable fail-closed simplified-Chinese copy/export and UTC+8/IANA-timezone verification contracts under OBL-FOUND-TRACE-003; Phase 56 owns product acceptance for first-release Chinese surfaces and international-message timezone-identity persistence.
- D-08: Edge, Safari, Firefox, Internet Explorer, Chromium, and every browser other than the standard-path local desktop Google Chrome are unsupported and outside acceptance. The verifier downloads no browser, uses no ChromeDriver, and requires no remote provider, tunnel, browser secret, VM, or unsupported-browser blocking UI.

### The agent's discretion

- Internal Ruby, Node, shell, JSON schema, fixture layout, and CI job decomposition may follow repository conventions as long as every locked decision and atomic obligation remains executable.

</decisions>

<code_context>
## Existing code insights

### Reusable assets

- `.planning/tools/validate-prd-obligations.rb` already validates the nine-field catalog and owner/test/evidence uniqueness.
- `.planning/tools/validate-phase-entry.rb`, `validate-ui-contract.rb`, and their fixture suite already implement substantial fail-closed phase/UI contracts.
- `skills/code-review/scripts/precheck.sh` supplies branch-aware whitespace and Java diagnostic prechecks.
- `origin/feature/4-web-test-cases` contains an unmerged Playwright candidate implementation; planning may reuse compatible changes only after entry authorization and must avoid claiming its mocked flows as backend integration evidence.

### Established patterns

- Backend commands run from the repository root with `mvn -f core/pom.xml ...`.
- Frontend commands use `npm --prefix web ...` and Node 20 in CI.
- Planning validators are standard-library Ruby and return explicit nonzero status on contract failure.

### Integration points

- `.github/workflows/ci.yml` is the remote verification entry.
- `web/package.json`, Playwright configuration, React routes, JSX `data-testid` values, and browser tests form the web verification surface.
- `.planning/PRD-OBLIGATIONS.md`, phase artifacts, and `EVIDENCE/*.json` form the planning verification surface.
- GitHub issue #13 contains the corrected seven-item Phase 1 scope and the allowed-user execution trigger at `https://github.com/Stanley-Zheong/ycsopen-sms/issues/13#issuecomment-5466863741`; issue authorization is resolved and is not an entry blocker.

</code_context>

<specifics>
## Specific ideas

- Preserve per-check diagnostic evidence even when the aggregate command fails.
- Make destructive fixtures prove each fail-closed rule, not merely the positive path.
- Keep Google Chrome identity honest: a missing standard path, non-Google-Chrome version string, failed direct entry launch, or failed Playwright acceptance launch is `BLOCKED` and nonzero.
- Exclude generated entry/runtime evidence from canonical source membership. Bootstrap/lifecycle validates `local-chrome-entry.json` and the independent review separately; the browser envelope/evidence manifest separately checksums Plan 06 `local-chrome-runtime.json`. Neither artifact is an immutable downloaded-source input or requires a committed envelope to contain its own final commit identity.

</specifics>

<deferred>
## Deferred ideas

- Phase 2 owns the complete Admin/Tenant prototype and UI registry content.
- Later production UI phases own React behavior and obligation-linked browser tests for their business surfaces.
- Phases 51-56 own final assurance across security, capacity, reliability, observability, extensions, and release composition; Phase 56 specifically owns OBL-NFR-CHINESE and OBL-NFR-TIMEZONE product acceptance.

</deferred>
