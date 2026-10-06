# Spirit 03 Quality Gateway

## Required Commands

| Gate | Command | Required result | Evidence |
|---|---|---|---|
| Diff hygiene | `git diff --check` | Pass | Not recorded |
| Unit tests | `npm --prefix web test` plus targeted finance tests | Pass | Not recorded |
| Build | `npm --prefix web run build` | Pass | Not recorded |
| Chrome Playwright | Finance/tenant commercial route coverage for selected issue | Pass | Not recorded |
| Backend checks | `mvn -f core/pom.xml test` when API, billing, or persistence behavior changes | Pass or not applicable reason | Not recorded |

## Issue #122 Gate

| Gate | Command | Evidence status |
|---|---|---|
| Focused backend | `mvn -f core/pom.xml -Dtest=ContractPricingServiceTest,ContractPricingControllerSecurityContractTest,OperationAuditInterceptorTest,TenantReviewServiceTest,TrialPrepaidLedgerServiceTest test` | Pending final combined run |
| Focused frontend | `npm --prefix web test -- --run test/unit/contract-pricing.test.tsx test/unit/trial-prepaid.test.tsx` | PASS, 9 tests |
| Chromium | `YCSOPEN_USE_BUNDLED_CHROMIUM=true npm --prefix web exec -- playwright test contract-pricing.spec.ts --config web/playwright.config.ts --project=bundled-chromium --workers=1` | Pending |
| Full gates | Maven suite, Vitest suite, Vite build, planning validators, `git diff --check` | Pending |

## Merge Gate

- Gate item: Trial, prepaid, postpaid, billing, and invoice terms are not conflated.
- Gate item: Finance actions have target-aware confirmation and audit input when required.
- Gate item: PR body records verification evidence and unresolved product decisions.
- Gate item: displayed and transaction-time conversion eligibility share one
  server owner and include tenant/account/trial/price checks.
- Gate item: the conversion form is bound to the selected tenant, accepts only
  active prices, preserves values on rejection, and refreshes after success.
