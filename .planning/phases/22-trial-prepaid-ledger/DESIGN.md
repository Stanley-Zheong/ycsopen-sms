# Phase 22 Design

## Backend

- `trial_accounts` stores current trial status, quota, validity and version.
- `trial_consumption_ledger` records trial consumption and freeze facts, keyed by tenant and message reference for idempotency.
- `prepaid_accounts` stores current balance/frozen amount and version.
- `prepaid_ledger` records per-business-document prepaid reservation/confirmation/reversal state.
- `balance_audit_entries` records append-only balance mutations with before/after snapshots.
- `trial_conversion_requests` records tenant conversion requests from eligible trial states.

## Frontend

- Tenant overview shows trial status, quota, validity, manual trial consume action, and conversion request action.
- Tenant consumption ledger shows immutable rows and filter controls.
- Admin trial/prepaid page provides trial quota/validity configuration and balance audit viewing.
