# Phase 22 UI Spec

## Pages

- `/admin/tenant-trial-contracts`: platform operator/admin configures tenant trial quota and validity.
- `/admin/balance-audit`: platform operator/admin/finance views immutable balance audit rows.
- `/tenant/overview`: tenant views trial status, remaining quota, validity, and conversion action.
- `/tenant/consumption-ledger`: tenant views immutable consumption ledger rows and business-type filter.
- `/tenant/qualification`: existing qualification page remains the approved-review state evidence for entering trial.

## Chrome-only validation

The production browser contract is verified with `local-google-chrome` only. No Edge/Safari/browser download matrix is part of this phase.
