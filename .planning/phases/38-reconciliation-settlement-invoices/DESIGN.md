# Phase 38 Design

Backend:

- `statements` remains the statement aggregate and is extended with statement number, billing mode, price-book version, source snapshot, side confirmations, and resolution note.
- `statement_differences` records explicit disagreement evidence.
- `settlement_records` owns postpaid settlement state transitions.
- `invoices` is extended with statement linkage, request evidence, requester, issuer, and update trace.

Frontend:

- Finance uses one workbench page for statement generation, difference resolution, settlement, and invoice issue.
- Tenant uses one workbench page for statement confirmation, difference submission, and invoice request.

Permission:

- Reuse `trial-prepaid:read/write` plus role checks. This avoids a permission registry expansion in this phase.
