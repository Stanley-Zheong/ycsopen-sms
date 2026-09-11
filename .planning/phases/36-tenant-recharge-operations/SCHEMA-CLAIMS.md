# Phase 36 Schema Claims

- `tenant_recharge_records.transaction_ref_hash` is unique and stores the transaction reference digest.
- `tenant_recharge_records.transaction_ref_mask` is safe for UI display.
- `tenant_recharge_records.status` is the request lifecycle state: PENDING, APPROVED, REJECTED.
- Approved review writes one `balance_audit_entries` row with `mutation_type='RECHARGE_APPROVE'`.
