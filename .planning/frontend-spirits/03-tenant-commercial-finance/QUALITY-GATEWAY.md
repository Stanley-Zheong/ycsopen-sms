# Spirit 03 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | PASS |
| Unit tests | `npm --prefix web test` plus targeted finance tests | Pass | PASS, 226 full and 10 targeted tests; a later full rerun reproduced an existing identity-page timeout and that file then passed 8/8 alone |
| Build | `npm --prefix web run build` | Pass | PASS |
| Chrome Playwright | Finance/tenant commercial route coverage for selected issue | Pass | PASS, 7 bundled Chromium tests |
| Backend checks | `mvn -f core/pom.xml test` when API, billing, or persistence behavior changes | Pass or not applicable reason | Focused PASS, 37 tests; full-suite process-reaping boundary in `37-VERIFICATION.md` |

## Issue #122 Gate

| Gate | Command | Evidence status |
|---|---|---|
| Focused backend | `mvn -f core/pom.xml -Dtest=ContractPricingServiceTest,ContractPricingControllerSecurityContractTest,OperationAuditInterceptorTest,TenantReviewServiceTest,TrialPrepaidLedgerServiceTest test` | PASS, 37 tests |
| Focused frontend | `npm --prefix web test -- --run test/unit/contract-pricing.test.tsx test/unit/trial-prepaid.test.tsx` | PASS, 10 tests |
| Chromium | `YCSOPEN_USE_BUNDLED_CHROMIUM=true npm --prefix web exec -- playwright test contract-pricing.spec.ts --config web/playwright.config.ts --project=bundled-chromium --workers=1 --reporter=json` | PASS, 7 tests; commit-bound report |
| Full gates | Maven suite, Vitest suite, Vite build, planning validators, `git diff --check` | Frontend/build/validators/diff PASS; Maven and Docker runtime boundary recorded |

## Merge Gate

- Gate item: Trial, prepaid, postpaid, billing, and invoice terms are not conflated.
- Gate item: Finance actions have target-aware confirmation and audit input when required.
- Gate item: PR body records verification evidence and unresolved product decisions.
- Gate item: displayed and transaction-time conversion eligibility share one
  server owner and include tenant/account/trial/price checks.
- Gate item: the conversion form is bound to the selected tenant, accepts only
  active prices, preserves values on rejection, and refreshes after success.
