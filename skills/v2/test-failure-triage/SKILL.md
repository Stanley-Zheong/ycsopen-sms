---
name: test-failure-triage
description: "Classify evidence-backed ycsopen-sms generation, static-gate, Playwright, CI, or report failures into contract, spec, fixture, environment, known issue, flaky, insufficient evidence, or product-bug candidate, with bounded repair only for test-owned causes."
---

# Test failure triage

Correlate the first causal failure with the automation contract, expected
events, fixture manifest, run manifest, trace/log/readback, retries, data
cross-check, environment consistency, current source, and known issues.

Classify exactly one primary disposition:

```text
product-bug-candidate | spec-issue | case-contract-issue | fixture-issue |
environment-issue | known-issue | flaky | insufficient-evidence
```

Check in that order: claim/oracle, fixture/ownership/isolation/cleanup,
selector/action/timing/assertion, environment/auth/services, known issue/version,
then repeatability and cross-check. A serial pass/parallel fail normally exposes
spec or fixture contamination; do not hide it with `workers=1`.

Repair only test-owned files and rerun the original plus affected parallel lane.
A product candidate requires valid contract/fixture, strong failed readback,
consistent environment, repeat failure, supporting cross-check, and no existing
issue owner. Produce a GitHub issue draft; create it only with explicit user
authorization. Record the classified ledger in phase evidence.
