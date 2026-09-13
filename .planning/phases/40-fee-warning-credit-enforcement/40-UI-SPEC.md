# Phase 40 UI Spec

Routes:

- `/admin/fee/warning`: finance/admin rule configuration, evaluation, episode history, delivery state, approval action.
- `/tenant/balance`: tenant-visible balance/fee warning overview and delivery evidence.

Chrome-only production validation uses `local-google-chrome`.

Design constraints:

- Reuse existing console card/table/status styles.
- Every actionable element has a stable `data-testid`.
- Inputs display raw configured metric/action/targets because they become automation fixtures.
