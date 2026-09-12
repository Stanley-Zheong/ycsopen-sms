# Issue 51 Sidebar Navigation Test Matrix

This matrix is a scoped production-change addendum. It does not replace a GSD
phase production-exit record or claim that the unavailable local Chrome run has
passed.

| Obligation ID | Requirement IDs | Behavior ID | Catalog test/layer | Playwright ID | Page ID/route | data-testid | Case ID | Case | Command | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| OBL-ISSUE-51-ADMIN-SIDEBAR | PROJECT-UI-CONTRACT | issue-51-shared-sidebar-01 | T-ISSUE-51-ADMIN-SIDEBAR:playwright | pw-issue-51-admin-sidebar | admin-dashboard `/admin/dashboard` | admin-console-navigation-overview-group-toggle | C-ISSUE-51-ADMIN | Admin sidebar exposes the PRD module hierarchy, allows at most one expanded group, follows active deep routes, preserves permission-filtered leaves, and shows a visible keyboard-focus indicator. | `(cd web && ./node_modules/.bin/playwright test test/scripts/sidebar-navigation.spec.ts --config playwright.config.ts --project=local-google-chrome --grep 'pw-issue-51-admin-sidebar')` | pending-runtime: local standard-path Google Chrome and host libraries are unavailable |
| OBL-ISSUE-51-TENANT-SIDEBAR | PROJECT-UI-CONTRACT | issue-51-shared-sidebar-01 | T-ISSUE-51-TENANT-SIDEBAR:playwright | pw-issue-51-tenant-sidebar | tenant-overview `/tenant/overview` | tenant-console-navigation-overview-group-toggle | C-ISSUE-51-TENANT | Tenant sidebar exposes the PRD module hierarchy, allows at most one expanded group, follows active routes, and applies the later production-owner role matrix to administrator, business-user, and developer leaves. | `(cd web && ./node_modules/.bin/playwright test test/scripts/sidebar-navigation.spec.ts --config playwright.config.ts --project=local-google-chrome --grep 'pw-issue-51-tenant-sidebar')` | pending-runtime: local standard-path Google Chrome and host libraries are unavailable |
