# Spirit 05: Frontend Release Acceptance Spec

## Intent

Make frontend release evidence repeatable so Docker test environments, default accounts, build commit identity, migrations, and Chrome acceptance reports match the code being reviewed.

## Scope

### In

- Docker Web/Core version identity visible in deployed frontend.
- Default test environment account and login verification.
- Fresh database migration and seeded data expectations.
- Chrome Playwright release reports for selected frontend spirits.
- PR release evidence template.

### Out

- Production hosting infrastructure.
- Non-Chrome browser support.
- New backend business behavior unless required by release verification.

## Behavior

| Behavior ID | Required behavior | Observable acceptance |
|---|---|---|
| FE-SPIRIT-05-IDENTITY | Docker Web and Core expose the reviewed commit identity and health status. | Release verification records commit, health endpoint, and frontend meta build commit. |
| FE-SPIRIT-05-LOGIN | Default test account login works in the Docker environment or a documented seed boundary explains why it cannot. | Chrome or API login verifies the default account without exposing secrets beyond approved test credentials. |
| FE-SPIRIT-05-REPORTS | Frontend release acceptance emits lane-specific reports for fresh, upgrade, and restart checks when release behavior changes. | Report paths and checksums are recorded in quality evidence. |

## Remaining TODO

- [ ] Decide whether every frontend spirit requires Docker release verification or only release-sensitive changes.
- [ ] Add a PR evidence template after product review.
- [ ] Record final verification commands in `QUALITY-GATEWAY.md`.
