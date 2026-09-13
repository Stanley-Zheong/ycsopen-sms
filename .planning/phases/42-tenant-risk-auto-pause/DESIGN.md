# Phase 42 Design

Backend:

- `V5100__tenant_risk_auto_pause.sql` expands `tenant_alert_rules` and creates `tenant_risk_episodes`.
- `TenantRiskAutoPauseService` validates rules/evaluations, computes rates, writes episodes, creates alert evidence, pauses tenants, and handles recovery.
- `TenantRiskAutoPauseController` exposes console endpoints under `/api/v1/console/tenant-risk`.

Frontend:

- `tenantRiskAutoPauseApi.ts` wraps rules, evaluate, episodes, and recovery endpoints.
- `AdminTenantRiskPage.tsx` renders the complete operator workflow on `/admin/tenant-risk`.
- `AdminLayout.tsx` exposes the navigation item for authorized platform users.

Data-quality design:

- `denominator <= 0` records `data_quality=UNKNOWN` and `rate=NULL`.
- UI renders `未知` when `dataQuality=UNKNOWN` or `rate` is null.

Pause design:

- Auto-pause writes `status=PAUSED` and updates tenant lifecycle to `FROZEN`.
- The existing tenant eligibility policy rejects future submissions.
- Recovery requires `recovery_review_id` and restores `before_lifecycle_status`.
