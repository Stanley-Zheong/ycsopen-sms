---
name: test-calibration-workflow
description: "Calibrate an approved ycsopen-sms UI automation case whose fixture, cleanup, readback, negative contrast, oracle, or primary claim remains unclear. Use machine evidence for prefill but require explicit reviewed decisions; do not generate Playwright while a required axis is unresolved."
---

# Test calibration workflow

Record calibration in the active phase `EVIDENCE/test-calibration.md`. For each
case, evaluate six axes independently:

- fixture and ownership;
- cleanup/restore;
- durable readback;
- negative contrast;
- primary oracle;
- bounded claim.

For each axis record `satisfied`, `needs-detail`, or `unusable`, authority path,
reviewer decision, and consequence. Current PRD/SPEC/code/runtime evidence wins
over similar historical cases. Machine inference never becomes a product oracle
without reviewed authority.

Set `generation_ready=true` only when fixture, cleanup, readback, and negative
axes are executable and oracle/claim are explicitly reviewed. Otherwise keep
the case TODO open and return the exact missing axis. Ready cases proceed to
`test-automation-contract`; no local server, private ledger, or unavailable
`sbin` command is assumed.
