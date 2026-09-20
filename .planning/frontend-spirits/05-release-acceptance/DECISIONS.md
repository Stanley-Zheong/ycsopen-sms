# Spirit 05 Decisions

## DR-FE05-001: Release Evidence Is Part Of Frontend Acceptance

### Status
Accepted

### Context
Issue `#60` showed that local checks are not sufficient when Docker build identity, migrations, and fresh database startup differ from development mode.

### Decision
Frontend release-sensitive work must record Docker identity, health, login, and Chrome acceptance evidence before merge. Documentation-only frontend governance changes may record the boundary instead of running Docker.

### Consequences

- Release-sensitive frontend PRs carry heavier evidence requirements.
- Documentation-only changes can avoid unnecessary runtime checks but must state the boundary.
