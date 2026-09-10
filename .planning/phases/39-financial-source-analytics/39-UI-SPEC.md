# Phase 39 UI Spec

Routes:

- `/admin/finance`
- `/admin/statistics`

Pages:

- `admin-financial-analytics`: finance cost/revenue/profit summary.
- `admin-statistics-channel`: channel cost/revenue/profit summary.

Primary elements:

- Filter fields for period, tenant ID, and channel ID.
- Summary table showing source count, billable count, cost, revenue, profit, price version, and freshness.
- Channel table showing channel, source count, final success count, cost, revenue, and profit.
- Drilldown action and source table showing message ID, final state, cost, revenue, profit, formula, price version, and freshness.

Browser verification: local Google Chrome only.
