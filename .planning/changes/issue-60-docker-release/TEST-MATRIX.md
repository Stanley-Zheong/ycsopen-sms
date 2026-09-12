# Issue 60 Docker Release Test Matrix

This matrix is a scoped production-change addendum. It does not relabel or
complete Phase 56. Runtime evidence is pending until the pull-request Docker
job runs on the delivered commit.

The first PR run proved image construction and all 45 fresh-volume migrations,
then exposed a fixed-width JPA/schema mismatch before Core health. That finding
is covered by `ReleaseSchemaCompatibilityTest`; every end-to-end row below
remains pending until the corrected head completes the Docker/Chrome job.

| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OBL-ISSUE-60-FRESH | GITHUB-ISSUE-60 | issue-60-docker-release-01 | T-ISSUE-60-FRESH:docker-playwright | pw-issue-60-docker-release | login `/login` | shared-auth-login-page | C-ISSUE-60-BROWSER | A new project and volume build current images, Core becomes healthy, and `/login` returns 200. | `BUILD_COMMIT=$(git rev-parse HEAD) ./scripts/verify-docker-release` | pending PR runtime |
| OBL-ISSUE-60-DASHBOARD | GITHUB-ISSUE-60 | issue-60-docker-release-01 | T-ISSUE-60-DASHBOARD:playwright | pw-issue-60-docker-release | admin-dashboard `/admin/dashboard` | admin-dashboard-page | C-ISSUE-60-BROWSER | Login exposes the dashboard root; both dashboard API requests return without 5xx. | `BUILD_COMMIT=$(git rev-parse HEAD) ./scripts/verify-docker-release` | pending PR runtime |
| OBL-ISSUE-60-SEED | GITHUB-ISSUE-60 | issue-60-docker-release-01 | T-ISSUE-60-SEED:playwright-mysql | pw-issue-60-docker-release | admin-dashboard `/admin/dashboard` | admin-dashboard-page | C-ISSUE-60-BROWSER | Authenticated APIs return the stable release channel, template, and prefix version; MySQL contains one row for each seed key. | `BUILD_COMMIT=$(git rev-parse HEAD) ./scripts/verify-docker-release` | pending PR runtime |
| OBL-ISSUE-60-LOGS | GITHUB-ISSUE-60 | issue-60-docker-release-01 | T-ISSUE-60-LOGS:docker | pw-issue-60-docker-release | admin-dashboard `/admin/dashboard` | admin-dashboard-page | C-ISSUE-60-LOGS | Core logs contain the Spring started marker and none of the three prohibited startup failures. | `BUILD_COMMIT=$(git rev-parse HEAD) ./scripts/verify-docker-release` | pending PR runtime |
| OBL-ISSUE-60-RESTART | GITHUB-ISSUE-60 | issue-60-docker-release-01 | T-ISSUE-60-RESTART:docker-playwright | pw-issue-60-docker-release | login `/login` | shared-auth-login-page | C-ISSUE-60-RESTART | Starting the current release against the pre-Issue-60 Flyway-location baseline preserves an operator channel, records no failed migration, and a further forced restart leaves every release fixture at one row. | `BUILD_COMMIT=$(git rev-parse HEAD) ./scripts/verify-docker-release` | pending PR runtime |
| OBL-ISSUE-60-IDENTITY | GITHUB-ISSUE-60 | issue-60-docker-release-01 | T-ISSUE-60-IDENTITY:playwright | pw-issue-60-docker-release | login `/login` | ycsopen-build-commit | C-ISSUE-60-BROWSER | Web metadata and Core `/actuator/info` both equal the exact checked-out build commit. | `BUILD_COMMIT=$(git rev-parse HEAD) ./scripts/verify-docker-release` | pending PR runtime |
