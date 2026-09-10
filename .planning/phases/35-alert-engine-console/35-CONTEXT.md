# Phase 35 Context

Package: `alert-engine-console`

Phase 35 builds the alert-management loop:

- Configure alert rules.
- Select notification channels and recipients.
- Evaluate source events into deduplicated alert episodes.
- Record delivery attempts and adapter failures.
- Acknowledge, resolve, and mute alerts.
- Present dashboard cards, alert tabs, history, and delivery evidence in the Admin console.

Scope is intentionally limited to one synchronous source-event evaluation path. No external notification providers, async workers, or broad dashboard platform are introduced in this phase.
