# Issue 73 Primary Control Heights UI Elements

This production-change addendum records the shared initial-height contract for
ordinary console primary controls. The existing routing-policy page retains
ownership of values, validation, permissions, API calls, feedback, and layout.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| shared-primary-controls, represented by admin-routing-policy `/admin/routing-policy` | Existing admin routing-policy read permission | import and retry-policy form fields | ordinary single-line input, textarea, and single-value select | Existing page-owned version, CSV, and retry-category values and validation; 40 px initial height | Existing import and retry actions and API effects remain unchanged | default,focus,disabled,vertical-resize-for-textarea | admin-routing-circuit-routing-policy-version,admin-routing-circuit-routing-policy-import-input,admin-routing-circuit-routing-retry-category | OBL-ISSUE-73-PRIMARY-CONTROL-HEIGHT,PROJECT-UI-CONTRACT | issue-73-primary-control-height | T-ISSUE-73-CONTROL-HEIGHTS:playwright | pw-issue-73-primary-control-height |
