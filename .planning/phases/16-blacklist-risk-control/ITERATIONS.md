# Iterations

## Iteration 1

- Implemented schema migration, service/controller APIs, risk-control page, API client, route/nav entries, unit tests, and Chrome Playwright coverage for the initial vertical slice.
- Produced PRD evidence files and UI contract artifacts for the 12 owned obligations.

## Iteration 2

- Fixed review findings around protected disable, real routing decision recording, protected blind-index lookup, check permission, list expiry, tenant selection, filters, duplicate request refs, appeal not-found handling, and degraded Playwright assertions.
- Added regression coverage for expired whitelist behavior and recorder-failure isolation in routing.

## Iteration 3

- Fixed final review HIGH: real routing now reads active Phase16 provider config and records `THIRD_PARTY_DEGRADED` allow/cache evidence without incorrectly blocking or skipping downstream routing.
- Added `ThirdPartyBlacklistClientTest` for DB-driven provider fallback and extended `RoutingEngineTest` for recordable degraded allow behavior.

## Iteration 4

- Fixed final review HIGH on `CACHE` fallback by applying fresh cached provider results from `third_party_risk_check_logs` within `cache_ttl_seconds`.
- Added a cached-hit regression proving degraded cache can still block when the fresh cached provider result is risky.
- Fixed follow-up HIGH by writing successful real-routing provider hit/miss outcomes into `third_party_risk_check_logs` so the cache is warmed by the routing path itself.

## Iteration 5

- Fixed Claude follow-up BLOCKER by marking the production constructors in `ThirdPartyBlacklistClient` and `BlacklistRiskControlService` with `@Autowired`.
- Kept alternate constructors package-private for focused tests, avoiding Spring bean ambiguity while preserving fast unit coverage.
