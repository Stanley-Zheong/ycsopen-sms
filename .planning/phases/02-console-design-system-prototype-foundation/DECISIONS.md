# Decisions

## DR-02-001: Phase-2 is prototype-only

### Status
Accepted

### Context
Phase 2 precedes every production business UI phase and must remain stable even if business implementation changes.

### Decision
Keep production code and API behavior out of scope. Build only design shell, route, selector, and evidence artifacts.

### Consequences
- Stable design foundation without changing production React/API behavior.
- Phase 2 owns prototype-only Playwright execution in the installed local Chrome; later production phases still own React implementation and production acceptance.

### References
- ROADMAP phase 2
- `ROADMAP.md` Phase 2 scope

## DR-02-002: Concrete obligation slicing by lane

### Status
Accepted

### Context
Phase 2 has 83 owned obligations with distinct IA and edge coverage.

### Decision
Split implementation plans by lane: design system, admin IA, tenant IA, edge/design consistency.

### Consequences
- Bounded responsibility and easier review.
- Lane-specific blockers are explicit.

### References
- `ROADMAP.md` wave and plan definitions

## DR-02-003: Checkpoint review contract stays explicit

### Status
Accepted

### Context
Existing project policy requires review files for phase entry and Claude review.

### Decision
Keep `ENTRY-REVIEW.md` and `CLAUDE-REVIEW.md` explicit and replace their initial blockers only from executable evidence and independent review.

### Consequences
- Entry and final review contracts remain explicit throughout execution.
- No accidental completion through unchecked assumptions.

### References
- `EXECUTION-STANDARD.md`

## DR-02-004: One static browser case per owned obligation

### Status
Accepted

### Context
The UI contract validator requires future automation inputs to be statically discoverable and each of the 83 owned obligations must retain its exact route, `data-testid`, Case ID, and Playwright ID.

### Decision
Use 83 literal Playwright test blocks rather than runtime-generated test definitions. Bind the source, matrix, UI inventory, HTML, Pencil file, and Playwright config with committed SHA-256 evidence. Execute only the installed standard-path Google Chrome at `1440x900`.

### Consequences
- Static drift validation and later test generation can resolve every case without executing arbitrary code.
- Other browsers, browser downloads, version matrices, providers, and compatibility claims remain outside Phase 2.

### References
- `02-UI-SPEC.md`
- `UI-ELEMENTS.md`
- `TEST-MATRIX.md`
- `EVIDENCE/runs/phase02-latest/playwright-report.json`
- `EVIDENCE/evidence-manifest.json`

## DR-02-005: Edge semantics are executable, not placeholders

### Status
Accepted

### Context
Selector presence alone could not prove the empty-list and invalid-review-form behaviors required by the PRD.

### Decision
The empty-list prototype must render guidance and disabled bulk/export actions. The invalid review form must render the exact reason, focus the invalid field, and keep submit disabled. Both behaviors are asserted by local-Chrome Playwright tests and mapped to committed per-obligation results.

### Consequences
- Edge obligations close from behavior assertions rather than placeholder DOM nodes.
- The prototype remains non-production and performs no real API mutation.

### References
- `design-output/prototype.html`
- `design-output/prototype.spec.ts`
- `02-04-SUMMARY.md`

## DR-02-006: Structural mappings cannot close UI obligations

### Status
Accepted

### Context
The first implementation produced 83 green cases by intercepting every request and injecting a test-authored DOM. The route/selector mapping was consistent, but neither the checked-in HTML nor the Pencil source delivered the promised design.

### Decision
An obligation closes only when the installed Chrome loads the checked-in HTML, its literal route/selector test passes, and the catalog's exact evidence target is generated from the canonical reporter. Pencil must be authored and saved through Pencil with named, editable nodes. Aggregate JSON is supporting evidence only.

### Consequences
- Synthetic request interception and user-authored PASS flags are rejected.
- Three visual obligations close through Playwright PNGs; the other 80 close through strict JSON targets.
- The shared validator rejects stale reports, skipped/flaky cases, missing/duplicate targets, source drift, symlink escape and stub markers.

### References
- `02-REVIEW.md`
- `02-VERIFICATION.md`
- `EVIDENCE/evidence-manifest.json`
- `.planning/tools/phase2-ui-evidence.rb`

## DR-02-007: Role visibility, shared elements and brand provenance are executable contracts

### Status
Accepted

### Context
Portal-level labels were insufficient to freeze the PRD's route-specific Admin and Tenant permissions, and an unpinned visual reference could not support a reproducible ycsan design claim.

### Decision
Record exact route-to-role sets in `UI-ELEMENTS.md`, HTML `data-allowed-roles`, role-filtered navigation and Playwright allow/deny assertions. Parse the second 12-column shared-element table into the same UI manifest so every field, action, table column, overlay and reusable state is source- and test-bound. Pin the ycsan reference to commit `f4f8aae9c05a5b527aafd725b1d7410a3b3ad31b`, `app/globals.css` SHA-256 `6931f74f3bbe90f76b972c907b9b519bc93a348fe3e74ba20f7eacfb3ce1fc53`, and the real-Chrome `1440x900` screenshot seal.

### Consequences
- Platform administrator, operator, finance, organization administrator, business user and developer destinations are independently verifiable.
- The UI contract manifest contains both 83 canonical obligation selectors and the exhaustive shared-element selectors.
- `tokens.css`, the pinned snapshot JSON and reference screenshot are runtime evidence inputs; changing any of them invalidates the reporter seal.

### References
- `UI-ELEMENTS.md`
- `design-output/ycsan-style-snapshot.json`
- `EVIDENCE/ycsan-reference-1440x900.png`
