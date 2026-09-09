# Design

Runtime content safety is placed inside `RoutingEngine` after blacklist/risk checks and before frequency/channel selection. `MessageSubmitService` already passes the Phase 13 final rendered content into `RoutingContext`; Phase 17 makes the checker authoritative for that content.

Policy precedence:

1. Scope: product, tenant, global.
2. Level: high, medium, low.
3. Action: block, replace, alert.
4. Stable id order.

Delete is soft-disable. This keeps policy rows available for historical hit replay and avoids orphaning `content_safety_hits`.
