# Phase 36 UI Spec

Routes:

- `/tenant/recharge`: tenant recharge submission and history.
- `/admin/tenant-recharge-review`: finance/admin recharge review.

Primary elements:

- `tenant-recharge-operations-recharge-form`
- `tenant-recharge-operations-recharge-amount`
- `tenant-recharge-operations-recharge-method`
- `tenant-recharge-operations-recharge-transaction`
- `tenant-recharge-operations-recharge-evidence`
- `tenant-recharge-operations-recharge-submit`
- `tenant-recharge-operations-recharge-state`
- `admin-tenant-recharge-operations-review-table`
- `admin-tenant-recharge-operations-review-reason`
- `admin-tenant-recharge-operations-review-approve`
- `admin-tenant-recharge-operations-review-reject`

Design notes:

- Reuse existing card/table/sidebar style.
- Show only masked transaction reference in history/review tables.
- Keep approval/rejection inline; no modal is needed for this phase.
