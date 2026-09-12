# Issue 63 Control Sizing UI Elements

This production-change addendum refines the shared console control contract
from GitHub issue #63. It preserves every page-owned selector, API action,
permission, validation, and state contract. The query grid displays up to
three compact fields on one row above 1200 px, two fields from 901-1200 px,
and one field at 900 px or below; additional fields remain collapsible. A
single-line input and select share the 40 px console control height. Ordinary
action buttons use their label width plus the shared horizontal padding;
components that explicitly own a full-width interaction, including the login
submit button and sidebar controls, retain that width.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| shared-query-panel, represented by admin-tenants `/admin/tenants` | Existing page read permission | shared desktop query fields | single-line input and select controls | Existing page-owned values and validation; 40 px height and compact width | Existing query actions remain unchanged; three fields occupy one desktop row and four or more fields expose the existing collapse control | expanded,collapsed,focus,disabled,compact-viewport | query-panel,query-panel-fields,query-panel-toggle,query-input-keyword,query-input-verification-status,query-input-operating-status | OBL-ISSUE-63-QUERY-CONTROLS,PROJECT-UI-CONTRACT | issue-63-query-controls | T-ISSUE-63-CONTROL-SIZING:playwright | pw-issue-63-query-controls |
| shared-console-actions, represented by admin-routing-policy `/admin/routing-policy` | Existing page action permissions | shared ordinary action buttons | primary button with intrinsic label width and shared horizontal padding | Existing accessible label, disabled state, and action payload | Existing API effect remains unchanged; grid layout cannot stretch the button across its track | default,hover,focus,disabled,loading | admin-routing-circuit-routing-policy-import,admin-routing-circuit-routing-policy-simulate,admin-routing-circuit-routing-circuit-record,admin-routing-circuit-routing-retry-save | OBL-ISSUE-63-ACTION-BUTTONS,PROJECT-UI-CONTRACT | issue-63-action-buttons | T-ISSUE-63-CONTROL-SIZING:playwright | pw-issue-63-action-buttons |
