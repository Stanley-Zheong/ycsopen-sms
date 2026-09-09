# Phase 17 Context

Phase 17 owns PRD F-5.5 only: runtime content safety for final rendered SMS text. It depends on Phase 13 for canonical template/signature/variable rendering and Phase 16 for risk-control placement before task creation.

Scope is intentionally narrow:

- Admin manages sensitive word policies.
- Runtime checker scans final rendered content, including variable values.
- Actions are `BLOCK`, `REPLACE`, and `ALERT`.
- Evidence records matched policy, scope, action, result, and hit metrics.

Out of scope:

- Template resource review decisions already owned by Phase 13.
- Frequency/rate controls owned by Phase 18.
- Generic alert notification routing owned by Phase 35.
