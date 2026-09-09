# Phase 06 Context

## Inputs

- Phase 3 owns encryption and redaction primitives.
- Phase 5 owns console JWT identity, current database RBAC, protected platform-account phone storage, login history, and anomaly handoff.
- Phase 2 owns the desktop shell and visual tokens.

## Constraints

- Java 21 and the existing Spring MVC/JdbcTemplate stack; no AOP dependency is added.
- Node.js 20+ and the existing React Query/React Router stack.
- Acceptance uses the locally installed Google Chrome at 1440x900 only.
- Sensitive values never enter URL parameters, browser persistence, audit request values, security-event summaries, or screenshots.
- Work remains on the Phase 06 branch until one reviewed phase commit is created and pushed.

## Existing seams to reuse

- `CorrelationIdFilter` supplies the trace identity.
- `JwtAccessVerifier` supplies current database authorities.
- `SecurityRedactionConverter` supplies denylist-based log redaction.
- `PlatformAccountPhoneStore` supplies encrypted persistence and masked reads.
- `LoginAnomalyService` supplies the existing unusual-login decision point.
