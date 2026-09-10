# Phase 42 UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
|---|---|---|---|---|---|---|---|---|---|---|---|
| admin-tenant-risk-rules `/admin/tenant-risk` | ADMIN/OPERATOR/FINANCE | rule workspace | page | metric, threshold, duration, action, notify targets, source snapshot | GET/POST `/console/tenant-risk/rules`, POST `/console/tenant-risk/evaluate` | loading/error/saved/evaluated | admin-tenant-risk-rules-page | OBL-F-9-5-A, OBL-FLOW-12-1-RISK-PAUSE; REQ-F-9-5, PROJECT-CHAPTER-12-FLOW | tenant-risk-auto-pause-01, tenant-risk-auto-pause-04 | T-F-9-5-A:playwright, T-FLOW-12-1-RISK-PAUSE:uat | pw-p42-rules, pw-p42-risk-flow |
| admin-tenant-risk-rules `/admin/tenant-risk` | ADMIN/OPERATOR/FINANCE | pause detail | card/table | episode id, tenant, metric, source, numerator, denominator, quality, rate, action, status | GET `/console/tenant-risk/episodes`; auto-pause evidence is read-only on page | COMPLETE/UNKNOWN, ACTIVE/PAUSED/RESOLVED | admin-tenant-risk-tenant-risk-pause-detail | OBL-F-9-5-C, OBL-STATE-TENANT-RISK-FREEZE; REQ-F-9-5, PROJECT-STATE-MACHINE | tenant-risk-auto-pause-02, tenant-risk-auto-pause-03 | T-F-9-5-C:fault, T-STATE-TENANT-RISK-FREEZE:integration | pw-p42-pause, pw-p42-freeze |
| admin-tenant-risk-rules `/admin/tenant-risk` | ADMIN/OPERATOR/FINANCE | recovery | form/action | review id, recovery note, episode id | POST `/console/tenant-risk/episodes/{id}/recover` | disabled without episode, recovered/error | admin-tenant-risk-tenant-risk-recovery | OBL-F-9-5-D, OBL-STATE-TENANT-RESTORE; REQ-F-9-5, PROJECT-STATE-MACHINE | tenant-risk-auto-pause-02, tenant-risk-auto-pause-03 | T-F-9-5-D:playwright, T-STATE-TENANT-RESTORE:playwright | pw-p42-recovery, pw-p42-restore |
| admin-tenant-risk-rules `/admin/tenant-risk` | ADMIN/OPERATOR/FINANCE | complaint-ratio handoff | source link/indicator | tenant complaint ratio breach source id | Source-linked handoff into tenant risk episode workflow | route-level evidence retained | admin-complaint-ratio-dashboard-complaint-ratio-tenant | OBL-FLOW-12-2-COMPLAINT-TENANT; PROJECT-CHAPTER-12-FLOW | tenant-risk-auto-pause-04 | T-FLOW-12-2-COMPLAINT-TENANT:uat | pw-p42-complaint-tenant |

Implementation-only selectors:

- `admin-tenant-risk-nav-menu`: sidebar navigation.
- `admin-tenant-risk-message`: success feedback.
- `admin-tenant-risk-error`: mutation error feedback.
- `admin-tenant-risk-refresh`: page refresh action.
- `admin-tenant-risk-rule-panel`: threshold configuration card.
- `admin-tenant-risk-tenant-id`: tenant id input.
- `admin-tenant-risk-rule-name`: rule name input.
- `admin-tenant-risk-metric`: metric selector.
- `admin-tenant-risk-threshold`: threshold input.
- `admin-tenant-risk-duration`: duration input.
- `admin-tenant-risk-action`: notify or auto-pause selector.
- `admin-tenant-risk-notify-targets`: notification targets input.
- `admin-tenant-risk-rule-save`: save rule action.
- `admin-tenant-risk-source-evaluation`: source snapshot evaluation card.
- `admin-tenant-risk-numerator`: numerator input.
- `admin-tenant-risk-denominator`: denominator input.
- `admin-tenant-risk-window`: source window input.
- `admin-tenant-risk-source-key`: source key input.
- `admin-tenant-risk-source-registry`: source registry input.
- `admin-tenant-risk-evaluate`: evaluation action.
- `admin-tenant-risk-current-rules`: current rules section.
- `admin-tenant-risk-rules-table`: rule table.
- `admin-tenant-risk-rule-row`: rule row.
- `admin-tenant-risk-data-quality`: episode data quality indicator.
- `admin-tenant-risk-rate`: episode rate indicator, renders `未知` when source is incomplete.
- `admin-tenant-risk-episode-table`: episode table.
- `admin-tenant-risk-episode-row`: episode row.
- `admin-tenant-risk-source-snapshot`: immutable source snapshot.
- `admin-tenant-risk-recovery-review-id`: recovery review id input.
- `admin-tenant-risk-recovery-note`: recovery note input.
- `admin-tenant-risk-recover`: recovery action.
