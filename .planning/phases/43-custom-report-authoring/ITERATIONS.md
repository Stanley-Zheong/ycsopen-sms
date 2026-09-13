# Phase 43 Iterations

## Iteration 1

- RED: `CustomReportServiceTest` failed because `CustomReportService` did not exist.
- GREEN: Implemented service and migration.
- Fix: Generated key handling now reads the explicit `id` key when H2 returns multiple generated columns.

## Iteration 2

- RED: `CustomReportControllerTest` failed because `CustomReportController` did not exist.
- GREEN: Implemented controller endpoints.

## Iteration 3

- RED: frontend unit test failed because `customReportApi` and `AdminCustomReportsPage` did not exist.
- GREEN: Implemented API client, page, route, navigation, and production Playwright script.

## Iteration 4

- RED: `CustomReportServiceTest` failed on unmapped registry exposure, tenant actor role-scope persistence, lowercase `messageType`, and overlong report names.
- GREEN: Added supported metric filtering from the dimension/measure whitelist intersection, tenant role-scope forcing, uppercase message type normalization, and report-name length validation.

## Iteration 5

- RED: frontend unit test failed because selected measures were truncated to the first three registry measures.
- GREEN: The report builder now submits all registry-supported measures for the selected metric and verifies metric switching changes the submitted command.

## Review fixes

- Claude summary review raised ambiguity around per-field validation and auth principal derivation; existing code/tests close those risks.
- Claude summary review identified whitelist drift as actionable; implementation now derives exposed metric codes from the supported dimension/measure maps.
