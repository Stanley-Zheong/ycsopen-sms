# Phase 39 Spec

Goal: displayed channel and financial cost/revenue/profit must reconcile to message source records, latest receipt state, active tenant price version, and billable final quantity.

Owned obligations:

- OBL-F-4-5-A
- OBL-F-8-8-A
- OBL-F-8-8-B

Functional contract:

- Finance/admin can query period, tenant, and channel summaries.
- Cost is computed from `message_tasks.cost`.
- Revenue is computed from billable latest final states and active `tenant_price_books.unit_price_mil`.
- Profit is revenue minus provider cost.
- Drilldown exposes immutable source rows, formula, formula version, price version, and freshness.

Out of scope:

- Fee threshold warnings.
- Credit blocking.
- Generic custom reports.
- Background financial warehouse.
