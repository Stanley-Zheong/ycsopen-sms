# Phase 16 Summary

## Delivered

- Added protected blacklist/whitelist management with masked display values, effective/expiry windows, protected create/disable, import partial-failure evidence, and export-request metadata.
- Added pre-task risk evaluation and routing evidence for tenant whitelist, system blacklist, tenant blacklist, and deterministic third-party provider risk.
- Added provider configuration, single/batch validation, configured degraded fallback behavior, provider logs, analytics, and appeal recording.
- Wired real routing provider checks to active Phase16 provider config and recorded degraded provider fallback decisions from the routing path, including routing-created fresh-cache hit/miss behavior.
- Resolved Spring constructor ambiguity with explicit production `@Autowired` constructor wiring while keeping test constructors package-private.
- Added Admin `/admin/riskcontrol` production page with list filters, list actions, provider controls, analytics, appeal action, and stable `data-testid` coverage.
- Added backend service/controller/security tests, protected lookup regression tests, frontend unit tests, and local Google Chrome Playwright acceptance.

## Verification

- `mvn -q -f core/pom.xml test`
- `mvn -q -f core/pom.xml -Dtest=RoutingEngineTest,ThirdPartyBlacklistClientTest,BlacklistRiskControlServiceTest,BlacklistRiskControlControllerSecurityContractTest,BlacklistEntryProtectionAdapterTest,BlindIndexLookupServiceTest test`
- `npm --prefix web ci`
- `npm --prefix web test`
- `npm --prefix web run build`
- `npm --prefix web exec -- playwright test blacklist-risk-control.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=json`
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner blacklist-risk-control --assert-unique --assert-traced`
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 16 --package blacklist-risk-control --stage production`

## Review

- `16-REVIEW.md`: final blocker/high count is zero.
- `CLAUDE-REVIEW.md`: blocker/high review passed after expiry, routing-recorder, cache-warm, and constructor-wiring fixes.

## Branch

- Branch: `phase/16-risk-list-control`
