# Issue 60 Docker Release UI Elements

This addendum records machine-readable selectors used by release acceptance. It
does not change visual layout or replace a GSD phase production-exit record.

| Page ID/route | Role/permission | Region | Element/type | Data/validation/format | Action and API effect | States and feedback | data-testid | Obligation/requirement IDs | Behavior IDs | Catalog test/layer | Playwright ID |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| admin-dashboard `/admin/dashboard` | ADMIN | page root | main region | Authenticated dashboard content | Loads channel and tenant complaint-ratio APIs | loading, data, empty, error | admin-dashboard-page | OBL-ISSUE-60-DASHBOARD | issue-60-docker-release-01 | T-ISSUE-60-DASHBOARD:playwright | pw-issue-60-docker-release |
| login `/login` | public | document metadata | build identity meta | Exact non-empty Git commit supplied by the build | Read-only; no API mutation | present on every built SPA route | ycsopen-build-commit | OBL-ISSUE-60-IDENTITY | issue-60-docker-release-01 | T-ISSUE-60-IDENTITY:playwright | pw-issue-60-docker-release |
