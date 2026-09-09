# Phase 18 Iterations

## Iteration 1 — Runtime enforcement

- Added Redis fixed-window atomic counter.
- Extended `FrequencyChecker` for mobile opaque indexes, tenant, IP, content-similarity dimensions, scoped rules, exemptions, and hit evidence.
- Added API key second/minute/hour/day enforcement before message submission.
- Added 429 exception contract and handler response.

## Iteration 2 — Admin and tenant UI

- Added `/admin/frequency/rules` page, API client, nav entry, route, styles, and unit test.
- Added tenant API key rate-limit selector marker while preserving Phase 09 test-id contract.
- Added local Chrome Playwright coverage for admin rule actions and tenant rate-limit visibility.

## Iteration 3 — Contract closure

- Added Phase18 spec, intent, design, UI element contract, test matrix, and evidence files.
- Fixed UI contract inventory and Playwright execution evidence.
- Removed duplicate unit-level disable assertion after Chrome Playwright and backend service/controller coverage already proved that behavior.

## Debugging note

The full frontend suite initially exposed test isolation leakage from the new Phase18 unit test because it mutated `apiClient.defaults.adapter`. The test was changed to module mocks so it no longer modifies global axios adapter state.
