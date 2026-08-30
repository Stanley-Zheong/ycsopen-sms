---
name: test-case-generation
description: "Generate or materially rewrite source-backed ycsopen-sms testcases from PRD obligations, phase behaviors, issues, implementation, UI inventory, manuals, and historical regressions. Do not review an existing draft, provision data, or write Playwright code."
---

# Testcase generation

For every owned atomic obligation, derive cases from current authority rather
than generic test theory. Store detailed case notes under the active phase
`EVIDENCE/test-cases/` and populate the canonical TEST-MATRIX row.

Each case names stable OBL/REQ/behavior/Case/Playwright IDs, role and tenant,
initial state, fixture ownership, exact action, observable oracle, negative and
boundary contrast, persistence/readback, async terminal state, cleanup, and
evidence paths. UI cases use exact routes and `data-testid` values from
UI-ELEMENTS. Split independent claims or incompatible fixtures into separate
atomic obligations before adding rows; do not duplicate one obligation row.

Cover relevant authentication/authorization, cross-tenant denial, validation,
idempotency, routing, channel failure/retry, receipt, billing, complaint,
loading/empty/error, destructive, and recovery behavior. Hand every new or
materially rewritten case to `test-case-review`.
