# Iterations

## Iteration 1

- Defined thin Phase 15 scope from the single owned obligation.
- Implemented backend read projection and frontend review-history page.
- Fixed Playwright mock route precedence after reproducing the detail drawer empty-field failure.

## Iteration 2

- Fixed code-review findings: permission migration columns, ADMIN/OPERATOR alignment, decision-time filters, invalid date validation, and OPERATOR-path tests.
- Fixed Claude high finding by bounding list queries and replacing detail full-scan with direct type-specific lookup.
- Fixed final pagination overflow medium by capping `page` and `pageSize`.
