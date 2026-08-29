# Current Implementation Audit

## Verified baseline

- Backend unit suite currently executes 18 tests.
- Frontend unit suite currently executes 4 formatting tests.
- Frontend lint does not run because the repository has no ESLint configuration.
- There is no Playwright dependency, configuration, script, or executable end-to-end test.
- There is no `data-testid` in `web/src`.

## Existing behavior that must be hardened rather than assumed complete

| Area | Existing code | Blocking gaps |
| --- | --- | --- |
| Login | Password check, failure lock, JWT issuance | JWT is not enforced on console APIs; no real RBAC or server-side tenant isolation |
| Tenant onboarding | Registration and a partial trial activation path | Required qualification fields are dropped; trial quota is not consumed by sending |
| Channel admin | Basic list/create/pause/resume endpoints | Incomplete channel fields, no dependency migration, health check, task failover, or audit closure |
| Routing | Short-circuit orchestration for blacklist, content, frequency, and channel selection | Individual rules are incomplete or stubbed; no attribution, pools, weights, real circuit breaking, or checker integration tests |
| HTTP submission | Basic template/signature check, render, route, task insert, debit reservation | Signature header is not verified, no idempotency, no upstream delivery, no receipt transition, and no billing confirmation/reversal caller |
| Billing | Prepaid reserve/confirm/reverse methods | Fixed price, no trial path, no real database concurrency evidence, and no dispatch integration |
| Complaint ratio | Partial calculation and tables | Incorrect denominator, missing names, drill-down, pause action, threshold configuration, and real complaint workflow dependency |
| Web console | Login, partial channel/tenant/dashboard/send pages | Tenant list API is missing, send uses the wrong auth surface, overview and most routes are placeholders |

## Skeleton-only or absent areas

- Upstream and downstream CMPP, and other carrier protocols.
- Signature/template lifecycle and operator filing.
- Channel connector runtime, health, hot reload, pools, and failover.
- Detailed message queries, export, resend, and callback monitoring.
- Postpaid billing, recharge, statement, settlement, invoice, cost, profit, and expense alerts.
- Complaint case flow, uplink normalization, unsubscribe compliance, alert engine, short links, status codes, number attribution maintenance, task tools, and audit instrumentation.
- Production-grade integration, contract, component, accessibility, visual, security, performance, failure, recovery, and end-to-end tests.

## Corrected dependency facts

- Complaint ratio delivery depends on real message aggregation and complaint records.
- Tenant auto-pause depends on tenant status control, unsubscribe/complaint data, and alert delivery.
- Cooperation termination depends on settlement, credential revocation, and signature/template deactivation.
- Bulk and downstream CMPP submissions must reuse the same proven single-message acceptance pipeline.
- Every UI module is delivered with its API, authorization, error handling, test-ID contract, and Playwright evidence in the same phase.

