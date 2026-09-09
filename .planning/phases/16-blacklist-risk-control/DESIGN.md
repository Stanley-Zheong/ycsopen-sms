# Design

## Data Design

- `blacklist_entries` stores protected mobile ciphertext/hash plus masked display text, list type, source, status, effective time, and optional expiry.
- `risk_provider_configs` stores provider URL, protected credential reference, risk level, score threshold, timeout, and fallback mode.
- `risk_intercept_decisions` stores the immutable pre-task decision evidence: tenant, request id, protected mobile reference, source category, risk result, trace reason, task/charge flags, score, and provider trace.
- `risk_intercept_appeals` stores false-positive appeal evidence without mutating the original decision row.
- `third_party_risk_check_logs` records provider request kind, item count, score/result, fallback policy, and degradation reason.

## Backend Design

- Runtime risk lookup uses the existing protected routing preparation and blind-index lookup path.
- Evaluation order is tenant whitelist, system blacklist, tenant blacklist, then deterministic provider contract.
- Disable operations go through the protected-list adapter instead of direct SQL mutation of protected bindings.
- Routing records blacklist/degraded decision evidence on best effort; recorder failure cannot convert a block into allow or a degraded allow into rejection.
- Provider checks from the real routing path write non-degraded hit/miss rows into `third_party_risk_check_logs`.
- Provider `CACHE` fallback first applies the latest non-degraded provider result within `cache_ttl_seconds`, keyed by protected mobile reference and provider. A cached hit blocks with `THIRD_PARTY_DEGRADED` evidence; a cached miss or missing fresh cache allows with degraded evidence.

## UI Design

- `/admin/riskcontrol` contains three production surfaces: black/white list management, third-party provider config/check, and interception analytics/appeal.
- Every browser-asserted element uses stable `data-testid` values recorded in `UI-ELEMENTS.md`.
- Chrome Playwright verifies the production route using the locally installed Google Chrome project.
