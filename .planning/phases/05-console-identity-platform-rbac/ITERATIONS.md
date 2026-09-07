# Phase 5 Iterations

## Authentication and authorization foundation

- Added JWT request enforcement for console APIs while preserving the separate SMS HMAC boundary.
- Added mutable account-state and database permission checks on authenticated requests.
- Added browser session expiry, persistence, logout, and platform/tenant route isolation.
- Added password-expiry, account field, role migration, account state transition, session revocation, correlation, and safe 500 primitives.
- Added JWT `jti`, durable `user_sessions`, revocation-aware request checks, `login_history`, account change history schema, and authenticated logout.
- Verification: focused backend suite 18/18 PASS; frontend unit suite 16/16 PASS; production frontend build PASS.

## Production identity slice

- Completed account CRUD, protected phone handling, password replacement, account state transitions, role CRUD/migration, grouped permission management, account overview, own/privileged login history, logout, and correlated safe-error presentation.
- Added separate BUTTON and API authorities while resolving every protected request from current database state.
- Added durable role mutation audit and retained revoked session records rather than deleting them.
- Added the desktop Chrome UI with the 19 cataloged page/element selectors and accessible modal focus/dirty-close behavior.

## Independent-review correction loop

- Committed rejected-login mutations outside rollback and serialized concurrent failures with a pessimistic user lock.
- Reused the Phase 3 canonical phone protection context for migration and live access.
- Cleared protected React Query state across subject/session changes and included the principal key in protected query keys.
- Split own login history from privileged all-user history; broad reads require both API and DATA authority unless the caller is ADMIN.
- Allowed the exact authenticated logout route for tenant and platform principals while retaining the platform-only default console gate.
- Serialized role assignment and deletion by locking all involved platform roles in stable ID order; a concurrent transaction test proves a newly assigned account is migrated rather than left dangling.
- Completed role audit snapshots, aligned account field widths/nullability with the schema, and made permission-save state survive/confirm the server refetch.
- Reclassified fixture-backed Playwright cases as UI contract coverage only. Backend persistence, authorization, crypto-context, and transaction claims are bound to Java evidence instead.
- Added a production-codec/JDBC phone round trip against the Phase 3 migration AAD and a proxied account-creation rollback test covering both the JPA account insert and protected-column update.
- Bounded public-login usernames before persistence and blocked self-edit of account identity/roles.
- Reused the Phase 01 disposable MySQL 8.4 fixture to execute a successful unusual login, session activity/revocation, login history, outbox persistence, and Flyway validation against V1/V1200-V1402.
- Closed the final Claude privilege-escalation finding by bounding delegated role permission, account-role assignment, role migration, and ADMIN account creation/promotion/demotion to the actor's live authority; non-ADMIN actors also cannot mutate their own role.

## Verification replay

- Backend: `mvn -f core/pom.xml test` — 457 tests, 0 failures, 0 errors, 18 infrastructure-gated skips.
- Real database: `mvn -f core/pom.xml -Pphase01-integration -Dtest=Phase05IdentityMySqlIntegrationTest test` — 1/1 PASS on disposable MySQL 8.4 with Flyway through V1402; owned Docker residuals 0.
- Frontend: 27/27 Vitest tests PASS; lint PASS; production build PASS.
- Installed Google Chrome: Phase 05 identity suite 19/19 PASS; complete web suite 33/33 PASS at 1440x900 with one worker.
- Planning contracts: 21/21 owned obligation evidence files parse as PASS; PRD trace validator PASS; production UI contract validator PASS.

`TODO.md` remains the completion authority and is closed only after independent and Claude reviews pass.
