# Phase 49 Iterations

## Iteration 1

- Added schema migration for termination request, participants and audits.
- Added backend service and controller.
- Added admin UI and Chrome Playwright script.
- Added backend service and migration tests.

## Verification fix

Initial targeted backend test failed because H2 returned more than one generated key. The insert now requests only `id`.
