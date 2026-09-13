# Issue 78 Complaint Layout UI Elements

This production-change addendum records the complaint page's card, field-grid,
action, and empty-table layout contract. The Phase 41 inventory retains
ownership of field values, validation, permissions, API effects, and feedback.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-complaints `/admin/complaints` | Existing Phase 41 complaint page access and mutation permissions | complaint intake, attribution and requirements, handling evidence, complaint list | four ordered cards; responsive field grids; registration action cell; fixed-layout seven-column data table | Existing complaint values and validation; 40 px ±2 px single-line controls and registration action | Existing complaint registration, handling, remediation, and recovery API effects remain unchanged | loading,error,empty,populated,success,disabled,desktop,narrow | admin-complaint-case-complaints-intake-card,admin-complaint-case-complaints-attribution-card,admin-complaint-case-complaints-evidence-card,admin-complaint-case-complaints-list-card,entity-form,form-actions,form-submit,data-table,table-empty | OBL-ISSUE-78-COMPLAINTS-LAYOUT,PROJECT-UI-CONTRACT | issue-78-complaints-layout | T-ISSUE-78-COMPLAINTS-LAYOUT:playwright | pw-issue-78-complaints-layout |
