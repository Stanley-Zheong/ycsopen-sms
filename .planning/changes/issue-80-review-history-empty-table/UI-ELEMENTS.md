# Issue 80 Review-History UI Elements

This production-change addendum records the query and structured empty-table
contract. Phase 15 retains ownership of permissions, API values, pagination,
populated rows, and immutable detail behavior.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-review-history `/admin/review-history` | ADMIN or OPERATOR with `review-history:read` | query panel, result table header, result body | eight-field shared query form and eight-column read-only data table | resource type, tenant ID, state, reviewer, risk, keyword, start/end time; decision, resource, tenant, state, reviewer, reason, time, action columns | Search applies the draft criteria to the existing protected review-history GET at page 0; reset restores empty criteria; table rows remain read-only | collapsed,expanded,loading,error,empty,populated,filtered,permission-denied; empty success displays one eight-column spanning row | query-panel,query-fields,query-actions,query-submit,query-reset,data-table,table-empty,admin-resource-review-history-review-filters,admin-resource-review-history-review-table | OBL-ISSUE-80-REVIEW-HISTORY-EMPTY,PROJECT-UI-CONTRACT | issue-80-review-history-empty-table | T-ISSUE-80-REVIEW-HISTORY-EMPTY:unit+playwright | pw-issue-80-review-history-empty-table |
