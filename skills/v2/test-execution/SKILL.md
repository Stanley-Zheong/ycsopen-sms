---
name: test-execution
description: "Preflight and execute reviewed ycsopen-sms Playwright specs or analyze their reports, producing a reproducible manifest, normalized case events, artifacts, and raw failure ledger without modifying specs or deciding product-bug ownership."
---

# Playwright execution

Freeze the exact app commit, command, environment/config, base URL, account
aliases, fixture refs, expected Case/Playwright/OBL IDs, browser/project,
workers, retries, and isolation policy before execution. Store output in the
active phase `EVIDENCE/playwright/<run-id>/`:

Fail closed if the Playwright dependency/config/browser setup or declared CI
lane does not exist; do not substitute Vitest or prototype HTML evidence.

- `run-manifest.json`;
- report and normalized events;
- `failure-ledger.raw.json`;
- trace, screenshot/video, console/network, API/database/protocol evidence as
  required by the claim.

Use the exact TEST-MATRIX command. Prefer two or more workers when isolation
allows; serial requires a declared unavoidable global-state reason. On failure,
retain the first cause and perform only the retries/cross-checks authorized by
the contract. Missing expected events are a spec/evidence gap, never a silent
pass. Do not label a product bug during execution.

Any failure, flaky result, missing event, or blocked run goes to
`test-failure-triage`. A clean, matching run can support delivery review.
