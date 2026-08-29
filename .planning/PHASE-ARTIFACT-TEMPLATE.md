# Phase Artifact Template

Replace `<NN>`, `<package-id>`, `<behavior-id>`, and `<requirement-id>` with stable values.
Delete instructional text when a phase package is instantiated.

## `<NN>-SPEC.md`

```markdown
# <package-id>: <module title>

## Intent

<One narrow module purpose.>

## Scope

### In
- <Owned behavior>

### Out
- <Explicit non-goal and owning phase>

## External Behavior

### <package-id>-1

Where <static condition>, while <state>, when <trigger>, <subject> shall <observable outcome>.

## Internal Behavior

### <package-id>-2

Where <condition>, when <trigger>, <component> shall <hidden guarantee required by the external behavior>.

## Errors and boundaries

| Case | Required outcome | Behavior ID |
| --- | --- | --- |
| <case> | <outcome> | <package-id>-1 |

## Verification

### <package-id>-3

Where <fixture/state>, when <real action>, the suite shall assert <result> [[<package-id>-1](#<package-id>-1)].

## Requirement trace

| PRD requirement | Behavior IDs | Verification IDs |
| --- | --- | --- |
| <requirement-id> | <package-id>-1 | <package-id>-3 |
```

## `<NN>-CONTEXT.md`

```markdown
# Phase Context

## Dependency evidence
- <dependency SUMMARY, empty-TODO result, and remote SHA>

## Current implementation facts
- <verified code/schema/route fact with inspection command>

## Constraints and exclusions
- <phase fence and inherited contract>
```

## `INTENT.md`

```markdown
# Intent

## Status
Open

## Goal
<The outcome this phase is trying to realize.>

## Deliverables
- [ ] <deliverable with evidence target>

## Tasks
1. <One coherent, commit-sized action inside the phase.>

## Verification
Planned: <commands and evidence targets>.
```

## `DESIGN.md`

```markdown
# Design

## Context and constraints
## Architecture and ownership
## Data model and migrations
## State machines
## API or protocol contracts
## Authorization and tenant isolation
## UI and interaction model
## Async, idempotency, retry, and concurrency
## Security and privacy
## Observability and audit
## Failure, rollback, and recovery
## Alternatives rejected

Schema migrations: none
```

Replace the final marker with `Schema migrations: declared` only when this phase plans persistence changes. A declared phase also creates `SCHEMA-CLAIMS.md`; omission, namespace conflict, or an unapproved cross-owner claim fails entry.

## `SCHEMA-CLAIMS.md` when migrations are declared

```markdown
# Schema Claims

| Claim ID | Schema object/prefix | Owner package | Migration ID | Depends on migration | Compatibility step | Rollback | Cross-owner approval |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SC-<NN>-001 | <prefix registered in SCHEMA-OWNERSHIP.md> | <registered owner> | V<unique number in owner namespace> | <V IDs or -> | <expand, migrate, or contract> | <forward-compatible rollback/compensation> | <- or DR-ID in DECISIONS.md> |
```

## `ITERATIONS.md`

```markdown
# Iterations

| Iteration ID | Trigger or finding | Evidence | Change made | Affected behavior/decision | Recheck |
| --- | --- | --- | --- | --- | --- |
| I-001 | <finding> | <path/command> | <correction> | <IDs> | <result> |
```

## `DECISIONS.md`

```markdown
# Decisions

## DR-<NN>-001: <title>

### Status
Accepted

### Context
<Why a durable choice is required.>

### Decision
<Choice and constraints.>

### Consequences
- <positive or negative consequence>

### References
- <PRD or authoritative source>
```

## `TODO.md`

```markdown
# Authoritative Phase TODO

Every checked item must cite executable evidence.

## Entry gate
- [ ] <criterion> — Evidence: <not recorded>

## Spec and design
- [ ] <artifact/behavior> — Evidence: <not recorded>

## Implementation
- [ ] <behavior slice> — Evidence: <not recorded>

## Tests and verification
- [ ] <test or gate> — Evidence: <not recorded>

## Reviews and delivery
- [ ] GSD verification has no unresolved blocking finding — Evidence: <not recorded>
- [ ] GSD code review has no unresolved blocking finding — Evidence: <not recorded>
- [ ] Claude review has no unresolved blocking finding — Evidence: <not recorded>
- [ ] Scoped TODO query is empty after this item is closed — Evidence: <not recorded>
- [ ] Atomic commit is visible on the configured GitHub remote — Evidence: <not recorded>
```

## `TEST-MATRIX.md`

```markdown
# Test Matrix

| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| <exact owned OBL-ID> | <exact catalog REQ/PROJECT IDs> | <exact catalog behavior ID> | <exact catalog test ID/layer> | <Playwright ID or reason-bearing not-applicable value> | <page ID/route or reason-bearing not-applicable value> | <full selector or reason-bearing not-applicable value> | <unique case ID> | <explicit case> | <executable command> | <evidence path> |
```

The validator requires exactly one row for every owned atomic obligation. Free text, a missing/duplicate atomic row, or a requirement/behavior/catalog-test mismatch fails closed. A direct `page:` or `element:` obligation must use Page ID/route, `data-testid`, and Playwright ID values identical to its linked UI row.

## `UI-ELEMENTS.md`

```markdown
# UI Elements and Automation Contract

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| <id/route> | <explicit rule> | <region> | <element> | <validation/format or explicit read-only rule> | <trigger/API result or explicit no-action> | <asserted states> | <stable-id> | <exact OBL and REQ/PROJECT IDs> | <exact behavior IDs> | <exact catalog test/layer values> | <test-id> |
```

All cells are mandatory and `-`/`n/a`/`tbd`/`todo` placeholders fail. Every direct UI obligation is machine-linked on its corresponding page/selector row with exact atomic, requirement, behavior, catalog-test, and Playwright IDs.

UI phases also contain `<NN>-UI-SPEC.md`, at least one Pencil `.pen`, at least one clickable HTML prototype, and `EVIDENCE/ui-contract.json`. The inventory JSON declares exact routes, selectors, checksum-bound source files, and Playwright evidence:

```json
{
  "mode": "production",
  "pencil": {
    "sources": [{"path": ".planning/phases/<phase>/screen.pen", "sha256": "<64-hex>"}]
  },
  "manifest": {
    "routes": ["/route"],
    "test_ids": ["portal-module-page-region-element"],
    "sources": [{"path": "web/src/module/page.tsx", "sha256": "<64-hex>"}]
  },
  "prototype": {
    "routes": ["/route"],
    "test_ids": ["portal-module-page-region-element"],
    "sources": [{"path": ".planning/phases/<phase>/design-output/prototype.html", "sha256": "<64-hex>"}]
  },
  "prototype_playwright": {
    "test_ids": ["portal-module-page-region-element"],
    "evidence_kind": "prototype",
    "sources": [{"path": ".planning/phases/<phase>/design-output/prototype.spec.ts", "sha256": "<64-hex>"}]
  },
  "implementation": {
    "routes": ["/route"],
    "test_ids": ["portal-module-page-region-element"],
    "sources": [{"path": "web/src/module/Page.tsx", "sha256": "<64-hex>"}]
  },
  "playwright": {
    "test_ids": ["portal-module-page-region-element"],
    "evidence_kind": "production",
    "sources": [{"path": "web/e2e/module/page.spec.ts", "sha256": "<64-hex>"}]
  },
  "execution": {
    "command": "npx playwright test web/e2e/module/page.spec.ts",
    "commit": "<40-hex application commit>",
    "config": "<exact Playwright config and environment identity>",
    "result": "PASS",
    "report": {"path": ".planning/phases/<phase>/EVIDENCE/production-playwright-report.json", "sha256": "<64-hex>"}
  }
}
```

The design-stage inventory binds every `.pen`, HTML prototype, selector manifest, and `prototype_playwright` source. The execution-entry command runs `--stage design`; it does not require React or production browser evidence. Phase 2 uses mode `prototype`, never supplies production sections, and exits only as a design-foundation module.

Every production UI phase adds `implementation`, `playwright`, and `execution`, then runs `--stage production` before its TODO-empty/commit gate. `implementation.sources` are executable `web/src/**/*.{tsx,ts,jsx,js}` components/routes. `playwright.sources` are executable `web/**/*.{spec,test}.{ts,tsx,js,jsx}` tests. The execution report is checksum-bound JSON whose command, commit, config, PASS result, and complete Case-ID set match inventory metadata. Comments, arbitrary strings, `.txt` files, dead locators, and substring-only evidence fail.

## `ENTRY-REVIEW.md`

```markdown
# Entry Review

| Criterion ID | Verdict | Evidence | Command or inspection rule |
| --- | --- | --- | --- |
| ENTRY-<NN>-001 | PASS | <evidence path> | <reproducible command/rule> |

## Verdict

PASS
```

## Verification and review records

`<NN>-VERIFICATION.md`, `<NN>-REVIEW.md`, and `CLAUDE-REVIEW.md` each record scope, exact inputs, findings, resolution commits or diffs, executed rechecks, unresolved items, and final verdict.
`SUMMARY.md` records delivered behaviors, evidence index, decisions, known later-phase dependencies, the empty TODO result, and the remote commit identity.
