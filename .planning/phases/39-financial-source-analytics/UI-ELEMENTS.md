# Phase 39 UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-statistics-channel `/admin/statistics` | ADMIN/FINANCE | channel summary | page/table | channel, source count, final success, cost, revenue, profit | GET `/console/financial/analytics` filtered by period/channel | loading/error/empty/data | admin-financial-source-channel-statistics-page | OBL-F-4-5-A; REQ-F-4-5 | financial-source-analytics-02 | T-F-4-5-A:integration | pw-p39-channel-statistics |
| admin-financial-analytics `/admin/finance` | ADMIN/FINANCE | finance summary | page/table | tenant, channel, period, source count, billable count, cost, revenue, profit, price version, freshness | GET `/console/financial/analytics` | loading/error/empty/data | admin-financial-source-financial-analytics-page | OBL-F-8-8-A; REQ-F-8-8 | financial-source-analytics-01 | T-F-8-8-A:integration | pw-p39-finance-analytics |
| admin-financial-analytics `/admin/finance` | ADMIN/FINANCE | source drilldown | button/table | message ID, final status, cost, revenue, profit, formula, price version, freshness | GET `/console/financial/analytics/drilldown` | drilldown panel opens with source rows | admin-financial-source-financial-analytics-drilldown | OBL-F-8-8-B; REQ-F-8-8 | financial-source-analytics-01 | T-F-8-8-B:playwright | pw-p39-drilldown |

## Supplemental shared elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-financial-analytics `/admin/finance` | ADMIN/FINANCE | filters | date/input/button | startDate, endDate, tenantId, channelId | applies query filters | refreshed table | admin-financial-source-financial-analytics-apply | OBL-F-8-8-A; REQ-F-8-8 | financial-source-analytics-01 | T-F-8-8-A:integration | pw-p39-finance-analytics |
