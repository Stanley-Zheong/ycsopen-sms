---
name: test-case-review
description: "Independently review existing ycsopen-sms testcase notes and TEST-MATRIX rows for PRD traceability, state/role/tenant correctness, actions, fixtures, strong oracles, cleanup, UI selector mapping, and automation claims. Do not author a new suite or execute Playwright."
---

# Testcase review

Read the complete case, owned obligation, phase behavior, implementation/API,
UI-ELEMENTS, neighboring cases, and cited evidence. Findings lead and cite exact
paths/lines.

Check one-to-one atomic coverage, realistic state machine, same/cross-tenant
permissions, exact element/action mapping, independent readback, failure and
boundary paths, deterministic cleanup, and whether automation labels are
supported by actual selectors/spec assertions rather than names alone.

Return:

```yaml
verdict: approved | revise | blocked
case_ids: []
automation_contract_required: true | false
findings: []
residual_risk: []
next_owner:
```

Approved but incomplete automation axes go to calibration or automation
contract. Defects return to generation. Review approval is not runtime proof.
