---
name: test-spec-generation
description: "Generate or materially rewrite ycsopen-sms Playwright specs from an approved automation contract and verified fixture evidence, with exact PRD/behavior/case metadata, stable data-testid selectors, worker isolation, strong readback, cleanup, and generation evidence."
---

# Playwright spec generation

Generate production browser tests under `web/e2e/**/*.spec.ts`. Reject an
incomplete contract; generation does not invent an oracle, route, selector,
permission, or fixture. Also reject generation when the owning phase has not
provided a working `@playwright/test` dependency, config, browser installation,
base URL/web-server policy, and CI command. Harness bootstrap is upstream phase
work, not an implicit side effect of generating a spec.

Every direct UI test block includes exact Playwright ID, Case ID, and OBL ID in
its title/annotations, calls `await page.goto(<linked route>)`, and performs an
awaited action/assertion against the linked `data-testid`. Reuse the one-to-one
mapping from UI-ELEMENTS and TEST-MATRIX.

Structure setup -> action -> independent readback -> cleanup. Prefer role/
tenant-aware API or persisted result readback for business claims. Avoid fixed
sleeps, `networkidle` as a business oracle, silent catches, text/CSS-position
selectors, shared mutable storage state, and soft assertions for primary
outcomes. Use per-worker data/context/account or a documented scoped lock;
`workers=1` does not repair avoidable contamination.

Record generation attempts under phase evidence, run TypeScript/static checks
and the exact spec when the required environment exists, then hand the changed
spec to `test-spec-review`. Missing axes return upstream; observed failures go
to triage.
