# Decisions

| ID | Decision | Rationale |
| --- | --- | --- |
| P21-D01 | Provide a policy contract and simulator, not a full dispatch rewrite. | Keeps this phase focused and avoids coupling later task execution before its phase. |
| P21-D02 | Store decision history with policy version and circuit/retry snapshot. | Later details, analytics, and billing can audit the source policy used for a route. |
| P21-D03 | Use local Chrome only for UI verification. | Matches project browser boundary. |

