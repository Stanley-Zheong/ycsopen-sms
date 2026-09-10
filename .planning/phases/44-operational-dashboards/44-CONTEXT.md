# Phase 44 Context

## Dependencies used

- Phase 22/31 account and prepaid tables: `trial_accounts`, `prepaid_accounts`.
- Phase 34 statistics registry and aggregates: `statistics_metric_registry`, `statistics_aggregates`.
- Phase 37 contract state: `tenant_contracts`.
- Phase 40 fee warning episodes: `fee_warning_episodes`.
- Phase 1 base tables: `users`, `tenants`, `channels`.

## Implementation boundary

The phase is a read-side dashboard/configuration layer. It does not create new analytics facts. Every metric shown by React comes from declared existing tables or from the new role configuration table.
