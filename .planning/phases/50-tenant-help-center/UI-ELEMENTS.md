# Phase 50 UI Elements

## Validator-bound obligation rows

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tenant-help-guide `/tenant/help/guide` | TENANT_ADMIN/TENANT_USER/TENANT_DEV | guide page | page | content version, search query, article category/steps/permissions | Search filters bundled guide articles without backend mutation | empty/query/results | tenant-tenant-help-guide-page | OBL-IA-TENANT-HELP-GUIDE; PROJECT-TENANT-HELP | tenant-help-center-01 | T-IA-TENANT-HELP-GUIDE:playwright | pw-p50-guide |
| tenant-help-api-docs `/tenant/help/api` | TENANT_ADMIN/TENANT_USER/TENANT_DEV | API docs page | page | endpoint, version, HMAC headers, request/response fields, errors, example | Documents implemented `/api/v1/sms/send` contract | readable/versioned | tenant-tenant-help-api-docs-page | OBL-IA-TENANT-HELP-API; PROJECT-TENANT-HELP; REQ-F-6-1 | tenant-help-center-02 | T-IA-TENANT-HELP-API:playwright | pw-p50-api |
| tenant-help-customer-service `/tenant/help/customer-service` | TENANT_ADMIN/TENANT_USER/TENANT_DEV | customer service page | page | availability, destination, fallback text | Local fallback action reveals offline contact instructions | available/fallback-visible | tenant-tenant-help-customer-service-page | OBL-IA-TENANT-HELP-SERVICE; PROJECT-TENANT-HELP | tenant-help-center-03 | T-IA-TENANT-HELP-SERVICE:playwright | pw-p50-service |

## Supplemental selector inventory

These selectors are part of the Phase 50 UI test contract, but the three page-level rows above remain the canonical obligation-to-Playwright mapping.

| Route | Selector | Element | Action / assertion |
| --- | --- | --- | --- |
| `/tenant/help/guide` | tenant-tenant-help-content-version | content version badge | Assert versioned content is visible. |
| `/tenant/help/guide` | tenant-tenant-help-guide-tab | guide tab | Navigate to guide page. |
| `/tenant/help/guide` | tenant-tenant-help-guide-search-region | search card | Scope guide search assertions. |
| `/tenant/help/guide` | tenant-tenant-help-guide-search-input | search input | Type query text and filter local guide articles. |
| `/tenant/help/guide` | tenant-tenant-help-guide-results | results region | Assert filtered guide result text. |
| `/tenant/help/guide` | tenant-tenant-help-guide-article | guide article card | Assert article title/category/steps/permissions. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-tab | API docs tab | Navigate to API docs page. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-contract | API docs card | Scope implemented API contract assertions. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-endpoint | endpoint label | Assert `POST /api/v1/sms/send` is documented. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-auth-headers | auth header list | Assert HMAC headers and Unix-second timestamp rule. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-request-table | request table | Assert implemented request fields. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-response-table | response table | Assert implemented response fields. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-error-table | error table | Assert representative implemented error codes. |
| `/tenant/help/api` | tenant-tenant-help-api-docs-example | JSON example | Assert request example is visible. |
| `/tenant/help/customer-service` | tenant-tenant-help-customer-service-tab | customer-service tab | Navigate to customer-service page. |
| `/tenant/help/customer-service` | tenant-tenant-help-customer-service-entry | customer-service card | Scope contact assertions. |
| `/tenant/help/customer-service` | tenant-tenant-help-customer-service-availability | availability text | Assert current availability is visible. |
| `/tenant/help/customer-service` | tenant-tenant-help-customer-service-destination | destination text | Assert contact destination is visible. |
| `/tenant/help/customer-service` | tenant-tenant-help-customer-service-fallback-action | fallback button | Click to reveal fallback instructions. |
| `/tenant/help/customer-service` | tenant-tenant-help-customer-service-fallback | fallback status | Assert traceId-based fallback instructions. |
