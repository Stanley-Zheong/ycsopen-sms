# Planning Standards

## Completion invariant

- Do not use duration, calendar, staffing, velocity, completion-date, or schedule-progress estimates.
- A phase is complete only when every scoped TODO is closed and each closure has executable verification evidence.
- The project is complete only when the repository-wide scoped TODO query is empty after final verification.
- A passing build alone is evidence for that build command only; it is never evidence that a PRD behavior is implemented.

## Phase boundary

- Each phase owns one coherent business or platform module.
- A module phase is a vertical slice: specification, decisions, design, data/API behavior, UI when applicable, implementation, automated tests, verification, review, documentation, and one atomic commit.
- Shared foundations may be their own phases only when later modules depend on their explicit contracts.
- A phase must not absorb unrelated work merely because it touches the same technical layer.

## Mandatory phase records

Every phase directory must contain and maintain:

- `SPEC.md`: falsifiable scope, behaviors, boundaries, and acceptance criteria.
- `INTENT.md`: goal, deliverables, commit-sized tasks, and verification state.
- `DESIGN.md`: architecture, data flow, state machines, API and UI design.
- `ITERATIONS.md`: discoveries, attempted approaches, corrections, and evidence from execution loops.
- `DECISIONS.md`: accepted/rejected decisions with context and consequences.
- `PLAN.md`: executable tasks, dependencies, files, tests, and verification commands.
- `TODO.md`: the authoritative scoped checklist; no separate percentage status is allowed.
- `TEST-MATRIX.md`: PRD requirement to unit/integration/API/Playwright test mapping.
- `VERIFICATION.md`: executed commands, results, evidence links, and remaining TODO query.
- `REVIEW.md`: GSD review findings and resolution evidence.
- `CLAUDE-REVIEW.md`: external Claude review findings and resolution evidence.
- `SUMMARY.md`: concise handoff after all gates pass.

UI phases additionally require `UI-SPEC.md`, `UI-ELEMENTS.md`, Pencil `.pen` artifacts, clickable prototype output, and visual QA evidence.

## Entry gate

Before implementation begins, a verification subagent must check that the phase entry criteria are comprehensive, rigorous, executable, and traceable to the PRD.
The phase cannot enter execution while that check has an unresolved blocking finding.

## Exit gate

The phase cannot be called complete until all of the following are true:

- Scoped PRD behaviors have tests and executed evidence.
- UI contracts, when applicable, enumerate every page, region, element, table, modal, drawer, popover, floating control, action, state, permission condition, and stable `data-testid`.
- Playwright scenarios are derived from the PRD and the actual implementation, not only from mocked prose.
- GSD verification and code review have no unresolved blocking finding.
- Claude has independently reviewed the phase changes and all blocking findings are resolved.
- `TODO.md` contains no unchecked scoped item.
- The phase is committed atomically; pushing or opening a pull request follows the repository's explicit delivery policy.

