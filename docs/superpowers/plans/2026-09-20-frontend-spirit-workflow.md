# Frontend Spirit Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish the frontend development convention and divide the remaining frontend work into independently verifiable spirit packages.

**Architecture:** The repository-level convention lives in `AGENTS.md`; the frontend spirit governance and package index live under `.planning/frontend-spirits/`. Each spirit owns a narrow product slice and carries its own spec, decisions, design, iteration ledger, and quality gateway so development can proceed commit-by-commit and merge only after evidence is recorded.

**Tech Stack:** Markdown planning artifacts, React/Vite frontend under `web`, Vitest, Chrome Playwright, Docker Compose release verification.

**Spec:** `docs/PRD_V2.md`

## Global Constraints

- Target Java 21. Run backend checks with `mvn -f core/pom.xml test` when backend behavior changes.
- Target Node.js 20 or newer. Run frontend checks with `npm --prefix web ci`, `npm --prefix web test`, and `npm --prefix web run build`.
- Deliver repository changes through a branch and pull request. Do not push implementation commits directly to `main`.
- Keep changes scoped to the GitHub issue. Add or update tests for behavior changes and record any verification boundary that could not be executed.
- Keep status claims evidence-based; a placeholder, prototype, or unverified integration must not be described as complete.
- Do not estimate time, duration, person-days, weeks, timelines, velocity, completion dates, or schedule percentages.

## Review Focus

- Action contracts: every changed button names its target, input, effect, failure feedback, and audit result.
- Form behavior: no editable input remains without query, submit, or explicit async-link behavior.
- UI consistency: query panels, forms, tables, empty states, modals, widths, heights, and `data-testid` follow `docs/frontend页面实现规范.md`.
- Data lineage: every displayed metric/export/result names source, freshness, and known limitation when applicable.
- Release evidence: changed frontend behavior is validated through unit, Chrome Playwright, build, and spirit-specific Docker checks when release behavior changes.

---

### Task 1: Repository Frontend Contract

**Files:**
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: `docs/frontend页面实现规范.md`, `docs/PRD_V2.md`
- Produces: repository-wide frontend development rules used by every later spirit.

- [x] **Step 1: Add frontend contract to `AGENTS.md`**

```markdown
- Frontend work must follow `docs/frontend页面实现规范.md` and
  `.planning/frontend-spirits/README.md`.
```

- [x] **Step 2: Verify contract formatting**

Run: `git diff --check`
Expected: exit code 0.

- [x] **Step 3: Commit**

```bash
git add AGENTS.md
git commit -m "docs: add frontend spirit development contract"
```

### Task 2: Frontend Spirit Index

**Files:**
- Create: `.planning/frontend-spirits/README.md`

**Interfaces:**
- Consumes: `docs/PRD_V2.md`, `docs/ISSUE_BUG_RETROSPECTIVE.md`
- Produces: the common spirit definition, required artifacts, merge gate, and ordered spirit list.

- [x] **Step 1: Write spirit index**

Create `.planning/frontend-spirits/README.md` with the definition of a frontend spirit, required artifact names, quality gate, and the five package sequence.

- [x] **Step 2: Verify index formatting**

Run: `git diff --check`
Expected: exit code 0.

- [x] **Step 3: Commit**

```bash
git add .planning/frontend-spirits/README.md
git commit -m "docs: define frontend spirit workflow"
```

### Task 3: Spirit Packages

**Files:**
- Create: `.planning/frontend-spirits/01-foundation-contract/SPEC.md`
- Create: `.planning/frontend-spirits/01-foundation-contract/DECISIONS.md`
- Create: `.planning/frontend-spirits/01-foundation-contract/SYSTEM-DESIGN.md`
- Create: `.planning/frontend-spirits/01-foundation-contract/ITERATIONS.md`
- Create: `.planning/frontend-spirits/01-foundation-contract/QUALITY-GATEWAY.md`
- Repeat the same artifact set for `02-admin-operations`, `03-tenant-commercial-finance`, `04-delivery-data-workbench`, and `05-release-acceptance`.

**Interfaces:**
- Consumes: spirit index, current GitHub issue set, V2 PRD, frontend page specification.
- Produces: one independently reviewable development package per frontend slice.

- [x] **Step 1: Write each spirit artifact**

Each spirit package must identify scope, out-of-scope boundaries, action contracts, shared component ownership, decisions, iteration ledger schema, and quality gate commands.

- [x] **Step 2: Verify artifact formatting**

Run: `git diff --check`
Expected: exit code 0.

- [x] **Step 3: Commit**

```bash
git add .planning/frontend-spirits
git commit -m "docs: split frontend work into spirit packages"
```

### Task 4: Pull Request Update

**Files:**
- Existing PR branch only.

**Interfaces:**
- Consumes: commits from Tasks 1-3.
- Produces: updated pull request with verification evidence.

- [x] **Step 1: Push branch**

Run: `git push`
Expected: branch updates successfully.

- [x] **Step 2: Update PR body or comment**

Record `git diff --check` and note that this is documentation-only with no frontend runtime tests run.

- [x] **Step 3: Merge only after gate**

Merge the PR only after repository checks required for the documentation change are green and the PR review boundary is satisfied.
