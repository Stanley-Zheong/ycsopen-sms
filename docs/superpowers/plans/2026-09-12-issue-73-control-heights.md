# Issue 73 Control Heights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ordinary inputs, single-value selects, and textareas start at the same 40px height throughout the web console.

**Architecture:** Keep the sizing contract in the shared stylesheet because every page imports it and the defect is visual rather than page-specific. Preserve the existing 16px checkbox/radio contract and the multi-row select contract, while allowing textareas to remain vertically resizable after their consistent initial render.

**Tech Stack:** React 18, TypeScript 5.6, Vite 5, CSS, Playwright 1.62, Vitest 2.1.

**Spec:** `.planning/changes/issue-73-control-heights/SPEC.md`

## Global Constraints

- Target Node.js 20 or newer.
- Use the existing 40px console control contract and add no dependency.
- Preserve checkbox/radio sizing, multi-select sizing, permissions, values, validation, API effects, and page layout.
- Deliver from a scoped branch through a pull request; never push implementation commits directly to `main`.

---

### Task 1: Shared primary-control height contract

**Files:**
- Create: `.planning/changes/issue-73-control-heights/SPEC.md`
- Create: `.planning/changes/issue-73-control-heights/UI-ELEMENTS.md`
- Create: `.planning/changes/issue-73-control-heights/TEST-MATRIX.md`
- Create after execution: `.planning/changes/issue-73-control-heights/EVIDENCE/playwright-control-height-report.json`
- Modify: `web/test/scripts/control-sizing.spec.ts`
- Modify: `web/src/styles/index.css`

**Interfaces:**
- Consumes: the existing `--color-*` and `--radius-control` tokens, the existing 40px query-control contract, and the rendered `/admin/routing-policy` controls.
- Produces: behavior `issue-73-primary-control-height`, Playwright case `C-ISSUE-73-PRIMARY-CONTROL-HEIGHT`, and a shared CSS rule that gives ordinary inputs, single-value selects, and textareas a 40px initial height.

- [ ] **Step 1: Record the scoped production contract**

  Add the Issue #73 behavior, exclusions, representative route, selectors, and browser evidence command to the three `.planning/changes/issue-73-control-heights/` contract files.

- [ ] **Step 2: Write the failing browser test**

  Extend `web/test/scripts/control-sizing.spec.ts` with a real-DOM geometry assertion for these existing controls:

  ```ts
  const controls = [
    page.getByTestId('admin-routing-circuit-routing-policy-version'),
    page.getByTestId('admin-routing-circuit-routing-policy-import-input'),
    page.getByTestId('admin-routing-circuit-routing-retry-category'),
  ];

  for (const control of controls) {
    const box = await control.boundingBox();
    expect(box, 'primary control has a layout box').not.toBeNull();
    expect(box!.height, 'primary controls share the 40px height').toBe(40);
  }
  ```

- [ ] **Step 3: Run the focused test and verify RED**

  Run from `web/`:

  ```bash
  YCSOPEN_USE_BUNDLED_CHROMIUM=true npm run test:e2e -- control-sizing.spec.ts --project=bundled-chromium --grep pw-issue-73-primary-control-height
  ```

  Expected: FAIL because `admin-routing-circuit-routing-policy-import-input` renders at 72px while the input and select render at 40px.

- [ ] **Step 4: Implement the shared CSS fix**

  In `web/src/styles/index.css`, include `textarea` in shared typography and field styling. Set ordinary non-choice inputs, single-value selects, and textareas to `height: 40px` and `min-height: 40px` from a selector specific enough to override page-local minimum heights. Keep textareas `resize: vertical`; exclude checkbox/radio and `select[multiple]` from the fixed initial height.

- [ ] **Step 5: Run focused and full verification**

  Run the focused Playwright case until it passes, then run:

  ```bash
  npm --prefix web test
  npm --prefix web run build
  npm --prefix web run lint
  mvn -f core/pom.xml test
  /usr/bin/env ruby .planning/tools/test-planning-validators.rb
  git diff --check
  ```

  Record the executed browser command, result, runtime boundary, and SHA-256 checksums for the shared stylesheet and Playwright source in `EVIDENCE/playwright-control-height-report.json`.

- [ ] **Step 6: Review and deliver**

  Run the repository pre-push review over the complete semantic diff, resolve actionable findings, create one atomic commit named `fix(web): unify primary control heights`, push `fix/73-unify-control-heights`, open a PR with `Closes #73`, wait for required checks, and merge only if branch protection and repository checks allow it.
