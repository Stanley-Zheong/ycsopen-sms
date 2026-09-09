# UI Elements

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-routing-policy /admin/routing-policy | ADMIN/OPERATOR routing-policy:read/routing-policy:import/routing-policy:write | routing authoring | Routing policy page | priority, condition type/value, target type/ref, weight, version | GET rules/versions; POST import | active rules and import feedback | admin-routing-circuit-routing-policy-page | OBL-F-5-8-A,OBL-FLOW-12-2-ROUTING,REQ-F-5-8,PROJECT-CHAPTER-12-FLOW | routing-circuit-policy-01,routing-circuit-policy-04 | T-F-5-8-A:playwright,T-FLOW-12-2-ROUTING:uat | pw-p21-author,pw-p21-flow |
| admin-routing-policy /admin/routing-policy | ADMIN/OPERATOR routing-policy:read | simulator | Routing simulator | tenant, carrier, prefix, content, normalized category | POST `/api/v1/console/routing-policy/simulate` | selected version, rule, target, explanation | admin-routing-circuit-routing-policy-simulator | OBL-F-5-8-B,REQ-F-5-8 | routing-circuit-policy-01 | T-F-5-8-B:unit | pw-p21-simulator |
| admin-routing-policy /admin/routing-policy | ADMIN/OPERATOR routing-policy:read/routing-policy:write | circuit state | Circuit table/action | channel, state, failure, success, latency, history | GET circuits; POST record circuit | closed/open state and history visible | admin-routing-circuit-routing-circuit-state | OBL-F-5-9-B,REQ-F-5-9 | routing-circuit-policy-02 | T-F-5-9-B:fault | pw-p21-circuit |
| admin-routing-policy /admin/routing-policy | ADMIN/OPERATOR routing-policy:read/routing-policy:write | retry policy | Retry rule editor | normalized category, retryable, delay, max attempts | POST `/api/v1/console/routing-policy/retry` | saved retry feedback | admin-routing-circuit-routing-retry-rules | OBL-F-5-10-A,REQ-F-5-10 | routing-circuit-policy-03 | T-F-5-10-A:playwright | pw-p21-retry |

## Runtime-only controls

| Control | Purpose |
| --- | --- |
| `RoutingCircuitPolicyService#simulate` | Covers `OBL-F-5-9-A` health/circuit-aware ineligible channel exclusion. |
| `V3000__routing_circuit_policy.sql` | Covers `OBL-DATA-10-4-ROUTING` versioned data storage. |

