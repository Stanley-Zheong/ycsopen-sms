---
phase: 16-blacklist-risk-control
reviewed: 2026-09-09T04:27:00Z
depth: deep
status: clean
---

# Phase 16: Code Review Report

## Review Summary

The independent review initially found blocking/high-impact issues in the first implementation pass:

- Direct disable bypassed protected-list invariants.
- Real routing decisions were not recorded.
- Risk evaluation used unprotected ad-hoc lookups instead of the protected blind-index path.
- `/risk/check` mutated provider/degradation evidence under read-only permission.
- Expiry/effective-window logic was incomplete.
- Frontend check tenant id was hardcoded.
- Additional medium/low gaps existed for list filters/disable controls, duplicate request refs, appeal not-found handling, and test precision.

All listed findings were addressed in the implementation before exit verification.

## Resolution Evidence

- Protected create/disable now goes through `BlacklistEntryProtectionAdapter`.
- `BlacklistRiskControlService` implements `RiskDecisionRecorder`; `RoutingEngine` records blacklist evidence and catches recorder failures so blocking semantics remain stable.
- Real lookup uses `MessageTaskProtectionAdapter.prepareForRouting` and `BlindIndexLookupService.lookupBlacklist`.
- `/risk/check` requires `risk-analysis:check`; controller tests cover read/check separation.
- `effective_at` and `expires_at` are enforced in repository and blind-index metadata lookup paths.
- UI exposes tenant/filter inputs, disable action, provider config, degraded check, analytics, and appeal actions with stable test IDs.
- Final subagent reviews found three related HIGH issues: real routing still did not use Phase16 provider config or record degraded fallback evidence; `CACHE` fallback recorded a cache label without applying fresh cached decisions; and real routing did not warm that cache from successful provider outcomes. These were fixed by wiring `ThirdPartyBlacklistClient` to `risk_provider_configs`, adding deterministic `mock://failure` and `mock://hit` provider-contract modes, writing non-degraded routing provider outcomes to `third_party_risk_check_logs`, applying fresh cache results within `cache_ttl_seconds`, adding recordable degraded `BlacklistChecker.Result` values, and making `RoutingEngine` persist recordable degraded allow/cache/block decisions without corrupting route flow.
- Final Claude follow-up found Spring constructor ambiguity in `ThirdPartyBlacklistClient` and `BlacklistRiskControlService`; production constructors are now explicitly annotated with `@Autowired`, leaving package-private constructors for focused tests only.

## Review Table

| Cycle | Attempt | BLOCKER | HIGH | Escalated | Subject | Result |
| --- | ---: | ---: | ---: | --- | --- | --- |
| 1 | 1 | 4 | 2 | no | Initial Phase16 staged diff | BLOCKED |
| 1 | 2 | 0 | 1 | no | Corrected Phase16 staged diff after protected/routing/permission/expiry fixes | BLOCKED |
| 1 | 3 | 0 | 1 | yes | Corrected Phase16 staged diff after provider-config routing/degraded-evidence fix | BLOCKED |
| 2 | 1 | 0 | 1 | no | Corrected Phase16 staged diff after fresh-cache fallback implementation | BLOCKED |
| 2 | 2 | 0 | 0 | no | Corrected Phase16 staged diff after routing provider cache-warm implementation | PASS |
| 3 | 1 | 2 | 0 | no | Claude follow-up review after cache-warm fix | BLOCKED |
| 3 | 2 | 0 | 0 | no | Constructor ambiguity fix with explicit production `@Autowired` wiring | PASS |

## Final Verdict

PASS for blocker/high review. No unresolved blocking or high-severity findings remain.
