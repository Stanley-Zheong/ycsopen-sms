# Spirit 05 System Design

## Release Data Flow

Git commit identity flows into Web build metadata and Core image metadata. Docker Compose starts Web, Core, MySQL, and Redis with migrations applied before acceptance checks run.

## Command Flow

1. Build Web with reviewed commit identity.
2. Build Docker images without relying on stale local assets.
3. Start fresh or upgrade environment as named by the spirit.
4. Verify Core health, Web served commit, default account login, and selected route acceptance.
5. Store lane-specific reports and record command output boundaries.

## Failure Model

Credential helper, stale `web/dist`, unhealthy database, migration failure, or login failure blocks release acceptance until root cause is fixed or the PR explicitly records a non-release boundary.

## Verification Model

Release-sensitive frontend spirits run Docker and Chrome acceptance. Documentation-only or non-release UI changes state why Docker is not required.
