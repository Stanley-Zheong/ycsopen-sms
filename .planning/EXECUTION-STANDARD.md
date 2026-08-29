# GSD Phase Execution Standard

## Governing rule

The only completion signal is an empty verified TODO set for the declared scope.
No schedule, duration, staffing, velocity, completion-date, or percentage status is created or maintained.

## Bounded review and revision cycle

The same cycle governs the entry verification subagent, UI/design checker, plan checker, GSD goal verifier, GSD code reviewer, and Claude reviewer:

1. Start a cycle with the current artifact/diff, executable evidence, and unresolved-finding inventory.
2. Run no more than three review attempts in that cycle. Record each attempt, its exact finding IDs and severities, evidence, corrections, and recheck in `ITERATIONS.md` and the review artifact.
3. After each attempt, count unresolved `BLOCKER` and `HIGH` findings. For checkers that also gate on `WARNING`, record the gated-warning count separately.
4. Pass only when the applicable unresolved blocking count is zero and every required executable check passes.
5. If the blocking count does not decrease between consecutive attempts, or the third attempt remains blocked, stop the cycle and escalate to the developer with the exact remaining findings and failed evidence.
6. Escalation is not approval, implementation authorization, phase completion, or permission to check a TODO. Every affected TODO remains open.
7. A new cycle may begin only after a new developer decision or new executable evidence is recorded. The new cycle again has no more than three attempts.

There is no “proceed anyway” completion path. The eventual final review must be blocking-free.

## Phase selection

Select exactly one dependency-unblocked phase from `ROADMAP.md`.
Do not begin another module merely because a shared file is nearby.
If execution reveals another module, record it as a dependency or later-phase TODO and keep the active phase fenced.

## Canonical phase directory

Use `.planning/phases/<NN>-<package-id>/`.

GSD-generated artifacts keep their native names, including `<NN>-SPEC.md`, `<NN>-CONTEXT.md`, `<NN>-UI-SPEC.md`, one or more `<NN>-<plan>-PLAN.md` and `<NN>-<plan>-SUMMARY.md`, `<NN>-VERIFICATION.md`, and `<NN>-REVIEW.md`.

The project extension artifacts are `INTENT.md`, `DESIGN.md`, `ITERATIONS.md`, `DECISIONS.md`, `TODO.md`, `TEST-MATRIX.md`, `UI-ELEMENTS.md` when UI exists, `SCHEMA-CLAIMS.md` when migrations are declared, `CLAUDE-REVIEW.md`, and `EVIDENCE/`.
Each artifact is updated as the phase evolves; later files do not silently overwrite earlier intent or decisions.

## Gate A — dependency preflight and artifact instantiation

1. Resolve every `Depends on` entry to executable evidence and the dependency's empty TODO result.
2. Instantiate the canonical phase directory and every required artifact from the project template before filling their contracts.
3. Copy the roadmap entry criteria into the phase spec and expand each criterion into a binary check with an evidence target.
4. Create attack, boundary, failure, authorization, data-quality, and recovery cases appropriate to the module.
5. Fail closed on missing or non-clear dependency evidence. This dependency preflight does not claim that the later full execution-entry gate has passed.

Passing this dependency preflight authorizes specification, design, and planning work for the selected module only. It never authorizes implementation.

## Gate B — intent and behavior contract

1. Run the GSD spec workflow against the selected phase and current code.
2. Give the phase a stable kebab-case package ID.
3. Give every behavior a permanent `<package-id>-<number>` ID.
4. Express one contract per behavior, with static conditions, state, trigger, subject, and required outcome.
5. Cite exact peer behavior IDs at every cross-module binding.
6. Define external behavior, internal behavior when required, exclusions, errors, boundaries, and verification items.
7. Map every owned PRD requirement to behavior IDs and verification IDs.
8. Record material choices and rejected alternatives in `DECISIONS.md`.

### Schema ownership and migration contract

1. Treat `.planning/SCHEMA-OWNERSHIP.md` as the machine-readable owner and namespace registry; a phase does not acquire a table/prefix merely by editing it.
2. Write exactly `Schema migrations: none` or `Schema migrations: declared` in `DESIGN.md`.
3. For `declared`, create `SCHEMA-CLAIMS.md` with concrete schema object/prefix, registered owner, globally unique migration ID inside that owner's namespace, migration dependencies, one `expand`/`migrate`/`contract` step, and executable rollback/compensation.
4. A cross-owner claim keeps the registered owner and cites an owning-package approval `DR-*` present in the active phase's `DECISIONS.md`.
5. Entry fails on unknown/duplicate prefixes, namespace overlap, duplicate planned or SQL migration IDs, incompatible dependencies, missing rollback, or missing cross-owner approval. Contract/removal waits for compatibility evidence from every reader and writer.

The spec is not ready while implementation would require guessing a role, state transition, data source, error outcome, or external dependency contract.

## Gate C — UI design contract when applicable

1. Run the GSD UI workflow before implementation.
2. Use the local Pencil MCP and the local uiskill at `/Users/laosanzheong/Documents/codebases/hengshi-jarvis/projectlogs/907/uiskill`.
3. Reconcile the module with the ycsan web brand and the project tokens; do not invent a disconnected style.
4. Freeze information architecture, page inventory, role/permission matrix, user flows, implicit-requirement decisions, shared components, and responsive behavior.
5. Create high-fidelity Pencil screens and clickable states; access `.pen` files only through Pencil tools.
6. Inventory every page, region, field, button, link, card, chart, table, row action, tab, modal, drawer, popover, tooltip, toast, floating control, state, permission, and stable `data-testid` in the exact `UI-ELEMENTS.md` table from `UI-TEST-CONTRACT.md`. Every semantic column is explicit; placeholders fail. Every direct UI obligation links its exact atomic, requirement, behavior, catalog-test, and Playwright IDs on the corresponding page/selector row.
7. Create checksum-bound `EVIDENCE/ui-contract.json` that maps the exact UI-ELEMENTS routes/selectors to manifest, prototype interaction sources, and later production evidence. Design entry does not require React. Production implementation proof is restricted to executable `web/src/**/*.{tsx,ts,jsx,js}` route/JSX contexts; production browser proof is restricted to executable `web/**/*.{spec,test}.{ts,tsx,js,jsx}` test blocks.
8. Phase 2 alone uses explicit `prototype` mode: its inventory binds Pencil/HTML/selector registry/prototype Playwright sources and labels test/evidence rows `prototype`; it cannot satisfy React production obligations or run the production stage.
9. Run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase <NN> --package <package-id> --stage design`, then the design-consistency and PRD/atomic-obligation reviews under the bounded cycle. `validate-phase-entry.rb --ui` invokes this exact design stage. Escalation leaves UI TODOs open and blocks implementation.

No UI implementation starts with an incomplete element inventory or unresolved design blocking finding.

## Gate D — executable plan audit

1. Run the GSD plan workflow with the accepted spec, decisions, design, and current code.
2. Break work into testable tasks with explicit files, behavior IDs, expected tests, commands, and evidence outputs.
3. Prefer vertical behavior slices inside the module; do not create backend-only or UI-only completion claims.
4. Have a plan-checker subagent perform goal-backward verification under the bounded revision cycle, recording exact issue counts on every attempt.
5. Resolve plan-checker blocking findings within the cycle. Non-decreasing or third-attempt unresolved findings escalate without execution authorization and remain open TODOs.
6. Run the roadmap's exact `/usr/bin/env ruby .planning/tools/validate-phase-entry.rb ...` command against the now-populated spec, context, intent, design, plans, TODO, TEST-MATRIX, decisions, iterations, evidence directory, owned atomic trace, schema claims when declared, dependency evidence, and UI artifacts when applicable. At entry, every owned obligation has exactly one open TODO checkbox and no current-phase checkbox may be pre-checked; completed dependencies, by contrast, must have no unchecked TODO.
7. Spawn an independent entry verification subagent to check completeness, rigor, executability, PRD traceability, and scope focus. Under its own bounded revision cycle, it writes a unique row per criterion in `ENTRY-REVIEW.md` using the exact columns `Criterion ID | Verdict | Evidence | Command or inspection rule`; verdict is exactly `PASS` or `BLOCKER`, and a final `## Verdict` section is exactly `PASS` only when every row passes.
8. After correction, rerun the same execution-entry command with the completed `ENTRY-REVIEW.md`. Missing artifacts, missing owned IDs, non-runnable checks, contradictions, or any BLOCKER fail closed; only the final zero-exit, blocking-free result authorizes implementation.

## Gate E — implementation and iteration record

1. Write failing tests for the selected behavior at the lowest useful layer.
2. Implement production behavior in human-readable code with domain names, small units, comments for policy or protocol reasoning, and PRD/behavior references where useful.
3. Run focused tests, then the affected suite.
4. Record meaningful failures, discoveries, deviations, and corrections in `ITERATIONS.md`.
5. Update `DECISIONS.md` before implementing a consequential deviation.
6. Keep `TODO.md` synchronized item by item; a checked item cites evidence rather than merely claiming completion.

## Gate F — acceptance automation

1. Derive acceptance cases from every owned atomic PRD obligation, each linked top-level integration requirement, phase behaviors, state machines, errors, permissions, and actual implementation paths.
2. Fill the fixed `TEST-MATRIX.md` machine table with exactly one row per owned atomic obligation. Each row repeats exact requirement, behavior, and catalog test/layer values and records an explicit case, command, and evidence; direct UI obligations also exactly match their UI row's page, selector, and Playwright ID. Free text and missing, duplicate, or mismatched atomic rows fail closed.
3. Author Playwright tests against stable documented `data-testid` selectors and real application behavior.
4. Cover allow and deny paths, validation, loading, empty, error, retry, destructive confirmation, async outcomes, tenant isolation, and role visibility where applicable.
5. Preserve traces, screenshots, videos, logs, request IDs, database facts, protocol captures, or other evidence required to diagnose a failure.
6. For every direct UI obligation, put its Playwright ID, Case ID, and OBL ID in one test title/annotation; that same block awaits the linked route navigation and a linked-selector action/assertion. One-to-one Case/Playwright IDs, dead-locator rejection, and route→selector browser closure are mandatory.

## Gate G — independent verification and review

1. Run GSD goal verification against the phase spec and requirement trace under the bounded revision cycle.
2. Run GSD code review against the complete phase diff under the same bounded cycle.
3. Resolve blocking findings and rerun affected verification within the active cycle; a non-decreasing count or unresolved third attempt escalates with TODO open.
4. Invoke Claude as an independent read-only reviewer of the full phase diff, contracts, tests, and evidence under the same bounded cycle.
5. Record every Claude attempt, finding count, resolution, evidence, and recheck in `CLAUDE-REVIEW.md`. Claude BLOCKER/HIGH fixes must be reviewed again; an escalation cannot substitute for a final clear Claude result.
6. Run the phase TODO query; any unchecked scoped item or escalated unresolved finding fails the gate.

## Gate H — atomic delivery

1. For a production UI phase, run `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase <NN> --package <package-id> --stage production` and require PASS. This reruns design plus React/real-browser/executed-evidence validation. Phase 2 instead reruns its scoped `--stage design` gate because it owns no React implementation.
2. Confirm the phase directory and implementation diff contain no unrelated work.
3. Record final commands and evidence in `<NN>-VERIFICATION.md` and `SUMMARY.md`.
4. Confirm GSD and Claude have final blocking-free results and no review cycle is left in escalated/unresolved state.
5. Confirm the scoped TODO query is empty.
6. Create one atomic phase commit that includes specification, design, decisions, code, tests, and evidence references.
7. Push the commit to the configured GitHub remote and record the remote branch and commit SHA in `SUMMARY.md`.
8. Advance `STATE.md` only after the remote commit is visible.
