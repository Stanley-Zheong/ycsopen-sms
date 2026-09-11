# Phase 35 UI Spec

## Admin `/admin/alerts`

- Dashboard card region: total, active, severe, and resolved alert counts.
- Alert tabs: all, active, severe.
- Rule form: name, metric, comparison, threshold, duration, severity, source, state.
- Notification settings: channel JSON for SMS/EMAIL/DINGTALK/WECOM and target JSON for operations/finance/tenant/role/individual targets.
- Alert history table: title, severity, state, description, source, impact, trigger time, delivery state.
- Actions: acknowledge, resolve, mute; actor/time/state are recorded by backend.
- Delivery attempts table: alert id, channel, target snapshot, provider result, retry count, status, failure reason.

Style:

- Reuse existing console sidebar/card/table/input/button conventions.
- Chrome is the only supported verification browser.
- No mobile-specific layout.
