# Phase 40 UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
|---|---|---|---|---|---|---|---|---|---|---|---|
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | fee warning workspace | page | tenant, metric, thresholds, action, targets | GET/POST `/console/fee-warnings/rules`; POST `/console/fee-warnings/evaluate`; GET `/console/fee-warnings/episodes` | loading/error/saved/evaluated/data | admin-fee-warning-credit-page | OBL-F-8-10-A; REQ-F-8-10 | fee-warning-credit-enforcement-01 | T-F-8-10-A:playwright | pw-p40-threshold-rules |
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | targets | input | JSON-like channels and targets | saved with rule command | saved/error | admin-fee-warning-fee-warning-notification-targets | OBL-F-8-10-B; REQ-F-8-10 | fee-warning-credit-enforcement-01 | T-F-8-10-B:integration | pw-p40-notification-targets |
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | episode action | table/action | source amount, credit limit, ratio, action | approve manual episode through `/console/fee-warnings/episodes/{id}/approve` | pending/approved/blocked | admin-fee-warning-fee-warning-credit-action | OBL-F-8-2-B; REQ-F-8-2 | fee-warning-credit-enforcement-02 | T-F-8-2-B:integration | pw-p40-credit-action |
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | enforcement | cell | approval state | ingress blocks until approved or rejects when blocked | PENDING/APPROVED/BLOCKED | admin-fee-warning-fee-warning-enforcement-action | OBL-F-8-10-C; REQ-F-8-10 | fee-warning-credit-enforcement-02 | T-F-8-10-C:component | pw-p40-ingress-enforcement |
| tenant-fee-warning-overview `/tenant/balance` | TENANT_ADMIN/TENANT_USER/TENANT_DEV | warning overview | page/table | metric, source amount, threshold, action, delivery | GET `/tenant/fee-warnings/episodes` | loading/error/empty/data | tenant-fee-warning-overview-low-balance-warning | OBL-F-8-1-C; REQ-F-8-1 | fee-warning-credit-enforcement-01 | T-F-8-1-C:integration | pw-p40-tenant-low-balance |
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | lifecycle fee warning | button/action | warning rule and approval action | notify tenant, finance, operations; then approve | saved/approved | admin-fee-warning-fee-warning-rule-save | OBL-FLOW-12-1-FEE-WARNING; PROJECT-CHAPTER-12-FLOW | fee-warning-credit-enforcement-03 | T-FLOW-12-1-FEE-WARNING:uat | pw-p40-lifecycle-fee-warning |
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | finance supervision | source snapshot | source amount, credit limit, estimated amount, ratio | read traceable source before finance action | data | admin-fee-warning-source-snapshot | OBL-FLOW-12-2-FINANCE; PROJECT-CHAPTER-12-FLOW | fee-warning-credit-enforcement-03 | T-FLOW-12-2-FINANCE:uat | pw-p40-finance-supervision |

## Supplemental elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
|---|---|---|---|---|---|---|---|---|---|---|---|
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | evaluation | button | tenantId and estimatedAmountMil | evaluate sources | evaluated/error | admin-fee-warning-fee-warning-evaluate | OBL-F-8-10-C; REQ-F-8-10 | fee-warning-credit-enforcement-02 | T-F-8-10-C:component | pw-p40-ingress-enforcement |
| admin-fee-warning `/admin/fee/warning` | ADMIN/FINANCE | approval | button | episodeId and reason | approve manual episode | approved/error | admin-fee-warning-fee-warning-approve | OBL-F-8-2-B; REQ-F-8-2 | fee-warning-credit-enforcement-02 | T-F-8-2-B:integration | pw-p40-credit-action |
| tenant-fee-warning-overview `/tenant/balance` | TENANT_ADMIN/TENANT_USER/TENANT_DEV | delivery | evidence card | alertRecordId and deliveryState | read-only | data/empty | tenant-fee-warning-overview-delivery-evidence | OBL-F-8-1-C; REQ-F-8-1 | fee-warning-credit-enforcement-01 | T-F-8-1-C:integration | pw-p40-tenant-low-balance |

## Implementation-only selectors

- `admin-fee-warning-refresh`: refresh button, invalidates rules and episodes queries.
- `admin-fee-warning-message`: success status message after save/evaluate/approve.
- `admin-fee-warning-error`: mutation error message.
- `admin-fee-warning-rule-panel`: threshold-rule editor region.
- `admin-fee-warning-fee-warning-tenant-id`: tenant ID input.
- `admin-fee-warning-fee-warning-rule-name`: rule name input.
- `admin-fee-warning-fee-warning-metric`: metric selector.
- `admin-fee-warning-fee-warning-threshold`: threshold input.
- `admin-fee-warning-fee-warning-action`: enforcement action selector.
- `admin-fee-warning-fee-warning-notify-channels`: notification channel input.
- `admin-fee-warning-fee-warning-targets`: notification target input.
- `admin-fee-warning-current-rules`: current rule list region.
- `admin-fee-warning-rules-loading`: rule-list loading state.
- `admin-fee-warning-rules-error`: rule-list error state.
- `admin-fee-warning-rules-table`: current rule table.
- `admin-fee-warning-rule-row`: current rule table row.
- `admin-fee-warning-loading`: episode loading state.
- `admin-fee-warning-load-error`: episode load error state.
- `admin-fee-warning-fee-warning-approval-reason`: manual approval reason input.
- `admin-fee-warning-episode-table`: episode table.
- `admin-fee-warning-episode-row`: episode table row.
- `tenant-fee-warning-loading`: tenant warning loading state.
- `tenant-fee-warning-error`: tenant warning error state.
- `tenant-fee-warning-overview-page`: tenant warning overview page root.
- `tenant-fee-warning-overview-row`: tenant warning row.
