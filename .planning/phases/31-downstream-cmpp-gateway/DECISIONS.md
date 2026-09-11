# Phase 31 Decisions

- Implement a transport-independent gateway session core instead of adding a production TCP listener in this phase.
- Reuse Phase30 PDU/auth primitives where appropriate; use a Phase31 test body codec for downstream submit binding.
- Route every accepted downstream CMPP `SUBMIT` through `CmppDownstreamAcceptancePort`, with `CmppMessageSubmitAcceptanceAdapter` calling `MessageSubmitService` so CMPP cannot bypass existing eligibility, template, routing, billing, and idempotency rules.
- Pass CMPP service ID, product code, and credential ID into the shared submit request template parameters with `_cmpp_*` keys until a richer product binding column exists.
- Enforce IP whitelist, max connection count, TPS, window, and revocation in the session layer because those decisions must occur before any task or charge can be created.
- Keep unacknowledged delivery reports in `CmppDownstreamSessionRegistry`, keyed by tenant, so reports can survive a socket/session object replacement. Production persistence can replace the in-memory registry behind the same behavior later.
