# Issue 71 Query-Panel UI Elements

This addendum records the responsive action-position contract for the shared
query panel. Existing page inventories retain ownership of field values,
permissions, API effects, results, and error states.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| shared-query-panel, represented by admin-tenants `/admin/tenants` | Existing read permission for each owning page | query field and action disclosure | responsive field grid with trailing search/reset action group | Existing page-owned field values and validation; 3/2/1 field columns; intrinsic non-wrapping action buttons | Search and reset preserve each page's existing handler and API effect; this contract changes layout only | expanded,collapsed,single-row,multi-row,narrow-next-row,keyboard-focus,disabled | query-panel,query-panel-fields,query-panel-toggle,query-input-keyword,query-input-operating-status,query-submit,query-reset | OBL-ISSUE-71-QUERY-ACTION-POSITION,PROJECT-UI-CONTRACT | issue-71-query-action-position | T-ISSUE-71-QUERY-ACTION-POSITION:playwright | pw-issue-71-query-action-position |
