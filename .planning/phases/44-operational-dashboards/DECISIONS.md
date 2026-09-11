# Phase 44 Decisions

- Use existing Phase 34 aggregate rows as the metric source of truth.
- Use `prepaid_accounts`, `trial_accounts`, and `tenant_contracts` for tenant overview instead of duplicating balance/trial/contract data.
- Use `channels.status` for current channel health counts because it is the canonical current channel state in the base schema.
- Use `fee_warning_episodes` for active finance warnings because Phase 40 records deduplicated warning episodes there.
- Keep dashboard configuration to role visibility and refresh settings; drag-and-drop layout is explicitly out of scope.
- Validate browser behavior only with local Google Chrome.
- Keep Phase 44 as a focused read/config layer and do not introduce dashboard widgets, polling infrastructure, or a second metric registry.
