# UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-number-attribution /admin/number-attribution | ADMIN/OPERATOR number-attribution:read | attribution lookup | Attribution page | mobile, carrier, prefix carrier, province, city, source, freshness, degraded flag | GET `/api/v1/console/number-attribution/lookup` | result, fallback source, error | admin-number-attribution-portability-attribution-page | OBL-F-5-7-A,REQ-F-5-7 | number-attribution-portability-01 | T-F-5-7-A:integration | pw-p19-attribution |
| admin-number-portability /admin/number-portability | ADMIN/OPERATOR number-attribution:portability | portability cache | Portability page | masked mobile, original/current carrier, source, freshness, state | GET/POST `/api/v1/console/number-attribution/portability` | protected cache saved, source retained | admin-number-attribution-portability-portability-page | OBL-F-5-7-B,REQ-F-5-7 | number-attribution-portability-02 | T-F-5-7-B:integration | pw-p19-portability |
| admin-prefixes /admin/prefixes | ADMIN/OPERATOR number-attribution:import | prefix import/version | Prefix management page | 3-to-7 digit prefix, carrier, province, city, version, update type, conflict count | POST `/api/v1/console/number-attribution/prefixes/import`; GET versions | import success, conflict evidence | admin-number-attribution-portability-prefixes-page | OBL-F-13-4-A,REQ-F-13-4 | number-attribution-portability-03 | T-F-13-4-A:playwright | pw-p19-prefixes |

## Runtime-only controls

| Control | Purpose |
| --- | --- |
| `NumberAttributionService.lookup(..., forceProviderFailure=true)` | Covers `OBL-F-5-7-C` deterministic degraded prefix fallback. |
| `mobile_portability.mobile_hash` | Protects portability numbers from plaintext persistence. |
