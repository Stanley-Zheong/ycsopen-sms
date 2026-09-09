# Phase 22 Iterations

1. Added backend migration/service/controller/tests.
2. Fixed reserve idempotency so existing prepaid balance is not overwritten.
3. Fixed duplicate trial consumption ordering so idempotency wins even after freeze.
4. Added UI pages, API client, routes, navigation, unit tests, and Playwright scripts.
5. Fixed Playwright qualification mock path from `/api/v1/tenant/qualification` to `/api/v1/console/tenant/qualification`.
6. Fixed Claude review blockers: freeze rollback, prepaid reserve version guard, tenant scoping, and production `credit-test` endpoint removal.
7. Fixed second Claude review blocker: confirm/reverse now conditionally transition reserved ledger state before mutating money.
