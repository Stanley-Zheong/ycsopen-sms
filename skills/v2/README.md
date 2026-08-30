# Test skills v2

`catalog.json` is the machine-readable catalog for fourteen typed ycsopen-sms
testing skills. Each stage owns one artifact or decision boundary.

```text
test-case-generation -> test-case-review -> test-calibration-workflow (when needed)
  -> test-automation-contract -> test-fixture-plan
  -> test-fixture-provision (only for authorized writes)
  -> test-spec-generation -> test-spec-review -> test-execution
  -> test-failure-triage (only on failure) -> test-delivery-review

any stage -> test-knowledge-query
explicit reviewed mutation -> test-knowledge-publish -> test-knowledge-query
ambiguous entry -> test-lifecycle -> exactly one owner
```

## Shared project contract

- Atomic PRD authority: `.planning/PRD-OBLIGATIONS.md`.
- Phase behavior and evidence: `.planning/phases/<NN>-<package-id>/`.
- UI selector and Playwright law: `.planning/UI-TEST-CONTRACT.md`.
- Execution and review gates: `.planning/EXECUTION-STANDARD.md`.
- Planned production browser specs: `web/e2e/**/*.spec.ts`. The current baseline
  has Vitest but not Playwright; the first owning production UI phase must add
  the dependency, config, browser setup, and CI lane before generation/execution.

Every owned obligation has exactly one TEST-MATRIX row. UI rows map exact
route, `data-testid`, Playwright ID, Case ID, OBL ID, requirement IDs, and
behavior ID. Prototype, static, unit, integration, and production browser
evidence remain distinct.

Remote/shared fixture mutation, GitHub issue creation, knowledge publication,
commit, push, merge, or release requires explicit authority. No skill creates
estimates; stage completion is an empty verified scoped TODO set.
