# Phase 5 Context

## Inputs

- Phase 1 provides executable Maven, npm, and local-Google-Chrome verification commands.
- Phase 2 provides the desktop Admin shell, token system, stable UI registry rules, and ycsan visual baseline.
- Phase 3 provides protected-field encryption used for platform-account phone storage.
- The existing Spring Boot application, React console, `users`, `roles`, `permissions`, `user_roles`, and `role_permissions` structures are the integration baseline.

## Module boundary

Phase 5 owns platform identity and RBAC only: login, current-session enforcement, platform accounts, platform roles, four permission granularities, login history, unusual-login handoff, and the safe HTTP-500 console state. Tenant subaccounts, privileged plaintext reveal, notification routing policy, and operational audit search remain with their later roadmap owners.

## Execution rule

The phase closes only when every item in `TODO.md` has executable evidence, independent review contains no BLOCKER/HIGH, and the TODO set is empty. No schedule estimate or progress percentage is used.
