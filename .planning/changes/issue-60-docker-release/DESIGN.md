# Issue 60 Docker Release Design

## Behavior and invariant

Behavior `issue-60-docker-release-01` owns the repository's development release
composition. A release build uses the checked-out Core and Web sources, starts
against a new or previously initialized MySQL volume, exposes configurable Web
and Core host ports, and reports the caller-supplied build commit from both
runtime surfaces.

The build commit is an explicit input. Callers set `BUILD_COMMIT` to the Git
commit being built. Core exposes it through `/actuator/info`; Web embeds it in
the generated `index.html`. A missing input is reported as `unknown` and cannot
satisfy release acceptance.

Schema migrations: none. Existing versioned Flyway files remain immutable. The
change disables Flyway placeholder substitution for message-template literals
and adds repeatable, development-release seed data in a separate Flyway
location. The seed preserves existing rows and can run repeatedly without
creating duplicates.

## Scope and ownership

| Rule | Owner | Delivery surface | Verification surface |
| --- | --- | --- | --- |
| Core image is built from the checked-out repository | `core/Dockerfile` | Compose `core` service | image build and `/actuator/info` |
| Web image is built from the checked-out repository | `web/Dockerfile` | Compose `web` service | `/login` and embedded commit metadata |
| Service order and configurable ports | `compose.yaml` | Docker Compose | fresh and repeated Compose start |
| Migration account creation on a new volume | `deploy/mysql/10-create-migration-user.sh` | MySQL initialization | Core startup and Flyway history |
| Development fixtures remain non-empty and idempotent | `db/releasemigration` | Flyway repeatable migration | authenticated API and row-count assertions |
| Dashboard root is stable for browser acceptance | `DashboardPage.tsx` | `/admin/dashboard` | Chrome Playwright selector assertion |

## State flow

1. Compose builds Core and Web with one `BUILD_COMMIT` input.
2. MySQL initializes the application and migration users on a new volume.
3. Core waits for MySQL and Redis health, then Flyway applies all versioned
   migrations followed by development repeatable migrations.
4. The Web proxy serves the SPA and forwards `/api` requests to Core.
5. Chrome loads `/login`, authenticates with the development administrator,
   checks dashboard data and seeded resources, and compares both reported
   commit values with `BUILD_COMMIT`.
6. A second isolated project first creates the pre-Issue-60 Flyway baseline,
   then force-recreates the current Core/Web containers against that volume.
   An operator-owned row must survive, and a further forced restart must leave
   each release fixture at one row.

## Error and compatibility behavior

- Core startup remains failed when Flyway validation or migration fails. Logs
  retain the original Flyway and Spring Boot root cause.
- The MySQL initializer runs only for a new volume. Existing installations must
  already have the documented migration account; Compose never repairs or
  deletes `flyway_schema_history`.
- Release verification generates two unoverrideable, unused Compose project
  names and deletes only resources bearing those Compose project labels.
- Published ports bind to `127.0.0.1` by default. Nginx forwards only the
  required Actuator health and info endpoints.
- The release seed uses stable business keys and does not overwrite
  operator-managed rows.
- Docker release packaging does not change production credential management,
  external channel connectivity, or Phase 56 cross-protocol acceptance.

## Validation ladder

- Unit: runtime metadata configuration, repeatable seed idempotency, dashboard
  selector.
- Repository: Java 21 tests, Node 20 install/tests/build, planning validators.
- Integration: fresh MySQL volume, Core health, Flyway history, repeated start.
- Browser/API: Google Chrome Playwright login, dashboard, API data, fixture and
  commit assertions.
- Operational: Core log allow/deny patterns and Compose cleanup evidence.
