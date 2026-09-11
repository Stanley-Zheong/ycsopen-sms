# Phase 36 Spec

Goal: tenants submit complete recharge evidence and finance approves or rejects the request without duplicate balance credit.

Functional contract:

- Tenant submits positive `amountMil`, supported recharge method, transaction reference, and evidence text.
- Transaction reference is not persisted in plaintext; the system stores SHA-256 hash for uniqueness and a masked display value.
- Tenant sees submitted records and processing status.
- Finance/Admin reviews pending records with a required reason.
- Approval credits the tenant prepaid balance once and writes a `RECHARGE_APPROVE` balance audit entry.
- Rejection records reason and does not create or mutate prepaid balance.
- Re-reviewing an already processed request returns the existing state without applying another credit.
