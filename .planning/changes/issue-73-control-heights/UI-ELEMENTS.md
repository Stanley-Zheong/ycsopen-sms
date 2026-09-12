# Issue 73 Primary Control Heights UI Elements

This production-change addendum records the shared initial-height contract for
ordinary console primary controls. The existing routing-policy page retains
ownership of values, validation, permissions, API calls, feedback, and layout.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| shared-primary-controls, represented by admin-routing-policy `/admin/routing-policy` and public registration `/tenant/register` | Existing admin routing-policy read permission; public registration does not require an authenticated role | import, retry-policy, and initial-administrator form fields | ordinary single-line input, textarea, and single-value select | Existing page-owned version, CSV, retry-category, and administrator-username values and validation; console controls have 40 px initial height and public inputs retain 40 px minimum height | Existing import, retry, and registration actions and API effects remain unchanged | default,focus,disabled,vertical-resize-for-textarea,public-registration | admin-routing-circuit-routing-policy-version,admin-routing-circuit-routing-policy-import-input,admin-routing-circuit-routing-retry-category,public-tenant-qualification-register-admin-username | OBL-ISSUE-73-PRIMARY-CONTROL-HEIGHT,PROJECT-UI-CONTRACT | issue-73-primary-control-height | T-ISSUE-73-CONTROL-HEIGHTS:playwright | pw-issue-73-primary-control-height |
