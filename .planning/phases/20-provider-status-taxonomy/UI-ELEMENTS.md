# UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-status-codes /admin/status-codes | ADMIN/OPERATOR provider-status:read/provider-status:import/provider-status:export | status mapping | Status-code taxonomy page | provider, protocol, provider code, platform category, final, billable, retryable, severity, advice | GET mappings; POST import; GET normalize; POST export-request | import success, normalize result, export request feedback | admin-provider-status-taxonomy-status-codes-page | OBL-PROVIDER-TAXONOMY-001,OBL-F-13-3-A,REQ-F-13-3 | provider-status-taxonomy-01 | T-PROVIDER-TAXONOMY-001:playwright,T-F-13-3-A:playwright | pw-p20-map,pw-p20-crud |
| admin-status-codes /admin/status-codes | ADMIN/OPERATOR provider-status:read | version history | Version history table | version, status, source, conflict count, effective date | GET `/api/v1/console/provider-status/versions` | active/superseded history visible | admin-provider-status-status-codes-version-history | OBL-PROVIDER-TAXONOMY-002,REQ-F-13-3 | provider-status-taxonomy-01 | T-PROVIDER-TAXONOMY-002:database | pw-p20-version |

## Runtime-only controls

| Control | Purpose |
| --- | --- |
| `ProviderStatusTaxonomyService.normalize(...)` unknown branch | Covers `OBL-PROVIDER-TAXONOMY-003` safe unknown-code policy. |
| `ProviderStatusTaxonomyPort` | Covers `OBL-PROVIDER-TAXONOMY-004` shared HTTP/CMPP consumer contract. |

