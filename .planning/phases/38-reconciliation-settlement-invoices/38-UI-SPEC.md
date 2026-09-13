# Phase 38 UI Spec

Routes:

- `/admin/reconciliation`: finance statement, difference, settlement, and invoice workbench.
- `/admin/settlements`: aliases the same finance workbench for IA compatibility.
- `/admin/invoices`: aliases the same finance workbench for IA compatibility.
- `/tenant/statements`: tenant statement confirmation, difference, and invoice request workbench.
- `/tenant/invoices`: aliases the tenant workbench for IA compatibility.

Primary test IDs:

- `admin-reconciliation-settlement-reconciliation-page`
- `admin-reconciliation-settlement-reconciliation-generate`
- `admin-reconciliation-settlement-reconciliation-resolve`
- `admin-reconciliation-settlement-settlements-page`
- `admin-reconciliation-settlement-settlements-start`
- `admin-reconciliation-settlement-settlements-complete`
- `admin-reconciliation-settlement-settlements-received`
- `admin-reconciliation-settlement-invoices-issue`
- `tenant-reconciliation-settlement-statements-page`
- `tenant-reconciliation-settlement-statements-confirm`
- `tenant-reconciliation-settlement-statements-difference`
- `tenant-reconciliation-settlement-invoices-page`
- `tenant-reconciliation-settlement-invoices-request`

No modal/drawer/floating control is required for Phase38. Tables and inline forms are sufficient.
