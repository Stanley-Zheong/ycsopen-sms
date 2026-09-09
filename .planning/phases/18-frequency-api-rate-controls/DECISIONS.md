# Phase 18 Decisions

- Use Redis Lua `INCR` + `EXPIRE` as one atomic operation for fixed windows.
- Use window-bucket keys, not first-hit sliding TTL, so instances share the same second/minute/hour/day boundary.
- Use all ordered opaque mobile indexes as the mobile frequency identity; do not call `RoutingContext.getOpaqueMobileQueryValue()`.
- API key 429 is enforced in `MessageController` before `MessageSubmitService.submit(...)`, so no task or billing reservation can occur after limit rejection.
- DELAY/ALERT frequency actions record hit evidence; queue-worker execution is deferred to later bulk/scheduled phases.
