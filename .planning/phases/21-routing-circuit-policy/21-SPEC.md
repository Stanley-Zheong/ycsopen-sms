# Phase 21 Spec — Routing Circuit Policy

## Owned obligations

- `OBL-F-5-8-A`: Operators author ordered rules over carrier, prefix, tenant, content keyword, and time with channel, pool, or weight target.
- `OBL-F-5-8-B`: Rule precedence and matching are deterministic, explicit default handles no match, and simulation explains the selected version and target.
- `OBL-F-5-9-A`: Weighted and primary-backup selection consumes current health, success, and latency observations and excludes ineligible channels.
- `OBL-F-5-9-B`: Circuit open, fallback, half-open probe, recovery, and anti-flapping behavior follow configured thresholds and preserve decision history.
- `OBL-F-5-10-A`: Retry policy maps normalized error class to retryability, delay, and maximum attempts.
- `OBL-FLOW-12-2-ROUTING`: Channel health, statistics, complaint ratio, cost, route weights, pause, alert, fallback, and recovery use consistent source and policy versions.
- `OBL-DATA-10-4-ROUTING`: Pools, routes, prefixes, protected portability records, and retry rules preserve versioned conditions, targets, weights, priority, state, source, and history.

## Acceptance

- `mvn -q -f core/pom.xml test`
- `npm --prefix web ci`
- `npm --prefix web test`
- `npm --prefix web run build`
- `npm --prefix web exec -- playwright test routing-policy.spec.ts --config web/playwright.config.ts --project=local-google-chrome --reporter=json`
- `/usr/bin/env ruby .planning/tools/validate-prd-obligations.rb --owner routing-circuit-policy --assert-unique --assert-traced`
- `/usr/bin/env ruby .planning/tools/validate-ui-contract.rb --phase 21 --package routing-circuit-policy --stage production`

