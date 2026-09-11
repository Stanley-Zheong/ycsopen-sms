# Phase 48 ITERATIONS

## Iteration 1

Added backend migration/service/controller and tests. Existing V1 short-link schema was discovered, so the migration extends those tables instead of recreating them.

## Iteration 2

Added tenant/admin/public React pages, API client, route/nav entries, unit tests and local-Chrome Playwright tests.

## Iteration 3

Review found that the initial tenant endpoints used `/api/v1/tenant/**`, outside the existing JWT filter path. Moved tenant short-link APIs and frontend client calls to `/api/v1/console/tenant/shortlinks/**`, and added the matching tenant-role rule in `SecurityConfig`.

Review also found that tenant ID was accepted from the request body. The controller now derives tenant scope from the authenticated user record and ignores spoofed request-body tenant IDs.

## Iteration 4

Changed target-domain blacklist handling from pre-create rejection to explicit `DOMAIN_BLACKLISTED` automated evidence. Blacklisted targets remain `PENDING` with `BLOCKED` automated verdict and cannot be approved or redirected.
