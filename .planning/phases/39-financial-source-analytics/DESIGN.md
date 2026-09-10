# Phase 39 Design

- Backend reads source tables live instead of introducing another aggregation table.
- The latest delivery report overrides task status for billable final state.
- Revenue uses active tenant contract price version and immutable price book unit price.
- The same React page serves `/admin/finance` and `/admin/statistics`; both finance and channel sections are visible.
