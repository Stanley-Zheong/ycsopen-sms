# Phase 36 Design

Backend:

- `tenant_recharge_records` stores request/review state and masked/hash transaction reference evidence.
- `TenantRechargeService` owns submission, tenant history, review queue, and review state transitions.
- `TrialPrepaidLedgerService.creditRecharge` owns actual prepaid account mutation and balance audit append.

Frontend:

- Tenant page: form plus history table.
- Admin page: review filter, reason input, pending table, approve/reject actions.

Validation:

- Unit tests cover state and money effects.
- Playwright covers both PRD-owned pages on local Chrome.
