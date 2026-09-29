# Issue 87 UI Elements

| Surface | Region / element | State and format | Stable selector contract | Behavior |
|---|---|---|---|---|
| All 56 terminal `/admin/*` routes | Visible ordinary single-line input and single-value select | Existing value/validation; <=420px and <=30% at desktop; errors and loading do not change the cap | Existing page selectors; Admin scope is `shared-console-shell[data-console-kind="admin"]` | issue-87-form-control-width, issue-87-horizontal-containment |
| Seven representative forms and nested postpaid group | Query/form field grid | 4/3/2/1 columns; aligned wrapping; children stay inside their track; nested group spans its parent | `query-fields`, `admin-blacklist-risk-black-white-lists-form`, `admin-runtime-content-content-safety-form`, `admin-frequency-api-frequency-rules-form`, `admin-number-attribution-portability-prefixes-page`, `admin-uplink-normalization-uplinks-filter-tenant`, `admin-custom-report-custom-reports-builder`, `admin-contract-pricing-tenant-contract-postpaid-fields` | issue-87-four-column-layout |
| Page-owned and modal grids | Card/panel/dialog layout | Existing 2/3-column geometry remains independent of viewport-level field-grid rules | `.alert-engine-rule-grid`, `.retention-archive-grid`, `.tenant-termination-grid`, `.channel-configuration-grid`, `admin-channel-health-channel-pools-weight-editor` | issue-87-four-column-layout-regression |
| Route-wide and named static option evidence | Single-value select | Every option set that fits the cap receives its intrinsic width; named selectors guard stable representative content | All visible selects in the 56-route traversal; `admin-auditable-exemption-exemption-policy-type`, `admin-blacklist-risk-black-white-lists-type`, `admin-runtime-content-content-safety-category`, `admin-frequency-api-frequency-rules-type`, `admin-prefixes-update-type` | issue-87-select-option-fit |
| Channel-pool editor | Data-backed member select | Artificial long fixture exercises desktop 30% cap | `admin-channel-health-channel-pools-member-channel` | issue-87-select-option-fit |

All elements retain their existing page-owned action and API effect. This addendum changes geometry
only and introduces no orphan input.
