# Phase 25 TODO

- [x] Owner obligation set identified by `validate-prd-obligations --owner dispatch-task-migration-recovery`.
- [x] Recovery evidence schema added for dispatch recovery events and channel recovery tests.
- [x] READY/PENDING task migration moves task and outbox to one fallback channel.
- [x] Repeated migration is idempotent and does not reselect or duplicate.
- [x] CLAIMED provider-timeout outcome is quarantined from automatic migration or retry.
- [x] No-backup case records evidence and leaves the original task untouched.
- [x] FAILED retry creates a new protected PENDING task and READY outbox without overwriting the original attempt.
- [x] Paused channel resume requires successful recovery-test evidence.
- [x] Operator UI exposes migration, retry, failover refresh, recovery test, and resume controls with stable test IDs.
- [x] Unit, migration, local Chrome Playwright, PRD obligation, and UI contract evidence are captured.
- [x] Phase review is captured with no BLOCKING/HIGH finding.
- [x] Phase changes are ready for commit and push.
