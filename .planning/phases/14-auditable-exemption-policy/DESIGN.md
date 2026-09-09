# Design

## Backend

- `ExemptionPolicyService` owns policy creation, approval-state validation, preview evaluation, revocation, and audit append.
- `exempt_rules` remains the primary rule table and is expanded with explicit product/resource/scope/approval/version/revocation columns.
- `exempt_rule_history` records create/revoke decisions.
- `exempt_rule_usage_history` records every preview/evaluation result.

## Frontend

- `ExemptionPolicyPage` is a focused Admin page under `/admin/exemption/policy`.
- One form creates bounded exemptions.
- One preview panel explains why an exemption is or is not effective.
- One usage table exposes the audit trail for automation and review.
