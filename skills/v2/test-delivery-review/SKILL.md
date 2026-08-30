---
name: test-delivery-review
description: "Perform a read-only final QA assessment of a ycsopen-sms phase commit, GitHub pull request, or release candidate using requirement/test trace, fixture/spec reviews, executed runtime evidence, classified failures, coverage, and residual risk. Do not commit, push, merge, publish, or fill missing evidence."
---

# Test delivery review

Freeze the exact diff/commit, branch/version, active phase, owned obligation
scope, environment, and delivery claim. Require matching TEST-MATRIX rows,
contracts, fixture manifests, spec approval, runtime reports, classified failure
ledgers, validator results, and TODO/review state.

Return findings first, then:

```yaml
verdict: pass | block | advisory
scope:
evidence: []
missing_evidence: []
open_failures: []
coverage_delta:
residual_risk: []
required_followup: []
```

Static gates are not runtime behavior proof. Unclassified failures, mismatched
commit/environment evidence, unresolved BLOCKER/HIGH review findings, incomplete
atomic coverage, failed UI production gate, or non-empty scoped TODOs block.
This skill supplies evidence to the delivery owner and performs no Git/GitHub
mutation.
