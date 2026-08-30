---
name: test-spec-review
description: "Independently review generated or changed ycsopen-sms Playwright specs for atomic contract coverage, metadata/selector trace, fixture ownership, worker isolation, action timing, oracle strength, cleanup, maintainability, static gates, and affected shared callers."
---

# Playwright spec review

Read the contract, TEST-MATRIX/UI-ELEMENTS rows, fixture manifest, generation
record, full changed spec, current React/API source, and every affected shared
helper caller.

Check exact OBL/REQ/behavior/Case/Playwright mapping, real route navigation,
linked `data-testid` action/assertion in the same block, role/tenant setup,
strong oracle, negative contrast, async terminal state, deterministic cleanup,
parallel safety, and absence of fixed sleeps or hidden errors. Static validation
proves structure only.

Return findings with paths/lines and:

```yaml
verdict: approved | revise | blocked
contract_coverage: strong | partial | weak | blocked
gate_evidence:
runtime_required: true
next_owner:
```

Approved specs proceed to execution. Contract or implementation defects return
to generation; inherited/observed failures go to triage.
