# Issue 122 Test Matrix

| Case | Layer | Claim | Command | Status |
|---|---|---|---|---|
| Service and transaction | Java/H2 | Real trial-window metrics; fallback/provisioning; VERIFIED/NORMAL eligibility; active prices; atomic success and rollback; role, tenant scope, and audit outcomes | `mvn -f core/pom.xml -Dtest=ContractPricingServiceTest,ContractPricingControllerSecurityContractTest,OperationAuditInterceptorTest,TenantReviewServiceTest,TrialPrepaidLedgerServiceTest test` | PASS, 37 tests |
| Component | React/Vitest | Filters, source metadata, analysis, selected identity, active price, conditional fields, trial adjustment, and preserved rejection | `npm --prefix web test -- --run test/unit/contract-pricing.test.tsx test/unit/trial-prepaid.test.tsx` | PASS, 10 tests |
| Browser | Bundled Chromium/Playwright | Filter, analysis, adjustment, conversion/readback, rejection preservation, query error/retry, analysis states, and tenant overview | `YCSOPEN_USE_BUNDLED_CHROMIUM=true npm --prefix web exec -- playwright test contract-pricing.spec.ts --config web/playwright.config.ts --project=bundled-chromium --workers=1 --reporter=json` | PASS, 7 tests |
| Repository gates | Maven, Vitest, TypeScript/Vite, planning | Full suites and planning validators | See `VERIFICATION.md` | PASS for issue scope; full Maven and Docker boundaries recorded |
