# Phase 07 Context

## Inputs

- Phase 02 supplies the approved desktop Admin shell, visual tokens, and interaction conventions.
- Phase 03 supplies secret-handling and redaction constraints.
- Phase 05 supplies JWT identity, database-backed current RBAC, and platform administrator boundaries.
- Phase 06 supplies structural operation audit for every console mutation.

## Constraints

- Java 21 with Spring MVC, JdbcTemplate, Flyway, and Jackson already in the repository.
- React, React Query, React Router, and the existing console API conventions.
- Locally installed Google Chrome at 1440x900 is the sole browser acceptance target.
- Configuration storage never contains raw secrets; secret-classified keys accept references such as `env:YCS_SMS_EXPORT_SIGNING_KEY` only.
- Runtime activation is local-process and atomic. Cross-node convergence belongs to a later operations phase.
- One reviewed phase commit is created only after the verified TODO set is empty.

## Existing seams to reuse

- `JwtAccessVerifier` and controller method security for current permissions.
- `OperationAuditInterceptor` for structural console audit.
- `AuthService` for the concrete login-threshold runtime consumer.
- `AdminLayout`, `PageGuard`, `httpClient`, and shared CSS primitives for the production page.
