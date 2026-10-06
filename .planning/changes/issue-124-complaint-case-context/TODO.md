# Issue 124 TODO

- [x] Freeze the issue contract, UI inventory, test matrix, and independent entry review.
- [x] Add the registered append-only event migration, honest historical backfill, complete per-case timeline, and minimal reference-option read models.
- [x] Make accept, handle, close, recovery, and remediation/state coordination atomic against concurrent state changes.
- [x] Prove remediation-versus-close and recovery-versus-new-remediation races with deterministic transaction integration tests.
- [x] Persist sanitized failed-remediation evidence in the still-locking command transaction after the nested resource transaction exits, with a Spring transaction integration test.
- [x] Replace page-level handling drafts with a selected-case workspace and contextual action dialog.
- [x] Remove invented intake defaults and use controlled/source-backed choices.
- [x] Add loading, empty, error, retry, stale-state, invalid-action, and duplicate-submit behavior.
- [x] Replace static complaint browser mocks with a stateful create-to-close flow and retain Issue 78 geometry coverage.
- [ ] Pass focused Java, React, and local Google Chrome tests.
- [x] Document the initial cutover and downgrade rule that freezes complaint mutations until every active application instance writes events.
- [ ] Pass full backend/frontend/build/planning/diff gates or record a verified external boundary.
- [x] Complete implementation and pre-push reviews with no remaining BLOCKER/HIGH/MEDIUM finding.
- [ ] Record branch, commit, pull request, CI, and merge evidence.
