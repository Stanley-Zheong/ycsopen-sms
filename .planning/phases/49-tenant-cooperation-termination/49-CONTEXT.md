# Phase 49 Context

## Existing implementation reused

- `tenants.lifecycle_status` already includes `TERMINATED`.
- `TenantEligibilityPolicy.requireNewWorkAllowed` already rejects terminated tenants because TERMINATED is not an eligible lifecycle.
- Resource tables already exist for API keys, CMPP credentials, console users/sessions, callback config, bulk scheduled work, signatures, templates, statements, settlement records and archive manifests.
- Phase 49 therefore needs orchestration state and evidence, not another copy of every upstream resource module.

## Implementation boundary

The termination service records a request, computes clearance, requires administrator approval, then updates existing resource states in one transaction. Historical reads are retained through termination request detail, participant rows and audit rows.
