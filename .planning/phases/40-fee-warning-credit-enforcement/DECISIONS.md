# Phase 40 Decisions

- Use `fee_warning_rules` and `fee_warning_episodes`; do not introduce a generic policy engine.
- Use Phase35 `alert_records` and `alert_delivery_attempts` as delivery evidence instead of adding another notification subsystem.
- Use `tenantId + ruleId + metricType` as the dedupe source key.
- Keep browser verification Chrome-only.
