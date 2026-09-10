# Phase 35 Design

## Backend

- V4400 extends existing `alert_rules` and `alert_records` tables.
- V4400 adds `alert_delivery_attempts` and `alert_mutes`.
- `AlertEngineService.evaluate` accepts a source event and checks active rules for the event metric.
- A rule only fires when comparison matches and sustained minutes meet rule duration.
- Open episodes are deduped by `rule_id + source_key + open status`.
- Non-breaching source events resolve open episodes as `SOURCE_RECOVERED`.
- Delivery attempts are recorded synchronously as delivered, failed, or muted evidence.
- Muting suppresses future delivery attempts only; it does not change alert lifecycle state.

## Frontend

- `/admin/alerts` is the single Phase35 surface.
- Page regions: dashboard cards, tabs, rule form/list, notification targets, history/actions, delivery attempts.
- Every owned page/element has a stable `data-testid` recorded in `UI-ELEMENTS.md`.
