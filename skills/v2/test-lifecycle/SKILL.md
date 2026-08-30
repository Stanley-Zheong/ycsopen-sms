---
name: test-lifecycle
description: "Route an ambiguous ycsopen-sms QA request to exactly one owner among testcase design/review, calibration, automation contract, fixture plan/provision, Playwright generation/review/execution, failure triage, delivery review, or project-knowledge query/publish. Skip when the user already names a narrower owner with complete inputs."
---

# Test lifecycle router

Read `skills/v2/catalog.json`, the active phase, its owned rows in
`.planning/PRD-OBLIGATIONS.md`, and current TEST-MATRIX evidence. Emit:

```yaml
target:
baseline:
oracle:
deliverable:
owner_skill:
```

Choose the owner by the artifact that must exist next. Never skip an upstream
contract because downstream implementation appears easy. Static evidence is not
runtime proof, prototype evidence is not React proof, and execution facts are
not failure classification.

If required authority, fixture ownership, environment identity, or review is
missing, return the exact missing input to its owner. Completion is not a
percentage; the selected stage closes only when its scoped TODOs are empty.
