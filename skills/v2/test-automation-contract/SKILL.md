---
name: test-automation-contract
description: "Compile an approved ycsopen-sms testcase into an executable claim, fixture, action, oracle, cleanup, environment, isolation, known-issue, and readiness contract before fixture work or Playwright generation. Do not invent unresolved product semantics or mutate data."
---

# Automation contract

Store one YAML/Markdown contract per primary claim under the active phase
`EVIDENCE/automation-contracts/`:

```yaml
obligation_id:
requirement_ids: []
behavior_id:
case_id:
playwright_id:
claim:
fixture:
actions: []
oracle:
negative_oracle:
cleanup:
environment:
isolation:
known_issues: []
generation_ready: false
not_ready_reason:
```

Actions name real routes/APIs/selectors and state transitions. Persisted or
derived claims use API/database/result/config readback; visual assertions are
primary only for genuinely visual behavior. Isolation names accounts, tenants,
browser state, mutable IDs, queues, global settings, unique data, and any scoped
lock. `generation_ready=true` requires every material field to be concrete or
an exact verified reuse pointer. Route unresolved fixtures to
`test-fixture-plan`; never guess to unblock generation.
