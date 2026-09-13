# Phase 39 Decisions

- D1: Do not build a background financial warehouse in Phase39. Live queries are enough for the scoped UI and avoid duplicate state.
- D2: Use ADMIN/FINANCE role authorization only. Cost/profit is finance-sensitive, so operator access is not added in this phase.
- D3: Use local Google Chrome as the only browser automation target.
