---
name: ycsopen-sms
description: Repo-local engineering workflow for ycsopen-sms. Use for implementing, debugging, refactoring, or verifying changes across the Java 21/Spring Boot core, React/Vite web console, Flyway schema, APIs, permissions, asynchronous SMS lifecycle, documentation, and GSD phase artifacts. Do not use for unrelated repositories or for planning work whose owning GSD phase has not passed its entry gate.
---

# ycsopen-sms engineering

This is the repository entry skill. Read it before a narrower skill in this
directory.

## Sources of truth

Use authority in this order and report conflicts instead of silently choosing:

1. `docs/PRD.md` and `.planning/PRD-OBLIGATIONS.md` for product obligations.
2. The active phase SPEC, DESIGN, DECISIONS, TODO, TEST-MATRIX, and UI-ELEMENTS.
3. Current executable code, migrations, tests, and observed runtime behavior.
4. `core/docs/API.md`, `core/docs/ARCHITECTURE.md`, README, and the user manual.

Current implementation roots are `core/` (Java 21, Spring Boot 3.3, JPA,
MySQL 8, Redis, Quartz, Flyway) and `web/` (React 18, TypeScript, Vite,
Vitest). Playwright is required by the GSD production UI contract but is not
installed in the current frontend baseline; the first owning UI phase must add
and verify its config, dependency, browser setup, `web/e2e/` layout, and CI lane
before Playwright generation/execution can claim readiness. Do not import assumptions from Everest, Lhotse, SSM/Vue, GitLab,
Jenkins, or Kubernetes unless the current repository adds them explicitly.

## GSD execution gate

- Select one dependency-unblocked module from `.planning/ROADMAP.md`.
- Follow `.planning/EXECUTION-STANDARD.md`; implementation starts only after
  the phase entry validator and independent entry review pass.
- Keep scope inside the active phase. Record discovered cross-module work as an
  open dependency or later-phase TODO.
- Maintain SPEC, INTENT, DESIGN, ITERATIONS, DECISIONS, TODO, TEST-MATRIX,
  evidence, and UI/SCHEMA artifacts required by the phase template.
- Never create schedule, duration, staffing, velocity, percentage, or target
  date estimates. Completion means the declared and verified TODO set is empty.

## Implementation rules

1. State the behavior ID and invariant before changing code.
2. Locate the semantic owner; do not hide service, persistence, permission, or
   protocol defects in controllers or UI conditionals.
3. Write the smallest failing test at the lowest truthful layer, then implement
   clear domain code. Comments explain policy, protocol, security, or non-obvious
   constraints; they do not narrate syntax.
4. Preserve tenant isolation, role checks, secret handling, idempotency,
   transaction boundaries, message state transitions, billing consistency, and
   auditability on every affected path.
5. Update `core/docs/API.md` and user-facing docs when their contract changes.
6. For schema work, load `skills/flyway-migration/SKILL.md` and satisfy the
   phase schema-ownership contract.
7. For non-trivial cross-layer work, load
   `skills/feature-delivery-guardrails/SKILL.md`.
8. For UI work, load `skills/ui-design/SKILL.md` and obey
   `.planning/UI-TEST-CONTRACT.md`. Every interactive or asserted element has a
   documented stable `data-testid` before implementation.

## Verification

Run focused checks first and then the affected suites:

```bash
mvn -f core/pom.xml test
npm --prefix web ci
npm --prefix web test
npm --prefix web run build
/usr/bin/env ruby .planning/tools/test-planning-validators.rb
```

Use phase-specific validator and Playwright commands from the active
TEST-MATRIX. Static checks, mocked tests, prototypes, and production browser
evidence are distinct and must not be represented as one another.

Before completion, run GSD verification, code review, and the required Claude
review cycle; resolve every BLOCKER/HIGH finding and prove the scoped TODO query
is empty. Follow `AGENTS.md`: create the atomic phase commit on a scoped branch,
push that branch, deliver through a pull request, and never push implementation
commits directly to `main`. Record the visible commit SHA and PR in the phase
summary.
