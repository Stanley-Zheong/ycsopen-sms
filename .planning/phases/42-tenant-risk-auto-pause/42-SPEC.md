# Phase 42 Spec

Goal: incomplete source data must display `UNKNOWN` instead of a misleading safe rate.

Functional contract:

- Operators configure tenant rules for `COMPLAINT_RATE`, `FAILURE_RATE`, and `UNSUBSCRIBE_RATE`.
- Each rule records threshold, duration window, action (`NOTIFY` or `AUTO_SUSPEND`), targets, status, and actor.
- Evaluation consumes a source snapshot containing numerator, denominator, window, source registry, and source key.
- Denominator `0` creates an `UNKNOWN` episode with no computed rate and no pause.
- A sustained threshold breach creates exactly one episode for the source key.
- `AUTO_SUSPEND` changes the tenant lifecycle to `FROZEN`; existing tenant eligibility fences then reject new sends while read/query/reconciliation APIs remain untouched.
- Recovery requires an operator review id and restores the pre-pause lifecycle recorded on the episode.
- Warning, pause, and recovery retain source evidence through `tenant_risk_episodes.source_snapshot`.

Non-goals:

- No generic scheduler framework.
- No broad browser matrix; Chrome-only verification.
- No duplicate alert transport implementation.
