# Shared component patterns

The files under `assets/components/` are accessible HTML starting points. Copy
their structure into the phase prototype and replace example text/data with the
active module contract. React production code must convert repeated structures
into shared components under `web/src/` while preserving final selectors and
semantics.

Required shared families include shell/sidebar/header, profile popover, table
toolbar, filters, selection/bulk actions, forms and validation, loading/empty/
error states, destructive confirmation, toast/status feedback, drawers, and
accessible chart alternatives. Include only capabilities required by the PRD or
an explicitly recorded implicit-requirement decision.

Every interactive or asserted element receives a documented `data-testid`.
Template `shared-*` IDs demonstrate stable shared-component naming; page-owned
controls must use the full portal/module/page namespace from UI-ELEMENTS.
Elements use semantic HTML (`button`, `a`, `label`, `input`, `dialog` roles),
accessible names, keyboard operation, and focus behavior.

For SMS operations, design explicitly for masked phone numbers and message
content, task/channel state, async progress/partial failure, receipt/error code
details, tenant scope, billing impact, audit history, retry/cancel permissions,
and correlation/copy actions when those obligations belong to the phase.
