# Playwright generation attempt

- Baseline: `origin/main@2a8acfe`
- Source evidence: `web/test/cases/console-core-flows.md`
- Contract: `web/test/cases/automation-contracts.yaml`
- Fixture plan: `web/test/cases/fixture-plan.yaml`
- Generated coverage: WEB-AUTH-001~007, WEB-DASH-001~002, WEB-TENANT-001, WEB-CHANNEL-001~002, WEB-SEND-001~002.
- Isolation: per-test browser context and per-test network route state; parallel-safe; no remote mutation.
- Static gate: `npm run build` and `npx playwright test --list` passed; 14 tests discovered across 4 spec files.
- Runtime gate: blocked before test execution because the container lacks Chromium system libraries; browser download succeeded, but `npx playwright install-deps chromium` could not elevate to root.
- Failure classification: `environment_issue`; all 14 cases failed at `browserType.launch`, so no product or spec oracle was evaluated.
- Known gap: mocked requests do not prove backend authentication, persistence, routing failover, or the known-unintegrated JWT console send path.
